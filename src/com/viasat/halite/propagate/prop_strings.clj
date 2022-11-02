;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.prop-strings
  "Propagate support for strings.

  The strategy is to simplify all string-related expressions as much as possible,
  and then replace all string variables and string-related expressions with boolean
  variables that represent the results of comparisons."
  (:require [clojure.set :as set]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.types :as types]
            [com.viasat.halite.propagate.prop-choco :as prop-choco]
            [com.viasat.halite.transpile.lowering :as lowering]
            [com.viasat.halite.transpile.ssa :as ssa]
            [com.viasat.halite.transpile.rewriting :as rewriting]
            [com.viasat.halite.transpile.simplify :as simplify]
            [com.viasat.halite.transpile.util :as util]
            [loom.alg :as loom-alg]
            [loom.graph :as loom-graph]
            [loom.label :as loom-label]
            [loom.derived :as loom-derived]
            [schema.core :as s])
  (:import [org.chocosolver.solver.exception ContradictionException]))

;;;;; Bounds, extended with Strings ;;;;;
(s/defschema AtomBound
  (s/cond-pre
   s/Int
   s/Bool
   s/Str
   (s/enum :Unset :String)
   {:$in (s/cond-pre
          #{(s/cond-pre s/Int s/Bool s/Str (s/enum :Unset :String))}
          [(s/one s/Int :lower) (s/one s/Int :upper) (s/optional (s/enum :Unset) :Unset)])}))

