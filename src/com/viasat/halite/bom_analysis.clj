;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.bom-analysis
  (:require [clojure.set :as set]
            [com.viasat.halite.analysis :as analysis]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.types :as types]
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
           (= 1 (count (:$enum bom)))
           (= true (:$value? bom)))
    (first (:$enum bom))
    bom))

(defn collapse-ranges-into-value [bom]
  (if (and
       ;; the bom may have been converted to a primitive value by a prior rule
       (bom/is-primitive-bom? bom)
       (contains? bom :$ranges)
       (= 1 (count (:$ranges bom)))
       (range-size-one? (first (:$ranges bom))))
    (let [value (first (first (:$ranges bom)))]
      (if (= true (:$value? bom))
        value
        (if (nil? (:$enum bom))
          (-> bom (dissoc :$ranges) (assoc :$enum #{value}))
          bom)))
    bom))

(defn detect-empty-enum
  [bom]
  (if (and (contains? bom :$enum)
           (empty? (:$enum bom)))
    bom/no-value-bom
    bom))

(defn detect-empty-concrete-choices
  [bom]
  (if (and (:$concrete-choices bom)
           (zero? (count (:$concrete-choices bom))))
    (if (= true (:$value? bom))
      bom/contradiction-bom
      bom/no-value-bom)
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
  (some->> ranges
           (map range-to-bom-format)
           set))

(defn- tlfc-to-bom [tlfc]
  (let [{:keys [enum ranges]} tlfc]
    (some->> {:$enum enum
              :$ranges (ranges-to-bom-format ranges)}
             base/no-nil-entries)))

(defn collapse-ranges-and-enum
  "Normalize all the ranges and enumerated values to the smallest equivalent"
  [bom]
  (if (or (contains? bom :$enum)
          (contains? bom :$ranges))
    (let [{enum :$enum ranges :$ranges} bom
          {enum' :$enum ranges' :$ranges} (->> (invoke-analysis enum ranges) tlfc-to-bom)
          to-merge (cond
                     (and enum (or (and enum' (zero? (count enum')))
                                   (nil? enum')))
                     (if (= true (:$value? bom))
                       bom/no-value-bom
                       {:$enum #{}})

                     (> (count enum') 0) {:$enum enum'}
                     (> (count ranges') 0) {:$ranges ranges'})]
      (-> bom
          (dissoc :$enum :$ranges)
          (merge to-merge)))
    bom))

(defn- and-ranges
  [ranges-a ranges-b]
  (let [{enum :enum ranges :ranges} (-> (list 'and
                                              (ranges-to-expression-tree ranges-a)
                                              (ranges-to-expression-tree ranges-b))
                                        analysis/compute-tlfc-map
                                        (get 'x))]
    (->> {:$enum enum
          :$ranges (ranges-to-bom-format ranges)}
         base/no-nil-entries)))

;;;;

(s/defn conjoin-boms [a b]
  (let [bom-types (set [(bom-op/type-symbol-of-bom a)
                        (bom-op/type-symbol-of-bom b)])]
    (cond
      ('#{#{Integer bom/NoValueBom}
          #{FixedDecimal bom/NoValueBom}
          #{String bom/NoValueBom}
          #{Boolean bom/NoValueBom}
          #{[] bom/NoValueBom}
          #{#{} bom/NoValueBom}
          #{bom/YesValueBom bom/NoValueBom}} bom-types)
      bom/contradiction-bom

      ('#{#{Integer bom/YesValueBom}
          #{FixedDecimal bom/YesValueBom}
          #{String bom/YesValueBom}
          #{Boolean bom/YesValueBom}
          #{[] bom/YesValueBom}
          #{#{} bom/YesValueBom}} bom-types)
      (let [[a b] (if (bom/is-yes-value-bom? a) [a b] [b a])]
        b)

      ('#{#{bom/NoValueBom}} bom-types)
      bom/no-value-bom

      ('#{#{bom/YesValueBom}} bom-types)
      bom/yes-value-bom

      ('#{#{bom/PrimitiveBom bom/NoValueBom}
          #{bom/InstanceValue bom/NoValueBom}
          #{bom/ConcreteInstanceBom bom/NoValueBom}
          #{bom/AbstractInstanceBom bom/NoValueBom}} bom-types)
      (let [[a b] (if (bom/is-no-value-bom? a) [b a] [a b])]
        (cond
          (= true (:$value? a)) bom/contradiction-bom
          (= false (:$value? a)) bom/no-value-bom
          (nil? (:$value? a)) bom/no-value-bom
          (= {:$primitive-type :Boolean} (:$value? a)) bom/no-value-bom
          :default (throw (ex-info "unhandled merge case" {:a a :b b}))))

      ('#{#{bom/PrimitiveBom bom/YesValueBom}
          #{bom/InstanceValue bom/YesValueBom}
          #{bom/ConcreteInstanceBom bom/YesValueBom}
          #{bom/AbstractInstanceBom bom/YesValueBom}} bom-types)
      (let [[a b] (if (bom/is-yes-value-bom? a) [b a] [a b])]
        (cond
          (= true (:$value? a)) a
          (= false (:$value? a)) bom/contradiction-bom
          (nil? (:$value? a)) (merge a b)))

      ('#{#{Integer}
          #{FixedDecimal}
          #{String}
          #{Boolean}
          #{[]}
          #{#{}}
          #{bom/InstanceValue}} bom-types)
      (if (= a b)
        a
        bom/contradiction-bom)

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
      (let [base-bom-a (dissoc a :$value? :$enum :$ranges)
            base-bom-b (dissoc b :$value? :$enum :$ranges)]
        (when (not= (merge base-bom-a base-bom-b)
                    (merge base-bom-b base-bom-a))
          (throw (ex-info "unexpected case" {:a a :b b
                                             :merge-1 (merge base-bom-a base-bom-b)
                                             :merge-2 (merge base-bom-b base-bom-a)})))
        ;; keep other fields of the bom that we are not accounting for here
        (let [result (if (or (= false (:$value? a)) (= false (:$value? b)))
                       (if (or (= true (:$value? a)) (= true (:$value? b)))
                         bom/contradiction-bom
                         bom/no-value-bom)
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
                                          (and (contains? a :$value?) (not (contains? b :$value?))) (select-keys a [:$value?])
                                          (and (contains? b :$value?) (not (contains? a :$value?))) (select-keys b [:$value?])
                                          (= (:$value? a) (:$value? b)) (select-keys a [:$value?])
                                          (and (= (:$value? a) {:$primitive-type :Boolean}) (= (:$value? b) {:$enum #{true false}})) {:$primitive-type :Boolean}
                                          (and (= (:$value? b) {:$primitive-type :Boolean}) (= (:$value? a) {:$enum #{true false}})) {:$primitive-type :Boolean}

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
                               :default (throw (ex-info "bug: expected a bom to have been produced" {:a a :b b})))))]
          (if (and (map? result)
                   (not (bom/is-no-value-bom? result))
                   (not (bom/is-contradiction-bom? result)))
            (merge base-bom-a
                   base-bom-b
                   result)
            result)))

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
            (let [merged (merge-with conjoin-boms a b)]
              (if (or (:$refinements b)
                      (:$valid? b))
                (dissoc merged :$type)
                (dissoc merged :$instance-of)))))
        (if (or (= true (:$value? a)) (= true (:$value? b)))
          bom/contradiction-bom
          bom/no-value-bom))

      ('#{#{bom/ConcreteInstanceBom}
          #{bom/AbstractInstanceBom}} bom-types)
      (if (and (= (bom/get-spec-id a)
                  (bom/get-spec-id b))
               (not (bom/is-a-no-value-bom? a))
               (not (bom/is-a-no-value-bom? b)))
        (let [result (-> (merge-with conjoin-boms (bom/to-bare-instance-bom a) (bom/to-bare-instance-bom b))
                         (assoc (if (bom/is-abstract-instance-bom? a)
                                  :$refines-to
                                  :$instance-of) (bom/get-spec-id a)
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
                                :$valid? (cond
                                           (or (= true (:$valid? a)) (= true (:$valid? b))) true
                                           (or (:$valid? a) (:$valid? b)) (throw (ex-info "did not expect :$valid?" {:a a :b b})))
                                :$refinements (cond
                                                (and (:$refinements a) (:$refinements b))
                                                (merge-with conjoin-boms (:$refinements a) (:$refinements b))

                                                (:$refinements a) (:$refinements a)
                                                (:$refinements b) (:$refinements b)
                                                :default nil)
                                :$concrete-choices (cond
                                                     (and (:$concrete-choices a) (:$concrete-choices b))
                                                     (let [keyset (set/intersection (->> a :$concrete-choices keys set)
                                                                                    (->> b :$concrete-choices keys set))]
                                                       (merge-with conjoin-boms
                                                                   (select-keys a keyset)
                                                                   (select-keys b keyset)))

                                                     (:$concrete-choices a) (:$concrete-choices a)
                                                     (:$concrete-choices b) (:$concrete-choices b)
                                                     :default nil))
                         base/no-nil-entries
                         detect-empty-concrete-choices)]
          (if (= 1 (count (:$enum result)))
            (conjoin-boms (first (:$enum result))
                          (dissoc result :$enum))
            result))
        (if (or (= true (:$value? a)) (= true (:$value? b)))
          bom/contradiction-bom
          (if (bom/is-abstract-instance-bom? a)
            (throw (ex-info "merging abstract boms of different specs not supported" {:a a :b b}))
            bom/no-value-bom)))

      ('#{#{bom/InstanceValue bom/AbstractInstanceBom}
          #{bom/ConcreteInstanceBom bom/AbstractInstanceBom}} bom-types)
      (let [[a b] (if (bom/is-abstract-instance-bom? a) [b a] [a b])
            current-choice (get-in b [:$concrete-choices (bom/get-spec-id a)])]
        (cond
          (and (:$concrete-choices b) current-choice)
          (assoc b :$concrete-choices {(bom/get-spec-id a) (conjoin-boms current-choice a)})

          (and (:$concrete-choices b) (not current-choice))
          (if (or (= true (:$value? a)) (= true (:$value? b)))
            bom/contradiction-bom
            bom/no-value-bom)

          :default
          (assoc b :$concrete-choices {(bom/get-spec-id a) a})))

      (and (= 2 (count bom-types))
           (contains? bom-types 'bom/ExpressionBom))
      ;; an expression that appears in an instance literal is not expected to be
      ;; combined with another bom, so this simply leaves the ExpressionBom unchanged
      (let [[a b] (if (bom/is-expression-bom? a) [a b] [b a])]
        a)

      :default bom/contradiction-bom)))

;;;;

(s/defn bom-for-field
  [include-primitive-type-field? tlfc-map [field-name field-type]]
  (-> field-name
      symbol
      tlfc-map
      tlfc-to-bom
      (assoc :$value? (when-not (types/maybe-type? field-type)
                        true))
      (assoc :$instance-of (when (->> field-type
                                      types/no-maybe
                                      types/spec-type?)
                             (->> field-type types/no-maybe types/inner-spec-type)))
      (assoc :$refines-to (when (and (->> field-type
                                          types/no-maybe
                                          types/abstract-spec-type?))
                            (->> field-type types/no-maybe types/inner-abstract-spec-type)))
      (assoc :$primitive-type (when include-primitive-type-field?
                                (when-not (or (->> field-type
                                                   types/no-maybe
                                                   types/spec-type?)
                                              (->> field-type
                                                   types/no-maybe
                                                   types/abstract-spec-type?))
                                  (->> field-type types/no-maybe))))
      base/no-nil-entries
      base/no-empty))

(defn- make-conjunct [es]
  (apply list 'and es))

(defn- var-map-from-bom [bom]
  (update-keys (->> bom
                    bom/to-bare-instance-bom
                    (filter (fn [[field-name field-value]]
                              (bom/is-bom-value? field-value)))
                    (mapcat identity)
                    (apply hash-map))
               symbol))

(s/defn compute-tlfc-map
  ([spec-info :- envs/SpecInfo]
   (compute-tlfc-map nil spec-info))
  ([bom
    spec-info :- envs/SpecInfo]
   (let [{:keys [constraints]} spec-info
         constraint-expression (->> constraints
                                    (map second)
                                    make-conjunct)
         constraint-expression (if bom
                                 (analysis/replace-free-vars (var-map-from-bom bom) constraint-expression)
                                 constraint-expression)]
     (analysis/compute-tlfc-map constraint-expression))))

(s/defn bom-for-spec
  ([spec-id
    spec-info :- envs/SpecInfo]
   (bom-for-spec nil spec-id spec-info))
  ([bom
    spec-id
    spec-info :- envs/SpecInfo]
   (let [{:keys [fields]} spec-info
         field-names (->> fields (map first))
         tlfc-map (compute-tlfc-map bom spec-info)]
     (assoc (->> fields
                 (map (partial bom-for-field false tlfc-map))
                 (zipmap field-names)
                 base/no-nil-entries)
            :$instance-of spec-id))))
