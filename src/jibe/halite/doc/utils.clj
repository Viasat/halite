;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.doc.utils
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]))

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

(defn code-snippet [lang code]
  (str "```"
       ({:halite "clojure", :jadeite "java"} lang) "\n"
       code
       "```\n\n"))

(defn spec-map-str [lang spec-map]
  ({:halite (with-out-str (pprint/pprint spec-map))
    :jadeite (str (json/encode spec-map {:pretty true}) "\n")} lang))

(def safe-char-map
  (let [weird "*!$?=<>_+."
        norml "SBDQELGUAP"]
    (zipmap weird (map #(str "_" %) norml))))

(defn safe-op-anchor
  "To avoid github markdown behavior of stripping out special characters and
  then avoiding collisions with an auto-incrementing number, use this function
  to generate anchors and links that github will leave unmolested."
  [s]
  (apply str (map #(safe-char-map % %) (str s))))

(defn url-encode [s]
  (java.net.URLEncoder/encode (str s)))

(def generated-msg
  "<!---
  This markdown file was generated. Do not edit.
  -->\n\n")
