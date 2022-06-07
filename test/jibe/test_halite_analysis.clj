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
           (halite-analysis/sort-tlfc (halite-analysis/gather-tlfc v))])

    true [true
          nil]

    '(and true false) ['true
                       nil]

    '(= x 100) ['(= x 100)
                '{x (= x 100)}]

    '(= x (+ 200 3)) [true
                      nil]

    '(and (= x 300)
          (= 2 y)) ['(and (= x 300)
                          (= 2 y))
                    '{x (= x 300)
                      y (= 2 y)}]

    '(and (and (= x 400)
               (= 2 y))
          z) ['(and (= x 400)
                    (= 2 y))
              '{x (= x 400)
                y (= 2 y)}]

    '(< x 500) ['(< x 500)
                '{x (< x 500)}]

    '(< x y) [true
              nil]

    '(= x [1 600]) ['(= x [1 600])
                    '{x (= x [1 600])}]

    '(= x [700 z]) [true
                    nil]

    '(let [y 800]
       (< x y)) [true
                 nil]

    '(< x (let [y 900] y)) [true
                            nil]

    '(contains? #{1000 2 3} x) ['(contains? #{1000 3 2} x)
                                '{x (contains? #{1000 3 2} x)}]

    '(and (contains? #{1100 2 3} x)
          (= y 1)
          (and (<= z 20)
               (> z 10))) ['(and (contains? #{1100 3 2} x)
                                 (= y 1)
                                 (<= z 20)
                                 (> z 10))
                           '{x (contains? #{1100 3 2} x),
                             y (= y 1),
                             z (and (<= z 20) (> z 10))}]

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
                z (and (<= z 20) (> z 10))}]

    '(or (= x 1300) (= x 2)) ['(or (= x 1300) (= x 2))
                              '{x (or (= x 1300) (= x 2))}]

    '(and (or (= x 1310) (= x 2))
          (= y 3)) ['(and (or (= x 1310) (= x 2)) (= y 3))
                    '{x (or (= x 1310) (= x 2))
                      y (= y 3)}]

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
                y (= y 1)}]

    '(= [1 1500] [x 1500]) [true
                            nil]

    '(= #{x 1600} #{1 1600}) [true
                              nil]

    '(or (and (>= x 3)
              (< x 12))
         (and (>= x 20)
              (< x 1700))) ['(or (and (>= x 3) (< x 12))
                                 (and (>= x 20) (< x 1700)))
                            '{x (or (and (>= x 3) (< x 12))
                                    (and (>= x 20) (< x 1700)))}]))

;; (run-tests)
