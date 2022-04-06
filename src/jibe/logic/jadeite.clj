;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.logic.jadeite
  (:require [clojure.string :as string]
            [clojure.core.match :as match :refer [match]]
            [clojure.java.io :as io]
            [instaparse.core :as insta]
            [clojure.edn :as edn]))

(set! *warn-on-reflection* true)

;;;;
;; From Jadeite to Halite

(defn flatten-variadics [h]
  (if-not (and (seq? h) (seq? (second h)))
    h
    (let [[op2 [op1 & args1] & args2] h]
      (if-not (and (contains? '#{+ - * div or and =} op2)
                   (= op2 op1))
        h
        (concat [op2] args1 args2)))))

(defn toh
  "Translate from tree of expression objects created by instaparse (hiccup) into halite"
  [tree]
  (flatten-variadics
   (match [tree]
     [[:lambda [:params & params] body]]        (list 'fn (mapv toh params) (toh body))
     [[:conditional a "?" b ":" c]] (list 'if (toh a) (toh b) (toh c))
     [[:conditional a "=>" b]]      (list '=> (toh a) (toh b))
     [[:or  a "||" b]]              (list 'or (toh a) (toh b))
     [[:and a "&&" b]]              (list 'and (toh a) (toh b))
     [[:equality a "==" b]]         (list '= (toh a) (toh b))
     [[:equality a "!=" b]]         (list 'not= (toh a) (toh b))
     [[:relational a op b]]         (list (symbol op) (toh a) (toh b))
     [[:add a op b]] (let [hb (toh b)]
                       (match [op hb]
                              ["+" 1] (list 'inc (toh a))
                              ["-" 1] (list 'dec (toh a))
                              :else (list (symbol op) (toh a) hb)))
     [[:mult a "*" b]]            (list '* (toh a) (toh b))
     [[:mult a "/" b]]            (list 'div (toh a) (toh b))
     [[:mult a "%" b]]            (list 'mod* (toh a) (toh b))
     [[:prefix "!" a]]            (list 'not (toh a))
     [[:get-field a [:symbol b]]] (list 'get* (toh a) (keyword b))
     [[:get-index a b]]           (list 'get* (toh a) (toh b))
     [[:call-fn [:symbol "set"] & args]]   (set (map toh args))
     [[:call-fn [:symbol "list"] & args]]  (vec (map toh args))
     [[:call-fn [:symbol "expt"] & args]]  (list* 'expt (map toh args))
     [[:call-fn [:symbol "str"] & args]]   (list* 'str (map toh args))
     [[:call-method a [:symbol "A"] [:expr [:lambda [:params b] c]]]] (list 'A [(toh b) (toh a)] (toh c))
     [[:call-method a [:symbol "E"] [:expr [:lambda [:params b] c]]]] (list 'E [(toh b) (toh a)] (toh c))
     [[:call-method a [:symbol "contains"] & args]]  (list* 'contains? (toh a) (map toh args))
     [[:call-method a [:symbol "containsAll"] b]]    (list 'subset? (toh b) (toh a))
     [[:call-method a [:symbol "isConcrete"]]]       (list 'concrete? (toh a))
     [[:call-method a [:symbol "map"] b]]            (list 'map* (toh b) (toh a))
     [[:call-method a [:symbol "reduce"] & args]]    (concat ['reduce-] (map toh args) [(toh a)])
     [[:call-method a [:symbol "refineTo"] & args]]  (list* 'refine-to (toh a) (map toh args))
     [[:call-method a [:symbol "refinesTo"] & args]] (list* 'refines-to? (toh a) (map toh args))
     [[:call-method a [:symbol "select"] b]]         (list 'select (toh b) (toh a))
     [[:call-method a [:symbol op] & args]]          (list* (symbol op) (toh a) (map toh args))
     [[:map & args]]      (->> args
                               (partition 2)
                               (map (fn [[[_ key-string] val-tree]]
                                        [(keyword key-string) (toh val-tree)]))
                               (into {}))
     [[:let & args]]      (list 'let (mapv toh (drop-last args)) (toh (last args)))
     [[:int i]]           (parse-long i)
     [[:symbol "true"]]   true
     [[:symbol "false"]]  false
     [[:symbol s]]        (symbol s)
     [[:typename s]]      (keyword s)
     [[:string s]]        (edn/read-string s)

     [[(_ :guard keyword?) (kid :guard vector?)]] (toh kid)
     :else (throw (ex-info (str "Unhandled parse tree:\n" (pr-str tree))
                           {:tree tree})))))

(def whitespace-or-comments
  (insta/parser
   "ws-or-comments = #'\\s+' | comment+
    comment = #'\\s*#.*$'"))

(def parse
  (insta/parser (io/resource "jibe/jadeite.bnf")
                :auto-whitespace whitespace-or-comments))

(defn to-halite [jadeite-string]
  (let [tree (parse jadeite-string)]
    (when (insta/failure? tree)
      (throw (ex-info (pr-str tree) {:parse-failure tree})))
    (toh tree)))

;;;;
;; From Halite to Jadeite

(declare toj)

(defn infix
  ([args] (infix "(" ", " ")" args))
  ([op args] (infix "(" op ")" args))
  ([pre op post args] (apply str (concat [pre]
                                         (interpose op (map toj args))
                                         [post]))))

(defn typename [kw]
  (str "<" (subs (str kw) 1) ">"))

(defn call-method [method-name [target & args]]
  (str (toj target) "." method-name (infix args)))

(defn toj [x]
  (cond
    (string? x) (pr-str x)
    (vector? x) (str "list" (infix x))
    (keyword? x) (typename x)
    (set? x) (apply str (concat ["set("]
                                (interpose ", " (sort (map toj x)))
                                [")"]))
    (map? x) (apply str (concat ["{"]
                                (interpose ", " (sort (map #(str (name (key %)) ": " (toj (val %))) x)))
                                ["}"]))
    (seq? x) (let [[op & [a0 a1 a2 :as args]] x]
               (case op
                 A (str (toj (second a0)) ".A(" (toj (first a0)) " -> " (toj a1) ")")
                 E (str (toj (second a0)) ".E(" (toj (first a0)) " -> " (toj a1) ")")
                 = (infix " == " args)
                 and (infix " && " args)
                 concrete? (call-method "isConcrete" args)
                 conj (call-method "conj" args)
                 contains? (call-method "contains" args)
                 count (str (toj a0) ".count()")
                 dec (str "(" (toj a0) " - 1)")
                 difference (call-method "difference" args)
                 div (infix " / " args)
                 expt (str "expt" (infix args))
                 fn (str (infix a0) " -> " (toj a1))
                 get* (if (keyword? a1)
                        (str (toj a0) '. (name a1))
                        (str (toj a0) [a1]))
                 if (str "(" (toj a0)
                         " ? " (toj a1)
                         " : " (toj a2) ")")
                 inc (str "(" (toj a0) " + 1)")
                 let (let [[bindings expr] args]
                       (str "{"
                            (->> bindings
                                 (partition 2)
                                 (mapcat (fn [[k v]]
                                           [k " = " (toj v) ";"]))
                                 (apply str))
                            (toj expr)
                            "}"))
                 intersection (call-method "intersection" args)
                 into (call-method "into" args)
                 map* (str (toj a1) ".map(" (toj a0) ")")
                 mod* (infix " % " args)
                 not (str "!" (toj a0))
                 not= (infix " != " args)
                 or (infix " || " args)
                 reduce- (str (toj (last args)) ".reduce" (infix (drop-last args)))
                 refine-to (str (toj a0) ".refineTo( " (typename a1) " )")
                 refines-to? (str (toj a0) ".refinesTo( " (typename a1) " )")
                 select (str (toj a1) ".select(" (toj a0) ")")
                 str (str "str" (infix args))
                 subset? (call-method "containsAll" (reverse args))
                 union (call-method "union" args)
                 ;; default:
                 (infix (str " " op " ") args)))
    :else (str x)))

(def to-jadeite
  "Translate halite form into jadeite string"
  toj)