(s/defschema StringBound
  (s/cond-pre s/Str (s/enum :Unset :String) {:$in #{(s/cond-pre s/Str (s/enum :String :Unset))}}))

(s/defschema SpecBound
  {types/BareKeyword AtomBound})

;;;;;;; String expression simplification ;;;;;;;;

;; The strategy for lowering strings depends on the only string-valued expressions
;; being string-type variables, string literals, and flat (str ...) forms.
;; These rewrite rules simplify string-valued expressions of other forms.

;;    (= ... (if p a b) ...)
;;    (if p a b) :- :String
;; ===========================
;;  (if p (= ... a ...) (= ... b ...))
(s/defn ^:private push-comparison-into-string-valued-if
  [{{:keys [ssa-graph] :as ctx} :ctx} :- rewriting/RewriteFnCtx, id, [form htype]]
  (let [string-if? (fn [[form htype]]
                     (and (seq? form) (= 'if (first form)) (= :String htype)))]
    (when (and (seq? form) (contains? #{'= 'not=} (first form)))
      (let [[op & arg-ids] form
            args (mapv (partial ssa/deref-id ssa-graph) arg-ids)]
        (when-let [[i [[_if pred-id then-id else-id]]] (first (filter (comp string-if? second) (map-indexed vector args)))]
          (let [then-node (ssa/deref-id ssa-graph then-id), then-type (ssa/node-type then-node)
                else-node (ssa/deref-id ssa-graph else-id), else-type (ssa/node-type else-node)]
            (list 'if pred-id
                  (if (= :Nothing then-type)
                    then-id
                    (apply list op (assoc (vec arg-ids) i then-id)))
                  (if (= :Nothing else-type)
                    else-id
                    (apply list op (assoc (vec arg-ids) i else-id))))))))))

(s/defn ^:private string-compatible-type? :- s/Bool
  "Returns true if there exists an expression of type htype that could evaluate to a String, and false otherwise."
  [htype :- types/HaliteType]
  (or (boolean (#{:Any :Value} htype)) (= :String (types/no-maybe htype))))

(s/defn ^:private rewrite-string-valued-do-child
  [{:keys [sctx ctx] :as rctx} :- rewriting/RewriteFnCtx, id]
  (let [{:keys [ssa-graph]} ctx
        node (ssa/deref-id ssa-graph id), form (ssa/node-form node), htype (ssa/node-type node)
        rewrite-child-exprs (fn [child-exprs]
                              (-> child-exprs
                                  (->> (remove #(->> % (ssa/deref-id ssa-graph) ssa/node-form (simplify/always-evaluates? ssa-graph)))
                                       (mapcat #(let [node (ssa/deref-id ssa-graph %)
                                                      form (ssa/node-form node)
                                                      htype (ssa/node-type node)]
                                                  (if (string-compatible-type? htype)
                                                    (let [r (rewrite-string-valued-do-child rctx %)]
                                                      (if (and (seq? r) (= '$do! (first r)))
                                                        (rest r)
                                                        [r]))
                                                    [%]))))
                                  (util/make-do true)))]
    (when-not (string-compatible-type? htype)
      (throw (ex-info "BUG! Called rewrite-string-valued-do-child with expr that can never evaluate to a string."
                      {:ssa-graph ssa-graph :id id :form form :htype htype})))
    (cond
      (or (symbol? form) (string? form)) true
      (seq? form) (let [[op & args] form]
                    (cond
                      (= 'str op) true
                      (= 'if op) (let [[pred-id then-id else-id] args
                                       then-type (ssa/node-type (ssa/deref-id ssa-graph then-id))
                                       else-type (ssa/node-type (ssa/deref-id ssa-graph else-id))]
                                   (if (and (not (string-compatible-type? then-type))
                                            (not (string-compatible-type? else-type)))
                                     id
                                     (list 'if pred-id
                                           (cond->> then-id
                                             (string-compatible-type? then-type)
                                             (rewrite-string-valued-do-child rctx))
                                           (cond->> else-id
                                             (string-compatible-type? else-type)
                                             (rewrite-string-valued-do-child rctx)))))

                      (= '$do! op) (rewrite-child-exprs args)

                      :else (throw (ex-info "BUG! Unrecognized string-valued form"
                                            {:ssa-graph ssa-graph :id id :form form}))))
      :else (throw (ex-info "BUG! Unrecognized string-valued form"
                            {:ssa-graph ssa-graph :id id :form form})))))

(s/defn eliminate-unused-string-valued-exprs
  [{:keys [sctx ctx] :as rctx} :- rewriting/RewriteFnCtx, id, [form htype]]
  (let [ssa-graph (:ssa-graph ctx)]
    (when (and (seq? form) (= '$do! (first form)))
      (let [child-htypes (mapv #(ssa/node-type (ssa/deref-id ssa-graph %)) (rest form))]
        (when (some string-compatible-type? child-htypes)
          (util/make-do
           (mapv (fn [child htype]
                   (cond->> child
                     (string-compatible-type? htype) (rewrite-string-valued-do-child rctx)))
                 (butlast (rest form)) child-htypes)
           (last form)))))))

(s/defn ^:private expand-higher-arity-string-comparisons
  [{{:keys [ssa-graph]} :ctx} :- rewriting/RewriteFnCtx, id, [form htype]]
  (when (and (seq? form) (contains? #{'not= '=} (first form)))
    (let [arg-nodes (map #(ssa/deref-id ssa-graph %) (rest form))]
      (when (true? (some #(= :String (types/no-maybe %)) (map ssa/node-type arg-nodes)))
        (let [args (map first arg-nodes)]
          (cond->>
           (->> (rest args)
                (map #(list '= %1 %2) args)
                (util/mk-junct 'and))
            (= 'not= (first form)) (list 'not)))))))

(s/defn ^:private simplify-string-exprs :- ssa/SpecInfo
  "Simplify expressions in spec such that the only string-valued expressions
  are string-typed variable references, string literals, and unnested (str ...) forms."
  [spec :- ssa/SpecInfo]
  (-> {:$propagate/Bounds spec}
      (rewriting/rewrite-reachable-sctx
       [(rewriting/rule push-comparison-into-string-valued-if)
        (rewriting/rule eliminate-unused-string-valued-exprs)
        (rewriting/rule expand-higher-arity-string-comparisons)
        ;; We actually DO NOT want these next two rules! They
        ;; have the effect of rewriting comparisons that we'd rather turn
        ;; directly into boolean variables.
        ;; TODO: WEAKEN these rules as they appear in prop-composition, so we don't lose important string-related info!
        ;;(rewriting/rule lowering/lower-maybe-comparison-expr)
        ;;(rewriting/rule lowering/lower-no-value-comparison-expr)
        (rewriting/rule simplify/simplify-do)
        (rewriting/rule lowering/bubble-up-do-expr)
        (rewriting/rule lowering/flatten-do-expr)
        (rewriting/rule simplify/simplify-redundant-value!)
        (rewriting/rule simplify/simplify-statically-known-value?)
        (rewriting/rule lowering/push-if-value-into-if-in-expr)
        (rewriting/rule lowering/push-comparison-into-nonprimitive-if-in-expr)
        (rewriting/rule lowering/eliminate-unused-no-value-exprs-in-do-expr)])
      simplify/simplify
      :$propagate/Bounds))

;;;;;;;;;; String Comparison Graph ;;;;;;;;;;;;;;;;

(defn- string-type? [var-type]
  (= :String (->> var-type (envs/halite-type-from-var-type {}) types/no-maybe)))

(defn- maybe-string-type?
  "Return true if var-type is [:Maybe \"String\"]"
  [var-type]
  (let [ht (envs/halite-type-from-var-type {} var-type)]
    (and (types/maybe-type? ht)
         (= :String (types/no-maybe ht)))))

(defn- get-alt-var [scg n]
  (:alt-var (loom-label/label scg n)))

(defn- get-alt-val [scg a b]
  (get-in (loom-label/label scg a) [:alt-vals b]))

(defn- scg-node-for-id [ssa-graph id]
  ;; note that for some var v, 'v and '($value! v) map to the same scg node
  (let [form (ssa/node-form (ssa/deref-id ssa-graph id))]
    (if (and (seq? form) (= '$value! (first form)))
      (ssa/node-form (ssa/deref-id ssa-graph (second form)))
      form)))

(defn- scg-edge [scg e]
  (let [a (loom-graph/src e), b (loom-graph/dest e)]
    (conj
     (if (not (symbol? a))
       [b a]
       [a b])
     (loom-label/label scg a b))))

(defn- scg-edges [scg]
  (map #(scg-edge scg %) (loom-graph/edges scg)))

(s/defn ^:private compute-string-comparison-graph :- (s/protocol loom-graph/Graph)
  "Return an undirected graph where the nodes are string expressions (or :Unset) and an edge represents
  an equality comparison between two string expressions. Each edge is labeled with a boolean expression
  whose value represents that of the corresponding comparison. Variable-variable comparisons are
  represented with boolean variables. Variable-literal comparisons are represented by
  variable-int comparisons."
  [{{dgraph :dgraph :as ssa-graph} :ssa-graph, spec-vars :spec-vars :as spec} :- ssa/SpecInfo]
  (let [g (loom-graph/graph)
        g (loom-graph/add-nodes g :Unset)
        ;; ensure nodes for all string-valued variables
        optional-str-var? (->> spec-vars (filter (comp maybe-string-type? val)) (map (comp symbol key)) set)
        g (->> spec-vars
               (filter (comp string-type? val))
               (map (comp symbol key))
               (apply loom-graph/add-nodes g))
        ;; ensure nodes for all string-valued expressions
        g (->> dgraph
               vals
               (filter #(= :String (-> % ssa/node-type types/no-maybe)))
               (map ssa/node-form)
               ;; We don't need separate nodes for ($value! <sym>)
               (remove #(and (seq? %) (= '$value! (first %))))
               (apply loom-graph/add-nodes g))
        ;; add edges from optional vars to :Unset
        g (apply loom-graph/add-edges g (for [v optional-str-var?] [v :Unset]))
        ;; add edges for all comparisons
        comp-forms (->> dgraph
                        vals
                        (map ssa/node-form)
                        (filter #(and (seq? %) (= '= (first %))))
                        (map (fn [[_ & arg-ids]] (mapv #(scg-node-for-id ssa-graph %) arg-ids)))
                        (filter #(every? (partial loom-graph/has-node? g) %))
                        ;; ensure comparisons of optional vars with :Unset
                        (concat (for [v optional-str-var?] [v :Unset]))
                        set)
        edges (->> comp-forms
                   (mapcat #(map vector % (rest %)))
                   ;; we only care about comparisons between symbols and things
                   ;; NOTE: This stops being true when (str ...) forms are supported!
                   (filter #(true? (some symbol? %)))
                   ;; when the comparison is with a literal, the symbol comes first
                   (map (fn [[a b]] (if (symbol? a) [a b] [b a])))
                   set)
        g (apply loom-graph/add-edges g edges)
        var-nodes (->> g loom-graph/nodes (filter symbol?))
        ;; compute alternatives for each var based on comparisons with literals
        alts (->> var-nodes
                  (map #(zipmap
                         (->> %
                              (loom-graph/successors g)
                              (filter (fn [n] (or (string? n) (and (optional-str-var? %) (= :Unset n)))))
                              (sort-by name))
                         (range)))
                  (zipmap var-nodes))
        ;; add node labels
        g (reduce
           (fn [g [n alts-for-n]]
             (loom-label/add-label g n {:alt-var (symbol (str "$" (name n)))
                                        :alt-vals alts-for-n}))
           g
           alts)
        ;; add edge labels
        g (reduce
           (fn [g [a b]]
             (->> (if (symbol? b)
                    (let [[a b] (sort [a b])]
                      (symbol (str "$" (name a) "=" (name b))))
                    (let [alt-var (get-alt-var g a)
                          alt-val (get-alt-val g a b)]
                      (if (nil? alt-val)
                        false
                        (list 'if-value alt-var (list '= alt-var alt-val) false))))
                  (loom-label/add-label g a b)))
           g
           edges)]
    g))

;;;;;;;;;;;;;; Spec Lowering ;;;;;;;;;;;;;;;;;;;;

(defn- alt-var-decls [scg]
  (-> (loom-graph/nodes scg)
      (->> (filter symbol?) (map #(keyword (get-alt-var scg %))))
      (zipmap (repeat [:Maybe "Integer"]))))

(defn- comp-var-decls [scg]
  (-> scg
      (scg-edges)
      (->>
       (map last)
       (filter symbol?)
       (map keyword))
      (zipmap (repeat "Boolean"))))

(s/defn ^:private replace-string-comparison-with-var
  [scg {{:keys [ssa-graph]} :ctx} :- rewriting/RewriteFnCtx, id, [form htype]]
  (when (and (seq? form) (contains? #{'not= '=} (first form)))
    (let [nodes (map #(scg-node-for-id ssa-graph %) (rest form))]
      (when (every? #(loom-graph/has-node? scg %) nodes)
        (when (not= 2 (count nodes))
          (throw (ex-info "BUG! expected all string comparisons of arity > 2 to have been rewritten" {:form form})))
        (let [expr (apply loom-label/label scg nodes)]
          (cond->> expr (= 'not= (first form)) (list 'not)))))))

(s/defn ^:private replace-$value?-with-unset-comparison
  [scg {{:keys [ssa-graph]} :ctx} :- rewriting/RewriteFnCtx, id, [form htype]]
  (when (and (seq? form) (= '$value? (first form)))
    (when-let [var-sym (scg-node-for-id ssa-graph (second form))]
      (when (loom-graph/has-node? scg var-sym)
        (let [expr (loom-label/label scg var-sym :Unset)]
          (list 'not expr))))))

(s/defn ^:private lower-spec :- ssa/SpecInfo
  [spec :- ssa/SpecInfo, scg]
  (let [spec (update spec :spec-vars merge (alt-var-decls scg))
        spec (update spec :spec-vars merge (comp-var-decls scg))
        spec (->> {:$propagate/Bounds spec}
                  (rewriting/rewrite-sctx*
                   {:rule-name "replace-string-comparison-with-var"
                    :rewrite-fn (partial replace-string-comparison-with-var scg)
                    :nodes :all})
                  (rewriting/rewrite-sctx*
                   {:rule-name "replace-$value?-with-unset-comparison"
                    :rewrite-fn (partial replace-$value?-with-unset-comparison scg)
                    :nodes :all})
                  :$propagate/Bounds)
        str-var-kws (->> scg loom-graph/nodes (filter symbol?) (map keyword))]
    (-> spec
        (update :spec-vars #(apply dissoc % str-var-kws)))))

;;;;;;;;;;; Bounds ;;;;;;;;;;;;;;;;;

(s/defn ^:private disjoint-string-bounds? :- s/Bool
  [a :- StringBound, b :- StringBound]
  (cond
    (or (= a :String) (= b :String)) false
    (string? a) (recur {:$in #{a}} b)
    (string? b) (recur a {:$in #{b}})
    :else (empty? (set/intersection (:$in a) (:$in b)))))

(s/defn ^:private simplify-atom-bound :- AtomBound
  [a :- AtomBound]
  (if (and (map? a) (= 1 (count (:$in a))))
    (first (:$in a))
    a))

(s/defn ^:private lower-spec-bound :- prop-choco/SpecBound
  [initial-bound :- SpecBound scg]
  (let [str-var-kws (->> scg loom-graph/nodes (filter symbol?) (map keyword))
        ;; first, lower the user-supplied bounds into bounds for the alts vars
        bound (->> str-var-kws
                   (map (juxt identity initial-bound))
                   (map (fn [[var-kw var-bound]]
                          (let [{:keys [alt-var alt-vals]} (loom-label/label scg (symbol var-kw))
                                alts (conj (set (vals alt-vals)) :Unset)
                                optional? (= 0 (alt-vals :Unset))
                                var-bound (cond
                                            (nil? var-bound) (cond-> #{:String} optional? (conj :Unset))
                                            (or (string? var-bound) (keyword? var-bound)) #{var-bound}
                                            :else (:$in var-bound))]
                            [(keyword alt-var)
                             (simplify-atom-bound
                              {:$in (if (contains? var-bound :String)
                                      (cond-> (conj (set (vals alt-vals)) :Unset)
                                        (and optional? (not (contains? var-bound :Unset))) (disj 0))
                                      (->> var-bound (map #(get alt-vals % :Unset)) set))})])))
                   (into {})
                   (merge initial-bound))
        ;; next, see if bounds for compared variables allow us to conclude that
        ;; the variables are equal or inequal, and update comparison variable bounds accordingly
        bound (->> scg
                   (scg-edges)
                   (filter #(every? symbol? %))
                   (map (fn [[a b comp-var]]
                          (let [a-bound (get initial-bound (keyword a) :String)
                                b-bound (get initial-bound (keyword b) :String)
                                comp-kw (keyword comp-var)]
                            (cond
                              (disjoint-string-bounds? a-bound b-bound) [comp-kw false]
                              (and (string? a-bound) (string? b-bound) (= a-bound b-bound)) [comp-kw true]
                              :else nil))))
                   (remove nil?)
                   (into {})
                   (merge bound))]
    (apply dissoc bound str-var-kws)))

(defn throw-contradiction
  "For now, we're throwing a choco ContradictionException so that at least there's a consistent
  way to catch these exceptions. Really though, we need a way to signal contradiction in general that is
  decoupled from Choco."
  []
  (throw (ContradictionException.)))

(s/defn ^:private raise-spec-bound :- SpecBound
  [bound :- prop-choco/SpecBound scg initial-bound :- SpecBound]
  (let [str-vars (->> scg loom-graph/nodes (filter symbol?))
        ;; expressions proven to be equal
        eq-g (loom-derived/edges-filtered-by
              #(let [[a b expr :as e] (scg-edge scg %)]
                 (and (symbol? expr) (true? (bound (keyword expr)))))
              scg)
        ;; compute per-var bounds from alt vars
        new-bound (reduce
                   (fn [new-bound var-sym]
                     (let [{:keys [alt-var alt-vals]} (loom-label/label scg var-sym)
                           could-be-unset? (seq? (loom-label/label scg var-sym :Unset))
                           given-bound (get initial-bound (keyword var-sym) (if could-be-unset? {:$in #{:String :Unset}} :String))
                           var-bound (get bound (keyword alt-var))
                           var-bound (if (map? var-bound) (:$in var-bound) #{var-bound})
                           alt-map (assoc (set/map-invert alt-vals) :Unset :String)
                           var-bound (set (map alt-map var-bound))
                           var-bound (if (contains? var-bound :String) #{:String} var-bound)]
                       (-> new-bound
                           (dissoc (keyword alt-var))
                           (assoc (keyword var-sym)
                                  (cond
                                    (contains? var-bound :String) given-bound
                                    (= 1 (count var-bound)) (first var-bound)
                                    :else {:$in var-bound})))))
                   (apply dissoc bound (->> scg scg-edges (map last) (filter symbol?) (map keyword)))
                   str-vars)
        ;; when two vars are proven equal, use each one's computed bound to restrict the others'
        new-bound (reduce
                   (fn [new-bound [a-kw b-kw]]
                     (let [a-bound (new-bound a-kw)
                           b-bound (new-bound b-kw)
                           {:keys [alt-var alt-vals]} (loom-label/label scg (symbol b-kw))
                           val-map (set/map-invert alt-vals)
                           b-alt-bound (bound (keyword alt-var))
                           b-alt-vals (if (map? b-alt-bound) (:$in b-alt-bound) #{b-alt-bound})
                           disproved-vals (set (vals (apply dissoc val-map b-alt-vals)))]
                       (cond
                         ;; if a and b must be equal, but must also be different
                         (and (string? a-bound) (string? b-bound) (not= a-bound b-bound))
                         (throw-contradiction)

                         ;; otherwise, if b is a specific value, then so must be a
                         (string? b-bound)
                         (assoc new-bound a-kw b-bound)

                         ;; if a has been narrowed to a specific value, and b cannot be that value, contradiction
                         (string? a-bound)
                         (if (contains? disproved-vals a-bound)
                           (throw-contradiction)
                           new-bound)

                         ;; if a has been narrowed to a specific set of values, we can remove
                         ;; disproven b alternatives
                         (and (map? a-bound) (every? string? (:$in a-bound)))
                         (let [remaining (set/difference (:$in a-bound) disproved-vals)]
                           (when (empty? remaining)
                             (throw-contradiction))
                           (assoc new-bound a-kw (simplify-atom-bound {:$in remaining})))

                         :else new-bound)))
                   new-bound
                   (for [a (filter symbol? (loom-graph/nodes eq-g))
                         b (filter symbol? (loom-graph/successors eq-g a))]
                     [(keyword a) (keyword b)]))]

    ;; TODO: when two vars are proven different, subtract each bound from the other
    (update-vals new-bound simplify-atom-bound)))

;;;;;;;;;;;; Propagate ;;;;;;;;;;;;;;;;;

(def Opts prop-choco/Opts)

(def default-options prop-choco/default-options)

(s/defn propagate :- SpecBound
  ([spec :- ssa/SpecInfo, initial-bound :- SpecBound]
   (propagate spec default-options initial-bound))
  ([spec :- ssa/SpecInfo, opts :- Opts, initial-bound :- SpecBound]
   (let [spec (simplify-string-exprs spec)
         scg (-> spec compute-string-comparison-graph)
         spec' (lower-spec spec scg)]
     (-> spec'
         (prop-choco/propagate
          opts
          (lower-spec-bound initial-bound scg))
         (raise-spec-bound scg initial-bound)))))
