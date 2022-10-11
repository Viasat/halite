;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.propagate.prop-abstract
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [jibe.halite.halite-envs :as halite-envs]
            [jibe.halite.halite-types :as halite-types]
            [jibe.halite.propagate.prop-composition :as prop-composition]
            [jibe.halite.transpile.ssa :as ssa :refer [SpecCtx SpecInfo]]
            [jibe.halite.transpile.lowering :as lowering]
            [jibe.halite.transpile.util :refer [fixpoint mk-junct]]
            [jibe.halite.transpile.simplify :as simplify :refer [simplify-redundant-value! simplify-statically-known-value?]]
            [jibe.halite.transpile.rewriting :as rewriting]
            [loom.graph :as loom-graph]
            [loom.derived :as loom-derived]
            [schema.core :as s]
            [viasat.choco-clj-opt :as choco-clj]))

;;;;;;;;;;;; Abstractness ;;;;;;;;;;;;

;; We handle abstractness by transforming specs and input bounds to eliminate it,
;; and then reversing the transformation on the resulting output bound.
;;
;; For every abstract spec A, let C_0...C_n be a total ordering of all concrete specs
;; that refine to A directly or indirectly.
;;
;; We transform a spec S with variable a of type A into a spec S' with variables:
;;   a$type of type Integer
;;   a$0 of type [:Maybe C_0]
;;   ...
;;   a$n of type [:Maybe C_n]
;;
;; We transform the existing constraint and refinement expressions of S, replacing each
;; occurrence of a with:
;;   (if-value a$0 a$0 (if-value a$1 .... (error "unreachable")))
;;
;; 

(declare Bound)

