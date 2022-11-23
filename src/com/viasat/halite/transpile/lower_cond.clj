;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.transpile.lower-cond
  "Lower 'cond' forms to 'if' forms."
  (:require [com.viasat.halite.base :as base]
            [com.viasat.halite.envs :as envs]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(declare lower-cond-in-expr)

;;

(s/defn ^:private lower-cond-in-collection
  [expr]
  (map lower-cond-in-expr expr))

(s/defn ^:private lower-cond-in-seq
  "Assumes the 'cond expressions have been type-checked and have valid number of arguments."
  [expr]
  (cond
    (= 'cond (first expr))
    ;; turn 'cond into nested 'ifs
    (lower-cond-in-expr (reduce (fn [if-expr [pred then]]
                                  (list 'if pred then if-expr))
                                (last expr)
                                (reverse (partition 2 (rest expr)))))
    :default
    (apply list (first expr) (lower-cond-in-collection (rest expr)))))

(defn- lower-cond-in-expr
  [expr]
  (cond
    (boolean? expr) expr
    (base/integer-or-long? expr) expr
    (base/fixed-decimal? expr) expr
    (string? expr) expr
    (symbol? expr) expr
    (keyword? expr) expr
    (map? expr) (-> expr
                    (dissoc :$type)
                    (update-vals lower-cond-in-expr)
                    (assoc :$type (:$type expr)))
    (seq? expr) (lower-cond-in-seq expr)
    (set? expr) (set (lower-cond-in-collection expr))
    (vector? expr) (vec (lower-cond-in-collection expr))
    :default (throw (ex-info "unexpected expr to lower-cond-in-expr" {:expr expr}))))

;;

(s/defn ^:private lower-cond-in-expr-object :- envs/ExprObject
  [expr-object :- envs/ExprObject]
  (-> expr-object
      (update :expr lower-cond-in-expr)))

(s/defn ^:private lower-cond-in-spec :- envs/SpecInfo
  [spec :- envs/SpecInfo]
  (-> spec
      (update :constraints #(->> %
                                 (mapv (fn [[cname expr]]
                                         [cname (lower-cond-in-expr expr)]))))
      (update :refines-to #(update-vals % lower-cond-in-expr-object))))

(s/defn lower-cond-in-spec-map :- envs/SpecMap
  [spec-map :- envs/SpecMap]
  (update-vals spec-map lower-cond-in-spec))
