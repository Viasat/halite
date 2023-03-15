;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.doc.md-basic
  (:require [clojure.string :as string]
            [com.viasat.halite.doc.doc-util :as doc-util]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(defn tags-md-block
  "Return markdown string with links to all the tags given as keywords"
  [{:keys [lang tag-def-map]} {:keys [prefix get-link-f]} tags]
  (string/join ", "
               (for [a (sort tags)]
                 (str " [" (:label (tag-def-map a)) "]("
                      (get-link-f lang prefix "" (str (name a) "-reference"))
                      ")"))))

(defn basic-md [{:keys [lang] :as info} {:keys [prefix get-link-f get-svg-link-f get-table-data-f] :as config} op-name op]
  (let [bnf (if (= :halite lang)
              (:bnf op)
              (if (contains? (set (keys op)) :bnf-j)
                (:bnf-j op)
                (or (:bnf-j op)
                    (:bnf op))))]
    (when bnf
      (->> ["## "
            "<a name=\"" (doc-util/safe-op-anchor op-name) "\"></a>"
            op-name "\n\n" (if (= :halite lang) (:doc op) (or (:doc-j op) (:doc op))) "\n\n"
            (when-let [d2 (:doc-2 op)] [d2 "\n\n"])
            (get-svg-link-f "halite-bnf-diagrams/basic-syntax/" (str (doc-util/url-encode (doc-util/safe-op-name op-name)) (doc-util/get-language-modifier lang)) (pr-str bnf))
            (let [c-1 (if (= :halite lang) (:comment op) (or (:comment-j op) (:comment op)))
                  c-2 (if (= :halite lang) (:comment-2 op) (or (:comment-2-j op) (:comment-2 op)))
                  c-3 (if (= :halite lang) (:comment-3 op) (or (:comment-3-j op) (:comment-3 op)))]
              (when (or c-1 c-2 c-3) [(string/join " " [c-1 c-2 c-3]) "\n\n"]))
            (when-let [es (:examples op)]
              ["<table>"
               (for [row (doc-util/text-tile-rows (map (partial doc-util/example-text lang) es))]
                 ["<tr>"
                  (for [tile (:tiles row)]
                    (get-table-data-f lang tile))
                  "</tr>"])
               "</table>\n\n"])
            (when-let [t (:throws op)]
              ["### Possible errors:\n\n"
               (for [msg (sort t)]
                 (str "* " "[`" msg "`]("
                      (get-link-f lang prefix "" "err-id-reference")
                      "#" (doc-util/safe-op-anchor msg) ")" "\n"))
               "\n"])
            (when-let [tags (seq (or (when (= :jadeite lang)
                                       (:tags-j op))
                                     (:tags op)))]
              ["### Tags:\n\n" (tags-md-block info config (map keyword tags)) "\n\n"])
            (when-let [how-to-refs (:how-to-ref op)]
              ["### How tos:\n\n"
               (for [a (sort how-to-refs)]
                 (str "* " "[" (name a) "](" (get-link-f lang prefix "how-to/" (name a)) ")" "\n"))
               "\n\n"])
            (when-let [tutorial-refs (:tutorial-ref op)]
              ["### Tutorials:\n\n"
               (for [a (sort tutorial-refs)]
                 (str "* " "[" (name a) "](" (get-link-f lang prefix "tutorial/" (name a)) ")" "\n"))
               "\n\n"])
            (when-let [explanation-refs (:explanation-ref op)]
              ["### Explanations:\n\n"
               (for [a (sort explanation-refs)]
                 (str "* " "[" (name a) "](" (get-link-f lang prefix "explanation/" (name a)) ")" "\n"))
               "\n\n"])

            "---\n"]
           flatten (apply str)))))

(defn diagram-description [x]
  (str "The syntax diagrams are a graphical representation of the grammar rules for the different " x ".\n\n"))

(def element-name-description "In the diagrams when a rule starts with 'element_name:', this is not part of the syntax for the grammar element, but is instead naming the grammar element so it can be referenced in subsequent diagrams.\n\n")

(def label-description "In the diagrams when a grammar element appears as 'x:label' the label is simply a descriptive label to convey to the reader the meaining of the element.\n\n")

(s/defn produce-basic-core-md [{:keys [lang] :as info} {:keys [generate-hdr-f get-image-link-f] :as config} basic-bnf]
  (str
   (generate-hdr-f "Halite Basic Syntax Reference" (str "halite_basic-syntax-reference" (doc-util/get-language-modifier lang)) (str "/" (name lang)) "Halite basic syntax reference")
   "# "
   (doc-util/lang-str lang)
   " basic syntax and types reference\n\n"
   (diagram-description "elements")
   element-name-description
   label-description
   (->> basic-bnf
        (partition 2)
        (map (partial apply basic-md info config))
        (apply str))
   "# Type Graph"
   (get-image-link-f "types.dot.png" "type graph")))
