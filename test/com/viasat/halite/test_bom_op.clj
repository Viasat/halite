;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-bom-op
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-op :as bom-op]
            [schema.core :as s]
            [schema.test])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(bom-op/def-bom-multimethod sample-multimethod
  "This is a sample to show the syntax."
  [bom]
  #{Integer
    FixedDecimal
    String
    Boolean
    bom/InstanceValue
    #{}
    []
    bom/PrimitiveBom}
  [:primitive bom]

  bom/AbstractInstanceBom
  [:abstract-instance-bom bom]

  bom/ConcreteInstanceBom
  [:concrete-instance-bom bom]

  bom/NoValueBom
  [:no-value-bom bom])

(deftest test-multimethod
  (is (= [:primitive 100] (sample-multimethod 100)))
  (is (= [:primitive {:$type :test/A$v1
                      :x 100}]
         (sample-multimethod {:$type :test/A$v1
                              :x 100})))
  (is (= [:abstract-instance-bom {:$refines-to :test/A$v1
                                  :x 100}]
         (sample-multimethod {:$refines-to :test/A$v1
                              :x 100})))
  (is (= [:concrete-instance-bom {:$instance-of :test/A$v1
                                  :x 100}]
         (sample-multimethod {:$instance-of :test/A$v1
                              :x 100})))

  (is (= [:no-value-bom {:$value? false}]
         (sample-multimethod {:$value? false}))))

;;;;

(s/defn set-concrete-spec-type :- bom/InstanceBom
  "Produce a new instance by specifying the new type."
  [instance :- bom/InstanceBom
   new-spec-type :- bom/SpecId]
  (-> instance
      (dissoc :$instance-of
              :$refines-to)
      (assoc :$instance-of new-spec-type)))

(s/defn set-abstract-spec-type :- bom/InstanceBom
  [instance :- bom/InstanceBom
   new-spec-type :- bom/SpecId]
  (-> instance
      (dissoc :$instance-of
              :$refines-to)
      (assoc :$refines-to new-spec-type)))

(s/defn to-concrete :- bom/InstanceBom
  [instance :- bom/InstanceBom]
  (set-concrete-spec-type instance (bom/get-spec-id instance)))

(s/defn to-abstract :- bom/InstanceBom
  [instance :- bom/InstanceBom]
  (set-abstract-spec-type instance (bom/get-spec-id instance)))

(declare sample-bom-op)

(bom-op/def-bom-multimethod sample-bom-op
  "This is a sample to show walking the bom tree recursively."
  [bom]
  #{Integer
    FixedDecimal
    String
    Boolean
    bom/InstanceValue
    #{}
    []
    bom/PrimitiveBom
    bom/NoValueBom}
  bom

  bom/AbstractInstanceBom
  (merge (to-concrete bom)
         (update-vals (bom/to-bare-instance bom) sample-bom-op))

  bom/ConcreteInstanceBom
  (merge (to-abstract bom)
         (update-vals (bom/to-bare-instance bom) sample-bom-op)))

(deftest test-sample-bom-op
  (is (= 100
         (sample-bom-op 100)))

  (is (= {:$instance-of :test/A$v1}
         (sample-bom-op {:$refines-to :test/A$v1})))

  (is (= {:$refines-to :test/A$v1}
         (sample-bom-op {:$instance-of :test/A$v1})))

  (is (= {:$refines-to :test/B$v1
          :x {:$instance-of :test/A$v1}
          :z {:$refines-to :test/G$v1}}
         (sample-bom-op {:$instance-of :test/B$v1
                         :x {:$refines-to :test/A$v1}
                         :z {:$instance-of :test/G$v1}}))))

(bom-op/def-bom-multimethod sample-bom-op-with-extra-args-2
  [n x]
  Integer
  (+ n x)

  #{FixedDecimal
    String
    Boolean
    bom/InstanceValue
    #{}
    []
    bom/PrimitiveBom
    bom/AbstractInstanceBom
    bom/ConcreteInstanceBom
    bom/NoValueBom}
  n)

(deftest test-sample-bom-op-with-extra-args
  "When writing an op with additional arguments, the bom arg is expected to be the last one"
  (is (= 101
         (sample-bom-op-with-extra-args-2 1 100)))
  (is (= 1
         (sample-bom-op-with-extra-args-2 1 {:$type :test/A$v1}))))

;; (time (run-tests))
