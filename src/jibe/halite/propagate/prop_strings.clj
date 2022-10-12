;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.propagate.prop-strings
  "Propagate support for strings.

  The strategy is to simplify all string-related expressions as much as possible,
  and then replace all string variables and string-related expressions with boolean
  variables that represent the results of comparisons."
  (:require [clojure.set :as set]
            [jibe.halite.halite-envs :as halite-envs]
            [jibe.halite.halite-types :as halite-types]
            [jibe.halite.propagate.prop-choco :as prop-choco]
            [jibe.halite.transpile.ssa :as ssa]
            [jibe.halite.transpile.rewriting :as rewriting]
            [jibe.halite.transpile.util :as util]
            [loom.alg :as loom-alg]
            [loom.graph :as loom-graph]
            [loom.label :as loom-label]
            [loom.derived :as loom-derived]
            [schema.core :as s])
  (:import [org.chocosolver.solver.exception ContradictionException]))

(s/defschema AtomBound
  (s/cond-pre
   s/Int
   s/Bool
   s/Str
   (s/enum :Unset :String)
   {:$in (s/cond-pre
          #{(s/cond-pre s/Int s/Bool s/Str (s/enum :Unset))}
          [(s/one s/Int :lower) (s/one s/Int :upper) (s/optional (s/enum :Unset) :Unset)])}))

