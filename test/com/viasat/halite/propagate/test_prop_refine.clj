;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.test-prop-refine
  (:require [clojure.pprint :refer [pprint]]
            [clojure.test :refer :all]
            [com.viasat.halite.eval :as eval]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.transpile.ssa :as ssa]
            [com.viasat.halite.transpile.rewriting :as rewriting]
            [com.viasat.halite.propagate.prop-composition :as prop-composition]
            [com.viasat.halite.propagate.prop-refine :as prop-refine]
            [schema.core :as s]))

(def lower-spec-bound #'prop-composition/lower-spec-bound)

(defn prop-twice
  ([sctx bound] (prop-twice sctx prop-refine/default-options bound))
  ([sctx opts bound]
   (let [out1 (prop-refine/propagate sctx opts bound)
         out2 (prop-refine/propagate sctx opts out1)]
     (testing "re-propagate"
       (is (= out2 out1)))
     out1)))

(deftest test-internals
  (is (= {:$type :spec/A,
          :an 5,
          :>spec$B {:$type [:Maybe :spec/B],
                    :>spec$C {:$type [:Maybe :spec/C],
                              :>spec$D {:$type [:Maybe :spec/D],
                                        :dn 7}}}}
         (prop-refine/assoc-in-refn-path {:$type :spec/A, :an 5}
                                         [:spec/A :spec/B :spec/C :spec/D]
                                         {:$type [:Maybe :spec/D], :dn 7})))
  (is (= {:$type :spec/A,
          :an 5,
          :>spec$B {:$type :spec/B,
                    :>spec$C {:$type :spec/C,
                              :>spec$D {:$type :spec/D,
                                        :dn 7}}}}
         (prop-refine/assoc-in-refn-path {:$type :spec/A, :an 5}
                                         [:spec/A :spec/B :spec/C :spec/D]
                                         {:$type :spec/D, :dn 7}))))

(deftest test-basics
  (let [sctx (ssa/spec-map-to-ssa
              '{:my/A {:fields {:a1 :Integer}
                       :constraints [["a1_pos" (< 0 a1)]]}
                :my/B {:fields {:b1 :Integer}
                       :refines-to {:my/A {:expr {:$type :my/A, :a1 (+ 10 b1)}}}}
                :my/C {:fields {:c1 :Integer}
                       :constraints [["refines_B" (refines-to? {:$type :my/B :b1 1} :my/A)]]
                       :refines-to {:my/B {:expr {:$type :my/B, :b1 (+ 5 c1)}}}}
                :my/D {:refines-to {:my/A {:expr {:$type :my/A, :a1 10}}}}})]

    (s/with-fn-validation
      (is (= {:$type :my/C,
              :c1 {:$in [-14 1000]},
              :>my$B {:$type :my/B,
                      :b1 {:$in [-9 9]},
                      :>my$A {:$type :my/A,
                              :a1 {:$in [1 19]}}}}

             (prop-refine/lower-bound
              sctx
              (prop-refine/make-rgraph sctx)
              {:$type :my/C,
               :c1 {:$in [-14 1000]},
               :$refines-to {:my/B {:b1 {:$in [-9 9]}}
                             :my/A {:a1 {:$in [1 19]}}}})))

      (is (= {:$type :my/C,
              :c1 {:$in [-14 1000]},
              :$refines-to {:my/B {:b1 {:$in [-9 9]}}
                            :my/A {:a1 {:$in [1 19]}}}}

             (prop-refine/raise-bound
              sctx
              {:$type :my/C,
               :c1 {:$in [-14 1000]},
               :>my$B {:$type :my/B,
                       :b1 {:$in [-9 9]},
                       :>my$A {:$type :my/A,
                               :a1 {:$in [1 19]}}}})))

      (is (thrown-with-msg? Exception #"No.*refinement path"
                            (prop-refine/lower-bound
                             sctx
                             (prop-refine/make-rgraph sctx)
                             {:$type :my/C,
                              :$refines-to {:my/D {}}}))))

    (is (= {:$type :my/C,
            :c1 {:$in [-14 985]},
            :$refines-to {:my/B {:b1 {:$in [-9 990]}}
                          :my/A {:a1 {:$in [1 1000]}}}}
           (prop-twice sctx {:$type :my/C})))))

(deftest test-basic-refine-to
  (let [sctx (ssa/spec-map-to-ssa
              '{:my/A {:fields {:a1 :Integer}
                       :constraints [["a1_pos" (< 0 a1)]]}
                :my/B {:fields {:b1 :Integer}
                       :refines-to {:my/A {:expr {:$type :my/A, :a1 (+ 10 b1)}}}}
                :my/C {:fields {:c1 :Integer}
                       :refines-to {:my/B {:expr {:$type :my/B, :b1 (+ 5 c1)}}}}
                :my/D {:fields {:a [:Instance :my/A]
                                :c1 :Integer}
                       :constraints [["rto" (= a (refine-to {:$type :my/C
                                                             :c1 c1}
                                                            :my/A))]]}})]
    (is (= '{:my/A {:fields {:a1 :Integer},
                    :constraints [["$all" (< 0 a1)]],
                    :refines-to {}},
             :my/B {:fields {:b1 :Integer, :>my$A [:Instance :my/A]},
                    :constraints [["$all" (= >my$A {:a1 (+ 10 b1),
                                                    :$type :my/A})]],
                    :refines-to {}},
             :my/C {:fields {:c1 :Integer, :>my$B [:Instance :my/B]},
                    :constraints [["$all" (let [v1 (+ 5 c1)]
                                            (= >my$B
                                               {:>my$A {:a1 (+ 10 v1),
                                                        :$type :my/A},
                                                :b1 v1,
                                                :$type :my/B}))]],
                    :refines-to {}},
             :my/D {:fields {:a [:Instance :my/A], :c1 :Integer},
                    :constraints [["$all"
                                   (let [v1 (+ 5 c1)]
                                     (= a
                                        (get
                                         (get
                                          {:$type :my/C,
                                           :>my$B {:$type :my/B, :>my$A {:$type :my/A, :a1 (+ 10 v1)}, :b1 v1},
                                           :c1 c1}
                                          :>my$B)
                                         :>my$A)))]],
                    :refines-to {}}}
           (update-vals (prop-refine/lower-spec-refinements sctx (prop-refine/make-rgraph sctx))
                        ssa/spec-from-ssa)))

    (is (= {:$type :my/D,
            :c1 {:$in [-14 985]}
            :a {:$type :my/A,
                :a1 {:$in [1 1000]}}}
           (prop-twice sctx {:$type :my/D})))))

(deftest test-propagate-and-refinement
  (let [specs (ssa/spec-map-to-ssa
               '{:ws/A
                 {:fields {:an :Integer}
                  :constraints [["a1" (<= an 6)]]
                  :refines-to {:ws/B {:expr {:$type :ws/B :bn (+ 1 an)}}}}

                 :ws/B
                 {:fields {:bn :Integer}
                  :constraints [["b1" (< 0 bn)]]
                  :refines-to {:ws/C {:expr {:$type :ws/C :cn bn}}}}

                 :ws/C
                 {:fields {:cn :Integer}
                  :constraints [["c1" (= 0 (mod cn 2))]]}

                 :ws/D
                 {:fields {:a [:Instance :ws/A] :dm :Integer, :dn :Integer}
                  :constraints [["d1" (= dm (get (refine-to {:$type :ws/A :an dn} :ws/C) :cn))
                                 "d2" (= (+ 1 dn) (get (refine-to a :ws/B) :bn))]]}})]

    (are [in out]
         (= out (prop-twice specs in))

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
                      :dn {:$in [1 5]}})))

