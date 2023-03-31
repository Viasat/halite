;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-find-concrete
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.op-find-concrete :as op-find-concrete]
            [schema.test])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-create-concrete-choices
  (is (= bom/no-value-bom (op-find-concrete/find-concrete-op {} {:$refines-to :ws/A$v1})))
  (is (= {:$refines-to :ws/A$v1
          :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1}}}
         (op-find-concrete/find-concrete-op {:ws/A$v1 {}
                                             :ws/B$v1 {:refines-to {:ws/A$v1 {:expr nil}}}}
                                            {:$refines-to :ws/A$v1})))
  (is (= {:$refines-to :ws/A$v1
          :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1}
                              :ws/C$v1 {:$instance-of :ws/C$v1}}}
         (op-find-concrete/find-concrete-op {:ws/A$v1 {}
                                             :ws/B$v1 {:refines-to {:ws/A$v1 {:expr nil}}}
                                             :ws/C$v1 {:refines-to {:ws/B$v1 {:expr nil}}}}
                                            {:$refines-to :ws/A$v1})))
  (is (= {:$refines-to :ws/A$v1
          :$concrete-choices {:ws/C$v1 {:$instance-of :ws/C$v1}}}
         (op-find-concrete/find-concrete-op {:ws/A$v1 {}
                                             :ws/B$v1 {:abstract? true
                                                       :refines-to {:ws/A$v1 {:expr nil}}}
                                             :ws/C$v1 {:refines-to {:ws/B$v1 {:expr nil}}}}
                                            {:$refines-to :ws/A$v1}))))

(deftest test-filter-concrete-choices
  (is (= bom/no-value-bom
         (op-find-concrete/find-concrete-op {} {:$refines-to :ws/A$v1
                                                :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1}}})))
  (is (= {:$refines-to :ws/A$v1
          :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1}}}
         (op-find-concrete/find-concrete-op {:ws/A$v1 {}
                                             :ws/B$v1 {:refines-to {:ws/A$v1 {:expr nil}}}}
                                            {:$refines-to :ws/A$v1
                                             :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1}}})))
  (is (= bom/no-value-bom
         (op-find-concrete/find-concrete-op {:ws/A$v1 {}
                                             :ws/B$v1 {:refines-to {:ws/A$v1 {:expr nil}}}}
                                            {:$refines-to :ws/A$v1
                                             :$concrete-choices {:ws/D$v1 {:$instance-of :ws/D$v1}}})))
  (is (= bom/no-value-bom
         (op-find-concrete/find-concrete-op {:ws/A$v1 {}
                                             :ws/B$v1 {:refines-to {:ws/A$v1 {:expr nil}}}}
                                            {:$refines-to :ws/A$v1
                                             :$value? true
                                             :$concrete-choices {:ws/D$v1 {:$instance-of :ws/D$v1}}})))
  (is (= bom/no-value-bom
         (op-find-concrete/find-concrete-op {:ws/A$v1 {}
                                             :ws/B$v1 {:abstract? true
                                                       :refines-to {:ws/A$v1 {:expr nil}}}
                                             :ws/C$v1 {:refines-to {:ws/B$v1 {:expr nil}}}}
                                            {:$refines-to :ws/A$v1
                                             :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1}}})))
  (is (= {:$refines-to :ws/A$v1
          :$concrete-choices {:ws/C$v1 {:$instance-of :ws/C$v1}}}
         (op-find-concrete/find-concrete-op {:ws/A$v1 {}
                                             :ws/B$v1 {:abstract? true
                                                       :refines-to {:ws/A$v1 {:expr nil}}}
                                             :ws/C$v1 {:refines-to {:ws/B$v1 {:expr nil}}}}
                                            {:$refines-to :ws/A$v1
                                             :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1}
                                                                 :ws/C$v1 {:$instance-of :ws/C$v1}}}))))

;; (run-tests)
