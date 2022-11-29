;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.test-prop-composition
  (:require [com.viasat.halite.choco-clj-opt :as choco-clj]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.propagate.prop-composition :as pc]
            [com.viasat.halite.transpile.lowering :as lowering]
            [com.viasat.halite.transpile.rewriting :as rewriting :refer [with-summarized-trace-for]]
            [com.viasat.halite.transpile.simplify :as simplify]
            [com.viasat.halite.transpile.ssa :as ssa]
            [com.viasat.halite.transpile.util :refer [fixpoint]]
            [schema.core :as s]
            [schema.test])
  (:use clojure.test))

;; Prismatic schema validation is too slow to leave on by default for these tests.
;; If you're debugging a test failure, and the problem is a 'type' error,
;; turning schema validation on is likely to help you track it down.
;; (use-fixtures :once schema.test/validate-schemas)

(deftest test-spec-ify-bound-on-simple-spec
  (let [specs (ssa/spec-map-to-ssa
               '{:ws/A
                 {:fields {:x :Integer, :y :Integer, :b :Boolean}
                  :constraints [["c1" (let [delta (abs (- x y))]
                                        (and (< 5 delta)
                                             (< delta 10)))]
                                ["c2" (= b (< x y))]]}})]

    (is (= '{:fields {:x :Integer, :y :Integer, :b :Boolean}
             :constraints [["vars" (valid? {:$type :ws/A :x x :y y :b b})]]}
           (pc/spec-ify-bound specs {:$type :ws/A})))))

(deftest test-spec-ify-bound-with-composite-specs
  (testing "simple composition"
    (let [specs (ssa/spec-map-to-ssa
                 '{:ws/A {:fields {:x :Integer, :y :Integer}
                          :constraints [["pos" (and (< 0 x) (< 0 y))]
                                        ["boundedSum" (< (+ x y) 20)]]}
                   :ws/B {:fields {:a1 [:Instance :ws/A] :a2 [:Instance :ws/A]}
                          :constraints [["a1smaller" (and (< (get a1 :x) (get a2 :x))
                                                          (< (get a1 :y) (get a2 :y)))]]}})]
      (is (= '{:fields {:a1|x :Integer, :a1|y :Integer, :a2|x :Integer, :a2|y :Integer}
               :constraints [["vars" (valid? {:$type :ws/B
                                              :a1 {:$type :ws/A :x a1|x :y a1|y}
                                              :a2 {:$type :ws/A :x a2|x :y a2|y}})]]}
             (pc/spec-ify-bound specs {:$type :ws/B}))))))

(deftest test-propagation-of-trivial-spec
  (let [specs (ssa/spec-map-to-ssa
               {:ws/A {:fields {:x :Integer, :y :Integer, :oddSum :Boolean}
                       :constraints '[["pos" (and (< 0 x) (< 0 y))]
                                      ["y_is_greater" (< x y)]
                                      ["lines" (let [xysum (+ x y)]
                                                 (or (= 42 xysum)
                                                     (= 24 (+ 42 (- 0 xysum)))))]
                                      ["oddSum" (= oddSum (= 1 (mod (+ x y) 2)))]]}})
        opts {:default-int-bounds [-100 100]}]
    (are [in out]
         (= out (pc/propagate specs opts in))

      {:$type :ws/A} {:$type :ws/A, :x {:$in [1 99]}, :y {:$in [2 100]}, :oddSum {:$in #{true false}}}

      {:$type :ws/A, :x 8} {:$type :ws/A, :x 8, :y {:$in [9 100]}, :oddSum {:$in #{true false}}}

      {:$type :ws/A, :x 8, :oddSum false} {:$type :ws/A, :x 8, :y {:$in [10 100]}, :oddSum false}

      {:$type :ws/A, :x 10} {:$type :ws/A, :x 10, :y 32, :oddSum false})))

(deftest test-one-to-one-composition
  (let [specs (ssa/spec-map-to-ssa
               '{:ws/A {:fields {:x :Integer, :y :Integer}
                        :constraints [["pos" (and (< 0 x) (< 0 y))]
                                      ["boundedSum" (< (+ x y) 20)]]}
                 :ws/B {:fields {:a1 [:Instance :ws/A] :a2 [:Instance :ws/A]}
                        :constraints [["a1smaller" (and (< (get a1 :x) (get a2 :x))
                                                        (< (get a1 :y) (get a2 :y)))]]}})
        opts {:default-int-bounds [-100 100]}]

    (are [in out]
         (= out (pc/propagate specs opts in))

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
  (let [specs (ssa/spec-map-to-ssa
               '{:ws/A
                 {:fields {:ax :Integer}
                  :constraints [["c1" (and (< 0 ax) (< ax 10))]]}

                 :ws/B
                 {:fields {:by :Integer, :bz :Integer}
                  :constraints [["c2" (let [a {:$type :ws/A :ax (* 2 by)}
                                            x (get {:$type :ws/A :ax (+ bz bz)} :ax)]
                                        (= x (get {:$type :ws/C :cx (get a :ax)} :cx)))]]}

                 :ws/C
                 {:fields {:cx :Integer}
                  :constraints [["c3" (= cx (get {:$type :ws/A :ax cx} :ax))]]}})
        opts {:default-int-bounds [-100 100]}]
    (are [in out]
         (= out (pc/propagate specs opts in))

      {:$type :ws/C} {:$type :ws/C :cx {:$in [1 9]}}
      {:$type :ws/B} {:$type :ws/B :by {:$in [1 4]} :bz {:$in [1 4]}}
      {:$type :ws/B :by 2} {:$type :ws/B :by 2 :bz 2})))

(deftest test-spec-ify-bound-for-primitive-optionals
  (let [specs '{:ws/A
                {:fields {:an :Integer, :aw [:Maybe :Integer], :p :Boolean}
                 :constraints [["a1" (= aw (when p an))]]}}
        opts {:default-int-bounds [-10 10]}]

    (is (= '{:fields {:an :Integer, :aw [:Maybe :Integer], :p :Boolean}
             :constraints [["vars" (valid? {:$type :ws/A :an an :aw aw :p p})]]}
           (pc/spec-ify-bound specs {:$type :ws/A})))))

