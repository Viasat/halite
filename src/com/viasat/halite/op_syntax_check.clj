;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-syntax-check
  (:require [com.viasat.halite.b-err :as b-err]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.lib.format-errors :as format-errors]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(declare syntax-check-op)

(s/defn ^:private verify-spec-exists
  "Filter specs out that do not exist"
  [spec-env spec-id]
  (let [spec (envs/lookup-spec spec-env spec-id)]
    (if (nil? spec)
      (format-errors/throw-err (b-err/spec-does-not-exist {:spec-id (symbol spec-id)}))
      spec-id)))

(s/defn ^:private verify-variable
  "Verify the variable exists in the spec"
  [spec-env spec-id variable]
  (let [variable-key (key variable)
        variable-value (val variable)
        spec-variable (->> (envs/lookup-spec spec-env spec-id)
                           :fields
                           variable-key)]
    ;; recursive syntax-check of variable
    (syntax-check-op spec-env variable-value)
    (if (nil? spec-variable)
      (format-errors/throw-err (b-err/variable-does-not-exist {:spec-id (symbol spec-id)
                                                               :variable (symbol variable-key)}))
      spec-variable)))

(s/defn ^:private syntax-check-spec
  "Return spec if syntax correct, else an exception is generated"
  [spec-env bom]
  (let [spec-id (bom/get-spec-id bom)
        recursive-bom (bom/to-bare-instance-bom bom)
        filtered-spec (verify-spec-exists spec-env spec-id)
        variables (->> recursive-bom
                       (map (partial verify-variable spec-env filtered-spec))
                       doall)])
  bom)

(bom-op/def-bom-multimethod syntax-check-op
  "Recursively walk and syntax-check a bom.  Verify that the specs are in the workspace, and all the variables referenced exist."
  [spec-env bom]
  #{Integer
    FixedDecimal
    String
    Boolean
    #{}
    []
    bom/NoValueBom
    bom/ContradictionBom
    bom/PrimitiveBom}
  bom

  #{bom/InstanceValue
    bom/AbstractInstanceBom
    bom/ConcreteInstanceBom}
  (syntax-check-spec spec-env bom))