(s/defschema SpecIdToBound
  {halite-types/NamespacedKeyword
   {halite-types/BareKeyword (s/recursive #'Bound)}})

(s/defschema SpecIdToBoundWithRefinesTo
  {halite-types/NamespacedKeyword
   {(s/optional-key :$refines-to) SpecIdToBound
    halite-types/BareKeyword (s/recursive #'Bound)}
   (s/optional-key :Unset) s/Bool})

(s/defschema AbstractSpecBound
  (s/constrained
   {(s/optional-key :$in) SpecIdToBoundWithRefinesTo
    (s/optional-key :$if) SpecIdToBoundWithRefinesTo
    (s/optional-key :$refines-to) SpecIdToBound}
   #(< (count (select-keys % [:$in :$if])) 2)
   "$in-and-$if-mutually-exclusive"))

(s/defschema ConcreteSpecBound2
  {:$type (s/cond-pre [(s/one (s/enum :Maybe) :maybe) (s/one halite-types/NamespacedKeyword :type)]
                      halite-types/NamespacedKeyword)
   (s/optional-key :$refines-to) SpecIdToBound
   halite-types/BareKeyword (s/recursive #'Bound)})

(s/defschema SpecBound
  (s/conditional
   :$type ConcreteSpecBound2
   :else AbstractSpecBound))

(s/defschema Bound
  (s/conditional
   :$type prop-composition/ConcreteBound
   #(or (not (map? %))
        (and (contains? % :$in)
             (not (map? (:$in %))))) prop-composition/AtomBound
   :else AbstractSpecBound))

(def Opts prop-composition/Opts)

(def default-options prop-composition/default-options)

(defn- discriminator-var-name [var-kw] (str (name var-kw) "$type"))
(defn- discriminator-var-kw [var-kw] (keyword (discriminator-var-name var-kw)))
(defn- discriminator-var-sym [var-kw] (symbol (discriminator-var-name var-kw)))

(defn- var-entry->spec-id [specs [var-kw var-type]]
  (->> var-type (halite-envs/halite-type-from-var-type specs) halite-types/no-maybe halite-types/spec-id))

(defn- abstract-var?
  [specs var-entry]
  (if-let [spec-id (var-entry->spec-id specs var-entry)]
    (true? (:abstract? (specs spec-id)))
    false))

(defn- lower-abstract-vars
  [specs alternatives {:keys [spec-vars] :as spec-info}]
  (reduce-kv
   (fn [{:keys [spec-vars constraints refines-to] :as spec-info} var-kw var-type]
     (let [alts (alternatives (var-entry->spec-id specs [var-kw var-type]))
           optional-var? (and (vector? var-type) (= :Maybe (first var-type)))
           lowered-expr (reduce
                         (fn [expr i]
                           (let [alt-var (symbol (str (name var-kw) "$" i))]
                             (list 'if-value alt-var alt-var expr)))
                         (if optional-var? '$no-value '(error "unreachable"))
                         (reverse (sort (vals alts))))]
       ;; TODO: Should this just produce an unsatisfiable spec, instead?
       (when (empty? alts)
         (throw (ex-info (format "No values for variable %s: No concrete specs refine to %s" var-kw var-type)
                         {:spec-info spec-info :alternatives alternatives})))
       (assoc
        spec-info

        :spec-vars
        (reduce-kv
         (fn [spec-vars alt-spec-id i]
           (assoc spec-vars (keyword (str (name var-kw) "$" i)) [:Maybe alt-spec-id]))
         (-> spec-vars
             (dissoc var-kw)
             (assoc (discriminator-var-kw var-kw) (cond->> "Integer" optional-var? (vector :Maybe))))
         alts)

        :constraints
        (vec
         (concat
          (map
           (fn [[cname cexpr]]
             [cname (list 'let [(symbol var-kw) lowered-expr] cexpr)])
           constraints)
          (map (fn [i] [(str (name var-kw) "$" i)
                        (list '=
                              (list '= (discriminator-var-sym var-kw) i)
                              (list 'if-value (symbol (str (name var-kw) "$" i)) true false))])
               (sort (vals alts)))))
        :refines-to
        (reduce-kv
         (fn [acc target-spec-id {:keys [expr] :as refn}]
           (assoc acc target-spec-id
                  (assoc refn :expr (list 'let [(symbol var-kw) lowered-expr] expr))))
         {}
         refines-to))))
   spec-info
   (filter (partial abstract-var? specs) spec-vars)))

(defn- invert-adj [adj-lists]
  (reduce-kv
   (fn [acc from to-list]
     (reduce
      (fn [acc to]
        (if (contains? acc to)
          (update acc to conj from)
          (assoc acc to [from])))
      acc
      to-list))
   {}
   adj-lists))

(declare lower-abstract-bounds)

(defn- lower-abstract-var-bound
  [specs alternatives var-kw optional-var? alts-for-spec {:keys [$if $in $type $refines-to] :as abstract-bound} parent-bound]
  (cond
    $if (let [b {:$in (merge (zipmap (keys alts-for-spec) (repeat {})) $if)}
              b (cond-> b $refines-to (assoc :$refines-to $refines-to))
              b (cond-> b (and optional-var? (not (false? (:Unset $if)))) (assoc-in [:$in :Unset] true))]
          (recur specs alternatives var-kw optional-var? alts-for-spec b parent-bound))
    $type (let [b {:$in {$type (dissoc abstract-bound :$type)}}]
            (recur specs alternatives var-kw optional-var? alts-for-spec b parent-bound))
    $in (let [unset? (true? (:Unset $in))
              alt-ids (->> (dissoc $in :Unset) keys (map alts-for-spec) set)]
          (reduce-kv
           (fn [parent-bound spec-id spec-bound]
             (let [i (alts-for-spec spec-id)]
               (assoc parent-bound (keyword (str (name var-kw) "$" i))
                      (lower-abstract-bounds
                       (cond-> (assoc spec-bound :$type [:Maybe spec-id])
                         $refines-to (assoc :$refines-to $refines-to))
                       specs alternatives))))
           ;; restrict the discriminator
           (assoc parent-bound (discriminator-var-kw var-kw) {:$in (cond-> alt-ids unset? (conj :Unset))})
           (dissoc $in :Unset)))
    :else (throw (ex-info "Invalid abstract bound" {:bound abstract-bound}))))

(s/defn ^:private lower-abstract-bounds :- ConcreteSpecBound2
  [spec-bound :- SpecBound, specs :- halite-envs/SpecMap, alternatives]
  (let [spec-id (:$type spec-bound)
        spec-id (cond-> spec-id (vector? spec-id) second) ; unwrap [:Maybe ..]
        {:keys [spec-vars] :as spec} (specs spec-id)]
    (->>
     spec-vars
     (filter #(abstract-var? specs %))
     (reduce
      (fn [spec-bound [var-kw var-type :as var-entry]]
        (let [var-spec-id (var-entry->spec-id specs var-entry)
              optional-var? (and (vector? var-type) (= :Maybe (first var-type)))
              alts (alternatives var-spec-id)
              var-bound (or (var-kw spec-bound) {:$if {}})]
          (if (= :Unset var-bound)
            (-> spec-bound (dissoc var-kw) (assoc (discriminator-var-kw var-kw) :Unset))
            (let [var-bound (cond-> var-bound
                              ;; TODO: intersect $refines-to bounds when present at both levels
                              (= [:$refines-to] (keys var-bound)) (assoc :$if {}))]
              (-> spec-bound
                  ;; remove the abstract bound, if any
                  (dissoc var-kw)
                  ;; add in a bound for the discriminator
                  (assoc (discriminator-var-kw var-kw) {:$in (cond-> (set (range (count alts))) optional-var? (conj :Unset))})
                  ;; if an abstract bound was provided, lower it
                  (cond->> var-bound (lower-abstract-var-bound specs alternatives var-kw optional-var? alts var-bound)))))))
      spec-bound))))

(declare raise-abstract-bounds)

(defn- raise-abstract-var-bound
  [specs alternatives var-kw alts parent-bound]
  (let [discrim-kw (discriminator-var-kw var-kw)
        parent-bound (reduce
                      (fn [parent-bound [spec-id i]]
                        (let [alt-var-kw (keyword (str (name var-kw) "$" i))
                              alt-bound (parent-bound alt-var-kw)]
                          (cond-> (dissoc parent-bound alt-var-kw)
                            (not= :Unset alt-bound)
                            (-> (assoc-in [var-kw :$in spec-id] (-> alt-bound (raise-abstract-bounds specs alternatives) (dissoc :$type)))
                                (update-in [var-kw :$refines-to] prop-composition/union-refines-to-bounds (:$refines-to alt-bound))))))
                      (-> parent-bound
                          (assoc var-kw (if (= :Unset (discrim-kw parent-bound))
                                          :Unset
                                          {:$in (cond-> {} (some-> parent-bound discrim-kw :$in :Unset) (assoc :Unset true))}))
                          (dissoc discrim-kw))
                      alts)
        alt-bounds (get-in parent-bound [var-kw :$in])]
    (if (= 1 (count alt-bounds))
      (let [[spec-id bound] (first alt-bounds)]
        (assoc parent-bound var-kw (assoc bound :$type spec-id)))
      parent-bound)))

(s/defn ^:private raise-abstract-bounds :- SpecBound
  [spec-bound :- ConcreteSpecBound2, specs :- halite-envs/SpecMap, alternatives]
  (let [spec-id (:$type spec-bound)
        spec-id (cond-> spec-id (vector? spec-id) second)
        {:keys [spec-vars] :as spec} (specs spec-id)]
    (->>
     spec-vars
     (filter #(abstract-var? specs %))
     (reduce
      (fn [spec-bound [var-kw var-type :as var-entry]]
        (let [var-spec-id (var-entry->spec-id specs var-entry)
              alts (alternatives var-spec-id)]
          (raise-abstract-var-bound specs alternatives var-kw alts spec-bound)))
      spec-bound))))

(s/defn propagate :- SpecBound
  ([senv :- (s/protocol halite-envs/SpecEnv), initial-bound :- SpecBound]
   (propagate senv prop-composition/default-options initial-bound))
  ([senv :- (s/protocol halite-envs/SpecEnv), opts :- prop-composition/Opts, initial-bound :- SpecBound]
   (let [specs (cond-> senv
                 (or (instance? jibe.halite.halite_envs.SpecEnvImpl senv)
                     (not (map? senv))) (halite-envs/build-spec-map (:$type initial-bound)))
         abstract? #(-> % specs :abstract? true?)
         refns (invert-adj (update-vals specs (comp keys :refines-to)))
         refn-graph (if (empty? refns)
                      (loom-graph/digraph)
                      (loom-graph/digraph refns))
         alternatives (reduce
                       (fn [alts spec-id]
                         (->> spec-id
                              (loom-derived/subgraph-reachable-from refn-graph)
                              loom-graph/nodes
                              (filter (complement abstract?))
                              sort
                              (#(zipmap % (range)))
                              (assoc alts spec-id)))
                       {}
                       (filter abstract? (keys specs)))]
     (-> specs
         (update-vals #(lower-abstract-vars specs alternatives %))
         (prop-composition/propagate opts (lower-abstract-bounds initial-bound specs alternatives))
         (raise-abstract-bounds specs alternatives)))))
