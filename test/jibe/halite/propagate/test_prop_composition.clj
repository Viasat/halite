;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.propagate.test-prop-composition
  (:require [jibe.halite.halite-envs :as halite-envs]
            [jibe.halite.propagate.prop-composition :as pc]
            [jibe.halite.transpile.lowering :as lowering]
            [jibe.halite.transpile.rewriting :as rewriting :refer [with-summarized-trace-for]]
            [jibe.halite.transpile.simplify :as simplify]
            [jibe.halite.transpile.ssa :as ssa]
            [jibe.halite.transpile.util :refer [fixpoint]]
            [schema.core :as s]
            [schema.test]
            [viasat.choco-clj-opt :as choco-clj])
  (:use clojure.test))

;; Prismatic schema validation is too slow to leave on by default for these tests.
;; If you're debugging a test failure, and the problem is a 'type' error,
;; turning schema validation on is likely to help you track it down.
;; (use-fixtures :once schema.test/validate-schemas)

(deftest test-spec-ify-bound-on-simple-spec
  (let [specs '{:ws/A
                {:spec-vars {:x "Integer", :y "Integer", :b "Boolean"}
                 :constraints [["c1" (let [delta (abs (- x y))]
                                       (and (< 5 delta)
                                            (< delta 10)))]
                               ["c2" (= b (< x y))]]
                 :refines-to {}}}]

    (is (= '{:spec-vars {:x "Integer", :y "Integer", :b "Boolean"}
             :constraints
             [["vars" (valid? {:$type :ws/A :x x :y y :b b})]]
             :refines-to {}}
           (pc/spec-ify-bound specs {:$type :ws/A})))))

(deftest test-spec-ify-bound-with-composite-specs
  (testing "simple composition"
    (let [specs '{:ws/A {:spec-vars {:x "Integer", :y "Integer"}
                         :constraints [["pos" (and (< 0 x) (< 0 y))]
                                       ["boundedSum" (< (+ x y) 20)]]
                         :refines-to {}}
                  :ws/B {:spec-vars {:a1 :ws/A, :a2 :ws/A}
                         :constraints [["a1smaller" (and (< (get a1 :x) (get a2 :x))
                                                         (< (get a1 :y) (get a2 :y)))]]
                         :refines-to {}}}]
      (is (= '{:spec-vars {:a1|x "Integer", :a1|y "Integer", :a2|x "Integer", :a2|y "Integer"}
               :refines-to {}
               :constraints
               [["vars" (valid? {:$type :ws/B
                                 :a1 {:$type :ws/A :x a1|x :y a1|y}
                                 :a2 {:$type :ws/A :x a2|x :y a2|y}})]]}
             (pc/spec-ify-bound specs {:$type :ws/B}))))))

