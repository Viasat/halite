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

(deftest test-find-specs-refining-to
  (is (= [:ws/F$v1 :ws/C$v1 :ws/A$v1]
         (spec/find-specs-refining-to {:ws/B$v1 {}
                                       :ws/A$v1 {:refines-to {:ws/B$v1 {:name "specId" :expr nil}}}
                                       :ws/C$v1 {:refines-to {:ws/A$v1 {:name "specId" :expr nil}}}
                                       :ws/D$v1 {:refines-to {:ws/E$v1 {:name "specId" :expr nil}}}
                                       :ws/F$v1 {:refines-to {:ws/C$v1 {:name "specId" :expr nil}}}}
                                      :ws/B$v1))))

(deftest test-find-specs-can-refine-to
  (is (= [:ws/E$v1]
         (spec/find-specs-can-refine-to {:ws/B$v1 {}
                                         :ws/A$v1 {:refines-to {:ws/B$v1 {:name "specId" :expr nil}}}
                                         :ws/C$v1 {:refines-to {:ws/A$v1 {:name "specId" :expr nil}}}
                                         :ws/D$v1 {:refines-to {:ws/E$v1 {:name "specId" :expr nil}}}
                                         :ws/F$v1 {:refines-to {:ws/C$v1 {:name "specId" :expr nil}}}}
                                        :ws/D$v1)))

  (is (= [:ws/C$v1 :ws/G$v1 :ws/A$v1 :ws/B$v1]
         (spec/find-specs-can-refine-to {:ws/B$v1 {}
                                         :ws/A$v1 {:refines-to {:ws/B$v1 {:name "specId" :expr nil}}}
                                         :ws/C$v1 {:refines-to {:ws/A$v1 {:name "specId" :expr nil}}}
                                         :ws/D$v1 {:refines-to {:ws/E$v1 {:name "specId" :expr nil}}}
                                         :ws/F$v1 {:refines-to {:ws/C$v1 {:name "specId" :expr nil}
                                                                :ws/G$v1 {:name "specId" :expr nil}}}
                                         :ws/G$v1 {}}
                                        :ws/F$v1))))

(deftest test-find-comprehensive-refinement-paths-from
  (is (= [[{:to-spec-id :ws/G$v1}]
          [{:to-spec-id :ws/C$v1} {:to-spec-id :ws/A$v1} {:to-spec-id :ws/B$v1}]]
         (spec/find-comprehensive-refinement-paths-from {:ws/B$v1 {}
                                                         :ws/A$v1 {:refines-to {:ws/B$v1 {:name "specId" :expr nil}}}
                                                         :ws/C$v1 {:refines-to {:ws/A$v1 {:name "specId" :expr nil}}}
                                                         :ws/D$v1 {:refines-to {:ws/E$v1 {:name "specId" :expr nil}}}
                                                         :ws/F$v1 {:refines-to {:ws/C$v1 {:name "specId" :expr nil}
                                                                                :ws/G$v1 {:name "specId" :expr nil}}}
                                                         :ws/G$v1 {}}
                                                        :ws/F$v1))))

;; (run-tests)
