;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.test-propagate
  (:require [jibe.halite.halite-envs :as halite-envs]
            [jibe.halite.propagate :as hp]
            [jibe.halite.transpile.ssa :as ssa]
            [schema.test])
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
             [["$all" (let [$42 (abs (- x y))]
                        (and (and (< 5 $42)
                                  (< $42 10))
                             (= b (< x y))))]]
             :refines-to {}}
           (hp/spec-ify-bound senv {:$type :ws/A})))))

(deftest test-spec-ify-bound-on-instance-valued-exprs
  ;; only integer and boolean valued variables, but expressions may be instance valued
  (let [senv (halite-envs/spec-env
              '{:ws/A
                {:spec-vars {:an "Integer"}
                 :constraints [["a1" (< an 10)]
                               ["a2" (< an (get {:$type :ws/B :bn (+ 1 an)} :bn))]]
                 :refines-to {}}
                :ws/B
                {:spec-vars {:bn "Integer"}
                 :constraints [["b1" (< 0 (get {:$type :ws/C :cn bn} :cn))]]
                 :refines-to {}}
                :ws/C
                {:spec-vars {:cn "Integer"}
                 :constraints [["c1" (= 0 (mod cn 2))]]
                 :refines-to {}}})]
    (is (= '{:spec-vars {:an "Integer"}
             :refines-to {}
             :constraints
             [["$all" (let [$96 (+ 1 an)]
                        (and (< an 10)
                             (if (if (= 0 (mod $96 2))
                                   (< 0 $96)
                                   false)
                               (< an $96)
                               false)))]]}
           (hp/spec-ify-bound senv {:$type :ws/A})))))

