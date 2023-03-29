;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-canon
  (:require [clojure.set :as set]
            [com.viasat.halite.analysis :as analysis]
            [com.viasat.halite.b-err :as b-err]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.lib.fixed-decimal :as fixed-decimal]
            [com.viasat.halite.lib.format-errors :as format-errors]
            [com.viasat.halite.spec :as spec]
            [com.viasat.halite.type-check :as type-check]
            [com.viasat.halite.types :as types]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(defn- collapse-to-no-value [bom]
  (if (= bom/no-value-bom (select-keys bom [:$value?]))
    bom/no-value-bom
    bom))

(defn- get-increment [n]
  (if (base/integer-or-long? n)
    1
    (->> n
         fixed-decimal/get-scale
         (analysis/make-fixed-decimal-string 1)
         (fixed-decimal/fixed-decimal-reader))))

(defn- invoke-analysis
  [enum ranges]
  ;; This is kind of silly to convert to code, but it allows us to tap into the 'analysis' code.
  (-> (list 'and (conj (->> enum
                            (map (fn [v]
                                   (list '= 'x v))))
                       'or)
            (conj (->> ranges
                       (map (fn [[min max]]
                              (list 'and
                                    (list '>= 'x min)
                                    (list '< 'x max)))))
                  'or))
      analysis/compute-tlfc-map
      (get 'x)))

(defn- collapse-ranges-and-enum
  "Normalize all the ranges and enumerated values to the smallest equivalent"
  [bom]
  (if (or (contains? bom :$enum)
          (contains? bom :$ranges))
    (let [{enum :$enum ranges :$ranges} bom
          {enum' :enum ranges' :ranges} (invoke-analysis enum ranges)
          to-merge (cond
                     (and enum (zero? (count enum'))) bom/no-value-bom
                     (> (count enum') 0) {:$enum enum'}
                     (> (count ranges') 0) {:$ranges (->> ranges'
                                                          (map (fn [{:keys [min max max-inclusive min-inclusive]}]
                                                                 [(if min-inclusive
                                                                    min
                                                                    (base/h+ min (get-increment min)))
                                                                  (if max-inclusive
                                                                    (base/h+ max (get-increment max))
                                                                    max)]))
                                                          set)})]
      (-> bom
          (dissoc :$enum :$ranges)
          (merge to-merge)))
    bom))

(defn- range-size-one?
  "Is the range of a size such that it only contains a single value?"
  [[min max]]
  (= (base/h+ min (get-increment min))
     max))

(defn- collapse-enum-into-value [bom]
  (if (and (contains? bom :$enum)
           (= 1 (count (:$enum bom))))
    (first (:$enum bom))
    bom))

(defn- collapse-ranges-into-value [bom]
  (if (and
       ;; the bom may have been converted to a primitive value by a prior rule
       (bom/is-primitive-bom? bom)
       (contains? bom :$ranges)
       (= 1 (count (:$ranges bom)))
       (range-size-one? (first (:$ranges bom))))
    (first (first (:$ranges bom)))
    bom))

(defn- detect-empty-enum
  [bom]
  (if (and (contains? bom :$enum)
           (empty? (:$enum bom)))
    bom/no-value-bom
    bom))

(bom-op/def-bom-multimethod canon-op
  [bom]

  #{Integer
    FixedDecimal
    String
    Boolean
    bom/NoValueBom
    #{}
    []
    bom/InstanceValue}
  bom

  bom/PrimitiveBom
  (->> bom
       detect-empty-enum
       collapse-to-no-value
       collapse-ranges-and-enum
       collapse-enum-into-value
       collapse-ranges-into-value)

  #{bom/ConcreteInstanceBom
    bom/AbstractInstanceBom}
  (let [bom (-> bom collapse-to-no-value detect-empty-enum)]
    (if (bom/is-no-value-bom? bom)
      bom
      (merge bom (-> bom
                     bom/to-bare-instance
                     (update-vals canon-op))))))
