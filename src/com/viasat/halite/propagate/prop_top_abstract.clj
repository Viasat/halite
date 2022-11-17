;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.prop-top-abstract
  "Handle the case where a top-level abstract spec bound is used. Introduce a new spec which contains
  a field of the type of one of the abstract specs referenced in the bound. Does not handle the case
  where all of the specs in the :$refines-to field of the bound are concrete."
  (:require [com.viasat.halite.envs :as envs]
            [com.viasat.halite.propagate.prop-abstract :as prop-abstract]
            [com.viasat.halite.propagate.prop-composition :as prop-composition]
            [com.viasat.halite.transpile.ssa :as ssa :refer [SpecCtx]]
            [com.viasat.halite.types :as types]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(def ^:private generated-field-name :$x)

(s/defn ^:private translate-down
  "Convert a top-level bound by expressing it in terms of a new, enclosing spec."
  [generated-spec-id bound]
  {:$type generated-spec-id
   generated-field-name bound})

(s/defn ^:private translate-up
  "Convert the resulting bound by lifting the bound on the fabricated spec's field to be the
  top-level result bound."
  [bound]
  (let [{:keys [$in] :as result-bound} (generated-field-name bound)
        $in (dissoc $in :Unset)]
    (condp = (count $in)
      0 result-bound
      1 (assoc (val (first $in))
               :$type (key (first $in)))
      (assoc result-bound
             :$in $in))))

(s/defn ^:private add-spec
  "Fabricate a new spec to hold a field of the required abstract type. Add it to the context."
  [generated-spec-id sctx refines-to-spec-id]
  (ssa/add-spec-to-context sctx
                           generated-spec-id
                           (ssa/spec-to-ssa (ssa/as-spec-env sctx)
                                            {:fields {generated-field-name (-> refines-to-spec-id types/concrete-spec-type types/maybe-type)}})))

(s/defn ^:private generate-spec-id
  "Generate a unique spec-id that will not collide with the current context."
  [spec-id]
  (keyword (namespace spec-id)
           (str "$prop-top-abstract" (name spec-id))))

(s/defn propagate :- prop-abstract/SpecBound
  ([sctx :- ssa/SpecCtx
    initial-bound :- prop-abstract/SpecBound]
   (propagate sctx prop-composition/default-options initial-bound))
  ([sctx :- ssa/SpecCtx
    opts :- prop-composition/Opts
    initial-bound :- prop-abstract/SpecBound]
   (let [{:keys [$refines-to $type]} initial-bound]
     (if (or
          ;; the bound is a concrete bound
          $type
          ;; no refines-to to handle
          (empty? $refines-to))

       ;; bypass this module's functionality by passing the call straight through
       (prop-abstract/propagate sctx opts initial-bound)
       ;; if the bound is an abstract bound then perform the transformation to turn it into a bound
       ;; on a field
       (let [senv (ssa/as-spec-env sctx)
             ;; choose an abstract spec to be the field type in the synthesized spec
             primary-refines-to-spec-id (->> (keys $refines-to)
                                             (filter #(:abstract? (envs/lookup-spec senv %)))
                                             first)
             generated-spec-id (generate-spec-id primary-refines-to-spec-id)]
         (translate-up (prop-abstract/propagate (add-spec generated-spec-id sctx primary-refines-to-spec-id)
                                                opts
                                                (translate-down generated-spec-id initial-bound))))))))