(deftest test-refine-to-of-unknown-type
  (let [sctx (ssa/spec-map-to-ssa
              '{:t/A {:fields {:y :Integer}
                      :refines-to {:t/C {:expr {:$type :t/C
                                                :cn (+ y 10)}}}}
                :t/B {:fields {:y :Integer}
                      :refines-to {:t/C {:expr {:$type :t/C
                                                :cn (+ y 20)}}}}
                :t/C {:fields {:cn :Integer}}
                :t/D {:fields {:x :Integer
                               :cn :Integer}
                      :constraints [["the point"
                                     (= cn
                                        (get (refine-to (if (< x 5)
                                                          {:$type :t/A, :y x}
                                                          {:$type :t/B, :y x})
                                                        :t/C)
                                             :cn))]]}})]
    (is (= {:$type :t/D, :x 4, :cn 14}
           (prop-twice sctx {:$type :t/D, :x 4})))

    (is (= {:$type :t/D, :x 6, :cn 26}
           (prop-twice sctx {:$type :t/D, :x 6})))))

(deftest test-optionals-travel-together
  (let [sctx (ssa/spec-map-to-ssa
              '{:ws/A {:fields {:an :Integer}
                       :constraints [["a1" (< 0 an)]]}
                :ws/B {:fields {:bn :Integer}
                       :constraints [["b1" (< bn 10)]]
                       :refines-to {:ws/A {:expr {:$type :ws/A :an bn}}}}
                :ws/C {:fields {:b [:Maybe [:Instance :ws/B]]
                                :cn :Integer}
                       :constraints [["c1" (if-value b (= cn (get (refine-to b :ws/A) :an)) true)]]}
                :ws/D {:fields {:b [:Maybe [:Instance :ws/B]]
                                :cn :Integer}
                       :constraints [["c1" (if-value b (refines-to? b :ws/A) true)]]}})]

    (is (= {:$type :ws/C
            :b :Unset
            :cn {:$in [-1000 1000]}}
           (prop-refine/propagate
            sctx
            {:$type :ws/C
             :b {:$type [:Maybe :ws/B]
                 :$refines-to {:ws/A {:an 12}}}})))

    (is (= {:$type :ws/D
            :b :Unset
            :cn {:$in [-1000 1000]}}
           (prop-refine/propagate
            sctx
            {:$type :ws/D
             :b {:$type [:Maybe :ws/B]
                 :$refines-to {:ws/A {:an 12}}}})))))

(deftest test-lower-bound
  (s/with-fn-validation
    (let [sctx (ssa/spec-map-to-ssa
                '{:ws/A {:fields {:an :Integer}
                         :constraints [["a1" (< 0 an)]]
                         :refines-to {}}
                  :ws/B {:fields {:bn :Integer}
                         :constraints [["b1" (< bn 10)]]
                         :refines-to {:ws/A {:expr {:$type :ws/A :an bn}}}}
                  :ws/C {:fields {:b [:Maybe [:Instance :ws/B]]
                                  :cn :Integer}
                         :constraints [["c1" (if-value b (= cn (get (refine-to b :ws/A) :an)) true)]]
                         :refines-to {}}})]

      (are [in out]
           (= out (prop-refine/lower-bound sctx (prop-refine/make-rgraph sctx) in))

        {:$type :ws/C :b {:$type [:Maybe :ws/B] :$refines-to {:ws/A {:an 12}}}}
        {:$type :ws/C :b {:$type [:Maybe :ws/B] :>ws$A {:$type :ws/A, :an 12}}}

        {:$type :ws/C :b {:$type [:Maybe :ws/B] :$refines-to {:ws/A {:an {:$in [10 12]}}}}}
        {:$type :ws/C :b {:$type [:Maybe :ws/B] :>ws$A {:$type :ws/A :an {:$in [10 12]}}}}

        {:$type :ws/C :b {:$type [:Maybe :ws/B] :$refines-to {:ws/A {:an {:$in #{10 11 12}}}}}}
        {:$type :ws/C :b {:$type [:Maybe :ws/B] :>ws$A {:$type :ws/A :an {:$in #{10 11 12}}}}}

        {:$type :ws/C :b :Unset}
        {:$type :ws/C :b :Unset}))))

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
    :ws/D {:fields {:dx :Integer}
           :refines-to {:ws/B {:expr {:$type :ws/B
                                      :bx (+ dx 1)
                                      :bw (when (= 0 (mod (abs dx) 2))
                                            (div dx 2))
                                      :bp false
                                      :c1 {:$type :ws/C :cx dx :cw dx}
                                      :c2 {:$type :ws/C :cx dx :cw 8}}}}}})

(deftest test-propagate-for-spec-valued-optionals
  (let [opts {:default-int-bounds [-10 10]}
        specs (ssa/spec-map-to-ssa nested-optionals-spec-env)]

    (are [in out]
         (= out (prop-twice specs opts in))

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
                            :c2 {:$type :ws/C, :cw 8, :cx {:$in [-5 5]}}}}})))

(deftest test-refine-optional
  ;; The 'features' that interact here: valid? and instance literals w/ unassigned variables.
  (let [sctx (ssa/spec-map-to-ssa
              '{:my/A {:fields {:a1 [:Maybe :Integer]
                                :a2 [:Maybe :Integer]}
                       :constraints [["a1_pos" (if-value a1 (> a1 0) true)]
                                     ["a2_pos" (if-value a2 (> a2 0) true)]]}
                :my/B {:fields {:b :Integer
                                :ob [:Maybe :Integer]}
                       :refines-to {:my/A {:expr {:$type :my/A,
                                                  :a1 (if-value ob
                                                                (+ b ob)
                                                                b)}}}}
                :my/C {:constraints [["touch B"
                                      (< 3 (get {:$type :my/B, :b 5} :b))]]}})]

    (is (= {:$type :my/B
            :b {:$in [1 100]}
            :ob 0
            :$refines-to {:my/A {:a1 {:$in [1 100]}, :a2 :Unset}}}
           (prop-twice sctx {:$type :my/B :b {:$in [-100 100]} :ob 0})))

    (is (= {:$type :my/C}
           (prop-twice sctx {:$type :my/C})))))

