;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.transpile.test-simplify
  (:require [com.viasat.halite.envs :as envs]
            [com.viasat.halite.transpile.ssa :as ssa]
            [com.viasat.halite.transpile.simplify :refer [simplify]]
            [schema.test])
  (:use clojure.test))

(use-fixtures :once schema.test/validate-schemas)

(deftest test-simplify
  (let [senv '{:ws/A
               {:spec-vars {:an :Integer, :ap :Boolean}}}]
    (are [expr simplified]
         (= simplified
            (binding [ssa/*hide-non-halite-ops* false]
              (-> senv
                  (update-in [:ws/A :constraints] conj ["c" expr])
                  (envs/spec-env)
                  (ssa/build-spec-ctx :ws/A)
                  (simplify)
                  :ws/A
                  (ssa/spec-from-ssa)
                  :constraints
                  first second)))

      true true
      '(= an 1) '(= an 1)
      '(and true true) true
      '(and true false) false
      '(and (and true true) true) true
      '(and (and false true) true) false
      '(and true ap) 'ap
      '(and true) true
      '(and ap) 'ap
      '(and false ap false) '(and false ap)
      '(and ap ap) 'ap

      '(if ap 1 2) '(if ap 1 2)
      '(if true 1 2) 1
      '(if false 1 2) 2
      '(if (and true true) 1 2) 1
      '(if ap true true) true
      '(if ap false false) false
      '(if ap true false) 'ap
      '(if ap false true) '(not ap)
      '(if ap 1 1) 1
      '(if (< 1 2) true false) true

      '(not true) false
      '(not false) true
      '(not (and true false)) true
      '(not (if true false true)) true

      '($do! (= an 1)) '(= an 1)
      '($do! 1 true (div 120 an) false 42) '($do! (div 120 an) 42)
      '($do! 1 2 3) 3

      '(if ($value? no-value) 3 4) 4
      '(if ($value? an) (+ an 1) 12) '(+ an 1))))

;; (run-tests)
