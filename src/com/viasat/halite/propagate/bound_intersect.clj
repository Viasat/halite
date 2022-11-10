;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.bound-intersect
  "Logic for computing the intersection of two bounds."
  (:require [clojure.set :as set]
            [com.viasat.halite.propagate.prop-abstract :as prop-abstract]
            [com.viasat.halite.propagate.prop-composition :as prop-composition]
            [com.viasat.halite.propagate.prop-strings :as prop-strings]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(defn remove-vals-from-map
  "Remove entries from the map if applying f to the value produced false."
  [m f]
  (->> m
       (remove (comp f second))
       (apply concat)
       (apply hash-map)))

(defn no-empty
  "Convert empty collection to nil"
  [coll]
  (when (seq coll)
    coll))

(defn no-nil
  "Remove all nil values from the map"
  [m]
  (remove-vals-from-map m nil?))

;;

(declare combine-bounds)

(s/defn ^:private combine-abstract-$in-bounds
  [a-in :- prop-abstract/SpecIdToBoundWithRefinesTo
   b-in :- prop-abstract/SpecIdToBoundWithRefinesTo]
  ;; TODO: this code is WRONG!, it needs to be taking the union of the keys
  (let [common-spec-ids (set/intersection (set (keys a-in))
                                          (set (keys b-in)))]
    (when-not (seq common-spec-ids)
      (prop-strings/throw-contradiction))
    (merge-with combine-bounds
                ;; in an obscure way this handles :Unset boolean values in the bounds
                (select-keys a-in common-spec-ids)
                (select-keys b-in common-spec-ids))))

(s/defn ^:private combine-untyped-spec-bounds
  [a :- prop-abstract/UntypedSpecBound
   b :- prop-abstract/UntypedSpecBound]
  (merge-with combine-bounds a b))

(s/defn ^:private combine-abstract-spec-bounds
  [a :- prop-abstract/AbstractSpecBound
   b :- prop-abstract/AbstractSpecBound]
  (let [{a-refines-to :$refines-to
         a-in :$in} a
        {b-refines-to :$refines-to
         b-in :$in} b
        combined-in-bounds (when (or (seq a-in) (seq b-in))
                             (combine-abstract-$in-bounds a-in b-in))
        combined-in-bounds (if (not (:Unset combined-in-bounds))
                             (dissoc combined-in-bounds :Unset)
                             combined-in-bounds)
        single-in-bound (when (= (count combined-in-bounds) 1)
                          (key (first combined-in-bounds)))]
    (if (= single-in-bound :Unset)
      :Unset
      (merge (no-nil {:$type (when single-in-bound
                               single-in-bound)
                      :$refines-to (no-empty
                                    ;; TODO: consider detecting whether there are any specs that refine to the combined results
                                    ;; or has that already been accounted for?
                                    (merge-with combine-untyped-spec-bounds a-refines-to b-refines-to))
                      :$in (when-not single-in-bound
                             combined-in-bounds)})
             (combine-untyped-spec-bounds (dissoc a :$in :$if :$type :$refines-to)
                                          (dissoc b :$in :$if :$type :$refines-to))))))

(defn- concrete-spec-bound-to-AbstractSpecBound
  [bound]
  (let [{:keys [$type]} bound
        {:keys [unsettable? inner-type]} (if (and (vector? $type)
                                                  (= :Maybe (first $type)))
                                           {:unsettable? true
                                            :inner-type (second $type)}
                                           {:unsettable? false
                                            :inner-type $type})]
    {:$in (no-nil {inner-type (dissoc bound :$type)
                   :Unset unsettable?})}))

(s/defn ^:private bound-object
  [bound :- prop-abstract/Bound]
  (cond
    (:$type bound) {:bound (concrete-spec-bound-to-AbstractSpecBound bound)
                    :bound-type :prop-abstract/AbstractSpecBound}
    (or (not (map? bound))
        (and (contains? bound :$in)
             (not (map? (:$in bound))))) {:bound bound
                                          :bound-type :prop-composition/AtomBound}
    :else {:bound bound
           :bound-type :prop-abstract/AbstractSpecBound}))

(s/defn ^:private primitive?
  [bound :- prop-strings/AtomBound]
  (or (integer? bound)
      (boolean? bound)
      (string? bound)))

(s/defn ^:private atom-bound-object
  [bound :- prop-strings/AtomBound]
  (cond
    (primitive? bound) {:bound {:$in #{bound}}
                        :bound-type :enum}
    (#{:Unset :String} bound) {:bound {:$in #{bound}}
                               :bound-type :enum}
    (set? (:$in bound)) {:bound bound
                         :bound-type :enum}
    (vector? (:$in bound)) {:bound bound
                            :bound-type :range}
    :default (throw (ex-info "unknown atom bound" {:bound bound}))))

(s/defn ^:private simplify-enum :- prop-strings/AtomBound
  [enum :- #{s/Any}]
  (cond
    (empty? enum) (prop-strings/throw-contradiction)
    (= 1 (count enum)) (first enum)
    :default {:$in enum}))

(s/defn ^:private combine-atom-bounds
  [a b]
  (let [bound-objects (sort-by :bound-type (map atom-bound-object [a b]))
        [x y] (map :bound bound-objects)]
    (condp = (vec (map :bound-type bound-objects))
      [:enum :enum] (simplify-enum (set/intersection (:$in x)
                                                     (:$in y)))
      [:enum :range] (let [[lower upper unset?] (:$in y)
                           unsettable? (and
                                        ; range allows :Unset
                                        unset?
                                        ; enum allows :Unset
                                        (contains? (:$in x) :Unset))
                           filtered-enum (set (filter #(<= lower % upper) (disj (:$in x)
                                                                                :Unset)))
                           filtered-enum (if unsettable?
                                           (conj filtered-enum :Unset)
                                           filtered-enum)]
                       (simplify-enum filtered-enum))
      [:range :range] (let [[x-lower x-upper x-unset?] (:$in x)
                            [y-lower y-upper y-unset?] (:$in y)
                            lower (max x-lower y-lower)
                            upper (min x-upper y-upper)
                            unsettable? (and x-unset? y-unset?)]
                        (cond
                          (and (> lower upper) unsettable?) :Unset
                          (> lower upper) (prop-strings/throw-contradiction)
                          :default {:$in (if unsettable?
                                           [lower upper :Unset]
                                           [lower upper])})))))

(defn- unset-to-abstract-spec-bounds-if-needed [bound]
  (if (= :Unset bound)
    {:$in {:Unset true}}
    bound))

(s/defn combine-bounds
  [a :- prop-abstract/Bound
   b :- prop-abstract/Bound]
  (let [bound-objects (map bound-object (remove #(= :Unset %) [a b]))
        bound-types (map :bound-type bound-objects)]
    (when (> (count (set bound-types)) 1)
      (throw (ex-info "invalid bound types" {:bound-types bound-types
                                             :bounds [a b]})))
    (let [bound-type (first bound-types)]
      (condp = bound-type
        nil :Unset
        :prop-abstract/AbstractSpecBound (combine-abstract-spec-bounds
                                          (unset-to-abstract-spec-bounds-if-needed (:bound (first bound-objects)))
                                          (unset-to-abstract-spec-bounds-if-needed (:bound (second bound-objects))))
        :prop-composition/AtomBound (combine-atom-bounds a b)))))
