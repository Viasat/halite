;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-find-refinements
  (:require [clojure.test :refer :all]
            [com.viasat.halite.op-find-refinements :as op-find-refinements]
            [schema.core :as s]
            [schema.test]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-extend-refinement-path
  (is (= {:$instance-of :ws/A$v1
          :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                   :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1}}}}}
         (#'op-find-refinements/extend-refinement-path {:$instance-of :ws/A$v1}
                                                       [{:to-spec-id :ws/B$v1} {:to-spec-id :ws/C$v1}])))
  (is (= {:$instance-of :ws/A$v1
          :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1}
                         :ws/B$v1 {:$instance-of :ws/B$v1
                                   :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1}}}}}
         (#'op-find-refinements/extend-refinement-path {:$instance-of :ws/A$v1
                                                        :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1}}}
                                                       [{:to-spec-id :ws/B$v1} {:to-spec-id :ws/C$v1}])))
  (is (= {:$instance-of :ws/A$v1
          :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                   :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1}}}}}
         (#'op-find-refinements/extend-refinement-path {:$instance-of :ws/A$v1
                                                        :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1}}}
                                                       [{:to-spec-id :ws/B$v1} {:to-spec-id :ws/C$v1}])))

  (is (= {:$instance-of :ws/A$v1
          :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                   :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1}}}}}
         (#'op-find-refinements/extend-refinement-path {:$instance-of :ws/A$v1
                                                        :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                                                                 :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1}}}}}
                                                       [{:to-spec-id :ws/B$v1} {:to-spec-id :ws/C$v1}]))))

;; (run-tests)
