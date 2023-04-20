;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.flow-inline
  "Convert a lowered expression to a lowered expression with some forms inlined."
  (:require [com.viasat.halite.bom :as bom]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.eval :as eval]
            [com.viasat.halite.flow-boolean :as flow-boolean]
            [com.viasat.halite.flow-expr :as flow-expr]
            [com.viasat.halite.lib.fixed-decimal :as fixed-decimal]
            [com.viasat.halite.var-ref :as var-ref]
            [com.viasat.halite.walk :as walk2]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(defn- collapse-booleans [expr]
  "Attempt to simplify boolean expressions."
  (cond
    (and (seq? expr) (= 'not (first expr))) (apply flow-boolean/make-not (rest expr))
    (and (seq? expr) (= 'or (first expr))) (apply flow-boolean/make-or (rest expr))
    (and (seq? expr) (= 'and (first expr))) (apply flow-boolean/make-and (rest expr))
    :default expr))

(s/defn ^:private inline-constants
  "If a variable is constrained to a single value and it must have a value, then in-line the value."
  [bom :- bom/Bom
   expr :- bom/LoweredExpr]
  (->> expr
       (walk2/postwalk (fn [expr]
                         (collapse-booleans (if (var-ref/var-ref? expr)
                                              (let [path (var-ref/get-path expr)
                                                    v (or (get-in bom path)
                                                          (when (and (= :$value? (last path))
                                                                     (bom/is-bom-value? (get-in bom (butlast path))))
                                                            true))]
                                                (if (bom/is-bom-value? v)
                                                  (if (fixed-decimal/fixed-decimal? v)
                                                    (flow-expr/flower-fixed-decimal v)
                                                    v)
                                                  expr))
                                              expr))))))

(s/defn ^:private inline-ops
  "If an expression is a function call with all args as primitive values, then go ahead and evaluate it."
  [expr]
  (->> expr
       (walk2/postwalk (fn [expr]
                         (collapse-booleans (if (and (seq? expr)
                                                     (every? bom/is-bom-value? (rest expr)))
                                              (eval/eval-expr* {:senv (envs/spec-env {})
                                                                :env (envs/env {})}
                                                               expr)
                                              expr))))))

(s/defn inline :- bom/LoweredExpr
  [bom :- bom/Bom
   expr :- bom/LoweredExpr]
  (->> expr
       (inline-constants bom)
       inline-ops
       bom/ensure-flag-lowered))
