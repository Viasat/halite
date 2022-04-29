;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.test-propagate
  (:require [jibe.halite.envs :as halite-envs]
            [jibe.halite.propagate :as hp]
            [schema.test])
  (:use clojure.test))

(use-fixtures :once schema.test/validate-schemas)

(deftest test-spec-ify-bound-on-simple-spec
  (let [senv (halite-envs/spec-env
              '{:ws/A
                {:spec-vars {:x :Integer, :y :Integer, :b :Boolean}
                 :constraints [["c1" (let [delta (abs (- x y))]
                                       (and (< 5 delta)
                                            (< delta 10)))]
                               ["c2" (= b (< x y))]]
                 :refines-to {}}})]

    (is (= '{:spec-vars {:x :Integer, :y :Integer, :b :Boolean}
             :constraints
             [["$all" (let [$22 (abs (- x y))]
                        (and (and (< 5 $22)
                                  (< $22 10))
                             (= b (< x y))))]]
             :refines-to {}}
           (hp/spec-ify-bound senv {:$type :ws/A})))

    (is (= '{:spec-vars {:x :Integer, :y :Integer, :b :Boolean}
             :constraints
             [["$all" (let [$22 (abs (- x y))]
                        (and (and (< 5 $22)
                                  (< $22 10))
                             (= b (< x y))
                             (= x 12)
                             (= b false)))]]
             :refines-to {}}
           (hp/spec-ify-bound senv {:$type :ws/A :x 12 :b false})))))

(deftest test-spec-ify-bound-on-instance-valued-exprs
  ;; only integer and boolean valued variables, but expressions may be instance valued
  (let [senv (halite-envs/spec-env
              '{:ws/A
                {:spec-vars {:an :Integer}
                 :constraints [["a1" (< an 10)]
                               ["a2" (< an (get {:$type :ws/B :bn (+ 1 an)} :bn))]]
                 :refines-to {}}
                :ws/B
                {:spec-vars {:bn :Integer}
                 :constraints [["b1" (< 0 (get {:$type :ws/C :cn bn} :cn))]]
                 :refines-to {}}
                :ws/C
                {:spec-vars {:cn :Integer}
                 :constraints [["c1" (= 0 (mod cn 2))]]
                 :refines-to {}}})]
    (is (= '{:spec-vars {:an :Integer}
             :refines-to {}
             :constraints
             [["$all" (let [$43 (+ 1 an)]
                        (and (< an 10)                ; a1
                             (< an $43)               ; a2
                             (and (< 0 $43)           ; b1
                                  (= 0 (mod $43 2)))))]]} ; c1
           (hp/spec-ify-bound senv {:$type :ws/A})))))

(deftest test-spec-ify-bound-on-ifs-and-instance-literals
  (let [senv (halite-envs/spec-env
              '{:ws/A
                {:spec-vars {:an :Integer, :ab :Boolean}
                 :constraints [["a1" (not=
                                      (if ab
                                        {:$type :ws/B :bn an}
                                        {:$type :ws/B :bn 12})
                                      {:$type :ws/B :bn (+ an 1)})]]
                 :refines-to {}}
                :ws/B
                {:spec-vars {:bn :Integer}
                 :constraints [["b1" (<= (div 10 bn) 10)]]
                 :refines-to {}}})]
    (is (= '{:spec-vars {:an :Integer :ab :Boolean}
             :refines-to {}
             :constraints
             [["$all" (let [$47 (+ an 1)]
                        (and
                         (not= (if ab an 12) $47)
                         (if ab (<= (div 10 an) 10) true)
                         (<= (div 10 $47) 10)
                         (if (not ab) (<= (div 10 12) 10) true)))]]}
           (hp/spec-ify-bound senv {:$type :ws/A})))))