(deftest test-basic-refines-to-bounds
  (let [specs (ssa/spec-map-to-ssa
               '{:my/A {:fields {:a1 :Integer}
                        :constraints [["a1_pos" (< 0 a1)]]}
                 :my/B {:fields {:b1 :Integer}
                        :refines-to {:my/A {:expr {:$type :my/A, :a1 (+ 10 b1)}}}}
                 :my/C {:fields {:cb [:Instance :my/B]}}})]

    (testing "Refinement bounds can be given and will influence resulting bound"
      (is (= {:$type :my/C,
              :cb {:$type :my/B,
                   :b1 -5
                   :$refines-to {:my/A {:a1 5}}}}
             (prop-twice specs {:$type :my/C,
                                :cb {:$type :my/B
                                     :$refines-to {:my/A {:a1 5}}}}))))

    (testing "Refinement bounds are generated even when not given."
      (is (= {:$type :my/C,
              :cb {:$type :my/B
                   :b1 {:$in [-9 990]}
                   :$refines-to {:my/A {:a1 {:$in [1 1000]}}}}}
             (prop-twice specs {:$type :my/C}))))

    (testing "Refinement bounds at top level of composition"
      (is (= {:$type :my/B
              :b1 -7
              :$refines-to {:my/A {:a1 3}}}
             (prop-twice specs {:$type :my/B
                                :$refines-to {:my/A {:a1 3}}}))))))

(deftest test-refinements-with-optionals
  (let [specs '{:my/A {:fields {:a1 :Integer, :a2 [:Maybe :Integer]}
                       :constraints [["a1_pos" (< 0 a1)]]}
                :my/B {:fields {:b1 :Integer}
                       :refines-to {:my/A {:expr {:$type :my/A,
                                                  :a1 (+ 10 b1),
                                                  :a2 (when (< 5 b1) (+ 2 b1))}}}}
                :my/C {:fields {:cb [:Maybe [:Instance :my/B]]}}}
        sctx (ssa/spec-map-to-ssa specs)]

    (is (= {:$type :my/B
            :b1 {:$in [-9 990]}
            :$refines-to {:my/A {:a1 {:$in [1 1000]}
                                 :a2 {:$in [8 992 :Unset]}}}}
           (prop-twice sctx {:$type :my/B})))

    (is (= {:$type :my/B,
            :b1 -5
            :$refines-to {:my/A {:a1 5, :a2 :Unset}}}
           (prop-twice sctx {:$type :my/B
                             :$refines-to {:my/A {:a1 5}}})))

    (is (= {:$type :my/B,
            :b1 {:$in [-9 5]}
            :$refines-to {:my/A {:a1 {:$in [1 15]},
                                 :a2 :Unset}}}
           (prop-twice sctx {:$type :my/B
                             :$refines-to {:my/A {:a2 :Unset}}})))

    (is (= {:$type :my/C,
            :cb {:$type [:Maybe :my/B],
                 :b1 {:$in [-9 990]},
                 :$refines-to {:my/A {:a1 {:$in [1 1000]},
                                      :a2 {:$in [-1000 1000 :Unset]}}}}}
           (prop-twice sctx {:$type :my/C})))))

(deftest test-transitive-refinement-across-optionals
  (let [specs '{:my/A {:fields {:a1 :Integer, :a2 [:Maybe :Integer]}
                       :constraints [["a1_pos" (< 0 a1)]]}
                :my/B {:fields {:b1 :Integer}
                       :refines-to {:my/A {:expr {:$type :my/A,
                                                  :a1 (+ 10 b1),
                                                  :a2 (when (< 5 b1) (+ 2 b1))}}}}
                :my/C {:fields {:cb [:Maybe [:Instance :my/B]]}}
                :my/D {:fields {:d1 :Integer}
                       :refines-to {:my/B {:expr {:$type :my/B,
                                                  :b1 (* 2 d1)}}}}}
        sctx (ssa/spec-map-to-ssa specs)]

    (is (= {:$type :my/D,
            :d1 {:$in [3 6]},
            :$refines-to {:my/B {:b1 {:$in [6 12]}},
                          :my/A {:a1 {:$in [16 22]},
                                 :a2 {:$in [8 14]}}}}
           (rewriting/with-captured-traces
             (prop-twice sctx {:$type :my/D,
                               :$refines-to {:my/A {:a2 {:$in [-5 15]}}}}))))

    (is (= {:$type :my/D,
            :d1 {:$in [-2 7]},
            :$refines-to {:my/B {:b1 {:$in [-4 14]}},
                          :my/A {:a1 {:$in [6 24]},
                                 :a2 {:$in [8 16 :Unset]}}}}
           (prop-twice sctx {:$type :my/D,
                             :$refines-to {:my/A {:a2 {:$in [-5 15]}}
                                           :my/B {:b1 {:$in [-5 15]}}}})))

    #_;; old version produced these tighter bounds:
      {:$type :my/D,
       :d1 {:$in [3 6]},
       :$refines-to {:my/B {:b1 {:$in [6 12]}},
                     :my/A {:a1 {:$in [16 22]},
                            :a2 {:$in [8 14]}}}}
    #_;; check against eval:
      (prn :eval
           (eval/eval-expr* {:senv specs :env (envs/env {})}
                            '(let [d {:$type :my/D, :d1 3}]
                               [d (refine-to d :my/A) (refine-to d :my/B)])))))

(deftest test-nested-refinement-bounds-with-optionals
  (let [specs '{:my/A {:fields {:a1 :Integer, :a2 [:Maybe :Integer]}
                       :constraints [["a1_pos" (< 0 a1)]]}
                :my/B {:fields {:b1 :Integer}
                       :refines-to {:my/A {:expr {:$type :my/A,
                                                  :a1 (+ 10 b1),
                                                  :a2 (when (< 5 b1) (+ 2 b1))}}}}
                :my/C {:fields {:cb [:Maybe [:Instance :my/B]]}}
                :my/D {:fields {:d1 :Integer}
                       :refines-to {:my/C {:expr {:$type :my/C,
                                                  :cb {:$type :my/B
                                                       :b1 (* d1 2)}}}}}}
        sctx (ssa/spec-map-to-ssa specs)]

    (is (= {:$type :my/D,
            :d1 {:$in [-4 495]},
            :$refines-to {:my/C {:cb {:$type :my/B,
                                      :b1 {:$in [-8 990]},
                                      :$refines-to {:my/A {:a1 {:$in [2 1000]},
                                                           :a2 {:$in [8 992 :Unset]}}}}}}}
           (prop-twice sctx {:$type :my/D})))

    (is (= {:$type :my/D,
            :d1 3,
            :$refines-to {:my/C {:cb {:$type :my/B,
                                      :b1 6,
                                      :$refines-to {:my/A {:a1 16,
                                                           :a2 8}}}}}}
           (prop-twice sctx {:$type :my/D,
                             :$refines-to {:my/C {:cb {:$type :my/B,
                                                       :$refines-to {:my/A {:a2 {:$in [-9 9]}}}}}}})))))

