;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.doc.run
  (:require [com.viasat.halite :as halite]
            [com.viasat.halite.base :as halite-base]
            [com.viasat.halite.envs :as halite-envs]
            [com.viasat.halite.types :as halite-types]
            [com.viasat.halite.lint :as halite-lint]
            [com.viasat.halite.lib.format-errors :as format-errors]
            [com.viasat.halite.jadeite :as jadeite])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(deftype HInfo [s t j-expr h-result jh-result j-result])

(def ^:dynamic *check-spec-map-for-cycles?* false)

(defn- eval-h-expr [senv tenv env expr]
  (halite/eval-expr true true true *check-spec-map-for-cycles?* senv tenv env expr))

(defmacro h-eval [expr]
  ;; helper for debugging
  `(let [spec-map# {}
         tenv# (halite-envs/type-env {})
         env# (halite-envs/env {})]
     (eval-h-expr spec-map# tenv# env# '~expr)))

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
    (eval-h-expr spec-map tenv env expr)))

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
           h-result (try (eval-h-expr spec-map tenv env expr)
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
                       (eval-h-expr spec-map tenv env jh-expr)
                       (catch RuntimeException e
                         [:throws (.getMessage e)]))
           jh-result-type (check-result-type spec-map tenv t jh-result)
           j-result (try
                      (jadeite/to-jadeite (eval-h-expr spec-map tenv env jh-expr))
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
          h-result (try (eval-h-expr spec-map tenv env expr)
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
                      (eval-h-expr spec-map tenv env jh-expr)
                      (catch RuntimeException e
                        [:throws (.getMessage e)]))
          j-result (try
                     (jadeite/to-jadeite (eval-h-expr spec-map tenv env jh-expr))
                     (catch RuntimeException e
                       [:throws (.getMessage e)]))]

      (HCInfo. s t h-result j-expr jh-result j-result))))

(defn hc-body
  ([spec-map expr]
   (hc* spec-map expr true)))
