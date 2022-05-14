;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.transpile.simplify
  "Simplify halite specs by evaluating those parts of the spec
  that are statically evaluable."
  (:require [jibe.halite.halite-envs :as halite-envs]
            [jibe.halite.transpile.rewriting :as rewriting]
            [jibe.halite.transpile.ssa :as ssa]
            [jibe.halite.transpile.util :refer [fixpoint]]
            [schema.core :as s]))

(defn- deref-form [dgraph id]
  (some->> id (ssa/deref-id dgraph) first))

(s/defn ^:private simplify-and
  [{:keys [dgraph] :as ctx} :- ssa/SSACtx, id, [form htype :as d]]
  (when (and (seq? form) (= 'and (first form)))
    (let [child-terms (->> (rest form)
                           (map #(vector % (deref-form dgraph %)))
                           (remove (comp true? second)))]
      (cond
        ;; every term was literal true -- collapse to literal true
        (empty? child-terms)
        true

        ;; every term that isn't literal true is literal false -- collapse to literal false
        (every? false? (map second child-terms))
        false

        ;; there's just a single term that wasn't literal true
        (= 1 (count (set child-terms)))
        (ffirst child-terms)

        ;; there are multiple literal false terms (and at least one non-false term)
        (< 1 (count (filter false? (map second child-terms))))
        (apply list 'and false (map first (remove (comp false? second) child-terms)))))))

(s/defn ^:private simplify-not
  [{:keys [dgraph] :as ctx} :- ssa/SSACtx, id [form htype]]
  (when (and (seq? form) (= 'not (first form)))
    (let [subform (deref-form dgraph (second form))]
      (when (boolean? subform)
        (not subform)))))

(s/defn ^:private simplify-if-static-pred
  [{:keys [dgraph] :as ctx} :- ssa/SSACtx, id, [form htype]]
  (when (and (seq? form) (= 'if (first form)))
    (let [[_if pred-id then-id else-id] form
          subform (deref-form dgraph pred-id)]
      (when (boolean? subform)
        (if subform then-id else-id)))))

(defn- always-evaluates?
  "True if the given form cannot possibly produce a runtime error during evaluation."
  [form]
  (or
   (integer? form)
   (boolean? form)
   (symbol? form)))

(s/defn ^:private simplify-if-static-branches
  [{:keys [dgraph] :as ctx} :- ssa/SSACtx, id, [form htype]]
  (when (and (seq? form) (= 'if (first form)))
    (let [[_ pred-id then-id else-id] form
          pred (deref-form dgraph pred-id)]
      (when (always-evaluates? pred)
        (let [then (deref-form dgraph then-id)
              else (deref-form dgraph else-id)]
          (cond
            (and (true? then) (true? else)) true
            (and (false? then) (false? else)) false
            (and (true? then) (false? else)) pred-id
            (and (false? then) (true? else)) (ssa/negated dgraph pred-id)
            (= then else) then-id))))))


(s/defn ^:private simplify-step :- ssa/SpecCtx
  [sctx :- ssa/SpecCtx]
  (-> sctx
      (rewriting/rewrite-sctx simplify-and)
      (rewriting/rewrite-sctx simplify-not)
      (rewriting/rewrite-sctx simplify-if-static-pred)
      (rewriting/rewrite-sctx simplify-if-static-branches)))

(s/defn simplify :- ssa/SpecCtx
  "Perform semantics-preserving simplifications on the expressions in the specs."
  [sctx :- ssa/SpecCtx]
  (fixpoint simplify-step sctx))
