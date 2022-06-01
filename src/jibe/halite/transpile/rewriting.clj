;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.transpile.rewriting
  "Functions to facilitate rewriting of halite specs."
  (:require [clojure.set :as set]
            [jibe.halite.halite-envs :as halite-envs]
            [jibe.halite.halite-types :as halite-types]
            [jibe.halite.transpile.ssa :as ssa
             :refer [SpecInfo SpecCtx SSACtx]]
            [schema.core :as s]))

(def ^:dynamic *rewrite-traces* nil)

(defmacro with-tracing
  [[traces-sym] & body]
  `(let [~traces-sym (atom {})]
     (binding [*rewrite-traces* ~traces-sym]
       ~@body)))

(defmacro ^:private trace! [item-form]
  `(when *rewrite-traces*
     (let [item# ~item-form
           spec-id# (:spec-id item#)]
       (swap! *rewrite-traces*
              (fn [traces#]
                (if (contains? traces# spec-id#)
                  (update traces# spec-id# conj item#)
                  (assoc traces# spec-id# [item#])))))))

(defn- prune
  [spec-id after {:keys [derivations] :as spec-info}]
  (let [spec-info' (ssa/prune-derivations spec-info false)
        derivations' (:derivations spec-info')
        ids (->> derivations keys set), ids' (->> derivations' keys set)
        pruned-ids (set/difference ids ids')]
    (when (seq pruned-ids)
      (trace!
       (binding [ssa/*elide-top-level-bindings* true]
         {:op :prune
          :spec-id spec-id
          :dgraph derivations
          :dgraph' derivations'
          :pruned-ids pruned-ids
          :pruned (map #(ssa/form-from-ssa spec-info %) pruned-ids)})))
    spec-info'))

(s/defn rewrite-dgraph :- ssa/Derivations
  [{:keys [dgraph tenv] :as ctx} :- ssa/SSACtx, rewrite-rule-name :- s/Str, rewrite-fn & [spec-id spec-info]]
  (let [scope (->> tenv (halite-envs/scope) keys set)]
    (->> dgraph
         (reduce
          (fn [ctx [id deriv]]
            (let [form (rewrite-fn ctx id deriv)]
              (if (some? form)
                (let [dgraph (or (:dgraph (meta form)) (:dgraph ctx))
                      [dgraph' id'] (->> form (ssa/form-to-ssa (assoc ctx :dgraph dgraph) id))]
                  (trace!
                   (binding [ssa/*elide-top-level-bindings* true]
                     (cond->
                         {:op :rewrite
                          :rule rewrite-rule-name
                          :dgraph dgraph
                          :dgraph' dgraph'
                          :id id
                          :form (ssa/form-from-ssa scope dgraph id)
                          :id' id'
                          :result form
                          :form' (ssa/form-from-ssa scope dgraph' id')}
                       spec-id (assoc :spec-id spec-id)
                       spec-info (assoc :spec-info (assoc spec-info :derivations dgraph)))))
                  (assoc ctx :dgraph dgraph'))
                ctx)))
          ctx)
         :dgraph)))

(s/defn rewrite-spec :- SpecInfo
  [sctx :- SpecCtx, spec-id :- halite-types/NamespacedKeyword, spec-info :- SpecInfo, rewrite-rule-name :- s/Str, rewrite-fn]
  (let [ctx (ssa/make-ssa-ctx sctx spec-info)]
    (->> spec-info
         (rewrite-dgraph ctx rewrite-rule-name rewrite-fn spec-id)
         (assoc spec-info :derivations)
         (prune spec-id rewrite-rule-name))))

(s/defn rewrite-sctx* :- SpecCtx
  [sctx :- SpecCtx, rewrite-rule-name, rewrite-fn]
  (reduce
   (fn [sctx spec-id]
     (update sctx spec-id #(rewrite-spec sctx spec-id % rewrite-rule-name rewrite-fn)))
   sctx
   (keys sctx)))

(defmacro rewrite-sctx
  [sctx-form rewrite-fn-sym]
  (when-not (symbol? rewrite-fn-sym)
    (throw (ex-info "second arg to rewrite-sctx must be a symbol resolving to a rewrite fn" {})))
  `(rewrite-sctx* ~sctx-form ~(name rewrite-fn-sym) ~rewrite-fn-sym))
