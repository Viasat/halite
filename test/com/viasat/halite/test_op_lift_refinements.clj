;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-lift-refinements
  (:require [clojure.test :refer :all]
            [com.viasat.halite.op-lift-refinements :as op-lift-refinements]
            [schema.test]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-basic
  (let [bom {:$instance-of :ws/A$v1
             :$refinements {:ws/E$v1 {:$instance-of :ws/E$v1
                                      :b 100}}}]
    (is (= bom
           (op-lift-refinements/lift-refinements-op bom))))

  (is (= {:$instance-of :ws/A$v1
          :$refinements {:ws/E$v1 {:$instance-of :ws/E$v1
                                   :b 100}
                         :ws/F$v1 {:$instance-of :ws/F$v1
                                   :x 99}}}
         (op-lift-refinements/lift-refinements-op {:$instance-of :ws/A$v1
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
         (op-lift-refinements/lift-refinements-op {:$instance-of :ws/A$v1
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
           (op-lift-refinements/lift-refinements-op bom)))

    ;; now test the same thing embedded in concrete choices of an abstract spec
    (is (= {:$refines-to :ws/X$v1
            :$concrete-choices {:ws/A$v1 expected}}
           (op-lift-refinements/lift-refinements-op {:$refines-to :ws/X$v1
                                                     :$concrete-choices {:ws/A$v1 bom}})))

    ;; now test the same thing embedded in refinements of an abstract spec
    ;; unchanged, to be dealt with as part of pushing the refinements down into concrete choices
    (is (= {:$refines-to :ws/X$v1
            :$refinements {:ws/A$v1 bom}}
           (op-lift-refinements/lift-refinements-op {:$refines-to :ws/X$v1
                                                     :$refinements {:ws/A$v1 bom}})))

    ;; now test the same thing embedded in another concrete spec
    (is (= {:$instance-of :ws/X$v1
            :$refinements (-> expected
                              :$refinements
                              (assoc :ws/A$v1 {:$instance-of :ws/A$v1}))}
           (op-lift-refinements/lift-refinements-op {:$instance-of :ws/X$v1
                                                     :$refinements {:ws/A$v1 bom}})))))

;; (run-tests)
