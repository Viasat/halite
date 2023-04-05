;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-make-var-refs
  "Add constraint expressions from specs to boms"
  (:require [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.var-ref :as var-ref]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

;;;;

(declare make-var-refs)

(s/defn ^:private make-var-refs-symbol
  [path
   env :- (s/protocol envs/Env)
   sym]
  (if (= '$no-value sym)
    sym
    (if (contains? (envs/bindings env) sym)
      sym
      (var-ref/make-var-ref (conj path (keyword sym))))))

(s/defn ^:private make-var-refs-instance
  [path
   env :- (s/protocol envs/Env)
   expr]
  (-> expr
      (dissoc :$type)
      (update-vals (partial make-var-refs path env))
      (assoc :$type (:$type expr))))

(s/defn ^:private make-var-refs-get
  [op
   path
   env :- (s/protocol envs/Env)
   expr]
  (let [[_ target accessor] expr
        target' (make-var-refs path env target)]
    (if (var-ref/var-ref? target')
      (var-ref/extend-path target' (if (vector? accessor)
                                     accessor
                                     [accessor]))
      (list op target' accessor))))

(def ^:private placeholder-value 0)

(s/defn ^:private make-var-refs-let
  [path
   env :- (s/protocol envs/Env)
   expr]
  (let [[bindings body] (rest expr)
        {env' :env
         bindings' :bindings} (reduce (fn [{:keys [env bindings]} [sym binding-e]]
                                        (let [env' (envs/bind env sym placeholder-value)]
                                          {:env env'
                                           :bindings (into bindings
                                                           [sym (make-var-refs path env' binding-e)])}))
                                      {:env env
                                       :bindings []}
                                      (partition 2 bindings))]
    (list 'let
          bindings'
          (make-var-refs path env' body))))

(s/defn ^:private make-var-refs-if-value-let
  [path
   env :- (s/protocol envs/Env)
   expr]
  (let [[_ [sym target] then-clause else-clause] expr]
    (list 'if-value-let [sym (make-var-refs path env target)]
          (make-var-refs path (envs/bind env sym placeholder-value) then-clause)
          (make-var-refs path env else-clause))))

(s/defn ^:private make-var-refs-when-value-let
  [path
   env :- (s/protocol envs/Env)
   expr]
  (let [[_ [sym target] then-clause] expr]
    (list 'when-value-let [sym (make-var-refs path env target)]
          (make-var-refs path (envs/bind env sym placeholder-value) then-clause))))

(s/defn ^:private make-var-refs-refine-to
  [op
   path
   env :- (s/protocol envs/Env)
   expr]
  (let [[_ instance spec-id] expr]
    (list op (make-var-refs path env instance) spec-id)))

(s/defn ^:private make-var-refs-every?
  [op
   path
   env :- (s/protocol envs/Env)
   expr]
  (let [[_ [sym c] e] expr]
    (list op [sym (make-var-refs path env c)] (make-var-refs path (envs/bind env sym placeholder-value) e))))

(s/defn ^:private make-var-refs-reduce
  [path
   env :- (s/protocol envs/Env)
   expr]
  (let [[_ [a-sym a-init] [sym c] e] expr]
    (list 'reduce
          [a-sym (make-var-refs path env a-init)]
          [sym (make-var-refs path env c)]
          (make-var-refs path
                         (-> env
                             (envs/bind a-sym placeholder-value)
                             (envs/bind sym placeholder-value))
                         e))))

(s/defn ^:private make-var-refs-fn-application
  [path
   env :- (s/protocol envs/Env)
   expr]
  (let [[op & args] expr]
    (apply list op (->> args
                        (map (partial make-var-refs path env))))))

(s/defn ^:private make-var-refs-vector
  [path
   env :- (s/protocol envs/Env)
   expr]
  (->> expr
       (mapv (partial make-var-refs path env))))

(s/defn ^:private make-var-refs-set
  [path
   env :- (s/protocol envs/Env)
   expr]
  (->> expr
       (map (partial make-var-refs path env))
       set))

(s/defn make-var-refs
  [path
   env :- (s/protocol envs/Env)
   expr]
  (cond
    (boolean? expr) expr
    (base/integer-or-long? expr) expr
    (base/fixed-decimal? expr) expr
    (string? expr) expr
    (symbol? expr) (make-var-refs-symbol path env expr)
    (map? expr) (make-var-refs-instance path env expr)
    (seq? expr) (condp = (first expr)
                  'get (make-var-refs-get 'get path env expr)
                  'get-in (make-var-refs-get 'get-in path env expr) ;; same form as get
                  'let (make-var-refs-let path env expr)
                  'if-value-let (make-var-refs-if-value-let path env expr)
                  'when-value-let (make-var-refs-when-value-let path env expr)
                  'refine-to (make-var-refs-refine-to 'refine-to path env expr)
                  'refines-to? (make-var-refs-refine-to 'refines-to? path env expr) ;; same form as refine-to
                  'every? (make-var-refs-every? 'every? path env expr)
                  'any? (make-var-refs-every? 'any? path env expr) ;; same form as every?
                  'map (make-var-refs-every? 'map path env expr) ;; same form as every?
                  'filter (make-var-refs-every? 'filter path env expr) ;; same form as every?
                  'sort-by (make-var-refs-every? 'sort-by path env expr) ;; same form as every?
                  'reduce (make-var-refs-reduce path env expr)

                  ;; else:
                  (make-var-refs-fn-application path env expr))
    (vector? expr) (make-var-refs-vector path env expr)
    (set? expr) (make-var-refs-set path env expr)
    :else (throw (ex-info "unrecognized halite form" {:expr expr}))))

;;;;

(bom-op/def-bom-multimethod make-var-refs-op*
  [path bom]
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
                         [field-name (make-var-refs-op* (conj path field-name) field-bom)]))
                  (into {})))
      (assoc :$constraints (some-> bom :$constraints (update-vals (partial make-var-refs path (envs/env {})))))
      (assoc :$refinements (some-> bom
                                   :$refinements
                                   (map (fn [[other-spec-id sub-bom]]
                                          [other-spec-id (make-var-refs-op* (conj path other-spec-id) sub-bom)]))
                                   (into {})))
      (assoc :$concrete-choices (some-> bom
                                        :$concrete-choices
                                        (map (fn [[other-spec-id sub-bom]]
                                               [other-spec-id (make-var-refs-op* (conj path other-spec-id) sub-bom)]))
                                        (into {})))
      base/no-nil-entries))

(s/defn make-var-refs-op [bom]
  (make-var-refs-op* [] bom))
