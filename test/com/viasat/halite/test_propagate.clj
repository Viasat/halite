;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-propagate
  (:require [com.viasat.halite.envs :as halite-envs]
            [com.viasat.halite.propagate :as hp]
            [com.viasat.halite.transpile.lowering :as lowering]
            [com.viasat.halite.transpile.rewriting :as rewriting :refer [with-summarized-trace-for]]
            [com.viasat.halite.transpile.simplify :as simplify]
            [com.viasat.halite.transpile.ssa :as ssa]
            [com.viasat.halite.transpile.util :refer [fixpoint]]
            [schema.core :as s]
            [schema.test]
            [com.viasat.halite.choco-clj-opt :as choco-clj])
  (:use clojure.test))

(def strings-and-abstract-specs-example
  '{:ws/Colored
    {:abstract? true
     :spec-vars {:color "String"}
     :constraints [[:validColors (or (= color "red") (= color "green") (= color "blue"))]]}

    :ws/Car
    {:spec-vars {:horsePower "Integer"}
     :constraints [[:validHorsePowers (and (<= 120 horsePower) (<= horsePower 300))]]
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
