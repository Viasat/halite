;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.bom-op
  (:require [clojure.set :as set]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(s/defn bom-dispatch-f
  [x :- bom/VariableValueBom]
  (cond
    (bom/is-no-value-bom? x) bom/NoValueBom
    (base/integer-or-long? x) Integer
    (base/fixed-decimal? x) FixedDecimal
    (string? x) String
    (boolean? x) Boolean
    (bom/is-instance-value? x) bom/InstanceValue
    (set? x) #{}
    (vector? x) []
    (bom/is-abstract-instance-bom? x) bom/AbstractInstanceBom
    (bom/is-concrete-instance-bom? x) bom/ConcreteInstanceBom
    (bom/is-primitive-bom? x) bom/PrimitiveBom))

(defn- find-dups [seq]
  (->> seq
       frequencies
       (filter (fn [[k v]]
                 (> v 1)))
       (map first)))

(defn dispatch-values-symbols [d]
  (get {Integer 'Integer
        FixedDecimal 'FixedDecimal
        String 'String
        Boolean 'Boolean
        bom/InstanceValue 'bom/InstanceValue
        #{} '#{}
        [] '[]
        bom/AbstractInstanceBom 'bom/AbstractInstanceBom
        bom/ConcreteInstanceBom 'bom/ConcreteInstanceBom
        bom/PrimitiveBom 'bom/PrimitiveBom
        bom/NoValueBom 'bom/NoValueBom}
       d d))

(defn- verify-all-dispatch-values-listed [bodies]
  (let [dispatch-values-raw (->> (take-nth 2 bodies)
                                 (map #(if (and (set? %)
                                                (not (empty? %)))
                                         %
                                         #{%})))
        dispatch-values-referenced (->> (reduce into #{} dispatch-values-raw)
                                        (map dispatch-values-symbols)
                                        set)
        dispatch-values-count (->> dispatch-values-raw
                                   (map count)
                                   (apply +))
        dispatch-values-expected (->> #{Integer
                                        FixedDecimal
                                        String
                                        Boolean
                                        bom/InstanceValue
                                        #{}
                                        []
                                        bom/AbstractInstanceBom
                                        bom/ConcreteInstanceBom
                                        bom/PrimitiveBom
                                        bom/NoValueBom}
                                      (map dispatch-values-symbols)
                                      set)
        dups (find-dups (->> dispatch-values-raw
                             (reduce into [])))
        error-count (when (seq dups)
                      {"dispatch values duplicated" dups})
        missing (set/difference dispatch-values-expected dispatch-values-referenced)
        error-missing (when (seq missing)
                        {"missing dispatch values" missing})
        extra (set/difference dispatch-values-expected dispatch-values-referenced)
        error-extra (when (seq extra)
                      {"missing dispatch values" extra})
        errors (merge error-count error-missing error-extra)]
    (when (seq errors)
      errors)))

(defn update-trace [trace value]
  (into [(conj (or (first trace) []) value)]
        (rest trace)))

(defmacro meta-handler [name x body]
  `(let [result# ~body]
     (if (instance? clojure.lang.IObj result#)
       (let [old-value# ~x
             old-meta# (:trace (meta old-value#))]
         (with-meta
           result#
           (if (= old-value# result#)
             {:trace (update-trace old-meta# ~name)}
             {:trace (conj (if old-meta#
                             (if (vector? old-meta#)
                               old-meta#
                               [[old-meta#]])
                             [])
                           [~name])})))
       result#)))

(defmacro def-bom-multimethod* [f-name args & bodies]
  (when (pos? (count bodies))
    (let [[types body & rest-bodies] bodies]
      (if (= 2 (count bodies))
        (if (and (set? types)
                 (not (empty? types)))
          (if (> (count types) 1)
            `(do (defmethod ~f-name ~(first types) ~args (meta-handler '~f-name ~(last args) ~body))
                 (def-bom-multimethod* ~f-name ~args ~(set (rest types)) ~body ~@rest-bodies))
            `(defmethod ~f-name ~(first types) ~args (meta-handler '~f-name ~(last args) ~body)))
          `(defmethod ~f-name ~types ~args (meta-handler '~f-name ~(last args) ~body)))
        `(do (def-bom-multimethod* ~f-name ~args ~types ~body)
             (def-bom-multimethod* ~f-name ~args ~@rest-bodies))))))

(defmacro def-bom-multimethod [f-name & rest-args]
  (let [comment-string? (string? (first rest-args))
        [args & bodies] (if comment-string?
                          (rest rest-args)
                          rest-args)
        errors (verify-all-dispatch-values-listed bodies)]
    (when errors
      (throw (ex-info (str errors) {})))
    `(do (defmulti ~f-name (fn [& args#]
                             (bom-dispatch-f (last args#))))
         (def-bom-multimethod* ~f-name ~args ~@bodies))))

;;;;

(def-bom-multimethod bom-assumes-optional?
  "Answer the question of whether this bom presupposes that the field is optional."
  [bom]
  #{Integer
    FixedDecimal
    String
    Boolean
    bom/InstanceValue
    #{}
    []}
  false

  bom/NoValueBom
  true

  #{bom/PrimitiveBom
    bom/ConcreteInstanceBom
    bom/AbstractInstanceBom}
  (contains? bom :$value?))
