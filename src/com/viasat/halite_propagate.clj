;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite-propagate
  "Public API for propagate"
  (:require [com.viasat.halite.envs :as envs]
            [com.viasat.halite.propagate :as propagate]
            [com.viasat.halite.propagate.prop-abstract :as prop-abstract]
            [com.viasat.halite.var-types :as var-types]
            [potemkin]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(s/defn propagate :- propagate/SpecBound
  ([senv :- (s/protocol envs/SpecEnv)
    initial-bound :- propagate/SpecBound]
   (propagate senv propagate/default-options initial-bound))
  ([senv :- (s/protocol envs/SpecEnv)
    opts :- prop-abstract/Opts
    initial-bound :- propagate/SpecBound]
   (let [senv (var-types/to-halite-spec-env senv)]
     (propagate/propagate senv opts initial-bound))))

(s/defn propagate-halite-senv :- propagate/SpecBound
  "Call propagate with an senv already formatted for Halite"
  ([senv :- (s/protocol envs/SpecEnv)
    initial-bound :- propagate/SpecBound]
   (propagate-halite-senv senv propagate/default-options initial-bound))
  ([senv :- (s/protocol envs/SpecEnv)
    opts :- prop-abstract/Opts
    initial-bound :- propagate/SpecBound]
   (propagate/propagate senv opts initial-bound)))

(potemkin/import-vars
 [propagate
  default-options])

