;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.doc.halite-run
  (:require [jibe.halite :as halite]
            [jibe.halite-base :as halite-base]
            [jibe.halite.halite-envs :as halite-envs]
            [jibe.halite.halite-types :as halite-types]
            [jibe.halite.halite-lint :as halite-lint]
            [jibe.lib.format-errors :as format-errors]
            [jibe.logic.jadeite :as jadeite])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(deftype HInfo [s t j-expr h-result jh-result j-result])

(defmacro h-eval [expr]
  ;; helper for debugging
  `(let [spec-map# {}
         tenv# (halite-envs/type-env {})
         env# (halite-envs/env {})]
     (halite/eval-expr spec-map# tenv# env# '~expr)))

(defmacro h-lint [expr]
  ;; helper for debugging
  `(let [spec-map# {}
         tenv# (halite-envs/type-env {})]
     (halite-lint/type-check spec-map# tenv# '~expr)))

(defn j-eval [expr-str]
  ;; helper for debugging
  (let [spec-map {}
        tenv (halite-envs/type-env {})
        env (halite-envs/env {})
        expr (jadeite/to-halite expr-str)]
    (halite/eval-expr spec-map tenv env expr)))

(defn is-harness-error? [x]
  (and (vector? x)
       (= :throws (first x))))

(defn check-result-type [spec-map tenv t result]
  (when-not (or (is-harness-error? result)
                (is-harness-error? t))
    (if (= :Unset result)
      (assert (halite-types/maybe-type? t))
      (let [result-type (halite-lint/type-check spec-map tenv result)]
        (when-not (is-harness-error? t)
          (assert (halite-types/subtype? result-type t)))))))

(def halite-limits {:string-literal-length 1024
                    :string-runtime-length 1024
                    :vector-literal-count 1024
                    :vector-runtime-count 1024
                    :set-literal-count 1024
                    :set-runtime-count 1024
                    :list-literal-count 256
                    :expression-nesting-depth 10})

(defn ^HInfo h*
  ([expr]
   (h* expr false))
  ([expr separate-err-id?]
   (binding [halite-base/*limits* halite-limits
             format-errors/*squash-throw-site* true]
     (let [spec-map {}
           tenv (halite-envs/type-env {})
           env (halite-envs/env {})
           j-expr (try (jadeite/to-jadeite expr)
                       (catch RuntimeException e
                         [:throws (.getMessage e)]))
           s (try (halite/syntax-check expr)
                  nil
                  (catch RuntimeException e
                    [:syntax-check-throws (.getMessage e)]))
           t (try (halite-lint/type-check spec-map tenv expr)
                  (catch RuntimeException e
                    [:throws (.getMessage e)]))
           h-result (try (halite/eval-expr spec-map tenv env expr)
                         (catch ExceptionInfo e
                           (if separate-err-id?
                             [:throws (.getMessage e) (:err-id (ex-data e))]
                             [:throws (.getMessage e)]))
                         (catch RuntimeException e
                           [:throws (.getMessage e)]))
           h-result-type (check-result-type spec-map tenv t h-result)
           jh-expr (when (string? j-expr)
                     (try
                       (jadeite/to-halite j-expr)
                       (catch RuntimeException e
                         [:throws (.getMessage e)])))

           jh-result (try
                       (halite/eval-expr spec-map tenv env jh-expr)
                       (catch RuntimeException e
                         [:throws (.getMessage e)]))
           jh-result-type (check-result-type spec-map tenv t jh-result)
           j-result (try
                      (jadeite/to-jadeite (halite/eval-expr spec-map tenv env jh-expr))
                      (catch RuntimeException e
                        [:throws (.getMessage e)]))]
       (HInfo. s t j-expr h-result jh-result j-result)))))

(deftype HCInfo [s t h-result j-expr jh-result j-result])

(defn ^HCInfo hc*
  [spec-map expr separate-err-id?]
  (binding [format-errors/*squash-throw-site* true]
    (let [tenv (halite-envs/type-env {})
          env (halite-envs/env {})
          j-expr (try (jadeite/to-jadeite expr)
                      (catch RuntimeException e
                        [:throws (.getMessage e)]))
          s (try (halite/syntax-check expr)
                 nil
                 (catch RuntimeException e
                   [:syntax-check-throws (.getMessage e)]))
          t (try (halite-lint/type-check spec-map tenv expr)
                 (catch RuntimeException e
                   [:throws (.getMessage e)]))
          h-result (try (halite/eval-expr spec-map tenv env expr)
                        (catch ExceptionInfo e
                          (if separate-err-id?
                            [:throws (.getMessage e) (:err-id (ex-data e))]
                            [:throws (.getMessage e)]))
                        (catch RuntimeException e
                          [:throws (.getMessage e)]))
          jh-expr (when (string? j-expr)
                    (try
                      (jadeite/to-halite j-expr)
                      (catch RuntimeException e
                        [:throws (.getMessage e)])))

          jh-result (try
                      (halite/eval-expr spec-map tenv env jh-expr)
                      (catch RuntimeException e
                        [:throws (.getMessage e)]))
          j-result (try
                     (jadeite/to-jadeite (halite/eval-expr spec-map tenv env jh-expr))
                     (catch RuntimeException e
                       [:throws (.getMessage e)]))]

      (HCInfo. s t h-result j-expr jh-result j-result))))

(defn hc-body
  ([spec-map expr]
   (hc* (-> spec-map
            (update-vals #(merge {:spec-vars {}
                                  :constraints []
                                  :refines-to {}}
                                 %)))
        expr true)))
