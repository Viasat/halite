;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.var-ref
  "Represents a reference to a variable"
  (:require [clojure.pprint :as pprint]
            [schema.core :as s])
  (:import [java.io Writer]))

(set! *warn-on-reflection* true)

(deftype VarRef [path]
  Object
  (equals [_ other]
    (and (instance? VarRef other)
         (= path (.-path ^VarRef other))))
  (hashCode [_]
    (.hashCode path)))

(s/defn make-var-ref :- VarRef
  [path]
  (VarRef. path))

(s/defn var-ref-reader :- VarRef
  [path]
  (make-var-ref path))

(s/defn var-ref? :- Boolean
  "Is the value a var-ref object? Only returns true for objects created by this module."
  [value :- s/Any]
  (instance? VarRef value))

(s/defn get-path
  [var-ref :- VarRef]
  (.-path var-ref))

(s/defn extend-path :- VarRef
  [var-ref :- VarRef
   more-path]
  (make-var-ref (into (get-path var-ref) more-path)))

(def ^:dynamic *reader-symbol* 'r)

(defn print-var-ref [^VarRef var-ref ^Writer writer]
  (.write writer (str "#" *reader-symbol* " " (.-path var-ref))))

(defmethod print-method VarRef [var-ref writer]
  (print-var-ref var-ref writer))

(defmethod print-dup VarRef [var-ref writer]
  (print-var-ref var-ref writer))

(.addMethod ^clojure.lang.MultiFn pprint/simple-dispatch VarRef
            (fn [var-ref]
              (print-var-ref var-ref *out*)))

(defn init []
  ;; this is here for other modules to call to force this namespace to be loaded
  )
