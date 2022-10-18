;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.transpile.lowering
  "Re-express halite specs in a minimal subset of halite, by compiling higher-level
  features down into lower-level features."
  (:require [clojure.set :as set]
            [com.viasat.halite.envs :as halite-envs]
            [com.viasat.halite.types :as halite-types]
            [com.viasat.halite.transpile.ssa :as ssa
             :refer [NodeId SSAGraph SpecInfo SpecCtx make-ssa-ctx]]
            [com.viasat.halite.transpile.rewriting :refer [rewrite-sctx ->>rewrite-sctx] :as halite-rewriting]
            [com.viasat.halite.transpile.simplify :refer [simplify always-evaluates?]]
            [com.viasat.halite.transpile.util :refer [fixpoint mk-junct make-do]]
            [loom.alg]
            [loom.graph]
            [schema.core :as s]))

;;;;;;;;; Bubble up and Flatten $do! ;;;;;;;;;;;;

;; We don't generally want to have to invent rewrite rules for
;; all the various forms as combined with $do!, so we'll write
;; some rules that move and combine $do! forms so as to minimize
;; the number of things we need to handle.

;; Here are some rules for 'bubbling up' dos:

;; For any of the halite 'builtins', along with get, =, not=, refine-to, valid?,
;; we can 'pull out' a $do! form.

