;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.spec
  (:require [com.viasat.halite.envs :as envs]
            [com.viasat.halite.types :as types]
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
