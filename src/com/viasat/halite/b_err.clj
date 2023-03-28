;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.b-err
  (:require [com.viasat.halite.lib.format-errors :as format-errors]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.format_errors Text]))

(set! *warn-on-reflection* true)

(format-errors/deferr spec-does-not-exist [data]
                      {:template "Spec not found: :spec-id"})

(format-errors/deferr variable-does-not-exist [data]
                      {:template "Variable does not exist: :spec-id :variable"})
