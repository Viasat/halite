;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-add-types
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.op-add-types :as op-add-types]
            [schema.test])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-basic
  (is (= {:$instance-of :ws/A$v1
          :x {:$primitive-type :Integer
              :$value? true}}
         (op-add-types/add-types-op {:ws/A$v1 {:fields {:x [:Maybe :Integer]}}}
                                    {:$instance-of :ws/A$v1
                                     :x {:$value? true}})))
  (is (= {:$instance-of :ws/A$v1
          :x {:$value? false}}
         (op-add-types/add-types-op {:ws/A$v1 {:fields {:x [:Maybe :Integer]}}}
                                    {:$instance-of :ws/A$v1
                                     :x {:$value? false}})))

  (is (= {:$instance-of :ws/A$v1
          :x {:$primitive-type :Integer
              :$value? true}
          :y {:$value? true
              :$primitive-type [:Vec :String]}}
         (op-add-types/add-types-op {:ws/A$v1 {:fields {:x [:Maybe :Integer]
                                                        :y [:Vec :String]}}}
                                    {:$instance-of :ws/A$v1
                                     :x {:$value? true}
                                     :y {:$value? true}}))))

(deftest test-composition
  (is (= {:$instance-of :ws/A$v1
          :x {:$instance-of :ws/X$v1}}
         (op-add-types/add-types-op {:ws/A$v1 {:fields {:x [:Maybe [:Instance :ws/X$v1]]}}}
                                    {:$instance-of :ws/A$v1
                                     :x {:$instance-of :ws/X$v1}})))

  (is (= {:$instance-of :ws/A$v1
          :x {:$refines-to :ws/X$v1}}
         (op-add-types/add-types-op {:ws/A$v1 {:fields {:x [:Maybe [:Instance :* #{:ws/X$v1}]]}}}
                                    {:$instance-of :ws/A$v1
                                     :x {:$refines-to :ws/X$v1}}))))

(deftest test-refinements
  (is (= {:$refines-to :ws/A$v1
          :x {:$primitive-type :Integer
              :$value? true}
          :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                   :y {:$primitive-type :Integer
                                       :$ranges #{[1 10]}}}}}
         (op-add-types/add-types-op {:ws/A$v1 {:fields {:x :Integer}}
                                     :ws/B$v1 {:fields {:y :Integer}}}
                                    {:$refines-to :ws/A$v1
                                     :x {:$value? true}
                                     :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                                              :y {:$ranges #{[1 10]}}}}})))

  (is (= {:$refines-to :ws/A$v1
          :x {:$primitive-type :Integer
              :$value? true}
          :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1
                                        :y {:$primitive-type :Integer
                                            :$ranges #{[1 10]}}}}}
         (op-add-types/add-types-op {:ws/A$v1 {:fields {:x :Integer}}
                                     :ws/B$v1 {:fields {:y :Integer}}}
                                    {:$refines-to :ws/A$v1
                                     :x {:$value? true}
                                     :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1
                                                                   :y {:$ranges #{[1 10]}}}}}))))

;; (run-tests)
