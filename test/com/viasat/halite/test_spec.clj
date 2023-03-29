;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-spec
  (:require [clojure.test :refer :all]
            [com.viasat.halite.spec :as spec]
            [schema.core :as s]
            [schema.test]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-get-optional-field-names
  (is (= #{:y :q}
         (set (spec/get-optional-field-names {:fields {:x :Integer
                                                       :y [:Maybe :Integer]
                                                       :z [:Vec :Integer]
                                                       :q [:Maybe :String]}}))))

  (is (= #{}
         (set (spec/get-optional-field-names {:fields {:x :Integer}})))))

;; (run-tests)
