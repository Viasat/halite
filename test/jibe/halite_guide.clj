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
           j-expr# (jadeite/to-jadeite ~expr)
           t# (try (halite/type-check senv# tenv# ~expr)
                   (catch RuntimeException e#
                     [:throws (.getMessage e#)]))
           h-result# (try (halite/eval-expr senv# tenv# env# ~expr)
                          (catch RuntimeException e#
                            [:throws (.getMessage e#)]))
           jh-expr# (jadeite/to-halite j-expr#)

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
       (is (= ~result-expected jh-result#))
       (is (= ~j-expr-expected j-expr#))
       (is (= ~j-result-expected j-result#))

       (list (quote ~'h)
             ~expr
             t#
             h-result#
             j-expr#
             j-result#))))

(deftest test-all
  (h 1 :Integer 1 "1" "1")

  (h '(+ 1 2) :Integer 3 "(1 + 2)" "3")

  (h '(- 3 2) :Integer 1 "(3 - 2)" "1")

  (h -1 :Integer -1 "-1" "-1")

  (h '(+ 1 -1) :Integer 0 "(1 + -1)" "0")

  (h '(- 3 -2) :Integer 5 "(3 - -2)" "5")

  (h '(* 2 3) :Integer 6 "(2 * 3)" "6")

  (h '(div 6 2) :Integer 3 "(6 / 2)" "3")

  (h '(div 7 2) :Integer 3 "(7 / 2)" "3")

  (h '(div 1 2) :Integer 0 "(1 / 2)" "0")

  (h '(mod 3 2) :Integer 1 "(3 % 2)" "1")

  (h '(mod -3 2) :Integer 1 "(-3 % 2)" "1")

  (h '(mod 3 -2) :Integer -1 "(3 % -2)" "-1")

  (h '(mod -3 -2) :Integer -1 "(-3 % -2)" "-1")

  (h '(mod 3 0) :Integer [:throws "Divide by zero"] "(3 % 0)" [:throws "Divide by zero"])

  (h 2147483647 :Integer 2147483647 "2147483647" "2147483647")

  (h -2147483648 :Integer -2147483648 "-2147483648" "-2147483648")

  (h 9223372036854775807 :Integer 9223372036854775807 "9223372036854775807" "9223372036854775807")

  (h 9223372036854775808 [:throws "Syntax error"] [:throws "Syntax error"] "9223372036854775808" [:throws "Syntax error"])

  (h '(+ 9223372036854775808 0) [:throws "Syntax error"] [:throws "Syntax error"] "(9223372036854775808 + 0)" [:throws "Syntax error"])

  (h -9223372036854775808 :Integer -9223372036854775808 "-9223372036854775808" "-9223372036854775808")

  (h '(* 2147483647 2147483647) :Integer 4611686014132420609 "(2147483647 * 2147483647)" "4611686014132420609")

  (h '(* 9223372036854775807 2) :Integer [:throws "long overflow"] "(9223372036854775807 * 2)" [:throws "long overflow"])

  (h '(+ 9223372036854775807 1) :Integer [:throws "long overflow"] "(9223372036854775807 + 1)" [:throws "long overflow"])

  (h '(- 9223372036854775807 1) :Integer 9223372036854775806 "(9223372036854775807 - 1)" "9223372036854775806")

  (h '(- 9223372036854775807 -1) :Integer [:throws "long overflow"] "(9223372036854775807 - -1)" [:throws "long overflow"])

  (h '(- -9223372036854775808 1) :Integer [:throws "long overflow"] "(-9223372036854775808 - 1)" [:throws "long overflow"])

  (h -9223372036854775809 [:throws "Syntax error"] [:throws "Syntax error"] "-9223372036854775809" [:throws "Syntax error"])

  (h '(+ -9223372036854775808 -1) :Integer [:throws "long overflow"] "(-9223372036854775808 + -1)" [:throws "long overflow"])

  (h '(+ -9223372036854775808 1) :Integer -9223372036854775807 "(-9223372036854775808 + 1)" "-9223372036854775807"))

;; (run-tests)
