;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-fog
  (:require [clojure.test :refer :all]
            [com.viasat.halite.fog :as fog]
            [schema.test]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(fog/init)

(deftest test-reader
  (is (= #fog :String
         (fog/fog-reader :String))))

(deftest test-get-type
  (is (= [:Vec :Integer]
         (->> [:Vec :Integer]
              fog/make-fog
              fog/get-type))))

;; (time (run-tests))
