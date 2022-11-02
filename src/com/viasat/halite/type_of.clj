;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.type-of
  "halite type-of implementation, that determines the type of values"
  (:require [com.viasat.halite.h-err :as h-err]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.eval :as eval]
            [com.viasat.halite.type-check :as type-check]
            [com.viasat.halite.types :as halite-types]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.lib.format-errors :refer [throw-err]]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(s/defn ^:private type-of* :- halite-types/HaliteType
  [ctx :- type-check/TypeContext
   value]
  (cond
    (boolean? value) :Boolean
    (base/integer-or-long? value) :Integer
    (base/fixed-decimal? value) (type-check/type-check-fixed-decimal value)
    (string? value) :String
    (= :Unset value) :Unset
    (map? value) (let [t (type-check/type-check-instance type-of* :value ctx value)]
                   (eval/validate-instance (:senv ctx) value)
                   t)
    (coll? value) (type-check/type-check-coll type-of* :value ctx value)
    :else (throw-err (h-err/invalid-value {:value value}))))

(s/defn type-of :- halite-types/HaliteType
  "Return the type of the given runtime value, or throw an error if the value is invalid and cannot be typed.
  For instances, this function checks all applicable constraints. Any constraint violations result in a thrown exception."
  [senv :- (s/protocol envs/SpecEnv)
   tenv :- (s/protocol envs/TypeEnv)
   value :- s/Any]
  (type-of* {:senv senv :tenv tenv} value))
