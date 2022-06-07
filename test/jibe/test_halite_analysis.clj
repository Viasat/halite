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
  (are [v expected expected-sort]
       (= [expected expected-sort]
          [(halite-analysis/gather-tlfc v)
           (halite-analysis/sort-tlfc (halite-analysis/gather-tlfc v))])

    true
    true
    nil

    '(and true false)
    'true
    nil

    '(= x 1)
    '(= x 1)
    '{x (= x 1)}

    '(= x (+ 1 3))
    true
    nil

    '(and (= x 1)
          (= 2 y))
    '(and (= x 1)
          (= 2 y))
    '{x (= x 1)
      y (= 2 y)}

    '(and (and (= x 1)
               (= 2 y))
          z)
    '(and (= x 1)
          (= 2 y))
    '{x (= x 1)
      y (= 2 y)}

    '(< x 1)
    '(< x 1)
    '{x (< x 1)}

    '(< x y)
    true
    nil

    '(= x [1 2])
    '(= x [1 2])
    '{x (= x [1 2])}

    '(= x [1 z])
    true
    nil

    '(let [y 1]
       (< x y))
    true
    nil

    '(< x (let [y 1] y))
    true
    nil

    '(contains? #{1 2 3} x)
    '(contains? #{1 3 2} x)
    '{x (contains? #{1 3 2} x)}

    '(and (contains? #{1 2 3} x)
          (= y 1)
          (and (<= z 20)
               (> z 10)))
    '(and (contains? #{1 3 2} x)
          (= y 1)
          (<= z 20)
          (> z 10))
    '{x (contains? #{1 3 2} x),
      y (= y 1),
      z (and (<= z 20) (> z 10))}

    '(and (contains? #{1 2 3} x)
          (or a b)
          (and (= y 1)
               (and (<= z 20)
                    (> z 10)))
          q)
    '(and
      (contains? #{1 3 2} x)
      (= y 1)
      (<= z 20)
      (> z 10))
    '{x (contains? #{1 3 2} x)
      y (= y 1),
      z (and (<= z 20) (> z 10))}

    '(or (= x 1) (= x 2))
    '(or (= x 1) (= x 2))
    '{x (or (= x 1) (= x 2))}

    '(and (contains? #{1 2 3} x)
          (or a b)
          (c (every? [c [1 2 3]]
                     (= c 1)))
          (= z 1)
          (= y 1)
          (<= z 20)
          (> z 10)
          q)
    '(and
      (contains? #{1 3 2} x)
      (= z 1)
      (= y 1)
      (<= z 20)
      (> z 10))
    '{x (contains? #{1 3 2} x)
      z (and (= z 1) (<= z 20) (> z 10)),
      y (= y 1)}

    '(= [1 2] [x 2])
    true
    nil

    '(= #{x 2} #{1 2})
    true
    nil

    '(or (and (>= x 3)
              (< x 12))
         (and (>= x 20)
              (< x 25)))
    '(or (and (>= x 3) (< x 12))
         (and (>= x 20) (< x 25)))
    '{x (or (and (>= x 3) (< x 12))
            (and (>= x 20) (< x 25)))}))

;; (run-tests)
