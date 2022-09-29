;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.doc.md-full
  (:require [clojure.string :as string]
            [jibe.data.model :as model]
            [jibe.halite.doc.utils :as utils]
            [internal :as s]))

(s/defn full-intro
  [lang
   mode]
  ["## All Operators\n\n"
   "The syntax for all of the operators is summarized in the following \n\n"
   (if (= mode :user-guide)
     (utils/get-svg-link (str "halite-bnf-diagrams/" (if (= :halite lang) "halite" "jadeite") ".svg"))
     (str "![" "all operators" "](../halite-bnf-diagrams/" (if (= :halite lang) "halite" "jadeite") ".svg)\n\n"))
   "## Operators\n\n"])

(s/defn full-md
  [{:keys [lang tag-reference tag-def-map]} {:keys [mode prefix]} op-name op]
  (->> ["### "
        "<a name=\"" (utils/safe-op-anchor op-name) "\"></a>"
        op-name "\n\n" (if (= :halite lang) (:doc op) (or (:doc-j op) (:doc op))) "\n\n"
        (when-let [d2 (:doc-2 op)] [d2 "\n\n"])
        (map-indexed
         (fn [i sig]
           (if (= :user-guide mode)
             ;; user-guide format
             [(utils/get-svg-link (str "halite-bnf-diagrams/op/"
                                       (utils/url-encode (utils/safe-op-name op-name)) "-" i (when (= :jadeite lang) "-j") ".svg"))]
             ;; local doc format
             ["![" (pr-str sig) "](../halite-bnf-diagrams/op/"
              (utils/url-encode (utils/safe-op-name op-name)) "-" i (when (= :jadeite lang) "-j") ".svg)\n\n"]))
         (op ({:halite :sigs, :jadeite :sigs-j} lang)))
        (when-let [md-links (utils/basic-ref-links lang mode prefix op "")]
          ["#### Basic elements:\n\n" (interpose ", " md-links) "\n\n"])
        (when-let [c (:comment op)] [c "\n\n"])
        (when-let [es (:examples op)]
          ["#### Examples:\n\n"
           "<table>"
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
          ["#### Possible errors:\n\n"
           (for [msg (sort t)]
             (str "* " "[`" msg "`]("
                  (utils/get-reference lang mode prefix "err-id-reference")
                  "#" (utils/safe-op-anchor msg) ")" "\n"))
           "\n"])
        (when-let [alsos (:op-ref op)]
          ["See also:"
           (for [a (sort (remove #(= op-name %) alsos))]
             [" [`" a "`](#" (utils/safe-op-anchor a) ")"])
           "\n\n"])
        (when-let [how-to-refs (:how-to-ref op)]
          ["#### How tos:\n\n"
           (for [a (sort how-to-refs)]
             (str "* " "[" (name a) "](" (when (= :local mode) "how-to/") (utils/get-reference lang mode prefix (name a)) ")" "\n"))
           "\n\n"])
        (when-let [tutorial-refs (:tutorial-ref op)]
          ["#### Tutorials:\n\n"
           (for [a (sort tutorial-refs)]
             (str "* " "[" (name a) "](" (when (= :local mode) "tutorial/") (utils/get-reference lang mode prefix (name a)) ")" "\n"))
           "\n\n"])
        (when-let [explanation-refs (:explanation-ref op)]
          ["#### Explanations:\n\n"
           (for [a (sort explanation-refs)]
             (str "* " "[" (name a) "](" (when (= :local mode) "explanation/") (utils/get-reference lang mode prefix (name a)) ")" "\n"))
           "\n\n"])
        (when-let [tags (:tags op)]
          ["#### Tags:" "\n\n"
           (string/join ", "
                        (for [a (sort tags)]
                          (let [a (name a)]
                            (str " [" (:label (tag-def-map (keyword a))) "]("
                                 (tag-reference lang mode prefix a)
                                 ")"))))
           "\n\n"])
        "---\n"]))

(s/defn full-md-all
  [{:keys [lang] :as info} {:keys [mode sidebar-file] :as config} op-maps]
  (->> [(when (= :user-guide mode)
          (utils/append-user-guide-sidebar sidebar-file (get-in utils/user-guide-values [:md_full lang :title]) (get-in utils/user-guide-values [:md_full lang :link]) false)
          (utils/generate-user-guide-hdr "Halite Full Reference" "halite_full_reference" "/halite" "Halite operator reference (all operators)"))
        utils/generated-msg
        "# "
        (utils/lang-str lang)
        " operator reference (all operators)\n\n"
        (full-intro lang mode)
        (->> op-maps
             (map (fn [[op-name op]]
                    (full-md info config op-name op))))]
       flatten
       (apply str)))
