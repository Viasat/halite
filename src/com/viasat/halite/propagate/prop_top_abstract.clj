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
  bound)

(s/defn ^:private translate-up
  "Convert the resulting bound by lifting the bound on the fabricated spec's field to be the
  top-level result bound."
  [bound]
  bound)

(s/defn ^:private add-spec
  "Fabricate a new spec to hold a field of the required abstract type. Add it to the context."
  [sctx]
  sctx)

(s/defn propagate :- prop-abstract/SpecBound
  ([sctx :- ssa/SpecCtx
    initial-bound :- prop-abstract/SpecBound]
   (propagate sctx prop-composition/default-options initial-bound))
  ([sctx :- ssa/SpecCtx
    opts :- prop-composition/Opts
    initial-bound :- prop-abstract/SpecBound]
   (let [{:keys [$refines-to $type]} initial-bound]
     (if $type
       ;; if the bound is a concrete bound then bypass this module's functionality
       (prop-abstract/propagate sctx opts initial-bound)
       ;; if the bound is an abstract bound then perform the transformation to turn it into a bound
       ;; on a field
       (let [initial-bound' (translate-down initial-bound)
             sctx' (add-spec sctx)]
         (translate-up (prop-abstract/propagate sctx' opts initial-bound')))))))
