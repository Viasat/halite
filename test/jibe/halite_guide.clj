;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite-guide
  (:require [jibe.halite :as halite]
            [jibe.halite.envs :as halite-envs]
            [jibe.logic.jadeite :as jadeite]
            [internal :refer :all]))

(defmacro h
  [expr & args]
  (let [[expected-t result-expected j-expr-expected j-result-expected] args]
    `(let [senv# (halite-envs/spec-env {})
           tenv# (halite-envs/type-env {})
           env# (halite-envs/env {})
           j-expr# (try (jadeite/to-jadeite (quote ~expr))
                        (catch RuntimeException e#
                          [:throws (.getMessage e#)]))
           t# (try (halite/type-check senv# tenv# (quote ~expr))
                   (catch RuntimeException e#
                     [:throws (.getMessage e#)]))
           h-result# (try (halite/eval-expr senv# tenv# env# (quote ~expr))
                          (catch RuntimeException e#
                            [:throws (.getMessage e#)]))
           jh-expr# (when (string? j-expr#)
                      (try
                        (jadeite/to-halite j-expr#)
                        (catch RuntimeException e#
                          [:throws (.getMessage e#)])))

           jh-result# (try
                        (halite/eval-expr senv# tenv# env# jh-expr#)
                        (catch RuntimeException e#
                          [:throws (.getMessage e#)]))
           j-result# (try
                       (jadeite/to-jadeite (halite/eval-expr senv# tenv# env# jh-expr#))
                       (catch RuntimeException e#
                         [:throws (.getMessage e#)]))]
       (is (= ~expected-t t#))

       (when (not (and (vector? t#)
                       (= :throws (first t#))))
         (is (= ~result-expected h-result#))
         (when (string? j-expr#)
           (is (= ~result-expected jh-result#)))
         (when (string? j-expr#)
           (is (= ~j-result-expected j-result#)))
         (is (= ~j-expr-expected j-expr#)))
       (if (and (vector? t#)
                (= :throws (first t#)))
         (list (quote ~'h)
               (quote ~expr)
               t#)
         (list (quote ~'h)
               (quote ~expr)
               t#
               h-result#
               j-expr#
               j-result#)))))

(defn hf
  [expr & args]
  (let [[expected-t result-expected j-expr-expected j-result-expected] args]
    (let [senv (halite-envs/spec-env {})
          tenv (halite-envs/type-env {})
          env (halite-envs/env {})
          j-expr (try (jadeite/to-jadeite expr)
                      (catch RuntimeException e
                        [:throws (.getMessage e)]))
          t (try (halite/type-check senv tenv expr)
                 (catch RuntimeException e
                   [:throws (.getMessage e)]))
          h-result (try (halite/eval-expr senv tenv env expr)
                        (catch RuntimeException e
                          [:throws (.getMessage e)]))
          jh-expr (when (string? j-expr)
                    (try
                      (jadeite/to-halite j-expr)
                      (catch RuntimeException e
                        [:throws (.getMessage e)])))

          jh-result (try
                      (halite/eval-expr senv tenv env jh-expr)
                      (catch RuntimeException e
                        [:throws (.getMessage e)]))
          j-result (try
                     (jadeite/to-jadeite (halite/eval-expr senv tenv env jh-expr))
                     (catch RuntimeException e
                       [:throws (.getMessage e)]))]
      (is (= expected-t t))
      (when (not (and (vector? t)
                      (= :throws (first t))))
        (is (= result-expected h-result))
        (when (string? j-expr)
          (is (= result-expected jh-result)))
        (is (= j-expr-expected j-expr))
        (when (string? j-expr)
          (is (= j-result-expected j-result))))

      (if (and (vector? t)
               (= :throws (first t)))
        (list 'hf
              expr
              t)
        (list 'hf
              expr
              t
              h-result
              j-expr
              j-result)))))

(deftest test-bool
  (h true :Boolean true "true" "true")

  (h false :Boolean false "false" "false")

  (h (and true false) :Boolean false "(true && false)" "false")

  (h (and true true) :Boolean true "(true && true)" "true")

  (h (and true) :Boolean true "(true)" "true")

  (h (and false) :Boolean false "(false)" "false")

  (h (and) [:throws "no matching signature for 'and'"])

  (h (and true false true) :Boolean false "(true && false && true)" "false")

  (h (and true (or false true)) :Boolean true "(true && (false || true))" "true")

  (h (or true true) :Boolean true "(true || true)" "true")

  (h (or true false) :Boolean true "(true || false)" "true")

  (h (or false false) :Boolean false "(false || false)" "false")

  (h (or true) :Boolean true "(true)" "true")

  (h (or) [:throws "no matching signature for 'or'"])

  (h (or true false true) :Boolean true "(true || false || true)" "true")

  (h (=> true false) :Boolean false "(true => false)" "false")

  (h (=> true true) :Boolean true "(true => true)" "true")

  (h (=> false true) :Boolean true "(false => true)" "true")

  (h (=> false false) :Boolean true "(false => false)" "true")

  (h (=>) [:throws "no matching signature for '=>'"])

  (h (=> true) [:throws "no matching signature for '=>'"])

  (h (=> true false true) [:throws "no matching signature for '=>'"])

  (h (= true false) :Boolean false "(true == false)" "false")

  (h (= true) [:throws "Wrong number of arguments to '=': expected at least 2, but got 1"])

  ;; TODO: (h (= true false false) :Boolean false "(true == false == false)" [:throws "Syntax error"])

  (h (not= true) [:throws "Wrong number of arguments to 'not=': expected at least 2, but got 1"])

  (h (not= true false) :Boolean true "(true != false)" "true")

  (h (if true false true) :Boolean false "(if(true) {false} else {true})" "false")

  (h (if true false) [:throws "Wrong number of arguments to 'if': expected 3, but got 2"])

  (h (if-value true false true) [:throws "First argument to 'if-value' must be a bare symbol"]))

(deftest test-int
  (h 1 :Integer 1 "1" "1")

  (h (+ 1) [:throws "no matching signature for '+'"])

  (h (+ 1 2) :Integer 3 "(1 + 2)" "3")

  (h (+ 1 2 3) :Integer 6 "(1 + 2 + 3)" "6")

  (h (+) [:throws "no matching signature for '+'"])

  (h (- 3 2) :Integer 1 "(3 - 2)" "1")

  (h (- 1) [:throws "no matching signature for '-'"])

  (h (-) [:throws "no matching signature for '-'"])

  (h (- 3 false) [:throws "no matching signature for '-'"])

  (h (+ 1 -) [:throws "Undefined: '-'"])

  (h (- 3 2 1) :Integer 0 "(3 - 2 - 1)" "0")

  (h -1 :Integer -1 "-1" "-1")

  (h (+ 1 -1) :Integer 0 "(1 + -1)" "0")

  (h (- 3 -2) :Integer 5 "(3 - -2)" "5")

  (h (* 2 3) :Integer 6 "(2 * 3)" "6")

  (h (* 2) [:throws "no matching signature for '*'"])

  (h (*) [:throws "no matching signature for '*'"])

  (h (* 2 3 4) :Integer 24 "(2 * 3 * 4)" "24")

  (h (div 6 2) :Integer 3 "(6 / 2)" "3")

  (h (div 6) [:throws "no matching signature for 'div'"])

  (h (div) [:throws "no matching signature for 'div'"])

  (h (div 20 2 3) [:throws "no matching signature for 'div'"])

  (h (div 7 2) :Integer 3 "(7 / 2)" "3")

  (h (div 1 2) :Integer 0 "(1 / 2)" "0")

  (h (div 1 9223372036854775808) [:throws "Syntax error"])

  (h (div 1 9223372036854775807) :Integer 0 "(1 / 9223372036854775807)" "0")

  (h (mod 3 2) :Integer 1 "(3 % 2)" "1")

  (h (mod 3) [:throws "no matching signature for 'mod'"])

  (h (mod 3 1) :Integer 0 "(3 % 1)" "0")

  (h (mod 6 3 2) [:throws "no matching signature for 'mod'"])

  (h (mod -3 2) :Integer 1 "(-3 % 2)" "1")

  (h (mod 3 -2) :Integer -1 "(3 % -2)" "-1")

  (h (mod -3 -2) :Integer -1 "(-3 % -2)" "-1")

  (h (mod 3 0) :Integer [:throws "Divide by zero"] "(3 % 0)" [:throws "Divide by zero"])

  (h (mod 9223372036854775807 9223372036854775806) :Integer 1 "(9223372036854775807 % 9223372036854775806)" "1")

  (h (mod 1 9223372036854775808) [:throws "Syntax error"])

  (h (expt 2 3) :Integer 8 "2.expt(3)" "8")

  (h (expt 1 9223372036854775807) :Integer 1 "1.expt(9223372036854775807)" "1")

  (h (expt 1 9223372036854775808N) [:throws "Syntax error"])

  (h (expt 2 3 4) [:throws "no matching signature for 'expt'"])

  (h (expt 2 0) :Integer 1 "2.expt(0)" "1")

  (h (expt 2 -1) :Integer [:throws "Invalid exponent"] "2.expt(-1)" [:throws "Invalid exponent"])

  (h (expt -2 3) :Integer -8 "-2.expt(3)" "-8")

  (h (expt -2 1) :Integer -2 "-2.expt(1)" "-2")

  (h (expt -2 4) :Integer 16 "-2.expt(4)" "16")

  (h (expt -2 0) :Integer 1 "-2.expt(0)" "1")

  (h (expt -2 -3) :Integer [:throws "Invalid exponent"] "-2.expt(-3)" [:throws "Invalid exponent"])

  (h (inc 1) :Integer 2 "(1 + 1)" "2")

  (h (inc 1 2) [:throws "no matching signature for 'inc'"])

  (h (dec 1) :Integer 0 "(1 - 1)" "0")

  (h (dec 1 2) [:throws "no matching signature for 'dec'"])

  (h (abs) [:throws "no matching signature for 'abs'"])

  (h (abs 0) :Integer 0 "0.abs()" "0")

  (h (abs 1) :Integer 1 "1.abs()" "1")

  (h (abs -1) :Integer 1 "-1.abs()" "1")

  (h (abs 1 2 3) [:throws "no matching signature for 'abs'"])

  (h (abs (- 1 4)) :Integer 3 "(1 - 4).abs()" "3")

  (h (abs -9223372036854775808) :Integer -9223372036854775808 "-9223372036854775808.abs()" "-9223372036854775808")

  (h (abs true) [:throws "no matching signature for 'abs'"]))

(deftest test-int-equality-etc
  (h (= 1) [:throws "Wrong number of arguments to '=': expected at least 2, but got 1"])

  (h (= 1 1) :Boolean true "(1 == 1)" "true")

  (h (= 0 0) :Boolean true "(0 == 0)" "true")

  (h (= -9223372036854775808 -9223372036854775808) :Boolean true "(-9223372036854775808 == -9223372036854775808)" "true")

  (h (= 1 0) :Boolean false "(1 == 0)" "false")

  (h (= 1 true) [:throws "Arguments to '=' have incompatible types"])

  ;; TODO: (h (= 1 1 1) :Boolean true "(1 == 1 == 1)" [:throws "Syntax error"])

  (h (not= 1 2) :Boolean true "(1 != 2)" "true")

  (h (not= 1) [:throws "Wrong number of arguments to 'not=': expected at least 2, but got 1"])

  (h (not=) [:throws "Wrong number of arguments to 'not=': expected at least 2, but got 0"])

  (h (not= false 3) [:throws "Arguments to 'not=' have incompatible types"])

  ;; TODO: (h (not= 1 2 3) :Boolean true "(1 != 2 != 3)" [:throws "Syntax error"])

  ;; TODO: (h (not= 1 1 1) :Boolean false "(1 != 1 != 1)" [:throws "Syntax error"])

  (h (> 1 0) :Boolean true "(1 > 0)" "true")

  (h (> 1) [:throws "no matching signature for '>'"])

  (h (> 3 2 1) [:throws "no matching signature for '>'"])

  (h (> 1 1) :Boolean false "(1 > 1)" "false")

  (h (>) [:throws "no matching signature for '>'"])

  (h (> 3 true) [:throws "no matching signature for '>'"])

  (h (< 1 0) :Boolean false "(1 < 0)" "false")

  (h (< 0 1) :Boolean true "(0 < 1)" "true")

  (h (< 3 2 1) [:throws "no matching signature for '<'"])

  (h (< 3) [:throws "no matching signature for '<'"])

  (h (<) [:throws "no matching signature for '<'"])

  (h (< 4 false) [:throws "no matching signature for '<'"])

  (h (<= 1) [:throws "no matching signature for '<='"])

  (h (<=) [:throws "no matching signature for '<='"])

  (h (<= true 3) [:throws "no matching signature for '<='"])

  (h (>= 1 1) :Boolean true "(1 >= 1)" "true")

  (h (>=) [:throws "no matching signature for '>='"])

  (h (>= 1) [:throws "no matching signature for '>='"])

  (h (>= 1 2 3) [:throws "no matching signature for '>='"]))

(deftest test-int-other-types
  (hf (short 1) [:throws "Syntax error"])

  (h 1N [:throws "Syntax error"])

  (h 1.1 [:throws "Syntax error"])

  (h 1.1M [:throws "Syntax error"])

  (h 1/2 [:throws "Syntax error"])

  (hf (byte 1) [:throws "Syntax error"])

  (h ##NaN [:throws "Syntax error"])

  (hf Double/NaN [:throws "Syntax error"])

  (h ##Inf [:throws "Syntax error"])

  (h ##-Inf [:throws "Syntax error"]))

(deftest test-int-overflow
  (h 2147483647 :Integer 2147483647 "2147483647" "2147483647")

  (h -2147483648 :Integer -2147483648 "-2147483648" "-2147483648")

  (h (inc 9223372036854775807) :Integer [:throws "long overflow"] "(9223372036854775807 + 1)" [:throws "long overflow"])

  (h (dec 9223372036854775807) :Integer 9223372036854775806 "(9223372036854775807 - 1)" "9223372036854775806")

  (h (dec -9223372036854775808) :Integer [:throws "long overflow"] "(-9223372036854775808 - 1)" [:throws "long overflow"])

  (h (inc -9223372036854775808) :Integer -9223372036854775807 "(-9223372036854775808 + 1)" "-9223372036854775807")

  (h (abs -9223372036854775808) :Integer -9223372036854775808 "-9223372036854775808.abs()" "-9223372036854775808")

  (h (= -9223372036854775808 -9223372036854775808) :Boolean true "(-9223372036854775808 == -9223372036854775808)" "true")

  (h (<= -9223372036854775808 9223372036854775807) :Boolean true "(-9223372036854775808 <= 9223372036854775807)" "true")

  (h 2147483647 :Integer 2147483647 "2147483647" "2147483647")

  (h -2147483648 :Integer -2147483648 "-2147483648" "-2147483648")

  (h 9223372036854775807 :Integer 9223372036854775807 "9223372036854775807" "9223372036854775807")

  (h 9223372036854775808 [:throws "Syntax error"])

  (h 9223372036854775808N [:throws "Syntax error"])

  (h (+ 9223372036854775808 0) [:throws "Syntax error"])

  (h -9223372036854775808 :Integer -9223372036854775808 "-9223372036854775808" "-9223372036854775808")

  (h (* 2147483647 2147483647) :Integer 4611686014132420609 "(2147483647 * 2147483647)" "4611686014132420609")

  (h (* 9223372036854775807 2) :Integer [:throws "long overflow"] "(9223372036854775807 * 2)" [:throws "long overflow"])

  (h (+ 9223372036854775807 1) :Integer [:throws "long overflow"] "(9223372036854775807 + 1)" [:throws "long overflow"])

  (h (- 9223372036854775807 1) :Integer 9223372036854775806 "(9223372036854775807 - 1)" "9223372036854775806")

  (h (- 9223372036854775807 -1) :Integer [:throws "long overflow"] "(9223372036854775807 - -1)" [:throws "long overflow"])

  (h (- -9223372036854775808 1) :Integer [:throws "long overflow"] "(-9223372036854775808 - 1)" [:throws "long overflow"])

  (h -9223372036854775809 [:throws "Syntax error"] [:throws "Syntax error"] [:throws "Invalid numeric type"] [:throws "Syntax error"])

  (h (+ -9223372036854775808 -1) :Integer [:throws "long overflow"] "(-9223372036854775808 + -1)" [:throws "long overflow"])

  (h (+ -9223372036854775808 1) :Integer -9223372036854775807 "(-9223372036854775808 + 1)" "-9223372036854775807"))

(deftest test-bool-int
  (h (and 1) [:throws "no matching signature for 'and'"])

  (h (or 1) [:throws "no matching signature for 'or'"])

  (h (=> 1 true) [:throws "no matching signature for '=>'"]))

(deftest test-int-bool
  (h (+ true 1) [:throws "no matching signature for '+'"])

  (h (* true) [:throws "no matching signature for '*'"])

  (h (div 1 false) [:throws "no matching signature for 'div'"])

  (h (mod true 3) [:throws "no matching signature for 'mod'"])

  (h (expt true false) [:throws "no matching signature for 'expt'"])

  (h (inc false) [:throws "no matching signature for 'inc'"])

  (h (dec true) [:throws "no matching signature for 'dec'"])

  (h (if true 1 3) :Integer 1 "(if(true) {1} else {3})" "1")

  (h (if-value 3 1 2) [:throws "First argument to 'if-value' must be a bare symbol"])

  (h (if (> 2 1) false true) :Boolean false "(if((2 > 1)) {false} else {true})" "false")

  (h (if (> 2 1) 9 true) [:throws "then and else branches to 'if' have incompatible types"])

  (h (if (or (> 2 1) (= 4 5)) (+ 1 2) 99) :Integer 3 "(if(((2 > 1) || (4 == 5))) {(1 + 2)} else {99})" "3"))

(deftest test-string
  (h "hello" :String "hello" "\"hello\"" "\"hello\"")

  (h "" :String "" "\"\"" "\"\"")

  (h "a" :String "a" "\"a\"" "\"a\"")

  (h "☺" :String "☺" "\"☺\"" "\"☺\"")

  (h "\t\n" :String "\t\n" "\"\\t\\n\"" "\"\\t\\n\"")

  ;; TODO
  (h (concat "" "") [:throws "First argument to 'concat' must be a set or vector"])

  ;; TODO
  (h (count "") [:throws "no matching signature for 'count'"])

  (h (count "a") [:throws "no matching signature for 'count'"])

  (h (= "" "") :Boolean true "(\"\" == \"\")" "true")

  (h (= "a" "") :Boolean false "(\"a\" == \"\")" "false")

  (h (= "a" "b") :Boolean false "(\"a\" == \"b\")" "false")

  (h (= "a" "a") :Boolean true "(\"a\" == \"a\")" "true")

  ;; TODO: confirm expected
  (h (first "") [:throws "Argument to 'first' must be a vector"])

  (h (first "a") [:throws "Argument to 'first' must be a vector"])

  (h (rest "ab") [:throws "Argument to 'rest' must be a vector"])

  (h (contains? "ab" "a") [:throws "no matching signature for 'contains?'"])

  (h (union "" "a") [:throws "Arguments to 'union' must be sets"])

  ;; TODO:

  (h (get "" 0) [:throws "First argument to get must be an instance of known type or non-empty vector"])

  (h (get "ab" 1) [:throws "First argument to get must be an instance of known type or non-empty vector"]))

(deftest test-string-bool
  (h (and "true" false) [:throws "no matching signature for 'and'"])

  (h (=> true "hi") [:throws "no matching signature for '=>'"])

  (h (if (= "a" "b") (= (+ 1 3) 5) false) :Boolean false "(if((\"a\" == \"b\")) {((1 + 3) == 5)} else {false})" "false"))

(deftest test-bool-string
  (h (count true) [:throws "no matching signature for 'count'"])

  (h (concat "" false) [:throws "First argument to 'concat' must be a set or vector"]))

(deftest test-sets
  (h #{1 2} [:Set :Integer] #{1 2} "#{1, 2}" "#{1, 2}")

  (h #{1} [:Set :Integer] #{1} "#{1}" "#{1}")

  (h #{} :EmptySet #{} "#{}" "#{}")

  (h #{"a"} [:Set :String] #{"a"} "#{\"a\"}" "#{\"a\"}")

  (h #{"☺" "a"} [:Set :String] #{"☺" "a"} "#{\"a\", \"☺\"}" "#{\"a\", \"☺\"}")

  (h #{1 "a"} [:Set :Any] #{1 "a"} "#{\"a\", 1}" "#{\"a\", 1}")

  (h (count #{1 true "a"}) :Integer 3 "#{\"a\", 1, true}.count()" "3")

  (h (count #{}) :Integer 0 "#{}.count()" "0")

  (h (count) [:throws "no matching signature for 'count'"])

  (h (count #{10 20 30}) :Integer 3 "#{10, 20, 30}.count()" "3")

  (h (count #{} #{2}) [:throws "no matching signature for 'count'"])

  (h (contains? #{"a" "b"} "a") :Boolean true "#{\"a\", \"b\"}.contains?(\"a\")" "true")

  (h (contains? #{"a" "b"}) [:throws "no matching signature for 'contains?'"])

  (h (contains?) [:throws "no matching signature for 'contains?'"])

  (h (contains? #{1 2} "b") :Boolean false "#{1, 2}.contains?(\"b\")" "false")

  (h (contains? #{1 2} "b" "c") [:throws "no matching signature for 'contains?'"])

  (h (concat #{1}) [:throws "Wrong number of arguments to 'concat': expected 2, but got 1"])

  (h (concat #{1} #{2}) [:Set :Integer] #{1 2} "#{1}.concat(#{2})" "#{1, 2}")

  (h (concat #{1} #{2} #{3}) [:throws "Wrong number of arguments to 'concat': expected 2, but got 3"])

  (h (concat) [:throws "Wrong number of arguments to 'concat': expected 2, but got 0"])

  (h (concat #{} #{}) :EmptySet #{} "#{}.concat(#{})" "#{}")

  (h (concat #{"a" "b"} #{1 2}) [:Set :Any] #{1 "a" 2 "b"} "#{\"a\", \"b\"}.concat(#{1, 2})" "#{\"a\", \"b\", 1, 2}")

  (h #{#{4 3} #{1}} [:Set [:Set :Integer]] #{#{4 3} #{1}} "#{#{1}, #{3, 4}}" "#{#{1}, #{3, 4}}")

  (h (contains? #{#{4 3} #{1}} #{2}) :Boolean false "#{#{1}, #{3, 4}}.contains?(#{2})" "false")

  (h (contains? #{#{4 3} #{1}} #{4 3}) :Boolean true "#{#{1}, #{3, 4}}.contains?(#{3, 4})" "true")

  (h (subset? #{1 2} #{1 4 3 2}) :Boolean true "#{1, 2}.subset?(#{1, 2, 3, 4})" "true")

  (h (subset? #{1 2}) [:throws "no matching signature for 'subset?'"])

  (h (subset?) [:throws "no matching signature for 'subset?'"])

  (h (subset? #{1 2} #{1 4 3 2}) :Boolean true "#{1, 2}.subset?(#{1, 2, 3, 4})" "true")

  (h (subset? #{} #{1 4 3 2}) :Boolean true "#{}.subset?(#{1, 2, 3, 4})" "true")

  (h (subset? #{1 "a"} #{1 "a"}) :Boolean true "#{\"a\", 1}.subset?(#{\"a\", 1})" "true")

  (h (= #{} #{}) :Boolean true "(#{} == #{})" "true")

  (h (= #{1 2} #{2 1}) :Boolean true "(#{1, 2} == #{1, 2})" "true")

  (h (= #{1} #{2}) :Boolean false "(#{1} == #{2})" "false")

  (h (= #{1} #{}) :Boolean false "(#{1} == #{})" "false")

  ;; TODO: (h (= #{1} #{} #{2}) :Boolean false "(#{1} == #{} == #{2})" [:throws "Syntax error"])

  (h (not= #{} #{}) :Boolean false "(#{} != #{})" "false")

  (h (not= #{1} #{}) :Boolean true "(#{1} != #{})" "true")

  (h (intersection #{} #{}) :EmptySet #{} "#{}.intersection(#{})" "#{}")

  (h (intersection #{}) [:throws "Wrong number of arguments to 'intersection': expected at least 2, but got 1"])

  (h (intersection #{1 2}) [:throws "Wrong number of arguments to 'intersection': expected at least 2, but got 1"])

  (h (intersection) [:throws "Wrong number of arguments to 'intersection': expected at least 2, but got 0"])

  (h (intersection #{1 2} #{3 2}) [:Set :Integer] #{2} "#{1, 2}.intersection(#{2, 3})" "#{2}")

  (h (intersection #{} #{3 2}) :EmptySet #{} "#{}.intersection(#{2, 3})" "#{}")

  (h (union #{} #{}) :EmptySet #{} "#{}.union(#{})" "#{}")

  (h (union #{2}) [:throws "Wrong number of arguments to 'union': expected at least 2, but got 1"])

  ;; TODO: (h (union) :EmptySet #{} ".union()" [:throws "Syntax error"])

  (h (union #{1} #{}) [:Set :Integer] #{1} "#{1}.union(#{})" "#{1}")

  (h (union #{1} #{3 2} #{4}) [:Set :Integer] #{1 4 3 2} "#{1}.union(#{2, 3}, #{4})" "#{1, 2, 3, 4}")

  (h (difference #{} #{}) :EmptySet #{} "#{}.difference(#{})" "#{}")

  (h (difference #{1 3 2} #{1 2}) [:Set :Integer] #{3} "#{1, 2, 3}.difference(#{1, 2})" "#{3}")

  (h (difference #{1 2}) [:throws "Wrong number of arguments to 'difference': expected 2, but got 1"])

  (h (difference) [:throws "Wrong number of arguments to 'difference': expected 2, but got 0"])

  (h (first #{}) [:throws "Argument to 'first' must be a vector"])

  (h (first #{1}) [:throws "Argument to 'first' must be a vector"])

  (h (rest #{}) [:throws "Argument to 'rest' must be a vector"])

  (h (rest #{1 2}) [:throws "Argument to 'rest' must be a vector"])

  (h (conj #{}) [:throws "Wrong number of arguments to 'conj': expected at least 2, but got 1"])

  (h (conj #{} 1 2) [:Set :Integer] #{1 2} "#{}.conj(1, 2)" "#{1, 2}")

  (h (conj #{} 1) [:Set :Integer] #{1} "#{}.conj(1)" "#{1}")

  (h (conj #{} 1 #{3 2}) [:Set :Any] #{1 #{3 2}} "#{}.conj(1, #{2, 3})" "#{#{2, 3}, 1}"))

(deftest test-set-every?
  (h (every? [x #{1 2}] (> x 0)) :Boolean true "every?(x in #{1, 2})(x > 0)" "true")

  (h (every? [x #{1 2}] (> x 10)) :Boolean false "every?(x in #{1, 2})(x > 10)" "false")

  (h (every? [x #{1 2}] (> x 1)) :Boolean false "every?(x in #{1, 2})(x > 1)" "false")

  (h (every? [x #{3 2}] (> (count x) 1)) [:throws "no matching signature for 'count'"])

  (h (every? [] true) [:throws "Binding form for 'every?' must have one variable and one collection"])

  (h (every? [#{1 2}] true) [:throws "Binding form for 'every?' must have one variable and one collection"])

  (h (every? [x #{} y #{1}] (> x y)) [:throws "Binding form for 'every?' must have one variable and one collection"])

  (h (every? [x #{4 3}] (every? [y #{1 2}] (< y x))) :Boolean true "every?(x in #{3, 4})every?(y in #{1, 2})(y < x)" "true")

  (h (every? [x #{4 3}] false) :Boolean false "every?(x in #{3, 4})false" "false")

  (h (every?) [:throws "Wrong number of arguments to 'every?': expected 2, but got 0"])

  (h (every? [x #{}] true false) [:throws "Wrong number of arguments to 'every?': expected 2, but got 3"])

  (h (every? [x #{} 1] false) [:throws "Binding form for 'every?' must have one variable and one collection"])

  (h (every? [_ #{}] false) :Boolean true "every?(<_> in #{})false" "true")

  (h (every? [_ #{}] _) [:throws "Body expression in 'every?' must be boolean"])

  (h (every? [_ #{true}] _) :Boolean true "every?(<_> in #{true})<_>" "true")

  (h (every? [h19 #{true}] h19) :Boolean true "every?(h19 in #{true})h19" "true")

  (h (every? [☺ #{true}] ☺) :Boolean true "every?(<☺> in #{true})<☺>" "true")

  (h (every? [? #{true}] ?) :Boolean true "every?(<?> in #{true})<?>" "true"))

(deftest test-every?-other
  (h (every? [x true] false) [:throws "collection required for 'every?', not :Boolean"])

  (h (every? [19 true] false) [:throws "Binding target for 'every?' must be a bare symbol, not: 19"])

  (h (every? [19 #{"a"}] true) [:throws "Binding target for 'every?' must be a bare symbol, not: 19"])

  (h (every? [if +] true) [:throws "Undefined: '+'"])

  (h (every? [if #{1 2}] (> 0 if)) :Boolean false "every?(if in #{1, 2})(0 > if)" "false")

  (h (every? [if if] (> 0 if)) [:throws "Undefined: 'if'"])

  (h (every? [x "ab"] (= x x)) [:throws "collection required for 'every?', not :String"]))

(deftest test-set-any?
  (h (any? [x #{}] (> x 1)) [:throws "no matching signature for '>'"])

  (h (any? [x #{1 2}] (> x 1)) :Boolean true "any?(x in #{1, 2})(x > 1)" "true")

  (h (any? [x #{1 3 2}] (= x 1)) :Boolean true "any?(x in #{1, 2, 3})(x == 1)" "true")

  (h (any? ["a" #{1 3 2}] true) [:throws "Binding target for 'any?' must be a bare symbol, not: \"a\""])

  (h (any?) [:throws "Wrong number of arguments to 'any?': expected 2, but got 0"])

  (h (any? [x #{1 2} y #{4 3}] (= x y)) [:throws "Binding form for 'any?' must have one variable and one collection"])

  (h (any? [x #{1 3 2}] (any? [y #{4 6 2}] (= x y))) :Boolean true "any?(x in #{1, 2, 3})any?(y in #{2, 4, 6})(x == y)" "true")

  (h (any? [x #{1 3 2}] (every? [y #{1 2}] (< y x))) :Boolean true "any?(x in #{1, 2, 3})every?(y in #{1, 2})(y < x)" "true"))

(deftest test-any?-other
  (h (any? [x "test"] true) [:throws "collection required for 'any?', not :String"]))

(deftest test-vector
  (h [] :EmptyVec [] "[]" "[]")

  (h [1 2] [:Vec :Integer] [1 2] "[1, 2]" "[1, 2]")

  (h [[] [1] ["a" true]] [:Vec [:Vec :Any]] [[] [1] ["a" true]] "[[], [1], [\"a\", true]]" "[[], [1], [\"a\", true]]")

  (h [[1] []] [:Vec [:Vec :Integer]] [[1] []] "[[1], []]" "[[1], []]")

  (h (first []) [:throws "argument to first is always empty"])

  (h (first) [:throws "Wrong number of arguments to 'first': expected 1, but got 0"])

  (h (first [0] [1]) [:throws "Wrong number of arguments to 'first': expected 1, but got 2"])

  (h (first [10]) :Integer 10 "[10].first()" "10")

  (h (first [10 true "b"]) :Any 10 "[10, true, \"b\"].first()" "10")

  (h (rest [10 true "b"]) [:Vec :Any] [true "b"] "[10, true, \"b\"].rest()" "[true, \"b\"]")

  (h (concat [] []) :EmptyVec [] "[].concat([])" "[]")

  (h (concat [] [1]) [:Vec :Integer] [1] "[].concat([1])" "[1]")

  (h (concat [1 2] [3] [4 5 6]) [:throws "Wrong number of arguments to 'concat': expected 2, but got 3"])

  (h (concat [1]) [:throws "Wrong number of arguments to 'concat': expected 2, but got 1"])

  (h (concat) [:throws "Wrong number of arguments to 'concat': expected 2, but got 0"])

  (h (sort [2 1 3]) [:Vec :Integer] [1 2 3] "[2, 1, 3].sort()" "[1, 2, 3]")

  (h (first (sort [2 1 3])) :Integer 1 "[2, 1, 3].sort().first()" "1")

  (h (sort (quote (3 1 2))) [:throws "function 'quote' not found"])

  (h (sort []) :EmptyVec [] "[].sort()" "[]")

  (h (sort [1] [2 3]) [:throws "no matching signature for 'sort'"])

  (h (sort) [:throws "no matching signature for 'sort'"])

  (h (range) [:throws "no matching signature for 'range'"])

  (h (range 1) [:Vec :Integer] [0] "1.range()" "[0]")

  (h (range 1 2) [:Vec :Integer] [1] "1.range(2)" "[1]")

  ;;TODO
  (h (range 1 2 3) [:Vec :Integer] [1] "1.range(2, 3)" "[1]")

  (h (conj [10 20] 30) [:Vec :Integer] [10 20 30] "[10, 20].conj(30)" "[10, 20, 30]")

  (h (conj [] 30) [:Vec :Integer] [30] "[].conj(30)" "[30]")

  (h (conj [10 20] []) [:Vec :Any] [10 20 []] "[10, 20].conj([])" "[10, 20, []]")

  (h (conj [10]) [:throws "Wrong number of arguments to 'conj': expected at least 2, but got 1"])

  (h (conj) [:throws "Wrong number of arguments to 'conj': expected at least 2, but got 0"])

  (h (conj [] 1 2 3) [:Vec :Integer] [1 2 3] "[].conj(1, 2, 3)" "[1, 2, 3]")

  (h (conj [10] 1 2 3) [:Vec :Integer] [10 1 2 3] "[10].conj(1, 2, 3)" "[10, 1, 2, 3]")

  (h (get [10 20] 0) :Integer 10 "[10, 20][0]" "10")

  (h (get [] 0) [:throws "Cannot index into empty vector"])

  (h (get [10 20 30] 1) :Integer 20 "[10, 20, 30][1]" "20")

  (h (get (get (get [[10 20 [300 302]] [30 40]] 0) 2) 1) [:throws "First argument to get must be an instance of known type or non-empty vector"])

  (h (get (get (get [[[10] [20] [300 302]] [[30] [40]]] 0) 2) 1) :Integer 302 "[[[10], [20], [300, 302]], [[30], [40]]][0][2][1]" "302")

  (h (count []) :Integer 0 "[].count()" "0")

  (h (count [10 20 30]) :Integer 3 "[10, 20, 30].count()" "3")

  (h (count (get [[10 20] [30] [40 50 60]] 2)) :Integer 3 "[[10, 20], [30], [40, 50, 60]][2].count()" "3")

  (h (count (get [[10 20] "hi" [40 50 60]] 2)) [:throws "no matching signature for 'count'"]))

(deftest test-vector-every?
  (h (every? [_x [10 20 30]] (= (mod _x 3) 1)) :Boolean false "every?(<_x> in [10, 20, 30])((<_x> % 3) == 1)" "false")

  (h (every? [x [#{10} #{20 30}]] (> (count x) 0)) :Boolean true "every?(x in [#{10}, #{20, 30}])(x.count() > 0)" "true")

  (h (every? [x []] false) :Boolean true "every?(x in [])false" "true")

  (h (every? [x [1]] false) :Boolean false "every?(x in [1])false" "false")

  (h (every? [x [1 2 3]] (every? [y [10 20]] (> y x))) :Boolean true "every?(x in [1, 2, 3])every?(y in [10, 20])(y > x)" "true")

  (h (every? [x [false (> (mod 2 0) 3)]] x) :Boolean [:throws "Divide by zero"] "every?(x in [false, ((2 % 0) > 3)])x" [:throws "Divide by zero"]))

(deftest test-vector-any?
  (h (any? [x []] false) :Boolean false "any?(x in [])false" "false")

  (h (any? [x []] true) :Boolean false "any?(x in [])true" "false")

  (h (any? [x [true false false]] x) :Boolean true "any?(x in [true, false, false])x" "true")

  (h (any? [x [true (= 4 (div 1 0))]] x) :Boolean [:throws "Divide by zero"] "any?(x in [true, (4 == (1 / 0))])x" [:throws "Divide by zero"]))

(deftest test-sets-and-vectors

  (h [#{[3 4] [1 2]} #{[5 6]}] [:Vec [:Set [:Vec :Integer]]] [#{[3 4] [1 2]} #{[5 6]}] "[#{[1, 2], [3, 4]}, #{[5, 6]}]" "[#{[1, 2], [3, 4]}, #{[5, 6]}]")

  (h (count (get [#{[3 4] [1 2]} #{#{9 8} #{7} [5 6]}] 1)) :Integer 3 "[#{[1, 2], [3, 4]}, #{#{7}, #{8, 9}, [5, 6]}][1].count()" "3")

  (h #{#{[#{[true false] [true true]}] [#{[false]}]} #{[#{[true false] [true true]}] [#{[false]} #{[true]}]}} [:Set [:Set [:Vec [:Set [:Vec :Boolean]]]]] #{#{[#{[true false] [true true]}] [#{[false]}]} #{[#{[true false] [true true]}] [#{[false]} #{[true]}]}} "#{#{[#{[false]}, #{[true]}], [#{[true, false], [true, true]}]}, #{[#{[false]}], [#{[true, false], [true, true]}]}}" "#{#{[#{[false]}, #{[true]}], [#{[true, false], [true, true]}]}, #{[#{[false]}], [#{[true, false], [true, true]}]}}"))

;; (run-tests)
