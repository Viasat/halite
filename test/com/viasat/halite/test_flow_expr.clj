;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-flow-expr
  (:require [clojure.pprint :as pprint]
            [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.fog :as fog]
            [com.viasat.halite.flow-expr :as flow-expr]
            [schema.test])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(fog/init)

(defmacro check-flower [in out & more]
  `(let [in# ~in
         out# ~out
         spec-info# {:fields {:x :Integer
                              :xs [:Vec :Integer]
                              :z :Integer}}
         instance-literal-atom# (atom {})
         result# (flow-expr/lower-expr {:spec-env {:ws/A$v1 spec-info#
                                                   :ws/B$v1 {:fields {:n :Integer
                                                                      :o [:Maybe :Integer]}}}
                                        :spec-type-env (envs/type-env-from-spec spec-info#)
                                        :type-env (envs/type-env {'~'a :Integer
                                                                  '~'b :Integer})
                                        :env (envs/env {'~'a 10
                                                        '~'b 20})
                                        :path [:p]
                                        :counter-atom (atom -1)
                                        :instance-literal-f (fn [path# instance-literal#]
                                                              (swap! instance-literal-atom# assoc-in path# instance-literal#))
                                        :guards []}
                                       in#)
         expected-instance-literals# (or ~(first more) {})]
     (is (= out# result#))
     (is (= expected-instance-literals# @instance-literal-atom#))
     [result# @instance-literal-atom#]))

(deftest test-make-var-refs
  (check-flower '1 '1)
  (check-flower "hi"  "hi")
  (check-flower '(= x a) '(= #r [:p :x] 10))
  (check-flower '(not= x a) '(not= #r [:p :x] 10))
  (check-flower '(+ #d "1.1" x) '(+ 11 #r [:p :x]))
  (check-flower (with-meta '{:$type :ws/A :x x :a a} {:id-path [:q]}) #instance {:$type :ws/A, :x #r [:p :x], :a 10}
                {:q {:$instance-literal-type :ws/A
                     :x {:$expr #r [:p :x]}
                     :a 10
                     :$guards []}})
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
  (check-flower '^{:id-path [:q 2]} (valid ^{:id-path [:q 1]} {:$type :ws/A :x x})
                '(when #r [:q 2] #instance {:x #r [:p :x], :$type :ws/A})
                '{:q {1 {:$instance-literal-type :ws/A
                         :x {:$expr #r [:p :x]}
                         :$guards []
                         :$valid-var-path [:q 2]}}})
  (check-flower '^{:id-path [:q 2]} (valid? ^{:id-path [:q 1]} {:$type :ws/A :x x})
                '#r [:q 2]
                {:q {1 {:$instance-literal-type :ws/A
                        :x {:$expr #r [:p :x]}
                        :$guards []
                        :$valid-var-path [:q 2]}}})

  (check-flower '(get ^{:id-path [:q 1]} {:$type :ws/A :x x} :x)
                #r [:p :x]
                {:q {1 {:$instance-literal-type :ws/A,
                        :x {:$expr #r [:p :x]}
                        :$guards []}}})
  (check-flower '(get x :q) '#r [:p :x :q])
  (check-flower '(get x :x) '#r [:p :x :x])
  (check-flower '(get x {:$type :ws/A$v1 :x 1 :xs [10 20] :z 3} :x) #r [:p :x {:$type :ws/A$v1 :x 1 :xs [10 20] :z 3}])
  (check-flower '(get-in x {:$type :ws/A$v1 :x 1 :xs [10 20] :z 3} [:x]) #r [:p :x :$type :ws/A$v1 :x 1 :xs [10 20] :z 3])
  (check-flower '(get-in x [:q :r]) '#r [:p :x :q :r])
  (check-flower '(get-in x [:x :y]) '#r [:p :x :x :y])

  (check-flower '(if-value-let [x (get ^{:id-path [:q 1]} {:$type :ws/B$v1 :n x} :o)]
                               (div x 3)
                               5)
                5
                {:q {1 {:$instance-literal-type :ws/B$v1
                        :n {:$expr #r [:p :x]}
                        :$guards []}}})

  (check-flower '(if-value-let [x (get ^{:id-path [:q 1]} {:$type :ws/B$v1 :n x :o 6} :o)]
                               (div x 3)
                               5)
                '(div 6 3)
                {:q {1 {:n {:$expr #r [:p :x]}
                        :o 6
                        :$instance-literal-type :ws/B$v1
                        :$guards []}}})

  (check-flower '(if-value-let [x (get ^{:id-path [:q 1]} {:$type :ws/B$v1 :n 3 :o x} :o)]
                               (div x 3)
                               5)
                '(if #r [:p :x :$value?]
                   (div #r [:p :x] 3)
                   5)
                {:q {1 {:$instance-literal-type :ws/B$v1
                        :n 3
                        :o {:$expr #r [:p :x]}
                        :$guards []}}})

  (check-flower '(when-value-let [x (get ^{:id-path [:q 1]} {:$type :ws/B$v1 :n x} :o)]
                                 (div x 3))
                '$no-value
                {:q {1 {:n {:$expr #r [:p :x]}, :$instance-literal-type :ws/B$v1, :$guards []}}})

  (check-flower '(when-value-let [x (get ^{:id-path [:q 1]} {:$type :ws/B$v1 :n x :o 6} :o)]
                                 (div x 3))
                '(div 6 3)
                {:q {1 {:n {:$expr #r [:p :x]}, :o 6, :$instance-literal-type :ws/B$v1, :$guards []}}})

  (check-flower '(when-value-let [x (get ^{:id-path [:q 1]} {:$type :ws/B$v1 :n 6 :o x} :o)]
                                 (div x 3))
                '(when #r [:p :x :$value?]
                   (div #r [:p :x] 3))
                {:q {1 {:n 6, :o {:$expr #r [:p :x]}, :$instance-literal-type :ws/B$v1, :$guards []}}}))

(deftest test-lowered
  (is (= #lowered (+ 1 #r [:x])
         (flow-expr/wrap-lowered-expr '(+ 1 #r [:x]))))

  (is (flow-expr/lowered-expr? #lowered (+ 1 #r [:x])))

  (is (not (flow-expr/lowered-expr? '(+ 1 x))))

  (is (= '(+ 1 #r [:x])
         (flow-expr/unwrap-lowered-expr #lowered (+ 1 #r [:x]))))

  (is (= "#lowered #r [:x]\n"
         (with-out-str (pprint/pprint (flow-expr/wrap-lowered-expr #r [:x]))))))

;; (run-tests)
