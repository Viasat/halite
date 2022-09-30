;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.doc.md-outline
  (:require [jibe.halite.doc.utils :as utils]))

(defn produce-outline [{:keys [tutorials how-tos explanations tag-def-map
                               tutorial-filename how-to-filename explanation-filename tag-reference]}
                       {:keys [mode prefix]}]
  (->>
   [(when (= :user-guide mode)
      (utils/generate-user-guide-hdr "Halite Outline" "halite_outline" nil "Outline of Halite expression lanuage documentation.")) utils/generated-msg
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
                                                   " [(Halite)](" (tutorial-filename :halite mode id) ")"
                                                   " [(Jadeite)](" (tutorial-filename :jadeite mode id) ")\n"
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
                                                   " [(Halite)](" (how-to-filename :halite mode id) ")"
                                                   " [(Jadeite)](" (how-to-filename :jadeite mode id) ")\n"
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
                                                   " [(Halite)](" (explanation-filename :halite mode id) ")"
                                                   " [(Jadeite)](" (explanation-filename :jadeite mode id) ")\n"
                                                   "  * " (:desc h) "\n"]))
                             (apply str))
                        "\n"))))

    "## Reference\n"
    "* Basic Syntax and Types [(Halite)](" (when (= :local mode) "halite/") (utils/get-reference-filename-link :halite mode prefix "basic-syntax") "), [(Jadeite)](" (when (= :local mode) "jadeite/") (utils/get-reference-filename-link :jadeite mode prefix "basic-syntax") ")\n"
    "* Specification Map Syntax [(Halite)](" "halite_spec-syntax-reference" (if (= mode :local)
                                                                              ".md"
                                                                              ".html") ")\n"
    "* All Operators (alphabetical) [(Halite)](" (when (= :local mode) "halite/") (utils/get-reference-filename-link :halite mode prefix "full") "), [(Jadeite)](" (when (= :local mode) "jadeite/") (utils/get-reference-filename-link :jadeite mode prefix "full") ")\n"
    "* Error ID Reference [(Halite)](" (when (= :local mode) "halite/") (utils/get-reference-filename-link :halite mode prefix "err-id") "), [(Jadeite)](" (when (= :local mode) "jadeite/") (utils/get-reference-filename-link :jadeite mode prefix "err-id") ")\n\n"

    "#### Operators grouped by tag:\n\n"

    (let [separate-tags ['control-flow 'special-form]
          cols (->> tag-def-map vals (map :type-mode) set sort
                    (remove (set separate-tags)))]
      [(->> separate-tags
            (map (fn [tag]
                   ["* " (get-in tag-def-map [(keyword tag) :label])
                    " [(Halite)]("  (when (= :local mode) "halite/") (tag-reference :halite  mode prefix tag) ")"
                    " [(Jadeite)](" (when (= :local mode) "jadeite/") (tag-reference :jadeite mode prefix tag) ")\n"])))
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
                                             [" [H](" (when (= :local mode) "halite/") (tag-reference :halite  mode prefix (name k)) ")"
                                              " [J](" (when (= :local mode) "jadeite/") (tag-reference :jadeite mode prefix (name k)) ")\n"])))
                                 "</td>"])))
                    "</tr>"])))
       "</table>\n\n"])]
   flatten
   (apply str)))