(deftest test-propagate-for-primitive-optionals
  (let [specs (ssa/spec-map-to-ssa
               '{:ws/A
                 {:fields {:an :Integer, :aw [:Maybe :Integer], :p :Boolean}
                  :constraints [["a1" (= aw (when p an))]]}})
        opts {:default-int-bounds [-10 10]}]

    (are [in out]
         (= out (pc/propagate specs opts in))

      {:$type :ws/A}            {:$type :ws/A, :an {:$in [-10 10]}, :aw {:$in [-10 10 :Unset]}, :p {:$in #{false true}}}
      {:$type :ws/A :p true}    {:$type :ws/A :an {:$in [-10 10]} :aw {:$in [-10 10]}, :p true}
      {:$type :ws/A :p false}   {:$type :ws/A :an {:$in [-10 10]} :aw :Unset, :p false}
      {:$type :ws/A :aw :Unset} {:$type :ws/A :an {:$in [-10 10]} :aw :Unset, :p false})))

(def nested-optionals-spec-env
  '{:ws/A {:fields {:b1 [:Maybe [:Instance :ws/B]], :b2 [:Maybe [:Instance :ws/B]], :ap :Boolean}
           :constraints [["a1" (= b1 b2)]
                         ["a2" (=> ap (if-value b1 true false))]]}
    :ws/B {:fields {:bx :Integer, :bw [:Maybe :Integer], :bp :Boolean
                    :c1 [:Instance :ws/C]
                    :c2 [:Maybe [:Instance :ws/C]]}
           :constraints [["b1" (= bw (when bp bx))]
                         ["b2" (< bx 15)]]}
    :ws/C {:fields {:cx :Integer
                    :cw [:Maybe :Integer]}}
    :ws/D {:fields {:dx :Integer}}})

