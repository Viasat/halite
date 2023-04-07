;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-flower
  "Lower expressions in boms down into expressions that are supported by propagation."
  (:require [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.fog :as fog]
            [com.viasat.halite.type-check :as type-check]
            [com.viasat.halite.var-ref :as var-ref]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(fog/init)

(def LowerContext {:spec-env (s/protocol envs/SpecEnv)
                   :type-env (s/protocol envs/TypeEnv)
                   :env (s/protocol envs/Env) ;; abuse this to hold local types, i.e. like envs/TypeEnv, but for 'let' values
                   :path [s/Any]})

;;;;

(declare flower)

(s/defn ^:private flower-symbol
  [context :- LowerContext
   sym]
  (let [{:keys [env path]} context]
    (if (= '$no-value sym)
      sym
      (if (contains? (envs/bindings env) sym)
        sym
        (var-ref/make-var-ref (conj path (keyword sym)))))))

(s/defn ^:private flower-instance
  [context :- LowerContext
   expr]
  (let [{:keys [path]} context]
    (-> expr
        (dissoc :$type)
        (update-vals (partial flower context))
        (assoc :$type (:$type expr)))))

(s/defn ^:private flower-get
  [op
   context :- LowerContext
   expr]
  (let [[_ target accessor] expr
        target' (flower context target)]
    (when-not (var-ref/var-ref? target')
      (throw (ex-info "unexpected target of get" {:op op
                                                  :expr expr
                                                  :target' target'})))
    (var-ref/extend-path target' (if (vector? accessor)
                                   accessor
                                   [accessor]))))

(defn- combine-envs [type-env env]
  (reduce (fn [type-env [sym value]]
            (envs/extend-scope type-env sym value))
          type-env
          (envs/bindings env)))

(s/defn ^:private flower-let
  [context :- LowerContext
   expr]
  (let [{:keys [spec-env type-env env path]} context
        [bindings body] (rest expr)
        {env' :env
         bindings' :bindings} (reduce (fn [{:keys [env bindings]} [sym binding-e]]
                                        (let [binding-e' (flower (assoc context :env env) binding-e)
                                              env' (envs/bind env
                                                              sym
                                                              (type-check/type-check spec-env
                                                                                     (combine-envs type-env env)
                                                                                     binding-e))]
                                          {:env env'
                                           :bindings (into bindings
                                                           [sym binding-e'])}))
                                      {:env env
                                       :bindings []}
                                      (partition 2 bindings))]
    (list 'let
          bindings'
          (flower (assoc context :env env') body))))

(s/defn ^:private flower-if-value
  [context :- LowerContext
   expr]
  (let [{:keys [type-env path]} context
        [_ target then-clause else-clause] expr
        target' (flower context target)]
    (when-not (var-ref/var-ref? target')
      (throw (ex-info "unexpected target of if-value" {:expr expr
                                                       :target' target'
                                                       :context context})))
    (list 'if
          (var-ref/extend-path target' [:$value?])
          (flower context then-clause)
          (flower context else-clause))))

(s/defn ^:private flower-if-value-let
  [context :- LowerContext
   expr]
  (let [{:keys [spec-env type-env env path]} context
        [_ [sym target] then-clause else-clause] expr]
    (list 'if-value-let [sym (flower context target)]
          (flower (assoc context :env (envs/bind sym (type-check/type-check spec-env
                                                                            (combine-envs type-env env)
                                                                            target)))
                  then-clause)
          (flower context else-clause))))

(s/defn ^:private flower-when-value-let
  [context :- LowerContext
   expr]
  (let [{:keys [spec-env type-env env path]} context
        [_ [sym target] then-clause] expr]
    (list 'when-value-let [sym (flower context target)]
          (flower (assoc context :env (envs/bind sym (type-check/type-check spec-env
                                                                            (combine-envs type-env env)
                                                                            target)))
                  then-clause))))

(s/defn ^:private flower-refine-to
  [op
   context :- LowerContext
   expr]
  (let [[_ instance spec-id] expr]
    (list op (flower context instance) spec-id)))

(s/defn ^:private flower-fog
  [context :- LowerContext
   expr]
  (let [{:keys [spec-env type-env env]} context]
    (fog/make-fog (type-check/type-check spec-env
                                         (combine-envs type-env env)
                                         expr))))

(s/defn ^:private flower-fn-application
  [context :- LowerContext
   expr]
  (let [[op & args] expr]
    (apply list op (->> args
                        (map (partial flower context))))))

(s/defn ^:private flower-vector
  [context :- LowerContext
   expr]
  (->> expr
       (mapv (partial flower context))))

(s/defn ^:private flower-set
  [context :- LowerContext
   expr]
  (->> expr
       (map (partial flower context))
       set))

(s/defn flower
  [context :- LowerContext
   expr]
  (cond
    (boolean? expr) expr
    (base/integer-or-long? expr) expr
    (base/fixed-decimal? expr) expr
    (string? expr) expr
    (symbol? expr) (flower-symbol context expr)
    (map? expr) (flower-instance context expr)
    (seq? expr) (condp = (first expr)
                  'get (flower-get 'get context expr)
                  'get-in (flower-get 'get-in context expr) ;; same form as get
                  'let (flower-let context expr)
                  'if-value (flower-if-value context expr)
                  'if-value-let (flower-if-value-let context expr)
                  'when-value-let (flower-when-value-let context expr)
                  'refine-to (flower-refine-to 'refine-to context expr)
                  'refines-to? (flower-refine-to 'refines-to? context expr) ;; same form as refine-to
                  'every? (flower-fog context expr)
                  'any? (flower-fog context expr) ;; same form as every?
                  'map (flower-fog context expr) ;; same form as every?
                  'filter (flower-fog context expr) ;; same form as every?
                  'sort-by (flower-fog context expr) ;; same form as every?
                  'reduce (flower-fog context expr)

                  ;; else:
                  (flower-fn-application context expr))
    (vector? expr) (flower-vector context expr)
    (set? expr) (flower-set context expr)
    :else (throw (ex-info "unrecognized halite form" {:expr expr}))))

;;;;

(bom-op/def-bom-multimethod flower-op*
  [context bom]
  #{Integer
    FixedDecimal
    String
    Boolean
    bom/PrimitiveBom
    bom/NoValueBom
    bom/YesValueBom
    bom/ContradictionBom
    #{}
    []
    bom/InstanceValue}
  bom

  #{bom/ConcreteInstanceBom
    bom/AbstractInstanceBom}
  (let [{:keys [spec-env path]} context
        spec-id (bom/get-spec-id bom)
        spec-info (envs/lookup-spec spec-env spec-id)]
    (-> bom
        (merge (->> bom
                    bom/to-bare-instance-bom
                    (map (fn [[field-name field-bom]]
                           [field-name (flower-op* (assoc context :path (conj path field-name)) field-bom)]))
                    (into {})))
        (assoc :$constraints (some-> bom
                                     :$constraints
                                     (update-vals (partial flower (assoc context
                                                                         :type-env (envs/type-env-from-spec spec-info))))))
        (assoc :$refinements (some-> bom
                                     :$refinements
                                     (map (fn [[other-spec-id sub-bom]]
                                            [other-spec-id (flower-op* (assoc context :path (conj path other-spec-id)) sub-bom)]))
                                     (into {})))
        (assoc :$concrete-choices (some-> bom
                                          :$concrete-choices
                                          (map (fn [[other-spec-id sub-bom]]
                                                 [other-spec-id (flower-op* (assoc context :path (conj path other-spec-id)) sub-bom)]))
                                          (into {})))
        base/no-nil-entries)))

(s/defn flower-op
  [spec-env :- (s/protocol envs/SpecEnv)
   env :- (s/protocol envs/Env)
   bom]
  (flower-op* {:spec-env spec-env
               :env env
               :path []}
              bom))
