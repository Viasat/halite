;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.prop-abstract
  (:require [clojure.set :as set]
            [com.viasat.halite.choco-clj-opt :as choco-clj]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.propagate.bound-union :refer [union-refines-to-bounds]]
            [com.viasat.halite.propagate.prop-composition :as prop-composition]
            [com.viasat.halite.transpile.lowering :as lowering]
            [com.viasat.halite.transpile.rewriting :as rewriting]
            [com.viasat.halite.transpile.simplify :as simplify :refer [simplify-redundant-value! simplify-statically-known-value?]]
            [com.viasat.halite.transpile.ssa :as ssa]
            [com.viasat.halite.transpile.util :refer [fixpoint mk-junct]]
            [com.viasat.halite.types :as types]
            [loom.derived :as loom-derived]
            [loom.graph :as loom-graph]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

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

(s/defschema UntypedSpecBound {types/BareKeyword (s/recursive #'Bound)})

(s/defschema SpecIdToBound
  {types/NamespacedKeyword UntypedSpecBound})

(s/defschema SpecIdToBoundWithRefinesTo
  {types/NamespacedKeyword {(s/optional-key :$refines-to) SpecIdToBound
                            types/BareKeyword (s/recursive #'Bound)}
   (s/optional-key :Unset) s/Bool})

(s/defschema AbstractSpecBound
  (s/constrained
   {(s/optional-key :$in) SpecIdToBoundWithRefinesTo
    (s/optional-key :$if) SpecIdToBoundWithRefinesTo
    (s/optional-key :$refines-to) SpecIdToBound}
   #(< (count (select-keys % [:$in :$if])) 2)
   "$in-and-$if-mutually-exclusive"))

;; Despite the name, this can appear at the position of a field whose declared
;; type is an abstract spec, though the :$type actually identified in this
;; bounds will always be concrete.
(s/defschema ConcreteSpecBound2
  {:$type (s/cond-pre [(s/one (s/enum :Maybe) :maybe) (s/one types/NamespacedKeyword :type)]
                      types/NamespacedKeyword)
   (s/optional-key :$refines-to) SpecIdToBound
   types/BareKeyword (s/recursive #'Bound)})

(s/defschema SpecBound
  (s/conditional
   :$type ConcreteSpecBound2
   :else AbstractSpecBound))

(defn- concrete-spec-bound? [bound]
  (:$type bound))

(defn- atom-bound? [bound]
  (and (not (concrete-spec-bound? bound))
       (or (not (map? bound))
           (and (contains? bound :$in)
                (not (map? (:$in bound)))))))

(defn- abstract-spec-bound? [bound]
  (and (not (concrete-spec-bound? bound))
       (not (atom-bound? bound))))

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

(defn- var-entry->spec-id [senv [var-kw var-type]]
  (->> var-type types/no-maybe types/spec-id))

(defn- abstract-var?
  [senv var-entry]
  (if-let [spec-id (var-entry->spec-id senv var-entry)]
    (true? (:abstract? (envs/lookup-spec senv spec-id)))
    false))

(s/defn ^:private replace-all
  [{:keys [constraints refines-to ssa-graph] :as spec} replacements]
  (as-> spec spec
    (reduce
     (fn [{:keys [ssa-graph] :as spec} [cname cid]]
       (let [[ssa-graph cid'] (ssa/replace-in-expr ssa-graph cid replacements)]
         (-> spec
             (update :constraints conj [cname cid'])
             (assoc :ssa-graph ssa-graph))))
     (assoc spec :constraints [])
     constraints)
    (reduce-kv
     (fn [{:keys [ssa-graph] :as spec} spec-id {:keys [expr]}]
       (let [[ssa-graph id'] (ssa/replace-in-expr ssa-graph expr replacements)]
         (-> spec
             (assoc :ssa-graph ssa-graph)
             (assoc-in [:refines-to spec-id :expr] id'))))
     spec
     refines-to)))

(defn- lower-abstract-vars
  "Modifies (?) a spec in the context to have additional variables to represent the concrete
  alternatives for an abstract variable."
  [sctx alternatives spec-id spec]
  (let [senv (ssa/as-spec-env sctx)
        {:keys [fields]} spec]
    (ssa/add-spec-to-context
     sctx
     spec-id
     (reduce-kv
      (fn [{:keys [fields constraints refines-to] :as spec} var-kw var-type]
        (let [alts (alternatives (var-entry->spec-id senv [var-kw var-type]))

              optional-var? (types/maybe-type? var-type)

              ;; add the discriminator var
              spec (assoc-in spec [:fields (discriminator-var-kw var-kw)] (cond->> :Integer optional-var? types/maybe-type))

              ;; add the alt vars
              spec (reduce-kv
                    (fn [spec alt-spec-id i]
                      (assoc-in spec [:fields (keyword (str (name var-kw) "$" i))] (-> alt-spec-id types/concrete-spec-type types/maybe-type)))
                    spec alts)

              ;; the expression that this abstract var will be replaced with
              lowered-expr (if (empty? alts)
                             ;; there are no concrete specs for the abstract spec, if the field is optional this is the right value
                             :Unset
                             (reduce
                              (fn [expr i]
                                (let [alt-var (symbol (str (name var-kw) "$" i))]
                                  (list 'if-value alt-var alt-var expr)))
                              (if optional-var? '$no-value '(error "unreachable"))
                              (reverse (sort (vals alts)))))

              ;; fold the lowered expression into the SSA graph
              ctx (ssa/make-ssa-ctx sctx spec)
              [ssa-graph lowered-expr-id] (ssa/form-to-ssa ctx lowered-expr)
              ctx (assoc ctx :ssa-graph ssa-graph)
              spec (assoc spec :ssa-graph ssa-graph)

              ;; replace all abstract variable occurrences
              var-node-id (ssa/find-form (:ssa-graph ctx) (symbol var-kw))
              spec (cond-> spec
                     (some? var-node-id) (replace-all {var-node-id lowered-expr-id}))

              ;; add constraints tying discriminator to alt vars
              spec (reduce
                    (fn [spec i]
                      (let [])
                      (rewriting/add-constraint
                       "link-abstract-var-discriminator-to-alt"
                       sctx spec-id spec
                       (str (name var-kw) "$" i)
                       (list '=
                             (list '= (discriminator-var-sym var-kw) i)
                             (list 'if-value (symbol (str (name var-kw) "$" i)) true false))))
                    spec
                    (sort (vals alts)))

              ;; remove abstract var
              spec (update spec :fields dissoc var-kw)]
          spec))
      spec
      (filter (partial abstract-var? senv) fields)))))

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

(s/defn spec-context-to-inverted-refinement-graph
  [sctx :- ssa/SpecCtx]
  (let [refns (invert-adj (update-vals sctx (comp keys :refines-to)))]
    (if (empty? refns)
      (loom-graph/digraph)
      (loom-graph/digraph refns))))

(declare lower-abstract-bounds)

(s/defn ^:private lower-abstract-var-bound
  "Takes an AbstractSpecBound and converts it into bounds on new variables in the parent bound.
  $if and $type fields are normalized into $in values. Lifts all of the $in values into the parent-bound as different alternatives."
  [senv
   parent-spec-ids :- [types/NamespacedKeyword]
   alternatives var-kw optional-var? alts-for-spec {:keys [$if $in $type $refines-to] :as abstract-bound} parent-bound]
  (cond
    $if (let [b {:$in (merge (zipmap (keys alts-for-spec) (repeat {})) $if)}
              b (cond-> b $refines-to (assoc :$refines-to $refines-to))
              b (cond-> b (and optional-var? (not (false? (:Unset $if)))) (assoc-in [:$in :Unset] true))]
          (recur senv parent-spec-ids alternatives var-kw optional-var? alts-for-spec b parent-bound))
    $type (let [b {:$in {$type (dissoc abstract-bound :$type)}}]
            (recur senv parent-spec-ids alternatives var-kw optional-var? alts-for-spec b parent-bound))
    $in (let [unset? (true? (:Unset $in))
              alt-ids (->> (dissoc $in :Unset) keys (map alts-for-spec) set)]
          (reduce-kv
           (fn [parent-bound spec-id spec-bound]
             (let [i (alts-for-spec spec-id)]
               (assoc parent-bound (keyword (str (name var-kw) "$" i))
                      (lower-abstract-bounds
                       parent-spec-ids
                       (cond-> (assoc spec-bound :$type [:Maybe spec-id])
                         $refines-to (assoc :$refines-to $refines-to))
                       senv alternatives))))
           ;; restrict the discriminator
           (assoc parent-bound (discriminator-var-kw var-kw) {:$in (cond-> alt-ids unset? (conj :Unset))})
           (dissoc $in :Unset)))
    :else (throw (ex-info "Invalid abstract bound" {:bound abstract-bound}))))

(s/defn ^:private promote-abstract-bounds-to-concrete-bounds-where-appropriate
  "If an abstract var bound was used for a concrete field, then populate the type field of the
  bound (to make it a concrete bound)."
  [spec-bound senv fields]
  (->> fields
       (remove #(abstract-var? senv %))
       (reduce (fn [spec-bound [var-kw var-type]]
                 (let [var-spec-id (-> var-type types/no-maybe types/inner-spec-type)
                       var-bound (var-kw spec-bound)]
                   (if (and var-bound
                            (abstract-spec-bound? var-bound)
                            (empty? (select-keys var-bound [:$in :$if])))
                     (assoc spec-bound var-kw (assoc var-bound :$type var-spec-id))
                     spec-bound)))
               spec-bound)))

(defn- recursive-parentage? [parent-spec-ids spec-id]
  (some #(= % spec-id) parent-spec-ids))

(s/defn ^:private lower-abstract-bounds :- ConcreteSpecBound2
  [parent-spec-ids :- [types/NamespacedKeyword]
   spec-bound :- SpecBound
   senv :- (s/protocol envs/SpecEnv)
   alternatives]
  (let [spec-id (:$type spec-bound)
        spec-id (cond-> spec-id (vector? spec-id) second) ; unwrap [:Maybe ..]
        {:keys [fields]} (envs/lookup-spec senv spec-id)]
    (->>
     fields
     (reduce
      (fn [spec-bound [var-kw var-type :as var-entry]]
        (let [recur? (or (var-kw spec-bound)
                         (not (recursive-parentage? parent-spec-ids spec-id)))]
          ;; handle abstract var
          (if (abstract-var? senv var-entry)
            (let [var-spec-id (var-entry->spec-id senv var-entry)
                  optional-var? (types/maybe-type? var-type)
                  alts (select-keys (alternatives var-spec-id)
                                    ;; find the spec-ids which have the prescribed refinement paths
                                    (seq (reduce
                                          (fn [alts-set spec-id]
                                            (set/intersection alts-set
                                                              (conj (set (keys (alternatives spec-id)))
                                                                    spec-id)))
                                          (set (keys (alternatives var-spec-id)))
                                          (keys (:$refines-to (var-kw spec-bound))))))
                  var-bound (or (var-kw spec-bound)
                                (when recur? {:$if {}}))]
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
                      (cond->> var-bound (lower-abstract-var-bound senv
                                                                   (conj parent-spec-ids spec-id)
                                                                   alternatives var-kw optional-var? alts var-bound))))))
            ;; handle concrete var
            (if-let [var-spec-id (var-entry->spec-id senv var-entry)]
              (let [var-bound (or (var-kw spec-bound)
                                  (when recur?
                                    {:$type var-spec-id}))]
                (if var-bound
                  (assoc spec-bound
                         var-kw (lower-abstract-bounds (conj parent-spec-ids spec-id)
                                                       var-bound
                                                       senv
                                                       alternatives))
                  spec-bound))
              spec-bound))))
      (promote-abstract-bounds-to-concrete-bounds-where-appropriate spec-bound senv fields)))))

(declare raise-abstract-bounds)

(defn- raise-abstract-var-bound
  [senv alternatives var-kw alts parent-bound]
  (let [discrim-kw (discriminator-var-kw var-kw)
        first-refines-to-bounds (get-in parent-bound [(keyword (str (name var-kw) "$0")) :$refines-to])
        parent-bound (reduce
                      (fn [parent-bound [spec-id i]]
                        (let [alt-var-kw (keyword (str (name var-kw) "$" i))
                              alt-bound (parent-bound alt-var-kw)]
                          (cond-> (dissoc parent-bound alt-var-kw)
                            (not= :Unset alt-bound)
                            (-> (assoc-in [var-kw :$in spec-id] (-> alt-bound (raise-abstract-bounds senv alternatives) (dissoc :$type)))
                                (update-in [var-kw :$refines-to] union-refines-to-bounds (:$refines-to alt-bound))))))
                      (-> parent-bound
                          (assoc var-kw (if (= :Unset (discrim-kw parent-bound))
                                          :Unset
                                          {:$in (cond-> {}
                                                  (some-> parent-bound discrim-kw :$in :Unset) (assoc :Unset true))
                                           :$refines-to first-refines-to-bounds}))
                          (dissoc discrim-kw))
                      alts)
        alt-bounds (get-in parent-bound [var-kw :$in])]
    (if (= 1 (count alt-bounds))
      (let [[spec-id bound] (first alt-bounds)]
        (assoc parent-bound var-kw (assoc bound :$type spec-id)))
      parent-bound)))

(s/defn ^:private raise-abstract-bounds :- SpecBound
  [spec-bound :- ConcreteSpecBound2, senv :- (s/protocol envs/SpecEnv), alternatives]
  (let [spec-id (:$type spec-bound)
        spec-id (cond-> spec-id (vector? spec-id) second)
        {:keys [fields]} (envs/lookup-spec senv spec-id)]
    (->>
     fields
     (reduce
      (fn [spec-bound [var-kw _ :as var-entry]]
        (if (abstract-var? senv var-entry)
          (let [var-spec-id (var-entry->spec-id senv var-entry)
                alts (alternatives var-spec-id)]
            (raise-abstract-var-bound senv alternatives var-kw alts spec-bound))
          (if-let [var-spec-id (var-entry->spec-id senv var-entry)]
            (assoc spec-bound var-kw (raise-abstract-bounds (var-kw spec-bound)
                                                            senv
                                                            alternatives))
            spec-bound)))
      spec-bound))))

(s/defn propagate :- SpecBound
  ([sctx :- ssa/SpecCtx, initial-bound :- SpecBound]
   (propagate sctx prop-composition/default-options initial-bound))
  ([sctx :- ssa/SpecCtx, opts :- prop-composition/Opts, initial-bound :- SpecBound]
   (let [abstract? #(-> % sctx :abstract? true?)
         refn-graph (spec-context-to-inverted-refinement-graph sctx)
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
                       (keys sctx))
         senv (ssa/as-spec-env sctx)]
     (-> (reduce-kv (fn [acc spec-id spec] (lower-abstract-vars acc alternatives spec-id spec)) sctx sctx)
         (prop-composition/propagate opts (lower-abstract-bounds [] initial-bound senv alternatives))
         (raise-abstract-bounds senv alternatives)))))
