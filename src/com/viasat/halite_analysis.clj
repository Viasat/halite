;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite-analysis
  "Public API for analyzing halite expressions"
  (:require [com.viasat.halite.analysis :as analysis]
            [com.viasat.halite.type-check :as halite-type-check]
            [potemkin]))

(set! *warn-on-reflection* true)

(potemkin/import-vars
 [analysis
  find-spec-refs-but-tail Range Natural gather-referenced-spec-ids gather-free-vars compute-tlfc-map
  replace-free-vars make-fixed-decimal-string encode-fixed-decimals])

(potemkin/import-vars
 [halite-type-check
  find-field-accesses])
