;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite-analysis
  (:require [jibe.halite :as halite]
            [internal :as s]))

(declare gather-free-vars)

(defn gather-seq-free-vars [context expr]
  (cond
    (= 'let (first expr))
    (let [[_ bindings body] expr
          [context free-vars] (reduce
                               (fn [[context free-vars] [sym e]]
                                 [(conj context sym) (into free-vars (gather-free-vars context e))])
                               [context #{}]
                               (partition 2 bindings))]
      (into free-vars (gather-free-vars context body)))

    (#{'every 'all} (first expr))
    (let [[_ [sym coll] body] expr]
      (into (gather-free-vars context coll)
            (gather-free-vars (conj context sym) body)))

    :default
    (->> expr
         rest
         (map (partial gather-free-vars context))
         (reduce into #{}))))

(defn gather-collection-free-vars [context expr]
  (->> expr
       (map (partial gather-free-vars context))
       (reduce into #{})))

(defn gather-free-vars
  "Recursively find the set of symbols in the expr which refer to the surrounding context."
  ([expr]
   (gather-free-vars #{} expr))
  ([context expr]
   (cond
     (boolean? expr) #{}
     (halite/long? expr) #{}
     (string? expr) #{}
     (symbol? expr) (if (context expr)
                      #{}
                      #{expr})
     (keyword? expr) #{}
     (map? expr) (->> (vals expr)
                      (map (partial gather-free-vars context))
                      (reduce into #{}))
     (seq? expr) (gather-seq-free-vars context expr)
     (set? expr) (gather-collection-free-vars context expr)
     (vector? expr) (gather-collection-free-vars context expr)
     :default (throw (ex-info "unexpected expr to gather-free-vars" {:expr expr})))))

;;;;

(defn- simple-value-or-symbol? [expr]
  (cond
    (boolean? expr) true
    (halite/long? expr) true
    (string? expr) true
    (symbol? expr) true
    (map? expr) true
    (set? expr) true
    (vector? expr) true
    :default false))

(defn- sort-tlfc [tlfc]
  (-> (group-by gather-free-vars tlfc)
      (update-keys first)
      (update-vals set)))

(defn- gather-tlfc*
  [expr]
  (cond
    (and (seq? expr)
         (= 'and (first expr))) (->> expr
                                     rest
                                     (map gather-tlfc*)
                                     (reduce into #{}))
    (and (seq? expr)
         (#{'= '< '<= '> '>=} (first expr))) (if (and (= 1 (count (gather-free-vars expr)))
                                                      (->> (rest expr)
                                                           (every? simple-value-or-symbol?))
                                                      (some symbol? (rest expr)))
                                               #{expr}
                                               #{})

    (and (seq? expr)
         (= 'contains? (first expr))) (let [[_ coll e] expr]
                                        (if (and (= 1 (count (gather-free-vars expr)))
                                                 (->> (rest expr)
                                                      (every? simple-value-or-symbol?))
                                                 (symbol? e))
                                          #{expr}
                                          #{}))
    :default #{}))

(defn gather-tlfc
  "Produce a map from symbols to sets of all of the 'top-level-field-constraints' contained in the
  boolean expr. These are expressions that are clauses of the expression which only refer to a
  single variable, which are of a form that can be understood simply. These are necessary, but not
  sufficient constraints for the entire expression to evaluate to 'true'."
  [expr]
  (->> expr gather-tlfc* sort-tlfc))
