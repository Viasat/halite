;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.transpile.rewriting
  "Functions to facilitate rewriting of halite specs."
  (:require [clojure.set :as set]
            [jibe.halite.halite-envs :as halite-envs]
            [jibe.halite.halite-types :as halite-types]
            [jibe.halite.transpile.ssa :as ssa
             :refer [SpecInfo SpecCtx SSACtx]]
            [schema.core :as s]
            [weavejester.dependency :as dep]))

(def ^:dynamic *rewrite-traces* nil)

(defmacro with-tracing
  [[traces-sym] & body]
  `(let [~traces-sym (atom {})]
     (binding [*rewrite-traces* ~traces-sym]
       ~@body)))

(defmacro trace! [item-form]
  `(when *rewrite-traces*
     (let [item# ~item-form
           spec-id# (:spec-id item#)]
       (swap! *rewrite-traces*
              (fn [traces#]
                (if (contains? traces# spec-id#)
                  (update traces# spec-id# conj item#)
                  (assoc traces# spec-id# [item#]))))
       (when-let [dgraph# (:dgraph' item#)]
         (when (ssa/cycle? dgraph#)
           (throw (ex-info (format "BUG! Cycle detected after %s %s" (:op item#) (:rule item#))
                           {:item item#})))))))

(defn print-trace-item [{:keys [rule op pruned-ids form form']}]
  (if rule
    (println (format "%s:  %s\n |->%s%s"
                     rule form (apply str (repeat (dec (count rule)) \space)) form'))
    (println "--- prune" (count pruned-ids) "---")))

(defn print-trace-summary* [trace]
  (doseq [item trace]
    (print-trace-item item)))

(defmacro print-trace-summary
  [body spec-id]
  `(rewriting/with-tracing [traces#]
     (let [spec-id# ~spec-id]
       (try
         ~body
         (finally
           (print-trace-summary* (get @traces# spec-id#)))))))

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

(s/defschema RewriteRule
  {:rule-name s/Str
   :rewrite-fn (s/pred ifn?)
   ;; :target (s/enum :nodes :constraints)
   :nodes (s/enum :all :constraints)})

(defn- apply-rule-to-id [rule sctx ctx scope spec-id spec-info id & [replacement]]
  (let [{:keys [rule-name rewrite-fn nodes]} rule
        dgraph (:dgraph ctx)
        deriv (dgraph id)
        form (rewrite-fn sctx ctx id deriv)
        replace? (= :replace replacement)]
    (when (and replace? (->> form (tree-seq coll? seq) (some #(= % id))))
      (throw (ex-info (format  "BUG! Rewrite rule %s used rewritten node id in replacement form!"
                               rule-name rule)
                      {:dgraph dgraph :id id :form form})))
    (if (some? form)
      (let [dgraph' (or (:dgraph (meta form)) dgraph)
            ctx (assoc ctx :dgraph dgraph')
            [dgraph' id'] (if replace?
                            (ssa/form-to-ssa ctx id form)
                            (ssa/form-to-ssa ctx form))]
        (trace!
         (binding [ssa/*elide-top-level-bindings* true]
           (cond->
            {:op :rewrite
             :rule rule-name
             :dgraph dgraph
             :dgraph' dgraph'
             :id id
             :form (ssa/form-from-ssa scope dgraph id)
             :id' id'
             :result form
             :form' (ssa/form-from-ssa scope dgraph' id')}
             spec-id (assoc :spec-id spec-id)
             spec-info (assoc :spec-info (assoc spec-info :derivations dgraph)))))
        [dgraph' id'])
      [dgraph id])))

(s/defn rewrite-spec-constraints :- SpecInfo
  [rule :- RewriteRule, sctx :- SpecCtx, spec-id :- halite-types/NamespacedKeyword]
  (let [spec-info (get sctx spec-id)
        {:keys [tenv] :as ctx} (ssa/make-ssa-ctx sctx spec-info)
        scope (->> tenv (halite-envs/scope) keys set)]
    (->> (:constraints spec-info)
         (reduce (fn [spec-info [cname cid]]
                   (let [dgraph (:derivations spec-info)
                         [dgraph' cid'] (apply-rule-to-id rule sctx (assoc ctx :dgraph dgraph) scope spec-id spec-info cid)]
                     (-> spec-info
                         (assoc :derivations dgraph')
                         (update :constraints conj [cname cid']))))
                 (assoc spec-info :constraints []))
         (prune spec-id (:rule-name rule)))))

(s/defn rewrite-spec-derivations :- SpecInfo
  [rule :- RewriteRule, sctx :- SpecCtx, spec-id :- halite-types/NamespacedKeyword]
  (let [spec-info (get sctx spec-id)
        {:keys [tenv] :as ctx} (ssa/make-ssa-ctx sctx spec-info)
        scope (->> tenv (halite-envs/scope) keys set)]
    (->> (keys (:derivations spec-info))
         (reduce
          (fn [{:keys [dgraph] :as ctx} id]
            (let [[dgraph' id'] (apply-rule-to-id rule sctx ctx scope spec-id spec-info id :replace)]
              (assoc ctx :dgraph dgraph')))
          ctx)
         :dgraph
         (assoc spec-info :derivations)
         (prune spec-id (:rule-name rule)))))

(s/defn rewrite-spec :- SpecInfo
  [rule :- RewriteRule, sctx :- SpecCtx, spec-id :- halite-types/NamespacedKeyword]
  ((condp = (:nodes rule)
     :all rewrite-spec-derivations
     :constraints rewrite-spec-constraints
     (throw (ex-info (format "BUG! Invalid rule, %s not recognized" (:nodes rule))
                     {:rule rule})))
   rule sctx spec-id))

(s/defn rewrite-sctx* :- SpecCtx
  [rule :- RewriteRule, sctx :- SpecCtx]
  (let [rewrite-machine (condp = (:nodes rule)
                          :all rewrite-spec
                          :constraints rewrite-spec-constraints
                          :else (throw (ex-info (format "BUG! Invalid rule, %s not recognized" (:nodes rule))
                                                {:rule rule})))]
    (->> sctx keys (map #(vector % (rewrite-spec rule sctx %))) (into {}))))

(s/defn rewrite-in-dependency-order* :- SpecCtx
  [rule :- RewriteRule, deps-fn :- (s/pred ifn?), sctx :- SpecCtx]
  (as-> (dep/graph) dg
    ;; ensure that everything is in the dependency graph, depending on :nothing
    (reduce #(dep/depend %1 %2 :nothing) dg (keys sctx))
    ;; add the deps for each spec
    (reduce
     (fn [dg [spec-id spec]]
       (reduce #(dep/depend %1 spec-id %2) dg (deps-fn spec)))
     dg
     sctx)
    ;; rewrite in the correct order
    (->> dg
         (dep/topo-sort)
         (remove #(= :nothing %))
         (reduce
          (fn [sctx spec-id]
            (assoc sctx spec-id (rewrite-spec rule sctx spec-id)))
          sctx))))

(defmacro rewrite-sctx
  [sctx rewrite-fn-sym]
  (when-not (symbol? rewrite-fn-sym)
    (throw (ex-info "second arg to rewrite-sctx must be a symbol resolving to a rewrite fn" {})))
  `(rewrite-sctx*
    {:rule-name ~(name rewrite-fn-sym) :rewrite-fn ~rewrite-fn-sym, :nodes :all}
    ~sctx))

(defmacro ->>rewrite-sctx [rewrite-fn-sym sctx] `(rewrite-sctx ~sctx ~rewrite-fn-sym))
