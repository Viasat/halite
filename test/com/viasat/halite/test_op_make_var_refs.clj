;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-make-var-refs
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.op-make-var-refs :as op-make-var-refs]
            [schema.test])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-basic
  (is (= {:$instance-of :ws/A$v1}
         (op-make-var-refs/make-var-refs-op {:$instance-of :ws/A$v1})))

  (is (= {:$instance-of :ws/A$v1
          :$constraints {"x" '(> #r [:x] 100)}}
         (op-make-var-refs/make-var-refs-op {:$instance-of :ws/A$v1
                                             :$constraints {"x" '(> x 100)}})))

  (is (= {:$instance-of :ws/A$v1
          :b {:$instance-of :ws/B$v1
              :$constraints {"x" '(> #r [:b :x] 100)}}}
         (op-make-var-refs/make-var-refs-op {:$instance-of :ws/A$v1
                                             :b {:$instance-of :ws/B$v1
                                                 :$constraints {"x" '(> x 100)}}})))

  (is (= {:$instance-of :ws/A$v1
          :b {:$instance-of :ws/B$v1
              :$constraints {"x" '(let [a 100]
                                    (> #r [:b :x] a))}}}
         (op-make-var-refs/make-var-refs-op {:$instance-of :ws/A$v1
                                             :b {:$instance-of :ws/B$v1
                                                 :$constraints {"x" '(let [a 100]
                                                                       (> x a))}}}))))

;; (run-tests)
