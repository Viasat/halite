;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.flow-boolean
  "Functions to construct boolean expressions. These functions will apply rules to automatically
  simplify the expressions as they are built"
  (:require [com.viasat.halite.bom :as bom]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(s/defn make-and [& args :- [bom/Expr]]
  (let [args (->> args
                  (remove #(= true %)))]
    (cond
      (= 0 (count args)) true
      (= 1 (count args)) (first args)
      (some #(= false %) args) false
      :default (apply list 'and args))))

(s/defn make-or [& args :- [bom/Expr]]
  (let [args (->> args
                  (remove #(= false %)))]
    (cond
      (= 0 (count args)) false
      (= 1 (count args)) (first args)
      (some #(= true %) args) true
      :default (apply list 'or args))))

(s/defn make-not [x :- bom/Expr]
  (cond
    (= true x) false
    (= false x) true
    (and (seq? x) (= 'not (first x))) (second x)
    :default (list 'not x)))

(s/defn make-if
  [target :- bom/Expr
   then-clause :- bom/Expr
   else-clause :- bom/Expr]
  (cond
    (= true target) then-clause
    (= false target) else-clause
    (and (= true then-clause) (= true else-clause)) true
    (and (= false then-clause) (= false else-clause)) false
    (and (= true then-clause) (= false else-clause)) target
    (and (= false then-clause) (= true else-clause)) (make-not target)
    (= true else-clause) (make-or (make-not target) then-clause)
    (= true then-clause) (make-or target else-clause)
    (= false else-clause) (make-and target then-clause)
    (= false then-clause) (make-and (make-not target) else-clause)
    ;; cannot do these simplifications because the clauses might not be boolean types
    ;; (= target then-clause) (make-or then-clause else-clause)
    ;; (= target else-clause) (make-or 'or then-clause else-clause)
    :default (list 'if target then-clause else-clause)))

(s/defn make-when
  [target :- bom/Expr
   then-clause :- bom/Expr]
  (cond
    (= true target) then-clause
    (= false target) '$no-value
    :default (list 'when target then-clause)))