(deftest test-spec-ify-bound-with-composite-specs
  (testing "simple composition"
    (let [senv (halite-envs/spec-env
                '{:ws/A {:spec-vars {:x :Integer, :y :Integer}
                         :constraints [["pos" (and (< 0 x) (< 0 y))]
                                       ["boundedSum" (< (+ x y) 20)]]
                         :refines-to {}}
                  :ws/B {:spec-vars {:a1 :ws/A, :a2 :ws/A}
                         :constraints [["a1smaller" (and (< (get a1 :x) (get a2 :x))
                                                         (< (get a1 :y) (get a2 :y)))]]
                         :refines-to {}}})]
      (is (= '{:spec-vars {:a1|x :Integer, :a1|y :Integer, :a2|x :Integer, :a2|y :Integer}
               :refines-to {}
               :constraints
               [["$all" (and
                         (and (< a1|x a2|x) (< a1|y a2|y))
                         (and (< 0 a1|x) (< 0 a1|y))
                         (< (+ a1|x a1|y) 20)
                         (and (< 0 a2|x) (< 0 a2|y))
                         (< (+ a2|x a2|y) 20))]]}
             (hp/spec-ify-bound senv {:$type :ws/B})))))

  (testing "composition and instance literals"
    (let [senv (halite-envs/spec-env
                '{:ws/C
                  {:spec-vars {:m :Integer, :n :Integer}
                   :constraints [["c1" (>= m n)]]
                   :refines-to {}}

                  :ws/D
                  {:spec-vars {:c :ws/C :m :Integer}
                   :constraints [["c1" (= c (let [a 2
                                                  c {:$type :ws/C :m (get c :n) :n (* a m)}
                                                  b 3] c))]]
                   :refines-to {}}})]
      (is (= '{:spec-vars {:c|m :Integer, :c|n :Integer, :m :Integer}
               :refines-to {}
               :constraints
               [["$all" (let [$33 (* 2 m)]
                          (and
                           (and (= c|m c|n)
                                (= c|n $33))
                           (<= $33 c|n)
                           (<= c|n c|m)))]]}
             (hp/spec-ify-bound senv {:$type :ws/D})))))

  (testing "composition of recursive specs"
    ;; Note that due to unconditional recursion there are no finite valid instances of A or C!
    ;; That doesn't prevent us from making the idea of a bound on a recursive spec finite and
    ;; well-defined.
    (let [senv (halite-envs/spec-env
                '{:ws/A
                  {:spec-vars {:b :ws/B :c :ws/C}
                   :constraints [["a1" (= (get b :bn) (get c :cn))]
                                 ["a2" (if (> (get b :bn) 0)
                                         (< (get b :bn)
                                            (get (get (get c :a) :b) :bn))
                                         true)]]
                   :refines-to {}}
                  :ws/B
                  {:spec-vars {:bn :Integer :bp :Boolean}
                   :constraints [["b1" (if bp (<= bn 10) (>= bn 10))]]
                   :refines-to {}}
                  :ws/C
                  {:spec-vars {:a :ws/A :cn :Integer}
                   :constraints []
                   :refines-to {}}})]
      (are [bound choco-spec]
           (= choco-spec (hp/spec-ify-bound senv bound))

        {:$type :ws/A}
        '{:spec-vars {:b|bn :Integer, :b|bp :Boolean, :c|cn :Integer}
          :refines-to {}
          :constraints
          [["$all" (and (= b|bn c|cn)
                        (if b|bp (<= b|bn 10) (<= 10 b|bn)))]]}

        {:$type :ws/A :b {:$type :ws/B :bn {:$in [2 8]}}}
        '{:spec-vars {:b|bn :Integer, :b|bp :Boolean, :c|cn :Integer}
          :refines-to {}
          :constraints
          [["$all" (and
                    (= b|bn c|cn)
                    (if b|bp (<= b|bn 10) (<= 10 b|bn))
                    (and (<= 2 b|bn) (<= b|bn 8)))]]}

        #_{:$type :ws/A :b {:$type :ws/B :bn {:$in #{3 4 5}}}}
        #_'{:vars {b|bn :Int, b|bp :Bool, c|cn :Int}
            :constraints
            nil}

        {:$type :ws/A
         :c {:$type :ws/C :cn 14}}
        '{:spec-vars {:b|bn :Integer, :b|bp :Boolean, :c|cn :Integer}
          :refines-to {}
          :constraints
          [["$all" (and (= b|bn c|cn)
                        (if b|bp (<= b|bn 10) (<= 10 b|bn))
                        (= c|cn 14))]]}

        {:$type :ws/A
         :b {:$type :ws/B :bp true}
         :c {:$type :ws/C
             :a {:$type :ws/A}}}
        '{:spec-vars {:b|bn :Integer, :b|bp :Boolean, :c|a|b|bn :Integer, :c|a|b|bp :Boolean, :c|cn :Integer}
          :refines-to {}
          :constraints
          [["$all" (and
                    (= b|bn c|cn)
                    (if (< 0 b|bn) (< b|bn c|a|b|bn) true)
                    (if b|bp (<= b|bn 10) (<= 10 b|bn))
                    (= b|bp true)
                    (if c|a|b|bp (<= c|a|b|bn 10) (<= 10 c|a|b|bn)))]]}))))

