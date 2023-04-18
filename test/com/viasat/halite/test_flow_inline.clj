;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-flow-inline
  (:require [clojure.test :refer :all]
            [com.viasat.halite.flow-inline :as flow-inline]
            [schema.test])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-inline-constants
  (is (= true
         (flow-inline/inline-constants {:$instance-of :ws/A$v1,
                                        :a {:$value? true}}
                                       #r [:a :$value?])))

  (is (= 20
         (flow-inline/inline-constants {:$instance-of :ws/A$v1,
                                        :a 20}
                                       #r [:a])))

  (is (= #r [:a]
         (flow-inline/inline-constants {:$instance-of :ws/A$v1,
                                        :a {:$value? true
                                            :$enum #{20}}}
                                       #r [:a]))
      "this case is not handled by this function, rather it handled elswhere"))

(deftest test-inline-ops
  (is (= '(or #r [:x] #r [:y])
         (flow-inline/inline-ops '(or #r [:x] #r [:y]))))

  (is (= true
         (flow-inline/inline-ops '(or #r [:x] true))))

  (is (= #r [:x]
         (flow-inline/inline-ops '(and #r [:x] true))))

  (is (= true
         (flow-inline/inline-ops '(< (+ 1 3) 20)))))

(deftest test-inline
  (is (= true
         (flow-inline/inline {:$instance-of :ws/A$v1
                              :a 20}
                             '(< #r [:a] 30))))
  (is (= '(< 20 #r [:b])
         (flow-inline/inline {:$instance-of :ws/A$v1
                              :a 20}
                             '(< #r [:a] #r [:b])))))

;; (run-tests)