(deftest test-spec-ify-bound-on-ifs-and-instance-literals
  (let [senv (halite-envs/spec-env
              '{:ws/A
                {:spec-vars {:an "Integer", :ab "Boolean"}
                 :constraints [["a1" (not=
                                      (if ab
                                        {:$type :ws/B :bn an}
                                        {:$type :ws/B :bn 12})
                                      {:$type :ws/B :bn (+ an 1)})]]
                 :refines-to {}}
                :ws/B
                {:spec-vars {:bn "Integer"}
                 :constraints [["b1" (<= (div 10 bn) 10)]]
                 :refines-to {}}})]
    (is (= '{:spec-vars {:an "Integer" :ab "Boolean"}
             :refines-to {}
             :constraints
             [["$all"  (let [$91 (+ an 1)]
                         (if (and (if ab
                                    (<= (div 10 an) 10)
                                    (<= (div 10 12) 10))
                                  (<= (div 10 $91) 10))
                           (not= (if ab an 12) $91)
                           false))]]}
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
                  {:spec-vars {:m "Integer", :n "Integer"}
                   :constraints [["c1" (>= m n)]]
                   :refines-to {}}

                  :ws/D
                  {:spec-vars {:c :ws/C :m "Integer"}
                   :constraints [["c1" (= c (let [a 2
                                                  c {:$type :ws/C :m (get c :n) :n (* a m)}
                                                  b 3] c))]]
                   :refines-to {}}})]
      (is (= '{:spec-vars {:c|m "Integer", :c|n "Integer", :m "Integer"}
               :refines-to {}
               :constraints
               [["$all" (let [$64 (* 2 m), $67 (<= $64 c|n)]
                          (and (if $67 (and (= c|m c|n) (= c|n $64)) false) (<= c|n c|m)))]]}
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
                  {:spec-vars {:bn "Integer" :bp "Boolean"}
                   :constraints [["b1" (if bp (<= bn 10) (>= bn 10))]]
                   :refines-to {}}
                  :ws/C
                  {:spec-vars {:a :ws/A :cn "Integer"}
                   :constraints []
                   :refines-to {}}})]
      (are [bound choco-spec]
           (= choco-spec (hp/spec-ify-bound senv bound))

        {:$type :ws/A}
        '{:spec-vars {:b|bn "Integer", :b|bp "Boolean", :c|cn "Integer"}
          :refines-to {}
          :constraints
          [["$all" (and
                    (= b|bn c|cn)
                    (if b|bp (<= b|bn 10) (<= 10 b|bn)))]]}

        {:$type :ws/A :b {:$type :ws/B :bn {:$in [2 8]}}}
        '{:spec-vars {:b|bn "Integer", :b|bp "Boolean", :c|cn "Integer"}
          :refines-to {}
          :constraints
          [["$all" (and
                    (= b|bn c|cn)
                    (if b|bp (<= b|bn 10) (<= 10 b|bn)))]]}

        {:$type :ws/A :b {:$type :ws/B :bn {:$in #{3 4 5}}}}
        '{:spec-vars {:b|bn "Integer", :b|bp "Boolean", :c|cn "Integer"}
          :constraints
          [["$all" (and
                    (= b|bn c|cn)
                    (if b|bp (<= b|bn 10) (<= 10 b|bn)))]]
          :refines-to {}}

        {:$type :ws/A
         :c {:$type :ws/C :cn 14}}
        '{:spec-vars {:b|bn "Integer", :b|bp "Boolean", :c|cn "Integer"}
          :refines-to {}
          :constraints
          [["$all" (and
                    (= b|bn c|cn)
                    (if b|bp (<= b|bn 10) (<= 10 b|bn)))]]}

        {:$type :ws/A
         :b {:$type :ws/B :bp true}
         :c {:$type :ws/C
             :a {:$type :ws/A}}}
        '{:spec-vars {:b|bn "Integer", :b|bp "Boolean", :c|a|b|bn "Integer", :c|a|b|bp "Boolean", :c|cn "Integer"}
          :refines-to {}
          :constraints
          [["$all" (let [$58 (< 0 b|bn)]
                     (and
                      (= b|bn c|cn)
                      (if (if $58 true true) (if $58 (< b|bn c|a|b|bn) true) false)
                      (if b|bp (<= b|bn 10) (<= 10 b|bn))
                      (if c|a|b|bp (<= c|a|b|bn 10) (<= 10 c|a|b|bn))))]]}))))

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
                {:spec-vars {:bn "Integer" :bp "Boolean"}
                 :constraints [["b1" (if bp (<= bn 10) (>= bn 10))]]
                 :refines-to {}}
                :ws/C
                {:spec-vars {:a :ws/A :cn "Integer"}
                 :constraints []
                 :refines-to {}}})]

    (are [b-bound constraint]
         (= {:spec-vars {:bn "Integer", :bp "Boolean"}
             :constraints
             [["$all" constraint]]
             :refines-to {}}
            (hp/spec-ify-bound senv b-bound))

      {:$type :ws/B} '(if bp (<= bn 10) (<= 10 bn))
      {:$type :ws/B :bn 12} '(if bp (<= bn 10) (<= 10 bn)))

    (is (= '{:spec-vars {:b|bn "Integer" :b|bp "Boolean"
                         :c|cn "Integer"}
             :constraints
             [["$all" (and
                       (= b|bn c|cn)
                       (if b|bp
                         (<= b|bn 10)
                         (<= 10 b|bn)))]]
             :refines-to {}}
           (->> {:$type :ws/A}
                (hp/spec-ify-bound senv))))

    (is (= '{:spec-vars {:b|bn "Integer" :b|bp "Boolean"
                         :c|cn "Integer"
                         :c|a|b|bn "Integer" :c|a|b|bp "Boolean"}
             :constraints
             [["$all" (let [$58 (< 0 b|bn)]
                        (and
                         (= b|bn c|cn)
                         (if (if $58 true true) (if $58 (< b|bn c|a|b|bn) true) false)
                         (if b|bp (<= b|bn 10) (<= 10 b|bn))
                         (if c|a|b|bp (<= c|a|b|bn 10) (<= 10 c|a|b|bn))))]]
             :refines-to {}}
           (->> '{:$type :ws/A
                  :b {:$type :ws/B :bp true}
                  :c {:$type :ws/C :a {:$type :ws/A}}}
                (hp/spec-ify-bound senv))))))

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
    (is (= '{:spec-vars {:a1|x "Integer", :a1|y "Integer", :a2|x "Integer", :a2|y "Integer"}
             :constraints [["$all"
                            (and
                             (and (< a1|x a2|x) (< a1|y a2|y))
                             (and (< 0 a1|x) (< 0 a1|y))
                             (< (+ a1|x a1|y) 20)
                             (and (< 0 a2|x) (< 0 a2|y))
                             (< (+ a2|x a2|y) 20))]]
             :refines-to {}}
           (hp/spec-ify-bound senv {:$type :ws/B})))

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
  (schema.core/without-fn-validation
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
                 {:spec-vars {:bn "Integer" :bp "Boolean"}
                  :constraints [["b1" (if bp (<= bn 10) (>= bn 10))]]
                  :refines-to {}}
                 :ws/C
                 {:spec-vars {:a :ws/A :cn "Integer"}
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
            :cn {:$in [-100 10]}}}))))

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

