;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.types
  "Schemata defining the set of halite type terms,
  together with functions that define the subtype
  relation and compute meets and joins.

  The subtype graph looks like this:

                         +---->  :Any  <-------+--------------------+-----------------+
                         |                     |                    |                 |
         +---->  [:Maybe :Coll] <-+        [:Maybe :Integer]  [:Maybe :String]  [:Maybe Boolean]
         |           ^            |           ^        ^         ^        ^       ^         ^
  [:Maybe [:Set T]]  |  [:Maybe [:Vec T]]     |        |         |        |       |         |
   ^    ^            |        ^     ^         |        |         |        |       |         |
   |    |  +----> :Coll <---+ |     |       :Integer   |       :String    |      :Boolean   |
   |    |  |                | |     |                  |                  |                 |
   |   [:Set T]           [:Vec T]  |                  |                  |                 |
   |     ^                   ^      |                  |                  |                 |
   |     |                   |      |                  |                  |                 |
   |  :EmptySet           :EmptyVec |                  |                  |                 |
   |                                |                  |                  |                 |
   +----------  :Unset  ------------+-------------------------------------------------------+

  The variable T in the diagram above ranges over all types except those having the form [:Maybe ...].
  The idea is that a maybe type signals the possible absence of a value, and it is not meaningful for a
  collection to contain the absence of a value.

  Note also that there is no node in the graph above for terms of the form [:Maybe [:Maybe ...]],
  which are not valid type terms.
  "
  (:require [schema.core :as s]))

(s/defn bare? :- s/Bool
  "true if the symbol or keyword lacks a namespace component, false otherwise"
  [sym-or-kw :- (s/cond-pre s/Keyword s/Symbol)] (nil? (namespace sym-or-kw)))

(def namespaced?
  "true if the symbol or keyword has a namespace component, false otherwise"
  (complement bare?))

(s/defschema BareKeyword (s/constrained s/Keyword bare?))
(s/defschema NamespacedKeyword (s/constrained s/Keyword namespaced?))
(s/defschema BareSymbol (s/constrained s/Symbol bare?))

(s/defn spec-type? :- s/Bool
  "True if t is a namespaced keyword (the type term that represents a spec), false otherwise"
  [t] (and (keyword? t) (namespaced? t)))

(s/defschema TypeAtom
  "Type atoms are always keywords. Namespace-qualified keywords are interpreted as spec ids.
  Unqualified keywords identify built-in scalar types."
  (s/conditional
   spec-type? NamespacedKeyword
   :else (s/enum :Integer :String :Boolean :EmptySet :EmptyVec :Coll :Any :Unset)))

(declare maybe-type?)

(s/defschema HaliteType
  "A Halite type is either a type atom (keyword), or a 'type constructor' represented
  as a tuple whose first element is the constructor name, and whose remaining elements
  are types.

  Note that the :Set and :Vec constructors do not accept :Maybe types."
  (s/cond-pre
   TypeAtom
   (s/constrained
    [(s/one (s/enum :Set :Vec :Maybe) "coll-type") (s/one (s/recursive #'HaliteType) "elem-type")]
    (fn [[col-type elem-type]]
      (not (maybe-type? elem-type)))
    :no-nested-maybe)))

(s/defn maybe-type? :- s/Bool
  "True if t is :Unset or [:Maybe T], false otherwise."
  [t :- HaliteType]
  (or (= :Unset t)
      (and (vector? t) (= :Maybe (first t)))))

(s/defn subtype? :- s/Bool
  "True if s is a subtype of t, and false otherwise."
  [s :- HaliteType, t :- HaliteType]
  (or
    (= s t) ; the subtyping relation is reflexive
    (= t :Any) ; :Any is the 'top' type
    (and (= t :Coll) (boolean (#{:EmptyVec :EmptySet} s)))
    (and (= s :EmptyVec) (vector? t) (= :Vec (first t)))
    (and (= s :EmptySet) (vector? t) (= :Set (first t)))
    (and (= s :Unset) (vector? t) (= :Maybe (first t)))
    (and (vector? t) (= :Maybe (first t)) (subtype? s (second t)))
    (and (vector? s) (vector? t) (= (first s) (first t)) (subtype? (second s) (second t)))
    (and (vector? s) (boolean (#{:Set :Vec} (first s))) (= t :Coll))))

(s/defn meet :- HaliteType
  "The 'least' supertype of s and t. Formally, return the type m such that all are true:
    (subtype? s m)
    (subtype? t m)
    For all types l, (implies (and (subtype? s l) (subtype? t l)) (subtype? l m))"
  [s :- HaliteType, t :- HaliteType]
  (cond
    (subtype? s t) t
    (subtype? t s) s
    (and (vector? s) (vector? t) (= (first s) (first t))) [(first s) (meet (second s) (second t))]
    (and (subtype? s :Coll) (subtype? t :Coll)) :Coll
    :else :Any))

(s/defn join :- (s/maybe HaliteType)
  "The 'greatest' subtype of s and t. Formally, return the type m such that all are true:
    (subtype? m s)
    (subtype? m t)
    For all types l, (implies (and (subtype? l s) (subtype? l t)) (subtype? l m))

   The Halite type system has no 'bottom' type, so this function may return nil in the case where
   s and t have no common subtype (e.g. :String and :Integer)."
  [s :- HaliteType, t :- HaliteType]
  (cond
    (subtype? s t) s
    (subtype? t s) t
    (and (vector? s) (vector? t) (= (first s) (first t))) (if-let [m (join (second s) (second t))]
                                                            [(first s) m]
                                                            (if (= :Set (first s)) :EmptySet :EmptyVec))
    :else nil))
