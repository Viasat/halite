;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.gen-docs
  (:require [clojure.test :as test :refer [deftest]]
            [com.viasat.halite.doc.docs :as docs]
            [schema.test :refer [validate-schemas]]))

;; (clojure.test/use-fixtures :once validate-schemas)

(deftest gen-docs
  (docs/generate-local-docs))

;; (time (clojure.test/run-tests))
