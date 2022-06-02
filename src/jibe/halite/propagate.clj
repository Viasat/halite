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

(s/defn ^:private inline-gets
  ([expr] (inline-gets {} expr))
  ([bound-gets :- {s/Symbol s/Any} expr]
   (cond
     (or (boolean? expr) (integer? expr)) expr
     (symbol? expr) (get bound-gets expr expr)
     (seq? expr) (let [[op & args] expr]
                   (condp = op
                     'let (let [[bindings body] args
                                [bound-gets bindings] (reduce
                                                       (fn [[bound-gets bindings] [var-sym expr]]
                                                         (let [expr' (inline-gets bound-gets expr)]
                                                           (if (or (symbol? expr') (and (seq? expr') (= 'get (first expr'))))
                                                             [(assoc bound-gets var-sym expr') bindings]
                                                             [bound-gets (conj bindings var-sym expr')])))
                                                       [bound-gets []]
                                                       (partition 2 bindings))]
                            (cond->> (inline-gets bound-gets body)
                              (seq bindings) (list 'let bindings)))
                     'get (let [[subexpr kw] args]
                            (list 'get (inline-gets bound-gets subexpr) kw))
                     (->> args (map (partial inline-gets bound-gets)) (apply list op))))
     :else (throw (ex-info "BUG! Cannot inline gets" {:bound-gets bound-gets :expr expr})))))

(s/defn ^:private collapse-get-chain :- (s/cond-pre FlattenedVar FlattenedVars)
  [vars :- FlattenedVars get-chain]
  (let [[_get expr var-kw] get-chain
        info (cond
               (symbol? expr)
               (or (vars (keyword expr))
                   (throw (ex-info "Undefined" {:symbol expr :vars vars :skip-constraint? true})))

               (and (seq? expr) (= 'get (first expr)))
               (collapse-get-chain vars expr)

               :else (throw (ex-info "Not a get chain" {:expr get-chain})))]
    (or (some-> var-kw info)
        (throw (ex-info "Couldn't flatten get chain, sub-expr doesn't have var"
                        {:info info :var-kw var-kw :skip-constraint? true})))))

(s/defn ^:private flatten-expr
  [vars :- FlattenedVars, scope :- #{s/Symbol}, expr]
  (cond
    (or (boolean? expr) (integer? expr) (contains? scope expr)) expr
    (symbol? expr) (if (scope expr)
                     expr
                     (let [info (or (some-> expr keyword vars)
                                    (throw (ex-info "Undefined" {:symbol expr :vars vars :skip-constraint? true :scope scope})))]
                       (if (vector? info)
                         (symbol (first info))
                         (throw (ex-info "BUG! Naked composite symbol" {:symbol expr :vars vars})))))
    (seq? expr) (let [[op & args] expr]
                  (condp = op
                    'let (let [[bindings body] args
                               [bindings scope] (reduce
                                                 (fn [[bindings scope] [var-sym expr]]
                                                   [(conj bindings var-sym (flatten-expr vars scope expr)) (conj scope var-sym)])
                                                 [[] scope]
                                                 (partition 2 bindings))]
                           (list 'let bindings (flatten-expr vars scope body)))
                    'get (let [info (collapse-get-chain vars expr)]
                           (if (vector? info)
                             (symbol (first info))
                             (throw (ex-info "BUG! get chain collapsed to composite var"
                                             {:vars vars :expr expr :info info}))))
                    'if-value (let [[value then else] args
                                    then' (flatten-expr vars scope then)
                                    else' (flatten-expr vars scope else)
                                    info (if (and (seq? value) (= 'get (first value)))
                                           (collapse-get-chain vars value)
                                           (or (some-> value keyword vars)
                                               (throw (ex-info "Undefined" {:symbol value :vars vars :skip-constraint? true}))))]
                                (if (vector? info)
                                  (list 'if-value (symbol (first info)) then' else')
                                  (list 'if (symbol (first (:$witness info)))
                                        (reduce
                                         (fn [then mandatory]
                                           (list 'if-value (symbol mandatory) then else'))
                                         then'
                                         (::mandatory info))
                                        else')))
                    (->> args (map (partial flatten-expr vars scope)) (apply list op))))
    :else (throw (ex-info "BUG! Cannot flatten expr" {:vars vars :expr expr}))))

(s/defn ^:private guard-constraint-in-optional-context
  [witnesses :- [halite-types/BareSymbol]
   mandatories :- #{halite-types/BareSymbol}
   boolean-expr]
  (as-> boolean-expr expr
    (reduce
     (fn [expr var-sym] (list 'if-value var-sym expr false))
     expr
     (sort mandatories))
    (list 'if (mk-junct 'and witnesses) expr true)))

(s/defn ^:private inline-spec-constraints
  [sctx :- SpecCtx
   tenv :- (s/protocol halite-envs/TypeEnv)
   vars :- FlattenedVars
   witnesses :- [halite-types/BareSymbol]
   mandatories :- #{halite-types/BareSymbol}
   spec-info :- SpecInfo]
  (let [senv (ssa/as-spec-env sctx)
        spec-to-inline (-> vars ::spec-id sctx)
        old-scope (->> spec-to-inline :spec-vars keys (map symbol) set)]
    (reduce
     (fn [{:keys [derivations] :as spec-info} [cname id]]
       (let [expr (ssa/form-from-ssa old-scope (:derivations spec-to-inline) id)]
         (if-let [expr (try
                         (->> expr (inline-gets) (flatten-expr vars #{'no-value}))
                         ;; TODO: This keeps masking implementation errors, we need a safer way to exclude constraints!
                         (catch clojure.lang.ExceptionInfo ex
                           (when-not (:skip-constraint? (ex-data ex))
                             (throw ex))))]
           (let [[dgraph id] (->> expr
                                  (guard-constraint-in-optional-context witnesses mandatories)
                                  (ssa/form-to-ssa {:senv senv :tenv tenv :env {} :dgraph derivations}))]
             (-> spec-info
                 (assoc :derivations dgraph)
                 (update :constraints conj [cname id])))
           spec-info)))
     spec-info
     (:constraints spec-to-inline))))

(defn- add-constraint
  [sctx {:keys [derivations] :as spec-info} cname form]
  (let [ctx (ssa/make-ssa-ctx sctx spec-info)
        [dgraph id] (ssa/form-to-ssa ctx form)]
    (-> spec-info
        (assoc :derivations dgraph)
        (update :constraints conj [cname id]))))

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

(s/defn ^:private flatten-constraints :- SpecInfo
  ([sctx :- SpecCtx, tenv :- (s/protocol halite-envs/TypeEnv), vars :- FlattenedVars, spec-info :- SpecInfo]
   (flatten-constraints sctx tenv vars [] #{} spec-info))
  ([sctx :- SpecCtx
    tenv :- (s/protocol halite-envs/TypeEnv)
    vars :- FlattenedVars
    witnesses :- [halite-types/BareSymbol]
    mandatories :- #{halite-types/BareSymbol}
    spec-info :- SpecInfo]
   (let [senv (ssa/as-spec-env sctx)
         spec-vars (-> vars ::spec-id sctx :spec-vars)]
     (->> (dissoc vars ::spec-id ::mandatory :$witness)
          (filter (comp map? second))
          (reduce
           (fn [spec-info [var-kw nested-vars]]
             (let [witness-var (some-> nested-vars :$witness first symbol)
                   witnesses (cond-> witnesses witness-var (conj witness-var))
                   mandatories (cond-> mandatories witness-var (into (map symbol (::mandatory nested-vars))))]
               (flatten-constraints sctx tenv nested-vars witnesses mandatories spec-info)))
           (inline-spec-constraints sctx tenv vars witnesses mandatories spec-info))))))

(s/defn ^:private optionality-constraint :- s/Any
  [senv :- (s/protocol halite-envs/SpecEnv), flattened-vars :- FlattenedVars]
  (let [witness-var (->> flattened-vars :$witness first symbol)
        mandatory-vars (::mandatory flattened-vars)
        mandatory-clause (apply list '= witness-var (map #(list 'if-value (symbol %) true false) (sort mandatory-vars)))
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
    (mk-junct 'and (cons mandatory-clause optional-clauses))))

(s/defn ^:private optionality-constraints :- SpecInfo
  [sctx :- SpecCtx, flattened-vars :- FlattenedVars, spec-info :- SpecInfo]
  (let [senv (ssa/as-spec-env sctx)]
    (->> flattened-vars
         (filter (fn [[var-kw info]] (and (map? info) (contains? info :$witness))))
         (reduce
          (fn [spec-info [var-kw info]]
            (->> (optionality-constraint senv info)
                 (add-constraint sctx spec-info (str "$" (name (first (:$witness info)))))
                 (optionality-constraints sctx info)))
          spec-info))))

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
  (binding [ssa/*next-id* (atom 0)]
    (let [spec-id (:$type spec-bound)
          sctx (->> spec-id
                    (ssa/build-spec-ctx senv)
                    (lowering/lower)
                    ;; below this line, we're changing semantics
                    (lowering/eliminate-runtime-constraint-violations)
                    (fixpoint lowering/cancel-get-of-instance-literal)
                    (lowering/eliminate-dos))
          flattened-vars (flatten-vars senv spec-bound)
          new-spec {:spec-vars (->> flattened-vars leaves (filter vector?) (into {}))
                    :constraints []
                    :refines-to {}}
          tenv (halite-envs/type-env-from-spec senv new-spec)]
      (-> new-spec
          (assoc :derivations {})
          (->> (flatten-constraints sctx tenv flattened-vars)
               (optionality-constraints sctx flattened-vars)
               (assoc {} :new/spec)
               (simplify)
               :new/spec)
          (ssa/spec-from-ssa)))))

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
   (binding [choco-clj/*default-int-bounds* (:default-int-bounds opts)]
     (let [flattened-vars (flatten-vars senv initial-bound)
           lowered-bounds (lower-spec-bound flattened-vars initial-bound)]
       (->> initial-bound
            (spec-ify-bound senv)
            (to-choco-spec senv)
            (#(choco-clj/propagate % lowered-bounds))
            (to-spec-bound senv (:$type initial-bound)))))))
