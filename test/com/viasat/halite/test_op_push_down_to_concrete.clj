;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-push-down-to-concrete
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.op-push-down-to-concrete :as op-push-down-to-concrete]
            [schema.test])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-basic
  (is (= {:$instance-of :ws/B$v1
          :$refinements {:ws/A$v1 {:$instance-of :ws/A$v1}}}
         (op-push-down-to-concrete/push-down-to-concrete-op {:$refines-to :ws/A$v1
                                                             :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1}}})))

  (is (= {:$refines-to :ws/A$v1
          :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1
                                        :$refinements {:ws/A$v1 {:$instance-of :ws/A$v1}}}
                              :ws/C$v1 {:$instance-of :ws/C$v1
                                        :$refinements {:ws/A$v1 {:$instance-of :ws/A$v1}}}}}
         (op-push-down-to-concrete/push-down-to-concrete-op {:$refines-to :ws/A$v1
                                                             :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1}
                                                                                 :ws/C$v1 {:$instance-of :ws/C$v1}}})))

  (is (= {:$refines-to :ws/A$v1
          :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1
                                        :$refinements {:ws/A$v1 {:$instance-of :ws/A$v1
                                                                 :$value? true}}}
                              :ws/C$v1 {:$instance-of :ws/C$v1
                                        :$refinements {:ws/A$v1 {:$instance-of :ws/A$v1
                                                                 :$value? true}}}}}
         (op-push-down-to-concrete/push-down-to-concrete-op {:$refines-to :ws/A$v1
                                                             :$value? true
                                                             :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1}
                                                                                 :ws/C$v1 {:$instance-of :ws/C$v1}}}))))

(deftest test-combine-refinements
  (is (= {:$instance-of :ws/B$v1
          :$refinements {:ws/A$v1 {:$instance-of :ws/A$v1}
                         :ws/X$v1 {:$instance-of :ws/X$v1
                                   :x 1}}}
         (op-push-down-to-concrete/push-down-to-concrete-op {:$refines-to :ws/A$v1
                                                             :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1
                                                                                      :x 1}}
                                                             :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1
                                                                                           :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1
                                                                                                                    :x 1}}}}})))

  (is (= {:$instance-of :ws/B$v1
          :$refinements {:ws/A$v1 {:$instance-of :ws/A$v1}
                         :ws/X$v1 {:$instance-of :ws/X$v1
                                   :x bom/contradiction-bom}}}
         (op-push-down-to-concrete/push-down-to-concrete-op {:$refines-to :ws/A$v1
                                                             :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1
                                                                                      :x 1}}
                                                             :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1
                                                                                           :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1
                                                                                                                    :x 2}}}}})))

  (is (= {:$instance-of :ws/B$v1
          :$refinements {:ws/A$v1 {:$instance-of :ws/A$v1}
                         :ws/X$v1 {:$instance-of :ws/X$v1
                                   :x 1
                                   :y 2}}}
         (op-push-down-to-concrete/push-down-to-concrete-op {:$refines-to :ws/A$v1
                                                             :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1
                                                                                      :x 1}}
                                                             :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1
                                                                                           :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1
                                                                                                                    :y 2}}}}})))

  (is (= {:$refines-to :ws/A$v1
          :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1
                                        :$refinements {:ws/A$v1 {:$instance-of :ws/A$v1
                                                                 :$value? true}
                                                       :ws/X$v1 {:$instance-of :ws/X$v1
                                                                 :x 1}}}
                              :ws/C$v1 {:$instance-of :ws/C$v1
                                        :$refinements {:ws/A$v1 {:$instance-of :ws/A$v1
                                                                 :$value? true}
                                                       :ws/X$v1 {:$instance-of :ws/X$v1
                                                                 :x 1}}}}}
         (op-push-down-to-concrete/push-down-to-concrete-op {:$refines-to :ws/A$v1
                                                             :$value? true
                                                             :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1
                                                                                      :x 1}}
                                                             :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1}
                                                                                 :ws/C$v1 {:$instance-of :ws/C$v1}}})))

  (is (= {:$refines-to :ws/A$v1
          :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1
                                        :$refinements {:ws/A$v1 {:$instance-of :ws/A$v1
                                                                 :$value? true}
                                                       :ws/X$v1 {:$instance-of :ws/X$v1
                                                                 :x {:$contradiction? true}}}}
                              :ws/C$v1 {:$instance-of :ws/C$v1
                                        :$refinements {:ws/A$v1 {:$instance-of :ws/A$v1
                                                                 :$value? true}
                                                       :ws/X$v1 {:$instance-of :ws/X$v1
                                                                 :x 1}}}}}
         (op-push-down-to-concrete/push-down-to-concrete-op {:$refines-to :ws/A$v1
                                                             :$value? true
                                                             :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1
                                                                                      :x 1}}
                                                             :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1
                                                                                           :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1
                                                                                                                    :x 2}}}
                                                                                 :ws/C$v1 {:$instance-of :ws/C$v1}}}))))

(deftest test-nested
  (let [bom {:$refines-to :ws/A$v1
             :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1
                                      :x 1}}
             :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1
                                           :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1
                                                                    :x 1}}}}}
        expected {:$instance-of :ws/B$v1
                  :$refinements {:ws/A$v1 {:$instance-of :ws/A$v1}
                                 :ws/X$v1 {:$instance-of :ws/X$v1
                                           :x 1}}}]
    (is (= {:$instance-of :ws/P$v1
            :a expected}
           (op-push-down-to-concrete/push-down-to-concrete-op {:$instance-of :ws/P$v1
                                                               :a bom})))
    (is (= {:$instance-of :ws/P$v1
            :$refinements {:ws/Q$v1 {:$instance-of :ws/Q$v1
                                     :a expected}}}
           (op-push-down-to-concrete/push-down-to-concrete-op {:$instance-of :ws/P$v1
                                                               :$refinements {:ws/Q$v1 {:$instance-of :ws/Q$v1
                                                                                        :a bom}}})))))

;; (run-tests)
