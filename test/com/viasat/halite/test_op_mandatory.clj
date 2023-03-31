;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-mandatory
  (:require [clojure.test :refer :all]
            [com.viasat.halite.op-mandatory :as op-mandatory]
            [schema.test]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-basic
  (is (= {:$instance-of :ws/A$v1} (op-mandatory/mandatory-op {:ws/A$v1 {}} {:$instance-of :ws/A$v1})))
  (is (= {:$instance-of :ws/A$v1
          :x {:$value? true
              :$enum #{1 2}}}
         (op-mandatory/mandatory-op {:ws/A$v1 {:fields {:x :Integer}}}
                                    {:$instance-of :ws/A$v1
                                     :x {:$enum #{1 2}}})))
  (is (= {:$instance-of :ws/A$v1
          :x {:$value? true
              :$enum #{1 2}}
          :y {:$value? true
              :$ranges #{[10 20]}}
          :z {:$value? true}}
         (op-mandatory/mandatory-op {:ws/A$v1 {:fields {:x :Integer
                                                        :y :Integer
                                                        :z :Integer}}}
                                    {:$instance-of :ws/A$v1
                                     :x {:$enum #{1 2}}
                                     :y {:$ranges #{[10 20]}}
                                     :z {:$value? true}})))

  (is (= {:$instance-of :ws/A$v1
          :x {:$value? true
              :$enum #{1 2}}
          :y {:$ranges #{[10 20]}}
          :z {:$value? true}
          :b {:$refines-to :ws/B$v1, :$value? true}}
         (op-mandatory/mandatory-op {:ws/A$v1 {:fields {:x :Integer
                                                        :y [:Maybe :Integer]
                                                        :z :Integer
                                                        :b [:Instance :* #{:ws/B$v1}]}}
                                     :ws/B$v1 {:fields {:p :String}}}
                                    {:$instance-of :ws/A$v1
                                     :x {:$enum #{1 2}}
                                     :y {:$ranges #{[10 20]}}
                                     :z {:$value? true}
                                     :b {:$refines-to :ws/B$v1}})))

  (is (= {:$instance-of :ws/A$v1
          :x {:$enum #{1 2}}}
         (op-mandatory/mandatory-op {:ws/A$v1 {:fields {:x [:Maybe :Integer]}}}
                                    {:$instance-of :ws/A$v1
                                     :x {:$enum #{1 2}}})))

  (is (= {:$instance-of :ws/A$v1
          :x {:$value? false
              :$enum #{1 2}}}
         (op-mandatory/mandatory-op {:ws/A$v1 {:fields {:x [:Maybe :Integer]}}}
                                    {:$instance-of :ws/A$v1
                                     :x {:$value? false
                                         :$enum #{1 2}}})))

  (is (= {:$instance-of :ws/A$v1
          :x {:$contradiction? true}}
         (op-mandatory/mandatory-op {:ws/A$v1 {:fields {:x :Integer}}}
                                    {:$instance-of :ws/A$v1
                                     :x {:$value? false
                                         :$enum #{1 2}}})))

  (is (= {:$instance-of :ws/A$v1}
         (op-mandatory/mandatory-op {:ws/A$v1 {:fields {:x :Integer}}}
                                    {:$instance-of :ws/A$v1}))))

(deftest test-refinements
  (is (= {:$instance-of :ws/A$v1
          :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                   :y {:$value? true
                                       :$ranges #{[1 10]}}}}}
         (op-mandatory/mandatory-op {:ws/A$v1 {:fields {:x :Integer}}
                                     :ws/B$v1 {:fields {:y :Integer}}}
                                    {:$instance-of :ws/A$v1
                                     :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                                              :y {:$ranges #{[1 10]}}}}})))

  (is (= {:$refines-to :ws/A$v1
          :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                   :y {:$value? true
                                       :$ranges #{[1 10]}}}}}
         (op-mandatory/mandatory-op {:ws/A$v1 {:fields {:x :Integer}}
                                     :ws/B$v1 {:fields {:y :Integer}}}
                                    {:$refines-to :ws/A$v1
                                     :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                                              :y {:$ranges #{[1 10]}}}}})))

  (is (= {:$refines-to :ws/A$v1
          :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1
                                        :y {:$value? true
                                            :$ranges #{[1 10]}}}}}
         (op-mandatory/mandatory-op {:ws/A$v1 {:fields {:x :Integer}}
                                     :ws/B$v1 {:fields {:y :Integer}}}
                                    {:$refines-to :ws/A$v1
                                     :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1
                                                                   :y {:$ranges #{[1 10]}}}}}))))

;; (run-tests)