(deftest test-spec-ify-bound-for-refinement
  (schema.core/without-fn-validation
   (let [senv (halite-envs/spec-env
               '{:ws/A
                 {:spec-vars {:an "Integer"}
                  :constraints [["a1" (< an 10)]]
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

     (are [spec-id spec]
          (= spec (hp/spec-ify-bound senv {:$type spec-id}))

       :ws/C '{:spec-vars {:cn "Integer"}
               :constraints [["$all" (= 0 (mod cn 2))]]
               :refines-to {}}

       :ws/B '{:spec-vars {:bn "Integer"}
               :constraints
               [["$all" (and (< 0 bn)
                             (= 0 (mod bn 2)))]]
               :refines-to {}}

       :ws/A '{:spec-vars {:an "Integer"}
               :constraints
               [["$all" (let [$101 (+ 1 an)]
                          (and (< an 10)
                               (and (< 0 $101)
                                    (= 0 (mod $101 2)))))]]
               :refines-to {}}

       :ws/D '{:spec-vars {:a|an "Integer", :dm "Integer", :dn "Integer"}
               :constraints
               [["$all"
                 (let [$245 (+ 1 dn)
                       $278 (+ 1 a|an)
                       $284 (and (< 0 $278) (= 0 (mod $278 2)))
                       $254 (= 0 (mod $245 2))
                       $256 (and (< 0 $245) $254)
                       $258 (and (< dn 10) $256)
                       $262 (if $258 $256 false)]
                   (and
                    (if (and $258 $262 $258 (if $262 $254 false)) (= dm $245) false)
                    (if $284 (= $245 $278) false)
                    (< a|an 10)
                    $284))
                 ;; hand simplification of the above, for validation purposes
                 #_(and
                    (< dn 10)                ; a1 as instantiated from d1
                    (< 0 (+ 1 dn))           ; b1 as instantiated from d1 thru A->B
                    (= 0 (mod (+ 1 dn) 2))   ; c1 as instantiated from d1 thru A->B->C
                    (= dm (+ 1 dn))          ; d1 itself
                    (< 0 (+ 1 a|an))         ; b1 as instanted on a thru A->B
                    (= 0 (mod (+ 1 a|an) 2)) ; c1 as instantiated on a thru A->B->C
                    (= (+ 1 dn) (+ 1 a|an))  ; d2 itself
                    (< a|an 10))]]           ; a1 as instantiated on a thru A->B->C
               :refines-to {}}))))

(deftest test-spec-ify-for-various-instance-literal-cases
  (let [senv '{:ws/Simpler
               {:spec-vars {:x "Integer", :b "Boolean"}
                :constraints [["posX" (< 0 x)]
                              ["bIfOddX" (= b (= (mod* x 2) 1))]]
                :refines-to {}}

               :ws/Simpler2
               {:spec-vars {:y "Integer"}
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
                (hp/spec-ify-bound {:$type :ws/Test})
                :constraints first second))

      '(get
        (let [x (+ 1 2)
              s {:$type :ws/Simpler :x (+ x 1) :b false}
              s {:$type :ws/Simpler :x (- (get s :x) 2) :b true}]
          {:$type :ws/Simpler :x 12 :b (get s :b)})
        :b)
      '(let [$129 (+ 1 2)
             $130 (+ $129 1)
             $139 (and (< 0 $130) (= false (= (mod $130 2) 1)))
             $153 (if $139 (let [$141 (- $130 2)] (and (< 0 $141) (= true (= (mod $141 2) 1)))) false)]
         (if (and true $139 $153 (if $153 (and (< 0 12) (= true (= (mod 12 2) 1))) false))
           true
           false))
      ;; hand-simplified
      #_'(and (< 0 (+ (+ 1 2) 1))
              (= false (= (mod (+ (+ 1 2) 1) 2) 1))
              (< 0 (- (+ (+ 1 2) 1) 2))
              (= true (= (mod (- (+ (+ 1 2) 1) 2) 2) 1))
              (< 0 12)
              (= true (= (mod 12 2) 1)))

      '(get {:$type :ws/Simpler :x (get {:$type :ws/Simpler :x 14 :b false} :x) :b true} :b)
      '(let [$87 (< 0 14)
             $92 (= (mod 14 2) 1)]
         (if (if (and $87 (= false $92))
               (and $87 (= true $92))
               false)
           true
           false))

      '(not= 10 (get {:$type :ws/Simpler2 :y 12} :y))
      '(if (if (and (< 0 12) (= false (= (mod 12 2) 1)))
             (= 12 12)
             false)
         (not= 10 12)
         false))))

