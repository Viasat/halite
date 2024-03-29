;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-type-check
  (:require [clojure.test :refer :all]
            [com.viasat.halite.op-type-check :as op-type-check]
            [schema.test])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-basic
  (is (thrown-with-msg? ExceptionInfo #"spec not found"
                        (op-type-check/type-check-op {} {:$type :ws/A$v1})))

  (is (= {:$type :ws/A$v1}
         (op-type-check/type-check-op {:ws/A$v1 {}}
                                      {:$type :ws/A$v1})))

  (is (thrown-with-msg? ExceptionInfo #"wrong type"
                        (op-type-check/type-check-op {:ws/A$v1 {:fields {:x :Integer}}}
                                                     {:$type :ws/A$v1
                                                      :x "hi"})))

  (is (thrown-with-msg? ExceptionInfo #"unexpected type"
                        (op-type-check/type-check-op {:ws/A$v1 {:fields {:x :Integer}}}
                                                     {:$instance-of :ws/A$v1
                                                      :x "hi"})))

  (is (thrown-with-msg? ExceptionInfo #"unexpected type"
                        (op-type-check/type-check-op {:ws/A$v1 {:fields {:x :Integer}}}
                                                     {:$instance-of :ws/A$v1
                                                      :x {:$enum #{"hi" "bye"}}})))
  (is (thrown-with-msg? ExceptionInfo #"unexpected type"
                        (op-type-check/type-check-op {:ws/A$v1 {:fields {:x :Integer}}}
                                                     {:$instance-of :ws/A$v1
                                                      :x {:$ranges #{[#d "1.0" #d "3.0"]}}})))
  (is (thrown-with-msg? ExceptionInfo #"conflicting types"
                        (op-type-check/type-check-op {:ws/A$v1 {:fields {:x :Integer}}}
                                                     {:$instance-of :ws/A$v1
                                                      :x {:$enum #{1 2}
                                                          :$ranges #{[#d "1.0" #d "3.0"]}}})))

  (let [bom {:$instance-of :ws/A$v1
             :x {:$enum #{1 2}
                 :$ranges #{[0 20]}}}]
    (is (= bom
           (op-type-check/type-check-op {:ws/A$v1 {:fields {:x :Integer}}} bom)))
    (is (= [:Instance :ws/A$v1]
           (op-type-check/determine-type {:ws/A$v1 {:fields {:x :Integer}}} bom))))

  (is (thrown-with-msg? ExceptionInfo #"only optional"
                        (op-type-check/type-check-op {:ws/A$v1 {:fields {:x :Integer}}}
                                                     {:$instance-of :ws/A$v1
                                                      :x {:$value? false}})))

  (let [bom {:$instance-of :ws/A$v1
             :x {:$value? false}}]
    (is (= bom
           (op-type-check/type-check-op {:ws/A$v1 {:fields {:x [:Maybe :Integer]}}} bom))))

  (let [bom {:$refines-to :ws/A$v1
             :x 1}]
    (is (= bom
           (op-type-check/type-check-op {:ws/A$v1 {:fields {:x :Integer}}} bom))))

  (is (thrown-with-msg? ExceptionInfo #"unexpected type"
                        (op-type-check/type-check-op {:ws/A$v1 {:fields {:x :Integer}}}
                                                     {:$refines-to :ws/A$v1
                                                      :x "hi"})))

  (is (thrown-with-msg? ExceptionInfo #"unexpected type"
                        (op-type-check/type-check-op {:ws/A$v1 {:fields {:b [:Instance :ws/B$v1]}}}
                                                     {:$instance-of :ws/A$v1
                                                      :b {:$refines-to :ws/B$v1}})))

  (is (thrown-with-msg? ExceptionInfo #"spec not found"
                        (op-type-check/type-check-op {:ws/A$v1 {:fields {:b [:Instance :* #{:ws/B$v1}]}}}
                                                     {:$instance-of :ws/A$v1
                                                      :b {:$instance-of :ws/B$v1}})))

  (is (thrown-with-msg? ExceptionInfo #"cannot use an abstract instance"
                        (op-type-check/type-check-op {:ws/A$v1 {:fields {:b [:Instance :* #{:ws/B$v1}]}}
                                                      :ws/B$v1 {:abstract? true}}
                                                     {:$instance-of :ws/A$v1
                                                      :b {:$instance-of :ws/B$v1}})))

  (let [bom {:$instance-of :ws/A$v1
             :b {:$refines-to :ws/B$v1}}]
    (is (= bom
           (op-type-check/type-check-op {:ws/A$v1 {:fields {:b [:Instance :* #{:ws/B$v1}]}}} bom))))

  (is (thrown-with-msg? ExceptionInfo #"only optional"
                        (op-type-check/type-check-op {:ws/A$v1 {:fields {:b [:Instance :ws/B$v1]}}}
                                                     {:$instance-of :ws/A$v1
                                                      :b {:$instance-of :ws/B$v1
                                                          :$value? true}})))

  ;; typo in value field
  (is (thrown-with-msg? ExceptionInfo #"does not match schema"
                        (op-type-check/type-check-op {:ws/A$v1 {:fields {:b [:Instance :ws/B$v1]}}}
                                                     {:$instance-of :ws/A$v1
                                                      :b {:$instance-of :ws/B$v1
                                                          :$value true}})))

  (is (thrown-with-msg? ExceptionInfo #"field not found"
                        (op-type-check/type-check-op {:ws/A$v1 {:fields {:x :Integer}}}
                                                     {:$refines-to :ws/A$v1
                                                      :y "hi"})))

  (let [bom {:$refines-to :ws/A$v1
             :x {:$enum #{[1 2] [3 4]}}}]
    (is (= bom
           (op-type-check/type-check-op {:ws/A$v1 {:fields {:x [:Vec :Integer]}}} bom))))

  (is (thrown-with-msg? ExceptionInfo #"unexpected type"
                        (op-type-check/type-check-op {:ws/A$v1 {:fields {:x [:Vec :Integer]}}}
                                                     {:$refines-to :ws/A$v1
                                                      :x {:$enum #{[#d "1.0" #d "2.0"] [#d "3.0" #d "4.0"]}}})))
  (is (thrown-with-msg? ExceptionInfo #"do not match"
                        (op-type-check/type-check-op {:ws/A$v1 {:fields {:x [:Vec :Integer]}}}
                                                     {:$refines-to :ws/A$v1
                                                      :x {:$enum #{[#d "1.0" #d "2.0"] [#d "3.0" #d "4.01"]}}})))

  (let [bom {:$refines-to :ws/A$v1
             :x {:$enum #{#{1 2} #{3 4}}}}]
    (is (= bom
           (op-type-check/type-check-op {:ws/A$v1 {:fields {:x [:Set :Integer]}}} bom))))

  (is (thrown-with-msg? ExceptionInfo #"unexpected type"
                        (op-type-check/type-check-op {:ws/A$v1 {:fields {:x [:Set :Integer]}}}
                                                     {:$refines-to :ws/A$v1
                                                      :x {:$enum #{#{#d "1.0" #d "2.0"} #{#d "3.0" #d "4.0"}}}})))
  (is (thrown-with-msg? ExceptionInfo #"do not match"
                        (op-type-check/type-check-op {:ws/A$v1 {:fields {:x [:Set :Integer]}}}
                                                     {:$refines-to :ws/A$v1
                                                      :x {:$enum #{#{#d "1.0" #d "2.0"} #{#d "3.0" #d "4.01"}}}}))))

(deftest test-refinements
  (is (thrown-with-msg? ExceptionInfo #"field not found"
                        (op-type-check/type-check-op {:ws/A$v1 {:fields {:x :Integer}}
                                                      :ws/B$v1 {}}
                                                     {:$instance-of :ws/A$v1
                                                      :$refinements {:ws/B$v1 {:$instance-of :ws/A$v1
                                                                               :y "hi"}}})))

  (is (thrown-with-msg? ExceptionInfo #"unexpected type"
                        (op-type-check/type-check-op {:ws/A$v1 {:fields {:x :Integer}}
                                                      :ws/B$v1 {}}
                                                     {:$instance-of :ws/A$v1
                                                      :$refinements {:ws/B$v1 {:$instance-of :ws/A$v1
                                                                               :x "hi"}}})))
  (is (thrown-with-msg? ExceptionInfo #"field not found"
                        (op-type-check/type-check-op {:ws/A$v1 {:fields {:x :Integer}}
                                                      :ws/B$v1 {}}
                                                     {:$refines-to :ws/A$v1
                                                      :$refinements {:ws/B$v1 {:$instance-of :ws/A$v1
                                                                               :y "hi"}}})))
  (is (thrown-with-msg? ExceptionInfo #"unexpected type"
                        (op-type-check/type-check-op {:ws/A$v1 {:fields {:x :Integer}}
                                                      :ws/B$v1 {}}
                                                     {:$refines-to :ws/A$v1
                                                      :$concrete-choices {:ws/B$v1 {:$instance-of :ws/A$v1

                                                                                    :x "hi"}}})))
  (let [bom {:$refines-to :ws/A$v1
             :$concrete-choices {:ws/B$v1 {:$instance-of :ws/A$v1
                                           :x 1}}}]
    (is (= bom (op-type-check/type-check-op {:ws/A$v1 {:fields {:x :Integer}}
                                             :ws/B$v1 {}}
                                            bom)))))

;; (run-tests)
