;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-bom-analysis
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-analysis :as bom-analysis]
            [schema.test]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-collapse-ranges-into-enum
  (is (= {:$enum #{3 5 20}}
         (#'bom-analysis/collapse-ranges-and-enum {:$enum #{3 5 20 30}
                                                   :$ranges #{[1 10]
                                                              [20 30]}})))

  (is (= bom/no-value-bom
         (#'bom-analysis/collapse-ranges-and-enum {:$enum #{0}
                                                   :$ranges #{[1 10]
                                                              [20 30]}})))

  (is (= {:$ranges #{[1 10]
                     [20 3000]}}
         (#'bom-analysis/collapse-ranges-and-enum {:$ranges #{[1 10]
                                                              [20 30]
                                                              [25 3000]}}))))

(deftest test-merge-boms
  (is (= bom/no-value-bom (bom-analysis/merge-boms 1 2)))
  (is (= bom/no-value-bom (bom-analysis/merge-boms 1 "hi")))
  (is (= bom/no-value-bom (bom-analysis/merge-boms [1] #{"hi"})))
  (is (= bom/no-value-bom (bom-analysis/merge-boms [1] {:$type :ws/A$v1 :x 1})))
  (is (= 2 (bom-analysis/merge-boms {:$enum #{1 2}} {:$enum #{2 3}})))
  (is (= {:$enum #{3 2}} (bom-analysis/merge-boms {:$enum #{1 2 3}} {:$enum #{2 3 4}})))
  (is (= bom/no-value-bom (bom-analysis/merge-boms {:$enum #{1 2 3}} {:$enum #{2 3 4}
                                                                      :$value? false})))
  (is (= {:$enum #{20 1}} (bom-analysis/merge-boms {:$enum #{1 20 30}} {:$ranges #{[0 25]}})))
  (is (= {:$ranges #{[5 2500]}} (bom-analysis/merge-boms {:$ranges #{[5 3000]}} {:$ranges #{[0 2500]}})))
  (is (= {:$ranges #{[5 2500]
                     [2600 2700]}} (bom-analysis/merge-boms {:$ranges #{[5 3000]}} {:$ranges #{[0 2500]
                                                                                               [2600 2700]}})))
  (is (= bom/no-value-bom (bom-analysis/merge-boms {:$ranges #{[5 3000]}} {:$ranges #{[0 2500]
                                                                                      [2600 2700]}
                                                                           :$value? false})))

  (is (= bom/no-value-bom (bom-analysis/merge-boms {:$type :ws/A$v1 :x 1} {:$type :ws/B$v1 :x 1})))
  (is (= {:$type :ws/A$v1 :x 1} (bom-analysis/merge-boms {:$type :ws/A$v1 :x 1} {:$type :ws/A$v1 :x 1})))
  (is (= {:$type :ws/A$v1 :x 1} (bom-analysis/merge-boms {:$type :ws/A$v1 :x 1} {:$instance-of :ws/A$v1})))
  (is (= {:$instance-of :ws/A$v1
          :x 1
          :$accessed? true} (bom-analysis/merge-boms {:$type :ws/A$v1 :x 1}
                                                     {:$instance-of :ws/A$v1
                                                      :$accessed? true})))
  (is (= {:$instance-of :ws/A$v1
          :x 1
          :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1}}} (bom-analysis/merge-boms {:$type :ws/A$v1 :x 1}
                                                                                      {:$instance-of :ws/A$v1
                                                                                       :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1}}})))
  (is (= {:$type :ws/A$v1
          :x {:$value? false}} (bom-analysis/merge-boms {:$type :ws/A$v1 :x 1} {:$instance-of :ws/A$v1 :x 2})))
  (is (= {:$type :ws/A$v1
          :x {:$value? false}} (bom-analysis/merge-boms {:$type :ws/A$v1 :x 1} {:$instance-of :ws/A$v1 :x 2}))))

;; (run-tests)
