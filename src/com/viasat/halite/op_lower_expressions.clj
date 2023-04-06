;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-lower-expressions
  "Lower halite expressions to make use of the variables available in the bom."
  (:require [clojure.math :as math]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.lib.fixed-decimal :as fixed-decimal]
            [com.viasat.halite.op-flatten :as op-flatten]
            [com.viasat.halite.types :as types]
            [com.viasat.halite.var-ref :as var-ref]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

;;;;

(declare lower-expression)

(s/defn ^:private lower-expression-instance
  [path-to-bom-map
   path
   env :- (s/protocol envs/Env)
   expr]
  (-> expr
      (dissoc :$type)
      (update-vals (partial lower-expression path-to-bom-map path env))
      (assoc :$type (:$type expr))))

(s/defn ^:private lower-expression-get
  [op
   path-to-bom-map
   path
   env :- (s/protocol envs/Env)
   expr]
  (let [[_ target accessor] expr
        target' (lower-expression path-to-bom-map path env target)]
    (when-not (var-ref/var-ref? target')
      (throw (ex-info "unexpected target of get" {:op op
                                                  :expr expr
                                                  :target' target'})))
    (var-ref/extend-path target' (if (vector? accessor)
                                   accessor
                                   [accessor]))))

(s/defn ^:private lower-expression-let
  [path-to-bom-map
   path
   env :- (s/protocol envs/Env)
   expr]
  (let [[bindings body] (rest expr)
        {env' :env
         bindings' :bindings} (reduce (fn [{:keys [env bindings]} [sym binding-e]]
                                        (let [binding-e' (lower-expression path-to-bom-map path env binding-e)
                                              env' (envs/bind env sym binding-e')]
                                          {:env env'
                                           :bindings (into bindings
                                                           [sym binding-e'])}))
                                      {:env env
                                       :bindings []}
                                      (partition 2 bindings))]
    (list 'let
          bindings'
          (lower-expression path-to-bom-map path env' body))))

(s/defn ^:private lower-expression-if-value
  [path-to-bom-map
   path
   env :- (s/protocol envs/Env)
   expr]
  (let [[_ target then-clause else-clause] expr
        target' (if (and (symbol? target)
                         (contains? (envs/bindings env) target))
                  (get (envs/bindings env) target)
                  (lower-expression path-to-bom-map path env target))]
    (when-not (var-ref/var-ref? target')
      (throw (ex-info "unexpected target of if-value" {:expr expr
                                                       :target' target'
                                                       :env (envs/bindings env)})))
    (list 'if
          (var-ref/extend-path target' [:$value?])
          (lower-expression path-to-bom-map path env then-clause)
          (lower-expression path-to-bom-map path env else-clause))))

(s/defn ^:private lower-expression-if-value-let
  [path-to-bom-map
   path
   env :- (s/protocol envs/Env)
   expr]
  (let [[_ [sym target] then-clause else-clause] expr
        target' (lower-expression path-to-bom-map path env target)]
    (list 'if-value-let [sym target']
          (lower-expression path-to-bom-map path (envs/bind env sym target') then-clause)
          (lower-expression path-to-bom-map path env else-clause))))

(s/defn ^:private lower-expression-when-value-let
  [path-to-bom-map
   path
   env :- (s/protocol envs/Env)
   expr]
  (let [[_ [sym target] then-clause] expr
        target' (lower-expression path-to-bom-map path env target)]
    (list 'when-value-let [sym target']
          (lower-expression path-to-bom-map path (envs/bind env sym target') then-clause))))

(s/defn ^:private lower-expression-refine-to
  [op
   path-to-bom-map
   path
   env :- (s/protocol envs/Env)
   expr]
  (let [[_ instance spec-id] expr]
    (list op (lower-expression path-to-bom-map path env instance) spec-id)))

(def ^:private placeholder-value 0)

(s/defn ^:private lower-expression-every?
  [op
   path-to-bom-map
   path
   env :- (s/protocol envs/Env)
   expr]
  (let [[_ [sym c] e] expr]
    (list op [sym (lower-expression path-to-bom-map path env c)]
          (lower-expression path-to-bom-map path (envs/bind env sym placeholder-value) e))))

(s/defn ^:private lower-expression-reduce
  [path-to-bom-map
   path
   env :- (s/protocol envs/Env)
   expr]
  (let [[_ [a-sym a-init] [sym c] e] expr]
    (list 'reduce
          [a-sym (lower-expression path-to-bom-map path env a-init)]
          [sym (lower-expression path-to-bom-map path env c)]
          (lower-expression path-to-bom-map
                            path
                            (-> env
                                (envs/bind a-sym placeholder-value)
                                (envs/bind sym placeholder-value))
                            e))))

(s/defn ^:private lower-expression-rescale
  [path-to-bom-map
   path
   env :- (s/protocol envs/Env)
   expr]
  (let [[_ target-form new-scale] expr
        target-scale (cond
                       (fixed-decimal/fixed-decimal? target-form) (fixed-decimal/get-scale target-form)
                       (var-ref/var-ref? target-form)
                       (types/decimal-scale (:$primitive-type (path-to-bom-map (var-ref/get-path target-form)))))
        target-form (lower-expression path-to-bom-map path env target-form)]
    (let [shift (- target-scale new-scale)]
      (if (= shift 0)
        target-form
        (list (if (> shift 0) 'div '*)
              target-form
              (->> shift abs (math/pow 10) long))))))

(s/defn ^:private lower-expression-fn-application
  [path-to-bom-map
   path
   env :- (s/protocol envs/Env)
   expr]
  (let [[op & args] expr]
    (apply list op (->> args
                        (map (partial lower-expression path-to-bom-map path env))))))

(s/defn ^:private lower-expression-vector
  [path-to-bom-map
   path
   env :- (s/protocol envs/Env)
   expr]
  (->> expr
       (mapv (partial lower-expression path-to-bom-map path env))))

(s/defn ^:private lower-expression-set
  [path-to-bom-map
   path
   env :- (s/protocol envs/Env)
   expr]
  (->> expr
       (map (partial lower-expression path-to-bom-map path env))
       set))

(s/defn lower-expression
  [path-to-bom-map
   path
   env :- (s/protocol envs/Env)
   expr]
  (cond
    (var-ref/var-ref? expr) expr
    (boolean? expr) expr
    (base/integer-or-long? expr) expr
    (base/fixed-decimal? expr) (-> expr fixed-decimal/extract-long second)
    (string? expr) expr
    (symbol? expr) expr
    (map? expr) (lower-expression-instance path-to-bom-map path env expr)
    (seq? expr) (condp = (first expr)
                  'get (lower-expression-get 'get path-to-bom-map path env expr)
                  'get-in (lower-expression-get 'get-in path-to-bom-map path env expr) ;; same form as get
                  'let (lower-expression-let path-to-bom-map path env expr)
                  'if-value (lower-expression-if-value path-to-bom-map path env expr)
                  'if-value-let (lower-expression-if-value-let path-to-bom-map path env expr)
                  'when-value-let (lower-expression-when-value-let path-to-bom-map path env expr)
                  'refine-to (lower-expression-refine-to 'refine-to path-to-bom-map path env expr)
                  'refines-to? (lower-expression-refine-to 'refines-to? path-to-bom-map path env expr) ;; same form as refine-to
                  'every? (lower-expression-every? 'every? path-to-bom-map path env expr)
                  'any? (lower-expression-every? 'any? path-to-bom-map path env expr) ;; same form as every?
                  'map (lower-expression-every? 'map path-to-bom-map path env expr) ;; same form as every?
                  'filter (lower-expression-every? 'filter path-to-bom-map path env expr) ;; same form as every?
                  'sort-by (lower-expression-every? 'sort-by path-to-bom-map path env expr) ;; same form as every?
                  'reduce (lower-expression-reduce path-to-bom-map path env expr)
                  'rescale (lower-expression-rescale path-to-bom-map path env expr)
                  ;; else:
                  (lower-expression-fn-application path-to-bom-map path env expr))
    (vector? expr) (lower-expression-vector path-to-bom-map path env expr)
    (set? expr) (lower-expression-set path-to-bom-map path env expr)
    :else (throw (ex-info "unrecognized halite form" {:expr expr}))))

;;;;

(bom-op/def-bom-multimethod lower-expression-op*
  [path-to-bom-map path bom]
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
  (-> bom
      (merge (->> bom
                  bom/to-bare-instance-bom
                  (map (fn [[field-name field-bom]]
                         [field-name (lower-expression-op* path-to-bom-map (conj path field-name) field-bom)]))
                  (into {})))
      (assoc :$constraints (some-> bom :$constraints (update-vals (partial lower-expression path-to-bom-map path (envs/env {})))))
      (assoc :$refinements (some-> bom
                                   :$refinements
                                   (map (fn [[other-spec-id sub-bom]]
                                          [other-spec-id (lower-expression-op* path-to-bom-map (conj path other-spec-id) sub-bom)]))
                                   (into {})))
      (assoc :$concrete-choices (some-> bom
                                        :$concrete-choices
                                        (map (fn [[other-spec-id sub-bom]]
                                               [other-spec-id (lower-expression-op* path-to-bom-map (conj path other-spec-id) sub-bom)]))
                                        (into {})))
      base/no-nil-entries))

(defn- flattened-vars-to-bom-map [vars]
  (zipmap (map :path vars) (map :value vars)))

(s/defn lower-expression-op [bom]
  (lower-expression-op* (->> bom op-flatten/flatten-op flattened-vars-to-bom-map) [] bom))
