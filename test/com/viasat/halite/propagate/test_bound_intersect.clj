;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.test-bound-intersect
  (:require [clojure.test :refer :all]
            [com.viasat.halite.propagate.bound-intersect :as bound-intersect]
            [schema.test :refer [validate-schemas]])
  (:import [clojure.lang ExceptionInfo]
           [org.chocosolver.solver.exception ContradictionException]))

(use-fixtures :once validate-schemas)

(deftest test-bound-intersect
  (is (= {:$in {}}
         (bound-intersect/combine-bounds {:$type :spec/A}
                                         {:$type :spec/B})))
  (is (= {:$type :spec/A}
         (bound-intersect/combine-bounds {:$type :spec/A}
                                         {:$type :spec/A})))
  (is (= {:$type :spec/A
          :x 2}
         (bound-intersect/combine-bounds {:$type :spec/A
                                          :x 2}
                                         {:$type :spec/A
                                          :x {:$in #{2}}}))))

;; (run-tests)
