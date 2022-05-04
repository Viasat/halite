;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.transpile.test-simplify
  (:require [jibe.halite.envs :as halite-envs]
            [jibe.halite.transpile.ssa :as ssa]
            [jibe.halite.transpile.simplify :refer [simplify]]
            [schema.test])
  (:use clojure.test))

(use-fixtures :once schema.test/validate-schemas)

(deftest test-simplify
  (let [senv '{:ws/A
               {:spec-vars {:an :Integer, :ap :Boolean}
                :constraints []
                :refines-to {}}}]
    (are [expr simplified]
        (= simplified
           (binding [ssa/*next-id* (atom 0)]
             (-> senv
                 (update-in [:ws/A :constraints] conj ["c" expr])
                 (halite-envs/spec-env)
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

      '(if ap true false) '(if ap true false)
      '(if true 1 2) 1
      '(if false 1 2) 2
      '(if (and true true) 1 2) 1

      '(not true) false
      '(not false) true
      '(not (and true false)) true
      '(not (if true false true)) true
      )))
