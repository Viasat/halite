;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite-docs
  (:require [cheshire.core :as json]
            [clojure.pprint :as pprint]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [com.viasat.halite.doc.bnf-diagrams :as bnf-diagrams]
            [com.viasat.halite.doc.data-basic-bnf :refer [basic-bnf-vector]]
            [com.viasat.halite.doc.data-err-maps :as err-maps]
            [com.viasat.halite.doc.data-explanation :refer [explanations]]
            [com.viasat.halite.doc.data-how-tos :refer [how-tos]]
            [com.viasat.halite.doc.data-op-maps :as op-maps]
            [com.viasat.halite.doc.data-spec-bnf :as data-spec-bnf]
            [com.viasat.halite.doc.data-tag-def-map :refer [tag-def-map]]
            [com.viasat.halite.doc.data-tutorial :refer [tutorials]]
            [com.viasat.halite.doc.run :as halite-run]
            [com.viasat.halite.doc.md-basic :as md-basic]
            [com.viasat.halite.doc.md-err :as md-err]
            [com.viasat.halite.doc.md-full :as md-full]
            [com.viasat.halite.doc.md-how-to :as md-how-to]
            [com.viasat.halite.doc.md-outline :refer [produce-outline]]
            [com.viasat.halite.doc.md-spec :as md-spec]
            [com.viasat.halite.doc.md-tag :as md-tag]
            [com.viasat.halite.doc.utils :as utils]
            [com.viasat.halite.lib.fixed-decimal :as fixed-decimal]
            [com.viasat.halite.lib.format-errors :as format-errors]
            [com.viasat.jadeite :as jadeite])
  (:import [com.viasat.halite.doc.run HCInfo]))

(set! *warn-on-reflection* true)

(def ^:dynamic *run-config* nil)

(def ^:dynamic *sidebar-atom* nil)

;; TODO:
;; define jadeite operator precedence
;; specify use of parens and {} in jadeite

