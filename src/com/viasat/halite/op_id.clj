;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-id
  "Mark forms that contain the 'valid' operator with an identifier. Also mark instance literals with ids."
  (:require [clojure.walk :as walk]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-op :as bom-op]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

;;

(def IdContext {:path [s/Any]
                :counter-atom s/Any ;; an atom to generate unique IDs
                })

;;

(defn id-expr [id-context expr]
  (let [{:keys [path counter-atom]} id-context]
    (->> expr
         (walk/postwalk (fn [expr]
                          (cond
                            (and (seq? expr)
                                 (#{'valid 'valid?} (first expr)))
                            (with-meta expr {:id-path
                                             (conj (vec (butlast path))
                                                   :$valid-vars
                                                   (str (last path) "$" (base/next-id counter-atom)))})

                            (map? expr)
                            (with-meta expr {:id-path
                                             (conj (vec (butlast path))
                                                   (str (last path) "$" (base/next-id counter-atom)))})
                            :default
                            expr))))))

;;

(bom-op/def-bom-multimethod id-op*
  [context bom]
  #{Integer
    String
    Boolean
    bom/PrimitiveBom
    bom/ExpressionBom
    bom/NoValueBom
    bom/YesValueBom
    bom/ContradictionBom
    #{}
    []
    bom/InstanceValue
    FixedDecimal}
  bom

  #{bom/ConcreteInstanceBom
    bom/AbstractInstanceBom
    bom/InstanceLiteralBom}
  (let [{:keys [path]} context]
    (-> bom
        (merge (->> bom
                    bom/to-bare-instance-bom
                    (map (fn [[field-name field-bom]]
                           [field-name (id-op* (assoc context :path (conj path field-name)) field-bom)]))
                    (into {})))
        (assoc :$constraints (some->> bom
                                      :$constraints
                                      (map (fn [[constraint-name x]]
                                             [constraint-name (id-expr (assoc context :path
                                                                              (conj path (str constraint-name)))
                                                                       x)]))
                                      (into {})))
        (assoc :$refinements (some-> bom
                                     :$refinements
                                     (map (fn [[other-spec-id sub-bom]]
                                            [other-spec-id (id-expr (assoc context :path (conj path :$refinements other-spec-id)) sub-bom)]))
                                     (into {})))
        (assoc :$concrete-choices (some-> bom
                                          :$concrete-choices
                                          (map (fn [[other-spec-id sub-bom]]
                                                 [other-spec-id (id-expr (assoc context :path (conj path :$concrete-choices other-spec-id)) sub-bom)]))
                                          (into {})))
        base/no-nil-entries)))

(s/defn id-op :- bom/Bom
  [bom :- bom/Bom]
  (id-op* {:path []
           :counter-atom (atom -1)} bom))
