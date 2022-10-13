;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.doc.md-how-to
  (:require [jibe.halite.doc.utils :as utils]
            [jibe.halite.doc.halite-run :as halite-run]
            [jibe.logic.jadeite :as jadeite]
            [clojure.string :as string])
  (:import [jibe.halite.doc.halite_run HCInfo]))

(defn how-to-reference [lang mode prefix id]
  (str prefix (name id) "-reference" (when (= :jadeite lang) "-j") (if (= :user-guide mode)
                                                                     ".html"
                                                                     ".md")))

;; user-guide requires this header
(defn generate-how-to-user-guide-hdr [lang prefix id how-to]
  (str "---\n"
       "title: " (:label how-to) "\n"
       "tags: \n"
       "keywords: \n"
       "sidebar:  jibe_sidebar\n"
       "permalink: " (how-to-reference lang :user-guide prefix id) "\n"
       "folder: jibe/halite/" (name lang) "\n"
       "summary: " (:desc how-to) "\n"
       "---\n\n"))

(defn append-how-to-sidebar [*sidebar-atom* lang mode prefix location title id]
  (let [existing-text (get-in @*sidebar-atom* location)
        new-text (utils/get-sidebar-l3-entry title (how-to-reference lang mode prefix id))]
    (swap! *sidebar-atom* assoc-in location (str existing-text new-text))))

(defn how-to-md [lang {:keys [mode prefix]} *sidebar-atom* [id how-to doc-type]]
  (->> [(when (= :user-guide mode)
          (generate-how-to-user-guide-hdr lang prefix id how-to))
        utils/generated-msg
        "## " (:label how-to) "\n\n"
        (:desc how-to) "\n\n"
        (loop [[c & more-c] (:contents how-to)
               spec-map nil
               spec-map-throws nil
               results []]
          (if c
            (cond
              (string? c) (recur more-c spec-map spec-map-throws (conj results (str c "\n\n")))

              (and (map c) (:spec-map c)) (let [spec-map-result (when (= :auto (:throws c))
                                                                  (let [^HCInfo i (binding [halite-run/*check-spec-map-for-cycles?* true]
                                                                                    (halite-run/hc-body
                                                                                     (:spec-map c)
                                                                                     'true))
                                                                        h-result (.-h-result i)]
                                                                    (when (not (and (vector? h-result)
                                                                                    (= :throws (first h-result))))
                                                                      (throw (ex-info "expected spec-map to fail" {:spec-map spec-map
                                                                                                                   :h-result h-result})))
                                                                    (str ({:halite  "\n\n;-- result --\n"
                                                                           :jadeite "\n\n//-- result --\n"}
                                                                          lang)
                                                                         ({:halite (utils/pprint-halite h-result)
                                                                           :jadeite (str h-result "\n")} lang))))]
                                            (recur more-c
                                                   (:spec-map c)
                                                   (:throws c)
                                                   (conj results
                                                         (str (utils/code-snippet lang mode (utils/spec-map-str lang (:spec-map c)))
                                                              spec-map-result))))
              (and (map c) (:code c)) (let [h-expr (:code c)
                                            ^HCInfo i (halite-run/hc-body
                                                       spec-map
                                                       h-expr)
                                            {:keys [t h-result j-result j-expr]} {:t (.-t i)
                                                                                  :h-result (.-h-result i)
                                                                                  :j-result (.-j-result i)
                                                                                  :j-expr (jadeite/to-jadeite h-expr)}
                                            skip-lint? (get c :skip-lint? false)
                                            [h-result j-result] (if (and (not skip-lint?)
                                                                         (vector? t)
                                                                         (= :throws (first t)))
                                                                  [t t]
                                                                  [h-result j-result])]
                                        (when (and (not (:throws c))
                                                   (vector? h-result)
                                                   (= :throws (first h-result)))
                                          (throw (ex-info "failed" {:h-expr h-expr
                                                                    :h-result h-result})))
                                        (when (and (:throws c)
                                                   (not (and (vector? h-result)
                                                             (= :throws (first h-result)))))
                                          (throw (ex-info "expected to fail" {:h-expr h-expr
                                                                              :h-result h-result})))
                                        (recur more-c
                                               spec-map
                                               spec-map-throws
                                               (conj results (utils/code-snippet
                                                              lang mode
                                                              (str ({:halite (utils/pprint-halite h-expr)
                                                                     :jadeite (str j-expr "\n")} lang)
                                                                   (when (or (:result c)
                                                                             (:throws c))
                                                                     (str ({:halite  "\n\n;-- result --\n"
                                                                            :jadeite "\n\n//-- result --\n"}
                                                                           lang)
                                                                          ({:halite (utils/pprint-halite h-result)
                                                                            :jadeite (str j-result "\n")} lang)))))))))
            results))
        (let [basic-ref-links (utils/basic-ref-links lang mode prefix how-to (if (= :user-guide mode)
                                                                               nil
                                                                               "../"))
              op-refs (some->> (:op-ref how-to)
                               (map ({:halite identity
                                      :jadeite utils/translate-op-name-to-jadeite} lang)))
              how-to-refs (:how-to-ref how-to)
              tutorial-refs (:tutorial-ref how-to)
              explanation-refs (:explanation-ref how-to)]
          (when (= :user-guide mode)
            (append-how-to-sidebar *sidebar-atom* lang mode prefix [lang doc-type] (:label how-to) id))
          [(when (or basic-ref-links op-refs how-to-refs tutorial-refs explanation-refs)
             "### Reference\n\n")
           (when basic-ref-links
             ["#### Basic elements:\n\n"
              (interpose ", " basic-ref-links) "\n\n"])
           (when op-refs
             ["#### Operator reference:\n\n"
              (for [a (sort op-refs)]
                (str "* " "[`" a "`](" (when (= :local mode) "../") (utils/get-reference-filename-link lang mode prefix "full")
                     "#" (utils/safe-op-anchor a) ")" "\n"))
              "\n\n"])
           (when how-to-refs
             ["#### How Tos:\n\n"
              (for [a (sort how-to-refs)]
                (str "* " "[" (name a) "](" (when (= :local mode) "../how-to/") (utils/get-reference lang mode prefix (name a)) ")" "\n"))
              "\n\n"])
           (when tutorial-refs
             ["#### Tutorials:\n\n"
              (for [a (sort tutorial-refs)]
                (str "* " "[" (name a) "](" (when (= :local mode) "../tutorial/") (utils/get-reference lang mode prefix (name a)) ")" "\n"))
              "\n\n"])
           (when explanation-refs
             ["#### Explanations:\n\n"
              (for [a (sort explanation-refs)]
                (str "* " "[" (name a) "](" (when (= :local mode) "../explanation/") (utils/get-reference lang mode prefix (name a)) ")" "\n"))
              "\n\n"])])]
       flatten
       (apply str)))
