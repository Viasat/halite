;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-id
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.op-id :as op-id]
            [schema.test])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-basic
  (is (= {:$instance-of :ws/A$v1
          :b {:$instance-of :ws/B$v1
              :$constraints {"x" '(let [a 100]
                                    (> x a))}}}
         (op-id/id-op {:$instance-of :ws/A$v1
                       :b {:$instance-of :ws/B$v1
                           :$constraints {"x" '(let [a 100]
                                                 (> x a))}}})))

  (let [result (op-id/id-op {:$instance-of :ws/A$v1
                             :b {:$instance-of :ws/B$v1
                                 :$constraints {"x" '(get {:$type :ws/A, :x true, :a 10} :x)}}})]
    (is (= {:$instance-of :ws/A$v1
            :b {:$instance-of :ws/B$v1
                :$constraints {"x" '(get {:$type :ws/A, :x true, :a 10} :x)}}}
           result))
    (is (= {:id-path [:b :$constraints "x" 0]}
           (meta (second (get-in result [:b :$constraints "x"]))))))

  (let [result (op-id/id-op {:$instance-of :ws/A$v1
                             :b {:$instance-of :ws/B$v1
                                 :$constraints {"x" '(valid {:$type :ws/A, :x true, :a 10})}}})]
    (is (= {:$instance-of :ws/A$v1
            :b {:$instance-of :ws/B$v1
                :$constraints {"x" '(valid {:$type :ws/A, :x true, :a 10})}}}
           result))
    (is (= {:id-path [:b :$constraints "x" 0]}
           (meta (second (get-in result [:b :$constraints "x"])))))
    (is (= {:id-path [:b :$constraints "x" 1]}
           (meta (get-in result [:b :$constraints "x"])))))

  (let [result (op-id/id-op {:$instance-of :ws/A$v1
                             :b {:$instance-of :ws/B$v1
                                 :$constraints {"x" '(valid? {:$type :ws/A, :x true, :a 10})}}})]
    (is (= {:$instance-of :ws/A$v1
            :b {:$instance-of :ws/B$v1
                :$constraints {"x" '(valid? {:$type :ws/A, :x true, :a 10})}}}
           result))
    (is (= {:id-path [:b :$constraints "x" 0]}
           (meta (second (get-in result [:b :$constraints "x"])))))
    (is (= {:id-path [:b :$constraints "x" 1]}
           (meta (get-in result [:b :$constraints "x"]))))))

;; (run-tests)
