;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.test-propagate
  (:require [jibe.halite.halite-envs :as halite-envs]
            [jibe.halite.propagate :as hp]
            [jibe.halite.transpile.rewriting :as rewriting :refer [with-summarized-trace-for]]
            [jibe.halite.transpile.ssa :as ssa]
            [jibe.halite.transpile.lowering :as lowering]
            [jibe.halite.transpile.util :refer [fixpoint]]
            [jibe.halite.transpile.simplify :as simplify]
            [schema.core :as s]
            [schema.test]
            [viasat.choco-clj-opt :as choco-clj])
  (:use clojure.test))

;; Prismatic schema validation is too slow to leave on by default for these tests.
;; If you're debugging a test failure, and the problem is a 'type' error,
;; turning schema validation on is likely to help you track it down.
;; (use-fixtures :once schema.test/validate-schemas)

;; TODO: We need to rewrite 'div forms in the case where the quotient is a variable,
;; to ensure that choco doesn't force the variable to be zero even when the div might not
;; be evaluated.

(deftest test-spec-ify-bound-on-simple-spec
  (let [senv (halite-envs/spec-env
              '{:ws/A
                {:spec-vars {:x "Integer", :y "Integer", :b "Boolean"}
                 :constraints [["c1" (let [delta (abs (- x y))]
                                       (and (< 5 delta)
                                            (< delta 10)))]
                               ["c2" (= b (< x y))]]
                 :refines-to {}}})]

    (is (= '{:spec-vars {:x "Integer", :y "Integer", :b "Boolean"}
             :constraints
             [["vars" (valid? {:$type :ws/A :x x :y y :b b})]]
             :refines-to {}}
           (hp/spec-ify-bound senv {:$type :ws/A})))))

(deftest test-spec-ify-bound-with-composite-specs
  (testing "simple composition"
    (let [senv (halite-envs/spec-env
                '{:ws/A {:spec-vars {:x "Integer", :y "Integer"}
                         :constraints [["pos" (and (< 0 x) (< 0 y))]
                                       ["boundedSum" (< (+ x y) 20)]]
                         :refines-to {}}
                  :ws/B {:spec-vars {:a1 :ws/A, :a2 :ws/A}
                         :constraints [["a1smaller" (and (< (get a1 :x) (get a2 :x))
                                                         (< (get a1 :y) (get a2 :y)))]]
                         :refines-to {}}})]
      (is (= '{:spec-vars {:a1|x "Integer", :a1|y "Integer", :a2|x "Integer", :a2|y "Integer"}
               :refines-to {}
               :constraints
               [["vars" (valid? {:$type :ws/B
                                 :a1 {:$type :ws/A :x a1|x :y a1|y}
                                 :a2 {:$type :ws/A :x a2|x :y a2|y}})]]}
             (hp/spec-ify-bound senv {:$type :ws/B}))))))

