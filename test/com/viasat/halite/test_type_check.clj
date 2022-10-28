;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-type-check
  (:require [com.viasat.halite.envs :as halite-envs]
            [com.viasat.halite.type-check :as halite-type-check]
            [schema.core :as s]
            [schema.test :refer [validate-schemas]]
            [clojure.test :refer :all]))

(set! *warn-on-reflection* true)

(clojure.test/use-fixtures :once validate-schemas)

(deftest test-find-field-accesses
  (is (= #{{:spec-id :ws/D$v1
            :variable-name :xss}
           {:spec-id :ws2/B$v1
            :variable-name :d}}
         (halite-type-check/find-field-accesses
          {:ws/A$v1 {:spec-vars {:x "Integer"
                                 :y "Boolean"
                                 :c :ws2/B$v1}}
           :ws2/B$v1 {:spec-vars {:d :ws/D$v1}}
           :ws/C$v1 {:spec-vars {:xs ["Integer"]}}
           :ws/D$v1 {:spec-vars {:xss [["Integer"]]}}}
          {:spec-vars {:x "Integer"
                       :y "Boolean"
                       :c [:ws2/B$v1]}}
          '(get-in c [0 :d :xss])))))

;; (run-tests)