(deftest test-maybe-refines-to-bounds-tight
  (let [specs '{:my/A {:fields {:ab [:Maybe [:Instance :my/B]]}}
                :my/B {:refines-to {:my/C {:expr {:$type :my/C
                                                  :cn 5}}}}
                :my/C {:fields {:cn :Integer}}}
        sctx (ssa/spec-map-to-ssa specs)]

    (is (= '{:fields {:ab? :Boolean, :ab|>my$C|cn [:Maybe :Integer]}
             :constraints [["vars"
                            (valid?
                             {:$type :my/A,
                              :ab (when ab?
                                    (if-value ab|>my$C|cn
                                              {:$type :my/B,
                                               :>my$C {:$type :my/C, :cn ab|>my$C|cn}}
                                              $no-value))})]
                           ["$ab?" (= ab? (if-value ab|>my$C|cn true false))]]}
           (prop-composition/spec-ify-bound (prop-refine/lower-spec-refinements sctx (prop-refine/make-rgraph sctx))
                                            {:$type :my/A})))

    (is (= {:$type :my/A,
            :ab {:$type [:Maybe :my/B], :$refines-to #:my{:C {:cn 5}}}}
           (prop-twice sctx {:$type :my/A})))))

(deftest test-optionals
  (rewriting/with-captured-traces
    (let [specs '{:my/A {:fields {:a1 :Integer, :a2 [:Maybe :Integer]}
                         :constraints [["a1_pos" (< 0 a1)]]}
                  :my/B {:fields {:b1 :Integer}
                         :refines-to {:my/A {:expr {:$type :my/A,
                                                    :a1 (+ 10 b1),
                                                    :a2 (when (< 5 b1) (+ 2 b1))}}}}
                  :my/C {:fields {:cb [:Maybe [:Instance :my/B]]}}}
          sctx (ssa/spec-map-to-ssa specs)]

      (is (= {:$type :my/C,
              :cb {:$type [:Maybe :my/B],
                   :b1 {:$in [-9 990]},
                   :$refines-to {:my/A {:a1 {:$in [1 1000]},
                                        :a2 {:$in [-1000 1000 :Unset]}}}}}
             (prop-twice sctx {:$type :my/C})))

      ;; There was once a version of propagate that produced these bounds, which
      ;; also seem valid, though not strictly tighter:
      #_{:$type :my/C,
         :cb {:$type [:Maybe :my/B],
              :b1 {:$in [-1000 1000]},
              :$refines-to {:my/A {:a1 {:$in [1 1000]},
                                   :a2 {:$in [8 992 :Unset]}}}}}
      #_(prn :eval
             (eval/eval-expr* {:senv specs :env (envs/env {})}
                              '(refine-to {:$type :my/B :b1 991} :my/A))))))

(deftest test-refines-to-bounds-errors
  (let [sctx (ssa/spec-map-to-ssa
              '{:my/A {:fields {:a1 :Integer}
                       :constraints [["a1_pos" (< 0 a1)]]}
                :my/B {:fields {:b1 :Integer}
                       :refines-to {:my/A {:expr {:$type :my/A, :a1 (+ 10 b1)}}}}
                :my/C {:fields {:c1 :Integer}
                       :refines-to {:my/B {:expr {:$type :my/B, :b1 (+ 5 c1)}}}}})]

    (testing "transitive refinements can be listed directly"
      (is (= {:$type :my/C,
              :c1 -10,
              :$refines-to {:my/B {:b1 -5},
                            :my/A {:a1 5}}}
             (prop-twice sctx {:$type :my/C
                               :$refines-to {:my/B {:b1 {:$in [-20 50]}}
                                             :my/A {:a1 5}}}))))

    (testing "transitive refinements cannot be nested"
      (is (thrown? Exception
                   (prop-twice sctx {:$type :my/C
                                     :$refines-to {:my/B {:b1 {:$in [20 50]}
                                                          :$refines-to {:my/A {:a1 5}}}}}))))

    (testing "disallow refinement bound on non-existant refinement path"
      (is (thrown? Exception
                   (prop-twice sctx {:$type :my/A
                                     :$refines-to {:my/C {:c1 {:$in [20 50]}}}}))))))

(deftest test-push-if-value-with-refinement
  (let [sctx (ssa/spec-map-to-ssa
              '{:ws/B
                {:fields {:bw [:Maybe :Integer], :c2 [:Maybe [:Instance :ws/C]]}}
                :ws/C
                {:fields {:cw [:Maybe :Integer]}}
                :ws/D
                {:fields {:dx :Integer}
                 :constraints [["a1" (< dx 10)]]
                 :refines-to {:ws/B {:expr {:$type :ws/B
                                            :bw (when (= 0 dx) 5)
                                            :c2 {:$type :ws/C :cw 6}}}}}
                :ws/E {:fields {:dx :Integer,
                                :bw [:Maybe :Integer],
                                :cw [:Maybe :Integer]},
                       :constraints [["$all" (= (refine-to {:dx dx, :$type :ws/D} :ws/B)
                                                {:bw bw,
                                                 :c2 (if-value cw
                                                               {:cw cw, :$type :ws/C}
                                                               $no-value),
                                                 :$type :ws/B})]]}
                :ws/F {:fields {:dx :Integer,
                                :bw [:Maybe :Integer],
                                :cw [:Maybe :Integer]},
                       :constraints [["$all" (and (refines-to? {:dx dx, :$type :ws/D} :ws/B)
                                                  (refines-to? {:bw bw,
                                                                :c2 (if-value cw
                                                                              {:cw cw, :$type :ws/C}
                                                                              $no-value),
                                                                :$type :ws/B}
                                                               :ws/B))]]}})]
    (is (= {:$type :ws/E, :bw {:$in #{5 :Unset}}, :cw 6, :dx {:$in [-10 9]}}
           (prop-twice sctx {:default-int-bounds [-10 10]} {:$type :ws/E})))

    (is (= {:$type :ws/F, :bw {:$in [-10 10 :Unset]}, :cw {:$in [-10 10 :Unset]}, :dx {:$in [-10 9]}}
           (prop-twice sctx {:default-int-bounds [-10 10]} {:$type :ws/F})))))

(deftest test-lower-refine-to
  (let [sctx (ssa/spec-map-to-ssa
              '{:ws/A
                {:fields {:an :Integer}
                 :constraints [["a1" (< an 10)]]
                 :refines-to {:ws/B {:expr {:$type :ws/B :bn (+ 1 an)}}}}
                :ws/B
                {:fields {:bn :Integer}
                 :constraints [["b1" (< 0 bn)]]
                 :refines-to {:ws/C {:expr {:$type :ws/C :cn bn}}}}
                :ws/C
                {:fields {:cn :Integer}
                 :constraints [["c1" (= 0 (mod cn 2))]]}
                :ws/D
                {:fields {:dm :Integer, :dn :Integer}
                 :constraints [["d1" (= dm (get (refine-to {:$type :ws/A :an dn} :ws/C) :cn))]
                               ["d2" (= dn (get (refine-to {:$type :ws/B :bn dn} :ws/B) :bn))]
                               ["d3" (not= 72 (get {:$type :ws/A :an dn} :an))]]}})]
    (is (= '(let [v1 (+ 1 dn)]
              (and (= dm (get (get (get {:$type :ws/A, :>ws$B {:$type :ws/B, :>ws$C {:$type :ws/C, :cn v1}, :bn v1}, :an dn}
                                        :>ws$B)
                                   :>ws$C)
                              :cn))
                   (= dn (get {:>ws$C {:cn dn, :$type :ws/C},
                               :bn dn,
                               :$type :ws/B}
                              :bn))
                   (not= 72 (get {:$type :ws/A,
                                  :an dn
                                  :>ws$B {:$type :ws/B,
                                          :>ws$C {:$type :ws/C, :cn v1},
                                          :bn v1}}
                                 :an))))

           (-> (prop-refine/lower-spec-refinements sctx (prop-refine/make-rgraph sctx))
               :ws/D
               (ssa/spec-from-ssa)
               :constraints first second)))))

