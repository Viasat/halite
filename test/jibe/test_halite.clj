;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.test-halite
  (:require [jibe.halite :as halite]
            [jibe.halite.envs :as halite-envs]
            [clojure.test :refer [deftest is are test-vars]]
            [schema.test :refer [validate-schemas]])
  (:import [clojure.lang ExceptionInfo]))

(clojure.test/use-fixtures :once validate-schemas)

(defrecord TestSpecEnv [specs]
  halite-envs/SpecEnv
  (lookup-spec* [self spec-id]
    (when-let [{:keys [spec-vars constraints refines-to] :as spec} (get specs spec-id)]
      (cond-> spec
        (nil? spec-vars) (assoc :spec-vars {})
        (nil? constraints) (assoc :constraints [])
        (nil? refines-to) (assoc :refines-to {})))))

(def senv (map->TestSpecEnv
           {:specs {:ws/A$v1 {:spec-vars {:x :Integer
                                          :y :Boolean
                                          :c :ws2/B$v1}}
                    :ws2/B$v1 {:spec-vars {:s :String}}
                    :ws/C$v1 {:spec-vars {:xs [:Vec :Integer]}}
                    :ws/D$v1 {:spec-vars {:xss [:Vec [:Vec :Integer]]}}}}))

(def tenv (halite-envs/type-env {}))

(def empty-env (halite-envs/env {}))

