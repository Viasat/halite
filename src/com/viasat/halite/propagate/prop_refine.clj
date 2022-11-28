;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.prop-refine
  (:require [clojure.walk :refer [postwalk]]
            [com.viasat.halite.h-err :as h-err]
            [com.viasat.halite.lib.format-errors :refer [throw-err]]
            [com.viasat.halite.propagate.prop-composition :as prop-composition :refer [unwrap-maybe]]
            [com.viasat.halite.transpile.lowering :as lowering]
            [com.viasat.halite.transpile.rewriting :as rewriting]
            [com.viasat.halite.transpile.simplify :as simplify]
            [com.viasat.halite.transpile.ssa :as ssa :refer [SpecCtx]]
            [com.viasat.halite.transpile.util :refer [fixpoint]]
            [com.viasat.halite.types :as types]
            [loom.alg :as loom-alg]
            [loom.graph :as loom-graph]
            [schema.core :as s]))

;; This layer adds to each Spec one field for each direct intrinsic refinement,
;; expressing refinement in terms of composition (the next layer down).
;;
;; Extrinsic refinements are not included because instances need to be created
;; even if extrinsic refinements fail. Therefore those refinement expressions
;; are substituted in place at the site of `refine-to` and `refines-to?` calls.
;; [this isn't right yet -- where to put bounds for these extrinsic refinements?]
;;
;; Transitive refinements are not included directly either. Imagine a chain of
;; intrinsic refinements A -> B -> C.  If transitives were included instances of
;; A would have fields for B and C, but A's B would not need a field for C
;; (since it would be covered by A's C).  However, an instance of B by itself
;; _would_ still need a field C, requiring multiple versions of spec B.  So
;; instead transitive refinements are lowered to nested levels of composition: A
;; with a field for refinement to B, and that B with a field for refinement to C.
;;
;; However, since we do not want to require users to know full refinement paths,
;; :$refines-to bounds contain all transitive refinements, both extrinsic and
;; intrinsic, at a single level per source instance.

;; TODO: extrinsic (inverted) refinements
;; TODO: guarded refinements
;; TODO: refines-to? support

(def AtomBound prop-composition/AtomBound)

(declare ConcreteBound)

(s/defschema SpecIdBound
  (s/cond-pre [(s/one (s/enum :Maybe) :maybe) (s/one types/NamespacedKeyword :type)]
              types/NamespacedKeyword))

(s/defschema RefinementBound
  {types/NamespacedKeyword
   {;;(s/optional-key :$type) SpecIdBound ;; TODO add this to support guarded refinements
    types/BareKeyword (s/recursive #'ConcreteBound)}})

;; this is not the place to interpret a missing :$type field as either T or
;; [:Maybe T] -- such a feature should be at an earlier lowering layer so that
;; the policy is in one place and all these later layers can assume a :$type
;; field exists.
(s/defschema ConcreteSpecBound
  {:$type SpecIdBound
   (s/optional-key :$refines-to) RefinementBound
   types/BareKeyword (s/recursive #'ConcreteBound)})

(s/defschema ConcreteBound
  (s/conditional
   :$type ConcreteSpecBound
   :else AtomBound))

(def Opts prop-composition/Opts)
(def default-options prop-composition/default-options)

(defn refinement-field [to-spec-id]
  (keyword (str ">" (namespace to-spec-id) "$" (name to-spec-id))))

(s/defn realize-intrisic-refinements-expr
  [{:keys [sctx]} :- rewriting/RewriteFnCtx, id, [form htype]]
  (when (map? form)
    (let [spec-id (:$type form)
          spec (get sctx spec-id)
          intrinsics (->> spec :refines-to (remove #(-> % val :inverted?)))
          refinement-fields (zipmap (map #(keyword (refinement-field %)) (keys intrinsics))
                                    (map #(ssa/form-from-ssa spec (-> % val :expr)) intrinsics))]
      (when (and (seq refinement-fields)
                 (not (contains? form (first (keys refinement-fields)))))
        (let [field-kws (keys (dissoc form :$type))
              original-as-bindings (vec (mapcat (fn [kw]
                                                  [(symbol kw) (form kw)])
                                                field-kws))
              original-fields (zipmap field-kws (map symbol field-kws))

              instance-literal (merge {:$type spec-id} original-fields refinement-fields)
              let-form (list 'let original-as-bindings instance-literal)]
          #_(prn :let-form let-form)
          let-form)))))

(defn realize-intrisic-refinements [sctx]
  (rewriting/squash-trace! {:rule "realize-intrisic-refinements"}
    (-> sctx
        ;; add refinement variables to specs:
        (update-vals
         (fn [spec]
           (update spec :fields (fnil into {})
                   (->> (:refines-to spec)
                        (remove #(-> % val :inverted?))
                        (map (fn [[to-spec-id {:keys [expr]}]]
                               [(refinement-field to-spec-id)
                                (-> spec :ssa-graph (ssa/deref-id expr) ssa/node-type)]))))))
        ;; add refinement variables to instance literals:
        ((fn [sctx]
           (fixpoint
            #(rewriting/rewrite-sctx % realize-intrisic-refinements-expr)
            sctx)))
        #_ ;; why doesn't this work?
        (rewriting/rewrite-reachable-sctx
         [(rewriting/rule realize-intrisic-refinements-expr)]))))


(defn add-refinement-constraints [sctx]
  (zipmap (keys sctx)
          (->> sctx
               (map (fn [[spec-id spec]]
                      (->> (:refines-to spec)
                           (remove #(-> % val :inverted?))
                           (reduce (fn [spec [to-spec-id {:keys [expr]}]]
                                     (let [field (refinement-field to-spec-id)]
                                       (rewriting/add-constraint "prop-refine-as-constraint"
                                                                 sctx spec-id spec (name field)
                                                                 (list '= (symbol field) expr))))
                                   spec)))))))

;; TODO support refines-to? like this but return true or false instead of inst or error
(s/defn lower-refine-to-expr
  [rgraph
   {{:keys [ssa-graph] :as ctx} :ctx sctx :sctx} :- rewriting/RewriteFnCtx
   id
   [form htype]]
  (when-let [[_ expr-id to-spec-id] (and (seq? form) (= 'refine-to (first form)) form)]
    (let [from-spec-id (->> expr-id (ssa/deref-id ssa-graph) ssa/node-type types/spec-id)]
      (if (= from-spec-id to-spec-id)
        expr-id
        (let [path (rest (loom.alg/shortest-path rgraph from-spec-id to-spec-id))]
          (when (empty? path)
            (throw (ex-info "No static refinement path"
                            {:from from-spec-id, :to to-spec-id, :form form})))
          (reduce (fn [expr to-spec-id]
                    (list 'let ['inst (list 'get expr (refinement-field to-spec-id))]
                          (list 'if-value 'inst
                                'inst
                                (list 'error "No active refinement path"))))
                  expr-id
                  path))))))

(defn- disallow-unsupported-refinements
  "Our refinement lowering code is not currently correct when the refinements
  are optional, or when refinements are extrinsic (inverted).
  We'll fix it, but until we do, we should at least not emit incorrect results silently."
  [sctx]
  (doseq [[spec-id {:keys [refines-to ssa-graph]}] sctx
          [to-id {:keys [expr inverted?]}] refines-to]
    (when inverted?
      (throw (ex-info (format "BUG! Refinement of %s to %s is inverted, and propagate does not yet support inverted refinements"
                              spec-id to-id)
                      {:sctx sctx})))
    (let [htype (->> expr (ssa/deref-id ssa-graph) ssa/node-type)]
      (when (types/maybe-type? htype)
        (throw (ex-info (format "BUG! Refinement of %s to %s is optional, and propagate does not yet support optional refinements"
                                spec-id to-id)
                        {:sctx sctx})))))
  sctx)

(defn lower-spec-refinements [sctx rgraph]
  (-> sctx
      (disallow-unsupported-refinements)

      (realize-intrisic-refinements)
      (add-refinement-constraints)
      (update-vals #(dissoc % :refines-to))

      ;; These rules are known to be necessary here (some tests will fail
      ;; without them), but others may also be necessary for spec combinations
      ;; we haven't discovered yet. See the rules applied in
      ;; prop-composition/propagate for inspiration.
      (rewriting/rewrite-reachable-sctx
       [(rewriting/rule lowering/push-if-value-into-if-in-expr)
        (rewriting/rule lowering/push-refine-to-into-if)
        ;; This rule needs access to the refinement graph, so we don't use the rule macro.
        {:rule-name "lower-refine-to"
         :rewrite-fn (partial lower-refine-to-expr rgraph)
         :nodes :all}])
      simplify/simplify))

;;; raise and lower bounds

(s/defn update-spec-bounds
  [bound :- ConcreteSpecBound
   f]
  (postwalk #(if-not (and (map? %) (:$type %))
               %
               (f (unwrap-maybe (:$type %)) %))
            bound))

(defn assoc-in-refn-path [base-bound spec-id-path bound]
  (let [path (next spec-id-path)]
    (->
     (reduce (fn [base-bound path]
               (update-in base-bound
                          ;; TODO: to support guarded refinements, choose correct maybeness
                          ;; (merge of mandatory and optional is mandatory?)
                          (conj (mapv refinement-field path) :$type)
                          #(or % (peek path))))
             base-bound
             (rest (reductions conj [] path)))
     (assoc-in (map refinement-field path)
               (merge {:$type (last path)}  ;; default mandatory; should be supplied at a higher layer?
                      bound)))))

(s/defn lower-bound
  [sctx :- SpecCtx
   rgraph
   bound :- ConcreteSpecBound]
  (update-spec-bounds
   bound
   (fn [spec-id spec-bound]
     (reduce (fn [spec-bound [to-spec-id inner-bound]]
               (if-let [path (loom-alg/shortest-path rgraph spec-id to-spec-id)]
                 (assoc-in-refn-path spec-bound path inner-bound)
                 (throw-err (h-err/invalid-refines-to-bound
                             {:spec-id (symbol (unwrap-maybe (:$type spec-bound)))
                              :to-spec-id (symbol to-spec-id)}))))
             (dissoc spec-bound :$refines-to)
             (:$refines-to spec-bound)))))

(s/defn raise-bound
  [sctx :- SpecCtx
   bound :- ConcreteSpecBound]
  (update-spec-bounds
   bound
   (fn [spec-id spec-bound]
     (let [to-spec-ids (keys (get-in sctx [spec-id :refines-to]))]
       (reduce (fn [spec-bound to-spec-id]
                 (let [field (refinement-field to-spec-id)]
                   (if-let [b (get spec-bound field)]
                     (-> spec-bound
                         (dissoc field)
                         (assoc-in [:$refines-to to-spec-id]
                                   (-> b
                                       (dissoc :$type) ;; until we support guarded refinements
                                       (dissoc :$refines-to)))
                         (update :$refines-to merge (:$refines-to b))))))
               spec-bound
               to-spec-ids)))))

(defn make-rgraph [sctx]
  (loom-graph/digraph (update-vals sctx (comp keys :refines-to))))

(s/defn propagate :- ConcreteSpecBound
  ([sctx :- SpecCtx, initial-bound :- ConcreteSpecBound]
   (propagate sctx default-options initial-bound))
  ([sctx :- SpecCtx, opts :- Opts, initial-bound :- ConcreteSpecBound]
   (let [rgraph (make-rgraph sctx)]
     (->> (lower-bound sctx rgraph initial-bound)
          (prop-composition/propagate (lower-spec-refinements sctx rgraph) opts)
          (raise-bound sctx)))))