(deftest test-propagation-of-trivial-spec
  (let [senv (halite-envs/spec-env
              {:ws/A {:spec-vars {:x "Integer", :y "Integer", :oddSum "Boolean"}
                      :constraints '[["pos" (and (< 0 x) (< 0 y))]
                                     ["y is greater" (< x y)]
                                     ["lines" (let [xysum (+ x y)]
                                                (or (= 42 xysum)
                                                    (= 24 (+ 42 (- 0 xysum)))))]
                                     ["oddSum" (= oddSum (= 1 (mod* (+ x y) 2)))]]
                      :refines-to {}}})
        opts {:default-int-bounds [-100 100]}]
    (are [in out]
         (= out (hp/propagate senv opts in))

      {:$type :ws/A} {:$type :ws/A, :x {:$in [1 99]}, :y {:$in [2 100]}, :oddSum {:$in #{true false}}}

      {:$type :ws/A, :x 8} {:$type :ws/A, :x 8, :y {:$in [9 100]}, :oddSum {:$in #{true false}}}

      {:$type :ws/A, :x 8, :oddSum false} {:$type :ws/A, :x 8, :y {:$in [10 100]}, :oddSum false}

      {:$type :ws/A, :x 10} {:$type :ws/A, :x 10, :y 32, :oddSum false})))

(deftest test-one-to-one-composition
  (let [senv (halite-envs/spec-env
              '{:ws/A {:spec-vars {:x "Integer", :y "Integer"}
                       :constraints [["pos" (and (< 0 x) (< 0 y))]
                                     ["boundedSum" (< (+ x y) 20)]]
                       :refines-to {}}
                :ws/B {:spec-vars {:a1 :ws/A, :a2 :ws/A}
                       :constraints [["a1smaller" (and (< (get a1 :x) (get a2 :x))
                                                       (< (get a1 :y) (get a2 :y)))]]
                       :refines-to {}}})
        opts {:default-int-bounds [-100 100]}]

    (are [in out]
         (= out (hp/propagate senv opts in))

      {:$type :ws/B}
      {:$type :ws/B
       :a1 {:$type :ws/A
            :x {:$in [1 16]}
            :y {:$in [1 16]}}
       :a2 {:$type :ws/A
            :x {:$in [2 17]}
            :y {:$in [2 17]}}}

      {:$type :ws/B
       :a1 {:$type :ws/A
            :x 15}}
      {:$type :ws/B
       :a1 {:$type :ws/A
            :x 15
            :y {:$in [1 2]}}
       :a2 {:$type :ws/A
            :x {:$in [16 17]}
            :y {:$in [2 3]}}})))

(deftest test-instance-literals
  (let [senv (halite-envs/spec-env
              '{:ws/A
                {:spec-vars {:ax "Integer"}
                 :constraints [["c1" (and (< 0 ax) (< ax 10))]]
                 :refines-to {}}

                :ws/B
                {:spec-vars {:by "Integer", :bz "Integer"}
                 :constraints [["c2" (let [a {:$type :ws/A :ax (* 2 by)}
                                           x (get {:$type :ws/A :ax (+ bz bz)} :ax)]
                                       (= x (get {:$type :ws/C :cx (get a :ax)} :cx)))]]
                 :refines-to {}}

                :ws/C
                {:spec-vars {:cx "Integer"}
                 :constraints [["c3" (= cx (get {:$type :ws/A :ax cx} :ax))]]
                 :refines-to {}}})
        opts {:default-int-bounds [-100 100]}]
    (are [in out]
         (= out (hp/propagate senv opts in))

      {:$type :ws/C} {:$type :ws/C :cx {:$in [1 9]}}
      {:$type :ws/B} {:$type :ws/B :by {:$in [1 4]} :bz {:$in [1 4]}}
      {:$type :ws/B :by 2} {:$type :ws/B :by 2 :bz 2})))

