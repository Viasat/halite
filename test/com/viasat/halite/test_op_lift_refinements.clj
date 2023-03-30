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

  ;; notice that one of the fields becomes no-value because the values are conflicting
  (is (= {:$instance-of :ws/A$v1
          :$refinements {:ws/E$v1 {:$instance-of :ws/E$v1
                                   :b 100}
                         :ws/F$v1 {:$instance-of :ws/F$v1
                                   :x {:$value? false}}
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
                                                                            :x 98}}}))))

;; (run-tests)
