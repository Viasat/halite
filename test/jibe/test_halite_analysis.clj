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
  (are [v expected]
       (= expected
          (halite-analysis/gather-tlfc v))

    true
    {}

    '(and true false)
    {}

    '(= x 1)
    '{x #{(= x 1)}}

    '(= x (+ 1 3))
    {}

    '(and (= x 1)
          (= 2 y))
    '{x #{(= x 1)}
      y #{(= 2 y)}}

    '(and (and (= x 1)
               (= 2 y))
          z)
    '{x #{(= x 1)}
      y #{(= 2 y)}}

    '(< x 1)
    '{x #{(< x 1)}}

    '(< x y)
    {}

    '(= x [1 2])
    '{x #{(= x [1 2])}}

    '(= x [1 z])
    {}

    '(let [y 1]
       (< x y))
    {}

    '(< x (let [y 1] y))
    {}

    '(contains? #{1 2 3} x)
    '{x #{(contains? #{1 2 3} x)}}

    '(and (contains? #{1 2 3} x)
          (= y 1)
          (and (<= z 20)
               (> z 10)))
    '{x #{(contains? #{1 2 3} x)}
      y #{(= y 1)}
      z #{(<= z 20)
          (> z 10)}}

    '(and (contains? #{1 2 3} x)
          (or a b)
          (and (= y 1)
               (and (<= z 20)
                    (> z 10)))
          q)
    '{x #{(contains? #{1 2 3} x)}
      y #{(= y 1)}
      z #{(<= z 20)
          (> z 10)}}

    '(or (= x 1) (= x 2))
    {}

    '(and (contains? #{1 2 3} x)
          (or a b)
          (and (c (every? [c [1 2 3]]
                          (= c 1)))
               (and true
                    (and (= z 1))))
          (and (= y 1)
               (and (<= z 20)
                    (> z 10)))
          q)
    '{z #{(> z 10) (<= z 20) (= z 1)}
      x #{(contains? #{1 3 2} x)}
      y #{(= y 1)}}

    '(= [1 2] [x 2])
    {}

    '(= #{x 2} #{1 2})
    {}))

;; (run-tests)
