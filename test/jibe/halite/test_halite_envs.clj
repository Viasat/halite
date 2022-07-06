;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.test-halite-envs
  (:require [jibe.halite.halite-envs :as halite-envs])
  (:import [clojure.lang ExceptionInfo])
  (:use [clojure.test]))

(deftest test-halite-type-from-var-type
  (let [senv (halite-envs/spec-env
              '{:ws/A {:abstract? true :spec-vars {} :constraints #{} :refines-to {}}
                :ws/B {:spec-vars {} :constraints #{} refines-to {}}})]
    (are [vtype htype]
         (= htype (halite-envs/halite-type-from-var-type senv vtype))

      "Integer" :Integer
      "String" :String
      "Boolean" :Boolean
      :ws/A [:Instance :* #{:ws/A}]
      :ws/B [:Instance :ws/B]
      [:ws/A] [:Vec [:Instance :* #{:ws/A}]]
      [:ws/B] [:Vec [:Instance :ws/B]]
      #{:ws/A} [:Set [:Instance :* #{:ws/A}]]
      #{:ws/B} [:Set [:Instance :ws/B]]
      [:Maybe "Integer"] [:Maybe :Integer]
      [:Maybe :ws/A] [:Maybe [:Instance :* #{:ws/A}]]
      [:Maybe :ws/B] [:Maybe [:Instance :ws/B]]
      [:Maybe [:ws/A]] [:Maybe [:Vec [:Instance :* #{:ws/A}]]])

    (are [invalid-type msg]
         (thrown-with-msg? ExceptionInfo msg
                           (halite-envs/halite-type-from-var-type senv invalid-type))

      "Foo" #"Unrecognized primitive type"
      :ws/C #"Spec not found"
      [] #"exactly one inner type"
      ["Integer" "String"] #"exactly one inner type"
      #{} #"exactly one inner type"
      #{"Integer" "String"} #"exactly one inner type"
      [[:Maybe "Integer"]] #"cannot have optional inner type"
      #{[:Maybe "Integer"]} #"cannot have optional inner type"
      [:Maybe "Integer" :ws/A] #"exactly one inner type"
      [:Maybe] #"exactly one inner type"
      :foo #"Invalid spec variable type")))

(deftest test-type-env-from-spec
  (let [senv (halite-envs/spec-env
              '{:ws/A {:abstract? true :spec-vars {} :constraints #{} :refines-to {}}
                :ws/B {:spec-vars {} :constraints #{} refines-to {}}
                :ws/C
                {:spec-vars {:x "Integer"
                             :w [:Maybe "Integer"]
                             :as [:Maybe #{:ws/A}]
                             :bs [:ws/B]}
                 :constraints #{}
                 :refines-to {}}})]
    (is (=
         '{$no-value :Unset
           no-value :Unset
           x :Integer
           w [:Maybe :Integer]
           as [:Maybe [:Set [:Instance :* #{:ws/A}]]]
           bs [:Vec [:Instance :ws/B]]}
         (->> :ws/C
              (halite-envs/lookup-spec senv)
              (halite-envs/type-env-from-spec senv)
              (halite-envs/scope))))))

;; (run-tests)
