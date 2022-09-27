;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.doc.md-basic
  (:require [clojure.string :as string]
            [jibe.halite.doc.utils :as utils]))

(defn tags-md-block
  "Return markdown string with links to all the tags given as keywords"
  [{:keys [lang tag-def-map tag-md-filename]} tags]
  (string/join ", "
               (for [a (sort tags)]
                 (str " [" (:label (tag-def-map a)) "]("
                      (tag-md-filename lang (name a))
                      ")"))))

(defn basic-md [{:keys [lang] :as info} op-name op]
  (let [bnf (if (= :halite lang)
              (:bnf op)
              (if (contains? (set (keys op)) :bnf-j)
                (:bnf-j op)
                (or (:bnf-j op)
                    (:bnf op))))]
    (when bnf
      (->> ["### "
            "<a name=\"" (utils/safe-op-anchor op-name) "\"></a>"
            op-name "\n\n" (if (= :halite lang) (:doc op) (or (:doc-j op) (:doc op))) "\n\n"
            (when-let [d2 (:doc-2 op)] [d2 "\n\n"])
            ["![" (pr-str bnf) "](./halite-bnf-diagrams/basic-syntax/"
             (utils/url-encode (utils/safe-op-name op-name)) (when (= :jadeite lang) "-j") ".svg)\n\n"]

            (let [c-1 (if (= :halite lang) (:comment op) (or (:comment-j op) (:comment op)))
                  c-2 (if (= :halite lang) (:comment-2 op) (or (:comment-2-j op) (:comment-2 op)))
                  c-3 (if (= :halite lang) (:comment-3 op) (or (:comment-3-j op) (:comment-3 op)))]
              (when (or c-1 c-2 c-3) [(string/join " " [c-1 c-2 c-3]) "\n\n"]))
            (when-let [es (:examples op)]
              ["<table>"
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
            (when-let [tags (seq (or (when (= :jadeite lang)
                                       (:tags-j op))
                                     (:tags op)))]
              ["#### Tags:\n\n" (tags-md-block info (map keyword tags)) "\n\n"])
            (when-let [how-to-refs (:how-to-ref op)]
              ["#### How tos:\n\n"
               (for [a (sort how-to-refs)]
                 (str "* " "[" (name a) "](" "../how-to/" (name a) ".md" ")" "\n"))
               "\n\n"])
            (when-let [tutorial-refs (:tutorial-ref op)]
              ["#### Tutorials:\n\n"
               (for [a (sort tutorial-refs)]
                 (str "* " "[" (name a) "](" "../tutorial/" (name a) ".md" ")" "\n"))
               "\n\n"])
            (when-let [explanation-refs (:explanation-ref op)]
              ["#### Explanations:\n\n"
               (for [a (sort explanation-refs)]
                 (str "* " "[" (name a) "](" "../explanation/" (name a) ".md" ")" "\n"))
               "\n\n"])

            "---\n"]
           flatten (apply str)))))

(defn produce-basic-core-md [{:keys [lang] :as info} basic-bnf]
  (str utils/generated-msg
       "# "
       (utils/lang-str lang)
       " basic syntax and types reference\n\n"
       (->> basic-bnf
            (partition 2)
            (map (partial apply basic-md info))
            (apply str))
       "### Type Graph"
       "![" "type graph" "](./types.dot.png)\n\n"))
