;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.test-prop-top-concrete
  "This namespace exists to provide a test case for propagate that runs with schema validation on."
  (:require [com.viasat.halite.propagate :as hp]
            [schema.test :refer [validate-schemas]])
  (:use clojure.test))

(clojure.test/use-fixtures :once validate-schemas)

(deftest test-top-level-refines-to-concrete-spec-with-refinement
  (is (= {:$in {:ws/A {:$refines-to {}}
                :ws/B {:$refines-to {:ws/A {}}}}
          :$refines-to {}}
         (hp/propagate '{:ws/A {}
                         :ws/B {:refines-to {:ws/A {:expr {:$type :ws/A}}}}}
                       {:$refines-to {:ws/A {}}}))))

;; (time (clojure.test/run-tests))
