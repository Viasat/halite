;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.spec
  (:require [clojure.set :as set]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.types :as types]
            [loom.alg]
            [loom.graph]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(s/defn get-field-names :- [types/BareKeyword]
  [spec-info :- envs/SpecInfo]
  (let [{:keys [fields]} spec-info]
    (->> fields
         (map first))))

(s/defn get-optional-field-names :- [types/BareKeyword]
  [spec-info :- envs/SpecInfo]
  (let [{:keys [fields]} spec-info]
    (->> fields
         (filter (fn [[field-name field-type]]
                   (types/maybe-type? field-type)))
         (map first))))

(s/defn get-mandatory-field-names :- [types/BareKeyword]
  [spec-info :- envs/SpecInfo]
  (let [{:keys [fields]} spec-info]
    (->> fields
         (remove (fn [[field-name field-type]]
                   (types/maybe-type? field-type)))
         (map first))))

(s/defn get-field-type :- types/HaliteType
  [spec-info :- envs/SpecInfo
   field-name :- types/BareKeyword]
  (let [{:keys [fields]} spec-info]
    (->> field-name
         (get fields))))

(s/defn find-refinement-path :- (s/maybe [types/NamespacedKeyword])
  [spec-env
   from-spec-id :- types/NamespacedKeyword
   to-spec-id :- types/NamespacedKeyword]
  (let [graph (loop [[spec-id & more-spec-ids] #{from-spec-id}
                     spec-id-graph {}]
                (if spec-id
                  (let [{:keys [refines-to]} (envs/lookup-spec spec-env spec-id)
                        found (set (keys refines-to))
                        new-graph (assoc spec-id-graph spec-id found)]
                    (recur (set/union (set more-spec-ids)
                                      (set/difference found (->> new-graph keys set)))
                           new-graph))
                  spec-id-graph))]
    (-> graph
        loom.graph/digraph
        (loom.alg/shortest-path from-spec-id to-spec-id))))
