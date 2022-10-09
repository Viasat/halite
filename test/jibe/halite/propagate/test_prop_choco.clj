;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.propagate.test-prop-choco
  (:require [jibe.halite.propagate.prop-choco :as prop-choco])
  (:use clojure.test))

(deftest test-propagate
  (let [spec '{:spec-vars {:n "Integer" :m [:Maybe "Integer"] :p "Boolean"}
               :constraints [["c1" (< 0 n)]
                             ["c2" (if-value m (and (< 0 m) (< m n)) (not p))]
                             ["c3" (< n (if p 10 15))]]
               :refines-to {}}]
    (are [in out]
        (= out (prop-choco/propagate spec in))

      {} {:m {:$in [1 13 :Unset]}, :n {:$in [1 14]}, :p {:$in #{false true}}}
      {:p true} {:m {:$in [1 8]}, :n {:$in [2 9]}, :p true}
      {:p false} {:m {:$in [1 13 :Unset]}, :n {:$in [1 14]}, :p false}
      {:n 1} {:m :Unset :n 1 :p false})))
