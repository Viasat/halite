;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-canon
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.op-canon :as op-canon]
            [schema.core :as s]
            [schema.test])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-collapse-ranges-into-enum
  (is (= {:$enum #{3 5 20}}
         (#'op-canon/collapse-ranges-and-enum {:$enum #{3 5 20 30}
                                               :$ranges #{[1 10]
                                                          [20 30]}})))

  (is (= bom/no-value-bom
         (#'op-canon/collapse-ranges-and-enum {:$enum #{0}
                                               :$ranges #{[1 10]
                                                          [20 30]}})))

  (is (= {:$ranges #{[1 10]
                     [20 3000]}}
         (#'op-canon/collapse-ranges-and-enum {:$ranges #{[1 10]
                                                          [20 30]
                                                          [25 3000]}}))))

(deftest test-canon-op
  (is (= {:$instance-of :ws/A$v1
          :x {:$enum #{20 3 5}}}
         (op-canon/canon-op {:$instance-of :ws/A$v1
                             :x {:$enum #{3 5 20 30}
                                 :$ranges #{[1 10]
                                            [20 30]}}})))

  (is (= {:$instance-of :ws/A$v1
          :x bom/no-value-bom}
         (op-canon/canon-op {:$instance-of :ws/A$v1
                             :x {:$enum #{0}
                                 :$ranges #{[1 10]
                                            [20 30]}}})))

  (is (= bom/no-value-bom
         (op-canon/canon-op {:$instance-of :ws/A$v1
                             :$enum #{}
                             :x {:$enum #{0}
                                 :$ranges #{[1 10]
                                            [20 30]}}})))

  (is (= bom/no-value-bom
         (op-canon/canon-op {:$refines-to :ws/A$v1
                             :$enum #{}
                             :x {:$enum #{0}
                                 :$ranges #{[1 10]
                                            [20 30]}}})))

  (is (= {:$instance-of :ws/A$v1
          :x 5}
         (op-canon/canon-op {:$instance-of :ws/A$v1
                             :x {:$enum #{-1 5}
                                 :$ranges #{[1 10]
                                            [20 30]}}})))

  (is (= {:$refines-to :ws/A$v1
          :x 5}
         (op-canon/canon-op {:$refines-to :ws/A$v1
                             :x {:$enum #{-1 5}
                                 :$ranges #{[1 10]
                                            [20 30]}}})))

  (is (= {:$instance-of :ws/A$v1
          :x #d "5.5"}
         (op-canon/canon-op {:$instance-of :ws/A$v1
                             :x {:$enum #{#d "-1.0" #d "5.5"}
                                 :$ranges #{[#d "1.0" #d "10.2"]
                                            [#d "20.2" #d "30.3"]}}})))

  (is (= {:$instance-of :ws/A$v1
          :x 1}
         (op-canon/canon-op {:$instance-of :ws/A$v1
                             :x {:$ranges #{[1 2]}}})))

  (is (= {:$instance-of :ws/A$v1
          :x #d "1.1"}
         (op-canon/canon-op {:$instance-of :ws/A$v1
                             :x {:$ranges #{[#d "1.1" #d "1.2"]}}})))

  (is (= {:$instance-of :ws/A$v1
          :x [1 2 3]}
         (op-canon/canon-op {:$instance-of :ws/A$v1
                             :x {:$enum #{[1 2 3]}}})))

  (is (= {:$instance-of :ws/A$v1
          :x #{1 2 3}}
         (op-canon/canon-op {:$instance-of :ws/A$v1
                             :x {:$enum #{#{1 2 3}}}})))

  (is (= {:$instance-of :ws/A$v1
          :b {:$refines-to :ws/B$v1
              :x #{1 3 2}}}
         (op-canon/canon-op {:$instance-of :ws/A$v1
                             :b {:$refines-to :ws/B$v1
                                 :x {:$enum #{#{1 2 3}}}}}))))

;; (run-tests)
