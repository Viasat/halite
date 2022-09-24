;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns viasat.test-choco-clj
  (:require [schema.test]
            [viasat.choco-clj :as choco-clj])
  (:use clojure.test))

(use-fixtures :once schema.test/validate-schemas)

(deftest test-int-enum
  (let [spec '{:vars {n #{1 2}
                      x :Int}
               :constraints
               #{(=> (= 1 n) (< x 10))
                 (=> (= 2 n) (> x 5))}}]
    (binding [choco-clj/*default-int-bounds* [0 100]]
      (are [extra-constraints bounds]
           (= bounds (choco-clj/propagate
                      (update spec :constraints into extra-constraints)))

        [] '{n #{1 2}, x [0 100]}
        '[(= x 20)] '{x 20, n 2}))))

(deftest test-int-var-does-not-get-bool-bounds
  (is (= '{n #{0 1}} (choco-clj/propagate '{:vars {n #{0 1}} :constraints #{}}))))

(deftest test-literal-true-and-false
  (let [spec '{:vars {n :Int, m :Int, p :Bool}
               :constraints #{}}]
    (binding [choco-clj/*default-int-bounds* [0 10]]
      (are [constraint bound]
           (= bound (choco-clj/propagate
                     (update spec :constraints conj constraint)))

        true '{n [0 10], m [0 10], p #{true false}}))))

(deftest test-bounds-as-var-types
  (binding [choco-clj/*default-int-bounds* [0 100]]
    (are [bound expected]
         (= expected
            (-> {:vars {'v bound}, :constraints #{}}
                (choco-clj/propagate)
                (get 'v)))

      :Int choco-clj/*default-int-bounds*
      :Bool #{true false}
      [1 90] [1 90]
      [2 6] [2 6]
      #{1 2} #{1 2}
      [1 1] 1
      #{1} 1
      true true
      false false
      1 1
      #{true} true
      #{true false} #{true false})))

(deftest test-intersect-bound
  (are [a b _ result]
       (= result (choco-clj/intersect-bound a b))

    :Int :Int => :Int
    :Int 1 => 1
    :Int #{1 2 3} => #{1 2 3}
    :Int [2 5] => [2 5]

    :Bool :Bool => :Bool
    :Bool true => true
    :Bool #{true false} => #{true false}
    :Bool #{true} => true

    true false => #{}
    true true => true
    false false => false
    false #{false} => false
    false #{true} => #{}

    #{true} :Bool => true
    #{true false} :Bool => #{true false}
    #{true false} true => true
    #{true} false => #{}
    #{true false} #{false} => false
    #{true} #{false} => #{}

    1 :Int => 1
    1 1 => 1
    1 2 => #{}
    1 #{1 2} => 1
    1 #{2 3} => #{}
    1 [0 2] => 1
    1 [3 5] => #{}

    #{1 2 3} :Int => #{1 2 3}
    #{1 2 3} 1 => 1
    #{1 2 3} 4 => #{}
    #{1 2 3} #{2 3 4} => #{2 3}
    #{1 2 3} [2 5] => #{2 3}

    [1 4] :Int => [1 4]
    [1 4] 2 => 2
    [1 4] 5 => #{}
    [1 4] #{3 4 5} => #{3 4}
    [1 4] #{5 6} => #{}
    [1 4] [3 5] => [3 4]
    [1 4] [5 7] => #{}))

(deftest test-union-bound
  (are [a b _ result]
       (= result (choco-clj/union-bound a b))

    :Int :Int => :Int
    :Int 1 => :Int
    :Int #{1 2 3} => :Int
    :Int [2 5] => :Int

    :Bool :Bool => :Bool
    :Bool true => :Bool
    :Bool #{true false} => :Bool
    :Bool #{true} => :Bool

    true false => #{true false}
    true true => true
    false false => false
    false #{false} => false
    false #{true} => #{true false}

    #{true} :Bool => :Bool
    #{true false} :Bool => :Bool
    #{true false} true => #{true false}
    #{true} false => #{true false}
    #{true false} #{false} => #{true false}
    #{true} #{false} => #{true false}

    1 :Int => :Int
    1 1 => 1
    1 2 => #{1 2}
    1 #{1 2} => #{1 2}
    1 #{2 3} => #{1 2 3}
    1 [0 2] => [0 2]
    1 [3 5] => [1 5]

    #{1 2 3} :Int => :Int
    #{1 2 3} 1 => #{1 2 3}
    #{1 2 3} 4 => #{1 2 3 4}
    #{1 2 3} #{2 3 4} => #{1 2 3 4}
    #{1 2 3} [2 5] => [1 5]

    [1 4] :Int => :Int
    [1 4] 2 => [1 4]
    [1 4] 5 => [1 5]
    [1 4] #{3 4 5} => [1 5]
    [1 4] #{5 6} => [1 6]
    [1 4] [3 5] => [1 5]
    [1 4] [5 7] => [1 7]))

(deftest test-initial-bounds
  (let [spec '{:vars {m :Int, n :Int, p :Bool}
               :constraints #{(if p (< n m) (> n m))}}]
    (binding [choco-clj/*default-int-bounds* [-10 10]]
      (are [in out]
           (= out (choco-clj/propagate spec in))

        '{}                                     '{m [-10 10], n [-10 10], p #{true false}}
        '{m 1}                                  '{m 1, n [-10 10], p #{true false}}
        '{m [0 10], p false}                    '{m [0 9], n [1 10], p false}
        '{m 0, n #{-2 -1 0 1 2}, p true}        '{m 0, n #{-2 -1}, p true}))))

(defn- trunc-div [a n]
  (let [q (/ (double a) (double n))]
    (if (< q 0)
      (Math/ceil q)
      (Math/floor q))))

(defn- java-% [a n]
  (int (- a (* n (trunc-div a n)))))

(deftest test-mod-workaround
  (binding [choco-clj/*default-int-bounds* [-100 100]]
    (let [spec {:vars {'n :Int, 'r :Int, 'd :Int},
                :constraints #{'(= (mod n d) r)}}]

      (is (= '{n [-100 100], r [-100 100], d [-100 100]}
             (choco-clj/propagate spec)))

      (is (= '{n [1 20], d 2, r [0 1]}
             (choco-clj/propagate spec '{n [1 20], d 2})))

      (is (= '{n [-20 -1], d 2, r [-100 100]}
             (choco-clj/propagate spec '{n [-20 -1], d 2}))))))

(deftest test-add-behavior
  (binding [choco-clj/*default-int-bounds* [-100 100]]
    (let [spec '{:vars {a :Int, b :Int, c :Int}
                 :constraints #{(= (+ a b) c)}}]
      (doseq [a (range -10 11), b (range -10 11)]
        (is (= (+ a b) (get (choco-clj/propagate spec {'a a, 'b b}) 'c))
            (format "expected (= (+ %d %d) %d)" a b (+ a b)))))))

(deftest test-pow-workaround
  (binding [choco-clj/*default-int-bounds* [-10 10]]
    (let [spec '{:vars {x :Int, p :Bool}
                 :constraints #{(if p (< 0 (- (expt 2 x) (expt x 2))) true)}}]
      (are [in out]
           (= out (choco-clj/propagate spec in))

        '{}        '{x [-10 10], p #{true false}}
        '{p true}  '{x [0 10] p true}
        '{x -3}    '{x -3, p false}))))

(deftest test-expt-excludes-negative-exponents
  (is (= {'n [0 10]}
         (choco-clj/propagate
          '{:vars {n :Int}
            :constraints #{(<= 0 (expt 2 n))}}
          {'n [-10 10]}))))

(deftest test-unused-let-binding-still-constrains-domains
  (is (= {'n #{-2 -1 1 2}}
         (choco-clj/propagate
          '{:vars {n :Int}
            :constraints #{(let [m (div 10 n)]
                             (and (< -3 n) (< n 3)))}}
          {'n (set (range -10 11))})))

  (is (= {'n #{-2 -1 1 2}}
         (choco-clj/propagate
          '{:vars {n :Int}
            :constraints #{(let [m (mod 10 n)]
                             (and (< -3 n) (< n 3)))}}
          {'n (set (range -10 11))})))

  (is (= {'n (set (range 0 6))}
         (choco-clj/propagate
          '{:vars {n :Int}
            :constraints #{(let [m (expt 2 n)] (< -5 n))}}
          {'n (set (range -5 6))}))))
