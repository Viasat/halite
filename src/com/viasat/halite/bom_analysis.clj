;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.bom-analysis
  (:require [com.viasat.halite.analysis :as analysis]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.lib.fixed-decimal :as fixed-decimal]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(defn- get-increment [n]
  (if (base/integer-or-long? n)
    1
    (->> n
         fixed-decimal/get-scale
         (analysis/make-fixed-decimal-string 1)
         (fixed-decimal/fixed-decimal-reader))))

(defn- range-size-one?
  "Is the range of a size such that it only contains a single value?"
  [[min max]]
  (= (base/h+ min (get-increment min))
     max))

(defn collapse-to-no-value [bom]
  (if (= bom/no-value-bom (select-keys bom [:$value?]))
    bom/no-value-bom
    bom))

(defn collapse-enum-into-value [bom]
  (if (and (contains? bom :$enum)
           (= 1 (count (:$enum bom))))
    (first (:$enum bom))
    bom))

(defn collapse-ranges-into-value [bom]
  (if (and
       ;; the bom may have been converted to a primitive value by a prior rule
       (bom/is-primitive-bom? bom)
       (contains? bom :$ranges)
       (= 1 (count (:$ranges bom)))
       (range-size-one? (first (:$ranges bom))))
    (first (first (:$ranges bom)))
    bom))

(defn detect-empty-enum
  [bom]
  (if (and (contains? bom :$enum)
           (empty? (:$enum bom)))
    bom/no-value-bom
    bom))

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

(defn collapse-ranges-and-enum
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
