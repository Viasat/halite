;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.transpile.simplify
  "Simplify halite specs by evaluating those parts of the spec
  that are statically evaluable."
  (:require [jibe.halite.envs :as halite-envs]
            [jibe.halite.transpile.ssa :as ssa]
            [jibe.halite.transpile.util :refer [fixpoint]]
            [schema.core :as s]))

(defn- deref-form [dgraph id]
  (some->> id (ssa/deref-id dgraph) first))

(s/defn ^:private simplify-and :- ssa/SSACtx
  [{:keys [dgraph] :as ctx} :- ssa/SSACtx]
  (->> dgraph
       (filter (fn [[id [form]]]
                 (and (seq? form)
                      (= 'and (first form))
                      (every? boolean?
                              (map (partial deref-form dgraph) (rest form))))))
       (reduce
        (fn [dgraph [id [form htype :as d]]]
          (let [child-terms (map (partial deref-form dgraph) (rest form))]
            (first (ssa/form-to-ssa (assoc ctx :dgraph dgraph) id (every? true? child-terms)))))
        dgraph)
       (assoc ctx :dgraph)))

(s/defn ^:private simplify-not :- ssa/SSACtx
  [{:keys [dgraph] :as ctx} :- ssa/SSACtx]
  (->> dgraph
       (filter (fn [[id [form]]]
                 (and (seq? form)
                      (= 'not (first form))
                      (boolean? (deref-form dgraph (second form))))))
       (reduce
        (fn [dgraph [not-id [[_ id _]]]]
          (first (ssa/form-to-ssa (assoc ctx :dgraph dgraph) not-id (false? (deref-form dgraph id)))))
        dgraph)
       (assoc ctx :dgraph)))

(s/defn ^:private simplify-if :- ssa/SSACtx
  [{:keys [dgraph] :as ctx} :- ssa/SSACtx]
  (->> dgraph
       (filter (fn [[id [form]]]
                 (and (seq? form)
                      (= 'if (first form))
                      (boolean? (deref-form dgraph (second form))))))
       (reduce
        (fn [dgraph [id [[_ pred then-id else-id]]]]
          (->>
           (if (true? (deref-form dgraph pred)) then-id else-id)
           (ssa/rewrite-node dgraph id)))
        dgraph)
       (assoc ctx :dgraph)))

(s/defn ^:private simplify-step :- ssa/SpecInfo
  [sctx :- ssa/SpecCtx, {:keys [derivations] :as spec-info} :- ssa/SpecInfo]
  (let [senv (ssa/as-spec-env sctx)]
    (->> {:senv senv
          :tenv (halite-envs/type-env-from-spec
                 senv (dissoc spec-info :derivations))
          :env {}
          :dgraph derivations}
         (simplify-and)
         (simplify-not)
         (simplify-if)
         :dgraph
         (assoc spec-info :derivations))))

(s/defn simplify :- ssa/SpecCtx
  [sctx :- ssa/SpecCtx]
  (fixpoint #(update-vals % (partial simplify-step %)) sctx))
