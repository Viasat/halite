;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-inflate
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.op-inflate :as op-inflate]
            [schema.test])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-basic
  (is (= {:$instance-of :ws/A$v1
          :x {:$primitive-type :Integer
              :$value? true
              :$ranges #{[2 100]}}
          :y {:$primitive-type :Integer
              :$value? true
              :$ranges #{[1 99]}}}
         (op-inflate/inflate-op {:$instance-of :ws/A$v1
                                 :x {:$primitive-type :Integer
                                     :$value? true}
                                 :y {:$primitive-type :Integer
                                     :$value? true}}
                                {[:x] {:$ranges #{[2 100]}}
                                 [:y] {:$ranges #{[1 99]}}}))))

;; (run-tests)
