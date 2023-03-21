;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.doc.md-full
  (:require [clojure.string :as string]
            [com.viasat.halite.doc.md-basic :as md-basic]
            [com.viasat.halite.doc.utils :as utils]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(s/defn full-intro
  [lang
   get-svg-link]
  ["## All Operators\n\n"
   "The syntax for all of the operators is summarized in the following \n\n"
   (md-basic/diagram-description "operations")
   md-basic/label-description
   "In the syntax diagrams the '»' is to be read as 'produces'. The element after this indicator is the type of the value produced by the prior form.\n\n"
   (get-svg-link "halite-bnf-diagrams/" (if (= :halite lang) "halite" "jadeite") "all operators")
   "## Operators\n\n"])

(s/defn full-md
  [{:keys [lang tag-def-map]} {:keys [prefix get-link-f get-svg-link-f get-table-data-f get-reference-links-f]} op-name op]
  (->> ["### "
        "<a name=\"" (utils/safe-op-anchor op-name) "\"></a>"
        op-name "\n\n" (if (= :halite lang) (:doc op) (or (:doc-j op) (:doc op))) "\n\n"
        (when-let [d2 (:doc-2 op)] [d2 "\n\n"])
        (map-indexed
         (fn [i sig]
           (get-svg-link-f "halite-bnf-diagrams/op/" (str (utils/url-encode (utils/safe-op-name op-name)) "-" i (utils/get-language-modifier lang)) sig))
         (op ({:halite :sigs, :jadeite :sigs-j} lang)))
        (when-let [md-links (get-reference-links-f lang prefix "" op)]
          ["#### Basic elements:\n\n" (interpose ", " md-links) "\n\n"])
        (when-let [c (:comment op)] [c "\n\n"])
        (when-let [es (:examples op)]
          ["#### Examples:\n\n"
           "<table>"
           (for [row (utils/text-tile-rows (map (partial utils/example-text lang) es))]
             ["<tr>"
              (for [tile (:tiles row)]
                (get-table-data-f lang tile))
              "</tr>"])
           "</table>\n\n"])
        (when-let [t (:throws op)]
          ["#### Possible errors:\n\n"
           (for [msg (sort t)]
             (str "* " "[`" msg "`]("
                  (get-link-f lang prefix "" "err-id-reference")
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
             (str "* " "[" (name a) "](" (get-link-f lang prefix "how-to/" (name a)) ")" "\n"))
           "\n\n"])
        (when-let [tutorial-refs (:tutorial-ref op)]
          ["#### Tutorials:\n\n"
           (for [a (sort tutorial-refs)]
             (str "* " "[" (name a) "](" (get-link-f lang prefix "tutorial/" (name a)) ")" "\n"))
           "\n\n"])
        (when-let [explanation-refs (:explanation-ref op)]
          ["#### Explanations:\n\n"
           (for [a (sort explanation-refs)]
             (str "* " "[" (name a) "](" (get-link-f lang prefix "explanation/" (name a)) ")" "\n"))
           "\n\n"])
        (when-let [tags (:tags op)]
          ["#### Tags:" "\n\n"
           (string/join ", "
                        (for [a (sort tags)]
                          (let [a (name a)]
                            (str " [" (:label (tag-def-map (keyword a))) "]("
                                 (get-link-f lang prefix "" (str a "-reference"))
                                 ")"))))
           "\n\n"])
        "---\n"]))

(s/defn full-md-all
  [{:keys [lang] :as info} {:keys [generate-hdr-f get-svg-link-f] :as config} op-maps]
  (->> [(generate-hdr-f "Halite Full Reference" (str "halite_full-reference" (utils/get-language-modifier lang)) (str "/" (name lang)) "Halite operator reference (all operators)")
        "# "
        (utils/lang-str lang)
        " operator reference (all operators)\n\n"
        (full-intro lang get-svg-link-f)
        (->> op-maps
             (map (fn [[op-name op]]
                    (full-md info config op-name op))))]
       flatten
       (apply str)))
