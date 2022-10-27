;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.prop-top-concrete
  "Handle the case where a top-level bound uses an abstract spec bound to reference only concrete
  specs. For these cases, introduce a new spec which is abstract, which the 'topmost' concrete spec
  refines to. As long as there is at least one abstract spec in the :$refines-to field of the spec
  this layer is not needed."
  (:require [com.viasat.halite.envs :as halite-envs]
            [com.viasat.halite.propagate.prop-abstract :as prop-abstract]
            [com.viasat.halite.propagate.prop-top-abstract :as prop-top-abstract]
            [com.viasat.halite.propagate.prop-composition :as prop-composition]
            [com.viasat.halite.transpile.ssa :as ssa :refer [SpecCtx]]
            [loom.alg]
            [schema.core :as s]))

(s/defn ^:private translate-down
  "Convert a top-level bound by expanding it to reference the generated spec."
  [generated-spec-id bound]
  (assoc-in bound [:$refines-to generated-spec-id] {}))

(s/defn ^:private translate-up
  "Convert the resulting bound by pruning out references to the generated spec."
  [generated-spec-id bound]
  (let [remove-generated-f (fn [bound]
                             (if (and (map? bound)
                                      (:$refines-to bound))
                               (update bound :$refines-to dissoc generated-spec-id)
                               bound))
        bound (remove-generated-f bound)]
    (if (and (map? bound)
             (:$in bound))
      (update bound :$in update-vals remove-generated-f)
      bound)))

(s/defn ^:private add-spec
  "Fabricate a new spec to be an abstract refinement target."
  [generated-spec-id sctx spec-env topmost-refines-to-spec-id]
  (-> sctx
      (assoc generated-spec-id
             (ssa/spec-to-ssa spec-env
                              {:abstract? true
                               :spec-vars {}
                               :constraints []
                               :refines-to {}}))
      (update topmost-refines-to-spec-id
              (fn [spec]
                (ssa/spec-to-ssa spec-env
                                 (-> spec
                                     ssa/spec-from-ssa
                                     (assoc-in [:refines-to generated-spec-id] {:expr {:$type generated-spec-id}})))))))

(s/defn ^:private generate-spec-id
  "Generate a unique spec-id that will not collide with the current context."
  [spec-id]
  (keyword (namespace spec-id)
           (str "$prop-top-concrete" (name spec-id))))

(s/defn propagate :- prop-abstract/SpecBound
  ([sctx :- ssa/SpecCtx
    initial-bound :- prop-abstract/SpecBound]
   (propagate sctx prop-composition/default-options initial-bound))
  ([sctx :- ssa/SpecCtx
    opts :- prop-composition/Opts
    initial-bound :- prop-abstract/SpecBound]
   (let [{:keys [$refines-to $type]} initial-bound
         pass-through #(prop-top-abstract/propagate sctx opts initial-bound)]
     (if (or
          ;; the bound is a concrete bound
          $type
          ;; no refines-to to handle
          (empty? $refines-to))

       ;; bypass this module's functionality by passing the call straight through
       (pass-through)

       (let [senv (ssa/as-spec-env sctx)
             refines-to-spec-ids (keys $refines-to)]
         (if (->> refines-to-spec-ids
                  (some #(boolean (:abstract? (halite-envs/lookup-spec senv %)))))
           ;; there is an abstract spec in refines-to, so lower layers can deal with it
           (pass-through)
           ;; if the bound is an abstract bound only on concrete specs then perform the transformation to
           ;; add a new, abstract spec above them
           (let [refines-to-graph (prop-abstract/spec-context-to-inverted-refinement-graph sctx)
                 topmost-refines-to-spec-id (or (->> refines-to-graph
                                                     loom.alg/topsort
                                                     (filter (set refines-to-spec-ids))
                                                     first)
                                                ;; if there are no refinements, just take one of the spec ids
                                                (first refines-to-spec-ids))
                 generated-spec-id (generate-spec-id topmost-refines-to-spec-id)]
             (translate-up generated-spec-id
                           (prop-top-abstract/propagate (add-spec generated-spec-id sctx senv topmost-refines-to-spec-id)
                                                        opts
                                                        (translate-down generated-spec-id initial-bound))))))))))
