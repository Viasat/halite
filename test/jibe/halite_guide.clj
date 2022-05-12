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

  (h (mod 3 2) :Integer 1 "(3 % 2)" "1")

  (h (mod 3) [:throws "no matching signature for 'mod'"])

  (h (mod 3 1) :Integer 0 "(3 % 1)" "0")

  (h (mod 6 3 2) [:throws "no matching signature for 'mod'"])

  (h (mod -3 2) :Integer 1 "(-3 % 2)" "1")

  (h (mod 3 -2) :Integer -1 "(3 % -2)" "-1")

  (h (mod -3 -2) :Integer -1 "(-3 % -2)" "-1")

  (h (mod 3 0) :Integer [:throws "Divide by zero"] "(3 % 0)" [:throws "Divide by zero"])

  (h (expt 2 3) :Integer 8 "2.expt(3)" "8")

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

  (h (union "" "a") [:throws "Arguments to 'union' must be sets"]))

(deftest test-string-bool
  (h (and "true" false) [:throws "no matching signature for 'and'"])

  (h (=> true "hi") [:throws "no matching signature for '=>'"])

  (h (if (= "a" "b") (= (+ 1 3) 5) false) :Boolean false "(if((\"a\" == \"b\")) {((1 + 3) == 5)} else {false})" "false"))

(deftest test-bool-string
  (h (count true) [:throws "no matching signature for 'count'"])

  (h (concat "" false) [:throws "First argument to 'concat' must be a set or vector"]))

;; (run-tests)
