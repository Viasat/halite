;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-check-constraints
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.op-add-constraints :as op-add-constraints]
            [com.viasat.halite.op-check-constraints :as op-check-constraints]
            [schema.test])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(defmacro check [spec-env bom]
  `(->> ~bom
        (op-add-constraints/add-constraints-op ~spec-env)
        (op-check-constraints/check-constraints-op ~spec-env)))

(deftest test-basic
  (is (nil?
       (check {:ws/A$v1 {:fields {:x :Integer}}}
              {:$instance-of :ws/A$v1, :x {:$enum #{1 2}}})))
  (is (nil?
       (check {:ws/A$v1 {:fields {:x :Integer}}}
              {:$instance-of :ws/A$v1
               :x 1})))
  (is (nil?
       (check {:ws/A$v1 {:fields {:x :Integer}
                         :constraints [["valid_x" '(and (> x 0) (< x 10000))]]}}
              {:$instance-of :ws/A$v1
               :x 1})))
  (is (= {[] ["valid_x"]}
         (check {:ws/A$v1 {:fields {:x :Integer}
                           :constraints [["valid_x" '(and (> x 0) (< x 10000))]]}}
                {:$instance-of :ws/A$v1
                 :x -1})))

  (is (= {[] ["valid_y"]
          [:a] ["valid_x"]}
         (check {:ws/A$v1 {:fields {:x :Integer}
                           :constraints [["valid_x" '(and (> x 0) (< x 10000))]]}
                 :ws/B$v1 {:fields {:y :Integer
                                    :a [:Instance :ws/A$v1]}
                           :constraints [["valid_y" '(< y 0)]]}}
                {:$instance-of :ws/B$v1
                 :a {:$instance-of :ws/A$v1
                     :x -1}
                 :y 0}))))

;; (run-tests)
