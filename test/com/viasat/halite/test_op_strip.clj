;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-strip
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.op-strip :as op-strip]
            [schema.test])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-basic
  (is (= {:$instance-of :ws/A$v1
          :x {:$primitive-type :Integer
              :$value? true}
          :y {:$primitive-type :Integer
              :$value? true}}
         (op-strip/strip-op {:$instance-of :ws/A$v1
                             :x {:$primitive-type :Integer
                                 :$value? true}
                             :y {:$primitive-type :Integer
                                 :$value? true}
                             :$constraints {"w" '(> x 1)}}))))

;; (run-tests)
