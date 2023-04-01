;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-analysis
  (:require [clojure.test :refer :all]
            [com.viasat.halite.analysis :as analysis]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.var-types :as var-types]
            [schema.core :as s]
            [schema.test :as schema.test]))

(set! *warn-on-reflection* true)

(use-fixtures :once schema.test/validate-schemas)

(deftest test-schema
  (s/check analysis/EnumConstraint {:enum #{100}})
  (s/check analysis/Range {:min 100
                           :min-inclusive true})
  (s/check analysis/Range {:max 100
                           :min-inclusive true})
  (s/check analysis/Range {:max 100
                           :max-inclusive true})
  (s/check analysis/Range {:min 1
                           :min-inclusive false
                           :max 100
                           :max-inclusive true})
  (s/check analysis/RangeConstraint {:ranges #{{:min 100
                                                :min-inclusive true}}})

  (s/check analysis/CollectionConstraint {:coll-size 5})
  (s/check analysis/CollectionConstraint {:coll-elements {:enum #{1 2}}})
  (s/check analysis/CollectionConstraint {:coll-size 5
                                          :coll-elements {:enum #{1 2}}}))

(deftest test-gather-free-vars
  (are [v expected]
       (= expected
          (analysis/gather-free-vars v))
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
          (analysis/replace-free-vars var-map v))
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
    '(+ p a b r)

    ;;

    '(if-value x 1 0)
    '{x a}
    '1

    '(if-value x 1 0)
    '{x 99}
    '1

    '(if-value x 1 0)
    '{x (when-value a a)}
    '(if-value a 1 0)

    '(if-value x 1 0)
    '{x (when-value a (+ a 1))}
    '(if-value-let [x (when-value a (+ a 1))]
                   1 0)

    '(if-value x 1 0)
    '{x (if-value a (when-value b b) c)}
    '(if-value-let [x (if-value a (when-value b b) c)] 1 0)

    ;;

    '(if-value x x y)
    '{x a
      y b}
    'a

    '(if-value x x y)
    '{x 99
      y b}
    '99

    '(if-value x x y)
    '{x (when-value a a)
      y b}
    '(if-value a a b)

    '(if-value x x y)
    '{x (when-value a (+ a 1))
      y b}
    '(if-value-let [x (when-value a (+ a 1))]
                   x b)

    '(if-value x x y)
    '{x (if-value a (when-value b b) c)
      y b}
    '(if-value-let [x (if-value a (when-value b b) c)] x b)

    ;;

    '(when-value x y)
    '{x a
      y b}
    'b

    '(when-value x y)
    '{x 99
      y b}
    'b

    '(when-value x y)
    '{x (when-value a a)
      y b}
    '(when-value a b)

    '(when-value x y)
    '{x (when-value a (+ a 1))
      y b}
    '(when-value-let [x (when-value a (+ a 1))]
                     b)

    '(when-value x y)
    '{x (if-value a (when-value b b) c)
      y b}
    '(when-value-let [x (if-value a (when-value b b) c)] b)

    ;;

    '(when-value x 1)
    '{x a}
    '1

    '(when-value x 1)
    '{x 99}
    '1

    '(when-value x 1)
    '{x (when-value a a)}
    '(when-value a 1)

    '(when-value x 1)
    '{x (when-value a (+ a 1))}
    '(when-value-let [x (when-value a (+ a 1))]
                     1)

    '(when-value x 1)
    '{x (if-value a (when-value b b) c)}
    '(when-value-let [x (if-value a (when-value b b) c)] 1)))

