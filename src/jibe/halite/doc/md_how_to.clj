;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.doc.md-how-to
  (:require [jibe.halite.doc.utils :as utils]
            [jibe.halite-guide :as halite-guide]
            [jibe.logic.jadeite :as jadeite]
            [clojure.string :as string])
  (:import [jibe.halite_guide HCInfo]))

(defn how-to-md [lang [id how-to]]
  (->> [utils/generated-msg
        "## " (:label how-to) "\n\n"
        (:desc how-to) "\n\n"
        (loop [[c & more-c] (:contents how-to)
               spec-map nil
               results []]
          (if c
            (cond
              (string? c) (recur more-c spec-map (conj results (str c "\n\n")))

              (and (map c) (:spec-map c)) (recur more-c (:spec-map c) (conj results (utils/code-snippet lang (utils/spec-map-str lang (:spec-map c)))))
              (and (map c) (:code c)) (let [h-expr (:code c)
                                            ^HCInfo i (halite-guide/hc-body
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
                                        (recur more-c spec-map
                                               (conj results (utils/code-snippet lang (str ({:halite (utils/pprint-halite h-expr)
                                                                                             :jadeite (str j-expr "\n")} lang)
                                                                                           (when (or (:result c)
                                                                                                     (:throws c))
                                                                                             (str ({:halite  "\n\n;-- result --\n"
                                                                                                    :jadeite "\n\n//-- result --\n"}
                                                                                                   lang)
                                                                                                  ({:halite (utils/pprint-halite h-result)
                                                                                                    :jadeite (str j-result "\n")} lang)))))))))
            results))
        (let [basic-ref-links (utils/basic-ref-links lang how-to "../")
              op-refs (some->> (:op-ref how-to)
                               (map ({:halite identity
                                      :jadeite utils/translate-op-name-to-jadeite} lang)))
              how-to-refs (:how-to-ref how-to)
              tutorial-refs (:tutorial-ref how-to)
              explanation-refs (:explanation-ref how-to)]
          [(when (or basic-ref-links op-refs how-to-refs tutorial-refs explanation-refs)
             "### Reference\n\n")
           (when basic-ref-links
             ["#### Basic elements:\n\n"
              (interpose ", " basic-ref-links) "\n\n"])
           (when op-refs
             ["#### Operator reference:\n\n"
              (for [a (sort op-refs)]
                (str "* " "[`" a "`](" (if (= :halite lang)
                                         "../halite-full-reference.md"
                                         "../jadeite-full-reference.md")
                     "#" (utils/safe-op-anchor a) ")" "\n"))
              "\n\n"])
           (when how-to-refs
             ["#### How tos:\n\n"
              (for [a (sort how-to-refs)]
                (str "* " "[" (name a) "](" "../how-to/" (name a) ".md" ")" "\n"))
              "\n\n"])
           (when tutorial-refs
             ["#### Tutorials:\n\n"
              (for [a (sort tutorial-refs)]
                (str "* " "[" (name a) "](" "../tutorial/" (name a) ".md" ")" "\n"))
              "\n\n"])
           (when explanation-refs
             ["#### Explanations:\n\n"
              (for [a (sort explanation-refs)]
                (str "* " "[" (name a) "](" "../explanation/" (name a) ".md" ")" "\n"))
              "\n\n"])])]
       flatten
       (apply str)))
