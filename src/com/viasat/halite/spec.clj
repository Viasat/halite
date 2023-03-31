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

(s/defn get-field-type :- (s/maybe types/HaliteType)
  [spec-info :- envs/SpecInfo
   field-name :- types/BareKeyword]
  (let [{:keys [fields]} spec-info]
    (->> field-name
         (get fields))))

(def Refinement {:to-spec-id types/NamespacedKeyword
                 (s/optional-key :extrinsic?) (s/eq true)})

(defn- make-spec-id-graph-following-refinements-starting-at-spec-id
  [spec-env
   spec-id]
  (loop [[spec-id & more-spec-ids] #{spec-id}
         spec-id-graph {}
         refinement-objects {}]
    (if spec-id
      (let [{:keys [refines-to]} (envs/lookup-spec spec-env spec-id)
            found (set (keys refines-to))
            new-graph (assoc spec-id-graph spec-id found)]
        (recur (set/union (set more-spec-ids)
                          (set/difference found (->> new-graph keys set)))
               new-graph
               (reduce (fn [refinement-objects arg2]
                         (let [[to-spec-id {:keys [extrinsic?]}] arg2]
                           (assoc-in refinement-objects
                                     [spec-id to-spec-id]
                                     (if (= true extrinsic?)
                                       {:extrinsic? true}
                                       nil))))
                       refinement-objects
                       refines-to)))
      {:spec-id-graph spec-id-graph
       :refinement-objects refinement-objects})))

(defn- make-spec-id-graph-reversing-refinements
  [spec-env]
  (->> spec-env
       (map (fn [[spec-id spec]]
              (let [{:keys [refines-to]} spec]
                [spec-id (-> refines-to keys)])))
       (into {})))

(s/defn find-refinement-path :- (s/maybe [Refinement])
  [spec-env
   from-spec-id :- types/NamespacedKeyword
   to-spec-id :- types/NamespacedKeyword]
  (let [{:keys [spec-id-graph
                refinement-objects]} (make-spec-id-graph-following-refinements-starting-at-spec-id spec-env from-spec-id)]
    (loop [spec-id-path (-> spec-id-graph
                            loom.graph/digraph
                            (loom.alg/shortest-path from-spec-id to-spec-id))
           refinement-object-path nil]
      (if (> (count spec-id-path) 1)
        (let [[from-spec-id to-spec-id] spec-id-path]
          (recur (rest spec-id-path)
                 (conj (or refinement-object-path [])
                       (assoc (get-in refinement-objects [from-spec-id to-spec-id])
                              :to-spec-id to-spec-id))))
        refinement-object-path))))

(s/defn find-specs-refining-to
  [spec-env
   to-spec-id :- types/NamespacedKeyword]
  (let [spec-id-graph (make-spec-id-graph-reversing-refinements spec-env)
        g (when-not (empty? spec-id-graph)
            (->> spec-id-graph loom.graph/digraph))]
    (some->> g
             loom.alg/topsort
             (take-while #(not= % to-spec-id))
             (map #(loom.alg/shortest-path g % to-spec-id))
             (remove nil?)
             (map first))))
