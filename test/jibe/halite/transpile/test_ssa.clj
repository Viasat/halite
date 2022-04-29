;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.transpile.test-ssa
  (:require [jibe.halite.envs :as halite-envs]
            [jibe.halite.types :as halite-types]
            [jibe.halite.transpile.ssa :as ssa]
            [schema.test])
  (:use clojure.test))

(use-fixtures :once schema.test/validate-schemas)

(deftest test-spec-to-ssa
  (let [spec-info {:spec-vars {:x :Integer, :y :Integer, :z :Integer, :b :Boolean}
                   :constraints []
                   :refines-to {}}
        bar-info {:spec-vars {:a :Integer, :b :Boolean}, :constraints [], :refines-to {}}]
    (are [constraints derivations new-constraints]
        (= [derivations new-constraints]
           (binding [ssa/*next-id* (atom 0)]
             (let [spec-info (->> constraints
                                  (map-indexed #(vector (str "c" %1) %2))
                                  (update spec-info :constraints into))
                   senv (halite-envs/spec-env {:ws/A spec-info, :foo/Bar bar-info})]
               (->> spec-info
                    (ssa/spec-to-ssa senv)
                    ((juxt #(-> % :derivations)
                           #(->> % :constraints (map second))))))))

      [] {} []

      '[(= x 1)]
      '{$1 [x :Integer]
        $2 [1 :Integer]
        $3 [(= $1 $2) :Boolean $4]
        $4 [(not= $1 $2) :Boolean $3]}
      '[$3]

      '[(not= x 1)]
      '{$1 [x :Integer]
        $2 [1 :Integer]
        $3 [(= $1 $2) :Boolean $4]
        $4 [(not= $1 $2) :Boolean $3]}
      '[$4]

      '[(not (= x 1))]
      '{$1 [x :Integer]
        $2 [1 :Integer]
        $3 [(= $1 $2) :Boolean $4]
        $4 [(not= $1 $2) :Boolean $3]}
      '[$4]

      '[(not (not= x 1))]
      '{$1 [x :Integer]
        $2 [1 :Integer]
        $3 [(= $1 $2) :Boolean $4]
        $4 [(not= $1 $2) :Boolean $3]}
      '[$3]

      '[(< x y)]
      '{$1 [x :Integer]
        $2 [y :Integer]
        $3 [(< $1 $2) :Boolean $4]
        $4 [(<= $2 $1) :Boolean $3]}
      '[$3]

      '[(> x y)]
      '{$1 [x :Integer]
        $2 [y :Integer]
        $3 [(< $2 $1) :Boolean $4]
        $4 [(<= $1 $2) :Boolean $3]}
      '[$3]

      '[(<= x y)]
      '{$1 [x :Integer]
        $2 [y :Integer]
        $3 [(< $2 $1) :Boolean $4]
        $4 [(<= $1 $2) :Boolean $3]}
      '[$4]

      '[(>= x y)]
      '{$1 [x :Integer]
        $2 [y :Integer]
        $3 [(< $1 $2) :Boolean $4]
        $4 [(<= $2 $1) :Boolean $3]}
      '[$4]

      '[(< x y) (not (>= x y))]
      '{$1 [x :Integer]
        $2 [y :Integer]
        $3 [(< $1 $2) :Boolean $4]
        $4 [(<= $2 $1) :Boolean $3]}
      '[$3 $3]

      '[(or (< x y) (and b (= z (* x y))))]
      '{$1 [x :Integer]
        $2 [y :Integer]
        $3 [(< $1 $2) :Boolean $4]
        $4 [(<= $2 $1) :Boolean $3]
        $5 [b :Boolean $6]
        $6 [(not $5) :Boolean $5]
        $7 [z :Integer]
        $8 [(* $1 $2) :Integer]
        $9 [(= $7 $8) :Boolean $10]
        $10 [(not= $7 $8) :Boolean $9]
        $11 [(and $5 $9) :Boolean $12]
        $12 [(not $11) :Boolean $11]
        $13 [(or $3 $11) :Boolean $14]
        $14 [(not $13) :Boolean $13]}
      '[$13]

      '[(< x (+ y 1)) (< (+ y 1) z)] ; IDEA: lexically sort terms to +|*|and|or|etc. where order doesn't matter
      '{$1 [x :Integer]
        $2 [y :Integer]
        $3 [1 :Integer]
        $4 [(+ $2 $3) :Integer]
        $5 [(< $1 $4) :Boolean $6]
        $6 [(<= $4 $1) :Boolean $5]
        $7 [z :Integer]
        $8 [(< $4 $7) :Boolean $9]
        $9 [(<= $7 $4) :Boolean $8]}
      '[$5 $8]

      '[(let [x (+ 1 x y)] (< z x))]
      '{$1 [1 :Integer]
        $2 [x :Integer]
        $3 [y :Integer]
        $4 [(+ $1 $2 $3) :Integer]
        $5 [z :Integer]
        $6 [(< $5 $4) :Boolean $7]
        $7 [(<= $4 $5) :Boolean $6]}
      '[$6]

      '[(if (< x y) b false)]
      '{$1 [x :Integer]
        $2 [y :Integer]
        $3 [(< $1 $2) :Boolean $4]
        $4 [(<= $2 $1) :Boolean $3]
        $5 [b :Boolean $6]
        $6 [(not $5) :Boolean $5]
        $7 [true :Boolean $8]
        $8 [false :Boolean $7]
        $9 [(if $3 $5 $8) :Boolean $10]
        $10 [(not $9) :Boolean $9]}
      '[$9]

      '[(= 5 (if b x y))]
      '{$1 [5 :Integer]
        $2 [b :Boolean $3]
        $3 [(not $2) :Boolean $2]
        $4 [x :Integer]
        $5 [y :Integer]
        $6 [(if $2 $4 $5) :Integer]
        $7 [(= $1 $6) :Boolean $8]
        $8 [(not= $1 $6) :Boolean $7]}
      '[$7]

      '[(get* {:$type :foo/Bar :a 10 :b false} :b)]
      '{$1 [10 :Integer]
        $2 [true :Boolean $3]
        $3 [false :Boolean $2]
        $4 [{:$type :foo/Bar :a $1 :b $3} :foo/Bar]
        $5 [(get* $4 :b) :Boolean $6]
        $6 [(not $5) :Boolean $5]}
      '[$5]
      )))

(deftest test-spec-from-ssa
  (let [spec-info {:spec-vars {:x :Integer, :y :Integer, :z :Integer, :b :Boolean}
                   :constraints []
                   :refines-to {}}]

    (are [constraints derivations new-constraint]
        (= [["$all" new-constraint]]
           (-> spec-info
               (assoc :derivations derivations
                      :constraints
                      (vec (map-indexed #(vector (str "c" %1) %2) constraints)))
               (ssa/spec-from-ssa)
               :constraints))

      [] {} true

      '[$1] '{$1 [b :Boolean]} 'b

      '[$3 $5]
      '{$1 [x :Integer]
        $2 [1 :Integer]
        $3 [(< $2 $1) :Boolean]
        $4 [10 :Integer]
        $5 [(< $1 $4) :Boolean]}
      '(and (< 1 x) (< x 10))

      '[$7 $8]
      '{$1 [(+ $2 $3) :Integer]
        $2 [x :Integer]
        $3 [y :Integer]
        $4 [z :Integer]
        $5 [10 :Integer]
        $6 [2 :Integer]
        $7 [(< $1 $5) :Boolean]
        $8 [(= $9 $10) :Boolean]
        $9 [(+ $3 $4) :Integer]
        $10 [(* $6 $1) :Integer]}
      '(let [$1 (+ x y)]
         (and (< $1 10)
              (= (+ y z) (* 2 $1))))

      '[$1]
      '{$1 [(get* $2 :foo) :Boolean]
        $3 [b :Boolean]
        $2 [{:$type :ws/Foo :foo $3} :ws/Foo]}
      '(get* {:$type :ws/Foo :foo b} :foo)

      '[$4]
      '{$1 [12 :Integer]
        $2 [$1 :Integer]
        $3 [x :Integer]
        $4 [(< $3 $2) :Boolean]}
      '(< x 12)
      )))