(deftest test-gather-tlfc
  (are [v x]
       (= x
          [(analysis/gather-tlfc v)
           (analysis/sort-tlfc (analysis/gather-tlfc v))
           (binding [analysis/*max-enum-size* 2]
             (analysis/compute-tlfc-map v))
           (binding [analysis/*max-enum-size* 10]
             (analysis/compute-tlfc-map v))])

    true [true
          nil
          {}
          {}]

    '(and true ;; this expression references no fields, so no field constraints are extracted
          false)
    ['true
     nil
     {}
     {}]

    '(= x 100) ;; field assignments come out as enum values
    ['(= x 100)
     '{x (= x 100)}
     '{x {:enum #{100}}}
     '{x {:enum #{100}}}]

    '(= x (+ 200 3)) [true
                      nil
                      {}
                      {}]

    '(and (= x 300) ;; multiple fields can be teased apart as long as the clauses are independent
          (= 2 y)) ['(and (= x 300)
                          (= 2 y))
                    '{x (= x 300)
                      y (= 2 y)}
                    '{x {:enum #{300}}
                      y {:enum #{2}}}
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
                        y {:enum #{2}}}
                      '{x {:enum #{400}}
                        y {:enum #{2}}}]

    '(< x 500) ;; partial ranges are extracted
    ['(< x 500)
     '{x (< x 500)}
     '{x {:ranges #{{:max 500
                     :max-inclusive false}}}}
     '{x {:ranges #{{:max 500
                     :max-inclusive false}}}}]

    '(and (and (< x 505) ;; mutually exclusive ranges are detected as no value can satisfy
               (> x 0))
          (and (< x 1505)
               (> x 1000)))
    ['(and (< x 505) (> x 0) (< x 1505) (> x 1000))
     '{x (and (< x 505) (> x 0) (< x 1505) (> x 1000))}
     '{x {:enum #{}}}
     '{x {:enum #{}}}]

    '(< 510 x) ;; arguments can be in either order
    ['(< 510 x)
     '{x (< 510 x)}
     '{x {:ranges #{{:min 510
                     :min-inclusive true}}}}
     '{x {:ranges #{{:min 510
                     :min-inclusive true}}}}]

    '(< x y) ;; expressions over multiple fields are not extracted
    [true
     nil
     {}
     {}]

    '(= x [1 600]) ;; fields of any type are extracted into enums
    ['(= x [1 600])
     '{x (= x [1 600])}
     '{x {:enum #{[1 600]}}}
     '{x {:enum #{[1 600]}}}]

    '(= x {:$type :my/Spec}) ;; instance values are pulled out into enums
    ['(= x {:$type :my/Spec})
     '{x (= x {:$type :my/Spec})}
     '{x {:enum #{{:$type :my/Spec}}}}
     '{x {:enum #{{:$type :my/Spec}}}}]

    '(= x [700 z]) ;; no "destructuring" of collections
    [true
     nil
     {}
     {}]

    '(let [y 800] ;; no navigating through locals to find literal values
       (< x y)) [true
                 nil
                 {}
                 {}]

    '(< x (let [y 900] y)) [true
                            nil
                            {}
                            {}]

    '(contains? #{1000 2 3} x) ;; set containment translated into enums
    ['(contains? #{1000 3 2} x)
     '{x (contains? #{1000 3 2} x)}
     '{x {:enum #{1000 3 2}}}
     '{x {:enum #{1000 3 2}}}]

    '(contains? #{1050 x 3} 1) ;; no set "deconstruction"
    [true nil {} {}]

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
                                           :min-inclusive false}}}}
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
                              :min-inclusive false}}}}
              '{x {:enum #{1200 3 2}}
                y {:enum #{1}}
                z {:ranges #{{:max 20
                              :max-inclusive true
                              :min 10
                              :min-inclusive false}}}}]

    '(or (= x 1300)
         (= x 2)) ['(or (= x 1300) (= x 2))
                   '{x (or (= x 1300) (= x 2))}
                   '{x {:enum #{2 1300}}}
                   '{x {:enum #{2 1300}}}]

    '(and (or (= x 1310)
              (= x 2))
          (= y 3)) ['(and (or (= x 1310) (= x 2)) (= y 3))
                    '{x (or (= x 1310) (= x 2))
                      y (= y 3)}
                    '{x {:enum #{1310 2}}
                      y {:enum #{3}}}
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
                y {:enum #{1}}}
              '{x {:enum #{1400 3 2}}
                z {:enum #{}}
                y {:enum #{1}}}]

    '(= [1 1500] [x 1500]) [true nil {} {}]

    '(= #{x 1600} #{1 1600}) [true nil {} {}]

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
                                            :max-inclusive false}}}}
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
                                            :max-inclusive false}}}}
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
                      {}
                      {}]
    '(or (< x 1)
         (< x 2000)) ['(or (< x 1) (< x 2000))
                      '{x (or (< x 1) (< x 2000))}
                      '{x {:ranges #{{:max 2000
                                      :max-inclusive false}}}}
                      '{x {:ranges #{{:max 2000
                                      :max-inclusive false}}}}]

    '(or (< x 2100)
         (<= x 2100)) ['(or (< x 2100) (<= x 2100))
                       '{x (or (< x 2100) (<= x 2100))}
                       '{x {:ranges #{{:max 2100
                                       :max-inclusive true}}}}
                       '{x {:ranges #{{:max 2100
                                       :max-inclusive true}}}}]

    '(or (< x 2200)
         (>= 2200 x)) ['(or (< x 2200) (>= 2200 x))
                       '{x (or (< x 2200) (>= 2200 x))}
                       '{x {:ranges #{{:max 2200
                                       :max-inclusive false}}}}
                       '{x {:ranges #{{:max 2200
                                       :max-inclusive false}}}}]

    '(and (< x 2300)
          (>= 2300 x)) ['(and (< x 2300) (>= 2300 x))
                        '{x (and (< x 2300) (>= 2300 x))}
                        '{x {:ranges #{{:max 2300
                                        :max-inclusive false}}}}
                        '{x {:ranges #{{:max 2300
                                        :max-inclusive false}}}}]

    '(or (< x 2400)
         (<= x 2400)) ['(or (< x 2400) (<= x 2400))
                       '{x (or (< x 2400) (<= x 2400))}
                       '{x {:ranges #{{:max 2400
                                       :max-inclusive true}}}}
                       '{x {:ranges #{{:max 2400
                                       :max-inclusive true}}}}]

    '(and (< x 2500)
          (<= x 2500)) ['(and (< x 2500) (<= x 2500))
                        '{x (and (< x 2500) (<= x 2500))}
                        '{x {:ranges #{{:max 2500
                                        :max-inclusive false}}}}
                        '{x {:ranges #{{:max 2500
                                        :max-inclusive false}}}}]

    '(or (> x 2600)
         (>= x 2600)) ['(or (> x 2600) (>= x 2600))
                       '{x (or (> x 2600) (>= x 2600))}
                       '{x {:ranges #{{:min 2600
                                       :min-inclusive true}}}}
                       '{x {:ranges #{{:min 2600
                                       :min-inclusive true}}}}]

    '(and (> x 2700)
          (>= x 2700)) ['(and (> x 2700) (>= x 2700))
                        '{x (and (> x 2700) (>= x 2700))}
                        '{x {:ranges #{{:min 2700
                                        :min-inclusive false}}}}
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
                                             :max-inclusive true}}}}
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
                                             :max-inclusive true}}}}
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
                                   :max-inclusive true}}}}
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
                                                   :max-inclusive true}}}}
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
                                                   :max-inclusive true}}}}
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
                       '{x {:ranges #{{:min 3275
                                       :min-inclusive false
                                       :max 3280
                                       :max-inclusive true}
                                      {:min 3285
                                       :min-inclusive false
                                       :max 3290
                                       :max-inclusive false}}}}
                       '{x {:enum #{3276 3277 3278 3279 3280
                                    3286 3287 3288 3289}}}]

    '(and (> x #d "32.75")
          (= b x)
          (or (and (> x #d "32.70")
                   (<= x #d "32.80"))
              (and (> x #d "32.85")
                   (<= x #d "32.95"))
              (and (> x #d "32.60")
                   (<= x #d "32.65")))
          (< x #d "32.90")) ['(and (> x #d "32.75")
                                   (or (and (> x #d "32.70") (<= x #d "32.80"))
                                       (and (> x #d "32.85") (<= x #d "32.95"))
                                       (and (> x #d "32.60") (<= x #d "32.65")))
                                   (< x #d "32.90"))
                             '{x (and (> x #d "32.75")
                                      (or (and (> x #d "32.70") (<= x #d "32.80"))
                                          (and (> x #d "32.85") (<= x #d "32.95"))
                                          (and (> x #d "32.60") (<= x #d "32.65")))
                                      (< x #d "32.90"))}
                             '{x {:ranges #{{:min #d "32.75"
                                             :min-inclusive false
                                             :max #d "32.80"
                                             :max-inclusive true}
                                            {:min #d "32.85"
                                             :min-inclusive false
                                             :max #d "32.90"
                                             :max-inclusive false}}}}
                             '{x {:enum #{#d "32.76" #d "32.77" #d "32.78" #d "32.79" #d "32.80"
                                          #d "32.86" #d "32.87" #d "32.88" #d "32.89"}}}]

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
                                     '{x {:enum #{3325}}}
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
                                   '{x {:enum #{}}}]

    '(and (= x 3500)
          (= x 3500)
          (< x 16)
          (>= x 10)) ['(and (= x 3500) (= x 3500) (< x 16) (>= x 10))
                      '{x (and (= x 3500) (= x 3500) (< x 16) (>= x 10))}
                      '{x {:enum #{}}}
                      '{x {:enum #{}}}]

    '(and (< x 3501)
          (or (= x 10)
              (= x 20))
          (> x 16)) ['(and (< x 3501) (or (= x 10) (= x 20)) (> x 16))
                     '{x (and (< x 3501) (or (= x 10) (= x 20)) (> x 16))}
                     '{x {:enum #{20}}}
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
                       '{x {:enum #{20 10}}} ;; if enum values fall in any of the alternate ranges they are included
                       '{x {:enum #{20 10}}}]

    '(or ;; or at the root with mixed enum and ranges foils the logic to pull out direct field constraints
      (or (= x 3600)
          (= x 1))
      (and (< x 16)
           (>= x 10))) [true nil {} {}]

    '(or (and (> x 3700))
         q ;; an extra clause in an 'or' foils attempts to lift out mandatory values for x
         (and (>= x 3750)
              (<= x 3760))) [true nil {} {}]

    '(= x y z 3800) ;; only binary '=' are extracted
    [true nil {} {}]

    '(every? [x a]
             (= x 3900)) ['[(= a 3900)]
                          '{a [(= a 3900)]}
                          '{a {:coll-elements {:enum #{3900}}}}
                          '{a {:coll-elements {:enum #{3900}}}}]

    '(every? [x a]
             (and (= x 4000)
                  y)) [true nil {} {}]

    '(every? [x a]
             (every? [y x]
                     (= y 4100))) ['[[(= a 4100)]]
                                   '{a [[(= a 4100)]]}
                                   '{a {:coll-elements {:coll-elements {:enum #{4100}}}}}
                                   '{a {:coll-elements {:coll-elements {:enum #{4100}}}}}]

    '(every? [x a]
             (every? [y x]
                     (> y 4150))) ['[[(> a 4150)]]
                                   '{a [[(> a 4150)]]}
                                   '{a {:coll-elements {:coll-elements {:ranges #{{:min 4150
                                                                                   :min-inclusive false}}}}}}
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
                                                                                           :max-inclusive true}}}}}}
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
                   '{x {:enum #{"4300"}}}
                   '{x {:enum #{"4300"}}}]

    '(contains? #{"4400" "a" "b"} x) ['(contains? #{"a" "b" "4400"} x)
                                      '{x (contains? #{"a" "b" "4400"} x)}
                                      '{x {:enum #{"a" "b" "4400"}}}
                                      '{x {:enum #{"a" "b" "4400"}}}]

    '(= 4500 (count x)) ['(= 4500 (count x))
                         '{x (= 4500 (count x))}
                         '{x {:coll-size 4500}}
                         '{x {:coll-size 4500}}]

    '(= (count x) 4510) ['(= (count x) 4510)
                         '{x (= (count x) 4510)}
                         '{x {:coll-size 4510}}
                         '{x {:coll-size 4510}}]

    '(every? [x a]
             (= 4600 (count x))) ['[(= 4600 (count a))]
                                  '{a [(= 4600 (count a))]}
                                  '{a {:coll-elements {:coll-size 4600}}}
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
                                                                        :coll-elements {:enum #{"a" "b"}}}}}
                                                   '{a {:coll-size 4700
                                                        :coll-elements {:coll-size 5
                                                                        :coll-elements {:enum #{"a" "b"}}}}}]

    '(or (= (count a) 4800)
         (every? [x a]
                 (and (= 5 (count x))
                      (every? [y x]
                              (or (= y "a")
                                  (= y "b")))))) [true nil {} {}]

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
                                                                     :enum #{["a" "b"] ["a"]}}}}
                                                '{a {:coll-elements {:coll-elements {:enum #{"a" "b" "4895"}}
                                                                     :enum #{["a" "b"] ["a"]}}}}]

    '(or (= "a" x)
         (= x 4900)) ['(or (= "a" x) (= x 4900))
                      '{x (or (= "a" x) (= x 4900))}
                      '{x {:enum #{"a" 4900}}}
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
                                                           :a 5000}}}}
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
                                               {:$type :spec/B}}}}
                                  '{x {:enum #{{:$type :spec/A
                                                :a 5100}
                                               {:$type :spec/B}}}}]

    '(and (<= z 10) ;; what to do if the min is greater than the max?
          (> z 5200)) ['(and (<= z 10)
                             (> z 5200))
                       '{z (and (<= z 10)
                                (> z 5200))}
                       '{z {:enum #{}}}
                       '{z {:enum #{}}}]

    '(and (<= z #d "1.1") ;; what to do if the min is greater than the max?
          (> z #d "5250.1")) ['(and (<= z #d "1.1")
                                    (> z #d "5250.1"))
                              '{z (and (<= z #d "1.1")
                                       (> z #d "5250.1"))}
                              '{z {:enum #{}}}
                              '{z {:enum #{}}}]

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
                                          :min-inclusive false}}}}
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
                                                    :max-inclusive false}}}}
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
                                                '{x {:enum #{#d "5600.12" #d "2.24" #d "2.00" #d "1.00"}}}
                                                '{x {:enum #{#d "5600.12" #d "2.24" #d "2.00" #d "1.00"}}}]
    '(or (= 5700 x)
         (= x 2)
         (contains? #{1 2} x)) ['(or (= 5700 x)
                                     (= x 2)
                                     (contains? #{1 2} x))
                                '{x (or (= 5700 x)
                                        (= x 2)
                                        (contains? #{1 2} x))}
                                '{x {:enum #{1 2 5700}}}
                                '{x {:enum #{1 2 5700}}}]

    '(and (= #d "5800.12" x)
          (= x #d "2.24")
          (contains? #{#d "1.00" #d "2.00"} x)) ['(and (= #d "5800.12" x)
                                                       (= x #d "2.24")
                                                       (contains? #{#d "2.00" #d "1.00"} x))
                                                 '{x (and (= #d "5800.12" x)
                                                          (= x #d "2.24")
                                                          (contains? #{#d "2.00" #d "1.00"} x))}
                                                 '{x {:enum #{}}}
                                                 '{x {:enum #{}}}]
    '(and (= 5900 x)
          (= x 2)
          (contains? #{1 2} x)) ['(and (= 5900 x) (= x 2) (contains? #{1 2} x))
                                 '{x (and (= 5900 x) (= x 2) (contains? #{1 2} x))}
                                 '{x {:enum #{}}}
                                 '{x {:enum #{}}}]

    '(if-value x (= x 6000) true) ['(if-value x (= x 6000))
                                   '{x (if-value x (= x 6000))}
                                   '{x {:enum #{6000} :optional true}}
                                   '{x {:enum #{6000} :optional true}}]

    '(if-value x
               (or (= x "6100")
                   (= x "no")
                   (= x "yes"))
               true) ['(if-value x (or (= x "6100")
                                       (= x "no")
                                       (= x "yes")))
                      '{x (if-value x (or (= x "6100")
                                          (= x "no")
                                          (= x "yes")))}
                      '{x {:enum #{"6100" "yes" "no"}
                           :optional true}}
                      '{x {:enum #{"6100" "yes" "no"}
                           :optional true}}]

    '(if-value y (= z 6200) true) [true nil {} {}]

    '(if-value x (<= 6300 x) true) ['(if-value x (<= 6300 x))
                                    '{x (if-value x (<= 6300 x))}
                                    '{x {:ranges #{{:min 6300
                                                    :min-inclusive false
                                                    :optional true}}}}
                                    '{x {:ranges #{{:min 6300
                                                    :min-inclusive false
                                                    :optional true}}}}]

    '(and (if-value x (<= 6400 x) true)
          (if-value x (> 0 x) true)) ['(and (if-value x (<= 6400 x)) (if-value x (> 0 x)))
                                      '{x (and (if-value x (<= 6400 x)) (if-value x (> 0 x)))}
                                      '{x {:enum #{} :optional true}}
                                      '{x {:enum #{} :optional true}}]

    '(or (if-value x (<= 6500 x) true)
         (if-value x (> 0 x) true)) [true nil {} {}]

    '(any? [n #{1 6600}]
           (= x n)) [true nil {} {}]

    '(and (= x $no-value)
          (= x 6700))
    ['(and (= x $no-value) (= x 6700))
     '{x (and (= x $no-value) (= x 6700))}
     '{x :none}
     '{x :none}]

    '(= x $no-value)
    ['(= x $no-value)
     '{x (= x $no-value)}
     '{x :none}
     '{x :none}]

    '(not= x $no-value)
    ['(not= x $no-value)
     '{x (not= x $no-value)}
     '{x :some}
     '{x :some}]

    '(and (= x $no-value)
          (> x 6900))
    ['(and (= x $no-value)
           (> x 6900))
     '{x (and (= x $no-value)
              (> x 6900))}
     '{x :none}
     '{x :none}]

    '(or (= x $no-value)
         (contains? #{7000} x))
    ['(or (= x $no-value)
          (contains? #{7000} x))
     '{x (or (= x $no-value)
             (contains? #{7000} x))}
     '{x {:enum #{7000}
          :optional true}}
     '{x {:enum #{7000}
          :optional true}}]

    '(and (not= x $no-value)
          (if-value x (contains? #{7100} x) true))
    ['(and (not= x $no-value)
           (if-value x (contains? #{7100} x)))
     '{x (and (not= x $no-value)
              (if-value x (contains? #{7100} x)))}
     '{x {:enum #{7100}}}
     '{x {:enum #{7100}}}]))

(deftest test-find-spec-refs
  (is (= #{}
         (analysis/find-spec-refs '1)))
  (is (= #{{:tail :my/Spec}}
         (analysis/find-spec-refs '{:$type :my/Spec})))
  (is (= #{:my/Spec}
         (analysis/find-spec-refs '(= {:$type :my/Spec} {:$type :my/Spec}))))
  (is (= #{{:tail :my/Other} {:tail :my/Spec}}
         (analysis/find-spec-refs '{:$type :my/Spec
                                    :a {:$type :my/Other}})))
  (is (= #{:my/Other {:tail :my/Spec}}
         (analysis/find-spec-refs '{:$type :my/Spec
                                    :a (= {:$type :my/Other}
                                          {:$type :my/Other})})))
  (is (= #{{:tail :my/Spec}}
         (analysis/find-spec-refs '(let [x {:$type :my/Spec}]
                                     x))))
  (is (= #{:my/Spec {:tail :my/Other}}
         (analysis/find-spec-refs '(when {:$type :my/Spec} {:$type :my/Other}))))
  (is (= #{{:tail :my/Spec} :my/Spec}
         (analysis/find-spec-refs '(let [x {:$type :my/Spec}]
                                     (when x (if x x x))))))
  (is (= #{{:tail :my/Spec} :my/Spec}
         (analysis/find-spec-refs '(let [x {:$type :my/Spec}]
                                     (when x (if x x (get x :a)))))))
  (is (= #{{:tail :my/Spec} :my/Spec}
         (analysis/find-spec-refs '(let [x {:$type :my/Spec}]
                                     (when x (if x x (get-in x [:a])))))))
  (is (= #{:my/Spec}
         (analysis/find-spec-refs '(any? [x [{:$type :my/Spec}]] true))))
  (is (= #{:my/Spec :my/Other}
         (analysis/find-spec-refs '(any? [x [{:$type :my/Spec}]] {:$type :my/Other}))))
  (is (= #{:my/Spec {:tail :my/Other}}
         (analysis/find-spec-refs '(refine-to {:$type :my/Spec} :my/Other))))
  (is (= #{:my/Spec :my/Other}
         (analysis/find-spec-refs '(refines-to? {:$type :my/Spec} :my/Other))))

  (is (= #{{:tail :my/Spec} {:tail :my/Other}}
         (analysis/find-spec-refs '(if true {:$type :my/Spec} {:$type :my/Other}))))
  (is (= #{{:tail :my/Spec}}
         (analysis/find-spec-refs '(let [x {:$type :my/Spec}
                                         y x]
                                     y))))

  (is (= #{:tutorials.vending/EventHandler$v1
           :tutorials.vending/State$v1
           :tutorials.vending/Transition$v1
           :spec/Mine
           :spec/Event}
         (analysis/find-spec-refs '(let [events [{:$type :spec/Event}]]
                                     (reduce [a (refine-to {:$type :spec/Mine} :tutorials.vending/State$v1)]
                                             [e events]
                                             (get (refine-to {:$type :tutorials.vending/EventHandler$v1
                                                              :current a
                                                              :event e}
                                                             :tutorials.vending/Transition$v1)
                                                  :next))))))

  (is (= #{:tutorials.vending/EventHandler$v1
           :tutorials.vending/State$v1
           {:tail :tutorials.vending/Transition$v1}
           :spec/Mine
           :spec/Event}
         (analysis/find-spec-refs '(let [events [{:$type :spec/Event}]]
                                     (reduce [a (refine-to {:$type :spec/Mine} :tutorials.vending/State$v1)]
                                             [e events]
                                             (refine-to {:$type :tutorials.vending/EventHandler$v1
                                                         :current a
                                                         :event e}
                                                        :tutorials.vending/Transition$v1))))))

  (is (= #{{:tail :spec/Event}
           :spec/Event
           :tutorials.vending/State$v1
           :spec/Mine}
         (analysis/find-spec-refs '(let [events [{:$type :spec/Event}]]
                                     (reduce [a (refine-to {:$type :spec/Mine} :tutorials.vending/State$v1)]
                                             [e events]
                                             e)))))
  (is (= #{{:tail :tutorials.vending/State$v1}
           :spec/Mine
           :spec/Event}
         (analysis/find-spec-refs '(let [events [{:$type :spec/Event}]]
                                     (reduce [a (refine-to {:$type :spec/Mine} :tutorials.vending/State$v1)]
                                             [e events]
                                             a)))))

  (is (= #{:spec/X
           {:tail :spec/Y}
           :spec/Z}
         (analysis/find-spec-refs '(let [x {:$type :spec/X}
                                         y {:$type :spec/Y}]
                                     (let [z {:$type :spec/Z}
                                           a y]
                                       a)))))

  (is (= #{:spec/X
           {:tail :spec/Y}
           :spec/Z}
         (analysis/find-spec-refs '(let [x {:$type :spec/X}
                                         y {:$type :spec/Y}
                                         z {:$type :spec/Z}
                                         a y]
                                     a))))
  (is (= #{:spec/X
           {:tail :spec/Y}
           :spec/Y
           :spec/Z}
         (analysis/find-spec-refs '(let [x {:$type :spec/X}
                                         y {:$type :spec/Y}]
                                     (let [z {:$type :spec/Z}
                                           a y]
                                       y)))))
  (is (= #{:spec/X
           :spec/Y
           {:tail :spec/Z}}
         (analysis/find-spec-refs '(let [x {:$type :spec/X}
                                         y {:$type :spec/Y}]
                                     (let [z {:$type :spec/Z}]
                                       z)))))

  (is (= #{{:tail :spec/X}
           {:tail :spec/Y}
           :spec/Z}
         (analysis/find-spec-refs '(let [x {:$type :spec/X}
                                         y {:$type :spec/Y}]
                                     (let [z {:$type :spec/Z}
                                           a y]
                                       (if true
                                         a
                                         x))))))
  (is (= #{{:tail :spec/X}
           {:tail :spec/Y}
           :spec/Z}
         (analysis/find-spec-refs '(let [x {:$type :spec/X}
                                         y {:$type :spec/Y}]
                                     (let [z {:$type :spec/Z}
                                           a y
                                           q (if true
                                               a
                                               x)]
                                       q)))))

  (is (= #{:spec/X
           :spec/Y
           :spec/Z
           {:tail :spec/Q}}
         (analysis/find-spec-refs '(let [x {:$type :spec/X}
                                         y {:$type :spec/Y}]
                                     (let [z {:$type :spec/Z}
                                           a y
                                           q (if true
                                               a
                                               x)]
                                       (when-value-let [q {:$type :spec/Q}]
                                                       q))))))
  (is (= #{{:tail :spec/X}
           {:tail :spec/Y}
           :spec/Z
           :spec/Q}
         (analysis/find-spec-refs '(let [x {:$type :spec/X}
                                         y {:$type :spec/Y}]
                                     (let [z {:$type :spec/Z}
                                           a y
                                           q (if true
                                               a
                                               x)]
                                       (when-value-let [p {:$type :spec/Q}]
                                                       q))))))

  (is (= #{{:tail :my/Spec}}
         (analysis/find-spec-refs '(if-value-let [o {:$type :my/Spec}]
                                                 o
                                                 {:$type :my/Spec}))))
  (is (= #{{:tail :my/Spec}
           {:tail :my/Other}}
         (analysis/find-spec-refs '(if-value-let [o {:$type :my/Spec}]
                                                 o
                                                 {:$type :my/Other}))))
  (is (= #{:my/Spec
           {:tail :my/X}
           {:tail :my/Other}}
         (analysis/find-spec-refs '(if-value-let [o {:$type :my/Spec}]
                                                 {:$type :my/X}
                                                 {:$type :my/Other}))))
  (is (= #{{:tail :my/Spec}}
         (analysis/find-spec-refs '(when-value-let [o {:$type :my/Spec}]
                                                   o))))
  (is (= #{:my/Spec
           {:tail :my/Other}}
         (analysis/find-spec-refs '(when-value-let [o {:$type :my/Spec}]
                                                   {:$type :my/Other}))))

  (is (= #{:my/Other}
         (analysis/find-spec-refs-but-tail :my/Spec '(if true {:$type :my/Spec} {:$type :my/Other})))))

(deftest test-cyclical-dependencies
  (let [spec-map (var-types/to-halite-spec-env {:spec/Destination {:fields {:d :Integer}}
                                                :spec/Path1 {:refines-to {:spec/Destination {:name "refine_to_Destination"
                                                                                             :expr '{:$type :spec/Destination
                                                                                                     :d 1}}}}
                                                :spec/Path2 {:refines-to {:spec/Destination {:name "refine_to_Destination"
                                                                                             :expr '{:$type :spec/Destination
                                                                                                     :d 2}}}}
                                                :spec/Start {:refines-to {:spec/Path1 {:name "refine_to_Path1"
                                                                                       :expr '{:$type :spec/Path1}}
                                                                          :spec/Path2 {:name "refine_to_Path2"
                                                                                       :expr '{:$type :spec/Path2}}}}})]
    (is (= {:spec/Path1 #{:spec/Destination}
            :spec/Path2 #{:spec/Destination}
            :spec/Start #{:spec/Path1 :spec/Path2}}
           (#'analysis/get-spec-map-dependencies spec-map)))
    (is (nil? (analysis/find-cycle-in-dependencies spec-map))))
  (let [spec-map (var-types/to-halite-spec-env {:spec/A {:fields {:b :spec/B}}
                                                :spec/B {:fields {:a :spec/A}}})]
    (is (= {:spec/A #{:spec/B}
            :spec/B #{:spec/A}}
           (#'analysis/get-spec-map-dependencies spec-map)))
    (is (= [:spec/A :spec/B :spec/A]
           (analysis/find-cycle-in-dependencies spec-map)))))

;; (run-tests)
