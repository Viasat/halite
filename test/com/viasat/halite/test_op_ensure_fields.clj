;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-ensure-fields
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.op-ensure-fields :as op-ensure-fields]
            [schema.test])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-basic
  (is (= {:$instance-of :ws/A$v1
          :x {:$primitive-type :Integer}}
         (op-ensure-fields/ensure-fields-op {:ws/A$v1 {:fields {:x [:Maybe :Integer]}}}
                                            {:$instance-of :ws/A$v1}))))

(deftest test-composition
  (is (= {:$instance-of :ws/B$v1
          :a {:$instance-of :ws/A$v1
              :$value? true
              :x {:$value? true
                  :$primitive-type :Integer}}}
         (op-ensure-fields/ensure-fields-op {:ws/B$v1 {:fields {:a [:Instance :ws/A$v1]}}
                                             :ws/A$v1 {:fields {:x :Integer}}}
                                            {:$instance-of :ws/B$v1
                                             :a {:$instance-of :ws/A$v1
                                                 :$value? true}})))

  (is (= {:$instance-of :ws/B$v1
          :a {:$instance-of :ws/A$v1
              :$value? true
              :x {:$value? true
                  :$primitive-type :Integer}}}
         (op-ensure-fields/ensure-fields-op {:ws/B$v1 {:fields {:a [:Instance :ws/A$v1]}}
                                             :ws/A$v1 {:fields {:x :Integer}}}
                                            {:$instance-of :ws/B$v1}))))

;; (run-tests)
