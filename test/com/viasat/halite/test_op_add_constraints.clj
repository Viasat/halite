;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-add-constraints
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.op-add-constraints :as op-add-constraints]
            [schema.test])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-basic
  (is (= {:$instance-of :ws/A$v1
          :x {:$enum #{1 2}}}
         (op-add-constraints/add-constraints-op {:ws/A$v1 {:fields {:x :Integer}}}
                                                {:$instance-of :ws/A$v1
                                                 :x {:$enum #{1 2}}})))

  (is (= {:$instance-of :ws/A$v1
          :x {:$enum #{1 2}}
          :$constraints {"x" '(and (> x 0) (< x 10000))}}
         (op-add-constraints/add-constraints-op {:ws/A$v1 {:fields {:x :Integer}
                                                           :constraints [["x" '(and (> x 0) (< x 10000))]]}}
                                                {:$instance-of :ws/A$v1
                                                 :x {:$enum #{1 2}}})))

  (is (= {:$instance-of :ws/A$v1
          :b {:$instance-of :ws/B$v1
              :$constraints {"x" '(and (> x 0) (< x 10000))}}}
         (op-add-constraints/add-constraints-op {:ws/A$v1 {:fields {:b [:Instance :ws/B$v1]}}
                                                 :ws/B$v1 {:fields {:x :Integer}
                                                           :constraints [["x" '(and (> x 0) (< x 10000))]]}}
                                                {:$instance-of :ws/A$v1
                                                 :b {:$instance-of :ws/B$v1}})))

  (is (= {:$instance-of :ws/A$v1, :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1}}}
         (op-add-constraints/add-constraints-op {:ws/A$v1 {:fields {:b [:Instance :ws/B$v1]}}
                                                 :ws/B$v1 {:fields {:x :Integer}
                                                           :constraints [["x" '(and (> x 0) (< x 10000))]]}}
                                                {:$instance-of :ws/A$v1
                                                 :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1}}}))
      "refinement constraints are _not_ picked up")

  (is (= {:$refines-to :ws/A$v1
          :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1
                                        :$constraints {"x" '(and (> x 0) (< x 10000))}}}}
         (op-add-constraints/add-constraints-op {:ws/A$v1 {:fields {:b [:Instance :ws/B$v1]}}
                                                 :ws/B$v1 {:fields {:x :Integer}
                                                           :constraints [["x" '(and (> x 0) (< x 10000))]]
                                                           :refines-to {:ws/A$v1 {:expr nil}}}}
                                                {:$refines-to :ws/A$v1
                                                 :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1}}}))))

(deftest test-instance-literal-bom
  (is (= {:$instance-literal-type :ws/B$v1
          :$constraints {"x" '(and (> x 0) (< x 10000))}}
         (op-add-constraints/add-constraints-op {:ws/B$v1 {:fields {:x :Integer}
                                                           :constraints [["x" '(and (> x 0) (< x 10000))]]}}
                                                {:$instance-literal-type :ws/B$v1})))

  (is (= {:$instance-literal-type :ws/B$v1
          :x 7
          :$constraints {"x" '(and (> x 0) (< x 10000))}}
         (op-add-constraints/add-constraints-op {:ws/B$v1 {:fields {:x :Integer}
                                                           :constraints [["x" '(and (> x 0) (< x 10000))]]}}
                                                {:$instance-literal-type :ws/B$v1
                                                 :x 7}))))

;; (run-tests)
