;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-bom-analysis
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-analysis :as bom-analysis]
            [schema.test]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-collapse-ranges-into-enum
  (is (= {:$enum #{3 5 20}}
         (#'bom-analysis/collapse-ranges-and-enum {:$enum #{3 5 20 30}
                                                   :$ranges #{[1 10]
                                                              [20 30]}})))

  (is (= bom/no-value-bom
         (#'bom-analysis/collapse-ranges-and-enum {:$enum #{0}
                                                   :$ranges #{[1 10]
                                                              [20 30]}})))

  (is (= {:$ranges #{[1 10]
                     [20 3000]}}
         (#'bom-analysis/collapse-ranges-and-enum {:$ranges #{[1 10]
                                                              [20 30]
                                                              [25 3000]}}))))

;; (run-tests)
