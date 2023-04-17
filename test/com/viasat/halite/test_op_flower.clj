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

(defmacro check-flower [in out]
  `(let [in# ~in
         out# ~out
         spec-info# {:fields {:x :Integer
                              :xs [:Vec :Integer]
                              :z :Integer}}
         result# (op-flower/lower-expr {:top-bom {:$instance-of :ws/A$v1} ;; not used by test
                                        :spec-env {:ws/A$v1 spec-info#}
                                        :spec-type-env (envs/type-env-from-spec spec-info#)
                                        :type-env (envs/type-env {'~'a :Integer
                                                                  '~'b :Integer})
                                        :env (envs/env {'~'a 10
                                                        '~'b 20})
                                        :path [:p]
                                        :counter-atom (atom -1)
                                        :instance-literal-atom (atom {})
                                        :guards []}
                                       in#)]
     (is (= out# result#))
     result#))

(deftest test-make-var-refs
  (check-flower '1 '1)
  (check-flower "hi"  "hi")
  (check-flower '(= x a) '(= #r [:p :x] 10))
  (check-flower '(not= x a) '(not= #r [:p :x] 10))
  (check-flower '(+ #d "1.1" x) '(+ 11 #r [:p :x]))
  (check-flower (with-meta '{:$type :ws/A :x x :a a} {:id-path [:q]}) #instance {:$type :ws/A, :x #r [:p :x], :a 10})
  (check-flower '(+ x a y b) '(+ #r [:p :x] 10 #r [:p :y] 20))
  (check-flower '(+ x (- a y)) '(+ #r [:p :x] (- 10 #r [:p :y])))
  (check-flower '(let [c x d 1] (+ c d)) '(+ #r [:p :x] 1))
  (check-flower '(first [a x 100]) #fog :Integer)
  (check-flower '#{a x 100} #fog [:Set :Integer])
  (check-flower '(if x y z) '(if #r [:p :x] #r [:p :y] #r [:p :z]))
  (check-flower '(when x a) '(when #r [:p :x] 10))
  (check-flower '(cond z x y a 12) '(if #r [:p :z] #r [:p :x] (if #r [:p :y] 10 12)))
  (check-flower '(cond true x y a 12) '#r [:p :x])
  (check-flower '(if-value x 1 y) '(if #r [:p :x :$value?] 1 #r [:p :y]))
  (check-flower '(if-value x x y) '(if #r [:p :x :$value?] #r [:p :x] #r [:p :y]))

  (check-flower '(when-value x x) '(when #r [:p :x :$value?] #r [:p :x]))

  (check-flower '(refine-to a :ws/A$v1) '(refine-to 10 :ws/A$v1))
  (check-flower '(refine-to x :ws/A$v1) '(refine-to #r [:p :x] :ws/A$v1))
  (check-flower '(refines-to? x :ws/A$v1) '(refines-to? #r [:p :x] :ws/A$v1))
  (check-flower '(every? [x xs] (= x z)) #fog :Boolean)
  (check-flower '(any? [x xs] (= x b)) #fog :Boolean)
  (check-flower '(map [x [10 11 12]] (inc x)) #fog [:Vec :Integer])
  (check-flower '(map [i xs] (inc x)) #fog [:Vec :Integer])
  (check-flower '(filter [x xs] true) #fog [:Vec :Integer])
  (check-flower '(reduce [a 0] [x xs] (+ a x)) #fog :Integer)
  (check-flower '(reduce [a 0] [x xs] (cond (= x 1) "hi" 2)) #fog :Value)
  (check-flower '(sort-by [x #{[3] [1 2]}] (count x)) #fog [:Vec [:Vec :Integer]])
  (check-flower '(sort-by [p xs] (count xs)) #fog [:Vec :Integer])
  ;; TODO
  ;; (check-flower '(valid {:$type :ws/A :x x}) '(valid {:x #r [:p :x], :$type :ws/A}))
  ;; (check-flower '(valid? {:$type :ws/A :x x}) '(valid? {:x #r [:p :x], :$type :ws/A}))
  ;; (check-flower '(get {:$type :ws/A :x x} :x) '(get {:$type :ws/A :x x} :x))
  (check-flower '(get x :q) '#r [:p :x :q])
  (check-flower '(get x :x) '#r [:p :x :x])
  (check-flower '(get x {:$type :ws/A$v1 :x 1 :xs [10 20] :z 3} :x) #r [:p :x {:$type :ws/A$v1 :x 1 :xs [10 20] :z 3}])
  (check-flower '(get-in x {:$type :ws/A$v1 :x 1 :xs [10 20] :z 3} [:x]) #r [:p :x :$type :ws/A$v1 :x 1 :xs [10 20] :z 3])
  (check-flower '(get-in x [:q :r]) '#r [:p :x :q :r])
  (check-flower '(get-in x [:x :y]) '#r [:p :x :x :y])

  #_(check-flower '(if-value-let [x (get {:n x, :p 2, :$type :my/Spec$v1} :o)]
                                 (div x y)
                                 x)
                  '(if-value-let [x (get {:$type :my/Spec$v1 :n x :p 2} :o)]
                                 (div x y)
                                 x))

  #_(check-flower '(when-value-let [x (get {:n x, :p 2, :$type :my/Spec$v1} :o)]
                                   (div x y))
                  '(when-value-let [x (get {:$type :my/Spec$v1 :n x :p 2} :o)]
                                   (div x y)
                                   x)))

(def empty-context {:top-bom {:$instance-of :ws/A$v1} ;; not used by tests
                    :spec-env (envs/spec-env {})
                    :spec-type-env (envs/type-env {})
                    :type-env (envs/type-env {})
                    :env (envs/env {})
                    :path []
                    :counter-atom (atom -1)
                    :instance-literal-atom (atom {})
                    :guards []})

(deftest test-return-path
  (is (= true
         (#'op-flower/return-path empty-context 1)))

  (is (= true
         (#'op-flower/return-path empty-context
                                  '(if x 9 2))))

  (is (= #r [:x]
         (#'op-flower/return-path empty-context
                                  '(when x 1))))

  (is (= '(> 100 5)
         (#'op-flower/return-path (assoc empty-context
                                         :env (envs/env {'x 100}))
                                  '(when (> x 5) 1))))

  (is (= true
         (#'op-flower/return-path (assoc empty-context
                                         :env (envs/env {'x 100}))
                                  'x)))

  (is (= #r [:x :$value?]
         (#'op-flower/return-path empty-context
                                  'x)))

  ;; boolean ops

  (is (= '(if #r [:x] #r [:y :$value?] #r [:z :$value?])
         (#'op-flower/return-path empty-context
                                  '(if x y z))))

  (is (= '(and #r [:x] #r [:y :$value?])
         (#'op-flower/return-path empty-context
                                  '(when x y))))

  ;; if-value-let

  (is (= '(if #r [:x :$value?] #r [:y :$value?] #r [:z :$value?])
         (#'op-flower/return-path empty-context
                                  '(if-value-let [a x] y z))))

  (is (= '(if #r [:x :$value?] #r [:x :$value?] #r [:z :$value?])
         (#'op-flower/return-path empty-context
                                  '(if-value-let [a x] a z))))

  (is (= '(or #r [:x :$value?] #r [:z :$value?])
         (#'op-flower/return-path empty-context
                                  '(if-value-let [a x] (inc a) z))))

  ;; when-value-let

  (is (= '(and #r [:x :$value?] #r [:y :$value?])
         (#'op-flower/return-path empty-context
                                  '(when-value-let [a x] y))))

  (is (= '(and #r [:x :$value?] #r [:x :$value?])
         (#'op-flower/return-path empty-context
                                  '(when-value-let [a x] a))))

  ;; get
  (is (= #r [:x :$value?]
         (#'op-flower/return-path empty-context
                                  '(get {:$type :spec/A$v1 :a x} :a))))

  (is (= #r [:x :$value?]
         (#'op-flower/return-path empty-context
                                  '(get (get {:$type :spec/A$v1 :a {:$type :spec/B$v1 :b x}} :a) :b))))

  (is (= true
         (#'op-flower/return-path empty-context
                                  '(get (get {:$type :spec/A$v1 :a {:$type :spec/B$v1 :b 5}} :a) :b))))

  (is (= '(or #r [:y] #r [:x :$value?])
         (#'op-flower/return-path empty-context
                                  '(get (get {:$type :spec/A$v1
                                              :a (if y
                                                   {:$type :spec/B$v1 :b 5}
                                                   {:$type :spec/B$v1 :b x})} :a) :b))))

  (is (= '(and #r [:x :$value?] #r [:x :a :$value?])
         (#'op-flower/return-path empty-context
                                  '(get x :a)))))

(deftest test-make-boolean
  (is (= 'x (#'op-flower/make-and 'x true)))
  (is (= false (#'op-flower/make-and 'x false)))

  (is (= true (#'op-flower/make-or 'x true)))

  (is (= false (#'op-flower/make-not true)))
  (is (= '(not x) (#'op-flower/make-not 'x))))

(deftest test-make-not
  (is (= false (#'op-flower/make-not true)))
  (is (= true (#'op-flower/make-not false)))
  (is (= '(not x) (#'op-flower/make-not 'x)))
  (is (= 'x (#'op-flower/make-not '(not x))))
  (is (=  true (#'op-flower/make-not '(not true))))
  (is (= false (#'op-flower/make-not '(not false)))))

(deftest test-make-and
  (doseq [x [true false]
          y [nil true false]
          z [nil true false]]
    (let [args (->> [x y z] (remove nil?))]
      (is (= (eval `(and ~@args))
             (apply #'op-flower/make-and args))))))

(deftest test-make-or
  (doseq [x [true false]
          y [nil true false]
          z [nil true false]]
    (let [args (->> [x y z] (remove nil?))]
      (is (= (eval `(or ~@args))
             (apply #'op-flower/make-or args))))))

(deftest test-make-if
  (doseq [target [true false]
          then-clause [true false]
          else-clause [true false]]
    (is (= (eval `(if ~target ~then-clause ~else-clause))
           (#'op-flower/make-if target then-clause else-clause))))
  (doseq [target ['com.viasat.halite.test-op-flower/x true false]
          then-clause ['com.viasat.halite.test-op-flower/y true false]
          else-clause ['com.viasat.halite.test-op-flower/z true false]]
    (doseq [x [true false]
            y [true false]
            z [true false]]
      (do (def com.viasat.halite.test-op-flower/x x)
          (def com.viasat.halite.test-op-flower/y y)
          (def com.viasat.halite.test-op-flower/z z)
          (is (= (eval `(if ~target ~then-clause ~else-clause))
                 (eval (#'op-flower/make-if target then-clause else-clause)))
              [:target target :then-clause then-clause :else-clause else-clause
               :x x :y y :z z
               :if (eval `(if ~target ~then-clause ~else-clause))
               :make-if (#'op-flower/make-if target then-clause else-clause)])))))

;; (run-tests)