(deftest test-guarded-refinement
  (let [sctx (ssa/spec-map-to-ssa
              '{:my/A {:fields {:a1 :Integer}
                       :constraints [["a1_pos" (< 0 a1)]]}
                :my/B {:fields {:b1 :Integer}
                       :refines-to {:my/A {:expr {:$type :my/A, :a1 (+ 10 b1)}}}}
                :my/C {:fields {:c1 :Integer}
                       :refines-to {:my/B {:expr (when (< c1 5)
                                                   {:$type :my/B, :b1 (+ 5 c1)})}}}
                :my/D {:refines-to {:my/A {:expr {:$type :my/A, :a1 10}}}}})
        rgraph (prop-refine/make-rgraph sctx)]

    (s/with-fn-validation
      (is (= '{:my/A {:fields {:a1 :Integer},
                      :constraints [["$all" (< 0 a1)]],
                      :refines-to {}},
               :my/B {:fields {:b1 :Integer, :>my$A [:Instance :my/A]},
                      :constraints [["$all" (= >my$A {:a1 (+ 10 b1), :$type :my/A})]],
                      :refines-to {}},
               :my/C {:fields {:c1 :Integer, :>my$B [:Maybe [:Instance :my/B]]},
                      :constraints [["$all" (= >my$B (if (< c1 5)
                                                       (let [v1 (+ 5 c1)]
                                                         {:$type :my/B
                                                          :b1 v1
                                                          :>my$A {:$type :my/A
                                                                  :a1 (+ 10 v1)}})
                                                       $no-value))]],
                      :refines-to {}},
               :my/D {:constraints [["$all" (= >my$A {:a1 10, :$type :my/A})]],
                      :fields {:>my$A [:Instance :my/A]},
                      :refines-to {}}}
             (update-vals (prop-refine/lower-spec-refinements sctx rgraph) ssa/spec-from-ssa)))

      (is (= {:$type :my/C,
              :c1 {:$in [-14 1000]},
              :>my$B {:$type :my/B,
                      :b1 {:$in [-9 9]},
                      :>my$A {:$type :my/A,
                              :a1 {:$in [1 19]}}}}

             (prop-refine/lower-bound sctx rgraph
                                      {:$type :my/C,
                                       :c1 {:$in [-14 1000]},
                                       :$refines-to {:my/B {:$type [:Maybe :my/B],
                                                            :b1 {:$in [-9 9]}}
                                                     :my/A {:$type :my/A,
                                                            :a1 {:$in [1 19]}}}})))
      (is (= {:$type :my/C, :>my$B :Unset}
             (prop-refine/lower-bound sctx rgraph
                                      {:$type :my/C,
                                       :$refines-to {:my/B :Unset}})))

      (is (thrown-with-msg? Exception #"No.*refinement path"
                            (prop-refine/lower-bound sctx rgraph
                                                     {:$type :my/A,
                                                      :$refines-to {:my/B {:$type :my/B}}}))))

    (is (= {:$type :my/C,
            :c1 {:$in [-14 1000]},
            :$refines-to {:my/A {:$type [:Maybe :my/A],
                                 :a1 {:$in [1 19]}}
                          :my/B {:$type [:Maybe :my/B],
                                 :b1 {:$in [-9 9]}}}}
           (prop-twice sctx {:$type :my/C})))))

(deftest test-refine-to-of-guarded
  (let [sctx (ssa/spec-map-to-ssa
              '{:my/A {:fields {:a1 :Integer}
                       :constraints [["a1_pos" (< 0 a1)]]}
                :my/B {:fields {:b1 :Integer}
                       :refines-to {:my/A {:expr {:$type :my/A, :a1 (+ 10 b1)}}}}
                :my/C {:fields {:c1 :Integer}
                       :refines-to {:my/B {:expr (when (< c1 5)
                                                   {:$type :my/B, :b1 (+ 5 c1)})}}}
                :my/D {:fields {:a [:Instance :my/A]
                                :c1 :Integer}
                       :constraints [["rto" (= a (refine-to {:$type :my/C
                                                             :c1 c1}
                                                            :my/A))]]}})
        rgraph (prop-refine/make-rgraph sctx)]

    (is (= '{:my/A {:fields {:a1 :Integer}
                    :constraints [["$all" (< 0 a1)]],
                    :refines-to {}},
             :my/B {:fields {:b1 :Integer
                             :>my$A [:Instance :my/A]}
                    :constraints [["$all" (= >my$A {:$type :my/A, :a1 (+ 10 b1)})]],
                    :refines-to {}},
             :my/C {:fields {:c1 :Integer
                             :>my$B [:Maybe [:Instance :my/B]]}
                    :constraints
                    [["$all" (= >my$B
                                (if (< c1 5)
                                  (let [v1 (+ 5 c1)]
                                    {:$type :my/B,
                                     :>my$A {:$type :my/A, :a1 (+ 10 v1)},
                                     :b1 v1})
                                  $no-value))]],
                    :refines-to {}},
             :my/D {:fields {:a [:Instance :my/A]
                             :c1 :Integer}
                    :constraints
                    [["$all" (= a
                                (get (let [v1 (get
                                               (let [v1 $no-value]
                                                 {:$type :my/C,
                                                  :c1 c1
                                                  :>my$B (if (< c1 5)
                                                           (let [v2 (+ 5 c1)]
                                                             {:$type :my/B
                                                              :b1 v2
                                                              :>my$A {:$type :my/A,
                                                                      :a1 (+ 10 v2)}})
                                                           v1)})
                                               :>my$B)]
                                       (if-value v1
                                                 v1
                                                 (error "No active refinement path")))
                                     :>my$A))]],
                    :refines-to {}}}
           (update-vals (s/with-fn-validation
                          (prop-refine/lower-spec-refinements sctx rgraph))
                        ssa/spec-from-ssa)))

    (is (= {:$type :my/D,
            :c1 {:$in [-14 4]}
            :a {:$type :my/A,
                :a1 {:$in [1 19]}}}
           (prop-twice sctx {:$type :my/D})))))

