;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.prop-top-abstract
  (:require [com.viasat.halite.propagate.prop-abstract :as prop-abstract]
            [com.viasat.halite.propagate.prop-composition :as prop-composition]
            [com.viasat.halite.transpile.ssa :as ssa :refer [SpecCtx]]
            [schema.core :as s]))

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
  (generated-field-name bound))

(s/defn ^:private add-spec
  "Fabricate a new spec to hold a field of the required abstract type. Add it to the context."
  [generated-spec-id sctx refines-to-spec-id]
  (assoc sctx
         generated-spec-id
         (ssa/spec-to-ssa (ssa/as-spec-env sctx)
                          {:spec-vars {generated-field-name refines-to-spec-id}
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
     (if (or
          ;; the bound is a concrete bound
          $type
          ;; the bound specifies multiple refinements
          (> (count $refines-to) 1))
       ;; bypass this module's functionality by passing the call straight through
       (prop-abstract/propagate sctx opts initial-bound)
       ;; if the bound is an abstract bound then perform the transformation to turn it into a bound
       ;; on a field
       (let [refines-to-spec-id (key (first $refines-to))
             generated-spec-id (generate-spec-id refines-to-spec-id)]
         (translate-up (prop-abstract/propagate (add-spec generated-spec-id sctx refines-to-spec-id)
                                                opts
                                                (translate-down generated-spec-id initial-bound))))))))
