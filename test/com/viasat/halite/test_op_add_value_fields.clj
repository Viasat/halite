;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-add-value-fields
  (:require [clojure.test :refer :all]
            [com.viasat.halite.op-add-value-fields :as op-add-value-fields]
            [schema.test]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-basic
  (is (= {:$instance-of :ws/A$v1} (op-add-value-fields/add-value-fields-op {:ws/A$v1 {}} {:$instance-of :ws/A$v1})))

  (is (= {:$instance-of :ws/A$v1
          :x {:$enum #{1 2}}}
         (op-add-value-fields/add-value-fields-op {:ws/A$v1 {:fields {:x :Integer}}}
                                                  {:$instance-of :ws/A$v1
                                                   :x {:$enum #{1 2}}})))

  (is (= {:$instance-of :ws/A$v1
          :x {:$value? {:$primitive-type :Boolean}
              :$enum #{1 2}}}
         (op-add-value-fields/add-value-fields-op {:ws/A$v1 {:fields {:x [:Maybe :Integer]}}}
                                                  {:$instance-of :ws/A$v1
                                                   :x {:$enum #{1 2}}}))))

(deftest test-instance-literal
  (is (= {:$instance-literal-type :ws/A$v1
          :x {:$expr #r [:a]}
          :y 7}
         (op-add-value-fields/add-value-fields-op {:ws/A$v1 {:fields {:x [:Maybe :Integer]
                                                                      :y [:Maybe :Integer]}}}
                                                  {:$instance-literal-type :ws/A$v1
                                                   :x {:$expr #r [:a]}
                                                   :y 7}))))

;; (run-tests)