(deftest test-propagation-of-trivial-spec
  (let [specs {:ws/A {:spec-vars {:x "Integer", :y "Integer", :oddSum "Boolean"}
                      :constraints '[["pos" (and (< 0 x) (< 0 y))]
                                     ["y is greater" (< x y)]
                                     ["lines" (let [xysum (+ x y)]
                                                (or (= 42 xysum)
                                                    (= 24 (+ 42 (- 0 xysum)))))]
                                     ["oddSum" (= oddSum (= 1 (mod* (+ x y) 2)))]]
                      :refines-to {}}}
        opts {:default-int-bounds [-100 100]}]
    (are [in out]
         (= out (pc/propagate specs opts in))

      {:$type :ws/A} {:$type :ws/A, :x {:$in [1 99]}, :y {:$in [2 100]}, :oddSum {:$in #{true false}}}

      {:$type :ws/A, :x 8} {:$type :ws/A, :x 8, :y {:$in [9 100]}, :oddSum {:$in #{true false}}}

      {:$type :ws/A, :x 8, :oddSum false} {:$type :ws/A, :x 8, :y {:$in [10 100]}, :oddSum false}

      {:$type :ws/A, :x 10} {:$type :ws/A, :x 10, :y 32, :oddSum false})))

(deftest test-one-to-one-composition
  (let [senv '{:ws/A {:spec-vars {:x "Integer", :y "Integer"}
                      :constraints [["pos" (and (< 0 x) (< 0 y))]
                                    ["boundedSum" (< (+ x y) 20)]]
                      :refines-to {}}
               :ws/B {:spec-vars {:a1 :ws/A, :a2 :ws/A}
                      :constraints [["a1smaller" (and (< (get a1 :x) (get a2 :x))
                                                      (< (get a1 :y) (get a2 :y)))]]
                      :refines-to {}}}
        opts {:default-int-bounds [-100 100]}]

    (are [in out]
         (= out (pc/propagate senv opts in))

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
  (let [senv '{:ws/A
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
                :refines-to {}}}
        opts {:default-int-bounds [-100 100]}]
    (are [in out]
         (= out (pc/propagate senv opts in))

      {:$type :ws/C} {:$type :ws/C :cx {:$in [1 9]}}
      {:$type :ws/B} {:$type :ws/B :by {:$in [1 4]} :bz {:$in [1 4]}}
      {:$type :ws/B :by 2} {:$type :ws/B :by 2 :bz 2})))

(deftest test-propagate-and-refinement
  (schema.core/without-fn-validation
   (let [senv '{:ws/A
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
                 :refines-to {}}}]

     (are [in out]
          (= out (pc/propagate senv in))

       {:$type :ws/C :cn {:$in (set (range 10))}} {:$type :ws/C :cn {:$in #{0 2 4 6 8}}}

       {:$type :ws/B :bn {:$in (set (range 10))}} {:$type :ws/B
                                                   :bn {:$in #{2 4 6 8}}
                                                   :$refines-to {:ws/C {:cn {:$in [2 8]}}}}

       {:$type :ws/A :an {:$in (set (range 10))}} {:$type :ws/A
                                                   :an {:$in #{1 3 5}}
                                                   :$refines-to {:ws/B {:bn {:$in [2 6]}}
                                                                 :ws/C {:cn {:$in [2 6]}}}}

       {:$type :ws/D} {:$type :ws/D,
                       :a {:$type :ws/A,
                           :an {:$in [1 5]}
                           :$refines-to {:ws/B {:bn {:$in [2 6]}}
                                         :ws/C {:cn {:$in [2 6]}}}},
                       :dm {:$in [2 6]},
                       :dn {:$in [1 5]}}))))

(deftest test-spec-ify-bound-for-primitive-optionals
  (s/without-fn-validation
   (let [specs '{:ws/A
                 {:spec-vars {:an "Integer", :aw [:Maybe "Integer"], :p "Boolean"}
                  :constraints [["a1" (= aw (when p an))]]
                  :refines-to {}}}
         opts {:default-int-bounds [-10 10]}]

     (is (= '{:spec-vars {:an "Integer", :aw [:Maybe "Integer"], :p "Boolean"}
              :constraints [["vars" (valid? {:$type :ws/A :an an :aw aw :p p})]]
              :refines-to {}}
            (pc/spec-ify-bound specs {:$type :ws/A}))))))

(deftest test-propagate-for-primitive-optionals
  (let [senv '{:ws/A
               {:spec-vars {:an "Integer", :aw [:Maybe "Integer"], :p "Boolean"}
                :constraints [["a1" (= aw (when p an))]]
                :refines-to {}}}
        opts {:default-int-bounds [-10 10]}]

    (are [in out]
         (= out (pc/propagate senv opts in))

      {:$type :ws/A}            {:$type :ws/A, :an {:$in [-10 10]}, :aw {:$in [-10 10 :Unset]}, :p {:$in #{false true}}}
      {:$type :ws/A :p true}    {:$type :ws/A :an {:$in [-10 10]} :aw {:$in [-10 10]}, :p true}
      {:$type :ws/A :p false}   {:$type :ws/A :an {:$in [-10 10]} :aw :Unset, :p false}
      {:$type :ws/A :aw :Unset} {:$type :ws/A :an {:$in [-10 10]} :aw :Unset, :p false})))

(deftest test-push-if-value-with-refinement
  (let [senv '{:ws/B
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
                      :refines-to {}}}]
    (is (= {:$type :ws/E, :bw {:$in #{5 :Unset}}, :cw 6, :dx {:$in [-10 10]}}
           (pc/propagate senv {:default-int-bounds [-10 10]} {:$type :ws/E})))))

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
                                :c2 {:$type :ws/C :cx dx :cw 8}}}}}})

