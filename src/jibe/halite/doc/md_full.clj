;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.doc.md-full
  (:require [jibe.halite.doc.utils :as utils]
            [clojure.string :as string]))

(defn full-intro [lang]
  ["## All Operators\n\n"
   "The syntax for all of the operators is summarized in the following \n\n"
   "![" "all operators" "](./halite-bnf-diagrams/" (if (= :halite lang) "halite" "jadeite") ".svg)\n\n"
   "## Operators\n\n"])

(defn full-md [{:keys [lang tag-md-filename tag-def-map]} op-name op]
  (->> ["### "
        "<a name=\"" (utils/safe-op-anchor op-name) "\"></a>"
        op-name "\n\n" (if (= :halite lang) (:doc op) (or (:doc-j op) (:doc op))) "\n\n"
        (when-let [d2 (:doc-2 op)] [d2 "\n\n"])
        (map-indexed
         (fn [i sig]
           ["![" (pr-str sig) "](./halite-bnf-diagrams/op/"
            (utils/url-encode (utils/safe-op-name op-name)) "-" i (when (= :jadeite lang) "-j") ".svg)\n\n"])
         (op ({:halite :sigs, :jadeite :sigs-j} lang)))
        (when-let [md-links (utils/basic-ref-links lang op "")]
          ["#### Basic elements:\n\n" (interpose ", " md-links) "\n\n"])
        (when-let [c (:comment op)] [c "\n\n"])
        (when-let [es (:examples op)]
          ["#### Examples:\n\n"
           "<table>"
           (for [row (utils/text-tile-rows (map (partial utils/example-text lang) es))]
             ["<tr>"
              (for [tile (:tiles row)]
                ["<td colspan=\"" (:cols tile) "\">\n\n"
                 "```" ({:halite "clojure", :jadeite "java"} lang) "\n"
                 (:text tile)
                 "\n```\n\n</td>"])
              "</tr>"])
           "</table>\n\n"])
        (when-let [t (:throws op)]
          ["#### Possible errors:\n\n"
           (for [msg (sort t)]
             (str "* " "[`" msg "`]("
                  (if (= :halite lang)
                    "halite-err-id-reference.md"
                    "jadeite-err-id-reference.md")
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
             (str "* " "[" (name a) "](" "how-to/" (name a) ".md" ")" "\n"))
           "\n\n"])
        (when-let [tutorial-refs (:tutorial-ref op)]
          ["#### Tutorials:\n\n"
           (for [a (sort tutorial-refs)]
             (str "* " "[" (name a) "](" "tutorial/" (name a) ".md" ")" "\n"))
           "\n\n"])
        (when-let [explanation-refs (:explanation-ref op)]
          ["#### Explanations:\n\n"
           (for [a (sort explanation-refs)]
             (str "* " "[" (name a) "](" "explanation/" (name a) ".md" ")" "\n"))
           "\n\n"])
        (when-let [tags (:tags op)]
          ["#### Tags:" "\n\n"
           (string/join ", "
                        (for [a (sort tags)]
                          (let [a (name a)]
                            (str " [" (:label (tag-def-map (keyword a))) "]("
                                 (tag-md-filename lang a)
                                 ")"))))
           "\n\n"])
        "---\n"]))

(defn full-md-all [{:keys [lang tag-md-filename tag-def-map] :as info} op-maps]
  (->> [utils/generated-msg
        "# "
        (utils/lang-str lang)
        " operator reference (all operators)\n\n"
        (full-intro lang)
        (->> op-maps
             (map (fn [[op-name op]]
                    (full-md info op-name op))))]
       flatten
       (apply str)))
