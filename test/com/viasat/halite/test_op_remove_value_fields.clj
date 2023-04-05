;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-remove-value-fields
  (:require [clojure.test :refer :all]
            [com.viasat.halite.op-remove-value-fields :as op-remove-value-fields]
            [schema.test]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-basic
  (is (= {:$instance-of :ws/A$v1}
         (op-remove-value-fields/remove-value-fields-op
          {:ws/A$v1 {}} {:$instance-of :ws/A$v1})))

  (is (= {:$instance-of :ws/A$v1
          :x {:$enum #{1 2}}}
         (op-remove-value-fields/remove-value-fields-op {:ws/A$v1 {:fields {:x :Integer}}}
                                                        {:$instance-of :ws/A$v1
                                                         :x {:$enum #{1 2}
                                                             :$value? {:$primitive-type :Boolean}}})))

  (is (= {:$instance-of :ws/A$v1
          :x {:$enum #{1 2}}}
         (op-remove-value-fields/remove-value-fields-op {:ws/A$v1 {:fields {:x [:Maybe :Integer]}}}
                                                        {:$instance-of :ws/A$v1
                                                         :x {:$enum #{1 2}
                                                             :$value? {:$primitive-type :Boolean}}}))))

;; (run-tests)
