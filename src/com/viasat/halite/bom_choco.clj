;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.bom-choco
  "Convert a bom into the format expected by choco"
  (:require [clojure.pprint :as pprint]
            [clojure.walk :as walk]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.choco-clj :as choco-clj]
            [com.viasat.halite.lib.fixed-decimal :as fixed-decimal]
            [com.viasat.halite.op-extract-constraints :as op-extract-constraints]
            [com.viasat.halite.op-flatten :as op-flatten]
            [com.viasat.halite.types :as types]
            [com.viasat.halite.var-ref :as var-ref]
            [schema.core :as s])
  (:import [org.chocosolver.solver.exception ContradictionException]))

(set! *warn-on-reflection* true)

(defn- fixed-decimals-in [x]
  (if (fixed-decimal/fixed-decimal? x)
    (-> x fixed-decimal/extract-long second)
    x))

(defn bom-to-bound [bom]
  (let [bom (fixed-decimals-in bom)]
    (cond
      (base/integer-or-long? bom) bom

      (boolean? bom) bom

      (:$enum bom) (->> (:$enum bom)
                        (map fixed-decimals-in)
                        set)

      (:$ranges bom)
      (->> [(->> (:$ranges bom)
                 (map first)
                 (apply min))
            (->> (:$ranges bom)
                 (map second)
                 (apply max))]
           (mapv fixed-decimals-in))

      (or (= :Integer (:$primitive-type bom))
          (types/decimal-type? (:$primitive-type bom))) choco-clj/*default-int-bounds*

      (= :Boolean (:$primitive-type bom)) #{true false}

      :default (throw (ex-info "failed to convert to bound" {:bom bom})))))

(def ^:dynamic *trace-propagate* false)

(defn bom-to-choco [bom]
  (when *trace-propagate*
    (pprint/pprint [:bom bom]))
  (let [constraints (op-extract-constraints/extract-constraints-op bom)
        vars (op-flatten/flatten-op bom)]
    {:choco-spec {:vars (->> vars
                             (map (fn [{:keys [path value]}]
                                    [path (cond
                                            (boolean? value) :Bool
                                            (base/integer-or-long? value) :Int
                                            (= (:$primitive-type value) :Integer) :Int
                                            (= (:$primitive-type value) :Boolean) :Bool
                                            (types/decimal-type? (:$primitive-type value)) :Int
                                            :default (throw (ex-info "unexpected bom value" {:path path :value value})))]))
                             (into {}))
                  :constraint-map (->> constraints
                                       (map (fn [{:keys [constraint-path constraint-e]}]
                                              [constraint-path constraint-e]))
                                       (into {}))}
     :choco-bounds (->> vars
                        (map (fn [{:keys [path value]}]
                               [path (bom-to-bound value)]))
                        (into {}))}))

(defn- flattened-vars-to-sym-map [vars]
  (-> (map :path vars)
      (zipmap (->> (range) (map #(symbol (str "$_" %)))))))

(defn- flattened-vars-to-bom-map [vars]
  (zipmap (map :path vars) (map :value vars)))

(defn- flattened-vars-to-reverse-sym-map [vars]
  (->> (map :path vars)
       (zipmap (->> (range) (map #(symbol (str "$_" %)))))))

(defn- resolve-path [path-to-bom-map path-to-sym-map path]
  (cond
    (contains? path-to-sym-map path) (path-to-sym-map path)

    (and (= :$value? (last path))
         (bom/is-primitive-value? (path-to-bom-map (butlast path))))
    true

    :default (throw (ex-info "unhandled path resolution case" {:path path
                                                               :path-to-sym-map path-to-sym-map
                                                               :path-to-bom-map path-to-bom-map}))))

(defn paths-to-syms [bom choco-data]
  (let [path-to-sym-map (->> bom op-flatten/flatten-op flattened-vars-to-sym-map)
        path-to-bom-map (->> bom op-flatten/flatten-op flattened-vars-to-bom-map)
        {:keys [choco-spec choco-bounds]} choco-data
        {:keys [vars constraint-map]} choco-spec]
    {:choco-spec {:vars (-> vars (update-keys path-to-sym-map))
                  :constraints (->> constraint-map
                                    vals
                                    (walk/postwalk (fn [x]
                                                     (if (var-ref/var-ref? x)
                                                       (->> x var-ref/get-path (resolve-path path-to-bom-map
                                                                                             path-to-sym-map))
                                                       x)))
                                    set)}
     :choco-bounds (-> choco-bounds (update-keys path-to-sym-map))
     :sym-to-path (->> path-to-sym-map
                       (map (fn [[path sym]]
                              [sym path]))
                       sort
                       (mapcat identity)
                       vec)}))

(defn choco-propagate [bom choco-data]
  (when *trace-propagate*
    (pprint/pprint [:choco-data choco-data]))
  (let [{:keys [choco-spec choco-bounds]} choco-data
        raw-result (try (choco-clj/propagate choco-spec choco-bounds)
                        (catch ContradictionException ex
                          nil))]
    (if (nil? raw-result)
      raw-result
      (let [sym-to-path-map (->> bom op-flatten/flatten-op flattened-vars-to-reverse-sym-map)
            result (-> raw-result
                       (update-keys sym-to-path-map))]
        (when *trace-propagate*
          (pprint/pprint result))
        result))))

(defn- handle-fixed-decimals-out [type x]
  (if (types/decimal-type? type)
    (fixed-decimal/package-long (types/decimal-scale type) (long x))
    x))

(defn choco-bound-to-bom [type bound]
  (cond
    (base/integer-or-long? bound) (handle-fixed-decimals-out type bound)
    (boolean? bound) bound
    (set? bound) {:$enum (->> bound
                              (map (partial handle-fixed-decimals-out type))
                              set)}
    :default (let [[lower-bound upper-bound] bound]
               ;; TODO: is the edge of this right, is upper-bound inclusive or exclusive
               {:$ranges #{(->> [lower-bound upper-bound]
                                (mapv (partial handle-fixed-decimals-out type)))}})))

(defn propagate-results-to-bounds [bom propagate-results]
  (when propagate-results
    (let [path-to-bom-map (->> bom op-flatten/flatten-op flattened-vars-to-bom-map)]
      (->> propagate-results
           (map (fn [[path bound]]
                  [path (choco-bound-to-bom (:$primitive-type (path-to-bom-map path)) bound)]))
           (into {})))))
