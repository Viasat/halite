;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-flower
  "Lower expressions in boms down into expressions that are supported by propagation."
  (:require [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.eval :as eval]
            [com.viasat.halite.flow-boolean :as flow-boolean]
            [com.viasat.halite.flow-expr :as flow-expr]
            [com.viasat.halite.flow-return-path :as flow-return-path]
            [com.viasat.halite.lib.fixed-decimal :as fixed-decimal]
            [com.viasat.halite.op-add-constraints :as op-add-constraints]
            [com.viasat.halite.op-add-value-fields :as op-add-value-fields]
            [com.viasat.halite.op-conjoin-spec-bom :as op-conjoin-spec-bom]
            [com.viasat.halite.var-ref :as var-ref]
            [com.viasat.halite.walk :as walk2]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

;;;;

(defn- next-id [counter-atom]
  (swap! counter-atom inc))

;;;;

(def LowerContext {:top-bom bom/Bom
                   :spec-env (s/protocol envs/SpecEnv)
                   :spec-type-env (s/protocol envs/TypeEnv) ;; holds the types coming from the contextual spec
                   :type-env (s/protocol envs/TypeEnv) ;; holds local types, e.g. from 'let' forms
                   :env (s/protocol envs/Env) ;; contains local values, e.g. from 'let' forms
                   :path [s/Any]
                   :counter-atom s/Any ;; an atom to generate unique IDs
                   (s/optional-key :valid-var-path) [s/Any] ;; path to a variable to represent the result of a 'valid' or 'valid?' invocation in an expression
                   :instance-literal-atom s/Any ;; holds information about instance literals discovered in expressions
                   (s/optional-key :constraint-name) String
                   :guards [s/Any]})

;;;;

(defn collapse-booleans [expr]
  "Attempt to simplify boolean expressions."
  (cond
    (and (seq? expr) (= 'not (first expr))) (apply flow-boolean/make-not (rest expr))
    (and (seq? expr) (= 'or (first expr))) (apply flow-boolean/make-or (rest expr))
    (and (seq? expr) (= 'and (first expr))) (apply flow-boolean/make-and (rest expr))
    :default expr))

(defn inline-constants
  "If a variable is constrained to a single value and it must have a value, then in-line the value."
  [bom expr]
  (->> expr
       (walk2/postwalk (fn [expr]
                         (collapse-booleans (if (var-ref/var-ref? expr)
                                              (let [path (var-ref/get-path expr)
                                                    v (or (get-in bom path)
                                                          (when (and (= :$value? (last path))
                                                                     (bom/is-primitive-value? (get-in bom (butlast path))))
                                                            true))]
                                                (if (bom/is-primitive-value? v)
                                                  (if (fixed-decimal/fixed-decimal? v)
                                                    (flow-expr/flower-fixed-decimal v)
                                                    v)
                                                  expr))
                                              expr))))))

(defn inline-ops
  "If an expression is a function call with all args as primitive values, then go ahead and evaluate it."
  [expr]
  (->> expr
       (walk2/postwalk (fn [expr]
                         (collapse-booleans (if (and (seq? expr)
                                                     (every? bom/is-primitive-value? (rest expr)))
                                              (eval/eval-expr* {:senv (envs/spec-env {})
                                                                :env (envs/env {})}
                                                               expr)
                                              expr))))))

;;

(defn- add-guards-to-constraints [bom]
  (let [guards (:$guards bom)]
    (-> bom
        (assoc :$constraints (some-> bom
                                     :$constraints
                                     (update-vals (fn [constraint]
                                                    (loop [[g & more-g] guards
                                                           r constraint]
                                                      (if g
                                                        (recur more-g
                                                               (flow-boolean/make-if g constraint true))
                                                        r))))))
        (dissoc :$guards)
        base/no-nil-entries)))

;;;;

(bom-op/def-bom-multimethod flower-op*
  [context bom]
  #{Integer
    String
    Boolean
    bom/PrimitiveBom
    bom/ExpressionBom
    bom/NoValueBom
    bom/YesValueBom
    bom/ContradictionBom
    #{}
    []
    bom/InstanceValue}
  bom

  FixedDecimal
  (flow-expr/flower-fixed-decimal bom)

  #{bom/ConcreteInstanceBom
    bom/AbstractInstanceBom
    bom/InstanceLiteralBom}
  (let [{:keys [top-bom spec-env path counter-atom]} context
        instance-literal-atom (atom {})
        context (-> context
                    (assoc :instance-literal-atom instance-literal-atom)
                    (dissoc :constraint-name))
        spec-id (bom/get-spec-id bom)
        spec-info (envs/lookup-spec spec-env spec-id)
        bom' (-> bom
                 (merge (->> bom
                             bom/to-bare-instance-bom
                             (map (fn [[field-name field-bom]]
                                    [field-name (flower-op* (assoc context :path (conj path field-name)) field-bom)]))
                             (into {})))
                 (assoc :$constraints (some->> bom
                                               :$constraints
                                               (map (fn [[constraint-name x]]
                                                      (let [lowered-x (->> x
                                                                           (flow-expr/lower-expr
                                                                            (-> context
                                                                                (assoc
                                                                                 :spec-type-env (envs/type-env-from-spec spec-info)
                                                                                 :constraint-name constraint-name
                                                                                 :guards [])
                                                                                (dissoc :top-bom)))
                                                                           (inline-constants top-bom)
                                                                           inline-ops)]
                                                        ;; a constraint of 'true is always satisfied, so simply drop it
                                                        (when-not (= true lowered-x)
                                                          [constraint-name lowered-x]))))
                                               (into {})))
                 (assoc :$refinements (some-> bom
                                              :$refinements
                                              (map (fn [[other-spec-id sub-bom]]
                                                     [other-spec-id (flower-op* (assoc context :path (conj path other-spec-id)) sub-bom)]))
                                              (into {})))
                 (assoc :$concrete-choices (some-> bom
                                                   :$concrete-choices
                                                   (map (fn [[other-spec-id sub-bom]]
                                                          [other-spec-id (flower-op* (assoc context :path (conj path other-spec-id)) sub-bom)]))
                                                   (into {})))
                 base/no-nil-entries)
        bom' (if (some #(= false %) (vals (:$constraints bom')))
               ;; if there is a constraint of 'false, then the constraints can never be satisfied, so drop the others
               (assoc bom' :$constraints (->> bom'
                                              :$constraints
                                              (map (fn [[k v]]
                                                     (when (= false v)
                                                       [k v])))
                                              (into {})))
               bom')
        valid-var-atom (atom {})
        bom' (-> bom'
                 (assoc :$instance-literals (->> @instance-literal-atom
                                                 (map (fn [[instance-literal-id instance-literal-bom]]
                                                        [instance-literal-id
                                                         (let [spec-id (bom/get-spec-id instance-literal-bom)
                                                               bare-instance-bom (bom/to-bare-instance-bom instance-literal-bom)
                                                               instance-literal-bom' (->> instance-literal-bom
                                                                                          ;; (op-conjoin-spec-bom/conjoin-spec-bom-op spec-env)
                                                                                          ;; (op-add-value-fields/add-value-fields-op spec-env)
                                                                                          (op-add-constraints/add-constraints-op spec-env)
                                                                                          (flower-op* (assoc context
                                                                                                             :path (conj (:path context)
                                                                                                                         :$instance-literals
                                                                                                                         instance-literal-id)
                                                                                                             :env
                                                                                                             (reduce (fn [env [field-name val]]
                                                                                                                       (flow-return-path/add-binding env (symbol field-name)
                                                                                                                                                     (if (bom/is-expression-bom? val)
                                                                                                                                                       (:$expr val)
                                                                                                                                                       val)))
                                                                                                                     (:env context)
                                                                                                                     bare-instance-bom)))
                                                                                          add-guards-to-constraints)]
                                                           (if (:$valid-var-path instance-literal-bom')
                                                             (do
                                                               (swap! valid-var-atom assoc-in
                                                                      (:$valid-var-path instance-literal-bom')
                                                                      {:$valid-var-constraints (-> (:$constraints instance-literal-bom')
                                                                                                   (update-keys #(str % "$" (next-id counter-atom))))})
                                                               (dissoc instance-literal-bom' :$constraints))
                                                             instance-literal-bom'))]))

                                                 (into {})
                                                 base/no-empty))
                 base/no-nil-entries)
        bom' (->> (-> bom'
                      (merge @valid-var-atom))
                  (walk2/postwalk (fn [x]
                                    (if (and (map? x)
                                             (contains? x :$valid-var-constraints))
                                      (apply flow-boolean/make-and (vals (:$valid-var-constraints x)))
                                      x))))]
    (->> bom'
         (walk2/postwalk (fn [x]
                           (if (and (var-ref/var-ref? x)
                                    (contains? (set (var-ref/get-path x)) :$valid-vars))
                             (get-in bom' (var-ref/get-path x))
                             x)))
         (walk2/postwalk (fn [x]
                           (if (and (map? x)
                                    (contains? x :$valid-var-constraints))
                             (apply flow-boolean/make-and (vals (:$valid-var-constraints x)))
                             x))))))

(s/defn flower-op
  [spec-env :- (s/protocol envs/SpecEnv)
   bom]
  (flower-op* {:top-bom bom
               :spec-env spec-env
               :type-env (envs/type-env {})
               :env (envs/env {})
               :path []
               :counter-atom (atom -1)
               :instance-literal-atom (atom {})}
              bom))
