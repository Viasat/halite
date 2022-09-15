;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.halite-docs
  (:require [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [jibe.halite-guide :as halite-guide]
            [jibe.halite.doc.basic-bnf :refer [basic-bnf-vector]]
            [jibe.halite.doc.err-maps :as err-maps]
            [jibe.halite.doc.how-tos :refer [how-tos]]
            [jibe.halite.doc.op-maps :as op-maps]
            [jibe.halite.doc.tag-def-map :refer [tag-def-map]]
            [jibe.lib.fixed-decimal :as fixed-decimal]
            [jibe.lib.format-errors :as format-errors]
            [jibe.logic.jadeite :as jadeite])
  (:import [jibe.halite_guide HCInfo]
           [net.nextencia.rrdiagram.grammar.model GrammarToRRDiagram BNFToGrammar]
           [net.nextencia.rrdiagram.grammar.rrdiagram RRDiagramToSVG]))

(set! *warn-on-reflection* true)

;; TODO:
;; define jadeite operator precedence
;; specify use of parens and {} in jadeite

(comment
  ;; an example of evaluating a halite form in the context of a spec-map
  (let [r (halite-guide/hc-body {:spec/A {:spec-vars {:x "Integer"}
                                          :constraints [["c" '(> x 12)]]
                                          :refines-to {:spec/B {:name "r"
                                                                :expr '{:$type :spec/B
                                                                        :a (inc x)}}}}
                                 :spec/B {:abstract? true
                                          :spec-vars {:a "Integer"}
                                          :constraints []
                                          :refines-to {}}}
                                '(refine-to {:$type :spec/A
                                             :x 100} :spec/B))]
    (.-h-result r)))

(defn spit-dir [filename txt]
  (io/make-parents filename)
  (spit filename txt))

