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
            [com.viasat.halite.transpile.lower-cond :as lower-cond]
            [com.viasat.halite.transpile.ssa :as ssa]
            [com.viasat.halite.lib.fixed-decimal :as fixed-decimal]
            [com.viasat.halite.types :as types]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(s/defn ^:private fixed-decimal-to-long [f]
  (let [scale (fixed-decimal/get-scale f)]
    (fixed-decimal/shift-scale f scale)))

(declare encode-fixed-decimals-in-expr)

(defn- encode-fixed-decimals-in-collection [expr]
  (->> expr
       (map encode-fixed-decimals-in-expr)))

(defn- encode-fixed-decimals-in-seq [expr]
  (cond
    :default
    (apply list (first expr) (encode-fixed-decimals-in-collection (rest expr)))))

(defn encode-fixed-decimals-in-expr
  [expr]
  (cond
    (boolean? expr) expr
    (base/integer-or-long? expr) expr
    (base/fixed-decimal? expr) (fixed-decimal-to-long expr)
    (string? expr) expr
    (symbol? expr) expr
    (keyword? expr) expr
    (map? expr) (-> expr (update-vals encode-fixed-decimals-in-expr))
    (seq? expr) (encode-fixed-decimals-in-seq expr)
    (set? expr) (set (encode-fixed-decimals-in-collection expr))
    (vector? expr) (vec (encode-fixed-decimals-in-collection expr))
    :default (throw (ex-info "unexpected expr to encode-fixed-decimals-in-expr" {:expr expr}))))

;;

(s/defn ^:private encode-fixed-decimals-in-expr-object :- envs/ExprObject
  [expr-object :- envs/ExprObject]
  (-> expr-object
      (update :expr encode-fixed-decimals-in-expr)))

(s/defn ^:private encode-fixed-decimals-in-spec :- envs/SpecInfo
  [spec :- envs/SpecInfo]
  (-> spec
      (update :fields #(update-vals % (fn [halite-type]
                                        (if (types/decimal-type? halite-type)
                                          :Integer
                                          halite-type))))
      (update :constraints #(->> %
                                 (mapv (fn [[cname expr]]
                                         [cname (encode-fixed-decimals-in-expr expr)]))))
      (update :refines-to #(update-vals % encode-fixed-decimals-in-expr-object))))

(s/defn encode-fixed-decimals-in-spec-map :- envs/SpecMap
  [spec-map :- envs/SpecMap]
  (update-vals spec-map encode-fixed-decimals-in-spec))

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

(s/defn propagate :- prop-abstract/SpecBound
  [spec-map :- envs/SpecMap
   opts :- prop-abstract/Opts
   initial-bound :- prop-abstract/SpecBound]
  (let [sctx (->> spec-map
                  encode-fixed-decimals-in-spec-map
                  lower-cond/lower-cond-in-spec-map
                  ssa/spec-map-to-ssa)]
    (->> (prop-top-concrete/propagate sctx opts
                                      (->> initial-bound
                                           (walk-bound {:f fixed-decimal-to-long})))
         (walk-bound {:g (fn [context n]
                           (let [{:keys [field type]} context
                                 field-type (get-in spec-map [type :fields field])]
                             (if (types/decimal-type? field-type)
                               (->> (analysis/make-fixed-decimal-string n (types/decimal-scale field-type))
                                    fixed-decimal/fixed-decimal-reader)
                               n)))}))))
