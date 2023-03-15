;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.test-prop-composition
  (:require [com.viasat.halite.propagate.bound-union :as bound-union]
            [schema.core :as s]
            [schema.test]
            [clojure.test :refer :all]))

;; Prismatic schema validation is too slow to leave on by default for these tests.
;; If you're debugging a test failure, and the problem is a 'type' error,
;; turning schema validation on is likely to help you track it down.
;; (use-fixtures :once schema.test/validate-schemas)

(deftest test-union-bounds
  (are [a b result]
       (= result (bound-union/union-bounds a b))

    :Unset :Unset :Unset

    ;; integer bounds
    1 1 1
    1 2 {:$in #{1 2}}
    1 :Unset {:$in #{1 :Unset}}
    :Unset 1 {:$in #{1 :Unset}}
    {:$in #{1 2}} 3 {:$in #{1 2 3}}
    {:$in #{1 2}} :Unset {:$in #{1 2 :Unset}}
    3 {:$in #{1 2}} {:$in #{1 2 3}}
    {:$in #{1 2}} {:$in #{2 3}} {:$in #{1 2 3}}

    {:$in [1 3]} 5 {:$in [1 5]}
    {:$in [1 3]} -4 {:$in [-4 3]}
    {:$in [1 3]} 2 {:$in [1 3]}
    {:$in [1 3]} :Unset {:$in [1 3 :Unset]}

    5 {:$in [1 3]} {:$in [1 5]}
    5 {:$in [1 3 :Unset]} {:$in [1 5 :Unset]}

    {:$in #{0 1 2}} {:$in [1 3]} {:$in [0 3]}
    {:$in [1 3]} {:$in #{0 1 2}} {:$in [0 3]}
    {:$in #{0 1 2 :Unset}} {:$in [1 3]} {:$in [0 3 :Unset]}
    {:$in [1 3 :Unset]} {:$in #{0 1 2}} {:$in [0 3 :Unset]}

    ;; boolean bounds
    true true true
    false false false
    :Unset true {:$in #{:Unset true}}
    true false {:$in #{true false}}
    {:$in #{true false}} true {:$in #{true false}}
    true {:$in #{true false}} {:$in #{true false}}
    :Unset {:$in #{true false}} {:$in #{true false :Unset}}
    true {:$in #{true false :Unset}} {:$in #{true false :Unset}}

    ;; spec-bounds
    {:$type :ws/A :a 1 :b true} {:$type :ws/A :a 2 :c {:$in [1 3]}}
    {:$type :ws/A :a {:$in #{1 2}} :b true :c {:$in [1 3]}}

    {:$type :ws/A :$refines-to {:ws/B {:n 1} :ws/D {:d {:$in [1 2]}}}}
    {:$type :ws/A :$refines-to {:ws/B {:n 2} :ws/C {:c true}}}
    {:$type :ws/A :$refines-to {:ws/B {:n {:$in #{1 2}}}}}

    {:$type :ws/A} {:$type [:Maybe :ws/A]} {:$type [:Maybe :ws/A]}
    {:$type [:Maybe :ws/A]} {:$type :ws/A} {:$type [:Maybe :ws/A]}
    :Unset {:$type :ws/A} {:$type [:Maybe :ws/A]}))

;; (run-tests)
