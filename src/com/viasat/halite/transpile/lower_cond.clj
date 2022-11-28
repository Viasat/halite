;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.transpile.lower-cond
  "Lower 'cond' forms to 'if' forms."
  (:require [com.viasat.halite.envs :as envs]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(s/defn lower-cond-in-spec-map :- envs/SpecMap
  [spec-map :- envs/SpecMap]
  (->> spec-map
       (clojure.walk/prewalk (fn [x]
                               (if (and (seq? x)
                                        (= 'cond (first x)))
                                 ;; Assumes the 'cond expressions have been type-checked and have valid number of arguments."
                                 ;; turn 'cond into nested 'ifs
                                 (reduce (fn [if-expr [pred then]]
                                           (list 'if pred then if-expr))
                                         (last x)
                                         (reverse (partition 2 (rest x))))
                                 x)))))