(deftest test-propagate-for-primitive-optionals
  (let [senv (halite-envs/spec-env
              '{:ws/A
                {:spec-vars {:an "Integer", :aw [:Maybe "Integer"], :p "Boolean"}
                 :constraints [["a1" (= aw (when p an))]]
                 :refines-to {}}})
        opts {:default-int-bounds [-10 10]}]

    (is (= '{:spec-vars {:an "Integer", :aw [:Maybe "Integer"], :p "Boolean"}
             :constraints [["$all"
                            (if (if-value aw true true)
                              (if p
                                (if-value aw (= aw an) false)
                                (if-value aw false true))
                              false)]]
             :refines-to {}}
           (hp/spec-ify-bound senv {:$type :ws/A})))

    (are [in out]
         (= out (hp/propagate senv opts in))

      {:$type :ws/A}            {:$type :ws/A, :an {:$in [-10 10]}, :aw {:$in [-10 10 :Unset]}, :p {:$in #{false true}}}
      {:$type :ws/A :p true}    {:$type :ws/A :an {:$in [-10 10]} :aw {:$in [-10 10]}, :p true}
      {:$type :ws/A :p false}   {:$type :ws/A :an {:$in [-10 10]} :aw :Unset, :p false}
      {:$type :ws/A :aw :Unset} {:$type :ws/A :an {:$in [-10 10]} :aw :Unset, :p false})))

(def nested-optionals-spec-env
  '{:ws/A
    {:spec-vars {:b1 [:Maybe :ws/B], :b2 [:Maybe :ws/B], :ap "Boolean"}
     :constraints [["a1" (= b1 b2)]
                   ["a2" (=> ap (if-value b1 true false))]]
     :refines-to {}}
    :ws/B
    {:spec-vars {:bx "Integer", :bw [:Maybe "Integer"], :bp "Boolean", :c1 :ws/C, :c2 [:Maybe :ws/C]}
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
  (binding [ssa/*next-id* (atom 0)]
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
         ::hp/mandatory #{}}))))

(def lower-spec-bound #'hp/lower-spec-bound)

(deftest test-lower-spec-bound-for-nested-optionals
  (binding [ssa/*next-id* (atom 0)]
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

        {:$type :ws/A :b1 {:$type :ws/B :c2 {:$type :ws/C :cw 5}}} '{b1? true, b1|c2? true, b1|c2|cw 5}))))

(def optionality-constraint #'hp/optionality-constraint)

(deftest test-constraints-for-composite-optional
  (binding [ssa/*next-id* (atom 0)]
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
              (:c2 (:b1 flattened-vars))))))))

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
  (schema.core/with-fn-validation
    (let [senv (halite-envs/spec-env
                '{:ws/A
                  {:spec-vars {:b1 [:Maybe :ws/B] :b2 [:Maybe :ws/B]}
                   :constraints [["a1" (= b1 b2)]]
                   :refines-to {}}
                  :ws/B
                  {:spec-vars {:bx "Integer"}
                   :constraints [["b1" (< 0 bx)]]
                   :refines-to {}}})
          opts {:default-int-bounds [-10 10]}]

      (is (= {:$type :ws/A, :b1 {:$type :ws/B, :bx 4}, :b2 {:$type :ws/B, :bx 4}}
             (hp/propagate senv opts {:$type :ws/A :b1 {:$type :ws/B :bx 4}}))))))

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

(def inline-gets #'hp/inline-gets)

(deftest test-inline-gets
  (are [expr result]
       (= result (inline-gets expr))

    1 1
    true true
    'foo 'foo
    '(get foo :bar) '(get foo :bar)
    '(let [x (get foo :bar)] (get x :baz)) '(get (get foo :bar) :baz)))

