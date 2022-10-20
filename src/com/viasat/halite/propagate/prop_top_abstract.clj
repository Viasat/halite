;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.prop-top-abstract
  (:require [com.viasat.halite.propagate.prop-abstract :as prop-abstract]
            [com.viasat.halite.propagate.prop-composition :as prop-composition]
            [com.viasat.halite.transpile.ssa :as ssa :refer [SpecCtx]]
            [schema.core :as s]))

(s/defn ^:private translate-down
  "Convert a top-level bound by expressing it in terms of a new, enclosing spec."
  [bound]
  {:$type :ws/Q
   :q bound})

(s/defn ^:private translate-up
  "Convert the resulting bound by lifting the bound on the fabricated spec's field to be the
  top-level result bound."
  [bound]
  (:q bound))

(s/defn ^:private add-spec
  "Fabricate a new spec to hold a field of the required abstract type. Add it to the context."
  [sctx refines-to-spec-id]
  (assoc sctx
         ;; how to generate a unique spec name?
         :ws/Q
         (ssa/spec-to-ssa (ssa/as-spec-env sctx)
                          {:spec-vars {:q refines-to-spec-id}
                           :constraints []
                           :refines-to {}})))

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
       (translate-up (prop-abstract/propagate (add-spec sctx (key (first $refines-to)))
                                              opts
                                              (translate-down initial-bound)))))))
