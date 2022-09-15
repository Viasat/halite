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
            [jibe.halite.doc.md-basic :as md-basic]
            [jibe.halite.doc.md-err :as md-err]
            [jibe.halite.doc.md-full :as md-full]
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

(defn tag-md-filename [lang tag]
  (str "halite-" tag "-reference" (when (= :jadeite lang) "-j") ".md"))

(assert (= (set (keys tag-def-map))
           (set (concat (map keyword (mapcat :tags (take-nth 2 (next basic-bnf))))
                        (mapcat :tags (vals op-maps)))))
        "Mismatch between defined tags and used tags.")

(defn produce-full-md []
  (let [info {:tag-def-map tag-def-map
              :tag-md-filename tag-md-filename}]
    (->> op-maps
         sort
         (md-full/full-md-all (assoc info :lang :halite))
         (utils/spit-dir "doc/halite-full-reference.md"))
    (->> op-maps-j
         sort
         (remove (fn [[k _]]
                   (jadeite-ommitted-ops k)))
         (md-full/full-md-all (assoc info :lang :jadeite))
         (utils/spit-dir "doc/jadeite-full-reference.md"))))

(defn produce-basic-md []
  (let [info {:tag-def-map tag-def-map
              :tag-md-filename tag-md-filename}]
    (->> (md-basic/produce-basic-core-md (assoc info :lang :halite) basic-bnf)
         (str utils/generated-msg "# Halite basic syntax reference\n\n")
         (utils/spit-dir "doc/halite-basic-syntax-reference.md"))
    (->> (md-basic/produce-basic-core-md (assoc info :lang :jadeite) basic-bnf)
         (str utils/generated-msg "# Jadeite basic syntax reference\n\n")
         (utils/spit-dir "doc/jadeite-basic-syntax-reference.md"))))

(defn produce-err-md []
  (->> err-maps
       sort
       (map (partial apply md-err/err-md :halite))
       (apply str utils/generated-msg "# Halite err-id reference\n\n")
       (utils/spit-dir "doc/halite-err-id-reference.md"))
  (->> err-maps
       sort
       (map (partial apply md-err/err-md :jadeite))
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