(deftest test-propagate-and-refinement
  (schema.core/without-fn-validation
   (let [senv (halite-envs/spec-env
               '{:ws/A
                 {:spec-vars {:an "Integer"}
                  :constraints [["a1" (<= an 6)]]
                  :refines-to {:ws/B {:expr {:$type :ws/B :bn (+ 1 an)}}}}

                 :ws/B
                 {:spec-vars {:bn "Integer"}
                  :constraints [["b1" (< 0 bn)]]
                  :refines-to {:ws/C {:expr {:$type :ws/C :cn bn}}}}

                 :ws/C
                 {:spec-vars {:cn "Integer"}
                  :constraints [["c1" (= 0 (mod cn 2))]]
                  :refines-to {}}

                 :ws/D
                 {:spec-vars {:a :ws/A, :dm "Integer", :dn "Integer"}
                  :constraints [["d1" (= dm (get (refine-to {:$type :ws/A :an dn} :ws/C) :cn))]
                                ["d2" (= (+ 1 dn) (get (refine-to a :ws/B) :bn))]]
                  :refines-to {}}})]

     (are [in out]
          (= out (hp/propagate senv in))

       {:$type :ws/C :cn {:$in (set (range 10))}} {:$type :ws/C :cn {:$in #{0 2 4 6 8}}}

       {:$type :ws/B :bn {:$in (set (range 10))}} {:$type :ws/B :bn {:$in #{2 4 6 8}}}

       {:$type :ws/A :an {:$in (set (range 10))}} {:$type :ws/A :an {:$in #{1 3 5}}}

       {:$type :ws/D} {:$type :ws/D, :a {:$type :ws/A, :an {:$in [1 5]}}, :dm {:$in [2 6]}, :dn {:$in [1 5]}}))))

(deftest test-spec-ify-bound-for-primitive-optionals
  (s/without-fn-validation
   (let [senv (halite-envs/spec-env
               '{:ws/A
                 {:spec-vars {:an "Integer", :aw [:Maybe "Integer"], :p "Boolean"}
                  :constraints [["a1" (= aw (when p an))]]
                  :refines-to {}}})
         opts {:default-int-bounds [-10 10]}]

     (is (= '{:spec-vars {:an "Integer", :aw [:Maybe "Integer"], :p "Boolean"}
              :constraints [["vars" (valid? {:$type :ws/A :an an :aw aw :p p})]]
              :refines-to {}}
            (hp/spec-ify-bound senv {:$type :ws/A}))))))

(deftest test-propagate-for-primitive-optionals
  (let [senv (halite-envs/spec-env
              '{:ws/A
                {:spec-vars {:an "Integer", :aw [:Maybe "Integer"], :p "Boolean"}
                 :constraints [["a1" (= aw (when p an))]]
                 :refines-to {}}})
        opts {:default-int-bounds [-10 10]}]

    (are [in out]
         (= out (hp/propagate senv opts in))

      {:$type :ws/A}            {:$type :ws/A, :an {:$in [-10 10]}, :aw {:$in [-10 10 :Unset]}, :p {:$in #{false true}}}
      {:$type :ws/A :p true}    {:$type :ws/A :an {:$in [-10 10]} :aw {:$in [-10 10]}, :p true}
      {:$type :ws/A :p false}   {:$type :ws/A :an {:$in [-10 10]} :aw :Unset, :p false}
      {:$type :ws/A :aw :Unset} {:$type :ws/A :an {:$in [-10 10]} :aw :Unset, :p false})))

(deftest test-push-if-value-with-refinement
  (let [senv (halite-envs/spec-env
              '{:ws/B
                {:spec-vars {:bw [:Maybe "Integer"], :c2 [:Maybe :ws/C]}
                 :constraints []
                 :refines-to {}}
                :ws/C
                {:spec-vars {:cw [:Maybe "Integer"]}
                 :constraints []
                 :refines-to {}}
                :ws/D
                {:spec-vars {:dx "Integer"}
                 :constraints []
                 :refines-to {:ws/B {:expr {:$type :ws/B
                                            :bw (when (= 0 dx) 5)
                                            :c2 {:$type :ws/C :cw 6}}}}}
                :ws/E {:spec-vars
                       {:dx "Integer",
                        :bw [:Maybe "Integer"],
                        :cw [:Maybe "Integer"]},
                       :constraints
                       [["$all" (= (refine-to {:dx dx, :$type :ws/D} :ws/B)
                                   {:bw bw,
                                    :c2 (if-value cw
                                                  {:cw cw, :$type :ws/C}
                                                  $no-value),
                                    :$type :ws/B})]],
                       :refines-to {}}})]
    (is (= {:$type :ws/E, :bw {:$in #{5 :Unset}}, :cw 6, :dx {:$in [-10 10]}}
           (hp/propagate senv {:default-int-bounds [-10 10]} {:$type :ws/E})))))

(def nested-optionals-spec-env
  '{:ws/A
    {:spec-vars {:b1 [:Maybe :ws/B], :b2 [:Maybe :ws/B], :ap "Boolean"}
     :constraints [["a1" (= b1 b2)]
                   ["a2" (=> ap (if-value b1 true false))]]
     :refines-to {}}
    :ws/B
    {:spec-vars {:bx "Integer", :bw [:Maybe "Integer"], :bp "Boolean"
                 :c1 :ws/C
                 :c2 [:Maybe :ws/C]}
     :constraints [["b1" (= bw (when bp bx))]
                   ["b2" (< bx 15)]]
     :refines-to {}}
    :ws/C
    {:spec-vars {:cx "Integer"
                 :cw [:Maybe "Integer"]}
     :constraints []
     :refines-to {}}
    :ws/D
    {:spec-vars {:dx "Integer"}
     :constraints []
     :refines-to {:ws/B {:expr {:$type :ws/B
                                :bx (+ dx 1)
                                :bw (when (= 0 (mod (abs dx) 2))
                                      (div dx 2))
                                :bp false
                                :c1 {:$type :ws/C :cx dx :cw dx}
                                :c2 {:$type :ws/C :cx dx :cw 12}}}}}})

(def flatten-vars #'hp/flatten-vars)

(deftest test-flatten-vars-for-nested-optionals
  (let [senv (halite-envs/spec-env nested-optionals-spec-env)]

    (are [bound expected]
         (= expected (flatten-vars senv bound))

      {:$type :ws/C}
      {::hp/spec-id :ws/C
       :cx [:cx "Integer"]
       :cw [:cw [:Maybe "Integer"]]
       ::hp/mandatory #{}}

      {:$type :ws/B}
      {::hp/spec-id :ws/B
       :bx [:bx "Integer"]
       :bw [:bw [:Maybe "Integer"]]
       :bp [:bp "Boolean"]
       :c1 {::hp/spec-id :ws/C
            :cx [:c1|cx "Integer"]
            :cw [:c1|cw [:Maybe "Integer"]]
            ::hp/mandatory #{}}
       :c2 {::hp/spec-id :ws/C
            :$witness [:c2? "Boolean"]
            :cx [:c2|cx [:Maybe "Integer"]]
            :cw [:c2|cw [:Maybe "Integer"]]
            ::hp/mandatory #{:c2|cx}}
       ::hp/mandatory #{}}

      {:$type :ws/A}
      {::hp/spec-id :ws/A
       :b1 {::hp/spec-id :ws/B
            :$witness [:b1? "Boolean"]
            :bx [:b1|bx [:Maybe "Integer"]]
            :bw [:b1|bw [:Maybe "Integer"]]
            :bp [:b1|bp [:Maybe "Boolean"]]
            :c1 {::hp/spec-id :ws/C
                 :cx [:b1|c1|cx [:Maybe "Integer"]]
                 :cw [:b1|c1|cw [:Maybe "Integer"]]
                 ::hp/mandatory #{:b1|c1|cx}}
            :c2 {::hp/spec-id :ws/C
                 :$witness [:b1|c2? "Boolean"]
                 :cx [:b1|c2|cx [:Maybe "Integer"]]
                 :cw [:b1|c2|cw [:Maybe "Integer"]]
                 ::hp/mandatory #{:b1|c2|cx}}
            ::hp/mandatory #{:b1|c1|cx :b1|bx :b1|bp}}
       :b2 {::hp/spec-id :ws/B
            :$witness [:b2? "Boolean"]
            :bx [:b2|bx [:Maybe "Integer"]]
            :bw [:b2|bw [:Maybe "Integer"]]
            :bp [:b2|bp [:Maybe "Boolean"]]
            :c1 {::hp/spec-id :ws/C
                 :cx [:b2|c1|cx [:Maybe "Integer"]]
                 :cw [:b2|c1|cw [:Maybe "Integer"]]
                 ::hp/mandatory #{:b2|c1|cx}}
            :c2 {::hp/spec-id :ws/C
                 :$witness [:b2|c2? "Boolean"]
                 :cx [:b2|c2|cx [:Maybe "Integer"]]
                 :cw [:b2|c2|cw [:Maybe "Integer"]]
                 ::hp/mandatory #{:b2|c2|cx}}
            ::hp/mandatory #{:b2|c1|cx :b2|bx :b2|bp}}
       :ap [:ap "Boolean"]
       ::hp/mandatory #{}}

      {:$type :ws/A :b1 {:$type [:Maybe :ws/B]}}
      {::hp/spec-id :ws/A
       :b1 {::hp/spec-id :ws/B
            :$witness [:b1? "Boolean"]
            :bx [:b1|bx [:Maybe "Integer"]]
            :bw [:b1|bw [:Maybe "Integer"]]
            :bp [:b1|bp [:Maybe "Boolean"]]
            :c1 {::hp/spec-id :ws/C
                 :cx [:b1|c1|cx [:Maybe "Integer"]]
                 :cw [:b1|c1|cw [:Maybe "Integer"]]
                 ::hp/mandatory #{:b1|c1|cx}}
            :c2 {::hp/spec-id :ws/C
                 :$witness [:b1|c2? "Boolean"]
                 :cx [:b1|c2|cx [:Maybe "Integer"]]
                 :cw [:b1|c2|cw [:Maybe "Integer"]]
                 ::hp/mandatory #{:b1|c2|cx}}
            ::hp/mandatory #{:b1|c1|cx :b1|bx :b1|bp}}
       :b2 {::hp/spec-id :ws/B
            :$witness [:b2? "Boolean"]
            :bx [:b2|bx [:Maybe "Integer"]]
            :bw [:b2|bw [:Maybe "Integer"]]
            :bp [:b2|bp [:Maybe "Boolean"]]
            :c1 {::hp/spec-id :ws/C
                 :cx [:b2|c1|cx [:Maybe "Integer"]]
                 :cw [:b2|c1|cw [:Maybe "Integer"]]
                 ::hp/mandatory #{:b2|c1|cx}}
            :c2 {::hp/spec-id :ws/C
                 :$witness [:b2|c2? "Boolean"]
                 :cx [:b2|c2|cx [:Maybe "Integer"]]
                 :cw [:b2|c2|cw [:Maybe "Integer"]]
                 ::hp/mandatory #{:b2|c2|cx}}
            ::hp/mandatory #{:b2|c1|cx :b2|bx :b2|bp}}
       :ap [:ap "Boolean"]
       ::hp/mandatory #{}}

      {:$type :ws/A :b1 :Unset}
      {::hp/spec-id :ws/A
       :b1 {::hp/spec-id :ws/B
            :$witness [:b1? "Boolean"]
            :bx [:b1|bx [:Maybe "Integer"]]
            :bw [:b1|bw [:Maybe "Integer"]]
            :bp [:b1|bp [:Maybe "Boolean"]]
            :c1 {::hp/spec-id :ws/C
                 :cx [:b1|c1|cx [:Maybe "Integer"]]
                 :cw [:b1|c1|cw [:Maybe "Integer"]]
                 ::hp/mandatory #{:b1|c1|cx}}
            :c2 {::hp/spec-id :ws/C
                 :$witness [:b1|c2? "Boolean"]
                 :cx [:b1|c2|cx [:Maybe "Integer"]]
                 :cw [:b1|c2|cw [:Maybe "Integer"]]
                 ::hp/mandatory #{:b1|c2|cx}}
            ::hp/mandatory #{:b1|c1|cx :b1|bx :b1|bp}}
       :b2 {::hp/spec-id :ws/B
            :$witness [:b2? "Boolean"]
            :bx [:b2|bx [:Maybe "Integer"]]
            :bw [:b2|bw [:Maybe "Integer"]]
            :bp [:b2|bp [:Maybe "Boolean"]]
            :c1 {::hp/spec-id :ws/C
                 :cx [:b2|c1|cx [:Maybe "Integer"]]
                 :cw [:b2|c1|cw [:Maybe "Integer"]]
                 ::hp/mandatory #{:b2|c1|cx}}
            :c2 {::hp/spec-id :ws/C
                 :$witness [:b2|c2? "Boolean"]
                 :cx [:b2|c2|cx [:Maybe "Integer"]]
                 :cw [:b2|c2|cw [:Maybe "Integer"]]
                 ::hp/mandatory #{:b2|c2|cx}}
            ::hp/mandatory #{:b2|c1|cx :b2|bx :b2|bp}}
       :ap [:ap "Boolean"]
       ::hp/mandatory #{}})))

(def lower-spec-bound #'hp/lower-spec-bound)

(deftest test-lower-spec-bound-for-nested-optionals
  (let [senv (halite-envs/spec-env nested-optionals-spec-env)]
    (are [bound lowered]
         (= lowered (lower-spec-bound (flatten-vars senv bound) bound))

      {:$type :ws/A} {}

      {:$type :ws/A, :ap true} '{ap true}

      {:$type :ws/A, :b1 {:$type [:Maybe :ws/B]}} {}

      {:$type :ws/A, :b1 {:$type :ws/B}} '{b1? true}

      {:$type :ws/A, :b1 :Unset} '{b1? false}

      {:$type :ws/A :b1 {:$type [:Maybe :ws/B] :bx 7}}  '{b1|bx #{7 :Unset}}

      {:$type :ws/A :b1 {:$type :ws/B :bx 7}}   '{b1? true, b1|bx 7}

      {:$type :ws/A :b1 {:$type [:Maybe :ws/B] :bx {:$in #{1 2 3}}}} '{b1|bx #{1 2 3 :Unset}}

      {:$type :ws/A :b1 {:$type :ws/B :bx {:$in #{1 2 3}}}} '{b1? true, b1|bx #{1 2 3}}

      {:$type :ws/A :b1 {:$type [:Maybe :ws/B] :bx {:$in [2 4]}}} '{b1|bx [2 4 :Unset]}

      {:$type :ws/A :b1 {:$type :ws/B :bx {:$in [2 4]}}} '{b1? true, b1|bx [2 4]}

      {:$type :ws/A :b1 {:$type [:Maybe :ws/B] :c2 {:$type [:Maybe :ws/C] :cw 5}}} '{b1|c2|cw #{5 :Unset}}

      {:$type :ws/A :b1 {:$type :ws/B :c2 {:$type [:Maybe :ws/C] :cw 5}}} '{b1? true, b1|c2|cw #{5 :Unset}}

      ;; TODO: Ensure that optionality-constraints for this case produces (=> b1? b1|c2?)
      {:$type :ws/A :b1 {:$type [:Maybe :ws/B] :c2 {:$type :ws/C :cw 5}}} '{b1|c2|cw #{5 :Unset}}

      {:$type :ws/A :b1 {:$type :ws/B :c2 {:$type :ws/C :cw 5}}} '{b1? true, b1|c2? true, b1|c2|cw 5})))

(def optionality-constraint #'hp/optionality-constraint)

(deftest test-constraints-for-composite-optional
  (let [senv (halite-envs/spec-env nested-optionals-spec-env)
        flattened-vars (flatten-vars senv {:$type :ws/A})]
    (is (= '(and
             (= b1?
                (if-value b1|bp true false)
                (if-value b1|bx true false)
                (if-value b1|c1|cx true false))
             (=> (if-value b1|bw true false) b1?)
             (=> b1|c2? b1?))
           (optionality-constraint
            senv
            (:b1 flattened-vars))))
    (is (= '(and
             (= b1|c2? (if-value b1|c2|cx true false))
             (=> (if-value b1|c2|cw true false) b1|c2?))
           (optionality-constraint
            senv
            (:c2 (:b1 flattened-vars)))))))

(deftest test-propagate-for-spec-valued-optionals
  (schema.core/without-fn-validation
   (let [senv (halite-envs/spec-env nested-optionals-spec-env)
         opts {:default-int-bounds [-10 10]}]

     (are [in out]
          (= out (hp/propagate senv opts in))

       {:$type :ws/A}
       {:$type :ws/A,
        :ap {:$in #{false true}},
        :b1 {:$type [:Maybe :ws/B],
             :bp {:$in #{false true}},
             :bw {:$in [-10 10 :Unset]},
             :bx {:$in [-10 10]},
             :c1 {:$type :ws/C, :cw {:$in [-10 10 :Unset]}, :cx {:$in [-10 10]}},
             :c2 {:$type [:Maybe :ws/C], :cw {:$in [-10 10 :Unset]}, :cx {:$in [-10 10]}}},
        :b2 {:$type [:Maybe :ws/B],
             :bp {:$in #{false true}},
             :bw {:$in [-10 10 :Unset]},
             :bx {:$in [-10 10]},
             :c1 {:$type :ws/C, :cw {:$in [-10 10 :Unset]}, :cx {:$in [-10 10]}},
             :c2 {:$type [:Maybe :ws/C], :cw {:$in [-10 10 :Unset]}, :cx {:$in [-10 10]}}}}

       {:$type :ws/A :b2 {:$type :ws/B}}
       {:$type :ws/A,
        :ap {:$in #{false true}},
        :b1 {:$type :ws/B,
             :bp {:$in #{false true}},
             :bw {:$in [-10 10 :Unset]},
             :bx {:$in [-10 10]},
             :c1 {:$type :ws/C, :cw {:$in [-10 10 :Unset]}, :cx {:$in [-10 10]}},
             :c2 {:$type [:Maybe :ws/C], :cw {:$in [-10 10 :Unset]}, :cx {:$in [-10 10]}}},
        :b2 {:$type :ws/B,
             :bp {:$in #{false true}},
             :bw {:$in [-10 10 :Unset]},
             :bx {:$in [-10 10]},
             :c1 {:$type :ws/C, :cw {:$in [-10 10 :Unset]}, :cx {:$in [-10 10]}},
             :c2 {:$type [:Maybe :ws/C], :cw {:$in [-10 10 :Unset]}, :cx {:$in [-10 10]}}}}

       {:$type :ws/A, :b1 :Unset}
       {:$type :ws/A, :ap false, :b1 :Unset, :b2 :Unset}

       {:$type :ws/A :ap true}
       {:$type :ws/A,
        :ap true,
        :b1 {:$type :ws/B,
             :bp {:$in #{false true}},
             :bw {:$in [-10 10 :Unset]},
             :bx {:$in [-10 10]},
             :c1 {:$type :ws/C, :cw {:$in [-10 10 :Unset]}, :cx {:$in [-10 10]}},
             :c2 {:$type [:Maybe :ws/C], :cw {:$in [-10 10 :Unset]}, :cx {:$in [-10 10]}}},
        :b2 {:$type :ws/B,
             :bp {:$in #{false true}},
             :bw {:$in [-10 10 :Unset]},
             :bx {:$in [-10 10]},
             :c1 {:$type :ws/C, :cw {:$in [-10 10 :Unset]}, :cx {:$in [-10 10]}},
             :c2 {:$type [:Maybe :ws/C], :cw {:$in [-10 10 :Unset]}, :cx {:$in [-10 10]}}}}

       {:$type :ws/A :b1 {:$type :ws/B :bw :Unset}}
       {:$type :ws/A,
        :ap {:$in #{false true}},
        :b1 {:$type :ws/B,
             :bp false,
             :bw :Unset,
             :bx {:$in [-10 10]},
             :c1 {:$type :ws/C, :cw {:$in [-10 10 :Unset]}, :cx {:$in [-10 10]}},
             :c2 {:$type [:Maybe :ws/C], :cw {:$in [-10 10 :Unset]}, :cx {:$in [-10 10]}}},
        :b2 {:$type :ws/B,
             :bp false,
             :bw :Unset,
             :bx {:$in [-10 10]},
             :c1 {:$type :ws/C, :cw {:$in [-10 10 :Unset]}, :cx {:$in [-10 10]}},
             :c2 {:$type [:Maybe :ws/C], :cw {:$in [-10 10 :Unset]}, :cx {:$in [-10 10]}}}}

       {:$type :ws/A :b1 {:$type :ws/B :bx 7} :b2 {:$type :ws/B :bp true}}
       {:$type :ws/A,
        :ap {:$in #{false true}},
        :b1 {:$type :ws/B,
             :bp true,
             :bw 7,
             :bx 7,
             :c1 {:$type :ws/C, :cw {:$in [-10 10 :Unset]}, :cx {:$in [-10 10]}},
             :c2 {:$type [:Maybe :ws/C], :cw {:$in [-10 10 :Unset]}, :cx {:$in [-10 10]}}},
        :b2 {:$type :ws/B,
             :bp true,
             :bw 7,
             :bx 7,
             :c1 {:$type :ws/C, :cw {:$in [-10 10 :Unset]}, :cx {:$in [-10 10]}},
             :c2 {:$type [:Maybe :ws/C], :cw {:$in [-10 10 :Unset]}, :cx {:$in [-10 10]}}}}

       {:$type :ws/A :b1 {:$type :ws/B :bx {:$in [2 6]}}}
       {:$type :ws/A,
        :ap {:$in #{false true}},
        :b1 {:$type :ws/B,
             :bp {:$in #{false true}},
             :bw {:$in [2 6 :Unset]},
             :bx {:$in [2 6]},
             :c1 {:$type :ws/C, :cw {:$in [-10 10 :Unset]}, :cx {:$in [-10 10]}},
             :c2 {:$type [:Maybe :ws/C], :cw {:$in [-10 10 :Unset]}, :cx {:$in [-10 10]}}},
        :b2 {:$type :ws/B,
             :bp {:$in #{false true}},
             :bw {:$in [2 6 :Unset]},
             :bx {:$in [2 6]},
             :c1 {:$type :ws/C, :cw {:$in [-10 10 :Unset]}, :cx {:$in [-10 10]}},
             :c2 {:$type [:Maybe :ws/C], :cw {:$in [-10 10 :Unset]}, :cx {:$in [-10 10]}}}}

       {:$type :ws/A :b1 {:$type :ws/B :bw 7}}
       {:$type :ws/A,
        :ap {:$in #{false true}},
        :b1 {:$type :ws/B,
             :bp true,
             :bw 7,
             :bx 7,
             :c1 {:$type :ws/C, :cw {:$in [-10 10 :Unset]}, :cx {:$in [-10 10]}},
             :c2 {:$type [:Maybe :ws/C], :cw {:$in [-10 10 :Unset]}, :cx {:$in [-10 10]}}},
        :b2 {:$type :ws/B,
             :bp true,
             :bw 7,
             :bx 7,
             :c1 {:$type :ws/C, :cw {:$in [-10 10 :Unset]}, :cx {:$in [-10 10]}},
             :c2 {:$type [:Maybe :ws/C], :cw {:$in [-10 10 :Unset]}, :cx {:$in [-10 10]}}}}

       {:$type :ws/D}
       {:$type :ws/D :dx {:$in [-9 9]}}

       {:$type :ws/D :dx {:$in (set (range -5 6))}}
       {:$type :ws/D :dx {:$in #{-5 -3 -1 1 3 5}}}))))

(deftest simpler-composite-optional-test
  (schema.core/without-fn-validation
   (let [senv (halite-envs/spec-env
               '{:ws/A
                 {:spec-vars {:b1 :ws/B :b2 [:Maybe :ws/B]}
                  :constraints [["a1" (= b1 b2)]]
                  :refines-to {}}
                 :ws/B
                 {:spec-vars {:bx "Integer", :by [:Maybe "Integer"]}
                  :constraints [["b1" (< 0 bx)]]
                  :refines-to {}}})
         opts {:default-int-bounds [-10 10]}]

     (is (= '{:spec-vars {:b1|bx "Integer"
                          :b1|by [:Maybe "Integer"]
                          :b2? "Boolean"
                          :b2|bx [:Maybe "Integer"]
                          :b2|by [:Maybe "Integer"]}
              :constraints [["vars" (valid?
                                     {:$type :ws/A
                                      :b1 {:$type :ws/B :bx b1|bx :by b1|by}
                                      :b2 (when b2?
                                            (if-value b2|bx
                                                      {:$type :ws/B :bx b2|bx :by b2|by}
                                                      $no-value))})]
                            ["$b2?" (and (= b2? (if-value b2|bx true false))
                                         (=> (if-value b2|by true false) b2?))]]
              :refines-to {}}
            (hp/spec-ify-bound senv {:$type :ws/A})))

     (is (= '{:$type :ws/A,
              :b1 {:$type :ws/B, :bx {:$in [1 1000]}, :by {:$in [-1000 1000 :Unset]}},
              :b2 {:$type :ws/B, :bx {:$in [1 1000]}, :by {:$in [-1000 1000 :Unset]}}}
            (hp/propagate senv {:$type :ws/A}))))))

(deftest test-refine-optional
  ;; The 'features' that interact here: valid? and instance literals w/ unassigned variables.
  (let [senv (halite-envs/spec-env
              '{:my/A {:abstract? true
                       :spec-vars {:a1 [:Maybe "Integer"]
                                   :a2 [:Maybe "Integer"]}
                       :constraints [["a1_pos" (if-value a1 (> a1 0) true)]
                                     ["a2_pos" (if-value a2 (> a2 0) true)]]
                       :refines-to {}}
                :my/B {:abstract? false
                       :spec-vars {:b "Integer"}
                       :constraints []
                       :refines-to {:my/A {:expr {:$type :my/A, :a1 b}}}}})]
    (is (= {:$type :my/B :b {:$in [1 100]}}
           (hp/propagate senv {:$type :my/B :b {:$in [-100 100]}})))))

(deftest test-propagate-with-errors
  (let [senv (halite-envs/spec-env
              '{:ws/A {:spec-vars {:an "Integer" :ap [:Maybe "Boolean"]}
                       :refines-to {}
                       :constraints [["a1" (if (< an 10)
                                             (if (< an 1)
                                               (error "an is too small")
                                               (if-value ap ap false))
                                             (= ap (when (< an 20)
                                                     (error "not big enough"))))]]}})]
    (are [in out]
         (= out (hp/propagate senv in))

      {:$type :ws/A} {:$type :ws/A, :an {:$in [1 1000]}, :ap {:$in #{true :Unset}}}
      {:$type :ws/A :an {:$in (set (range 7 22))}} {:$type :ws/A,:an {:$in #{7 8 9 20 21}},:ap {:$in #{true :Unset}}}
      {:$type :ws/A :an 21} {:$type :ws/A :an 21 :ap :Unset}
      {:$type :ws/A :an {:$in (set (range 7 22))} :ap :Unset} {:$type :ws/A :an {:$in #{20 21}} :ap :Unset})))

#_(deftest example-abstract-propagation
    (s/with-fn-validation
      (let [senv (halite-envs/spec-env
                  '{:ws/W ; 0
                    {:abstract? true
                     :spec-vars {:wn "Integer"}
                     :constraints [["wn_range" (and (< 2 wn) (< wn 8))]]
                     :refines-to {}}
                    :ws/A ; 1
                    {:spec-vars {:a "Integer"}
                     :constraints [["ca" (< a 6)]]
                     :refines-to
                     {:ws/W {:expr {:$type :ws/W, :wn (+ a 1)}}}}
                    :ws/B ; 2
                    {:spec-vars {:b "Integer"}
                     :constraints [["cb" (< 5 b)]]
                     :refines-to
                     {:ws/W {:expr {:$type :ws/W, :wn (- b 2)}}}}
                    :ws/C ; 3
                    {:spec-vars {:w :ws/W}
                     :constraints []
                     :refines-to {}}})
          ;; Hand-written choco spec for bound {:$type :ws/C}
            choco-spec '{:vars {w<-? #{1 2}
                                w<-ws$A|a :Int
                                w<-ws$B|b :Int}
                         :optionals #{w<-ws$A|a w<-ws$B|b}
                         :constraints
                         #{(if (= 1 w<-?) ; ws/A
                             (if-value w<-ws$A|a
                                       (and
                                        (< w<-ws$A|a 6)
                                        (< 2 (+ w<-ws$A|a 1))
                                        (< (+ w<-ws$A|a 1) 8))
                                       false)
                             (if (= 2 w<-?) ; ws/B
                               (if-value w<-ws$B|b
                                         (and
                                          (< 5 w<-ws$B|b)
                                          (< 2 (- w<-ws$B|b 2))
                                          (< (- w<-ws$B|b 2) 8))
                                         false)
                               false))}}
          ;; Other possible bounds: {:$type :ws/C :w {:$type :ws/A}}
          ;;                        {:$type :ws/C :w {:$in {:ws/A {}, :ws/B {}}}}
          ;;                        {:$type :ws/C :w {:$refines-to {:ws/W {:w 7}}}}
            ]
        (are [in out]
             (= out (choco-clj/propagate choco-spec in))

          {} '{w<-? #{1 2}, w<-ws$A|a [-1000 1000 :Unset], w<-ws$B|b [-1000 1000 :Unset]}
          '{w<-? 1} nil
          '{w<-? 2} nil))))

(deftest test-tricky-inst-literal-simplification
  (let [senv (halite-envs/spec-env
              '{:ws/A
                {:spec-vars {:b1 [:Maybe :ws/B]}
                 :constraints [["a2" (if-value b1 true false)]]
                 :refines-to {}}
                :ws/B
                {:spec-vars {}
                 :constraints []
                 :refines-to {}}})]
    (is (= {:$type :ws/A :b1 {:$type :ws/B}}
           (hp/propagate senv {:$type :ws/A})))))

(comment "
Stuff to do/remember regarding abstractness!

We'll need to extend the representation of a bound to handle alternative concrete types.

We'll need to handle that extension on the INPUT side, as well as producing the output.
")

(comment
  {:$type :ws/A}
  =>
  {:$type :ws/A
   :a {:$in [1 100]}
   :$refines-to
   {:ws/B {:b 12 :d {:$refines-to ...} :$expr {}}
    :ws/C {:c {:$in #{1 3 5}}}}})
