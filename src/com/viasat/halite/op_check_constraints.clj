;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-check-constraints
  "Evaluate constraints for which all the referenced variables have values."
  (:require [clojure.pprint :as pprint]
            [com.viasat.halite.analysis :as analysis]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.h-err :as h-err]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.eval :as eval]
            [com.viasat.halite.lib.format-errors :as format-errors]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(bom-op/def-bom-multimethod check-constraints-op*
  [spec-env bom]
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
    bom/InstanceValue
    bom/ExpressionBom}
  bom

  #{bom/ConcreteInstanceBom
    bom/AbstractInstanceBom
    bom/InstanceLiteralBom}
  (let [constraints (:$constraints bom)
        failed-constraint-names (->> constraints
                                     (map (fn [[constraint-name constraint-e]]
                                            (let [free-vars (analysis/gather-free-vars constraint-e)
                                                  free-var-keywords (->> free-vars (map keyword))]
                                              (when (every? (fn [var-keyword]
                                                              (bom/is-bom-value? (get bom var-keyword)))
                                                            free-var-keywords)
                                                (let [env (envs/env-from-field-keys free-var-keywords bom)]
                                                  (when (not (eval/eval-expr* {:env env :senv spec-env} constraint-e))
                                                    constraint-name))))))
                                     (remove nil?))]
    (when-not (empty? failed-constraint-names)
      (format-errors/throw-err (h-err/invalid-instance
                                {:spec-id (symbol (bom/get-spec-id bom))
                                 :violated-constraint-labels failed-constraint-names
                                 :value bom
                                 :halite-error :constraint-violation})))
    bom))

(def trace false)

(s/defn check-constraints-op :- bom/Bom
  [spec-env
   bom :- bom/Bom]
  (let [result (check-constraints-op* spec-env bom)]
    (when trace
      (pprint/pprint [:check-constraints-op bom :result result]))
    result))
