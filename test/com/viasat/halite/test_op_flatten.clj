;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-flatten
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.op-flatten :as op-flatten]
            [schema.test])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-basic
  (is (= [{:path [:x] :value {:$primitive-type :Integer}}]
         (op-flatten/flatten-op {:$instance-of :ws/A$v1
                                 :x {:$primitive-type :Integer}})))

  (is (= [{:path [:a :x], :value {:$primitive-type :Integer}}]
         (op-flatten/flatten-op {:$instance-of :ws/B$v1
                                 :a {:$instance-of :ws/A$v1
                                     :x {:$primitive-type :Integer}}})))

  (is (= [{:path [:$refinements :ws/B$v1 :a :x] :value {:$primitive-type :Integer}}]
         (op-flatten/flatten-op {:$instance-of :ws/C$v1
                                 :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                                          :a {:$instance-of :ws/A$v1
                                                              :x {:$primitive-type :Integer}}}}})))
  (is (= [{:path [:$concrete-choices :ws/B$v1 :a :x] :value {:$primitive-type :Integer}}]
         (op-flatten/flatten-op {:$refines-to :ws/C$v1
                                 :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1
                                                               :a {:$instance-of :ws/A$v1
                                                                   :x {:$primitive-type :Integer}}}}})))

  (is (= [{:path [:c1], :value {:$primitive-type :Integer}}
          {:path [:$concrete-choices :ws/B$v1 :b1], :value {:$primitive-type :String}}
          {:path [:$concrete-choices :ws/B$v1 :a :x], :value {:$primitive-type :Integer}}
          {:path [:$concrete-choices :ws/B$v1 :a :$refinements :ws/D$v1 :d1 :$valid?], :value true}
          {:path [:$concrete-choices :ws/B$v1 :a :$refinements :ws/D$v1 :d1 :e1], :value {:$primitive-type :Integer}}]
         (op-flatten/flatten-op {:$refines-to :ws/C$v1
                                 :c1 {:$primitive-type :Integer}
                                 :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1
                                                               :b1 {:$primitive-type :String}
                                                               :a {:$instance-of :ws/A$v1
                                                                   :x {:$primitive-type :Integer}
                                                                   :$refinements {:ws/D$v1 {:$instance-of :ws/D$v1
                                                                                            :d1 {:$instance-of :ws/E$v1
                                                                                                 :$valid? true
                                                                                                 :e1 {:$primitive-type :Integer}}}}}}}}))))

(deftest test-optional-field
  (is (= [{:path [:x] :value {:$primitive-type :Integer
                              :$value? {:$primitive-type :Boolean}}}
          {:path [:x :$value?] :value {:$primitive-type :Boolean}}]
         (op-flatten/flatten-op {:$instance-of :ws/A$v1
                                 :x {:$primitive-type :Integer
                                     :$value? {:$primitive-type :Boolean}}}))))

(deftest test-primitive-value
  (is (= [{:path [:x] :value true}]
         (op-flatten/flatten-op {:$instance-of :ws/A$v1
                                 :x true})))
  (is (= [{:path [:x] :value 30}]
         (op-flatten/flatten-op {:$instance-of :ws/A$v1
                                 :x 30})))
  (is (= [{:path [:a :$value?], :value {:$primitive-type :Boolean}}
          {:path [:a :x] :value 30}
          {:path [:b] :value {:$primitive-type :Boolean, :$value? true}}
          {:path [:b :$value?] :value true}]
         (op-flatten/flatten-op {:$instance-of :ws/B$v1
                                 :a {:$instance-of :ws/A$v1
                                     :$value? {:$primitive-type :Boolean}
                                     :x 30}
                                 :b {:$primitive-type :Boolean
                                     :$value? true}}))))

(deftest test-no-value
  (is (= [{:path [:a] :value {:$primitive-type :Integer, :$value? false}}
          {:path [:a :$value?] :value false}]
         (op-flatten/flatten-op {:$instance-of :ws/B$v1
                                 :a {:$primitive-type :Integer
                                     :$value? false}}))))

;; (run-tests)
