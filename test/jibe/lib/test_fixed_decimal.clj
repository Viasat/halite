;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.lib.test-fixed-decimal
  (:require [jibe.lib.fixed-decimal :as fixed-decimal]
            [internal :refer :all])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(deftest test-helpers
  (is (fixed-decimal/fixed-decimal? #d "1.0"))
  (is (fixed-decimal/fixed-decimal? #d "-1.0"))
  (is (fixed-decimal/fixed-decimal? #d "0.0"))
  (is (fixed-decimal/fixed-decimal? #d "0.00"))
  (is (fixed-decimal/fixed-decimal? #d "922337203685477580.7"))
  (is (fixed-decimal/fixed-decimal? #d "-922337203685477580.8"))
  (is (= #d "1.0" (read-string "#d\"1.0\"")))
  (is (thrown-with-msg? ExceptionInfo #"cannot be negative 0"
                        (read-string "#d \"-0.0\"")))
  (is (thrown-with-msg? ExceptionInfo #"invalid scale"
                        (read-string "#d \"1.\"")))
  (is (thrown-with-msg? ExceptionInfo #"invalid scale"
                        (read-string "#d \"1\"")))
  (is (thrown-with-msg? ExceptionInfo #"invalid scale"
                        (read-string "#d \"-1.\"")))
  (is (thrown-with-msg? ExceptionInfo #"invalid scale"
                        (read-string "#d \"-1\"")))
  (is (thrown-with-msg? ExceptionInfo #"cannot construct"
                        (read-string "#d \"\"")))
  (is (thrown-with-msg? NumberFormatException #"Invalid fixed-decimal string"
                        (read-string "#d \".0\"")))
  (is (thrown-with-msg? NumberFormatException #"Invalid fixed-decimal string"
                        (read-string "#d \".1\"")))
  (is (thrown-with-msg? NumberFormatException #"Invalid fixed-decimal string"
                        (read-string "#d \"-.0\"")))
  (is (thrown-with-msg? NumberFormatException #"Invalid fixed-decimal string"
                        (read-string "#d \"-.1\"")))
  (is (thrown-with-msg? NumberFormatException #"Invalid fixed-decimal string"
                        (read-string "#d \"+10.0\"")))
  (is (thrown-with-msg? NumberFormatException #"Invalid fixed-decimal string"
                        (read-string "#d \"- 0.0\"")))
  (is (thrown-with-msg? NumberFormatException #"Invalid fixed-decimal string"
                        (read-string "#d \"-0.0 \"")))
  (is (thrown-with-msg? NumberFormatException #"Invalid fixed-decimal string"
                        (read-string "#d \" -0.0\"")))
  (is (thrown-with-msg? ExceptionInfo #"cannot have a leading 0"
                        (read-string "#d \"01.0\"")))
  (is (thrown-with-msg? ExceptionInfo #"cannot have a leading 0"
                        (read-string "#d \"00.0\"")))
  (is (thrown-with-msg? ExceptionInfo #"cannot have a leading 0"
                        (read-string "#d \"-01.0\"")))
  (is (thrown-with-msg? ExceptionInfo #"cannot have a leading 0"
                        (read-string "#d \"-00.0\"")))
  (is (thrown-with-msg? NumberFormatException #"Invalid fixed-decimal string"
                        (read-string "#d \"922337203685477580.8\"")))
  (is (thrown-with-msg? NumberFormatException #"Invalid fixed-decimal string"
                        (read-string "#d \"-922337203685477580.9\"")))
  (is (thrown-with-msg? NumberFormatException #"Invalid fixed-decimal string"
                        (read-string "#d \"1.23E3\"")))
  (is (thrown-with-msg? NumberFormatException #"Invalid fixed-decimal string"
                        (read-string "#d \"1.23e3\"")))
  (is (not (fixed-decimal/fixed-decimal? "1.0")))
  (is (not (fixed-decimal/fixed-decimal? 1.2)))
  (is (not (fixed-decimal/fixed-decimal? 1.2M)))
  (is not (fixed-decimal/fixed-decimal? 12))

  (is (= 1
         (fixed-decimal/get-scale #d "0.0")))
  (is (= 2
         (fixed-decimal/get-scale #d "0.00")))

  (is (= #d "0.00"
         (fixed-decimal/fixed-decimal-reader "0.00")))
  (is (= #d "1.234567890123456789"
         (fixed-decimal/fixed-decimal-reader "1.234567890123456789"))))

(deftest test-equality
  (is (= #d "1.0" #d "1.0"))
  (is (= #d "1.0" #d "1.0" #d "1.0"))
  (is (= #d "1.0" (fixed-decimal/f+ #d "0.1" #d "0.9")))
  (is (not (= #d "-1.0" #d "1.0")))
  (is (not (= #d "1.0" #d "1.1")))
  (is (not (= #d "1.0" #d "1.00")))
  (is (not (= #d "1.0" 1)))
  (is (not (= #d "1.0" 1.0))))

(deftest test-addition
  (is (= #d "1.2"
         (fixed-decimal/f+ #d "1.1" #d "0.1")))
  (is (= #d "1.5"
         (fixed-decimal/f+ #d "1.1" #d "0.1" #d "0.3")))
  (is (= #d "1.0"
         (fixed-decimal/f+ #d "1.1" #d "-0.1")))
  (is (= #d "-1.2"
         (fixed-decimal/f+ #d "-1.1" #d "-0.1")))
  (is (= #d "-1.0"
         (fixed-decimal/f+ #d "-1.1" #d "0.1")))
  (is (= #d "1.1"
         (fixed-decimal/f+ #d "1.1" #d "0.0")))

  (is (= #d "922337203685477580.7"
         (fixed-decimal/f+ #d "922337203685477580.7" #d "0.0")))
  (is (thrown-with-msg? ArithmeticException #"long overflow"
                        (fixed-decimal/f+ #d "922337203685477580.7" #d "0.1")))

  (is (= #d "92233720368547758.07"
         (fixed-decimal/f+ #d "92233720368547758.07" #d "0.00")))
  (is (thrown-with-msg? ArithmeticException #"long overflow"
                        (fixed-decimal/f+ #d "92233720368547758.07" #d "0.01")))

  (is (thrown-with-msg? ExceptionInfo #"not fixed numbers with matching scale"
                        (fixed-decimal/f+ #d "1.2" #d "0.01")))

  (is (thrown-with-msg? ExceptionInfo #"invalid scale"
                        (read-string "#d \"1\"")))
  (is (thrown-with-msg? ExceptionInfo #"not fixed numbers with matching scale"
                        (fixed-decimal/f+ #d "1.1" 1)))
  (is (thrown-with-msg? ExceptionInfo #"unknown numeric type"
                        (fixed-decimal/f+ #d "1.1" 1.0))))

(deftest test-subtraction
  (is (= #d "1.0"
         (fixed-decimal/f- #d "1.1" #d "0.1")))
  (is (= #d "0.7"
         (fixed-decimal/f- #d "1.1" #d "0.1" #d "0.3")))
  (is (= #d "1.2"
         (fixed-decimal/f- #d "1.1" #d "-0.1")))
  (is (= #d "-1.0"
         (fixed-decimal/f- #d "-1.1" #d "-0.1")))
  (is (= #d "-1.2"
         (fixed-decimal/f- #d "-1.1" #d "0.1")))
  (is (= #d "1.1"
         (fixed-decimal/f- #d "1.1" #d "0.0")))
  (is (= #d "0.05"
         (fixed-decimal/f- #d "32.90" #d "32.85")))

  (is (= #d "-922337203685477580.8"
         (fixed-decimal/f- #d "-922337203685477580.8" #d "0.0")))
  (is (thrown-with-msg? ArithmeticException #"long overflow"
                        (fixed-decimal/f- #d "-922337203685477580.8" #d "0.1")))

  (is (= #d "-92233720368547758.08"
         (fixed-decimal/f- #d "-92233720368547758.08" #d "0.00")))
  (is (thrown-with-msg? ArithmeticException #"long overflow"
                        (fixed-decimal/f- #d "-92233720368547758.08" #d "0.01")))

  (is (thrown-with-msg? ExceptionInfo #"not fixed numbers with matching scale"
                        (fixed-decimal/f- #d "1.1" 1)))
  (is (thrown-with-msg? ExceptionInfo #"unknown numeric type"
                        (fixed-decimal/f- #d "1.1" 1.0)))
  (is (thrown-with-msg? ExceptionInfo #"not fixed numbers with matching scale"
                        (fixed-decimal/f- #d "1.2" #d "0.01"))))

(deftest test-multiplication
  (is (= #d "1.0"
         (fixed-decimal/f* #d "1.0" 1)))
  (is (= #d "6.0"
         (fixed-decimal/f* #d "1.0" 1 2 3)))
  (is (= #d "2.0"
         (fixed-decimal/f* #d "1.0" 2)))
  (is (= #d "2.0"
         (fixed-decimal/f* #d "2.0" 1)))
  (is (= #d "3.69"
         (fixed-decimal/f* #d "1.23" 3)))
  #_(is (thrown-with-msg? ExceptionInfo #"not fixed and integer type"
                          (fixed-decimal/f* #d "1.0" #d "2.0")))
  #_(is (thrown-with-msg? ExceptionInfo #"unknown numeric type"
                          (fixed-decimal/f* #d "1.0" 2.0)))

  (is (= #d "922337203685477580.7"
         (fixed-decimal/f* #d "922337203685477580.7" 1)))
  (is (thrown-with-msg? ArithmeticException #"long overflow"
                        (fixed-decimal/f* #d "922337203685477580.7" 2))))

(deftest test-division
  (is (= #d "1.0"
         (fixed-decimal/fquot #d "1.0" 1)))
  (is (= #d "0.2"
         (fixed-decimal/fquot #d "1.0" 1 2 2)))
  (is (= #d "0.5"
         (fixed-decimal/fquot #d "1.0" 2)))
  (is (= #d "2.0"
         (fixed-decimal/fquot #d "2.0" 1)))
  (is (= #d "0.41"
         (fixed-decimal/fquot #d "1.23" 3)))
  (is (= #d "0.00"
         (fixed-decimal/fquot #d "0.01" 3)))
  #_(is (thrown-with-msg? ExceptionInfo #"not fixed and integer type"
                          (fixed-decimal/fquot #d "1.0" #d "2.0")))
  #_(is (thrown-with-msg? ExceptionInfo #"unknown numeric type"
                          (fixed-decimal/fquot #d "1.0" 2.0)))
  (is (= #d "922337203685477580.7"
         (fixed-decimal/fquot #d "922337203685477580.7" 1)))
  (is (= #d "461168601842738790.3"
         (fixed-decimal/fquot #d "922337203685477580.7" 2))))

(deftest test-abs
  (is (= #d "1.0"
         (fixed-decimal/fabs #d "1.0")))
  (is (= #d "1.0"
         (fixed-decimal/fabs #d "-1.0")))
  (is (= #d "922337203685477580.7"
         (fixed-decimal/fabs #d "922337203685477580.7")))
  (is (= #d "-92233720368547758.08" ;; NOTE: replicates behavior of Java & Clojure with wrap-around of abs
         (fixed-decimal/fabs #d "-92233720368547758.08")))
  (is (= #d "-9223372036854775.808" ;; NOTE: replicates behavior of Java & Clojure with wrap-around of abs
         (fixed-decimal/fabs #d "-9223372036854775.808"))))

(deftest test-inqualities
  (is (fixed-decimal/f< #d "1.0" #d "2.0"))
  (is (fixed-decimal/f< #d "1.0" #d "2.0" #d "3.0"))
  (is (not (fixed-decimal/f< #d "3.0" #d "1.0" #d "2.0")))
  (is (not (fixed-decimal/f< #d "1.0" #d "1.0")))
  (is (not (fixed-decimal/f< #d "2.0" #d "1.0")))
  (is (fixed-decimal/f<= #d "1.0" #d "1.0"))
  (is (fixed-decimal/f<= #d "1.0" #d "2.0"))
  (is (not (fixed-decimal/f<= #d "2.0" #d "1.0")))
  (is (thrown-with-msg? ExceptionInfo #"not fixed numbers with matching scale"
                        (fixed-decimal/f< #d "1.0" #d "2.00")))
  (is (thrown-with-msg? ExceptionInfo #"not fixed numbers with matching scale"
                        (fixed-decimal/f< #d "1.0" 2)))
  (is (thrown-with-msg? ExceptionInfo #"unknown numeric type"
                        (fixed-decimal/f< #d "1.0" 2.0)))
  (is (thrown-with-msg? ExceptionInfo #"not fixed numbers with matching scale"
                        (fixed-decimal/f<= #d "1.0" #d "2.00")))
  (is (thrown-with-msg? ExceptionInfo #"not fixed numbers with matching scale"
                        (fixed-decimal/f<= #d "1.0" 2)))
  (is (thrown-with-msg? ExceptionInfo #"unknown numeric type"
                        (fixed-decimal/f<= #d "1.0" 2.0)))

  (is (not (fixed-decimal/f> #d "1.0" #d "2.0")))
  (is (not (fixed-decimal/f> #d "1.0" #d "1.0")))
  (is (fixed-decimal/f> #d "2.0" #d "1.0"))
  (is (fixed-decimal/f>= #d "1.0" #d "1.0"))
  (is (not (fixed-decimal/f>= #d "1.0" #d "2.0")))
  (is (fixed-decimal/f>= #d "2.0" #d "1.0"))
  (is (thrown-with-msg? ExceptionInfo #"not fixed numbers with matching scale"
                        (fixed-decimal/f> #d "1.0" #d "2.00")))
  (is (thrown-with-msg? ExceptionInfo #"not fixed numbers with matching scale"
                        (fixed-decimal/f> #d "1.0" 2)))
  (is (thrown-with-msg? ExceptionInfo #"unknown numeric type"
                        (fixed-decimal/f> #d "1.0" 2.0)))
  (is (thrown-with-msg? ExceptionInfo #"not fixed numbers with matching scale"
                        (fixed-decimal/f>= #d "1.0" #d "2.00")))
  (is (thrown-with-msg? ExceptionInfo #"not fixed numbers with matching scale"
                        (fixed-decimal/f>= #d "1.0" 2)))
  (is (thrown-with-msg? ExceptionInfo #"unknown numeric type"
                        (fixed-decimal/f>= #d "1.0" 2.0))))

(deftest test-set-scale
  (is (= #d "1.2"
         (fixed-decimal/set-scale #d "1.23" 1)))
  (is (= #d "1.23"
         (fixed-decimal/set-scale #d "1.23" 2)))
  (is (= #d "1.230"
         (fixed-decimal/set-scale #d "1.23" 3)))
  (is (= #d "1.2300"
         (fixed-decimal/set-scale #d "1.23" 4)))
  (is (= #d "1.230000000000000000"
         (fixed-decimal/set-scale #d "1.23" 18)))
  (is (thrown-with-msg? ExceptionInfo #"invalid scale"
                        (fixed-decimal/set-scale #d "1.23" 19)))
  (is (= 1
         (fixed-decimal/set-scale #d "1.24" 0)))
  (is (= Long
         (class (fixed-decimal/set-scale #d "1.24" 0))))
  (is (thrown-with-msg? ExceptionInfo #"invalid scale"
                        (fixed-decimal/set-scale #d "1.24" -1)))
  #_(is (thrown-with-msg? ExceptionInfo #"unexpected scale"
                          (fixed-decimal/set-scale #d "1.24" #d "3.0")))

  (is (= 922337203685477580
         (fixed-decimal/set-scale #d "922337203685477580.7" 0)))
  (is (= #d "922337203685477580.7"
         (fixed-decimal/set-scale #d "922337203685477580.7" 1)))
  (is (thrown-with-msg? NumberFormatException #"For input string"
                        (fixed-decimal/set-scale #d "922337203685477580.7" 2))))

(deftest test-shift-scale
  (is (= #d "12.3"
         (fixed-decimal/shift-scale #d "1.23" 1)))
  (is (= #d "-12.3"
         (fixed-decimal/shift-scale #d "-1.23" 1)))
  (is (= 123
         (fixed-decimal/shift-scale #d "1.23" 2)))
  (is (= -123
         (fixed-decimal/shift-scale #d "-1.23" 2)))
  (is (thrown-with-msg? ExceptionInfo #"invalid scale"
                        (fixed-decimal/shift-scale #d "1.23" 3)))
  (is (= #d "1.23"
         (fixed-decimal/shift-scale #d "1.23" 0)))
  (is (thrown-with-msg? ExceptionInfo #"shift amount cannot be negative"
                        (fixed-decimal/shift-scale #d "1.23" -1))))

(deftest test-customize-printing
  (binding [fixed-decimal/*reader-symbol* 'fixed-decimal/decimal]
    (is (= "#fixed-decimal/decimal \"1.2\""
           (pr-str #d "1.2")))))

(deftest test-package-long
  (is (= #d "0.0"
         (#'fixed-decimal/package-long 1 0)))
  (is (= #d "0.00"
         (#'fixed-decimal/package-long 2 0)))
  (is (= #d "0.1"
         (#'fixed-decimal/package-long 1 1)))
  (is (= #d "0.01"
         (#'fixed-decimal/package-long 2 1)))
  (is (= #d "0.001"
         (#'fixed-decimal/package-long 3 1)))
  (is (= #d "987.6"
         (#'fixed-decimal/package-long 1 9876)))
  (is (= #d "98.76"
         (#'fixed-decimal/package-long 2 9876)))
  (is (= #d "9.876"
         (#'fixed-decimal/package-long 3 9876)))
  (is (= #d "0.9876"
         (#'fixed-decimal/package-long 4 9876)))
  (is (= #d "0.09876"
         (#'fixed-decimal/package-long 5 9876))))

;; (run-tests)
