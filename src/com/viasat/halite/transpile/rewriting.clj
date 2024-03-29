;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.transpile.rewriting
  "Functions to facilitate rewriting of halite specs."
  (:require [clojure.set :as set]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.transpile.ssa :as ssa]
            [com.viasat.halite.transpile.transpile-util :as transpile-util]
            [com.viasat.halite.type-check :as type-check]
            [com.viasat.halite.types :as types]
            [schema.core :as s]
            [weavejester.dependency :as dependency]))

(set! *warn-on-reflection* true)

(def ^:dynamic *rewrite-traces* nil)

;; If true, type check the input and output graph of every rewrite rule
;; execution when tracing is on. See `type-check-traces` as an alternative.
(def ^:dynamic *typecheck-trace* false)

(defmacro with-tracing
  [[traces-sym] & body]
  `(let [~traces-sym (atom {})]
     (binding [*rewrite-traces* ~traces-sym]
       ~@body)))

(defn print-trace-item [{:keys [rule op pruned-ids id id' spec-info spec-info' total-ms]}]
  (binding [ssa/*hide-non-halite-ops* false, ssa/*elide-top-level-bindings* true]
    (let [form (some->> id (ssa/form-from-ssa spec-info))
          form' (some->> id' (ssa/form-from-ssa spec-info'))
          time-str (str total-ms "ms")]
      (println (format "%s:  %s\n %s|->%s%s"
                       rule form time-str (apply str (repeat (dec (- (count rule) (count time-str))) \space)) form')))))

(def type-error-item (atom nil))

(defn type-check-graph [senv ssa-graph stage item & {:keys [verbose?]}]
  (try
    (binding [ssa/*hide-non-halite-ops* true]
      (type-check/type-check-spec senv (ssa/spec-from-ssa ssa-graph))
      (when verbose? (println "ok" stage (:rule item))))
    (catch Exception ex
      (reset! type-error-item item)
      (let [msg (format "Found type error %s %s %s"
                        (:spec-id item) stage (:rule item))]
        (println msg)
        (throw (ex-info msg {:error-in-spec-id (:spec-id item)} ex))))))

(defn type-check-trace [senv trace]
  (let [senv (or senv
                 (-> trace meta :senv)
                 (throw (ex-info "senv is required for type-check" {})))]
    (->> (cons nil trace)
         (partition-all 2 1)
         (run! (fn [[previous item]]
                 ;; type check :ssa-graph only if we didn't in the
                 ;; previous step
                 (when (and (:ssa-graph item)
                            (not= (:ssa-graph item)
                                  (:ssa-graph' previous)))
                   (type-check-graph
                    senv (:ssa-graph item) :before item :verbose? true))
                 (type-check-graph
                  senv (:ssa-graph' item) :after item :verbose? true))))))

(defn type-check-traces [traces]
  (->> (vals traces)
       (run! (partial type-check-trace nil))))

(defn type-check-trace-item
  [senv
   {:keys [rule spec-info spec-info'] :as item}]
  (when (and rule spec-info')
    (let [report-err
          (fn [stage ex]
            (reset! type-error-item item)
            (println (format "Found type error %s %s" stage rule))
            (binding [ssa/*hide-non-halite-ops* true]
              (print-trace-item item))
            (throw (ex-info (format "Found type error %s %s" stage rule)
                            {:error-in-spec-id (:spec-id item)}
                            ex)))]
      (when spec-info
        (type-check-graph senv spec-info :before item))
      (type-check-graph senv spec-info' :after item))))

(defmacro trace! [sctx item-form]
  `(when *rewrite-traces*
     (let [senv# (ssa/as-spec-env ~sctx)
           item# ~item-form
           spec-id# (:spec-id item#)]
       (swap! *rewrite-traces* update spec-id# (fnil conj []) item#)
       (when-let [dgraph# (:dgraph' item#)]
         (when (ssa/cycle? dgraph#)
           (throw (ex-info (format "BUG! Cycle detected after %s %s" (:op item#) (:rule item#))
                           {:item item#}))))
       (when *typecheck-trace*
         (type-check-trace-item senv# item#)))))

(defn squash-trace* [sctx' start-nanos item subtrace]
  (let [total-ms (/ (- (System/nanoTime) start-nanos) 1000000.0)]
    (->> subtrace
         (run! (fn [[spec-id trace-vec]]
                 (trace! sctx' (merge
                                {:op :rewrite
                                 :rule "squashed"
                                 :total-ms total-ms}
                                (select-keys (peek trace-vec) [:spec-id :spec-info'])
                                item)))))))

(defmacro squash-trace! [item-form & body]
  `(let [start-nanos# (System/nanoTime)
         subtrace# (atom {})
         sctx'# (binding [*rewrite-traces* subtrace#
                          *typecheck-trace* false]
                  ~@body)]
     (squash-trace* sctx'# start-nanos# ~item-form @subtrace#)
     sctx'#))

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

(s/defschema RewriteFnCtx
  {:sctx ssa/SpecCtx :ctx ssa/SSACtx})

(s/defschema RewriteRule
  {:rule-name s/Str
   :rewrite-fn (s/pred ifn?)
   ;; :target (s/enum :nodes :constraints)
   :nodes (s/enum :all :constraints)})

(defn- apply-rule-to-node [rule sctx ctx scope spec-id spec-info id node]
  (let [{:keys [rule-name rewrite-fn nodes]} rule
        ssa-graph (:ssa-graph spec-info)
        start-nanos (System/nanoTime)
        form (rewrite-fn {:sctx sctx :ctx ctx} id node)]
    (when (and (= :all nodes) (->> form (tree-seq coll? seq) (some #(= % id))))
      (throw (ex-info (format  "BUG! Rewrite rule %s used rewritten node id in replacement form!"
                               rule-name rule)
                      {:ssa-graph ssa-graph :id id :form form})))
    (when (some? form)
      (try
        (let [ssa-graph' (or (:ssa-graph (meta form)) ssa-graph)
              ctx (assoc ctx :ssa-graph ssa-graph')
              rule-nanos (System/nanoTime)
              [ssa-graph' id'] (ssa/form-to-ssa ctx form)
              spec-info'
              (cond-> (assoc spec-info :ssa-graph ssa-graph')
                (= :all nodes) (ssa/replace-nodes (:senv ctx) {id id'})
                (= :constraints nodes) (update :constraints #(map (fn [[cname cid]] [cname (if (= cid id) id' cid)]) %)))
              end-nanos (System/nanoTime)
              rule-ms (/ (- rule-nanos start-nanos) 1000000.0)
              graph-ms (/ (- end-nanos rule-nanos) 1000000.0)
              total-ms (/ (- end-nanos start-nanos) 1000000.0)]
          (when (not= spec-info spec-info')
            (trace!
             sctx
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
            [spec-info' (ssa/deref-id ssa-graph' id')]))
        (catch Exception ex
          (trace!
           sctx
           {:op :rewrite
            :rule rule-name
            :id id
            :result form
            :spec-id spec-id
            :spec-info spec-info
            :error ex})
          (throw ex))))))

(s/defn add-constraint :- ssa/SpecInfo
  [rule-name :- s/Str, sctx :- ssa/SpecCtx, spec-id :- types/NamespacedKeyword, spec-info :- ssa/SpecInfo, cname :- base/ConstraintName, expr]
  (let [ctx (ssa/make-ssa-ctx sctx spec-info)
        [ssa-graph id] (ssa/form-to-ssa ctx expr)
        spec-info' (-> spec-info
                       (assoc :ssa-graph ssa-graph)
                       (update :constraints conj [cname id]))]
    (trace!
     sctx
     {:op :add-constraint
      :rule rule-name
      :id' id
      :result expr
      :spec-id spec-id
      :spec-info spec-info
      :spec-info' spec-info'})
    spec-info'))

(s/defn apply-to-reachable :- (s/maybe ssa/SpecInfo)
  [sctx ctx scope spec-id spec-info
   roots,
   rules :- [RewriteRule]]
  (loop [{:keys [ssa-graph] :as spec-info} spec-info
         reached (transient #{})
         ids-to-do (into clojure.lang.PersistentQueue/EMPTY roots)]
    (if-let [id (peek ids-to-do)]
      (if (contains? reached id)
        (recur spec-info reached (pop ids-to-do))
        (if (ssa/contains-id? ssa-graph id)
          (let [node (ssa/deref-id ssa-graph id)
                [spec-info' node']
                ,(or (some #(apply-rule-to-node % sctx (assoc ctx :ssa-graph ssa-graph) scope spec-id spec-info id node) rules)
                     [spec-info node])
                spec-info' (cond-> spec-info' (not= spec-info' spec-info) (ssa/prune-ssa-graph false))]
            (recur spec-info'
                   (conj! reached id)
                   (-> ids-to-do
                       pop
                       (into (ssa/child-nodes node')))))
          spec-info))
      spec-info)))

(s/defn rewrite-reachable :- ssa/SpecInfo
  [rules :- [RewriteRule], sctx :- ssa/SpecCtx, spec-id :- types/NamespacedKeyword]
  (let [spec-info (get sctx spec-id)
        {:keys [tenv] :as ctx} (ssa/make-ssa-ctx sctx spec-info)
        scope (envs/tenv-keys tenv)]
    (transpile-util/fixpoint #(apply-to-reachable sctx ctx scope spec-id %
                                                  (concat (map second (:constraints %))
                                                          (map (comp :expr val) (:refines-to %)))
                                                  rules)
                             spec-info)))

(defn rewrite-reachable-sctx [sctx rules]
  (->> sctx keys (map #(vector % (rewrite-reachable rules sctx %))) (into {})))

(defn- apply-rule-to-id [rule sctx ctx scope spec-id spec-info id]
  (let [result (apply-rule-to-node rule sctx ctx scope spec-id spec-info id (ssa/deref-id (:ssa-graph spec-info) id))]
    (if (some? result)
      (first result)
      spec-info)))

(s/defn rewrite-spec-constraints :- ssa/SpecInfo
  [rule :- RewriteRule, sctx :- ssa/SpecCtx, spec-id :- types/NamespacedKeyword spec-info]
  (let [{:keys [tenv] :as ctx} (ssa/make-ssa-ctx sctx spec-info)
        scope (envs/tenv-keys tenv)]
    (->> (:constraints spec-info)
         (reduce (fn [spec-info [cname cid]]
                   (apply-rule-to-id rule sctx ctx scope spec-id spec-info cid))
                 spec-info)
         (#(ssa/prune-ssa-graph % false)))))

(s/defn ^:private rewrite-ssa-graph :- ssa/SpecInfo
  [rule :- RewriteRule, sctx :- ssa/SpecCtx, spec-id :- types/NamespacedKeyword spec-info]
  (let [{:keys [tenv] :as ctx} (ssa/make-ssa-ctx sctx spec-info)
        scope (envs/tenv-keys tenv)
        reachable? (ssa/reachable-nodes spec-info)]
    (reduce
     #(apply-rule-to-id rule sctx (assoc ctx :ssa-graph (:ssa-graph %1)) scope spec-id %1 %2)
     spec-info
     reachable?)))

(s/defn rewrite-spec :- ssa/SpecInfo
  [rule :- RewriteRule, sctx :- ssa/SpecCtx, spec-id :- types/NamespacedKeyword, spec-info]
  ((condp = (:nodes rule)
     :all rewrite-ssa-graph
     :constraints rewrite-spec-constraints
     (throw (ex-info (format "BUG! Invalid rule, %s not recognized" (:nodes rule))
                     {:rule rule})))
   rule sctx spec-id spec-info))

(s/defn rewrite-sctx* :- ssa/SpecCtx
  [rule :- RewriteRule, sctx :- ssa/SpecCtx]
  (reduce-kv
   (fn [m spec-id spec-info]
     (ssa/add-spec-to-context m spec-id (rewrite-spec rule sctx spec-id spec-info)))
   sctx
   sctx))

(s/defn rewrite-in-dependency-order* :- ssa/SpecCtx
  [rule :- RewriteRule, deps-fn :- (s/pred ifn?), sctx :- ssa/SpecCtx]
  (as-> (dependency/graph) dg
    ;; ensure that everything is in the dependency graph, depending on :nothing
    (reduce #(dependency/depend %1 %2 :nothing) dg (keys sctx))
    ;; add the deps for each spec
    (reduce
     (fn [dg [spec-id spec]]
       (reduce #(dependency/depend %1 spec-id %2) dg (deps-fn spec)))
     dg
     sctx)
    ;; rewrite in the correct order
    (->> dg
         (dependency/topo-sort)
         (remove #(= :nothing %))
         (reduce
          (fn [sctx spec-id]
            (ssa/add-spec-to-context sctx spec-id (rewrite-spec rule sctx spec-id (sctx spec-id))))
          sctx))))

(defmacro rule [rewrite-fn-sym]
  (when-not (symbol? rewrite-fn-sym)
    (throw (ex-info "second arg to rewrite-sctx must be a symbol resolving to a rewrite fn" {})))
  `{:rule-name ~(name rewrite-fn-sym) :rewrite-fn ~rewrite-fn-sym, :nodes :all})

(defmacro rewrite-sctx
  [sctx rewrite-fn-sym]
  `(rewrite-sctx* (rule ~rewrite-fn-sym) ~sctx))

(defmacro ->>rewrite-sctx [rewrite-fn-sym sctx] `(rewrite-sctx ~sctx ~rewrite-fn-sym))

(defmacro with-captured-traces [& body]
  `(with-tracing [traces#]
     (try ~@body
          (finally
            (def ~'TRACES @traces#)))))
