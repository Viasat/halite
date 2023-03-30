;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.bom-analysis
  (:require [clojure.set :as set]
            [com.viasat.halite.analysis :as analysis]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.lib.fixed-decimal :as fixed-decimal]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

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

(defn- ranges-to-expression-tree [ranges]
  (conj (->> ranges
             (map (fn [[min max]]
                    (list 'and
                          (list '>= 'x min)
                          (list '< 'x max)))))
        'or))

(defn- invoke-analysis
  [enum ranges]
  ;; This is kind of silly to convert to code, but it allows us to tap into the 'analysis' code.
  (-> (list 'and (conj (->> enum
                            (map (fn [v]
                                   (list '= 'x v))))
                       'or)
            (ranges-to-expression-tree ranges))
      analysis/compute-tlfc-map
      (get 'x)))

(defn- range-to-bom-format [range]
  (let [{:keys [min max max-inclusive min-inclusive]} range]
    [(if min-inclusive
       min
       (base/h+ min (get-increment min)))
     (if max-inclusive
       (base/h+ max (get-increment max))
       max)]))

(defn- ranges-to-bom-format [ranges]
  (->> ranges
       (map range-to-bom-format)
       set))

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
                     (> (count ranges') 0) {:$ranges (ranges-to-bom-format ranges')})]
      (-> bom
          (dissoc :$enum :$ranges)
          (merge to-merge)))
    bom))

(defn no-nil-entries [m]
  (into {} (remove (comp nil? val) m)))

