;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-bom-analysis
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-analysis :as bom-analysis]
            [schema.test])
  (:import [clojure.lang ExceptionInfo]))

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
  (is (= bom/impossible-bom (bom-analysis/merge-boms 1 2)))
  (is (= bom/impossible-bom (bom-analysis/merge-boms 1 bom/impossible-bom)))
  (is (= bom/impossible-bom (bom-analysis/merge-boms 1 "hi")))
  (is (= bom/impossible-bom (bom-analysis/merge-boms [1] #{"hi"})))

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
                                                                           :$value? false}))))

(deftest test-merge-bom-instances
  (is (= bom/impossible-bom (bom-analysis/merge-boms [1] {:$type :ws/A$v1 :x 1})))
  (is (= bom/impossible-bom (bom-analysis/merge-boms {:$type :ws/A$v1 :x 1} {:$type :ws/B$v1 :x 1})))
  (is (= {:$type :ws/A$v1 :x 1} (bom-analysis/merge-boms {:$type :ws/A$v1 :x 1} {:$type :ws/A$v1 :x 1}))))

(deftest test-merge-concrete-instances
  (is (= {:$type :ws/A$v1 :x 1} (bom-analysis/merge-boms {:$type :ws/A$v1 :x 1} {:$instance-of :ws/A$v1})))
  (is (= {:$type :ws/A$v1
          :$value? true
          :x {:$impossible? true}}
         (bom-analysis/merge-boms {:$type :ws/A$v1 :x 1} {:$instance-of :ws/A$v1
                                                          :$value? true
                                                          :x 2})))
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
          :x {:$impossible? true}} (bom-analysis/merge-boms {:$type :ws/A$v1 :x 1} {:$instance-of :ws/A$v1 :x 2})))
  (is (= {:$type :ws/A$v1
          :x {:$impossible? true}} (bom-analysis/merge-boms {:$type :ws/A$v1 :x 1} {:$instance-of :ws/A$v1 :x 2})))

  (is (= {:$instance-of :ws/A$v1 :x 2 :y 3} (bom-analysis/merge-boms {:$instance-of :ws/A$v1 :x 2} {:$instance-of :ws/A$v1 :y 3})))
  (is (= {:$type :ws/A$v1
          :x 2}
         (bom-analysis/merge-boms {:$instance-of :ws/A$v1
                                   :$enum #{{:$type :ws/A$v1
                                             :x 1}
                                            {:$type :ws/A$v1
                                             :x 2}}}
                                  {:$instance-of :ws/A$v1
                                   :$enum #{{:$type :ws/A$v1
                                             :x 2}
                                            {:$type :ws/A$v1
                                             :x 3}}})))
  (is (= {:$instance-of :ws/A$v1
          :x 2
          :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1}}} (bom-analysis/merge-boms {:$instance-of :ws/A$v1
                                                                                       :$enum #{{:$type :ws/A$v1
                                                                                                 :x 1}
                                                                                                {:$type :ws/A$v1
                                                                                                 :x 2}}}
                                                                                      {:$instance-of :ws/A$v1
                                                                                       :$enum #{{:$type :ws/A$v1
                                                                                                 :x 2}
                                                                                                {:$type :ws/A$v1
                                                                                                 :x 3}}
                                                                                       :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1}}}))))

(deftest test-merge-abstract-instances
  (is (= {:$refines-to :ws/A$v1} (bom-analysis/merge-boms {:$refines-to :ws/A$v1} {:$refines-to :ws/A$v1})))
  (is (thrown-with-msg? ExceptionInfo #"not supported"
                        (bom-analysis/merge-boms {:$refines-to :ws/A$v1} {:$refines-to :ws/B$v1})))

  (is (= {:$refines-to :ws/A$v1
          :x 1
          :y 2}
         (bom-analysis/merge-boms {:$refines-to :ws/A$v1
                                   :x 1}
                                  {:$refines-to :ws/A$v1
                                   :y 2})))

  (is (= {:$refines-to :ws/A$v1
          :x {:$impossible? true}}
         (bom-analysis/merge-boms {:$refines-to :ws/A$v1
                                   :x 1}
                                  {:$refines-to :ws/A$v1
                                   :x 2})))

  (is (= bom/no-value-bom
         (bom-analysis/merge-boms {:$refines-to :ws/A$v1
                                   :$concrete-choices {:ws/X$v1 {:$instance-of :ws/X$v1}}}
                                  {:$refines-to :ws/A$v1
                                   :$concrete-choices {:ws/X$v1 {:$instance-of :ws/Y$v1}}})))

  (is (= bom/impossible-bom
         (bom-analysis/merge-boms {:$refines-to :ws/A$v1
                                   :$value? true
                                   :$concrete-choices {:ws/X$v1 {:$instance-of :ws/X$v1}}}
                                  {:$refines-to :ws/A$v1
                                   :$concrete-choices {:ws/X$v1 {:$instance-of :ws/Y$v1}}}))))

;; (run-tests)
