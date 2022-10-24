;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.test-bound-intersect
  (:require [com.viasat.halite.propagate.bound-intersect :as bound-intersect]
            [schema.test :refer [validate-schemas]])
  (:use clojure.test)
  (:import [clojure.lang ExceptionInfo]
           [org.chocosolver.solver.exception ContradictionException]))

(clojure.test/use-fixtures :once validate-schemas)

(deftest test-bound-intersect
  (is (= {:$in {}}
         (bound-intersect/combine-bounds {:$type :spec/A}
                                         {:$type :spec/B})))
  (is (= {:$type :spec/A}
         (bound-intersect/combine-bounds {:$type :spec/A}
                                         {:$type :spec/A}))))

;; (clojure.test/run-tests)
