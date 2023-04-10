;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-canon-refinements
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.op-canon-refinements :as op-canon-refinements]
            [schema.test])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-lift-refinements-op
  (let [bom {:$instance-of :ws/A$v1
             :$refinements {:ws/E$v1 {:$instance-of :ws/E$v1
                                      :b 100}}}]
    (is (= bom
           (op-canon-refinements/lift-refinements-op bom))))

  (is (= {:$instance-of :ws/A$v1
          :$refinements {:ws/E$v1 {:$instance-of :ws/E$v1
                                   :b 100}
                         :ws/F$v1 {:$instance-of :ws/F$v1
                                   :x 99}}}
         (op-canon-refinements/lift-refinements-op {:$instance-of :ws/A$v1
                                                    :$refinements {:ws/E$v1 {:$instance-of :ws/E$v1
                                                                             :$refinements {:ws/F$v1 {:$instance-of :ws/F$v1
                                                                                                      :x 99}}
                                                                             :b 100}}})))

  (is (= {:$instance-of :ws/A$v1
          :$refinements {:ws/E$v1 {:$instance-of :ws/E$v1
                                   :b 100}
                         :ws/F$v1 {:$instance-of :ws/F$v1
                                   :x 99
                                   :y 3}
                         :ws/G$v1 {:$instance-of :ws/G$v1
                                   :q 2}}}
         (op-canon-refinements/lift-refinements-op {:$instance-of :ws/A$v1
                                                    :$refinements {:ws/E$v1 {:$instance-of :ws/E$v1
                                                                             :$refinements {:ws/F$v1 {:$instance-of :ws/F$v1
                                                                                                      :x 99}}
                                                                             :b 100}
                                                                   :ws/F$v1 {:$instance-of :ws/F$v1
                                                                             :$refinements {:ws/G$v1 {:$instance-of :ws/G$v1
                                                                                                      :q 2}}
                                                                             :y 3}}})))

  (let [bom {:$instance-of :ws/A$v1
             :$refinements {:ws/E$v1 {:$instance-of :ws/E$v1
                                      :$refinements {:ws/F$v1 {:$instance-of :ws/F$v1
                                                               :x 99}}
                                      :b 100}
                            :ws/F$v1 {:$instance-of :ws/F$v1
                                      :$refinements {:ws/G$v1 {:$instance-of :ws/G$v1
                                                               :q 2}}
                                      :x 98}}}
        expected {:$instance-of :ws/A$v1
                  :$refinements {:ws/E$v1 {:$instance-of :ws/E$v1
                                           :b 100}
                                 :ws/F$v1 {:$instance-of :ws/F$v1
                                           :x {:$contradiction? true}}
                                 :ws/G$v1 {:$instance-of :ws/G$v1
                                           :q 2}}}]
    ;; notice that one of the fields becomes impossible because the values are conflicting
    (is (= expected
           (op-canon-refinements/lift-refinements-op bom)))

    ;; now test the same thing embedded in concrete choices of an abstract spec
    (is (= {:$refines-to :ws/X$v1
            :$concrete-choices {:ws/A$v1 expected}}
           (op-canon-refinements/lift-refinements-op {:$refines-to :ws/X$v1
                                                      :$concrete-choices {:ws/A$v1 bom}})))

    ;; now test the same thing embedded in refinements of an abstract spec
    ;; unchanged, to be dealt with as part of pushing the refinements down into concrete choices
    (is (= {:$refines-to :ws/X$v1
            :$refinements {:ws/A$v1 bom}}
           (op-canon-refinements/lift-refinements-op {:$refines-to :ws/X$v1
                                                      :$refinements {:ws/A$v1 bom}})))

    ;; now test the same thing embedded in another concrete spec
    (is (= {:$instance-of :ws/X$v1
            :$refinements (-> expected
                              :$refinements
                              (assoc :ws/A$v1 {:$instance-of :ws/A$v1}))}
           (op-canon-refinements/lift-refinements-op {:$instance-of :ws/X$v1
                                                      :$refinements {:ws/A$v1 bom}})))))

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

  (is (= {:$instance-of :ws/A$v1}
         (op-canon-refinements/canon-refinements-op {:ws/A$v1 {:refines-to {:ws/B$v1 {:expr nil}}}
                                                     :ws/B$v1 {:refines-to {:ws/C$v1 {:expr nil}}}}
                                                    {:$instance-of :ws/A$v1})))

  (is (= {:$instance-of :ws/A$v1}
         (op-canon-refinements/canon-refinements-op {:ws/A$v1 {:refines-to {:ws/B$v1 {:expr nil}}}
                                                     :ws/B$v1 {:refines-to {:ws/C$v1 {:expr nil}}}}
                                                    {:$instance-of :ws/A$v1
                                                     :$refinements {}})))

  (is (= {:$instance-of :ws/A$v1
          :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                   :$value? true
                                   :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1
                                                            :$value? true
                                                            :x {:$enum #{1 2}}}}}}}
         (op-canon-refinements/canon-refinements-op {:ws/A$v1 {:refines-to {:ws/B$v1 {:expr nil}}}
                                                     :ws/B$v1 {:refines-to {:ws/C$v1 {:expr nil}}}}
                                                    {:$instance-of :ws/A$v1
                                                     :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1
                                                                              :$value? true
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
                                   :$value? true
                                   :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1
                                                            :$value? false}
                                                  :ws/D$v1 {:$instance-of :ws/D$v1
                                                            :$value? true}}}}}
         (op-canon-refinements/canon-refinements-op {:ws/A$v1 {:refines-to {:ws/B$v1 {:expr nil}}}
                                                     :ws/B$v1 {:refines-to {:ws/C$v1 {:expr nil}
                                                                            :ws/D$v1 {:expr nil}}}}
                                                    {:$instance-of :ws/A$v1
                                                     :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1
                                                                              :$value? false}
                                                                    :ws/D$v1 {:$instance-of :ws/D$v1
                                                                              :$value? true}}})))

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
          :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                   :$value? true
                                   :$refinements {:ws/D$v1 bom/contradiction-bom}}}}
         (op-canon-refinements/canon-refinements-op {:ws/A$v1 {:refines-to {:ws/B$v1 {:expr nil}}}
                                                     :ws/B$v1 {:refines-to {:ws/D$v1 {:expr nil}}}
                                                     :ws/D$v1 {:refines-to {:ws/E$v1 {:expr nil}}}
                                                     :ws/E$v1 {}}
                                                    {:$instance-of :ws/A$v1
                                                     :$refinements {:ws/D$v1 {:$instance-of :ws/D$v1
                                                                              :$value? false}
                                                                    :ws/E$v1 {:$instance-of :ws/E$v1
                                                                              :$value? true}}})))

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
                                                                 :p 99
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
         (op-canon-refinements/canon-refinements-op {:ws/A$v1 {}
                                                     :ws/B$v1 {}
                                                     :ws/C$v1 {:refines-to {:ws/A$v1 {:expr nil}
                                                                            :ws/B$v1 {:expr nil}}}}
                                                    {:$refines-to :ws/A$v1
                                                     :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                                                              :b 100}}})))
  (is (= {:$refines-to :ws/X$v1
          :$concrete-choices {:ws/Y$v1 {:$instance-of :ws/Y$v1
                                        :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                                                 :$refinements {:ws/C$v1
                                                                                {:$instance-of :ws/C$v1
                                                                                 :$value? false}}}}}}}
         (op-canon-refinements/canon-refinements-op {:ws/B$v1 {:refines-to {:ws/C$v1 {:expr nil}}}
                                                     :ws/C$v1 {:refines-to {:ws/A$v1 {:expr nil}
                                                                            :ws/B$v1 {:expr nil}}}
                                                     :ws/Y$v1 {:refines-to {:ws/X$v1 {:expr nil}
                                                                            :ws/B$v1 {:expr nil}}}}
                                                    {:$refines-to :ws/X$v1
                                                     :$concrete-choices {:ws/Y$v1 {:$instance-of :ws/Y$v1
                                                                                   :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1
                                                                                                            :$value? false}}}}})))

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
                                   :$extrinsic? true
                                   :b 100}}}
         (op-canon-refinements/canon-refinements-op {:ws/A$v1 {:refines-to {:ws/B$v1 {:expr nil
                                                                                      :extrinsic? true}}}}
                                                    {:$instance-of :ws/A$v1
                                                     :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                                                              :b 100}}})))

  (is (= {:$instance-of :ws/A$v1
          :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                   :$extrinsic? true
                                   :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1
                                                            :$refinements {:ws/D$v1 {:$instance-of :ws/D$v1
                                                                                     :$extrinsic? true
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

(deftest test-find-required-values-in-refinements
  (is (= #{}
         (op-canon-refinements/find-required-values-in-refinements
          {:$instance-of :ws/A$v1
           :$refinements {:ws/E$v1 {:$instance-of :ws/E$v1
                                    :b 100}}})))
  (is (= #{[:ws/E$v1 :ws/A$v1]}
         (op-canon-refinements/find-required-values-in-refinements
          {:$instance-of :ws/A$v1
           :$refinements {:ws/E$v1 {:$instance-of :ws/E$v1
                                    :$value? true
                                    :b 100}}})))

  (is (= #{[:ws/A$v1]}
         (op-canon-refinements/find-required-values-in-refinements
          {:$instance-of :ws/A$v1
           :$value? true
           :$refinements {:ws/E$v1 {:$instance-of :ws/E$v1
                                    :b 100}}})))

  (is (= #{[:ws/E$v1]
           [:ws/A$v1]}
         (op-canon-refinements/find-required-values-in-refinements
          {:$instance-of :ws/A$v1
           :$value? true
           :$refinements {:ws/E$v1 {:$instance-of :ws/E$v1
                                    :$value? true
                                    :b 100}}})))
  (is (= #{[:ws/E$v1 :ws/C$v1]
           [:ws/A$v1]}
         (op-canon-refinements/find-required-values-in-refinements
          {:$instance-of :ws/A$v1
           :$value? true
           :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1
                                    :$refinements {:ws/E$v1 {:$instance-of :ws/E$v1
                                                             :$value? true
                                                             :b 100}}
                                    :b 100}}})))
  (is (= #{[:ws/C$v1 :ws/A$v1]}
         (op-canon-refinements/find-required-values-in-refinements
          {:$instance-of :ws/A$v1
           :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1
                                    :$value? true
                                    :$refinements {:ws/E$v1 {:$instance-of :ws/E$v1
                                                             :b 100}}
                                    :b 100}}})))
  (is (= #{[:ws/E$v1 :ws/C$v1]}
         (op-canon-refinements/find-required-values-in-refinements
          {:$instance-of :ws/A$v1
           :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1
                                    :$refinements {:ws/E$v1 {:$instance-of :ws/E$v1
                                                             :$value? true
                                                             :b 100}}
                                    :b 100}}})))
  (is (= #{[:ws/E$v1 :ws/C$v1]
           [:ws/Z$v1 :ws/X$v1]}
         (op-canon-refinements/find-required-values-in-refinements
          {:$instance-of :ws/A$v1
           :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1
                                    :$refinements {:ws/E$v1 {:$instance-of :ws/E$v1
                                                             :$value? true
                                                             :b 100}}
                                    :b 100}
                          :ws/X$v1 {:$instance-of :ws/X$v1
                                    :$refinements {:ws/Z$v1 {:$instance-of :ws/Z$v1
                                                             :$value? true
                                                             :b 100}}
                                    :b 100}}})))
  (is (= #{[:ws/E$v1 :ws/C$v1]
           [:ws/E$v1 :ws/D$v1]}
         (op-canon-refinements/find-required-values-in-refinements
          {:$instance-of :ws/A$v1
           :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1
                                    :$refinements {:ws/E$v1 {:$instance-of :ws/E$v1
                                                             :$value? true
                                                             :b 100}}
                                    :b 100}
                          :ws/D$v1 {:$instance-of :ws/D$v1
                                    :$refinements {:ws/E$v1 {:$instance-of :ws/E$v1
                                                             :$value? true
                                                             :b 100}}
                                    :b 100}}}))))

(deftest test-within-a-pair?
  (is (not (#'op-canon-refinements/within-a-pair? [:a :b :c]
                                                  #{[:z :d]}
                                                  #{:z})))
  (is (not (#'op-canon-refinements/within-a-pair? [:a :b :c]
                                                  #{[:z :d]}
                                                  #{})))
  (is (not (#'op-canon-refinements/within-a-pair? [:a :b :c :d]
                                                  #{[:z :d]}
                                                  #{})))
  (is (not (#'op-canon-refinements/within-a-pair? [:a :b :c :d]
                                                  #{[:z]}
                                                  #{})))
  (is (not (#'op-canon-refinements/within-a-pair? [:a :b :c :d]
                                                  #{[:b :a]}
                                                  #{:z})))
  (is (not (#'op-canon-refinements/within-a-pair? [:a :b :c :d]
                                                  #{[:b :a]}
                                                  #{})))
  (is (not (#'op-canon-refinements/within-a-pair? [:a :b :c]
                                                  #{[:z :d]
                                                    [:b :a]}
                                                  #{:z})))
  (is (#'op-canon-refinements/within-a-pair? []
                                             #{[:z]}
                                             #{:z})))

;; (run-tests)
