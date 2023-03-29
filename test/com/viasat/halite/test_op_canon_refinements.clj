;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-canon-refinements
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.op-canon-refinements :as op-canon-refinements]
            [schema.core :as s]
            [schema.test])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-basic
  (is (= {:$instance-of :ws/A$v1}
         (op-canon-refinements/canon-refinements-op {}
                                                    {:$instance-of :ws/A$v1
                                                     :$refinements {}})))
  (is (= {:$instance-of :ws/A$v1
          :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                   :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1
                                                            :x {:$enum #{1 2}}}}}}}
         (op-canon-refinements/canon-refinements-op {:ws/A$v1 {:refines-to {:ws/B$v1 {:expr nil}}}
                                                     :ws/B$v1 {:refines-to {:ws/C$v1 {:expr nil}}}}
                                                    {:$instance-of :ws/A$v1
                                                     :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1
                                                                              :x {:$enum #{1 2}}}}})))

  (is (= {:$instance-of :ws/A$v1
          :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                   :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1
                                                            :x {:$enum #{1 2}}}
                                                  :ws/D$v1 {:$instance-of :ws/D$v1
                                                            :y 12}}}}}
         (op-canon-refinements/canon-refinements-op {:ws/A$v1 {:refines-to {:ws/B$v1 {:expr nil}}}
                                                     :ws/B$v1 {:refines-to {:ws/C$v1 {:expr nil}
                                                                            :ws/D$v1 {:expr nil}}}}
                                                    {:$instance-of :ws/A$v1
                                                     :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1
                                                                              :x {:$enum #{1 2}}}
                                                                    :ws/D$v1 {:$instance-of :ws/D$v1
                                                                              :y 12}}})))

  (is (= {:$instance-of :ws/A$v1
          :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                   :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1
                                                            :x {:$enum #{1 2}}}
                                                  :ws/D$v1 {:$instance-of :ws/D$v1
                                                            :y 12
                                                            :$refinements {:ws/E$v1 {:$instance-of :ws/E$v1
                                                                                     :z 100}}}}}}}
         (op-canon-refinements/canon-refinements-op {:ws/A$v1 {:refines-to {:ws/B$v1 {:expr nil}}}
                                                     :ws/B$v1 {:refines-to {:ws/C$v1 {:expr nil}
                                                                            :ws/D$v1 {:expr nil}}}
                                                     :ws/D$v1 {:refines-to {:ws/E$v1 {:expr nil}}}
                                                     :ws/E$v1 {:refines-to {:ws/F$v1 {:expr nil}}}
                                                     :ws/F$v1 {:refines-to {:ws/G$v1 {:expr nil}}}}
                                                    {:$instance-of :ws/A$v1
                                                     :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1
                                                                              :x {:$enum #{1 2}}}
                                                                    :ws/D$v1 {:$instance-of :ws/D$v1
                                                                              :y 12}
                                                                    :ws/E$v1 {:$instance-of :ws/E$v1
                                                                              :z 100}}})))

  (is (= {:$instance-of :ws/A$v1
          :$refinements
          {:ws/B$v1 {:$instance-of :ws/B$v1
                     :$refinements
                     {:ws/C$v1 {:$instance-of :ws/C$v1
                                :x {:$enum #{1 2}}}
                      :ws/D$v1 {:$instance-of :ws/D$v1
                                :y 12
                                :$refinements
                                {:ws/E$v1 {:$instance-of :ws/E$v1
                                           :z 100
                                           :$refinements
                                           {:ws/F$v1 {:$instance-of :ws/F$v1
                                                      :$refinements
                                                      {:ws/G$v1 {:$instance-of :ws/G$v1
                                                                 :p 99}}}}}}}}}}}
         (op-canon-refinements/canon-refinements-op {:ws/A$v1 {:refines-to {:ws/B$v1 {:expr nil}}}
                                                     :ws/B$v1 {:refines-to {:ws/C$v1 {:expr nil}
                                                                            :ws/D$v1 {:expr nil}}}
                                                     :ws/D$v1 {:refines-to {:ws/E$v1 {:expr nil}}}
                                                     :ws/E$v1 {:refines-to {:ws/F$v1 {:expr nil}}}
                                                     :ws/F$v1 {:refines-to {:ws/G$v1 {:expr nil}}}}
                                                    {:$instance-of :ws/A$v1
                                                     :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1
                                                                              :x {:$enum #{1 2}}}
                                                                    :ws/D$v1 {:$instance-of :ws/D$v1
                                                                              :y 12
                                                                              :$refinements {:ws/G$v1 {:$instance-of :ws/G$v1
                                                                                                       :p 99}}}
                                                                    :ws/E$v1 {:$instance-of :ws/E$v1
                                                                              :z 100}}})))

  (is (= {:$instance-of :ws/A$v1
          :$refinements
          {:ws/B$v1 {:$instance-of :ws/B$v1
                     :$refinements
                     {:ws/C$v1 {:$instance-of :ws/C$v1
                                :x {:$enum #{1 2}}}
                      :ws/D$v1 {:$instance-of :ws/D$v1
                                :y 12
                                :$refinements
                                {:ws/E$v1 {:$instance-of :ws/E$v1
                                           :z 100
                                           :$refinements
                                           {:ws/F$v1 {:$instance-of :ws/F$v1
                                                      :$refinements
                                                      {:ws/G$v1 {:$instance-of :ws/G$v1
                                                                 ;; :p 99
                                                                 :q 100}}}}}}}}}}}
         (op-canon-refinements/canon-refinements-op {:ws/A$v1 {:refines-to {:ws/B$v1 {:expr nil}}}
                                                     :ws/B$v1 {:refines-to {:ws/C$v1 {:expr nil}
                                                                            :ws/D$v1 {:expr nil}}}
                                                     :ws/D$v1 {:refines-to {:ws/E$v1 {:expr nil}}}
                                                     :ws/E$v1 {:refines-to {:ws/F$v1 {:expr nil}}}
                                                     :ws/F$v1 {:refines-to {:ws/G$v1 {:expr nil}}}}
                                                    {:$instance-of :ws/A$v1
                                                     :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1
                                                                              :x {:$enum #{1 2}}}
                                                                    :ws/D$v1 {:$instance-of :ws/D$v1
                                                                              :y 12
                                                                              :$refinements {:ws/G$v1 {:$instance-of :ws/G$v1
                                                                                                       :p 99}}}
                                                                    :ws/E$v1 {:$instance-of :ws/E$v1
                                                                              :z 100
                                                                              :$refinements {:ws/G$v1 {:$instance-of :ws/G$v1
                                                                                                       :q 100}}}}})))

  (is (thrown-with-msg? ExceptionInfo #"does not match schema"
                        (op-canon-refinements/canon-refinements-op {}
                                                                   {:$refines-to :ws/A$v1
                                                                    :$refinements {:ws/B$v1 {:$refines-to :ws/B$v1
                                                                                             :b 100}}})))

  (is (= {:$refines-to :ws/A$v1
          :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                   :b 100}}}
         (op-canon-refinements/canon-refinements-op {}
                                                    {:$refines-to :ws/A$v1
                                                     :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                                                              :b 100}}})))

  (is (thrown-with-msg? ExceptionInfo #"no refinement path"
                        (op-canon-refinements/canon-refinements-op {:ws/A$v1 {}
                                                                    :ws/Z$v1 {}}
                                                                   {:$instance-of :ws/A$v1
                                                                    :$refinements {:ws/Z$v1 {:$instance-of :ws/Z$v1}}})))

  (is (= {:$instance-of :ws/A$v1
          :$refinements {:ws/B$v1
                         {:$refinements {:ws/C$v1 {:$instance-of :ws/C$v1
                                                   :$value? false}}
                          :$instance-of :ws/B$v1}}}
         (op-canon-refinements/canon-refinements-op {:ws/A$v1 {:refines-to {:ws/B$v1 {:expr nil}}}
                                                     :ws/B$v1 {:refines-to {:ws/C$v1 {:expr nil}}}}
                                                    {:$instance-of :ws/A$v1
                                                     :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1
                                                                              :$value? false}}})))

  ;; what if one refinement constraint says that a refinement does not exist, but another constraint assumes the refinement does exist?
  (is (= {:$instance-of :ws/A$v1
          :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                   :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1
                                                            :$value? false
                                                            :$refinements {:ws/D$v1 {:$instance-of :ws/D$v1
                                                                                     :x 100}}}}}}}
         (op-canon-refinements/canon-refinements-op {:ws/A$v1 {:refines-to {:ws/B$v1 {:expr nil}}}
                                                     :ws/B$v1 {:refines-to {:ws/C$v1 {:expr nil}}}
                                                     :ws/C$v1 {:refines-to {:ws/D$v1 {:expr nil}}}}
                                                    {:$instance-of :ws/A$v1
                                                     :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1
                                                                              :$value? false}
                                                                    :ws/D$v1 {:$instance-of :ws/D$v1
                                                                              :x 100}}})))

  (is (= {:$instance-of :ws/A$v1
          :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                   :$accessed? true
                                   :b 100}}}
         (op-canon-refinements/canon-refinements-op {:ws/A$v1 {:refines-to {:ws/B$v1 {:expr nil
                                                                                      :extrinsic? true}}}}
                                                    {:$instance-of :ws/A$v1
                                                     :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                                                              :b 100}}})))

  (is (= {:$instance-of :ws/A$v1
          :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                   :$accessed? true
                                   :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1
                                                            :$refinements {:ws/D$v1 {:$instance-of :ws/D$v1
                                                                                     :$accessed? true
                                                                                     :$refinements {:ws/E$v1
                                                                                                    {:$instance-of :ws/E$v1
                                                                                                     :b 100}}}}}}}}}
         (op-canon-refinements/canon-refinements-op {:ws/A$v1 {:refines-to {:ws/B$v1 {:expr nil
                                                                                      :extrinsic? true}}}
                                                     :ws/B$v1 {:refines-to {:ws/C$v1 {:expr nil}}}
                                                     :ws/C$v1 {:refines-to {:ws/D$v1 {:expr nil
                                                                                      :extrinsic? true}}}
                                                     :ws/D$v1 {:refines-to {:ws/E$v1 {:expr nil}}}}
                                                    {:$instance-of :ws/A$v1
                                                     :$refinements {:ws/E$v1 {:$instance-of :ws/E$v1
                                                                              :b 100}}}))))

;; (run-tests)