(comment
  ;; an example of evaluating a halite form in the context of a spec-map
  (let [r (halite-run/hc-body {:spec/A {:spec-vars {:x "Integer"}
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

(defn- expand-example [[op m]]
  [op (if (:examples m)
        (assoc m :examples (mapv (fn [example]
                                   (let [{:keys [expr-str expr-str-j result result-j spec-map-f instance spec-map]} example]
                                     (if expr-str
                                       (let [{:keys [h-result j-result j-expr]}
                                             (if spec-map
                                               (let [h-expr (utils/read-edn expr-str)
                                                     ^HCInfo i (halite-run/hc-body
                                                                spec-map
                                                                h-expr)]
                                                 {:h-result (.-h-result i)
                                                  :j-result (.-j-result i)
                                                  :j-expr (jadeite/to-jadeite h-expr)})
                                               (if spec-map-f
                                                 (let [h-expr (utils/read-edn expr-str)
                                                       spec-map (spec-map-f h-expr)
                                                       ^HCInfo i (halite-run/hc-body
                                                                  spec-map
                                                                  (list 'get
                                                                        (list 'refine-to instance :my/Result$v1)
                                                                        :x))]
                                                   {:h-result (.-h-result i)
                                                    :j-result (.-j-result i)
                                                    :j-expr (jadeite/to-jadeite h-expr)})
                                                 (let [i (halite-run/h*
                                                          (utils/read-edn expr-str)
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

(defn- expand-examples-map [op-maps]
  (->> op-maps
       (mapcat expand-example)
       (apply sorted-map)))

(defn- expand-examples-vector [basic-bnf]
  (->> basic-bnf
       (partition 2)
       (mapcat expand-example)
       vec))

;;;;

(def ^:private misc-notes ["'whitespace' refers to characters such as spaces, tabs, and newlines."
                           "Whitespace is generally not called out in the following diagrams. However, it is specified for a few syntactic constructs that explicitly rely on whitespace."])

(def ^:private misc-notes-halite ["For halite, whitespace also includes the comma. The comma can be used as an optional delimiter in sequences to improve readability."])

(def ^:private basic-bnf (expand-examples-vector basic-bnf-vector))

(def ^:private op-maps (expand-examples-map op-maps/op-maps))

(def ^:private jadeite-ommitted-ops #{'dec 'inc})

(def ^:private thrown-by-map (->> (-> op-maps
                                      (update-vals :throws))
                                  (remove (comp nil? second))
                                  (mapcat (fn [[k vs]] (map (fn [v] {v #{k}}) vs)))
                                  (apply merge-with into)))

(def ^:private thrown-by-basic-map (->> basic-bnf
                                        (partition 2)
                                        (map (fn [[k v]]
                                               [k (:throws v)]))
                                        (remove (comp nil? second))
                                        (mapcat (fn [[k vs]] (map (fn [v] {v #{k}}) vs)))
                                        (apply merge-with into)))

(def ^:private err-maps
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
                           (#{'com.viasat.halite.l-err 'com.viasat.halite.h-err} (:ns-name err)))
                         (merge-with merge
                                     @format-errors/error-atom
                                     err-maps/err-maps)))))

(defn- translate-op-maps-to-jadeite [op-maps]
  (let [op-maps (-> op-maps
                    (update-vals (fn [op]
                                   (if (:op-ref op)
                                     (update-in op [:op-ref] #(->> %
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

(def ^:private op-maps-j (translate-op-maps-to-jadeite op-maps))

(def ^:private tag-map (->> (-> op-maps
                                (update-vals :tags))
                            (remove (comp nil? second))
                            (mapcat (fn [[k vs]] (map (fn [v] {v #{k}}) vs)))
                            (apply merge-with into)))

(def ^:private tag-map-j (->> (-> op-maps-j
                                  (update-vals :tags))
                              (remove (comp nil? second))
                              (mapcat (fn [[k vs]] (map (fn [v] {v #{k}}) vs)))
                              (apply merge-with into)))

(defn- tag-md-filename [lang tag]
  (str (:prefix *run-config*) tag "-reference" (utils/get-language-modifier lang) ".md"))

(assert (= (set (keys tag-def-map))
           (set (concat (map keyword (mapcat :tags (take-nth 2 (next basic-bnf))))
                        (mapcat :tags (vals op-maps)))))
        "Mismatch between defined tags and used tags.")

(defn produce-full-md [lang]
  (let [titles {:halite "Halite Full Reference"
                :jadeite "Jadeite Full Reference"}
        info {:tag-def-map tag-def-map
              :tag-reference utils/get-reference-filename-link}
        {:keys [mode prefix append-sidebar-l2-f]} *run-config*]

    (when (= :user-guide mode)
      (append-sidebar-l2-f *sidebar-atom* lang mode prefix [lang :reference] (lang titles) "full"))
    (if (= :halite lang)
      (->> op-maps
           sort
           (md-full/full-md-all (assoc info :lang :halite) *run-config*)
           (utils/spit-dir (str (:root-dir *run-config*)
                                "/halite/"
                                (tag-md-filename lang "full"))))
      (->> op-maps-j
           sort
           (remove (fn [[k _]]
                     (jadeite-ommitted-ops k)))
           (md-full/full-md-all (assoc info :lang :jadeite) *run-config*)
           (utils/spit-dir (str (:root-dir *run-config*)
                                "/jadeite/"
                                (tag-md-filename lang "full")))))))

(defn- produce-basic-md [lang]
  (let [titles {:halite "Halite Basic Syntax Reference"
                :jadeite "Jadeite Basic Syntax Reference"}
        info {:tag-def-map tag-def-map
              :tag-reference utils/get-reference-filename-link}
        {:keys [mode prefix append-sidebar-l2-f]} *run-config*]
    (when (= :user-guide mode)
      (append-sidebar-l2-f *sidebar-atom* lang mode prefix [lang :reference] (lang titles) "basic-syntax"))
    (if (= :halite lang)
      (->> (md-basic/produce-basic-core-md (assoc info :lang :halite) *run-config* basic-bnf)
           (utils/spit-dir (str (:root-dir *run-config*)
                                "/halite/"
                                (tag-md-filename lang "basic-syntax"))))
      (->> (md-basic/produce-basic-core-md (assoc info :lang :jadeite) *run-config* basic-bnf)
           (utils/spit-dir (str (:root-dir *run-config*)
                                "/jadeite/"
                                (tag-md-filename lang "basic-syntax")))))))

(defn- produce-err-md [lang]
  (let [titles {:halite "Halite Error ID Reference"
                :jadeite "Jadeite Error ID Reference"}
        {:keys [mode prefix append-sidebar-l2-f]} *run-config*]
    (when (= :user-guide mode)
      (append-sidebar-l2-f *sidebar-atom* lang mode prefix [lang :reference] (lang titles) "err-id"))
    (if (= :halite lang)
      (->> err-maps
           sort
           (md-err/err-md-all :halite *run-config*)
           (utils/spit-dir (str (:root-dir *run-config*)
                                "/halite/"
                                (tag-md-filename lang "err-id"))))
      (->> err-maps
           sort
           (md-err/err-md-all :jadeite *run-config*)
           (utils/spit-dir (str (:root-dir *run-config*)
                                "/jadeite/"
                                (tag-md-filename lang "err-id")))))))

(defn- produce-tag-md [lang [tag-name tag]]
  (let [{:keys [mode prefix append-sidebar-l2-f]} *run-config*
        label (:label tag)]
    (when (= :user-guide mode)
      (append-sidebar-l2-f *sidebar-atom* lang mode prefix [lang :reference] label (name tag-name)))
    (->> [tag-name tag]
         (md-tag/produce-tag-md {:lang lang
                                 :op-maps op-maps
                                 :op-maps-j op-maps-j
                                 :tag-map tag-map
                                 :tag-map-j tag-map-j}
                                *run-config*)
         (utils/spit-dir (str (:root-dir *run-config*)
                              "/" (name lang)
                              "/" (tag-md-filename lang (name tag-name)))))))

(defn- how-to-filename [lang id]
  (str "how-to/" (str (:prefix *run-config*) (name id) (utils/get-language-modifier lang))))

(defn- how-to-reference [lang mode id]
  (str (when (= :local mode) (if (= :halite lang)
                               "halite/how-to/"
                               "jadeite/how-to/")) (str (:prefix *run-config*) (name id) (utils/get-language-modifier lang) (utils/get-reference-extension mode))))

(defn- how-to-md [lang [id how-to]]
  (->> (md-how-to/how-to-md lang *run-config* *sidebar-atom* [id how-to :how-to (how-to-reference lang (:mode *run-config*) id)])
       (utils/spit-dir (str (:root-dir *run-config*)
                            "/" (name lang)
                            "/" (how-to-filename lang id) ".md"))))

(defn- tutorial-filename [lang id]
  (str "tutorial/" (str (:prefix *run-config*) (name id) (utils/get-language-modifier lang))))

(defn- tutorial-reference [lang mode id]
  (str (when (= :local mode) (if (= :halite lang)
                               "halite/tutorial/"
                               "jadeite/tutorial/")) (str (:prefix *run-config*) (name id) (utils/get-language-modifier lang) (utils/get-reference-extension mode))))

(defn- tutorial-md [lang [id tutorial]]
  (->> (md-how-to/how-to-md lang *run-config* *sidebar-atom* [id tutorial :tutorial (tutorial-reference lang (:mode *run-config*) id)])
       (utils/spit-dir (str (:root-dir *run-config*)
                            "/" (name lang)
                            "/" (tutorial-filename lang id) ".md"))))

(defn- explanation-filename [lang id]
  (str "explanation/" (str (:prefix *run-config*) (name id) (utils/get-language-modifier lang))))

(defn- explanation-reference [lang mode id]
  (str (when (= :local mode) (if (= :halite lang)
                               "halite/explanation/"
                               "jadeite/explanation/")) (str (:prefix *run-config*) (name id) (utils/get-language-modifier lang) (utils/get-reference-extension mode))))

(defn- explanation-md [lang [id explanation]]
  (->> (md-how-to/how-to-md lang *run-config* *sidebar-atom* [id explanation :explanation (explanation-reference lang (:mode *run-config*) id)])
       (utils/spit-dir (str (:root-dir *run-config*)
                            "/" (name lang)
                            "/" (explanation-filename lang id) ".md"))))

;;

(defn- query-ops
  "Returns a subset of op-maps that include the given tag"
  [tag]
  (apply sorted-map (mapcat identity (filter (fn [[op m]]
                                               (get (:tags m) tag))
                                             op-maps))))

(defn- produce-bnf-diagram-for-tag [tag]
  (bnf-diagrams/produce-bnf-diagrams
   *run-config*
   (query-ops tag)
   (translate-op-maps-to-jadeite (query-ops tag))
   (str (name tag) ".svg")
   (str (name tag) "-j" ".svg")))

;; A list of vector pairs [data-var fn] indicating a document-generation
;; function that should be run whenever the data-var is updated, by installing
;; watcher on the data-var.  The fn is optional -- if not given, the watcher
;; will be removed from data-var. This is incomplete -- add entries as needed
;; for your documentation tasks:
(def ^:private gen-doc-fns
  [[#'op-maps/op-maps #(alter-var-root #'op-maps (constantly (expand-examples-map op-maps/op-maps)))]
   [#'op-maps #(do (alter-var-root #'op-maps-j (constantly (translate-op-maps-to-jadeite op-maps)))
                   (produce-full-md :halite)
                   (produce-full-md :jadeite))]
   [#'basic-bnf-vector #(alter-var-root #'basic-bnf (constantly (expand-examples-vector basic-bnf-vector)))]
   [#'basic-bnf #(do (bnf-diagrams/produce-basic-bnf-diagrams *run-config* "basic-all.svg" "basic-all-j.svg" basic-bnf)
                     (produce-basic-md :halite)
                     (produce-basic-md :jadeite))]
   [#'explanations #(do (run! (partial explanation-md :halite)  explanations)
                        (run! (partial explanation-md :jadeite) explanations))]])

(def ^:dynamic *running-gen-doc* #{})

(defn- gen-doc-with-fn [f _key data-var _old-val new-val]
  (when-not (*running-gen-doc* data-var)
    (let [indent (apply str (repeat (count *running-gen-doc*) "  "))]
      (binding [*running-gen-doc* (conj *running-gen-doc* data-var)]
        (println (format "%striggered at %s by %s" indent (java.util.Date.) data-var))
        (alter-var-root data-var (constantly new-val))
        (f)
        (println (format "%sdone" indent))))))

(doseq [[data-var f] gen-doc-fns]
  (if-not f
    (remove-watch data-var ::gen-doc)
    (add-watch data-var ::gen-doc (partial gen-doc-with-fn f))))

(defn- produce-spec-bnf-diagrams [run-config]
  (bnf-diagrams/produce-spec-bnf-diagram run-config "type.svg" data-spec-bnf/type-bnf-vector)
  (bnf-diagrams/produce-spec-bnf-diagram run-config "spec-var-map.svg" data-spec-bnf/spec-var-map-bnf-pair)
  (bnf-diagrams/produce-spec-bnf-diagram run-config "constraints.svg" data-spec-bnf/constraints-bnf-pair)
  (bnf-diagrams/produce-spec-bnf-diagram run-config "refinement-map.svg" data-spec-bnf/refinement-map-bnf-pair)
  (bnf-diagrams/produce-spec-bnf-diagram run-config "spec-map.svg" data-spec-bnf/spec-map-bnf-pair))

(defn generate-docs [run-config]
  (binding [*run-config* run-config
            *sidebar-atom* (atom {})]

    (produce-spec-bnf-diagrams run-config)

    (->> (md-spec/spec-md run-config)
         (utils/spit-dir (str (:root-dir run-config)
                              "/" (:prefix run-config) "spec-syntax-reference.md")))

    (bnf-diagrams/produce-basic-bnf-diagrams run-config "basic-all.svg" "basic-all-j.svg" basic-bnf)
    (bnf-diagrams/produce-bnf-diagrams run-config op-maps op-maps-j "halite.svg" "jadeite.svg")
    (->> (keys tag-def-map)
         (map produce-bnf-diagram-for-tag)
         dorun)

    (produce-full-md :halite)
    (produce-basic-md :halite)
    (produce-err-md :halite)

    (->> tag-def-map
         (map (partial produce-tag-md :halite))
         dorun)

    (->> explanations
         (map (partial explanation-md :halite))
         dorun)

    (->> how-tos
         (map (partial how-to-md :halite))
         dorun)

    (->> tutorials
         (map (partial tutorial-md :halite))
         dorun)

    (produce-full-md :jadeite)
    (produce-basic-md :jadeite)
    (produce-err-md :jadeite)

    (->> tag-def-map
         (map (partial produce-tag-md :jadeite))
         dorun)

    (->> explanations
         (map (partial explanation-md :jadeite))
         dorun)

    (->> how-tos
         (map (partial how-to-md :jadeite))
         dorun)

    (->> tutorials
         (map (partial tutorial-md :jadeite))
         dorun)

    (if-let [sidebar-file (:sidebar-file run-config)]
      (utils/spit-dir sidebar-file ((:produce-sidebar-f run-config) @*sidebar-atom*) true))

    (utils/spit-dir (str (:root-dir run-config)
                         "/" (:prefix run-config) "outline.md")
                    (produce-outline {:tag-def-map tag-def-map
                                      :tutorials tutorials
                                      :tutorial-filename tutorial-reference
                                      :how-tos how-tos
                                      :explanations explanations
                                      :explanation-filename explanation-reference
                                      :how-to-filename how-to-reference
                                      :tag-reference utils/get-reference-filename-link}
                                     run-config))))

(defn- generate-local-docs []
  (generate-docs {:mode :local
                  :root-dir "doc"
                  :image-dir "doc"
                  :prefix "halite_"}))
