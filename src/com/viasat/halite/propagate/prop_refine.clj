;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.prop-refine
  (:require [clojure.walk :refer [postwalk]]
            [com.viasat.halite.h-err :as h-err]
            [com.viasat.halite.lib.format-errors :as format-errors]
            [com.viasat.halite.propagate.prop-composition :as prop-composition]
            [com.viasat.halite.transpile.lowering :as lowering]
            [com.viasat.halite.transpile.rewriting :as rewriting]
            [com.viasat.halite.transpile.simplify :as simplify]
            [com.viasat.halite.transpile.ssa :as ssa :refer [SpecCtx]]
            [com.viasat.halite.types :as types]
            [loom.alg :as loom-alg]
            [loom.graph :as loom-graph]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

;; This layer adds to each Spec one field for each direct intrinsic refinement,
;; expressing refinement in terms of composition (the next layer down).
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
;; intrinsic, at a single level per source instance.  This is the source of much
;; of the complexity in lower-bound and raise-bound.  For example, if a
;; transitive refinement is guarded, but the input bound requires it to have a
;; value, then every instance in the chain to it from the root must also be
;; required.

(def AtomBound prop-composition/AtomBound)

(declare ConcreteBound)

(s/defschema SpecIdBound
  (s/cond-pre [(s/one (s/enum :Maybe) :maybe) (s/one types/NamespacedKeyword :type)]
              types/NamespacedKeyword))

(s/defschema RefinementBound
  {types/NamespacedKeyword
   (s/conditional
    map? {(s/optional-key :$type) SpecIdBound
          types/BareKeyword (s/recursive #'ConcreteBound)}
    :else (s/enum :Unset))})

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

(s/defschema Graph
  (s/protocol loom-graph/Graph))

(def Opts prop-composition/Opts)
(def default-options prop-composition/default-options)

(defn refinement-field [to-spec-id]
  (keyword (str ">" (namespace to-spec-id) "$" (name to-spec-id))))

(s/defn realize-intrisic-refinements-expr
  [{:keys [sctx]} :- rewriting/RewriteFnCtx, id, [form htype]]
  (when (map? form)
    (let [spec-id (:$type form)
          spec (get sctx spec-id)
          intrinsics (->> spec :refines-to (remove #(-> % val :extrinsic?)))
          refinement-fields (zipmap (map #(keyword (refinement-field %)) (keys intrinsics))
                                    (map #(ssa/form-from-ssa spec (-> % val :expr)) intrinsics))]
      (when (and (seq refinement-fields)
                 (not (contains? form (first (keys refinement-fields)))))
        (let [field-kws (-> #{}
                            (into (keys (dissoc form :$type))) ;; required fields
                            (into (keys (filter (fn [[_ t]] ;; optional fields
                                                  (types/maybe-type? t))
                                                (:fields spec)))))
              original-as-bindings (vec (mapcat (fn [kw]
                                                  [(symbol kw) (form kw '$no-value)])
                                                field-kws))
              original-fields (zipmap field-kws (map symbol field-kws))

              instance-literal (merge {:$type spec-id} original-fields refinement-fields)]
          (list 'let original-as-bindings instance-literal))))))

(s/defn realize-intrisic-refinements
  [sctx :- SpecCtx]
  (rewriting/squash-trace!
   {:rule "realize-intrisic-refinements"}
   (-> sctx
       ;; add refinement variables to specs:
       (update-vals
        (fn [spec]
          (update spec :fields (fnil into {})
                  (->> (:refines-to spec)
                       (remove #(-> % val :extrinsic?))
                       (map (fn [[to-spec-id {:keys [expr]}]]
                              [(refinement-field to-spec-id)
                               ;; guarded refinement expressions have :Maybe type here:
                               (-> spec :ssa-graph (ssa/deref-id expr) ssa/node-type)]))))))
       ;; add refinement variables to instance literals:
       (rewriting/rewrite-reachable-sctx
        [(rewriting/rule realize-intrisic-refinements-expr)]))))

(defn add-refinement-constraints [sctx]
  (zipmap (keys sctx)
          (->> sctx
               (map (fn [[spec-id spec]]
                      (->> (:refines-to spec)
                           (remove #(-> % val :extrinsic?))
                           (reduce (fn [spec [to-spec-id {:keys [expr]}]]
                                     (let [field (refinement-field to-spec-id)]
                                       (rewriting/add-constraint "prop-refine-as-constraint"
                                                                 sctx spec-id spec (name field)
                                                                 (list '= (symbol field) expr))))
                                   spec)))))))

;; TODO support refines-to? like this but return true or false instead of inst or error
(s/defn lower-refine-to-expr
  [rgraph :- Graph
   {{:keys [ssa-graph] :as ctx} :ctx sctx :sctx} :- rewriting/RewriteFnCtx
   id
   [form htype]]
  (when-let [[_ expr-id to-spec-id] (and (seq? form) (#{'refine-to '$refine-to-step} (first form)) form)]
    (let [from-spec-id (->> expr-id (ssa/deref-id ssa-graph) ssa/node-type types/spec-id)]
      (when from-spec-id ;; nil when the expr type is [:Instance :*]
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
                    path)))))))

(s/defn lower-refines-to?-expr
  [rgraph :- Graph
   {{:keys [ssa-graph] :as ctx} :ctx sctx :sctx} :- rewriting/RewriteFnCtx
   id
   [form htype]]
  (when-let [[_ expr-id to-spec-id] (and (seq? form) (= 'refines-to? (first form)) form)]
    (let [from-spec-id (->> expr-id (ssa/deref-id ssa-graph) ssa/node-type types/spec-id)]
      (when from-spec-id
        (if (= from-spec-id to-spec-id)
          'true
          (let [path (rest (loom.alg/shortest-path rgraph from-spec-id to-spec-id))]
            (if (empty? path)
              'false
              (let [first-level-expr (reduce (fn [expr to-spec-id]
                                               (list 'let ['previous-inst expr]
                                                     (list 'if-value 'previous-inst
                                                           (list 'get 'previous-inst (refinement-field to-spec-id))
                                                           '$no-value)))
                                             expr-id
                                             path)]

                (list 'let ['inst first-level-expr]
                      (list 'if-value 'inst
                            'true
                            'false))))))))))

(defn- disallow-unsupported-refinements
  "Our refinement lowering code is not currently correct when the refinements
  are optional, or when refinements are extrinsic.
  We'll fix it, but until we do, we should at least not emit incorrect results silently."
  [sctx]
  (doseq [[spec-id {:keys [refines-to ssa-graph]}] sctx
          [to-id {:keys [expr extrinsic?]}] refines-to]
    (when extrinsic?
      (throw (ex-info (format "BUG! Refinement of %s to %s is extrinsic, and propagate does not yet support extrinsic refinements"
                              spec-id to-id)
                      {:sctx sctx}))))
  sctx)

(s/defn lower-spec-refinements
  [sctx :- SpecCtx
   rgraph :- Graph]
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
         :nodes :all}
        {:rule-name "lower-refines-to?"
         :rewrite-fn (partial lower-refines-to?-expr rgraph)
         :nodes :all}])
      simplify/simplify))

;;; raise and lower bounds

(s/defn update-spec-bounds
  [bound :- ConcreteSpecBound
   f]
  (postwalk #(if-not (and (map? %) (:$type %))
               %
               (f (prop-composition/unwrap-maybe (:$type %)) %))
            bound))

(defn maybe? [spec-bound-type]
  (and (vector? spec-bound-type) (= :Maybe (first spec-bound-type))))

(defn ^:private assoc-in-refn-path*
  [base-bound [spec-id & more-path] leaf-bound maybe-leaf?]
  (cond
    (= :Unset base-bound) (if maybe-leaf?
                            :Unset
                            (format-errors/throw-err (h-err/invalid-refines-to-bound-conflict
                                                      {:spec-id (symbol spec-id)})))
    (empty? more-path) (if (= :Unset leaf-bound)
                         (if (or (maybe? (:$type base-bound))
                                 (= :Unset base-bound)
                                 (nil? base-bound))
                           :Unset
                           (format-errors/throw-err (h-err/invalid-refines-to-bound-conflict
                                                     {:spec-id (symbol spec-id)})))
                         ;; default mandatory, unless user bound provided a
                         ;; :$type like [:Maybe ...]
                         (merge {:$type spec-id} leaf-bound))
    :else (let [field (refinement-field (first more-path))
                field-bound (get base-bound field)]
            (let [sub-bound (assoc-in-refn-path*
                             field-bound more-path leaf-bound maybe-leaf?)]
              (assoc base-bound
                     :$type (if (and maybe-leaf?
                                     (or (nil? (:$type base-bound))
                                         (maybe? (:$type base-bound))))
                              [:Maybe spec-id]
                              spec-id)
                     field sub-bound)))))

(defn assoc-in-refn-path
  "Like assoc-in, but convert spec-id-path to fields using `refinement-field`,
  and update the :$type of each spec bound on the refinement path."
  [base-bound spec-id-path bound]
  (assoc (assoc-in-refn-path* base-bound spec-id-path bound
                              (or (maybe? (:$type bound))
                                  (= :Unset bound)))
         :$type (:$type base-bound)))

(s/defn lower-bound
  [sctx :- SpecCtx
   rgraph :- Graph
   bound :- ConcreteSpecBound]
  (update-spec-bounds
   bound
   (fn [spec-id spec-bound]
     (reduce (fn [spec-bound [to-spec-id inner-bound]]
               (if-let [path (loom-alg/shortest-path rgraph spec-id to-spec-id)]
                 (assoc-in-refn-path spec-bound path inner-bound)
                 (format-errors/throw-err (h-err/invalid-refines-to-bound
                                           {:spec-id (symbol (prop-composition/unwrap-maybe (:$type spec-bound)))
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
                                   (if (= :Unset b)
                                     :Unset
                                     (-> b
                                         (cond-> (not (maybe? (:$type b)))
                                           ;; TODO: never dissoc, leaving non-maybe :$types in place?
                                           (dissoc :$type))
                                         (dissoc :$refines-to))))
                         (update :$refines-to merge
                                 (-> (:$refines-to b)
                                     ;; If a refinement is maybe, we must raise all _its_
                                     ;; refinements as maybes too. This is a loss of
                                     ;; precision that we might choose to fix later:
                                     (cond-> (maybe? (:$type b))
                                       (as-> rt
                                             (zipmap (keys rt)
                                                     (map (fn [[k v]]
                                                            (if (= :Unset v)
                                                              :Unset
                                                              (assoc v :$type [:Maybe k])))
                                                          rt))))))))))
               spec-bound
               to-spec-ids)))))

(s/defn make-rgraph :- Graph
  [sctx :- SpecCtx]
  (loom-graph/digraph (update-vals sctx (comp keys :refines-to))))

(s/defn propagate :- ConcreteSpecBound
  ([sctx :- SpecCtx, initial-bound :- ConcreteSpecBound]
   (propagate sctx default-options initial-bound))
  ([sctx :- SpecCtx, opts :- Opts, initial-bound :- ConcreteSpecBound]
   (let [rgraph (make-rgraph sctx)]
     (->> (lower-bound sctx rgraph initial-bound)
          (prop-composition/propagate (lower-spec-refinements sctx rgraph) opts)
          (raise-bound sctx)))))
