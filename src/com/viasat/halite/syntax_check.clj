;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.syntax-check
  "Syntax checker for halite"
  (:require [com.viasat.halite.base :as base]
            [com.viasat.halite.h-err :as h-err]
            [com.viasat.halite.lib.format-errors :as format-errors]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(def max-symbol-length 256)

(def symbol-regex
  ;; from chouser, started with edn symbol description https://github.com/edn-format/edn#symbols
  #"(?x) # allow comments and whitespace in regex
        (?: # prefix (namespace) part
          (?: # first character of prefix
              [A-Za-z*!$?=<>_.]      # begin with non-numeric
              | [+-] (?=[^0-9]))     # for +/-, second character must be non-numeric
          [0-9A-Za-z*!$?=<>_+.-]*    # subsequent characters of prefix
          /                          # prefix / name separator
        )?                           # prefix is optional
        (?: # name (suffix) part
          (?!true$) (?!false$) (?!nil$) # these are not to be read as symbols
          (?: # first character of name
              [A-Za-z*!$?=<>_.]      # begin with non-numeric
              | [+-] (?=[^0-9]|$))   # for +/-, second character must be non-numeric
          [0-9A-Za-z*!$?=<>_+.-]*)   # subsequent characters of name
        ")

(defn- check-symbol-string [expr]
  (let [s (if (symbol? expr)
            (str expr)
            (subs (str expr) 1))]
    (when-not (re-matches symbol-regex s)
      (format-errors/throw-err ((if (symbol? expr)
                                  h-err/invalid-symbol-char
                                  h-err/invalid-keyword-char) {:form expr})))
    (when-not (<= (count s) max-symbol-length)
      (format-errors/throw-err ((if (symbol? expr)
                                  h-err/invalid-symbol-length
                                  h-err/invalid-keyword-length) {:form expr
                                                                 :length (count s)
                                                                 :limit max-symbol-length})))))

(defn check-n [object-type n v error-context]
  (when (and n
             (> v n))
    (format-errors/throw-err (h-err/limit-exceeded {:object-type object-type
                                                    :value v
                                                    :limit n}))))

(s/defn syntax-check
  ([expr]
   (syntax-check 0 expr))
  ([depth expr]
   (check-n "expression nesting" (get base/*limits* :expression-nesting-depth) depth {})
   (cond
     (boolean? expr) true
     (base/integer-or-long? expr) true
     (base/fixed-decimal? expr) true
     (string? expr) (do (base/check-limit :string-literal-length expr) true)
     (symbol? expr) (do (check-symbol-string expr) true)
     (keyword? expr) (do (check-symbol-string expr) true)

     (map? expr) (and (or (:$type expr)
                          (format-errors/throw-err (h-err/missing-type-field {:expr expr})))
                      (->> expr
                           (mapcat identity)
                           (map (partial syntax-check (inc depth)))
                           dorun))
     (seq? expr) (do
                   (or (#{'=
                          'rescale
                          'any?
                          'concat
                          'conj
                          'difference
                          'every?
                          'filter
                          'first
                          'get
                          'get-in
                          'if
                          'cond
                          'if-value
                          'if-value-let
                          'intersection
                          'let
                          'map
                          'not=
                          'reduce
                          'refine-to
                          'refines-to?
                          'rest
                          'sort-by
                          'union
                          'valid
                          'valid?
                          'when
                          'when-value
                          'when-value-let} (first expr))
                       (base/builtin-symbols (first expr))
                       (format-errors/throw-err (h-err/unknown-function-or-operator {:op (first expr)
                                                                                     :expr expr})))
                   (base/check-limit :list-literal-count expr)
                   (->> (rest expr)
                        (map (partial syntax-check (inc depth)))
                        dorun)
                   true)
     (or (vector? expr)
         (set? expr)) (do (base/check-limit (cond
                                              (vector? expr) :vector-literal-count
                                              (set? expr) :set-literal-count)
                                            expr)
                          (->> (map (partial syntax-check (inc depth)) expr) dorun))
     :else (format-errors/throw-err (h-err/syntax-error {:form expr :form-class (class expr)})))))
