;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.transpile.util
  "Simple, self-contained utility functions for manipulating halite expressions."
  (:require [schema.core :as s]))

(set! *warn-on-reflection* true)

(s/defn mk-junct :- s/Any
  [op :- (s/enum 'and 'or), clauses :- [s/Any]]
  (condp = (count clauses)
    0 ({'and true, 'or false} op)
    1 (first clauses)
    (apply list op clauses)))

(defn make-do
  [side-effects body]
  (if (empty? side-effects)
    body
    `(~'$do! ~@side-effects ~body)))

(def ^:dynamic *fixpoint-iteration-limit* nil)

(defn fixpoint
  "Iterate f on input until (= x (f x))."
  [f input]
  (loop [x input, i 0]
    (if (and *fixpoint-iteration-limit* (< *fixpoint-iteration-limit* i))
      (throw (ex-info (format "Failed to reach fixpoint within %d iterations!" *fixpoint-iteration-limit*)
                      {:input input :current x}))
      (let [x' (f x)]
        (cond-> x' (not= x' x) (recur (inc i)))))))
