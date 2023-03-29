;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-canon-refinements
  (:require [clojure.set :as set]
            [com.viasat.halite.analysis :as analysis]
            [com.viasat.halite.b-err :as b-err]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.lib.fixed-decimal :as fixed-decimal]
            [com.viasat.halite.lib.format-errors :as format-errors]
            [com.viasat.halite.spec :as spec]
            [com.viasat.halite.type-check :as type-check]
            [com.viasat.halite.types :as types]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(bom-op/def-bom-multimethod canon-refinements-op
  [spec-env bom]

  #{Integer
    FixedDecimal
    String
    Boolean
    bom/NoValueBom
    #{}
    []
    bom/InstanceValue
    bom/PrimitiveBom}
  bom

  bom/ConcreteInstanceBom
  (let [bom (let [{refinements :$refinements} bom]
              (if (zero? (count refinements))
                (dissoc bom :$refinements)
                (let [spec-id (bom/get-spec-id bom)]
                  (reduce (fn [bom' [refinement-spec-id refinement-bom]]
                            (let [refinement-path (spec/find-refinement-path spec-env spec-id refinement-spec-id)]
                              (when (nil? refinement-path)
                                (throw (ex-info "no refinement path" {:bom bom})))
                              ;; need to fill all the map entries on the path
                              (let [bom''' (loop [remaining-path (rest refinement-path)
                                                  bom'' bom']
                                             (if (seq remaining-path)
                                               (recur (butlast remaining-path)
                                                      (update-in bom''
                                                                 (interleave (repeat :$refinements)
                                                                             remaining-path)
                                                                 merge
                                                                 {:$instance-of (last remaining-path)}))
                                               bom''))]
                                ;; now at the end of the refinement path, put the refinement-bom
                                (update-in bom'''
                                           (interleave (repeat :$refinements)
                                                       (rest refinement-path))
                                           merge
                                           (canon-refinements-op spec-env refinement-bom)))))
                          (assoc bom :$refinements {})
                          refinements))))]

    ;; process child boms
    (if (bom/is-no-value-bom? bom)
      bom
      (merge bom (-> bom
                     bom/to-bare-instance
                     (update-vals (partial canon-refinements-op spec-env))))))

  bom/AbstractInstanceBom
  bom)
