;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.transpile.test-ssa
  (:require [com.viasat.halite :as halite]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.transpile.ssa :as ssa]
            [com.viasat.halite.transpile.util :refer [mk-junct]]
            [com.viasat.halite.var-types :as var-types]
            [schema.core :as s]
            [schema.test])
  (:use clojure.test))

(use-fixtures :once schema.test/validate-schemas)

(deftest test-cycle?
  (let [cycle-graph (assoc ssa/empty-ssa-graph :dgraph
                           '{$1 [(+ $2 $3) :Integer]
                             $2 [(+ $1 $3) :Integer]
                             $3 [1 :Integer]})]
    (is (= true (ssa/cycle? cycle-graph))))

  (let [non-cycle-graph (assoc ssa/empty-ssa-graph :dgraph
                               '{$1 [(+ $2 $3) :Integer]
                                 $2 [2 :Integer]
                                 $3 [3 :Integer]})]
    (is (= false (ssa/cycle? non-cycle-graph)))))

(deftest test-spec-to-ssa
  (let [senv (var-types/to-halite-spec-env
              '{:ws/A
                {:fields {:v [:Maybe :Integer], :w [:Maybe :Integer], :x :Integer, :y :Integer, :z :Integer, :b :Boolean, :c :ws/C}}
                :foo/Bar
                {:fields {:a :Integer, :b :Boolean}}
                :ws/C
                {:fields {:cn [:Maybe :Integer]}}})]
    (are [constraints dgraph new-constraints]
         (= [dgraph new-constraints]
            (-> senv
                (assoc-in [:ws/A :constraints] (vec (map-indexed #(vector (str "c" %1) %2) constraints)))
                (envs/spec-env)
                (#(ssa/spec-to-ssa % (envs/lookup-spec % :ws/A)))
                (#(vector (:dgraph (:ssa-graph %))
                          (map second (:constraints %))))))

      [] {} []

      '[(if (valid? {:$type :ws/C}) {:$type :ws/C} c)]
      '{$1 [{:$type :ws/C} [:Instance :ws/C]],
        $2 [(valid? $1) :Boolean $3],
        $3 [(not $2) :Boolean $2],
        $4 [c [:Instance :ws/C]],
        $5 [(if $2 $1 $4) [:Instance :ws/C]]}
      '[$5]

      '[(if (valid? (if true {:$type :ws/C, :cn 1} c))
          (if true {:$type :ws/C, :cn 1} c)
          c)]
      '{$1 [true :Boolean $2]
        $2 [false :Boolean $1]
        $3 [1 :Integer]
        $4 [{:$type :ws/C :cn $3} [:Instance :ws/C]]
        $5 [c [:Instance :ws/C]]
        $6 [(if $1 $4 $5) [:Instance :ws/C]]
        $7 [(valid? $6) :Boolean $8]
        $8 [(not $7) :Boolean $7]
        $9 [(if $7 $6 $5) [:Instance :ws/C]]}
      '[$9]

      '[(if (valid? {:$type :ws/C, :cn 1})
          (let [x {:$type :ws/C, :cn 1}]
            (if (valid? {:$type :ws/C, :cn 1})
              2
              4))
          8)]
      '{$1 [1 :Integer]
        $2 [{:$type :ws/C :cn $1} [:Instance :ws/C]]
        $3 [(valid? $2) :Boolean $4]
        $4 [(not $3) :Boolean $3]
        $5 [2 :Integer]
        $6 [4 :Integer]
        $7 [(if $3 $5 $6) :Integer]
        $8 [($do! $2 $7) :Integer]
        $9 [8 :Integer]
        $10 [(if $3 $8 $9) :Integer]}
      '($10)

      '[(if false (error "never") (if false (error "never") 5))]
      '{$1 [true :Boolean $2]
        $2 [false :Boolean $1]
        $3 ["never" :String]
        $4 [(error $3) :Nothing]
        $5 [5 :Integer]
        $6 [(if $2 $4 $5) :Integer]
        $7 [(if $2 $4 $6) :Integer]}
      '[$7]

      '[(= x 1)]
      '{$1 [x :Integer]
        $2 [1 :Integer]
        $3 [(= $1 $2) :Boolean $4]
        $4 [(not= $1 $2) :Boolean $3]}
      '[$3]

      '[(let [n (div 1 0)]
          true)]
      '{$1 [1 :Integer]
        $2 [0 :Integer]
        $3 [(div $1 $2) :Integer]
        $4 [true :Boolean $5]
        $5 [false :Boolean $4]
        $6 [($do! $3 $4) :Boolean $7]
        $7 [(not $6) :Boolean $6]}
      '[$6]

      '[(= 1 no-value-)]
      '{$1 [1 :Integer]
        $2 [:Unset :Unset]
        $3 [(= $1 $2) :Boolean $4]
        $4 [(not= $1 $2) :Boolean $3]}
      '[$3]

      '[($value? no-value-)]
      '{$1 [:Unset :Unset]
        $2 [($value? $1) :Boolean $3]
        $3 [(not $2) :Boolean $2]}
      '[$2]

      '[($value? $no-value)]
      '{$1 [:Unset :Unset]
        $2 [($value? $1) :Boolean $3]
        $3 [(not $2) :Boolean $2]}
      '[$2]

      '[($value? :Unset)]
      '{$1 [:Unset :Unset]
        $2 [($value? $1) :Boolean $3]
        $3 [(not $2) :Boolean $2]}
      '[$2]

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
      '{$1 [1 :Integer],
        $2 [x :Integer],
        $3 [y :Integer],
        $4 [(+ $1 $2 $3) :Integer],
        $5 [z :Integer],
        $6 [(< $5 $4) :Boolean $7],
        $7 [(<= $4 $5) :Boolean $6],
        $8 [($do! $4 $6) :Boolean $9],
        $9 [(not $8) :Boolean $8]}
      '[$8]

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

      '[(get {:$type :foo/Bar :a 10 :b false} :b)]
      '{$1 [10 :Integer]
        $2 [true :Boolean $3]
        $3 [false :Boolean $2]
        $4 [{:$type :foo/Bar :a $1 :b $3} [:Instance :foo/Bar]]
        $5 [(get $4 :b) :Boolean $6]
        $6 [(not $5) :Boolean $5]}
      '[$5]

      '[(= 1 (mod x 2))]
      '{$1 [1 :Integer]
        $2 [x :Integer]
        $3 [2 :Integer]
        $4 [(mod $2 $3) :Integer]
        $5 [(= $1 $4) :Boolean $6]
        $6 [(not= $1 $4) :Boolean $5]}
      '[$5]

      ;; if-value produces if, value? and value! nodes to facilitate semantics-preserving rewrites.
      ;; form-from-ssa must always collapse these back to an if-value form.
      '[(if-value w w 1)]  ; (if ($value? w) ($value! w) 1)
      '{$1 [:Unset :Unset]
        $2 [w [:Maybe :Integer]]
        $3 [($value? $2) :Boolean $4]
        $4 [(not $3) :Boolean $3]
        $5 [($value! $2) :Integer]
        $6 [1 :Integer]
        $7 [(if $3 $5 $6) :Integer]}
      '[$7]

      ;; !!!!!
      ;; While the linter won't allow users to write (if-value $no-value ...),
      ;; the type checker and evaluator will allow it, and there are rewrite rules
      ;; that could produce it.
      ;; The nature of the type system is such that the 'then' branch does not generally even HAVE a type!
      ;; We therefore simply *drop the then branch on the floor* and add the else branch.
      '[(if-value $no-value (+ 1 true) false)]
      '{$1 [:Unset :Unset]
        $2 [true :Boolean $3]
        $3 [false :Boolean $2]}
      '[$3]

      ;; In this case, x is known statically to have a value.
      ;; No ($value! x) wrapper is necessary in the body of the then branch.
      '[(if-value x (+ x 1) y)]
      '{$1 [:Unset :Unset]
        $2 [x :Integer]
        $3 [($value? $2) :Boolean $4]
        $4 [(not $3) :Boolean $3]
        $5 [1 :Integer]
        $6 [(+ $2 $5) :Integer]
        $7 [y :Integer]
        $8 [(if $3 $6 $7) :Integer]}
      '[$8]

      '[(if-value w w (if-value w (+ 1 true) 12))]
      '{$1 [:Unset :Unset]
        $2 [w [:Maybe :Integer]]
        $3 [($value? $2) :Boolean $4]
        $4 [(not $3) :Boolean $3]
        $5 [($value! $2) :Integer]
        $6 [12 :Integer]
        $7 [(if $3 $5 $6) :Integer]}
      '[$7]

      '[(if-value w w 1)]
      '{$1 [:Unset :Unset]
        $2 [w [:Maybe :Integer]]
        $3 [($value? $2) :Boolean $4]
        $4 [(not $3) :Boolean $3]
        $5 [($value! $2) :Integer]
        $6 [1 :Integer]
        $7 [(if $3 $5 $6) :Integer]}
      '[$7]

      ;; We also need to be able to accept the internal nodes, which
      ;; may be produced by rewrite rules.
      '[(if ($value? w) ($value! w) 1)]
      '{$1 [w [:Maybe :Integer]]
        $2 [:Unset :Unset]
        $3 [($value? $1) :Boolean $4]
        $4 [(not $3) :Boolean $3]
        $5 [($value! $1) :Integer]
        $6 [1 :Integer]
        $7 [(if $3 $5 $6) :Integer]}
      '[$7]

      '[(= v w)]
      '{$1 [v [:Maybe :Integer]]
        $2 [w [:Maybe :Integer]]
        $3 [(= $1 $2) :Boolean $4]
        $4 [(not= $1 $2) :Boolean $3]}
      '[$3]

      '[(= x (if b v w))]
      '{$1 [x :Integer]
        $2 [b :Boolean $3]
        $3 [(not $2) :Boolean $2]
        $4 [v [:Maybe :Integer]]
        $5 [w [:Maybe :Integer]]
        $6 [(if $2 $4 $5) [:Maybe :Integer]]
        $7 [(= $1 $6) :Boolean $8]
        $8 [(not= $1 $6) :Boolean $7]}
      '[$7]

      '[(let [u (if b v w)]
          (if-value u (< u 10) true))]
      '{$1 [b :Boolean $2],
        $2 [(not $1) :Boolean $1],
        $3 [v [:Maybe :Integer]],
        $4 [w [:Maybe :Integer]],
        $5 [(if $1 $3 $4) [:Maybe :Integer]],
        $6 [:Unset :Unset]
        $7 [($value? $5) :Boolean $8],
        $8 [(not $7) :Boolean $7],
        $9 [($value! $5) :Integer],
        $10 [10 :Integer]
        $11 [(< $9 $10) :Boolean $12],
        $12 [(<= $10 $9) :Boolean $11],
        $13 [true :Boolean $14],
        $14 [false :Boolean $13],
        $15 [(if $7 $11 $13) :Boolean $16],
        $16 [(not $15) :Boolean $15],
        $17 [($do! $5 $15) :Boolean $18],
        $18 [(not $17) :Boolean $17]}
      '[$17]

      '[(if-value v
                  (let [u (get c :cn)]
                    (if-value u (+ u 1) 0))
                  1)]
      '{$1 [:Unset :Unset]
        $2 [v [:Maybe :Integer]],
        $3 [($value? $2) :Boolean $4],
        $4 [(not $3) :Boolean $3],
        $5 [($value! $2) :Integer],
        $6 [c [:Instance :ws/C]],
        $7 [(get $6 :cn) [:Maybe :Integer]],
        $8 [($value? $7) :Boolean $9],
        $9 [(not $8) :Boolean $8],
        $10 [($value! $7) :Integer]
        $11 [1 :Integer],
        $12 [(+ $10 $11) :Integer],
        $13 [0 :Integer],
        $14 [(if $8 $12 $13) :Integer],
        $15 [($do! $7 $14) :Integer],
        $16 [(if $3 $15 $11) :Integer]}
      '[$16]

      '[(if true (error "nope") true)]
      '{$1 [true :Boolean $2]
        $2 [false :Boolean $1]
        $3 ["nope" :String]
        $4 [(error $3) :Nothing]
        $5 [(if $1 $4 $1) :Boolean $6]
        $6 [(not $5) :Boolean $5]}
      '[$5]

      ;; vector stuff
      '[(not= [1 2 3] [])]
      '{$1 [1 :Integer],
        $2 [2 :Integer],
        $3 [3 :Integer],
        $4 [[$1 $2 $3] [:Vec :Integer]],
        $5 [[] [:Vec :Nothing]],
        $6 [(= $4 $5) :Boolean $7],
        $7 [(not= $4 $5) :Boolean $6]}
      '[$7]

      '[(= 1 (get [1] x))]
      '{$1 [1 :Integer],
        $2 [[$1] [:Vec :Integer]],
        $3 [x :Integer],
        $4 [(get $2 $3) :Integer],
        $5 [(= $1 $4) :Boolean $6],
        $6 [(not= $1 $4) :Boolean $5]}
      '[$5]

      '[(every? [x [x y]] (< x 1))]
      '{$1 [x :Integer],
        $2 [y :Integer],
        $3 [[$1 $2] [:Vec :Integer]],
        $4 [($local 1 :Integer) :Integer],
        $5 [1 :Integer],
        $6 [(< $4 $5) :Boolean $7],
        $7 [(<= $5 $4) :Boolean $6],
        $8 [(every? $4 $3 $6) :Boolean $9],
        $9 [(not $8) :Boolean $8]}
      '[$8]

      '[(and (every? [x [x y]] (< x 1))
             (every? [z [x y]] (< z 1))
             (every? [x [0]] (< x 1)))]
      '{$1 [x :Integer]
        $2 [y :Integer]
        $3 [[$1 $2] [:Vec :Integer]]
        $4 [($local 1 :Integer) :Integer]
        $5 [1 :Integer]
        $6 [(< $4 $5) :Boolean $7]
        $7 [(<= $5 $4) :Boolean $6]
        $8 [(every? $4 $3 $6) :Boolean $9]
        $9 [(not $8) :Boolean $8]
        $10 [0 :Integer]
        $11 [[$10] [:Vec :Integer]]
        $12 [(every? $4 $11 $6) :Boolean $13]
        $13 [(not $12) :Boolean $12]
        $14 [(and $8 $8 $12) :Boolean $15]
        $15 [(not $14) :Boolean $14]}
      '[$14]

      '[(= 1 (map [x [x y]] (* x 2)))]
      '{$1 [1 :Integer]
        $2 [x :Integer]
        $3 [y :Integer]
        $4 [[$2 $3] [:Vec :Integer]]
        $5 [($local 1 :Integer) :Integer]
        $6 [2 :Integer]
        $7 [(* $5 $6) :Integer]
        $8 [(map $5 $4 $7) [:Vec :Integer]]
        $9 [(= $1 $8) :Boolean $10]
        $10 [(not= $1 $8) :Boolean $9]}
      '[$9])))

