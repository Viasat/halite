;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.transpile.simplify
  "Simplify halite specs by evaluating those parts of the spec
  that are statically evaluable."
  (:require [com.viasat.halite.transpile.rewriting :as rewriting]
            [com.viasat.halite.transpile.ssa :as ssa]
            [com.viasat.halite.transpile.util :refer [fixpoint]]
            [com.viasat.halite.types :as types]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(defn- deref-form [ssa-graph id]
  #_(when (ssa/contains-id? ssa-graph id)
      (first (ssa/deref-id ssa-graph id)))
  (->> id (ssa/deref-id ssa-graph) first))

(s/defn ^:private simplify-and
  [{{:keys [ssa-graph]} :ctx} :- rewriting/RewriteFnCtx, id, [form htype :as d]]
  (when (and (seq? form) (= 'and (first form)))
    (let [child-terms (->> (rest form)
                           (map #(vector % (deref-form ssa-graph %)))
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
  [{{:keys [ssa-graph]} :ctx} :- rewriting/RewriteFnCtx, id, [form htype]]
  (when (and (seq? form) (= 'not (first form)))
    (let [subform (deref-form ssa-graph (second form))]
      (when (boolean? subform)
        (not subform)))))

(s/defn ^:private simplify-if-static-pred
  [{{:keys [ssa-graph]} :ctx} :- rewriting/RewriteFnCtx, id, [form htype]]
  (when (and (seq? form) (= 'if (first form)))
    (let [[_if pred-id then-id else-id] form
          subform (deref-form ssa-graph pred-id)]
      (when (boolean? subform)
        (if subform then-id else-id)))))

(s/defn always-evaluates? :- s/Bool
  "True if the given form cannot possibly produce a runtime error during evaluation."
  [ssa-graph :- ssa/SSAGraph, form]
  (cond
    (or (integer? form) (boolean? form) (symbol? form) (string? form)) true
    (seq? form) (let [[op & arg-ids] form
                      args-evaluate? (reduce
                                      (fn [r arg-id]
                                        (if (keyword? arg-id)
                                          true
                                          (if (always-evaluates? ssa-graph (first (ssa/deref-id ssa-graph arg-id)))
                                            true
                                            (reduced false))))
                                      true
                                      arg-ids)]
                  (case op
                    (< <= > >= and or not => abs = not= get valid? $value? $value! if $do!) args-evaluate?
                    ;; when divisor is statically known not to be zero, we know mod will evaluate
                    mod (and args-evaluate?
                             (let [[form htype] (ssa/deref-id ssa-graph (second arg-ids))]
                               (and (integer? form) (not= 0 form))))
                    ;; assume false by default
                    false))
    :else false))

(s/defn ^:private simplify-if-static-branches
  [{{:keys [ssa-graph]} :ctx} :- rewriting/RewriteFnCtx, id, [form htype]]
  (when (and (seq? form) (= 'if (first form)))
    (let [[_ pred-id then-id else-id] form
          pred (deref-form ssa-graph pred-id)]
      (when (always-evaluates? ssa-graph pred)
        (let [then (deref-form ssa-graph then-id)
              else (deref-form ssa-graph else-id)
              if-value? (and (seq? pred) (= '$value? (first pred)))]
          (cond
            (and (true? then) (true? else)) true
            (and (false? then) (false? else)) false
            (and (true? then) (false? else) (not if-value?)) pred-id
            (and (false? then) (true? else) (not if-value?)) (ssa/negated ssa-graph pred-id)
            (= then else) then-id))))))

(s/defn simplify-do
  [{{:keys [ssa-graph]} :ctx} :- rewriting/RewriteFnCtx, id, [form htype]]
  (when (and (seq? form) (= '$do! (first form)))
    (let [side-effects (->> form (rest) (butlast)
                            (remove (comp (partial always-evaluates? ssa-graph) (partial deref-form ssa-graph))))]
      (cond
        (empty? side-effects) (last form)
        (< (count side-effects) (- (count form) 2)) `(~'$do! ~@side-effects ~(last form))))))

(s/defn ^:private simplify-no-value
  [{{:keys [ssa-graph]} :ctx} :- rewriting/RewriteFnCtx, id, [form htype]]
  (when (and (seq? form) (= '$value? (first form)) (= :Unset (deref-form ssa-graph (second form))))
    false))

(s/defn simplify-statically-known-value?
  [{{:keys [ssa-graph]} :ctx} :- rewriting/RewriteFnCtx, id, [form htype]]
  (when (and (seq? form) (= '$value? (first form)) (not (types/maybe-type? (second (ssa/deref-id ssa-graph (second form))))))
    true))

(s/defn simplify-redundant-value!
  [{{:keys [ssa-graph]} :ctx} :- rewriting/RewriteFnCtx, id, [form htype]]
  (when (and (seq? form) (= '$value! (first form)))
    (let [[_ inner-htype] (ssa/deref-id ssa-graph (second form))]
      (when-not (types/maybe-type? inner-htype)
        (second form)))))

(def ^:private comparison-fns
  "Comparisons that can be evaluated if all the arguments are literals, and the clojure fns for evaluating them."
  {'= =
   'not= not=
   '< <
   '<= <=
   '> >
   '>= >=})

(s/defn simplify-statically-known-comparisons
  [{{:keys [ssa-graph]} :ctx} :- rewriting/RewriteFnCtx, id, [form htype]]
  (when (and (seq? form) (contains? comparison-fns (first form)))
    (let [args (map #(ssa/node-form (ssa/deref-id ssa-graph %)) (rest form))]
      (when (every? #(or (integer? %) (string? %) (boolean? %) (keyword? %)) args)
        (apply (comparison-fns (first form)) args)))))

(s/defn simplify :- ssa/SpecCtx
  "Perform semantics-preserving simplifications on the expressions in the specs."
  [sctx :- ssa/SpecCtx]
  (rewriting/rewrite-reachable-sctx
   sctx
   [(rewriting/rule simplify-and)
    (rewriting/rule simplify-not)
    (rewriting/rule simplify-if-static-pred)
    (rewriting/rule simplify-if-static-branches)
    (rewriting/rule simplify-do)
    (rewriting/rule simplify-no-value)
    (rewriting/rule simplify-statically-known-value?)
    (rewriting/rule simplify-redundant-value!)
    (rewriting/rule simplify-statically-known-comparisons)]))