(s/defschema StringBound
  (s/cond-pre s/Str (s/enum :String) {:$in #{s/Str}}))

(s/defschema SpecBound
  {halite-types/BareKeyword AtomBound})

(s/defn ^:private compute-string-comparison-graph :- (s/protocol loom-graph/Graph)
  "Return an undirected graph where the nodes are string expressions and an edge represents
  an equality comparison between two string expressions. Each edge is labeled with the name
  of a boolean variable whose value represents that of the corresponding comparison."
  [{{dgraph :dgraph} :ssa-graph :as spec} :- ssa/SpecInfo]
  (as-> (loom-graph/graph) g

    ;; add all necessary nodes
    (reduce-kv
     (fn [g id node]
       (let [form (ssa/node-form node)]
         (cond
           (or (string? form) (symbol? form)) (loom-graph/add-nodes g id)
           (and (seq? form) (= 'str (first form))) (throw (ex-info "TODO: Composite strings not imlemented yet!"
                                                                   {:id id :node node}))
           :else (throw (ex-info "BUG! Unrecognized string-valued expression" {:id id :node node})))))
     g
     ;; string-valued nodes become graph nodes
     (filter #(= :String (ssa/node-type (val %))) dgraph))

    ;; add all edges
    (reduce-kv
     (fn [g id node]
       (let [arg-ids (rest (ssa/node-form node))
             pairs (map vector arg-ids (rest arg-ids))]
         (apply loom-graph/add-edges g pairs)))
     g
     (filter
      #(let [form (ssa/node-form (val %))]
         ;; We only need to consider equality nodes, because every disequality node must have
         ;; a corresponding equality node as its negation.
         (and (seq? form) (= '= (first form))
              (every? (partial loom-graph/has-node? g) (rest form))))
      dgraph))))

(s/defn ^:private label-scg :- (s/protocol loom-graph/Graph)
  [scg :- (s/protocol loom-graph/Graph)]
  (reduce
   (fn [scg e]
     (let [a (loom-graph/src e)
           b (loom-graph/dest e)]
       (loom-label/add-label scg a b (symbol (str (name a) "=" (name b))))))
   scg
   (set (loom-graph/edges scg))))

(defn- string-type? [var-type]
  (= :String (->> var-type (halite-envs/halite-type-from-var-type {}) halite-types/no-maybe)))

(defn- string-var-kws [{:keys [spec-vars] :as spec}]
  (reduce-kv (fn [acc var-kw var-type] (cond-> acc (string-type? var-type) (conj var-kw))) #{} spec-vars))

(defn- remove-string-vars
  [spec-vars]
  (when-let [[var-kw var-type] (some #(= [:Maybe "String"] (second %)) spec-vars)]
    (throw (ex-info "TODO: Implement optional string variables" {:var-kv var-kw})))
  (reduce-kv
   (fn [spec-vars var-kw var-type]
     (cond-> spec-vars (string-type? var-type) (dissoc var-kw)))
   spec-vars
   spec-vars))

(defn- add-comparison-vars
  [spec-vars scg]
  (reduce
   (fn [spec-vars edge]
     (let [l (loom-label/label scg (loom-graph/src edge) (loom-graph/dest edge))]
       (assoc spec-vars (keyword l) "Boolean")))
   spec-vars
   (loom-graph/edges scg)))

(defn- compared-literals
  [connectivity-fn ssa-graph scg var-kw]
  (->> (symbol var-kw)
       (ssa/find-form ssa-graph)
       (connectivity-fn scg)
       ;;(rest)
       (map #(ssa/node-form (ssa/deref-id ssa-graph %)))
       (filter string?)))

(def ^:private directly-compared-literals (partial compared-literals loom-graph/successors))
(def ^:private indirectly-compared-literals (partial compared-literals loom-alg/pre-traverse))

(defn- alts-var-kw [var-kw]
  (keyword (str "$" (name var-kw) "$alts")))

(defn- alts-var-sym [var-kw]
  (symbol (alts-var-kw var-kw)))

(defn- add-exclusivity-vars
  "Add an integer variable for each string variable whose purpose is to encode mutually exclusive comparisons."
  [{:keys [spec-vars ssa-graph] :as spec} scg]
  (reduce
   (fn [[spec alts] var-kw]
     (let [literals (sort (directly-compared-literals ssa-graph scg var-kw))]
       (if (empty? literals)
         [spec alts]
         [(assoc-in spec [:spec-vars (alts-var-kw var-kw)] [:Maybe "Integer"])
          (assoc alts (alts-var-kw var-kw) (assoc (zipmap (range) literals) :Unset :String))])))
   [spec {}]
   (string-var-kws spec)))

(s/defn ^:private replace-string-comparison-with-var
  [scg {:keys [sctx ctx] :as rctx} :- rewriting/RewriteFnCtx, id, [form htype]]
  (when (and (seq? form) (contains? #{'not= '=} (first form)) (every? (partial loom-graph/has-node? scg) (rest form)))
    (let [arg-ids (rest form)
          pairs (map vector arg-ids (rest arg-ids))]
      (cond->> (util/mk-junct
                (if (= '= (first form)) 'and 'or)
                (map #(apply loom-label/label scg %) pairs))
        (= 'not= (first form)) (list 'not)))))

(s/defn ^:private lower-spec :- [(s/one ssa/SpecInfo :spec)
                                 (s/one {s/Keyword {(s/cond-pre (s/enum :Unset) s/Int) (s/cond-pre (s/enum :String) s/Str)}} :alts)]
  [{:keys [spec-vars constraints ssa-graph] :as spec} :- ssa/SpecInfo, scg :- (s/protocol loom-graph/Graph)]
  (let [spec (update spec :spec-vars add-comparison-vars scg)
        [spec alts] (add-exclusivity-vars spec scg)
        spec (rewriting/rewrite-reachable
              [{:rule-name "replace-string-comparison-with-var"
                :rewrite-fn (partial replace-string-comparison-with-var scg)
                :nodes :all}]
              {:$propagate/Bounds spec}
              :$propagate/Bounds)
        spec (reduce
              (fn [spec str-var-kw]
                (let [alt-kw (alts-var-kw str-var-kw)
                      alts-for-var (dissoc (alts alt-kw) :Unset)
                      str-var-id (ssa/find-form ssa-graph (symbol str-var-kw))
                      comp-vars (reduce-kv
                                 (fn [acc i s]
                                   (assoc acc s (loom-label/label scg str-var-id (ssa/find-form ssa-graph s))))
                                 {}
                                 alts-for-var)]
                  (if (empty? alts-for-var)
                    spec
                    (rewriting/add-constraint
                     "add-string-exclusivity-constraint" {} :$propagate/Bounds spec (name alt-kw)
                     (list 'if-value (symbol alt-kw)
                           (->> alts-for-var
                                (map (fn [[i s]] (list '= (list '= i (symbol alt-kw)) (comp-vars s))))
                                (util/mk-junct 'and))
                           (util/mk-junct 'and (map #(list 'not %) (vals comp-vars))))))))
              spec
              (string-var-kws spec))]
    [(update spec :spec-vars remove-string-vars)
     alts]))

(defn- pprinted [& args]
  (clojure.pprint/pprint (vec args))
  (last args))

(s/defn ^:private disjoint-string-bounds? :- s/Bool
  [a :- StringBound, b :- StringBound]
  (cond
    (or (= a :String) (= b :String)) false
    (string? a) (recur {:$in #{a}} b)
    (string? b) (recur a {:$in #{b}})
    :else (empty? (set/intersection (:$in a) (:$in b)))))

(s/defn ^:private simplify-string-bound :- StringBound
  [a :- StringBound]
  (if (and (map? a) (= 1 (count (:$in a))))
    (first (:$in a))
    a))

(s/defn ^:private lower-spec-bound :- prop-choco/SpecBound
  [scg, {:keys [ssa-graph spec-vars] :as spec} :- ssa/SpecInfo, alts, initial-bound :- SpecBound]
  (reduce
   (fn [bound edge]
     (let [[a-id b-id] ((juxt loom-graph/src loom-graph/dest) edge)
           [a b] (map #(ssa/node-form (ssa/deref-id ssa-graph %)) [a-id b-id])
           [[a-id b-id] [a b]] (if (string? a) [[b-id a-id] [b a]] [[a-id b-id] [a b]])]
       (if (and (symbol? a) (contains? initial-bound (keyword a)))
         (let [a-bound (simplify-string-bound (get initial-bound (keyword a)))
               b-bound (simplify-string-bound
                        (cond (string? b) b
                              (symbol? b) (get initial-bound (keyword b) :String)
                              :else :String))
               comp-var (keyword (loom-label/label scg a-id b-id))]
           (cond
             (disjoint-string-bounds? a-bound b-bound) (assoc bound comp-var false)
             (and (string? a-bound) (string? b-bound) (= a-bound b-bound)) (assoc bound comp-var true)
             :else bound))
         bound)))
   (-> (apply dissoc initial-bound (string-var-kws spec))
       (merge (-> alts (update-vals #(hash-map :$in (set (keys %)))))))
   (loom-graph/edges scg)))

(defn- throw-contradiction
  "For now, we're throwing a choco ContradictionException so that at least there's a consistent
  way to catch these exceptions. Really though, we need a way to signal contradiction in general that is
  decoupled from Choco."
  []
  (throw (ContradictionException. )))

(s/defn ^:private raise-spec-bound :- SpecBound
  [computed-bound :- prop-choco/SpecBound
   scg, {:keys [ssa-graph spec-vars] :as spec} :- ssa/SpecInfo, alts, initial-bound :- SpecBound]
  (let [witness-var-kws (->> scg
                             loom-graph/edges
                             (map #(vector % (keyword (loom-label/label scg (loom-graph/src %) (loom-graph/dest %)))))
                             (into {}))
        ;; The eq-g graph's edges represent proven equality.
        ;; Two nodes in eq-g have an edge if they are known to have the same value.
        eq-g (loom-derived/edges-filtered-by #(true? (get computed-bound (witness-var-kws %))) scg)
        ;; The ne-g graph's edges represent proven inequality.
        ;; Two nodes in ne-g have an edge if they are known to have different values.
        ne-g (loom-derived/edges-filtered-by #(false? (get computed-bound (witness-var-kws %))) scg)]
    ;; No two nodes can be both equal and not equal.
    (doseq [a (loom-graph/nodes eq-g)
            b (loom-alg/pre-traverse eq-g a)]
      (when (loom-graph/has-edge? ne-g a b)
        (throw-contradiction)))
    (reduce
     (fn [bound var-kw]
       (let [var-id (ssa/find-form ssa-graph (symbol var-kw))
             literals (indirectly-compared-literals ssa-graph eq-g var-kw)
             alt-bound (get computed-bound (alts-var-kw var-kw))]
         ;; If we've 'proven' a string variable to be two different literal strings simultaneously,
         ;; then we've found a contradiction.
         (when (< 1 (count literals))
           (throw-contradiction))
         (assoc bound var-kw
                (cond
                  (= 1 (count literals))
                  (first literals)

                  (int? alt-bound)
                  (get-in alts [(alts-var-kw var-kw) alt-bound])

                  (and (map? alt-bound) (not (contains? (:$in alt-bound) :Unset)))
                  {:$in (set (vals (select-keys (get alts (alts-var-kw var-kw)) (:$in alt-bound))))}

                  :else :String))))
     (apply dissoc computed-bound (concat (vals witness-var-kws) (keys alts)))
     (string-var-kws spec))))

(def Opts prop-choco/Opts)

(def default-options prop-choco/default-options)

(s/defn propagate :- SpecBound
  ([spec :- halite-envs/SpecInfo, initial-bound :- SpecBound]
   (propagate spec default-options initial-bound))
  ([spec :- halite-envs/SpecInfo, opts :- Opts, initial-bound :- SpecBound]
   (let [spec (ssa/spec-to-ssa {} spec)
         scg (-> spec compute-string-comparison-graph label-scg)
         [spec' alts] (lower-spec spec scg)]
     (-> spec'
         (ssa/spec-from-ssa)
         (prop-choco/propagate
          opts
          (lower-spec-bound scg spec alts initial-bound))
         (raise-spec-bound scg spec alts initial-bound)))))