(deftest test-spec-to-ssa-with-refinements
  (let [senv (var-types/to-halite-spec-env
              '{:ws/A
                {:fields {:an :Integer}
                 :constraints #{{:name "a1" :expr (< 0 (+ 1 an))}}
                 :refines-to {:ws/B {:expr (when (< an 10)
                                             {:$type :ws/B
                                              :bn (+ 1 an)})}}}
                :ws/B
                {:fields {:bn :Integer}}})
        spec (ssa/spec-to-ssa (envs/spec-env senv) (:ws/A senv))]
    (is (= '{:fields {:an :Integer}
             :ssa-graph {:dgraph {$1 [0 :Integer]
                                  $2 [1 :Integer]
                                  $3 [an :Integer]
                                  $4 [(+ $2 $3) :Integer]
                                  $5 [(< $1 $4) :Boolean $6]
                                  $6 [(<= $4 $1) :Boolean $5]
                                  $7 [10 :Integer]
                                  $8 [(< $3 $7) :Boolean $9]
                                  $9 [(<= $7 $3) :Boolean $8]
                                  $10 [{:$type :ws/B, :bn $4} [:Instance :ws/B]]
                                  $11 [:Unset :Unset]
                                  $12 [(if $8 $10 $11) [:Maybe [:Instance :ws/B]]]}
                         :next-id 13
                         :form-ids {0 $1,
                                    1 $2,
                                    10 $7,
                                    an $3,
                                    (<= $4 $1) $6,
                                    (<= $7 $3) $9,
                                    (if $8 $10 $11) $12,
                                    :Unset $11
                                    {:$type :ws/B, :bn $4} $10,
                                    (+ $2 $3) $4,
                                    (< $1 $4) $5,
                                    (< $3 $7) $8}}
             :constraints [["a1" $5]]
             :refines-to {:ws/B {:expr $12}}}
           spec))

    (is (= '{:fields {:an :Integer}
             :constraints [["$all" (< 0 (+ 1 an))]]
             :refines-to {:ws/B {:expr (if (< an 10)
                                         {:$type :ws/B
                                          :bn (+ 1 an)}
                                         $no-value)}}}
           (ssa/spec-from-ssa spec)))))