(deftest test-spec-ify-bound-on-recursive-composition
  (let [senv (halite-envs/spec-env
              '{:ws/A
                {:spec-vars {:b :ws/B :c :ws/C}
                 :constraints [["a1" (= (get b :bn) (get c :cn))]
                               ["a2" (if (> (get b :bn) 0)
                                       (< (get b :bn)
                                          (get (get (get c :a) :b) :bn))
                                       true)]]
                 :refines-to {}}
                :ws/B
                {:spec-vars {:bn :Integer :bp :Boolean}
                 :constraints [["b1" (if bp (<= bn 10) (>= bn 10))]]
                 :refines-to {}}
                :ws/C
                {:spec-vars {:a :ws/A :cn :Integer}
                 :constraints []
                 :refines-to {}}})]

    (are [b-bound constraint]
         (= {:spec-vars {:bn :Integer, :bp :Boolean}
             :constraints
             [["$all" constraint]]
             :refines-to {}}
            (hp/spec-ify-bound senv b-bound))

      {:$type :ws/B} '(if bp (<= bn 10) (<= 10 bn))
      {:$type :ws/B :bn 12} '(and (if bp (<= bn 10) (<= 10 bn))
                                  (= bn 12)))

    (is (= '{:spec-vars {:b|bn :Integer :b|bp :Boolean
                         :c|cn :Integer}
             :constraints
             [["$all" (and
                       (= b|bn c|cn)
                       (if b|bp
                         (<= b|bn 10)
                         (<= 10 b|bn)))]]
             :refines-to {}}
           (->> {:$type :ws/A}
                (hp/spec-ify-bound senv))))

    (is (= '{:spec-vars {:b|bn :Integer :b|bp :Boolean
                         :c|cn :Integer
                         :c|a|b|bn :Integer :c|a|b|bp :Boolean}
             :constraints
             [["$all" (and
                       (= b|bn c|cn)
                       (if (< 0 b|bn)
                         (< b|bn c|a|b|bn)
                         true)
                       (if b|bp
                         (<= b|bn 10)
                         (<= 10 b|bn))
                       (= b|bp true)
                       (if c|a|b|bp
                         (<= c|a|b|bn 10)
                         (<= 10 c|a|b|bn)))]]
             :refines-to {}}
           (->> '{:$type :ws/A
                  :b {:$type :ws/B :bp true}
                  :c {:$type :ws/C :a {:$type :ws/A}}}
                (hp/spec-ify-bound senv))))))

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

      {:$type :ws/A, :x 10} {:$type :ws/A, :x 10, :y 32, :oddSum false})))

(deftest test-one-to-one-composition
  (let [senv (halite-envs/spec-env
              '{:ws/A {:spec-vars {:x :Integer, :y :Integer}
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

(deftest test-recursive-composition
  (let [senv (halite-envs/spec-env
              '{:ws/A
                {:spec-vars {:b :ws/B :c :ws/C}
                 :constraints [["a1" (= (get b :bn) (get c :cn))]
                               ["a2" (if (> (get b :bn) 0)
                                       (< (get b :bn)
                                          (get (get (get c :a) :b) :bn))
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
                                           x (get {:$type :ws/A :ax (+ bz bz)} :ax)]
                                       (= x (get {:$type :ws/C :cx (get a :ax)} :cx)))]]
                 :refines-to {}}

                :ws/C
                {:spec-vars {:cx :Integer}
                 :constraints [["c3" (= cx (get {:$type :ws/A :ax cx} :ax))]]
                 :refines-to {}}})
        opts {:default-int-bounds [-100 100]}]
    (are [in out]
         (= out (hp/propagate senv opts in))

      {:$type :ws/C} {:$type :ws/C :cx {:$in [1 9]}}
      {:$type :ws/B} {:$type :ws/B :by {:$in [1 4]} :bz {:$in [1 4]}}
      {:$type :ws/B :by 2} {:$type :ws/B :by 2 :bz 2})))

(deftest test-spec-ify-for-various-instance-literal-cases
  (let [senv '{:ws/Simpler
               {:spec-vars {:x :Integer, :b :Boolean}
                :constraints [["posX" (< 0 x)]
                              ["bIfOddX" (= b (= (mod* x 2) 1))]]
                :refines-to {}}

               :ws/Simpler2
               {:spec-vars {:y :Integer}
                :constraints [["likeSimpler" (= y (get {:$type :ws/Simpler :x y :b false} :x))]]
                :refines-to {}}

               :ws/Test
               {:spec-vars {}
                :constraints []
                :refines-to {}}}]
    (are [constraint choco-spec]
         (= choco-spec
            (-> senv
                (update-in [:ws/Test :constraints] conj ["c1" constraint])
                (halite-envs/spec-env)
                (hp/spec-ify-bound {:$type :ws/Test})))

      '(get
        (let [x (+ 1 2)
              s {:$type :ws/Simpler :x (+ x 1) :b false}
              s {:$type :ws/Simpler :x (- (get s :x) 2) :b true}]
          {:$type :ws/Simpler :x 12 :b (get s :b)})
        :b)
      '{:spec-vars {}
        :refines-to {}
        :constraints
        [["$all" (let [$63 (+ (+ 1 2) 1)
                       $74 (- $63 2)]
                   (and
                    true
                    (and (< 0 $63) (= false (= (mod $63 2) 1)))
                    (and (< 0 $74) (= true (= (mod $74 2) 1)))
                    (and (< 0 12) (= true (= (mod 12 2) 1)))))]]}

      '(get {:$type :ws/Simpler :x (get {:$type :ws/Simpler :x 14 :b false} :x) :b true} :b)
      '{:spec-vars {}
        :refines-to {}
        :constraints
        [["$all" (let [$52 (= (mod 14 2) 1)
                       $47 (< 0 14)]
                   (and true
                        (and $47 (= false $52))
                        (and $47 (= true $52))))]]}

      '(not= 10 (get {:$type :ws/Simpler2 :y 12} :y))
      '{:spec-vars {}
        :refines-to {}
        :constraints
        [["$all" (and
                  (not= 10 12)
                  (and (= 12 12)
                       (and (< 0 12)
                            (= false (= (mod 12 2) 1)))))]]})))
