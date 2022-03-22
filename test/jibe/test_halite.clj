;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.test-halite
  (:require [jibe.halite :as halite]
            [clojure.test :refer [deftest is are test-vars]]
            [schema.test :refer [validate-schemas]])
  (:import [clojure.lang ExceptionInfo]))

(clojure.test/use-fixtures :once validate-schemas)

(def tenv {:specs {:ws/A$v1 {:x :Integer
                             :y :Boolean
                             :c :ws2/B$v1}
                   :ws2/B$v1 {:s :String}
                   :ws/C$v1 {:xs [:Vec :Integer]}
                   :ws/D$v1 {:xss [:Vec [:Vec :Integer]]}}
           :vars {}
           :refinesTo* {}})

(deftest literal-type-tests
  (are [expr etype]
      (= etype (halite/type-check tenv expr))

    true :Boolean
    false :Boolean
    1 :Integer
    "hi" :String
    {:$type :ws2/B$v1 :s "foo"} :ws2/B$v1
    {:$type :ws/A$v1 :x 1 :y true
     :c {:$type :ws2/B$v1 :s "bar"}} :ws/A$v1
    #{} :EmptySet
    [] :EmptyVec
    [1 2 3] [:Vec :Integer]
    #{1 2 3} [:Set :Integer]
    [[]] [:Vec :EmptyVec]
    [#{} #{"foo"}] [:Vec [:Set :String]]
    [1 "two"] [:Vec :Any]
    #{[] #{}} [:Set :Coll]
    #{[1] #{}} [:Set :Coll]
    {:$type :ws/C$v1 :xs []} :ws/C$v1
    {:$type :ws/C$v1 :xs [1 2 3]} :ws/C$v1
    [{:$type :ws2/B$v1 :s "bar"}] [:Vec :ws2/B$v1]
    {:$type :ws/D$v1 :xss [[]]} :ws/D$v1)

  (are [expr err-msg]
      (thrown-with-msg? ExceptionInfo err-msg (halite/type-check tenv expr))

    {} #"instance literal must have :\$type field"
    {:$type "foo"} #"expected namespaced keyword as value of :\$type"
    {:$type :bar} #"expected namespaced keyword as value of :\$type"
    {:$type :foo/bar} #"resource spec not found"
    {:$type :ws/A$v1} #"missing required variables"
    {:$type :ws/A$v1 :x 1 :y 1 :c {:$type :ws2/B$v1 :s "foo"}} #"value of :y has wrong type"
    {:$type :ws/A$v1 :x 1 :y false :c {:$type :ws2/B$v1 :s 12}} #"value of :s has wrong type"
    {:$type :ws2/B$v1 :s "foo" :foo "bar"} #"variables not defined on spec"
    {:$type :ws/C$v1 :xs [1 "two"]} #"value of :xs has wrong type"))

(deftest literal-eval-tests
  (let [env (assoc tenv :bindings {} :refinesTo {})]
    (are [expr]
        (= expr (halite/eval-expr env expr))

      true
      false
      1
      "two"
      {:$type :ws2/B$v1 :s "foo"}
      []
      #{}
      [1 2 3]
      [{:$type :ws2/B$v1 :s "bar"}])))

(deftest application-tests
  (are [expr etype]
      (= etype (halite/type-check tenv expr))

    '(+ 1 2) :Integer
    '(+ (- 3 2) (* 4 5)) :Integer
    '(and (< 1 2) (> (+ 5 6) 90) (or true (<= 1 4))) :Boolean
    '(Cardinality [true true false]) :Integer
    '(contains? #{1 2 3} 2) :Boolean
    '(contains? #{1 2 3} "foo") :Boolean
    '(dec 12) :Integer
    '(div 14 3) :Integer
    '(mod* 5 2) :Integer
    '(expt 2 8) :Integer
    '(str) :String
    '(str "foo" (str) (str "bar")) :String
    '(subset? #{} #{1 2 3}) :Boolean
    '(subset? #{"nope"} #{1 2 3}) :Boolean)

  (are [expr err-msg]
      (thrown-with-msg? ExceptionInfo err-msg (halite/type-check tenv expr))

    '(foo) #"function 'foo' not found"
    '(+ 1 "two") #"no matching signature for '\+'"
    '(+ 1) #"no matching signature for '\+'")

  (let [env (assoc tenv :bindings {} :refinesTo {})]
    (are [expr v]
        (= v (halite/eval-expr env expr))

      '(+ 1 2) 3
      '(- 5 3) 2
      '(< 1 2) true
      '(< 2 1) false
      '(> 1 2) false
      '(> 2 1) true
      '(<= 1 1) true
      '(>= 1 1) true
      '(Cardinality [1 2 3 1]) 4
      '(and true false true) false
      '(or true false false) true
      '(or false false) false
      '(and (<= (+ 3 5) (* 2 2 2)) (or (> 0 1) (<= (Cardinality #{1 2}) 3))) true
      '(contains? #{1 2 3} 2) true
      '(dec 12) 11
      '(div 14 3) 4
      '(mod* 5 3) 2
      '(expt 2 8) 256
      '(str "foo" (str) (str "bar")) "foobar"
      '(subset? #{} #{1 2 3}) true
      '(subset? #{"nope"} #{1 2 3}) false)))

(deftest get*-type-checking-tests
  (let [tenv {:specs {:ws/A$v1 {:x :Integer}
                      :ws/B$v1 {:a :ws/A$v1}
                      :ws/C$v1 {:bs [:Vec :ws/B$v1]}}
              :vars {'a :ws/A$v1
                     'b :ws/B$v1
                     'c :ws/C$v1
                     'xs [:Vec :String]}
              :refinesTo* {}}]
    (are [expr etype]
        (= etype (halite/type-check tenv expr))
      
      'a :ws/A$v1   ; warm-up: symbol lookup
      '(get* a :x) :Integer
      '(get* b :a) :ws/A$v1
      '(get* (get* b :a) :x) :Integer
      '(get* xs (+ 1 2)) :String
      '(get* (get* (get* (get* c :bs) 2) :a) :x) :Integer)

    (are [expr err-msg]
        (thrown-with-msg? ExceptionInfo err-msg (halite/type-check tenv expr))

      'foo #"Undefined"
      '(get*) #"Wrong number of arguments"
      '(get* [] 1) #"Cannot index into empty vector"
      '(get* xs (< 1 2)) #"Second argument to get\* must be an integer"
      '(get* a :foo/bar) #"must be a variable name"
      '(get* a 12) #"must be a variable name"
      '(get* a :b) #"No such variable"
      '(get* #{} 1) #"must be an instance or non-empty vector")
    ))

(deftest get*-eval-tests
  (let [c {:$type :ws/C$v1
           :bs (mapv (fn [x]
                       {:$type :ws/B$v1
                        :a {:$type :ws/A$v1
                            :x x}})
                     (range 5))}
        env {:specs {:ws/A$v1 {:x :Integer}
                     :ws/B$v1 {:a :ws/A$v1}
                     :ws/C$v1 {:bs [:Vec :ws/B$v1]}}
             :vars {'c :ws/C$v1}
             :refinesTo* {}
             :bindings {'c c}
             :refinesTo {}}]
    (are [expr v]
        (= v (halite/eval-expr env expr))
      'c c
      '(get* c :bs) (get c :bs)
      '(get* (get* c :bs) 3) (get-in c [:bs 2])
      '(get* (get* (get* c :bs) 3) :a) (get-in c [:bs 2 :a]))))

(deftest equality-tests
  (are [expr etype]
      (= etype (halite/type-check tenv expr))

    '(= 1 (+ 2 3)) :Boolean
    '(= [#{}] [#{1} #{2}]) :Boolean
    '(= [#{12}] [#{}]) :Boolean
    '(not= [#{12}] [#{}]) :Boolean)

  (are [expr err-msg]
      (thrown-with-msg? ExceptionInfo err-msg (halite/type-check tenv expr))

    '(= 1 "two") #"incompatible types"
    '(= [] #{}) #"incompatible types"
    '(not= 1 "two") #"incompatible types"
    '(not= [] #{}) #"incompatible types")

  (let [env (assoc tenv :bindings {} :refinesTo {})]
    (are [expr v]
        (= v (halite/eval-expr env expr))

      '(= (* 2 3) (+ 2 4)) true
      '(= [1 2 3] [2 1 3]) false
      '(not= (* 2 3) (+ 2 4)) false
      '(not= [1 2 3] [2 1 3]) true)))

(deftest if-tests
  (are [expr etype]
      (= etype (halite/type-check tenv expr))
    '(if true 1 2) :Integer
    '(if false [[]] [[1]]) [:Vec [:Vec :Integer]])

  (are [expr err-msg]
      (thrown-with-msg? ExceptionInfo err-msg (halite/type-check tenv expr))

    '(if true 2) #"Wrong number of arguments"
    '(if 1 2 3) #"must be boolean"
    '(if true 1 "two") #"incompatible types")

  (let [env (assoc tenv :bindings {} :refinesTo {})]
    (are [expr v]
        (= v (halite/eval-expr env expr))

      '(if true 1 2) 1
      '(if (< 1 (Cardinality [])) 12 (+ 2 3)) 5)))

(deftest let-tests
  (are [expr etype]
      (= etype (halite/type-check tenv expr))

    '(let [] 1) :Integer
    '(let [x (+ 1 2)
           y [x]]
       y) [:Vec :Integer]
    '(let [x 1] (let [x "foo"] x)) :String
    '(let [x "foo"
           y (let [x 1] (+ x 2))]
       y) :Integer)

  (are [expr err-msg]
      (thrown-with-msg? ExceptionInfo err-msg (halite/type-check tenv expr))

    '(let) #"Wrong number of arguments"
    '(let [x] x) #"must have an even number of forms"
    '(let []) #"Wrong number of arguments"
    '(let [1 2] 1) #"must be symbols"
    '(let [x "foo"] (+ x 1)) #"no matching signature")

  (let [env (assoc tenv :bindings {} :refinesTo {})]
    (are [expr v]
        (= v (halite/eval-expr env expr))

      '(let [] 1) 1
      '(let [x (+ 1 2), y [x]] y) [3]
      '(let [x 1] (let [x "foo"] x)) "foo" 
      '(let [x "foo", y (let [x 1] (+ x 2))] y) 3
      )))

(deftest maybe-tests
  (let [tenv (-> tenv
                 (assoc-in [:specs :ws/Maybe$v1]
                           {:x [:Maybe :Integer]})
                 (assoc-in [:vars 'm] :ws/Maybe$v1)
                 (assoc-in [:vars 'x] [:Maybe :Integer]))]
    (are [expr etype]
        (= etype (halite/type-check tenv expr))

      'no-value- :Unset
      {:$type :ws/Maybe$v1} :ws/Maybe$v1
      {:$type :ws/Maybe$v1 :x 1} :ws/Maybe$v1
      {:$type :ws/Maybe$v1 :x 'no-value-} :ws/Maybe$v1
      '(get* m :x) [:Maybe :Integer]
      '(if-value- x (+ x 1) 1) :Integer
      '(let [x no-value-] (if-value- x x 1)) :Integer
      '(if-value- no-value- 42 "foo") :String)

    (are [expr err-msg]
        (thrown-with-msg? ExceptionInfo err-msg (halite/type-check tenv expr))

      '(let [no-value- 12] "ha") #"reserved word"
      '(if-value- 12 true false) #"must be a bare symbol"
      '(let [y 22] (if-value- y true false)) #"must have an optional type"
      '(if-value- x "foo" true) #"incompatible types")

    (let [env (assoc tenv
                     :bindings {'m {:$type :ws/Maybe$v1}
                                'x :Unset}
                     :refinesTo {})]
      (are [expr v]
          (= v (halite/eval-expr env expr))

        'no-value- :Unset
        '(get* m :x) :Unset
        '(if-value- x x 12) 12
        '(let [y no-value-] (if-value- y "foo" true)) true))))

(deftest union-tests
  (are [expr etype]
      (= etype (halite/type-check tenv expr))

    '(union #{1 2 3} #{3 4 5}) [:Set :Integer]
    '(union #{} #{"foo"}) [:Set :String]
    '(union #{1} #{"foo"}) [:Set :Any]
    '(union) :EmptySet
    '(union #{1}) [:Set :Integer]
    '(union #{[]} #{#{}}) [:Set :Coll]
    '(union #{1} #{2} #{3} #{4}) [:Set :Integer])

  (are [expr err-msg]
      (thrown-with-msg? ExceptionInfo err-msg (halite/type-check tenv expr))

    '(union #{1} [2]) #"must be sets")

  (let [env (assoc tenv :bindings {} :refinesTo {})]
    (are [expr v]
        (= v (halite/eval-expr env expr))

      '(union #{1 2 3} #{3 4 5}) #{1 2 3 4 5}
      '(union #{} #{"foo"}) #{"foo"}
      '(union #{1} #{"foo"}) #{1 "foo"}
      '(union) #{}
      '(union #{1}) #{1}
      '(union #{[]} #{#{}}) #{[] #{}}
      '(union #{1} #{2} #{3} #{4}) #{1 2 3 4})))
