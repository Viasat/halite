;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.test-halite-analysis
  (:require [jibe.halite-analysis :as halite-analysis]
            [internal :as s]
            [internal :refer :all]))

(set! *warn-on-reflection* true)

(deftest test-schema
  (s/check halite-analysis/EnumConstraint {:enum #{100}})
  (s/check halite-analysis/Range {:min 100
                                  :min-inclusive true})
  (s/check halite-analysis/Range {:max 100
                                  :min-inclusive true})
  (s/check halite-analysis/Range {:max 100
                                  :max-inclusive true})
  (s/check halite-analysis/Range {:min 1
                                  :min-inclusive false
                                  :max 100
                                  :max-inclusive true})
  (s/check halite-analysis/RangeConstraint {:ranges #{{:min 100
                                                       :min-inclusive true}}})

  (s/check halite-analysis/CollectionConstraint {:coll-size 5})
  (s/check halite-analysis/CollectionConstraint {:coll-elements {:enum #{1 2}}})
  (s/check halite-analysis/CollectionConstraint {:coll-size 5
                                                 :coll-elements {:enum #{1 2}}}))

(deftest test-gather-free-vars
  (are [v expected]
       (= expected
          (halite-analysis/gather-free-vars v))
    1
    #{}

    'x
    '#{x}

    '(+ 1 x y)
    '#{x y}

    '(let [x a
           y x
           a 1
           z a]
       (+ x y z b))
    '#{a b}

    '(and (or (= (+ 1 z))
              b))
    '#{z b}

    '{:$type :my/S$v1
      :x 1
      :y a
      :z (and b {:$type :my/T$v1 :q c})}
    '#{a b c}

    '[1 2 x]
    '#{x}

    '#{a b 3}
    '#{a b}

    '(if x y z)
    '#{x y z}

    '(let [x a
           y x
           a 1
           z a
           p [#{[#{c}]}]]
       (if (+ x y z b)
         (every? [x [a a d]]
                 (+ x e))))
    '#{a b c d e}

    '(every? [x [1 2 3]]
             (+ x 1))
    #{}))

(deftest test-replace-free-vars
  (are [v var-map expected]
       (= expected
          (halite-analysis/replace-free-vars var-map v))
    true
    {}
    true

    'x
    {}
    'x

    'x
    '{x y}
    'y

    '(+ x 1)
    {}
    '(+ x 1)

    '(+ x (let [x 1]
            x))
    '{x y}
    '(+ y (let [x 1]
            x))

    '[x #{y}]
    '{x a
      y b}
    '[a #{b}]

    '(+ x (let [x 1
                a y]
            (+ x a b z)))
    '{x p
      y q
      z r}
    '(+ p (let [x 1
                a q]
            (+ x a b r)))

    '(+ x a b z)
    '{x p
      y q
      z r}
    '(+ p a b r)))

