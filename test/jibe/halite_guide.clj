;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite-guide
  (:require [jibe.halite :as halite]
            [jibe.halite.envs :as halite-envs]
            [jibe.logic.expression :as expression]
            [jibe.logic.halite.spec-env :as spec-env]
            [jibe.logic.jadeite :as jadeite]
            [jibe.logic.resource-spec-construct :as resource-spec-construct :refer [workspace spec variables constraints refinements]]
            [jibe.logic.test-setup-specs :as test-setup-specs :refer [*spec-store*]]
            [internal :refer :all])
  (:import [clojure.lang ExceptionInfo]))

(defmacro h
  [expr & args]
  (let [[expected-t result-expected j-expr-expected j-result-expected] args]
    `(let [senv# (halite-envs/spec-env {})
           tenv# (halite-envs/type-env {})
           env# (halite-envs/env {})
           j-expr# (try (jadeite/to-jadeite (quote ~expr))
                        (catch RuntimeException e#
                          [:throws (.getMessage e#)]))
           s# (try (halite/syntax-check (quote ~expr))
                   nil
                   (catch RuntimeException e#
                     [:syntax-check-throws (.getMessage e#)]))
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
       (if (nil? s#)
         (do
           (is (= ~expected-t t#))

           (when (not (and (vector? t#)
                           (= :throws (first t#))))
             (when (and (vector? h-result#)
                        (= :throws (first h-result#))))
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
                   j-result#)))
         (do
           (is (= ~expected-t s#))
           (list (quote ~'h)
                 (quote ~expr)
                 s#))))))

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

  (h (= true false false) :Boolean false "equalTo(true, false, false)" "false")

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

  (h (div 1 9223372036854775808) [:syntax-check-throws "Syntax error"])

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

  (h (mod 1 9223372036854775808) [:syntax-check-throws "Syntax error"])

  (h (expt 2 3) :Integer 8 "2.expt(3)" "8")

  (h (expt 1 9223372036854775807) :Integer 1 "1.expt(9223372036854775807)" "1")

  (h (expt 1 9223372036854775808N) [:syntax-check-throws "Syntax error"])

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

  (h (= 1 true) [:throws "Result of '=' would always be false"])

  (h (= 1 1 1) :Boolean true "equalTo(1, 1, 1)" "true")

  (h (not= 1 2) :Boolean true "(1 != 2)" "true")

  (h (not= 1) [:throws "Wrong number of arguments to 'not=': expected at least 2, but got 1"])

  (h (not=) [:throws "Wrong number of arguments to 'not=': expected at least 2, but got 0"])

  (h (not= false 3) [:throws "Result of 'not=' would always be true"])

  (h (not= 1 2 3) :Boolean true "notEqualTo(1, 2, 3)" "true")

  (h (not= 1 1 1) :Boolean false "notEqualTo(1, 1, 1)" "false")

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

  (h (<= 1 2 3) [:throws "no matching signature for '<='"])

  (h (>= 1 1) :Boolean true "(1 >= 1)" "true")

  (h (>=) [:throws "no matching signature for '>='"])

  (h (>= 1) [:throws "no matching signature for '>='"])

  (h (>= 1 2 3) [:throws "no matching signature for '>='"]))

(deftest test-int-other-types
  (hf (short 1) [:throws "Syntax error"])

  (h 1N [:syntax-check-throws "Syntax error"])

  (h 1.1 [:syntax-check-throws "Syntax error"])

  (h 1.1M [:syntax-check-throws "Syntax error"])

  (h 1/2 [:syntax-check-throws "Syntax error"])

  (hf (byte 1) [:throws "Syntax error"])

  (h ##NaN [:syntax-check-throws "Syntax error"])

  (hf Double/NaN [:throws "Syntax error"])

  (h ##Inf [:syntax-check-throws "Syntax error"])

  (h ##-Inf [:syntax-check-throws "Syntax error"]))

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

  (h 9223372036854775808 [:syntax-check-throws "Syntax error"])

  (h 9223372036854775808N [:syntax-check-throws "Syntax error"])

  (h (+ 9223372036854775808 0) [:syntax-check-throws "Syntax error"])

  (h -9223372036854775808 :Integer -9223372036854775808 "-9223372036854775808" "-9223372036854775808")

  (h (* 2147483647 2147483647) :Integer 4611686014132420609 "(2147483647 * 2147483647)" "4611686014132420609")

  (h (* 9223372036854775807 2) :Integer [:throws "long overflow"] "(9223372036854775807 * 2)" [:throws "long overflow"])

  (h (+ 9223372036854775807 1) :Integer [:throws "long overflow"] "(9223372036854775807 + 1)" [:throws "long overflow"])

  (h (- 9223372036854775807 1) :Integer 9223372036854775806 "(9223372036854775807 - 1)" "9223372036854775806")

  (h (- 9223372036854775807 -1) :Integer [:throws "long overflow"] "(9223372036854775807 - -1)" [:throws "long overflow"])

  (h (- -9223372036854775808 1) :Integer [:throws "long overflow"] "(-9223372036854775808 - 1)" [:throws "long overflow"])

  (h -9223372036854775809 [:syntax-check-throws "Syntax error"] [:throws "Syntax error"] [:throws "Invalid numeric type"] [:throws "Syntax error"])

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

  (h (if (> 2 1) 9 true) :Object 9 "(if((2 > 1)) {9} else {true})" "9")

  (h (if (or (> 2 1) (= 4 5)) (+ 1 2) 99) :Integer 3 "(if(((2 > 1) || (4 == 5))) {(1 + 2)} else {99})" "3"))

(deftest test-string
  (h "hello" :String "hello" "\"hello\"" "\"hello\"")

  (h "" :String "" "\"\"" "\"\"")

  (h "a" :String "a" "\"a\"" "\"a\"")

  (h "☺" :String "☺" "\"☺\"" "\"☺\"")

  (h "\t\n" :String "\t\n" "\"\\t\\n\"" "\"\\t\\n\"")

  ;; TODO need to work out what functions work on strings
  (do
    (h (concat "" "") [:throws "First argument to 'concat' must be a set or vector"])

    (h (count "") [:throws "no matching signature for 'count'"])

    (h (count "a") [:throws "no matching signature for 'count'"])

    (h (= "" "") :Boolean true "(\"\" == \"\")" "true")

    (h (= "a" "") :Boolean false "(\"a\" == \"\")" "false")

    (h (= "a" "b") :Boolean false "(\"a\" == \"b\")" "false")

    (h (= "a" "a") :Boolean true "(\"a\" == \"a\")" "true")

    (h (first "") [:throws "Argument to 'first' must be a vector"])

    (h (first "a") [:throws "Argument to 'first' must be a vector"])

    (h (rest "ab") [:throws "Argument to 'rest' must be a vector"])

    (h (contains? "ab" "a") [:throws "no matching signature for 'contains?'"])

    (h (union "" "a") [:throws "Arguments to 'union' must be sets"])

    (h (get "" 0) [:throws "First argument to get must be an instance of known type or non-empty vector"])

    (h (get "ab" 1) [:throws "First argument to get must be an instance of known type or non-empty vector"])))

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

  (h #{} [:Set :Nothing] #{} "#{}" "#{}")

  (h #{"a"} [:Set :String] #{"a"} "#{\"a\"}" "#{\"a\"}")

  (h #{"☺" "a"} [:Set :String] #{"☺" "a"} "#{\"a\", \"☺\"}" "#{\"a\", \"☺\"}")

  (h #{1 "a"} [:Set :Object] #{1 "a"} "#{\"a\", 1}" "#{\"a\", 1}")

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

  (h (concat #{} #{}) [:Set :Nothing] #{} "#{}.concat(#{})" "#{}")

  (h (concat #{"a" "b"} #{1 2}) [:Set :Object] #{1 "a" 2 "b"} "#{\"a\", \"b\"}.concat(#{1, 2})" "#{\"a\", \"b\", 1, 2}")

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

  (h (= #{1} #{} #{2}) :Boolean false "equalTo(#{1}, #{}, #{2})" "false")

  (h (not= #{1} #{} #{2}) :Boolean true "notEqualTo(#{1}, #{}, #{2})" "true")

  (h (not= #{} #{}) :Boolean false "(#{} != #{})" "false")

  (h (not= #{1} #{}) :Boolean true "(#{1} != #{})" "true")

  (h (intersection #{} #{}) [:Set :Nothing] #{} "#{}.intersection(#{})" "#{}")

  (h (intersection #{}) [:throws "Wrong number of arguments to 'intersection': expected at least 2, but got 1"])

  (h (intersection #{1 2}) [:throws "Wrong number of arguments to 'intersection': expected at least 2, but got 1"])

  (h (intersection) [:throws "Wrong number of arguments to 'intersection': expected at least 2, but got 0"])

  (h (intersection #{1 2} #{3 2}) [:Set :Integer] #{2} "#{1, 2}.intersection(#{2, 3})" "#{2}")

  (h (intersection #{} #{3 2}) [:Set :Nothing] #{} "#{}.intersection(#{2, 3})" "#{}")

  (h (union #{} #{}) [:Set :Nothing] #{} "#{}.union(#{})" "#{}")

  (h (union #{2}) [:throws "Wrong number of arguments to 'union': expected at least 2, but got 1"])

  (h (union #{1} #{}) [:Set :Integer] #{1} "#{1}.union(#{})" "#{1}")

  (h (union #{1} #{3 2} #{4}) [:Set :Integer] #{1 4 3 2} "#{1}.union(#{2, 3}, #{4})" "#{1, 2, 3, 4}")

  (h (difference #{} #{}) [:Set :Nothing] #{} "#{}.difference(#{})" "#{}")

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

  (h (conj #{} 1 #{3 2}) [:Set :Object] #{1 #{3 2}} "#{}.conj(1, #{2, 3})" "#{#{2, 3}, 1}"))

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
  (h (any? [x #{}] (> x 1)) [:throws "Disallowed ':Nothing' expression: x"])

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
  (h [] [:Vec :Nothing] [] "[]" "[]")

  (h [1 2] [:Vec :Integer] [1 2] "[1, 2]" "[1, 2]")

  (h [[] [1] ["a" true]] [:Vec [:Vec :Object]] [[] [1] ["a" true]] "[[], [1], [\"a\", true]]" "[[], [1], [\"a\", true]]")

  (h [[1] []] [:Vec [:Vec :Integer]] [[1] []] "[[1], []]" "[[1], []]")

  (h (first []) [:throws "argument to first is always empty"])

  (h (first) [:throws "Wrong number of arguments to 'first': expected 1, but got 0"])

  (h (first [0] [1]) [:throws "Wrong number of arguments to 'first': expected 1, but got 2"])

  (h (first [10]) :Integer 10 "[10].first()" "10")

  (h (first [10 true "b"]) :Object 10 "[10, true, \"b\"].first()" "10")

  (h (rest [10 true "b"]) [:Vec :Object] [true "b"] "[10, true, \"b\"].rest()" "[true, \"b\"]")

  (h (concat [] []) [:Vec :Nothing] [] "[].concat([])" "[]")

  (h (concat [] [1]) [:Vec :Integer] [1] "[].concat([1])" "[1]")

  (h (concat [1 2] [3] [4 5 6]) [:throws "Wrong number of arguments to 'concat': expected 2, but got 3"])

  (h (concat [1]) [:throws "Wrong number of arguments to 'concat': expected 2, but got 1"])

  (h (concat) [:throws "Wrong number of arguments to 'concat': expected 2, but got 0"])

  (h (sort [2 1 3]) [:Vec :Integer] [1 2 3] "[2, 1, 3].sort()" "[1, 2, 3]")

  (h (first (sort [2 1 3])) :Integer 1 "[2, 1, 3].sort().first()" "1")

  (h (sort (quote (3 1 2))) [:syntax-check-throws "unknown function or operator"])

  (h (sort []) [:Vec :Nothing] [] "[].sort()" "[]")

  (h (sort [1] [2 3]) [:throws "no matching signature for 'sort'"])

  (h (sort) [:throws "no matching signature for 'sort'"])

  (h (range) [:throws "no matching signature for 'range'"])

  (h (range 1) [:Vec :Integer] [0] "1.range()" "[0]")

  (h (range 1 2) [:Vec :Integer] [1] "1.range(2)" "[1]")

  (h (range 1 2 3) [:Vec :Integer] [1] "1.range(2, 3)" "[1]")

  (h (conj [10 20] 30) [:Vec :Integer] [10 20 30] "[10, 20].conj(30)" "[10, 20, 30]")

  (h (conj [] 30) [:Vec :Integer] [30] "[].conj(30)" "[30]")

  (h (conj [10 20] []) [:Vec :Object] [10 20 []] "[10, 20].conj([])" "[10, 20, []]")

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

  (h (count (get [[10 20] "hi" [40 50 60]] 2)) [:throws "no matching signature for 'count'"])

  (h (= [1] [1] [1]) :Boolean true "equalTo([1], [1], [1])" "true")

  (h (not= [1] [1] [1]) :Boolean false "notEqualTo([1], [1], [1])" "false"))

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

(def workspaces-map {:basic [(workspace :my
                                        {:my/Spec [true]}
                                        (spec :Spec
                                              (variables [:p "Integer"]
                                                         [:n "Integer"]
                                                         [:o "Integer" :optional])
                                              (constraints [:pc [:halite "(> p 0)"]]
                                                           [:pn [:halite "(< n 0)"]])
                                              (refinements)))]
                     :basic-abstract [(workspace :my
                                                 {:my/Spec [false]}
                                                 (spec :Spec
                                                       (variables [:p "Integer"]
                                                                  [:n "Integer"]
                                                                  [:o "Integer" :optional])
                                                       (constraints [:pc [:halite "(> p 0)"]]
                                                                    [:pn [:halite "(< n 0)"]])
                                                       (refinements)))]
                     :basic-2 [(workspace :spec
                                          {:spec/A [true]
                                           :spec/B [false]
                                           :spec/C [true]
                                           :spec/D [true]}
                                          (spec :A
                                                (variables [:p "Integer"]
                                                           [:n "Integer"]
                                                           [:o "Integer" :optional])
                                                (constraints [:pc [:halite "(> p 0)"]]
                                                             [:pn [:halite "(< n 0)"]])
                                                (refinements [:as_b :to :spec/B$v1 [:halite "{:$type :spec/B$v1, :x (* 10 p), :y n, :z o}"]]))

                                          (spec :B
                                                (variables [:x "Integer"]
                                                           [:y "Integer"]
                                                           [:z "Integer" :optional])
                                                (constraints [:px [:halite "(< x 100)"]]
                                                             [:py [:halite "(> y -100)"]]
                                                             [:pz [:halite "(not= z 0)"]])
                                                (refinements))
                                          (spec :C
                                                (variables)
                                                (constraints)
                                                (refinements))
                                          (spec :D
                                                (variables [:ao :spec/A$v1 :optional]
                                                           [:co :spec/C$v1 :optional])
                                                (constraints)
                                                (refinements)))]})

(defmacro hc [workspaces workspace-id comment? & raw-args]
  (let [raw-args (if (string? comment?)
                   (first raw-args)
                   comment?)
        [expr & args] raw-args
        [expected-t result-expected j-expr-expected j-result-expected] args]
    `(test-setup-specs/setup-specs ~(if (keyword? workspaces)
                                      `'~(workspaces-map workspaces)
                                      workspaces)
                                   (let [senv# (spec-env/for-workspace *spec-store* ~workspace-id)
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
                                       (vector (quote ~expr)
                                               t#)
                                       (vector (quote ~expr)
                                               t#
                                               h-result#
                                               j-expr#
                                               j-result#))))))

(deftest test-instances
  (h {} [:syntax-check-throws "instance literal must have :$type field"])

  (h {:$type :my/Spec$v1} [:throws "resource spec not found: :my/Spec$v1"])

  (hc [(workspace :my
                  {:my/Spec [true]}
                  (spec :Spec (variables) (constraints) (refinements)))]
      :my
      [{:$type :my/Spec$v1} :my/Spec$v1 {:$type :my/Spec$v1} "{$type: my/Spec$v1}" "{$type: my/Spec$v1}"])

  (hc :basic
      :my
      [{:$type :my/Spec$v1} [:throws "missing required variables: :n,:p"]])

  (hc :basic
      :my
      [{:$type :my/Spec$v1 :p 1 :n -1} :my/Spec$v1 {:$type :my/Spec$v1, :p 1, :n -1} "{$type: my/Spec$v1, n: -1, p: 1}" "{$type: my/Spec$v1, n: -1, p: 1}"])

  (hc :basic
      :other
      [{:$type :my/Spec$v1, :p 1, :n -1} [:throws "resource spec not found: :my/Spec$v1"]])

  (hc :basic
      :my
      [{:$type :my/Spec$v1, :p 0, :n -1} :my/Spec$v1 [:throws "invalid instance of 'my/Spec$v1', violates constraints pc"] "{$type: my/Spec$v1, n: -1, p: 0}" [:throws "invalid instance of 'my/Spec$v1', violates constraints pc"]])

  (hc :basic
      :my
      [{:$type :my/Spec$v1, :p "0", :n -1} [:throws "value of :p has wrong type"]])

  (hc :basic-abstract
      :my
      [{:$type :my/Spec$v1 :p 1 :n -1} :my/Spec$v1 {:$type :my/Spec$v1, :p 1, :n -1} "{$type: my/Spec$v1, n: -1, p: 1}" "{$type: my/Spec$v1, n: -1, p: 1}"])

  (hc :basic
      :my
      [(get {:$type :my/Spec$v1, :p 1, :n -1} :p) :Integer 1 "{$type: my/Spec$v1, n: -1, p: 1}.p" "1"])

  (hc :basic
      :my
      [(get {:$type :my/Spec$v1, :p 1, :n -1} :q) [:throws "No such variable 'q' on spec ':my/Spec$v1'"]])

  (hc :basic
      :my
      [(get {:$type :my/Spec$v1, :p 1, :n -1} 0) [:throws "Second argument to get must be a variable name (as a keyword) when first argument is an instance"]])

  (hc :basic
      :my
      [(get {:$type :my/Spec$v1, :p 1, :n -1} "p") [:throws "Second argument to get must be a variable name (as a keyword) when first argument is an instance"]])

  ;; test equality
  (hc :basic-2
      :spec
      [(= {:$type :spec/A$v1, :p 1, :n -1} {:$type :spec/A$v1, :p 1, :n -1}) :Boolean true "({$type: spec/A$v1, n: -1, p: 1} == {$type: spec/A$v1, n: -1, p: 1})" "true"])
  (hc :basic-2
      :spec
      [(= {:$type :spec/A$v1, :p 1, :n -1} {:$type :spec/A$v1, :p 2, :n -1}) :Boolean false "({$type: spec/A$v1, n: -1, p: 1} == {$type: spec/A$v1, n: -1, p: 2})" "false"])
  (hc :basic-2
      :spec
      [(= {:$type :spec/A$v1, :p 1, :n -1} {:$type :spec/C$v1}) [:throws "Result of '=' would always be false"]])
  (hc :basic-2
      :spec
      [(= (refine-to {:$type :spec/A$v1, :p 1, :n -1} :spec/B$v1) {:$type :spec/B$v1, :x 10, :y -1}) :Boolean true "({$type: spec/A$v1, n: -1, p: 1}.refineTo( spec/B$v1 ) == {$type: spec/B$v1, x: 10, y: -1})" "true"])
  (hc :basic-2
      :spec
      [(= {:$type :spec/A$v1, :p 1, :n -1} (get {:$type :spec/B$v1, :x 10, :y -1} :z)) [:throws "Result of '=' would always be false"]])
  ;; TODO
  (hc :basic-2
      :spec
      [(= (get {:$type :spec/A$v1, :p 1, :n -1} :o) (get {:$type :spec/B$v1, :x 10, :y -1} :z)) :Boolean true "({$type: spec/A$v1, n: -1, p: 1}.o == {$type: spec/B$v1, x: 10, y: -1}.z)" "true"])

  (hc :basic-2
      :spec
      [(= (get {:$type :spec/D$v1} :ao) (get {:$type :spec/D$v1} :ao)) :Boolean true "({$type: spec/D$v1}.ao == {$type: spec/D$v1}.ao)" "true"])

  ;; test type compatibility in branches of expression
  (hc :basic-2
      :spec
      [(if true {:$type :spec/A$v1, :p 1, :n -1} {:$type :spec/A$v1, :p 1, :n -2}) :spec/A$v1 {:$type :spec/A$v1, :p 1, :n -1} "(if(true) {{$type: spec/A$v1, n: -1, p: 1}} else {{$type: spec/A$v1, n: -2, p: 1}})" "{$type: spec/A$v1, n: -1, p: 1}"])
  (hc :basic-2
      :spec
      [(if true {:$type :spec/A$v1, :p 1, :n -1} {:$type :spec/C$v1}) :Instance {:$type :spec/A$v1, :p 1, :n -1} "(if(true) {{$type: spec/A$v1, n: -1, p: 1}} else {{$type: spec/C$v1}})" "{$type: spec/A$v1, n: -1, p: 1}"])
  (hc :basic-2
      :spec
      [(get (if true {:$type :spec/A$v1, :p 1, :n -1} {:$type :spec/C$v1}) :p) [:throws "First argument to get must be an instance of known type or non-empty vector"]])

  (hc :basic-2
      :spec
      [(if true {:$type :spec/A$v1, :p 1, :n -1} {:$type :spec/B$v1, :x 1, :y -1}) :Instance {:$type :spec/A$v1, :p 1, :n -1} "(if(true) {{$type: spec/A$v1, n: -1, p: 1}} else {{$type: spec/B$v1, x: 1, y: -1}})" "{$type: spec/A$v1, n: -1, p: 1}"])
  (hc :basic-2
      :spec
      [(if true {:$type :spec/A$v1, :p 1, :n -1} (refine-to {:$type :spec/C$v1} :spec/B$v1)) :Instance {:$type :spec/A$v1, :p 1, :n -1} "(if(true) {{$type: spec/A$v1, n: -1, p: 1}} else {{$type: spec/C$v1}.refineTo( spec/B$v1 )})" "{$type: spec/A$v1, n: -1, p: 1}"])
  (hc :basic-2
      :spec
      [(if true {:$type :spec/A$v1, :p 1, :n -1} (refine-to {:$type :spec/C$v1} :spec/A$v1)) :spec/A$v1 {:$type :spec/A$v1, :p 1, :n -1} "(if(true) {{$type: spec/A$v1, n: -1, p: 1}} else {{$type: spec/C$v1}.refineTo( spec/A$v1 )})" "{$type: spec/A$v1, n: -1, p: 1}"])
  (hc :basic-2
      :spec
      [(if true (refine-to {:$type :spec/A$v1, :p 1, :n -1} :spec/B$v1) (refine-to {:$type :spec/C$v1} :spec/B$v1)) :spec/B$v1 {:$type :spec/B$v1, :x 10, :y -1} "(if(true) {{$type: spec/A$v1, n: -1, p: 1}.refineTo( spec/B$v1 )} else {{$type: spec/C$v1}.refineTo( spec/B$v1 )})" "{$type: spec/B$v1, x: 10, y: -1}"])

  (hc :basic-2
      :spec
      [(if true (valid {:$type :spec/A$v1, :p 1, :n -1}) {:$type :spec/A$v1, :p 1, :n -2}) [:Maybe :spec/A$v1] {:$type :spec/A$v1, :p 1, :n -1} "(if(true) {(valid {$type: spec/A$v1, n: -1, p: 1})} else {{$type: spec/A$v1, n: -2, p: 1}})" "{$type: spec/A$v1, n: -1, p: 1}"])
  (hc :basic-2
      :spec
      [(let [v (if true (valid {:$type :spec/A$v1, :p 1, :n -1}) {:$type :spec/A$v1, :p 1, :n -2})] (if-value v v {:$type :spec/A$v1, :p 1, :n -3})) :spec/A$v1 {:$type :spec/A$v1, :p 1, :n -1} "{ v = (if(true) {(valid {$type: spec/A$v1, n: -1, p: 1})} else {{$type: spec/A$v1, n: -2, p: 1}}); (ifValue(v) {v} else {{$type: spec/A$v1, n: -3, p: 1}}) }" "{$type: spec/A$v1, n: -1, p: 1}"])

  (hc :basic-2
      :spec
      [[{:$type :spec/A$v1, :p 1, :n -1} {:$type :spec/A$v1, :p 1, :n -2}] [:Vec :spec/A$v1] [{:$type :spec/A$v1, :p 1, :n -1} {:$type :spec/A$v1, :p 1, :n -2}] "[{$type: spec/A$v1, n: -1, p: 1}, {$type: spec/A$v1, n: -2, p: 1}]" "[{$type: spec/A$v1, n: -1, p: 1}, {$type: spec/A$v1, n: -2, p: 1}]"])
  (hc :basic-2
      :spec
      [[{:$type :spec/A$v1, :p 1, :n -1} {:$type :spec/C$v1}] [:Vec :Instance] [{:$type :spec/A$v1, :p 1, :n -1} {:$type :spec/C$v1}] "[{$type: spec/A$v1, n: -1, p: 1}, {$type: spec/C$v1}]" "[{$type: spec/A$v1, n: -1, p: 1}, {$type: spec/C$v1}]"])
  (hc :basic-2
      :spec
      [(get [{:$type :spec/A$v1, :p 1, :n -1} {:$type :spec/C$v1}] 0) :Instance {:$type :spec/A$v1, :p 1, :n -1} "[{$type: spec/A$v1, n: -1, p: 1}, {$type: spec/C$v1}][0]" "{$type: spec/A$v1, n: -1, p: 1}"])
  (hc :basic-2
      :spec
      [(get (get [{:$type :spec/A$v1, :p 1, :n -1} {:$type :spec/C$v1}] 0) :p) [:throws "First argument to get must be an instance of known type or non-empty vector"]])
  (hc :basic-2
      :spec
      [(get (refine-to (get [{:$type :spec/A$v1, :p 1, :n -1} {:$type :spec/C$v1}] 0) :spec/A$v1) :p) :Integer 1 "[{$type: spec/A$v1, n: -1, p: 1}, {$type: spec/C$v1}][0].refineTo( spec/A$v1 ).p" "1"])
  (hc :basic-2
      :spec
      [(get (refine-to (get [{:$type :spec/A$v1, :p 1, :n -1} {:$type :spec/C$v1}] 1) :spec/A$v1) :p) :Integer [:throws "No active refinement path from 'spec/C$v1' to 'spec/A$v1'"] "[{$type: spec/A$v1, n: -1, p: 1}, {$type: spec/C$v1}][1].refineTo( spec/A$v1 ).p" [:throws "No active refinement path from 'spec/C$v1' to 'spec/A$v1'"]])

  (hc :basic-2
      :spec
      [(let [vs [{:$type :spec/A$v1, :p 1, :n -1} {:$type :spec/C$v1}]] (if true (get vs 0) (get vs 1))) :Instance {:$type :spec/A$v1, :p 1, :n -1} "{ vs = [{$type: spec/A$v1, n: -1, p: 1}, {$type: spec/C$v1}]; (if(true) {vs[0]} else {vs[1]}) }" "{$type: spec/A$v1, n: -1, p: 1}"]))

(deftest test-instances-optionality
  (hc :basic
      :my
      [{:$type :my/Spec$v1, :p 1, :n -1, :o 100} :my/Spec$v1 {:$type :my/Spec$v1, :p 1, :n -1, :o 100} "{$type: my/Spec$v1, n: -1, o: 100, p: 1}" "{$type: my/Spec$v1, n: -1, o: 100, p: 1}"])

  (hc :basic
      :my
      [(get {:$type :my/Spec$v1, :p 1, :n -1} :o) [:Maybe :Integer] :Unset "{$type: my/Spec$v1, n: -1, p: 1}.o" "Unset"])

  (hc :basic
      :my
      [(inc (get {:$type :my/Spec$v1, :p 1, :n -1} :o)) [:throws "no matching signature for 'inc'"]])

  (hc :basic
      :my
      [(if-value (get {:$type :my/Spec$v1, :p 1, :n -1} :o) 19 32) [:throws "First argument to 'if-value' must be a bare symbol"]])

  (hc :basic
      :my
      [(let [v (get {:$type :my/Spec$v1, :p 1, :n -1} :o)]
         (if-value v 19 32))
       :Integer 32 "{ v = {$type: my/Spec$v1, n: -1, p: 1}.o; (ifValue(v) {19} else {32}) }" "32"])

  (hc :basic
      :my
      [(let [v (get {:$type :my/Spec$v1, :p 1, :n -1, :o 0} :o)]
         (if-value v 19 32))
       :Integer 19 "{ v = {$type: my/Spec$v1, n: -1, o: 0, p: 1}.o; (ifValue(v) {19} else {32}) }" "19"]))

(deftest test-instances-valid
  (hc :basic
      :my
      [{:$type :my/Spec$v1, :p 1} [:throws "missing required variables: :n"]])

  (hc :basic
      :my
      [(valid {:$type :my/Spec$v1, :p 0}) [:throws "missing required variables: :n"]])

  (hc :basic
      :my
      [(valid? {:$type :my/Spec$v1, :p 0}) [:throws "missing required variables: :n"]])

  (hc :basic
      :my
      [(valid {:$type :my/Spec$v1, :p 1, :n 0}) [:Maybe :my/Spec$v1] :Unset "(valid {$type: my/Spec$v1, n: 0, p: 1})" "Unset"])

  (hc :basic
      :my
      [(valid {:$type :my/Spec$v1, :p 1, :n -1}) [:Maybe :my/Spec$v1] {:$type :my/Spec$v1, :p 1, :n -1} "(valid {$type: my/Spec$v1, n: -1, p: 1})" "{$type: my/Spec$v1, n: -1, p: 1}"])

  (hc :basic
      :my
      [(valid? {:$type :my/Spec$v1, :p 1, :n 0}) :Boolean false "(valid? {$type: my/Spec$v1, n: 0, p: 1})" "false"])

  (hc :basic
      :my
      [(valid? {:$type :my/Spec$v1, :p 1, :n -1}) :Boolean true "(valid? {$type: my/Spec$v1, n: -1, p: 1})" "true"])

  (hc :basic
      :my
      [(let [v (valid {:$type :my/Spec$v1, :p 1, :n -1})] (if-value v "hi" "bye")) :String "hi" "{ v = (valid {$type: my/Spec$v1, n: -1, p: 1}); (ifValue(v) {\"hi\"} else {\"bye\"}) }" "\"hi\""])

  (hc :basic
      :my
      [(let [v (valid {:$type :my/Spec$v1, :p 1, :n 0})] (if-value v "hi" "bye")) :String "bye" "{ v = (valid {$type: my/Spec$v1, n: 0, p: 1}); (ifValue(v) {\"hi\"} else {\"bye\"}) }" "\"bye\""])

  (hc :basic
      :my
      [(let [v (valid {:$type :my/Spec$v1, :p 1, :n -1})] (if-value v "hi" "bye")) :String "hi" "{ v = (valid {$type: my/Spec$v1, n: -1, p: 1}); (ifValue(v) {\"hi\"} else {\"bye\"}) }" "\"hi\""])

  (hc :basic
      :my
      [(if (valid? {:$type :my/Spec$v1, :p 1, :n 0}) "hi" "bye") :String "bye" "(if((valid? {$type: my/Spec$v1, n: 0, p: 1})) {\"hi\"} else {\"bye\"})" "\"bye\""])

  (hc :basic
      :my
      [(if (valid? {:$type :my/Spec$v1, :p 1, :n -1}) "hi" "bye") :String "hi" "(if((valid? {$type: my/Spec$v1, n: -1, p: 1})) {\"hi\"} else {\"bye\"})" "\"hi\""]))

(deftest test-instance-refine
  (hc :basic
      :my
      [(refines-to? {:$type :my/Spec$v1, :p 1, :n -1} :my/Spec$v1) :Boolean true "{$type: my/Spec$v1, n: -1, p: 1}.refinesTo?( my/Spec$v1 )" "true"])

  (hc :basic
      :my
      [(refines-to? {:$type :my/Spec$v1, :p 1, :n -1} :other/Spec$v1) [:throws "Spec not found: 'other/Spec$v1'"]])

  (hc :basic-2
      :spec
      [(refines-to? {:$type :spec/A$v1, :p 1, :n -1} :spec/B$v1) :Boolean true "{$type: spec/A$v1, n: -1, p: 1}.refinesTo?( spec/B$v1 )" "true"])

  (hc :basic-2
      :spec
      [(refines-to? {:$type :spec/A$v1, :p 1, :n -1} :spec/C$v1) :Boolean false "{$type: spec/A$v1, n: -1, p: 1}.refinesTo?( spec/C$v1 )" "false"])

  (hc :basic-2
      :spec
      [(refine-to {:$type :spec/A$v1, :p 1, :n -1} :spec/B$v1) :spec/B$v1 {:$type :spec/B$v1, :x 10, :y -1} "{$type: spec/A$v1, n: -1, p: 1}.refineTo( spec/B$v1 )" "{$type: spec/B$v1, x: 10, y: -1}"])

  (hc :basic-2
      :spec
      [(refine-to {:$type :spec/A$v1, :p 1, :n -1} :spec/C$v1) :spec/C$v1 [:throws "No active refinement path from 'spec/A$v1' to 'spec/C$v1'"] "{$type: spec/A$v1, n: -1, p: 1}.refineTo( spec/C$v1 )" [:throws "No active refinement path from 'spec/A$v1' to 'spec/C$v1'"]])

  (hc :basic-2
      :spec
      [(valid? (refine-to {:$type :spec/A$v1, :p 1, :n -1} :spec/B$v1)) :Boolean true "(valid? {$type: spec/A$v1, n: -1, p: 1}.refineTo( spec/B$v1 ))" "true"])

  (hc :basic-2
      :spec
      [(valid? (refine-to {:$type :spec/A$v1, :p 1, :n -1} :spec/C$v1)) :Boolean [:throws "No active refinement path from 'spec/A$v1' to 'spec/C$v1'"] "(valid? {$type: spec/A$v1, n: -1, p: 1}.refineTo( spec/C$v1 ))" [:throws "No active refinement path from 'spec/A$v1' to 'spec/C$v1'"]])

  (hc :basic-2
      :spec
      [(refine-to {:$type :spec/A$v1, :p 10, :n -1} :spec/B$v1) :spec/B$v1 [:throws "invalid instance of 'spec/B$v1', violates constraints px"] "{$type: spec/A$v1, n: -1, p: 10}.refineTo( spec/B$v1 )" [:throws "invalid instance of 'spec/B$v1', violates constraints px"]])

  (hc :basic-2
      :spec
      [(valid? (refine-to {:$type :spec/A$v1, :p 10, :n -1} :spec/B$v1)) :Boolean false "(valid? {$type: spec/A$v1, n: -1, p: 10}.refineTo( spec/B$v1 ))" "false"])

  (hc :basic-2
      :spec
      [(valid (refine-to {:$type :spec/A$v1, :p 10, :n -1} :spec/B$v1)) [:Maybe :spec/B$v1] :Unset "(valid {$type: spec/A$v1, n: -1, p: 10}.refineTo( spec/B$v1 ))" "Unset"])

  (hc :basic-2
      :spec
      [(let [v (refine-to {:$type :spec/A$v1, :p 10, :n -1} :spec/B$v1)] (if-value v [1] [2])) [:throws "First argument to 'if-value' must have an optional type"]])

  (hc :basic-2
      :spec
      [(let [v (valid (refine-to {:$type :spec/A$v1, :p 10, :n -1} :spec/B$v1))] (if-value v [1] [2])) [:Vec :Integer] [2] "{ v = (valid {$type: spec/A$v1, n: -1, p: 10}.refineTo( spec/B$v1 )); (ifValue(v) {[1]} else {[2]}) }" "[2]"])

  (hc :basic-2
      :spec
      [(let [v (valid (refine-to {:$type :spec/A$v1, :p 1, :n -1} :spec/B$v1))] (if-value v [1] [2])) [:Vec :Integer] [1] "{ v = (valid {$type: spec/A$v1, n: -1, p: 1}.refineTo( spec/B$v1 )); (ifValue(v) {[1]} else {[2]}) }" "[1]"])

  (hc :basic-2
      :spec
      [(let [v (valid (refine-to {:$type :spec/A$v1, :p 1, :n -1} :spec/B$v1))] (if-value v [1] "no")) :Object [1] "{ v = (valid {$type: spec/A$v1, n: -1, p: 1}.refineTo( spec/B$v1 )); (ifValue(v) {[1]} else {\"no\"}) }" "[1]"])

  (hc :basic-2
      :spec
      [(let [v (valid (refine-to {:$type :spec/A$v1, :p 1, :n -1} :spec/B$v1))] (if-value v [1] ["no"])) [:Vec :Object] [1] "{ v = (valid {$type: spec/A$v1, n: -1, p: 1}.refineTo( spec/B$v1 )); (ifValue(v) {[1]} else {[\"no\"]}) }" "[1]"])

  (hc :basic-2
      :spec
      [(let [v (valid (refine-to {:$type :spec/A$v1, :p 1, :n -1} :spec/B$v1))] (if-value v [1] [])) [:Vec :Integer] [1] "{ v = (valid {$type: spec/A$v1, n: -1, p: 1}.refineTo( spec/B$v1 )); (ifValue(v) {[1]} else {[]}) }" "[1]"])

  (hc :basic-2
      :spec
      [(let [v (valid (refine-to {:$type :spec/A$v1, :p 10, :n -1} :spec/B$v1))] (if-value v [1] [])) [:Vec :Integer] [] "{ v = (valid {$type: spec/A$v1, n: -1, p: 10}.refineTo( spec/B$v1 )); (ifValue(v) {[1]} else {[]}) }" "[]"])

  (hc :basic-2
      :spec
      [(let [v (valid (refine-to {:$type :spec/A$v1, :p 10, :n -1} :spec/B$v1)) w (if-value v v v)] (if-value w 1 2)) :Integer 2 "{ v = (valid {$type: spec/A$v1, n: -1, p: 10}.refineTo( spec/B$v1 )); w = (ifValue(v) {v} else {v}); (ifValue(w) {1} else {2}) }" "2"])

  (hc :basic-2
      :spec
      [{:$type :spec/A$v1, :p 10, :n -1} :spec/A$v1 [:throws "invalid instance of 'spec/B$v1', violates constraints px"] "{$type: spec/A$v1, n: -1, p: 10}" [:throws "invalid instance of 'spec/B$v1', violates constraints px"]])

  (hc :basic-2
      :spec
      [(refine-to {:$type :spec/A$v1, :p 10, :n -1} :spec/A$v1) :spec/A$v1 [:throws "invalid instance of 'spec/B$v1', violates constraints px"] "{$type: spec/A$v1, n: -1, p: 10}.refineTo( spec/A$v1 )" [:throws "invalid instance of 'spec/B$v1', violates constraints px"]])

  (hc :basic-2
      :spec
      [(refine-to {:$type :spec/A$v1, :p 1, :n -1} :spec/A$v1) :spec/A$v1 {:$type :spec/A$v1, :p 1, :n -1} "{$type: spec/A$v1, n: -1, p: 1}.refineTo( spec/A$v1 )" "{$type: spec/A$v1, n: -1, p: 1}"])

  (hc :basic-2
      :spec
      [(valid (refine-to {:$type :spec/A$v1, :p 1, :n -1} :spec/C$v1)) [:Maybe :spec/C$v1] [:throws "No active refinement path from 'spec/A$v1' to 'spec/C$v1'"] "(valid {$type: spec/A$v1, n: -1, p: 1}.refineTo( spec/C$v1 ))" [:throws "No active refinement path from 'spec/A$v1' to 'spec/C$v1'"]])

  (hc :basic-2
      :spec
      [(valid? (refine-to {:$type :spec/A$v1, :p 1, :n -1} :spec/C$v1)) :Boolean [:throws "No active refinement path from 'spec/A$v1' to 'spec/C$v1'"] "(valid? {$type: spec/A$v1, n: -1, p: 1}.refineTo( spec/C$v1 ))" [:throws "No active refinement path from 'spec/A$v1' to 'spec/C$v1'"]])

  (hc :basic-2
      :spec
      [(refines-to? {:$type :spec/A$v1, :p 1, :n -1} :spec/C$v1) :Boolean false "{$type: spec/A$v1, n: -1, p: 1}.refinesTo?( spec/C$v1 )" "false"])

  (hc :basic-2
      :spec
      [(refines-to? {:$type :spec/A$v1, :p 1, :n -1} :spec/A$v1) :Boolean true "{$type: spec/A$v1, n: -1, p: 1}.refinesTo?( spec/A$v1 )" "true"])

  (hc :basic-2
      :spec
      [(refines-to? {:$type :spec/A$v1, :p 1, :n -1} :spec/X$v1) [:throws "Spec not found: 'spec/X$v1'"]])

  (hc :basic-2
      :spec
      [(get {:$type :spec/A$v1, :p 1, :n -1} :$type) [:throws "No such variable '$type' on spec ':spec/A$v1'"]]))

(deftest test-misc-types
  (h #"a" [:syntax-check-throws "Syntax error"])
  (h \a [:syntax-check-throws "Syntax error"])
  (h :a [:throws "Syntax error"])
  (h (1 2 3) [:syntax-check-throws "unknown function or operator"])
  (h () [:syntax-check-throws "unknown function or operator"])
  (h nil [:syntax-check-throws "Syntax error"])
  (hf (ex-info "fail" {}) [:throws "Syntax error"])
  (hf (fn []) [:throws "Syntax error"])
  (hf (random-uuid) [:throws "Syntax error"])
  (hf (make-array String 1) [:throws "Syntax error"])
  (hf #inst "2018-03-28T10:48:00.000-00:00" [:throws "Syntax error"])
  (hf java.lang.String [:throws "Syntax error"])

  ;; ranges are initially accepted as syntactically valid (since they are seqs), but there is no way to use them
  (hf (range 10) [:throws "function '0' not found"])
  (hf `(~'any? [~'x ~(range 10)] true) [:throws "function '0' not found"])
  (hf `(~'count ~(range 10) true) [:throws "function '0' not found"])
  (hf (filter odd?) [:throws "Syntax error"])
  (hf (promise) [:throws "Syntax error"])
  (hf (atom nil) [:throws "Syntax error"])
  (hf (agent nil) [:throws "Syntax error"])

  ;; lazy seqs are accepted
  (hf (map identity ['+ 1 2]) :Integer 3 "(1 + 2)" "3")
  ;; also seqs
  (hf (vals {:a '+ :b 1 :c 2}) :Integer 3 "(1 + 2)" "3")
  (hf (cons '+ (cons 2 (cons 3 '()))) :Integer 5 "(2 + 3)" "5"))

;; systematically test ways in which instances are created and used

(deftest test-instantiate-use
  (hc [(workspace :spec
                  {:spec/T [true]}
                  (spec :T
                        (variables [:n "Integer"])
                        (constraints)
                        (refinements)))]
      :spec
      [(get {:$type :spec/T$v1, :n 1} :n) :Integer 1 "{$type: spec/T$v1, n: 1}.n" "1"])
  (hc [(workspace :spec
                  {:spec/T [true]}
                  (spec :T
                        (variables [:n "Integer"])
                        (constraints)
                        (refinements)))]
      :spec
      [(valid {:$type :spec/T$v1, :n 1}) [:Maybe :spec/T$v1] {:$type :spec/T$v1, :n 1} "(valid {$type: spec/T$v1, n: 1})" "{$type: spec/T$v1, n: 1}"])
  (hc [(workspace :spec
                  {:spec/T [true]}
                  (spec :T
                        (variables [:n "Integer"])
                        (constraints)
                        (refinements)))]
      :spec
      [(valid? {:$type :spec/T$v1, :n 1}) :Boolean true "(valid? {$type: spec/T$v1, n: 1})" "true"])
  (hc [(workspace :spec
                  {:spec/T [true]}
                  (spec :T
                        (variables [:n "Integer"])
                        (constraints)
                        (refinements)))]
      :spec
      [(refine-to {:$type :spec/T$v1, :n 1} :spec/T$v1) :spec/T$v1 {:$type :spec/T$v1, :n 1} "{$type: spec/T$v1, n: 1}.refineTo( spec/T$v1 )" "{$type: spec/T$v1, n: 1}"])

  (hc [(workspace :spec
                  {:spec/T [false]}
                  (spec :T
                        (variables [:n "Integer"])
                        (constraints)
                        (refinements)))]
      :spec
      [(get {:$type :spec/T$v1, :n 1} :n) :Integer 1 "{$type: spec/T$v1, n: 1}.n" "1"])
  (hc [(workspace :spec
                  {:spec/T [false]}
                  (spec :T
                        (variables [:n "Integer"])
                        (constraints)
                        (refinements)))]
      :spec
      [(valid {:$type :spec/T$v1, :n 1}) [:Maybe :spec/T$v1] {:$type :spec/T$v1, :n 1} "(valid {$type: spec/T$v1, n: 1})" "{$type: spec/T$v1, n: 1}"])
  (hc [(workspace :spec
                  {:spec/T [false]}
                  (spec :T
                        (variables [:n "Integer"])
                        (constraints)
                        (refinements)))]
      :spec
      [(valid? {:$type :spec/T$v1, :n 1}) :Boolean true "(valid? {$type: spec/T$v1, n: 1})" "true"])
  (hc [(workspace :spec
                  {:spec/T [false]}
                  (spec :T
                        (variables [:n "Integer"])
                        (constraints)
                        (refinements)))]
      :spec
      [(refine-to {:$type :spec/T$v1, :n 1} :spec/T$v1) :spec/T$v1 {:$type :spec/T$v1, :n 1} "{$type: spec/T$v1, n: 1}.refineTo( spec/T$v1 )" "{$type: spec/T$v1, n: 1}"]))

(deftest test-component-use
  (hc [(workspace :spec
                  {:spec/T [true]
                   :spec/V [true]}
                  (spec :T
                        (variables [:n "Integer"])
                        (constraints)
                        (refinements))
                  (spec :V
                        (variables [:t :spec/T$v1])
                        (constraints)
                        (refinements)))]
      :spec
      [(get {:$type :spec/V$v1, :t {:$type :spec/T$v1, :n 1}} :t) :spec/T$v1 {:$type :spec/T$v1, :n 1} "{$type: spec/V$v1, t: {$type: spec/T$v1, n: 1}}.t" "{$type: spec/T$v1, n: 1}"])

  (hc [(workspace :spec
                  {:spec/T [true]
                   :spec/C [true]
                   :spec/V [true]}
                  (spec :C
                        (variables)
                        (constraints)
                        (refinements [:as_T :to :spec/T$v1 [:halite "{:$type :spec/T$v1 :n 1}"]]))
                  (spec :T
                        (variables [:n "Integer"])
                        (constraints)
                        (refinements))
                  (spec :V
                        (variables [:t :spec/T$v1])
                        (constraints)
                        (refinements)))]
      :spec
      [{:$type :spec/V$v1, :t {:$type :spec/C$v1}} [:throws "value of :t has wrong type"]])

  (hc [(workspace :spec
                  {:spec/T [false]
                   :spec/C [false]
                   :spec/V [true]}
                  (spec :C
                        (variables)
                        (constraints)
                        (refinements [:as_T :to :spec/T$v1 [:halite "{:$type :spec/T$v1 :n 1}"]]))
                  (spec :T
                        (variables [:n "Integer"])
                        (constraints)
                        (refinements))
                  (spec :V
                        (variables [:t :spec/T$v1])
                        (constraints)
                        (refinements)))]
      :spec
      [{:$type :spec/V$v1, :t {:$type :spec/C$v1}} :spec/V$v1 [:throws "instance cannot contain abstract value"] "{$type: spec/V$v1, t: {$type: spec/C$v1}}" [:throws "instance cannot contain abstract value"]])

  (hc [(workspace :spec
                  {:spec/T [false]
                   :spec/C [true]
                   :spec/V [true]}
                  (spec :C
                        (variables)
                        (constraints)
                        (refinements [:as_T :to :spec/T$v1 [:halite "{:$type :spec/T$v1 :n 1}"]]))
                  (spec :T
                        (variables [:n "Integer"])
                        (constraints)
                        (refinements))
                  (spec :V
                        (variables [:t :spec/T$v1])
                        (constraints)
                        (refinements)))]
      :spec
      [(get {:$type :spec/V$v1, :t {:$type :spec/C$v1}} :t) :Instance {:$type :spec/C$v1} "{$type: spec/V$v1, t: {$type: spec/C$v1}}.t" "{$type: spec/C$v1}"]))

(deftest test-instantiate-construction
  ;; T concrete
  ;; S != T => S abstract, T refines to S
  (hc [(workspace :spec
                  {:spec/T [false]
                   :spec/S [false]
                   :spec/U [true]}
                  (spec :U
                        (variables [:s :spec/S$v1])
                        (constraints)
                        (refinements))
                  (spec :S
                        (variables)
                        (constraints)
                        (refinements))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements [:as_S :to :spec/S$v1 [:halite "{:$type :spec/S$v1}"]])))]
      :spec
      [{:$type :spec/U$v1, :s {:$type :spec/T$v1}} :spec/U$v1 [:throws "instance cannot contain abstract value"] "{$type: spec/U$v1, s: {$type: spec/T$v1}}" [:throws "instance cannot contain abstract value"]])
  (hc [(workspace :spec
                  {:spec/T [false]
                   :spec/S [true]
                   :spec/U [true]}
                  (spec :U
                        (variables [:s :spec/S$v1])
                        (constraints)
                        (refinements))
                  (spec :S
                        (variables)
                        (constraints)
                        (refinements))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements [:as_S :to :spec/S$v1 [:halite "{:$type :spec/S$v1}"]])))]
      :spec
      [{:$type :spec/U$v1, :s {:$type :spec/T$v1}} [:throws "value of :s has wrong type"]])

  (hc [(workspace :spec
                  {:spec/T [true]
                   :spec/S [true]
                   :spec/U [true]}
                  (spec :U
                        (variables [:s :spec/S$v1])
                        (constraints)
                        (refinements))
                  (spec :S
                        (variables)
                        (constraints)
                        (refinements))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements [:as_S :to :spec/S$v1 [:halite "{:$type :spec/S$v1}"]])))]
      :spec
      [{:$type :spec/U$v1, :s {:$type :spec/T$v1}} [:throws "value of :s has wrong type"]])

  (hc [(workspace :spec
                  {:spec/T [true]
                   :spec/S [false]
                   :spec/U [true]}
                  (spec :U
                        (variables [:s :spec/S$v1])
                        (constraints)
                        (refinements))
                  (spec :S
                        (variables)
                        (constraints)
                        (refinements))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements [:as_S :to :spec/S$v1 [:halite "{:$type :spec/S$v1}"]])))]
      :spec
      [{:$type :spec/U$v1, :s {:$type :spec/T$v1}} :spec/U$v1 {:$type :spec/U$v1, :s {:$type :spec/T$v1}} "{$type: spec/U$v1, s: {$type: spec/T$v1}}" "{$type: spec/U$v1, s: {$type: spec/T$v1}}"])

  (hc [(workspace :spec
                  {:spec/T [true]
                   :spec/U [true]}
                  (spec :U
                        (variables [:s :spec/T$v1])
                        (constraints)
                        (refinements))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements)))]
      :spec
      [{:$type :spec/U$v1, :s {:$type :spec/T$v1}} :spec/U$v1 {:$type :spec/U$v1, :s {:$type :spec/T$v1}} "{$type: spec/U$v1, s: {$type: spec/T$v1}}" "{$type: spec/U$v1, s: {$type: spec/T$v1}}"]))

(deftest test-component-construction
  ;; S = T => T concrete
  ;; S != T => (T (concrete) refines to S (abstract)) or (S (concrete) refines to T (abstract)) or (S & T abstract, some type C refines to both of them)
  
  ;; C = T => T concrete and S abstract
  ;; C = S = T => T concrete
  ;; C = S => S concrete
  ;; C != S != T => T abstract, S abstract, C concrete, C refines to T, C refines to S

  (hc [(workspace :spec
                  {:spec/T [false]
                   :spec/C [true]
                   :spec/U [true]
                   :spec/V [true]}
                  (spec :V
                        (variables [:t :spec/T$v1])
                        (constraints)
                        (refinements))
                  (spec :U
                        (variables [:s :spec/C$v1])
                        (constraints)
                        (refinements))
                  (spec :C
                        (variables)
                        (constraints)
                        (refinements [:as_t :to :spec/T$v1 [:halite "{:$type :spec/T$v1}"]]))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements)))]
      :spec
      [{:$type :spec/U$v1, :s (get {:$type :spec/V$v1, :t {:$type :spec/C$v1}} :t)} [:throws "value of :s has wrong type"]])

  (hc [(workspace :spec
                  {:spec/T [true]
                   :spec/S [true]
                   :spec/C [true]
                   :spec/U [true]
                   :spec/V [true]}
                  (spec :V
                        (variables [:t :spec/T$v1])
                        (constraints)
                        (refinements))
                  (spec :U
                        (variables [:s :spec/S$v1])
                        (constraints)
                        (refinements))
                  (spec :S
                        (variables)
                        (constraints)
                        (refinements))
                  (spec :C
                        (variables)
                        (constraints)
                        (refinements [:as_t :to :spec/T$v1 [:halite "{:$type :spec/T$v1}"]]))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements)))]
      :spec
      "Since T is concrete, cannot use another type in its place"
      [{:$type :spec/U$v1, :s (get {:$type :spec/V$v1, :t {:$type :spec/C$v1}} :t)} [:throws "value of :t has wrong type"]])
  (hc [(workspace :spec
                  {:spec/T [true]
                   :spec/S [false]
                   :spec/U [true]
                   :spec/V [true]}
                  (spec :V
                        (variables [:t :spec/T$v1])
                        (constraints)
                        (refinements))
                  (spec :U
                        (variables [:s :spec/S$v1])
                        (constraints)
                        (refinements))
                  (spec :S
                        (variables)
                        (constraints)
                        (refinements))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements [:as_s :to :spec/S$v1 [:halite "{:$type :spec/S$v1}"]])))]
      :spec
      [{:$type :spec/U$v1, :s (get {:$type :spec/V$v1, :t {:$type :spec/T$v1}} :t)} :spec/U$v1 {:$type :spec/U$v1, :s {:$type :spec/T$v1}} "{$type: spec/U$v1, s: {$type: spec/V$v1, t: {$type: spec/T$v1}}.t}" "{$type: spec/U$v1, s: {$type: spec/T$v1}}"])
  (hc [(workspace :spec
                  {:spec/T [true]
                   :spec/U [true]
                   :spec/V [true]}
                  (spec :V
                        (variables [:t :spec/T$v1])
                        (constraints)
                        (refinements))
                  (spec :U
                        (variables [:s :spec/T$v1])
                        (constraints)
                        (refinements))
                  (spec :S
                        (variables)
                        (constraints)
                        (refinements))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements)))]
      :spec
      "Since T is concrete, T = C = S"
      [{:$type :spec/U$v1, :s (get {:$type :spec/V$v1, :t {:$type :spec/T$v1}} :t)} :spec/U$v1 {:$type :spec/U$v1, :s {:$type :spec/T$v1}} "{$type: spec/U$v1, s: {$type: spec/V$v1, t: {$type: spec/T$v1}}.t}" "{$type: spec/U$v1, s: {$type: spec/T$v1}}"])

  (hc [(workspace :spec
                  {:spec/T [true]
                   :spec/C [false]
                   :spec/U [true]
                   :spec/V [true]}
                  (spec :V
                        (variables [:t :spec/T$v1])
                        (constraints)
                        (refinements))
                  (spec :U
                        (variables [:s :spec/T$v1])
                        (constraints)
                        (refinements))
                  (spec :S
                        (variables)
                        (constraints)
                        (refinements))
                  (spec :C
                        (variables)
                        (constraints)
                        (refinements [:as_t :to :spec/T$v1 [:halite "{:$type :spec/T$v1}"]]))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements)))]
      :spec
      "Nonsense to try and use an abstract instance in a place that needs a concrete instance"
      [{:$type :spec/V$v1, :t {:$type :spec/C$v1}} [:throws "value of :t has wrong type"]])

  (hc [(workspace :spec
                  {:spec/T [false]
                   :spec/C [false]
                   :spec/V [true]}
                  (spec :V
                        (variables [:t :spec/T$v1])
                        (constraints)
                        (refinements))
                  (spec :C
                        (variables)
                        (constraints)
                        (refinements [:as_t :to :spec/T$v1 [:halite "{:$type :spec/T$v1}"]]))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements)))]
      :spec
      [{:$type :spec/V$v1, :t {:$type :spec/C$v1}} :spec/V$v1 [:throws "instance cannot contain abstract value"] "{$type: spec/V$v1, t: {$type: spec/C$v1}}" [:throws "instance cannot contain abstract value"]])

  ;; TODO
  (hc [(workspace :spec
                  {:spec/T [false]
                   :spec/S [true]
                   :spec/C [true]
                   :spec/U [true]
                   :spec/V [true]}
                  (spec :V
                        (variables [:t :spec/T$v1])
                        (constraints)
                        (refinements))
                  (spec :U
                        (variables [:s :spec/S$v1])
                        (constraints)
                        (refinements))
                  (spec :S
                        (variables)
                        (constraints)
                        (refinements))
                  (spec :C
                        (variables)
                        (constraints)
                        (refinements [:as_t :to :spec/T$v1 [:halite "{:$type :spec/T$v1}"]]
                                     [:as_s :to :spec/S$v1 [:halite "{:$type :spec/S$v1}"]]))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements [:as_s :to :spec/S$v1 [:halite "{:$type :spec/S$v1}"]])))]
      :spec
      "This should fail because s is concrete, so it cannot hold a C"
      [{:$type :spec/U$v1, :s (get {:$type :spec/V$v1, :t {:$type :spec/C$v1}} :t)} [:throws "value of :s has wrong type"]])

  (hc [(workspace :spec
                  {:spec/T [false]
                   :spec/S [true]
                   :spec/U [true]
                   :spec/V [true]}
                  (spec :V
                        (variables [:t :spec/T$v1])
                        (constraints)
                        (refinements))
                  (spec :U
                        (variables [:s :spec/S$v1])
                        (constraints)
                        (refinements))
                  (spec :S
                        (variables)
                        (constraints)
                        (refinements [:as_t :to :spec/T$v1 [:halite "{:$type :spec/T$v1}"]]))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements [:as_s :to :spec/S$v1 [:halite "{:$type :spec/S$v1}"]])))]
      :spec
      [{:$type :spec/U$v1, :s (get {:$type :spec/V$v1, :t {:$type :spec/S$v1}} :t)} [:throws "value of :s has wrong type"]])

  (hc [(workspace :spec
                  {:spec/T [false]
                   :spec/S [false]
                   :spec/C [true]
                   :spec/U [true]
                   :spec/V [true]}
                  (spec :V
                        (variables [:t :spec/T$v1])
                        (constraints)
                        (refinements))
                  (spec :U
                        (variables [:s :spec/S$v1])
                        (constraints)
                        (refinements))
                  (spec :S
                        (variables)
                        (constraints)
                        (refinements))
                  (spec :C
                        (variables)
                        (constraints)
                        (refinements [:as_t :to :spec/T$v1 [:halite "{:$type :spec/T$v1}"]]
                                     [:as_s :to :spec/S$v1 [:halite "{:$type :spec/S$v1}"]]))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements [:as_s :to :spec/S$v1 [:halite "{:$type :spec/S$v1}"]])))]
      :spec
      [{:$type :spec/U$v1, :s (get {:$type :spec/V$v1, :t {:$type :spec/C$v1}} :t)} :spec/U$v1 {:$type :spec/U$v1, :s {:$type :spec/C$v1}} "{$type: spec/U$v1, s: {$type: spec/V$v1, t: {$type: spec/C$v1}}.t}" "{$type: spec/U$v1, s: {$type: spec/C$v1}}"])

  (hc [(workspace :spec
                  {:spec/T [false]
                   :spec/S [false]
                   :spec/C [true]
                   :spec/U [true]
                   :spec/V [true]}
                  (spec :V
                        (variables [:t :spec/T$v1])
                        (constraints)
                        (refinements))
                  (spec :U
                        (variables [:s :spec/S$v1])
                        (constraints)
                        (refinements))
                  (spec :S
                        (variables)
                        (constraints)
                        (refinements))
                  (spec :C
                        (variables)
                        (constraints)
                        (refinements [:as_t :to :spec/T$v1 [:halite "{:$type :spec/T$v1}"]]
                                     [:as_s :to :spec/S$v1 [:halite "{:$type :spec/S$v1}"]]))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements)))]
      :spec
      [{:$type :spec/U$v1, :s (get {:$type :spec/V$v1, :t {:$type :spec/C$v1}} :t)} :spec/U$v1 {:$type :spec/U$v1, :s {:$type :spec/C$v1}} "{$type: spec/U$v1, s: {$type: spec/V$v1, t: {$type: spec/C$v1}}.t}" "{$type: spec/U$v1, s: {$type: spec/C$v1}}"])
  )

(deftest test-refine-to-construction
  ;; S != T => T must be concrete, S must be abstract
  (hc [(workspace :spec
                  {:spec/T [true]
                   :spec/S [false]
                   :spec/C [true]
                   :spec/U [true]}
                  (spec :U
                        (variables [:s :spec/S$v1])
                        (constraints)
                        (refinements))
                  (spec :S
                        (variables)
                        (constraints)
                        (refinements))
                  (spec :C
                        (variables)
                        (constraints)
                        (refinements [:as_t :to :spec/T$v1 [:halite "{:$type :spec/T$v1}"]]))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements [:as_s :to :spec/S$v1 [:halite "{:$type :spec/S$v1}"]])))]
      :spec
      [{:$type :spec/U$v1, :s (refine-to {:$type :spec/C$v1} :spec/T$v1)} :spec/U$v1 {:$type :spec/U$v1, :s {:$type :spec/T$v1}} "{$type: spec/U$v1, s: {$type: spec/C$v1}.refineTo( spec/T$v1 )}" "{$type: spec/U$v1, s: {$type: spec/T$v1}}"])

  (hc [(workspace :spec
                  {:spec/T [true]
                   :spec/S [false]
                   :spec/C [true]
                   :spec/U [true]}
                  (spec :U
                        (variables [:s :spec/S$v1])
                        (constraints)
                        (refinements))
                  (spec :S
                        (variables)
                        (constraints)
                        (refinements))
                  (spec :C
                        (variables)
                        (constraints)
                        (refinements [:as_t :to :spec/T$v1 [:halite "{:$type :spec/T$v1}"]]))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements)))]
      :spec
      [{:$type :spec/U$v1, :s (refine-to {:$type :spec/C$v1} :spec/T$v1)} :spec/U$v1 [:throws "No active refinement path from 'spec/T$v1' to 'spec/S$v1'"] "{$type: spec/U$v1, s: {$type: spec/C$v1}.refineTo( spec/T$v1 )}" [:throws "No active refinement path from 'spec/T$v1' to 'spec/S$v1'"]])

  (hc [(workspace :spec
                  {:spec/T [false]
                   :spec/S [false]
                   :spec/C [true]
                   :spec/U [true]}
                  (spec :U
                        (variables [:s :spec/S$v1])
                        (constraints)
                        (refinements))
                  (spec :S
                        (variables)
                        (constraints)
                        (refinements))
                  (spec :C
                        (variables)
                        (constraints)
                        (refinements [:as_t :to :spec/T$v1 [:halite "{:$type :spec/T$v1}"]]))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements [:as_s :to :spec/S$v1 [:halite "{:$type :spec/S$v1}"]])))]
      :spec
      [{:$type :spec/U$v1, :s (refine-to {:$type :spec/C$v1} :spec/T$v1)} :spec/U$v1 [:throws "instance cannot contain abstract value"] "{$type: spec/U$v1, s: {$type: spec/C$v1}.refineTo( spec/T$v1 )}" [:throws "instance cannot contain abstract value"]])

  (hc [(workspace :spec
                  {:spec/T [false]
                   :spec/S [true]
                   :spec/C [true]
                   :spec/U [true]}
                  (spec :U
                        (variables [:s :spec/S$v1])
                        (constraints)
                        (refinements))
                  (spec :S
                        (variables)
                        (constraints)
                        (refinements))
                  (spec :C
                        (variables)
                        (constraints)
                        (refinements [:as_t :to :spec/T$v1 [:halite "{:$type :spec/T$v1}"]]))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements [:as_s :to :spec/S$v1 [:halite "{:$type :spec/S$v1}"]])))]
      :spec
      [{:$type :spec/U$v1, :s (refine-to {:$type :spec/C$v1} :spec/T$v1)} [:throws "value of :s has wrong type"]])

  (hc [(workspace :spec
                  {:spec/T [true]
                   :spec/C [true]
                   :spec/S [true]
                   :spec/U [true]}
                  (spec :U
                        (variables [:s :spec/S$v1])
                        (constraints)
                        (refinements))
                  (spec :S
                        (variables)
                        (constraints)
                        (refinements))
                  (spec :C
                        (variables)
                        (constraints)
                        (refinements [:as_t :to :spec/T$v1 [:halite "{:$type :spec/T$v1}"]]))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements [:as_s :to :spec/S$v1 [:halite "{:$type :spec/S$v1}"]])))]
      :spec
      [{:$type :spec/U$v1, :s (refine-to {:$type :spec/C$v1} :spec/T$v1)} [:throws "value of :s has wrong type"]])

  ;; S = T => T concrete
  (hc [(workspace :spec
                  {:spec/T [true]
                   :spec/C [true]
                   :spec/U [true]}
                  (spec :U
                        (variables [:s :spec/T$v1])
                        (constraints)
                        (refinements))
                  (spec :C
                        (variables)
                        (constraints)
                        (refinements [:as_t :to :spec/T$v1 [:halite "{:$type :spec/T$v1}"]]))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements)))]
      :spec
      [{:$type :spec/U$v1, :s (refine-to {:$type :spec/C$v1} :spec/T$v1)} :spec/U$v1 {:$type :spec/U$v1, :s {:$type :spec/T$v1}} "{$type: spec/U$v1, s: {$type: spec/C$v1}.refineTo( spec/T$v1 )}" "{$type: spec/U$v1, s: {$type: spec/T$v1}}"])

  (hc [(workspace :spec
                  {:spec/T [false]
                   :spec/C [true]
                   :spec/U [true]}
                  (spec :U
                        (variables [:s :spec/T$v1])
                        (constraints)
                        (refinements))
                  (spec :C
                        (variables)
                        (constraints)
                        (refinements [:as_t :to :spec/T$v1 [:halite "{:$type :spec/T$v1}"]]))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements)))]
      :spec
      [{:$type :spec/U$v1, :s (refine-to {:$type :spec/C$v1} :spec/T$v1)} :spec/U$v1 [:throws "instance cannot contain abstract value"] "{$type: spec/U$v1, s: {$type: spec/C$v1}.refineTo( spec/T$v1 )}" [:throws "instance cannot contain abstract value"]]))

(deftest test-component-refinement
  ;; T = C = R & T concrete
  (is (thrown-with-msg? ExceptionInfo #"Exception validating spec"
                        (hc [(workspace :spec
                                        {:spec/T [false]
                                         :spec/C [true]
                                         :spec/V [true]
                                         :spec/R [true]}
                                        (spec :V
                                              (variables [:t :spec/T$v1])
                                              (constraints)
                                              (refinements [:as_r :to :spec/R$v1 [:halite "t"]]))
                                        (spec :C
                                              (variables)
                                              (constraints)
                                              (refinements [:as_t :to :spec/T$v1 [:halite "{:$type :spec/T$v1}"]]
                                                           [:as_r :to :spec/R$v1 [:halite "{:$type :spec/R$v1}"]]))
                                        (spec :T
                                              (variables)
                                              (constraints)
                                              (refinements))
                                        (spec :R
                                              (variables)
                                              (constraints)
                                              (refinements)))]
                            :spec
                            "fails because refinement expression is of type :Instance, but cannot work because R is not C, so the refinement type would not match, even if it knew it was a C in this case"
                            [(refine-to {:$type :spec/V$v1 :t {:$type :spec/C$v1}} :spec/R$v1)])))

  ;; TODO
  (is (thrown-with-msg? ExceptionInfo #"Exception validating spec"
                        (hc [(workspace :spec
                                        {:spec/T [false]
                                         :spec/C [true]
                                         :spec/V [true]}
                                        (spec :V
                                              (variables [:t :spec/T$v1])
                                              (constraints)
                                              (refinements [:as_c :to :spec/C$v1 [:halite "t"]]))
                                        (spec :C
                                              (variables)
                                              (constraints)
                                              (refinements [:as_t :to :spec/T$v1 [:halite "{:$type :spec/T$v1}"]]))
                                        (spec :T
                                              (variables)
                                              (constraints)
                                              (refinements)))]
                            :spec
                            "fails because refinement expression is of type :Instance. This effectively means that the type of t is C"
                            [(refine-to {:$type :spec/V$v1 :t {:$type :spec/C$v1}} :spec/C$v1)])))

  (is (thrown-with-msg? ExceptionInfo #"Exception validating spec"
                        (hc [(workspace :spec
                                        {:spec/T [false]
                                         :spec/C [true]
                                         :spec/V [false]}
                                        (spec :V
                                              (variables [:t :spec/T$v1])
                                              (constraints)
                                              (refinements [:as_t :to :spec/T$v1 [:halite "t"]]))
                                        (spec :C
                                              (variables)
                                              (constraints)
                                              (refinements [:as_t :to :spec/T$v1 [:halite "{:$type :spec/T$v1}"]]))
                                        (spec :T
                                              (variables)
                                              (constraints)
                                              (refinements)))]
                            :spec
                            "fails because refinement expression is of type :Instance"
                            [(refine-to {:$type :spec/V$v1, :t {:$type :spec/T$v1}} :spec/T$v1)])))

  (hc [(workspace :spec
                  {:spec/T [true]
                   :spec/V [true]}
                  (spec :V
                        (variables [:t :spec/T$v1])
                        (constraints)
                        (refinements [:as_t :to :spec/T$v1 [:halite "t"]]))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements)))]
      :spec
      [(refine-to {:$type :spec/V$v1, :t {:$type :spec/T$v1}} :spec/T$v1) :spec/T$v1 {:$type :spec/T$v1} "{$type: spec/V$v1, t: {$type: spec/T$v1}}.refineTo( spec/T$v1 )" "{$type: spec/T$v1}"])

  ;;
  
  (hc [(workspace :spec
                  {:spec/T [false]
                   :spec/C [true]
                   :spec/V [true]}
                  (spec :V
                        (variables [:t :spec/T$v1])
                        (constraints)
                        (refinements [:as_t :to :spec/T$v1 [:halite "(refine-to t :spec/T$v1)"]]))
                  (spec :C
                        (variables)
                        (constraints)
                        (refinements [:as_t :to :spec/T$v1 [:halite "{:$type :spec/T$v1}"]]))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements)))]
      :spec
      "This doesn't count because it has `refine-to` invocation around the field"
      [(refine-to {:$type :spec/V$v1, :t {:$type :spec/C$v1}} :spec/T$v1) :spec/T$v1 {:$type :spec/T$v1} "{$type: spec/V$v1, t: {$type: spec/C$v1}}.refineTo( spec/T$v1 )" "{$type: spec/T$v1}"])
  
  (hc [(workspace :spec
                  {:spec/T [true]
                   :spec/V [true]
                   :spec/R [true]}
                  (spec :V
                        (variables [:t :spec/T$v1])
                        (constraints)
                        (refinements [:as_r :to :spec/R$v1 [:halite "(refine-to t :spec/R$v1)"]]))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements [:as_r :to :spec/R$v1 [:halite "{:$type :spec/R$v1}"]]))
                  (spec :R
                        (variables)
                        (constraints)
                        (refinements)))]
      :spec
      "This doesn't count because it has `refine-to` invocation around the field"
      [(refine-to {:$type :spec/V$v1, :t {:$type :spec/T$v1}} :spec/R$v1) :spec/R$v1 {:$type :spec/R$v1} "{$type: spec/V$v1, t: {$type: spec/T$v1}}.refineTo( spec/R$v1 )" "{$type: spec/R$v1}"])

  (hc [(workspace :spec
                  {:spec/T [false]
                   :spec/C [true]
                   :spec/V [true]}
                  (spec :V
                        (variables [:t :spec/T$v1])
                        (constraints)
                        (refinements [:as_c :to :spec/C$v1 [:halite "(refine-to t :spec/C$v1)"]]))
                  (spec :C
                        (variables)
                        (constraints)
                        (refinements [:as_t :to :spec/T$v1 [:halite "{:$type :spec/T$v1}"]]))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements)))]
      :spec
      "This doesn't count because it has `refine-to` invocation around the field"
      [(refine-to {:$type :spec/V$v1, :t {:$type :spec/C$v1}} :spec/C$v1) :spec/C$v1 {:$type :spec/C$v1} "{$type: spec/V$v1, t: {$type: spec/C$v1}}.refineTo( spec/C$v1 )" "{$type: spec/C$v1}"])
  )

(deftest test-instantiate-refinement
  (is (thrown-with-msg? ExceptionInfo #"Exception validating spec"
       (hc [(workspace :spec
                                        {:spec/U [true]
                                         :spec/T [true]
                                         :spec/R [true]}
                                        (spec :U
                                              (variables)
                                              (constraints)
                                              (refinements [:as_r :to :spec/R$v1 [:halite "{:$type :spec/T$v1}"]]))
                                        (spec :T
                                              (variables)
                                              (constraints)
                                              (refinements [:as_r :to :spec/R$v1 [:halite "{:$type :spec/R$v1}"]]))
                                        (spec :R
                                              (variables)
                                              (constraints)
                                              (refinements)))]
                            :spec
                            [(refine-to {:$type :spec/U$v1} :spec/R$v1)])))

  (is (thrown-with-msg? ExceptionInfo #"Exception validating spec"
                        (hc [(workspace :spec
                                        {:spec/U [true]
                                         :spec/T [true]
                                         :spec/C [true]
                                         :spec/R [true]}
                                        (spec :U
                                              (variables)
                                              (constraints)
                                              (refinements [:as_r :to :spec/R$v1 [:halite "(refine-to {:$type :spec/C$v1} :spec/T$v1)"]]))
                                        (spec :C
                                              (variables)
                                              (constraints)
                                              (refinements [:as_T :to :spec/T$v1 [:halite "{:$type :spec/T$v1}"]]))
                                        (spec :T
                                              (variables)
                                              (constraints)
                                              (refinements [:as_r :to :spec/R$v1 [:halite "{:$type :spec/R$v1}"]]))
                                        (spec :R
                                              (variables)
                                              (constraints)
                                              (refinements)))]
                            :spec
                            [(refine-to {:$type :spec/U$v1} :spec/R$v1)])))

  (is (thrown-with-msg? ExceptionInfo #"Exception validating spec"
                        (hc [(workspace :spec
                                        {:spec/U [true]
                                         :spec/T [false]
                                         :spec/R [true]}
                                        (spec :U
                                              (variables)
                                              (constraints)
                                              (refinements [:as_r :to :spec/R$v1 [:halite "{:$type :spec/T$v1}"]]))
                                        (spec :T
                                              (variables)
                                              (constraints)
                                              (refinements [:as_r :to :spec/R$v1 [:halite "{:$type :spec/R$v1}"]]))
                                        (spec :R
                                              (variables)
                                              (constraints)
                                              (refinements)))]
                            :spec
                            [(refine-to {:$type :spec/U$v1} :spec/R$v1)])))
  
  )

(deftest test-component-refine-to
  (hc [(workspace :spec
                  {:spec/T [false]
                   :spec/C [true]
                   :spec/V [true]}
                  (spec :V
                        (variables [:t :spec/T$v1])
                        (constraints)
                        (refinements))
                  (spec :C
                        (variables)
                        (constraints)
                        (refinements [:as_t :to :spec/T$v1 [:halite "{:$type :spec/T$v1}"]]))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements)))]
      :spec
      [(refine-to (get {:$type :spec/V$v1, :t {:$type :spec/C$v1}} :t) :spec/T$v1) :spec/T$v1 {:$type :spec/T$v1} "{$type: spec/V$v1, t: {$type: spec/C$v1}}.t.refineTo( spec/T$v1 )" "{$type: spec/T$v1}"])

  (hc [(workspace :spec
                  {:spec/T [true]
                   :spec/V [true]}
                  (spec :V
                        (variables [:t :spec/T$v1])
                        (constraints)
                        (refinements))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements)))]
      :spec
      [(refine-to (get {:$type :spec/V$v1, :t {:$type :spec/T$v1}} :t) :spec/T$v1) :spec/T$v1 {:$type :spec/T$v1} "{$type: spec/V$v1, t: {$type: spec/T$v1}}.t.refineTo( spec/T$v1 )" "{$type: spec/T$v1}"])

  (hc [(workspace :spec
                  {:spec/T [false]
                   :spec/C [true]
                   :spec/V [true]
                   :spec/N [true]}
                  (spec :V
                        (variables [:t :spec/T$v1])
                        (constraints)
                        (refinements))
                  (spec :C
                        (variables)
                        (constraints)
                        (refinements [:as_t :to :spec/T$v1 [:halite "{:$type :spec/T$v1}"]]
                                     [:as_n :to :spec/N$v1 [:halite "{:$type :spec/N$v1}"]]))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements))
                  (spec :N
                        (variables)
                        (constraints)
                        (refinements)))]
      :spec
      [(refine-to (get {:$type :spec/V$v1, :t {:$type :spec/C$v1}} :t) :spec/N$v1) :spec/N$v1 {:$type :spec/N$v1} "{$type: spec/V$v1, t: {$type: spec/C$v1}}.t.refineTo( spec/N$v1 )" "{$type: spec/N$v1}"])

  (hc [(workspace :spec
                  {:spec/T [false]
                   :spec/C [true]
                   :spec/V [true]
                   :spec/N [true]}
                  (spec :V
                        (variables [:t :spec/T$v1])
                        (constraints)
                        (refinements))
                  (spec :C
                        (variables)
                        (constraints)
                        (refinements [:as_t :to :spec/T$v1 [:halite "{:$type :spec/T$v1}"]]))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements))
                  (spec :N
                        (variables)
                        (constraints)
                        (refinements)))]
      :spec
      [(refine-to (get {:$type :spec/V$v1, :t {:$type :spec/C$v1}} :t) :spec/N$v1) :spec/N$v1 [:throws "No active refinement path from 'spec/C$v1' to 'spec/N$v1'"] "{$type: spec/V$v1, t: {$type: spec/C$v1}}.t.refineTo( spec/N$v1 )" [:throws "No active refinement path from 'spec/C$v1' to 'spec/N$v1'"]])

  (hc [(workspace :spec
                  {:spec/T [true]
                   :spec/V [true]
                   :spec/N [true]}
                  (spec :V
                        (variables [:t :spec/T$v1])
                        (constraints)
                        (refinements))
                  (spec :T
                        (variables)
                        (constraints)
                        (refinements [:as_n :to :spec/N$v1 [:halite "{:$type :spec/N$v1}"]]))
                  (spec :N
                        (variables)
                        (constraints)
                        (refinements)))]
      :spec
      [(refine-to (get {:$type :spec/V$v1, :t {:$type :spec/T$v1}} :t) :spec/N$v1) :spec/N$v1 {:$type :spec/N$v1} "{$type: spec/V$v1, t: {$type: spec/T$v1}}.t.refineTo( spec/N$v1 )" "{$type: spec/N$v1}"])

  )

;; (time (run-tests))
