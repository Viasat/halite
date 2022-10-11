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