(deftest test-gather-tlfc
  (are [v x]
       (= x
          [(halite-analysis/gather-tlfc v)
           (halite-analysis/sort-tlfc (halite-analysis/gather-tlfc v))
           (halite-analysis/compute-tlfc-map v)])

    true [true
          nil
          {}]

    '(and true ;; this expression references no fields, so no field constraints are extracted
          false)
    ['true
     nil
     {}]

    '(= x 100) ;; field assignments come out as enum values
    ['(= x 100)
     '{x (= x 100)}
     '{x {:enum #{100}}}]

    '(= x (+ 200 3)) [true
                      nil
                      {}]

    '(and (= x 300) ;; multiple fields can be teased apart as long as the clauses are independent
          (= 2 y)) ['(and (= x 300)
                          (= 2 y))
                    '{x (= x 300)
                      y (= 2 y)}
                    '{x {:enum #{300}}
                      y {:enum #{2}}}]

    '(and ;; multiple ands can be walked through
      z ;; even if there are constraints that are not extraced
      (and (= x 400)
           (= 2 y))) ['(and (= x 400)
                            (= 2 y))
                      '{x (= x 400)
                        y (= 2 y)}
                      '{x {:enum #{400}}
                        y {:enum #{2}}}]

    '(< x 500) ;; partial ranges are extracted
    ['(< x 500)
     '{x (< x 500)}
     '{x {:ranges #{{:max 500
                     :max-inclusive false}}}}]

    '(< 510 x) ;; arguments can be in either order
    ['(< 510 x)
     '{x (< 510 x)}
     '{x {:ranges #{{:min 510
                     :min-inclusive true}}}}]

    '(< x y) ;; expressions over multiple fields are not extracted
    [true
     nil
     {}]

    '(= x [1 600]) ;; fields of any type are extracted into enums
    ['(= x [1 600])
     '{x (= x [1 600])}
     '{x {:enum #{[1 600]}}}]

    '(= x {:$type :my/Spec}) ;; instance values are pulled out into enums
    ['(= x {:$type :my/Spec})
     '{x (= x {:$type :my/Spec})}
     '{x {:enum #{{:$type :my/Spec}}}}]

    '(= x [700 z]) ;; no "destructuring" of collections
    [true
     nil
     {}]

    '(let [y 800] ;; no navigating through locals to find literal values
       (< x y)) [true
                 nil
                 {}]

    '(< x (let [y 900] y)) [true
                            nil
                            {}]

    '(contains? #{1000 2 3} x) ;; set containment translated into enums
    ['(contains? #{1000 3 2} x)
     '{x (contains? #{1000 3 2} x)}
     '{x {:enum #{1000 3 2}}}]

    '(contains? #{1050 x 3} 1) ;; no set "deconstruction"
    [true nil {}]

    '(and (contains? #{1100 2 3} x) ;; many fields can be teased apart as long as the clauses are independent 'and' sub-expressions
          (= y 1)
          (and (<= z 20)
               (> z 10))) ['(and (contains? #{1100 3 2} x)
                                 (= y 1)
                                 (<= z 20)
                                 (> z 10))
                           '{x (contains? #{1100 3 2} x),
                             y (= y 1),
                             z (and (<= z 20) (> z 10))}
                           '{x {:enum #{1100 3 2}}
                             y {:enum #{1}}
                             z {:ranges #{{:max 20
                                           :max-inclusive true
                                           :min 10
                                           :min-inclusive false}}}}]
    '(and (contains? #{1200 2 3} x)
          (or a b)
          (and (= y 1)
               (and (<= z 20)
                    (> z 10)))
          q) ['(and
                (contains? #{1200 3 2} x)
                (= y 1)
                (<= z 20)
                (> z 10))
              '{x (contains? #{1200 3 2} x)
                y (= y 1),
                z (and (<= z 20) (> z 10))}
              '{x {:enum #{1200 3 2}}
                y {:enum #{1}}
                z {:ranges #{{:max 20
                              :max-inclusive true
                              :min 10
                              :min-inclusive false}}}}]

    '(or (= x 1300)
         (= x 2)) ['(or (= x 1300) (= x 2))
                   '{x (or (= x 1300) (= x 2))}
                   '{x {:enum #{2 1300}}}]

    '(and (or (= x 1310)
              (= x 2))
          (= y 3)) ['(and (or (= x 1310) (= x 2)) (= y 3))
                    '{x (or (= x 1310) (= x 2))
                      y (= y 3)}
                    '{x {:enum #{1310 2}}
                      y {:enum #{3}}}]

    '(and (contains? #{1400 2 3} x)
          (or a b)
          (every? [c [1 2 3]]
                  (= c 1))
          (= z 1)
          (= y 1)
          (<= z 20)
          (> z 10)
          q) ['(and
                (contains? #{1400 3 2} x)
                (= z 1)
                (= y 1)
                (<= z 20)
                (> z 10))
              '{x (contains? #{1400 3 2} x)
                z (and (= z 1) (<= z 20) (> z 10)),
                y (= y 1)}
              '{x {:enum #{1400 3 2}}
                z {:enum #{}}
                y {:enum #{1}}}]

    '(= [1 1500] [x 1500]) [true
                            nil
                            {}]

    '(= #{x 1600} #{1 1600}) [true
                              nil
                              {}]

    '(or (and (>= x 3)
              (< x 12))
         (and (>= x 20)
              (< x 1700))) ['(or (and (>= x 3) (< x 12))
                                 (and (>= x 20) (< x 1700)))
                            '{x (or (and (>= x 3) (< x 12))
                                    (and (>= x 20) (< x 1700)))}
                            '{x {:ranges #{{:min 3
                                            :min-inclusive true
                                            :max 12
                                            :max-inclusive false}
                                           {:min 20
                                            :min-inclusive true
                                            :max 1700
                                            :max-inclusive false}}}}]

    '(or (and (>= x 3)
              (< x 13)
              (<= x 9)
              (< x 12)
              (> 14 x))
         (and (>= x 20)
              (< x 1800))) ['(or
                              (and (>= x 3) (< x 13) (<= x 9) (< x 12) (> 14 x))
                              (and (>= x 20) (< x 1800)))
                            '{x (or
                                 (and (>= x 3) (< x 13) (<= x 9) (< x 12) (> 14 x))
                                 (and (>= x 20) (< x 1800)))}
                            '{x {:ranges #{{:min 3
                                            :min-inclusive true
                                            :max 9
                                            :max-inclusive true}
                                           {:min 20
                                            :min-inclusive true
                                            :max 1800
                                            :max-inclusive false}}}}]

    '(or (< x 1)
         (= x 2)
         (< x 1900)) ['(or (< x 1) (= x 2) (< x 1900))
                      '{x (or (< x 1) (= x 2) (< x 1900))}
                      {}]
    '(or (< x 1)
         (< x 2000)) ['(or (< x 1) (< x 2000))
                      '{x (or (< x 1) (< x 2000))}
                      '{x {:ranges #{{:max 2000
                                      :max-inclusive false}}}}]

    '(or (< x 2100)
         (<= x 2100)) ['(or (< x 2100) (<= x 2100))
                       '{x (or (< x 2100) (<= x 2100))}
                       '{x {:ranges #{{:max 2100
                                       :max-inclusive true}}}}]

    '(or (< x 2200)
         (>= 2200 x)) ['(or (< x 2200) (>= 2200 x))
                       '{x (or (< x 2200) (>= 2200 x))}
                       '{x {:ranges #{{:max 2200
                                       :max-inclusive false}}}}]

    '(and (< x 2300)
          (>= 2300 x)) ['(and (< x 2300) (>= 2300 x))
                        '{x (and (< x 2300) (>= 2300 x))}
                        '{x {:ranges #{{:max 2300
                                        :max-inclusive false}}}}]

    '(or (< x 2400)
         (<= x 2400)) ['(or (< x 2400) (<= x 2400))
                       '{x (or (< x 2400) (<= x 2400))}
                       '{x {:ranges #{{:max 2400
                                       :max-inclusive true}}}}]

    '(and (< x 2500)
          (<= x 2500)) ['(and (< x 2500) (<= x 2500))
                        '{x (and (< x 2500) (<= x 2500))}
                        '{x {:ranges #{{:max 2500
                                        :max-inclusive false}}}}]

    '(or (> x 2600)
         (>= x 2600)) ['(or (> x 2600) (>= x 2600))
                       '{x (or (> x 2600) (>= x 2600))}
                       '{x {:ranges #{{:min 2600
                                       :min-inclusive true}}}}]

    '(and (> x 2700)
          (>= x 2700)) ['(and (> x 2700) (>= x 2700))
                        '{x (and (> x 2700) (>= x 2700))}
                        '{x {:ranges #{{:min 2700
                                        :min-inclusive false}}}}]

    '(or (and (> x 2800)
              (<= x 2850))
         (and (> x 2840)
              (<= x 2860))) ['(or (and (> x 2800) (<= x 2850))
                                  (and (> x 2840) (<= x 2860)))
                             '{x (or
                                  (and (> x 2800) (<= x 2850))
                                  (and (> x 2840) (<= x 2860)))}
                             '{x {:ranges #{{:min 2800
                                             :min-inclusive false
                                             :max 2860
                                             :max-inclusive true}}}}]

    '(or (and (> x 2900)
              (< x 2950))
         (and (>= x 2950)
              (<= x 2960))) ['(or (and (> x 2900) (< x 2950))
                                  (and (>= x 2950) (<= x 2960)))
                             '{x (or (and (> x 2900) (< x 2950))
                                     (and (>= x 2950) (<= x 2960)))}
                             '{x {:ranges #{{:min 2900
                                             :min-inclusive false
                                             :max 2960
                                             :max-inclusive true}}}}]

    '(or (and (> x 3000)
              (<= x 3050))
         (and (> x 3040)
              (<= x 3060))
         (> x 1)) ['(or (and (> x 3000) (<= x 3050))
                        (and (> x 3040) (<= x 3060))
                        (> x 1))
                   '{x (or (and (> x 3000) (<= x 3050))
                           (and (> x 3040) (<= x 3060))
                           (> x 1))}
                   '{x {:ranges #{{:min 1
                                   :min-inclusive false
                                   :max 3060
                                   :max-inclusive true}}}}]

    '(and (> x 1)
          (let [x 5] ;; this x does not collide with the outer x, this clause is ignored since it is in 'and'
            (> x 6))
          (or (and (> x 3100)
                   (<= x 3150))
              (and (> x 3140)
                   (<= x 3160)))) ['(and (> x 1)
                                         (or
                                          (and (> x 3100) (<= x 3150))
                                          (and (> x 3140) (<= x 3160))))
                                   '{x (and (> x 1)
                                            (or
                                             (and (> x 3100) (<= x 3150))
                                             (and (> x 3140) (<= x 3160))))}
                                   '{x {:ranges #{{:min 3100
                                                   :min-inclusive false
                                                   :max 3160
                                                   :max-inclusive true}}}}]

    '(and (> x 3225) ;; this is folded into the rest of the range restriction
          (= b x) ;; this is ignored, but it is in an 'and', so the other expressions remain
          (or (and (> x 3200)
                   (<= x 3250))
              (and (> x 3240)
                   (<= x 3260)))) ['(and (> x 3225)
                                         (or
                                          (and (> x 3200) (<= x 3250))
                                          (and (> x 3240) (<= x 3260))))
                                   '{x (and (> x 3225)
                                            (or
                                             (and (> x 3200) (<= x 3250))
                                             (and (> x 3240) (<= x 3260))))}
                                   '{x {:ranges #{{:min 3225
                                                   :min-inclusive false
                                                   :max 3260
                                                   :max-inclusive true}}}}]

    '(and (> x 3275)
          (= b x)
          (or (and (> x 3270)
                   (<= x 3280))
              (and (> x 3285)
                   (<= x 3295))
              (and (> x 3260)
                   (<= x 3265)))
          (< x 3290)) ['(and (> x 3275)
                             (or (and (> x 3270) (<= x 3280))
                                 (and (> x 3285) (<= x 3295))
                                 (and (> x 3260) (<= x 3265)))
                             (< x 3290))
                       '{x (and (> x 3275)
                                (or (and (> x 3270) (<= x 3280))
                                    (and (> x 3285) (<= x 3295))
                                    (and (> x 3260) (<= x 3265)))
                                (< x 3290))}
                       '{x {:ranges
                            #{{:min 3275
                               :min-inclusive false
                               :max 3280
                               :max-inclusive true}
                              {:min 3285
                               :min-inclusive false
                               :max 3290
                               :max-inclusive false}}}}]

    '(and ;; extra 'and' at root makes no difference
      (and (= x 3325)
           (or (and (> x 3300)
                    (<= x 3350))
               (and (> x 3340)
                    (<= x 3360))))) ['(and (= x 3325)
                                           (or (and (> x 3300) (<= x 3350))
                                               (and (> x 3340) (<= x 3360))))
                                     '{x (and
                                          (= x 3325)
                                          (or (and (> x 3300) (<= x 3350))
                                              (and (> x 3340) (<= x 3360))))}
                                     '{x {:enum #{3325}}}]
    '(and (= x 0)
          (or (and (> x 3400)
                   (<= x 3450))
              (and (> x 3440)
                   (<= x 3460)))) ['(and (= x 0)
                                         (or (and (> x 3400) (<= x 3450))
                                             (and (> x 3440) (<= x 3460))))
                                   '{x (and (= x 0)
                                            (or (and (> x 3400) (<= x 3450))
                                                (and (> x 3440) (<= x 3460))))}
                                   '{x {:enum #{}}} ;; empty because of the range
                                   ]

    '(and (= x 3500)
          (= x 3500)
          (< x 16)
          (>= x 10)) ['(and (= x 3500) (= x 3500) (< x 16) (>= x 10))
                      '{x (and (= x 3500) (= x 3500) (< x 16) (>= x 10))}
                      '{x {:enum #{}}}]

    '(and (< x 3501)
          (or (= x 10)
              (= x 20))
          (> x 16)) ['(and (< x 3501) (or (= x 10) (= x 20)) (> x 16))
                     '{x (and (< x 3501) (or (= x 10) (= x 20)) (> x 16))}
                     '{x {:enum #{20}}}]

    '(and
      (or (= x 10)
          (= x 20)
          (= x 0))
      (or (and
           (< x 3550)
           (> x 16))
          (and
           (< x 15)
           (> x 5)))) ['(and (or (= x 10)
                                 (= x 20)
                                 (= x 0))
                             (or (and (< x 3550)
                                      (> x 16))
                                 (and (< x 15)
                                      (> x 5))))
                       '{x (and (or (= x 10)
                                    (= x 20)
                                    (= x 0))
                                (or (and (< x 3550)
                                         (> x 16))
                                    (and (< x 15)
                                         (> x 5))))}
                       '{x {:enum #{20 10}}}] ;; if enum values fall in any of the alternate ranges they are included

    '(or ;; or at the root with mixed enum and ranges foils the logic to pull out direct field constraints
      (or (= x 3600)
          (= x 1))
      (and (< x 16)
           (>= x 10))) [true nil {}]

    '(or (and (> x 3700))
         q ;; an extra clause in an 'or' foils attempts to lift out mandatory values for x
         (and (>= x 3750)
              (<= x 3760))) [true nil {}]

    '(= x y z 3800) ;; only binary '=' are extracted
    [true nil {}]

    '(every? [x a]
             (= x 3900)) ['[(= a 3900)]
                          '{a [(= a 3900)]}
                          '{a {:coll-elements {:enum #{3900}}}}]

    '(every? [x a]
             (and (= x 4000)
                  y)) [true nil {}]

    '(every? [x a]
             (every? [y x]
                     (= y 4100))) ['[[(= a 4100)]]
                                   '{a [[(= a 4100)]]}
                                   '{a {:coll-elements {:coll-elements {:enum #{4100}}}}}]

    '(every? [x a]
             (every? [y x]
                     (> y 4150))) ['[[(> a 4150)]]
                                   '{a [[(> a 4150)]]}
                                   '{a {:coll-elements {:coll-elements {:ranges #{{:min 4150
                                                                                   :min-inclusive false}}}}}}]

    '(every? [x a]
             (every? [y x]
                     (or (and (> y 4200)
                              (<= y 4210))
                         (and (< y 5)
                              (> y 0))))) ['[[(or (and (> a 4200) (<= a 4210)) (and (< a 5) (> a 0)))]]
                                           '{a [[(or
                                                  (and (> a 4200) (<= a 4210))
                                                  (and (< a 5) (> a 0)))]]}
                                           '{a {:coll-elements {:coll-elements {:ranges #{{:max 5
                                                                                           :max-inclusive false
                                                                                           :min 0
                                                                                           :min-inclusive false}
                                                                                          {:min 4200
                                                                                           :min-inclusive false
                                                                                           :max 4210
                                                                                           :max-inclusive true}}}}}}]

    '(= x "4300") ['(= x "4300")
                   '{x (= x "4300")}
                   '{x {:enum #{"4300"}}}]

    '(contains? #{"4400" "a" "b"} x) ['(contains? #{"a" "b" "4400"} x)
                                      '{x (contains? #{"a" "b" "4400"} x)}
                                      '{x {:enum #{"a" "b" "4400"}}}]

    '(= 4500 (count x)) ['(= 4500 (count x))
                         '{x (= 4500 (count x))}
                         '{x {:coll-size 4500}}]
    '(= (count x) 4510) ['(= (count x) 4510)
                         '{x (= (count x) 4510)}
                         '{x {:coll-size 4510}}]

    '(every? [x a]
             (= 4600 (count x))) ['[(= 4600 (count a))]
                                  '{a [(= 4600 (count a))]}
                                  '{a {:coll-elements {:coll-size 4600}}}]

    '(and (= (count a) 4700)
          (every? [x a]
                  (and (= 5 (count x))
                       (every? [y x]
                               (or (= y "a")
                                   (= y "b")))))) ['(and (= (count a) 4700)
                                                         [(and (= 5 (count a))
                                                               [(or (= a "a") (= a "b"))])])
                                                   '{a (and (= (count a) 4700)
                                                            [(and (= 5 (count a))
                                                                  [(or (= a "a") (= a "b"))])])}
                                                   '{a {:coll-size 4700
                                                        :coll-elements {:coll-size 5
                                                                        :coll-elements {:enum #{"a" "b"}}}}}]

    '(or (= (count a) 4800)
         (every? [x a]
                 (and (= 5 (count x))
                      (every? [y x]
                              (or (= y "a")
                                  (= y "b")))))) [true nil {}]

    '(and (every? [v a]
                  (or (= v ["a"])
                      (= v ["a" "b"])))
          (every? [v a]
                  (every? [s v]
                          (or (= s "a")
                              (= s "b")
                              (= s "4895"))))) ['(and [(or (= a ["a"]) (= a ["a" "b"]))]
                                                      [[(or (= a "a") (= a "b") (= a "4895"))]])
                                                '{a (and
                                                     [(or (= a ["a"]) (= a ["a" "b"]))]
                                                     [[(or (= a "a") (= a "b") (= a "4895"))]])}
                                                '{a {:coll-elements {:coll-elements {:enum #{"a" "b" "4895"}}
                                                                     :enum #{["a" "b"] ["a"]}}}}]

    '(or (= "a" x)
         (= x 4900)) ['(or (= "a" x) (= x 4900))
                      '{x (or (= "a" x) (= x 4900))}
                      '{x {:enum #{"a" 4900}}}]

    '(or (= {:$type :spec/A
             :a 5000} x)
         (contains? #{{:$type :spec/B}
                      {:$type :spec/C}} x)) ['(or (= {:$type :spec/A
                                                      :a 5000} x)
                                                  (contains? #{{:$type :spec/C}
                                                               {:$type :spec/B}} x))
                                             '{x (or (= {:$type :spec/A
                                                         :a 5000} x)
                                                     (contains? #{{:$type :spec/C}
                                                                  {:$type :spec/B}} x))}
                                             '{x {:enum #{{:$type :spec/C}
                                                          {:$type :spec/B}
                                                          {:$type :spec/A
                                                           :a 5000}}}}]

    '(or (= {:$type :spec/A
             :a 5100} x)
         (= x {:$type :spec/B})) ['(or (= {:$type :spec/A
                                           :a 5100} x)
                                       (= x {:$type :spec/B}))
                                  '{x (or (= {:$type :spec/A
                                              :a 5100} x)
                                          (= x {:$type :spec/B}))}
                                  '{x {:enum #{{:$type :spec/A
                                                :a 5100}
                                               {:$type :spec/B}}}}]

    '(and (<= z 10) ;; what to do if the min is greater than the max?
          (> z 5200)) ['(and (<= z 10)
                             (> z 5200))
                       '{z (and (<= z 10)
                                (> z 5200))}
                       '{z {:ranges #{{:max 10
                                       :max-inclusive true
                                       :min 5200
                                       :min-inclusive false}}}}]

    '(or (and (<= z 10) ;; sensible ranges are not combined with non-sensical ranges
              (> z 5300))
         (and (<= z 30)
              (> z 20))) ['(or (and (<= z 10)
                                    (> z 5300))
                               (and (<= z 30)
                                    (> z 20)))
                          '{z (or (and
                                   (<= z 10)
                                   (> z 5300))
                                  (and (<= z 30)
                                       (> z 20)))}
                          '{z {:ranges #{{:max 10
                                          :max-inclusive true
                                          :min 5300
                                          :min-inclusive false}
                                         {:max 30
                                          :max-inclusive true
                                          :min 20
                                          :min-inclusive false}}}}]

    '(or (and (> #d "1.5400" x)
              (>= x #d "0.0000"))

         (and (> x #d "20.0000")
              (< x #d "30.0000"))) ['(or (and (> #d "1.5400" x)
                                              (>= x #d "0.0000"))
                                         (and (> x #d "20.0000")
                                              (< x #d "30.0000")))
                                    '{x (or (and (> #d "1.5400" x)
                                                 (>= x #d "0.0000"))
                                            (and (> x #d "20.0000")
                                                 (< x #d "30.0000")))}
                                    '{x {:ranges #{{:max #d "1.5400"
                                                    :max-inclusive true
                                                    :min #d "0.0000"
                                                    :min-inclusive true}
                                                   {:min #d "20.0000"
                                                    :min-inclusive false
                                                    :max #d "30.0000"
                                                    :max-inclusive false}}}}]

    #_'(or (and (> #d "1.5500" x) ;; inconsistent data types throw
                (>= x 0))
           (and (> x 20)
                (< x 30))) #_['(or (and (> #d "1.5500" x)
                                        (>= x 0))
                                   (and (> x 20)
                                        (< x 30)))
                              '{x (or (and (> #d "1.5500" x)
                                           (>= x 0))
                                      (and (> x 20)
                                           (< x 30)))}
                              '{x {:ranges #{{:max #d "1.5500"
                                              :max-inclusive true
                                              :min 0
                                              :min-inclusive true}
                                             {:min 20
                                              :min-inclusive false
                                              :max 30
                                              :max-inclusive false}}}}]

    '(or (= #d "5600.12" x)
         (= x #d "2.24")
         (contains? #{#d "1.00" #d "2.00"} x)) ['(or (= #d "5600.12" x)
                                                     (= x #d "2.24")
                                                     (contains? #{#d "2.00" #d "1.00"} x))
                                                '{x (or (= #d "5600.12" x)
                                                        (= x #d "2.24")
                                                        (contains? #{#d "2.00" #d "1.00"} x))}
                                                '{x {:enum #{#d "5600.12" #d "2.24" #d "2.00" #d "1.00"}}}]
    '(or (= 5700 x)
         (= x 2)
         (contains? #{1 2} x)) ['(or (= 5700 x)
                                     (= x 2)
                                     (contains? #{1 2} x))
                                '{x (or (= 5700 x)
                                        (= x 2)
                                        (contains? #{1 2} x))}
                                '{x {:enum #{1 2 5700}}}]

    '(and (= #d "5800.12" x)
          (= x #d "2.24")
          (contains? #{#d "1.00" #d "2.00"} x)) ['(and (= #d "5800.12" x)
                                                       (= x #d "2.24")
                                                       (contains? #{#d "2.00" #d "1.00"} x))
                                                 '{x (and (= #d "5800.12" x)
                                                          (= x #d "2.24")
                                                          (contains? #{#d "2.00" #d "1.00"} x))}
                                                 '{x {:enum #{}}}]
    '(and (= 5900 x)
          (= x 2)
          (contains? #{1 2} x)) ['(and (= 5900 x) (= x 2) (contains? #{1 2} x))
                                 '{x (and (= 5900 x) (= x 2) (contains? #{1 2} x))}
                                 '{x {:enum #{}}}]

    '(if-value x (= x 6000) true) ['(= x 6000)
                                   '{x (= x 6000)}
                                   '{x {:enum #{6000}}}]

    '(if-value x
               (or (= x "6100")
                   (= x "no")
                   (= x "yes"))
               true) ['(or (= x "6100")
                           (= x "no")
                           (= x "yes"))
                      '{x (or (= x "6100")
                              (= x "no")
                              (= x "yes"))}
                      '{x {:enum #{"6100" "yes" "no"}}}]

    '(if-value y (= z 6200) true) [true nil {}]

    '(if-value x (<= 6300 x) true) ['(<= 6300 x)
                                    '{x (<= 6300 x)}
                                    '{x {:ranges #{{:min 6300
                                                    :min-inclusive false}}}}]

    '(and (if-value x (<= 6400 x) true)
          (if-value x (> 0 x) true)) ['(and (<= 6400 x) (> 0 x))
                                      '{x (and (<= 6400 x) (> 0 x))}
                                      '{x {:ranges #{{:min 6400
                                                      :min-inclusive false
                                                      :max 0
                                                      :max-inclusive true}}}}]

    '(or (if-value x (<= 6500 x) true)
         (if-value x (> 0 x) true)) [true nil {}]

    '(any? [n #{1 6600}]
           (= x n)) [true nil {}]))

;; (run-tests)
