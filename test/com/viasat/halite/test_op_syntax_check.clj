;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-syntax-check
  (:require [clojure.test :refer :all]
            [com.viasat.halite.op-syntax-check :as op-syntax-check]
            [schema.test])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-basic
  (is (thrown-with-msg? ExceptionInfo #"Spec not found"
                        (op-syntax-check/syntax-check-op {} {:$type :ws/A$v1})))

  (is (= {:$type :ws/A$v1}
         (op-syntax-check/syntax-check-op {:ws/A$v1 {}}
                                          {:$type :ws/A$v1})))

  (is (thrown-with-msg? ExceptionInfo #"Variable does not exist"
                        (op-syntax-check/syntax-check-op {:ws/A$v1 {}}
                                                         {:$type :ws/A$v1
                                                          :x 19})))

  (is (= {:$type :ws/A$v1
          :x 19}
         (op-syntax-check/syntax-check-op {:ws/A$v1 {:fields {:x :Integer}}}
                                          {:$type :ws/A$v1
                                           :x 19}))))

(deftest test-nested
  (is (thrown-with-msg? ExceptionInfo #"Spec not found"
                        (op-syntax-check/syntax-check-op {:ws/A$v1 {:fields {:b [:Instance :ws/B$v1]
                                                                             :c [:Instance :* #{:ws/C$v1}]}}
                                                          :ws/B$v1 {:fields {:b1 :Integer}}
                                                          :ws/C$v1 {:fields {:x :Integer}}}
                                                         {:$instance-of :ws/A$v1
                                                          :b {:$type :ws/B$v1
                                                              :b1 19}
                                                          :c {:$refines-to :ws/C$v1
                                                              :c1 {:$instance-of :ws/D$v1}}})))

  (is (thrown-with-msg? ExceptionInfo #"Variable does not exist"
                        (op-syntax-check/syntax-check-op {:ws/A$v1 {:fields {:b [:Instance :ws/B$v1]
                                                                             :c [:Instance :* #{:ws/C$v1}]}}
                                                          :ws/B$v1 {:fields {:b1 :Integer}}
                                                          :ws/C$v1 {:fields {:c1 :Integer}}
                                                          :ws/D$v1 {}}
                                                         {:$instance-of :ws/A$v1
                                                          :b {:$type :ws/B$v1
                                                              :b1 19}
                                                          :c {:$refines-to :ws/C$v1
                                                              :c1 {:$instance-of :ws/D$v1
                                                                   :d1 "hi"}}})))

  (let [bom {:$instance-of :ws/A$v1
             :b {:$type :ws/B$v1
                 :b1 19}
             :c {:$refines-to :ws/C$v1
                 :c1 {:$instance-of :ws/D$v1
                      :d1 "hi"}}}]
    (is (= bom
           (op-syntax-check/syntax-check-op {:ws/A$v1 {:fields {:b [:Instance :ws/B$v1]
                                                                :c [:Instance :* #{:ws/C$v1}]}}
                                             :ws/B$v1 {:fields {:b1 :Integer}}
                                             :ws/C$v1 {:fields {:c1 :Integer}}
                                             :ws/D$v1 {:fields {:d1 :String}}}
                                            bom)))))

(deftest test-refinements
  (is (thrown-with-msg? ExceptionInfo #"Variable does not exist"
                        (op-syntax-check/syntax-check-op {:ws/A$v1 {:fields {:x :Integer}}
                                                          :ws/B$v1 {:fields {:b :String}}}
                                                         {:$instance-of :ws/A$v1
                                                          :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                                                                   :q 1}}})))

  (is (thrown-with-msg? ExceptionInfo #"Variable does not exist"
                        (op-syntax-check/syntax-check-op {:ws/A$v1 {:fields {:x :Integer}}
                                                          :ws/B$v1 {:fields {:b :String}}}
                                                         {:$refines-to :ws/A$v1
                                                          :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                                                                   :q 1}}})))

  (let [bom {:$refines-to :ws/A$v1
             :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                      :b 1}}}]
    (is (= bom
           (op-syntax-check/syntax-check-op {:ws/A$v1 {:fields {:x :Integer}}
                                             :ws/B$v1 {:fields {:b :String}}}
                                            bom))))

  (is (thrown-with-msg? ExceptionInfo #"Variable does not exist"
                        (op-syntax-check/syntax-check-op {:ws/A$v1 {:fields {:x :Integer}}
                                                          :ws/B$v1 {:fields {:b :String}}}
                                                         {:$refines-to :ws/A$v1
                                                          :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1
                                                                                        :q 1}}}))))

;; (run-tests)