(deftest test-form-to-ssa-correctly-handles-node-references-in-if-value
  (let [senv (envs/spec-env
              (var-types/to-halite-spec-env
               '{:ws/A
                 {:fields {:s [:Maybe :Boolean] :p [:Maybe :Boolean]}
                  :constraints #{{:name "c1" :expr (= s p)}}}}))
        sctx (ssa/build-spec-ctx senv :ws/A)
        a (:ws/A sctx)
        s-id (ssa/find-form (:ssa-graph a) 's)
        ctx (ssa/make-ssa-ctx sctx a)]
    (is (= '{$1 [s [:Maybe :Boolean]],
             $2 [p [:Maybe :Boolean]],
             $3 [(= $1 $2) :Boolean $4],
             $4 [(not= $1 $2) :Boolean $3],
             $5 [:Unset :Unset],
             $6 [($value? $1) :Boolean $7],
             $7 [(not $6) :Boolean $6],
             $8 [($value! $1) :Boolean $9],
             $9 [(not $8) :Boolean $8]
             $10 [true :Boolean $11],
             $11 [false :Boolean $10],
             $12 [(if $6 $8 $11) :Boolean $13],
             $13 [(not $12) :Boolean $12]}
           (:dgraph (first (ssa/form-to-ssa ctx (list 'if-value s-id s-id false))))))

    (is (= '{$1 [s [:Maybe :Boolean]],
             $2 [p [:Maybe :Boolean]],
             $3 [(= $1 $2) :Boolean $4],
             $4 [(not= $1 $2) :Boolean $3],
             $5 [:Unset :Unset],
             $6 [($value? $1) :Boolean $7],
             $7 [(not $6) :Boolean $6],
             $8 [($value! $1) :Boolean $9],
             $9 [(not $8) :Boolean $8],
             $10 [(if $6 $5 $5) :Unset]}
           (:dgraph (first (ssa/form-to-ssa ctx (list 'if-value s-id '$no-value s-id))))))))

(def form-from-ssa* #'ssa/form-from-ssa*)

(s/defn ^:private guards-from-ssa :- {s/Any s/Any}
  "To facilitate testing"
  [ssa-graph :- ssa/SSAGraph, bound :- #{s/Symbol}, guards]
  (binding [ssa/*hide-non-halite-ops* false]
    (let [ordering (zipmap (ssa/topo-sort ssa-graph) (range))]
      (-> guards
          (update-keys (partial form-from-ssa* ssa-graph ordering {} bound #{}))
          (update-vals
           (fn [guards]
             (if (seq guards)
               (->> guards
                    (map #(->> % (map (partial form-from-ssa* ssa-graph ordering {} bound #{})) (mk-junct 'and)))
                    (mk-junct 'or))
               true)))))))

(deftest test-compute-guards
  (let [senv (var-types/to-halite-spec-env
              '{:ws/A
                {:fields {:v [:Maybe :Integer], :w [:Maybe :Integer], :x :Integer, :y :Integer, :b1 :Boolean, :b2 :Boolean}}
                :ws/Foo
                {:fields {:x :Integer, :y :Integer}}})]
    (are [constraints guards]
         (= guards
            (let [spec-info (-> senv
                                (update-in [:ws/A :constraints] into
                                           (map-indexed #(vector (str "c" %1) %2) constraints))
                                (envs/spec-env)
                                (ssa/build-spec-ctx :ws/A)
                                :ws/A)]
              (-> spec-info
                  :ssa-graph
                  (ssa/compute-guards (->> spec-info :constraints (map second) set))
                  (->> (guards-from-ssa
                        (:ssa-graph spec-info)
                        (->> spec-info :fields keys (map symbol) set))
                       (remove (comp true? val))
                       (into {})))))

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

      '[(= 1 (if b1 (get {:$type :ws/Foo :x 1 :y 2} :x) 3))]
      '{2 b1
        3 (not b1)
        {:$type :ws/Foo :x 1 :y 2} b1
        (get {:$type :ws/Foo :x 1 :y 2} :x) b1}

      '[(if b1 (= w v) true)]
      '{w b1
        v b1
        (= w v) b1
        true (not b1)}

      '[(if b1 (not= 0 (if-value w (+ w 1) 0)) true)]
      '{0 b1
        ($value? w) b1
        ($value! w) (and ($value? w) b1)
        w b1
        (+ ($value! w) 1) (and ($value? w) b1)
        1 (and ($value? w) b1)
        true (not b1)
        (if ($value? w) (+ ($value! w) 1) 0) b1
        (not= 0 (if ($value? w) (+ ($value! w) 1) 0)) b1}

      '[(if b1 [1 2] [2 3])]
      '{1 b1
        [1 2] b1
        3 (not b1)
        [2 3] (not b1)}

      '[(if b1 (get [1] x) y)]
      '{1 b1
        [1] b1
        x b1
        (get [1] x) b1
        y (not b1)}

      '[(every? [x [1 2 3]] b1)]
      '{b1 ($local 1 :Integer)})))

(def normalize-vars #'ssa/normalize-vars)

(deftest test-normalize-vars
  (are [expr expr']
       (= expr' (normalize-vars #{'v3} expr))

    1 1
    true true
    "foo" "foo"
    '(+ 1 2) '(+ 1 2)

    '(let [x 1, $42 a] (+ x $42))
    '(let [x 1, v1 a] (+ x v1))

    '(let [$42 x] (let [$42 y] (+ $42 $42)))
    '(let [v1 x] (let [v2 y] (+ v2 v2)))

    '(let [$10 1, $20 2, $30 3] (+ $10 $20 v3 $30))
    '(let [v1 1, v2 2, v4 3] (+ v1 v2 v3 v4))))

(deftest test-foo
  (let [spec-info {:fields {:v [:Maybe :Integer], :w [:Maybe :Integer], :x :Integer, :y :Integer, :z :Integer, :b :Boolean, :c [:Instance :ws/C]
                            :s :String}}]

    (are [constraints dgraph new-constraint]
         (= [["$all" new-constraint]]
            (-> spec-info
                (assoc :ssa-graph (ssa/make-ssa-graph dgraph)
                       :constraints (vec (map-indexed #(vector (str "c" %1) %2) constraints)))
                (ssa/spec-from-ssa)
                :constraints))
      '[$9]
      '{$1 [true :Boolean $2]
        $2 [false :Boolean $1]
        $3 [1 :Integer]
        $4 [{:$type :ws/C :cn $3} [:Instance :ws/C]]
        $5 [c [:Instance :ws/C]]
        $6 [(if $1 $4 $5) [:Instance :ws/C]]
        $7 [(valid? $6) :Boolean $8]
        $8 [(not $7) :Boolean $7]
        $9 [(if $7 $6 $5) [:Instance :ws/C]]}
      '(if (valid? (if true {:cn 1, :$type :ws/C} c))
         (if true {:cn 1, :$type :ws/C} c)
         c))))

(deftest test-spec-from-ssa
  (let [spec-info {:fields {:v [:Maybe :Integer], :w [:Maybe :Integer], :x :Integer, :y :Integer, :z :Integer, :b :Boolean, :c [:Instance :ws/C]
                            :s :String}}]

    (are [constraints dgraph new-constraint]
         (= [["$all" new-constraint]]
            (-> spec-info
                (assoc :ssa-graph (ssa/make-ssa-graph dgraph)
                       :constraints (vec (map-indexed #(vector (str "c" %1) %2) constraints)))
                (ssa/spec-from-ssa)
                :constraints))

      [] {} true

      '[$1] '{$1 [b :Boolean $2] $2 [(not $1) :Boolean $1]} 'b

      '[$7]
      '{$1 [true :Boolean $2]
        $2 [false :Boolean $1]
        $3 ["never" :String]
        $4 [(error $3) :Nothing]
        $5 [5 :Integer]
        $6 [(if $2 $4 $5) :Integer]
        $7 [(if $2 $4 $6) :Integer]}
      '(if false (error "never") (if false (error "never") 5))

      '[$3]
      '{$1 [1 :Integer]
        $2 [:Unset :Unset]
        $3 [(= $1 $2) :Boolean $4]
        $4 [(not= $1 $2) :Boolean $3]}
      '(= 1 $no-value)

      '[$3 $5]
      '{$1 [x :Integer]
        $2 [1 :Integer]
        $3 [(< $2 $1) :Boolean $6]
        $4 [10 :Integer]
        $5 [(< $1 $4) :Boolean $7]
        $6 [(<= $1 $2) :Boolean $3]
        $7 [(<= $4 $1) :Boolean $5]}
      '(and (< 1 x) (< x 10))

      '[$6]
      '{$1 [1 :Integer]
        $2 [0 :Integer]
        $3 [(div $1 $2) :Integer]
        $4 [true :Boolean $5]
        $5 [false :Boolean $4]
        $6 [($do! $3 $4) :Boolean $7]
        $7 [(not $6) :Boolean $6]}
      '(let [v1 (div 1 0)]
         true)

      '[$7 $8]
      '{$1 [(+ $2 $3) :Integer]
        $2 [x :Integer]
        $3 [y :Integer]
        $4 [z :Integer]
        $5 [10 :Integer]
        $6 [2 :Integer]
        $7 [(< $1 $5) :Boolean $11]
        $8 [(= $9 $10) :Boolean $12]
        $9 [(+ $3 $4) :Integer]
        $10 [(* $6 $1) :Integer]
        $11 [(<= $5 $1) :Boolean $7]
        $12 [(not= $9 $10) :Boolean $8]}
      '(let [v1 (+ x y)]
         (and (< v1 10)
              (= (+ y z) (* 2 v1))))

      '[$1]
      '{$1 [(get $2 :foo) :Boolean $4]
        $3 [b :Boolean $5]
        $2 [{:$type :ws/Foo :foo $3} [:Instance :ws/Foo]]
        $4 [(not $1) :Boolean $1]
        $5 [(not $3) :Boolean $3]}
      '(get {:$type :ws/Foo :foo b} :foo)

      '[$4]
      '{$1 [12 :Integer]
        $3 [x :Integer]
        $4 [(< $3 $1) :Boolean $5]
        $5 [(<= $1 $3) :Boolean $4]}
      '(< x 12)

      '[$1]
      '{$1 [(= $3 $3) :Boolean $2]
        $2 [(not= $3 $3) :Boolean $1]
        $3 [:Unset :Unset]}
      '(= $no-value $no-value)

      '[$6]
      '{$1 [w [:Maybe :Integer]]
        $2 [($value? $1) :Boolean $3]
        $3 [(not $2) :Boolean $2]
        $4 [($value! $1) :Integer]
        $5 [1 :Integer]
        $6 [(if $2 $4 $5) :Integer]}
      '(if-value w w 1)

      ;; The instance literal should not be referenced outside the `valid?` guard:
      '[$5]
      '{$1 [{:$type :ws/C} [:Instance :ws/C]],
        $2 [(valid? $1) :Boolean $3],
        $3 [(not $2) :Boolean $2],
        $4 [c [:Instance :ws/C]],
        $5 [(if $2 $1 $4) [:Instance :ws/C]]}
      '(if (valid? {:$type :ws/C})
         {:$type :ws/C}
         c)

      '[$9]
      '{$1 [true :Boolean $2]
        $2 [false :Boolean $1]
        $3 [1 :Integer]
        $4 [{:$type :ws/C :cn $3} [:Instance :ws/C]]
        $5 [c [:Instance :ws/C]]
        $6 [(if $1 $4 $5) [:Instance :ws/C]]
        $7 [(valid? $6) :Boolean $8]
        $8 [(not $7) :Boolean $7]
        $9 [(if $7 $6 $5) [:Instance :ws/C]]}
      '(if (valid? (if true {:cn 1, :$type :ws/C} c))
         (if true {:cn 1, :$type :ws/C} c)
         c)

      '($10)
      '{$1 [1 :Integer]
        $2 [{:$type :ws/C :cn $1} [:Instance :ws/C]]
        $3 [(valid? $2) :Boolean $4]
        $4 [(not $3) :Boolean $3]
        $5 [2 :Integer]
        $6 [4 :Integer]
        $7 [(if $3 $5 $6) :Integer]
        $8 [($do! $2 $7) :Integer]
        $9 [8 :Integer]
        $10 [(if $3 $8 $9) :Integer]}
      '(if (valid? {:cn 1, :$type :ws/C})
         (let [v1 {:cn 1, :$type :ws/C}]
           (if (valid? v1) 2 4)) 8)

      '[$14]
      '{$1 [b :Boolean $2]
        $2 [(not $1) :Boolean $1]
        $3 [v [:Maybe :Integer]]
        $4 [w [:Maybe :Integer]]
        $5 [(if $1 $3 $4) [:Maybe :Integer]]
        $6 [($value? $5) :Boolean $7]
        $7 [(not $6) :Boolean $6]
        $8 [($value! $5) :Integer]
        $9 [10 :Integer]
        $10 [(< $8 $9) :Boolean $11]
        $11 ((<= $9 $8) :Boolean $10)
        $12 [true :Boolean $13]
        $13 [false :Boolean $12]
        $14 [(if $6 $10 $12) :Boolean $15]
        $15 [(not $14) :Boolean $14]}
      '(let [v1 (if b v w)]
         (if-value v1 (< v1 10) true))

      '[$14]
      '{$1 [v [:Maybe :Integer]],
        $2 [($value? $1) :Boolean $3],
        $3 [(not $2) :Boolean $2],
        $4 [($value! $1) :Integer],
        $5 [c [:Instance :ws/C]],
        $6 [(get $5 :cn) [:Maybe :Integer]],
        $7 [($value? $6) :Boolean $8],
        $8 [(not $7) :Boolean $7],
        $9 [($value! $6) :Integer]
        $10 [1 :Integer],
        $11 [(+ $9 $10) :Integer],
        $12 [0 :Integer],
        $13 [(if $7 $11 $12) :Integer],
        $14 [(if $2 $13 $10) :Integer]}
      '(if-value v
                 (let [v1 (get c :cn)]
                   (if-value v1 (+ v1 1) 0))
                 1)

      '[$1]
      '{$1 [(if $2 $3 $4) :Integer]
        $2 [($value? $5) :Boolean $7]
        $3 [1 :Integer]
        $4 [2 :Integer]
        $5 [(get $6 :foo) [:Maybe :Integer]]
        $6 [{:$type :ws/C :foo $8} [:Instance :ws/C]]
        $7 [(not $2) :Boolean $2]
        $8 [12 :Integer]}
      '(let [v1 (get {:$type :ws/C :foo 12} :foo)]
         (if-value v1 1 2))

      '[$2]
      '{$1 [:Unset :Unset]
        $2 [($value? $1) :Boolean $3]
        $3 [(not $2) :Boolean $2]}
      '($value? $no-value)

      '[$3]
      '{$1 ["foo" :String]
        $2 [s :String]
        $3 [(= $1 $2) :Boolean $4]
        $4 [(not= $1 $2) :Boolean $3]}
      '(= "foo" s)

      '[$5]
      '{$1 [true :Boolean $2]
        $2 [false :Boolean $1]
        $3 ["nope" :String]
        $4 [(error $3) :Nothing]
        $5 [(if $1 $4 $1) :Boolean $6]
        $6 [(not $5) :Boolean $5]}
      '(if true (error "nope") true)

      ;; vector stuff
      '[$7]
      '{$1 [1 :Integer],
        $2 [2 :Integer],
        $3 [3 :Integer],
        $4 [[$1 $2 $3] [:Vec :Integer]],
        $5 [[] [:Vec :Nothing]],
        $6 [(= $4 $5) :Boolean $7],
        $7 [(not= $4 $5) :Boolean $6]}
      '(not= [1 2 3] [])

      '[$5]
      '{$1 [1 :Integer],
        $2 [[$1] [:Vec :Integer]],
        $3 [x :Integer],
        $4 [(get $2 $3) :Integer],
        $5 [(= $1 $4) :Boolean $6],
        $6 [(not= $1 $4) :Boolean $5]}
      '(= 1 (get [1] x))

      '[$14]
      '{$1 [x :Integer]
        $2 [y :Integer]
        $3 [[$1 $2] [:Vec :Integer]]
        $4 [($local 1 :Integer) :Integer]
        $5 [1 :Integer]
        $6 [(< $4 $5) :Boolean $7]
        $7 [(<= $5 $4) :Boolean $6]
        $8 [(every? $4 $3 $6) :Boolean $9]
        $9 [(not $8) :Boolean $8]
        $10 [0 :Integer]
        $11 [[$10] [:Vec :Integer]]
        $12 [(every? $4 $11 $6) :Boolean $13]
        $13 [(not $12) :Boolean $12]
        $14 [(and $8 $8 $12) :Boolean $15]
        $15 [(not $14) :Boolean $14]}
      '(let [v1 (every? [v1 [x y]] (< v1 1))]
         (and v1 v1 (every? [v2 [0]] (< v2 1))))

      '[$9]
      '{$1 [1 :Integer]
        $2 [x :Integer]
        $3 [y :Integer]
        $4 [[$2 $3] [:Vec :Integer]]
        $5 [($local 1 :Integer) :Integer]
        $6 [2 :Integer]
        $7 [(* $5 $6) :Integer]
        $8 [(map $5 $4 $7) [:Vec :Integer]]
        $9 [(= $1 $8) :Boolean $10]
        $10 [(not= $1 $8) :Boolean $9]}
      '(= 1 (map [v1 [x y]] (* v1 2))))))

(deftest test-spec-from-ssa-preserves-guards
  (let [senv (envs/spec-env
              (var-types/to-halite-spec-env
               '{:ws/A
                 {:fields {:p :Boolean, :q :Boolean}
                  :constraints #{{:name "c1" :expr (if p (< (div 10 0) 1) true)}
                                 {:name "c2" :expr (if q true (and (< 1 (div 10 0)) (< (div 10 0) 1)))}
                                 {:name "c3" :expr (if (not q) (< 1 (div 10 0)) true)}}}}))
        sctx (ssa/build-spec-ctx senv :ws/A)]
    (is (= '[["$all" (and (if (not q) (< 1 (div 10 0)) true)
                          (if p (< (div 10 0) 1) true)
                          (if q true (let [v1 (div 10 0)] (and (< 1 v1) (< v1 1)))))]]
           (-> sctx (ssa/build-spec-env) (envs/lookup-spec :ws/A) :constraints)))))

(deftest test-semantics-preservation
  ;; Test that this spec has the same interpretation after round-tripping through SSA representation.
  (let [senv (envs/spec-env
              (var-types/to-halite-spec-env
               {:ws/A
                '{:fields {:x :Integer, :y :Integer, :p :Boolean, :q :Boolean}
                  :constraints #{{:name "a1" :expr (< x y)}
                                 {:name "a2" :expr (if p (< (div 15 x) 10) true)}
                                 {:name "a3" :expr (if q (< 1 (div 15 x)) true)}
                                 {:name "a4" :expr (and (<= 0 x) (< x 10))}
                                 {:name "a5" :expr (and (<= 0 y) (< y 10))}}}}))
        senv' (-> senv (ssa/build-spec-ctx :ws/A) (ssa/build-spec-env))
        tenv (envs/type-env {})
        env (envs/env {})
        check (fn [senv inst]
                (try (halite/eval-expr senv tenv env (list 'valid? inst))
                     (catch Exception ex
                       :runtime-error)))]
    (doseq [x (range -1 11), y (range -1 11), p [true false], q [true false]]
      (let [inst {:$type :ws/A :x x :y y :p p :q q}]
        (testing (pr-str inst)
          (is (= (check senv inst) (check senv' inst))))))))

(deftest test-replace-in-expr
  (let [senv (envs/spec-env
              '{:ws/A {:fields {:an :Integer}}})
        tenv (envs/type-env '{x :Integer, y :Integer, p :Boolean})
        ctx {:senv senv, :tenv tenv, :env {}, :ssa-graph ssa/empty-ssa-graph :local-stack []}
        [ssa-graph1 orig-id] (ssa/form-to-ssa ctx '(let [foo (+ x (- 0 y))]
                                                     (or p
                                                         (< foo 24)
                                                         (<= 0 (get {:$type :ws/A :an (* foo 2)} :an)))))
        old-add-id (->> ssa-graph1
                        :dgraph
                        (filter (fn [[id [form]]] (and (seq? form) (= '+ (first form)))))
                        ffirst)
        [ssa-graph2 add-id] (ssa/form-to-ssa
                             (assoc ctx :ssa-graph ssa-graph1)
                             '(+ x y))
        ;;_ (clojure.pprint/pprint (sort-by #(Integer/parseInt (subs (name (first %)) 1)) dgraph2))
        [ssa-graph3 new-id] (ssa/replace-in-expr senv ssa-graph2 orig-id {old-add-id add-id})]
    (is (= '(let [v1 (+ x y)]
              (or p
                  (< v1 24)
                  (<= 0 (get {:$type :ws/A, :an (* v1 2)} :an))))
           (ssa/form-from-ssa '#{x y p} ssa-graph3 new-id)))))

(deftest test-roundtripping-nested-if-values
  (let [spec '{:fields {:n [:Maybe :Integer]}
               :constraints [["$all" (if-value n (if-value n n 0) 1)]]
               :refines-to {}}]
    ;; Round-tripping through SSA shouldn't introduce let bindings for nested, redundant if-value expressions.
    ;; Not only does it hurt readability, it actually causes propagate failures (because choco-clj's rules on
    ;; use of if-value are stricter than halite's, and disallow use of if-value with let-bound symbols).
    ;;
    ;; This is a regression test. Round-tripping spec used to produce (if-value n (let [v1 n] (if-value v1 v1 0)) 1).
    (is (= spec (ssa/spec-from-ssa (ssa/spec-to-ssa {} spec))))))

(deftest test-roundtripping-when-value
  (let [spec '{:fields {:n [:Maybe :Integer]}
               :constraints [["$all" (when-value n (+ n 1))]]
               :refines-to {}}]
    (is (= spec (ssa/spec-from-ssa (ssa/spec-to-ssa {} spec))))))

(deftest test-as-spec-env
  (is (nil?
       (envs/lookup-spec (ssa/as-spec-env {}) :ws/A)))
  (is (= {}
         (envs/lookup-spec (ssa/as-spec-env {:ws/A {:ssa-graph ssa/empty-ssa-graph}}) :ws/A))))

(deftest test-form-to-ssa
  (let [senv (envs/spec-env {})
        tenv (envs/type-env '{x [:Vec :Integer]})
        ctx {:senv senv, :tenv tenv, :env {}, :ssa-graph ssa/empty-ssa-graph :local-stack []}]
    (is (= '[{:dgraph {$1 [x [:Vec :Integer]]
                       $2 [1 :Integer]
                       $3 [2 :Integer]
                       $4 [[$2 $3] [:Vec :Integer]]
                       $5 [(concat $1 $4) [:Vec :Integer]]}
              :next-id 6
              :form-ids {x $1
                         1 $2
                         2 $3
                         [$2 $3] $4
                         (concat $1 $4) $5}}
             $5]
           (ssa/form-to-ssa ctx '(concat x [1 2]))))
    (is (= '[{:dgraph {$1 [x [:Vec :Integer]]
                       $2 [1 :Integer]
                       $3 [(conj $1 $2) [:Vec :Integer]]}
              :next-id 4
              :form-ids {x $1
                         1 $2
                         (conj $1 $2) $3}}
             $3]
           (ssa/form-to-ssa ctx '(conj x 1)))))
  (let [senv (envs/spec-env
              '{:ws/A {:fields {:an :Integer}}})
        tenv (envs/type-env '{a [:Instance :ws/A], x :Integer, y :Integer, p :Boolean})
        ctx {:senv senv, :tenv tenv, :env {}, :ssa-graph ssa/empty-ssa-graph :local-stack []}]
    (is (= '[{:dgraph {$1 [a [:Instance :ws/A]]
                       $2 [(get $1 :an) :Integer]}
              :next-id 3
              :form-ids {a $1
                         (get $1 :an) $2}}
             $2]
           (ssa/form-to-ssa ctx '(get a :an))))))

;; (run-tests)
