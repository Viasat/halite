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

(deftest test-intersect-int-bounds
  (are [a b _ result]
      (= result (choco-clj/intersect-int-bounds a b))

    :Int :Int => :Int
    :Int 1 => 1
    :Int #{1 2 3} => #{1 2 3}
    :Int [2 5] => [2 5]

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
