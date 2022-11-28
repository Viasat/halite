;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.transpile.test-lower-cond
  (:require [com.viasat.halite.transpile.lower-cond :as lower-cond]
            [schema.test :refer [validate-schemas]])
  (:use clojure.test))

(deftest test-lower-cond
  (let [spec-1-cond {:$type :spec/A
                     :spec-vars {:x :Integer}
                     :abstract? false
                     :constraints [["c1" '(cond a b x y z)]
                                   ["c2" '(if p q r)]]
                     :refines-to {:spec/B {:name "r1"
                                           :expr '(cond a b x y z p q r)}}}
        spec-1-if {:$type :spec/A
                   :spec-vars {:x :Integer}
                   :abstract? false
                   :constraints [["c1" '(if a b (if x y z))]
                                 ["c2" '(if p q r)]]
                   :refines-to {:spec/B {:name "r1"
                                         :expr '(if a b (if x y (if z p (if q r r))))}}}]
    (is (= {:spec/A spec-1-if}
           (lower-cond/lower-cond-in-spec-map {:spec/A spec-1-cond})))))

;; (run-tests)
