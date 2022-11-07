;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite-analysis
  "Public API for analyzing halite expressions"
  (:require [com.viasat.halite.analysis :as analysis]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.type-check :as type-check]
            [com.viasat.halite.var-type :as var-type]
            [potemkin]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(s/defn find-field-accesses
  [senv :- (s/protocol envs/SpecEnv)
   spec-info :- var-type/UserSpecInfo
   expr]
  (let [senv (var-type/to-halite-spec-env senv)
        spec-info (var-type/to-halite-spec senv spec-info)]
    (type-check/find-field-accesses senv spec-info expr)))

(potemkin/import-vars
 [analysis
  find-spec-refs-but-tail Range Natural gather-referenced-spec-ids gather-free-vars compute-tlfc-map
  replace-free-vars make-fixed-decimal-string encode-fixed-decimals])
