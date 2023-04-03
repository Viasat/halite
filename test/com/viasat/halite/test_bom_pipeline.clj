;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-bom-pipeline
  (:require [clojure.test :refer :all]
            [com.viasat.halite.op-canon :as op-canon]
            [com.viasat.halite.op-canon-refinements :as op-canon-refinements]
            [com.viasat.halite.op-contradictions :as op-contradictions]
            [com.viasat.halite.op-find-concrete :as op-find-concrete]
            [com.viasat.halite.op-mandatory :as op-mandatory]
            [com.viasat.halite.op-push-down-to-concrete :as op-push-down-to-concrete]
            [com.viasat.halite.op-syntax-check :as op-syntax-check]
            [com.viasat.halite.op-type-check :as op-type-check]
            [schema.test])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(defn pipeline [spec-env bom]
  (->> bom
       (op-syntax-check/syntax-check-op spec-env)
       (op-type-check/type-check-op spec-env)
       op-canon/canon-op
       (op-mandatory/mandatory-op spec-env)

       (op-find-concrete/find-concrete-op spec-env)
       op-push-down-to-concrete/push-down-to-concrete-op
       (op-canon-refinements/canon-refinements-op spec-env)

       op-contradictions/bubble-up-contradictions))

(deftest test-pipeline
  (is (= {:$instance-of :ws/A$v1
          :i 1
          :x {:$refines-to :ws/X$v1
              :$concrete-choices {:ws/Y$v1 {:$instance-of :ws/Y$v1
                                            :$refinements {:ws/X$v1 {:x2 2
                                                                     :$instance-of :ws/X$v1}}}
                                  :ws/ZZ$v1 {:$instance-of :ws/ZZ$v1
                                             :$refinements {:ws/Z$v1 {:$instance-of :ws/Z$v1
                                                                      :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1
                                                                                               :x2 2}}}}}}}}
         (pipeline {:ws/A$v1 {:fields {:i :Integer
                                       :x [:Maybe [:Instance :* #{:ws/X$v1}]]}}
                    :ws/X$v1 {:fields {:x2 :Integer}}
                    :ws/Y$v1 {:refines-to {:ws/X$v1 {:expr nil}}}
                    :ws/Z$v1 {:abstract? true
                              :refines-to {:ws/X$v1 {:expr nil}}}
                    :ws/ZZ$v1 {:refines-to {:ws/Z$v1 {:expr nil}}}}
                   {:$instance-of :ws/A$v1
                    :i 1
                    :x {:$refines-to :ws/X$v1
                        :x2 2}})))

  (is (= {:$instance-of :ws/A$v1
          :i 1
          :x {:$refines-to :ws/X$v1
              :$value? true
              :$concrete-choices
              {:ws/Y$v1 {:$instance-of :ws/Y$v1
                         :$refinements {:ws/X$v1
                                        {:$instance-of :ws/X$v1
                                         :x2 2
                                         :$value? true}}}
               :ws/ZZ$v1 {:$instance-of :ws/ZZ$v1
                          :$refinements {:ws/Z$v1 {:$instance-of :ws/Z$v1
                                                   :$value? true
                                                   :$refinements {:ws/X$v1
                                                                  {:$instance-of :ws/X$v1
                                                                   :x2 2
                                                                   :$value? true}}}}}
               :ws/ZZZ$v1 {:$instance-of :ws/ZZZ$v1
                           :$refinements {:ws/ZZ$v1 {:$instance-of :ws/ZZ$v1
                                                     :$value? true
                                                     :$refinements
                                                     {:ws/Z$v1 {:$instance-of :ws/Z$v1
                                                                :$value? true
                                                                :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1
                                                                                         :x2 2
                                                                                         :$value? true}}}}}}}}}}
         (pipeline {:ws/A$v1 {:fields {:i :Integer
                                       :x [:Instance :* #{:ws/X$v1}]}}
                    :ws/X$v1 {:fields {:x2 :Integer}}
                    :ws/Y$v1 {:refines-to {:ws/X$v1 {:expr nil}}}
                    :ws/Z$v1 {:abstract? true
                              :refines-to {:ws/X$v1 {:expr nil}}}
                    :ws/ZZ$v1 {:refines-to {:ws/Z$v1 {:expr nil}}}
                    :ws/ZZZ$v1 {:refines-to {:ws/ZZ$v1 {:expr nil}}}}
                   {:$instance-of :ws/A$v1
                    :i 1
                    :x {:$refines-to :ws/X$v1
                        :x2 2}})))

  (is (= {:$instance-of :ws/A$v1
          :i 1
          :x {:$value? false}}
         (pipeline {:ws/A$v1 {:fields {:i :Integer
                                       :x [:Maybe [:Instance :* #{:ws/X$v1}]]}}
                    :ws/X$v1 {:fields {:x2 :Integer}}
                    :ws/Y$v1 {:refines-to {:ws/X$v1 {:expr nil}}}
                    :ws/Z$v1 {:abstract? true
                              :refines-to {:ws/X$v1 {:expr nil}}}
                    :ws/ZZ$v1 {:refines-to {:ws/Z$v1 {:expr nil}}}}
                   {:$instance-of :ws/A$v1
                    :i 1
                    :x {:$refines-to :ws/X$v1
                        :$value? false}}))))

;; (time (run-tests))