(defn- do-form? [form] (and (seq? form) (= '$do! (first form))))

(def do!-fence-ops
  "The set of ops that a $do! form cannot bubble up out of without changing semantics.
  The $do! form is in here too, to avoid redundant rewrites."
  '#{if valid? $do! $value? $value!})

(s/defn ^:private first-nested-do :- (s/maybe [(s/one s/Int :index) (s/one ssa/Node :node)])
  [{:keys [ssa-graph] :as ctx}, form]
  (->> (rest form)
       (map-indexed #(vector (inc %1) (when (symbol? %2) (ssa/deref-id ssa-graph %2))))
       (remove (comp nil? second))
       (filter (comp do-form? first second))
       first))

(s/defn bubble-up-do-expr
  [{{:keys [ssa-graph] :as ctx} :ctx} :- halite-rewriting/RewriteFnCtx, id, [form htype]]
  (cond
    (map? form)
    (let [[match? done-ids inst]
          ,,(reduce
             (fn [[match? done-ids inst] [var-kw val-id]]
               (let [[val-form] (ssa/deref-id ssa-graph val-id)]
                 (if (do-form? val-form)
                   [true (into done-ids (butlast (rest val-form))) (assoc inst var-kw (last val-form))]
                   [match? done-ids (assoc inst var-kw val-id)])))
             [false [] (select-keys form [:$type])]
             (sort-by key (dissoc form :$type)))]
      (when match?
        (make-do done-ids inst)))

    ;; We *cannot* pull $do! forms out of valid? or the branches of if, but we can
    ;; pull it out of the predicates of ifs.
    (and (seq? form) (= 'if (first form)))
    (let [[_if pred-id then-id else-id] form
          pred (ssa/node-form (ssa/deref-id ssa-graph pred-id))]
      ;; We need to handle if and if-value as separate sub-cases.
      (if (do-form? pred)
        (make-do (butlast (rest pred))
                 (list 'if (last pred) then-id else-id))
        (when (and (seq? pred) (= '$value? (first pred)))
          (let [inner-id (second pred)
                inner-form (ssa/node-form (ssa/deref-id ssa-graph inner-id))]
            (when (do-form? inner-form)
              ;; Yikes... we need to rewrite the then branch such that ($value! ($do! ... v)) becomes ($value! v).
              ;; We don't need to rewrite the else branch, because in SSA form all the references to the tested
              ;; value are replaced with $no-value in the else branch.
              (let [[ssa-graph' new-then-id] (ssa/replace-in-expr ssa-graph then-id {inner-id (last inner-form)})]
                (vary-meta
                 (make-do
                  (butlast (rest inner-form))
                  (list 'if (list '$value? (last inner-form)) new-then-id else-id))
                 assoc :ssa-graph ssa-graph')))))))

    (and (seq? form) (not (contains? do!-fence-ops (first form))))
    (when-let [[i [nested-do-form]] (first-nested-do ctx form)]
      (let [side-effects (->> nested-do-form rest butlast)
            body (seq (assoc (vec form) i (last nested-do-form)))]
        (make-do side-effects body)))))

;; Finally, we can 'flatten' $do! forms.

(s/defn flatten-do-expr
  [{ctx :ctx} :- halite-rewriting/RewriteFnCtx, id, [form htype]]
  (when (do-form? form)
    (when-let [[i [nested-do-form]] (first-nested-do ctx form)]
      (concat (take i form) (rest nested-do-form) (drop (inc i) form)))))

;;;;;;;;; Comparisons known to be true/false, by argument type ;;;;;;;

(s/defn lower-comparison-exprs-with-incompatible-types
  [{{:keys [ssa-graph]} :ctx} :- halite-rewriting/RewriteFnCtx, id [form htype]]
  (when (and (seq? form) (#{'= 'not=} (first form)))
    (let [comparison-op (first form)
          arg-ids (rest form)
          arg-types (set (map (comp second (partial ssa/deref-id ssa-graph)) arg-ids))
          compatible? (->> (rest arg-types)
                           (interleave (butlast arg-types))
                           (partition 2)
                           (every? #(or (halite-types/subtype? (first %) (second %))
                                        (halite-types/subtype? (second %) (first %)))))]
      (when-not compatible?
        (= comparison-op 'not=)))))

;;;;;;;;; Instance Comparison Lowering ;;;;;;;;

(s/defn lower-instance-comparison-expr
  [{{:keys [ssa-graph senv] :as ctx} :ctx} :- halite-rewriting/RewriteFnCtx, id, [form htype]]
  (when (and (seq? form) (#{'= 'not=} (first form)))
    (let [comparison-op (first form)
          logical-op (if (= comparison-op '=) 'and 'or)
          arg-ids (rest form)
          arg-types (set (map (comp second (partial ssa/deref-id ssa-graph)) arg-ids))]
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

(s/defn lower-instance-comparisons :- SpecCtx
  [sctx :- SpecCtx]
  (rewrite-sctx sctx lower-instance-comparison-expr))

;;;;;;;;; Push gets inside instance-valued ifs ;;;;;;;;;;;

(s/defn push-gets-into-ifs-expr
  [{{:keys [ssa-graph] :as ctx} :ctx} :- halite-rewriting/RewriteFnCtx, id, [form htype]]
  (when (and (seq? form) (= 'get (first form)))
    (let [[_get arg-id var-kw] form
          [subform htype] (ssa/deref-id ssa-graph (second form))]
      (when (and (seq? subform) (= 'if (first subform)) (halite-types/spec-type? htype))
        (let [[_if pred-id then-id else-id] subform
              then-node (ssa/deref-id ssa-graph then-id), then-type (ssa/node-type then-node)
              else-node (ssa/deref-id ssa-graph else-id), else-type (ssa/node-type else-node)]
          (list 'if pred-id
                (if (= :Nothing then-type)
                  then-id
                  (list 'get then-id var-kw))
                (if (= :Nothing else-type)
                  else-id
                  (list 'get else-id var-kw))))))))

(s/defn push-gets-into-ifs :- SpecCtx
  [sctx :- SpecCtx]
  (rewrite-sctx sctx push-gets-into-ifs-expr))

;;;;;;;;; Lower valid? ;;;;;;;;;;;;;;;;;

(s/defn ^:private deps-via-instance-literal :- #{halite-types/NamespacedKeyword}
  [spec-info :- SpecInfo]
  (->> spec-info
       :ssa-graph
       :dgraph
       vals
       (map (fn [[form htype]] (when (map? form) (:$type form))))
       (remove nil?)
       (set)))

(declare replace-in-expr)

(defn- fresh-symbol [replacements sym]
  (loop [n 1]
    (let [sym' (symbol (str (name sym) "$" n))]
      (if (contains? replacements sym')
        (recur (inc n))
        sym'))))

(defn- replace-in-let-expr
  [replacements bindings expr]
  (let [[replacements bindings]
        ,,(reduce
           (fn [[replacements bindings] [sym expr]]
             (let [expr' (replace-in-expr replacements expr)
                   replacements (if (contains? replacements sym)
                                  (assoc replacements sym (fresh-symbol replacements sym))
                                  replacements)]
               [replacements
                (conj bindings (get replacements sym sym) expr')]))
           [replacements []]
           (partition 2 bindings))]
    (list 'let bindings (replace-in-expr replacements expr))))

(defn- replace-in-if-expr
  [replacements [_if pred-expr then-expr else-expr]]
  (if (and (seq? pred-expr)
           (= '$value? (first pred-expr))
           (= '$no-value (replacements (second pred-expr))))
    (replace-in-expr replacements else-expr)
    (list 'if
          (replace-in-expr replacements pred-expr)
          (replace-in-expr replacements then-expr)
          (replace-in-expr replacements else-expr))))

(defn- replace-in-expr
  [replacements expr]
  (cond
    (or (integer? expr) (boolean? expr) (string? expr) (keyword? expr)) expr
    (symbol? expr) (get replacements expr expr)
    (seq? expr) (let [[op & args] expr]
                  (condp = op
                    'let (replace-in-let-expr replacements (first args) (last args))
                    'if (replace-in-if-expr replacements expr)
                    (apply list op (map (partial replace-in-expr replacements) args))))
    (map? expr) (update-vals expr (partial replace-in-expr replacements))
    :else (throw (ex-info "BUG! Invalid expression" {:expr expr}))))

(declare validity-guard)

(s/defn ^:private validity-guard-inst
  [sctx :- SpecCtx, ctx :- ssa/SSACtx, inst]
  (let [{:keys [spec-vars ssa-graph constraints] :as spec-info} (sctx (:$type inst))
        inst-entries (dissoc inst :$type)
        scope (->> spec-vars keys (map symbol) set)
        inlined-constraints (->> constraints
                                 (map (fn [[cname id]]
                                        (binding [ssa/*hide-non-halite-ops* false]
                                          (ssa/form-from-ssa scope ssa-graph id))))
                                 (mk-junct 'and)
                                 (replace-in-expr
                                  (-> spec-vars (update-vals (constantly '$no-value)) (merge inst-entries) (update-keys symbol))))
        sub-guards (->> inst-entries
                        (vals)
                        (map (partial validity-guard sctx ctx))
                        (remove true?)
                        (mk-junct 'and))
        unused-field-vals (reduce
                           disj
                           (->> inst-entries vals set)
                           (->> inlined-constraints
                                (tree-seq coll? seq)
                                (filter symbol?)))
        result (make-do unused-field-vals inlined-constraints)]
    (if (true? sub-guards)
      result
      (list 'if sub-guards result false))))

(s/defn ^:private validity-guard-if
  [sctx :- SpecCtx, ctx :- ssa/SSACtx, [_if pred-id then-id else-id]]
  (let [pred-guard (validity-guard sctx ctx pred-id)
        then-guard (validity-guard sctx ctx then-id)
        else-guard (validity-guard sctx ctx else-id)
        branches-guard (if (and (true? then-guard) (true? else-guard))
                         true
                         (list 'if pred-id then-guard else-guard))]
    (if (true? pred-guard)
      branches-guard
      (list 'if pred-guard
            branches-guard
            false))))

(s/defn ^:private validity-guard-when
  [sctx :- SpecCtx, ctx :- ssa/SSACtx, [_when pred-id then-id]]
  (let [pred-guard (validity-guard sctx ctx pred-id)
        then-guard (validity-guard sctx ctx then-id)]
    (if (true? pred-guard)
      then-guard
      (list 'if pred-guard
            then-guard
            false))))

(s/defn ^:private validity-guard-app
  [sctx :- SpecCtx, ctx :- ssa/SSACtx, args]
  (->> args
       (map (partial validity-guard sctx ctx))
       (remove true?)
       (mk-junct 'and)))

(s/defn validity-guard
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
  [sctx :- SpecCtx, {:keys [ssa-graph] :as ctx} :- ssa/SSACtx, id :- NodeId]
  (let [[form htype :as node] (ssa/deref-id ssa-graph id)]
    (cond
      (or (int? form) (boolean? form) (= :Unset form) (symbol? form) (string? form)) 'true
      (map? form) (validity-guard-inst sctx ctx form)
      (seq? form) (let [[op & args] form]
                    (condp = op
                      'get (validity-guard sctx ctx (first args))
                      'refine-to (validity-guard sctx ctx (first args))
                      'if (validity-guard-if sctx ctx form)
                      'when (validity-guard-when sctx ctx form)
                      (validity-guard-app sctx ctx args)))
      :else (throw (ex-info "BUG! Cannot compute validity-guard" {:ssa-graph ssa-graph :id id :node node})))))

(s/defn ^:private lower-valid?-expr
  [{sctx :sctx, ctx :ctx} :- halite-rewriting/RewriteFnCtx, id, [form htype]]
  (when (and (seq? form) (= 'valid? (first form)))
    (let [[_valid? expr-id] form]
      (validity-guard sctx ctx expr-id))))

(s/defn lower-valid? :- SpecCtx
  [sctx :- SpecCtx]
  (halite-rewriting/rewrite-in-dependency-order*
   {:rule-name "lower-valid?"
    :rewrite-fn lower-valid?-expr
    :nodes :all}
   deps-via-instance-literal
   sctx))

;;;;;;;;;; Lower refine-to ;;;;;;;;;;;;;;;

(s/defn lower-refine-to-expr
  [rgraph, {{:keys [ssa-graph] :as ctx} :ctx sctx :sctx} :- halite-rewriting/RewriteFnCtx, id, [form htype]]
  (when-let [[_ expr-id to-spec-id] (and (seq? form) (= 'refine-to (first form)) form)]
    (let [[_ from-htype] (ssa/deref-id ssa-graph expr-id)]
      (when-let [from-spec-id (halite-types/spec-id from-htype)]
        (->> (loom.alg/shortest-path rgraph from-spec-id to-spec-id)
             (partition 2 1)
             (reduce (fn [out-form [from-spec-id to-spec-id]]
                       (let [{:keys [spec-vars refines-to] :as spec-info} (sctx from-spec-id)
                             refine-expr-id (:expr (get refines-to to-spec-id))
                             refine-expr (ssa/form-from-ssa spec-info refine-expr-id)
                             bindings (vec (mapcat (fn [var-kw] [(symbol var-kw)
                                                                 (list 'get out-form var-kw)])
                                                   (keys spec-vars)))]
                         (list 'let bindings refine-expr)))
                     expr-id))))))

(s/defn lower-refine-to :- SpecCtx
  [sctx :- SpecCtx]
  (let [g (loom.graph/digraph (update-vals sctx (comp keys :refines-to)))]
    (halite-rewriting/rewrite-sctx* {:rule-name "lower-refine-to"
                                     :rewrite-fn (partial lower-refine-to-expr g),
                                     :nodes :all}
                                    sctx)))

(s/defn push-refine-to-into-if
  [{{:keys [ssa-graph] :as ctx} :ctx} :- halite-rewriting/RewriteFnCtx, id [form htype]]
  (when-let [[_ subexpr-id to-spec-id] (and (seq? form) (= 'refine-to (first form)) form)]
    (let [subexpr-node (ssa/deref-id ssa-graph subexpr-id), subexpr (ssa/node-form subexpr-node)]
      (when-let [[_ pred-id then-id else-id] (and (seq? subexpr) (= 'if (first subexpr)) subexpr)]
        (let [then (ssa/deref-id ssa-graph then-id), then-type (ssa/node-type then)
              else (ssa/deref-id ssa-graph else-id), else-type (ssa/node-type else)]
          (list 'if pred-id
                (if (= :Nothing then-type)
                  then-id
                  (list 'refine-to then-id to-spec-id))
                (if (= :Nothing else-type)
                  else-id
                  (list 'refine-to else-id to-spec-id))))))))

;;;;;;;;;; Lower Refinement Constraints ;;;;;;;;;;;;;;;

(s/defn ^:private lower-refinement-constraints-in-spec :- [(s/one s/Keyword "spec-id") (s/one SpecInfo "spec-info")]
  [sctx :- SpecCtx
   [spec-id  {:keys [refines-to ssa-graph] :as spec-info}] :- [(s/one s/Keyword "spec-id") (s/one SpecInfo "spec-info")]]
  (let [ctx (make-ssa-ctx sctx spec-info)
        scope (->> ctx :tenv (halite-envs/scope) keys set)]
    [spec-id
     (->> refines-to
          (reduce
           (fn [{:keys [ssa-graph] :as spec-info} [target-id {:keys [expr inverted?]}]]
             (when inverted?
               (throw (ex-info "BUG! Lowering inverted refinements not yet supported" {:spec-info spec-info})))
             (let [[ssa-graph id] (ssa/form-to-ssa (assoc ctx :ssa-graph ssa-graph) (list 'valid? expr))
                   result (-> spec-info
                              (assoc :ssa-graph ssa-graph)
                              (update :constraints conj [(str target-id) id]))]
               (halite-rewriting/trace!
                sctx
                {:op :add-constraint
                 :rule "lower-refinement-constraints"
                 :spec-id spec-id
                 :id' id
                 :result expr
                 :spec-info spec-info
                 :spec-info' result})
               result))
           spec-info))]))

(s/defn lower-refinement-constraints :- SpecCtx
  [sctx :- SpecCtx]
  (->> sctx
       (map (partial lower-refinement-constraints-in-spec sctx))
       (into {})))

;;;;;;;;;; Lowering Optionality ;;;;;;;;;;;;

(s/defn lower-no-value-comparison-expr
  [{{:keys [ssa-graph] :as ctx} :ctx} :- halite-rewriting/RewriteFnCtx, id, [form htype]]
  (when (and (seq? form) (contains? #{'= 'not=} (first form)))
    (let [[op & arg-ids] form
          args (map-indexed vector (mapv (partial ssa/deref-id ssa-graph) arg-ids))
          no-value-args (filter #(= :Unset (second (second %))) args)
          no-value-literal? #(= :Unset (first (second %)))
          maybe-typed-args (filter #(and (halite-types/maybe-type? (second (second %))) (not= :Unset (second (second %)))) args)]
      (when (and (seq no-value-args) (empty? maybe-typed-args))
        (make-do
         (->> args (remove no-value-literal?) (map (comp (partial nth arg-ids) first)))
         (every? #(= :Unset (second (second %))) args))))))

(s/defn lower-no-value-comparisons :- SpecCtx
  [sctx :- SpecCtx]
  (rewrite-sctx sctx lower-no-value-comparison-expr))

(s/defn ^:private lower-when-expr
  [{ctx :ctx} :- halite-rewriting/RewriteFnCtx, id, [form htype]]
  (when (and (seq? form) (= 'when (first form)))
    (let [[_when pred-id body-id] form]
      (list 'if pred-id body-id :Unset))))

(s/defn lower-when :- SpecCtx
  [sctx :- SpecCtx]
  (rewrite-sctx sctx lower-when-expr))

(s/defn lower-maybe-comparison-expr
  [{{:keys [ssa-graph] :as ctx} :ctx} :- halite-rewriting/RewriteFnCtx, id, [form htype]]
  (let [opt-arg? (fn [[form htype]]
                   (halite-types/maybe-type? htype))
        if-form? (fn [[form htype]]
                   (and (seq? form) (= 'if (first form))))]
    (when (and (seq? form) (contains? #{'= 'not=} (first form)))
      (let [[op & arg-ids] form
            args (map-indexed vector (mapv (partial ssa/deref-id ssa-graph) arg-ids))
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

(s/defn lower-maybe-comparisons :- SpecCtx
  [sctx :- SpecCtx]
  (rewrite-sctx sctx lower-maybe-comparison-expr))

(s/defn push-comparison-into-nonprimitive-if-in-expr
  [{{:keys [ssa-graph] :as ctx} :ctx} :- halite-rewriting/RewriteFnCtx, id, [form htype]]
  (let [nonprimitive-if? (fn [[form htype]]
                           (and (seq? form)
                                (= 'if (first form))
                                (not (contains? #{:Boolean :Integer :String} htype))))]
    (when (and (seq? form) (contains? #{'= 'not=} (first form)))
      (let [[op & arg-ids] form
            args (mapv (partial ssa/deref-id ssa-graph) arg-ids)]
        (when-let [[i [[_if pred-id then-id else-id]]] (first (filter (comp nonprimitive-if? second) (map-indexed vector args)))]
          (let [then-node (ssa/deref-id ssa-graph then-id), then-type (ssa/node-type then-node)
                else-node (ssa/deref-id ssa-graph else-id), else-type (ssa/node-type else-node)]
            (list 'if pred-id
                  (if (= :Nothing then-type)
                    then-id
                    (apply list op (assoc (vec arg-ids) i then-id)))
                  (if (= :Nothing else-type)
                    else-id
                    (apply list op (assoc (vec arg-ids) i else-id))))))))))

(s/defn push-if-value-into-if-in-expr
  [{{:keys [ssa-graph] :as ctx} :ctx} :- halite-rewriting/RewriteFnCtx, id, [form htype]]
  (when (and (seq? form) (= 'if (first form)))
    (let [[_if val?-id then-id else-id] form
          [val?-form] (ssa/deref-id ssa-graph val?-id)]
      (when (and (seq? val?-form) (= '$value? (first val?-form)))
        (let [[_value? nested-if-id] val?-form
              [nested-if-form] (ssa/deref-id ssa-graph nested-if-id)]
          (when (and (seq? nested-if-form) (= 'if (first nested-if-form)))
            (let [[_if nested-pred-id nested-then-id nested-else-id] nested-if-form
                  value!-nested-if-id (ssa/find-form ssa-graph (list '$value! nested-if-id))
                  #_(if ($value? (if <nested-pred-id>
                                   <nested-then-id>
                                   <nested-else-id>) :- <nested-if-id>) :- <val?-id>
                        <then-id>
                        <else-id>)
                  rewrite-branch (fn [ssa-graph branch-id]
                                   (let [[_ branch-htype] (ssa/deref-id ssa-graph branch-id)]
                                     (cond
                                       (halite-types/strict-maybe-type? branch-htype)
                                       (let [[ssa-graph rewritten-then-id] (ssa/replace-in-expr ssa-graph then-id {nested-if-id branch-id})]
                                         (ssa/form-to-ssa (assoc ctx :ssa-graph ssa-graph)
                                                          (list 'if (list '$value? branch-id)
                                                                rewritten-then-id
                                                                else-id)))

                                       (= :Unset branch-htype)
                                       [ssa-graph else-id]

                                       :else
                                       (if value!-nested-if-id
                                         (ssa/replace-in-expr ssa-graph then-id {value!-nested-if-id branch-id})
                                         [ssa-graph then-id]))))
                  [ssa-graph new-then-id] (rewrite-branch ssa-graph nested-then-id)
                  [ssa-graph new-else-id] (rewrite-branch ssa-graph nested-else-id)]
              (with-meta (list 'if nested-pred-id new-then-id new-else-id) {:ssa-graph ssa-graph}))))))))

(s/defn push-if-value-into-if :- SpecCtx
  [sctx :- SpecCtx]
  (rewrite-sctx sctx push-if-value-into-if-in-expr))

;;;;;;;;;; Combine semantics-preserving passes ;;;;;;;;;;;;;

(s/defn lower :- SpecCtx
  "Return a semantically equivalent spec context containing specs that have been reduced to
  a minimal subset of halite."
  [sctx :- SpecCtx]
  (-> sctx
      (lower-refine-to)
      (lower-refinement-constraints)
      (lower-valid?)
      (lower-when)
      (halite-rewriting/rewrite-reachable-sctx
       [(halite-rewriting/rule bubble-up-do-expr)
        (halite-rewriting/rule flatten-do-expr)
        (halite-rewriting/rule lower-instance-comparison-expr)
        (halite-rewriting/rule push-gets-into-ifs-expr)
        (halite-rewriting/rule lower-maybe-comparison-expr)
        (halite-rewriting/rule lower-no-value-comparison-expr)
        (halite-rewriting/rule push-comparison-into-nonprimitive-if-in-expr)
        (halite-rewriting/rule push-if-value-into-if-in-expr)])
      (simplify)))

;;;;;;;;;; Semantics-modifying passes ;;;;;;;;;;;;;;;;

(s/defn ^:private eliminate-runtime-constraint-violations-in-expr
  [{:keys [sctx ctx]} :- halite-rewriting/RewriteFnCtx, id, [form htype]]
  (let [guard (validity-guard sctx ctx id)]
    (when (not (true? guard))
      (list 'if guard id false))))

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
        deps-via-instance-literal)))

(s/defn cancel-get-of-instance-literal-expr
  [{{:keys [ssa-graph]} :ctx} :- halite-rewriting/RewriteFnCtx, id, [form htype]]
  (when-let [[_get coll-id var-kw] (and (seq? form) (= 'get (first form)) form)]
    (let [[coll] (ssa/deref-id ssa-graph coll-id)]
      (when (map? coll)
        (get coll var-kw '$no-value)))))

(s/defn cancel-get-of-instance-literal :- SpecCtx
  "Replace (get {... :k <subexpr>} :k) with <subexpr>. Not semantics preserving, in that
  the possible runtime constraint violations of the instance literal are eliminated."
  [sctx :- SpecCtx]
  (rewrite-sctx sctx cancel-get-of-instance-literal-expr))

(s/defn eliminate-do-expr
  [rctx :- halite-rewriting/RewriteFnCtx, id, [form htype]]
  (when (and (seq? form) (= '$do! (first form)))
    (let [tail-id (last form)]
      tail-id)))

(s/defn eliminate-dos :- SpecCtx
  [sctx :- SpecCtx]
  (rewrite-sctx sctx eliminate-do-expr))

(defn- find-error-ids [ssa-graph]
  (->> ssa-graph
       :dgraph
       (filter (fn [[id [form]]] (and (seq? form) (= 'error (first form)))))
       (map first)))

(s/defn ^:private add-error-guards-as-constraints
  [sctx :- SpecCtx {:keys [ssa-graph constraints] :as spec-info} :- SpecInfo]
  (let [guards (ssa/compute-guards ssa-graph (set (map second constraints)))
        ctx (ssa/make-ssa-ctx sctx spec-info)]
    (->> (find-error-ids ssa-graph)
         (reduce
          (fn [{:keys [ssa-graph] :as spec-info} error-id]
            (let [[[_error subexpr-id]] (ssa/deref-id ssa-graph error-id)
                  ;; This bit currently depends on the ssa namespace not allowing (error ...) forms
                  ;; with anything other than string literals inside.
                  [message] (ssa/deref-id ssa-graph subexpr-id)
                  guard (guards error-id)
                  guard-form (->> guard (map (comp (partial mk-junct 'and) seq)) (mk-junct 'or))
                  [ssa-graph' guard-id] (ssa/form-to-ssa (assoc ctx :ssa-graph ssa-graph) guard-form)
                  cid (ssa/negated ssa-graph' guard-id)
                  spec-info' (-> spec-info
                                 (assoc :ssa-graph ssa-graph')
                                 (update :constraints conj [message cid]))]
              (halite-rewriting/trace!
               sctx
               {:op :add-constraint
                :rule "add-error-guards-as-constraints"
                :id' cid
                :result (list 'not guard-form)
                :spec-info spec-info
                :spec-info' spec-info'})
              spec-info'))
          spec-info))))

(s/defn ^:private drop-branches-containing-unguarded-errors-rewrite-fn
  [unguarded-error? {{:keys [ssa-graph]} :ctx} :- halite-rewriting/RewriteFnCtx, id, [form htype]]
  (when (and (seq? form) (= 'when (first form)))
    (throw (ex-info "BUG! Applied drop-branches-containing-ungaurded-errors rule without lowering 'when'"
                    {:ssa-graph ssa-graph :id id})))
  (when (and (seq? form) (= 'if (first form)))
    (let [[_if pred-id then-id else-id] form
          then-error? (unguarded-error? ssa-graph then-id)
          else-error? (unguarded-error? ssa-graph else-id)]
      (cond
        (and then-error? else-error?) (list 'error "both branches error")
        then-error? else-id
        else-error? then-id
        :else nil))))

(s/defn ^:private drop-branches-containing-unguarded-errors
  [sctx :- SpecCtx spec-id {:keys [ssa-graph constraints] :as spec-info}]
  ;; TODO: This is dumb. Refactor rewriting machinery so that we can compute spec-level info that is passed to rewriting rule.
  (let [guards (ssa/compute-guards ssa-graph (set (map second constraints)))
        error-ids (set (find-error-ids ssa-graph))
        unguarded-error? (fn [ssa-graph id]
                           (let [unconditionally-reachable (ssa/reachable-nodes ssa-graph id {:conditionally? false})]
                             (seq (set/intersection error-ids unconditionally-reachable))))]
    (assoc
     sctx spec-id
     (halite-rewriting/rewrite-spec
      {:rule-name "drop-branches-containing-unguarded-errors"
       :rewrite-fn (partial drop-branches-containing-unguarded-errors-rewrite-fn unguarded-error?)
       :nodes :all}
      sctx
      spec-id
      spec-info))))

(s/defn eliminate-error-forms :- SpecCtx
  [sctx :- SpecCtx]
  (let [sctx (update-vals sctx (partial add-error-guards-as-constraints sctx))]
    (fixpoint #(reduce-kv drop-branches-containing-unguarded-errors % %) sctx)))

(s/defn ^:private instance-compatible-type? :- s/Bool
  "Returns true if there exists an expression of type htype that could evaluate to an instance, and false otherwise."
  [htype :- halite-types/HaliteType]
  (or (boolean (#{:Any :Value} htype))
      (and (vector? htype) (= :Instance (first htype)))
      (and (halite-types/maybe-type? htype)
           (let [inner (halite-types/no-maybe htype)]
             (and (vector? inner) (= :Instance (first inner)))))))

(s/defn ^:private rewrite-instance-valued-do-child
  [{:keys [sctx ctx] :as rctx} :- halite-rewriting/RewriteFnCtx, id]
  (let [{:keys [ssa-graph]} ctx
        node (ssa/deref-id ssa-graph id), form (ssa/node-form node), htype (ssa/node-type node)
        rewrite-child-exprs (fn [child-exprs]
                              (-> child-exprs
                                  (->> (remove #(->> % (ssa/deref-id ssa-graph) ssa/node-form (always-evaluates? ssa-graph)))
                                       (mapcat #(let [node (ssa/deref-id ssa-graph %)
                                                      form (ssa/node-form node)
                                                      htype (ssa/node-type node)]
                                                  (if (instance-compatible-type? htype)
                                                    (let [r (rewrite-instance-valued-do-child rctx %)]
                                                      (if (and (seq? r) (= '$do! (first r)))
                                                        (rest r)
                                                        [r]))
                                                    [%]))))
                                  (make-do true)))]
    (when-not (instance-compatible-type? htype)
      (throw (ex-info "BUG! Called rewrite-instance-valued-do-child with expr that can never evaluate to an instance."
                      {:ssa-graph ssa-graph :id id :form form :htype htype})))
    (cond
      (symbol? form) true
      (map? form) (-> form
                      (dissoc :$type)
                      (vals)
                      (rewrite-child-exprs))
      (seq? form) (let [[op & args] form]
                    (cond
                      (= 'if op) (let [[pred-id then-id else-id] args
                                       then-type (ssa/node-type (ssa/deref-id ssa-graph then-id))
                                       else-type (ssa/node-type (ssa/deref-id ssa-graph else-id))]
                                   (if (and (not (instance-compatible-type? then-type))
                                            (not (instance-compatible-type? else-type)))
                                     id
                                     (list 'if pred-id
                                           (cond->> then-id
                                             (instance-compatible-type? then-type)
                                             (rewrite-instance-valued-do-child rctx))
                                           (cond->> else-id
                                             (instance-compatible-type? else-type)
                                             (rewrite-instance-valued-do-child rctx)))))

                      (#{'get '$value!} op) (rewrite-instance-valued-do-child rctx (first args))

                      (= '$do! op) (rewrite-child-exprs args)

                      (#{'when 'refine-to} op) (throw (ex-info "BUG! Expected this operator to be lowered away by now."
                                                               {:ssa-graph ssa-graph :id id :form form :op op}))

                      :else (throw (ex-info "BUG! Unrecognized instance-valued form"
                                            {:ssa-graph ssa-graph :id id :form form}))))
      :else (throw (ex-info "BUG! Unrecognized instance-valued form"
                            {:ssa-graph ssa-graph :id id :form form})))))

(s/defn eliminate-unused-instance-valued-exprs-in-do-expr
  [{:keys [sctx ctx] :as rctx} :- halite-rewriting/RewriteFnCtx, id, [form htype]]
  (let [ssa-graph (:ssa-graph ctx)]
    (when (and (seq? form) (= '$do! (first form)))
      (let [child-htypes (mapv #(ssa/node-type (ssa/deref-id ssa-graph %)) (rest form))]
        (when (some instance-compatible-type? child-htypes)
          (make-do
           (mapv (fn [child htype]
                   (cond->> child
                     (instance-compatible-type? htype) (rewrite-instance-valued-do-child rctx)))
                 (butlast (rest form)) child-htypes)
           (last form)))))))

(s/defn eliminate-unused-instance-valued-exprs-in-dos
  "Rewrite every instance-valued non-tail child expression of a $do! as an expression that
  evaluates to:
    true exactly when the rewritten expression evaluates
    a runtime error exactly when the rewritten expression evaluates to a runtime error

  Note that instance literal constraints are ignored! This pass should only be applied
  after all constraints implied by instance literals have been made explicit."
  [sctx :- SpecCtx]
  (rewrite-sctx sctx eliminate-unused-instance-valued-exprs-in-do-expr))

(s/defn ^:private rewrite-no-value-do-child
  [{:keys [ctx] :as rctx} :- halite-rewriting/RewriteFnCtx, id]
  (let [{:keys [ssa-graph]} ctx
        node (ssa/deref-id ssa-graph id), form (ssa/node-form node), htype (ssa/node-type node)]
    (when-not (halite-types/subtype? :Unset htype)
      (throw (ex-info "BUG! Called rewrite-no-value-do-child with expr that could not possibly evaluate to $no-value"
                      {:ssa-graph ssa-graph :id id :form form :htype htype})))

    (cond
      (symbol? form) form
      (= :Unset form) true
      (seq? form) (let [[op & args] form]
                    (cond
                      (= 'if op) (let [[pred-id then-id else-id] args]
                                   (list 'if pred-id
                                         (cond->> then-id
                                           (halite-types/maybe-type? (ssa/node-type (ssa/deref-id ssa-graph then-id)))
                                           (rewrite-no-value-do-child rctx))
                                         (cond->> else-id
                                           (halite-types/maybe-type? (ssa/node-type (ssa/deref-id ssa-graph else-id)))
                                           (rewrite-no-value-do-child rctx))))
                      (= '$do! op) (apply list '$do! (mapv #(rewrite-no-value-do-child rctx %) args))
                      :else id))
      :else id)))

(s/defn eliminate-unused-no-value-exprs-in-do-expr
  [rctx :- halite-rewriting/RewriteFnCtx id [form htype]]
  (let [ssa-graph (:ssa-graph (:ctx rctx))]
    (when (and (seq? form) (= '$do! (first form)))
      (let [child-htypes (mapv #(ssa/node-type (ssa/deref-id ssa-graph %)) (rest form))]
        (when (some #(halite-types/subtype? :Unset %) child-htypes)
          (make-do
           (mapv (fn [child htype]
                   (cond->> child
                     (halite-types/maybe-type? htype) (rewrite-no-value-do-child rctx)))
                 (butlast (rest form)) child-htypes)
           (last form)))))))
