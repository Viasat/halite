;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.prop-fixed-decimal
  "Lower fixed-decimal fields and values to integers."
  (:require [com.viasat.halite.analysis :as analysis]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.propagate.prop-abstract :as prop-abstract]
            [com.viasat.halite.propagate.prop-composition :as prop-composition]
            [com.viasat.halite.propagate.prop-top-concrete :as prop-top-concrete]
            [com.viasat.halite.transpile.rewriting :refer [rewrite-sctx] :as halite-rewriting]
            [com.viasat.halite.transpile.ssa :as ssa]
            [com.viasat.halite.lib.fixed-decimal :as fixed-decimal]
            [com.viasat.halite.types :as types]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

;;

(defn remove-vals-from-map
  "Remove entries from the map if applying f to the value produced false."
  [m f]
  (->> m
       (remove (comp f second))
       (apply concat)
       (apply hash-map)))

(defn no-empty
  "Convert empty collection to nil"
  [coll]
  (when (seq coll)
    coll))

(defn no-nil
  "Remove all nil values from the map"
  [m]
  (remove-vals-from-map m nil?))

(defn update-map
  "Replace all of the map entries with the resulting of applying f to each key-value pair."
  [m f]
  (into {} (for [[k v] m] (f k v))))

;;

(s/defn ^:private context-into-type
  [context t]
  (assoc context :type t))

(s/defn ^:private context-into-field
  [context f]
  (assoc context :field f))

;;

(declare walk-bound)

(declare walk-concrete-bound)

(s/defn ^:private walk-map
  [context m]
  (-> m
      (update-map (fn [f bound]
                    (let [context (context-into-field context f)]
                      [f (walk-concrete-bound context bound)])))))

(s/defn ^:private walk-untyped-map
  [context m]
  (-> m
      (update-map (fn [f bound]
                    (let [context (context-into-field context f)]
                      [f (walk-bound context bound)])))))

(s/defn ^:private walk-spec-id
  [context bound]
  bound)

