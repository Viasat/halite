;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-propagate
  (:require [clojure.test :refer :all]
            [com.viasat.halite.propagate :as propagate]
            [com.viasat.halite.transpile.lowering :as lowering]
            [com.viasat.halite.transpile.rewriting :as rewriting :refer [with-summarized-trace-for]]
            [com.viasat.halite.transpile.simplify :as simplify]
            [com.viasat.halite.transpile.ssa :as ssa]
            [schema.core :as s]
            [schema.test]))

(def strings-and-abstract-specs-example
  '{:ws/Painted
    {:abstract? true
     :fields {:color :String}
     :constraints [["validColors" (or (= color "red") (= color "green") (= color "blue"))]]}

    :ws/Car
    {:fields {:horsePower :Integer}
     :constraints [["validHorsePowers" (and (<= 120 horsePower) (<= horsePower 300))]]
     :refines-to {:ws/Painted
                  {:expr {:$type :ws/Painted
                          :color (if (> horsePower 250) "red" "blue")}}}}})

(def simple-answer {:$type :ws/Car,
                    :horsePower {:$in [120 300]},
                    :$refines-to #:ws{:Painted {:color {:$in #{"blue" "green" "red"}}}}})

(deftest test-strings-and-abstract-specs-example
  (are [in out]
       (= out (propagate/propagate strings-and-abstract-specs-example in))

    {:$type :ws/Car}
    simple-answer

    {:$type :ws/Car :$refines-to {:ws/Painted {:color {:$in #{"red" "yellow"}}}}}
    {:$type :ws/Car,
     :horsePower {:$in [251 300]},
     :$refines-to #:ws{:Painted {:color "red"}}}

    {:$type :ws/Car :horsePower 140}
    {:$type :ws/Car, :horsePower 140
     :$refines-to {:ws/Painted {:color "blue"}}}))

(deftest test-propagate-cond
  (is (= simple-answer
         (propagate/propagate '{:ws/Painted {:abstract? true
                                             :fields {:color :String}
                                             :constraints [["validColors" (cond (= color "red") true
                                                                                (= color "green") true
                                                                                (= color "blue") true
                                                                                false)]]}
                                :ws/Car {:fields {:horsePower :Integer}
                                         :constraints [["validHorsePowers" (cond (and (<= 120 horsePower)
                                                                                      (<= horsePower 300)) true
                                                                                 false)]]
                                         :refines-to {:ws/Painted
                                                      {:expr {:$type :ws/Painted
                                                              :color (cond (> horsePower 250) "red"
                                                                           "blue")}}}}}
                              {:$type :ws/Car}))))

(deftest test-propagate-fixed-decimal
  (is (= {:$type :ws/Car,
          :horsePower {:$in [#d "12.0" #d "30.0"]},
          :$refines-to #:ws{:Painted {:color {:$in #{"blue" "green" "red"}}}}}
         (propagate/propagate '{:ws/Painted {:abstract? true
                                             :fields {:color :String}
                                             :constraints [["validColors" (cond (= color "red") true
                                                                                (= color "green") true
                                                                                (= color "blue") true
                                                                                false)]]}
                                :ws/Car {:fields {:horsePower [:Decimal 1]}
                                         :constraints [["validHorsePowers" (cond (and (<= #d "12.0" horsePower)
                                                                                      (<= horsePower #d "30.0")) true
                                                                                 false)]]
                                         :refines-to {:ws/Painted
                                                      {:expr {:$type :ws/Painted
                                                              :color (cond (> horsePower #d "25.0") "red"
                                                                           "blue")}}}}}
                              {:$type :ws/Car}))))

(deftest test-propagate-fixed-decimal-rescale
  (is (= {:$type :ws/Car,
          :horsePower {:$in [#d "12.0" #d "30.0"]},
          :$refines-to #:ws{:Painted {:color {:$in #{"blue" "green" "red"}}}}}
         (propagate/propagate '{:ws/Painted {:abstract? true
                                             :fields {:color :String}
                                             :constraints [["validColors" (cond (= color "red") true
                                                                                (= color "green") true
                                                                                (= color "blue") true
                                                                                false)]]}
                                :ws/Car {:fields {:horsePower [:Decimal 1]}
                                         :constraints [["validHorsePowers" (cond (and (<= (rescale (if true
                                                                                                     #d "12.0123"
                                                                                                     #d "13.9999") 1) horsePower)
                                                                                      (<= horsePower (rescale (rescale (* #d "1.0" 30) 2) 1))) true
                                                                                 false)]]
                                         :refines-to {:ws/Painted
                                                      {:expr {:$type :ws/Painted
                                                              :color (cond (> horsePower (rescale #d "25.09" 1)) "red"
                                                                           "blue")}}}}}
                              {:$type :ws/Car}))))

;; (run-tests)
