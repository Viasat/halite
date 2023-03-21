;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.gen-docs
  (:require [clojure.test :as test :refer [deftest]]
            [com.viasat.halite-docs :as halite-docs]
            [schema.test :refer [validate-schemas]]))

;; (clojure.test/use-fixtures :once validate-schemas)

(deftest gen-docs
  (#'halite-docs/generate-local-docs))

;; (time (clojure.test/run-tests))
