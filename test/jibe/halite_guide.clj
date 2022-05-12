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
       (is (= ~result-expected h-result#))
       (when (string? j-expr#)
         (is (= ~result-expected jh-result#)))
       (is (= ~j-expr-expected j-expr#))
       (when (string? j-expr#)
         (is (= ~j-result-expected j-result#)))

       (list (quote ~'h)
             (quote ~expr)
             t#
             h-result#
             j-expr#
             j-result#))))

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
      (is (= result-expected h-result))
      (when (string? j-expr)
        (is (= result-expected jh-result)))
      (is (= j-expr-expected j-expr))
      (when (string? j-expr)
        (is (= j-result-expected j-result)))

      (list 'hf
            expr
            t
            h-result
            j-expr
            j-result))))

(deftest test-all
  (h 1 :Integer 1 "1" "1")

  (h (+ 1 2) :Integer 3 "(1 + 2)" "3")

  (h (+ 1 2 3) :Integer 6 "(1 + 2 + 3)" "6")

  (h (+) [:throws "no matching signature for '+'"] [:throws "no matching signature for '+'"] [:throws "Too few args"] [:throws "Syntax error"])

  (h (- 3 2) :Integer 1 "(3 - 2)" "1")

  (h (- 3 2 1) :Integer 0 "(3 - 2 - 1)" "0")

  (h -1 :Integer -1 "-1" "-1")

  (h (+ 1 -1) :Integer 0 "(1 + -1)" "0")

  (h (- 3 -2) :Integer 5 "(3 - -2)" "5")

  (h (* 2 3) :Integer 6 "(2 * 3)" "6")

  (h (* 2 3 4) :Integer 24 "(2 * 3 * 4)" "24")

  (h (div 6 2) :Integer 3 "(6 / 2)" "3")

  (h (div 20 2 3) [:throws "no matching signature for 'div'"] [:throws "no matching signature for 'div'"] "(20 / 2 / 3)" [:throws "no matching signature for 'div'"])

  (h (div 7 2) :Integer 3 "(7 / 2)" "3")

  (h (div 1 2) :Integer 0 "(1 / 2)" "0")

  (h (mod 3 2) :Integer 1 "(3 % 2)" "1")

  (h (mod 3) [:throws "no matching signature for 'mod'"] [:throws "no matching signature for 'mod'"] [:throws "Unexpected arg count"] [:throws "Syntax error"])

  (h (mod 3 1) :Integer 0 "(3 % 1)" "0")

  (h (mod 6 3 2) [:throws "no matching signature for 'mod'"] [:throws "no matching signature for 'mod'"] [:throws "Unexpected arg count"] [:throws "Syntax error"])

  (h (mod -3 2) :Integer 1 "(-3 % 2)" "1")

  (h (mod 3 -2) :Integer -1 "(3 % -2)" "-1")

  (h (mod -3 -2) :Integer -1 "(-3 % -2)" "-1")

  (h (mod 3 0) :Integer [:throws "Divide by zero"] "(3 % 0)" [:throws "Divide by zero"])

  (h (expt 2 3) :Integer 8 "2.expt(3)" "8")

  (h (expt 2 3 4) [:throws "no matching signature for 'expt'"] [:throws "no matching signature for 'expt'"] "2.expt(3, 4)" [:throws "no matching signature for 'expt'"])

  (h (expt 2 0) :Integer 1 "2.expt(0)" "1")

  (h (expt 2 -1) :Integer [:throws "Invalid exponent"] "2.expt(-1)" [:throws "Invalid exponent"])

  (h (expt -2 3) :Integer -8 "-2.expt(3)" "-8")

  (h (expt -2 1) :Integer -2 "-2.expt(1)" "-2")

  (h (expt -2 4) :Integer 16 "-2.expt(4)" "16")

  (h (expt -2 0) :Integer 1 "-2.expt(0)" "1")

  (h (expt -2 -3) :Integer [:throws "Invalid exponent"] "-2.expt(-3)" [:throws "Invalid exponent"])

  (h (inc 1) :Integer 2 "(1 + 1)" "2")

  (h (inc 1 2) [:throws "no matching signature for 'inc'"] [:throws "no matching signature for 'inc'"] [:throws "Unexpected arg count"] [:throws "Syntax error"])

  (h (dec 1) :Integer 0 "(1 - 1)" "0")

  (h (dec 1 2) [:throws "no matching signature for 'dec'"] [:throws "no matching signature for 'dec'"] [:throws "Unexpected arg count"] [:throws "Syntax error"])

  (h (inc 9223372036854775807) :Integer [:throws "long overflow"] "(9223372036854775807 + 1)" [:throws "long overflow"])

  (h (dec 9223372036854775807) :Integer 9223372036854775806 "(9223372036854775807 - 1)" "9223372036854775806")

  (h (dec -9223372036854775808) :Integer [:throws "long overflow"] "(-9223372036854775808 - 1)" [:throws "long overflow"])

  (h (inc -9223372036854775808) :Integer -9223372036854775807 "(-9223372036854775808 + 1)" "-9223372036854775807")

  (h (abs 0) :Integer 0 "0.abs()" "0")

  (h (abs 1) :Integer 1 "1.abs()" "1")

  (h (abs -1) :Integer 1 "-1.abs()" "1")

  (h (abs 1 2 3) [:throws "no matching signature for 'abs'"] [:throws "no matching signature for 'abs'"] "1.abs(2, 3)" [:throws "no matching signature for 'abs'"])

  (h (abs (- 1 4)) :Integer 3 "(1 - 4).abs()" "3")

  (h (abs -9223372036854775808) :Integer -9223372036854775808 "-9223372036854775808.abs()" "-9223372036854775808")

  (h (= 1) [:throws "Wrong number of arguments to '=': expected at least 2, but got 1"] [:throws "Wrong number of arguments to '=': expected at least 2, but got 1"] [:throws "Too few args"] [:throws "Syntax error"])

  (h (= 1 1) :Boolean true "(1 == 1)" "true")

  (h (= 0 0) :Boolean true "(0 == 0)" "true")

  (h (= -9223372036854775808 -9223372036854775808) :Boolean true "(-9223372036854775808 == -9223372036854775808)" "true")

  (h (= 1 0) :Boolean false "(1 == 0)" "false")

  ;; (h (= 1 1 1) :Boolean true "(1 == 1 == 1)" [:throws "Syntax error"])

  (h 2147483647 :Integer 2147483647 "2147483647" "2147483647")

  (h -2147483648 :Integer -2147483648 "-2147483648" "-2147483648")

  (h 9223372036854775807 :Integer 9223372036854775807 "9223372036854775807" "9223372036854775807")

  (h 9223372036854775808 [:throws "Syntax error"] [:throws "Syntax error"] [:throws "Invalid numeric type"] [:throws "Syntax error"])

  (h (+ 9223372036854775808 0) [:throws "Syntax error"] [:throws "Syntax error"] [:throws "Invalid numeric type"] [:throws "Syntax error"])

  (h -9223372036854775808 :Integer -9223372036854775808 "-9223372036854775808" "-9223372036854775808")

  (h (* 2147483647 2147483647) :Integer 4611686014132420609 "(2147483647 * 2147483647)" "4611686014132420609")

  (h (* 9223372036854775807 2) :Integer [:throws "long overflow"] "(9223372036854775807 * 2)" [:throws "long overflow"])

  (h (+ 9223372036854775807 1) :Integer [:throws "long overflow"] "(9223372036854775807 + 1)" [:throws "long overflow"])

  (h (- 9223372036854775807 1) :Integer 9223372036854775806 "(9223372036854775807 - 1)" "9223372036854775806")

  (h (- 9223372036854775807 -1) :Integer [:throws "long overflow"] "(9223372036854775807 - -1)" [:throws "long overflow"])

  (h (- -9223372036854775808 1) :Integer [:throws "long overflow"] "(-9223372036854775808 - 1)" [:throws "long overflow"])

  (h -9223372036854775809 [:throws "Syntax error"] [:throws "Syntax error"] [:throws "Invalid numeric type"] [:throws "Syntax error"])

  (h (+ -9223372036854775808 -1) :Integer [:throws "long overflow"] "(-9223372036854775808 + -1)" [:throws "long overflow"])

  (h (+ -9223372036854775808 1) :Integer -9223372036854775807 "(-9223372036854775808 + 1)" "-9223372036854775807")

  (hf (short 1) [:throws "Syntax error"] [:throws "Syntax error"] [:throws "Invalid numeric type"])

  (h 1N [:throws "Syntax error"] [:throws "Syntax error"] [:throws "Invalid numeric type"] [:throws "Syntax error"])

  (h 1.1 [:throws "Syntax error"] [:throws "Syntax error"] [:throws "Invalid numeric type"] [:throws "Syntax error"])

  (h 1.1M [:throws "Syntax error"] [:throws "Syntax error"] [:throws "Invalid numeric type"] [:throws "Syntax error"])

  (h 1/2 [:throws "Syntax error"] [:throws "Syntax error"] [:throws "Invalid numeric type"] [:throws "Syntax error"])

  (hf (byte 1) [:throws "Syntax error"] [:throws "Syntax error"] [:throws "Invalid numeric type"] [:throws "Syntax error"])

  (h ##NaN [:throws "Syntax error"] [:throws "Syntax error"] [:throws "Invalid numeric type"] [:throws "Syntax error"])

  (hf Double/NaN [:throws "Syntax error"] [:throws "Syntax error"] [:throws "Invalid numeric type"] [:throws "Syntax error"])

  (h ##Inf [:throws "Syntax error"] [:throws "Syntax error"] [:throws "Invalid numeric type"] [:throws "Syntax error"])

  (h ##-Inf [:throws "Syntax error"] [:throws "Syntax error"] [:throws "Invalid numeric type"] [:throws "Syntax error"]))

;; (run-tests)
