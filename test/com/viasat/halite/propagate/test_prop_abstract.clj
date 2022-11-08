;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.test-prop-abstract
  (:require [com.viasat.halite.choco-clj-opt :as choco-clj]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.propagate.prop-abstract :as pa]
            [com.viasat.halite.transpile.lowering :as lowering]
            [com.viasat.halite.transpile.rewriting :as rewriting :refer [with-summarized-trace-for]]
            [com.viasat.halite.transpile.simplify :as simplify]
            [com.viasat.halite.transpile.ssa :as ssa]
            [com.viasat.halite.transpile.util :refer [fixpoint]]
            [schema.core :as s]
            [schema.test])
  (:use clojure.test)
  (:import [clojure.lang ExceptionInfo]))

(def simplest-abstract-var-example
  '{:ws/W
    {:abstract? true
     :spec-vars {:wn :Integer}
     :constraints {:wn_range (and (< 2 wn) (< wn 8))}}
    :ws/A
    {:spec-vars {:a :Integer}
     :constraints {:ca (< a 6)}
     :refines-to {:ws/W {:expr {:$type :ws/W, :wn (+ a 1)}}}}
    :ws/B
    {:spec-vars {:b :Integer}
     :constraints {:cb (< 5 b)}
     :refines-to {:ws/W {:expr {:$type :ws/W, :wn (- b 2)}}}}
    :ws/C
    {:spec-vars {:w [:Instance :* #{:ws/W}] :cn :Integer}
     :constraints {:c1 (< cn (get (refine-to w :ws/W) :wn))}}})

(def optional-abstract-var-example
  (assoc
   simplest-abstract-var-example
   :ws/C
   '{:spec-vars {:w [:Maybe [:Instance :ws/W]] :cn :Integer}
     :constraints {:c1 (< cn (if-value w (get (refine-to w :ws/W) :wn) 10))}}))