(deftest test-guarded-refinement-chain
  (let [specs '{:my/A {:fields {:x :Integer}
                       :refines-to {:my/B {:expr (when (< 7 x) {:$type :my/B :x (div x 2)})}}}
                :my/B {:fields {:x :Integer}
                       :refines-to {:my/C {:expr (when (< 6 x) {:$type :my/C :x (div x 2)})}}}
                :my/C {:fields {:x :Integer}
                       :refines-to {:my/D {:expr (when (< 5 x) {:$type :my/D :x (div x 2)})}}}
                :my/D {:fields {:x :Integer}}}
        sctx (ssa/spec-map-to-ssa specs)]

    (s/with-fn-validation
      (is (= {:$type :my/A, :>my$B {:$type [:Maybe :my/B], :>my$C :Unset}}
             (prop-refine/lower-bound sctx (prop-refine/make-rgraph sctx)
                                      {:$type :my/A
                                       :$refines-to {:my/C :Unset}})))
      (is (thrown-with-msg? Exception #"conflict.*my/C"
                            (prop-refine/lower-bound sctx (prop-refine/make-rgraph sctx)
                                                     {:$type :my/A
                                                      :$refines-to {:my/D {}
                                                                    :my/C :Unset}})))
      (is (= {:$type :my/A, :>my$B {:$type [:Maybe :my/B], :>my$C :Unset}}
             (prop-refine/lower-bound sctx (prop-refine/make-rgraph sctx)
                                      {:$type :my/A
                                       :$refines-to {:my/D :Unset
                                                     :my/C :Unset}})))
      (is (thrown-with-msg? Exception #"conflict.*my/C"
                            (prop-refine/lower-bound sctx (prop-refine/make-rgraph sctx)
                                                     {:$type :my/A
                                                      :$refines-to {:my/C :Unset
                                                                    :my/D {}}})))
      (is (= {:$type :my/A, :>my$B {:$type [:Maybe :my/B], :>my$C :Unset}}
             (prop-refine/lower-bound sctx (prop-refine/make-rgraph sctx)
                                      {:$type :my/A
                                       :$refines-to {:my/C :Unset
                                                     :my/D :Unset}}))))

    (is (= {:$type :my/A,
            :x {:$in [-1000 1000]},
            :$refines-to #:my{:B {:$type [:Maybe :my/B], :x {:$in [3 500]}},
                              :C {:$type [:Maybe :my/C], :x {:$in [3 250]}},
                              :D {:$type [:Maybe :my/D], :x {:$in [2 125]}}}}
           (prop-twice sctx {:$type :my/A})))

    (is (= {:$type :my/A,
            :x {:$in [-1000 1000]},
            :$refines-to #:my{:B {:$type [:Maybe :my/B], :x {:$in [3 500]}},
                              :C {:$type [:Maybe :my/C], :x {:$in [3 250]}},
                              :D {:$type [:Maybe :my/D], :x {:$in [2 125]}}}}
           (prop-twice sctx {:$type :my/A
                             :$refines-to {:my/D {:$type [:Maybe :my/D]}}})))

    (is (= {:$type :my/A,
            :x {:$in [14 1000]},
            :$refines-to #:my{:B {:x {:$in [7 500]}},
                              :C {:x {:$in [3 250]}},
                              :D {:$type [:Maybe :my/D]
                                  :x {:$in [2 125]}}}}
           (prop-twice sctx {:$type :my/A
                             :$refines-to {:my/C {}}})))

    (is (= {:$type :my/A,
            :x {:$in [14 87]},
            :$refines-to #:my{:B {:x {:$in [7 43]}},
                              :C {:x {:$in [3 21]}},
                              :D {:$type [:Maybe :my/D]
                                  :x {:$in [5 10]}}}}
           (prop-twice sctx {:$type :my/A
                             :$refines-to {:my/C {} ;; required!
                                           :my/D {:$type [:Maybe :my/D]
                                                  :x {:$in [5 10]}}}})))
    (is (= {:$type :my/A,
            :x {:$in [-1000 13]},
            :$refines-to {:my/B {:$type [:Maybe :my/B],
                                 :x {:$in [3 6]}},
                          :my/C :Unset}}
           (prop-twice sctx {:$type :my/A
                             :$refines-to {:my/C :Unset}})))

    (is (= {:$type :my/A,
            :x {:$in [-1000 13]},
            :$refines-to {:my/B {:$type [:Maybe :my/B],
                                 :x {:$in [3 6]}},
                          :my/C :Unset}}
           (prop-twice sctx {:$type :my/A
                             :$refines-to {:my/C :Unset
                                           :my/D :Unset}})))

    ;; TODO: use prop-twice for these, once we have a way to represent "if B
    ;; refines to C, then C's bounds are:"
    (is (= {:$type :my/A,
            :x {:$in [24 1000]},
            :$refines-to #:my{:B {:x {:$in [12 500]}},
                              :C {:x {:$in [6 250]}},
                              :D {:x {:$in [2 125]}}}}
           (prop-refine/propagate sctx {:$type :my/A
                                        :$refines-to {:my/D {}}})))

    (is (= {:$type :my/A,
            :x {:$in [24 1000]},
            :$refines-to {:my/B {:x {:$in [12 500]}},
                          :my/C {:x {:$in [6 250]}},
                          :my/D {:x {:$in [2 125]}}}}
           (prop-refine/propagate sctx {:$type :my/A
                                        :$refines-to {:my/C {:$type [:Maybe :my/C]}
                                                      :my/D {:$type :my/D}}})))

    #_(prn :eval
           (eval/eval-expr* {:senv specs :env (envs/env {})}
                            '(refine-to {:$type :my/A :x 13} :my/C)))))

(deftest test-mix-guarded-chain
  (let [specs '{:my/A {:fields {:x :Integer}
                       :refines-to {:my/B {:expr (when (< 7 x) {:$type :my/B :x (div x 2)})}}}
                :my/B {:fields {:x :Integer}
                       :refines-to {:my/C {:expr {:$type :my/C :x (div x 2)}}}}
                :my/C {:fields {:x :Integer}
                       :refines-to {:my/D {:expr (when (< 5 x) {:$type :my/D :x (div x 2)})}}}
                :my/D {:fields {:x :Integer}}}
        sctx (ssa/spec-map-to-ssa specs)]

    (s/with-fn-validation
      (prop-refine/lower-bound sctx (prop-refine/make-rgraph sctx)
                               {:$type :my/A,
                                :x {:$in [-1000 1000]},
                                :$refines-to {:my/B {:$type [:Maybe :my/B], :x {:$in [3 500]}},
                                              :my/C {:$type [:Maybe :my/C], :x {:$in [1 250]}},
                                              :my/D {:$type [:Maybe :my/D], :x {:$in [-1000 1000]}}}}))

    (is (= {:$type :my/A,
            :x {:$in [-1000 1000]},
            :$refines-to {:my/B {:$type [:Maybe :my/B], :x {:$in [3 500]}},
                          :my/C {:$type [:Maybe :my/C], :x {:$in [1 250]}},
                          :my/D {:$type [:Maybe :my/D], :x {:$in [-1000 1000]}}}}
           (prop-twice sctx {:$type :my/A})))

    (is (= {:$type :my/A,
            :x {:$in [8 1000]},
            :$refines-to {:my/B {:x {:$in [4 500]}},
                          :my/C {:x {:$in [2 250]}},
                          :my/D {:$type [:Maybe :my/D], :x {:$in [-1000 1000]}}}}
           (prop-twice sctx {:$type :my/A
                             :$refines-to {:my/B {}}})))

    (is (= {:$type :my/A,
            :x {:$in [8 1000]},
            :$refines-to {:my/B {:x {:$in [4 500]}},
                          :my/C {:x {:$in [2 250]}},
                          :my/D {:$type [:Maybe :my/D], :x {:$in [-1000 1000]}}}}
           (prop-twice sctx {:$type :my/A
                             :$refines-to {:my/C {}}})))

    ;; TODO: use prop-twice for these, once we have a way to represent "if B
    ;; refines to C, then C's bounds are:"
    (is (= {:$type :my/A,
            :x {:$in [8 1000]},
            :$refines-to {:my/B {:x {:$in [4 500]}},
                          :my/C {:x {:$in [2 250]}},
                          :my/D {:x {:$in [2 125]}}}}
           (prop-refine/propagate sctx {:$type :my/A
                                        :$refines-to {:my/D {}}})))

    (s/with-fn-validation
      ;; Spot-check the bounds above against eval; A.x = 7 cannot refine to D, but 24 can:
      (is (thrown-with-msg? Exception #"no-refinement-path"
                            (eval/eval-expr* {:senv specs :env (envs/env {})}
                                             '(refine-to {:$type :my/A
                                                          :x 7} :my/D))))

      (is (= {:$type :my/D, :x 3}
             (eval/eval-expr* {:senv specs :env (envs/env {})}
                              '(refine-to {:$type :my/A
                                           :x 24} :my/D)))))

    (testing "lower refine-to with mixed guarded chain"
      (let [e-specs (merge specs
                           '{:my/E {:fields {:ex :Integer}
                                    :constraints [["must refine"
                                                   (let [a {:$type :my/A
                                                            :x (- ex 10)}
                                                         d (refine-to a :my/D)]
                                                     (< 10 (get d :x)))]]}
                             :my/F {:fields {:ex :Integer}
                                    :constraints [["must refine"
                                                   (refines-to? {:$type :my/A
                                                                 :x (- ex 10)} :my/D)]]}})
            e-sctx (ssa/spec-map-to-ssa e-specs)]
        (is (= {:$type :my/E, :ex {:$in [98 1000]}}
               (prop-refine/propagate e-sctx {:$type :my/E})))

        (is (= {:$type :my/F, :ex {:$in [34 1000]}}
               (prop-refine/propagate e-sctx {:$type :my/F})))))))

