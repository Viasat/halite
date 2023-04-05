;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-var-ref
  (:require [clojure.test :refer :all]
            [com.viasat.halite.var-ref :as var-ref]
            [schema.test]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-reader
  (is (= #r [:x :y :z]
         (var-ref/var-ref-reader [:x :y :z]))))

(deftest test-get-path
  (is (= [:a :b]
         (->> [:a :b]
              var-ref/make-var-ref
              var-ref/get-path))))

(deftest test-extend
  (is (= #r [:a :b :c :d]
         (-> [:a :b]
             var-ref/make-var-ref
             (var-ref/extend-path [:c :d])))))

;; (time (run-tests))
