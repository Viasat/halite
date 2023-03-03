;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.bound-union
  (:require [clojure.set :as set]
            [com.viasat.halite.propagate.prop-refine
             :refer [ConcreteBound RefinementBound]]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(defn- int-bound? [bound]
  (or (int? bound)
      (and (map? bound)
           (contains? bound :$in)
           (every? int? (:$in bound)))))

(defn- union-int-bounds* [a b]
  (cond
    (and (int? a) (int? b)) (if (= a b) a #{a b})
    (int? a) (recur b a)
    (and (set? a) (set? b)) (set/union a b)
    (and (set? a) (int? b)) (conj a b)
    (set? a) (recur b a)
    (and (vector? a) (int? b)) [(min (first a) b) (max (second a) b)]
    (and (vector? a) (set? b)) [(apply min (first a) b) (apply max (second a) b)]
    (vector? a) [(min (first a) (first b)) (max (second a) (second b))]
    :else (throw (ex-info "BUG! Couldn't union int bounds" {:a a :b b}))))

(defn- union-int-bounds [a b]
  (when (not (int-bound? b))
    (throw (ex-info "BUG! Tried to union bounds of different kinds" {:a a :b b})))
  (let [result (union-int-bounds* (cond-> a (:$in a) (:$in)) (cond-> b (:$in b) (:$in)))]
    (if (int? result) result {:$in result})))

(defn- bool-bound? [bound]
  (or (boolean? bound)
      (and (map? bound)
           (contains? bound :$in)
           (every? boolean? (:$in bound)))))

(defn- union-bool-bounds [a b]
  (when (not (bool-bound? b))
    (throw (ex-info "BUG! Tried to union bounds of different kinds" {:a a :b b})))
  (let [a (if (map? a) (:$in a) (set [a]))
        b (if (map? b) (:$in b) (set [b]))
        result (set/union a b)]
    (if (= 1 (count result))
      (first result)
      {:$in result})))

(declare union-bounds)

(s/defn union-refines-to-bounds :- RefinementBound
  "The union of two :$refines-to bounds is the union of bounds for each spec-id
  that appears in BOTH. Including a spec-id that appeared in only `a` would
  cause the resulting bound to be narrower than `b`, because :$refines-to is a
  kind of conjunction."
  [a :- (s/maybe RefinementBound), b :- (s/maybe RefinementBound)]
  (reduce
   (fn [result spec-id]
     (assoc result spec-id
            (dissoc
             (union-bounds
              (assoc (spec-id a) :$type spec-id)
              (assoc (spec-id b) :$type spec-id))
             :$type)))
   {}
   (filter (set (keys a)) (keys b))))

(defn- union-spec-bounds [a b]
  (when (not= (:$type a) (:$type b))
    (throw (ex-info "BUG! Tried to union bounds for different spec types" {:a a :b b})))

  ;; TODO: should not need to special case this
  (if (and (= a :String) (= b :String))
    :String
    (let [refines-to (union-refines-to-bounds (:$refines-to a) (:$refines-to b))]
      (reduce
       (fn [result var-kw]
         (assoc result var-kw
                (cond
                  (and (contains? a var-kw) (contains? b var-kw)) (union-bounds (var-kw a) (var-kw b))
                  (contains? a var-kw) (var-kw a)
                  :else (var-kw b))))
       (cond-> {:$type (:$type a)}
         (not (empty? refines-to)) (assoc :$refines-to refines-to))
       (disj (set (concat (keys a) (keys b))) :$type :$refines-to)))))

(defn- allows-unset? [bound]
  (or
   (= :Unset bound)
   (and (map? bound) (contains? bound :$in)
        (let [in (:$in bound)]
          (if (set? in)
            (contains? in :Unset)
            (= :Unset (last in)))))
   (and (map? bound) (contains? bound :$type)
        (vector? (:$type bound))
        (= :Maybe (first (:$type bound))))))

(defn- remove-unset [bound]
  (cond
    (= :Unset bound)
    (throw (ex-info "BUG! Called remove-unset on :Unset" {:bound bound}))

    (or (int? bound) (boolean? bound)) bound

    (and (map? bound) (contains? bound :$in))
    (let [in (:$in bound)]
      (cond
        (set? in) (update bound :$in disj :Unset)
        (vector? in) (update bound :$in subvec 0 2)
        :else (throw (ex-info "BUG! Unrecognized bound" {:bound bound}))))

    (and (map? bound) (contains? bound :$type))
    (update bound :$type #(cond-> % (vector? %) second))

    (= :String bound) bound

    :else (throw (ex-info "BUG! Unrecognized bound" {:bound bound}))))

(defn- add-unset [bound]
  (cond
    (or (boolean? bound) (int? bound)) {:$in #{bound :Unset}}

    (and (map? bound) (contains? bound :$in))
    (let [in (:$in bound)]
      (cond
        (set? in) (update bound :$in conj :Unset)
        (vector? in) (assoc bound :$in (conj (subvec in 0 2) :Unset))
        :else (throw (ex-info "BUG! Unrecognized bound" {:bound bound}))))

    (and (map? bound) (contains? bound :$type))
    (update bound :$type #(cond->> % (keyword? %) (conj [:Maybe])))

    :else (throw (ex-info "BUG! Unrecognized bound" {:bound bound}))))

(s/defn union-bounds :- ConcreteBound
  [a :- ConcreteBound, b :- ConcreteBound]
  (if (and (= :Unset a) (= :Unset b))
    :Unset
    (let [unset? (or (allows-unset? a) (allows-unset? b))
          a (if (= :Unset a) b a), b (if (= :Unset b) a b)
          a (remove-unset a),      b (remove-unset b)]
      (cond->
       (cond
         (int-bound? a) (union-int-bounds a b)
         (bool-bound? a) (union-bool-bounds a b)
         :else (union-spec-bounds a b))
        unset? add-unset))))
