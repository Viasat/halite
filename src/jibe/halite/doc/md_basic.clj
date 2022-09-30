;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.doc.md-basic
  (:require [clojure.string :as string]
            [jibe.data.model :as model]
            [jibe.halite.doc.utils :as utils]
            [schema.core :as s]))

(defn tags-md-block
  "Return markdown string with links to all the tags given as keywords"
  [{:keys [lang tag-def-map tag-reference]} {:keys [mode prefix]} tags]
  (string/join ", "
               (for [a (sort tags)]
                 (str " [" (:label (tag-def-map a)) "]("
                      (tag-reference lang mode prefix (name a))
                      ")"))))

(defn basic-md [{:keys [lang] :as info} {:keys [mode prefix] :as config} op-name op]
  (let [bnf (if (= :halite lang)
              (:bnf op)
              (if (contains? (set (keys op)) :bnf-j)
                (:bnf-j op)
                (or (:bnf-j op)
                    (:bnf op))))]
    (when bnf
      (->> ["## "
            "<a name=\"" (utils/safe-op-anchor op-name) "\"></a>"
            op-name "\n\n" (if (= :halite lang) (:doc op) (or (:doc-j op) (:doc op))) "\n\n"
            (when-let [d2 (:doc-2 op)] [d2 "\n\n"])
            (if (= :user-guide mode)
              ;; user-guide format
              [(utils/get-svg-link (str "halite-bnf-diagrams/basic-syntax/" (utils/url-encode (utils/safe-op-name op-name)) (when (= :jadeite lang) "-j") ".svg"))]
              ["![" (pr-str bnf) "](../halite-bnf-diagrams/basic-syntax/" (utils/url-encode (utils/safe-op-name op-name)) (when (= :jadeite lang) "-j") ".svg)\n\n"])
            (let [c-1 (if (= :halite lang) (:comment op) (or (:comment-j op) (:comment op)))
                  c-2 (if (= :halite lang) (:comment-2 op) (or (:comment-2-j op) (:comment-2 op)))
                  c-3 (if (= :halite lang) (:comment-3 op) (or (:comment-3-j op) (:comment-3 op)))]
              (when (or c-1 c-2 c-3) [(string/join " " [c-1 c-2 c-3]) "\n\n"]))
            (when-let [es (:examples op)]
              ["<table>"
               (for [row (utils/text-tile-rows (map (partial utils/example-text lang) es))]
                 ["<tr>"
                  (for [tile (:tiles row)]
                    (if (= :user-guide mode)
                      ;; user-guide mode: examples are assigned to variable, then run through the liquid filter mardownify to render html
                      ["<td colspan=\"" (:cols tile) "\">\n\n"
                       (str "{% assign example = \n'```" ({:halite "clojure", :jadeite "java"} lang) "\n" (:text tile) "\n```' %}\n")
                       "{{ example | markdownify }}\n"
                       "\n</td>"]
                      ;; local docs can be formatted normally
                      ["<td colspan=\"" (:cols tile) "\">\n\n"
                       "```" ({:halite "clojure", :jadeite "java"} lang) "\n"
                       (:text tile)
                       "\n```\n\n</td>"]))
                  "</tr>"])
               "</table>\n\n"])
            (when-let [t (:throws op)]
              ["### Possible errors:\n\n"
               (for [msg (sort t)]
                 (str "* " "[`" msg "`]("
                      (utils/get-reference lang mode prefix "err-id-reference")
                      "#" (utils/safe-op-anchor msg) ")" "\n"))
               "\n"])
            (when-let [tags (seq (or (when (= :jadeite lang)
                                       (:tags-j op))
                                     (:tags op)))]
              ["### Tags:\n\n" (tags-md-block info config (map keyword tags)) "\n\n"])
            (when-let [how-to-refs (:how-to-ref op)]
              ["### How tos:\n\n"
               (for [a (sort how-to-refs)]
                 (str "* " "[" (name a) "](" (when (= :local mode) "how-to/") (utils/get-reference lang mode prefix (name a)) ")" "\n"))
                 ;(str "* " "[" (name a) "](" "how-to/" (name a) ".md" ")" "\n"))
               "\n\n"])
            (when-let [tutorial-refs (:tutorial-ref op)]
              ["### Tutorials:\n\n"
               (for [a (sort tutorial-refs)]
                 (str "* " "[" (name a) "](" (when (= :local mode) "tutorial/") (utils/get-reference lang mode prefix (name a)) ")" "\n"))
                 ;(str "* " "[" (name a) "](" "tutorial/" (name a) ".md" ")" "\n"))
               "\n\n"])
            (when-let [explanation-refs (:explanation-ref op)]
              ["### Explanations:\n\n"
               (for [a (sort explanation-refs)]
                 (str "* " "[" (name a) "](" (when (= :local mode) "explanation/") (utils/get-reference lang mode prefix (name a)) ")" "\n"))
                 ;(str "* " "[" (name a) "](" "explanation/" (name a) ".md" ")" "\n"))
               "\n\n"])

            "---\n"]
           flatten (apply str)))))

(defn diagram-description [x]
  (str "The syntax diagrams are a graphical representation of the grammar rules for the different " x ".\n\n"))

(def element-name-description "In the diagrams when a rule starts with 'element_name:', this is not part of the syntax for the grammar element, but is instead naming the grammar element so it can be referenced in subsequent diagrams.\n\n")

(def label-description "In the diagrams when a grammar element appears as 'x:label' the label is simply a descriptive label to convey to the reader the meaining of the element.\n\n")

(s/defn produce-basic-core-md [{:keys [lang] :as info} {:keys [mode sidebar-file] :as config} basic-bnf]
  (str
   (when (= :user-guide mode)
     (utils/append-user-guide-sidebar sidebar-file (get-in utils/user-guide-values [:md_basic lang :title]) (get-in utils/user-guide-values [:md_basic lang :link]) false)
     (utils/generate-user-guide-hdr "Halite Basic Syntax Reference" "halite-basic-syntax-reference" "/halite" "Halite basic syntax reference"))
   utils/generated-msg
   "# "
   (utils/lang-str lang)
   " basic syntax and types reference\n\n"
   (diagram-description "elements")
   element-name-description
   label-description
   (->> basic-bnf
        (partition 2)
        (map (partial apply basic-md info config))
        (apply str))
   "# Type Graph"
   (if (= :user-guide mode)
     (str "![" "type graph" "](images/types.dot.png)\n\n")
     (str "![" "type graph" "](../types.dot.png)\n\n"))))
