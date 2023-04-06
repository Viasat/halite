;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-conjoin-spec-bom
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.op-conjoin-spec-bom :as op-conjoin-spec-bom]
            [schema.test]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-basic
  (is (= {:$instance-of :ws/A$v1
          :x {:$value? true}}
         (op-conjoin-spec-bom/conjoin-spec-bom-op {:ws/A$v1 {:fields {:x :Integer}}}
                                                  {:$instance-of :ws/A$v1})))

  (is (= {:$instance-of :ws/A$v1
          :x {:$value? true}}
         (op-conjoin-spec-bom/conjoin-spec-bom-op {:ws/A$v1 {:fields {:x :Integer
                                                                      :y [:Maybe :Integer]}}}
                                                  {:$instance-of :ws/A$v1})))

  (is (= {:$instance-of :ws/A$v1
          :x {:$value? true
              :$ranges #{[1 10000]}}}
         (op-conjoin-spec-bom/conjoin-spec-bom-op {:ws/A$v1 {:fields {:x :Integer}
                                                             :constraints [["x" '(and (> x 0) (< x 10000))]]}}
                                                  {:$instance-of :ws/A$v1})))

  (is (= {:$instance-of :ws/A$v1
          :x {:$value? true
              :$enum #{1 3 5}}}
         (op-conjoin-spec-bom/conjoin-spec-bom-op {:ws/A$v1 {:fields {:x :Integer}
                                                             :constraints [["x" '(and (> x 0) (< x 10000))]]}}
                                                  {:$instance-of :ws/A$v1
                                                   :x {:$enum #{1 3 5}}})))

  (is (= {:$instance-of :ws/A$v1
          :y {:$value? true
              :$ranges #{[1 10000]}}}
         (op-conjoin-spec-bom/conjoin-spec-bom-op {:ws/A$v1 {:fields {:y [:Maybe :Integer]}
                                                             :constraints [["y" '(if-value y
                                                                                           (and (> y 0) (< y 10000))
                                                                                           true)]]}}
                                                  {:$instance-of :ws/A$v1
                                                   :y {:$value? true}})))

  (is (= {:$instance-of :ws/A$v1
          :x {:$contradiction? true}}
         (op-conjoin-spec-bom/conjoin-spec-bom-op {:ws/A$v1 {:fields {:x :Integer}}}
                                                  {:$instance-of :ws/A$v1
                                                   :x {:$value? false}}))))

(deftest test-composition
  (is (= {:$instance-of :ws/A$v1
          :b {:$instance-of :ws/B$v1
              :$value? true
              :x {:$value? true}}}
         (op-conjoin-spec-bom/conjoin-spec-bom-op {:ws/A$v1 {:fields {:b [:Instance :ws/B$v1]}}
                                                   :ws/B$v1 {:fields {:x :Integer}}}
                                                  {:$instance-of :ws/A$v1})))

  (is (= {:$instance-of :ws/A$v1
          :b {:$instance-of :ws/B$v1
              :$value? true
              :x {:$value? true
                  :$ranges #{[1 10000]}}}}
         (op-conjoin-spec-bom/conjoin-spec-bom-op {:ws/A$v1 {:fields {:b [:Instance :ws/B$v1]}}
                                                   :ws/B$v1 {:fields {:x :Integer}
                                                             :constraints [["x" '(and (> x 0) (< x 10000))]]}}
                                                  {:$instance-of :ws/A$v1})))

  (is (= {:$instance-of :ws/A$v1
          :b {:$instance-of :ws/B$v1
              :$value? true
              :x bom/contradiction-bom}}
         (op-conjoin-spec-bom/conjoin-spec-bom-op {:ws/A$v1 {:fields {:b [:Instance :ws/B$v1]}}
                                                   :ws/B$v1 {:fields {:x :Integer}
                                                             :constraints [["x" '(and (> x 0) (< x 10000))]]}}
                                                  {:$instance-of :ws/A$v1
                                                   :b {:$instance-of :ws/B$v1
                                                       :x {:$value? false}}})))
  (is (= {:$instance-of :ws/A$v1
          :b {:$instance-of :ws/B$v1
              :$value? true
              :x {:$value? true
                  :$ranges #{[100 10000]}}}}
         (op-conjoin-spec-bom/conjoin-spec-bom-op {:ws/A$v1 {:fields {:b [:Instance :ws/B$v1]}}
                                                   :ws/B$v1 {:fields {:x :Integer}
                                                             :constraints [["x" '(and (> x 0) (< x 10000))]]}}
                                                  {:$instance-of :ws/A$v1
                                                   :b {:$instance-of :ws/B$v1
                                                       :x {:$ranges #{[100 20000]}}}}))))

;; (run-tests)
