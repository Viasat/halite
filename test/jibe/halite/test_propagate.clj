;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.test-propagate
  (:require [jibe.halite.envs :as halite-envs]
            [jibe.halite.propagate :as hp]
            [schema.test])
  (:use clojure.test))

(use-fixtures :once schema.test/validate-schemas)

(deftest test-propagation-of-trivial-spec
  (let [senv (halite-envs/spec-env
              {:ws/A {:spec-vars {:x :Integer, :y :Integer, :oddSum :Boolean}
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

      {:$type :ws/A, :x 10} {:$type :ws/A, :x 10, :y 32, :oddSum false}
      )))

(deftest test-one-to-one-composition
  (let [senv (halite-envs/spec-env
              '{:ws/A {:spec-vars {:x :Integer, :y :Integer}
                       :constraints [["pos" (and (< 0 x) (< 0 y))]
                                     ["boundedSum" (< (+ x y) 20)]]
                       :refines-to {}}
                :ws/B {:spec-vars {:a1 :ws/A, :a2 :ws/A}
                       :constraints [["a1smaller" (and (< (get* a1 :x) (get* a2 :x))
                                                       (< (get* a1 :y) (get* a2 :y)))]]
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

(deftest test-recursive-composition
  (let [senv (halite-envs/spec-env
              '{:ws/A
                {:spec-vars {:b :ws/B :c :ws/C}
                 :constraints [["a1" (= (get* b :bn) (get* c :cn))]
                               ["a2" (if (> (get* b :bn) 0)
                                       (< (get* b :bn)
                                          (get* (get* (get* c :a) :b) :bn))
                                       (= 1 1) #_true)]]
                 :refines-to {}}
                :ws/B
                {:spec-vars {:bn :Integer :bp :Boolean}
                 :constraints [["b1" (if bp (<= bn 10) (>= bn 10))]]
                 :refines-to {}}
                :ws/C
                {:spec-vars {:a :ws/A :cn :Integer}
                 :constraints []
                 :refines-to {}}})
        opts {:default-int-bounds [-100 100]}]
    (are [in out]
        (= out (hp/propagate senv opts in))

      {:$type :ws/A}
      {:$type :ws/A
       :b {:$type :ws/B
           :bn {:$in [-100 100]}
           :bp {:$in #{false true}}}
       :c {:$type :ws/C
           :a {:$type :ws/A}
           :cn {:$in [-100 100]}}}

      {:$type :ws/A :c {:$type :ws/C :cn 12}}
      {:$type :ws/A
       :b {:$type :ws/B, :bn 12, :bp false}
       :c {:$type :ws/C
           :cn 12
           :a {:$type :ws/A}}}

      {:$type :ws/A :c {:$type :ws/C :a {:$type :ws/A}}}
      {:$type :ws/A,
       :b {:$type :ws/B,
           :bn {:$in [-100 100]},
           :bp {:$in #{false true}}},
       :c {:$type :ws/C,
           :a {:$type :ws/A,
               :b {:$type :ws/B,
                   :bn {:$in [-100 100]},
                   :bp {:$in #{false true}}},
               :c {:$type :ws/C}},
           :cn {:$in [-100 100]}}}

      {:$type :ws/A :b {:$type :ws/B :bp true} :c {:$type :ws/C :a {:$type :ws/A}}}
      {:$type :ws/A,
       :b {:$type :ws/B, :bn {:$in [-100 10]}, :bp true},
       :c {:$type :ws/C,
           :a {:$type :ws/A,
               :b {:$type :ws/B, :bn {:$in [-100 100]}, :bp {:$in #{false true}}},
               :c {:$type :ws/C}},
           :cn {:$in [-100 10]}}})))

(deftest test-instance-literals
  (let [senv (halite-envs/spec-env
              '{:ws/A
                {:spec-vars {:ax :Integer}
                 :constraints [["c1" (and (< 0 ax) (< ax 10))]]
                 :refines-to {}}

                :ws/B
                {:spec-vars {:by :Integer, :bz :Integer}
                 :constraints [["c2" (let [a {:$type :ws/A :ax (* 2 by)}
                                           x (get* {:$type :ws/A :ax (+ bz bz)} :ax)]
                                       (= x (get* {:$type :ws/C :cx (get* a :ax)} :cx)))]]
                 :refines-to {}}

                :ws/C
                {:spec-vars {:cx :Integer}
                 :constraints [["c3" (= cx (get* {:$type :ws/A :ax cx} :ax))]]
                 :refines-to {}}})
        opts {:default-int-bounds [-100 100]}]
    (are [in out]
        (= out (hp/propagate senv opts in))

      {:$type :ws/C} {:$type :ws/C :cx {:$in [1 9]}}
      {:$type :ws/B} {:$type :ws/B :by {:$in [1 4]} :bz {:$in [1 4]}}
      {:$type :ws/B :by 2} {:$type :ws/B :by 2 :bz 2})))
