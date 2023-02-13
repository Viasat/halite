;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.interface-model
  (:require
   [schema.core :as s]))

;; from types ;;

(s/defn bare? :- s/Bool
  "true if the symbol or keyword lacks a namespace component, false otherwise"
  [sym-or-kw :- (s/cond-pre s/Keyword s/Symbol)]
  (nil? (namespace sym-or-kw)))

(def namespaced?
  "true if the symbol or keyword has a namespace component, false otherwise"
  (complement bare?))

(s/defschema BareKeyword (s/constrained s/Keyword bare?))
(s/defschema NamespacedKeyword (s/constrained s/Keyword namespaced?))
(s/defschema BareSymbol (s/constrained s/Symbol bare?))

;; from prop-strings ;;

;;;;; Bounds, extended with Strings ;;;;;
(s/defschema AtomBound
  (s/cond-pre
   s/Int
   s/Bool
   s/Str
   (s/enum :Unset :String)
   {:$in (s/cond-pre
          #{(s/cond-pre s/Int s/Bool s/Str (s/enum :Unset :String))}
          [(s/one s/Int :lower) (s/one s/Int :upper) (s/optional (s/enum :Unset) :Unset)])}))

(s/defschema StringBound
  (s/cond-pre s/Str (s/enum :Unset :String) {:$in #{(s/cond-pre s/Str (s/enum :String :Unset))}}))

(s/defschema SpecBound
  {BareKeyword AtomBound})

;; from prop-refine ;;

(declare ConcreteBound)

(s/defschema SpecIdBound
  (s/cond-pre [(s/one (s/enum :Maybe) :maybe) (s/one NamespacedKeyword :type)]
              NamespacedKeyword))

(s/defschema RefinementBound
  {NamespacedKeyword
   (s/conditional
    map? {(s/optional-key :$type) SpecIdBound
          BareKeyword (s/recursive #'ConcreteBound)}
    :else (s/enum :Unset))})

;; this is not the place to interpret a missing :$type field as either T or
;; [:Maybe T] -- such a feature should be at an earlier lowering layer so that
;; the policy is in one place and all these later layers can assume a :$type
;; field exists.
(s/defschema ConcreteSpecBound
  {:$type SpecIdBound
   (s/optional-key :$refines-to) RefinementBound
   BareKeyword (s/recursive #'ConcreteBound)})

(s/defschema ConcreteBound
  (s/conditional
   :$type ConcreteSpecBound
   :else AtomBound))

;; from prop_abstract ;;

(declare Bound)

(s/defschema UntypedSpecBound {BareKeyword (s/recursive #'Bound)})

(s/defschema SpecIdToBound
  {NamespacedKeyword UntypedSpecBound})

(s/defschema SpecIdToBoundWithRefinesTo
  {NamespacedKeyword {(s/optional-key :$refines-to) SpecIdToBound
                            BareKeyword (s/recursive #'Bound)}
   (s/optional-key :Unset) s/Bool})

(s/defschema AbstractSpecBound
  (s/constrained
   {(s/optional-key :$in) SpecIdToBoundWithRefinesTo
    (s/optional-key :$if) SpecIdToBoundWithRefinesTo
    (s/optional-key :$refines-to) SpecIdToBound}
   #(< (count (select-keys % [:$in :$if])) 2)
   "$in-and-$if-mutually-exclusive"))

;; Despite the name, this can appear at the position of a field whose declared
;; type is an abstract spec, though the :$type actually identified in this
;; bounds will always be concrete.
(s/defschema ConcreteSpecBound2
  {:$type (s/cond-pre [(s/one (s/enum :Maybe) :maybe) (s/one NamespacedKeyword :type)]
                      NamespacedKeyword)
   (s/optional-key :$refines-to) SpecIdToBound
   BareKeyword (s/recursive #'Bound)})

(s/defschema SpecBound
  (s/conditional
   :$type ConcreteSpecBound2
   :else AbstractSpecBound))


(s/defschema Bound
  (s/conditional
   :$type ConcreteBound
   #(or (not (map? %))
        (and (contains? % :$in)
             (not (map? (:$in %))))) AtomBound
   :else AbstractSpecBound))

;; from prop-composition ;;
(s/defschema ConcreteSpecBound
  {:$type (s/cond-pre [(s/one (s/enum :Maybe) :maybe) (s/one NamespacedKeyword :type)]
                      NamespacedKeyword)
   BareKeyword (s/recursive #'ConcreteBound)})

(s/defschema ConcreteBound
  (s/conditional
   :$type ConcreteSpecBound
   :else AtomBound))

