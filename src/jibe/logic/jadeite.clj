;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.logic.jadeite
  (:require [clojure.core.match :as match :refer [match]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [instaparse.core :as insta]
            [jibe.halite :as halite]
            [jibe.lib.fixed-decimal :as fixed-decimal]
            [jibe.logic.expression :as expression]))

(set! *warn-on-reflection* true)

(def global-fns
  "Set of halite operator names that are written as function calls in jadeite,
  otherwise method call syntax is used."
  '#{abs error expt range str rescale})

;;;;
;; From Jadeite to Halite

(defn- flatten-variadics [h]
  (if-not (and (seq? h) (seq? (second h)))
    h
    (let [[op2 [op1 & args1] & args2] h]
      (cond
        (and (contains? '#{+ - * div or and =} op2)
             (= op2 op1))
        (concat [op2] args1 args2)

        (and (= op2 'get) (= op1 'get))
        (list 'get-in (first args1) [(second args1) (first args2)])

        (and (= op2 'get) (= op1 'get-in))
        (list 'get-in (first args1) (conj (second args1) (first args2)))

        :else h))))

(defn- unwrap-symbol [s]
  (if-let [[_ weird-s] (or (re-matches #"'([^\s']+)'" s)
                           (re-matches #"<(\S+)>" s))]
    (symbol weird-s)
    (symbol s)))

(defprotocol AddMetadata
  (add-source-metadata-when-possible [obj source-tree]))

(extend-protocol AddMetadata
  clojure.lang.IObj
  (add-source-metadata-when-possible [obj source-tree]
    (let [{:instaparse.gll/keys [start-line start-column end-line end-column]} (meta source-tree)]
      (if start-line
        (with-meta obj (merge (meta obj) {:row start-line
                                          :col start-column
                                          :end-row end-line
                                          :end-col end-column}))
        obj)))

  Object
  ;; default implementation for clojure objects that do not support metadata
  (add-source-metadata-when-possible [obj source-tree]
    obj))

(defn toh
  "Translate from tree of expression objects created by instaparse (hiccup) into halite"
  [tree]
  (-> (match [tree]
        [[:conditional op a b c]]      (list (if (= "if" op) 'if 'if-value) (toh a) (toh b) (toh c))
        [[:if-value-let sym m t e]]    (list 'if-value-let [(toh sym) (toh m)] (toh t) (toh e))
        [[:when-value-let sym m t]]    (list 'when-value-let [(toh sym) (toh m)] (toh t))
        [[:when-value sym t]]          (list 'when-value (toh sym) (toh t))
        [[:optional pred body]]        (list 'when (toh pred) (toh body))
        [[:valid op body]]             (list (symbol op) (toh body))
        [[:implication a b]]           (list '=> (toh a) (toh b))
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
        [[:mult a "%" b]]            (list 'mod (toh a) (toh b))
        [[:prefix "!" a]]            (list 'not (toh a))
        [[:get-field a [:symbol b]]] (list 'get (toh a) (keyword (unwrap-symbol b)))
        [[:get-index a b]]           (list 'get (toh a) (toh b))
        [[:comprehend [:symbol "sortBy"] s bind pred]] (list 'sort-by [(toh s) (toh bind)] (toh pred))
        [[:comprehend [:symbol op] s bind pred]] (list (symbol op) [(toh s) (toh bind)] (toh pred))
        [[:reduce acc init elem coll body]] (list 'reduce [(toh acc) (toh init)]
                                                  [(toh elem) (toh coll)] (toh body))
        [[:call-fn [:symbol "equalTo"] & args]]    (list* '= (map toh args))
        [[:call-fn [:symbol "notEqualTo"] & args]] (list* 'not= (map toh args))
        [[:call-fn op & args]]                     (let [s (toh op)]
                                                     (if (global-fns s)
                                                       (list* s (map toh args))
                                                       (throw (ex-info (str "No such global function: " s)
                                                                       {:op s :args args}))))
        [[:call-method a [:symbol "reduce"] & args]] (concat ['reduce-] (map toh args) [(toh a)])
        [[:call-method a op & args]]                 (let [s (toh op)]
                                                       (if (global-fns s)
                                                         (throw (ex-info (str "No such method: " s)
                                                                         {:op s :args args}))
                                                         (list* s (toh a) (map toh args))))
        [[:type-method a "refineTo" & args]]         (list* 'refine-to (toh a) (map toh args))
        [[:type-method a "refinesTo?" & args]]       (list* 'refines-to? (toh a) (map toh args))
        [[:map & args]]          (into {} (map toh) args)
        [[:map-entry "$type" v]] [:$type (toh v)]
        [[:map-entry [_ k] v]]   [(keyword (unwrap-symbol k)) (toh v)]
        [[:set & args]]          (set (map toh args))
        [[:vec & args]]          (vec (map toh args))
        [[:let & args]]          (if (next args)
                                   (list 'let (mapv toh (drop-last args)) (toh (last args)))
                                   (toh (last args)))
        [[:int & strs]]          (parse-long (apply str strs))
        [[:decimal s]]           (fixed-decimal/fixed-decimal-reader (edn/read-string (second s)))
        [[:symbol "true"]]       true
        [[:symbol "false"]]      false
        [[:symbol s]]            (unwrap-symbol s)
        [[:typename [_ s]]]      (keyword (unwrap-symbol s))
        [[:typename s]]          (keyword s)
        [[:string s]]            (edn/read-string s)

             ;; Default to descending through intermediate grammar nodes
        [[(_ :guard keyword?) (kid :guard vector?)]] (toh kid)
        :else (throw (ex-info (str "Unhandled parse tree:\n" (pr-str tree))
                              {:tree tree})))
      flatten-variadics
      (add-source-metadata-when-possible tree)))

(def whitespace-or-comments
  (insta/parser
   "ws-or-comments = #'\\s+' | comment+
    comment = #'\\s*//.*(\\n\\s*|$)'"))

(def parse
  (insta/parser (io/resource "jibe/jadeite.bnf")
                :auto-whitespace whitespace-or-comments))

(defn to-halite [jadeite-string]
  (let [tree (->> jadeite-string
                  parse
                  (insta/add-line-and-column-info-to-metadata jadeite-string))]
    (when (insta/failure? tree)
      (throw (ex-info (pr-str tree) {:parse-failure tree})))
    (toh tree)))

;;;;
;; From Halite to Jadeite

(declare toj)

(def ^:dynamic *pprint* false)

(defn infix
  ([args] (infix "(" ", " ")" args))
  ([op args] (infix "(" op ")" args))
  ([pre op post args & {:keys [sort?]}]
   (let [parts (map toj args)
         ordered-parts (if sort? (sort parts) parts)]
     (if (and *pprint*
              (or (some #(re-find #"\n" %) parts)
                  (< 70 (reduce + (map count parts)))))
       (apply str (concat [pre "\n"]
                          (interpose (str op "\n")
                                     (map #(string/replace % #"\n" "\n  ")
                                          ordered-parts))
                          [post]))
       (apply str (concat [pre] (interpose op ordered-parts) [post]))))))

(defn typename [kw]
  (let [without-colon (subs (str kw) 1)]
    (if (and *pprint* (re-find #"[^a-zA-Z0-9./$]" without-colon))
      (str "<" without-colon ">")
      without-colon)))

(defn call-method [method-name [target & args]]
  (let [args-str (infix args)]
    (if (re-find #"\n" args-str)
      (str (toj target) "\n." method-name (string/replace args-str #"\n" "\n  "))
      (str (toj target) "." method-name args-str))))

(defn call-fn-or-method [op args]
  (if (global-fns op)
    (str op (infix ", " args))
    (call-method op args)))

(defn wrap-string [s]
  (if (re-find #"[^a-zA-Z0-9]" s)
    (str "'" s "'")
    s))

(defn toj [x]
  (cond
    (string? x) (pr-str x)
    (halite/fixed-decimal? x) (str "#d" " \"" (fixed-decimal/string-representation x) "\"")
    (keyword? x) (typename x)
    (symbol? x) (wrap-string (str x))
    (set? x) (infix "#{" ", " "}" x :sort? true)
    (map? x) (infix "{" ", " "}" x :sort? true)
    (map-entry? x) (let [[k v] x]
                     (str (if (= :$type k)
                            (name k)
                            (wrap-string (name k)))
                          ": "
                          (if (= :$type k)
                            (typename v)
                            (toj v))))
    (vector? x) (infix "[" ", " "]" x)
    (seq? x) (let [[op & [a0 a1 a2 :as args]] x]
               (case op
                 (< <= > >= + - * =>) (infix (str " " op " ") args)
                 = (if (> (count args) 2)
                     (str "equalTo" (infix ", " args))
                     (infix " == " args))
                 (every? any? map filter) (str op (infix " in " a0) (toj a1))
                 sort-by (str "sortBy" (infix " in " a0) (toj a1))
                 reduce (let [[[acc init] [elem coll] body] args]
                          (str "(reduce( " (toj acc) " = " (toj init) "; " (toj elem) " in " (toj coll)
                               " ) { " (toj body) " })"))
                 and (infix " && " args)
                 dec (str "(" (toj a0) " - 1)")
                 div (infix " / " args)
                 fn (str (infix a0) " -> " (toj a1))
                 get (if (keyword? a1)
                       (str (toj a0) '. (wrap-string (name a1)))
                       (str (toj a0) "[" (toj a1) "]"))
                 get-in (reduce (fn [target index]
                                  (if (keyword? index)
                                    (str target '. (wrap-string (name index)))
                                    (str target "[" (toj index) "]")))
                                (toj a0)
                                a1)
                 if (str "(if(" (toj a0)
                         ") {" (toj a1)
                         "} else {" (toj a2) "})")
                 when (str "(when(" (toj a0) ") {" (toj a1) "})") ; WAT
                 valid? (str "(valid? " (toj a0) ")")
                 valid (str "(valid " (toj a0) ")")
                 (if-value if-value-) (str "(ifValue(" (toj a0)
                                           ") {" (toj a1)
                                           "} else {" (toj a2) "})")
                 when-value (str "(whenValue(" (toj a0) ") {" (toj a1) "})")
                 if-value-let (apply format "(ifValueLet ( %s = %s ) {%s} else {%s})"
                                     (map toj [(first a0) (second a0) a1 a2]))
                 when-value-let (apply format "(whenValueLet ( %s = %s ) {%s})"
                                       (map toj [(first a0) (second a0) a1]))
                 inc (str "(" (toj a0) " + 1)")
                 let (let [[bindings expr] args]
                       (str "({ "
                            (->> bindings
                                 (partition 2)
                                 (mapcat (fn [[k v]]
                                           [(toj k) " = " (toj v) "; "]))
                                 (apply str))
                            (toj expr)
                            " })"))
                 mod (infix " % " args)
                 not (str "!" (toj a0))
                 not= (if (> (count args) 2)
                        (str "notEqualTo" (infix ", " args))
                        (infix " != " args))
                 or (infix " || " args)
                 reduce- (str (toj (last args)) ".reduce" (infix (drop-last args)))
                 refine-to (str (toj a0) ".refineTo( " (typename a1) " )")
                 refines-to? (str (toj a0) ".refinesTo?( " (typename a1) " )")
                 select (call-method "select" (reverse args))
                 ;; default:
                 (call-fn-or-method op args)))
    :else (str x)))

(def to-jadeite
  "Translate halite form into jadeite string"
  toj)

;; Implement jibe expression multimethods

(defmethod expression/parse-text* :jadeite
  [_ text]
  (to-halite text))

(defmethod expression/format-text* :jadeite
  [_ tree]
  (to-jadeite tree))

(defn init
  "This is here to have a hook to call to get the multimethods loaded"
  [])
