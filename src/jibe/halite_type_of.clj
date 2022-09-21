;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite-type-of
  "halite type-of implementation, that determines the type of values"
  (:require [jibe.h-err :as h-err]
            [jibe.halite-base :as halite-base]
            [jibe.halite-eval :as halite-eval]
            [jibe.halite-type-check :as halite-type-check]
            [jibe.halite.halite-types :as halite-types]
            [jibe.halite.halite-envs :as halite-envs]
            [jibe.lib.format-errors :refer [throw-err]]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(s/defn ^:private type-of* :- halite-types/HaliteType
  [ctx :- halite-type-check/TypeContext
   value]
  (cond
    (boolean? value) :Boolean
    (halite-base/integer-or-long? value) :Integer
    (halite-base/fixed-decimal? value) (halite-type-check/type-check-fixed-decimal value)
    (string? value) :String
    (= :Unset value) :Unset
    (map? value) (let [t (halite-type-check/check-instance type-of* :value ctx value)]
                   (halite-eval/validate-instance (:senv ctx) value)
                   t)
    (coll? value) (halite-type-check/check-coll type-of* :value ctx value)
    :else (throw-err (h-err/invalid-value {:value value}))))

(s/defn type-of :- halite-types/HaliteType
  "Return the type of the given runtime value, or throw an error if the value is invalid and cannot be typed.
  For instances, this function checks all applicable constraints. Any constraint violations result in a thrown exception."
  [senv :- (s/protocol halite-envs/SpecEnv)
   tenv :- (s/protocol halite-envs/TypeEnv)
   value :- s/Any]
  (type-of* {:senv senv :tenv tenv} value))
