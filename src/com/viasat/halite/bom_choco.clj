;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.bom-choco
  "Convert a bom into the format expected by choco"
  (:require [clojure.walk :as walk]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.choco-clj :as choco-clj]
            [com.viasat.halite.op-extract-constraints :as op-extract-constraints]
            [com.viasat.halite.op-flatten :as op-flatten]
            [com.viasat.halite.var-ref :as var-ref]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(defn bom-to-choco [bom]
  (let [constraints (op-extract-constraints/extract-constraints-op bom)
        vars (op-flatten/flatten-op bom)]
    {:choco-spec {:vars (->> vars
                             (map (fn [{:keys [path value]}]
                                    [path (condp = (:$primitive-type value)
                                            :Integer :Int)]))
                             (into {}))
                  :constraint-map (->> constraints
                                       (map (fn [{:keys [constraint-path constraint-e]}]
                                              [constraint-path constraint-e]))
                                       (into {}))}
     :choco-bounds (->> vars
                        (map (fn [{:keys [path value]}]
                               ;; TODO use actual bounds here
                               [path [1 100]]))
                        (into {}))}))

(defn- flattened-vars-to-sym-map [vars]
  (-> (map :path vars)
      (zipmap (->> (range) (map #(symbol (str "$_" %)))))))

(defn- flattened-vars-to-reverse-sym-map [vars]
  (->> (map :path vars)
       (zipmap (->> (range) (map #(symbol (str "$_" %)))))))

(defn paths-to-syms [bom choco-data]
  (let [path-to-sym-map (->> bom op-flatten/flatten-op flattened-vars-to-sym-map)
        {:keys [choco-spec choco-bounds]} choco-data
        {:keys [vars constraint-map]} choco-spec]
    {:choco-spec {:vars (-> vars (update-keys path-to-sym-map))
                  :constraints (->> constraint-map
                                    vals
                                    (walk/postwalk (fn [x]
                                                     (if (var-ref/var-ref? x)
                                                       (-> x var-ref/get-path path-to-sym-map)
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
  (let [{:keys [choco-spec choco-bounds]} choco-data
        result (choco-clj/propagate choco-spec choco-bounds)
        sym-to-path-map (->> bom op-flatten/flatten-op flattened-vars-to-reverse-sym-map)]
    (-> result
        (update-keys sym-to-path-map))))
