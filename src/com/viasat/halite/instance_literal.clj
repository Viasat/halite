;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.instance-literal
  "Represents instance literals in expressions."
  (:require [clojure.pprint :as pprint]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.types :as types]
            [schema.core :as s])
  (:import [java.io Writer]))

(set! *warn-on-reflection* true)

(deftype InstanceLiteral [bindings]
  Object
  (equals [_ other]
    (and (instance? InstanceLiteral other)
         (= bindings (.-bindings ^InstanceLiteral other))))
  (hashCode [_]
    (.hashCode bindings))

  bom/LoweredObject
  (is-lowered-object? [_] true))

(s/defn make-instance-literal :- InstanceLiteral
  [bindings]
  (InstanceLiteral. bindings))

(s/defn instance-literal-reader :- InstanceLiteral
  [bindings]
  (make-instance-literal bindings))

(s/defn instance-literal? :- Boolean
  "Is the value a instance-literal object? Only returns true for objects created by this module."
  [value :- s/Any]
  (instance? InstanceLiteral value))

(s/defn get-bindings
  [instance-literal :- InstanceLiteral]
  (.-bindings instance-literal))

(def ^:dynamic *reader-symbol* 'instance)

(defn print-instance-literal [^InstanceLiteral instance-literal ^Writer writer]
  (.write writer (str "#" *reader-symbol* " " (.-bindings instance-literal))))

(defmethod print-method InstanceLiteral [instance-literal writer]
  (print-instance-literal instance-literal writer))

(defmethod print-dup InstanceLiteral [instance-literal writer]
  (print-instance-literal instance-literal writer))

(.addMethod ^clojure.lang.MultiFn pprint/simple-dispatch InstanceLiteral
            (fn [instance-literal]
              (print-instance-literal instance-literal *out*)))

(defn init []
  ;; this is here for other modules to call to force this namespace to be loaded
  )
