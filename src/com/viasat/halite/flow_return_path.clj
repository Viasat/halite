;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.flow-return-path
  "Turn a non-lowered expression into a lowered boolean expression indicating whether or not the expression produces a value."
  (:require [clojure.math :as math]
            [com.viasat.halite.flow-boolean :as flow-boolean]
            [com.viasat.halite.flow-get :as flow-get]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.eval :as eval]
            [com.viasat.halite.instance-literal :as instance-literal]
            [com.viasat.halite.lib.fixed-decimal :as fixed-decimal]
            [com.viasat.halite.op-add-constraints :as op-add-constraints]
            [com.viasat.halite.op-add-value-fields :as op-add-value-fields]
            [com.viasat.halite.op-conjoin-spec-bom :as op-conjoin-spec-bom]
            [com.viasat.halite.fog :as fog]
            [com.viasat.halite.type-check :as type-check]
            [com.viasat.halite.types :as types]
            [com.viasat.halite.var-ref :as var-ref]
            [com.viasat.halite.walk :as walk2]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(fog/init)

(instance-literal/init)

;;;;

(def ReturnPathContext {:env (s/protocol envs/Env) ;; contains local values, e.g. from 'let' forms
                        :path [s/Any]
                        :lower-f s/Any ;; a function to use to lower expressions
                        })

;;;;

;; Turn an expression into a boolean expression indicating whether or not the expression produces a value.

(declare return-path)

(s/defn ^:private return-path-if
  [context :- ReturnPathContext
   expr]
  (let [{:keys [lower-f]} context
        [_ target then-clause else-clause] expr]
    (flow-boolean/make-if (lower-f target)
                          (return-path context then-clause)
                          (return-path context else-clause))))

(s/defn ^:private return-path-when
  [context :- ReturnPathContext
   expr]
  (let [{:keys [lower-f]} context
        [_ target then-clause] expr]
    (flow-boolean/make-if (lower-f target)
                          (return-path context then-clause)
                          false)))

(s/defn ^:private return-path-if-value
  [context :- ReturnPathContext
   expr]
  (let [[_ target then-clause else-clause] expr]
    (flow-boolean/make-if (return-path target)
                          (return-path context then-clause)
                          (return-path context else-clause))))

(s/defn ^:private return-path-when-value
  [context :- ReturnPathContext
   expr]
  (let [[_ target then-clause] expr]
    (flow-boolean/make-if (return-path context target)
                          (return-path context then-clause)
                          false)))

(defn add-binding [env sym value]
  (envs/bind env sym value))

(s/defn ^:private return-path-if-value-let
  [context :- ReturnPathContext
   expr]
  (let [{:keys [env]} context
        [_ [sym target] then-clause else-clause] expr]
    (flow-boolean/make-if (return-path context target)
                          (return-path (assoc context :env (add-binding env sym target))
                                       then-clause)
                          (if (nil? else-clause)
                            false
                            (return-path context else-clause)))))

(s/defn ^:private return-path-get
  [context :- ReturnPathContext
   expr]
  (let [{:keys [path]} context
        [_ target accessor] expr
        result (flow-get/push-down-get path accessor target)]
    (if (:done? (meta result))
      result
      (return-path context result))))

(s/defn ^:private return-path-symbol
  [context :- ReturnPathContext
   sym]
  (let [{:keys [env path]} context]
    (if (= '$no-value sym)
      false
      (if (contains? (envs/bindings env) sym)
        (return-path context ((envs/bindings env) sym))
        (var-ref/make-var-ref (conj path (keyword sym) :$value?))))))

(s/defn return-path
  [context :- ReturnPathContext
   expr]
  (cond
    (boolean? expr) true
    (base/integer-or-long? expr) true
    (base/fixed-decimal? expr) true
    (string? expr) true
    (symbol? expr) (return-path-symbol context expr)
    (keyword? expr) (throw (ex-info "unexpected expr to return-path" {:expr expr}))
    (map? expr) true
    (seq? expr) (condp = (first expr)
                  'if (return-path-if context expr)
                  'when (return-path-when context expr)
                  'if-value (return-path-if-value context expr)
                  'when-value (return-path-when-value context expr)
                  'if-value-let (return-path-if-value-let context expr)
                  'when-value-let (return-path-if-value-let context expr) ;; same general form as if-value-let
                  'get (return-path-get context expr)
                  'get-in (ex-info "return-path not implemented for expr" {:expr expr})
                  'inc true
                  'valid (let [id-path (:id-path (meta expr))]
                           (when (nil? id-path)
                             (throw (ex-info "expected id-path in metadata" {:expr expr
                                                                             :meta (meta expr)})))
                           (var-ref/make-var-ref id-path))
                  (throw (ex-info "return-path not implemented for expr" {:expr expr})))
    (set? expr) true
    (vector? expr) true
    :default (throw (ex-info "unexpected expr to return-path" {:expr expr}))))
