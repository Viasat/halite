;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-flow-get
  (:require [clojure.test :refer :all]
            [com.viasat.halite.flow-get :as flow-get]
            [schema.test]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-push-down-get
  (is (= 'a
         (flow-get/push-down-get []
                                 :x
                                 '{:$type :ws/X$v1, :x a, :y 7})))

  (is (= 'a
         (flow-get/push-down-get []
                                 :x
                                 '(get {:$type :ws/P$v1
                                        :q {:$type :ws/X$v1
                                            :x a
                                            :y 7}}
                                       :q)))))

;; (run-tests)
