;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-flow-boolean
  (:require [clojure.test :refer :all]
            [com.viasat.halite.flow-boolean :as flow-boolean]
            [schema.test])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-make-boolean
  (is (= 'x (flow-boolean/make-and 'x true)))
  (is (= false (flow-boolean/make-and 'x false)))

  (is (= true (flow-boolean/make-or 'x true)))

  (is (= false (flow-boolean/make-not true)))
  (is (= '(not x) (flow-boolean/make-not 'x))))

(deftest test-make-not
  (is (= false (flow-boolean/make-not true)))
  (is (= true (flow-boolean/make-not false)))
  (is (= '(not x) (flow-boolean/make-not 'x)))
  (is (= 'x (flow-boolean/make-not '(not x))))
  (is (=  true (flow-boolean/make-not '(not true))))
  (is (= false (flow-boolean/make-not '(not false)))))

(deftest test-make-and
  (doseq [x [true false]
          y [nil true false]
          z [nil true false]]
    (let [args (->> [x y z] (remove nil?))]
      (is (= (eval `(and ~@args))
             (apply flow-boolean/make-and args))))))

(deftest test-make-or
  (doseq [x [true false]
          y [nil true false]
          z [nil true false]]
    (let [args (->> [x y z] (remove nil?))]
      (is (= (eval `(or ~@args))
             (apply flow-boolean/make-or args))))))

(deftest test-make-if
  (doseq [target [true false]
          then-clause [true false]
          else-clause [true false]]
    (is (= (eval `(if ~target ~then-clause ~else-clause))
           (flow-boolean/make-if target then-clause else-clause))))
  (doseq [target ['com.viasat.halite.test-flow-boolean/x true false]
          then-clause ['com.viasat.halite.test-flow-boolean/y true false]
          else-clause ['com.viasat.halite.test-flow-boolean/z true false]]
    (doseq [x [true false]
            y [true false]
            z [true false]]
      (do (def com.viasat.halite.test-flow-boolean/x x)
          (def com.viasat.halite.test-flow-boolean/y y)
          (def com.viasat.halite.test-flow-boolean/z z)
          (is (= (eval `(if ~target ~then-clause ~else-clause))
                 (eval (flow-boolean/make-if target then-clause else-clause)))
              [:target target :then-clause then-clause :else-clause else-clause
               :x x :y y :z z
               :if (eval `(if ~target ~then-clause ~else-clause))
               :make-if (flow-boolean/make-if target then-clause else-clause)])))))

(defn nil-to-no-value [x]
  (if (nil? x)
    '$no-value
    x))

(defn no-value-to-nil [x]
  (when-not (= '$no-value x)
    x))

(deftest test-make-when
  (doseq [target [true false]
          then-clause [true false]]
    (is (= (nil-to-no-value (eval `(when ~target ~then-clause)))
           (flow-boolean/make-when target then-clause))))
  (doseq [target ['com.viasat.halite.test-flow-boolean/x true false]
          then-clause ['com.viasat.halite.test-flow-boolean/y true false]]
    (doseq [x [true false]
            y [true false]]
      (do (def com.viasat.halite.test-flow-boolean/x x)
          (def com.viasat.halite.test-flow-boolean/y y)
          (is (= (eval `(when ~target ~then-clause))
                 (eval (no-value-to-nil (flow-boolean/make-when target then-clause))))
              [:target target :then-clause then-clause
               :x x :y y
               :when (nil-to-no-value (eval `(when ~target ~then-clause)))
               :make-when (flow-boolean/make-when target then-clause)])))))

;; (run-tests)
