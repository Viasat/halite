;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.transpile.test-to-choco-clj2
  (:require [jibe.halite.envs :as halite-envs]
            [jibe.halite.transpile.to-choco-clj2 :as h2c]
            [schema.test]
            [viasat.choco-clj :as choco-clj])
  (:use clojure.test))

(use-fixtures :once schema.test/validate-schemas)

(def spec-to-ssa #'h2c/spec-to-ssa)

;; TODO: We need to rewrite 'div forms in the case where the quotient is a variable,
;; to ensure that choco doesn't force the variable to be zero even when the div might not
;; be evaluated.

(deftest test-spec-to-ssa
  (let [spec-info {:spec-vars {:x :Integer, :y :Integer, :z :Integer, :b :Boolean}
                   :constraints []
                   :refines-to {}}
        bar-info {:spec-vars {:a :Integer, :b :Boolean}, :constraints [], :refines-to {}}
        tenv (halite-envs/type-env-from-spec
              (halite-envs/spec-env {})
              spec-info)]
    (are [constraints derivations new-constraints]
        (= [derivations new-constraints]
           (binding [h2c/*next-id* (atom 0)]
             (let [spec-info (->> constraints
                                  (map-indexed #(vector (str "c" %1) %2))
                                  (update spec-info :constraints into))
                   senv (halite-envs/spec-env {:ws/A spec-info, :foo/Bar bar-info})
                   tenv (halite-envs/type-env-from-spec senv spec-info)]
               (->> spec-info
                    (spec-to-ssa senv tenv)
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

(def spec-from-ssa #'h2c/spec-from-ssa)

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
               (spec-from-ssa)
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

(def build-spec-ctx #'h2c/build-spec-ctx)
(def lower-instance-comparisons #'h2c/lower-instance-comparisons)

(deftest test-lower-instance-comparisons
  (binding [h2c/*next-id* (atom 0)]
    (let [senv (halite-envs/spec-env
                '{:ws/A
                  {:spec-vars {:an :Integer}
                   :constraints [["a1" (let [b {:$type :ws/B :bn 12 :bb true}]
                                         (and
                                          (= b {:$type :ws/B :bn an :bb true})
                                          (not= {:$type :ws/B :bn 4 :bb false} b)
                                          (= an 45)))]]
                   :refines-to {}}
                  :ws/B
                  {:spec-vars {:bn :Integer :bb :Boolean}
                   :constraints []
                   :refines-to {}}})
          sctx (build-spec-ctx senv :ws/A)]
      (is (= '[["$all" (let [$4 {:$type :ws/B :bn 12 :bb true}
                             $24 (get* $4 :bn)
                             $6 {:$type :ws/B :bn an :bb true}
                             $18 (get* $4 :bb)
                             $10 {:$type :ws/B :bn 4 :bb false}]
                         (and
                          (and (= $18 (get* $6 :bb))
                               (= $24 (get* $6 :bn)))
                          (or (not= (get* $10 :bb) $18)
                              (not= (get* $10 :bn) $24))
                          (= an 45)))]]
             (-> sctx lower-instance-comparisons :ws/A spec-from-ssa :constraints)))
      )))

(def fixpoint #'h2c/fixpoint)

(deftest test-lower-instance-comparisons-for-composition
  (binding [h2c/*next-id* (atom 0)]
    (let [senv (halite-envs/spec-env
                '{:ws/A
                  {:spec-vars {:b1 :ws/B, :b2 :ws/B}
                   :constraints [["a1" (not= b1 b2)]]
                   :refines-to {}}
                  :ws/B
                  {:spec-vars {:c1 :ws/C, :c2 :ws/C}
                   :constraints []
                   :refines-to {}}
                  :ws/C
                  {:spec-vars {:x :Integer :y :Integer}
                   :constraints []
                   :refines-to {}}})
          sctx (build-spec-ctx senv :ws/A)]
      (is (= '[["$all" (let [$6 (get* b2 :c1)
                             $5 (get* b1 :c1)
                             $9 (get* b1 :c2)
                             $10 (get* b2 :c2)]
                         (or
                          (or
                           (not= (get* $5 :x) (get* $6 :x))
                           (not= (get* $5 :y) (get* $6 :y)))
                          (or
                           (not= (get* $9 :x) (get* $10 :x))
                           (not= (get* $9 :y) (get* $10 :y)))))]]
             (->> sctx
                  (fixpoint lower-instance-comparisons)
                  :ws/A spec-from-ssa :constraints))))))

(def compute-guards #'h2c/compute-guards)
(def guards-from-ssa #'h2c/guards-from-ssa)

(deftest test-compute-guards
  (let [senv '{:ws/A
               {:spec-vars {:x :Integer, :y :Integer, :b1 :Boolean, :b2 :Boolean}
                :constraints []
                :refines-to {}}
               :ws/Foo
               {:spec-vars {:x :Integer, :y :Integer}
                :constraints []
                :refines-to {}}}]
    (are [constraints guards]
      (= guards
         (binding [h2c/*next-id* (atom 0)]
           (let [spec-info (-> senv
                               (update-in [:ws/A :constraints] into
                                          (map-indexed #(vector (str "c" %1) %2) constraints))
                               (halite-envs/spec-env)
                               (build-spec-ctx :ws/A)
                               :ws/A)]
             (-> spec-info
                 (compute-guards)
                 (->> (guards-from-ssa
                       (:derivations spec-info)
                       (->> spec-info :spec-vars keys (map symbol) set))
                      (remove (comp true? val))
                      (into {}))))))

      []
      {}

      '[b1 true]
      {}

      '[(< x y)]
      {}

      '[(if (< x y)
          (< x 10)
          (> x 10))]
      '{(< x 10) (< x y)
        (< 10 x) (<= y x)}

      '[(if (< x y) (<= (div 10 x) 10) true)
        (if (>= x y) (<= (div 10 x) 10) true)]
      '{}

      '[(not= 1 (if b1 (if b2 1 2) 3))]
      '{b2 b1
        2 (and b1 (not b2))
        3 (not b1)
        (if b2 1 2) b1}

      '[(not= 1 (if b1 (if b2 1 2) 3))
        (not= 3 (if (not b2) 2 4))]
      '{4 b2
        (if b2 1 2) b1
        2 (not b2)}

      '[(if b1 {:$type :ws/Foo :x 1 :y 2} {:$type :ws/Foo :x 2 :y 3})]
      '{1 b1
        3 (not b1)
        {:$type :ws/Foo :x 1 :y 2} b1
        {:$type :ws/Foo :x 2 :y 3} (not b1)}

      '[(= 1 (if b1 (get* {:$type :ws/Foo :x 1 :y 2} :x) 3))]
      '{2 b1
        3 (not b1)
        {:$type :ws/Foo :x 1 :y 2} b1
        (get* {:$type :ws/Foo :x 1 :y 2} :x) b1}
      )))

(def lower-implicit-constraints #'h2c/lower-implicit-constraints)

(deftest test-lower-implicit-constraints
  (binding [h2c/*next-id* (atom 0)]
    (let [senv (halite-envs/spec-env
                '{:ws/A
                  {:spec-vars {:an :Integer}
                   :constraints [["a1" (< an 10)]
                                 ["a2" (= an (get* {:$type :ws/B :bn (+ 1 an)} :bn))]]
                   :refines-to {}}
                  :ws/B
                  {:spec-vars {:bn :Integer}
                   :constraints [["b1" (> 0 (if (<= bn 5) (get* {:$type :ws/C :cn bn} :cn) 6))]]
                   :refines-to {}}
                  :ws/C
                  {:spec-vars {:cn :Integer}
                   :constraints [["c1" (= 0 (mod cn 2))]]
                   :refines-to {}}})
          sctx (build-spec-ctx senv :ws/A)]
      (is (= '[["$all" (let [$6 (+ 1 an)
                             $38 (<= $6 5)]
                         (and
                          (< an 10)
                          (= an (get* {:$type :ws/B, :bn $6} :bn))
                          (and (< (if $38 (get* {:$type :ws/C, :cn $6} :cn) 6) 0)
                               (if $38 (= 0 (mod $6 2)) true))))]]
             (-> sctx lower-implicit-constraints :ws/A spec-from-ssa :constraints))))))

(def push-gets-into-ifs #'h2c/push-gets-into-ifs)

(deftest test-push-gets-into-ifs
  (binding [h2c/*next-id* (atom 0)]
    (let [senv (halite-envs/spec-env
                '{:ws/A
                  {:spec-vars {:ab :Boolean}
                   :constraints [["a1" (not= 1 (get* (if ab {:$type :ws/B :bn 2} {:$type :ws/B :bn 1}) :bn))]]
                   :refines-to {}}
                  :ws/B
                  {:spec-vars {:bn :Integer}
                   :constraints []
                   :refines-to {}}})
          sctx (build-spec-ctx senv :ws/A)]
      (is (= '[["$all" (not= 1 (if ab
                                 (get* {:$type :ws/B :bn 2} :bn)
                                 (get* {:$type :ws/B :bn 1} :bn)))]]
             (-> sctx push-gets-into-ifs :ws/A spec-from-ssa :constraints))))))

(deftest test-push-gets-into-nested-ifs
  (binding [h2c/*next-id* (atom 0)]
    (let [senv (halite-envs/spec-env
                '{:ws/A
                  {:spec-vars {:b1 :ws/B, :b2 :ws/B, :b3 :ws/B, :b4 :ws/B, :a :Boolean, :b :Boolean}
                   :constraints [["a1" (= 12 (get* (if a (if b b1 b2) (if b b3 b4)) :n))]]
                   :refines-to {}}
                  :ws/B
                  {:spec-vars {:n :Integer}
                   :constraints []
                   :refines-to {}}})
          sctx (build-spec-ctx senv :ws/A)]
      (is (= '[["$all" (= 12 (if a (if b (get* b1 :n) (get* b2 :n)) (if b (get* b3 :n) (get* b4 :n))))]]
             (->> sctx (fixpoint push-gets-into-ifs)
                 :ws/A spec-from-ssa :constraints))))))

(def lower-instance-literals-in-spec #'h2c/lower-instance-literals-in-spec)

(deftest test-lower-instance-literals-in-spec
  (binding [h2c/*next-id* (atom 0)]
    (let [senv (halite-envs/spec-env
                '{:ws/A
                  {:spec-vars {:an :Integer}
                   :constraints [["a1" (< an (get* (get* {:$type :ws/B :c {:$type :ws/C :cn (get* {:$type :ws/C :cn (+ 1 an)} :cn)}} :c) :cn))]]
                   :refines-to {}}
                  :ws/B
                  {:spec-vars {:c :ws/C}
                   :constraints [] :refines-to {}}
                  :ws/C
                  {:spec-vars {:cn :Integer}
                   :constraints [] :refines-to {}}})
          sctx (build-spec-ctx senv :ws/A)]
      (is (= '[["$all" (< an (+ 1 an))]]
             (->> sctx :ws/A lower-instance-literals-in-spec spec-from-ssa :constraints))))))

(def spec-ify-assignment #'h2c/spec-ify-assignment)

(deftest test-spec-ify-assignment
  (binding [h2c/*next-id* (atom 0)]
    (let [senv (halite-envs/spec-env
                '{:ws/A
                  {:spec-vars {:b :ws/B :c :ws/C}
                   :constraints [["a1" (= (get* b :bn) (get* c :cn))]
                                 ["a2" (if (> (get* b :bn) 0)
                                         (< (get* b :bn)
                                            (get* (get* (get* c :a) :b) :bn))
                                         true)]]
                   :refines-to {}}
                  :ws/B
                  {:spec-vars {:bn :Integer :bp :Boolean}
                   :constraints [["b1" (if bp (<= bn 10) (>= bn 10))]]
                   :refines-to {}}
                  :ws/C
                  {:spec-vars {:a :ws/A :cn :Integer}
                   :constraints []
                   :refines-to {}}})
          sctx (build-spec-ctx senv :ws/A)]

      (are [b-assignment constraint]
          (= {:spec-vars {:bn :Integer, :bp :Boolean}
              :constraints
              [["$all" constraint]]
              :refines-to {}}
             (->> b-assignment (spec-ify-assignment sctx) spec-from-ssa))

        {:$type :ws/B} '(if bp (<= bn 10) (<= 10 bn))
        {:$type :ws/B :bn 12} '(and (if bp (<= bn 10) (<= 10 bn))
                                    (= bn 12)))

      (is (= '{:spec-vars {:b|bn :Integer :b|bp :Boolean
                           :c|cn :Integer}
               :constraints
               [["$all" (and
                         (= b|bn c|cn)
                         (if b|bp
                           (<= b|bn 10)
                           (<= 10 b|bn)))]]
               :refines-to {}}
             (->> {:$type :ws/A} (spec-ify-assignment sctx) spec-from-ssa)))

      (is (= '{:spec-vars {:b|bn :Integer :b|bp :Boolean
                           :c|cn :Integer
                           :c|a|b|bn :Integer :c|a|b|bp :Boolean}
               :constraints
               [["$all" (and
                         (= b|bn c|cn)
                         (if (< 0 b|bn)
                           (< b|bn c|a|b|bn)
                           true)
                         (if b|bp
                           (<= b|bn 10)
                           (<= 10 b|bn))
                         (= b|bp true)
                         (if c|a|b|bp
                           (<= c|a|b|bn 10)
                           (<= 10 c|a|b|bn)))]]
               :refines-to {}}
             (->> '{:$type :ws/A
                    :b {:$type :ws/B :bp true}
                    :c {:$type :ws/C :a {:$type :ws/A}}}
                  (spec-ify-assignment sctx)
                  spec-from-ssa)))
      )))

(deftest test-transpile-l0
  ;; l0: only integer and boolean valued variables and expressions
  (let [senv (halite-envs/spec-env
              '{:ws/A
                {:spec-vars {:x :Integer, :y :Integer, :b :Boolean}
                 :constraints [["c1" (let [delta (abs (- x y))]
                                       (and (< 5 delta)
                                            (< delta 10)))]
                               ["c2" (= b (< x y))]]
                 :refines-to {}}})]

    (is (= '{:vars {x :Int, y :Int, b :Bool}
             :constraints
             #{(let [$22 (abs (- x y))]
                 (and (and (< 5 $22)
                           (< $22 10))
                      (= b (< x y))))}}
           (h2c/transpile senv {:$type :ws/A})))

    (is (= '{:vars {x :Int, y :Int, b :Bool}
             :constraints
             #{(let [$22 (abs (- x y))]
                 (and (and (< 5 $22)
                           (< $22 10))
                      (= b (< x y))
                      (= x 12)
                      (= b false)))}}
           (h2c/transpile senv {:$type :ws/A :x 12 :b false})))))

(deftest test-transpile-l1
  ;; l1: only integer and boolean valued variables, but expressions may be instance valued
  (let [senv (halite-envs/spec-env
              '{:ws/A
                {:spec-vars {:an :Integer}
                 :constraints [["a1" (< an 10)]
                               ["a2" (< an (get* {:$type :ws/B :bn (+ 1 an)} :bn))]]
                 :refines-to {}}
                :ws/B
                {:spec-vars {:bn :Integer}
                 :constraints [["b1" (< 0 (get* {:$type :ws/C :cn bn} :cn))]]
                 :refines-to {}}
                :ws/C
                {:spec-vars {:cn :Integer}
                 :constraints [["c1" (= 0 (mod cn 2))]]
                 :refines-to {}}})]
    (is (= '{:vars {an :Int}
             :constraints
             #{(let [$43 (+ 1 an)]
                 (and (< an 10)                  ; a1
                      (< an $43)                  ; a2
                      (and (< 0 $43)              ; b1
                           (= 0 (mod $43 2)))))}} ; c1
           (h2c/transpile senv {:$type :ws/A})))))

(deftest test-transpile-ifs-and-instance-literals
  (let [senv (halite-envs/spec-env
              '{:ws/A
                {:spec-vars {:an :Integer, :ab :Boolean}
                 :constraints [["a1" (not=
                                      (if ab
                                        {:$type :ws/B :bn an}
                                        {:$type :ws/B :bn 12})
                                      {:$type :ws/B :bn (+ an 1)})]]
                 :refines-to {}}
                :ws/B
                {:spec-vars {:bn :Integer}
                 :constraints [["b1" (<= (div 10 bn) 10)]]
                 :refines-to {}}})]
    (is (= '{:vars {an :Int ab :Bool}
             :constraints
             #{(let [$47 (+ an 1)]
                 (and
                  (not= (if ab an 12) $47)
                  (if ab (<= (div 10 an) 10) true)
                  (<= (div 10 $47) 10)
                  (if (not ab) (<= (div 10 12) 10) true)))}}
           (h2c/transpile senv {:$type :ws/A})))))

;;; Illustrate composition elimination

(def sctx
  '{:ws/A
    {:spec-vars {:an :Integer :b :ws/B}
     :derivations {}
     :constraints [["a1" (and (< 0 an) (< an (get* b :bn)))]
                   ("a2" (= an (get* (get* b :c) :cn)))]
     :refines-to {}}

    :ws/B
    {:spec-vars {:bn :Integer, c :ws/C}
     :derivations {}
     :constraints [["b1" (< bn 10)]]
     :refines-to {}}

    :ws/C
    {:spec-vars {:cn :Integer}
     :derivations {}
     :constraints [["c1" (= 0 (mod* cn 2))]]
     :refines-to {}}
    })

(def sctx-ssa1
  "First SSA pass."
  '{:ws/A
    {:spec-vars {:an :Integer :b :ws/B}
     :derivations {$1 [(< 0 an) :Boolean]
                   $2 [(get* b :bn) :Integer]
                   $3 [(< an $2) :Boolean]
                   $4 [(get* b :c) :ws/C]
                   $5 [(get* $4 :cn) :Integer]}
     :constraints [["a1" (and $1 $3)]
                   ("a2" (= an $5))]
     :refines-to {}}

    :ws/B
    {:spec-vars {:bn :Integer, c :ws/C}
     :derivations {}
     :constraints [["b1" (< bn 10)]]
     :refines-to {}}

    :ws/C
    {:spec-vars {:cn :Integer}
     :derivations {$1 [(mod* cn 2) :Integer]}
     :constraints [["c1" (= 0 $1)]]
     "refines-to" {}}})

;;; A Thought! We can probably eliminate equality derivations
;;; that must be true for all instances by merging nodes.

;;; At any rate, we ought to collapse redundant nodes after each
;;; stage.

(def sctx-comp-elim
  "Composition elimination.
  Recursively expand each spec-valued variable.
  Add a derivation for an instance literal that stands for the eliminated variable.
  Replace all occurrences of eliminated variable w/ introduced derivation.
    How do we know this won't produce cyclic derivations?
  Do this bottom-up according to the spec dependency graph.
  Abstraction elimination can produce recursive specs,
  so we'll need to detect recursion and/or use the assignment
  to bound expansion in that case."
  '{:ws/A
    {:spec-vars {:an :Integer :b|bn, :Integer, :b|c|cn :Integer}
     :derivations {$6 [{:$type :ws/C :cn b|c|cn} :ws/C]
                   $7 [{:$type :ws/B :bn b|bn :c|cn b|c|cn}]
                   $1 [(< 0 an) :Boolean]
                   $2 [(get* $7 :bn) :Integer]
                   $3 [(< an $2) :Boolean]
                   $4 [$6 :ws/C]
                   $5 [(get* $4 :cn) :Integer]}
     :constraints [["a1" (and $1 $3)]
                   ("a2" (= an $5))]
     :refines-to {}}

    :ws/B
    {:spec-vars {:bn :Integer, :c|cn :Integer}
     :derivations {$1 {:$type :ws/C :cn c|cn}}
     :constraints [["b1" (< bn 10)]]
     :refines-to {}}

    :ws/C
    {:spec-vars {:cn :Integer}
     :derivations {$1 [(mod* cn 2) :Integer]}
     :constraints [["c1" (= 0 $1)]]
     :refines-to {}}})

"You might think we could be pruning 'dead' code between each pass, but
we've got to be careful with pruning! Every instance literal implies constraints.
Pruning an instance literal before its implied constraints are brought along
will impact semantics!"

(def sctx-inline-constraints
  "Constraint inlining.
  For every instance literal, re-instantiate all derivations/constraints
  for the given variable assignment.
  Do this bottom-up according to the spec dependency graph.

  When we get to cycles e.g. introduced by abstraction elimination,
  we'll need to figure out how to handle that."
  '{:ws/A
    {:spec-vars {:an :Integer :b|bn, :Integer, :b|c|cn :Integer}
     :derivations {$6 [{:$type :ws/C :cn b|c|cn} :ws/C]
                   $7 [{:$type :ws/B :bn b|bn :c|cn b|c|cn}]
                   $1 [(< 0 an) :Boolean]
                   $2 [(get* $7 :bn) :Integer]
                   $3 [(< an $2) :Boolean]
                   $5 [(get* $6 :cn) :Integer]

                   ;; bn -> b|bn, c|cn -> b|c|cn
                   ;;$6 [{:$type :ws/C :cn b|c|n} :ws/C] ;; redundant!
                   ;; $1 -> $6
                   $8 [(mod* cb|c|cn 2) :Integer]
                   ;; $2 -> $8
                   }
     :constraints [["a1" (and $1 $3)]
                   ["a2" (= an $5)]
                   ["b1" (< b|bn 10)]
                   ["c1" (= 0 $8)]]
     :refines-to {}}

    :ws/B
    {:spec-vars {:bn :Integer, :c|cn :Integer}
     :derivations {$1 {:$type :ws/C :cn c|cn}
                   ;; cn -> c|cn
                   $2 [(mod* c|cn 2) :Integer]
                   }
     :constraints [["b1" (< bn 10)]
                   ["c1" (= 0 $2)]]
     :refines-to {}}

    :ws/C
    {:spec-vars {:cn :Integer}
     :derivations {$1 [(mod* cn 2) :Integer]}
     :constraints [["c1" (= 0 $1)]]
     :refines-to {}}})

(def sctx-prune
  "Having eliminated all instance-valued spec variables and inlined
  all implicit constraints, it's time to prune dead code. This is
  straightforward reachability from constraints."
  '{:ws/A
    {:spec-vars {:an :Integer :b|bn, :Integer, :b|c|cn :Integer}
     :derivations {$6 [{:$type :ws/C :cn b|c|cn} :ws/C]
                   $7 [{:$type :ws/B :bn b|bn :c|cn b|c|cn}] ;x
                   $1 [(< 0 an) :Boolean] ;x
                   $2 [(get* $7 :bn) :Integer] ;x
                   $3 [(< an $2) :Boolean] ;x
                   $5 [(get* $6 :cn) :Integer] ;x
                   $8 [(mod* cb|c|cn 2) :Integer] ;x
                   }
     :constraints [["a1" (and $1 $3)]
                   ["a2" (= an $5)]
                   ["b1" (< b|bn 10)]
                   ["c1" (= 0 $8)]]
     :refines-to {}}

    :ws/B
    {:spec-vars {:bn :Integer, :c|cn :Integer}
     :derivations {$2 [(mod* c|cn 2) :Integer] ;x
                   }
     :constraints [["b1" (< bn 10)]
                   ["c1" (= 0 $2)]]
     :refines-to {}}

    :ws/C
    {:spec-vars {:cn :Integer}
     :derivations {$1 [(mod* cn 2) :Integer]}
     :constraints [["c1" (= 0 $1)]]
     :refines-to {}}})

(def sctx-elim-instances
  "Now it's time to eliminate instance-valued variables entirely.
  I think this needs to be done breadth-first starting from the
  constraints and working backwards.

  Each primitive-typed derivation with instance-valued arguments needs to be
  Replaced with a semantically equivalent subgraph of primitive-typed derivations
  with primitive-typed arguments. Building this new subgraph will require
  traversing through instance-valued derivations, specifically get* and if.
  "
  '{:ws/A
    {:spec-vars {:an :Integer :b|bn, :Integer, :b|c|cn :Integer}
     :derivations {$1 [(< 0 an) :Boolean]
                   $8 [(mod* b|c|cn 2) :Integer]
                   $9 [(< an b|bn)]
                   }
     :constraints [["a1" (and $1 $9)]
                   ["a2" (= an b|c|cn)]
                   ["b1" (< b|bn 10)]
                   ["c1" (= 0 $8)]]
     :refines-to {}}

    :ws/B
    {:spec-vars {:bn :Integer, :c|cn :Integer}
     :derivations {$2 [(mod* c|cn 2) :Integer] ;x
                   }
     :constraints [["b1" (< bn 10)]
                   ["c1" (= 0 $2)]]
     :refines-to {}}

    :ws/C
    {:spec-vars {:cn :Integer}
     :derivations {$1 [(mod* cn 2) :Integer]}
     :constraints [["c1" (= 0 $1)]]
     :refines-to {}}}
  )

(def spec-for-A
  "Finally, we can translate what remains into a choco-clj spec."
  '{:vars {an :Int, b|bn :Int, b|c|cn :Int}
    :constraints
    #((and
       (and (< 0 an) (< an b|bn))
       (= an b|c|cn)
       (< b|bn 10)
       (= 0 (mod* b|c|cn))))})


;;; The next thing to figure out: Recursive specs!
;;; We can do that before we get to abstraction elimination.
;;; We should see if halite works for recursive specs...




(comment 
  (def sctx
    '{:a/A
      {:spec-vars {:v1 :Integer, :v2 :Boolean}
       :abstract? true
       :derivations {}
       :constraints [["v1Bound" (and (<= -100 v1) (<= v1 100))]
                     ["v2WhenNegV1" (=> (< v1 0) v2)]]
       :refines-to {:b/A {:clauses [["as b/A" {:v3 v1}]]}}}

      :b/A
      {:spec-vars {:v3 :Integer}
       :derivations {}
       :constraints [["v3" (not (and (< 5 v3) (< v3 10)))]]
       :refines-to {}}

      :a/B
      {:spec-vars {:v5 :Integer
                   :v8 :a/D}
       :derivations {}
       :constraints []
       :refines-to {:a/A {:clauses [["as a/A" {:v1 v5, :v2 false}]]}}}

      :a/C
      {:spec-vars {:v4 :a/A, :v7 :Integer}
       :derivations {}
       :constraints []
       :refines-to {:a/A {:clauses [["as a/A" {:v1 (let [child-v1 (get* (refine-to v4 :a/A) :v1)]
                                                     (+ child-v1 v7))
                                               :v2 true}]]}}}

      :a/D
      {:spec-vars {:v6 :Integer}
       :derivations {}
       :constraints [["v6" (get* {:$type :a/E :v9 (+ v6 2) :v10 true} :v10)]]
       :refines-to {}}

      :a/E
      {:spec-vars {:v9 :Integer :v10 :Boolean}
       :derivations {}
       :constraints [["e" (= v10 (not= v9 42))]]
       :refines-to {}}})


  (def sctx-ssa
    '{:a/A
      {:spec-vars {:v1 :Integer, :v2 :Boolean}
       :abstract? true
       :derivations {$1 (<= -100 v1)
                     $2 (<= v1 100)
                     $3 (< v1 0)}
       :constraints [["v1Bound" (and $1 $2)]
                     ["v2WhenNegV1" (=> $3 v2)]]
       :refines-to {:b/A {:clauses [["as b/A" {:v3 v1}]]}}}

      :b/A
      {:spec-vars {:v3 :Integer}
       :derivations {$1 (< 5 v3)
                     $2 (< v3 10)
                     $3 (and $1 $2)}
       :constraints [["v3" (not $3)]]
       :refines-to {}}

      :a/B
      {:spec-vars {:v5 :Integer
                   :v8 :a/D}
       :derivations {}
       :constraints []
       :refines-to {:a/A {:clauses [["as a/A" {:v1 v5, :v2 false}]]}}}

      :a/C
      {:spec-vars {:v4 :a/A, :v7 :Integer}
       :derivations {$1 (refine-to v4 :a/A)
                     $2 (get* $1 :v1)}
       :constraints []
       :refines-to {:a/A {:clauses [["as a/A" {:v1 (+ $2 v7)
                                               :v2 true}]]}}}

      :a/D
      {:spec-vars {:v6 :Integer}
       :derivations {$1 (+ v6 2)
                     $2 {:$type :a/E :v9 $1 :v10 true}}
       :constraints [["v6" (get* $2 :v10)]]
       :refines-to {}}

      :a/E
      {:spec-vars {:v9 :Integer :v10 :Boolean}
       :derivations {$1 (not= v9 42)}
       :constraints [["e" (= v10 $1)]]
       :refines-to {}}})

  (def sctx-implied-constraints
    (-> sctx-ssa
        (assoc
         :a/D
         '{:spec-vars {:v6 :Integer}
           :derivations {$1 (+ v6 2)
                         $2 {:$type :a/E :v9 $1 :v10 true}
                         $3 (not= $1 42)}
           :constraints [["v6" (get* $2 :v10)]
                         ["e" (= true $3)]]
           :refines-to {}})))

  (def sctx-elim-abstraction
    (-> sctx-ssa
        (assoc
         :a/C
         '{:spec-vars {:v4 :a/A
                       :v4?type :Integer  ; :ws/B -> 0, :ws/C -> 1
                       :v4?ws.B :ws/B
                       :v4?ws.C :ws/C
                       :v7 :Integer}
           :derivations {$1 (refine-to v4 :a/A)
                         $2 (get* $1 :v1)}
           :constraints [["v4?" (and (<= 0 v4!type)
                                     (< v4!type 2)
                                     (=> (= 0 v4!type)
                                         (= v4 {:$type :ws/A :v1 (get* v4?ws.B :v5) :v2 false}))
                                     (=> (= 1 v4!type)
                                         (= v4 {:$type :ws/A :v1 (+ (get*))})))]
                         ]
           :refines-to {:a/A {:clauses [["as a/A" {:v1 (+ $2 v7)
                                                   :v2 true}]]}}}))))
