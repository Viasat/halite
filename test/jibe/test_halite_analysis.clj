;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.test-halite-analysis
  (:require [jibe.halite-analysis :as halite-analysis]
            [internal :refer :all]))

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
    '#{a b c d e}))

(deftest test-gather-tlfc
  (are [v x]
       (= x
          [(halite-analysis/gather-tlfc v)
           (halite-analysis/sort-tlfc (halite-analysis/gather-tlfc v))
           (-> (halite-analysis/sort-tlfc (halite-analysis/gather-tlfc v))
               halite-analysis/tlfc-data-map)])

    true [true
          nil
          {}]

    '(and true false) ['true
                       nil
                       {}]

    '(= x 100) ['(= x 100)
                '{x (= x 100)}
                '{x {:enum #{100}}}]

    '(= x (+ 200 3)) [true
                      nil
                      {}]

    '(and (= x 300)
          (= 2 y)) ['(and (= x 300)
                          (= 2 y))
                    '{x (= x 300)
                      y (= 2 y)}
                    '{x {:enum #{300}}
                      y {:enum #{2}}}]

    '(and (and (= x 400)
               (= 2 y))
          z) ['(and (= x 400)
                    (= 2 y))
              '{x (= x 400)
                y (= 2 y)}
              '{x {:enum #{400}}
                y {:enum #{2}}}]

    '(< x 500) ['(< x 500)
                '{x (< x 500)}
                '{x {:range [{:max 500
                              :max-inclusive false}]}}]

    '(< 510 x) ['(< 510 x)
                '{x (< 510 x)}
                '{x {:range [{:min 510
                              :min-inclusive true}]}}]

    '(< x y) [true
              nil
              {}]

    '(= x [1 600]) ['(= x [1 600])
                    '{x (= x [1 600])}
                    '{x {:enum #{[1 600]}}}]

    '(= x [700 z]) [true
                    nil
                    {}]

    '(let [y 800]
       (< x y)) [true
                 nil
                 {}]

    '(< x (let [y 900] y)) [true
                            nil
                            {}]

    '(contains? #{1000 2 3} x) ['(contains? #{1000 3 2} x)
                                '{x (contains? #{1000 3 2} x)}
                                '{x {:enum #{1000 3 2}}}]

    '(and (contains? #{1100 2 3} x)
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
                             z {:range [{:max 20
                                         :max-inclusive true
                                         :min 10
                                         :min-inclusive false}]}}]
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
                z {:range [{:max 20
                            :max-inclusive true
                            :min 10
                            :min-inclusive false}]}}]

    '(or (= x 1300) (= x 2)) ['(or (= x 1300) (= x 2))
                              '{x (or (= x 1300) (= x 2))}
                              '{x {:enum #{2 1300}}}]

    '(and (or (= x 1310) (= x 2))
          (= y 3)) ['(and (or (= x 1310) (= x 2)) (= y 3))
                    '{x (or (= x 1310) (= x 2))
                      y (= y 3)}
                    '{x {:enum #{1310 2}}
                      y {:enum #{3}}}]

    '(and (contains? #{1400 2 3} x)
          (or a b)
          (c (every? [c [1 2 3]]
                     (= c 1)))
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
                z {:enum #{1}}
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
                            '{x {:range [{:min 3
                                          :min-inclusive true
                                          :max 12
                                          :max-inclusive false}
                                         {:min 20
                                          :min-inclusive true
                                          :max 1700
                                          :max-inclusive false}]}}]

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
                            '{x {:range [{:min 3
                                          :min-inclusive true
                                          :max 9
                                          :max-inclusive true}
                                         {:min 20
                                          :min-inclusive true
                                          :max 1800
                                          :max-inclusive false}]}}]

    '(or (< x 1)
         (= x 2)
         (< x 1900)) ['(or (< x 1) (= x 2) (< x 1900))
                      '{x (or (< x 1) (= x 2) (< x 1900))}
                      {}]
    '(or (< x 1)
         (< x 2000)) ['(or (< x 1) (< x 2000))
                      '{x (or (< x 1) (< x 2000))}
                      '{x {:range [{:max 2000
                                    :max-inclusive false}]}}]

    '(or (< x 2100)
         (<= x 2100)) ['(or (< x 2100) (<= x 2100))
                       '{x (or (< x 2100) (<= x 2100))}
                       '{x {:range [{:max 2100
                                     :max-inclusive true}]}}]

    '(or (< x 2200)
         (>= 2200 x)) ['(or (< x 2200) (>= 2200 x))
                       '{x (or (< x 2200) (>= 2200 x))}
                       '{x {:range [{:max 2200
                                     :max-inclusive false}]}}]

    '(and (< x 2300)
          (>= 2300 x)) ['(and (< x 2300) (>= 2300 x))
                        '{x (and (< x 2300) (>= 2300 x))}
                        '{x {:range [{:max 2300
                                      :max-inclusive false}]}}]

    '(or (< x 2400)
         (<= x 2400)) ['(or (< x 2400) (<= x 2400))
                       '{x (or (< x 2400) (<= x 2400))}
                       '{x {:range [{:max 2400
                                     :max-inclusive true}]}}]

    '(and (< x 2500)
          (<= x 2500)) ['(and (< x 2500) (<= x 2500))
                        '{x (and (< x 2500) (<= x 2500))}
                        '{x {:range [{:max 2500
                                      :max-inclusive false}]}}]

    '(or (> x 2600)
         (>= x 2600)) ['(or (> x 2600) (>= x 2600))
                       '{x (or (> x 2600) (>= x 2600))}
                       '{x {:range [{:min 2600
                                     :min-inclusive true}]}}]

    '(and (> x 2700)
          (>= x 2700)) ['(and (> x 2700) (>= x 2700))
                        '{x (and (> x 2700) (>= x 2700))}
                        '{x {:range [{:min 2700
                                      :min-inclusive false}]}}]

    '(or (and (> x 2800)
              (<= x 2850))
         (and (> x 2840)
              (<= x 2860))) ['(or (and (> x 2800) (<= x 2850))
                                  (and (> x 2840) (<= x 2860)))
                             '{x (or
                                  (and (> x 2800) (<= x 2850))
                                  (and (> x 2840) (<= x 2860)))}
                             '{x {:range
                                  [{:min 2800
                                    :min-inclusive false
                                    :max 2860
                                    :max-inclusive true}]}}]

    '(or (and (> x 2900)
              (< x 2950))
         (and (>= x 2950)
              (<= x 2960))) ['(or (and (> x 2900) (< x 2950))
                                  (and (>= x 2950) (<= x 2960)))
                             '{x (or (and (> x 2900) (< x 2950))
                                     (and (>= x 2950) (<= x 2960)))}
                             '{x {:range
                                  [{:min 2900
                                    :min-inclusive false
                                    :max 2960
                                    :max-inclusive true}]}}]

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
                   '{x {:range
                        [{:min 1
                          :min-inclusive false
                          :max 3060
                          :max-inclusive true}]}}]

    '(and (> x 1)
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
                                   '{x {:range [{:min 3100
                                                 :min-inclusive false
                                                 :max 3160
                                                 :max-inclusive true}]}}]

    '(and (> x 3225) ;; this falls out and is not included in the range, ranges are assumed to be more specific
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
                                   '{x {:range [{:min 3200
                                                 :min-inclusive false
                                                 :max 3260
                                                 :max-inclusive true}]}}]

    '(and (= x 3325)
          (or (and (> x 3300)
                   (<= x 3350))
              (and (> x 3340)
                   (<= x 3360)))) ['(and (= x 3325)
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
                                   '{x {:enum #{0}}} ;; not smart enough to figure out this violates the ranges
                                   ]))

;; (run-tests)
