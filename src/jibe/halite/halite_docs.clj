;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.halite-docs
  (:require [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [jibe.halite-guide :as halite-guide]
            [jibe.halite.doc.bnf-diagrams :as bnf-diagrams]
            [jibe.halite.doc.basic-bnf :refer [basic-bnf-vector]]
            [jibe.halite.doc.err-maps :as err-maps]
            [jibe.halite.doc.how-tos :refer [how-tos]]
            [jibe.halite.doc.md-how-to :as md-how-to]
            [jibe.halite.doc.md-outline :refer [produce-outline]]
            [jibe.halite.doc.md-tag :as md-tag]
            [jibe.halite.doc.op-maps :as op-maps]
            [jibe.halite.doc.tag-def-map :refer [tag-def-map]]
            [jibe.halite.doc.utils :as utils]
            [jibe.lib.fixed-decimal :as fixed-decimal]
            [jibe.lib.format-errors :as format-errors]
            [jibe.logic.jadeite :as jadeite])
  (:import [jibe.halite_guide HCInfo]))

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

(def err-maps
  (apply hash-map
         (mapcat (fn [[err-id err]]
                   [err-id (let [err (if-let [thrown-by (thrown-by-map err-id)]
                                       (assoc err
                                              :thrown-by (vec thrown-by)
                                              :thrown-by-j (vec (mapcat utils/translate-op-name-to-jadeite-plural
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
                                                                     (mapcat utils/translate-op-name-to-jadeite-plural)
                                                                     sort
                                                                     vec))
                                     op))))]
    (->> op-maps
         (mapcat (fn [[k v]]
                   (->> (utils/translate-op-name-to-jadeite-plural k)
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
                (utils/spec-map-str lang spec-map)
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
        "<a name=\"" (utils/safe-op-anchor op-name) "\"></a>"
        op-name "\n\n" (if (= :halite lang) (:doc op) (or (:doc-j op) (:doc op))) "\n\n"
        (when-let [d2 (:doc-2 op)] [d2 "\n\n"])
        (map-indexed
         (fn [i sig]
           ["![" (pr-str sig) "](./halite-bnf-diagrams/op/"
            (utils/url-encode (utils/safe-op-name op-name)) "-" i (when (= :jadeite lang) "-j") ".svg)\n\n"])
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
                  "#" (utils/safe-op-anchor msg) ")" "\n"))
           "\n"])
        (when-let [alsos (:see-also op)]
          ["See also:"
           (for [a (sort (remove #(= op-name %) alsos))]
             [" [`" a "`](#" (utils/safe-op-anchor a) ")"])
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

(defn full-intro [lang]
  ["## All Operators\n\n"
   "The syntax for all of the operators is summarized in the following \n\n"
   "![" "all operators" "](./halite-bnf-diagrams/" (if (= :halite lang) "halite" "jadeite") ".svg)\n\n"
   "## Operators\n\n"])

(defn produce-full-md []
  (->> op-maps
       sort
       (map (partial apply full-md :halite))
       (apply str utils/generated-msg
              (apply str "# Halite operator reference (all operators)\n\n"
                     (full-intro :halite)))
       (utils/spit-dir "doc/halite-full-reference.md"))
  (->> op-maps-j
       sort
       (remove (fn [[k _]]
                 (jadeite-ommitted-ops k)))
       (map (partial apply full-md :jadeite))
       (apply str utils/generated-msg
              (apply str "# Jadeite operator reference (all operators)\n\n"
                     (full-intro :jadeite)))
       (utils/spit-dir "doc/jadeite-full-reference.md")))

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
                      "#" (utils/safe-op-anchor msg) ")" "\n"))
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
       (str utils/generated-msg "# Halite basic syntax reference\n\n")
       (utils/spit-dir "doc/halite-basic-syntax-reference.md"))
  (->> (produce-basic-core-md :jadeite)
       (str utils/generated-msg "# Jadeite basic syntax reference\n\n")
       (utils/spit-dir "doc/jadeite-basic-syntax-reference.md")))

(defn err-md [lang err-id err]
  (->> ["### "
        "<a name=\"" (utils/safe-op-anchor err-id) "\"></a>"
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
                  "#" (utils/safe-op-anchor a) ")" "\n"))
           "\n"])
        (when-let [thrown-bys (if (= :halite lang)
                                (:thrown-by err)
                                (:thrown-by-j err))]
          ["#### Produced by operators:\n\n"
           (for [a (sort thrown-bys)]
             (str "* " "[`" a "`](" (if (= :halite lang)
                                      "halite-full-reference.md"
                                      "jadeite-full-reference.md")
                  "#" (utils/safe-op-anchor a) ")" "\n"))
           "\n"])
        (when-let [alsos (:see-also err)]
          ["See also:"
           (for [a (sort alsos)]
             [" [`" a "`](#" (utils/safe-op-anchor a) ")"])
           "\n\n"])
        "---\n"]
       flatten (apply str)))

(defn produce-err-md []
  (->> err-maps
       sort
       (map (partial apply err-md :halite))
       (apply str utils/generated-msg "# Halite err-id reference\n\n")
       (utils/spit-dir "doc/halite-err-id-reference.md"))
  (->> err-maps
       sort
       (map (partial apply err-md :jadeite))
       (apply str utils/generated-msg "# Jadeite err-id reference\n\n")
       (utils/spit-dir "doc/jadeite-err-id-reference.md")))

(defn produce-tag-md [lang [tag-name tag]]
  (->> [tag-name tag]
       (md-tag/produce-tag-md {:lang lang
                               :op-maps op-maps
                               :op-maps-j op-maps-j
                               :tag-map tag-map
                               :tag-map-j tag-map-j})
       (utils/spit-dir (str "doc/" (tag-md-filename lang (name tag-name))))))

(defn how-to-filename [lang id]
  (str "how-to/" (str (name id) (when (= :jadeite lang) "-j") ".md")))

(defn how-to-md [lang [id how-to]]
  (->> (md-how-to/how-to-md lang [id how-to])
       (utils/spit-dir (str "doc/" (how-to-filename lang id)))))

;;

(defn query-ops
  "Returns a subset of op-maps that include the given tag"
  [tag]
  (apply sorted-map (mapcat identity (filter (fn [[op m]]
                                               (get (:tags m) tag))
                                             op-maps))))

(defn produce-bnf-diagram-for-tag [tag]
  (bnf-diagrams/produce-bnf-diagrams
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
    (bnf-diagrams/produce-basic-bnf-diagrams "basic-all.svg" "basic-all-j.svg" basic-bnf)
    (bnf-diagrams/produce-bnf-diagrams op-maps op-maps-j "halite.svg" "jadeite.svg")
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

    (utils/spit-dir (str "doc/outline.md")
                    (produce-outline {:tag-def-map tag-def-map
                                      :how-tos how-tos
                                      :how-to-filename how-to-filename
                                      :tag-md-filename tag-md-filename}))))
