;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.propagate
  "Constraint propagation for halite."
  (:require [clojure.string :as str]
            [jibe.halite.halite-envs :as halite-envs]
            [jibe.halite.halite-types :as halite-types]
            [jibe.halite.transpile.ssa :as ssa :refer [SpecCtx SpecInfo]]
            [jibe.halite.transpile.lowering :as lowering]
            [jibe.halite.transpile.util :refer [fixpoint mk-junct]]
            [jibe.halite.transpile.simplify :refer [simplify]]
            [schema.core :as s]
            [viasat.choco-clj-opt :as choco-clj]))

(declare Bound)

(s/defschema SpecBound
  {:$type (s/cond-pre [(s/one (s/enum :Maybe) :maybe) (s/one halite-types/NamespacedKeyword :type)]
                      halite-types/NamespacedKeyword)
   halite-types/BareKeyword (s/recursive #'Bound)})

(s/defschema AtomBound
  (s/cond-pre
   s/Int
   s/Bool
   (s/enum :Unset)
   {:$in (s/cond-pre
          #{(s/cond-pre s/Int s/Bool (s/enum :Unset))}
          [(s/one s/Int :lower) (s/one s/Int :upper) (s/optional (s/enum :Unset) :Unset)])}))

(s/defschema Bound
  (s/conditional
   :$type (s/cond-pre SpecBound (s/enum :Unset))
   :else AtomBound))

;;;;;;;;; Bound Spec-ification ;;;;;;;;;;;;;;;;

(s/defschema ^:private PrimitiveType (s/enum "Boolean" "Integer"))
(s/defschema ^:private PrimitiveMaybeType [(s/one (s/enum :Maybe) :maybe) (s/one PrimitiveType :inner)])

(s/defschema ^:private FlattenedVar
  [(s/one halite-types/BareKeyword :var-kw)
   (s/one (s/cond-pre PrimitiveMaybeType PrimitiveType) :type)])

(s/defschema ^:private FlattenedVars
  {::mandatory #{halite-types/BareKeyword}
   ::spec-id halite-types/NamespacedKeyword
   halite-types/BareKeyword (s/cond-pre FlattenedVar (s/recursive #'FlattenedVars))})

(defn- primitive-maybe-type?
  [htype]
  (or (#{:Integer :Boolean} htype)
      (and (halite-types/maybe-type? htype) (vector? htype)
           (#{:Integer :Boolean} (second htype)))))

(defn- spec-maybe-type?
  [htype]
  (or (halite-types/spec-type? htype)
      (and (halite-types/maybe-type? htype) (vector? htype)
           (halite-types/spec-type? (second htype)))))

(defn- unwrap-maybe [htype]
  (cond-> htype
    (and (vector? htype) (= :Maybe (first htype))) second))

(s/defn ^:private flatten-vars :- FlattenedVars
  ([senv :- (s/protocol halite-envs/SpecEnv), spec-bound :- SpecBound]
   (flatten-vars senv [] "" false spec-bound))
  ([senv :- (s/protocol halite-envs/SpecEnv)
    parent-spec-ids :- [halite-types/NamespacedKeyword]
    prefix :- s/Str
    already-optional? :- s/Bool
    spec-bound :- SpecBound]
   (let [spec-id (->> spec-bound :$type unwrap-maybe)]
     (reduce
      (fn [vars [var-kw vtype]]
        (let [htype (halite-envs/halite-type-from-var-type senv vtype)]
          (cond
            (primitive-maybe-type? htype)
            (let [actually-mandatory? (and already-optional? (not (halite-types/maybe-type? htype)))
                  prefixed-var-kw (keyword (str prefix (name var-kw)))]
              (-> vars
                  (assoc var-kw [prefixed-var-kw
                                 (cond->> vtype
                                   actually-mandatory? (vector :Maybe))])
                  (cond->
                   actually-mandatory? (update ::mandatory conj prefixed-var-kw))))

            (spec-maybe-type? htype)
            (let [spec-id (halite-types/spec-id (unwrap-maybe htype))
                  recur? (or (contains? spec-bound var-kw)
                             (every? #(not= % spec-id) parent-spec-ids))
                  optional? (halite-types/maybe-type? htype)
                  sub-bound (get spec-bound var-kw :Unset)
                  flattened-vars (when recur?
                                   (flatten-vars senv
                                                 (conj parent-spec-ids (unwrap-maybe (:$type spec-bound)))
                                                 (str prefix (name var-kw) "|")
                                                 (or already-optional? optional?)
                                                 (if (not= :Unset sub-bound) sub-bound {:$type spec-id})))]
              (cond-> vars
                recur? (assoc var-kw flattened-vars)

                (not optional?) (update ::mandatory into (::mandatory flattened-vars))

                optional? (assoc-in [var-kw :$witness] [(keyword (str prefix (name var-kw) "?")) "Boolean"])))

            :else (throw (ex-info (format "BUG! Variables of type '%s' not supported yet" htype)
                                  {:var-kw var-kw :type htype})))))
      {::mandatory #{} ::spec-id spec-id}
      (->> spec-id (halite-envs/lookup-spec senv) :spec-vars)))))

(defn- leaves [m]
  (if (map? m)
    (mapcat leaves (vals m))
    [m]))

(s/defn ^:private lower-spec-bound :- choco-clj/VarBounds
  ([vars :- FlattenedVars, spec-bound :- SpecBound]
   (lower-spec-bound vars false spec-bound))
  ([vars :- FlattenedVars, optional-context? :- s/Bool, spec-bound :- SpecBound]
   (reduce
    (fn [choco-bounds [var-kw bound]]
      (let [composite-var? (map? (vars var-kw))
            choco-var (when-not composite-var?
                        (or (some-> var-kw vars first symbol)
                            (throw (ex-info "BUG! No choco var for var in spec bound"
                                            {:vars vars :spec-bound spec-bound :var-kw var-kw}))))
            witness-var (when composite-var?
                          (some-> var-kw vars :$witness first symbol))]
        (cond
          (or (int? bound) (boolean? bound))
          (assoc choco-bounds choco-var (if optional-context? #{bound :Unset} bound))

          (= :Unset bound)
          (if composite-var?
            (if witness-var
              (assoc choco-bounds witness-var false)
              (throw (ex-info (format "Invalid bounds: %s is not an optional variable, and cannot be unset" (name var-kw))
                              {:vars vars :var-kw var-kw :bound bound})))
            (assoc choco-bounds choco-var :Unset))

          (and (map? bound) (contains? bound :$in))
          (let [range-or-set (:$in bound)]
            (when composite-var?
              (throw (ex-info (format "Invalid bound for composite var %s" (name var-kw))
                              {:var-kw var-kw :bound bound})))
            (if (or (vector? range-or-set) (set? range-or-set))
              (assoc choco-bounds choco-var
                     (cond
                       (and (set? range-or-set) optional-context?) (conj range-or-set :Unset)
                       (and (vector range-or-set) (= 2 (count range-or-set)) optional-context?) (conj range-or-set :Unset)
                       :else range-or-set))
              (throw (ex-info (format "Invalid bound for %s" (name var-kw))
                              {:var-kw var-kw :bound bound}))))

          (and (map? bound) (contains? bound :$type))
          (let [optional? (and (vector? (:$type bound)) (= :Maybe (first (:$type bound))))
                htype (halite-types/concrete-spec-type (cond-> (:$type bound) optional? (second)))]
            (when-not composite-var?
              (throw (ex-info (format "Invalid bound for %s, which is not composite" (name var-kw))
                              {:var-kw var-kw :bound bound})))
            (when (and (nil? witness-var) (halite-types/maybe-type? htype))
              (throw (ex-info (format "Invalid bound for %s, which is not optional" (name var-kw))
                              {:var-kw var-kw :bound bound})))
            (-> choco-bounds
                (merge (lower-spec-bound (vars var-kw) (or optional-context? optional?) bound))
                (cond-> (and witness-var (not optional?) (not optional-context?))
                  (assoc witness-var true))))

          :else (throw (ex-info (format "Invalid bound for %s" (name var-kw))
                                {:var-kw var-kw :bound bound})))))
    {}
    (dissoc spec-bound :$type))))

(s/defn ^:private optionality-constraint :- s/Any
  [senv :- (s/protocol halite-envs/SpecEnv), flattened-vars :- FlattenedVars]
  (let [witness-var (->> flattened-vars :$witness first symbol)
        mandatory-vars (::mandatory flattened-vars)
        mandatory-clause (when (seq mandatory-vars)
                           (apply list '= witness-var (map #(list 'if-value (symbol %) true false) (sort mandatory-vars))))
        optional-clauses (->> (dissoc flattened-vars ::spec-id)
                              (remove (comp mandatory-vars first second))
                              (filter (fn [[var-kw info]]
                                        (if (vector? info)
                                          (halite-types/maybe-type? (halite-envs/halite-type-from-var-type senv (second info)))
                                          (contains? info :$witness))))
                              (sort-by first)
                              (map (fn [[opt-var-kw info]]
                                     (list '=>
                                           (if (vector? info)
                                             (list 'if-value (symbol (first info)) true false)
                                             (symbol (first (:$witness info))))
                                           witness-var))))]
    (mk-junct 'and (cond->> optional-clauses
                     mandatory-clause (cons mandatory-clause)))))

(s/defn ^:private optionality-constraints :- halite-envs/SpecInfo
  [senv :- (s/protocol halite-envs/SpecEnv), flattened-vars :- FlattenedVars, spec-info :- halite-envs/SpecInfo]
  (->> flattened-vars
       (filter (fn [[var-kw info]] (and (map? info) (contains? info :$witness))))
       (reduce
        (fn [spec-info [var-kw info]]
          (let [cexpr (optionality-constraint senv info)
                spec-info (if (= true cexpr)
                            spec-info
                            (->> [(str "$" (name (first (:$witness info)))) cexpr]
                                 (update spec-info :constraints conj)))]
            (optionality-constraints senv info spec-info)))
        spec-info)))

(s/defn ^:private guard-optional-instance-literal
  [[witness-kw htype] mandatory inst-expr]
  (->> mandatory
       (reduce
        (fn [inst-expr var-kw]
          (list 'if-value (symbol var-kw) inst-expr '$no-value))
        inst-expr)
       (list 'when (symbol witness-kw))))

(s/defn ^:private flattened-vars-as-instance-literal
  [{::keys [mandatory spec-id] :keys [$witness] :as flattened-vars} :- FlattenedVars]
  (cond->>
   (reduce
    (fn [inst-expr [var-kw v]]
      (assoc inst-expr var-kw
             (if (vector? v)
               (symbol (first v))
               (flattened-vars-as-instance-literal v))))
    {:$type spec-id}
    (dissoc flattened-vars ::spec-id ::mandatory :$witness))
    $witness (guard-optional-instance-literal $witness mandatory)))

(s/defn spec-ify-bound :- halite-envs/SpecInfo
  "Compile the spec-bound into a self-contained halite spec that explicitly states the constraints
  implied by the bound. The resulting spec is self-contained in the sense that:
    * it references no other specs, and
    * it directly incorporates as many spec constraints as possible

  The expressed bound is 'sound', in the sense that every valid instance of the bounded spec
  can be translated into a valid instance of the bound.
  However, the expressed bound is generally not 'tight': there will usually be valid instances of the bound
  that do not correspond to any valid instance of the bounded spec."
  [senv :- (s/protocol halite-envs/SpecEnv), spec-bound :- SpecBound]
  ;; First, flatten out the variables we'll need.
  (let [flattened-vars (flatten-vars senv spec-bound)]
    (->>
     {:spec-vars (->> flattened-vars leaves (filter vector?) (into {}))
      :constraints [["vars" (list 'valid? (flattened-vars-as-instance-literal flattened-vars))]]
      :refines-to {}}
     (optionality-constraints senv flattened-vars))))

;;;;;;;;;;; Conversion to Choco ;;;;;;;;;

(s/defn ^:private to-choco-type :- choco-clj/ChocoVarType
  [var-type :- halite-envs/VarType]
  (cond
    (or (= [:Maybe "Integer"] var-type) (= "Integer" var-type)) :Int
    (or (= [:Maybe "Boolean"] var-type) (= "Boolean" var-type)) :Bool
    :else (throw (ex-info (format "BUG! Can't convert '%s' to choco var type" var-type) {:var-type var-type}))))

(s/defn ^:private to-choco-spec :- choco-clj/ChocoSpec
  "Convert a spec-ified bound to a Choco spec."
  [senv :- (s/protocol halite-envs/SpecEnv), spec-info :- halite-envs/SpecInfo]
  {:vars (-> spec-info :spec-vars (update-keys symbol) (update-vals to-choco-type))
   :optionals (->> spec-info :spec-vars
                   (filter (comp halite-types/maybe-type?
                                 (partial halite-envs/halite-type-from-var-type senv)
                                 val))
                   (map (comp symbol key)) set)
   :constraints (->> spec-info :constraints (map second) set)})

;;;;;;;;;; Convert choco bounds to spec bounds ;;;;;;;;;

(s/defschema ^:private UnflattenedChocoBounds
  {halite-types/BareKeyword (s/cond-pre choco-clj/VarBound (s/recursive #'UnflattenedChocoBounds))})

(s/defn ^:private unflatten-choco-bounds :- UnflattenedChocoBounds
  [choco-bounds :- {[halite-types/BareKeyword] choco-clj/VarBound}]
  (->> choco-bounds
       (group-by (comp first key))
       (map (fn [[var-kw bounds]]
              (let [[var-kws var-bound] (first bounds)
                    bounds (into {} bounds)]
                [var-kw (if (= [var-kw] var-kws)
                          var-bound
                          (-> bounds
                              (update-keys #(drop 1 %))
                              (unflatten-choco-bounds)))])))
       (into {})))

(defn- decompose-var-name [sym]
  (let [sym-name (name sym)]
    (-> sym-name
        (str/split #"\||\?")
        (cond-> (str/ends-with? sym-name "?") (conj "?"))
        (->> (remove empty?) (map keyword)))))

(s/defn ^:private atom-bound :- AtomBound
  [choco-bound :- choco-clj/VarBound]
  (if (or (vector? choco-bound) (set? choco-bound))
    {:$in choco-bound}
    choco-bound))

(s/defn ^:private remove-unset :- AtomBound
  [atom-bound :- AtomBound]
  (if (map? atom-bound)
    (let [in-val (:$in atom-bound)
          in-val (cond
                   (vector? in-val) (subvec in-val 0 2)
                   (set? in-val) (disj in-val :Unset))
          in-val (cond-> in-val (= 1 (count in-val)) (first))]
      (if (or (set? in-val) (vector? in-val))
        {:$in in-val}
        in-val))
    atom-bound))

(s/defn ^:private spec-bound* :- SpecBound
  [senv :- (s/protocol halite-envs/SpecEnv), spec-id :- halite-types/NamespacedKeyword, unflattened-bounds :- UnflattenedChocoBounds]
  (let [{:keys [spec-vars]} (halite-envs/lookup-spec senv spec-id)
        witness-bounds (:? unflattened-bounds)]
    (if (false? witness-bounds)
      :Unset
      (reduce
       (fn [bound [var-kw vtype]]
         (let [htype (halite-envs/halite-type-from-var-type senv vtype)
               unflattened-bound (unflattened-bounds var-kw)]
           (assoc bound var-kw
                  (cond
                    (and (nil? unflattened-bound) (halite-types/spec-type? htype)) {:$type (halite-types/spec-id htype)}
                    (primitive-maybe-type? htype) (cond-> (atom-bound unflattened-bound) (not (halite-types/maybe-type? htype)) (remove-unset))
                    (spec-maybe-type? htype) (spec-bound* senv
                                                          (halite-types/spec-id
                                                           (cond-> htype (halite-types/maybe-type? htype) (second)))
                                                          unflattened-bound)
                    :else (throw (ex-info "BUG! Cannot reconstitute spec bound"
                                          {:spec-id spec-id :unflattened-bound unflattened-bound
                                           :var-kw var-kw :halite-type htype}))))))
       {:$type (if (or (nil? witness-bounds) (true? witness-bounds)) spec-id [:Maybe spec-id])}
       spec-vars))))

(s/defn ^:private to-spec-bound :- SpecBound
  [senv :- (s/protocol halite-envs/SpecEnv), spec-id :- halite-types/NamespacedKeyword, choco-bounds :- choco-clj/VarBounds]
  (-> choco-bounds
      (update-keys decompose-var-name)
      (unflatten-choco-bounds)
      (->> (spec-bound* senv spec-id))))

;;;;;;;;;;;; Main API ;;;;;;;;;;;;;;;;

(s/defschema Opts
  {:default-int-bounds [(s/one s/Int :lower) (s/one s/Int :upper)]})

(def default-options
  (s/validate
   Opts
   {:default-int-bounds [-1000 1000]}))

(s/defn propagate :- SpecBound
  ([senv :- (s/protocol halite-envs/SpecEnv), initial-bound :- SpecBound]
   (propagate senv default-options initial-bound))
  ([senv :- (s/protocol halite-envs/SpecEnv), opts :- Opts, initial-bound :- SpecBound]
   (binding [choco-clj/*default-int-bounds* (:default-int-bounds opts)
             ssa/*next-id* (atom 0)]
     (let [flattened-vars (flatten-vars senv initial-bound)
           lowered-bounds (lower-spec-bound flattened-vars initial-bound)
           spec-ified-bound (spec-ify-bound senv initial-bound)]
       (-> senv
           (ssa/build-spec-ctx (:$type initial-bound))
           (assoc :$propagate/Bounds (ssa/spec-to-ssa senv spec-ified-bound))
           (lowering/lower)
           (lowering/eliminate-runtime-constraint-violations)
           (lowering/eliminate-dos)
           ;; TODO: Figure out why these have to be done together to get the tests to pass...
           (->> (fixpoint #(-> %
                               lowering/cancel-get-of-instance-literal
                               lowering/push-if-value-into-if
                               simplify)))
           :$propagate/Bounds
           (ssa/spec-from-ssa)
           (->> (to-choco-spec senv))
           (choco-clj/propagate lowered-bounds)
           (->> (to-spec-bound senv (:$type initial-bound))))))))
