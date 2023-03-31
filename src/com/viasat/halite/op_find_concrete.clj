;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-find-concrete
  (:require [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.spec :as spec]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(defn- filter-out-abstract [spec-env concrete-choices]
  (->> concrete-choices
       (filter (fn [[spec-id bom]]
                 (let [spec (envs/lookup-spec spec-env spec-id)]
                   (when (and spec (not= true (:abstract? spec)))
                     [spec-id bom]))))
       (into {})))

(defn- handle-empty-choices [bom]
  (if (and (not (nil? (:$concrete-choices bom)))
           (empty? (:$concrete-choices bom)))
    (if (= true (:$value bom))
      bom/contradiction-bom
      bom/no-value-bom)
    bom))

(bom-op/def-bom-multimethod find-concrete-op
  [spec-env bom]
  #{Integer
    FixedDecimal
    String
    Boolean
    bom/PrimitiveBom
    bom/NoValueBom
    bom/ContradictionBom
    #{}
    []
    bom/InstanceValue}
  bom

  #{bom/ConcreteInstanceBom
    bom/AbstractInstanceBom}
  (let [bom (if (bom/is-abstract-instance-bom? bom)
              (cond
                (nil? (:$concrete-choices bom))
                ;; fill in the choices
                (-> bom
                    (assoc :$concrete-choices (->> bom
                                                   bom/get-spec-id
                                                   (spec/find-specs-refining-to spec-env)
                                                   (map (fn [concrete-spec-id]
                                                          [concrete-spec-id {:$instance-of concrete-spec-id}]))
                                                   (filter-out-abstract spec-env)))
                    bom-op/no-nil-entries
                    handle-empty-choices)

                (empty? (:$concrete-choices bom))
                (handle-empty-choices bom)

                :default
                ;; filter out the inapplicable concrete choices provided
                (-> bom
                    (assoc :$concrete-choices (->> bom
                                                   :$concrete-choices
                                                   (filter-out-abstract spec-env)))
                    bom-op/no-nil-entries
                    handle-empty-choices))
              bom)]
    (if (bom/is-instance-bom? bom)
      (-> bom
          (merge (-> bom
                     bom/to-bare-instance-bom
                     (update-vals (partial find-concrete-op spec-env))
                     (into {})))
          (assoc :$refinements (some-> bom :$refinements (update-vals (partial find-concrete-op spec-env))))
          bom-op/no-nil-entries)
      bom)))
