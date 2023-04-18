;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-flower
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.fog :as fog]
            [com.viasat.halite.op-flower :as op-flower]
            [schema.test])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(fog/init)

(deftest test-basic
  (is (= {:$instance-of :ws/A$v1}
         (op-flower/flower-op {:ws/A$v1 {}}
                              {:$instance-of :ws/A$v1})))

  (is (= {:$instance-of :ws/A$v1
          :$constraints {"x" '(> #r [:x] 100)}}
         (op-flower/flower-op {:ws/A$v1 {:fields {:x :Integer}}}
                              {:$instance-of :ws/A$v1
                               :$constraints {"x" '(> x 100)}})))

  (is (= {:$instance-of :ws/A$v1
          :b {:$instance-of :ws/B$v1
              :$constraints {"x" '(> #r [:b :x] 100)}}}
         (op-flower/flower-op {:ws/A$v1 {:fields {:b [:Instance :ws/B$v1]}}
                               :ws/B$v1 {:fields {:x :Integer}}}
                              {:$instance-of :ws/A$v1
                               :b {:$instance-of :ws/B$v1
                                   :$constraints {"x" '(> x 100)}}})))

  (is (= {:$instance-of :ws/A$v1
          :b {:$instance-of :ws/B$v1
              :$constraints {"x" '(> #r [:b :x] 100)}}}
         (op-flower/flower-op {:ws/A$v1 {:fields {:b [:Instance :ws/B$v1]}}
                               :ws/B$v1 {:fields {:x :Integer}}}
                              {:$instance-of :ws/A$v1
                               :b {:$instance-of :ws/B$v1
                                   :$constraints {"x" '(let [a 100]
                                                         (> x a))}}}))))

(deftest test-fog
  (is (= {:$instance-of :ws/A$v1
          :$constraints {"c" '(> #fog :Integer 30)}}
         (op-flower/flower-op {:ws/A$v1 {:fields {:xs [:Vec :Integer]
                                                  :ys [:Vec :Integer]}}}
                              {:$instance-of :ws/A$v1
                               :$constraints {"c" '(> (reduce [a 0] [x xs] (+ a x)) 30)}}))))

;; (run-tests)
