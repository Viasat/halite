;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.prop-extrinsic
  (:require [com.viasat.halite.propagate.prop-refine :as prop-refine]
            [com.viasat.halite.transpile.lowering :as lowering]
            [com.viasat.halite.transpile.rewriting :as rewriting]
            [com.viasat.halite.transpile.ssa :as ssa]
            [com.viasat.halite.types :as types]
            [loom.alg :as loom-alg]
            [loom.graph :as loom-graph]
            [schema.core :as s]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.transpile.simplify :as simplify]))
(set! *warn-on-reflection* true)

;; Lower extrinsic refinements to intrinsic refinements with the necessary guards.

(def AtomBound prop-refine/AtomBound)
(def ConcreteBound prop-refine/ConcreteBound)
(def ConcreteSpecBound prop-refine/ConcreteSpecBound)

(s/defschema Graph
  (s/protocol loom-graph/Graph))

(def Opts prop-refine/Opts)
(def default-options prop-refine/default-options)

(defn guard-field [to-spec-id]
  (keyword (str ">?" (namespace to-spec-id) "$" (name to-spec-id) "?")))

(s/defn add-extrinsic-guard-fields :- ssa/SpecCtx
  [sctx :- ssa/SpecCtx]
  (update-vals
   sctx
   (fn [spec]
     (-> spec
         (update :fields (fnil into {})
                 (->> (:refines-to spec)
                      (filter #(-> % val :extrinsic?))
                      (map (fn [[to-spec-id {:keys [expr]}]]
                             [(guard-field to-spec-id) [:Maybe :Boolean]]))))))))

(s/defn guard-extrinsic-refinements :- ssa/SpecCtx
  [sctx :- ssa/SpecCtx]
  (update-vals
   sctx
   (fn [spec]
     (let [ctx (ssa/make-ssa-ctx sctx spec)]
       (->> (:refines-to spec)
            (reduce (fn [{:keys [ssa-graph] :as spec} [to-spec-id refinement]]
                      (if-not (:extrinsic? refinement)
                        spec
                        (let [guard-sym (symbol (guard-field to-spec-id))
                              expr' (list 'when-value guard-sym
                                          (:expr refinement))
                              ;;[ssa-graph'] (ssa/ensure-node ssa-graph guard-sym :Boolean)
                              [ssa-graph' id'] (ssa/form-to-ssa (assoc ctx
                                                                       :ssa-graph ssa-graph)
                                                                expr')
                              refinement' (-> refinement
                                              (dissoc :extrinsic?)
                                              (assoc :expr id'))]
                          (-> spec
                              (assoc :ssa-graph ssa-graph')
                              (assoc-in [:refines-to to-spec-id] refinement')))))
                    spec))))))

(s/defn lower-extrinsic-refine-to-expr
  [rgraph :- Graph
   {{:keys [ssa-graph] :as ctx} :ctx sctx :sctx} :- rewriting/RewriteFnCtx
   id
   [form htype]]
  (when-let [[_ expr-id to-spec-id] (and (seq? form) (= 'refine-to (first form)) form)]
    (let [from-spec-id (->> expr-id (ssa/deref-id ssa-graph) ssa/node-type types/spec-id)]
      (when from-spec-id ;; don't rewrite (yet) if from-spec-id unknown ([:Instance :*])
        (if (= from-spec-id to-spec-id)
          expr-id
          (let [path (loom.alg/shortest-path rgraph from-spec-id to-spec-id)]
            (when (empty? path)
              (throw (ex-info "No static refinement path"
                              {:from from-spec-id, :to to-spec-id, :form form})))
            (->> path
                 (partition 2 1)
                 (reduce (fn [expr [from-spec-id to-spec-id]]
                           (let [expr'
                                 (if-not (:extrinsic? (get-in sctx [from-spec-id :refines-to
                                                                    to-spec-id]))
                                   expr
                                   (let [fields (keys (get-in sctx [from-spec-id :fields]))
                                         inst (gensym)]
                                     (list 'let [inst expr]
                                           (->
                                            (zipmap fields
                                                    (map #(list 'get inst %) fields))
                                            (assoc :$type from-spec-id
                                                   (guard-field to-spec-id) true)))))]
                             (list '$refine-to-step expr' to-spec-id)))
                         expr-id))))))))

(s/defn lower-specs :- ssa/SpecCtx
  [sctx :- ssa/SpecCtx]
  (let [rgraph (prop-refine/make-rgraph sctx)]
    (-> sctx
        (add-extrinsic-guard-fields)

        (rewriting/rewrite-reachable-sctx
         [(rewriting/rule lowering/push-if-value-into-if-in-expr)
          (rewriting/rule lowering/push-refine-to-into-if)
          ;; This rule needs access to the refinement graph, so we don't use the rule macro.
          {:rule-name "lower-extrinsic-refine-to"
           :rewrite-fn (partial lower-extrinsic-refine-to-expr rgraph)
           :nodes :all}])

        (guard-extrinsic-refinements))))

(s/defn raise-bound :- ConcreteSpecBound
  [sctx :- ssa/SpecCtx
   bound :- ConcreteSpecBound]
  (prop-refine/update-spec-bounds
   bound
   (fn [spec-id spec-bound]
     (->> (get sctx spec-id)
          :refines-to
          (filter (comp :extrinsic? val))
          keys
          (map guard-field)
          (apply dissoc spec-bound)))))

(s/defn propagate
  ([sctx :- ssa/SpecCtx, initial-bound]
   (propagate sctx default-options initial-bound))
  ([sctx :- ssa/SpecCtx, opts :- Opts, initial-bound]
   (->>
    (prop-refine/propagate (lower-specs sctx)
                           opts
                           initial-bound)
    (raise-bound sctx))))
