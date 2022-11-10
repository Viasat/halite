;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.prop-refine
  (:require [com.viasat.halite.propagate.prop-composition :as prop-composition]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.types :as types]
            [com.viasat.halite.transpile.ssa :as ssa :refer [SpecCtx SpecInfo]]
            [schema.core :as s]
            [clojure.string :as string]
            [clojure.walk :refer [prewalk]]))

(def AtomBound prop-composition/AtomBound)

(declare ConcreteBound)

(s/defschema RefinementBound
  {types/NamespacedKeyword
   {types/BareKeyword (s/recursive #'ConcreteBound)}})

(s/defschema ConcreteSpecBound
  {:$type (s/cond-pre [(s/one (s/enum :Maybe) :maybe) (s/one types/NamespacedKeyword :type)]
                      types/NamespacedKeyword)
   (s/optional-key :$refines-to) RefinementBound
   types/BareKeyword (s/recursive #'ConcreteBound)})

(s/defschema ConcreteBound
  (s/conditional
   :$type ConcreteSpecBound
   :else AtomBound))

(def Opts prop-composition/Opts)
(def default-options prop-composition/default-options)


(s/defn propagate :- ConcreteSpecBound
  ([sctx :- SpecCtx, initial-bound :- ConcreteSpecBound]
   (propagate sctx default-options initial-bound))
  ([sctx :- SpecCtx, opts :- Opts, initial-bound :- ConcreteSpecBound]
   (prop-composition/propagate sctx opts initial-bound)))
