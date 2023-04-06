;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-lower-expressions
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.op-lower-expressions :as op-lower-expressions]
            [schema.test])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-basic)

(deftest test-lower-expression
  (are [in out]
       (= out (op-lower-expressions/lower-expression nil [:p] (envs/env '{a 1 b 2}) in))

    '1 '1
    "hi" "hi"
    '(= #r [:p :x] a) '(= #r [:p :x] a)
    '(not= #r [:p :x] a) '(not= #r [:p :x] a)
    '(+ #d "1.1" #r [:p :x]) '(+ 11 #r [:p :x])
    '{:$type :ws/A :x #r [:p :x] :a a} '{:$type :ws/A :x #r [:p :x] :a a}
    '(+ #r [:p :x] a #r [:p :y] b) '(+ #r [:p :x] a #r [:p :y] b)
    '(+ #r [:p :x] (- a #r [:p :y])) '(+ #r [:p :x] (- a #r [:p :y]))
    '(let [c #r [:p :x] d 1] (+ c d)) '(let [c #r [:p :x] d 1] (+ c d))
    '(first [a #r [:p :x] 100])  '(first [a #r [:p :x] 100])
    '#{a #r [:p :x] 100} '#{a #r [:p :x] 100}
    '(if #r [:p :x] #r [:p :y] #r [:p :z]) '(if #r [:p :x] #r [:p :y] #r [:p :z])
    '(when #r [:p :x] a)  '(when #r [:p :x] a)
    '(cond true #r [:p :x] #r [:p :y] a) '(cond true #r [:p :x] #r [:p :y] a)
    '(if-value #r [:p :x] 1 #r [:p :y]) '(if #r [:p :x :$value?] 1 #r [:p :y])
    ;; '(when-value a x) '(when-value a #r [:p :x])
    ;; '(refine-to a :ws/A$v1) '(refine-to a :ws/A$v1)
    ;; '(refine-to x :ws/A$v1) '(refine-to #r [:p :x] :ws/A$v1)
    ;; '(refines-to? x :ws/A$v1) '(refines-to? #r [:p :x] :ws/A$v1)
    ;; '(every? [x y] (= x z)) '(every? [x #r [:p :y]] (= x #r [:p :z]))
    ;; '(any? [x a] (= x b)) '(any? [x a] (= x b))
    ;; '(any? [x x] (= x y)) '(any? [x #r [:p :x]] (= x #r [:p :y]))
    ;; '(map [x [10 11 12]] (inc x)) '(map [x [10 11 12]] (inc x))
    ;; '(map [i x] (inc y)) '(map [i #r [:p :x]] (inc #r [:p :y]))
    ;; '(filter [x x] true) '(filter [x #r [:p :x]] true)
    ;; '(reduce [a 0] [x y] (+ a x)) '(reduce [a 0] [x #r [:p :y]] (+ a x))
    ;; '(reduce [a 0] [x y] (+ a z)) '(reduce [a 0] [x #r [:p :y]] (+ a #r [:p :z]))
    ;; '(sort-by [x #{[3] [1 2]}] (count x)) '(sort-by [x #{[3] [1 2]}] (count x))
    ;; '(sort-by [p x] (count y)) '(sort-by [p #r [:p :x]] (count #r [:p :y]))
    ;; '(valid {:$type :ws/A :x x}) '(valid {:$type :ws/A :x #r [:p :x]})
    ;; '(valid? {:$type :ws/A :x x}) '(valid? {:$type :ws/A :x #r [:p :x]})
    ;; '(get #r [:p :a] :x) '(get {:$type :ws/A :x #r [:p :x]} :x)
    ;; '(get x :x) '(get #r [:p :x] :x)
    ;; '(get-in x [:q :r]) '(get-in #r [:p :x] [:q :r])
    ;; '(get-in x [:x :y]) '(get-in #r [:p :x] [:x :y])
    ))

(deftest test-lower-rescale
  (is (= {:$instance-of :ws/A$v1
          :a {:$primitive-type [:Decimal 2]
              :$value? true}
          :$constraints {"c1" '(= (* 501 10) (* #r [:a] 10))}}
         (op-lower-expressions/lower-expression-op {:$instance-of :ws/A$v1
                                                    :a {:$value? true, :$primitive-type [:Decimal 2]},
                                                    :$constraints {"c1" '(= (rescale #d "5.01" 3) (rescale #r [:a] 3))}})))

  (is (= {:$instance-of :ws/A$v1
          :a {:$primitive-type [:Decimal 2]
              :$value? true}
          :$constraints {"c1" '(= (div 501 10) (div #r [:a] 10))}}
         (op-lower-expressions/lower-expression-op {:$instance-of :ws/A$v1
                                                    :a {:$value? true, :$primitive-type [:Decimal 2]},
                                                    :$constraints {"c1" '(= (rescale #d "5.01" 1) (rescale #r [:a] 1))}}))))

(comment
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
                   (div x #r [:p :y])))

;; (run-tests)
