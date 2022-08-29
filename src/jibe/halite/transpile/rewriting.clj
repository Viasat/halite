;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.transpile.rewriting
  "Functions to facilitate rewriting of halite specs."
  (:require [clojure.set :as set]
            [clojure.core.reducers :as r]
            [jibe.halite :as halite]
            [jibe.halite.halite-envs :as halite-envs]
            [jibe.halite.halite-types :as halite-types]
            [jibe.halite.transpile.ssa :as ssa
             :refer [SpecInfo SpecCtx SSACtx]]
            [jibe.halite.transpile.util :refer [fixpoint]]
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

(defn print-trace-item [{:keys [rule op pruned-ids id id' spec-info spec-info' total-ms]}]
  (binding [ssa/*next-id* (atom 100000), ssa/*hide-non-halite-ops* false, ssa/*elide-top-level-bindings* true]
    (let [form (ssa/form-from-ssa spec-info id)
          form' (ssa/form-from-ssa spec-info' id')
          time-str (str total-ms "ms")]
      (println (format "%s:  %s\n %s|->%s%s"
                       rule form time-str (apply str (repeat (dec (- (count rule) (count time-str))) \space)) form')))))

(defn print-trace-summary [trace]
  (doseq [item trace]
    (print-trace-item item)))

(defmacro with-summarized-trace-for
  [spec-id body]
  `(rewriting/with-tracing [traces#]
     (let [spec-id# ~spec-id]
       (try
         ~body
         (finally
           (print-trace-summary (get @traces# spec-id#)))))))

(def type-error-item (atom nil))

(defn type-check-trace [senv trace]
  (doseq [{:keys [op rule spec-info spec-info'] :as item} trace]
    (when (and rule spec-info spec-info')
      (try
        (let [spec-info (binding [ssa/*next-id* (atom 100000), ssa/*hide-non-halite-ops* true] (ssa/spec-from-ssa spec-info))
              spec-info' (binding [ssa/*next-id* (atom 100000), ssa/*hide-non-halite-ops* true] (ssa/spec-from-ssa spec-info'))]
          (halite/type-check-spec senv spec-info)
          (println "ok before" rule)
          (halite/type-check-spec senv spec-info')
          (println "ok after" rule))
        (catch clojure.lang.ExceptionInfo ex
          (reset! type-error-item item)
          (let [spec-info (binding [ssa/*next-id* (atom 100000), ssa/*hide-non-halite-ops* false] (ssa/spec-from-ssa spec-info))
                spec-info' (binding [ssa/*next-id* (atom 100000), ssa/*hide-non-halite-ops* false] (ssa/spec-from-ssa spec-info'))]
            (-> spec-info :constraints first second clojure.pprint/pprint)
            (print-trace-item item)
            (-> spec-info' :constraints first second clojure.pprint/pprint))
          (throw (ex-info (str "Found type error for " rule) {} ex)))))))

(s/defschema RewriteFnCtx
  {:sctx SpecCtx
   :ctx SSACtx
   :guard #{ssa/DerivationName}})

(s/defschema RewriteRule
  {:rule-name s/Str
   :rewrite-fn (s/pred ifn?)
   ;; :target (s/enum :nodes :constraints)
   :nodes (s/enum :all :constraints)})

(defn- apply-rule-to-id [rule sctx ctx scope spec-id spec-info id]
  (let [{:keys [rule-name rewrite-fn nodes]} rule
        dgraph (:derivations spec-info)
        deriv (dgraph id)
        start-nanos (System/nanoTime)
        form (rewrite-fn {:sctx sctx :ctx (assoc ctx :dgraph dgraph) :guard #{}} id deriv)]
    (when (and (= :all nodes) (->> form (tree-seq coll? seq) (some #(= % id))))
      (throw (ex-info (format  "BUG! Rewrite rule %s used rewritten node id in replacement form!"
                               rule-name rule)
                      {:dgraph dgraph :id id :form form})))
    (if (some? form)
      (let [dgraph' (or (:dgraph (meta form)) dgraph)
            ctx (assoc ctx :dgraph dgraph')
            rule-nanos (System/nanoTime)
            [dgraph' id'] (ssa/form-to-ssa ctx form)
            spec-info' (cond-> (assoc spec-info :derivations dgraph')
                         (= :all nodes) (ssa/replace-node id id')
                         (= :constraints nodes) (update :constraints #(map (fn [[cname cid]] [cname (if (= cid id) id' cid)]) %)))
            end-nanos (System/nanoTime)
            rule-ms (/ (- rule-nanos start-nanos) 1000000.0)
            graph-ms (/ (- end-nanos rule-nanos) 1000000.0)
            total-ms (/ (- end-nanos start-nanos) 1000000.0)]
        (trace!
         {:op :rewrite
          :rule rule-name
          :id id
          :id' id'
          :result form
          :spec-id spec-id
          :spec-info spec-info
          :spec-info' spec-info'
          :total-ms total-ms
          :rule-ms rule-ms
          :graph-ms graph-ms})
        spec-info')
      spec-info)))

(defn- apply-rule-to-deriv [rule sctx ctx scope spec-id spec-info id deriv]
  (let [{:keys [rule-name rewrite-fn nodes]} rule
        dgraph (:derivations spec-info)
        start-nanos (System/nanoTime)
        form (rewrite-fn {:sctx sctx :ctx (assoc ctx :dgraph dgraph) :guard #{}} id deriv)]
    (when (and (= :all nodes) (->> form (tree-seq coll? seq) (some #(= % id))))
      (throw (ex-info (format  "BUG! Rewrite rule %s used rewritten node id in replacement form!"
                               rule-name rule)
                      {:dgraph dgraph :id id :form form})))
    (when (some? form)
      (let [dgraph' (or (:dgraph (meta form)) dgraph)
            ctx (assoc ctx :dgraph dgraph')
            rule-nanos (System/nanoTime)
            [dgraph' id'] (ssa/form-to-ssa ctx form)
            spec-info' (-> (assoc spec-info :derivations dgraph') (ssa/replace-node id id'))
            end-nanos (System/nanoTime)
            rule-ms (/ (- rule-nanos start-nanos) 1000000.0)
            graph-ms (/ (- end-nanos rule-nanos) 1000000.0)
            total-ms (/ (- end-nanos start-nanos) 1000000.0)]
        (trace!
         {:op :rewrite
          :rule rule-name
          :id id
          :id' id'
          :result form
          :spec-id spec-id
          :spec-info spec-info
          :spec-info' spec-info'
          :total-ms total-ms
          :rule-ms rule-ms
          :graph-ms graph-ms})
        spec-info'))))

(s/defn apply-to-reachable :- (s/maybe SpecInfo)
  [sctx ctx scope spec-id spec-info
   roots,
   rules :- [RewriteRule]]
  (loop [spec-info spec-info
         reached (transient #{})
         ids-to-do (into clojure.lang.PersistentQueue/EMPTY roots)]
    (if-let [id (peek ids-to-do)]
      (if (contains? reached id)
        (recur spec-info reached (pop ids-to-do))
        (let [deriv (get (:derivations spec-info) id)]
          (if-not deriv
            spec-info
            (recur (or (some #(apply-rule-to-deriv % sctx ctx scope spec-id spec-info id deriv)
                             rules)
                       spec-info)
                   (conj! reached id)
                   (-> ids-to-do
                       pop
                       (into (ssa/referenced-derivations deriv)))))))
      spec-info)))

(s/defn rewrite-reachable :- SpecInfo
  [rules :- [RewriteRule], sctx :- SpecCtx, spec-id :- halite-types/NamespacedKeyword]
  (let [spec-info (get sctx spec-id)
        {:keys [tenv] :as ctx} (ssa/make-ssa-ctx sctx spec-info)
        scope (->> tenv (halite-envs/scope) keys set)]
    (-> (fixpoint #(apply-to-reachable sctx ctx scope spec-id %
                                       (r/map second (:constraints %))
                                       rules)
                  spec-info)
        (ssa/prune-derivations false))))

(defn rewrite-reachable-sctx [sctx rules]
  (->> sctx keys (map #(vector % (rewrite-reachable rules sctx %))) (into {})))

(s/defn rewrite-spec-constraints :- SpecInfo
  [rule :- RewriteRule, sctx :- SpecCtx, spec-id :- halite-types/NamespacedKeyword spec-info]
  (let [{:keys [tenv] :as ctx} (ssa/make-ssa-ctx sctx spec-info)
        scope (->> tenv (halite-envs/scope) keys set)]
    (->> (:constraints spec-info)
         (reduce (fn [spec-info [cname cid]]
                   (apply-rule-to-id rule sctx ctx scope spec-id spec-info cid))
                 spec-info)
         (#(ssa/prune-derivations % false)))))

(s/defn rewrite-spec-derivations :- SpecInfo
  [rule :- RewriteRule, sctx :- SpecCtx, spec-id :- halite-types/NamespacedKeyword spec-info]
  (let [{:keys [tenv] :as ctx} (ssa/make-ssa-ctx sctx spec-info)
        scope (->> tenv (halite-envs/scope) keys set)
        reachable? (ssa/reachable-derivations spec-info)]
    (reduce
     #(apply-rule-to-id rule sctx ctx scope spec-id %1 %2)
     spec-info
     reachable?)))

(s/defn rewrite-spec :- SpecInfo
  [rule :- RewriteRule, sctx :- SpecCtx, spec-id :- halite-types/NamespacedKeyword, spec-info]
  ((condp = (:nodes rule)
     :all rewrite-spec-derivations
     :constraints rewrite-spec-constraints
     (throw (ex-info (format "BUG! Invalid rule, %s not recognized" (:nodes rule))
                     {:rule rule})))
   rule sctx spec-id spec-info))

(s/defn rewrite-sctx* :- SpecCtx
  [rule :- RewriteRule, sctx :- SpecCtx]
  (reduce-kv
   (fn [m spec-id spec-info]
     (assoc m spec-id (rewrite-spec rule sctx spec-id spec-info)))
   sctx
   sctx))

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
            (assoc sctx spec-id (rewrite-spec rule sctx spec-id (sctx spec-id))))
          sctx))))

(defmacro rule [rewrite-fn-sym]
  (when-not (symbol? rewrite-fn-sym)
    (throw (ex-info "second arg to rewrite-sctx must be a symbol resolving to a rewrite fn" {})))
  `{:rule-name ~(name rewrite-fn-sym) :rewrite-fn ~rewrite-fn-sym, :nodes :all})

(defmacro rewrite-sctx
  [sctx rewrite-fn-sym]
  `(rewrite-sctx* (rule ~rewrite-fn-sym) ~sctx))

(defmacro ->>rewrite-sctx [rewrite-fn-sym sctx] `(rewrite-sctx ~sctx ~rewrite-fn-sym))
