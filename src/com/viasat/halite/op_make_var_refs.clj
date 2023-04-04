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
      (var-ref/var-ref (conj path (keyword sym))))))

(s/defn ^:private make-var-refs-instance
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-get
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-get-in
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-equals
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-not-equals
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-rescale
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-if
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-cond
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-when
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-let
  [path
   env :- (s/protocol envs/Env)
   expr]
  (let [[bindings body] (rest expr)
        {env' :env
         bindings' :bindings} (reduce (fn [{:keys [env bindings]} [sym binding-e]]
                                        (let [env' (envs/bind env sym 1)]
                                          {:env env'
                                           :bindings (into bindings
                                                           [sym (make-var-refs path env' binding-e)])}))
                                      {:env env
                                       :bindings []}
                                      (partition 2 bindings))]
    (list 'let
          bindings'
          (make-var-refs path env' body))))

(s/defn ^:private make-var-refs-if-value
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-when-value
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-if-value-let
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-when-value-let
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-union
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-intersection
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-difference
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-first
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-rest
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-conj
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-concat
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-refine-to
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-refines-to?
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-every?
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-any?
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-map
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-filter
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-valid
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-valid?
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-sort-by
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-reduce
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

(s/defn ^:private make-var-refs-fn-application
  [path
   env :- (s/protocol envs/Env)
   expr]
  (let [[op & args] expr]
    (apply list op (->> args
                        (map (partial make-var-refs path env))))))

(s/defn ^:private make-var-refs-coll
  [path
   env :- (s/protocol envs/Env)
   expr]
  (throw (ex-info "todo" {})))

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
                  'get (make-var-refs-get path env expr)
                  'get-in (make-var-refs-get-in path env expr)
                  '= (make-var-refs-equals path env expr)
                  'not= (make-var-refs-not-equals path env expr)
                  'rescale (make-var-refs-rescale path env expr)
                  'if (make-var-refs-if path env expr)
                  'cond (make-var-refs-cond path env expr)
                  'when (make-var-refs-when path env expr)
                  'let (make-var-refs-let path env expr)
                  'if-value (make-var-refs-if-value path env expr)
                  'when-value (make-var-refs-when-value path env expr)
                  'if-value-let (make-var-refs-if-value-let path env expr)
                  'when-value-let (make-var-refs-when-value-let path env expr)
                  'union (make-var-refs-union path env expr)
                  'intersection (make-var-refs-intersection path env expr)
                  'difference (make-var-refs-difference path env expr)
                  'first (make-var-refs-first path env expr)
                  'rest (make-var-refs-rest path env expr)
                  'conj (make-var-refs-conj path env expr)
                  'concat (make-var-refs-concat path env expr)
                  'refine-to (make-var-refs-refine-to path env expr)
                  'refines-to? (make-var-refs-refines-to? path env expr)
                  'every? (make-var-refs-every? path env expr)
                  'any? (make-var-refs-any? path env expr)
                  'map (make-var-refs-map path env expr)
                  'filter (make-var-refs-filter path env expr)
                  'valid (make-var-refs-valid path env expr)
                  'valid? (make-var-refs-valid? path env expr)
                  'sort-by (make-var-refs-sort-by path env expr)
                  'reduce (make-var-refs-reduce path env expr)

                  ;; else:
                  (make-var-refs-fn-application path env expr))
    (coll? expr) (make-var-refs-coll path env expr)
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
