;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-extract-constraints
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.op-extract-constraints :as op-extract-constraints]
            [com.viasat.halite.var-ref :as var-ref]
            [schema.test])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(var-ref/init)

(deftest test-basic
  (is (= []
         (op-extract-constraints/extract-constraints-op {:$instance-of :ws/A$v1})))

  (is (= [{:constraint-path [:ws/A$v1 "x"], :constraint-e '(> #r [:x] 100)}]
         (op-extract-constraints/extract-constraints-op {:$instance-of :ws/A$v1
                                                         :$constraints {"x" '(> #r [:x] 100)}})))

  (is (= [{:constraint-path [:b :ws/B$v1 "x"], :constraint-e '(> #r [:b :x] 100)}]
         (op-extract-constraints/extract-constraints-op {:$instance-of :ws/A$v1
                                                         :b {:$instance-of :ws/B$v1
                                                             :$constraints {"x" '(> #r [:b :x] 100)}}})))

  (is (= [{:constraint-path [:b :ws/B$v1 "x"], :constraint-e '(let [a 100] (> #r [:b :x] a))}]
         (op-extract-constraints/extract-constraints-op {:$instance-of :ws/A$v1
                                                         :b {:$instance-of :ws/B$v1
                                                             :$constraints {"x" '(let [a 100]
                                                                                   (> #r [:b :x] a))}}})))

  (is (= [{:constraint-path [:c :ws/C$v1 "x"], :constraint-e '(let [a 100] (> #r [:c :y] a))}
          {:constraint-path [:b :ws/B$v1 "x"], :constraint-e '(let [a 100] (> #r [:b :x] a))}
          {:constraint-path [:ws/A$v1 "a"], :constraint-e '(let [a 100] (> #r [:a1] a))}]
         (op-extract-constraints/extract-constraints-op {:$instance-of :ws/A$v1
                                                         :$constraints {"a" '(let [a 100]
                                                                               (> #r [:a1] a))}
                                                         :b {:$instance-of :ws/B$v1
                                                             :$constraints {"x" '(let [a 100]
                                                                                   (> #r [:b :x] a))}}
                                                         :c {:$instance-of :ws/C$v1
                                                             :$constraints {"x" '(let [a 100]
                                                                                   (> #r [:c :y] a))}}}))))

;; (run-tests)
