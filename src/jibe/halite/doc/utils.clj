;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.doc.utils
  (:require [clojure.java.io :as io]))

(defn spit-dir [filename txt]
  (io/make-parents filename)
  (spit filename txt))

(defn safe-op-name [s]
  (get {'+ 'plus
        '- 'minus
        '% 'mod
        '&& 'and
        '|| 'or
        '! 'not
        '/ 'div
        '== 'doublequal
        '!= 'notequal} s s))

(def jadeite-operator-map {'= ['equalTo '==]
                           'sort-by ['sortBy]
                           'and ['&&]
                           'div ['/]
                           'get ['ACCESSOR]
                           'get-in ['ACCESSOR-CHAIN]
                           'when-value ['whenValue]
                           'when-value-let ['whenValueLet]
                           'if-value ['ifValue]
                           'if-value-let ['ifValueLet]
                           'mod ['%]
                           'not ['!]
                           'not= ['notEqualTo '!=]
                           'or ['||]
                           'refine-to ['refineTo]
                           'refines-to? ['refinesTo?]})

(defn translate-op-name-to-jadeite [op-name]
  (if-let [op-names-j (get jadeite-operator-map op-name)]
    (first op-names-j)
    op-name))

(defn translate-op-name-to-jadeite-plural [op-name]
  (if-let [op-names-j (get jadeite-operator-map op-name)]
    op-names-j
    [op-name]))
