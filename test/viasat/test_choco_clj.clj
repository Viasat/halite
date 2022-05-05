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

(deftest test-abstract-translation-strategy
  ;; An abstract variable expands into a type variable and variables for each
  ;; concrete spec; for each concrete spec, the variables
  ;; must be satisfied unconditionally.
  ;; References to the abstract variable should be conditional on
  ;; the type var.
  ;; A stand-alone propagation pass should be done for each spec,
  ;; and specs that are obviously unsatisfiable should be excluded
  ;; from the analysis.
  (let [senv '{:A {:spec-vars {:n :Integer}
                   :abstract? true
                   :constraints [["a1" (and (<= 0 n) (<= n 10))]]}

               :X {:spec-vars {:a :A :x :Int}
                   :constraints [["x1" (= x (get* (refine-to a :A) :n))]]}

               :B {:spec-vars {:b :Integer}
                   :constraints [["b1" (< b 4)]]
                   :refines-to {:A {:expr {:$type :A, :n b}}}}
               :C {:spec-vars {:c :Integer}
                   :constraints [["c1" (< 2 c)]]
                   :refines-to {:A {:expr {:$type :A, :n c}}}}}

        alt1 '{:vars {x :Int
                      a?B|b :Int
                      a?C|c :Int
                      a?type #{1 2}}
               :constraints
               #{(< a?B|b 4)
                 (let [n a?B|b]
                   (and (<= 0 n) (<= n 10)))
                 (< 2 a?C|c)
                 (let [n a?C|c]
                   (and (<= 0 n) (<= n 10)))
                 (= x (if (= a?type 1) a?B|b a?C|c))}}
        alt2 '{:vars {x :Int
                      a?B|b :Int
                      a?C|c :Int
                      a?type #{1 2}}
               :constraints
               #{(let [$1 (= a?type 1)
                       $2 (if $1 a?B|b a?C|c)]
                   (and (< a?B|b 4)
                        (and (<= 0 a?B|b) (<= a?B|b 10))
                        (< 2 a?C|c)
                        (and (<= 0 a?C|c) (<= a?C|c 10))
                        (= x $2)))}}
        spec alt2]

    (binding [choco-clj/*default-int-bounds* [-100 100]]
      (are [extra bounds]
           (= bounds (select-keys (choco-clj/propagate (update spec :constraints into extra)) (keys bounds)))

        '[]             '{a?type #{1 2}, x [-100 100]}
        '[(= x 3)]      '{a?type #{1 2}, a?B|b [0 3], a?C|c [3 10]}
        '[(= x 2)]      '{a?type 1, a?B|b 2}
        '[(= a?type 2)] '{x [3 10]}))))
