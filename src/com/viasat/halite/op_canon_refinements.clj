;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-canon-refinements
  "Based on the actual refinement paths in play in the spec-env, convert boms such that the
  $refinements fields reflect the actual refinement paths. i.e. chain them together following the
  refinement paths"
  (:require [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-analysis :as bom-analysis]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.spec :as spec]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(bom-op/def-bom-multimethod lift-refinements-op
  "Transform a bom such that nested $refinements field values are all 'lifted' to the
  top-level. i.e. break up whatever refinement chains are implied by the bom"
  [bom]

  #{Integer
    FixedDecimal
    String
    Boolean
    bom/NoValueBom
    bom/YesValueBom
    bom/ContradictionBom
    #{}
    []
    bom/InstanceValue
    bom/BasicBom
    bom/ExpressionBom}
  bom

  #{bom/AbstractInstanceBom
    bom/ConcreteInstanceBom
    bom/InstanceLiteralBom}
  (let [bom  (-> (if (bom/is-concrete-instance-bom? bom)
                   (let [{refinements :$refinements} bom]
                     (if (zero? (count refinements))
                       (dissoc bom :$refinements)
                       (let [refinements-from-children (->> refinements
                                                            (map (fn [[to-spec-id refinement-bom]]
                                                                   (let [lifted-child (lift-refinements-op refinement-bom)]
                                                                     (:$refinements lifted-child))))
                                                            (reduce (fn [refinements child-refinements]
                                                                      (merge-with bom-analysis/conjoin-boms
                                                                                  refinements
                                                                                  child-refinements))
                                                                    {}))]
                         (assoc bom :$refinements (merge-with bom-analysis/conjoin-boms
                                                              (-> refinements
                                                                  (update-vals #(dissoc % :$refinements)))
                                                              refinements-from-children)))))
                   bom)
                 (assoc :$concrete-choices (some-> bom :$concrete-choices (update-vals lift-refinements-op)))
                 base/no-nil-entries)]

    ;; process child boms
    (merge bom (-> bom
                   bom/to-bare-instance-bom
                   (update-vals lift-refinements-op)))))

(bom-op/def-bom-multimethod find-required-values-in-refinements
  "Examine the refinement structure to see which ones have a '$value?' field set to true. Record
  which instance these appear directly 'downstream' from."
  [bom]

  #{Integer
    FixedDecimal
    String
    Boolean
    bom/NoValueBom
    bom/YesValueBom
    bom/ContradictionBom
    #{}
    []
    bom/InstanceValue
    bom/BasicBom
    bom/ExpressionBom}
  #{}

  #{bom/AbstractInstanceBom
    bom/ConcreteInstanceBom
    bom/InstanceLiteralBom}
  (let [spec-id (bom/get-spec-id bom)
        {refinements :$refinements} bom
        required-pairs (->> refinements
                            vals
                            (map find-required-values-in-refinements)
                            (reduce into #{})
                            (map #(if (and (= (count %) 1)
                                           (not (= true (:$value? bom))))
                                    [(first %) spec-id]
                                    %))
                            set)]
    (if (= true (:$value? bom))
      (conj required-pairs [spec-id])
      required-pairs)))

(bom-op/def-bom-multimethod find-downstream-refinement-spec-ids
  "Return a set of all spec-ids that are names in refinements fields from this bom down."
  [bom]

  #{Integer
    FixedDecimal
    String
    Boolean
    bom/NoValueBom
    bom/YesValueBom
    bom/ContradictionBom
    #{}
    []
    bom/InstanceValue
    bom/BasicBom
    bom/ExpressionBom}
  #{}

  #{bom/AbstractInstanceBom
    bom/ConcreteInstanceBom
    bom/InstanceLiteralBom}
  (let [{refinements :$refinements} bom]
    (conj (->> refinements
               vals
               (map find-downstream-refinement-spec-ids)
               (reduce into #{})
               set)
          (bom/get-spec-id bom))))

(defn- within-a-pair?
  "Consider the current path in light of identified start and end pairs as well as what can be reached
  from the current local to determine if the current path falls within any of the start/end pairs."
  [path end-start-pair-set destination-set]
  (->> end-start-pair-set
       (some (fn [[end start]]
               (or (= end (last path))
                   (and (or (contains? (set path) start)
                            (nil? start))
                        (not (contains? (set path) end))
                        (contains? destination-set end)))))))

