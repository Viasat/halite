;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite-analysis
  (:require [clojure.set :as set]
            [jibe.halite :as halite]
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

(defn- condense-boolean-logic [expr]
  (cond
    (and (seq? expr)
         (= 'and (first expr))) (let [args (->> expr
                                                rest
                                                (map condense-boolean-logic)
                                                (remove #(= % true))
                                                (mapcat #(if (and (seq? %)
                                                                  (= 'and (first %)))
                                                           (rest %)
                                                           [%])))]
                                  (condp = (count args)
                                    0 true
                                    1 (first args)
                                    (cons 'and args)))
    (and (seq? expr)
         (= 'or (first expr))) (let [args (->> expr
                                               rest
                                               (map condense-boolean-logic)
                                               (remove #(= % false)))]
                                 (if (some #(= % true) args)
                                   true
                                   (condp = (count args)
                                     0 false
                                     1 (first args)
                                     (cons 'or args))))
    :default expr))

(defn sort-tlfc [expr]
  (cond
    (and (seq? expr)
         (= 'and (first expr))) (-> (group-by gather-free-vars (rest expr))
                                    (update-keys first)
                                    (update-vals #(condense-boolean-logic (cons 'and %))))
    (= true expr) nil

    (= 1 (count (gather-free-vars expr))) {(first (gather-free-vars expr))
                                           expr}

    :default (throw (ex-info "unexpected expression" {:expr expr}))))

(defn- third [s]
  (second (rest s)))

(defn- tlfc-data-or [xs]
  (if (some #(not (nil? (:enum %))) xs)
    (if (every? #(not (nil? (:enum %))) xs)
      (reduce (fn [a b]
                {:enum (set/union (:enum a) (:enum b))}) {:enum #{}} xs)
      true)
    {:range (vec (mapcat
                  #(or (:range %)
                       [%])
                  xs))}))

(defn- combine-mins [a b]
  (cond
    (< (:min a) (:min b)) (merge a b)
    (> (:min a) (:min b)) (merge b a)
    (= (:min a) (:min b)) (assoc (merge a b)
                                 :min-inclusive (and (:min-inclusive a)
                                                     (:min-inclusive b)))))

(defn- combine-maxs [a b]
  (cond
    (< (:max a) (:max b)) (merge b a)
    (> (:max a) (:max b)) (merge a b)
    (= (:max a) (:max b)) (assoc (merge a b)
                                 :max-inclusive (and (:max-inclusive a)
                                                     (:max-inclusive b)))))

(defn- combine-mins-or [a b]
  (cond
    (< (:min a) (:min b)) (merge b a)
    (> (:min a) (:min b)) (merge a b)
    (= (:min a) (:min b)) (assoc (merge a b)
                                 :min-inclusive (or (:min-inclusive a)
                                                    (:min-inclusive b)))))

(defn- combine-maxs-or [a b]
  (cond
    (< (:max a) (:max b)) (merge a b)
    (> (:max a) (:max b)) (merge b a)
    (= (:max a) (:max b)) (assoc (merge a b)
                                 :max-inclusive (or (:max-inclusive a)
                                                    (:max-inclusive b)))))

(defn- tlfc-data-and [xs]
  (if (some #(not (nil? (:enum %))) xs)
    (reduce (fn [a b]
              {:enum (if (= (:enum a) :any)
                       (:enum b)
                       (set/intersection (:enum a) (:enum b)))})
            {:enum :any}
            (filter #(not (nil? (:enum %))) xs))
    {:range [(reduce (fn [a b]
                       (cond
                         (and (:min a) (:min b)
                              (:max a) (:max b))
                         (merge (combine-mins a b)
                                (combine-maxs a b))

                         (and (:min a) (:min b))
                         (combine-mins a b)

                         (and (:max a) (:max b))
                         (combine-maxs a b)

                         :default
                         (merge a b))) {} xs)]}))

(defn- tlfc-data* [expr]
  (cond
    (and (seq? expr)
         (= '= (first expr))) {:enum (->> (rest expr)
                                          (remove symbol?)
                                          set)}

    (and (seq? expr)
         (#{'< '<= '> '>=} (first expr))) (if (symbol? (second expr))
                                            (condp = (first expr)
                                              '< {:max (third expr)
                                                  :max-inclusive false}
                                              '<= {:max (third expr)
                                                   :max-inclusive true}
                                              '> {:min (third expr)
                                                  :min-inclusive false}
                                              '>= {:min (third expr)
                                                   :min-inclusive true})
                                            (condp = (first expr)
                                              '< {:min (second expr)
                                                  :min-inclusive true}

                                              '<= {:min (second expr)
                                                   :min-inclusive false}
                                              '> {:max (second expr)
                                                  :max-inclusive true}
                                              '>= {:max (second expr)
                                                   :max-inclusive false}))

    (and (seq? expr)
         (= 'and (first expr))) (->> (rest expr)
                                     (map tlfc-data*)
                                     tlfc-data-and)

    (and (seq? expr)
         (= 'or (first expr))) (->> (rest expr)
                                    (map tlfc-data*)
                                    tlfc-data-or)

    (and (seq? expr)
         (= 'contains? (first expr))) {:enum (second expr)}

    :default expr))

(defn- combine-max-only-ranges [ranges]
  (let [max-only (filter #(nil? (:min %)) ranges)
        others (remove #(nil? (:min %)) ranges)]
    (if (> (count max-only) 1)
      (conj others (reduce combine-maxs-or (first max-only) (rest max-only)))
      ranges)))

(defn- combine-min-only-ranges [ranges]
  (let [min-only (filter #(nil? (:max %)) ranges)
        others (remove #(nil? (:max %)) ranges)]
    (if (> (count min-only) 1)
      (conj others (reduce combine-mins-or (first min-only) (rest min-only)))
      ranges)))

(defn- combine-single-leg-ranges [x]
  (if (:range x)
    (assoc x :range (->> (:range x)
                         combine-max-only-ranges
                         combine-min-only-ranges
                         vec))
    x))

(defn- tlfc-data [expr]
  (->> (let [result (tlfc-data* expr)]
         (if (and (map? result)
                  (or (contains? result :min)
                      (contains? result :max)))
           {:range [result]}
           result))
       combine-single-leg-ranges))

(defn tlfc-data-map [m]
  (->> (update-vals m tlfc-data)
       (remove #(= true (second %)))
       (into {})))

(defn- gather-tlfc-single*
  [expr]
  (cond
    (and (seq? expr)
         (= 'and (first expr))) (->> expr
                                     rest
                                     (map gather-tlfc-single*)
                                     (cons 'and))

    (and (seq? expr)
         (#{'= '< '<= '> '>=} (first expr))) (if (and (= 1 (count (gather-free-vars expr)))
                                                      (->> (rest expr)
                                                           (every? simple-value-or-symbol?))
                                                      (some symbol? (rest expr)))
                                               expr
                                               true)
    :default true))

(defn- gather-tlfc*
  [expr]
  (cond
    (and (seq? expr)
         (= 'and (first expr))) (->> expr
                                     rest
                                     (map gather-tlfc*)
                                     (cons 'and))
    (and (seq? expr)
         (= 'or (first expr))
         (= 1 (count (gather-free-vars expr)))) (->> expr
                                                     rest
                                                     (map gather-tlfc-single*)
                                                     (cons 'or))

    (and (seq? expr)
         (#{'= '< '<= '> '>=} (first expr))) (if (and (= 1 (count (gather-free-vars expr)))
                                                      (->> (rest expr)
                                                           (every? simple-value-or-symbol?))
                                                      (some symbol? (rest expr)))
                                               expr
                                               true)

    (and (seq? expr)
         (= 'contains? (first expr))) (let [[_ coll e] expr]
                                        (if (and (= 1 (count (gather-free-vars expr)))
                                                 (->> (rest expr)
                                                      (every? simple-value-or-symbol?))
                                                 (symbol? e))
                                          expr
                                          true))
    :default true))

(defn gather-tlfc
  "Produce a map from symbols to sets of all of the 'top-level-field-constraints' contained in the
  boolean expr. These are expressions that are clauses of the expression which only refer to a
  single variable, which are of a form that can be understood simply. These are necessary, but not
  sufficient constraints for the entire expression to evaluate to 'true'."
  [expr]
  (->> expr gather-tlfc* condense-boolean-logic))
