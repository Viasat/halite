;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.logic.test-service
  (:require [jibe.data.model :as model]
            [jibe.logic.data-store :as data-store]
            [jibe.logic.expression :as expression]
            [jibe.logic.permission-store :as permission-store]
            [jibe.logic.resource-spec-construct :as resource-spec-construct
             :refer [workspace spec variables constraints refinements]]
            [jibe.http.responses :as responses]
            [jibe.logic.permission-service :as permission-service]
            [jibe.logic.service :as service]
            [jibe.logic.test-setup-specs :as test-setup-specs :refer [*spec-store*]]
            [jibe.logic.test-hardware :as test-hardware]
            [jibe.logic.test-workspace :as test-workspace]
            [jibe.logic.workspace :as workspace]
            [internal :as s]
            [internal-close :as with-close]
            [internal :refer :all])
  (:import [clojure.lang ExceptionInfo]))

(deftest test-detect-cycle
  ;; test helper functions

  (is (#'service/path-contains? [1 2] 2))

  (is (not (#'service/path-contains? [1 2] 3)))

  (is (= #{:a :b :c}
         (#'service/detect-cycle* [] #{} :a {:a [:b :c]
                                             :b []
                                             :c []})))

  (is (thrown-with-msg? RuntimeException #"cycle detected"
                        (#'service/detect-cycle* [] #{} :a {:a [:b]
                                                            :b [:c]
                                                            :c [:d :e]
                                                            :d [:b]})))

  ;; test main function
  (is (thrown-with-msg? RuntimeException #"cycle detected"
                        (#'service/detect-cycle #{:a} {:a [:a]}))
      "Self reference is a cycle")

  (is (= #{:a :b :c :d :e :f :x :z}
         (#'service/detect-cycle #{:a :x} {:a [:b :c :d]
                                           :b [:c]
                                           :c [:d :e]
                                           :d [:f]
                                           :x [:z]}))
      "Tree without cycles, checks all roots, returns all nodes checked.")

  (is (thrown-with-msg? RuntimeException #"cycle detected"
                        (#'service/detect-cycle #{:a :p :x} {:a [:b :c :d]
                                                             :b [:c]
                                                             :c [:d :e]
                                                             :p [:q :r]
                                                             :r [:p]
                                                             :d [:f]
                                                             :x [:z]}))
      "All roots are checked, in this case just one leads to a cycle."))

;; (time (run-tests))
