;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-flower
  "Lower expressions in boms down into expressions that are supported by propagation."
  (:require [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.bom-pipeline-base :as bom-pipeline-base]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.flow-boolean :as flow-boolean]
            [com.viasat.halite.flow-expr :as flow-expr]
            [com.viasat.halite.flow-inline :as flow-inline]
            [com.viasat.halite.flow-return-path :as flow-return-path]
            [com.viasat.halite.op-add-constraints :as op-add-constraints]
            [com.viasat.halite.op-add-value-fields :as op-add-value-fields]
            [com.viasat.halite.op-conjoin-spec-bom :as op-conjoin-spec-bom]
            [com.viasat.halite.var-ref :as var-ref]
            [com.viasat.halite.walk :as walk2]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

;;;;

(def LowerContext {:top-bom bom/Bom
                   :spec-env (s/protocol envs/SpecEnv)
                   :spec-type-env (s/protocol envs/TypeEnv) ;; holds the types coming from the contextual spec
                   :env (s/protocol envs/Env) ;; contains local values, e.g. from 'let' forms
                   :path [s/Any]
                   :counter-atom s/Any ;; an atom to generate unique IDs
                   (s/optional-key :constraint-name) String})

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

(declare flower-op*)

(defn- lower-fields [context bom]
  (let [{:keys [path]} context]
    (merge bom (->> bom
                    bom/to-bare-instance-bom
                    (map (fn [[field-name field-bom]]
                           [field-name (flower-op* (assoc context :path (conj path field-name)) field-bom)]))
                    (into {})))))

(defn- lower-constraints [context instance-literal-atom bom]
  (let [{:keys [top-bom spec-env]} context
        spec-id (bom/get-spec-id bom)
        spec-info (envs/lookup-spec spec-env spec-id)]
    (-> bom
        (assoc :$constraints (some->> bom
                                      :$constraints
                                      (map (fn [[constraint-name x]]
                                             (let [lowered-x
                                                   (->> x
                                                        (flow-expr/lower-expr
                                                         (-> context
                                                             (assoc
                                                              :spec-type-env (envs/type-env-from-spec spec-info)
                                                              :constraint-name constraint-name
                                                              :instance-literal-f #(swap! instance-literal-atom assoc-in %1 %2))
                                                             (dissoc :top-bom)))
                                                        (flow-inline/inline top-bom))]
                                               ;; a constraint of 'true is always satisfied, so simply drop it
                                               (when-not (= true lowered-x)
                                                 [constraint-name lowered-x]))))
                                      (into {})))
        base/no-nil-entries)))

(defn- lower-refinements [context bom]
  (let [{:keys [path]} context]
    (-> bom
        (assoc :$refinements (some-> bom
                                     :$refinements
                                     (map (fn [[other-spec-id sub-bom]]
                                            [other-spec-id (flower-op* (assoc context :path (conj path other-spec-id)) sub-bom)]))
                                     (into {})))
        base/no-nil-entries)))

(defn- lower-concrete-choices [context bom]
  (let [{:keys [path]} context]
    (-> bom
        (assoc :$concrete-choices (some-> bom
                                          :$concrete-choices
                                          (map (fn [[other-spec-id sub-bom]]
                                                 [other-spec-id (flower-op* (assoc context :path (conj path other-spec-id)) sub-bom)]))
                                          (into {})))
        base/no-nil-entries)))

(defn- handle-false-constraints [bom]
  (if (some #(= false %) (vals (:$constraints bom)))
    ;; if there is a constraint of 'false, then the constraints can never be satisfied, so drop the others
    (assoc bom :$constraints (->> bom
                                  :$constraints
                                  (map (fn [[k v]]
                                         (when (= false v)
                                           [k v])))
                                  (into {})))
    bom))

(defn- populate-instance-literals [context instance-literal-atom valid-var-atom bom]
  (let [{:keys [spec-env counter-atom]} context]
    (-> bom
        (assoc
         :$instance-literals
         (->> @instance-literal-atom
              (map
               (fn [[instance-literal-id instance-literal-bom]]
                 [instance-literal-id
                  (let [spec-id (bom/get-spec-id instance-literal-bom)
                        bare-instance-bom (bom/to-bare-instance-bom instance-literal-bom)
                        instance-literal-bom' (->> instance-literal-bom
                                                   (bom-pipeline-base/expand-bom spec-env)
                                                   (flower-op* (assoc
                                                                context
                                                                :path (conj (:path context)
                                                                            :$instance-literals
                                                                            instance-literal-id)
                                                                :env
                                                                (reduce (fn [env [field-name val]]
                                                                          (flow-return-path/add-binding
                                                                           env
                                                                           (symbol field-name)
                                                                           (flow-expr/wrap-lowered-expr
                                                                            (if (bom/is-expression-bom? val)
                                                                              (:$expr val)
                                                                              val))))
                                                                        (:env context)
                                                                        bare-instance-bom)))
                                                   add-guards-to-constraints)]
                    (if (:$valid-var-path instance-literal-bom')
                      (do
                        (swap! valid-var-atom assoc-in
                               (:$valid-var-path instance-literal-bom')
                               {:$valid-var-constraints (-> (:$constraints instance-literal-bom')
                                                            (update-keys #(str % "$" (base/next-id counter-atom))))})
                        (dissoc instance-literal-bom' :$constraints))
                      instance-literal-bom'))]))

              (into {})
              base/no-empty))
        base/no-nil-entries)))

(defn- populate-valid-vars [valid-var-atom bom]
  (merge bom @valid-var-atom))

(defn- combine-valid-var-constraints [bom]
  (walk2/postwalk (fn [x]
                    (if (and (map? x)
                             (contains? x :$valid-var-constraints))
                      (apply flow-boolean/make-and (vals (:$valid-var-constraints x)))
                      x))
                  bom))

(defn- resolve-valid-vars [bom]
  (walk2/postwalk (fn [x]
                    (if (and (var-ref/var-ref? x)
                             (contains? (set (var-ref/get-path x)) :$valid-vars))
                      (get-in bom (var-ref/get-path x))
                      x))
                  bom))

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
        valid-var-atom (atom {})
        context (-> context
                    (dissoc :constraint-name))]
    (->> bom
         (lower-fields context)
         (lower-constraints context instance-literal-atom)
         (lower-refinements context)
         (lower-concrete-choices context)
         handle-false-constraints
         (populate-instance-literals context instance-literal-atom valid-var-atom)
         (populate-valid-vars valid-var-atom)
         combine-valid-var-constraints
         resolve-valid-vars)))

(s/defn flower-op :- bom/Bom
  [spec-env :- (s/protocol envs/SpecEnv)
   bom :- bom/Bom]
  (flower-op* {:top-bom bom
               :spec-env spec-env
               :type-env (envs/type-env {})
               :env (envs/env {})
               :path []
               :counter-atom (atom -1)}
              bom))
