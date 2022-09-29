;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.doc.utils
  (:require [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [jibe.lib.fixed-decimal :as fixed-decimal]
            [jibe.logic.jadeite :as jadeite]
            [zprint.core :as zprint]
            [schema.core :as s]))

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
                                       :style :backtranslate
                                       :map {:force-nl? true
                                             :key-order [:abstract? :spec-vars :constraints :refines-to
                                                         :name :expr :inverted?]}})]
     (if trailing-newline?
       (if (= \newline s)
         s
         (str s "\n"))
       (if (= \newline s)
         (subs s 0 (dec (count s)))
         s)))))

(defn update-spec-map-exprs
  "Given a map of specs, update the halite code expressions (constraints and
  refinement expressions) using f."
  [spec-map f]
  (-> spec-map
      (update-vals (fn [spec]
                     (cond-> spec
                       (:constraints spec)
                       (update :constraints #(->> %
                                                  (mapv (fn [[name expr]]
                                                          [name (f expr)]))))
                       (:refines-to spec)
                       (update :refines-to update-vals (fn [refinement]
                                                         (update refinement :expr f))))))))

(defn spec-map-str [lang spec-map]
  ({:halite (pprint-halite (update-spec-map-exprs spec-map #(list 'quote %)))
    :jadeite (str (json/encode (update-spec-map-exprs spec-map jadeite/to-jadeite)
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

(s/defn lang-str [lang]
  (or ({:halite "Halite"
        :jadeite "Jadeite"} lang)
      (throw (ex-info (str "Unsupported lang " lang) {:lang lang}))))

(def user-guide-values {:md_full {:halite {:title "Halite Full Reference"
                                           :link "halite_full_reference.html"
                                           :subdir "/halite"
                                           :summary "Halite operator reference (all operators)"}
                                  :jadeite {:title "Jadeite Full Reference"
                                            :link "jadeite_full_reference.html"
                                            :subdir "/jadeite"
                                            :summary "Jadeite operator reference (all operators)"}}
                        :md_basic {:halite {:title "Halite Basic Syntax Reference"
                                            :link "halite-basic-syntax-reference.html"
                                            :subdir "/halite"
                                            :summary "Halite basic syntax reference"}
                                   :jadeite {:title "Jadeite Basic Syntax Reference"
                                             :link "jadeite-basic-syntax-reference.html"
                                             :subdir "/jadeite"
                                             :summary "Jadeite basic syntax reference"}}
                        :md_err {:halite {:title "Halite Error ID Reference"
                                          :link "halite-err-id-reference.html"
                                          :subdir "/halite"
                                          :summary "Halite err-id reference"}
                                 :jadeite {:title "Jadeite Error ID Reference"
                                           :link "jadeite-err-id-reference.html"
                                           :subdir "/jadeite"
                                           :summary "Jadeite err-id reference"}}})

;; user-guide requires this header
(s/defn generate-user-guide-hdr [title link subdir summary]

  (str "---\n"
       "title: " title "\n"
       "tags: \n"
       "keywords: \n"
       "sidebar: jibe_sidebar\n"
       "permalink: " link ".html\n"
       "folder: jibe/halite" subdir "\n"
       "summary: " summary "\n"
       "---\n\n"))

;; generage html object block to display svg image
(s/defn get-svg-link
  [svg]
  (str "<object type=\"image/svg+xml\" data=\"images/" svg "\"></object>\n\n"))

(defn get-reference
  [lang
   mode
   prefix
   filename]
  (str prefix filename (when (= :jadeite lang) "-j") (if (= :user-guide mode)
                                                       ".html"
                                                       ".md")))

(defn get-tag-reference [lang mode prefix tag]
  (if (= mode :user-guide)
    (str prefix tag "-reference" (when (= :jadeite lang) "-j") ".html")
    (str prefix tag "-reference" (when (= :jadeite lang) "-j") ".md")))

(defn basic-ref-links
  "Returns a list of markdown links based on the :basic-ref or :basic-ref-j
  entries in the given data map."
  [lang mode prefix data doc-dir]
  (let [v (if (= :halite lang)
            (:basic-ref data)
            (or (:basic-ref-j data)
                (:basic-ref data)))]
    (when-let [basic-refs (if (symbol? v) [v] v)]
      (for [basic-ref (sort basic-refs)]
        ["[`" basic-ref "`]"
         "("
         doc-dir
         (get-tag-reference lang mode prefix "basic-syntax")
         "#" basic-ref
         ")"]))))

(defn create-sidebar-entry [file-path title]
  (spit file-path (str "  - title: " title "\n"
                       "    output: web\n"
                       "    folderitems:\n\n")
        :append true))

(defn create-sidebar-subfolder [file-path title]
  (spit file-path (str "      - title: " title "\n"
                       "        output: web\n"
                       "        subfolderitems:\n\n")
        :append true))

(defn append-subfolders [file-path]
  (spit file-path "      subfolders:\n" :append true))

(defn append-user-guide-sidebar [file-path title filename subfile]
  (if subfile
    (spit file-path (str "        - title: " title "\n"
                         "          url: /" filename "\n"
                         "          output: web\n\n")
          :append true)
    (spit file-path (str "    - title: " title "\n"
                         "      url: /" filename "\n"
                         "      output: web\n\n")
          :append true)))

(defn get-reference-extension [mode]
  (if (= :user-guide mode)
    ".html"
    ".md"))

(defn get-language-modifier [lang]
  (if (= :halite lang)
    ""
    "-j"))