(defn eval-all-traces! [specs traces spec-id expr]
  (let [specs (merge specs (update-vals traces #(ssa/spec-from-ssa (:spec-info (first %)))))]
    (pprint specs)
    (->> traces spec-id
         (run! (fn [t]
                 (let [spec (ssa/spec-from-ssa (:spec-info t))
                       specs (assoc specs spec-id spec)]
                   (prn :eval (eval/eval-expr* {:senv specs
                                                :env (envs/env {})}
                                               expr))
                   (prn)
                   (rewriting/print-trace-item t)))))))

(deftest test-valid-guard
  (let [specs '{:my/A {:fields {:x :Integer}
                       :refines-to {:my/B {:expr (when (valid? {:$type :my/B
                                                                :x (* 2 x)
                                                                :y 9})
                                                   {:$type :my/B
                                                    :x (* 2 x)
                                                    :y 8})}}}
                :my/B {:fields {:x :Integer, :y :Integer}
                       :constraints [["small-x" (< x 10)]]}}
        sctx (ssa/spec-map-to-ssa specs)]

    (rewriting/with-captured-traces
      (is (= {:$type :my/A,
              :x {:$in [-500 1000]},
              :$refines-to {:my/B {:$type [:Maybe :my/B]
                                   :x {:$in [-1000 8]}
                                   :y 8}}}
             (prop-twice sctx {:$type :my/A}))))

    #_(eval-all-traces! specs TRACES :my/A '(valid? {:$type :my/A :x 10, :>my$B $no-value}))))

(deftest test-optional-fields-guarded-refinements
  (let [specs '{:my/A {:fields {:ab [:Maybe [:Instance :my/B]]}
                       :refines-to {:my/B {:expr ab}}}
                :my/B {:fields {:bc [:Maybe [:Instance :my/C]]}
                       :refines-to {:my/D {:expr {:$type :my/D :dx 1}}}}
                :my/C {:fields {:cx :Integer}
                       :refines-to {:my/D {:expr {:$type :my/D :dx (- cx 2)}}}}
                :my/D {:fields {:dx :Integer}
                       :constraints [["dx" (and (< 0 dx) (< dx 15))]]}}
        sctx (ssa/spec-map-to-ssa specs)]

    (is (= {:$type :my/A,
            :ab {:$type [:Maybe :my/B],
                 :bc {:$type [:Maybe :my/C],
                      :cx {:$in [3 16]},
                      :$refines-to {:my/D {:dx {:$in [1 14]}}}},
                 :$refines-to {:my/D {:dx 1}}},
            :$refines-to {:my/B {:$type [:Maybe :my/B],
                                 :bc {:$type [:Maybe :my/C],
                                      :cx {:$in [3 16]},
                                      :$refines-to {:my/D {:dx {:$in [1 14]}}}}},
                          :my/D {:dx 1, :$type [:Maybe :my/D]}}}
           (prop-twice sctx {:$type :my/A})))

    (is (= {:$type :my/A,
            :ab {:$type :my/B,
                 :bc {:$type [:Maybe :my/C],
                      :cx {:$in [3 16]},
                      :$refines-to {:my/D {:dx {:$in [1 14]}}}},
                 :$refines-to {:my/D {:dx 1}}},
            :$refines-to {:my/B {:bc {:$type [:Maybe :my/C],
                                      :cx {:$in [3 16]},
                                      :$refines-to {:my/D {:dx {:$in [1 14]}}}}},
                          :my/D {:dx 1}}}
           (prop-twice sctx {:$type :my/A
                             :$refines-to {:my/B {}}})))

    (is (= {:$type :my/A
            :ab :Unset
            :$refines-to {:my/B :Unset}}
           (prop-twice sctx {:$type :my/A
                             :$refines-to {:my/B :Unset}})))))

(deftest test-basic-refines-to?
  (let [sctx (ssa/spec-map-to-ssa
              '{:my/A {:fields {:a1 :Integer}
                       :constraints [["a1_pos" (< 0 a1)]]}
                :my/B {:fields {:b1 :Integer}
                       :refines-to {:my/A {:expr {:$type :my/A, :a1 (+ 10 b1)}}}}
                :my/C {:fields {:c1 :Integer}
                       :refines-to {:my/B {:expr (when (< c1 10) {:$type :my/B, :b1 (+ 5 c1)})}}}
                :my/D {:fields {:a [:Instance :my/A]
                                :c1 :Integer}
                       :constraints [["rto" (and (refines-to? {:$type :my/C
                                                               :c1 c1}
                                                              :my/A)
                                                 (= a (refine-to {:$type :my/C
                                                                  :c1 c1}
                                                                 :my/A)))]]}
                :my/E {:fields {:a [:Instance :my/A]
                                :c1 :Integer}
                       :constraints [["rto" (refines-to? {:$type :my/C
                                                          :c1 c1}
                                                         :my/A)]]}})]
    (is (= '{:my/A {:fields {:a1 :Integer},
                    :constraints [["$all" (< 0 a1)]],
                    :refines-to {}},
             :my/B {:fields {:b1 :Integer, :>my$A [:Instance :my/A]},
                    :constraints [["$all" (= >my$A {:a1 (+ 10 b1),
                                                    :$type :my/A})]],
                    :refines-to {}},
             :my/C {:fields {:c1 :Integer, :>my$B [:Maybe [:Instance :my/B]]},
                    :constraints [["$all" (= >my$B
                                             (if (< c1 10)
                                               (let [v1 (+ 5 c1)]
                                                 {:>my$A {:a1 (+ 10 v1), :$type :my/A},
                                                  :b1 v1,
                                                  :$type :my/B})
                                               $no-value))]],
                    :refines-to {}},
             :my/D {:fields {:a [:Instance :my/A], :c1 :Integer},
                    :constraints [["$all"
                                   (and
                                    (let [v1 (let
                                              [v1
                                               (let
                                                [v1 (let [v1 $no-value]
                                                      {:>my$B
                                                       (if (< c1 10)
                                                         (let [v2 (+ 5 c1)]
                                                           {:>my$A {:a1 (+ 10 v2), :$type :my/A},
                                                            :b1 v2,
                                                            :$type :my/B})
                                                         v1),
                                                       :c1 c1,
                                                       :$type :my/C})]
                                                 (get v1 :>my$B))]
                                               (when-value v1 (get v1 :>my$A)))]
                                      (if-value v1 true false))
                                    (= a (get
                                          (let [v1 (get
                                                    (let [v1 $no-value]
                                                      {:>my$B
                                                       (if (< c1 10)
                                                         (let [v2 (+ 5 c1)]
                                                           {:>my$A {:a1 (+ 10 v2), :$type :my/A},
                                                            :b1 v2,
                                                            :$type :my/B})
                                                         v1),
                                                       :c1 c1,
                                                       :$type :my/C})
                                                    :>my$B)]
                                            (if-value v1 v1 (error "No active refinement path")))
                                          :>my$A)))]],
                    :refines-to {}}
             :my/E {:fields {:a [:Instance :my/A], :c1 :Integer},
                    :constraints [["$all"
                                   (let [v1 (let [v1
                                                  (let [v1
                                                        (let
                                                         [v1 $no-value]
                                                          {:>my$B (if (< c1 10)
                                                                    (let [v2 (+ 5 c1)]
                                                                      {:>my$A {:a1 (+ 10 v2), :$type :my/A},
                                                                       :b1 v2,
                                                                       :$type :my/B})
                                                                    v1),
                                                           :c1 c1,
                                                           :$type :my/C})]
                                                    (get v1 :>my$B))]
                                              (when-value v1 (get v1 :>my$A)))]
                                     (if-value v1 true false))]],
                    :refines-to {}}}
           (update-vals (prop-refine/lower-spec-refinements sctx (prop-refine/make-rgraph sctx))
                        ssa/spec-from-ssa)))

    (is (= {:$type :my/D,
            :c1 {:$in [-14 9]}
            :a {:$type :my/A,
                :a1 {:$in [1 24]}}}
           (prop-twice sctx {:$type :my/D})))

    (is (= {:$type :my/E,
            :c1 {:$in [-14 9]}
            :a {:$type :my/A,
                :a1 {:$in [1 1000]}}}
           (prop-twice sctx {:$type :my/E})))))

