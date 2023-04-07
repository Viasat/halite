;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-flower
  "Lower expressions in boms down into expressions that are supported by propagation."
  (:require [clojure.math :as math]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.lib.fixed-decimal :as fixed-decimal]
            [com.viasat.halite.fog :as fog]
            [com.viasat.halite.type-check :as type-check]
            [com.viasat.halite.types :as types]
            [com.viasat.halite.var-ref :as var-ref]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(fog/init)

(def LowerContext {:spec-env (s/protocol envs/SpecEnv)
                   :spec-type-env (s/protocol envs/TypeEnv) ;; holds the types coming from the contextual spec
                   :type-env (s/protocol envs/TypeEnv) ;; holds local types, e.g. from 'let' forms
                   :env (s/protocol envs/Env) ;; contains local values, e.g. from 'let' forms
                   :path [s/Any]})

;;;;

(defn- combine-envs [spec-type-env type-env]
  (reduce (fn [effective-type-env [sym value]]
            (envs/extend-scope effective-type-env sym value))
          spec-type-env
          (envs/scope type-env)))

(defn- expression-type [context expr]
  (let [{:keys [spec-env spec-type-env type-env path]} context]
    (type-check/type-check spec-env
                           (combine-envs spec-type-env type-env)
                           expr)))

(defn- non-root-fog? [x]
  (and (fog/fog? x)
       (not (#{:Integer :Boolean} (fog/get-type x)))))

;;;;

(declare flower)

(s/defn ^:private flower-fog
  [context :- LowerContext
   expr]
  (fog/make-fog (expression-type context expr)))

(s/defn ^:private flower-symbol
  [context :- LowerContext
   sym]
  (let [{:keys [env path]} context]
    (if (= '$no-value sym)
      sym
      (if (contains? (envs/bindings env) sym)
        ((envs/bindings env) sym)
        (var-ref/make-var-ref (conj path (keyword sym)))))))

(s/defn ^:private flower-instance
  [context :- LowerContext
   expr]
  (let [{:keys [path]} context
        new-contents (-> expr
                         (dissoc :$type)
                         (update-vals (partial flower context))
                         (assoc :$type (:$type expr)))]
    (if (->> new-contents
             vals
             (some non-root-fog?))
      (flower-fog context expr)
      new-contents)))

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

(s/defn ^:private flower-let
  [context :- LowerContext
   expr]
  (let [{:keys [type-env env path]} context
        [bindings body] (rest expr)
        {type-env' :type-env
         env' :env
         bindings' :bindings} (reduce (fn [{:keys [type-env env bindings]} [sym binding-e]]
                                        (let [binding-e' (flower (assoc context
                                                                        :type-env type-env
                                                                        :env env)
                                                                 binding-e)
                                              type-env' (envs/extend-scope type-env
                                                                           sym
                                                                           (expression-type context binding-e))
                                              env' (envs/bind env sym binding-e')]
                                          {:type-env type-env'
                                           :env env'
                                           :bindings (into bindings
                                                           [sym binding-e'])}))
                                      {:type-env type-env
                                       :env env
                                       :bindings []}
                                      (partition 2 bindings))
        body' (flower (assoc context
                             :type-env type-env'
                             :env env')
                      body)]
    (if (or (->> bindings'
                 (partition 2)
                 (map second)
                 (some non-root-fog?))
            (non-root-fog? body'))
      (flower-fog context expr)
      body')))

(s/defn ^:private flower-if-value
  [context :- LowerContext
   expr]
  (let [{:keys [spec-type-env type-env]} context
        [_ target then-clause else-clause] expr
        target' (flower context target)]
    (when-not (var-ref/var-ref? target')
      (throw (ex-info "unexpected target of if-value or when-value" {:expr expr
                                                                     :target' target'
                                                                     :context context})))
    (when-not (->> target
                   (envs/lookup-type* (combine-envs type-env spec-type-env)))
      (throw (ex-info "symbol not found" {:target target
                                          :type-env (envs/scope type-env)})))
    (let [then-clause' (flower (assoc context
                                      :type-env (envs/extend-scope type-env
                                                                   target
                                                                   (->> target
                                                                        (envs/lookup-type*
                                                                         (if (->> target
                                                                                  (envs/lookup-type* spec-type-env))
                                                                           spec-type-env
                                                                           type-env))
                                                                        types/no-maybe)))
                               then-clause)
          else-clause' (some->> else-clause
                                (flower context))
          target' (var-ref/extend-path target' [:$value?])]
      (if (or (non-root-fog? then-clause')
              (and (not (nil? else-clause'))
                   (non-root-fog? else-clause')))
        (flower-fog context expr)
        (if (nil? else-clause')
          (list 'when
                target'
                then-clause')
          (list (if (nil? else-clause') 'when 'if)
                target'
                then-clause'
                else-clause'))))))

(s/defn ^:private flower-if-value-let
  [context :- LowerContext
   expr]
  (let [{:keys [type-env]} context
        [_ [sym target] then-clause else-clause] expr
        target' (flower context target)
        then-clause' (flower (assoc context :type-env (envs/extend-scope type-env sym (expression-type context target)))
                             then-clause)
        else-clause' (flower context else-clause)]
    (if (or (non-root-fog? target')
            (non-root-fog? then-clause')
            (non-root-fog? else-clause'))
      (flower-fog context expr)
      (list 'if-value-let [sym target']
            then-clause'
            else-clause'))))

(s/defn ^:private flower-when-value-let
  [context :- LowerContext
   expr]
  (let [{:keys [type-env]} context
        [_ [sym target] body] expr
        target' (flower context target)
        body' (flower (assoc context :type-env (envs/extend-scope sym (expression-type context target)))
                      body)]
    (if (or (non-root-fog? target')
            (non-root-fog? body'))
      (flower-fog context expr)
      (list 'when-value-let [sym target']
            body'))))

(s/defn ^:private flower-refine-to
  [op
   context :- LowerContext
   expr]
  (let [[_ instance spec-id] expr
        instance' (flower context instance)]
    (if (non-root-fog? instance')
      (flower-fog context expr)
      (list op instance' spec-id))))

(s/defn ^:private flower-rescale
  [context :- LowerContext
   expr]
  (let [[_ target-form new-scale] expr
        target-scale (types/decimal-scale (expression-type context target-form))
        target-form' (flower context target-form)]
    (let [shift (- target-scale new-scale)]
      (if (= shift 0)
        target-form'
        (list (if (> shift 0) 'div '*)
              target-form'
              (->> shift abs (math/pow 10) long))))))

(s/defn ^:private flower-fn-application
  [context :- LowerContext
   expr]
  (let [[op & args] expr
        args' (->> args
                   (map (partial flower context)))]
    (if (some non-root-fog? args')
      (flower-fog context expr)
      (apply list op args'))))

(defn- flower-fixed-decimal [expr]
  (-> expr fixed-decimal/extract-long second))

(s/defn flower
  [context :- LowerContext
   expr]
  (cond
    (boolean? expr) expr
    (base/integer-or-long? expr) expr
    (base/fixed-decimal? expr) (flower-fixed-decimal expr)
    (string? expr) expr
    (symbol? expr) (flower-symbol context expr)
    (map? expr) (flower-instance context expr)
    (seq? expr) (condp = (first expr)
                  'get (flower-get 'get context expr)
                  'get-in (flower-get 'get-in context expr) ;; same form as get
                  'let (flower-let context expr)
                  'if-value (flower-if-value context expr)
                  'when-value (flower-if-value context expr) ;; also handles 'when-value
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

                  'rescale (flower-rescale context expr)
                  ;; else:
                  (flower-fn-application context expr))
    (vector? expr) (flower-fog context expr)
    (set? expr) (flower-fog context expr)
    :else (throw (ex-info "unrecognized halite form" {:expr expr}))))

;;;;

(bom-op/def-bom-multimethod flower-op*
  [context bom]
  #{Integer
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

  FixedDecimal
  (flower-fixed-decimal bom)

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
                                                                         :spec-type-env (envs/type-env-from-spec spec-info))))))
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
   bom]
  (flower-op* {:spec-env spec-env
               :type-env (envs/type-env {})
               :env (envs/env {})
               :path []}
              bom))
