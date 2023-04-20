;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.flow-get
  (:require [com.viasat.halite.bom :as bom]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.flow-boolean :as flow-boolean]
            [com.viasat.halite.var-ref :as var-ref]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(declare push-down-get)

(s/defn ^:private push-down-get-if
  [path :- [s/Any]
   field
   expr]
  (let [[_ target then-clause else-clause] expr]
    (flow-boolean/make-if target
                          (push-down-get path field then-clause)
                          (push-down-get path field else-clause))))

(s/defn ^:private push-down-get-when
  [path :- [s/Any]
   field
   expr]
  (let [[_ target then-clause] expr]
    (list 'when
          target
          (push-down-get path field then-clause))))

(s/defn ^:private push-down-get-if-value
  [path :- [s/Any]
   field
   expr]
  (let [[_ target then-clause else-clause] expr]
    (list 'if-value
          target
          (push-down-get path field then-clause)
          (push-down-get path field else-clause))))

(s/defn ^:private push-down-get-when-value
  [path :- [s/Any]
   field
   expr]
  (let [[_ target then-clause] expr]
    (list 'when-value
          target
          (push-down-get path field then-clause))))

(s/defn ^:private push-down-get-if-value-let
  [path :- [s/Any]
   field
   expr]
  (let [[_ [sym target] then-clause else-clause] expr]
    (list 'if-value-let [sym target]
          (push-down-get path field then-clause)
          (push-down-get path field else-clause))))

(s/defn ^:private push-down-get-when-value-let
  [path :- [s/Any]
   field
   expr]
  (let [[_ [sym target] then-clause] expr]
    (list 'when-value-let [sym target]
          (push-down-get path field then-clause))))

(s/defn ^:private push-down-get-get
  [path :- [s/Any]
   field
   expr]
  (let [[_ target accessor] expr]
    (push-down-get path field (push-down-get path accessor target))))

(s/defn ^:private push-down-get-valid
  [path :- [s/Any]
   field
   expr]
  (let [[_ target] expr]
    (list 'valid (push-down-get path field target))))

(s/defn push-down-get :- bom/Expr
  [path :- [s/Any]
   field
   expr :- bom/Expr]
  (cond
    (symbol? expr) (with-meta (flow-boolean/make-if
                               (var-ref/make-var-ref (conj path (keyword expr) :$value?))
                               (var-ref/make-var-ref (conj path (keyword expr) field :$value?))
                               false)
                     {:done? true})
    (map? expr) (get expr field)
    (vector? expr) (get expr field)
    (seq? expr) (condp = (first expr)
                  'if (push-down-get-if path field expr)
                  'when (push-down-get-when path field expr)
                  'if-value (push-down-get-if-value path field expr)
                  'when-value (push-down-get-when-value path field expr)
                  'if-value-let (push-down-get-if-value-let path field expr)
                  'when-value-let (push-down-get-when-value-let path field expr) ;; same general form as if-value-let
                  'get (push-down-get-get path field expr)
                  'valid (push-down-get-valid path field expr)
                  (throw (ex-info "expr not supported in push-down-get" {:expr expr})))
    :default (throw (ex-info "unexpected expr to push-down-get" {:expr expr}))))
