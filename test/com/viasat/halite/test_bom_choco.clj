;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-bom-choco
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-choco :as bom-choco]
            [com.viasat.halite.var-ref :as var-ref]
            [schema.test])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(var-ref/init)

(deftest test-basic
  (is (= {:choco-spec {:vars {}
                       :constraint-map {}}
          :choco-bounds {}}
         (bom-choco/bom-to-choco {:$instance-of :ws/A$v1})))

  (is (= '{:choco-spec {:vars {[:x] :Int}
                        :constraint-map {[:ws/A$v1 "x"] (> #r [:x] 100)}}
           :choco-bounds {[:x] [1 100]}}
         (bom-choco/bom-to-choco {:$instance-of :ws/A$v1
                                  :x {:$primitive-type :Integer}
                                  :$constraints {"x" '(> #r [:x] 100)}})))

  (is (= '{:choco-spec {:vars {[:b :x] :Int}
                        :constraint-map {[:b :ws/B$v1 "x"] (> #r [:b :x] 100)}}
           :choco-bounds {[:b :x] [1 100]}}
         (bom-choco/bom-to-choco {:$instance-of :ws/A$v1
                                  :b {:$instance-of :ws/B$v1
                                      :x {:$primitive-type :Integer}
                                      :$constraints {"x" '(> #r [:b :x] 100)}}})))

  (is (= '{:choco-spec {:vars {[:b :x] :Int}
                        :constraint-map {[:b :ws/B$v1 "x"] (let [a 100] (> #r [:b :x] a))}}
           :choco-bounds {[:b :x] [1 100]}}
         (bom-choco/bom-to-choco {:$instance-of :ws/A$v1
                                  :b {:$instance-of :ws/B$v1
                                      :x {:$primitive-type :Integer}
                                      :$constraints {"x" '(let [a 100]
                                                            (> #r [:b :x] a))}}})))

  (is (= '{:choco-spec {:vars {[:a1] :Int
                               [:b :x] :Int
                               [:c :y] :Int}
                        :constraint-map {[:c :ws/C$v1 "y_c"] (let [a 103] (> #r [:c :y] a))
                                         [:b :ws/B$v1 "x_c"] (let [a 102] (> #r [:b :x] a))
                                         [:ws/A$v1 "a1_c"] (let [a 101] (> #r [:a1] a))}}
           :choco-bounds {[:a1] [1 100]
                          [:b :x] [1 100]
                          [:c :y] [1 100]}}
         (bom-choco/bom-to-choco {:$instance-of :ws/A$v1
                                  :a1 {:$primitive-type :Integer}
                                  :$constraints {"a1_c" '(let [a 101]
                                                           (> #r [:a1] a))}
                                  :b {:$instance-of :ws/B$v1
                                      :x {:$primitive-type :Integer}
                                      :$constraints {"x_c" '(let [a 102]
                                                              (> #r [:b :x] a))}}
                                  :c {:$instance-of :ws/C$v1
                                      :y {:$primitive-type :Integer}
                                      :$constraints {"y_c" '(let [a 103]
                                                              (> #r [:c :y] a))}}}))))

(deftest test-to-syms
  (let [bom {:$instance-of :ws/A$v1
             :a1 {:$primitive-type :Integer}
             :$constraints {"a1_c" '(let [a 101]
                                      (> #r [:a1] a))}
             :b {:$instance-of :ws/B$v1
                 :x {:$primitive-type :Integer}
                 :$constraints {"x_c" '(let [a 102]
                                         (> #r [:b :x] a))}}
             :c {:$instance-of :ws/C$v1
                 :y {:$primitive-type :Integer}
                 :$constraints {"y_c" '(let [a 103]
                                         (> #r [:c :y] a))}}}]
    (is (= '{:choco-spec {:vars {$_0 :Int
                                 $_1 :Int
                                 $_2 :Int}
                          :constraints #{(let [a 103] (> $_2 a))
                                         (let [a 102] (> $_1 a))
                                         (let [a 101] (> $_0 a))}}
             :choco-bounds {$_0 [1 100]
                            $_1 [1 100]
                            $_2 [1 100]}
             :sym-to-path [$_0 [:a1]
                           $_1 [:b :x]
                           $_2 [:c :y]]}
           (bom-choco/paths-to-syms bom (bom-choco/bom-to-choco bom))))))

;; (run-tests)
