;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.propagate
  "Constraint propagation for halite."
  (:require [jibe.halite.halite-envs :as halite-envs]
            [jibe.halite.propagate.prop-abstract :as prop-abstract]
            [schema.core :as s]))

(def Bound prop-abstract/Bound)

(def SpecBound prop-abstract/SpecBound)

(def Opts prop-abstract/Opts)

(def default-options prop-abstract/default-options)

(s/defn propagate :- SpecBound
  ([senv :- (s/protocol halite-envs/SpecEnv), initial-bound :- SpecBound]
   (propagate senv default-options initial-bound))
  ([senv :- (s/protocol halite-envs/SpecEnv), opts :- prop-abstract/Opts, initial-bound :- SpecBound]
   (prop-abstract/propagate senv opts initial-bound)))

