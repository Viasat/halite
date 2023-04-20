;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.fog
  "Represents code for which it is not yet possible to convert it into propagation form."
  (:require [clojure.pprint :as pprint]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.types :as types]
            [schema.core :as s])
  (:import [java.io Writer]))

(set! *warn-on-reflection* true)

(deftype Fog [type]
  Object
  (equals [_ other]
    (and (instance? Fog other)
         (= type (.-type ^Fog other))))
  (hashCode [_]
    (.hashCode type))

  bom/LoweredObject
  (is-lowered-object? [_] true))

(s/defn make-fog :- Fog
  [type :- types/HaliteType]
  (Fog. type))

(s/defn fog-reader :- Fog
  [type]
  (make-fog type))

(s/defn fog? :- Boolean
  "Is the value a fog object? Only returns true for objects created by this module."
  [value :- s/Any]
  (instance? Fog value))

(s/defn get-type :- types/HaliteType
  [fog :- Fog]
  (.-type fog))

(def ^:dynamic *reader-symbol* 'fog)

(defn print-fog [^Fog fog ^Writer writer]
  (.write writer (str "#" *reader-symbol* " " (.-type fog))))

(defmethod print-method Fog [fog writer]
  (print-fog fog writer))

(defmethod print-dup Fog [fog writer]
  (print-fog fog writer))

(.addMethod ^clojure.lang.MultiFn pprint/simple-dispatch Fog
            (fn [fog]
              (print-fog fog *out*)))

(defn init []
  ;; this is here for other modules to call to force this namespace to be loaded
  )
