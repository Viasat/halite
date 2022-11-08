;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-envs
  (:require [com.viasat.halite :as halite]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.var-type :as var-type])
  (:import [clojure.lang ExceptionInfo])
  (:use [clojure.test]))

(deftest test-halite-type-from-var-type
  (let [senv {:ws/A {:abstract? true}
              :ws/B {}}]
    (are [vtype htype]
         (= htype (var-type/halite-type-from-var-type senv vtype))

      :Integer :Integer
      :String :String
      :Boolean :Boolean
      :ws/A [:Instance :* #{:ws/A}]
      :ws/B [:Instance :ws/B]
      [:Vec :ws/A] [:Vec [:Instance :* #{:ws/A}]]
      [:Vec :ws/B] [:Vec [:Instance :ws/B]]
      [:Set :ws/A] [:Set [:Instance :* #{:ws/A}]]
      [:Set :ws/B] [:Set [:Instance :ws/B]]
      [:Maybe :Integer] [:Maybe :Integer]
      [:Maybe :ws/A] [:Maybe [:Instance :* #{:ws/A}]]
      [:Maybe :ws/B] [:Maybe [:Instance :ws/B]]
      [:Maybe [:Vec :ws/A]] [:Maybe [:Vec [:Instance :* #{:ws/A}]]])

    (are [invalid-type msg]
         (thrown-with-msg? ExceptionInfo msg
                           (var-type/halite-type-from-var-type senv invalid-type))

      "Foo" #"Invalid spec variable type"
      :foo #"Unrecognized primitive type"
      :ws/C #"Spec not found"
      [] #"Invalid spec variable type"
      [:Integer :String] #"Invalid spec variable type"
      #{} #"Invalid spec variable type"
      #{:Integer :String} #"Invalid spec variable type"
      [[:Maybe :Integer]] #"Invalid spec variable type"
      #{[:Maybe :Integer]} #"Invalid spec variable type"
      [:Maybe :Integer :ws/A] #"Invalid spec variable type"
      [:Maybe] #"Invalid spec variable type")))

(deftest test-type-env-from-spec
  (let [senv (var-type/to-halite-spec-env {:ws/A {:abstract? true}
                                           :ws/B {}
                                           :ws/C {:spec-vars {:x :Integer
                                                              :w [:Maybe :Integer]
                                                              :as [:Maybe [:Set :ws/A]]
                                                              :bs [:Vec :ws/B]}}})]
    (is (=
         '{no-value :Unset
           x :Integer
           w [:Maybe :Integer]
           as [:Maybe [:Set [:Instance :* #{:ws/A}]]]
           bs [:Vec [:Instance :ws/B]]}
         (->> :ws/C
              (envs/lookup-spec senv)
              envs/type-env-from-spec
              envs/scope)))))

(deftest test-to-halite-spec
  (is (nil?
       (var-type/to-halite-spec {}
                                nil)))
  (is (= {:abstract? true
          :spec-vars {:x [:Maybe [:Vec :Integer]]}}
         (var-type/to-halite-spec {}
                                  {:abstract? true
                                   :spec-vars {:x [:Maybe [:Vec :Integer]]}})))
  (is (= {:abstract? true
          :spec-vars {:x [:Instance :ws/X]}}
         (var-type/to-halite-spec {:ws/X {}}
                                  {:abstract? true
                                   :spec-vars {:x :ws/X}})))
  (is (= {:spec-vars {:x [:Instance :* #{:ws/X}]}
          :constraints {:x 'true}}
         (var-type/to-halite-spec {:ws/X {:abstract? true}}
                                  {:spec-vars {:x :ws/X}
                                   :constraints {:x 'true}})))
  (is (= {:abstract? true
          :constraints {:x 'true}
          :refines-to {:ws/A {:expr '{:$type :ws/A}}}}
         (var-type/to-halite-spec {}
                                  {:abstract? true
                                   :constraints {:x 'true}
                                   :refines-to {:ws/A {:expr '{:$type :ws/A}}}}))))

;; (run-tests)
