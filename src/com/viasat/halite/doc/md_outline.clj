;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.doc.md-outline
  (:require [com.viasat.halite.doc.utils :as utils]))

(defn produce-outline [{:keys [tutorials how-tos explanations tag-def-map
                               tutorial-filename how-to-filename explanation-filename]}
                       {:keys [prefix generate-hdr-f get-link-f]}]
  (->>
   [(generate-hdr-f "Halite Outline" "halite_outline" nil "Outline of Halite expression lanuage documentation.")
    "# Halite resource specifications\n
All features are available in both Halite (s-expression) syntax and Jadeite (C-like) syntax.\n\n"

    "## Tutorials\n\n"
    (->> tutorials
         (group-by (comp namespace key))
         (mapcat (fn [[namespace tutorials]]
                   (str "### " namespace "\n\n"
                        (->> tutorials
                             (sort-by (comp :label val))
                             (mapcat (fn [[id h]] ["* " (:label h)
                                                   " [(Halite)](" (tutorial-filename :halite id) ")"
                                                   " [(Jadeite)](" (tutorial-filename :jadeite id) ")\n"
                                                   "  * " (:desc h) "\n"]))
                             (apply str))
                        "\n"))))

    "## How-To Guides\n\n"
    (->> how-tos
         (group-by (comp namespace key))
         (mapcat (fn [[namespace how-tos]]
                   (str "### " namespace "\n\n"
                        (->> how-tos
                             (sort-by (comp :label val))
                             (mapcat (fn [[id h]] ["* " (:label h)
                                                   " [(Halite)](" (how-to-filename :halite id) ")"
                                                   " [(Jadeite)](" (how-to-filename :jadeite id) ")\n"
                                                   "  * " (:desc h) "\n"]))
                             (apply str))
                        "\n"))))

    "## Explanation\n\n"
    (->> explanations
         (group-by (comp namespace key))
         (mapcat (fn [[namespace explanations]]
                   (str "### " namespace "\n\n"
                        (->> explanations
                             (sort-by (comp :label val))
                             (mapcat (fn [[id h]] ["* " (:label h)
                                                   " [(Halite)](" (explanation-filename :halite id) ")"
                                                   " [(Jadeite)](" (explanation-filename :jadeite id) ")\n"
                                                   "  * " (:desc h) "\n"]))
                             (apply str))
                        "\n"))))

    "## Reference\n"
    "* Basic Syntax and Types [(Halite)](" (get-link-f :halite prefix "halite/" "basic-syntax-reference") "), [(Jadeite)](" (get-link-f :jadeite prefix "jadeite/" "basic-syntax-reference") ")\n"
    "* Specification Map Syntax [(Halite)](" (get-link-f :halite prefix "" "spec-syntax-reference") ")\n"
    "* All Operators (alphabetical) [(Halite)](" (get-link-f :halite prefix "halite/" "full-reference") "), [(Jadeite)](" (get-link-f :jadeite prefix "jadeite/" "full-reference") ")\n"
    "* Error ID Reference [(Halite)](" (get-link-f :halite prefix "halite/" "err-id-reference") "), [(Jadeite)](" (get-link-f :jadeite prefix "jadeite/" "err-id-reference") ")\n\n"

    "#### Operators grouped by tag:\n\n"

    (let [separate-tags ['control-flow 'special-form]
          cols (->> tag-def-map vals (map :type-mode) set sort
                    (remove (set separate-tags)))]
      [(->> separate-tags
            (map (fn [tag]
                   ["* " (get-in tag-def-map [(keyword tag) :label])
                    " [(Halite)]("  (get-link-f :halite  prefix "halite/" (str tag "-reference")) ")"
                    " [(Jadeite)](" (get-link-f :jadeite  prefix "jadeite/" (str tag "-reference")) ")\n"])))
       "<table>"
       "<tr><th></th>"
       (->> cols (map (fn [tm] ["<th>" tm "</th>\n"])))
       "</tr>"
       (->> tag-def-map vals (map :type) set sort
            (remove nil?)
            (map (fn [t]
                   ["<tr>"
                    "<th>" t "</th>"
                    (->> cols
                         (map (fn [tm]
                                ["<td>\n\n"
                                 (->> tag-def-map
                                      (filter #(= tm (:type-mode (val %))))
                                      (filter #(= t (:type (val %))))
                                      (map (fn [[k v]]
                                             [" <a href=\"" (get-link-f :halite  prefix "halite/" (str (name k) "-reference")) "\">H</a>"
                                              " <a href=\"" (get-link-f :jadeite prefix "jadeite/" (str (name k) "-reference")) "\">J</a>\n"])))
                                 "</td>"])))
                    "</tr>"])))
       "</table>\n\n"])]
   flatten
   (apply str)))
