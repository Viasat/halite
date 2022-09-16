;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.doc.utils
  (:require [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [jibe.lib.fixed-decimal :as fixed-decimal]
            [jibe.logic.jadeite :as jadeite]
            [zprint.core :as zprint]))

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

(defn pprint-halite
  ([code]
   (pprint-halite code true))
  ([code trailing-newline?]
   (let [s (zprint/zprint-str code 80 {:fn-force-nl #{:binding}
                                       :map {:force-nl? true
                                             :key-order [:spec-vars :constraints :refines-to
                                                         :name :expr]}})]
     (if trailing-newline?
       (if (= \newline s)
         s
         (str s "\n"))
       (if (= \newline s)
         (subs s 0 (dec (count s)))
         s)))))

(defn spec-map-str [lang spec-map]
  ({:halite (pprint-halite spec-map)
    :jadeite (str (json/encode (update-vals (update-vals spec-map
                                                         (fn [spec]
                                                           (if-let [constraints (:constraints spec)]
                                                             (assoc spec
                                                                    :constraints (->> constraints
                                                                                      (mapv (fn [[name expr]]
                                                                                              [name (jadeite/to-jadeite expr)]))))
                                                             spec)))
                                            (fn [spec]
                                              (if-let [refines-to (:refines-to spec)]
                                                (assoc spec
                                                       :refines-to (->> refines-to
                                                                        (mapcat (fn [[name refinement]]
                                                                                  [name (assoc refinement
                                                                                               :expr (jadeite/to-jadeite (:expr refinement)))]))
                                                                        (apply hash-map)))
                                                spec)))
                               {:pretty true}) "\n")} lang))

;; (zprint/zprint nil :explain)

(defn read-edn [s]
  (edn/read-string {:readers {'d fixed-decimal/fixed-decimal-reader}}
                   s))

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

(defn text-width [s]
  (apply max 0 (map count (re-seq #".*" s))))

(defn text-tile-rows [texts]
  (let [chars-per-col 20
        cols-per-row 5]
    (reduce (fn [rows text]
              (let [cols (inc (quot (dec (text-width text)) chars-per-col))
                    tile {:text text, :cols cols}
                    last-row (peek rows)]
                (if (or (empty? rows)
                        (< cols-per-row (+ cols (:cols last-row))))
                  (conj rows {:cols cols :tiles [tile]})
                  (conj (pop rows) (-> last-row
                                       (update :cols + cols)
                                       (update :tiles conj tile))))))
            []
            texts)))

(defn example-text [lang e]
  (let [{:keys [spec-map doc]} e
        expr (if (= :halite lang)
               (:expr-str e)
               (or (:expr-str-j e)
                   (:expr-str e)))
        result (or (:result e)
                   (:err-result e))]
    (str (when doc
           (str ({:halite  ";-- "
                  :jadeite "//-- "}
                 lang)
                doc
                "\n"))
         (when spec-map
           (str ({:halite  ";-- context --\n"
                  :jadeite "//-- context --\n"}
                 lang)
                (spec-map-str lang spec-map)
                ({:halite  ";--\n\n"
                  :jadeite "//\n\n"}
                 lang)))
         expr ;; this is a string that, in the case of halite, has been custom formatted by hand in the example
         (when result ({:halite  "\n\n;-- result --\n"
                        :jadeite "\n\n//-- result --\n"}
                       lang))
         (when result result))))

(defn lang-str [lang]
  ({:halite "Halite"
    :jadeite "Jadeite"} lang))
