;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.doc.md-how-to
  (:require [clojure.string :as string]
            [com.viasat.halite.doc.run :as doc-run]
            [com.viasat.halite.doc.utils :as utils]
            [com.viasat.halite.propagate :as propagate]
            [com.viasat.halite.var-types :as var-types]
            [com.viasat.jadeite :as jadeite]
            [com.viasat.halite.transpile.rewriting :as rewriting])
  (:import [com.viasat.halite.doc.run HCInfo]))

(set! *warn-on-reflection* true)

(defn how-to-contents [{:keys [code-snippet-f spec-snippet-f translate-spec-map-to-f]} lang how-to specs-only?]
  (loop [[c & more-c] (:contents how-to)
         spec-map nil
         spec-map-throws nil
         results []]
    (if c
      (cond
        (string? c) (recur more-c spec-map spec-map-throws (conj results (if specs-only?
                                                                           nil
                                                                           (str c "\n\n"))))

        (and (map c) (or (:spec-map c)
                         (:spec-map-merge c))) (let [spec-map-to-use (or (:spec-map c)
                                                                         (merge spec-map (:spec-map-merge c)))
                                                     spec-map-result (when (= :auto (:throws c))
                                                                       (let [^HCInfo i (binding [doc-run/*check-spec-map-for-cycles?* true]
                                                                                         (doc-run/hc-body
                                                                                          spec-map-to-use
                                                                                          'true))
                                                                             h-result (.-h-result i)]
                                                                         (when (not (and (vector? h-result)
                                                                                         (= :throws (first h-result))))
                                                                           (throw (ex-info "expected spec-map to fail" {:spec-map spec-map-to-use
                                                                                                                        :h-result h-result})))
                                                                         (str ({:halite  "\n\n;-- result --\n"
                                                                                :jadeite "\n\n//-- result --\n"}
                                                                               lang)
                                                                              ({:halite (utils/pprint-halite h-result)
                                                                                :jadeite (str h-result "\n")} lang))))]
                                                 (recur more-c
                                                        spec-map-to-use
                                                        (:throws c)
                                                        (conj results
                                                              (spec-snippet-f lang (translate-spec-map-to-f lang (or
                                                                                                                  (:spec-map-merge c)
                                                                                                                  (:spec-map c))
                                                                                                            spec-map-result)))))
        (and (map c) (:code c)) (let [h-expr (:code c)
                                      ^HCInfo i (doc-run/hc-body
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
                                                              :h-result h-result}
                                                    (:ex (meta h-result)))))
                                  (when (and (:throws c)
                                             (not (and (vector? h-result)
                                                       (= :throws (first h-result)))))
                                    (throw (ex-info "expected to fail" {:h-expr h-expr
                                                                        :h-result h-result}
                                                    (:ex (meta h-result)))))
                                  (recur more-c
                                         spec-map
                                         spec-map-throws
                                         (conj results (if specs-only?
                                                         nil
                                                         (code-snippet-f
                                                          lang
                                                          (str ({:halite (utils/pprint-halite h-expr)
                                                                 :jadeite (str j-expr "\n")} lang)
                                                               (when (or (:result c)
                                                                         (:throws c))
                                                                 (str ({:halite  "\n\n;-- result --\n"
                                                                        :jadeite "\n\n//-- result --\n"}
                                                                       lang)
                                                                      (when (and (contains? c :result)
                                                                                 (not= (:result c) :auto)
                                                                                 (not= (:result c) h-result))
                                                                        (throw (ex-info "results do not match" {:c c
                                                                                                                :h-result h-result})))
                                                                      ({:halite (utils/pprint-halite h-result)
                                                                        :jadeite (str j-result "\n")} lang)))))))))
        (and (map c) (:propagate c)) (recur
                                      more-c
                                      spec-map
                                      spec-map-throws
                                      (conj results
                                            (let [comment ({:halite  ";;" :jadeite "//"} lang)
                                                  ;;code-str #(translate-spec-map-to-f lang % "") ;; Prettier jadeite, but doesn't handle fixed decimals
                                                  code-str ({:halite utils/pprint-halite
                                                             :jadeite #(str (jadeite/to-jadeite %) "\n")}
                                                            lang)]
                                              (spec-snippet-f lang
                                                              (str
                                                               comment " Propagate input bounds:\n"
                                                               (code-str (:propagate c))
                                                               "\n" comment " -- result bounds --\n"
                                                               (code-str
                                                                (propagate/propagate (var-types/to-halite-spec-env spec-map)
                                                                                     propagate/default-options
                                                                                     (:propagate c)))))))))
      results)))

(defn how-to-md [lang {:keys [menu-file
                              prefix
                              generate-how-to-hdr-f
                              append-to-how-to-menu-f
                              get-link-f
                              get-reference-links-f] :as config} [id how-to doc-type]]
  (->> [(generate-how-to-hdr-f lang prefix id how-to)
        "## " (:label how-to) "\n\n"
        (:desc how-to) "\n\n"
        (how-to-contents config lang how-to false)
        (let [basic-ref-links (get-reference-links-f lang prefix  "../" how-to)
              op-refs (some->> (:op-ref how-to)
                               (map ({:halite identity
                                      :jadeite utils/translate-op-name-to-jadeite} lang)))
              how-to-refs (:how-to-ref how-to)
              tutorial-refs (:tutorial-ref how-to)
              explanation-refs (:explanation-ref how-to)]
          (when menu-file
            (append-to-how-to-menu-f lang prefix [lang doc-type] (:label how-to) id))
          [(when (or basic-ref-links op-refs how-to-refs tutorial-refs explanation-refs)
             "### Reference\n\n")
           (when basic-ref-links
             ["#### Basic elements:\n\n"
              (interpose ", " basic-ref-links) "\n\n"])
           (when op-refs
             ["#### Operator reference:\n\n"
              (for [a (sort op-refs)]
                (str "* " "[`" a "`](" (get-link-f lang prefix "../" "full-reference")
                     "#" (utils/safe-op-anchor a) ")" "\n"))
              "\n\n"])
           (when how-to-refs
             ["#### How Tos:\n\n"
              (for [a (sort how-to-refs)]
                (str "* " "[" (name a) "](" (get-link-f lang prefix "../how-to/" (name a)) ")" "\n"))
              "\n\n"])
           (when tutorial-refs
             ["#### Tutorials:\n\n"
              (for [a (sort tutorial-refs)]
                (str "* " "[" (name a) "](" (get-link-f lang prefix "../tutorial/" (name a)) ")" "\n"))
              "\n\n"])
           (when explanation-refs
             ["#### Explanations:\n\n"
              (for [a (sort explanation-refs)]
                (str "* " "[" (name a) "](" (get-link-f lang prefix "../explanation/" (name a)) ")" "\n"))
              "\n\n"])])]
       flatten
       (apply str)))