(def flatten-vars #'pc/flatten-vars)

(deftest test-flatten-vars-for-nested-optionals
  (are [bound expected]
       (= expected (flatten-vars nested-optionals-spec-env bound))

    {:$type :ws/C}
    {::pc/spec-id :ws/C
     :cx [:cx "Integer"]
     :cw [:cw [:Maybe "Integer"]]
     ::pc/refines-to {}
     ::pc/mandatory #{}}

    {:$type :ws/B}
    {::pc/spec-id :ws/B
     :bx [:bx "Integer"]
     :bw [:bw [:Maybe "Integer"]]
     :bp [:bp "Boolean"]
     :c1 {::pc/spec-id :ws/C
          :cx [:c1|cx "Integer"]
          :cw [:c1|cw [:Maybe "Integer"]]
          ::pc/refines-to {}
          ::pc/mandatory #{}}
     :c2 {::pc/spec-id :ws/C
          :$witness [:c2? "Boolean"]
          :cx [:c2|cx [:Maybe "Integer"]]
          :cw [:c2|cw [:Maybe "Integer"]]
          ::pc/refines-to {}
          ::pc/mandatory #{:c2|cx}}
     ::pc/refines-to {}
     ::pc/mandatory #{}}

    {:$type :ws/A}
    {::pc/spec-id :ws/A
     :b1 {::pc/spec-id :ws/B
          :$witness [:b1? "Boolean"]
          :bx [:b1|bx [:Maybe "Integer"]]
          :bw [:b1|bw [:Maybe "Integer"]]
          :bp [:b1|bp [:Maybe "Boolean"]]
          :c1 {::pc/spec-id :ws/C
               :cx [:b1|c1|cx [:Maybe "Integer"]]
               :cw [:b1|c1|cw [:Maybe "Integer"]]
               ::pc/refines-to {}
               ::pc/mandatory #{:b1|c1|cx}}
          :c2 {::pc/spec-id :ws/C
               :$witness [:b1|c2? "Boolean"]
               :cx [:b1|c2|cx [:Maybe "Integer"]]
               :cw [:b1|c2|cw [:Maybe "Integer"]]
               ::pc/refines-to {}
               ::pc/mandatory #{:b1|c2|cx}}
          ::pc/refines-to {}
          ::pc/mandatory #{:b1|c1|cx :b1|bx :b1|bp}}
     :b2 {::pc/spec-id :ws/B
          :$witness [:b2? "Boolean"]
          :bx [:b2|bx [:Maybe "Integer"]]
          :bw [:b2|bw [:Maybe "Integer"]]
          :bp [:b2|bp [:Maybe "Boolean"]]
          :c1 {::pc/spec-id :ws/C
               :cx [:b2|c1|cx [:Maybe "Integer"]]
               :cw [:b2|c1|cw [:Maybe "Integer"]]
               ::pc/refines-to {}
               ::pc/mandatory #{:b2|c1|cx}}
          :c2 {::pc/spec-id :ws/C
               :$witness [:b2|c2? "Boolean"]
               :cx [:b2|c2|cx [:Maybe "Integer"]]
               :cw [:b2|c2|cw [:Maybe "Integer"]]
               ::pc/refines-to {}
               ::pc/mandatory #{:b2|c2|cx}}
          ::pc/refines-to {}
          ::pc/mandatory #{:b2|c1|cx :b2|bx :b2|bp}}
     :ap [:ap "Boolean"]
     ::pc/refines-to {}
     ::pc/mandatory #{}}

    {:$type :ws/A :b1 {:$type [:Maybe :ws/B]}}
    {::pc/spec-id :ws/A
     :b1 {::pc/spec-id :ws/B
          :$witness [:b1? "Boolean"]
          :bx [:b1|bx [:Maybe "Integer"]]
          :bw [:b1|bw [:Maybe "Integer"]]
          :bp [:b1|bp [:Maybe "Boolean"]]
          :c1 {::pc/spec-id :ws/C
               :cx [:b1|c1|cx [:Maybe "Integer"]]
               :cw [:b1|c1|cw [:Maybe "Integer"]]
               ::pc/refines-to {}
               ::pc/mandatory #{:b1|c1|cx}}
          :c2 {::pc/spec-id :ws/C
               :$witness [:b1|c2? "Boolean"]
               :cx [:b1|c2|cx [:Maybe "Integer"]]
               :cw [:b1|c2|cw [:Maybe "Integer"]]
               ::pc/refines-to {}
               ::pc/mandatory #{:b1|c2|cx}}
          ::pc/refines-to {}
          ::pc/mandatory #{:b1|c1|cx :b1|bx :b1|bp}}
     :b2 {::pc/spec-id :ws/B
          :$witness [:b2? "Boolean"]
          :bx [:b2|bx [:Maybe "Integer"]]
          :bw [:b2|bw [:Maybe "Integer"]]
          :bp [:b2|bp [:Maybe "Boolean"]]
          :c1 {::pc/spec-id :ws/C
               :cx [:b2|c1|cx [:Maybe "Integer"]]
               :cw [:b2|c1|cw [:Maybe "Integer"]]
               ::pc/refines-to {}
               ::pc/mandatory #{:b2|c1|cx}}
          :c2 {::pc/spec-id :ws/C
               :$witness [:b2|c2? "Boolean"]
               :cx [:b2|c2|cx [:Maybe "Integer"]]
               :cw [:b2|c2|cw [:Maybe "Integer"]]
               ::pc/refines-to {}
               ::pc/mandatory #{:b2|c2|cx}}
          ::pc/refines-to {}
          ::pc/mandatory #{:b2|c1|cx :b2|bx :b2|bp}}
     :ap [:ap "Boolean"]
     ::pc/refines-to {}
     ::pc/mandatory #{}}

    {:$type :ws/A :b1 :Unset}
    {::pc/spec-id :ws/A
     :b1 {::pc/spec-id :ws/B
          :$witness [:b1? "Boolean"]
          :bx [:b1|bx [:Maybe "Integer"]]
          :bw [:b1|bw [:Maybe "Integer"]]
          :bp [:b1|bp [:Maybe "Boolean"]]
          :c1 {::pc/spec-id :ws/C
               :cx [:b1|c1|cx [:Maybe "Integer"]]
               :cw [:b1|c1|cw [:Maybe "Integer"]]
               ::pc/refines-to {}
               ::pc/mandatory #{:b1|c1|cx}}
          :c2 {::pc/spec-id :ws/C
               :$witness [:b1|c2? "Boolean"]
               :cx [:b1|c2|cx [:Maybe "Integer"]]
               :cw [:b1|c2|cw [:Maybe "Integer"]]
               ::pc/refines-to {}
               ::pc/mandatory #{:b1|c2|cx}}
          ::pc/refines-to {}
          ::pc/mandatory #{:b1|c1|cx :b1|bx :b1|bp}}
     :b2 {::pc/spec-id :ws/B
          :$witness [:b2? "Boolean"]
          :bx [:b2|bx [:Maybe "Integer"]]
          :bw [:b2|bw [:Maybe "Integer"]]
          :bp [:b2|bp [:Maybe "Boolean"]]
          :c1 {::pc/spec-id :ws/C
               :cx [:b2|c1|cx [:Maybe "Integer"]]
               :cw [:b2|c1|cw [:Maybe "Integer"]]
               ::pc/refines-to {}
               ::pc/mandatory #{:b2|c1|cx}}
          :c2 {::pc/spec-id :ws/C
               :$witness [:b2|c2? "Boolean"]
               :cx [:b2|c2|cx [:Maybe "Integer"]]
               :cw [:b2|c2|cw [:Maybe "Integer"]]
               ::pc/refines-to {}
               ::pc/mandatory #{:b2|c2|cx}}
          ::pc/refines-to {}
          ::pc/mandatory #{:b2|c1|cx :b2|bx :b2|bp}}
     :ap [:ap "Boolean"]
     ::pc/refines-to {}
     ::pc/mandatory #{}}))

(def lower-spec-bound #'pc/lower-spec-bound)

(deftest test-lower-spec-bound-for-nested-optionals
  (s/with-fn-validation
    (are [bound lowered]
         (= lowered (lower-spec-bound (flatten-vars nested-optionals-spec-env bound) bound))

      {:$type :ws/A} {}

      {:$type :ws/A, :ap true} '{:ap true}

      {:$type :ws/A, :b1 {:$type [:Maybe :ws/B]}} {}

      {:$type :ws/A, :b1 {:$type :ws/B}} '{:b1? true}

      {:$type :ws/A, :b1 :Unset} '{:b1? false}

      {:$type :ws/A :b1 {:$type [:Maybe :ws/B] :bx 7}}  '{:b1|bx {:$in #{7 :Unset}}}

      {:$type :ws/A :b1 {:$type :ws/B :bx 7}}   '{:b1? true, :b1|bx 7}

      {:$type :ws/A :b1 {:$type [:Maybe :ws/B] :bx {:$in #{1 2 3}}}} '{:b1|bx {:$in #{1 2 3 :Unset}}}

      {:$type :ws/A :b1 {:$type :ws/B :bx {:$in #{1 2 3}}}} '{:b1? true, :b1|bx {:$in #{1 2 3}}}

      {:$type :ws/A :b1 {:$type [:Maybe :ws/B] :bx {:$in [2 4]}}} '{:b1|bx {:$in [2 4 :Unset]}}

      {:$type :ws/A :b1 {:$type :ws/B :bx {:$in [2 4]}}} '{:b1? true, :b1|bx {:$in [2 4]}}

      {:$type :ws/A :b1 {:$type [:Maybe :ws/B] :c2 {:$type [:Maybe :ws/C] :cw 5}}} '{:b1|c2|cw {:$in #{5 :Unset}}}

      {:$type :ws/A :b1 {:$type :ws/B :c2 {:$type [:Maybe :ws/C] :cw 5}}} '{:b1? true, :b1|c2|cw {:$in #{5 :Unset}}}

      ;; TODO: Ensure that optionality-constraints for this case produces (=> b1? b1|c2?)
      {:$type :ws/A :b1 {:$type [:Maybe :ws/B] :c2 {:$type :ws/C :cw 5}}} '{:b1|c2|cw {:$in #{5 :Unset}}}

      {:$type :ws/A :b1 {:$type :ws/B :c2 {:$type :ws/C :cw 5}}} '{:b1? true, :b1|c2? true, :b1|c2|cw 5})))

(deftest test-lower-spec-bound-and-refines-to
  (s/with-fn-validation
    (let [specs '{:ws/A {:spec-vars {:an "Integer"}
                         :constraints [["a1" (< 0 an)]]
                         :refines-to {}}
                  :ws/B {:spec-vars {:bn "Integer"}
                         :constraints [["b1" (< bn 10)]]
                         :refines-to {:ws/A {:expr {:an bn}}}}
                  :ws/C {:spec-vars {:b [:Maybe :ws/B] :cn "Integer"}
                         :constraints [["c1" (if-value b (= cn (get (refine-to b :ws/A) :an)) true)]]
                         :refines-to {}}}
          bounds {:$type :ws/C :b {:$type [:Maybe :ws/B] :$refines-to {:ws/A {:an 12}}}}
          flattened-vars (flatten-vars specs bounds)]
      (are [in out]
           (= out (lower-spec-bound (flatten-vars specs in) in))

        {:$type :ws/C :b {:$type [:Maybe :ws/B] :$refines-to {:ws/A {:an 12}}}}
        '{:b|>ws$A|an {:$in #{:Unset 12}}}

        {:$type :ws/C :b {:$type [:Maybe :ws/B] :$refines-to {:ws/A {:an {:$in [10 12]}}}}}
        '{:b|>ws$A|an {:$in [10 12 :Unset]}}

        {:$type :ws/C :b {:$type [:Maybe :ws/B] :$refines-to {:ws/A {:an {:$in #{10 11 12}}}}}}
        '{:b|>ws$A|an {:$in #{:Unset 10 11 12}}}))))

(def optionality-constraint #'pc/optionality-constraint)

(deftest test-constraints-for-composite-optional
  (let [specs nested-optionals-spec-env
        flattened-vars (flatten-vars specs {:$type :ws/A})]
    (is (= '(and
             (= b1?
                (if-value b1|bp true false)
                (if-value b1|bx true false)
                (if-value b1|c1|cx true false))
             (=> (if-value b1|bw true false) b1?)
             (=> b1|c2? b1?))
           (optionality-constraint
            specs
            (:b1 flattened-vars))))
    (is (= '(and
             (= b1|c2? (if-value b1|c2|cx true false))
             (=> (if-value b1|c2|cw true false) b1|c2?))
           (optionality-constraint
            specs
            (:c2 (:b1 flattened-vars)))))))

(deftest test-propagate-for-spec-valued-optionals
  (schema.core/without-fn-validation
   (let [opts {:default-int-bounds [-10 10]}]

     (are [in out]
          (= out (pc/propagate nested-optionals-spec-env opts in))

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
       {:$type :ws/D
        :dx {:$in [-9 9]}
        :$refines-to {:ws/B {:bp false,
                             :bw :Unset,
                             :bx {:$in [-8 10]},
                             :c1 {:$type :ws/C, :cw {:$in [-9 9]}, :cx {:$in [-9 9]}},
                             :c2 {:$type :ws/C, :cw 8, :cx {:$in [-9 9]}}}}}

       {:$type :ws/D :dx {:$in (set (range -5 6))}}
       {:$type :ws/D
        :dx {:$in #{-5 -3 -1 1 3 5}}
        :$refines-to {:ws/B {:bp false,
                             :bw :Unset,
                             :bx {:$in [-4 6]},
                             :c1 {:$type :ws/C, :cw {:$in [-5 5]}, :cx {:$in [-5 5]}},
                             :c2 {:$type :ws/C, :cw 8, :cx {:$in [-5 5]}}}}}))))

(deftest simpler-composite-optional-test
  (schema.core/without-fn-validation
   (let [spec-map '{:ws/A
                    {:spec-vars {:b1 :ws/B :b2 [:Maybe :ws/B]}
                     :constraints [["a1" (= b1 b2)]]
                     :refines-to {}}
                    :ws/B
                    {:spec-vars {:bx "Integer", :by [:Maybe "Integer"]}
                     :constraints [["b1" (< 0 bx)]]
                     :refines-to {}}}
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
            (pc/spec-ify-bound spec-map {:$type :ws/A})))

     (is (= '{:$type :ws/A,
              :b1 {:$type :ws/B, :bx {:$in [1 1000]}, :by {:$in [-1000 1000 :Unset]}},
              :b2 {:$type :ws/B, :bx {:$in [1 1000]}, :by {:$in [-1000 1000 :Unset]}}}
            (pc/propagate spec-map {:$type :ws/A}))))))

(deftest test-refine-optional
  ;; The 'features' that interact here: valid? and instance literals w/ unassigned variables.
  (let [senv '{:my/A {:abstract? true
                      :spec-vars {:a1 [:Maybe "Integer"]
                                  :a2 [:Maybe "Integer"]}
                      :constraints [["a1_pos" (if-value a1 (> a1 0) true)]
                                    ["a2_pos" (if-value a2 (> a2 0) true)]]
                      :refines-to {}}
               :my/B {:abstract? false
                      :spec-vars {:b "Integer"}
                      :constraints []
                      :refines-to {:my/A {:expr {:$type :my/A, :a1 b}}}}}]
    (is (= {:$type :my/B
            :b {:$in [1 100]}
            :$refines-to {:my/A {:a1 {:$in [1 100]}, :a2 :Unset}}}
           (pc/propagate senv {:$type :my/B :b {:$in [-100 100]}})))))

(deftest test-propagate-with-errors
  (let [senv '{:ws/A {:spec-vars {:an "Integer" :ap [:Maybe "Boolean"]}
                      :refines-to {}
                      :constraints [["a1" (if (< an 10)
                                            (if (< an 1)
                                              (error "an is too small")
                                              (if-value ap ap false))
                                            (= ap (when (< an 20)
                                                    (error "not big enough"))))]]}}]
    (are [in out]
         (= out (pc/propagate senv in))

      {:$type :ws/A} {:$type :ws/A, :an {:$in [-1000 1000]}, :ap {:$in #{true :Unset}}}
      {:$type :ws/A :an {:$in (set (range 7 22))}} {:$type :ws/A,:an {:$in #{7 8 9 20 21}},:ap {:$in #{true :Unset}}}
      {:$type :ws/A :an 21} {:$type :ws/A :an 21 :ap :Unset}
      {:$type :ws/A :an {:$in (set (range 7 22))} :ap :Unset} {:$type :ws/A :an {:$in #{20 21}} :ap :Unset})))

(defn spec-env [env-map]
  (update-vals env-map #(merge {:spec-vars {}, :constraints [], :refines-to {}} %)))

(deftest test-basic-refines-to-bounds
  (let [senv '{:my/A {:spec-vars {:a1 "Integer"}
                      :constraints [["a1_pos" (< 0 a1)]]}
               :my/B {:spec-vars {:b1 "Integer"}
                      :refines-to {:my/A {:expr {:$type :my/A, :a1 (+ 10 b1)}}}}
               :my/C {:spec-vars {:cb :my/B}}}]

    (testing "Refinement bounds can be given and will influence resulting bound"
      (is (= {:$type :my/C,
              :cb {:$type :my/B,
                   :b1 -5
                   :$refines-to {:my/A {:a1 5}}}}
             (pc/propagate senv {:$type :my/C,
                                 :cb {:$type :my/B
                                      :$refines-to {:my/A {:a1 5}}}}))))

    (testing "Refinement bounds are generated even when not given."
      (is (= {:$type :my/C,
              :cb {:$type :my/B
                   :b1 {:$in [-9 990]}
                   :$refines-to {:my/A {:a1 {:$in [1 1000]}}}}}
             (pc/propagate senv {:$type :my/C}))))

    (testing "Refinement bounds at top level of composition"
      (is (= {:$type :my/B
              :b1 -7
              :$refines-to {:my/A {:a1 3}}}
             (pc/propagate senv {:$type :my/B
                                 :$refines-to {:my/A {:a1 3}}}))))))

(deftest test-refines-to-bounds-with-optionals
  (let [env-map '{:my/A {:spec-vars {:a1 "Integer", :a2 [:Maybe "Integer"]}
                         :constraints [["a1_pos" (< 0 a1)]]}
                  :my/B {:spec-vars {:b1 "Integer"}
                         :refines-to {:my/A {:expr {:$type :my/A,
                                                    :a1 (+ 10 b1),
                                                    :a2 (when (< 5 b1) (+ 2 b1))}}}}
                  :my/C {:spec-vars {:cb [:Maybe :my/B]}}}
        senv (spec-env env-map)]

    (testing "basic optionals"
      (is (= {:$type :my/B
              :b1 {:$in [-9 990]}
              :$refines-to {:my/A {:a1 {:$in [1 1000]}
                                   :a2 {:$in [8 992 :Unset]}}}}
             (pc/propagate senv {:$type :my/B})))

      (is (= {:$type :my/B,
              :b1 -5
              :$refines-to {:my/A {:a1 5, :a2 :Unset}}}
             (pc/propagate senv {:$type :my/B
                                 :$refines-to {:my/A {:a1 5}}})))

      (is (= {:$type :my/B,
              :b1 {:$in [-9 5]}
              :$refines-to {:my/A {:a1 {:$in [1 15]},
                                   :a2 :Unset}}}
             (pc/propagate senv {:$type :my/B
                                 :$refines-to {:my/A {:a2 :Unset}}})))

      (is (= {:$type :my/C,
              :cb {:$type [:Maybe :my/B],
                   :b1 {:$in [-9 990]},
                   :$refines-to {:my/A {:a1 {:$in [1 1000]},
                                        :a2 {:$in [-1000 1000 :Unset]}}}}}
             (pc/propagate senv {:$type :my/C}))))

    (testing "transitive refinement bounds"
      (let [senv (spec-env (merge env-map
                                  '{:my/D {:spec-vars {:d1 "Integer"}
                                           :refines-to {:my/B {:expr {:$type :my/B,
                                                                      :b1 (* 2 d1)}}}}}))]

        (is (= {:$type :my/D,
                :d1 {:$in [-4 495]},
                :$refines-to {:my/B {:b1 {:$in [-8 990]}},
                              :my/A {:a1 {:$in [2 1000]},
                                     :a2 {:$in [8 992 :Unset]}}}}
               (pc/propagate senv {:$type :my/D})))

        (is (= {:$type :my/D,
                :d1 {:$in [3 6]},
                :$refines-to {:my/B {:b1 {:$in [6 12]}},
                              :my/A {:a1 {:$in [16 22]},
                                     :a2 {:$in [8 14]}}}}
               (pc/propagate senv {:$type :my/D,
                                   :$refines-to {:my/A {:a2 {:$in [-5 15]}}}})))

        (is (= {:$type :my/D,
                :d1 {:$in [3 6]},
                :$refines-to {:my/B {:b1 {:$in [6 12]}},
                              :my/A {:a1 {:$in [16 22]},
                                     :a2 {:$in [8 14]}}}}
               (pc/propagate senv {:$type :my/D,
                                   :$refines-to {:my/A {:a2 {:$in [-5 15]}}
                                                 :my/B {:b1 {:$in [-5 15]}}}})))))

    (testing "nested refinement bounds"
      (let [senv (spec-env (merge env-map
                                  '{:my/D {:spec-vars {:d1 "Integer"}
                                           :refines-to {:my/C {:expr {:$type :my/C,
                                                                      :cb {:$type :my/B
                                                                           :b1 (* d1 2)}}}}}}))]

        (is (= {:$type :my/D,
                :d1 {:$in [-4 495]},
                :$refines-to {:my/C {:cb {:$type :my/B,
                                          :b1 {:$in [-8 990]},
                                          :$refines-to {:my/A {:a1 {:$in [2 1000]},
                                                               :a2 {:$in [8 992 :Unset]}}}}}}}
               (pc/propagate senv {:$type :my/D})))

        (is (= {:$type :my/D,
                :d1 3,
                :$refines-to {:my/C {:cb {:$type :my/B,
                                          :b1 6,
                                          :$refines-to {:my/A {:a1 16,
                                                               :a2 8}}}}}}
               (pc/propagate senv {:$type :my/D,
                                   :$refines-to {:my/C {:cb {:$type :my/B,
                                                             :$refines-to {:my/A {:a2 {:$in [-9 9]}}}}}}})))))))

(deftest test-maybe-refines-to-bounds-tight
  (let [env-map '{:my/A {:spec-vars {:ab [:Maybe :my/B]}}
                  :my/B {:refines-to {:my/C {:expr {:$type :my/C
                                                    :cn 5}}}}
                  :my/C {:spec-vars {:cn "Integer"}}}]

    (is (= '{:spec-vars {:ab|>my$C|cn [:Maybe "Integer"], :ab? "Boolean"},
             :constraints
             [["vars" (valid? {:$type :my/A, :ab (when ab? {:$type :my/B})})]
              ["$refine|ab-to-:my/C"
               (let [$from (when ab? {:$type :my/B})]
                 (if-value $from
                           (= (refine-to $from :my/C)
                              (when ab?
                                (if-value ab|>my$C|cn
                                          {:$type :my/C, :cn ab|>my$C|cn}
                                          $no-value)))
                           true))]
              ["$refines:ab?=:ab|>my$C|cn?"
               (= ab? (if-value ab|>my$C|cn true false))]],
             :refines-to {}}
           (pc/spec-ify-bound env-map {:$type :my/A})))

    (is (= {:$type :my/A,
            :ab {:$type [:Maybe :my/B], :$refines-to #:my{:C {:cn 5}}}}
           (pc/propagate env-map {:$type :my/A})))))

(deftest test-refines-to-bounds-errors
  (let [senv (spec-env
              '{:my/A {:spec-vars {:a1 "Integer"}
                       :constraints [["a1_pos" (< 0 a1)]]}
                :my/B {:spec-vars {:b1 "Integer"}
                       :refines-to {:my/A {:expr {:$type :my/A, :a1 (+ 10 b1)}}}}
                :my/C {:spec-vars {:c1 "Integer"}
                       :refines-to {:my/B {:expr {:$type :my/B, :b1 (+ 5 c1)}}}}})]

    (testing "transitive refinements can be listed directly"
      (is (= {:$type :my/C,
              :c1 -10,
              :$refines-to {:my/B {:b1 -5},
                            :my/A {:a1 5}}}
             (pc/propagate senv {:$type :my/C
                                 :$refines-to {:my/B {:b1 {:$in [-20 50]}}
                                               :my/A {:a1 5}}}))))

    (testing "transitive refinements cannot be nested"
      (is (thrown? Exception
                   (pc/propagate senv {:$type :my/C
                                       :$refines-to {:my/B {:b1 {:$in [20 50]}
                                                            :$refines-to {:my/A {:a1 5}}}}}))))

    (testing "disallow refinement bound on non-existant refinement path"
      (is (thrown? Exception
                   (pc/propagate senv {:$type :my/A
                                       :$refines-to {:my/C {:c1 {:$in [20 50]}}}}))))))

(deftest test-semantics-preserving-instance-elimination
  (let [senv '{:ws/A {:spec-vars {:an "Integer"}
                      :refines-to {}
                      :constraints [["a1" (let [b {:$type :ws/B :bm (div 100 an) :bn an}]
                                            (and (< -5 an) (< an 5)))]]}
               :ws/B {:spec-vars {:bm "Integer" :bn "Integer"}
                      :refines-to {}
                      :constraints [["b1" (and (<= -3 bn) (<= bn 3))]]}}]
    (is (= {:$type :ws/A :an {:$in #{-3 -2 -1 1 2 3}}}
           (pc/propagate senv {:$type :ws/A :an {:$in (set (range -5 6))}})))))

(deftest test-short-circuiting-ifs
  (let [senv '{:ws/A {:spec-vars {:an "Integer" :ap "Boolean"}
                      :refines-to {}
                      :constraints [["a1" (let [b (when ap {:$type :ws/B :bn an})]
                                            (and (<= -3 an) (<= an 3)))]]}
               :ws/B {:spec-vars {:bn "Integer"}
                      :refines-to {}
                      :constraints [["b1" (< (div 20 bn) 20)]]}}]

    (is (= {:$type :ws/A
            :an {:$in (set (range -3 4))}
            :ap {:$in #{true false}}}
           (pc/propagate senv {:$type :ws/A :an {:$in (set (range -5 6))}})))

    (is (= {:$type :ws/A
            :an {:$in (disj (set (range -3 4)) 0)}
            :ap true}
           (pc/propagate senv {:$type :ws/A :an {:$in (set (range -5 6))} :ap true})))

    (is (= {:$type :ws/A
            :an 0
            :ap false}
           (pc/propagate senv {:$type :ws/A :an 0})))))

(deftest test-instance-if-comparison
  (let [senv '{:ws/A {:spec-vars {:an "Integer"} :constraints [] :refines-to {}}
               :ws/B {:spec-vars {:a :ws/A :bn "Integer" :p "Boolean"} :refines-to {}
                      :constraints [["b1" (= a (if p {:$type :ws/A :an bn} {:$type :ws/A :an (+ bn 1)}))]]}}]
    (is (= '{:$type :ws/B
             :a {:$type :ws/A :an 3}
             :p {:$in #{true false}}
             :bn {:$in [-10 10]}}
           (pc/propagate senv {:$type :ws/B
                               :a {:$type :ws/A :an 3}
                               :bn {:$in [-10 10]}})))))

(deftest test-abstract-type-instance-comparisons
  (let [senv '{:ws/A {:spec-vars {:an "Integer"} :constraints [] :refines-to {}}
               :ws/B {:spec-vars {:bn "Integer"} :constraints [] :refines-to {}}
               :ws/C {:spec-vars {:cn "Integer"} :constarints [] :refines-to {}}
               :ws/D {:spec-vars {:a :ws/A :b :ws/B :p "Boolean"}
                      :refines-to {}
                      :constraints [["d1" (= a (if p a b))]]}}]
    (is (= '{:$type :ws/D :p true
             :a {:$type :ws/A :an {:$in [-1000 1000]}}
             :b {:$type :ws/B :bn {:$in [-1000 1000]}}}
           (pc/propagate senv {:$type :ws/D})))))

(deftest test-union-bounds
  (are [a b result]
       (= result (pc/union-bounds a b))

    :Unset :Unset :Unset

    ;; integer bounds
    1 1 1
    1 2 {:$in #{1 2}}
    1 :Unset {:$in #{1 :Unset}}
    :Unset 1 {:$in #{1 :Unset}}
    {:$in #{1 2}} 3 {:$in #{1 2 3}}
    {:$in #{1 2}} :Unset {:$in #{1 2 :Unset}}
    3 {:$in #{1 2}} {:$in #{1 2 3}}
    {:$in #{1 2}} {:$in #{2 3}} {:$in #{1 2 3}}

    {:$in [1 3]} 5 {:$in [1 5]}
    {:$in [1 3]} -4 {:$in [-4 3]}
    {:$in [1 3]} 2 {:$in [1 3]}
    {:$in [1 3]} :Unset {:$in [1 3 :Unset]}

    5 {:$in [1 3]} {:$in [1 5]}
    5 {:$in [1 3 :Unset]} {:$in [1 5 :Unset]}

    {:$in #{0 1 2}} {:$in [1 3]} {:$in [0 3]}
    {:$in [1 3]} {:$in #{0 1 2}} {:$in [0 3]}
    {:$in #{0 1 2 :Unset}} {:$in [1 3]} {:$in [0 3 :Unset]}
    {:$in [1 3 :Unset]} {:$in #{0 1 2}} {:$in [0 3 :Unset]}

    ;; boolean bounds
    true true true
    false false false
    :Unset true {:$in #{:Unset true}}
    true false {:$in #{true false}}
    {:$in #{true false}} true {:$in #{true false}}
    true {:$in #{true false}} {:$in #{true false}}
    :Unset {:$in #{true false}} {:$in #{true false :Unset}}
    true {:$in #{true false :Unset}} {:$in #{true false :Unset}}

    ;; spec-bounds
    {:$type :ws/A :a 1 :b true} {:$type :ws/A :a 2 :c {:$in [1 3]}}
    {:$type :ws/A :a {:$in #{1 2}} :b true :c {:$in [1 3]}}

    {:$type :ws/A :$refines-to {:ws/B {:n 1} :ws/D {:d {:$in [1 2]}}}}
    {:$type :ws/A :$refines-to {:ws/B {:n 2} :ws/C {:c true}}}
    {:$type :ws/A :$refines-to {:ws/B {:n {:$in #{1 2}}}
                                :ws/C {:c true}
                                :ws/D {:d {:$in [1 2]}}}}

    {:$type :ws/A} {:$type [:Maybe :ws/A]} {:$type [:Maybe :ws/A]}
    {:$type [:Maybe :ws/A]} {:$type :ws/A} {:$type [:Maybe :ws/A]}
    :Unset {:$type :ws/A} {:$type [:Maybe :ws/A]}))