(deftest test-refines-to?-of-unknown-type
  (let [specs '{:t/A {:fields {:y :Integer}
                      :refines-to {:t/C {:expr {:$type :t/C
                                                :cn (+ y 10)}}}
                      :constraints [["limited"
                                     (> y -10)]]}
                :t/B {:fields {:y :Integer}
                      :refines-to {:t/C {:expr
                                         {:$type :t/C
                                          :cn (+ y 20)}}}
                      :constraints [["limited"
                                     (and (< y 100) (> y 10))]]}
                :t/C {:fields {:cn :Integer}}
                :t/D {:fields {:x :Integer}
                      :constraints [["the point"
                                     (refines-to? (if (< x 5)
                                                    {:$type :t/A, :y x}
                                                    {:$type :t/B, :y x})
                                                  :t/C)]]}}
        sctx (ssa/spec-map-to-ssa specs)]

    ;; x bounds should be tighter [-9 1000]
    (is (= {:$type :t/D :x {:$in [-1000 1000]}}
           (prop-twice sctx {:$type :t/D})))

    #_(prn (update-vals (prop-refine/lower-spec-refinements sctx (prop-refine/make-rgraph sctx))
                        ssa/spec-from-ssa))

    #_(prn :eval
           (eval/eval-expr* {:senv specs :env (envs/env {})}
                            '(valid? {:$type :t/D :x -10})))))

(deftest test-refines-to?-of-branching-type
  (let [specs '{:t/A {:fields {:y :Integer}
                      :constraints [["limited"
                                     (> y -10)]]}
                :t/B {:fields {:y :Integer}
                      :constraints [["limited"
                                     (and (< y 100) (> y 10))]]}
                :t/C {:fields {:cn :Integer}
                      :refines-to {:t/A {:expr (when (<= cn 1)
                                                 {:$type :t/A
                                                  :y cn})}
                                   :t/B {:expr (when (>= cn 1)
                                                 {:$type :t/B
                                                  :y (+ cn 20)})}}}
                :t/D {:fields {:x :Integer}
                      :constraints [["ref"
                                     (refines-to? {:$type :t/C, :cn x}
                                                  :t/A)]]}
                :t/E {:fields {:x :Integer}
                      :constraints [["ref"
                                     (refines-to? {:$type :t/C, :cn x}
                                                  :t/B)]]}

                :t/F {:fields {:x :Integer}
                      :constraints [["ref"
                                     (and (refines-to? {:$type :t/C, :cn x}
                                                       :t/B)
                                          (refines-to? {:$type :t/C, :cn x}
                                                       :t/A))]]}

                :t/G {:fields {:x :Integer}
                      :constraints [["ref"
                                     (or (refines-to? {:$type :t/C, :cn x}
                                                      :t/B)
                                         (refines-to? {:$type :t/C, :cn x}
                                                      :t/A))]]}}
        sctx (ssa/spec-map-to-ssa specs)]

    (is (= {:$type :t/D :x {:$in [-9 1]}}
           (prop-twice sctx {:$type :t/D})))

    (is (= {:$type :t/E :x {:$in [1 79]}}
           (prop-twice sctx {:$type :t/E})))

    (is (= {:$type :t/F :x 1}
           (prop-twice sctx {:$type :t/F})))

    ;; Bounds for x should be [-9 79]
    (is (= {:$type :t/G :x {:$in [-1000 1000]}}
           (prop-twice sctx {:$type :t/G})))

    #_(prn (update-vals (prop-refine/lower-spec-refinements sctx (prop-refine/make-rgraph sctx))
                        ssa/spec-from-ssa))

    #_(prn :eval
           (eval/eval-expr* {:senv specs :env (envs/env {})}
                            '(or (refines-to? {:$type :t/C, :cn 79}
                                              :t/B)
                                 (refines-to? {:$type :t/C, :cn 79}
                                              :t/A))))))

;; (time (run-tests))