(def lower-abstract-vars #'pa/lower-abstract-vars)

(deftest test-lower-abstract-vars
  (s/with-fn-validation
    (let [alts {:ws/W {:ws/A 0, :ws/B 1}}
          specs simplest-abstract-var-example
          sctx (update-vals specs #(ssa/spec-to-ssa specs %))
          c (:ws/C sctx)]
      (is (= '{:spec-vars
               {:w$type :Integer
                :w$0 [:Maybe [:Instance :ws/A]]
                :w$1 [:Maybe [:Instance :ws/B]]
                :cn :Integer}
               :constraints
               {:c1 (< cn (get (refine-to (if-value w$0 w$0 (if-value w$1 w$1 (error "unreachable"))) :ws/W) :wn))
                "w$0" (= (= w$type 0) (if-value w$0 true false))
                "w$1" (= (= w$type 1) (if-value w$1 true false))}
               :refines-to {}}
             (-> sctx
                 (lower-abstract-vars alts :ws/C c)
                 :ws/C
                 (ssa/spec-from-ssa {})))))))

(deftest test-lower-optional-abstract-vars
  (s/with-fn-validation
    (let [alts {:ws/W {:ws/A 0, :ws/B 1}}
          specs optional-abstract-var-example
          sctx (update-vals specs #(ssa/spec-to-ssa specs %))
          c (:ws/C sctx)]

      (is (= '{:spec-vars
               {:w$type [:Maybe :Integer]
                :w$0 [:Maybe [:Instance :ws/A]]
                :w$1 [:Maybe [:Instance :ws/B]]
                :cn :Integer}
               :constraints
               {:c1 (let [v1 (if-value w$0 w$0 (when-value w$1 w$1))]
                      (< cn (if-value v1 (get (refine-to v1 :ws/W) :wn) 10)))
                "w$0" (= (= w$type 0) (if-value w$0 true false))
                "w$1" (= (= w$type 1) (if-value w$1 true false))}
               :refines-to {}}
             (-> sctx
                 (lower-abstract-vars alts :ws/C c)
                 :ws/C
                 (ssa/spec-from-ssa {})))))))

(def lower-abstract-bounds #'pa/lower-abstract-bounds)

(deftest test-lower-abstract-bounds
  (s/with-fn-validation
    (let [alts {:ws/W {:ws/A 0, :ws/B 1}}
          senv (envs/spec-env simplest-abstract-var-example)]
      (are [in out]
           (= out (lower-abstract-bounds [] in senv alts))

        ;; enumerate the discriminator's domain
        {:$type :ws/C}
        {:$type :ws/C :w$type {:$in #{0 1}}
         :w$0 {:$type [:Maybe :ws/A]} :w$1 {:$type [:Maybe :ws/B]}}

        {:$type :ws/C :cn {:$in [0 5]}}
        {:$type :ws/C :cn {:$in [0 5]} :w$type {:$in #{0 1}}
         :w$0 {:$type [:Maybe :ws/A]} :w$1 {:$type [:Maybe :ws/B]}}

        ;; absence of an alternative in :$if is just absence of a constraint
        {:$type :ws/C :w {:$if {:ws/A {:a 12}}}}
        {:$type :ws/C
         :w$type {:$in #{0 1}}
         :w$0 {:$type [:Maybe :ws/A] :a 12}
         :w$1 {:$type [:Maybe :ws/B]}}

        ;; a concrete bound fixes the type
        {:$type :ws/C :w {:$type :ws/A}}
        {:$type :ws/C :w$type {:$in #{0}} :w$0 {:$type [:Maybe :ws/A]}}

        ;; absence of an alternative in :$in is a constraint
        {:$type :ws/C :w {:$in {:ws/A {}}}}
        {:$type :ws/C :w$type {:$in #{0}}
         :w$0 {:$type [:Maybe :ws/A]}}

        {:$type :ws/C :w {:$in {:ws/A {} :ws/B {:b 12}}}}
        {:$type :ws/C :w$type {:$in #{0 1}}
         :w$0 {:$type [:Maybe :ws/A]}
         :w$1 {:$type [:Maybe :ws/B] :b 12}}

        ;; $refines-to constraints get passed down
        {:$type :ws/C :w {:$if {:ws/A {:$refines-to {:ws/W {:wn 12}}}}}}
        {:$type :ws/C :w$type {:$in #{0 1}}
         :w$0 {:$type [:Maybe :ws/A] :$refines-to {:ws/W {:wn 12}}}
         :w$1 {:$type [:Maybe :ws/B]}}

        ;; unqualified :$refines-to applies to all alternatives
        {:$type :ws/C :w {:$refines-to {:ws/W {:wn 7}}}}
        {:$type :ws/C :w$type {:$in #{0 1}}
         :w$0 {:$type [:Maybe :ws/A] :$refines-to {:ws/W {:wn 7}}}
         :w$1 {:$type [:Maybe :ws/B] :$refines-to {:ws/W {:wn 7}}}}

        ;; TODO: Intersect concrete bounds
        ;; {:$type :ws/C :w {:$refines-to {:ws/W {:wn {:$in #{1 2 3}}}}
        ;;                   :$if {:ws/A {:$refines-to {:ws/W {:wn {:$in #{2 3 4}}}}}}}}
        ;; {:$type :ws/C :w$type {:$in #{0 1}}
        ;;  :w$0 {:$type [:Maybe :ws/A] :$refines-to {:ws/W {:wn {:$in #{2 3}}}}}
        ;;  :w$1 {:$type [:Maybe :ws/B] :$refines-to {:ws/W {:wn {:$in #{1 2 3}}}}}}
        ))))

(deftest test-lower-abstract-bounds-recursive
  (s/with-fn-validation
    (let [alts {:ws/W {:ws/A 0, :ws/B 1}}
          senv (envs/spec-env simplest-abstract-var-example)]
      (are [in out]
           (= out (lower-abstract-bounds
                   [:ws/C] ;; having the spec id in the parent-spec-ids, will cause the lowering to not be performed
                   in senv alts))

        {:$type :ws/C}
        {:$type :ws/C :w$type {:$in #{0 1}}}

        {:$type :ws/C :cn {:$in [0 5]}}
        {:$type :ws/C :cn {:$in [0 5]} :w$type {:$in #{0 1}}}))))

(deftest test-lower-optional-abstract-bounds
  (s/with-fn-validation
    (let [alts {:ws/W {:ws/A 0, :ws/B 1}}
          senv (envs/spec-env optional-abstract-var-example)]
      (are [in out]
           (= out (lower-abstract-bounds [] in senv alts))

        ;; discriminator's domain includes :Unset
        {:$type :ws/C}
        {:$type :ws/C :w$type {:$in #{0 1 :Unset}}
         :w$0 {:$type [:Maybe :ws/A]} :w$1 {:$type [:Maybe :ws/B]}}

        {:$type :ws/C :w {:$if {:ws/A {:a 12}}}}
        {:$type :ws/C
         :w$type {:$in #{0 1 :Unset}}
         :w$0 {:$type [:Maybe :ws/A] :a 12}
         :w$1 {:$type [:Maybe :ws/B]}}

        ;; it becomes possible to indicate with :$if that a value must be present
        {:$type :ws/C :w {:$if {:ws/A {:a 12} :Unset false}}}
        {:$type :ws/C
         :w$type {:$in #{0 1}}
         :w$0 {:$type [:Maybe :ws/A] :a 12}
         :w$1 {:$type [:Maybe :ws/B]}}

        ;; for :$in, a value must be present by default...
        {:$type :ws/C :w {:$in {:ws/A {:a 12}}}}
        {:$type :ws/C
         :w$type {:$in #{0}}
         :w$0 {:$type [:Maybe :ws/A] :a 12}}

        ;; ...but it's still possible to indicate that a value can be absent
        {:$type :ws/C :w {:$in {:ws/A {:a 12} :Unset true}}}
        {:$type :ws/C
         :w$type {:$in #{0 :Unset}}
         :w$0 {:$type [:Maybe :ws/A] :a 12}}

        {:$type :ws/C :w :Unset}
        {:$type :ws/C :w$type :Unset}))))

(def raise-abstract-bounds #'pa/raise-abstract-bounds)

(deftest test-raise-abstract-bounds
  (s/with-fn-validation
    (let [alts {:ws/W {:ws/A 0, :ws/B 1}}
          senv (envs/spec-env simplest-abstract-var-example)]
      (are [in out]
           (= out (raise-abstract-bounds in senv alts))

        {:$type :ws/C
         :cn 12
         :w$type {:$in #{0 1}}
         :w$0 {:$type [:Maybe :ws/A]
               :a {:$in [2 5]}
               :$refines-to {:ws/W {:wn 1}}}
         :w$1 {:$type [:Maybe :ws/B]
               :b {:$in #{6 7 8 9}}
               :$refines-to {:ws/W {:wn 2}}}}
        {:$type :ws/C
         :cn 12
         :w {:$in {:ws/A {:a {:$in [2 5]}
                          :$refines-to {:ws/W {:wn 1}}}
                   :ws/B {:b {:$in #{6 7 8 9}}
                          :$refines-to {:ws/W {:wn 2}}}}
             :$refines-to {:ws/W {:wn {:$in #{1 2}}}}}}

        {:$type :ws/C
         :cn 12
         :w$type 0
         :w$0 {:$type :ws/A
               :a {:$in [2 5]}
               :$refines-to #:ws{:W {:wn {:$in [3 6]}}}}
         :w$1 :Unset}
        {:$type :ws/C
         :cn 12
         :w {:$type :ws/A
             :a {:$in [2 5]}
             :$refines-to #:ws{:W {:wn {:$in [3 6]}}}}}))))

(deftest test-raise-optional-abstract-bounds
  (s/with-fn-validation
    (let [alts {:ws/W {:ws/A 0, :ws/B 1}}
          senv (envs/spec-env optional-abstract-var-example)]
      (are [in out]
           (= out (raise-abstract-bounds in senv alts))

        {:$type :ws/C
         :cn 12
         :w$type {:$in #{0 1 :Unset}}
         :w$0 {:$type [:Maybe :ws/A]
               :a {:$in [2 5]}
               :$refines-to {:ws/W {:wn 1}}}
         :w$1 {:$type [:Maybe :ws/B]
               :b {:$in #{6 7 8 9}}
               :$refines-to {:ws/W {:wn 2}}}}
        {:$type :ws/C
         :cn 12
         :w {:$in {:ws/A {:a {:$in [2 5]}
                          :$refines-to {:ws/W {:wn 1}}}
                   :ws/B {:b {:$in #{6 7 8 9}}
                          :$refines-to {:ws/W {:wn 2}}}
                   :Unset true}
             :$refines-to {:ws/W {:wn {:$in #{1 2}}}}}}

        {:$type :ws/C
         :cn 12
         :w$type :Unset
         :w$0 :Unset
         :w$1 :Unset}
        {:$type :ws/C :cn 12 :w :Unset}))))

(deftest test-propagate-for-abstract-variables
  (let [sctx (ssa/spec-map-to-ssa simplest-abstract-var-example)]
    (are [in out]
         (= out (pa/propagate sctx in))

      {:$type :ws/C}
      {:$type :ws/C
       :cn {:$in [-1000 6]}
       :w {:$in {:ws/A {:a {:$in [2 5]} :$refines-to {:ws/W {:wn {:$in [3 6]}}}}
                 :ws/B {:b {:$in [6 9]} :$refines-to {:ws/W {:wn {:$in [4 7]}}}}}
           :$refines-to {:ws/W {:wn {:$in [3 7]}}}}}

      {:$type :ws/C :w {:$type :ws/A}}
      {:$type :ws/C :w {:$type :ws/A :a {:$in [2 5]} :$refines-to {:ws/W {:wn {:$in [3 6]}}}}
       :cn {:$in [-1000 5]}}

      {:$type :ws/C :w {:$refines-to {:ws/W {:wn 7}}}}
      {:$type :ws/C :w {:$type :ws/B, :b 9 :$refines-to {:ws/W {:wn 7}}}
       :cn {:$in [-1000 6]}}

      {:$type :ws/C :w {:$refines-to {:ws/W {:wn {:$in #{6 7}}}}}}
      {:$type :ws/C,
       :cn {:$in [-1000 6]},
       :w {:$in {:ws/A {:$refines-to {:ws/W {:wn 6}}, :a 5},
                 :ws/B {:$refines-to {:ws/W {:wn {:$in #{6 7}}}}, :b {:$in [8 9]}}},
           :$refines-to {:ws/W {:wn {:$in #{6 7}}}}}})))

(deftest test-propagate-for-optional-abstract-variables
  (let [sctx (ssa/spec-map-to-ssa optional-abstract-var-example)]
    (are [in out]
         (= out (pa/propagate sctx in))

      {:$type :ws/C}
      {:$type :ws/C
       :cn {:$in [-1000 9]}
       :w {:$in {:ws/A {:a {:$in [2 5]} :$refines-to {:ws/W {:wn {:$in [3 6]}}}}
                 :ws/B {:b {:$in [6 9]} :$refines-to {:ws/W {:wn {:$in [4 7]}}}}
                 :Unset true}
           :$refines-to {:ws/W {:wn {:$in [3 7]}}}}}

      {:$type :ws/C :w {:$if {:Unset false}}}
      {:$type :ws/C,
       :cn {:$in [-1000 6]},
       :w {:$in {:ws/A {:$refines-to {:ws/W {:wn {:$in [3 6]}}}, :a {:$in [2 5]}},
                 :ws/B {:$refines-to {:ws/W {:wn {:$in [4 7]}}}, :b {:$in [6 9]}}},
           :$refines-to {:ws/W {:wn {:$in [3 7]}}}}}

      {:$type :ws/C :w {:$in {:ws/A {} :Unset true}}}
      {:$type :ws/C,
       :cn {:$in [-1000 9]},
       :w {:$in {:Unset true, :ws/A {:$refines-to {:ws/W {:wn {:$in [3 6]}}}, :a {:$in [2 5]}}},
           :$refines-to {:ws/W {:wn {:$in [3 6]}}}}})))

(def nested-abstracts-example
  '{:ws/W {:abstract? true
           :spec-vars {:wn :Integer}}

    :ws/A {:spec-vars {:an :Integer}
           :refines-to {:ws/W {:expr {:$type :ws/W :wn an}}}}

    :ws/B {:spec-vars {:bn :Integer}
           :refines-to {:ws/W {:expr {:$type :ws/W :wn bn}}}}

    :ws/V {:abstract? true
           :spec-vars {:vn :Integer}}

    :ws/C {:spec-vars {:cw [:Instance :* #{:ws/W}] :cn :Integer}
           :constraints {:c1 (< 0 cn)}
           :refines-to {:ws/V {:expr {:$type :ws/V
                                      :vn (+ cn (get (refine-to cw :ws/W) :wn))}}}}
    :ws/D {:spec-vars {:dn :Integer}
           :refines-to {:ws/V {:expr {:$type :ws/V :vn dn}}}}

    :ws/E {:spec-vars {:v [:Instance :* #{:ws/V}]}}})

(deftest test-lower-abstract-bounds-for-nested-abstracts
  (s/with-fn-validation
    (let [alts {:ws/W {:ws/A 0 :ws/B 1}, :ws/V {:ws/C 0 :ws/D 1}}
          senv (envs/spec-env nested-abstracts-example)]
      (are [in out]
           (= out (lower-abstract-bounds [] in senv alts))

        {:$type :ws/E}
        {:$type :ws/E
         :v$type {:$in #{0 1}}
         :v$0 {:$type [:Maybe :ws/C]
               :cw$type {:$in #{0 1}}
               :cw$0 {:$type [:Maybe :ws/A]}
               :cw$1 {:$type [:Maybe :ws/B]}}
         :v$1 {:$type [:Maybe :ws/D]}}

        {:$type :ws/E :v {:$in {:ws/C {}}}}
        {:$type :ws/E
         :v$type {:$in #{0}}
         :v$0 {:$type [:Maybe :ws/C]
               :cw$type {:$in #{0 1}}
               :cw$0 {:$type [:Maybe :ws/A]}
               :cw$1 {:$type [:Maybe :ws/B]}}}

        {:$type :ws/E :v {:$in {:ws/C {:cw {:$type :ws/B}}}}}
        {:$type :ws/E
         :v$type {:$in #{0}}
         :v$0 {:$type [:Maybe :ws/C]
               :cw$type {:$in #{1}}
               :cw$1 {:$type [:Maybe :ws/B]}}}))))

(deftest test-raise-nested-abstract-bounds
  (let [alts {:ws/W {:ws/A 0 :ws/B 1}, :ws/V {:ws/C 0 :ws/D 1}}
        senv (envs/spec-env nested-abstracts-example)]
    (are [in out]
         (= out (raise-abstract-bounds in senv alts))

      {:$type :ws/E,
       :v$type {:$in #{0 1}},
       :v$0
       {:$type [:Maybe :ws/C],
        :cn {:$in [1 1000]},
        :cw$type {:$in #{0 1}},
        :cw$0
        {:$type [:Maybe :ws/A],
         :an {:$in [-988 11]},
         :$refines-to #:ws{:W {:wn {:$in [-1000 1000]}}}},
        :cw$1
        {:$type [:Maybe :ws/B],
         :bn {:$in [-1000 1000]},
         :$refines-to #:ws{:W {:wn {:$in [-1000 1000]}}}},
        :$refines-to #:ws{:V {:vn 12}}},
       :v$1 {:$type [:Maybe :ws/D], :dn 12, :$refines-to #:ws{:V {:vn 12}}}}

      {:$type :ws/E
       :v {:$in {:ws/C {:cn {:$in [1 1000]}
                        :cw {:$in {:ws/A {:an {:$in [-988 11]}
                                          :$refines-to {:ws/W {:wn {:$in [-1000 1000]}}}}
                                   :ws/B {:bn {:$in [-1000 1000]}
                                          :$refines-to {:ws/W {:wn {:$in [-1000 1000]}}}}}
                             :$refines-to {:ws/W {:wn {:$in [-1000 1000]}}}}
                        :$refines-to {:ws/V {:vn 12}}}
                 :ws/D {:dn 12
                        :$refines-to {:ws/V {:vn 12}}}}
           :$refines-to {:ws/V {:vn 12}}}})))

(deftest test-propagate-for-nested-abstracts
  (let [sctx (ssa/spec-map-to-ssa nested-abstracts-example)]
    (are [in out]
         (= out (pa/propagate sctx in))

      {:$type :ws/E :v {:$refines-to {:ws/V {:vn 12}}}}
      {:$type :ws/E,
       :v {:$in {:ws/C {:cn {:$in [1 1000]}
                        :$refines-to {:ws/V {:vn 12}},
                        :cw {:$in {:ws/A {:an {:$in [-988 11]}
                                          :$refines-to {:ws/W {:wn {:$in [-988 11]}}}}
                                   ;; TODO: figure out why bn's bounds aren't as tight as an's
                                   :ws/B {:bn {:$in [-1000 1000]}
                                          :$refines-to {:ws/W {:wn {:$in [-1000 1000]}}}}}
                             :$refines-to {:ws/W {:wn {:$in [-1000 1000]}}}}}
                 :ws/D {:$refines-to {:ws/V {:vn 12}}, :dn 12}},
           :$refines-to {:ws/V {:vn 12}}}})))

(def abstract-refinement-chain-example
  '{:ws/W {:abstract? true
           :spec-vars {:wn :Integer}}

    :ws/A1 {:spec-vars {:a1n :Integer}
            :refines-to {:ws/W {:expr {:$type :ws/W :wn (+ a1n 1)}}}}

    :ws/A2 {:abstract? true
            :spec-vars {:a2n :Integer}
            :refines-to {:ws/A1 {:expr {:$type :ws/A1 :a1n (+ a2n 1)}}}}

    :ws/A3 {:spec-vars {:a3n :Integer}
            :refines-to {:ws/A2 {:expr {:$type :ws/A2 :a2n (+ a3n 1)}}}}

    :ws/B {:spec-vars {:w [:Instance :* #{:ws/W}]}}})

(deftest test-abstract-refinement-chain
  (let [sctx (ssa/spec-map-to-ssa abstract-refinement-chain-example)]
    (are [in out]
         (= out (pa/propagate sctx in))

      {:$type :ws/B}
      {:$type :ws/B,
       :w {:$in {:ws/A1 {:$refines-to {:ws/W {:wn {:$in [-999 1000]}}}, :a1n {:$in [-1000 999]}},
                 :ws/A3 {:$refines-to {:ws/A1 {:a1n {:$in [-998 999]}},
                                       :ws/A2 {:a2n {:$in [-999 998]}},
                                       :ws/W {:wn {:$in [-997 1000]}}},
                         :a3n {:$in [-1000 997]}}}
           :$refines-to {:ws/W {:wn {:$in [-999 1000]}}}}}

      {:$type :ws/B :w {:$refines-to {:ws/W {:wn 42}}}}
      {:$type :ws/B,
       :w {:$in {:ws/A1 {:a1n 41
                         :$refines-to {:ws/W {:wn 42}}}
                 :ws/A3 {:a3n 39
                         :$refines-to {:ws/A1 {:a1n 41}
                                       :ws/A2 {:a2n 40}
                                       :ws/W {:wn 42}}}}
           :$refines-to {:ws/W {:wn 42}}}}

      {:$type :ws/B :w {:$type :ws/A3 :a3n {:$in #{3 4 5}}}}
      {:$type :ws/B
       :w {:$type :ws/A3
           :a3n {:$in #{3 4 5}}
           :$refines-to {:ws/A1 {:a1n {:$in [5 7]}}
                         :ws/A2 {:a2n {:$in [4 6]}}
                         :ws/W {:wn {:$in [6 8]}}}}}

      {:$type :ws/B :w {:$in {:ws/A1 {:a1n 12}
                              :ws/A3 {:a3n 10}}}}
      {:$type :ws/B,
       :w {:$in {:ws/A1 {:$refines-to {:ws/W {:wn 13}}, :a1n 12},
                 :ws/A3 {:$refines-to {:ws/A1 {:a1n 12},
                                       :ws/A2 {:a2n 11},
                                       :ws/W {:wn 13}},
                         :a3n 10}},
           :$refines-to {:ws/W {:wn 13}}}}

      ;; Doesn't work :(
      ;; {:$type :ws/B :w {:$refines-to {:ws/A2 {:a2n 42}}}}
      ;; nil
      )))

;; Trying to propgate this example currently causes a stack overflow.
;; We'll want to support this sort of structural recursion eventually, but it will take some doing.
(def list-example
  '{:ws/List {:abstract? true
              :spec-vars {:length :Integer
                          :sum :Integer}
              :constraints {:posLength (<= 0 length)}}
    :ws/Empty {:refines-to {:ws/List {:expr {:$type :ws/List :length 0 :sum 0}}}}
    :ws/Node {:spec-vars {:head :Integer
                          :tail :ws/List}
              :constraints {:shortList (< (get (refine-to tail :ws/List) :length) 5)}
              :refines-to {:ws/List {:expr (let [t (refine-to tail :ws/List)]
                                             {:$type :ws/List
                                              :length (+ 1 (get t :length))
                                              :sum (+ head (get t :sum))})}}}
    :ws/A {:spec-vars {:list [:Instance :* #{:ws/List}]}}})

(deftest test-tricky-inst-literal-simplification
  (let [sctx (ssa/spec-map-to-ssa
              '{:ws/A
                {:spec-vars {:b1 [:Maybe [:Instance :ws/B]]}
                 :constraints {:a2 (if-value b1 true false)}}
                :ws/B
                {}})]
    (is (= {:$type :ws/A :b1 {:$type :ws/B}}
           (pa/propagate sctx {:$type :ws/A})))))

(deftest test-test-abstract-spec-bound-on-concrete-field
  (let [sctx (ssa/spec-map-to-ssa
              '{:ws/A {}
                :ws/B {:refines-to {:ws/A {:expr {:$type :ws/A}}}}
                :ws/C {:spec-vars {:b [:Instance :ws/B]}}})
        expected {:$type :ws/C
                  :b {:$type :ws/B
                      :$refines-to {:ws/A {}}}}]
    (is (= expected
           (pa/propagate sctx {:$type :ws/C
                               :b {:$type :ws/B
                                   :$refines-to {:ws/A {}}}})))
    (is (= expected
           (pa/propagate sctx {:$type :ws/C
                               :b {;; omit the $type field
                                   :$refines-to {:ws/A {}}}})))
    (is (thrown-with-msg? ExceptionInfo #"Invalid bound for"
                          (pa/propagate sctx {:$type :ws/C
                                              :b {;; omit the $type field
                                                  :$refines-to {:ws/A {}}
                                                  ;; with the :$if field, it cannot be promoted to a concrete bound
                                                  :$if {}}})))
    (is (thrown-with-msg? ExceptionInfo #"Invalid bound for"
                          (pa/propagate sctx {:$type :ws/C
                                              :b {;; omit the $type field
                                                  :$refines-to {:ws/A {}}
                                                  ;; with the :$in field, it cannot be promoted to a concrete bound
                                                  :$in {}}})))))

(deftest test-no-concrete-spec-for-abstract-spec
  (is (= {:$type :ws/A
          :x {:$in {:Unset true
                    :ws/Y {:$refines-to #:ws{:X {}}}}
              :$refines-to #:ws{:X {}}}}
         (pa/propagate (ssa/spec-map-to-ssa
                        '{:ws/X {:abstract? true}
                          :ws/Y {:refines-to {:ws/X {:expr {:$type :ws/X}}}}
                          :ws/A {:spec-vars {:x [:Maybe [:Instance :* #{:ws/X}]]}}})
                       {:$type :ws/A}))
      "Base case where an optional field does have concrete specs to satisfy it")
  (is (= {:$type :ws/A
          :x :Unset}
         (pa/propagate (ssa/spec-map-to-ssa
                        '{:ws/X {:abstract? true}
                          :ws/A {:spec-vars {:x [:Maybe [:Instance :* #{:ws/X}]]}}})
                       {:$type :ws/A}))
      "Case where an optional field has no concrete specs to satisfy it")
  (is (= {:$type :ws/A
          :x {:$refines-to #:ws{:X {}}
              :$type :ws/Y}}
         (pa/propagate (ssa/spec-map-to-ssa
                        '{:ws/X {:abstract? true}
                          :ws/Y {:refines-to {:ws/X {:expr {:$type :ws/X}}}}
                          :ws/A {:spec-vars {:x [:Instance :* #{:ws/X}]}}})
                       {:$type :ws/A}))
      "Case where a mandatory field does have concrete specs to satisfy it")
  (is (thrown-with-msg? ExceptionInfo #"has empty domain"
                        (pa/propagate (ssa/spec-map-to-ssa
                                       '{:ws/X {:abstract? true}
                                         :ws/A {:spec-vars {:x [:Instance :* #{:ws/X}]}}})
                                      {:$type :ws/A}))
      "Case where a mandatory field has no concrete specs to satisfy it"))

(deftest test-concrete-above-abstract
  (is (= {:$type :ws/P
          :a {:$type :ws/A
              :x {:$refines-to {:ws/X {}}
                  :$in {:Unset true
                        :ws/Y {:$refines-to {:ws/X {}}}}}}}
         (pa/propagate (ssa/spec-map-to-ssa
                        '{:ws/X {:abstract? true}
                          :ws/Y {:refines-to {:ws/X {:expr {:$type :ws/X}}}}
                          :ws/A {:spec-vars {:x [:Maybe [:Instance :* #{:ws/X}]]}}
                          :ws/P {:spec-vars {:a [:Instance :ws/A]}}})
                       {:$type :ws/P})))
  (is (= {:$type :ws/P
          :a {:$type :ws/A
              :x {:$refines-to {:ws/X {}}
                  :$in {:Unset true
                        :ws/Y {:$refines-to {:ws/X {}}}}}}}
         (pa/propagate (ssa/spec-map-to-ssa
                        '{:ws/X {:abstract? true}
                          :ws/Y {:refines-to {:ws/X {:expr {:$type :ws/X}}}}
                          :ws/A {:spec-vars {:x [:Maybe [:Instance :* #{:ws/X}]]}}
                          :ws/P {:spec-vars {:a [:Instance :ws/A]}}})
                       {:$type :ws/P
                        :a {:$type :ws/A}}))))

(deftest test-recursive-structure
  ;; TODO: currently produces NPE in prop-composition
  #_(do
      (pa/propagate (ssa/spec-map-to-ssa {:spec/Cell {:spec-vars {:value "Integer"
                                                                  :next [:Maybe :spec/Cell]}}})
                    {:$type :spec/Cell})
      (pa/propagate (ssa/spec-map-to-ssa {:spec/Cell {:spec-vars {:value "Integer"
                                                                  :next [:Maybe :spec/Cell]}}})
                    {:$type :spec/Cell
                     :next {:$type :spec/Cell}})
      (pa/propagate (ssa/spec-map-to-ssa {:spec/Cell {:spec-vars {:value "Integer"
                                                                  :next [:Maybe :spec/Cell]}}})
                    {:$type :spec/Cell
                     :next {:$type :spec/Cell
                            :next {:$type :spec/Cell}}})))

(comment "
Stuff to do/remember regarding abstractness!

[x] union of refines-to bounds for alternatives
[x] optional abstract variables
[ ] nesting (including 'recursive' specs)
[x] improved refines-to constraints
[ ] properly handle optional refinements
")

;; (run-tests)