(deftest literal-type-tests
  (are [expr etype]
       (= etype (halite/type-check senv tenv expr))

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
       (thrown-with-msg? ExceptionInfo err-msg (halite/type-check senv tenv expr))

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
  (are [expr]
       (= expr (halite/eval-expr senv tenv empty-env expr))

    true
    false
    1
    "two"
    {:$type :ws2/B$v1 :s "foo"}
    []
    #{}
    [1 2 3]
    [{:$type :ws2/B$v1 :s "bar"}]))

(deftest application-tests
  (are [expr etype]
       (= etype (halite/type-check senv tenv expr))

    '(+ 1 2) :Integer
    '(+ (- 3 2) (* 4 5)) :Integer
    '(and (< 1 2) (> (+ 5 6) 90) (or true (<= 1 4))) :Boolean
    '(=> true false) :Boolean
    '(not true) :Boolean
    '(Cardinality [true true false]) :Integer
    '(contains? #{1 2 3} 2) :Boolean
    '(contains? #{1 2 3} "foo") :Boolean
    '(inc 12) :Integer
    '(dec 12) :Integer
    '(div 14 3) :Integer
    '(mod 5 2) :Integer
    '(expt 2 8) :Integer
    '(str) :String
    '(str "foo" (str) (str "bar")) :String
    '(subset? #{} #{1 2 3}) :Boolean
    '(subset? #{"nope"} #{1 2 3}) :Boolean
    '(every? [x #{2 4 6}] (and (> x 2) (< x 5))) :Boolean
    '(any? [x #{2 4 6}] (and (> x 2) (< x 5))) :Boolean
    '(any? [x #{}] true) :Boolean
    '(map [x #{3 7}] [x]) [:Set [:Vec :Integer]]
    '(map [x [3 7]] #{x}) [:Vec [:Set :Integer]]
    '(map [x #{}] "blue") :EmptySet
    '(map [x []] "red") :EmptyVec
    '(filter [x #{3 7}] (< x 5)) [:Set :Integer]
    '(filter [x [3 7]] (< x 5)) [:Vec :Integer]
    '(filter [x #{}] true) :EmptySet
    '(filter [x []] false) :EmptyVec
    '(abs -10) :Integer)

  (are [expr err-msg]
       (thrown-with-msg? ExceptionInfo err-msg (halite/type-check senv tenv expr))

    '(foo) #"function 'foo' not found"
    '(+ 1 "two") #"no matching signature for '\+'"
    '(+ 1) #"no matching signature for '\+'"
    '(any? [x #{"nan"}] (+ x 10)) #"no matching signature for '\+'"
    '(any? [x #{}] x) #"must be boolean"
    '(any? [x #{}] (< x 5)) #"no matching signature for '<'"
    '(map [x #{"nan"}] (+ x 10)) #"no matching signature for '\+'"
    '(map [x #{}] (+ x 10)) #"no matching signature for '\+'"
    '(filter [x #{"nan"}] (< x 10)) #"no matching signature for '\<'"
    '(filter [x #{}] (< x 10)) #"no matching signature for '\<'")

  (are [expr v]
       (= v (halite/eval-expr senv tenv empty-env expr))

    '(+ 1 2) 3
    '(- 5 3) 2
    '(< 1 2) true
    '(< 2 1) false
    '(> 1 2) false
    '(> 2 1) true
    '(<= 1 1) true
    '(>= 1 1) true
    '(=> true false) false
    '(=> true true) true
    '(=> false false) true
    '(=> false true) true
    '(Cardinality [1 2 3 1]) 4
    '(and true false true) false
    '(or true false false) true
    '(or false false) false
    '(not false) true
    '(not true) false
    '(and (<= (+ 3 5) (* 2 2 2)) (or (> 0 1) (<= (Cardinality #{1 2}) 3))) true
    '(contains? #{1 2 3} 2) true
    '(inc 12) 13
    '(dec 12) 11
    '(div 14 3) 4
    '(mod 5 3) 2
    '(expt 2 8) 256
    '(str "foo" (str) (str "bar")) "foobar"
    '(subset? #{} #{1 2 3}) true
    '(subset? #{"nope"} #{1 2 3}) false
    '(every? [x #{2 4 6}] (> x 1)) true
    '(every? [x #{2 4 6}] (> x 3)) false
    '(any? [x #{2 4 6}] (> x 5)) true
    '(any? [x #{2 4 6}] (> x 7)) false
    '(map [x #{3 7}] [x]) #{[3] [7]}
    '(map [x [3 7]] #{x}) [#{3} #{7}]
    '(map [x #{}] "blue") #{}
    '(map [x []] "red") []
    '(filter [x #{3 7}] (< x 5)) #{3}
    '(filter [x [3 7]] (< x 5)) [3]
    '(filter [x #{}] true) #{}
    '(filter [x []] false) []
    '(abs 10) 10
    '(abs -10) 10))

(deftest get-type-checking-tests
  (let [senv (->TestSpecEnv
              {:ws/A$v1 {:spec-vars {:x :Integer}}
               :ws/B$v1 {:spec-vars {:a :ws/A$v1}}
               :ws/C$v1 {:spec-vars {:bs [:Vec :ws/B$v1]}}})
        tenv (halite-envs/type-env
              {'a :ws/A$v1
               'b :ws/B$v1
               'c :ws/C$v1
               'xs [:Vec :String]})]
    (are [expr etype]
         (= etype (halite/type-check senv tenv expr))

      'a :ws/A$v1   ; warm-up: symbol lookup
      '(get a :x) :Integer
      '(get b :a) :ws/A$v1
      '(get (get b :a) :x) :Integer
      '(get xs (+ 1 2)) :String
      '(get (get (get (get c :bs) 2) :a) :x) :Integer
      '(get* xs (+ 1 2)) :String) ;; deprecated

    (are [expr err-msg]
         (thrown-with-msg? ExceptionInfo err-msg (halite/type-check senv tenv expr))

      'foo #"Undefined"
      '(get) #"Wrong number of arguments"
      '(get [] 1) #"Cannot index into empty vector"
      '(get xs (< 1 2)) #"Second argument to get must be an integer"
      '(get a :foo/bar) #"must be a variable name"
      '(get a 12) #"must be a variable name"
      '(get a :b) #"No such variable"
      '(get #{} 1) #"must be an instance of known type or non-empty vector")))

(deftest get-eval-tests
  (let [c {:$type :ws/C$v1
           :bs (mapv (fn [x]
                       {:$type :ws/B$v1
                        :a {:$type :ws/A$v1
                            :x x}})
                     (range 5))}
        senv (->TestSpecEnv
              {:ws/A$v1 {:spec-vars {:x :Integer}}
               :ws/B$v1 {:spec-vars {:a :ws/A$v1}}
               :ws/C$v1 {:spec-vars {:bs [:Vec :ws/B$v1]}}})
        tenv (halite-envs/type-env {'c :ws/C$v1})
        env (halite-envs/env {'c c})]
    (are [expr v]
         (= v (halite/eval-expr senv tenv env expr))
      'c c
      '(get c :bs) (get c :bs)
      '(get* c :bs) (get c :bs) ;; deprecated
      '(get (get c :bs) 2) (get-in c [:bs 2])
      '(get* (get* c :bs) 3) (get-in c [:bs 2]) ;; deprecated
      '(get (get (get c :bs) 2) :a) (get-in c [:bs 2 :a]))))

(deftest equality-tests
  (are [expr etype]
       (= etype (halite/type-check senv tenv expr))

    '(= 1 (+ 2 3)) :Boolean
    '(= [#{}] [#{1} #{2}]) :Boolean
    '(= [#{12}] [#{}]) :Boolean
    '(not= [#{12}] [#{}]) :Boolean)

  (are [expr err-msg]
       (thrown-with-msg? ExceptionInfo err-msg (halite/type-check senv tenv expr))

    '(= 1 "two") #"incompatible types"
    '(= [] #{}) #"incompatible types"
    '(not= 1 "two") #"incompatible types"
    '(not= [] #{}) #"incompatible types")

  (are [expr v]
       (= v (halite/eval-expr senv tenv empty-env expr))

    '(= (* 2 3) (+ 2 4)) true
    '(= [1 2 3] [2 1 3]) false
    '(not= (* 2 3) (+ 2 4)) false
    '(not= [1 2 3] [2 1 3]) true))

(deftest if-tests
  (are [expr etype]
       (= etype (halite/type-check senv tenv expr))
    '(if true 1 2) :Integer
    '(if false [[]] [[1]]) [:Vec [:Vec :Integer]])

  (are [expr err-msg]
       (thrown-with-msg? ExceptionInfo err-msg (halite/type-check senv tenv expr))

    '(if true 2) #"Wrong number of arguments"
    '(if 1 2 3) #"must be boolean"
    '(if true 1 "two") #"incompatible types")

  (are [expr v]
       (= v (halite/eval-expr senv tenv empty-env expr))

    '(if true 1 2) 1
    '(if (< 1 (Cardinality [])) 12 (+ 2 3)) 5))

(deftest let-tests
  (are [expr etype]
       (= etype (halite/type-check senv tenv expr))

    '(let [] 1) :Integer
    '(let [x (+ 1 2)
           y [x]]
       y) [:Vec :Integer]
    '(let [x 1] (let [x "foo"] x)) :String
    '(let [x "foo"
           y (let [x 1] (+ x 2))]
       y) :Integer)

  (are [expr err-msg]
       (thrown-with-msg? ExceptionInfo err-msg (halite/type-check senv tenv expr))

    '(let) #"Wrong number of arguments"
    '(let [x] x) #"must have an even number of forms"
    '(let []) #"Wrong number of arguments"
    '(let [1 2] 1) #"must be symbols"
    '(let [x "foo"] (+ x 1)) #"no matching signature")

  (are [expr v]
       (= v (halite/eval-expr senv tenv empty-env expr))

    '(let [] 1) 1
    '(let [x (+ 1 2), y [x]] y) [3]
    '(let [x 1] (let [x "foo"] x)) "foo"
    '(let [x "foo", y (let [x 1] (+ x 2))] y) 3))

(deftest maybe-tests
  (let [senv (assoc-in senv [:specs :ws/Maybe$v1] {:spec-vars {:x [:Maybe :Integer]}})
        tenv (-> tenv
                 (halite-envs/extend-scope 'm :ws/Maybe$v1)
                 (halite-envs/extend-scope 'x [:Maybe :Integer]))]
    (are [expr etype]
         (= etype (halite/type-check senv tenv expr))

      'no-value- :Unset
      {:$type :ws/Maybe$v1} :ws/Maybe$v1
      {:$type :ws/Maybe$v1 :x 1} :ws/Maybe$v1
      {:$type :ws/Maybe$v1 :x 'no-value-} :ws/Maybe$v1
      '(get m :x) [:Maybe :Integer]
      '(if-value x (+ x 1) 1) :Integer
      '(let [x no-value-] (if-value x x 1)) :Integer
      '(if-value no-value- 42 "foo") :String
      '(if-value- no-value- 42 "foo") :String ;; deprecated
      '(some? no-value-) :Boolean
      '(some? x) :Boolean)

    (are [expr err-msg]
         (thrown-with-msg? ExceptionInfo err-msg (halite/type-check senv tenv expr))

      '(let [no-value- 12] "ha") #"reserved word"
      '(if-value 12 true false) #"must be a bare symbol"
      '(let [y 22] (if-value y true false)) #"must have an optional type"
      '(if-value x "foo" true) #"incompatible types"
      '(if-value- x "foo" true) #"incompatible types") ;; deprecated

    (let [env (halite-envs/env {'m {:$type :ws/Maybe$v1}
                                'x :Unset})]
      (are [expr v]
           (= v (halite/eval-expr senv tenv env expr))

        'no-value- :Unset
        {:$type :ws/Maybe$v1 :x 'no-value-} {:$type :ws/Maybe$v1}
        '(get m :x) :Unset
        '(if-value x x 12) 12
        '(let [y no-value-] (if-value y "foo" true)) true
        '(let [y no-value-] (if-value- y "foo" true)) true ;; deprecated
        '(some? x) false
        '(some? m) true))))

(deftest no-value-restrictions
  ;; We want to limit the ways in which [:Maybe <T>] can be used.
  ;; Specifically:
  ;; 1) [:Set [:Maybe <T>]] and [:Vec [:Maybe <T>]] are not valid types!
  ;;    We want `no-value-` to represent the absence of a value, and you
  ;;    can't put a value you don't have in a collection!
  ;; 2) [:Maybe [:Maybe <T>]] is not a valid type!
  (let [tenv (halite-envs/extend-scope tenv 'x [:Maybe :Integer])]
    (are [expr err-msg]
         (thrown-with-msg? ExceptionInfo err-msg (halite/type-check senv tenv expr))

      '[x] #"vector literal element may not always evaluate to a value"
      '#{x} #"set literal element may not always evaluate to a value"
      '(conj [] x) #"cannot conj possibly unset value to vector")))

(deftest when-tests
  (let [senv (assoc-in senv [:specs :ws/Maybe$v1] {:spec-vars {:x [:Maybe :Integer]}})
        tenv (-> tenv
                 (halite-envs/extend-scope 'm :ws/Maybe$v1)
                 (halite-envs/extend-scope 'x [:Maybe :Integer])
                 (halite-envs/extend-scope 'b :Boolean))]
    (are [expr etype]
        (= etype (halite/type-check senv tenv expr))

      '(when b 1) [:Maybe :Integer]
      '(when b x) [:Maybe :Integer]
      '(when b (when (if-value- x true false) "Foo")) [:Maybe :String]
      '{:$type :ws/Maybe$v1 :x (when b 12)} :ws/Maybe$v1)

    (are [expr err-msg]
        (thrown-with-msg? ExceptionInfo err-msg (halite/type-check senv tenv expr))

      '[(when b 1)] #"may not always evaluate to a value"
      '#{(when b 1)} #"may not always evaluate to a value"
      '(when 1 2) #"must be boolean"
      '(when) #"Wrong number of arguments"
      '(when b 1 2) #"Wrong number of arguments")

    (let [env (-> empty-env
                  (halite-envs/bind 'm {:$type :ws/Maybe$v1})
                  (halite-envs/bind 'x :Unset)
                  (halite-envs/bind 'b false))]
      (are [expr v]
          (= v (halite/eval-expr senv tenv env expr))

        '(when b 1) :Unset
        '(when (= 1 1) 1) 1
        '{:$type :ws/Maybe$v1 :x (when b 12)} {:$type :ws/Maybe$v1}))))

(deftest union-tests
  (are [expr etype]
       (= etype (halite/type-check senv tenv expr))

    '(union #{1 2 3} #{3 4 5}) [:Set :Integer]
    '(union #{} #{"foo"}) [:Set :String]
    '(union #{1} #{"foo"}) [:Set :Any]
    '(union) :EmptySet
    '(union #{1}) [:Set :Integer]
    '(union #{[]} #{#{}}) [:Set :Coll]
    '(union #{1} #{2} #{3} #{4}) [:Set :Integer]
    '(union #{#{}} #{#{3}}) [:Set [:Set :Integer]])

  (are [expr err-msg]
       (thrown-with-msg? ExceptionInfo err-msg (halite/type-check senv tenv expr))

    '(union #{1} [2]) #"must be sets")

  (are [expr v]
       (= v (halite/eval-expr senv tenv empty-env expr))

    '(union #{1 2 3} #{3 4 5}) #{1 2 3 4 5}
    '(union #{} #{"foo"}) #{"foo"}
    '(union #{1} #{"foo"}) #{1 "foo"}
    '(union) #{}
    '(union #{1}) #{1}
    '(union #{[]} #{#{}}) #{[] #{}}
    '(union #{1} #{2} #{3} #{4}) #{1 2 3 4}))

(deftest intersection-tests
  (are [expr etype]
       (= etype (halite/type-check senv tenv expr))

    '(intersection #{1 2}) [:Set :Integer]
    '(intersection #{1 2} #{"three"}) :EmptySet
    '(intersection #{1 2} #{}) :EmptySet
    '(intersection #{1 2} (union #{1} #{"two"})) [:Set :Integer]
    '(intersection #{"two" 3} #{12}) [:Set :Integer])

  (are [expr err-msg]
       (thrown-with-msg? ExceptionInfo err-msg (halite/type-check senv tenv expr))

    '(intersection) #"Wrong number of arguments"
    '(intersection #{1} [2]) #"must be sets")

  (are [expr v]
       (= v (halite/eval-expr senv tenv empty-env expr))

    '(intersection #{1 2}) #{1 2}
    '(intersection #{1 2} #{"three"}) #{}
    '(intersection #{1 2} #{}) #{}
    '(intersection #{1 2} (union #{1} #{"two"})) #{1}
    '(intersection #{3} #{12}) #{}))

(deftest difference-tests
  (are [expr etype]
       (= etype (halite/type-check senv tenv expr))

    '(difference #{1 2} #{}) [:Set :Integer]
    '(difference #{1 2} #{"three" true}) [:Set :Integer])

  (are [expr err-msg]
       (thrown-with-msg? ExceptionInfo err-msg (halite/type-check senv tenv expr))

    '(difference #{1}) #"Wrong number of arguments"
    '(difference #{1} #{2} #{3}) #"Wrong number of arguments"
    '(difference #{1} 1) #"must be sets")

  (are [expr v]
       (= v (halite/eval-expr senv tenv empty-env expr))

    '(difference #{} #{1}) #{}
    '(difference #{1 2 3} #{2}) #{1 3}))

(deftest vector-tests
  (are [expr etype]
       (= etype (halite/type-check senv tenv expr))
    '(first [1]) :Integer
    '(rest []) :EmptyVec
    '(rest [1 2]) [:Vec :Integer]
    '(rest [1]) [:Vec :Integer]
    '(conj [] 1) [:Vec :Integer]
    '(conj [1] 2) [:Vec :Integer]
    '(conj [] "one" "two") [:Vec :String]
    '(conj [1] "two") [:Vec :Any]
    '(conj #{} "one") [:Set :String]
    '(concat [] []) :EmptyVec
    '(concat [1 2] [3]) [:Vec :Integer]
    '(into [1 2] [3]) [:Vec :Integer] ;; deprecated
    '(concat #{} []) :EmptySet
    '(concat #{} #{}) :EmptySet
    '(concat #{"foo"} ["bar"]) [:Set :String]
    '(sort []) :EmptyVec
    '(sort #{}) :EmptyVec
    '(sort [1 2]) [:Vec :Integer]
    '(sort #{1 2}) [:Vec :Integer])

  (are [expr err-msg]
       (thrown-with-msg? ExceptionInfo err-msg (halite/type-check senv tenv expr))
    '(first) #"Wrong number of arguments"
    '(first [] []) #"Wrong number of arguments"
    '(first []) #"argument to first is always empty"
    '(first 12) #"must be a vector"
    '(rest) #"Wrong number of arguments"
    '(rest #{}) #"must be a vector"
    '(conj) #"Wrong number of arguments"
    '(conj []) #"Wrong number of arguments"
    '(conj 1 2) #"must be a set or vector"
    '(concat) #"Wrong number of arguments"
    '(concat 1) #"Wrong number of arguments"
    '(concat 1 2) #"must be a set or vector"
    '(concat [] #{}) #"second argument must also be a vector"
    '(into [] #{}) #"second argument must also be a vector" ;; deprecated
    '(sort) #"no matching signature"
    '(sort 1) #"no matching signature")

  (are [expr v]
       (= v (halite/eval-expr senv tenv empty-env expr))

    '(first [1 2 3]) 1
    '(rest []) []
    '(rest [1]) []
    '(rest [1 2 3]) [2 3]
    '(conj [] 1) [1]
    '(conj [1] 2) [1 2]
    '(conj [1] "two") [1 "two"]
    '(conj [1] 2 3 4) [1 2 3 4]
    '(conj #{1} 2 3 2 4) #{1 2 3 4}
    '(concat [] []) []
    '(concat [1 2] [1 2]) [1 2 1 2]
    '(concat #{} #{}) #{}
    '(concat #{} []) #{}
    '(concat #{1 2} [1 2 3]) #{1 2 3}
    '(into #{1 2} [1 2 3]) #{1 2 3})) ;; deprecated

(deftest test-constraint-validation
  (let [senv (update senv :specs merge
                     {:ws/E$v1 {:spec-vars {:x [:Maybe :Integer]
                                            :y :Boolean}
                                :constraints [["x-if-y" '(=> y (some? x))]]}
                      :ws/Invalid$v1 {:spec-vars {}
                                      :constraints [["broken" '(or y true)]]}})]
    ;; type-check cannot detect constraint violations, because that would often involve
    ;; actually evaluating forms
    (are [expr etype]
         (= etype (halite/type-check senv tenv expr))

      {:$type :ws/E$v1, :y false} :ws/E$v1
      {:$type :ws/E$v1, :y true} :ws/E$v1
      {:$type :ws/Invalid$v1} :ws/Invalid$v1)

    ;; type-of, on the other hand, only works with values, and *does* check constraints
    (are [expr err-msg]
         (thrown-with-msg? ExceptionInfo err-msg (halite/type-of senv tenv expr))

      {:$type :ws/E$v1, :y true} #"invalid instance"
      {:$type :ws/Invalid$v1} #"invalid constraint 'broken' of spec 'ws/Invalid\$v1'")

    ;; eval-expr also must check constraints
    (are [expr v]
         (= v (halite/eval-expr senv tenv empty-env expr))
      {:$type :ws/E$v1, :y false} {:$type :ws/E$v1, :y false})

    (are [expr err-msg]
         (thrown-with-msg? ExceptionInfo err-msg (halite/eval-expr senv tenv empty-env expr))

      {:$type :ws/E$v1, :y true} #"invalid instance"
      {:$type :ws/Invalid$v1} #"invalid constraint 'broken' of spec 'ws/Invalid\$v1'")))

(deftest test-refinement-validation
  (let [senv (->TestSpecEnv
              {:ws/A {:spec-vars {:x :Integer}
                      :refines-to {:ws/B {:clauses '[["asB" {:x x}]]}
                                   :ws/C {:inverted? true :clauses '[["fromC" {:x x}]]}}}
               :ws/B {:spec-vars {:x :Integer}
                      :constraints '[["posX" (< 0 x)]]
                      :refines-to {:ws/D {:clauses '[["asD" {:x (+ 1 x)}]]}}}
               :ws/C {:spec-vars {:x :Integer}
                      :constraints '[["boundedX" (< x 10)]]
                      :refines-to {:ws/D {:clauses '[["asD" {:x (+ 1 (* 2 x))}]]}}}
               :ws/D {:spec-vars {:x :Integer}
                      :constraints '[["xIsOdd" (= 1 (mod x 2))]]}
               :ws/E {:spec-vars {}}})]

    (let [invalid-a {:$type :ws/A :x -10}
          sketchy-a {:$type :ws/A :x 20}]
      (is (= :ws/A (halite/type-check senv tenv invalid-a)))
      (is (thrown-with-msg? ExceptionInfo #"invalid instance"
                            (halite/eval-expr senv tenv empty-env invalid-a)))
      (let [a (halite/eval-expr senv tenv empty-env sketchy-a)]
        (is (= a sketchy-a))
        (is (= {:ws/B {:$type :ws/B :x 20}
                :ws/D {:$type :ws/D :x 21}}
               (-> a meta :refinements (dissoc :ws/C)))))

      (is (thrown-with-msg?
           ExceptionInfo #"invalid instance"
           (halite/eval-expr senv
                             (halite-envs/extend-scope tenv 'a :ws/A)
                             (halite-envs/bind empty-env 'a invalid-a)
                             'a)))

      (is (= :ws/B (halite/type-check senv tenv (list 'refine-to sketchy-a :ws/B))))
      (is (= :ws/E (halite/type-check senv tenv '(refine-to {:$type :ws/D :x 1} :ws/E))))
      (is (thrown-with-msg?
           ExceptionInfo #"No active refinement path from 'ws/D' to 'ws/E'"
           (halite/eval-expr senv tenv empty-env '(refine-to {:$type :ws/D :x 1} :ws/E))))
      (is (thrown-with-msg?
           ExceptionInfo #"Spec not found: 'foo/Bar'"
           (halite/type-check senv tenv '(refine-to {:$type :ws/E} :foo/Bar))))
      (is (thrown-with-msg?
           ExceptionInfo #"First argument to 'refine-to' must be an instance"
           (halite/eval-expr senv tenv empty-env '(refine-to (+ 1 2) :ws/B))))
      (is (thrown-with-msg?
           ExceptionInfo #"Second argument to 'refine-to' must be a spec id"
           (halite/eval-expr senv tenv empty-env '(refine-to {:$type :ws/A :x 2} (+ 1 2)))))
      (is (thrown-with-msg?
           ExceptionInfo #"Refinement from 'ws/A' failed unexpectedly: invalid instance of 'ws/C'"
           (halite/eval-expr senv tenv empty-env (list 'refine-to sketchy-a :ws/C))))
      (is (= {:$type :ws/B :x 20}
             (halite/eval-expr senv tenv empty-env (list 'refine-to sketchy-a :ws/B))))
      (is (= {:$type :ws/E} (halite/eval-expr senv tenv empty-env '(refine-to {:$type :ws/E} :ws/E)))))))

(deftest test-refinement-with-guards
  (let [senv (->TestSpecEnv
              {:ws/A {:spec-vars {:x :Integer}
                      :constraints '[["posX" (< 0 x)]]}
               :ws/B {:spec-vars {:y :Integer}
                      :refines-to {:ws/A {:clauses '[["A passthru" {:x y} (< 0 y)]
                                                     ["A via negation" {:x (- 0 y)} (< y 0)]]}}}})]

    (is (= true (halite/eval-expr senv tenv empty-env
                                  '(refines-to? {:$type :ws/B, :y 5} :ws/A))))
    (is (= {:$type :ws/A, :x 5} (halite/eval-expr senv tenv empty-env
                                                  '(refine-to {:$type :ws/B, :y 5} :ws/A))))
    (is (= true (halite/eval-expr senv tenv empty-env
                                  '(refines-to? {:$type :ws/B, :y -5} :ws/A))))
    (is (= {:$type :ws/A, :x 5} (halite/eval-expr senv tenv empty-env
                                                  '(refine-to {:$type :ws/B, :y -5} :ws/A))))
    (is (= false (halite/eval-expr senv tenv empty-env
                                   '(refines-to? {:$type :ws/B, :y 0} :ws/A))))
    (is (thrown-with-msg?
         ExceptionInfo #"No active refinement path"
         (halite/eval-expr senv tenv empty-env
                           '(refine-to {:$type :ws/B, :y 0} :ws/A))))))

(deftest test-conflicting-guards
  (let [senv (->TestSpecEnv
              {:ws/A {:spec-vars {:x :Integer}
                      :constraints '[["non-neg x" (<= 0 x)]]}
               :ws/B {:spec-vars {:y :Integer}
                      :refines-to {:ws/A {:clauses '[["A passthru" {:x y} (<= 0 y)]
                                                     ["A via negation" {:x (- 0 y)} (<= y 0)]]}}}})]
    (is (thrown-with-msg?
         ExceptionInfo #"Only one guard may be active"
         (halite/eval-expr senv tenv empty-env
                           '(refines-to? {:$type :ws/B, :y 0} :ws/A))))))

(deftest test-instance-type
  (let [senv (->TestSpecEnv
              {:ws/A {:spec-vars {:x :Integer}
                      :constraints '[["posX" (< 0 x)]
                                     ["boundedX" (< x 10)]]}
               :ws/A1 {:spec-vars {}
                       :refines-to {:ws/A {:clauses '[["asA" {:x 5}]]}}}
               :ws/A2 {:spec-vars {:a :Integer
                                   :b :Integer}
                       :refines-to {:ws/A {:clauses '[["asA" {:x (+ a b)}]]}}}
               :ws/B {:spec-vars {:a :Instance}}
               :ws/C {:spec-vars {:as [:Vec :Instance]}}
               :ws/D {:spec-vars {}}})
        tenv2 (-> tenv
                  (halite-envs/extend-scope 'ax :Instance)
                  (halite-envs/extend-scope 'ay :Instance)
                  (halite-envs/extend-scope 'b1 :ws/B))
        env2 (-> empty-env
                 (halite-envs/bind 'ax {:$type :ws/A2 :a 3 :b 4})
                 (halite-envs/bind 'ay {:$type :ws/A1})
                 (halite-envs/bind 'b1 {:$type :ws/B :a {:$type :ws/A1}}))]

    (are [expr etype]
         (= etype (halite/type-check senv tenv2 expr))

      {:$type :ws/A1} :ws/A1
      {:$type :ws/A2 :a 1 :b 2} :ws/A2
      {:$type :ws/B :a {:$type :ws/A1}} :ws/B
      {:$type :ws/B :a {:$type :ws/A2 :a 1 :b 2}} :ws/B
      '(get {:$type :ws/B :a {:$type :ws/A2 :a 1 :b 2}} :a) :Instance
      'ax :Instance
      '(= ax ay) :Boolean
      '(= b1 ax) :Boolean
      {:$type :ws/B :a {:$type :ws/D}} :ws/B
      '(refine-to ax :ws/A) :ws/A
      '(union #{{:$type :ws/A1}} #{{:$type :ws/A2 :a 3 :b 3}}) [:Set :Instance])

    (are [expr v]
         (= v (halite/eval-expr senv tenv2 env2 expr))

      '(refine-to ax :ws/A) {:$type :ws/A :x 7}
      '(= ax (refine-to ax :ws/A2)) true)

    (are [expr err-msg]
         (thrown-with-msg? ExceptionInfo err-msg (halite/eval-expr senv tenv empty-env expr))

      {:$type :ws/A2 :a 7 :b 8} #"invalid instance"
      '(refine-to (get {:$type :ws/B :a {:$type :ws/D}} :a) :ws/A) #"No active refinement path from 'ws/D' to 'ws/A'")))

(deftest test-refines-to?
  (let [senv (->TestSpecEnv
              {:ws/A {:spec-vars {:x :Integer}
                      :constraints '[["posX" (< 0 x)]
                                     ["boundedX" (< x 10)]]}
               :ws/A1 {:spec-vars {}
                       :refines-to {:ws/A {:clauses '[["asA" {:x 5}]]}}}
               :ws/A2 {:spec-vars {:a :Integer
                                   :b :Integer}
                       :refines-to {:ws/A {:clauses '[["asA" {:x (+ a b)}]]}}}
               :ws/B {:spec-vars {:a :Instance}}
               :ws/C {:spec-vars {:as [:Vec :Instance]}}
               :ws/D {:spec-vars {}}})
        tenv2 (-> tenv
                  (halite-envs/extend-scope 'ax :Instance)
                  (halite-envs/extend-scope 'ay :Instance)
                  (halite-envs/extend-scope 'b1 :ws/B))
        env2 (-> empty-env
                 (halite-envs/bind 'ax {:$type :ws/A2 :a 3 :b 4})
                 (halite-envs/bind 'ay {:$type :ws/A1})
                 (halite-envs/bind 'b1 {:$type :ws/B :a {:$type :ws/A1}}))]

    (are [expr etype]
         (= etype (halite/type-check senv tenv2 expr))

      '(refines-to? ax :ws/A) :Boolean
      '(refines-to? {:$type :ws/D} :ws/A) :Boolean)

    (are [expr err-msg]
         (thrown-with-msg? ExceptionInfo err-msg (halite/type-check senv tenv2 expr))

      '(refines-to? ax) #"Wrong number of arguments"
      '(refines-to? ax :foo/Bar) #"Spec not found: 'foo/Bar'"
      '(refines-to? (+ 1 2) :ws/A) #"First argument to 'refines-to\?' must be an instance"
      '(refines-to? ax "foo") #"Second argument to 'refines-to\?' must be a spec id")

    (are [expr v]
         (= v (halite/eval-expr senv tenv2 env2 expr))

      '(refines-to? ax :ws/A) true
      '(refines-to? ax :ws/A2) true
      '(refines-to? ax :ws/A1) false)))

(deftest test-concrete?
  (let [senv (->TestSpecEnv
              {:ws/A {:spec-vars {:x :Integer}
                      :constraints '[["posX" (< 0 x)]
                                     ["boundedX" (< x 10)]]
                      :abstract? true}
               :ws/A1 {:spec-vars {}
                       :refines-to {:ws/A {:clauses '[["asA" {:x 5}]]}}}
               :ws/A2 {:spec-vars {:a :Integer
                                   :b :Integer}
                       :refines-to {:ws/A {:clauses '[["asA" {:x (+ a b)}]]}}}
               :ws/B {:spec-vars {:a :ws/A}}
               :ws/C {:spec-vars {:as [:Vec :ws/A]}}
               :ws/D {:spec-vars {}}})]

    (are [expr etype]
         (= etype (halite/type-check senv tenv expr))

      '(concrete? 1) :Boolean
      '(concrete? true) :Boolean
      '(concrete? "foo") :Boolean
      '(concrete? {:$type :ws/A :x 1}) :Boolean
      '(concrete? [{:$type :ws/A :x 1}]) :Boolean)

    (are [expr v]
         (= v (halite/eval-expr senv tenv empty-env expr))

      '(concrete? 1) true
      '(concrete? true) true
      '(concrete? "foo") true
      '(concrete? {:$type :ws/A :x 1}) false
      '(concrete? {:$type :ws/A1}) true
      '(concrete? (refine-to {:$type :ws/A1} :ws/A)) false
      '(concrete? [{:$type :ws/A :x 1}]) false
      '(concrete? [{:$type :ws/A1}]) true)))

(deftest abstract-specs
  (let [senv (->TestSpecEnv
              {:ws/A {:spec-vars {:x :Integer}
                      :constraints '[["posX" (< 0 x)]
                                     ["boundedX" (< x 10)]]
                      :abstract? true}
               :ws/A1 {:spec-vars {}
                       :refines-to {:ws/A {:clauses '[["asA" {:x 6}]]}}}
               :ws/A2 {:spec-vars {:a :Integer
                                   :b :Integer}
                       :refines-to {:ws/A {:clauses '[["asA" {:x (+ a b)}]]}}}
               :ws/B {:spec-vars {:a :ws/A}
                      :constraints '[["notFive" (not= 5 (get (refine-to a :ws/A) :x))]]}
               :ws/C {:spec-vars {:as [:Vec :ws/A]}}
               :ws/D {:spec-vars {}}
               :ws/Invalid {:spec-vars {:a :ws/A}
                            ;; a is typed as :Instance, so (get a :x) doesn't type check
                            :constraints '[["notFive" (not= 5 (get a :x))]]}})
        tenv2 (-> tenv
                  (halite-envs/extend-scope 'ax :Instance)
                  (halite-envs/extend-scope 'ay :Instance)
                  (halite-envs/extend-scope 'b1 :ws/B)
                  (halite-envs/extend-scope 'c :ws/C))
        env2 (-> empty-env
                 (halite-envs/bind 'ax {:$type :ws/A2 :a 3 :b 4})
                 (halite-envs/bind 'ay {:$type :ws/A1})
                 (halite-envs/bind 'b1 {:$type :ws/B :a {:$type :ws/A1}})
                 (halite-envs/bind 'c {:$type :ws/C :as [{:$type :ws/A1} {:$type :ws/A2 :a 2 :b 7}]}))]

    (are [expr etype]
         (= etype (halite/type-check senv tenv2 expr))

      {:$type :ws/A :x 5} :ws/A
      '(get b1 :a) :Instance
      '(get c :as) [:Vec :Instance]
      {:$type :ws/B :a {:$type :ws/A1}} :ws/B
      {:$type :ws/B :a {:$type :ws/D}} :ws/B
      {:$type :ws/C :as [{:$type :ws/D} {:$type :ws/A1}]} :ws/C
      '{:$type :ws/C :as (conj (get c :as) {:$type :ws/D})} :ws/C
      '{:$type :ws/C :as (conj (get c :as) {:$type :ws/A :x 9})} :ws/C)

    (are [expr err-msg]
         (thrown-with-msg? ExceptionInfo err-msg (halite/eval-expr senv tenv2 env2 expr))

      {:$type :ws/Invalid :a {:$type :ws/A1}} #"invalid constraint")

    (are [expr err-msg]
         (thrown-with-msg? ExceptionInfo err-msg (halite/eval-expr senv tenv2 env2 expr))

      {:$type :ws/B :a {:$type :ws/A :x 4}} #"cannot contain abstract value"
      {:$type :ws/B :a {:$type :ws/D}} #"No active refinement path from 'ws/D' to 'ws/A'"
      '{:$type :ws/C :as (conj (get c :as) {:$type :ws/D})} #"No active refinement path from 'ws/D' to 'ws/A'"
      '{:$type :ws/C :as (conj (get c :as) {:$type :ws/A :x 9})} #"instance cannot contain abstract value")

    (are [expr v]
         (= v (halite/eval-expr senv tenv2 env2 expr))

      '(conj (get c :as) {:$type :ws/D}) [{:$type :ws/A1} {:$type :ws/A2 :a 2 :b 7} {:$type :ws/D}]
      {:$type :ws/B :a {:$type :ws/A1}} {:$type :ws/B :a {:$type :ws/A1}})))

(deftest test-json-spec
  ;; This test doesn't cover any functionality not already covered above, but it
  ;; illustrates that 'recursive' data structures can be defined.
  (letfn [(to-json [arg]
            (cond
              (string? arg) {:$type :ws/JsonStr :s arg}
              (boolean? arg) {:$type :ws/JsonBool :b arg}
              (int? arg) {:$type :ws/JsonInt :n arg}
              (vector? arg) {:$type :ws/JsonVec :entries (mapv to-json arg)}
              (map? arg) {:$type :ws/JsonObj
                          :entries (set (map (fn [[k v]] {:$type :ws/JsonObjEntry :key k :val (to-json v)}) arg))}
              :else (throw (ex-info "Not convertable to json" {:value arg}))))]
    (let [senv (->TestSpecEnv
                {:ws/JsonVal
                 {:abstract? true, :spec-vars {}, :constraints [], :refines-to {}}

                 :ws/JsonStr
                 {:spec-vars {:s :String}
                  :refines-to {:ws/JsonVal {:clauses [["asJsonVal" {}]]}}}

                 :ws/JsonBool
                 {:spec-vars {:b :Boolean}
                  :refines-to {:ws/JsonVal {:clauses [["asJsonVal" {}]]}}}

                 :ws/JsonInt
                 {:spec-vars {:n :Integer}
                  :refines-to {:ws/JsonVal {:clauses [["asJsonVal" {}]]}}}

                 :ws/JsonVec
                 {:spec-vars {:entries [:Vec :ws/JsonVal]}
                  :refines-to {:ws/JsonVal {:clauses [["asJsonVal" {}]]}}}

                 :ws/JsonObjEntry
                 {:spec-vars {:key :String, :val :ws/JsonVal}}

                 :ws/JsonObj
                 {:spec-vars {:entries [:Set :ws/JsonObjEntry]}
                  :constraints [["uniqueKeys" true
                                 ;; TODO: each key shows up once
                                 #_(= (count entries) (count (for [entry entries] (get* entry :key))))]]
                  :refines-to {:ws/JsonVal {:clauses [["asJsonVal" {}]]}}}})]

      (are [v expected]
           (= expected (halite/eval-expr senv tenv empty-env (to-json v)))

        1 {:$type :ws/JsonInt :n 1}
        true {:$type :ws/JsonBool :b true}
        "foo" {:$type :ws/JsonStr :s "foo"}
        [1 true "foo"] {:$type :ws/JsonVec :entries
                        [{:$type :ws/JsonInt :n 1}
                         {:$type :ws/JsonBool :b true}
                         {:$type :ws/JsonStr :s "foo"}]}
        {"hi" "there"} {:$type :ws/JsonObj :entries
                        #{{:$type :ws/JsonObjEntry :key "hi" :val
                           {:$type :ws/JsonStr :s "there"}}}}))))
