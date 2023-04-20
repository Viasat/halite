;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-instance-literal
  (:require [clojure.test :refer :all]
            [com.viasat.halite.instance-literal :as instance-literal]
            [schema.test]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(instance-literal/init)

(deftest test-reader
  (is (= #instance [[:p :q] {a 1}]
         (instance-literal/instance-literal-reader [[:p :q] {'a 1}]))))

(deftest test-get-bindings
  (is (= '{a 1
           b 2}
         (->> (instance-literal/make-instance-literal [:p] '{a 1 b 2})
              instance-literal/get-bindings))))

;; (time (run-tests))
