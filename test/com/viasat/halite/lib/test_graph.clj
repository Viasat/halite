;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.lib.test-graph
  (:require [clojure.test :refer :all]
            [com.viasat.halite.lib.graph :as graph]))

(deftest test-detect-cycle
  ;; test helper functions

  (is (#'graph/path-contains? [1 2] 2))

  (is (not (#'graph/path-contains? [1 2] 3)))

  (is (= #{:a :b :c}
         (#'graph/detect-cycle* [] #{} :a {:a [:b :c]
                                           :b []
                                           :c []})))

  (is (thrown-with-msg? RuntimeException #"cycle detected"
                        (#'graph/detect-cycle* [] #{} :a {:a [:b]
                                                          :b [:c]
                                                          :c [:d :e]
                                                          :d [:b]})))

  ;; test main function
  (is (thrown-with-msg? RuntimeException #"cycle detected"
                        (graph/detect-cycle #{:a} {:a [:a]}))
      "Self reference is a cycle")

  (is (= #{:a :b :c :d :e :f :x :z}
         (graph/detect-cycle #{:a :x} {:a [:b :c :d]
                                       :b [:c]
                                       :c [:d :e]
                                       :d [:f]
                                       :x [:z]}))
      "Tree without cycles, checks all roots, returns all nodes checked.")

  (is (thrown-with-msg? RuntimeException #"cycle detected"
                        (graph/detect-cycle #{:a :p :x} {:a [:b :c :d]
                                                         :b [:c]
                                                         :c [:d :e]
                                                         :p [:q :r]
                                                         :r [:p]
                                                         :d [:f]
                                                         :x [:z]}))
      "All roots are checked, in this case just one leads to a cycle."))

(deftest test-find-roots
  (is (= #{}
         (graph/find-roots #{}
                           {})))
  (is (= #{:a}
         (graph/find-roots #{:a}
                           {:a #{}})))
  (is (= #{:a}
         (graph/find-roots #{:a :b}
                           {:a #{:b}})))
  (is (= #{}
         (graph/find-roots #{:a :b}
                           {:a #{:b}
                            :b #{:a}})))

  (is (= #{:a}
         (graph/find-roots #{:a :b :c :d}
                           {:a #{:b :c}
                            :b #{:d}
                            :c #{:d}
                            :d #{}})))
  (is (= #{:a :d}
         (graph/find-roots #{:a :b :c :d}
                           {:a #{:b :c}
                            :b #{}
                            :c #{}
                            :d #{:b :c}})))

  (is (= #{:a :f :e}
         (graph/find-roots #{:a :b :c :d :e :f}
                           {:a #{:b :c}
                            :b #{:d}
                            :c #{:d}
                            :d #{}
                            :e #{}
                            :f #{:g}}))))

;; (time (run-tests))
