;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.test-prop-extrinsic
  (:require [clojure.test :refer :all]
            [com.viasat.halite.eval :as eval]
            [com.viasat.halite.propagate.prop-extrinsic :as prop-extrinsic]
            [com.viasat.halite.transpile.rewriting :as rewriting]
            [com.viasat.halite.transpile.ssa :as ssa]
            [schema.core :as s]))

(defn prop-twice
  ([sctx bound] (prop-twice sctx prop-extrinsic/default-options bound))
  ([sctx opts bound]
   (let [out1 (prop-extrinsic/propagate sctx opts bound)
         out2 (prop-extrinsic/propagate sctx opts out1)]
     (testing "re-propagate"
       (is (= out2 out1)))
     out1)))

(deftest test-lower-specs
  (let [high-specs
        '{:t/A {:fields {:an :Integer}
                :constraints [["an" (< 8 an)]]
                :refines-to {:t/B {:extrinsic? true
                                   :expr {:$type :t/B
                                          :bn (+ 1 an)}}}}}

        low-specs
        '{:t/A {:fields {:an :Integer
                         :>?t$B? [:Maybe :Boolean]}
                :constraints [["$all" (< 8 an)]]
                :refines-to {:t/B {:expr (when-value >?t$B?
                                                     {:$type :t/B
                                                      :bn (+ 1 an)})}}}}]
    (s/with-fn-validation
      (is (= low-specs
             (update-vals (prop-extrinsic/lower-specs (ssa/spec-map-to-ssa high-specs))
                          ssa/spec-from-ssa))))))

(deftest test-extrinsic-basic
  (let [specs '{:t/A {:fields {:an :Integer}
                      :constraints [["an" (< 8 an)]]
                      :refines-to {:t/B {:extrinsic? true
                                         :expr {:$type :t/B
                                                :bn (+ 1 an)}}}}
                :t/B {:fields {:bn :Integer}
                      :constraints [["bn" (if (= bn 10)
                                            (error "nope")
                                            (and (< 10 bn)
                                                 (< bn 20)))]]}
                :t/C {:fields {:cn :Integer}
                      :constraints [["no refinement error"
                                     (let [a (refine-to {:$type :t/A
                                                         :an cn}
                                                        :t/B)]
                                       true)]]}
                :t/D {:fields {:cn :Integer}
                      :constraints [["refines"
                                     (let [a (refine-to {:$type :t/A
                                                         :an cn}
                                                        :t/B)]
                                       (if-value a true false))]]}}
        ctx {:senv specs :env (com.viasat.halite/env {})}
        sctx (ssa/spec-map-to-ssa specs)]

    (s/with-fn-validation
      (testing "valid A's when NOT refined"
        (are [an]
             (= {:$type :t/A :an an}
                (eval/eval-expr* {:senv specs :env (com.viasat.halite/env {})}
                                 '{:$type :t/A :an an}))
          9 10 11   19 20  50 100))

      (testing "refine A to B fails for given reason"
        (are [an msg]
             (thrown-with-msg? Exception msg
                               (eval/eval-expr* ctx
                                                '(refine-to {:$type :t/A :an an}
                                                            :t/B)))
          9  #"nope"
          19 #"refinement-error.*invalid-instance.*t/B/bn"))

      (testing "valid refinments of A to B"
        (are [an bn]
             (= {:$type :t/B, :bn bn}
                (eval/eval-expr* {:senv specs :env (com.viasat.halite/env {})}
                                 '(refine-to {:$type :t/A :an an}
                                             :t/B)))
          10 11
          11 12
          18 19)))

    (is (= {:$type :t/A,
            :an {:$in [9 1000]},
            :$refines-to {:t/B {:$type [:Maybe :t/B]
                                :bn {:$in [11 19]}}}}
           (prop-twice sctx {:$type :t/A})))

    (is (= {:$type :t/C, :cn {:$in [10 18]}}
           (prop-twice sctx {:$type :t/C})))

    (is (= {:$type :t/D, :cn {:$in [10 18]}}
           (prop-twice sctx {:$type :t/D})))))

;; This test is so slow! Marked as :qa-jibe so that most devs don't have to sit
;; through it most of the time:
(deftest ^:qa-jibe test-many-extrinsic
  (let [specs '{:my/A {:fields {:x :Integer}
                       :refines-to {:my/B {:extrinsic? true
                                           :expr {:$type :my/B :x (div x 2)}}}}
                :my/B {:fields {:x :Integer}
                       :refines-to {:my/C {:extrinsic? true
                                           :expr {:$type :my/C :x (div x 2)}}
                                    :my/E {:extrinsic? true
                                           :expr {:$type :my/E :x (+ 100 (div x 2))}}}}
                :my/C {:fields {:x :Integer}
                       :refines-to {:my/D {:extrinsic? true
                                           :expr {:$type :my/D :x (div x 2)}}}}
                :my/D {:fields {:x :Integer}}
                :my/E {:fields {:x :Integer}}
                :my/Ref {:fields {:x :Integer}
                         :refines-to
                         {:my/D {:expr (refine-to {:$type :my/A, :x x} :my/D)}
                          :my/E {:expr (refine-to {:$type :my/A, :x x} :my/E)}}}}
        sctx (ssa/spec-map-to-ssa specs)]

    (rewriting/with-captured-traces
      (is (= {:$type :my/Ref,
              :x {:$in [10 50]},
              :$refines-to {:my/D {:x {:$in [1 6]}},
                            :my/E {:x {:$in [102 112]}}}}
             (prop-extrinsic/propagate sctx {:$type :my/Ref
                                             :x {:$in [10 50]}}))))))

;; (time (run-tests))

;; Should be tested:
;; - guarded extrinsic refinement
;; - refinement chains with extrinsic at beginning, end, middle, and mixed (mixed with intrinsic refinements)
;; - mix of intrinsic and extrinsic refinements in same spec
;; - lowering refine-to expression with complex chains
;; - lowering refine-to expression with unknown type (if ...) for the inst
;; - lowering refines-to? expression across extrinsics