(s/defn ^:private walk-refinement
  [context
   t
   bound]
  (let [context' (context-into-type context t)]
    [(walk-spec-id context t)
     (-> bound
         (update-vals (partial walk-concrete-bound context')))]))

(s/defn ^:private walk-refinement-bound
  [context bound]
  (-> bound
      (update-map (partial walk-refinement context))))

(s/defn ^:private walk-concrete-spec-bound
  [context
   bound :- prop-composition/ConcreteSpecBound]
  (let [context' (context-into-type context (:$type bound))]
    (merge (no-nil {:$type (walk-spec-id context (:$type bound))
                    :$refines-to (some->> (:$refines-to bound)
                                          (walk-refinement-bound context'))})
           (->> (dissoc bound :$type :$refines-to)
                (walk-map context')))))

(s/defn ^:private walk-atom-bound
  [context
   bound :- prop-composition/AtomBound]
  (cond
    (integer? bound) (if (:g context)
                       ((:g context) context bound)
                       bound)
    (base/fixed-decimal? bound) (if (:f context)
                                  ((:f context) bound)
                                  bound)
    (boolean? bound) bound
    (string? bound) bound
    (= :Unset bound) bound
    (= :String bound) bound
    (map? bound) (let [in (:$in bound)]
                   {:$in (cond
                           (set? in) (set (map (partial walk-bound context) in))
                           (vector? in) (vec (map (partial walk-bound context) in)))})
    :default (throw (ex-info "unrecognized atom:" {:context context
                                                   :bound bound}))))

(s/defn ^:private walk-spec-id-to-bound-entry
  [context
   t
   bound]
  (let [context' (context-into-type context t)]
    [(walk-spec-id context t)
     (walk-untyped-map context' bound)]))

(s/defn ^:private walk-spec-id-to-bound
  [context
   bound]
  (-> bound
      (update-map (partial walk-spec-id-to-bound-entry context))))

(s/defn ^:private walk-refines-to
  [context
   t
   bound]
  (let [context' (context-into-type context t)]
    [(walk-spec-id context t)
     (merge (no-nil {:$refines-to (some->> (:$refines-to bound)
                                           (walk-spec-id-to-bound context'))})
            (->> (dissoc bound :$refines-to)
                 (walk-map context')))]))

(s/defn ^:private walk-spec-id-to-bound-with-refines-to
  [context
   bound :- prop-abstract/SpecIdToBoundWithRefinesTo]
  (merge bound
         (-> (dissoc bound :Unset)
             (update-map (partial walk-refines-to context)))))

(s/defn ^:private walk-abstract-spec-bound
  [context
   bound :- prop-abstract/AbstractSpecBound]
  (no-nil {:$in (some->> (:$in bound)
                         (walk-spec-id-to-bound-with-refines-to context))
           :$if (some->> (:$if bound)
                         (walk-spec-id-to-bound-with-refines-to context))
           :$refines-to (some->> (:$refines-to bound)
                                 (walk-spec-id-to-bound context))}))

(s/defn ^:private walk-concrete-bound
  [context
   bound :- prop-composition/ConcreteSpecBound]
  (cond
    (:$type bound) (walk-concrete-spec-bound context bound)
    :default (walk-atom-bound context bound)))

(s/defn ^:private walk-bound
  [context
   bound]
  (cond
    (:$type bound) (walk-concrete-bound context bound)
    (or (not (map? bound))
        (and (contains? bound :$in)
             (not (map? (:$in bound))))) (walk-atom-bound context bound)
    :default (walk-abstract-spec-bound context bound)))

;;

(defn- produce-rescale-code [target-form target-scale new-scale]
  `(~'let [~'$prop-fixed-decimal-target ~target-form]
          (~'let [~'$prop-fixed-decimal-shift (~'- ~target-scale ~new-scale)
                  ~'$prop-fixed-decimal-factor (~'expt 10 (~'abs ~'$prop-fixed-decimal-shift))]
                 (~'if (~'> ~'$prop-fixed-decimal-shift 0)
                       (~'div ~'$prop-fixed-decimal-target ~'$prop-fixed-decimal-factor)
                       (~'if (~'< ~'$prop-fixed-decimal-shift 0)
                             (~'* ~'$prop-fixed-decimal-target ~'$prop-fixed-decimal-factor)
                             ~'$prop-fixed-decimal-target)))))

(s/defn ^:private lower-rescale-expr
  [{ctx :ctx} :- halite-rewriting/RewriteFnCtx
   _
   [form]]
  (when (and (seq? form)
             (= 'rescale (first form)))
    (let [{:keys [ssa-graph]} ctx
          [_rescale target-id new-scale-id] form
          [target-form target-type] (ssa/deref-id ssa-graph target-id)
          target-scale (types/decimal-scale target-type)
          [new-scale] (ssa/deref-id ssa-graph new-scale-id)]
      (produce-rescale-code target-form target-scale new-scale))))

(s/defn ^:private lower-rescale :- ssa/SpecCtx
  [sctx :- ssa/SpecCtx]
  (halite-rewriting/rewrite-sctx sctx lower-rescale-expr))

(s/defn ^:private fixed-decimal-to-long [f]
  (let [scale (fixed-decimal/get-scale f)]
    (fixed-decimal/shift-scale f scale)))

(s/defn ^:private lower-fixed-decimal-values-expr
  [{ctx :ctx} :- halite-rewriting/RewriteFnCtx
   _
   [form]]
  (when (fixed-decimal/fixed-decimal? form)
    (fixed-decimal-to-long form)))

(s/defn ^:private lower-fixed-decimal-values :- ssa/SpecCtx
  [sctx :- ssa/SpecCtx]
  (halite-rewriting/rewrite-sctx sctx lower-fixed-decimal-values-expr))

(s/defn ^:private lower-fixed-decimal :- ssa/SpecCtx
  [sctx :- ssa/SpecCtx]
  (-> sctx
      lower-rescale
      lower-fixed-decimal-values))

;;

(s/defn ^:private lower-spec-field-types-in-spec :- ssa/SpecInfo
  [spec :- ssa/SpecInfo]
  (-> spec
      (update :fields #(update-vals % (fn [halite-type]
                                        (if (types/decimal-type? halite-type)
                                          :Integer
                                          halite-type))))))

(s/defn lower-spec-field-types :- ssa/SpecCtx
  [sctx :- ssa/SpecCtx]
  (update-vals sctx lower-spec-field-types-in-spec))

;;

(s/defn ^:private lowered-spec-context :- ssa/SpecCtx
  [spec-map  :- envs/SpecMap]
  (->> spec-map
       ssa/spec-map-to-ssa
       lower-fixed-decimal
       lower-spec-field-types))

(s/defn propagate :- prop-abstract/SpecBound
  [spec-map :- envs/SpecMap
   opts :- prop-abstract/Opts
   initial-bound :- prop-abstract/SpecBound]
  (->> (prop-top-concrete/propagate (lowered-spec-context spec-map)
                                    opts
                                    (->> initial-bound
                                         (walk-bound {:f fixed-decimal-to-long})))
       (walk-bound {:g (fn [context n]
                         (let [{:keys [field type]} context
                               field-type (get-in spec-map [type :fields field])]
                           (if (types/decimal-type? field-type)
                             (->> (analysis/make-fixed-decimal-string n (types/decimal-scale field-type))
                                  fixed-decimal/fixed-decimal-reader)
                             n)))})))
