;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.test-prop-top-concrete-schema-check
  "This namespace exists to provide a test case for propagate that runs with schema validation on."
  (:require [clojure.test :refer :all]
            [com.viasat.halite.propagate :as propagate]
            [schema.test :as schema.test]))

(use-fixtures :once schema.test/validate-schemas)

(deftest test-top-level-refines-to-concrete-spec-with-refinement
  (is (= {:$in {:ws/A {:$refines-to {}}
                :ws/B {:$refines-to {:ws/A {}}}}
          :$refines-to {}}
         (propagate/propagate '{:ws/A {}
                                :ws/B {:refines-to {:ws/A {:expr {:$type :ws/A}}}}}
                              {:$refines-to {:ws/A {}}}))))

;; (time (run-tests))