(defn- and-ranges
  [ranges-a ranges-b]
  (let [{enum :enum ranges :ranges} (-> (list 'and
                                              (ranges-to-expression-tree ranges-a)
                                              (ranges-to-expression-tree ranges-b))
                                        analysis/compute-tlfc-map
                                        (get 'x))]
    (->> {:$enum enum
          :$ranges (ranges-to-bom-format ranges)}
         no-nil-entries)))

;;;;

(s/defn merge-boms [a b]
  (let [bom-types (set [(bom-op/type-symbol-of-bom a)
                        (bom-op/type-symbol-of-bom b)])]
    (cond
      ('#{#{bom/NoValueBom}
          #{Integer bom/NoValueBom}
          #{FixedDecimal bom/NoValueBom}
          #{String bom/NoValueBom}
          #{Boolean bom/NoValueBom}
          #{[] bom/NoValueBom}
          #{#{} bom/NoValueBom}
          #{bom/PrimitiveBom bom/NoValueBom}
          #{bom/InstanceValue bom/NoValueBom}
          #{bom/ConcreteInstanceBom bom/NoValueBom}
          #{bom/AbstractInstanceBom bom/NoValueBom}} bom-types)
      bom/no-value-bom

      ('#{#{Integer}
          #{FixedDecimal}
          #{String}
          #{Boolean}
          #{[]}
          #{#{}}
          #{bom/InstanceValue}} bom-types)
      (if (= a b)
        a
        bom/no-value-bom)

      ('#{#{Integer bom/PrimitiveBom}
          #{FixedDecimal bom/PrimitiveBom}
          #{String bom/PrimitiveBom}
          #{Boolean bom/PrimitiveBom}
          #{[] bom/PrimitiveBom}
          #{#{} bom/PrimitiveBom}
          #{bom/InstanceValue bom/PrimitiveBom}} bom-types)
      (let [[a b] (if (bom/is-primitive-bom? a)
                    [b a]
                    [a b])]
        (->> (merge b {:$enum (if (:$enum b)
                                (set/intersection #{a} (:$enum b))
                                #{a})})
             collapse-ranges-and-enum
             collapse-enum-into-value))

      ('#{#{bom/PrimitiveBom}} bom-types)
      (if (or (= false (:$value? a)) (= false (:$value? b)))
        bom/no-value-bom
        (let [enum-bom (let [enum-set (cond
                                        (and (:$enum a) (:$enum b)) (set/intersection (:$enum a) (:$enum b))
                                        (:$enum a) (:$enum a)
                                        (:$enum b) (:$enum b)
                                        :default nil)]
                         (when enum-set {:$enum enum-set}))
              ranges-bom (cond
                           (and (:$ranges a) (:$ranges b)) (and-ranges (:$ranges a) (:$ranges b))
                           (:$ranges a) (select-keys a [:$ranges])
                           (:$ranges b) (select-keys b [:$ranges])
                           :default nil)
              value?-bom (cond
                           (or (= true (:$value? a)) (= true (:$value? b))) {:$value? true}
                           (and (nil? (:$value? a)) (nil? (:$value? b))) nil

                           :default (throw (ex-info "unexpected :$value? field" {:a a :b b})))]
          (cond (and enum-bom ranges-bom) (->> (merge enum-bom ranges-bom value?-bom)
                                               detect-empty-enum
                                               collapse-to-no-value
                                               collapse-ranges-and-enum
                                               collapse-enum-into-value
                                               collapse-ranges-into-value)
                enum-bom (->> (merge enum-bom value?-bom)
                              detect-empty-enum
                              collapse-enum-into-value)
                ranges-bom (merge ranges-bom value?-bom)
                value?-bom value?-bom
                :default (throw (ex-info "bug: expected a bom to have been produced" {:a a :b b})))))

      ('#{#{bom/InstanceValue bom/ConcreteInstanceBom}} bom-types)
      (if (and (= (bom/get-spec-id a)
                  (bom/get-spec-id b))
               (not (or (bom/is-a-no-value-bom? a)
                        (bom/is-a-no-value-bom? b))))
        (let [[a b] (if (bom/is-concrete-instance-bom? a) [b a] [a b])]
          (if (:$enum b)
            (if (contains? (:$enum b) a)
              a
              {:$instance-of (bom/get-spec-id a)
               :$value? false})
            (let [merged (merge-with merge-boms a b)]
              (if (or (:$refinements b)
                      (:$accessed? b))
                (dissoc merged :$type)
                (dissoc merged :$instance-of)))))
        bom/no-value-bom)

      ('#{#{bom/ConcreteInstanceBom}} bom-types)
      (if (and (= (bom/get-spec-id a)
                  (bom/get-spec-id b))
               (not (bom/is-a-no-value-bom? a))
               (not (bom/is-a-no-value-bom? b)))
        (-> (merge-with merge-boms (bom/to-bare-instance a) (bom/to-bare-instance b))
            (assoc :$instance-of (bom/get-spec-id a)
                   :$enum (cond
                            (and (:$enum a) (:$enum b)) (set/intersection (:$enum a) (:$enum b))
                            (:$enum a) (:$enum a)
                            (:$enum b) (:$enum b)
                            :default nil)
                   :$value? (cond
                              (and (= true (:$value? a)) (= true (:$value? b))) true
                              (= true (:$value? a)) true
                              (= true (:$value? b)) true
                              (or (:$value a) (:$value b)) (throw (ex-info "did not expect :$value" {:a a :b b}))
                              :default nil)
                   :$accessed? (cond
                                 (or (= true (:$accessed? a)) (= true (:$accessed? b))) true
                                 (or (:$accessed? a) (:$accessed? b)) (throw (ex-info "did not expect :$accessed?" {:a a :b b})))
                   :$refinements (merge-with merge-boms (:$refinements a) (:$refinements b)))
            no-nil-entries)
        bom/no-value-bom)

      ('#{#{bom/InstanceValue bom/AbstractInstanceBom}
          #{bom/ConcreteInstanceBom bom/AbstractInstanceBom}} bom-types)
      (let [[a b] (if (bom/is-abstract-instance-bom? a) [b a] [a b])
            current-choice (get-in b [:$concrete-choices (bom/get-spec-id a)])]
        (assoc b :$concrete-choices {(bom/get-spec-id a) (if current-choice
                                                           (merge-boms current-choice a)
                                                           a)}))

      ('#{#{bom/AbstractInstanceBom}} bom-types)
      (if (= (bom/get-spec-id a)
             (bom/get-spec-id b))
        (-> (merge-with merge-boms (bom/to-bare-instance a) (bom/to-bare-instance b))
            (assoc :$refines-to (bom/get-spec-id a)
                   :$concrete-choices (cond
                                        (and (:$concrete-choices a) (:$concrete-choices b))
                                        (let [keyset (set/intersection (->> a :$concrete-choices keys set)
                                                                       (->> b :$concrete-choices keys set))]
                                          (merge-with merge-boms
                                                      (select-keys a keyset)
                                                      (select-keys b keyset)))

                                        (:$concrete-choices a) (:$concrete-choices a)
                                        (:$concrete-choices b) (:$concrete-choices b)
                                        :default nil))
            no-nil-entries)
        (throw (ex-info "merging abstract boms of different specs not supported" {:a a :b b})))
      :default bom/no-value-bom)))
