;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-make-var-refs
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.envs :as envs]
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

(deftest test-make-var-refs
  (are [in out]
       (= out (op-make-var-refs/make-var-refs [:p] (envs/env '{a 1 b 2}) in))

    '1 '1
    "hi" "hi"
    '(= x a) '(= #r [:p :x] a)
    '(not= x a) '(not= #r [:p :x] a)
    '(+ #d "1.1" x) '(+ #d "1.1" #r [:p :x])
    '{:$type :ws/A :x x :a a} '{:$type :ws/A :x #r [:p :x] :a a}
    '(+ x a y b) '(+ #r [:p :x] a #r [:p :y] b)
    '(+ x (- a y)) '(+ #r [:p :x] (- a #r [:p :y]))
    '(let [c x d 1] (+ c d)) '(let [c #r [:p :x] d 1] (+ c d))
    '(first [a x 100]) '(first [a #r [:p :x] 100])
    '#{a x 100} '#{a #r [:p :x] 100}
    '(if x y z) '(if #r [:p :x] #r [:p :y] #r [:p :z])
    '(when x a) '(when #r [:p :x] a)
    '(cond true x y a) '(cond true #r [:p :x] #r [:p :y] a)
    '(if-value x 1 y) '(if-value #r [:p :x] 1 #r [:p :y])
    '(when-value a x) '(when-value a #r [:p :x])
    '(refine-to a :ws/A$v1) '(refine-to a :ws/A$v1)
    '(refine-to x :ws/A$v1) '(refine-to #r [:p :x] :ws/A$v1)
    '(refines-to? x :ws/A$v1) '(refines-to? #r [:p :x] :ws/A$v1)
    '(every? [x y] (= x z)) '(every? [x #r [:p :y]] (= x #r [:p :z]))
    '(any? [x a] (= x b)) '(any? [x a] (= x b))
    '(any? [x x] (= x y)) '(any? [x #r [:p :x]] (= x #r [:p :y]))
    '(map [x [10 11 12]] (inc x)) '(map [x [10 11 12]] (inc x))
    '(map [i x] (inc y)) '(map [i #r [:p :x]] (inc #r [:p :y]))
    '(filter [x x] true) '(filter [x #r [:p :x]] true)
    '(reduce [a 0] [x y] (+ a x)) '(reduce [a 0] [x #r [:p :y]] (+ a x))
    '(reduce [a 0] [x y] (+ a z)) '(reduce [a 0] [x #r [:p :y]] (+ a #r [:p :z]))
    '(sort-by [x #{[3] [1 2]}] (count x)) '(sort-by [x #{[3] [1 2]}] (count x))
    '(sort-by [p x] (count y)) '(sort-by [p #r [:p :x]] (count #r [:p :y]))
    '(valid {:$type :ws/A :x x}) '(valid {:$type :ws/A :x #r [:p :x]})
    '(valid? {:$type :ws/A :x x}) '(valid? {:$type :ws/A :x #r [:p :x]})
    '(get {:$type :ws/A :x x} :x) '(get {:$type :ws/A :x #r [:p :x]} :x)
    '(get x :q) '#r [:p :x :q]
    '(get x :x) '#r [:p :x :x]
    '(get-in x [:q :r]) '#r [:p :x :q :r]
    '(get-in x [:x :y]) '#r [:p :x :x :y]

    '(if-value-let [x (get {:$type :my/Spec$v1 :n x :p 2} :o)]
                   (div x y)
                   x)
    '(if-value-let [x (get {:n #r [:p :x], :p 2, :$type :my/Spec$v1} :o)]
                   (div x #r [:p :y])
                   #r [:p :x])

    '(when-value-let [x (get {:$type :my/Spec$v1 :n x :p 2} :o)]
                     (div x y)
                     x)
    '(when-value-let [x (get {:n #r [:p :x], :p 2, :$type :my/Spec$v1} :o)]
                     (div x #r [:p :y]))))

;; (run-tests)
