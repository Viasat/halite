;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-contradictions
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.op-contradictions :as op-contradictions]
            [schema.test])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-basic
  (let [bom {:$refines-to :ws/A$v1
             :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1}}}]
    (is (= bom (op-contradictions/bubble-up-contradictions bom))))

  (is (= bom/no-value-bom
         (op-contradictions/bubble-up-contradictions {:$instance-of :ws/B$v1
                                                      :x bom/contradiction-bom})))
  (is (= bom/contradiction-bom
         (op-contradictions/bubble-up-contradictions {:$instance-of :ws/B$v1
                                                      :$value? true
                                                      :x bom/contradiction-bom}))))

(deftest test-concrete-choices
  (is (= bom/no-value-bom
         (op-contradictions/bubble-up-contradictions {:$refines-to :ws/A$v1
                                                      :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1
                                                                                    :x bom/contradiction-bom}}})))
  (is (= bom/contradiction-bom
         (op-contradictions/bubble-up-contradictions {:$refines-to :ws/A$v1
                                                      :$value? true
                                                      :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1
                                                                                    :x bom/contradiction-bom}}}))))

(deftest test-refinements
  (is (= bom/no-value-bom
         (op-contradictions/bubble-up-contradictions {:$refines-to :ws/A$v1
                                                      :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                                                               :x bom/contradiction-bom}}})))

  (is (= bom/contradiction-bom
         (op-contradictions/bubble-up-contradictions {:$refines-to :ws/A$v1
                                                      :$value? true
                                                      :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                                                               :x bom/contradiction-bom}}}))))

;; (run-tests)
