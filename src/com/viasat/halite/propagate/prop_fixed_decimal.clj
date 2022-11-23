;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.prop-fixed-decimal
  "Lower fixed-decimal fields and values to integers."
  (:require [com.viasat.halite.base :as base]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.propagate.prop-abstract :as prop-abstract]
            [com.viasat.halite.propagate.prop-composition :as prop-composition]
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
      (update :spec-vars #(update-vals % (fn [halite-type]
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

(s/defn ^:private context-into-schema
  [context s]
  (update context :schema-path conj s))

;;

(declare walk-bound)

(declare walk-concrete-bound)

(s/defn ^:private walk-map
  [context m]
  (let [context (context-into-schema context 'map)]
    (-> m
        (update-vals (partial walk-concrete-bound context)))))

(s/defn ^:private walk-spec-id
  [context bound]
  (let [context (context-into-schema context 'spec-id)]
    bound))

(s/defn ^:private walk-refinement
  [context
   t
   bound]
  (let [context (context-into-schema context 'refinement)
        context' (context-into-type context t)]
    [(walk-spec-id context t)
     (-> bound
         (update-vals (partial walk-concrete-bound context')))]))

(s/defn ^:private walk-refinement-bound
  [context bound]
  (let [context (context-into-schema context 'refinement-bound)]
    (-> bound
        (update-map (partial walk-refinement context)))))

(s/defn ^:private walk-concrete-spec-bound
  [context
   bound :- prop-composition/ConcreteSpecBound]
  (let [context (context-into-schema context 'concrete-spec-bound)
        context' (context-into-type context (:$type bound))]
    (merge (no-nil {:$type (walk-spec-id context (:$type bound))
                    :$refines-to (some->> (:$refines-to bound)
                                          (walk-refinement-bound context'))})
           (->> (dissoc bound :$type :$refines-to)
                (walk-map context')))))

(s/defn ^:private walk-atom-bound
  [context
   bound :- prop-composition/AtomBound]
  (let [context (context-into-schema context 'atom-bound)]
    (cond
      (integer? bound) bound
      (base/fixed-decimal? bound) ((:f context) bound)
      (boolean? bound) bound
      (string? bound) bound
      (= :Unset bound) bound
      (= :String bound) bound
      (map? bound) (let [in (:$in bound)]
                     {:$in (cond
                             (set? in) (set (map (partial walk-bound context) in))
                             (vector? in) (vec (map (partial walk-bound context) in)))})
      :default (throw (ex-info "unrecognized atom:" {:context context
                                                     :bound bound})))))

(s/defn ^:private walk-spec-id-to-bound
  [context
   bound]
  (let [context (context-into-schema context 'spec-id-to-bound)]
    (-> bound
        (update-vals (partial walk-map)))))

(s/defn ^:private walk-refines-to
  [context
   t
   bound]
  (let [context (context-into-schema context 'refines-to)
        context' (context-into-type context t)]
    [(walk-spec-id context t)
     (merge (no-nil {:$refines-to (some-> (:$refines-to bound)
                                          (partial walk-spec-id-to-bound context))})
            (->> (dissoc bound :$refines-to)
                 (walk-map context')))]))

(s/defn ^:private walk-spec-id-to-bound-with-refines-to
  [context
   bound :- prop-abstract/SpecIdToBoundWithRefinesTo]
  (let [context (context-into-schema context 'spec-id-to-bound-with-refines-to)]
    (merge bound
           (-> (dissoc bound :Unset)
               (update-map (partial walk-refines-to context))))))

(s/defn ^:private walk-abstract-spec-bound
  [context
   bound :- prop-abstract/AbstractSpecBound]
  (let [context (context-into-schema context 'abstract-spec-bound)]
    (no-nil {:$in (some-> (:$in bound)
                          (walk-spec-id-to-bound-with-refines-to context))
             :$if (some-> (:$if bound)
                          (walk-spec-id-to-bound-with-refines-to context))
             :$refines-to (some-> (:$refines-to bound)
                                  (walk-spec-id-to-bound context))})))

(s/defn ^:private walk-concrete-bound
  [context
   bound :- prop-composition/ConcreteSpecBound]
  (let [context (context-into-schema context 'concrete-bound)]
    (cond
      (:$type bound) (walk-concrete-spec-bound context bound)
      :default (walk-atom-bound context bound))))

(s/defn ^:private walk-bound
  [context
   bound]
  (let [context (context-into-schema context 'walk)]
    (cond
      (:$type bound) (walk-concrete-bound context bound)
      (or (not (map? bound))
          (and (contains? bound :$in)
               (not (map? (:$in bound))))) (walk-atom-bound context bound)
      :default (walk-abstract-spec-bound context bound))))
