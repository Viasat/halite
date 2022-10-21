;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.prop-top-abstract
  (:require [clojure.set :as set]
            [com.viasat.halite.propagate.prop-abstract :as prop-abstract]
            [com.viasat.halite.propagate.prop-composition :as prop-composition]
            [com.viasat.halite.propagate.prop-strings :as prop-strings]
            [com.viasat.halite.transpile.ssa :as ssa :refer [SpecCtx]]
            [schema.core :as s]))

(def ^:private generated-field-name :$x)

;;;;

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

(s/defn ^:private generate-var-name
  "Generate a unique variable name that will not collide with the current context."
  [spec-id]
  (keyword (str (namespace spec-id)
                (str "$" (name spec-id)))))

(s/defn ^:private translate-down
  "Convert a top-level bound by expressing it in terms of a new, enclosing spec."
  [generated-spec-id bound]
  (assoc (zipmap (map generate-var-name (keys (:$refines-to bound)))
                 (map (fn [[spec-id bound-val]]
                        {:$refines-to {spec-id bound-val}}) (:$refines-to bound)))
         :$type generated-spec-id))

(declare combine-bounds)

(s/defn ^:private combine-abstract-$in-bounds
  [a-in :- prop-abstract/SpecIdToBoundWithRefinesTo
   b-in :- prop-abstract/SpecIdToBoundWithRefinesTo]
  (let [common-spec-ids (set/intersection (set (keys a-in))
                                          (set (keys b-in)))]
    (when-not (seq common-spec-ids)
      (prop-strings/throw-contradiction))
    (merge-with combine-bounds
                (select-keys a-in common-spec-ids)
                (select-keys b-in common-spec-ids))))

(s/defn ^:private combine-untyped-spec-bounds
  [a :- prop-abstract/UntypedSpecBound
   b :- prop-abstract/UntypedSpecBound]
  (merge-with combine-bounds a b))

(s/defn ^:private combine-spec-bounds
  [a :- prop-abstract/SpecBound
   b :- prop-abstract/SpecBound]
  (let [{a-type :$type
         a-refines-to :$refines-to
         a-in :$in} a
        {b-type :$type
         b-refines-to :$refines-to
         b-in :$in} b]
    (when (and a-type b-type (not= a-type b-type))
      (prop-strings/throw-contradiction))
    (merge (no-nil {:$type (or a-type b-type)
                    :$refines-to (no-empty
                                  ;; TODO: consider detecting whether there are any specs that refine to the combined results
                                  ;; or has that already been accounted for?
                                  (merge-with combine-untyped-spec-bounds a-refines-to b-refines-to))
                    ;; TODO: if there is a single common-spec-id, then lift it up to the :$type
                    :$in (when (or (seq a-in) (seq b-in))
                           (combine-abstract-$in-bounds a-in b-in))})
           (combine-untyped-spec-bounds (dissoc a :$in :$if :$type :$refines-to)
                                        (dissoc b :$in :$if :$type :$refines-to)))))

(s/defn ^:private bound-type
  [bound :- prop-abstract/Bound]
  (cond
    (:$type bound) prop-abstract/SpecBound
    (or (not (map? bound))
        (and (contains? bound :$in)
             (not (map? (:$in bound))))) prop-composition/AtomBound
    :else prop-abstract/SpecBound))

(s/defn ^:private primitive?
  [bound :- prop-strings/AtomBound]
  (or (integer? bound)
      (boolean? bound)
      (string? bound)))

(s/defn ^:private atom-bound-object
  [bound :- prop-strings/AtomBound]
  {:bound bound
   :bound-type (cond
                 (primitive? bound) :primitive
                 (#{:Unset :String} bound) bound
                 (set? (:$in bound)) :enum
                 (vector? (:$in bound)) :range
                 :default (throw (ex-info "unknown atom bound" {:bound bound})))})

(s/defn ^:private combine-atom-bounds
  [a b]
  (let [bound-objects (sort-by :bound-type (map atom-bound-object [a b]))
        [x y] (map :bound bound-objects)]
    (condp = (vec (map :bound-type bound-objects))
      [:String :String] :String
      [:String :Unset] (prop-strings/throw-contradiction)
      [:String :enum] y
      [:String :primitive] y
      [:String :range] (prop-strings/throw-contradiction)
      [:Unset :Unset] :Unset
      [:Unset :enum] {:$in (set/intersection #{:Unset}
                                             (:$in y))}
      [:Unset :primitive] :Unset
      [:Unset :range] :Unset
      [:enum :enum] {:$in (set/intersection (:$in x)
                                            (:$in y))}
      [:enum :primitive] (if (contains? (:$in x) y)
                           y
                           (prop-strings/throw-contradiction))
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
                       (cond
                         (empty? filtered-enum) (prop-strings/throw-contradiction)
                         (= 1 (count filtered-enum)) (first filtered-enum)
                         :default {:$in filtered-enum}))
      [:primitive :primitive] (if (= a b)
                                a
                                (prop-strings/throw-contradiction))
      [:primitive :range] (let [[lower upper _] (:$in y)]
                            (if (<= lower x upper)
                              x
                              (prop-strings/throw-contradiction)))
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

(s/defn ^:private combine-bounds
  [a :- prop-abstract/Bound
   b :- prop-abstract/Bound]
  (let [bound-types (set (map bound-type [a b]))]
    (when (> (count bound-types) 1)
      (throw (ex-info "invalid bound types" {:bound-types bound-types})))
    (let [bound-type (first bound-types)]
      (condp = bound-type
        prop-abstract/SpecBound (combine-spec-bounds a b)
        prop-composition/AtomBound (combine-atom-bounds a b)))))

(s/defn ^:private translate-up
  "Convert the resulting bound by lifting the bound on the fabricated spec's field to be the
  top-level result bound."
  [bound :- prop-abstract/ConcreteSpecBound2]
  (let [bounds (-> bound
                   (dissoc :$type :$refines-to)
                   vals)]
    (reduce combine-spec-bounds (first bounds) (rest bounds))))

(s/defn ^:private add-specs
  "Fabricate a new spec to hold a field of the required abstract type. Add it to the context."
  [generated-spec-id sctx refines-to-spec-ids]
  (assoc sctx
         generated-spec-id
         (ssa/spec-to-ssa (ssa/as-spec-env sctx)
                          {:spec-vars (zipmap (map generate-var-name refines-to-spec-ids)
                                              refines-to-spec-ids)
                           :constraints []
                           :refines-to {}})))

(s/defn ^:private generate-spec-id
  "Generate a unique spec-id that will not collide with the current context."
  [spec-id]
  (keyword (namespace spec-id)
           (str "$" (name spec-id))))

(s/defn propagate :- prop-abstract/SpecBound
  ([sctx :- ssa/SpecCtx
    initial-bound :- prop-abstract/SpecBound]
   (propagate sctx prop-composition/default-options initial-bound))
  ([sctx :- ssa/SpecCtx
    opts :- prop-composition/Opts
    initial-bound :- prop-abstract/SpecBound]
   (let [{:keys [$refines-to $type]} initial-bound]
     (if $type
       ;; the bound is a concrete bound, bypass this module's functionality by passing the call straight through
       (prop-abstract/propagate sctx opts initial-bound)
       ;; if the bound is an abstract bound then perform the transformation to turn it into a bound
       ;; on a field
       (let [generated-spec-id (generate-spec-id (key (first $refines-to)))]
         (translate-up (prop-abstract/propagate (add-specs generated-spec-id sctx (keys $refines-to))
                                                opts
                                                (translate-down generated-spec-id initial-bound))))))))
