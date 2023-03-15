;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.gen-docs
  (:require [clojure.test :as test :refer :all]
            [com.viasat.halite-docs :as halite-docs]
            [schema.test :as schema.test]))

;; (use-fixtures :once schema.test/validate-schemas)

(deftest gen-docs
  (#'halite-docs/generate-local-docs))

;; (time (run-tests))
