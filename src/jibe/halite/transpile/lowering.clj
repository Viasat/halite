;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.transpile.lowering
  "Re-express halite specs in a minimal subset of halite, by compiling higher-level
  features down into lower-level features."
  (:require [clojure.set :as set]
            [jibe.halite.halite-envs :as halite-envs]
            [jibe.halite.halite-types :as halite-types]
            [jibe.halite.transpile.ssa :as ssa
             :refer [DerivationName Derivations SpecInfo SpecCtx make-ssa-ctx]]
            [jibe.halite.transpile.rewriting :refer [rewrite-sctx ->>rewrite-sctx] :as halite-rewriting]
            [jibe.halite.transpile.simplify :refer [simplify]]
            [jibe.halite.transpile.util :refer [fixpoint mk-junct]]
            [schema.core :as s]
            [weavejester.dependency :as dep]))

;;;;;;;;; Bubble up and Flatten $do! ;;;;;;;;;;;;

(defn- make-do
  [side-effects body]
  (if (empty? side-effects)
    body
    `(~'$do! ~@side-effects ~body)))

;; We don't generally want to have to invent rewrite rules for
;; all the various forms as combined with $do!, so we'll write
;; some rules that move and combine $do! forms so as to minimize
;; the number of things we need to handle.

;; Here are some rules for 'bubbling up' dos:

;; For any of the halite 'builtins', along with get, =, not=, refine-to, valid?,
;; we can 'pull out' a $do! form.

(defn- do-form? [form] (and (seq? form) (= '$do! (first form))))

(def do!-fence-ops
  "The set of ops that a $do! form cannot bubble up out of without changing semantics."
  '#{if valid?})

(s/defn ^:private first-nested-do :- (s/maybe [(s/one s/Int :index) (s/one ssa/Derivation :deriv)])
  [{:keys [dgraph] :as ctx}, form]
  (->> (rest form)
       (map-indexed #(vector (inc %1) (when (symbol? %2) (ssa/deref-id dgraph %2))))
       (remove (comp nil? second))
       (filter (comp do-form? first second))
       first))

(s/defn ^:private bubble-up-do-expr
  [sctx :- SpecCtx, {:keys [dgraph] :as ctx} :- ssa/SSACtx, id, [form htype]]
  (cond
    (map? form)
    (let [[match? done-ids inst]
          ,,(reduce
             (fn [[match? done-ids inst] [var-kw val-id]]
               (let [[val-form] (ssa/deref-id dgraph val-id)]
                 (if (do-form? val-form)
                   [true (into done-ids (butlast (rest val-form))) (assoc inst var-kw (last val-form))]
                   [match? done-ids (assoc inst var-kw val-id)])))
             [false [] (select-keys form [:$type])]
             (sort-by key (dissoc form :$type)))]
      (when match?
        (make-do done-ids inst)))

    ;; We *cannot* pull $do! forms out of valid? or the branches of if, but we can
    ;; pull it out of the predicates of ifs.
    (and (seq? form) (= 'if (first form)) (do-form? (first (ssa/deref-id dgraph (second form)))))
    (let [[_if pred-id then-id else-id] form
          [pred-do-form] (ssa/deref-id dgraph (second form))]
      (make-do (butlast (rest pred-do-form))
               (list 'if (last pred-do-form) then-id else-id)))

    (and (seq? form) (not (contains? do!-fence-ops (first form))))
    (when-let [[i [nested-do-form]] (first-nested-do ctx form)]
      (make-do (->> nested-do-form rest butlast) (seq (assoc (vec form) i (last nested-do-form)))))))

;; Finally, we can 'flatten' $do! forms.

(s/defn ^:private flatten-do-expr
  [sctx :- SpecCtx, {:keys [dgraph] :as ctx} :- ssa/SSACtx, id, [form htype]]
  (when (do-form? form)
    (when-let [[i [nested-do-form]] (first-nested-do ctx form)]
      (concat (take i form) (rest nested-do-form) (drop (inc i) form)))))

;;;;;;;;; Instance Comparison Lowering ;;;;;;;;

(s/defn ^:private lower-instance-comparison-expr
  [sctx :- SpecCtx, {:keys [dgraph senv] :as ctx} id [form type]]
  (when (and (seq? form) (#{'= 'not=} (first form)))
    (let [comparison-op (first form)
          logical-op (if (= comparison-op '=) 'and 'or)
          arg-ids (rest form)
          arg-types (set (map (comp second dgraph) arg-ids))]
      (when (every? halite-types/spec-type? arg-types)
        (if (not= 1 (count arg-types))
          (= comparison-op 'not=)
          (let [arg-type (first arg-types)
                var-kws (->> arg-type (halite-types/spec-id) (halite-envs/lookup-spec senv) :spec-vars keys sort)]
            (->> var-kws
                 (map (fn [var-kw]
                        (apply list comparison-op
                               (map #(list 'get %1 var-kw) arg-ids))))
                 (mk-junct logical-op))))))))

(s/defn ^:private lower-instance-comparisons :- SpecCtx
  [sctx :- SpecCtx]
  (rewrite-sctx sctx lower-instance-comparison-expr))

;;;;;;;;; Push gets inside instance-valued ifs ;;;;;;;;;;;

(s/defn ^:private push-gets-into-ifs-expr
  [sctx :- SpecCtx, {:keys [dgraph] :as ctx} :- ssa/SSACtx, id, [form htype]]
  (when (and (seq? form) (= 'get (first form)))
    (let [[_get arg-id var-kw] form
          [subform htype] (ssa/deref-id dgraph (second form))]
      (when (and (seq? subform) (= 'if (first subform)) (halite-types/spec-type? htype))
        (let [[_if pred-id then-id else-id] subform]
          (list 'if pred-id (list 'get then-id var-kw) (list 'get else-id var-kw)))))))

(s/defn ^:private push-gets-into-ifs :- SpecCtx
  [sctx :- SpecCtx]
  (rewrite-sctx sctx push-gets-into-ifs-expr))

;;;;;;;;; Lower valid? ;;;;;;;;;;;;;;;;;

(s/defn ^:private deps-via-instance-literal :- #{halite-types/NamespacedKeyword}
  [{:keys [derivations] :as spec-info} :- SpecInfo]
  (->> derivations
       vals
       (map (fn [[form htype]] (when (map? form) (:$type form))))
       (remove nil?)
       (set)))

(declare validity-guard)

(s/defn ^:private validity-guard-inst :- ssa/DerivResult
  [sctx :- SpecCtx, {:keys [dgraph] :as ctx} :- ssa/SSACtx, [ssa-form htype] :- ssa/Derivation]
  (let [{:keys [spec-vars derivations constraints] :as spec-info} (sctx (:$type ssa-form))
        inst-entries (dissoc ssa-form :$type)
        [dgraph pred-clauses] (reduce
                               (fn [[dgraph pred-clauses] [var-kw var-expr-id]]
                                 (let [[dgraph clause-id] (validity-guard sctx (assoc ctx :dgraph dgraph) var-expr-id)]
                                   [dgraph (conj pred-clauses clause-id)]))
                               [dgraph []]
                               (sort-by first  inst-entries))
        [dgraph bindings] (reduce
                           (fn [[dgraph bindings] var-kw]
                             (let [var-expr-id-or-no-value (or (inst-entries var-kw) '$no-value)]
                               [dgraph (conj bindings (symbol var-kw) var-expr-id-or-no-value)]))
                           [dgraph []]
                           (->> spec-vars keys sort))
        scope (->> spec-info :spec-vars keys (map symbol) set)
        the-form (list 'if
                       (mk-junct 'and pred-clauses)
                       (list 'let (vec bindings)
                             (mk-junct 'and
                                       (map (fn [[cname id]]
                                              (ssa/form-from-ssa scope derivations id))
                                            constraints)))
                       false)]
    (ssa/form-to-ssa
     (assoc ctx :dgraph dgraph)
     the-form)))

(s/defn ^:private validity-guard-if :- ssa/DerivResult
  [sctx :- SpecCtx, {:keys [dgraph] :as ctx} :- ssa/SSACtx, [[_if pred-id then-id else-id] htype] :- ssa/Derivation]
  (let [[dgraph pred-guard-id] (validity-guard sctx ctx pred-id)
        [dgraph then-guard-id] (validity-guard sctx (assoc ctx :dgraph dgraph) then-id)
        [dgraph else-guard-id] (validity-guard sctx (assoc ctx :dgraph dgraph) else-id)]
    (ssa/form-to-ssa
     (assoc ctx :dgraph dgraph)
     (list 'if pred-guard-id
           (list 'if pred-id then-guard-id else-guard-id)
           false))))

(s/defn ^:private validity-guard-when :- ssa/DerivResult
  [sctx :- SpecCtx, {:keys [dgraph] :as ctx} :- ssa/SSACtx, [[_if pred-id then-id] htype] :- ssa/Derivation]
  (let [[dgraph pred-guard-id] (validity-guard sctx ctx pred-id)
        [dgraph then-guard-id] (validity-guard sctx (assoc ctx :dgraph dgraph) then-id)]
    (ssa/form-to-ssa
     (assoc ctx :dgraph dgraph)
     (list 'if pred-guard-id
           (list 'if pred-id then-guard-id true)
           false))))

(s/defn ^:private validity-guard-app :- ssa/DerivResult
  [sctx :- SpecCtx, {:keys [dgraph] :as ctx} :- ssa/SSACtx, [[op & args] htype] :- ssa/Derivation]
  (let [[dgraph guard-ids] (reduce
                            (fn [[dgraph guard-ids] arg-id]
                              (let [[dgraph guard-id] (validity-guard sctx (assoc ctx :dgraph dgraph) arg-id)]
                                [dgraph (conj guard-ids guard-id)]))
                            [dgraph []]
                            args)]
    (ssa/form-to-ssa
     (assoc ctx :dgraph dgraph)
     (mk-junct 'and guard-ids))))

(s/defn validity-guard :- ssa/DerivResult
  "Given an expression <expr>, the function validity-guard computes an expression that
  * evaluates to true iff <expr> evaluates to a value
  * evaluates to false iff evaluating <expr> results in the evaluation of an invalid instance
  * evaluates to a runtime error otherwise

  (validity-guard <int>) => true
  (validity-guard <boolean>) => true
  (validity-guard <spec-var>) => true
  (validity-guard (if <pred> <then> <else>))
  => (if <(validity-guard pred)>
       (if <pred> <(validity-guard then)> <(validity-guard else)>)
       false)
  (validity-guard (when <pred> <then>)
  => (if <(validity-guard pred)>
       (if <pred> <(validity-guard then)> true)
       false)
  (validity-guard (get <expr> <var-kw>) => <(validity-guard expr)>
  (validity-guard (valid? <expr>)) => <(validity-guard expr)>
  (validity-guard (<op> <...expr_i>)) => (and <...(validity-guard expr_i)>)
  (validity-guard {<...kw_i expr_i>})
  => (if (and <...(validity-guard expr_i)>)
       (let [<...kw_i (or expr_i $no-value)>]
         <inlined constraints>)
       false)"
  [sctx :- SpecCtx, {:keys [dgraph] :as ctx} :- ssa/SSACtx, id :- DerivationName]
  (let [[ssa-form htype :as deriv] (ssa/deref-id dgraph id)]
    (cond
      (or (int? ssa-form) (boolean? ssa-form) (= :Unset ssa-form) (symbol? ssa-form) (string? ssa-form)) (ssa/form-to-ssa ctx true)
      (map? ssa-form) (validity-guard-inst sctx ctx deriv)
      (seq? ssa-form) (let [[op & args] ssa-form]
                        (condp = op
                          'if (validity-guard-if sctx ctx deriv)
                          'when (validity-guard-when sctx ctx deriv)
                          'get (validity-guard sctx ctx (first args))
                          'valid? (validity-guard sctx ctx (first args))
                          (validity-guard-app sctx ctx deriv)))
      :else (throw (ex-info "BUG! Cannot compute validity-guard" {:dgraph dgraph :id id :deriv deriv})))))

(s/defn ^:private lower-valid?-expr
  [sctx :- SpecCtx, {:keys [dgraph] :as ctx} :- ssa/SSACtx, id, [form htype]]
  (when (and (seq? form) (= 'valid? (first form)))
    (let [[_valid? expr-id] form]
      (let [[dgraph new-id] (validity-guard sctx ctx expr-id)]
        (vary-meta new-id assoc :dgraph dgraph)))))

(s/defn ^:private lower-valid? :- SpecCtx
  [sctx :- SpecCtx]
  (halite-rewriting/rewrite-in-dependency-order*
   {:rule-name "lower-valid?"
    :rewrite-fn lower-valid?-expr
    :nodes :all}
   deps-via-instance-literal
   sctx))

;;;;;;;;;; Lower refine-to ;;;;;;;;;;;;;;;

(defn- refn-paths*
  [refn-graph curr-id target-id]
  (if (= curr-id target-id)
    [[target-id]]
    (let [paths (mapcat #(refn-paths* refn-graph % target-id) (refn-graph curr-id))]
      (mapv (partial cons curr-id) paths))))

(s/defn ^:private refn-paths :- [[halite-types/NamespacedKeyword]]
  [sctx :- SpecCtx, from-spec-id :- halite-types/NamespacedKeyword, to-spec-id :- halite-types/NamespacedKeyword]
  ;; NOTE! This function *assumes* the refinement graph is acyclic, and will not terminate otherwise.
  (let [refn-graph (-> (->> sctx
                            (mapcat (fn [[from-id {:keys [refines-to]}]]
                                      (for [to-id (keys refines-to)]
                                        [from-id to-id])))
                            (group-by first))
                       (update-vals (partial mapv second)))]
    (refn-paths* refn-graph from-spec-id to-spec-id)))

(s/defn ^:private lower-refine-to-expr :- ssa/DerivResult
  [{:keys [dgraph senv] :as ctx} :- ssa/SSACtx
   expr-id :- DerivationName
   from-spec-id :- halite-types/NamespacedKeyword
   path :- [halite-types/NamespacedKeyword]]
  (if (empty? path)
    [dgraph expr-id]
    (let [{:keys [spec-vars refines-to]} (halite-envs/lookup-spec senv from-spec-id)
          to-spec-id (first path)
          [dgraph new-expr-id] (ssa/dup-node dgraph expr-id)
          bindings (vec (mapcat (fn [var-kw] [(symbol var-kw) (list 'get new-expr-id var-kw)]) (keys spec-vars)))
          refn-expr (-> refines-to to-spec-id :expr)
          new-form (list 'let bindings refn-expr)
          [dgraph id] (ssa/form-to-ssa (assoc ctx :dgraph dgraph) expr-id new-form)]
      (recur (assoc ctx :dgraph dgraph) id to-spec-id (rest path)))))

(s/defn ^:private lower-refine-to-in-spec :- SpecInfo
  [sctx :- SpecCtx, {:keys [derivations] :as spec-info} :- SpecInfo]
  (let [ctx (make-ssa-ctx sctx spec-info)]
    (->> derivations
         (filter
          (fn [[id [form]]] (and (seq? form) (= 'refine-to (first form)))))
         (reduce
          (fn [dgraph [id [[_refine-to expr-id to-spec-id]] :as deriv]]
            (let [[_ htype] (ssa/deref-id dgraph expr-id)
                  from-spec-id (halite-types/spec-id htype)]
              (if (= from-spec-id to-spec-id)
                (ssa/rewrite-node dgraph id expr-id)
                (let [paths (refn-paths sctx from-spec-id to-spec-id)
                      npaths (count paths)]
                  (condp = npaths
                    0 (throw (ex-info (format "BUG! No refinement path from '%s' to '%s'" from-spec-id to-spec-id)
                                      {:dgraph derivations :id id :deriv deriv}))
                    1 (let [[dgraph new-id] (lower-refine-to-expr (assoc ctx :dgraph dgraph) expr-id from-spec-id (drop 1 (first paths)))]
                        (ssa/rewrite-node dgraph id new-id))
                    (throw (ex-info (format "BUG! Multiple refinement paths from '%s' to '%s'" from-spec-id to-spec-id)
                                    {:dgraph derivations :id id :deriv deriv :paths paths})))))))
          derivations)
         (assoc spec-info :derivations))))

(s/defn ^:private lower-refine-to :- SpecCtx
  [sctx :- SpecCtx]
  (update-vals sctx (partial lower-refine-to-in-spec sctx)))

;;;;;;;;;; Lower Refinement Constraints ;;;;;;;;;;;;;;;

(s/defn ^:private lower-refinement-constraints-in-spec :- [(s/one s/Keyword "spec-id") (s/one SpecInfo "spec-info")]
  [sctx :- SpecCtx
   [spec-id  {:keys [refines-to derivations] :as spec-info}] :- [(s/one s/Keyword "spec-id") (s/one SpecInfo "spec-info")]]
  (let [ctx (make-ssa-ctx sctx spec-info)
        scope (->> ctx :tenv (halite-envs/scope) keys set)]
    [spec-id
     (->> refines-to
          (reduce
           (fn [{:keys [derivations] :as spec-info} [target-id {:keys [expr inverted?]}]]
             (when inverted?
               (throw (ex-info "BUG! Lowering inverted refinements not yet supported" {:spec-info spec-info})))
             (let [[dgraph id] (ssa/form-to-ssa (assoc ctx :dgraph derivations) (list 'valid? expr))
                   result (-> spec-info
                              (assoc :derivations dgraph)
                              (update :constraints conj [(str target-id) id]))]
               (halite-rewriting/trace!
                {:op :add-constraint
                 :rule "lower-refinement-to-constraint"
                 :spec-id spec-id
                 :dgraph derivations
                 :dgraph' dgraph
                 :id' id
                 :form expr
                 :form' (ssa/form-from-ssa scope dgraph id)
                 :spec-info spec-info
                 :spec-info' result})
               result))
           spec-info))]))

(s/defn ^:private lower-refinement-constraints :- SpecCtx
  [sctx :- SpecCtx]
  (->> sctx
       (map (partial lower-refinement-constraints-in-spec sctx))
       (into {})))

;;;;;;;;;; Lowering Optionality ;;;;;;;;;;;;

(s/defn ^:private lower-no-value-comparison-expr
  [sctx :- SpecCtx, {:keys [dgraph] :as ctx} :- ssa/SSACtx, id [form htype]]
  (when (and (seq? form) (contains? #{'= 'not=} (first form)))
    (let [[op & arg-ids] form
          args (map-indexed vector (mapv (partial ssa/deref-id dgraph) arg-ids))
          no-value-args (filter #(= :Unset (second (second %))) args)
          no-value-literal? #(= :Unset (first (second %)))
          maybe-typed-args (filter #(and (halite-types/maybe-type? (second (second %))) (not= :Unset (second (second %)))) args)]
      (when (and (seq no-value-args) (empty? maybe-typed-args))
        (make-do
         (->> args (remove no-value-literal?) (map (comp (partial nth arg-ids) first)))
         (every? #(= :Unset (second (second %))) args))))))

;; FIXME: The current halite typing rules mean that the if generated from this rule
;; doesn't type check whenever the inner type is not a maybe type :( I think we'll need
;; to change them.
(s/defn ^:private lower-when-expr
  [sctx :- SpecCtx, {:keys [dgraph] :as ctx} :- ssa/SSACtx, id, [form htype]]
  (when (and (seq? form) (= 'when (first form)))
    (let [[_when pred-id body-id] form]
      (list 'if pred-id body-id :Unset))))

(s/defn ^:private lower-maybe-comparison-expr
  [sctx :- SpecCtx, {:keys [dgraph] :as ctx} :- ssa/SSACtx, id, [form htype]]
  (let [opt-arg? (fn [[form htype]]
                   (halite-types/maybe-type? htype))
        if-form? (fn [[form htype]]
                   (and (seq? form) (= 'if (first form))))]
    (when (and (seq? form) (contains? #{'= 'not=} (first form)))
      (let [[op & arg-ids] form
            args (map-indexed vector (mapv (partial ssa/deref-id dgraph) arg-ids))
            opt-args (filter #(and (opt-arg? (second %)) (not= :Unset (second (second %)))) args)
            no-value-args (filter #(= :Unset (second (second %))) args)
            no-value-literal? #(= :Unset (first (second %)))
            valued-args (filter #(not (opt-arg? (second %))) args)]
        (when (and (seq opt-args) (empty? (filter (comp if-form? second) opt-args)))
          (cond
            (and (seq no-value-args) (seq valued-args))
            (let [no-value-ids (set (map (comp (partial nth arg-ids) first) no-value-args))]
              (make-do (->> arg-ids (remove no-value-ids)) (= 'not= op)))

            (and (seq no-value-args) (= 1 (count opt-args)))
            (let [[i [form htype]] (first opt-args)
                  no-value-ids (->> no-value-args (remove no-value-literal?) (map (comp (partial nth arg-ids) first)))]
              (make-do no-value-ids
                       (list 'if (list '$value? (nth arg-ids i)) (= 'not= op) (not= 'not= op))))

            (seq no-value-args)
            (let [[i [form htype]] (first opt-args)
                  opt-arg-id (nth arg-ids i)
                  excluded-ids (->> no-value-args (map (comp (partial nth arg-ids) first)) (cons (nth arg-ids i)) set)
                  other-arg-ids (->> arg-ids (remove excluded-ids))]
              (make-do other-arg-ids
                       (list 'if (list '$value? opt-arg-id)
                             (= op 'not=)
                             (apply list op (nth arg-ids (ffirst no-value-args)) other-arg-ids))))

            (= 1 (count opt-args))
            (let [[i [form htype]] (first opt-args)
                  opt-arg-id (nth arg-ids i)
                  other-arg-ids (concat (take i arg-ids) (drop (inc i) arg-ids))]
              (make-do other-arg-ids
                       (list 'if (list '$value? opt-arg-id)
                             (apply list op (list '$value! opt-arg-id) other-arg-ids)
                             (= op 'not=))))

            (< 1 (count opt-args))
            (let [opt-pair (take 2 opt-args)
                  [i1 [form1 htype1]] (first opt-pair)
                  opt-id1 (nth arg-ids i1)
                  other-arg-ids (concat (take i1 arg-ids) (drop (inc i1) arg-ids))
                  [i2 [form2 htype2]] (second opt-pair)
                  opt-id2 (nth arg-ids i2)]
              (make-do other-arg-ids
                       (list 'if (list '$value? opt-id1)
                             (apply list op (list '$value! opt-id1) other-arg-ids)
                             (list 'if (list '$value? opt-id2)
                                   (= op 'not=)
                                   (not= op 'not=)))))))))))

(s/defn ^:private lower-maybe-comparisons :- SpecCtx
  [sctx :- SpecCtx]
  (rewrite-sctx sctx lower-maybe-comparison-expr))

(s/defn ^:private push-comparison-into-maybe-if-in-expr
  [sctx :- SpecCtx, {:keys [dgraph] :as ctx} :- ssa/SSACtx, id [form htype]]
  (let [maybe-if? (fn [[form htype]]
                    (and (seq? form)
                         (= 'if (first form))
                         (halite-types/maybe-type? htype)))]
    (when (and (seq? form) (contains? #{'= 'not=} (first form)))
      (let [[op & arg-ids] form
            args (mapv (partial ssa/deref-id dgraph) arg-ids)]
        (when-let [[i [[_if pred-id then-id else-id]]] (first (filter (comp maybe-if? second) (map-indexed vector args)))]
          (list 'if pred-id
                (apply list op (assoc (vec arg-ids) i then-id))
                (apply list op (assoc (vec arg-ids) i else-id))))))))

(s/defn ^:private push-comparisons-into-maybe-ifs :- SpecCtx
  [sctx :- SpecCtx]
  (rewrite-sctx sctx push-comparison-into-maybe-if-in-expr))

(s/defn ^:private push-if-value-into-if-in-expr
  [sctx :- SpecCtx, {:keys [dgraph] :as ctx} :- ssa/SSACtx, id [form htype]]
  (when (and (seq? form) (= 'if (first form)))
    (let [[_if val?-id then-id else-id] form
          [val?-form] (ssa/deref-id dgraph val?-id)]
      (when (and (seq? val?-form) (= '$value? (first val?-form)))
        (let [[_value? nested-if-id] val?-form
              [nested-if-form] (ssa/deref-id dgraph nested-if-id)
              value!-nested-if-id (ssa/find-form dgraph (list '$value! nested-if-id))]
          (when (and (seq? nested-if-form) (= 'if (first nested-if-form)))
            (let [[[_if nested-pred-id nested-then-id nested-else-id]] (ssa/deref-id dgraph nested-if-id)
                  rewrite-branch (fn [dgraph branch-id]
                                   (let [[_ branch-htype] (ssa/deref-id dgraph branch-id)]
                                     (if (halite-types/maybe-type? branch-htype)
                                       (let [[dgraph rewritten-then-id] (ssa/replace-in-expr dgraph then-id {nested-if-id branch-id})]
                                         (ssa/form-to-ssa (assoc ctx :dgraph dgraph)
                                                          (list 'if (list '$value? branch-id)
                                                                rewritten-then-id
                                                                else-id)))
                                       (if value!-nested-if-id
                                         (ssa/replace-in-expr dgraph then-id {value!-nested-if-id branch-id})
                                         [dgraph then-id]))))
                  [dgraph new-then-id] (rewrite-branch dgraph nested-then-id)
                  [dgraph new-else-id] (rewrite-branch dgraph nested-else-id)]
              (with-meta (list 'if nested-pred-id new-then-id new-else-id) {:dgraph dgraph}))))))))

(s/defn push-if-value-into-if :- SpecCtx
  [sctx :- SpecCtx]
  (rewrite-sctx sctx push-if-value-into-if-in-expr))

;;;;;;;;;; Combine semantics-preserving passes ;;;;;;;;;;;;;

(s/defn lower :- SpecCtx
  "Return a semantically equivalent spec context containing specs that have been reduced to
  a minimal subset of halite."
  [sctx :- SpecCtx]
  (->> sctx
       (lower-refine-to)
       (lower-refinement-constraints)
       (lower-valid?)
       (->>rewrite-sctx lower-when-expr)
       (fixpoint
        #(-> %
             (rewrite-sctx bubble-up-do-expr)
             (rewrite-sctx flatten-do-expr)
             (lower-instance-comparisons)
             (push-gets-into-ifs)
             (lower-maybe-comparisons)
             (rewrite-sctx lower-no-value-comparison-expr)
             (push-comparisons-into-maybe-ifs)
             (push-if-value-into-if)))
       (simplify)))

;;;;;;;;;; Semantics-modifying passes ;;;;;;;;;;;;;;;;

(s/defn ^:private eliminate-runtime-constraint-violations-in-expr
  [sctx :- SpecCtx, ctx :- ssa/SSACtx, id, [form htype]]
  (let [[dgraph guard-id] (validity-guard sctx ctx id)]
    (vary-meta (list 'if guard-id id false) assoc :dgraph dgraph)))

(s/defn eliminate-runtime-constraint-violations :- SpecCtx
  "Rewrite the constraints of every spec to eliminate the possibility of runtime constraint
  violations (but NOT runtime errors in general!). This is not a semantics-preserving operation.

  Every constraint expression <expr> is rewritten as (if <(validity-guard expr)> <expr> false)."
  [sctx :- SpecCtx]
  (->> sctx
      ;; Does this actually have to be done in dependency order? Why or why not?
       (halite-rewriting/rewrite-in-dependency-order*
        {:rule-name "eliminate-runtime-constraint-violations"
         :rewrite-fn eliminate-runtime-constraint-violations-in-expr
         :nodes :constraints}
        deps-via-instance-literal)
       (simplify)))

(s/defn ^:private cancel-get-of-instance-literal-in-spec :- SpecInfo
  "Replace (get {... :k <subexpr>} :k) with <subexpr>."
  [{:keys [derivations] :as spec-info} :- SpecInfo]
  (->
   (->> spec-info
        :derivations
        (filter (fn [[id [form htype]]]
                  (if (and (seq? form) (= 'get (first form)))
                    (let [[subform] (ssa/deref-id derivations (second form))]
                      (map? subform)))))
        (reduce
         (fn [dgraph [id [[_get subid var-kw] htype]]]
           (let [[inst-form] (ssa/deref-id dgraph subid)
                 field-node (ssa/deref-id dgraph (get inst-form var-kw))]
             (assoc dgraph id field-node)))
         derivations)
        (assoc spec-info :derivations))
   (ssa/prune-derivations false)))

(s/defn cancel-get-of-instance-literal :- SpecCtx
  "Replace (get {... :k <subexpr>} :k) with <subexpr>. Not semantics preserving, in that
  the possible runtime constraint violations of the instance literal are eliminated."
  [sctx :- SpecCtx]
  (update-vals sctx cancel-get-of-instance-literal-in-spec))

(s/defn ^:private eliminate-do-expr
  [sctx :- SpecCtx, {:keys [dgraph] :as ctx} :- ssa/SSACtx, id, [form htype]]
  (when (and (seq? form) (= '$do! (first form)))
    (let [tail-id (last form)]
      tail-id)))

(s/defn eliminate-dos :- SpecCtx
  [sctx :- SpecCtx]
  (rewrite-sctx sctx eliminate-do-expr))
