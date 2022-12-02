;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.lib.test-format-errors
  (:require [clojure.test :refer :all]
            [com.viasat.halite.lib.format-errors :as format-errors]))

(set! *warn-on-reflection* true)

(deftest test-truncate
  (is (= (str (apply str (repeat 2045 \a)) "...")
         (#'format-errors/truncate-msg (apply str (repeat 2096 \a))))))

;; (run-tests)