(bom-op/def-bom-multimethod set-value-field-based-on-refinement-path
  "Based on the required pairs that identify the end and start point for required sections of
  refinement paths, ensure that all refinement instances in the required paths are marked
  with :$value? true."
  [required-pairs refinement-path bom]
  #{Integer
    FixedDecimal
    String
    Boolean
    bom/NoValueBom
    bom/YesValueBom
    bom/ContradictionBom
    #{}
    []
    bom/InstanceValue
    bom/BasicBom
    bom/ExpressionBom}
  bom

  #{bom/AbstractInstanceBom
    bom/ConcreteInstanceBom
    bom/InstanceLiteralBom}
  (let [spec-id (bom/get-spec-id bom)
        result (-> bom
                   (assoc :$refinements (some-> bom
                                                :$refinements
                                                (update-vals (partial set-value-field-based-on-refinement-path
                                                                      required-pairs
                                                                      (conj refinement-path spec-id)))))
                   (assoc :$value? (if (within-a-pair? refinement-path
                                                       required-pairs
                                                       (find-downstream-refinement-spec-ids bom))
                                     true
                                     (:$value? bom)))
                   base/no-nil-entries)]
    (if (and (= false (:$value? bom))
             (= true (:$value? result)))
      bom/contradiction-bom
      result)))

(bom-op/def-bom-multimethod canon-refinements-op
  [spec-env bom]

  #{Integer
    FixedDecimal
    String
    Boolean
    bom/NoValueBom
    bom/YesValueBom
    bom/ContradictionBom
    #{}
    []
    bom/InstanceValue
    bom/BasicBom
    bom/ExpressionBom}
  bom

  #{bom/ConcreteInstanceBom
    bom/AbstractInstanceBom
    bom/InstanceLiteralBom}
  (let [bom (-> (if (bom/is-concrete-instance-bom? bom)
                  ;; refinements on abstract instance bom will be handled separately, as they are moved into concrete choices
                  (let [{refinements :$refinements} bom]
                    (if (zero? (count refinements))
                      (dissoc bom :$refinements)
                      (let [required-pairs (find-required-values-in-refinements bom)
                            bom (lift-refinements-op bom)
                            {refinements :$refinements} bom
                            spec-id (bom/get-spec-id bom)
                            result-bom (reduce (fn [bom' [refinement-spec-id refinement-bom]]
                                                 (let [refinement-path (spec/find-refinement-path spec-env spec-id refinement-spec-id)]
                                                   (when (nil? refinement-path)
                                                     (throw (ex-info "no refinement path" {:bom bom
                                                                                           :spec-id spec-id
                                                                                           :refinement-spec-id refinement-spec-id})))
                                                   ;; need to fill all the map entries on the path
                                                   (let [bom''' (loop [remaining-path refinement-path
                                                                       bom'' bom']
                                                                  (if (seq remaining-path)
                                                                    (recur (butlast remaining-path)
                                                                           (update-in bom''
                                                                                      (interleave (repeat :$refinements)
                                                                                                  (map :to-spec-id remaining-path))
                                                                                      merge
                                                                                      (let [to-add {:$instance-of (:to-spec-id (last remaining-path))}]
                                                                                        (if (and (not (bom/is-a-no-value-bom? refinement-bom))
                                                                                                 (:extrinsic? (last remaining-path)))
                                                                                          (assoc to-add :$extrinsic? true)
                                                                                          to-add))))
                                                                    bom''))]
                                                     ;; now at the end of the refinement path, put the refinement-bom
                                                     (update-in bom'''
                                                                (interleave (repeat :$refinements)
                                                                            (->> refinement-path (map :to-spec-id)))
                                                                merge
                                                                (canon-refinements-op spec-env refinement-bom)))))
                                               (assoc bom :$refinements {})
                                               refinements)]
                        (set-value-field-based-on-refinement-path required-pairs [] result-bom))))
                  bom)
                (assoc :$concrete-choices (some-> bom :$concrete-choices (update-vals (partial canon-refinements-op spec-env))))
                base/no-nil-entries)]

    ;; process child boms
    (if (bom/is-no-value-bom? bom)
      bom
      (merge bom (-> bom
                     bom/to-bare-instance-bom
                     (update-vals (partial canon-refinements-op spec-env)))))))
