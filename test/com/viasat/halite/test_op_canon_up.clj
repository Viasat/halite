;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-canon-up
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.op-canon-up :as op-canon-up]
            [schema.test]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-canon-op
  (is (= {:$instance-of :ws/A$v1
          :x {:$enum #{20 3 5 30}
              :$ranges #{[1 10] [20 30]}}}
         (op-canon-up/canon-up-op {:$instance-of :ws/A$v1
                                   :x {:$enum #{3 5 20 30}
                                       :$ranges #{[1 10]
                                                  [20 30]}}})))
  (is (= {:$instance-of :ws/A$v1
          :x 3}
         (op-canon-up/canon-up-op {:$instance-of :ws/A$v1
                                   :x {:$enum #{3}}})))

  (is (= {:$instance-of :ws/A$v1
          :x 3}
         (op-canon-up/canon-up-op {:$instance-of :ws/A$v1
                                   :x {:$value? true
                                       :$enum #{3}}})))

  (is (= {:$instance-of :ws/A$v1
          :x {:$value? false
              :$enum #{3}}}
         (op-canon-up/canon-up-op {:$instance-of :ws/A$v1
                                   :x {:$value? false
                                       :$enum #{3}}}))))

;; (run-tests)
