;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.test-halite-type-check
  (:require [jibe.halite-type-check :as halite-type-check]
            [jibe.test-halite :as test-halite]
            [schema.core :as s]
            [clojure.test :refer :all]))

(set! *warn-on-reflection* true)

(deftest test-find-field-accesses
  (is (= #{{:spec-id :ws/D$v1
            :variable-name :xss}
           {:spec-id :ws2/B$v1
            :variable-name :d}}
         (halite-type-check/find-field-accesses
          (test-halite/map->TestSpecEnv {:specs {:ws/A$v1 {:spec-vars {:x "Integer"
                                                                       :y "Boolean"
                                                                       :c :ws2/B$v1}}
                                                 :ws2/B$v1 {:spec-vars {:d :ws/D$v1}}
                                                 :ws/C$v1 {:spec-vars {:xs ["Integer"]}}
                                                 :ws/D$v1 {:spec-vars {:xss [["Integer"]]}}}})
          {:spec-vars {:x "Integer"
                       :y "Boolean"
                       :c [:ws2/B$v1]}}
          '(get-in c [0 :d :xss])))))

;; (run-tests)
