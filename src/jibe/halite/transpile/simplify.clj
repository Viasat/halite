;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.transpile.simplify
  "Simplify halite specs by evaluating those parts of the spec
  that are statically evaluable."
  (:require [jibe.halite.halite-envs :as halite-envs]
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
                      ;; if are boolean literals, then maybe we can improve things
                      ;;(seq (filter (comp boolean? (partial deref-form dgraph)) (rest form)))
                      )))
       (reduce
        (fn [dgraph [id [form htype :as d]]]
          (let [child-terms (->> (rest form)
                                 (map #(vector % (deref-form dgraph %)))
                                 (remove (comp true? second)))
                ctx (assoc ctx :dgraph dgraph)]
            (cond
              ;; every term was literal true -- collapse to literal true
              (empty? child-terms)
              (first (ssa/form-to-ssa ctx id true))

              ;; every term that isn't literal true is literal false -- collapse to literal false
              (every? false? (map second child-terms))
              (first (ssa/form-to-ssa ctx id false))

              ;; there's just a single term that wasn't literal true
              (= 1 (count (set child-terms)))
              (ssa/rewrite-node dgraph id (ffirst child-terms))

              ;; there are multiple literal false terms (and at least one non-false term)
              (< 1 (count (filter false? (map second child-terms))))
              (first (ssa/form-to-ssa ctx id (apply list 'and false (map first (remove (comp false? second) child-terms)))))

              ;; there was nothing to do
              :else dgraph)))
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

(s/defn ^:private simplify-if-static-pred :- ssa/SSACtx
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

(defn- eliminatable?
  "True if the given form can be safely eliminated without changing semantics"
  [form]
  (or
   (integer? form)
   (boolean? form)
   (symbol? form)))

(s/defn ^:private simplify-if-static-branches :- ssa/SSACtx
  [{:keys [dgraph] :as ctx} :- ssa/SSACtx]
  (->> dgraph
       (filter (fn [[id [form]]]
                 (and (seq? form)
                      (= 'if (first form))
                      (let [[_ pred-id then-id else-id] form]
                        (eliminatable? (deref-form dgraph pred-id))))))
       (reduce
        (fn [dgraph [id [[_ pred-id then-id else-id]]]]
          (let [then (deref-form dgraph then-id)
                else (deref-form dgraph else-id)
                new-form (cond
                           (and (true? then) (true? else)) true
                           (and (false? then) (false? else)) false
                           (and (true? then) (false? else)) pred-id
                           (and (false? then) (true? else)) (ssa/negated dgraph pred-id)
                           (= then else) then-id
                           :else id)]
            (if (not= id new-form)
              (first
               (ssa/form-to-ssa
                (assoc ctx :dgraph dgraph)
                id
                new-form))
              dgraph)))
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
         (simplify-if-static-pred)
         (simplify-if-static-branches)
         :dgraph
         (assoc spec-info :derivations))))

(s/defn simplify :- ssa/SpecCtx
  "Perform semantics-preserving simplifications on the expressions in the specs."
  [sctx :- ssa/SpecCtx]
  (fixpoint #(update-vals % (partial simplify-step %)) sctx))