(defn expand-example [[op m]]
  [op (if (:examples m)
        (assoc m :examples (mapv (fn [example]
                                   (let [{:keys [expr-str expr-str-j result result-j workspace-f instance spec-map]} example]
                                     (if expr-str
                                       (let [{:keys [h-result j-result j-expr]}
                                             (if spec-map
                                               (let [h-expr (edn/read-string
                                                             {:readers {'d fixed-decimal/fixed-decimal-reader}}
                                                             expr-str)
                                                     ^HCInfo i (halite-guide/hc-body
                                                                spec-map
                                                                h-expr)]
                                                 {:h-result (.-h-result i)
                                                  :j-result (.-j-result i)
                                                  :j-expr (jadeite/to-jadeite h-expr)})
                                               (if workspace-f
                                                 (let [workspace (workspace-f expr-str)
                                                       ^HCInfo i (halite-guide/hc-body
                                                                  [workspace]
                                                                  :my
                                                                  (list 'get
                                                                        (list 'refine-to instance :my/Result$v1)
                                                                        :x))]
                                                   {:h-result (.-h-result i)
                                                    :j-result (.-j-result i)
                                                    :j-expr (jadeite/to-jadeite (edn/read-string
                                                                                 {:readers {'d fixed-decimal/fixed-decimal-reader}}
                                                                                 expr-str))})
                                                 (let [i (halite-guide/h*
                                                          (edn/read-string
                                                           {:readers {'d fixed-decimal/fixed-decimal-reader}}
                                                           expr-str)
                                                          true)]
                                                   {:h-result (.-h-result i)
                                                    :j-result (.-j-result i)
                                                    :j-expr (.-j-expr i)})))

                                             err-result? (and (vector? h-result)
                                                              (= :throws (first h-result)))
                                             to-merge (apply merge [(when (= expr-str-j :auto)
                                                                      {:expr-str-j j-expr})
                                                                    (when (= result :auto)
                                                                      (if err-result?
                                                                        {:err-result (str (namespace (get h-result 2))
                                                                                          "/"
                                                                                          (name (get h-result 2)))}
                                                                        {:result (pr-str h-result)}))
                                                                    (when (or (= result-j :auto)
                                                                              (and expr-str-j
                                                                                   (not (contains? example :result-j))))
                                                                      (if err-result?
                                                                        {:err-result-j (str (namespace (get h-result 2))
                                                                                            "/"
                                                                                            (name (get h-result 2)))}
                                                                        {:result-j j-result}))])
                                             base-example (if (contains? to-merge :err-result)
                                                            (dissoc example :result)
                                                            example)
                                             base-example (if (contains? to-merge :err-result-j)
                                                            (dissoc base-example :result-j)
                                                            base-example)]
                                         (merge base-example to-merge))
                                       example)))
                                 (:examples m)))
        m)])

(defn expand-examples-map [op-maps]
  (->> op-maps
       (mapcat expand-example)
       (apply sorted-map)))

(defn expand-examples-vector [basic-bnf]
  (->> basic-bnf
       (partition 2)
       (mapcat expand-example)
       vec))

;;;;

(def misc-notes ["'whitespace' refers to characters such as spaces, tabs, and newlines."
                 "Whitespace is generally not called out in the following diagrams. However, it is specified for a few syntactic constructs that explicitly rely on whitespace."])

(def misc-notes-halite ["For halite, whitespace also includes the comma. The comma can be used as an optional delimiter in sequences to improve readability."])

(def basic-bnf (expand-examples-vector basic-bnf-vector))

(def op-maps (expand-examples-map op-maps/op-maps))

(def jadeite-ommitted-ops #{'dec 'inc})

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

(def thrown-by-map (->> (-> op-maps
                            (update-vals :throws))
                        (remove (comp nil? second))
                        (mapcat (fn [[k vs]] (map (fn [v] {v #{k}}) vs)))
                        (apply merge-with into)))

(def thrown-by-basic-map (->> basic-bnf
                              (partition 2)
                              (map (fn [[k v]]
                                     [k (:throws v)]))
                              (remove (comp nil? second))
                              (mapcat (fn [[k vs]] (map (fn [v] {v #{k}}) vs)))
                              (apply merge-with into)))

(defn translate-op-name-to-jadeite [op-name]
  (if-let [op-names-j (get jadeite-operator-map op-name)]
    (first op-names-j)
    op-name))

(defn translate-op-name-to-jadeite-plural [op-name]
  (if-let [op-names-j (get jadeite-operator-map op-name)]
    op-names-j
    [op-name]))

(def err-maps
  (apply hash-map
         (mapcat (fn [[err-id err]]
                   [err-id (let [err (if-let [thrown-by (thrown-by-map err-id)]
                                       (assoc err
                                              :thrown-by (vec thrown-by)
                                              :thrown-by-j (vec (mapcat translate-op-name-to-jadeite-plural
                                                                        (remove jadeite-ommitted-ops thrown-by))))
                                       err)
                                 err (if-let [thrown-by (thrown-by-basic-map err-id)]
                                       (assoc err
                                              :thrown-by-basic (vec thrown-by))
                                       err)]
                             err)])
                 (filter (fn [[err-id err]]
                           (#{'jibe.halite.l-err 'jibe.h-err} (:ns-name err)))
                         (merge-with merge
                                     @format-errors/error-atom
                                     err-maps/err-maps)))))

(defn translate-op-maps-to-jadeite [op-maps]
  (let [op-maps (-> op-maps
                    (update-vals (fn [op]
                                   (if (:see-also op)
                                     (update-in op [:see-also] #(->> %
                                                                     (mapcat translate-op-name-to-jadeite-plural)
                                                                     sort
                                                                     vec))
                                     op))))]
    (->> op-maps
         (mapcat (fn [[k v]]
                   (->> (translate-op-name-to-jadeite-plural k)
                        (mapcat (fn [k']
                                  [k' (cond
                                        (#{'== '!=} k') (update-in v [:sigs-j] (comp vector second))
                                        (#{'equalTo 'notEqualTo} k') (update-in v [:sigs-j] (comp vector first))
                                        :default v)])))))
         (apply sorted-map))))

(def op-maps-j (translate-op-maps-to-jadeite op-maps))

(def tag-map (->> (-> op-maps
                      (update-vals :tags))
                  (remove (comp nil? second))
                  (mapcat (fn [[k vs]] (map (fn [v] {v #{k}}) vs)))
                  (apply merge-with into)))

(def tag-map-j (->> (-> op-maps-j
                        (update-vals :tags))
                    (remove (comp nil? second))
                    (mapcat (fn [[k vs]] (map (fn [v] {v #{k}}) vs)))
                    (apply merge-with into)))

(defn adjust-connector-style
  "RRDiagramToSVG provides an interface for setting on connector path strokes
  only their color, nothing else. So to adjust width, we have to apply unstable
  private knowledge."
  [svg-str]
  (let [desired-style "fill: none; stroke: #888888; stroke-width: 2px;"]
    (-> svg-str
        (string/replace #"(<style.*?>.*[.]c[{]).*?([}].*</style>)"
                        (str "$1" desired-style "$2"))
        (string/replace #"(d=\"M0 )(\d+)"
                        (fn [[_ prefix y-offset-str]]
                          (str prefix (+ -4 (Long/parseLong y-offset-str))))))))

(defn produce-diagram [out-file-name ^String rule-str]
  (let [gtrd (GrammarToRRDiagram.)
        rts (RRDiagramToSVG.)
        rule-svg (->> rule-str
                      (.convert (BNFToGrammar.))
                      .getRules
                      (into [])
                      (map #(.convert gtrd %))
                      (map #(.convert rts %))
                      first
                      adjust-connector-style)]
    (spit-dir out-file-name rule-svg)))

(defn- rule-from-partitioned-bnf [partitioned-bnf k-f]
  (str "RULE = "
       "("
       (->> partitioned-bnf
            (map (fn [[n m]]
                   (let [bnf (k-f m)]
                     (when bnf
                       (str "("
                            "'" n ":' " "(" bnf ")"
                            ")")))))
            (remove nil?)
            (string/join " |\n"))
       ")"
       ";"))

(defn produce-basic-bnf-diagrams [all-file-name all-file-name-j basic-bnf]
  (produce-diagram (str "doc/halite-bnf-diagrams/basic-syntax/" all-file-name)
                   (rule-from-partitioned-bnf (partition 2 basic-bnf) :bnf))
  (produce-diagram (str "doc/halite-bnf-diagrams/basic-syntax/" all-file-name-j)
                   (rule-from-partitioned-bnf (partition 2 basic-bnf) (fn [bnf-map] (get bnf-map :bnf-j (:bnf bnf-map)))))

  (->> (partition 2 basic-bnf)
       (map (fn [[n {:keys [bnf]}]]
              (when bnf
                (produce-diagram (str "doc/halite-bnf-diagrams/basic-syntax/" n ".svg")
                                 (str "RULE = " "(" bnf ")" ";")))))
       dorun)

  (->> (partition 2 basic-bnf)
       (map (fn [[n bnf-map]]
              (let [bnf-j (get bnf-map :bnf-j (:bnf bnf-map))]
                (when bnf-j
                  (produce-diagram (str "doc/halite-bnf-diagrams/basic-syntax/" (translate-op-name-to-jadeite n) "-j" ".svg")
                                   (str "RULE = " "(" bnf-j ")" ";"))))))
       dorun))

#_(defn produce-basic-bnf-diagrams-for-tag [basic-bnf tag]
    (let [filtered-partitioned-bnf (->> (partition 2 basic-bnf)
                                        (filter (fn [[k v]]
                                                  (let [{:keys [tags]} v]
                                                    (when tags
                                                      (tags tag))))))]
      (produce-diagram (str "doc/halite-bnf-diagrams/basic-syntax/" (name tag) ".svg")
                       (rule-from-partitioned-bnf filtered-partitioned-bnf :bnf))
      (produce-diagram (str "doc/halite-bnf-diagrams/basic-syntax/" (str (name tag) "-j" ".svg"))
                       (rule-from-partitioned-bnf filtered-partitioned-bnf (fn [bnf-map] (get bnf-map :bnf-j (:bnf bnf-map)))))))

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

(defn produce-bnf-diagrams [op-maps op-maps-j all-filename all-filename-j]
  (let [op-keys (keys op-maps)
        rules-strs (->> op-maps
                        (mapcat (fn [[op {:keys [sigs]}]]
                                  (->> sigs
                                       (map (fn [i [args-bnf result-bnf]]
                                              {:op-name op
                                               :sig-index i
                                               :rule-str (str (string/replace op "=" "equal") i " = "
                                                              (when-not (string/starts-with? op "$") "'('")
                                                              "'" op "' " args-bnf
                                                              (when-not (string/starts-with? op "$") "')'")
                                                              " '»' " result-bnf ";")})
                                            (range))))))
        rules-strs-j (->> op-maps-j
                          (mapcat (fn [[op {:keys [sigs-j]}]]
                                    (->> sigs-j
                                         (map (fn [i [args-bnf result-bnf]]
                                                {:op-name op
                                                 :sig-index i
                                                 :rule-str (str (string/replace op "=" "equal") i " = "
                                                                (str "( " args-bnf
                                                                     " '»' " result-bnf " )"))})
                                              (range))))))
        single-rules-strs (->> op-maps
                               (map (fn [[op {:keys [sigs]}]]
                                      (str "("
                                           (when-not (string/starts-with? op "$") "'('")
                                           "'" op "' "
                                           " ( "
                                           (->> sigs
                                                (map (fn [[args-bnf result-bnf]]
                                                       (str "( " args-bnf
                                                            (when-not (string/starts-with? op "$") "')'")
                                                            " '»' " result-bnf " )")))
                                                (string/join " |\n"))
                                           " ) "

                                           ")"))))
        single-rules-strs-j (->> op-maps-j
                                 (map (fn [[op {:keys [sigs-j]}]]
                                        [op (str " ( "
                                                 (->> sigs-j
                                                      (map (fn [[args-bnf result-bnf]]
                                                             (str "( " args-bnf
                                                                  " '»' " result-bnf " )")))
                                                      (string/join " |\n"))
                                                 " ) ")]))
                                 (remove nil?))]
    (let [rule-str (str "RULE = "
                        "("
                        (->> single-rules-strs
                             sort
                             (string/join " |\n"))
                        ")"
                        ";")]
      (produce-diagram (str "doc/halite-bnf-diagrams/" all-filename) rule-str))
    (let [rule-str (str "RULE = "
                        "("
                        (->> single-rules-strs-j
                             (sort-by first)
                             (map second)
                             (string/join " |\n"))
                        ")"
                        ";")]
      (produce-diagram (str "doc/halite-bnf-diagrams/" all-filename-j) rule-str))
    (->> rules-strs
         (map (fn [{:keys [op-name sig-index ^String rule-str]}]
                (produce-diagram (str "doc/halite-bnf-diagrams/op/" (str (safe-op-name op-name) "-" sig-index ".svg")) rule-str)))
         dorun)
    (->> rules-strs-j
         (map (fn [{:keys [op-name sig-index ^String rule-str]}]
                (produce-diagram (str "doc/halite-bnf-diagrams/op/" (str (safe-op-name op-name) "-" sig-index "-j" ".svg")) rule-str)))
         dorun)))

(def safe-char-map
  (let [weird "*!$?=<>_+."
        norml "SBDQELGUAP"]
    (zipmap weird (map #(str "_" %) norml))))

(defn safe-op-anchor [s]
  (apply str (map #(safe-char-map % %) (str s))))

(defn url-encode [s]
  (java.net.URLEncoder/encode (str s)))

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

(defn spec-map-str [lang spec-map]
  ({:halite (with-out-str (pprint/pprint spec-map))
    :jadeite (str (json/encode spec-map {:pretty true}) "\n")} lang))

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
         expr
         (when result ({:halite  "\n\n;-- result --\n"
                        :jadeite "\n\n//-- result --\n"}
                       lang))
         (when result result))))

(defn tag-md-filename [lang tag]
  (str "halite-" tag "-reference" (when (= :jadeite lang) "-j") ".md"))

(assert (= (set (keys tag-def-map))
           (set (concat (map keyword (mapcat :tags (take-nth 2 (next basic-bnf))))
                        (mapcat :tags (vals op-maps)))))
        "Mismatch between defined tags and used tags.")

(defn full-md [lang op-name op]
  (->> ["### "
        "<a name=\"" (safe-op-anchor op-name) "\"></a>"
        op-name "\n\n" (if (= :halite lang) (:doc op) (or (:doc-j op) (:doc op))) "\n\n"
        (when-let [d2 (:doc-2 op)] [d2 "\n\n"])
        (map-indexed
         (fn [i sig]
           ["![" (pr-str sig) "](./halite-bnf-diagrams/op/"
            (url-encode (safe-op-name op-name)) "-" i (when (= :jadeite lang) "-j") ".svg)\n\n"])
         (op ({:halite :sigs, :jadeite :sigs-j} lang)))
        (when-let [basic-refs (some-> (if (= :halite lang)
                                        (:basic-ref op)
                                        (or (:basic-ref-j op)
                                            (:basic-ref op)))
                                      sort)]
          ["#### Basic elements:\n\n"
           (string/join ", "
                        (for [basic-ref basic-refs]
                          (str "[`" basic-ref "`]"
                               "("
                               (if (= :halite lang)
                                 "halite-basic-syntax-reference.md"
                                 "jadeite-basic-syntax-reference.md")
                               "#" basic-ref
                               ")")))
           "\n\n"])
        (when-let [c (:comment op)] [c "\n\n"])
        (when-let [es (:examples op)]
          ["#### Examples:\n\n"
           "<table>"
           (for [row (text-tile-rows (map (partial example-text lang) es))]
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
                  "#" (safe-op-anchor msg) ")" "\n"))
           "\n"])
        (when-let [alsos (:see-also op)]
          ["See also:"
           (for [a (sort (remove #(= op-name %) alsos))]
             [" [`" a "`](#" (safe-op-anchor a) ")"])
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
        "---\n"]
       flatten (apply str)))

(def generated-msg
  "<!---
  This markdown file was generated. Do not edit.
  -->\n\n")

(defn full-intro [lang]
  ["## All Operators\n\n"
   "The syntax for all of the operators is summarized in the following \n\n"
   "![" "all operators" "](./halite-bnf-diagrams/" (if (= :halite lang) "halite" "jadeite") ".svg)\n\n"
   "## Operators\n\n"])

(defn produce-full-md []
  (->> op-maps
       sort
       (map (partial apply full-md :halite))
       (apply str generated-msg
              (apply str "# Halite operator reference (all operators)\n\n"
                     (full-intro :halite)))
       (spit-dir "doc/halite-full-reference.md"))
  (->> op-maps-j
       sort
       (remove (fn [[k _]]
                 (jadeite-ommitted-ops k)))
       (map (partial apply full-md :jadeite))
       (apply str generated-msg
              (apply str "# Jadeite operator reference (all operators)\n\n"
                     (full-intro :jadeite)))
       (spit-dir "doc/jadeite-full-reference.md")))

(defn tags-md-block
  "Return markdown string with links to all the tags given as keywords"
  [lang tags]
  (string/join ", "
               (for [a (sort tags)]
                 (str " [" (:label (tag-def-map a)) "]("
                      (tag-md-filename lang (name a))
                      ")"))))

(defn basic-md [lang op-name op]
  (let [bnf (if (= :halite lang)
              (:bnf op)
              (if (contains? (set (keys op)) :bnf-j)
                (:bnf-j op)
                (or (:bnf-j op)
                    (:bnf op))))]
    (when bnf
      (->> ["### "
            "<a name=\"" (safe-op-anchor op-name) "\"></a>"
            op-name "\n\n" (if (= :halite lang) (:doc op) (or (:doc-j op) (:doc op))) "\n\n"
            (when-let [d2 (:doc-2 op)] [d2 "\n\n"])
            ["![" (pr-str bnf) "](./halite-bnf-diagrams/basic-syntax/"
             (url-encode (safe-op-name op-name)) (when (= :jadeite lang) "-j") ".svg)\n\n"]

            (let [c-1 (if (= :halite lang) (:comment op) (or (:comment-j op) (:comment op)))
                  c-2 (if (= :halite lang) (:comment-2 op) (or (:comment-2-j op) (:comment-2 op)))
                  c-3 (if (= :halite lang) (:comment-3 op) (or (:comment-3-j op) (:comment-3 op)))]
              (when (or c-1 c-2 c-3) [(string/join " " [c-1 c-2 c-3]) "\n\n"]))
            (when-let [es (:examples op)]
              ["<table>"
               (for [row (text-tile-rows (map (partial example-text lang) es))]
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
                      "#" (safe-op-anchor msg) ")" "\n"))
               "\n"])
            (when-let [tags (seq (or (when (= :jadeite lang)
                                       (:tags-j op))
                                     (:tags op)))]
              ["#### Tags:\n\n" (tags-md-block lang (map keyword tags)) "\n\n"])
            "---\n"]
           flatten (apply str)))))

(defn produce-basic-core-md [lang]
  (str (->> basic-bnf
            (partition 2)
            (map (partial apply basic-md lang))
            (apply str))
       "### Type Graph"
       "![" "type graph" "](./types.dot.png)\n\n"))

(defn produce-basic-md []
  (->> (produce-basic-core-md :halite)
       (str generated-msg "# Halite basic syntax reference\n\n")
       (spit-dir "doc/halite-basic-syntax-reference.md"))
  (->> (produce-basic-core-md :jadeite)
       (str generated-msg "# Jadeite basic syntax reference\n\n")
       (spit-dir "doc/jadeite-basic-syntax-reference.md")))

(defn err-md [lang err-id err]
  (->> ["### "
        "<a name=\"" (safe-op-anchor err-id) "\"></a>"
        err-id "\n\n" (:doc err) "\n\n"
        "#### Error message template:" "\n\n"
        "> " (:message err)
        "\n\n"
        (when-let [thrown-bys (:thrown-by-basic err)]
          ["#### Produced by elements:\n\n"
           (for [a (sort thrown-bys)]
             (str "* " "[`" a "`](" (if (= :halite lang)
                                      "halite-basic-syntax-reference.md"
                                      "jadeite-basic-syntax-reference.md")
                  "#" (safe-op-anchor a) ")" "\n"))
           "\n"])
        (when-let [thrown-bys (if (= :halite lang)
                                (:thrown-by err)
                                (:thrown-by-j err))]
          ["#### Produced by operators:\n\n"
           (for [a (sort thrown-bys)]
             (str "* " "[`" a "`](" (if (= :halite lang)
                                      "halite-full-reference.md"
                                      "jadeite-full-reference.md")
                  "#" (safe-op-anchor a) ")" "\n"))
           "\n"])
        (when-let [alsos (:see-also err)]
          ["See also:"
           (for [a (sort alsos)]
             [" [`" a "`](#" (safe-op-anchor a) ")"])
           "\n\n"])
        "---\n"]
       flatten (apply str)))

(defn produce-err-md []
  (->> err-maps
       sort
       (map (partial apply err-md :halite))
       (apply str generated-msg "# Halite err-id reference\n\n")
       (spit-dir "doc/halite-err-id-reference.md"))
  (->> err-maps
       sort
       (map (partial apply err-md :jadeite))
       (apply str generated-msg "# Jadeite err-id reference\n\n")
       (spit-dir "doc/jadeite-err-id-reference.md")))

(defn tag-md [lang tag-name tag]
  (->> [(:doc tag) "\n\n"
        (when-let [basic-ref (if (= :halite lang)
                               (:basic-ref tag)
                               (or (:basic-ref-j tag)
                                   (:basic-ref tag)))]
          ["For basic syntax of this data type see: [`" basic-ref "`]" "("
           (if (= :halite lang)
             "halite-basic-syntax-reference.md"
             "jadeite-basic-syntax-reference.md")
           "#" basic-ref
           ")" "\n\n"])
        ["![" (pr-str tag-name) "](./halite-bnf-diagrams/"
         (url-encode tag-name) (when (= :jadeite lang) "-j") ".svg)\n\n"]
        [(when-let [op-names ((if (= :halite lang) tag-map tag-map-j) (keyword tag-name))]
           (->> op-names
                (map (fn [op-name]
                       (let [op ((if (= :halite lang) op-maps op-maps-j) op-name)]
                         {:op-name op-name
                          :md (str "#### [`" op-name "`](" (if (= :halite lang)
                                                             "halite-full-reference.md"
                                                             "jadeite-full-reference.md")
                                   "#" (safe-op-anchor op-name) ")" "\n\n"
                                   (if (= :halite lang) (:doc op) (or (:doc-j op) (:doc op)))
                                   "\n\n")})))
                (sort-by :op-name)
                (map :md)))]
        "---\n"]
       flatten (apply str)))

(defn produce-tag-md [lang [tag-name tag]]
  (let [tag-name (name tag-name)]
    (->> (tag-md lang tag-name tag)
         (str generated-msg "# " (if (= :halite lang) "Halite" "Jadeite")
              " reference: "
              (:label tag)
              "\n\n")
         (spit-dir (str "doc/" (tag-md-filename lang tag-name))))))

(defn code-snippet [lang code]
  (str "```"
       ({:halite "clojure", :jadeite "java"} lang) "\n"
       code
       "```\n\n"))

(defn how-to-filename [lang id]
  (str "how-to/" (str (name id) (when (= :jadeite lang) "-j") ".md")))

(defn how-to-md [lang [id how-to]]
  (->> ["## " (:label how-to) "\n\n"
        (:desc how-to) "\n\n"
        (loop [[c & more-c] (:contents how-to)
               spec-map nil
               results []]
          (if c
            (cond
              (string? c) (recur more-c spec-map (conj results (str c "\n\n")))

              (and (map c) (:spec-map c)) (recur more-c (:spec-map c) (conj results (code-snippet lang (spec-map-str lang (:spec-map c)))))
              (and (map c) (:code c)) (let [h-expr (:code c)
                                            ^HCInfo i (halite-guide/hc-body
                                                       spec-map
                                                       h-expr)
                                            {:keys [h-result j-result j-expr]} {:h-result (.-h-result i)
                                                                                :j-result (.-j-result i)
                                                                                :j-expr (jadeite/to-jadeite h-expr)}]
                                        (when (and (not (:throws c))
                                                   (vector? h-result)
                                                   (= :throws (first h-result)))
                                          (throw (ex-info "failed" {:h-expr h-expr
                                                                    :h-result h-result})))
                                        (recur more-c spec-map
                                               (conj results (code-snippet lang (str ({:halite h-expr
                                                                                       :jadeite j-expr} lang)
                                                                                     (when (or (:result c)
                                                                                               (:throws c))
                                                                                       (str "\n"
                                                                                            ({:halite  "\n\n;-- result --\n"
                                                                                              :jadeite "\n\n//-- result --\n"}
                                                                                             lang)
                                                                                            ({:halite h-result
                                                                                              :jadeite j-result} lang)))
                                                                                     "\n"))))))
            results))
        (when-let [basic-refs (some-> (if (= :halite lang)
                                        (:basic-ref how-to)
                                        (or (:basic-ref-j how-to)
                                            (:basic-ref how-to)))
                                      sort)]
          ["#### Basic elements:\n\n"
           (string/join ", "
                        (for [basic-ref basic-refs]
                          (str "[`" basic-ref "`]"
                               "("
                               (if (= :halite lang)
                                 "../halite-basic-syntax-reference.md"
                                 "../jadeite-basic-syntax-reference.md")
                               "#" basic-ref
                               ")")))
           "\n\n"])

        (when-let [op-refs (some->> (:op-ref how-to)
                                    (map ({:halite identity
                                           :jadeite translate-op-name-to-jadeite} lang)))]
          ["#### Operator reference:\n\n"
           (for [a (sort op-refs)]
             (str "* " "[`" a "`](" (if (= :halite lang)
                                      "../halite-full-reference.md"
                                      "../jadeite-full-reference.md")
                  "#" (safe-op-anchor a) ")" "\n"))
           "\n\n"])
        (when-let [see-alsos (:see-also how-to)]
          ["#### See also:\n\n"
           (for [a (sort see-alsos)]
             (str "* " "[" (name a) "](" (name a) ".md" ")" "\n"))
           "\n\n"])]
       flatten
       (apply str)
       (spit-dir (str "doc/" (how-to-filename lang id)))))

(defn produce-outline []
  (->>
   ["# Halite resource specifications\n
All features are available in both Halite (s-expression) syntax and Jadeite (C-like) syntax.\n\n"

    "## Tutorials\n\nTBD\n\n"

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

    "## Explanation\n\nTBD\n\n"

    "## Reference\n

* Basic Syntax [(Halite)](halite-basic-syntax-reference.md)         [(Jadeite)](jadeite-basic-syntax-reference.md)
* All Operators (alphabetical) [(Halite)](halite-full-reference.md) [(Jadeite)](jadeite-full-reference.md)
* Error ID Reference [(Halite)](halite-err-id-reference.md)         [(Jadeite)](jadeite-err-id-reference.md)

#### Operators grouped by tag:\n\n"

    (let [separate-tags ['control-flow 'special-form]
          cols (->> tag-def-map vals (map :type-mode) set sort
                    (remove (set separate-tags)))]
      [(->> separate-tags
            (map (fn [tag]
                   ["* " (get-in tag-def-map [(keyword tag) :label])
                    " [(Halite)]("  (tag-md-filename :halite  tag) ")"
                    " [(Jadeite)](" (tag-md-filename :jadeite tag) ")\n"])))
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
                                             [" [H](" (tag-md-filename :halite  (name k)) ")"
                                              " [J](" (tag-md-filename :jadeite (name k)) ")\n"])))
                                 "</td>"])))
                    "</tr>"])))
       "</table>\n\n"])]
   flatten
   (apply str)
   (spit-dir (str "doc/outline.md"))))

;;

(defn query-ops
  [tag]
  (apply sorted-map (mapcat identity (filter (fn [[op m]]
                                               (get (:tags m) tag))
                                             op-maps))))

(defn produce-bnf-diagram-for-tag [tag]
  (produce-bnf-diagrams
   (query-ops tag)
   (translate-op-maps-to-jadeite (query-ops tag))
   (str (name tag) ".svg")
   (str (name tag) "-j" ".svg")))

(comment
  (def po
    (future
      (while true
        (let [f produce-outline]
          (Thread/sleep 500)
          (when-not (= f produce-outline)
            (println "Generating md" (java.util.Date.))
            (produce-outline))))))
  (future-cancel po)

  (do
    (produce-basic-bnf-diagrams "basic-all.svg" "basic-all-j.svg" basic-bnf)

    (produce-bnf-diagrams op-maps op-maps-j "halite.svg" "jadeite.svg")

    (->> (keys tag-def-map)
         (map produce-bnf-diagram-for-tag)
         dorun)
    (->> tag-def-map
         (map (partial produce-tag-md :halite))
         dorun)
    (->> tag-def-map
         (map (partial produce-tag-md :jadeite))
         dorun)

    (produce-basic-md)
    (produce-full-md)
    (produce-err-md)

    (->> how-tos
         (map (partial how-to-md :halite))
         dorun)
    (->> how-tos
         (map (partial how-to-md :jadeite))
         dorun)

    (produce-outline)))
