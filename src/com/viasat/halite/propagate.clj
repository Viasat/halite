;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate
  "Constraint propagation for halite."
  (:require [com.viasat.halite.envs :as envs]
            [com.viasat.halite.propagate.prop-abstract :as prop-abstract]
            [com.viasat.halite.propagate.prop-fixed-decimal :as prop-fixed-decimal]
            [com.viasat.halite.lib.format-errors :refer [throw-err]]
            [schema.core :as s]
            [com.viasat.halite.h-err :as h-err]))

(set! *warn-on-reflection* true)

(def Bound prop-abstract/Bound)

(def SpecBound prop-abstract/SpecBound)

(def Opts prop-abstract/Opts)

(def default-options prop-abstract/default-options)

(s/defn propagate :- SpecBound
  ([senv :- (s/protocol envs/SpecEnv), initial-bound :- SpecBound]
   (propagate senv default-options initial-bound))
  ([senv :- (s/protocol envs/SpecEnv), opts :- prop-abstract/Opts, initial-bound :- SpecBound]
   (prn "propagate! " initial-bound)
   (let [spec-map (if (map? senv)
                    senv
                    (envs/build-spec-map senv (:$type initial-bound)))]
     (try
       (prop-fixed-decimal/propagate spec-map opts initial-bound)
       (catch Exception ex
         (if (not= :h-err/no-valid-instance-in-bound (:err-id (ex-data ex)))
           (throw ex)
           (throw-err (h-err/no-valid-instance-in-bound
                       (assoc (ex-data ex) :initial-bound initial-bound))
                      ex)))))))
