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

(s/defn ^:private combine-bounds
  [a b]
  (let [{a-type :$type
         a-refines-to :$refines-to
         a-in :$in} a
        {b-type :$type
         b-refines-to :$refines-to
         b-in :$in} b]
    (when (and a-type b-type (not= a-type b-type))
      (prop-strings/throw-contradiction))
    (no-nil {:$type (or a-type b-type)
             :$refines-to (no-empty (merge-with combine-bounds a-refines-to b-refines-to))
             :$in (when (or (seq a-in) (seq b-in))
                    (let [common-spec-ids (set/intersection (set (keys a-in))
                                                            (set (keys b-in)))]
                      (when-not (seq common-spec-ids)
                        (prop-strings/throw-contradiction))
                      (merge-with combine-bounds
                                  (select-keys a-in common-spec-ids)
                                  (select-keys b-in common-spec-ids))))})))

(s/defn ^:private translate-up
  "Convert the resulting bound by lifting the bound on the fabricated spec's field to be the
  top-level result bound."
  [bound]
  (let [bounds (-> bound
                   (dissoc :$type)
                   vals)]
    (reduce combine-bounds (first bounds) (rest bounds))))

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
