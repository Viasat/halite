;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-envs
  (:require [com.viasat.halite :as halite]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.var-types :as var-types])
  (:import [clojure.lang ExceptionInfo])
  (:use [clojure.test]))

(deftest test-halite-type-from-var-type
  (let [senv {:ws/A {:abstract? true}
              :ws/B {}}]
    (are [vtype htype]
         (= htype (var-types/halite-type-from-var-type senv vtype))

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
                           (var-types/halite-type-from-var-type senv invalid-type))

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
  (let [senv (var-types/to-halite-spec-env {:ws/A {:abstract? true}
                                            :ws/B {}
                                            :ws/C {:fields {:x :Integer
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
       (var-types/to-halite-spec {}
                                 nil)))
  (is (= {:abstract? true
          :fields {:x [:Maybe [:Vec :Integer]]}}
         (var-types/to-halite-spec {}
                                   {:abstract? true
                                    :fields {:x [:Maybe [:Vec :Integer]]}})))
  (is (= {:abstract? true
          :fields {:x [:Instance :ws/X]}}
         (var-types/to-halite-spec {:ws/X {}}
                                   {:abstract? true
                                    :fields {:x :ws/X}})))
  (is (= {:fields {:x [:Instance :* #{:ws/X}]}
          :constraints [["x" 'true]]}
         (var-types/to-halite-spec {:ws/X {:abstract? true}}
                                   {:fields {:x :ws/X}
                                    :constraints #{{:name "x"
                                                    :expr 'true}}})))
  (is (= {:abstract? true
          :constraints [["x" 'true]]
          :refines-to {:ws/A {:expr '{:$type :ws/A}}}}
         (var-types/to-halite-spec {}
                                   {:abstract? true
                                    :constraints #{{:name "x" :expr 'true}}
                                    :refines-to {:ws/A {:expr '{:$type :ws/A}}}}))))

;; (run-tests)