(def flatten-vars #'pc/flatten-vars)

(deftest test-flatten-vars-for-nested-optionals
  (let [sctx (ssa/spec-map-to-ssa nested-optionals-spec-env)]
    (are [bound expected]
         (= expected (flatten-vars sctx bound))

      {:$type :ws/C}
      {::pc/spec-id :ws/C
       :cx [:cx :Integer]
       :cw [:cw [:Maybe :Integer]]
       ::pc/mandatory #{}}

      {:$type :ws/B}
      {::pc/spec-id :ws/B
       :bx [:bx :Integer]
       :bw [:bw [:Maybe :Integer]]
       :bp [:bp :Boolean]
       :c1 {::pc/spec-id :ws/C
            :cx [:c1|cx :Integer]
            :cw [:c1|cw [:Maybe :Integer]]
            ::pc/mandatory #{}}
       :c2 {::pc/spec-id :ws/C
            :$witness [:c2? :Boolean]
            :cx [:c2|cx [:Maybe :Integer]]
            :cw [:c2|cw [:Maybe :Integer]]
            ::pc/mandatory #{:c2|cx}}
       ::pc/mandatory #{}}

      {:$type :ws/A}
      {::pc/spec-id :ws/A
       :b1 {::pc/spec-id :ws/B
            :$witness [:b1? :Boolean]
            :bx [:b1|bx [:Maybe :Integer]]
            :bw [:b1|bw [:Maybe :Integer]]
            :bp [:b1|bp [:Maybe :Boolean]]
            :c1 {::pc/spec-id :ws/C
                 :cx [:b1|c1|cx [:Maybe :Integer]]
                 :cw [:b1|c1|cw [:Maybe :Integer]]
                 ::pc/mandatory #{:b1|c1|cx}}
            :c2 {::pc/spec-id :ws/C
                 :$witness [:b1|c2? :Boolean]
                 :cx [:b1|c2|cx [:Maybe :Integer]]
                 :cw [:b1|c2|cw [:Maybe :Integer]]
                 ::pc/mandatory #{:b1|c2|cx}}
            ::pc/mandatory #{:b1|c1|cx :b1|bx :b1|bp}}
       :b2 {::pc/spec-id :ws/B
            :$witness [:b2? :Boolean]
            :bx [:b2|bx [:Maybe :Integer]]
            :bw [:b2|bw [:Maybe :Integer]]
            :bp [:b2|bp [:Maybe :Boolean]]
            :c1 {::pc/spec-id :ws/C
                 :cx [:b2|c1|cx [:Maybe :Integer]]
                 :cw [:b2|c1|cw [:Maybe :Integer]]
                 ::pc/mandatory #{:b2|c1|cx}}
            :c2 {::pc/spec-id :ws/C
                 :$witness [:b2|c2? :Boolean]
                 :cx [:b2|c2|cx [:Maybe :Integer]]
                 :cw [:b2|c2|cw [:Maybe :Integer]]
                 ::pc/mandatory #{:b2|c2|cx}}
            ::pc/mandatory #{:b2|c1|cx :b2|bx :b2|bp}}
       :ap [:ap :Boolean]
       ::pc/mandatory #{}}

      {:$type :ws/A :b1 {:$type [:Maybe :ws/B]}}
      {::pc/spec-id :ws/A
       :b1 {::pc/spec-id :ws/B
            :$witness [:b1? :Boolean]
            :bx [:b1|bx [:Maybe :Integer]]
            :bw [:b1|bw [:Maybe :Integer]]
            :bp [:b1|bp [:Maybe :Boolean]]
            :c1 {::pc/spec-id :ws/C
                 :cx [:b1|c1|cx [:Maybe :Integer]]
                 :cw [:b1|c1|cw [:Maybe :Integer]]
                 ::pc/mandatory #{:b1|c1|cx}}
            :c2 {::pc/spec-id :ws/C
                 :$witness [:b1|c2? :Boolean]
                 :cx [:b1|c2|cx [:Maybe :Integer]]
                 :cw [:b1|c2|cw [:Maybe :Integer]]
                 ::pc/mandatory #{:b1|c2|cx}}
            ::pc/mandatory #{:b1|c1|cx :b1|bx :b1|bp}}
       :b2 {::pc/spec-id :ws/B
            :$witness [:b2? :Boolean]
            :bx [:b2|bx [:Maybe :Integer]]
            :bw [:b2|bw [:Maybe :Integer]]
            :bp [:b2|bp [:Maybe :Boolean]]
            :c1 {::pc/spec-id :ws/C
                 :cx [:b2|c1|cx [:Maybe :Integer]]
                 :cw [:b2|c1|cw [:Maybe :Integer]]
                 ::pc/mandatory #{:b2|c1|cx}}
            :c2 {::pc/spec-id :ws/C
                 :$witness [:b2|c2? :Boolean]
                 :cx [:b2|c2|cx [:Maybe :Integer]]
                 :cw [:b2|c2|cw [:Maybe :Integer]]
                 ::pc/mandatory #{:b2|c2|cx}}
            ::pc/mandatory #{:b2|c1|cx :b2|bx :b2|bp}}
       :ap [:ap :Boolean]
       ::pc/mandatory #{}}

      {:$type :ws/A :b1 :Unset}
      {::pc/spec-id :ws/A
       :b1 {::pc/spec-id :ws/B
            :$witness [:b1? :Boolean]
            :bx [:b1|bx [:Maybe :Integer]]
            :bw [:b1|bw [:Maybe :Integer]]
            :bp [:b1|bp [:Maybe :Boolean]]
            :c1 {::pc/spec-id :ws/C
                 :cx [:b1|c1|cx [:Maybe :Integer]]
                 :cw [:b1|c1|cw [:Maybe :Integer]]
                 ::pc/mandatory #{:b1|c1|cx}}
            :c2 {::pc/spec-id :ws/C
                 :$witness [:b1|c2? :Boolean]
                 :cx [:b1|c2|cx [:Maybe :Integer]]
                 :cw [:b1|c2|cw [:Maybe :Integer]]
                 ::pc/mandatory #{:b1|c2|cx}}
            ::pc/mandatory #{:b1|c1|cx :b1|bx :b1|bp}}
       :b2 {::pc/spec-id :ws/B
            :$witness [:b2? :Boolean]
            :bx [:b2|bx [:Maybe :Integer]]
            :bw [:b2|bw [:Maybe :Integer]]
            :bp [:b2|bp [:Maybe :Boolean]]
            :c1 {::pc/spec-id :ws/C
                 :cx [:b2|c1|cx [:Maybe :Integer]]
                 :cw [:b2|c1|cw [:Maybe :Integer]]
                 ::pc/mandatory #{:b2|c1|cx}}
            :c2 {::pc/spec-id :ws/C
                 :$witness [:b2|c2? :Boolean]
                 :cx [:b2|c2|cx [:Maybe :Integer]]
                 :cw [:b2|c2|cw [:Maybe :Integer]]
                 ::pc/mandatory #{:b2|c2|cx}}
            ::pc/mandatory #{:b2|c1|cx :b2|bx :b2|bp}}
       :ap [:ap :Boolean]
       ::pc/mandatory #{}})))

(def lower-spec-bound #'pc/lower-spec-bound)

(deftest test-lower-spec-bound-for-nested-optionals
  (let [sctx (ssa/spec-map-to-ssa nested-optionals-spec-env)]
    (s/with-fn-validation
      (are [bound lowered]
           (= lowered (lower-spec-bound (flatten-vars sctx bound) bound))

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

        {:$type :ws/A :b1 {:$type :ws/B :c2 {:$type :ws/C :cw 5}}} '{:b1? true, :b1|c2? true, :b1|c2|cw 5}))))

(def optionality-constraint #'pc/optionality-constraint)

(deftest test-constraints-for-composite-optional
  (let [specs nested-optionals-spec-env
        sctx (ssa/spec-map-to-ssa specs)
        flattened-vars (flatten-vars sctx {:$type :ws/A})]
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
  (let [opts {:default-int-bounds [-10 10]}
        specs (ssa/spec-map-to-ssa nested-optionals-spec-env)]

    (are [in out]
         (= out (pc/propagate specs opts in))

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
            :c2 {:$type [:Maybe :ws/C], :cw {:$in [-10 10 :Unset]}, :cx {:$in [-10 10]}}}})))

(deftest simpler-composite-optional-test
  (let [specs (ssa/spec-map-to-ssa
               '{:ws/A
                 {:fields {:b1 [:Instance :ws/B] :b2 [:Maybe [:Instance :ws/B]]}
                  :constraints [["a1" (= b1 b2)]]}
                 :ws/B
                 {:fields {:bx :Integer, :by [:Maybe :Integer]}
                  :constraints [["b1" (< 0 bx)]]}})
        opts {:default-int-bounds [-10 10]}]

    (is (= '{:fields {:b1|bx :Integer
                      :b1|by [:Maybe :Integer]
                      :b2? :Boolean
                      :b2|bx [:Maybe :Integer]
                      :b2|by [:Maybe :Integer]}
             :constraints [["vars" (valid?
                                    {:$type :ws/A
                                     :b1 {:$type :ws/B :bx b1|bx :by b1|by}
                                     :b2 (when b2?
                                           (if-value b2|bx
                                                     {:$type :ws/B :bx b2|bx :by b2|by}
                                                     $no-value))})]
                           ["$b2?" (and (= b2? (if-value b2|bx true false))
                                        (=> (if-value b2|by true false) b2?))]]}
           (pc/spec-ify-bound specs {:$type :ws/A})))

    (is (= '{:$type :ws/A,
             :b1 {:$type :ws/B, :bx {:$in [1 1000]}, :by {:$in [-1000 1000 :Unset]}},
             :b2 {:$type :ws/B, :bx {:$in [1 1000]}, :by {:$in [-1000 1000 :Unset]}}}
           (pc/propagate specs {:$type :ws/A})))))

(deftest test-propagate-with-errors
  (let [specs (ssa/spec-map-to-ssa
               '{:ws/A {:fields {:an :Integer :ap [:Maybe :Boolean]}
                        :constraints [["a1" (if (< an 10)
                                              (if (< an 1)
                                                (error "an is too small")
                                                (if-value ap ap false))
                                              (= ap (when (< an 20)
                                                      (error "not big enough"))))]]}})]
    (are [in out]
         (= out (pc/propagate specs in))

      {:$type :ws/A} {:$type :ws/A, :an {:$in [-1000 1000]}, :ap {:$in #{true :Unset}}}
      {:$type :ws/A :an {:$in (set (range 7 22))}} {:$type :ws/A,:an {:$in #{7 8 9 20 21}},:ap {:$in #{true :Unset}}}
      {:$type :ws/A :an 21} {:$type :ws/A :an 21 :ap :Unset}
      {:$type :ws/A :an {:$in (set (range 7 22))} :ap :Unset} {:$type :ws/A :an {:$in #{20 21}} :ap :Unset})))

(defn specs-to-ssa [env-map]
  (-> env-map
      (update-vals #(merge {:fields {}, :constraints []} %))
      ssa/spec-map-to-ssa))

(deftest test-semantics-preserving-instance-elimination
  (let [specs (specs-to-ssa
               '{:ws/A {:fields {:an :Integer}
                        :constraints [["a1" (let [b {:$type :ws/B :bm (div 100 an) :bn an}]
                                              (and (< -5 an) (< an 5)))]]}
                 :ws/B {:fields {:bm :Integer :bn :Integer}
                        :constraints [["b1" (and (<= -3 bn) (<= bn 3))]]}})]
    (is (= {:$type :ws/A :an {:$in #{-3 -2 -1 1 2 3}}}
           (pc/propagate specs {:$type :ws/A :an {:$in (set (range -5 6))}})))))

(deftest test-short-circuiting-ifs
  (let [specs (specs-to-ssa
               '{:ws/A {:fields {:an :Integer :ap :Boolean}
                        :constraints [["a1" (let [b (when ap {:$type :ws/B :bn an})]
                                              (and (<= -3 an) (<= an 3)))]]}
                 :ws/B {:fields {:bn :Integer}
                        :constraints [["b1" (< (div 20 bn) 20)]]}})]

    (is (= {:$type :ws/A
            :an {:$in (set (range -3 4))}
            :ap {:$in #{true false}}}
           (pc/propagate specs {:$type :ws/A :an {:$in (set (range -5 6))}})))

    (is (= {:$type :ws/A
            :an {:$in (disj (set (range -3 4)) 0)}
            :ap true}
           (pc/propagate specs {:$type :ws/A :an {:$in (set (range -5 6))} :ap true})))

    (is (= {:$type :ws/A
            :an 0
            :ap false}
           (pc/propagate specs {:$type :ws/A :an 0})))))

(deftest test-instance-if-comparison
  (let [specs (specs-to-ssa
               '{:ws/A {:fields {:an :Integer} :constraints []}
                 :ws/B {:fields {:a [:Instance :ws/A] :bn :Integer :p :Boolean}
                        :constraints [["b1" (= a (if p {:$type :ws/A :an bn} {:$type :ws/A :an (+ bn 1)}))]]}})]
    (is (= '{:$type :ws/B
             :a {:$type :ws/A :an 3}
             :p {:$in #{true false}}
             :bn {:$in [-10 10]}}
           (pc/propagate specs {:$type :ws/B
                                :a {:$type :ws/A :an 3}
                                :bn {:$in [-10 10]}})))))

(deftest test-abstract-type-instance-comparisons
  (let [specs (specs-to-ssa
               '{:ws/A {:fields {:an :Integer}}
                 :ws/B {:fields {:bn :Integer}}
                 :ws/C {:fields {:cn :Integer}}
                 :ws/D {:fields {:a [:Instance :ws/A] :b [:Instance :ws/B] :p :Boolean}
                        :constraints [["d1" (= a (if p a b))]]}})]
    (is (= '{:$type :ws/D :p true
             :a {:$type :ws/A :an {:$in [-1000 1000]}}
             :b {:$type :ws/B :bn {:$in [-1000 1000]}}}
           (pc/propagate specs {:$type :ws/D})))))

;; (run-tests)
