;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite-propagate
  "Public API for propagate"
  (:require [com.viasat.halite.propagate :as propagate]
            [potemkin]))

(set! *warn-on-reflection* true)

(potemkin/import-vars
 [propagate
  default-options propagate])

