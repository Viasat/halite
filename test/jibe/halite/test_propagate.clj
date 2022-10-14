;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.test-propagate
  (:require [jibe.halite.halite-envs :as halite-envs]
            [jibe.halite.propagate :as hp]
            [jibe.halite.transpile.lowering :as lowering]
            [jibe.halite.transpile.rewriting :as rewriting :refer [with-summarized-trace-for]]
            [jibe.halite.transpile.simplify :as simplify]
            [jibe.halite.transpile.ssa :as ssa]
            [jibe.halite.transpile.util :refer [fixpoint]]
            [schema.core :as s]
            [schema.test]
            [viasat.choco-clj-opt :as choco-clj])
  (:use clojure.test))

(def strings-and-abstract-specs-example
  '{:ws/Colored
    {:abstract? true
     :spec-vars {:color "String"}
     :constraints [["validColors" (or (= color "red") (= color "green") (= color "blue"))]]
     :refines-to {}}

    :ws/Car
    {:spec-vars {:horsePower "Integer"}
     :constraints [["validHorsePowers" (and (<= 120 horsePower) (<= horsePower 300))]]
     :refines-to {:ws/Colored
                  {:expr {:$type :ws/Colored
                          :color (if (> horsePower 250) "red" "blue")}}}}})

(deftest test-strings-and-abstract-specs-example
  (are [in out]
       (= out (hp/propagate strings-and-abstract-specs-example in))

    {:$type :ws/Car}
    {:$type :ws/Car,
     :horsePower {:$in [120 300]},
     :$refines-to #:ws{:Colored {:color {:$in #{"blue" "green" "red"}}}}}

    {:$type :ws/Car :$refines-to {:ws/Colored {:color {:$in #{"red" "yellow"}}}}}
    {:$type :ws/Car,
     :horsePower {:$in [251 300]},
     :$refines-to #:ws{:Colored {:color "red"}}}

    {:$type :ws/Car :horsePower 140}
    {:$type :ws/Car, :horsePower 140
     :$refines-to {:ws/Colored {:color "blue"}}}))
