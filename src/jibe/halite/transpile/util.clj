;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.transpile.util
  "Simple, self-contained utility functions for manipulating halite expressions."
  (:require [schema.core :as s]))

(s/defn mk-junct :- s/Any
  [op :- (s/enum 'and 'or), clauses :- [s/Any]]
  (condp = (count clauses)
    0 ({'and true, 'or false} op)
    1 (first clauses)
    (apply list op clauses)))

(defn fixpoint
  "Iterate f on input until (= x (f x))."
  [f input]
  (loop [x input]
    (let [x' (f x)]
      (cond-> x' (not= x' x) recur))))