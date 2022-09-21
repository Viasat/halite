;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.test-halite
  (:require [clojure.edn :as edn]
            [clojure.test :as test :refer [deftest is are test-vars]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [jibe.halite :as halite]
            [jibe.halite-syntax-check :as halite-syntax-check]
            [jibe.halite-type-check :as halite-type-check]
            [jibe.halite-type-of :as halite-type-of]
            [jibe.halite.halite-envs :as halite-envs]
            [jibe.halite.halite-lint :as halite-lint]
            [jibe.lib.format-errors :as format-errors]
            [schema.test :refer [validate-schemas]])
  (:import [clojure.lang ExceptionInfo]))

(clojure.test/use-fixtures :once validate-schemas)

(deftest test-halite-symbol-examples
  (are [string] (re-matches halite-syntax-check/symbol-regex string)
    "foo" "$foo" "!20<90" "-->*/*<--" "+" "</>" "a.b/c.d" "a.b" "True" "nilable" "nil/foo")
  (are [string] (not (re-matches halite-syntax-check/symbol-regex string))
    "72" "+8" "-9" "//" "/z" "foo/9" "-/1" "/" "foo//" "$//" "true" "false" "nil" "a/nil" ""))

(defn halite-symbol-differs-from-clj-symbol [string]
  (let [obj (try (edn/read-string string) (catch Exception ex nil))
        clj-sym? (and (symbol? obj) (= (str obj) string))]
    (if (re-matches halite-syntax-check/symbol-regex string)
      clj-sym?
      (or (not clj-sym?)
          ;; halite symbols disallow these chars that are legal in Clojure symbols:
          (re-find #"[:#|%&']" string)
          ;; Clojure symbols can have a slash in the name part. halite does not
          ;; support this:
          (= "/" string)
          (re-find #"/.*/" string)
          ;; If the name part can't be read as a symbol, we don't want to allow it (e.g. "foo/+0")
          (not (symbol? (try (edn/read-string (name obj))
                             (catch Exception ex :ok))))))))

(defspec symbol-prop
  {:num-tests 100000
   :reporter-fn (fn [_])}
  (prop/for-all [s gen/string-ascii]
                (halite-symbol-differs-from-clj-symbol s)))

(defrecord TestSpecEnv [specs]
  halite-envs/SpecEnv
  (lookup-spec* [self spec-id]
    (when-let [{:keys [spec-vars constraints refines-to] :as spec} (get specs spec-id)]
      (cond-> spec
        (nil? spec-vars) (assoc :spec-vars {})
        (nil? constraints) (assoc :constraints [])
        (nil? refines-to) (assoc :refines-to {})))))

(def senv (map->TestSpecEnv
           {:specs {:ws/A$v1 {:spec-vars {:x "Integer"
                                          :y "Boolean"
                                          :c :ws2/B$v1}}
                    :ws2/B$v1 {:spec-vars {:s "String"}}
                    :ws/C$v1 {:spec-vars {:xs ["Integer"]}}
                    :ws/D$v1 {:spec-vars {:xss [["Integer"]]}}}}))

(def tenv (halite-envs/type-env {}))

(def empty-env (halite-envs/env {}))

(deftest literal-type-tests
  (are [expr etype]
       (= etype (halite/type-check senv tenv expr))

    true :Boolean
    false :Boolean
    1 :Integer
    "hi" :String
    {:$type :ws2/B$v1 :s "foo"} [:Instance :ws2/B$v1]
    {:$type :ws/A$v1 :x 1 :y true
     :c {:$type :ws2/B$v1 :s "bar"}} [:Instance :ws/A$v1]
    #{} [:Set :Nothing]
    [] [:Vec :Nothing]
    [1 2 3] [:Vec :Integer]
    #{1 2 3} [:Set :Integer]
    [[]] [:Vec [:Vec :Nothing]]
    [#{} #{"foo"}] [:Vec [:Set :String]]
    [1 "two"] [:Vec :Value]
    #{[] #{}} [:Set [:Coll :Nothing]]
    #{[1] #{}} [:Set [:Coll :Integer]]
    {:$type :ws/C$v1 :xs []} [:Instance :ws/C$v1]
    {:$type :ws/C$v1 :xs [1 2 3]} [:Instance :ws/C$v1]
    [{:$type :ws2/B$v1 :s "bar"}] [:Vec [:Instance :ws2/B$v1]]
    {:$type :ws/D$v1 :xss [[]]} [:Instance :ws/D$v1])

  (are [expr err-msg]
       (thrown-with-msg? ExceptionInfo err-msg (halite/type-check senv tenv expr))

    {} #"Instance literal must have :\$type field"
    {:$type "foo"} #"Expected namespaced keyword as value of :\$type"
    {:$type :bar} #"Expected namespaced keyword as value of :\$type"
    {:$type :foo/bar} #"Resource spec not found"
    {:$type :ws/A$v1} #"Missing required variables"
    {:$type :ws/A$v1 :x 1 :y 1 :c {:$type :ws2/B$v1 :s "foo"}} #"Value of 'y' has wrong type"
    {:$type :ws/A$v1 :x 1 :y false :c {:$type :ws2/B$v1 :s 12}} #"Value of 's' has wrong type"
    {:$type :ws2/B$v1 :s "foo" :foo "bar"} #"Variables not defined on spec: foo"
    {:$type :ws/C$v1 :xs [1 "two"]} #"Value of 'xs' has wrong type"))

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

(deftest error-tests
  (are [expr etype]
       (= etype (halite/type-check senv tenv expr))
    '(error "You cannot pass") :Nothing
    '(error (str "error" " message")) :Nothing)

  (are [expr err-msg]
       (thrown-with-msg? ExceptionInfo err-msg (halite-lint/type-check senv tenv expr))
    '(error 10) #"No matching signature for 'error'"
    '(error (error "foo")) #"Disallowed.*expression"
    '(let [x (error "Fail")] "Dead code") #"Disallowed binding 'x' to :Nothing")

  (are [expr err-msg]
       (thrown-with-msg? ExceptionInfo err-msg (halite/eval-expr senv tenv empty-env expr))
    '(error "You cannot pass") #"Spec threw error: \"You cannot pass\""
    '(error (str "error" " message")) #"Spec threw error: \"error message\""))

(deftest application-tests
  (are [expr etype]
       (= etype (halite/type-check senv tenv expr))

    '(+ 1 2) :Integer
    '(+ (- 3 2) (* 4 5)) :Integer
    '(and (< 1 2) (> (+ 5 6) 90) (or true (<= 1 4))) :Boolean
    '(=> true false) :Boolean
    '(not true) :Boolean
    '(count [true true false]) :Integer
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
    '(map [x #{}] "blue") [:Set :Nothing]
    '(map [x []] "red") [:Vec :Nothing]
    '(filter [x #{3 7}] (< x 5)) [:Set :Integer]
    '(filter [x [3 7]] (< x 5)) [:Vec :Integer]
    '(filter [x #{}] true) [:Set :Nothing]
    '(filter [x []] false) [:Vec :Nothing]
    '(sort #{3 1 2}) [:Vec :Integer]
    '(sort #{#d "3.3" #d "1.1" #d "2.2"}) [:Vec [:Decimal 1]]
    '(sort [3 1 2]) [:Vec :Integer]
    '(sort [#d "3.3" #d "1.1" #d "2.2"]) [:Vec [:Decimal 1]]
    '(sort #{}) [:Vec :Nothing]
    '(sort []) [:Vec :Nothing]
    '(sort-by [x #{3 1 2}] x) [:Vec :Integer]
    '(sort-by [x #{[3] [1] [2]}] (get x 0)) [:Vec [:Vec :Integer]]
    '(sort-by [x [[1 3] [2 1] [3 2]]] (get x 1)) [:Vec [:Vec :Integer]]
    '(sort-by [x #{}] 1) [:Vec :Nothing]
    '(sort-by [x []] 2) [:Vec :Nothing]
    '(reduce [acc 0] [x [1 2 3]] (+ (* 10 acc) x)) :Integer
    '(reduce [acc [0]] [x [1 2 3]] (concat [x] acc)) [:Vec :Integer]
    '(reduce [a [[1]]] [x []] a) [:Vec [:Vec :Integer]]
    '(abs -10) :Integer)

  (are [expr err-msg]
       (thrown-with-msg? ExceptionInfo err-msg (halite-lint/type-check senv tenv expr))

    '(foo) #"Unknown function or operator: foo"
    '(+ 1 "two") #"No matching signature for '\+'"
    '(+ 1) #"No matching signature for '\+'"
    '(any? [x #{"nan"}] (+ x 10)) #"No matching signature for '\+'"
    '(any? [x #{}] x) #"must be boolean"
    '(any? [x #{}] (< x 5)) #":Nothing"
    '(map [x [1]] $no-value) #"must produce a value"
    '(map [x [1]] (when false 2)) #"must produce a value"
    '(map [x []] $no-value) #"must produce a value"
    '(map [x #{"nan"}] (+ x 10)) #"No matching signature for '\+'"
    '(map [x #{1}] $no-value) #"must produce a value"
    '(map [x #{1}] (when false 2)) #"must produce a value"
    '(map [x #{}] $no-value) #"must produce a value"
    '(map [x #{}] (+ x 10)) #":Nothing"
    '(filter [x #{"nan"}] (< x 10)) #"No matching signature for '\<'"
    '(filter [x #{}] (< x 10)) #":Nothing"
    '(sort 5) #"No matching signature"
    '(sort-by [x []] x) #"must be sortable"
    '(sort-by [x 5] 1) #"Collection required"
    '(sort-by [1 5] 1) #"must be a bare symbol"
    '(reduce [0 1] [x []] 2) #"must be a bare symbol"
    '(reduce [a 1] [2 []] 3) #"must be a bare symbol"
    '(reduce [a 1] [x 2] 3) #"must be a vector"
    '(reduce [a 1] [x #{1 2}] 3) #"must be a vector"
    '(reduce [a 1] [x []] (+ a x)) #":Nothing")

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
    '(count [1 2 3 1]) 4
    '(and true false true) false
    '(or true false false) true
    '(or false false) false
    '(not false) true
    '(not true) false
    '(and (<= (+ 3 5) (* 2 2 2)) (or (> 0 1) (<= (count #{1 2}) 3))) true
    '(contains? #{1 2 3} 2) true
    '(inc 12) 13
    '(dec 12) 11
    '(div 14 3) 4
    '(mod 5 3) 2
    '(expt 2 8) 256
    '(str "foo" (str) (str "bar")) "foobar"
    '(subset? #{} #{1 2 3}) true
    '(subset? #{"nope"} #{1 2 3}) false
    '(range 5) [0 1 2 3 4]
    '(range 2 5) [2 3 4]
    '(range 2 5 2) [2 4]
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
    '(sort #{3 1 2}) [1 2 3]
    '(sort #{#d "3.3" #d "1.1" #d "2.2"}) [#d "1.1" #d "2.2" #d "3.3"]
    '(sort [3 1 2]) [1 2 3]
    '(sort [#d "3.3" #d "1.1" #d "2.2"]) [#d "1.1" #d "2.2" #d "3.3"]
    '(sort []) []
    '(sort-by [x #{3 1 2}] x) [1 2 3]
    '(sort-by [x #{[3] [1] [2]}] (get x 0)) [[1] [2] [3]]
    '(sort-by [x #{[#d "3.3"] [#d "1.1"] [#d "2.2"]}] (get x 0)) [[#d "1.1"] [#d "2.2"] [#d "3.3"]]
    '(sort-by [x [[1 3] [2 1] [3 2]]] (get x 1)) [[2 1] [3 2] [1 3]]
    '(sort-by [x [[#d "1.1" #d "3.3"] [#d "2.2" #d "1.1"] [#d "3.3" #d "2.2"]]] (get x 1)) [[#d "2.2" #d "1.1"] [#d "3.3" #d "2.2"] [#d "1.1" #d "3.3"]]
    '(sort-by [x #{}] 1) []
    '(sort-by [x []] 2) []
    '(reduce [acc 0] [x [1 2 3]] (+ (* 10 acc) x)) 123
    '(reduce [acc [0]] [x [1 2 3]] (concat [x] acc)) [3 2 1 0]
    '(abs 10) 10
    '(abs -10) 10))

(deftest get-type-checking-tests
  (let [senv (->TestSpecEnv
              {:ws/A$v1 {:spec-vars {:x "Integer"}}
               :ws/B$v1 {:spec-vars {:a :ws/A$v1}}
               :ws/C$v1 {:spec-vars {:bs [:ws/B$v1]}}})
        tenv (halite-envs/type-env
              {'a [:Instance :ws/A$v1]
               'b [:Instance :ws/B$v1]
               'c [:Instance :ws/C$v1]
               'xs [:Vec :String]})]
    (are [expr etype]
         (= etype (halite/type-check senv tenv expr))

      'a [:Instance :ws/A$v1] ; warm-up: symbol lookup
      '(get a :x) :Integer
      '(get b :a) [:Instance :ws/A$v1]
      '(get (get b :a) :x) :Integer
      '(get xs (+ 1 2)) :String
      '(get (get (get (get c :bs) 2) :a) :x) :Integer
      '(get-in c [:bs 2 :a :x]) :Integer)

    (are [expr err-msg]
         (thrown-with-msg? ExceptionInfo err-msg (halite-lint/type-check senv tenv expr))

      'foo #"Undefined"
      '(get) #"Wrong number of arguments"
      '(get [] 1) #"Index out of bounds"
      '(get xs (< 1 2)) #"must be an integer"
      '(get a :foo/bar) #"must be a variable name"
      '(get a 12) #"must be a variable name"
      '(get a :b) #"Variables not defined on spec: b"
      '(get #{} 1) #"must be an instance of known type or non-empty vector"
      '(let [x [1]] (get-in c x)) #"must be a vector literal")))

(deftest get-eval-tests
  (let [c {:$type :ws/C$v1
           :bs (mapv (fn [x]
                       {:$type :ws/B$v1
                        :a {:$type :ws/A$v1
                            :x x}})
                     (range 5))}
        senv (->TestSpecEnv
              {:ws/A$v1 {:spec-vars {:x "Integer"}}
               :ws/B$v1 {:spec-vars {:a :ws/A$v1}}
               :ws/C$v1 {:spec-vars {:bs [:ws/B$v1]}}})
        tenv (halite-envs/type-env {'c [:Instance :ws/C$v1]})
        env (halite-envs/env {'c c})]
    (are [expr v]
         (= v (halite/eval-expr senv tenv env expr))
      'c c
      '(get c :bs) (get c :bs)
      '(get (get c :bs) 2) (get-in c [:bs 2])
      '(get (get (get c :bs) 2) :a) (get-in c [:bs 2 :a])
      '(get-in c [:bs 2]) (get-in c [:bs 2])
      '(get-in c [:bs 2 :a]) (get-in c [:bs 2 :a]))
    (is (thrown-with-msg? ExceptionInfo #"out of bounds"
                          (halite/eval-expr senv tenv env '(get (get c :bs) 10))))))

(deftest equality-tests
  (are [expr etype]
       (= etype (halite/type-check senv tenv expr))

    '(= 1 (+ 2 3)) :Boolean
    '(= [#{}] [#{1} #{2}]) :Boolean
    '(= [#{12}] [#{}]) :Boolean
    '(not= [#{12}] [#{}]) :Boolean
    '(= (when false 5) (when false "foo")) :Boolean)

  (are [expr err-msg]
       (thrown-with-msg? ExceptionInfo err-msg (halite-lint/type-check senv tenv expr))

    '(= 1 "two") #"would always be false"
    '(= [] #{}) #"would always be false"
    '(not= 1 "two") #"would always be true"
    '(not= [] #{}) #"would always be true")

  (are [expr v]
       (= v (halite/eval-expr senv tenv empty-env expr))

    '(= (* 2 3) (+ 2 4)) true
    '(= [1 2 3] [2 1 3]) false
    '(not= (* 2 3) (+ 2 4)) false
    '(not= [1 2 3] [2 1 3]) true
    '(= (when false 5) (when false "foo")) true))

(deftest if-tests
  (are [expr etype]
       (= etype (halite/type-check senv tenv expr))
    '(if true 1 2) :Integer
    '(if false [[]] [[1]]) [:Vec [:Vec :Integer]]
    '(if true 1 "two") :Value
    '(conj #{} (if true 1 "two")) [:Set :Value]
    '(conj (if true #{} []) (if true 1 "two")) [:Coll :Value])

  (are [expr err-msg]
       (thrown-with-msg? ExceptionInfo err-msg (halite/type-check senv tenv expr))

    '(conj #{} (if true (when false 1) 2)) #"possibly unset value"
    '(if true 2) #"Wrong number of arguments"
    '(if 1 2 3) #"must be boolean")

  (are [expr v]
       (= v (halite/eval-expr senv tenv empty-env expr))

    '(if true 1 2) 1
    '(if (< 1 (count [])) 12 (+ 2 3)) 5))

(deftest let-tests
  (are [expr etype]
       (= etype (halite-lint/type-check senv tenv expr))

    '(let [x (+ 1 2)
           y [x]]
       y) [:Vec :Integer]
    '(let [x 1] (let [x "foo"] x)) :String
    '(let [x "foo"
           y (let [x 1] (+ x 2))]
       y) :Integer)

  (are [expr err-msg]
       (thrown-with-msg? ExceptionInfo err-msg (halite-lint/type-check senv tenv expr))

    '(let) #"Wrong number of arguments"
    '(let [x] x) #"must have an even number of forms"
    '(let []) #"Wrong number of arguments"
    '(let [1 2] 1) #"must be bare symbols"
    '(let [x "foo"] (+ x 1)) #"No matching signature"
    '(let [$type 7] 8) #"must not.*[$]"
    '(let [$foo 7] 8) #"must not.*[$]")

  (are [expr v]
       (= v (halite/eval-expr senv tenv empty-env expr))

    '(let [] 1) 1
    '(let [x (+ 1 2), y [x]] y) [3]
    '(let [x 1] (let [x "foo"] x)) "foo"
    '(let [x "foo", y (let [x 1] (+ x 2))] y) 3))

(deftest maybe-tests
  (let [senv (assoc-in senv [:specs :ws/Maybe$v1] {:spec-vars {:x [:Maybe "Integer"]}})
        tenv (-> tenv
                 (halite-envs/extend-scope 'm [:Instance :ws/Maybe$v1])
                 (halite-envs/extend-scope 'x [:Maybe :Integer]))]
    (are [expr etype]
         (= etype (halite-lint/type-check senv tenv expr))

      '$no-value :Unset
      'no-value :Unset
      {:$type :ws/Maybe$v1} [:Instance :ws/Maybe$v1]
      {:$type :ws/Maybe$v1 :x 1} [:Instance :ws/Maybe$v1]
      {:$type :ws/Maybe$v1 :x '$no-value} [:Instance :ws/Maybe$v1]
      '(get m :x) [:Maybe :Integer]
      '(if-value x (+ x 1) 1) :Integer
      '(if-value x "foo" true) :Value
      '(if-value x x (when true "foo")) :Any
      '(when-value x "foo") [:Maybe :String]
      '(when-value x (+ x 1)) [:Maybe :Integer]
      '(when-value-let [y x] y) [:Maybe :Integer]
      '(when-value-let [y (get m :x)] y) [:Maybe :Integer]
      '(if-value x true false) :Boolean
      '(= $no-value x) :Boolean
      '(= 5 x) :Boolean
      '(let [no-value 42] no-value) :Integer
      '(if-value-let [y x] "foo" true) :Value
      '(if-value-let [y (get m :x)] y 5) :Integer)

    (are [expr err-msg]
         (thrown-with-msg? ExceptionInfo err-msg (halite-lint/type-check senv tenv expr))

      '(let [x $no-value] (if-value x x 1)) #"Disallowed binding.*to :Unset"
      '(let [no-value $no-value] 2) #"Disallowed binding.*to :Unset"
      '(let [$no-value 5] $no-value) #"Cannot bind a value to the reserved word"
      '(if-value 12 true false) #"must be a bare symbol"
      '(when-value 12 true) #"must be a bare symbol"
      '(+ 10 (if-value x $no-value 5)) #"No matching signature for '\+'"
      '(if-value x 1 (+ x 5)) #"No matching signature for '\+'"
      '(let [y 22] (if-value y true false)) #"must have an optional type"
      '(let [y 22] (when-value y false)) #"must have an optional type"
      '(= "foo" x) #"would always be false"
      '(= $no-value 5) #"would always be false"
      '(= m 5) #"would always be false")

    (let [env (halite-envs/env {'m {:$type :ws/Maybe$v1}
                                'x :Unset})]
      (are [expr v]
           (= v (halite/eval-expr senv tenv env expr))

        '$no-value :Unset
        {:$type :ws/Maybe$v1 :x '$no-value} {:$type :ws/Maybe$v1}
        '(get m :x) :Unset
        '(if-value x x 12) 12
        '(when-value x 12) :Unset
        '(let [y (when true "foo")] (when-value y 12)) 12
        '(= x (get m :x)) true
        '(= 5 (get m :x)) false
        '(let [no-value 42] no-value) 42
        '(if-value x true false) false
        '(if-value m true false) true
        '(if-value-let [y x] "foo" true) true
        '(when-value-let [y x] "foo") :Unset
        '(if-value-let [y (get m :x)] y 5) 5
        '(when-value-let [y (get m :x)] y) :Unset
        '(if-value-let [y (if true 10 $no-value)] y 5) 10
        '(when-value-let [y (if true 10 $no-value)] y) 10))))

(deftest no-value-restrictions
  ;; We want to limit the ways in which [:Maybe <T>] can be used.
  ;; Specifically:
  ;; 1) [:Set [:Maybe <T>]] and [:Vec [:Maybe <T>]] are not valid types!
  ;;    We want `no-value` to represent the absence of a value, and you
  ;;    can't put a value you don't have in a collection!
  ;; 2) [:Maybe [:Maybe <T>]] is not a valid type!
  (let [tenv (halite-envs/extend-scope tenv 'x [:Maybe :Integer])]
    (are [expr err-msg]
         (thrown-with-msg? ExceptionInfo err-msg (halite/type-check senv tenv expr))

      '[x] #"vector literal element must always evaluate to a value"
      '#{x} #"set literal element must always evaluate to a value"
      '(conj [] x) #"Cannot conj possibly unset value to vector")))

(deftest when-tests
  (let [senv (assoc-in senv [:specs :ws/Maybe$v1] {:spec-vars {:x [:Maybe "Integer"]}})
        tenv (-> tenv
                 (halite-envs/extend-scope 'm [:Instance :ws/Maybe$v1])
                 (halite-envs/extend-scope 'x [:Maybe :Integer])
                 (halite-envs/extend-scope 'b :Boolean))]
    (are [expr etype]
         (= etype (halite/type-check senv tenv expr))

      '(when b 1) [:Maybe :Integer]
      '(when b x) [:Maybe :Integer]
      '(when b (when (if-value x true false) "Foo")) [:Maybe :String]
      '{:$type :ws/Maybe$v1 :x (when b 12)} [:Instance :ws/Maybe$v1])

    (are [expr err-msg]
         (thrown-with-msg? ExceptionInfo err-msg (halite/type-check senv tenv expr))

      '[(when b 1)] #"must always evaluate to a value"
      '#{(when b 1)} #"must always evaluate to a value"
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
    '(union #{1} #{"foo"}) [:Set :Value]
    '(union #{[]} #{#{}}) [:Set [:Coll :Nothing]]
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
    '(union #{[]} #{#{}}) #{[] #{}}
    '(union #{1} #{2} #{3} #{4}) #{1 2 3 4}))

(deftest intersection-tests
  (are [expr etype]
       (= etype (halite/type-check senv tenv expr))

    '(intersection #{1 2} #{"three"}) [:Set :Nothing]
    '(intersection #{1 2} #{}) [:Set :Nothing]
    '(intersection #{1 2} (union #{1} #{"two"})) [:Set :Integer]
    '(intersection #{"two" 3} #{12}) [:Set :Integer])

  (are [expr err-msg]
       (thrown-with-msg? ExceptionInfo err-msg (halite/type-check senv tenv expr))

    '(intersection) #"Wrong number of arguments"
    '(intersection #{1} [2]) #"must be sets")

  (are [expr v]
       (= v (halite/eval-expr senv tenv empty-env expr))

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
    '(rest []) [:Vec :Nothing]
    '(rest [1 2]) [:Vec :Integer]
    '(rest [1]) [:Vec :Integer]
    '(conj [] 1) [:Vec :Integer]
    '(conj [1] 2) [:Vec :Integer]
    '(conj [] "one" "two") [:Vec :String]
    '(conj [1] "two") [:Vec :Value]
    '(conj #{} "one") [:Set :String]
    '(concat [] []) [:Vec :Nothing]
    '(concat [1 2] [3]) [:Vec :Integer]
    '(concat #{} []) [:Set :Nothing]
    '(concat #{} #{}) [:Set :Nothing]
    '(concat #{"foo"} ["bar"]) [:Set :String]
    '(sort []) [:Vec :Nothing]
    '(sort #{}) [:Vec :Nothing]
    '(sort [1 2]) [:Vec :Integer]
    '(sort #{1 2}) [:Vec :Integer])

  (are [expr err-msg]
       (thrown-with-msg? ExceptionInfo err-msg (halite/type-check senv tenv expr))
    '(first) #"Wrong number of arguments"
    '(first [] []) #"Wrong number of arguments"
    '(first []) #"Argument to first is always empty"
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
    '(sort) #"No matching signature"
    '(sort 1) #"No matching signature")

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
    '(concat #{1 2} [1 2 3]) #{1 2 3}))

(deftest test-constraint-validation
  (let [senv (update senv :specs merge
                     {:ws/E$v1 {:spec-vars {:x [:Maybe "Integer"]
                                            :y "Boolean"}
                                :constraints [["x-if-y" '(=> y (if-value x true false))]]}
                      :ws/Invalid$v1 {:spec-vars {}
                                      :constraints [["broken" '(or (+ 1 2) true)]]}})]
    ;; type-check cannot detect constraint violations, because that would often involve
    ;; actually evaluating forms
    (are [expr etype]
         (= etype (halite/type-check senv tenv expr))

      {:$type :ws/E$v1, :y false} [:Instance :ws/E$v1]
      {:$type :ws/E$v1, :y true} [:Instance :ws/E$v1]
      {:$type :ws/Invalid$v1} [:Instance :ws/Invalid$v1])

    ;; type-of, on the other hand, only works with values, and *does* check constraints
    (are [expr err-msg]
         (thrown-with-msg? ExceptionInfo err-msg
                           (halite/optionally-with-eval-bindings
                            true
                            (halite-type-of/type-of senv tenv expr)))

      {:$type :ws/E$v1, :y true} #"Invalid instance"
      {:$type :ws/Invalid$v1} #"No matching signature")

    ;; eval-expr also must check constraints
    (are [expr v]
         (= v (halite/eval-expr senv tenv empty-env expr))
      {:$type :ws/E$v1, :y false} {:$type :ws/E$v1, :y false})

    (are [expr err-msg]
         (thrown-with-msg? ExceptionInfo err-msg (halite/eval-expr senv tenv empty-env expr))

      {:$type :ws/E$v1, :y true} #"Invalid instance"
      {:$type :ws/Invalid$v1} #"No matching signature")))

(deftest test-refinement-validation
  (let [senv (->TestSpecEnv
              {:ws/A {:spec-vars {:x "Integer"}
                      :refines-to {:ws/B {:expr '{:$type :ws/B :x x}}
                                   :ws/C {:inverted? true :expr '{:$type :ws/C :x x}}}}
               :ws/B {:spec-vars {:x "Integer"}
                      :constraints '[["posX" (< 0 x)]]
                      :refines-to {:ws/D {:expr '{:$type :ws/D :x (+ 1 x)}}}}
               :ws/C {:spec-vars {:x "Integer"}
                      :constraints '[["boundedX" (< x 10)]]
                      :refines-to {:ws/D {:expr '{:$type :ws/D :x (+ 1 (* 2 x))}}}}
               :ws/D {:spec-vars {:x "Integer"}
                      :constraints '[["xIsOdd" (= 1 (mod x 2))]]}
               :ws/E {:spec-vars {}}})]

    (let [invalid-a {:$type :ws/A :x -10}
          sketchy-a {:$type :ws/A :x 20}]
      (is (= [:Instance :ws/A] (halite/type-check senv tenv invalid-a)))
      (is (thrown-with-msg? ExceptionInfo #"Invalid instance"
                            (halite/eval-expr senv tenv empty-env invalid-a)))
      (let [a (halite/eval-expr senv tenv empty-env sketchy-a)]
        (is (= a sketchy-a))
        (is (= {:ws/B {:$type :ws/B :x 20}
                :ws/D {:$type :ws/D :x 21}}
               (-> a meta :refinements (dissoc :ws/C)))))

      (is (thrown-with-msg?
           ExceptionInfo #"Invalid instance"
           (halite/eval-expr senv
                             (halite-envs/extend-scope tenv 'a [:Instance :ws/A])
                             (halite-envs/bind empty-env 'a invalid-a)
                             'a)))

      (is (= [:Instance :ws/B] (halite/type-check senv tenv (list 'refine-to sketchy-a :ws/B))))
      (is (= [:Instance :ws/E] (halite/type-check senv tenv '(refine-to {:$type :ws/D :x 1} :ws/E))))
      (is (thrown-with-msg?
           ExceptionInfo #"No active refinement path from 'ws/D' to 'ws/E'"
           (halite/eval-expr senv tenv empty-env '(refine-to {:$type :ws/D :x 1} :ws/E))))
      (is (thrown-with-msg?
           ExceptionInfo #"Resource spec not found: foo/Bar"
           (halite/type-check senv tenv '(refine-to {:$type :ws/E} :foo/Bar))))
      (is (thrown-with-msg?
           ExceptionInfo #"First argument to 'refine-to' must be an instance"
           (halite/eval-expr senv tenv empty-env '(refine-to (+ 1 2) :ws/B))))
      (is (thrown-with-msg?
           ExceptionInfo #"Second argument to 'refine-to' must be a spec id"
           (halite/eval-expr senv tenv empty-env '(refine-to {:$type :ws/A :x 2} (+ 1 2)))))
      (is (thrown-with-msg? ExceptionInfo #"Refinement from 'ws/A' failed unexpectedly: \"h-err/invalid-instance 0-0 : Invalid instance of 'ws/C'"
                            (binding [format-errors/*squash-throw-site* true]
                              (halite/eval-expr senv tenv empty-env (list 'refine-to sketchy-a :ws/C)))))
      (is (= {:$type :ws/B :x 20}
             (halite/eval-expr senv tenv empty-env (list 'refine-to sketchy-a :ws/B))))
      (is (= {:$type :ws/E} (halite/eval-expr senv tenv empty-env '(refine-to {:$type :ws/E} :ws/E)))))))

(deftest test-refinement-with-guards
  (let [senv (->TestSpecEnv
              {:ws/A {:spec-vars {:x "Integer"}
                      :constraints '[["posX" (< 0 x)]]}
               :ws/B {:spec-vars {:y "Integer"}
                      :refines-to {:ws/A {:expr '(if (< 0 y) ; A passthru
                                                   {:$type :ws/A, :x y}
                                                   (when (< y 0) ; A via negation
                                                     {:$type :ws/A, :x (- 0 y)}))}}}})]

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

(deftest test-instance-type
  (let [senv (->TestSpecEnv
              {:ws/A {:spec-vars {:x "Integer"}
                      :abstract? true
                      :constraints '[["posX" (< 0 x)]
                                     ["boundedX" (< x 10)]]}
               :ws/A1 {:spec-vars {}
                       :refines-to {:ws/A {:expr '{:$type :ws/A :x 5}}}}
               :ws/A2 {:spec-vars {:a "Integer"
                                   :b "Integer"}
                       :refines-to {:ws/A {:expr '{:$type :ws/A :x (+ a b)}}}}
               :ws/B {:spec-vars {:a :ws/A}}
               :ws/C {:spec-vars {:as [:ws/A]}}
               :ws/D {:spec-vars {}}})
        tenv2 (-> tenv
                  (halite-envs/extend-scope 'ax [:Instance :* #{:ws/A2}])
                  (halite-envs/extend-scope 'ay [:Instance :* #{:ws/A1}])
                  (halite-envs/extend-scope 'b1 [:Instance :ws/B]))
        env2 (-> empty-env
                 (halite-envs/bind 'ax {:$type :ws/A2 :a 3 :b 4})
                 (halite-envs/bind 'ay {:$type :ws/A1})
                 (halite-envs/bind 'b1 {:$type :ws/B :a {:$type :ws/A1}}))]

    (are [expr etype]
         (= etype (halite/type-check senv tenv2 expr))

      {:$type :ws/A1} [:Instance :ws/A1]
      {:$type :ws/A2 :a 1 :b 2} [:Instance :ws/A2]
      {:$type :ws/B :a {:$type :ws/A1}} [:Instance :ws/B]
      {:$type :ws/B :a {:$type :ws/A2 :a 1 :b 2}} [:Instance :ws/B]
      '(get {:$type :ws/B :a {:$type :ws/A2 :a 1 :b 2}} :a) [:Instance :* #{:ws/A}]
      'ax [:Instance :* #{:ws/A2}]
      '(= ax ay) :Boolean
      '(= b1 ax) :Boolean
      {:$type :ws/B :a {:$type :ws/D}} [:Instance :ws/B]
      '(refine-to ax :ws/A) [:Instance :ws/A]
      '(union #{{:$type :ws/A1}} #{{:$type :ws/A2 :a 3 :b 3}}) [:Set [:Instance :*]])

    (are [expr v]
         (= v (halite/eval-expr senv tenv2 env2 expr))

      '(refine-to ax :ws/A) {:$type :ws/A :x 7}
      '(= ax (refine-to ax :ws/A2)) true)

    (are [expr err-msg]
         (thrown-with-msg? ExceptionInfo err-msg (halite/eval-expr senv tenv empty-env expr))

      {:$type :ws/A2 :a 7 :b 8} #"Invalid instance"
      '(refine-to (get {:$type :ws/B :a {:$type :ws/D}} :a) :ws/A) #"No active refinement path from 'ws/D' to 'ws/A'")))

(deftest test-refines-to?
  (let [senv (->TestSpecEnv
              {:ws/A {:spec-vars {:x "Integer"}
                      :abstract? true
                      :constraints '[["posX" (< 0 x)]
                                     ["boundedX" (< x 10)]]}
               :ws/A1 {:spec-vars {}
                       :refines-to {:ws/A {:expr '{:$type :ws/A, :x 5}}}}
               :ws/A2 {:spec-vars {:a "Integer"
                                   :b "Integer"}
                       :refines-to {:ws/A {:expr '{:$type :ws/A, :x (+ a b)}}}}
               :ws/B {:spec-vars {:a :ws/A}}
               :ws/C {:spec-vars {:as [:ws/A]}}
               :ws/D {:spec-vars {}}})
        tenv2 (-> tenv
                  (halite-envs/extend-scope 'ax [:Instance :* #{:ws/A}])
                  (halite-envs/extend-scope 'ay [:Instance :* #{:ws/A}])
                  (halite-envs/extend-scope 'b1 [:Instance :ws/B]))
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
      '(refines-to? ax :foo/Bar) #"Resource spec not found: foo/Bar"
      '(refines-to? (+ 1 2) :ws/A) #"First argument to 'refines-to\?' must be an instance"
      '(refines-to? ax "foo") #"Second argument to 'refines-to\?' must be a spec id")

    (are [expr v]
         (= v (halite/eval-expr senv tenv2 env2 expr))

      '(refines-to? ax :ws/A) true
      '(refines-to? ax :ws/A2) true
      '(refines-to? ax :ws/A1) false)))

(deftest test-valid
  (let [senv (->TestSpecEnv
              '{:ws/A {:spec-vars {:x "Integer", :y "Integer"}
                       :constraints [["xLTy" (< x y)]]
                       :refines-to {:ws/B {:expr {:$type :ws/B :z (+ x y)}}}}
                :ws/B {:spec-vars {:z "Integer"}
                       :constraints [["posZ" (< 0 z)]]}
                :ws/C {:spec-vars {:a :ws/A}
                       :constraints [["maxSum" (< (+ (get a :x) (get a :y)) 10)]]}})]
    (are [expr etype]
         (= etype (halite/type-check senv tenv expr))

      '(valid {:$type :ws/A, :x 1, :y 0}) [:Maybe [:Instance :ws/A]]
      '(let [a (valid {:$type :ws/A :x 1 :y 0})] (if-value a 1 2)) :Integer
      '(valid {:$type :ws/C :a {:$type :ws/A :x 1 :y 0}}) [:Maybe [:Instance :ws/C]]
      '(let [a {:$type :ws/A :x 1 :y 0}] (valid a)) [:Maybe [:Instance :ws/A]]
      '(valid (let [a {:$type :ws/A :x 1 :y 0}] a)) [:Maybe [:Instance :ws/A]]
      '(valid? {:$type :ws/A, :x 1, :y 0}) :Boolean)
    ;; These are questionable...
    ;;'(valid (valid {:$type :ws/A :x 1 :y 0})) [:Maybe :ws/A]
    ;;'(valid (when (< 2 1) {:$type :ws/A :x 1 :y 0})) [:Maybe :ws/A]

    (are [expr err-msg]
         (thrown-with-msg? ExceptionInfo err-msg (halite/type-check senv tenv expr))

      '(valid 1) #"must be an instance of known type"
      '(valid? 1) #"must be an instance of known type")

    (are [expr v]
         (= v (halite/eval-expr senv tenv empty-env expr))

      '(valid {:$type :ws/A :x 2 :y 1}) :Unset
      '(valid {:$type :ws/A, :x -2, :y 0}) :Unset
      '(valid {:$type :ws/A, :x 1 :y 2}) {:$type :ws/A, :x 1, :y 2}
      '(valid {:$type :ws/C :a {:$type :ws/A :x 6 :y 7}}) :Unset
      '(valid {:$type :ws/C :a {:$type :ws/A :x 2 :y 1}}) :Unset
      '(valid (let [a {:$type :ws/A :x 1 :y 0}] a)) :Unset
      '(valid? {:$type :ws/A :x 2 :y 1}) false
      '(valid? {:$type :ws/A :x 1 :y 2}) true)))
;;'(valid (when (< 2 1) {:$type :ws/A :x 1 :y 2})) :Unset

(deftest abstract-specs
  (let [senv (->TestSpecEnv
              {:ws/A {:spec-vars {:x "Integer"}
                      :constraints '[["posX" (< 0 x)]
                                     ["boundedX" (< x 10)]]
                      :abstract? true}
               :ws/A1 {:spec-vars {}
                       :refines-to {:ws/A {:expr '{:$type :ws/A, :x 6}}}}
               :ws/A2 {:spec-vars {:a "Integer"
                                   :b "Integer"}
                       :refines-to {:ws/A {:expr '{:$type :ws/A, :x (+ a b)}}}}
               :ws/B {:spec-vars {:a :ws/A}
                      :constraints '[["notFive" (not= 5 (get (refine-to a :ws/A) :x))]]}
               :ws/C {:spec-vars {:as [:ws/A]}}
               :ws/D {:spec-vars {}}
               :ws/Z {:abstract? true}
               :ws/Z1 {:refines-to {:ws/Z {:expr '{:$type :ws/Z}}}}
               :ws/Invalid {:spec-vars {:a :ws/A
                                        :z :ws/Z}
                            ;; a is typed as :Instance, so (get a :x) doesn't type check
                            :constraints '[["notFive" (not= 5 (get
                                                               (first (sort-by [x (intersection #{a}
                                                                                                #{z})]
                                                                               1))
                                                               :x))]]}})
        tenv2 (-> tenv
                  (halite-envs/extend-scope 'ax [:Instance :* #{:ws/A}])
                  (halite-envs/extend-scope 'ay [:Instance :* #{:ws/A}])
                  (halite-envs/extend-scope 'b1 [:Instance :ws/B])
                  (halite-envs/extend-scope 'c [:Instance :ws/C]))
        env2 (-> empty-env
                 (halite-envs/bind 'ax {:$type :ws/A2 :a 3 :b 4})
                 (halite-envs/bind 'ay {:$type :ws/A1})
                 (halite-envs/bind 'b1 {:$type :ws/B :a {:$type :ws/A1}})
                 (halite-envs/bind 'c {:$type :ws/C :as [{:$type :ws/A1} {:$type :ws/A2 :a 2 :b 7}]}))]

    (are [expr etype]
         (= etype (halite/type-check senv tenv2 expr))

      {:$type :ws/A :x 5} [:Instance :ws/A]
      '(get b1 :a) [:Instance :* #{:ws/A}]
      '(get c :as) [:Vec [:Instance :* #{:ws/A}]]
      {:$type :ws/B :a {:$type :ws/A1}} [:Instance :ws/B]
      {:$type :ws/B :a {:$type :ws/D}} [:Instance :ws/B]
      {:$type :ws/C :as [{:$type :ws/D} {:$type :ws/A1}]} [:Instance :ws/C]
      '{:$type :ws/C :as (conj (get c :as) {:$type :ws/D})} [:Instance :ws/C]
      '{:$type :ws/C :as (conj (get c :as) {:$type :ws/A :x 9})} [:Instance :ws/C])

    (are [expr err-msg]
         (thrown-with-msg? ExceptionInfo err-msg (halite/eval-expr senv tenv2 env2 expr))

      {:$type :ws/Invalid :a {:$type :ws/A1} :z {:$type :ws/Z1}} #"Lookup target must be")

    (try
      (halite/eval-expr senv tenv2 env2
                        {:$type :ws/Invalid :a {:$type :ws/A1} :z {:$type :ws/Z1}})
      (is false)
      (catch ExceptionInfo ex
        (is (= {:constraint-name "notFive"
                :spec-id :ws/Invalid}
               (select-keys (ex-data ex) [:constraint-name :spec-id])))))

    (are [expr err-msg]
         (thrown-with-msg? ExceptionInfo err-msg (halite/eval-expr senv tenv2 env2 expr))

      {:$type :ws/B :a {:$type :ws/A :x 4}} #"cannot contain abstract value"
      {:$type :ws/B :a {:$type :ws/D}} #"No active refinement path from 'ws/D' to 'ws/A'"
      '{:$type :ws/C :as (conj (get c :as) {:$type :ws/D})} #"No active refinement path from 'ws/D' to 'ws/A'"
      '{:$type :ws/C :as (conj (get c :as) {:$type :ws/A :x 9})} #"Instance cannot contain abstract value")

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
                 {:spec-vars {:s "String"}
                  :refines-to {:ws/JsonVal {:expr {:$type :ws/JsonVal}}}}

                 :ws/JsonBool
                 {:spec-vars {:b "Boolean"}
                  :refines-to {:ws/JsonVal {:expr {:$type :ws/JsonVal}}}}

                 :ws/JsonInt
                 {:spec-vars {:n "Integer"}
                  :refines-to {:ws/JsonVal {:expr {:$type :ws/JsonVal}}}}

                 :ws/JsonVec
                 {:spec-vars {:entries [:ws/JsonVal]}
                  :refines-to {:ws/JsonVal {:expr {:$type :ws/JsonVal}}}}

                 :ws/JsonObjEntry
                 {:spec-vars {:key "String", :val :ws/JsonVal}}

                 :ws/JsonObj
                 {:spec-vars {:entries #{:ws/JsonObjEntry}}
                  :constraints [["uniqueKeys" true
                                 ;; TODO: each key shows up once
                                 #_(= (count entries) (count (for [entry entries] (get* entry :key))))]]
                  :refines-to {:ws/JsonVal {:expr {:$type :ws/JsonVal}}}}})]

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

(deftest test-embedding-env
  (test/testing "Initial scope may bind plain symbols"
    (let [tenv (-> tenv (halite-envs/extend-scope 'that :Integer))
          env (-> empty-env (halite-envs/bind 'that 71))]
      (is (= :Integer (halite-lint/type-check senv tenv '(+ that 25))))
      (is (= 96 (halite/eval-expr senv tenv env '(+ that 25))))))

  (test/testing "Initial scope may bind external-reserved-words"
    (let [tenv (-> tenv (halite-envs/extend-scope '$this :Integer))
          env (-> empty-env (halite-envs/bind '$this 71))]
      (is (= :Integer (halite-lint/type-check senv tenv '(+ $this 25))))
      (is (= 96 (halite/eval-expr senv tenv env '(+ $this 25))))))

  (test/testing "Initial scope may not bind arbitrary $-words"
    (let [tenv (-> tenv (halite-envs/extend-scope '$foo :Integer))]
      (is (thrown-with-msg? ExceptionInfo #"disallowed symbol.*[$]foo"
                            (halite-lint/type-check senv tenv '(+ $foo 25)))))))

;; (time (clojure.test/run-tests))
