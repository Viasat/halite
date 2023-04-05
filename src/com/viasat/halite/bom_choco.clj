;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.bom-choco
  "Convert a bom into the format expected by choco"
  (:require [clojure.walk :as walk]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.choco-clj :as choco-clj]
            [com.viasat.halite.op-extract-constraints :as op-extract-constraints]
            [com.viasat.halite.op-flatten :as op-flatten]
            [com.viasat.halite.var-ref :as var-ref]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(defn bom-to-bound [bom]
  (cond
    (base/integer-or-long? bom) bom

    (boolean? bom) bom

    (:$enum bom) (:$enum bom)

    (:$ranges bom)
    [(->> (:$ranges bom)
          (map first)
          (apply min))
     (->> (:$ranges bom)
          (map second)
          (apply max))]

    (= :Integer (:$primitive-type bom))
    choco-clj/*default-int-bounds*

    :default (throw (ex-info "failed to convert to bound" {:bom bom}))))

(defn bom-to-choco [bom]
  (let [constraints (op-extract-constraints/extract-constraints-op bom)
        vars (op-flatten/flatten-op bom)]
    {:choco-spec {:vars (->> vars
                             (map (fn [{:keys [path value]}]
                                    [path (cond
                                            (boolean? value) :Bool
                                            (= (:$primitive-type value) :Integer) :Int
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

(defn choco-bound-to-bom [bound]
  (cond
    (base/integer-or-long? bound) bound
    (boolean? bound) bound
    (set? bound) {:$enum bound}
    :default (let [[lower-bound upper-bound] bound]
               ;; TODO: is the edge of this right, is upper-bound inclusive or exclusive
               {:$ranges #{[lower-bound upper-bound]}})))

(defn propagate-results-to-bounds [propagate-results]
  (-> propagate-results
      (update-vals choco-bound-to-bom)))

(defn propagate-results-into-bom [bom propagate-results]
  propagate-results)
