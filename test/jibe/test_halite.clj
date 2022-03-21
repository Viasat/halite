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
    {:$type :ws/D$v1 :xss [[]]} :ws/D$v1))

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

(deftest application-type-checking-tests
  (are [expr etype]
      (= etype (halite/type-check tenv expr))

    '(+ 1 2) :Integer
    '(+ (- 3 2) (* 4 5)) :Integer
    '(and (< 1 2) (> (+ 5 6) 90) (or true (<= 1 4))) :Boolean
    '(Cardinality [true true false]) :Integer))

(deftest application-eval-tests
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
      '(and (<= (+ 3 5) (* 2 2 2)) (or (> 0 1) (<= (Cardinality #{1 2}) 3))) true)))

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
      '(get* (get* c :bs) 2) (get-in c [:bs 2])
      '(get* (get* (get* c :bs) 2) :a) (get-in c [:bs 2 :a]))))

(deftest equality-tests
  (are [expr etype]
      (= etype (halite/type-check tenv expr))

    '(= 1 (+ 2 3)) :Boolean
    '(= [#{}] [#{1} #{2}]) :Boolean
    '(= [#{12}] [#{}]) :Boolean)

  (are [expr err-msg]
      (thrown-with-msg? ExceptionInfo err-msg (halite/type-check tenv expr))

    '(= 1 "two") #"incompatible types"
    '(= [] #{}) #"incompatible types")

  (let [env (assoc tenv :bindings {} :refinesTo {})]
    (are [expr v]
        (= v (halite/eval-expr env expr))

      '(= (* 2 3) (+ 2 4)) true
      '(= [1 2 3] [2 1 3]) false)))
