;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-bom-pipeline
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-choco :as bom-choco]
            [com.viasat.halite.op-add-constraints :as op-add-constraints]
            [com.viasat.halite.op-add-types :as op-add-types]
            [com.viasat.halite.op-add-value-fields :as op-add-value-fields]
            [com.viasat.halite.op-canon :as op-canon]
            [com.viasat.halite.op-canon-refinements :as op-canon-refinements]
            [com.viasat.halite.op-canon-up :as op-canon-up]
            [com.viasat.halite.op-conjoin-spec-bom :as op-conjoin-spec-bom]
            [com.viasat.halite.op-contradictions :as op-contradictions]
            [com.viasat.halite.op-ensure-fields :as op-ensure-fields]
            [com.viasat.halite.op-find-concrete :as op-find-concrete]
            [com.viasat.halite.op-flower :as op-flower]
            [com.viasat.halite.op-inflate :as op-inflate]
            [com.viasat.halite.op-mandatory :as op-mandatory]
            [com.viasat.halite.op-push-down-to-concrete :as op-push-down-to-concrete]
            [com.viasat.halite.op-remove-value-fields :as op-remove-value-fields]
            [com.viasat.halite.op-strip :as op-strip]
            [com.viasat.halite.op-syntax-check :as op-syntax-check]
            [com.viasat.halite.op-type-check :as op-type-check]
            [schema.test]
            [zprint.core :as zprint])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(defn pipeline [spec-env bom]
  (->> bom
       (op-syntax-check/syntax-check-op spec-env)
       (op-type-check/type-check-op spec-env)
       op-canon/canon-op
       (op-mandatory/mandatory-op spec-env)

       (op-find-concrete/find-concrete-op spec-env)
       op-push-down-to-concrete/push-down-to-concrete-op
       (op-canon-refinements/canon-refinements-op spec-env)

       op-contradictions/bubble-up-contradictions))

(defn pipeline-2 [spec-env bom]
  (->> bom
       (op-syntax-check/syntax-check-op spec-env)
       (op-type-check/type-check-op spec-env)
       op-canon/canon-op
       (op-mandatory/mandatory-op spec-env)

       (op-find-concrete/find-concrete-op spec-env)
       op-push-down-to-concrete/push-down-to-concrete-op
       (op-canon-refinements/canon-refinements-op spec-env)

       (op-conjoin-spec-bom/conjoin-spec-bom-op spec-env)
       (op-find-concrete/find-concrete-op spec-env)
       op-push-down-to-concrete/push-down-to-concrete-op
       (op-canon-refinements/canon-refinements-op spec-env)
       op-contradictions/bubble-up-contradictions
       (op-add-types/add-types-op spec-env)))

(deftest test-pipeline
  (is (= {:$instance-of :ws/A$v1
          :i 1
          :x {:$refines-to :ws/X$v1
              :$concrete-choices {:ws/Y$v1 {:$instance-of :ws/Y$v1
                                            :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1
                                                                     :x2 2}}}
                                  :ws/ZZ$v1 {:$instance-of :ws/ZZ$v1
                                             :$refinements {:ws/Z$v1 {:$instance-of :ws/Z$v1
                                                                      :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1
                                                                                               :x2 2}}}}}}}}
         (pipeline {:ws/A$v1 {:fields {:i :Integer
                                       :x [:Maybe [:Instance :* #{:ws/X$v1}]]}}
                    :ws/X$v1 {:fields {:x2 :Integer}}
                    :ws/Y$v1 {:refines-to {:ws/X$v1 {:expr nil}}}
                    :ws/Z$v1 {:abstract? true
                              :refines-to {:ws/X$v1 {:expr nil}}}
                    :ws/ZZ$v1 {:refines-to {:ws/Z$v1 {:expr nil}}}}
                   {:$instance-of :ws/A$v1
                    :i 1
                    :x {:$refines-to :ws/X$v1
                        :x2 2}})))

  (is (= {:$instance-of :ws/A$v1
          :i 1
          :x {:$refines-to :ws/X$v1
              :$value? true
              :$concrete-choices
              {:ws/Y$v1 {:$instance-of :ws/Y$v1
                         :$refinements {:ws/X$v1
                                        {:$instance-of :ws/X$v1
                                         :x2 2
                                         :$value? true}}}
               :ws/ZZ$v1 {:$instance-of :ws/ZZ$v1
                          :$refinements {:ws/Z$v1 {:$instance-of :ws/Z$v1
                                                   :$value? true
                                                   :$refinements {:ws/X$v1
                                                                  {:$instance-of :ws/X$v1
                                                                   :x2 2
                                                                   :$value? true}}}}}
               :ws/ZZZ$v1 {:$instance-of :ws/ZZZ$v1
                           :$refinements {:ws/ZZ$v1 {:$instance-of :ws/ZZ$v1
                                                     :$value? true
                                                     :$refinements
                                                     {:ws/Z$v1 {:$instance-of :ws/Z$v1
                                                                :$value? true
                                                                :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1
                                                                                         :x2 2
                                                                                         :$value? true}}}}}}}}}}
         (pipeline {:ws/A$v1 {:fields {:i :Integer
                                       :x [:Instance :* #{:ws/X$v1}]}}
                    :ws/X$v1 {:fields {:x2 :Integer}}
                    :ws/Y$v1 {:refines-to {:ws/X$v1 {:expr nil}}}
                    :ws/Z$v1 {:abstract? true
                              :refines-to {:ws/X$v1 {:expr nil}}}
                    :ws/ZZ$v1 {:refines-to {:ws/Z$v1 {:expr nil}}}
                    :ws/ZZZ$v1 {:refines-to {:ws/ZZ$v1 {:expr nil}}}}
                   {:$instance-of :ws/A$v1
                    :i 1
                    :x {:$refines-to :ws/X$v1
                        :x2 2}})))

  (is (= {:$instance-of :ws/A$v1
          :i 1
          :x {:$value? false}}
         (pipeline {:ws/A$v1 {:fields {:i :Integer
                                       :x [:Maybe [:Instance :* #{:ws/X$v1}]]}}
                    :ws/X$v1 {:fields {:x2 :Integer}}
                    :ws/Y$v1 {:refines-to {:ws/X$v1 {:expr nil}}}
                    :ws/Z$v1 {:abstract? true
                              :refines-to {:ws/X$v1 {:expr nil}}}
                    :ws/ZZ$v1 {:refines-to {:ws/Z$v1 {:expr nil}}}}
                   {:$instance-of :ws/A$v1
                    :i 1
                    :x {:$refines-to :ws/X$v1
                        :$value? false}})))

  (is (= {:$instance-of :ws/A$v1
          :i {:$primitive-type :Integer
              :$value? true}
          :x {:$refines-to :ws/X$v1
              :$concrete-choices {:ws/Y$v1 {:$instance-of :ws/Y$v1
                                            :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1}}}
                                  :ws/ZZ$v1 {:$instance-of :ws/ZZ$v1
                                             :$refinements {:ws/Z$v1 {:$instance-of :ws/Z$v1
                                                                      :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1}}}}}}}}
         (pipeline-2 {:ws/A$v1 {:fields {:i :Integer
                                         :x [:Maybe [:Instance :* #{:ws/X$v1}]]}}
                      :ws/X$v1 {:fields {:x2 :Integer}}
                      :ws/Y$v1 {:refines-to {:ws/X$v1 {:expr nil}}}
                      :ws/Z$v1 {:abstract? true
                                :refines-to {:ws/X$v1 {:expr nil}}}
                      :ws/ZZ$v1 {:refines-to {:ws/Z$v1 {:expr nil}}}}
                     {:$instance-of :ws/A$v1})))

  (is (= {:$instance-of :ws/A$v1
          :i {:$primitive-type :Integer
              :$value? true}
          :x {:$instance-of :ws/Y$v1
              :x3 {:$primitive-type :Integer
                   :$value? true
                   :$ranges #{[1 10000]}}
              :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1}}}}
         (pipeline-2 {:ws/A$v1 {:fields {:i :Integer
                                         :x [:Maybe [:Instance :* #{:ws/X$v1}]]}}
                      :ws/X$v1 {:fields {:x2 :Integer}}
                      :ws/Y$v1 {:fields {:x3 :Integer}
                                :constraints [["x3" '(and (> x3 0) (< x3 10000))]]
                                :refines-to {:ws/X$v1 {:expr nil}}}
                      :ws/Z$v1 {:abstract? true
                                :refines-to {:ws/X$v1 {:expr nil}}}
                      :ws/ZZ$v1 {:refines-to {:ws/Z$v1 {:expr nil}}}}
                     {:$instance-of :ws/A$v1
                      :x {:$instance-of :ws/Y$v1}}))))

(def test-atom (atom []))

(defmacro check-propagate [spec-env bom & more]
  `(let [expected-result# ~(second (first more))
         spec-env0# (quote ~spec-env)
         spec-env# (eval spec-env0#)
         bom0# ~bom
         bom# (->> bom0#
                   (op-syntax-check/syntax-check-op spec-env#)
                   (op-type-check/type-check-op spec-env#)
                   op-canon/canon-op
                   (op-mandatory/mandatory-op spec-env#)

                   (op-find-concrete/find-concrete-op spec-env#)
                   op-push-down-to-concrete/push-down-to-concrete-op
                   (op-canon-refinements/canon-refinements-op spec-env#)

                   (op-conjoin-spec-bom/conjoin-spec-bom-op spec-env#)
                   (op-find-concrete/find-concrete-op spec-env#)
                   op-push-down-to-concrete/push-down-to-concrete-op
                   (op-canon-refinements/canon-refinements-op spec-env#)
                   op-contradictions/bubble-up-contradictions
                   (op-add-types/add-types-op spec-env#)
                   (op-ensure-fields/ensure-fields-op spec-env#)
                   (op-add-value-fields/add-value-fields-op spec-env#)
                   (op-add-constraints/add-constraints-op spec-env#))
         choco-data# (->> bom#
                          (op-flower/flower-op spec-env#)
                          bom-choco/bom-to-choco
                          (bom-choco/paths-to-syms bom#))
         propagate-result# (->> choco-data#
                                (bom-choco/choco-propagate bom#))
         result# (->> propagate-result#
                      (bom-choco/propagate-results-to-bounds bom#)
                      (op-inflate/inflate-op (op-remove-value-fields/remove-value-fields-op spec-env# bom#))
                      (op-remove-value-fields/remove-value-fields-op spec-env#)
                      op-canon-up/canon-up-op
                      op-strip/strip-op)]
     (is (= (quote ~(first (first more))) choco-data#))
     (is (= expected-result# result#))
     (swap! test-atom conj (list '~'check-propagate spec-env0# bom0# [choco-data# result#]))
     [choco-data# result#]))

(defn stanza [s]
  (swap! test-atom conj (list 'stanza s)))

(deftest test-propagate
  (do
    (stanza "boolean fields")

    (check-propagate {:ws/A$v1 {:fields {:x :Boolean,
                                         :y :Boolean},
                                :constraints [["c1" '(if x true y)]]}}
                     {:$instance-of :ws/A$v1}
                     [{:choco-spec {:vars {$_0 :Bool,
                                           $_1 :Bool,
                                           $_2 :Bool,
                                           $_3 :Bool},
                                    :constraints #{(if $_0 true $_2)}},
                       :choco-bounds {$_0 #{true false},
                                      $_1 true,
                                      $_2 #{true false},
                                      $_3 true},
                       :sym-to-path [$_0 [:x] $_1 [:x :$value?] $_2 [:y] $_3
                                     [:y :$value?]]} {:$instance-of :ws/A$v1}])

    (check-propagate {:ws/A$v1 {:fields {:x :Boolean,
                                         :y :Boolean},
                                :constraints [["c1" '(if x true y)]]}}
                     {:$instance-of :ws/A$v1,
                      :x false}
                     [{:choco-spec {:vars {$_0 :Bool,
                                           $_1 :Bool,
                                           $_2 :Bool},
                                    :constraints #{(if $_0 true $_1)}},
                       :choco-bounds {$_0 false,
                                      $_1 #{true false},
                                      $_2 true},
                       :sym-to-path [$_0 [:x] $_1 [:y] $_2 [:y :$value?]]}
                      {:$instance-of :ws/A$v1,
                       :x false,
                       :y true}])

    (check-propagate {:ws/A$v1 {:fields {:x :Boolean,
                                         :y :Boolean},
                                :constraints [["c1" '(if x true y)]]}}
                     {:$instance-of :ws/A$v1,
                      :y false}
                     [{:choco-spec {:vars {$_0 :Bool,
                                           $_1 :Bool,
                                           $_2 :Bool},
                                    :constraints #{(if $_1 true $_0)}},
                       :choco-bounds {$_0 false,
                                      $_1 #{true false},
                                      $_2 true},
                       :sym-to-path [$_0 [:y] $_1 [:x] $_2 [:x :$value?]]}
                      {:$instance-of :ws/A$v1,
                       :y false,
                       :x true}])

    (check-propagate {:ws/A$v1 {:fields {:x :Boolean,
                                         :y :Boolean},
                                :constraints [["c1" '(if x true y)]]}}
                     {:$instance-of :ws/A$v1,
                      :x true}
                     [{:choco-spec {:vars {$_0 :Bool,
                                           $_1 :Bool,
                                           $_2 :Bool},
                                    :constraints #{(if $_0 true $_1)}},
                       :choco-bounds {$_0 true,
                                      $_1 #{true false},
                                      $_2 true},
                       :sym-to-path [$_0 [:x] $_1 [:y] $_2 [:y :$value?]]}
                      {:$instance-of :ws/A$v1,
                       :x true}])

    (check-propagate {:ws/A$v1 {:fields {:x :Boolean,
                                         :y :Boolean},
                                :constraints [["c1" '(or x y)]]}}
                     {:$instance-of :ws/A$v1}
                     [{:choco-spec {:vars {$_0 :Bool,
                                           $_1 :Bool,
                                           $_2 :Bool,
                                           $_3 :Bool},
                                    :constraints #{(or $_0 $_2)}},
                       :choco-bounds {$_0 #{true false},
                                      $_1 true,
                                      $_2 #{true false},
                                      $_3 true},
                       :sym-to-path [$_0 [:x] $_1 [:x :$value?] $_2 [:y] $_3
                                     [:y :$value?]]} {:$instance-of :ws/A$v1}])

    (check-propagate {:ws/A$v1 {:fields {:x :Boolean,
                                         :y :Boolean},
                                :constraints [["c1" '(or x y)]]}}
                     {:$instance-of :ws/A$v1,
                      :x false}
                     [{:choco-spec {:vars {$_0 :Bool,
                                           $_1 :Bool,
                                           $_2 :Bool},
                                    :constraints #{(or $_0 $_1)}},
                       :choco-bounds {$_0 false,
                                      $_1 #{true false},
                                      $_2 true},
                       :sym-to-path [$_0 [:x] $_1 [:y] $_2 [:y :$value?]]}
                      {:$instance-of :ws/A$v1,
                       :x false,
                       :y true}])

    (stanza "optional boolean fields")

    (check-propagate {:ws/A$v1 {:fields {:x [:Maybe :Boolean],
                                         :y :Boolean}}}
                     {:$instance-of :ws/A$v1}
                     [{:choco-spec {:vars {$_0 :Bool,
                                           $_1 :Bool,
                                           $_2 :Bool,
                                           $_3 :Bool},
                                    :constraints #{}},
                       :choco-bounds {$_0 #{true false},
                                      $_1 true,
                                      $_2 #{true false},
                                      $_3 #{true false}},
                       :sym-to-path [$_0 [:y] $_1 [:y :$value?] $_2 [:x] $_3
                                     [:x :$value?]]} {:$instance-of :ws/A$v1}])

    (check-propagate {:ws/A$v1 {:fields {:x [:Maybe :Boolean],
                                         :y :Boolean},
                                :constraints [["c1" '(if-value x y true)]]}}
                     {:$instance-of :ws/A$v1}
                     [{:choco-spec {:vars {$_0 :Bool,
                                           $_1 :Bool,
                                           $_2 :Bool,
                                           $_3 :Bool},
                                    :constraints #{(if $_3 $_0 true)}},
                       :choco-bounds {$_0 #{true false},
                                      $_1 true,
                                      $_2 #{true false},
                                      $_3 #{true false}},
                       :sym-to-path [$_0 [:y] $_1 [:y :$value?] $_2 [:x] $_3
                                     [:x :$value?]]} {:$instance-of :ws/A$v1}])

    (check-propagate {:ws/A$v1 {:fields {:x [:Maybe :Boolean],
                                         :y :Boolean},
                                :constraints [["c1" '(if-value x y true)]]}}
                     {:$instance-of :ws/A$v1,
                      :x true}
                     [{:choco-spec {:vars {$_0 :Bool,
                                           $_1 :Bool,
                                           $_2 :Bool},
                                    :constraints #{(if true $_1 true)}},
                       :choco-bounds {$_0 true,
                                      $_1 #{true false},
                                      $_2 true},
                       :sym-to-path [$_0 [:x] $_1 [:y] $_2 [:y :$value?]]}
                      {:$instance-of :ws/A$v1,
                       :x true,
                       :y true}])

    (stanza "integer fields")

    (check-propagate
     {:ws/A$v1 {:fields {:x :Integer,
                         :y :Integer},
                :constraints [["c1" '(> x y)]]}}
     {:$instance-of :ws/A$v1}
     [{:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool,
                           $_2 :Int,
                           $_3 :Bool},
                    :constraints #{(> $_0 $_2)}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 true,
                      $_2 [-1000 1000],
                      $_3 true},
       :sym-to-path [$_0 [:x] $_1 [:x :$value?] $_2 [:y] $_3 [:y :$value?]]}
      {:$instance-of :ws/A$v1,
       :x {:$ranges #{[-999 1000]}},
       :y {:$ranges #{[-1000 999]}}}])

    (stanza "constraint across two integer fields")

    (check-propagate
     {:ws/A$v1 {:fields {:x :Integer,
                         :y :Integer},
                :constraints [["c1" '(= 15 (+ x y))]
                              ["c2" '(or (= x 1) (= x 2) (= x 3))]
                              ["c3" '(or (= y 10) (= y 12) (= y 14))]]}}
     {:$instance-of :ws/A$v1}
     [{:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool,
                           $_2 :Int,
                           $_3 :Bool},
                    :constraints #{(or (= $_2 10) (= $_2 12) (= $_2 14))
                                   (or (= $_0 1) (= $_0 2) (= $_0 3))
                                   (= 15 (+ $_0 $_2))}},
       :choco-bounds {$_0 #{1 3 2},
                      $_1 true,
                      $_2 #{12 14 10},
                      $_3 true},
       :sym-to-path [$_0 [:x] $_1 [:x :$value?] $_2 [:y] $_3 [:y :$value?]]}
      {:$instance-of :ws/A$v1,
       :x {:$enum #{1 3}},
       :y {:$enum #{12 14}}}])

    (stanza "fixed decimal")

    (check-propagate {:ws/A$v1 {:fields {:a [:Decimal 2]}}}
                     {:$instance-of :ws/A$v1}
                     [{:choco-spec {:vars {$_0 :Int,
                                           $_1 :Bool},
                                    :constraints #{}},
                       :choco-bounds {$_0 [-1000 1000],
                                      $_1 true},
                       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
                      {:$instance-of :ws/A$v1,
                       :a {:$ranges #{[#d "-10.00" #d "10.00"]}}}])

    (check-propagate
     {:ws/A$v1 {:fields {:a [:Decimal 2],
                         :b [:Decimal 2]},
                :constraints [["c1" '(= #d "1.05" (+ a b))]]}}
     {:$instance-of :ws/A$v1}
     [{:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool,
                           $_2 :Int,
                           $_3 :Bool},
                    :constraints #{(= 105 (+ $_0 $_2))}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 true,
                      $_2 [-1000 1000],
                      $_3 true},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?] $_2 [:b] $_3 [:b :$value?]]}
      {:$instance-of :ws/A$v1,
       :a {:$ranges #{[#d "-8.95" #d "10.00"]}},
       :b {:$ranges #{[#d "-8.95" #d "10.00"]}}}])

    (check-propagate
     {:ws/A$v1 {:fields {:a [:Decimal 2],
                         :b [:Decimal 2]},
                :constraints [["c1" '(= #d "1.05" (+ a b))]]}}
     {:$instance-of :ws/A$v1,
      :a {:$enum #{#d "0.05" #d "0.06"}}}
     [{:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool,
                           $_2 :Int,
                           $_3 :Bool},
                    :constraints #{(= 105 (+ $_0 $_2))}},
       :choco-bounds {$_0 #{6 5},
                      $_1 true,
                      $_2 [-1000 1000],
                      $_3 true},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?] $_2 [:b] $_3 [:b :$value?]]}
      {:$instance-of :ws/A$v1,
       :a {:$enum #{#d "0.05" #d "0.06"}},
       :b {:$ranges #{[#d "0.99" #d "1.00"]}}}])

    (check-propagate
     {:ws/A$v1 {:fields {:a [:Decimal 2]},
                :constraints [["c1" '(= (rescale #d "5.01" 3) (rescale a 3))]]}}
     {:$instance-of :ws/A$v1}
     [{:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(= (* 501 10) (* $_0 10))}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 true},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
      {:$instance-of :ws/A$v1,
       :a #d "5.01"}])

    (check-propagate
     {:ws/A$v1 {:fields {:a [:Decimal 2]},
                :constraints [["c1" '(= (rescale #d "5.01" 1) (rescale a 1))]]}}
     {:$instance-of :ws/A$v1}
     [{:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(= (div 501 10) (div $_0 10))}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 true},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
      {:$instance-of :ws/A$v1,
       :a {:$ranges #{[#d "-5.09" #d "5.09"]}}}])

    (check-propagate {:ws/A$v1 {:fields {:a [:Decimal 2]},
                                :constraints [["c1" '(= 5 (rescale a 0))]]}}
                     {:$instance-of :ws/A$v1}
                     [{:choco-spec {:vars {$_0 :Int,
                                           $_1 :Bool},
                                    :constraints #{(= 5 (div $_0 100))}},
                       :choco-bounds {$_0 [-1000 1000],
                                      $_1 true},
                       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
                      {:$instance-of :ws/A$v1,
                       :a {:$ranges #{[#d "-5.99" #d "5.99"]}}}])

    (check-propagate {:ws/A$v1 {:fields {:a [:Decimal 2]},
                                :constraints [["c1" '(= 503 (rescale a 2))]]}}
                     {:$instance-of :ws/A$v1}
                     [{:choco-spec {:vars {$_0 :Int,
                                           $_1 :Bool},
                                    :constraints #{(= 503 $_0)}},
                       :choco-bounds {$_0 [-1000 1000],
                                      $_1 true},
                       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
                      {:$instance-of :ws/A$v1,
                       :a #d "5.03"}])

    (check-propagate {:ws/A$v1 {:fields {:a [:Decimal 2]},
                                :constraints [["c1" '(= #d "3.0" (rescale a 1))]]}}
                     {:$instance-of :ws/A$v1}
                     [{:choco-spec {:vars {$_0 :Int,
                                           $_1 :Bool},
                                    :constraints #{(= 30 (div $_0 10))}},
                       :choco-bounds {$_0 [-1000 1000],
                                      $_1 true},
                       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
                      {:$instance-of :ws/A$v1,
                       :a {:$ranges #{[#d "-3.09" #d "3.09"]}}}])

    (check-propagate
     {:ws/A$v1 {:fields {:a [:Decimal 2]},
                :constraints [["c1" '(= #d "3.0" (rescale (+ a #d "0.01") 1))]]}}
     {:$instance-of :ws/A$v1}
     [{:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(= 30 (div (+ $_0 1) 10))}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 true},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
      {:$instance-of :ws/A$v1,
       :a {:$ranges #{[#d "-3.10" #d "3.08"]}}}])

    (check-propagate {:ws/A$v1 {:fields {:a [:Decimal 2]}}}
                     {:$instance-of :ws/A$v1,
                      :a #d "3.14"}
                     [{:choco-spec {:vars {$_0 :Int},
                                    :constraints #{}},
                       :choco-bounds {$_0 314},
                       :sym-to-path [$_0 [:a]]}
                      {:$instance-of :ws/A$v1,
                       :a #d "3.14"}])

    (check-propagate
     {:ws/A$v1 {:fields {:a [:Maybe [:Decimal 2]]},
                :constraints
                [["c1"
                  '(= #d "3.0"
                      (if-value a (rescale (+ a #d "0.01") 1) #d "2.0"))]]}}
     {:$instance-of :ws/A$v1}
     [{:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(= 30 (if $_1 (div (+ $_0 1) 10) 20))}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 #{true false}},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
      {:$instance-of :ws/A$v1,
       :a {:$ranges #{[#d "-3.10" #d "3.08"]},
           :$value? true}}])

    (check-propagate
     {:ws/A$v1 {:fields {:a [:Maybe [:Decimal 2]]},
                :constraints
                [["c1"
                  '(= #d "3.00"
                      (if-value a (rescale (+ a #d "0.01") 1) #d "3.00"))]]}}
     {:$instance-of :ws/A$v1}
     [{:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(= 300 (if $_1 (div (+ $_0 1) 10) 300))}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 #{true false}},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
      {:$instance-of :ws/A$v1,
       :a {:$value? false}}])

    (stanza "TODO: the output is not constrained as one would hope")

    (check-propagate
     {:ws/A$v1 {:fields {:a [:Maybe [:Decimal 2]]},
                :constraints
                [["c1"
                  '(= #d "3.0"
                      (if-value a (rescale (+ a #d "0.01") 1) #d "3.0"))]]}}
     {:$instance-of :ws/A$v1}
     [{:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(= 30 (if $_1 (div (+ $_0 1) 10) 30))}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 #{true false}},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
      {:$instance-of :ws/A$v1,
       :a {:$ranges #{[#d "-10.00" #d "10.00"]}}}])

    (check-propagate
     {:ws/A$v1 {:fields {:a [:Maybe [:Decimal 2]]},
                :constraints
                [["c1"
                  '(= #d "3.0"
                      (if-value a (rescale (+ a #d "0.01") 1) #d "3.0"))]]}}
     {:$instance-of :ws/A$v1,
      :a {:$value? true}}
     [{:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(= 30 (if $_1 (div (+ $_0 1) 10) 30))}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 true},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
      {:$instance-of :ws/A$v1,
       :a {:$ranges #{[#d "-3.10" #d "3.08"]},
           :$value? true}}])

    (check-propagate
     {:ws/A$v1 {:fields {:a [:Maybe [:Decimal 2]]},
                :constraints
                [["c1"
                  '(= #d "3.0"
                      (if-value a (rescale (+ a #d "0.01") 1) #d "3.0"))]]}}
     {:$instance-of :ws/A$v1,
      :a {:$value? false}}
     [{:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(= 30 (if $_1 (div (+ $_0 1) 10) 30))}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 false},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
      {:$instance-of :ws/A$v1,
       :a {:$value? false}}])

    (check-propagate
     {:ws/A$v1 {:fields {:a [:Maybe [:Decimal 2]]},
                :constraints
                [["c1"
                  '(= #d "3.0"
                      (if-value a (rescale (+ a #d "0.01") 1) #d "3.0"))]]}}
     {:$instance-of :ws/A$v1,
      :a #d "4.00"}
     [{:choco-spec {:vars {$_0 :Int},
                    :constraints #{(= 30 (if true (div (+ $_0 1) 10) 30))}},
       :choco-bounds {$_0 400},
       :sym-to-path [$_0 [:a]]} {:$contradiction? true}])

    (stanza "composition")

    (check-propagate
     {:ws/B$v1 {:fields {:a [:Instance :ws/A$v1],
                         :y :Integer},
                :constraints [["c1" '(= 15 (+ (get a :x) y))]
                              ["c3" '(or (= y 10) (= y 12) (= y 14))]]},
      :ws/A$v1 {:fields {:x :Integer},
                :constraints [["c2" '(or (= x 1) (= x 2) (= x 3))]]}}
     {:$instance-of :ws/B$v1}
     [{:choco-spec {:vars {$_0 :Bool,
                           $_1 :Int,
                           $_2 :Bool,
                           $_3 :Int,
                           $_4 :Bool},
                    :constraints #{(or (= $_3 10) (= $_3 12) (= $_3 14))
                                   (or (= $_1 1) (= $_1 2) (= $_1 3))
                                   (= 15 (+ $_1 $_3))}},
       :choco-bounds {$_0 true,
                      $_1 #{1 3 2},
                      $_2 true,
                      $_3 #{12 14 10},
                      $_4 true},
       :sym-to-path [$_0 [:a :$value?] $_1 [:a :x] $_2 [:a :x :$value?] $_3 [:y]
                     $_4 [:y :$value?]]}
      {:$instance-of :ws/B$v1,
       :a {:$instance-of :ws/A$v1,
           :x {:$enum #{1 3}}},
       :y {:$enum #{12 14}}}])

    (stanza "optional integer field")

    (check-propagate {:ws/A$v1 {:fields {:x [:Maybe :Integer]}}}
                     {:$instance-of :ws/A$v1}
                     [{:choco-spec {:vars {$_0 :Int,
                                           $_1 :Bool},
                                    :constraints #{}},
                       :choco-bounds {$_0 [-1000 1000],
                                      $_1 #{true false}},
                       :sym-to-path [$_0 [:x] $_1 [:x :$value?]]}
                      {:$instance-of :ws/A$v1,
                       :x {:$ranges #{[-1000 1000]}}}])

    (check-propagate
     {:ws/A$v1 {:fields {:x [:Maybe :Integer]},
                :constraints [["c1" '(if-value x (and (> x 20) (< x 30)) true)]]}}
     {:$instance-of :ws/A$v1}
     [{:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(if $_1 (and (> $_0 20) (< $_0 30)) true)}},
       :choco-bounds {$_0 #{27 24 21 22 29 28 25 23 26},
                      $_1 #{true false}},
       :sym-to-path [$_0 [:x] $_1 [:x :$value?]]}
      {:$instance-of :ws/A$v1,
       :x {:$enum #{27 24 21 22 29 28 25 23 26}}}])

    (check-propagate {:ws/A$v1 {:fields {:x [:Maybe :Integer]},
                                :constraints [["c1" '(if-value x false true)]]}}
                     {:$instance-of :ws/A$v1}
                     [{:choco-spec {:vars {$_0 :Int,
                                           $_1 :Bool},
                                    :constraints #{(if $_1 false true)}},
                       :choco-bounds {$_0 [-1000 1000],
                                      $_1 #{true false}},
                       :sym-to-path [$_0 [:x] $_1 [:x :$value?]]}
                      {:$instance-of :ws/A$v1,
                       :x {:$value? false}}])

    (check-propagate {:ws/A$v1 {:fields {:x [:Maybe :Integer]},
                                :constraints [["c1" '(if-value x true false)]]}}
                     {:$instance-of :ws/A$v1}
                     [{:choco-spec {:vars {$_0 :Int,
                                           $_1 :Bool},
                                    :constraints #{(if $_1 true false)}},
                       :choco-bounds {$_0 [-1000 1000],
                                      $_1 #{true false}},
                       :sym-to-path [$_0 [:x] $_1 [:x :$value?]]}
                      {:$instance-of :ws/A$v1,
                       :x {:$ranges #{[-1000 1000]},
                           :$value? true}}])

    (check-propagate
     {:ws/A$v1 {:fields {:x [:Maybe :Integer],
                         :y [:Maybe :Integer]},
                :constraints [["c1" '(if-value x (if-value y true false) false)]]}}
     {:$instance-of :ws/A$v1}
     [{:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool,
                           $_2 :Int,
                           $_3 :Bool},
                    :constraints #{(if $_1 (if $_3 true false) false)}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 #{true false},
                      $_2 [-1000 1000],
                      $_3 #{true false}},
       :sym-to-path [$_0 [:x] $_1 [:x :$value?] $_2 [:y] $_3 [:y :$value?]]}
      {:$instance-of :ws/A$v1,
       :x {:$ranges #{[-1000 1000]},
           :$value? true},
       :y {:$ranges #{[-1000 1000]},
           :$value? true}}])

    (check-propagate
     {:ws/A$v1 {:fields {:x [:Maybe :Integer],
                         :y [:Maybe :Integer]},
                :constraints [["c1" '(if-value x false (if-value y true false))]]}}
     {:$instance-of :ws/A$v1}
     [{:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool,
                           $_2 :Int,
                           $_3 :Bool},
                    :constraints #{(if $_1 false (if $_3 true false))}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 #{true false},
                      $_2 [-1000 1000],
                      $_3 #{true false}},
       :sym-to-path [$_0 [:x] $_1 [:x :$value?] $_2 [:y] $_3 [:y :$value?]]}
      {:$instance-of :ws/A$v1,
       :x {:$value? false},
       :y {:$ranges #{[-1000 1000]},
           :$value? true}}])

    (check-propagate
     {:ws/A$v1 {:fields {:x [:Maybe :Integer],
                         :y [:Maybe :Integer]},
                :constraints [["c1" '(if-value x false (if-value y false true))]]}}
     {:$instance-of :ws/A$v1}
     [{:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool,
                           $_2 :Int,
                           $_3 :Bool},
                    :constraints #{(if $_1 false (if $_3 false true))}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 #{true false},
                      $_2 [-1000 1000],
                      $_3 #{true false}},
       :sym-to-path [$_0 [:x] $_1 [:x :$value?] $_2 [:y] $_3 [:y :$value?]]}
      {:$instance-of :ws/A$v1,
       :x {:$value? false},
       :y {:$value? false}}])

    (stanza "optional-field with composition")

    (check-propagate
     {:ws/B$v1 {:fields {:a [:Instance :ws/A$v1],
                         :y [:Maybe :Integer]},
                :constraints
                [["c1" '(if-value y (= 15 (+ (get a :x) y)) true)]
                 ["c3" '(if-value y (or (= y 10) (= y 12) (= y 14)) true)]]},
      :ws/A$v1 {:fields {:x :Integer},
                :constraints [["c2" '(or (= x 1) (= x 2) (= x 3))]]}}
     {:$instance-of :ws/B$v1}
     [{:choco-spec {:vars {$_0 :Bool,
                           $_1 :Int,
                           $_2 :Bool,
                           $_3 :Int,
                           $_4 :Bool},
                    :constraints #{(if $_4
                                     (or (= $_3 10) (= $_3 12) (= $_3 14))
                                     true) (or (= $_1 1) (= $_1 2) (= $_1 3))
                                   (if $_4 (= 15 (+ $_1 $_3)) true)}},
       :choco-bounds {$_0 true,
                      $_1 #{1 3 2},
                      $_2 true,
                      $_3 #{12 14 10},
                      $_4 #{true false}},
       :sym-to-path [$_0 [:a :$value?] $_1 [:a :x] $_2 [:a :x :$value?] $_3 [:y]
                     $_4 [:y :$value?]]}
      {:$instance-of :ws/B$v1,
       :a {:$instance-of :ws/A$v1,
           :x {:$enum #{1 3 2}}},
       :y {:$enum #{12 14 10}}}])

    (check-propagate
     {:ws/B$v1 {:fields {:a [:Instance :ws/A$v1],
                         :y [:Maybe :Integer]},
                :constraints
                [["c1" '(if-value y (= 15 (+ (get a :x) y)) false)]
                 ["c3" '(if-value y (or (= y 10) (= y 12) (= y 14)) true)]]},
      :ws/A$v1 {:fields {:x :Integer},
                :constraints [["c2" '(or (= x 1) (= x 2) (= x 3))]]}}
     {:$instance-of :ws/B$v1}
     [{:choco-spec {:vars {$_0 :Bool,
                           $_1 :Int,
                           $_2 :Bool,
                           $_3 :Int,
                           $_4 :Bool},
                    :constraints #{(if $_4
                                     (or (= $_3 10) (= $_3 12) (= $_3 14))
                                     true) (or (= $_1 1) (= $_1 2) (= $_1 3))
                                   (if $_4 (= 15 (+ $_1 $_3)) false)}},
       :choco-bounds {$_0 true,
                      $_1 #{1 3 2},
                      $_2 true,
                      $_3 #{12 14 10},
                      $_4 #{true false}},
       :sym-to-path [$_0 [:a :$value?] $_1 [:a :x] $_2 [:a :x :$value?] $_3 [:y]
                     $_4 [:y :$value?]]}
      {:$instance-of :ws/B$v1,
       :a {:$instance-of :ws/A$v1,
           :x {:$enum #{1 3}}},
       :y {:$enum #{12 14},
           :$value? true}}])

    (check-propagate
     {:ws/B$v1 {:fields {:a [:Maybe [:Instance :ws/A$v1]]}},
      :ws/A$v1 {:fields {:x :Integer}}}
     {:$instance-of :ws/B$v1}
     [{:choco-spec {:vars {$_0 :Bool,
                           $_1 :Int,
                           $_2 :Bool},
                    :constraints #{}},
       :choco-bounds {$_0 #{true false},
                      $_1 [-1000 1000],
                      $_2 true},
       :sym-to-path [$_0 [:a :$value?] $_1 [:a :x] $_2 [:a :x :$value?]]}
      {:$instance-of :ws/B$v1,
       :a {:$instance-of :ws/A$v1,
           :x {:$ranges #{[-1000 1000]}}}}])

    (check-propagate {:ws/B$v1 {:fields {:a [:Maybe [:Instance :ws/A$v1]]},
                                :constraints [["c1" '(if-value a false true)]]},
                      :ws/A$v1 {:fields {}}}
                     {:$instance-of :ws/B$v1}
                     [{:choco-spec {:vars {$_0 :Bool},
                                    :constraints #{(if $_0 false true)}},
                       :choco-bounds {$_0 #{true false}},
                       :sym-to-path [$_0 [:a :$value?]]}
                      {:$instance-of :ws/B$v1,
                       :a {:$value? false}}])

    (check-propagate {:ws/B$v1 {:fields {:a [:Maybe [:Instance :ws/A$v1]]},
                                :constraints [["c1" '(if-value a true false)]]},
                      :ws/A$v1 {:fields {}}}
                     {:$instance-of :ws/B$v1}
                     [{:choco-spec {:vars {$_0 :Bool},
                                    :constraints #{(if $_0 true false)}},
                       :choco-bounds {$_0 #{true false}},
                       :sym-to-path [$_0 [:a :$value?]]}
                      {:$instance-of :ws/B$v1,
                       :a {:$instance-of :ws/A$v1,
                           :$value? true}}])

    (check-propagate
     {:ws/B$v1 {:fields {:a [:Maybe [:Instance :ws/A$v1]]},
                :constraints [["c1"
                               '(if-value a
                                          (let [q (get a :x)]
                                            (if-value q false true))
                                          false)]]},
      :ws/A$v1 {:fields {:x [:Maybe :Integer]}}}
     {:$instance-of :ws/B$v1}
     [{:choco-spec {:vars {$_0 :Bool,
                           $_1 :Int,
                           $_2 :Bool},
                    :constraints #{(if $_0 (if $_2 false true) false)}},
       :choco-bounds {$_0 #{true false},
                      $_1 [-1000 1000],
                      $_2 #{true false}},
       :sym-to-path [$_0 [:a :$value?] $_1 [:a :x] $_2 [:a :x :$value?]]}
      {:$instance-of :ws/B$v1,
       :a {:$instance-of :ws/A$v1,
           :x {:$value? false},
           :$value? true}}])

    (check-propagate
     {:ws/B$v1 {:fields {:a [:Maybe [:Instance :ws/A$v1]]},
                :constraints [["c1"
                               '(if-value a
                                          (let [q (get a :x)]
                                            (if-value q true false))
                                          false)]]},
      :ws/A$v1 {:fields {:x [:Maybe :Integer]}}}
     {:$instance-of :ws/B$v1}
     [{:choco-spec {:vars {$_0 :Bool,
                           $_1 :Int,
                           $_2 :Bool},
                    :constraints #{(if $_0 (if $_2 true false) false)}},
       :choco-bounds {$_0 #{true false},
                      $_1 [-1000 1000],
                      $_2 #{true false}},
       :sym-to-path [$_0 [:a :$value?] $_1 [:a :x] $_2 [:a :x :$value?]]}
      {:$instance-of :ws/B$v1,
       :a {:$instance-of :ws/A$v1,
           :x {:$ranges #{[-1000 1000]},
               :$value? true},
           :$value? true}}])

    (check-propagate
     {:ws/B$v1 {:fields {:a [:Maybe [:Instance :ws/A$v1]],
                         :b :Boolean},
                :constraints [["c1"
                               '(if b
                                  true
                                  (if-value a
                                            (let [q (get a :x)]
                                              (if-value q true false))
                                            false))]]},
      :ws/A$v1 {:fields {:x [:Maybe :Integer]}}}
     {:$instance-of :ws/B$v1,
      :b false}
     [{:choco-spec {:vars {$_0 :Bool,
                           $_1 :Bool,
                           $_2 :Int,
                           $_3 :Bool},
                    :constraints
                    #{(if $_0 true (if $_1 (if $_3 true false) false))}},
       :choco-bounds {$_0 false,
                      $_1 #{true false},
                      $_2 [-1000 1000],
                      $_3 #{true false}},
       :sym-to-path [$_0 [:b] $_1 [:a :$value?] $_2 [:a :x] $_3 [:a :x :$value?]]}
      {:$instance-of :ws/B$v1,
       :b false,
       :a {:$instance-of :ws/A$v1,
           :x {:$ranges #{[-1000 1000]},
               :$value? true},
           :$value? true}}])

    (check-propagate
     {:ws/B$v1 {:fields {:a [:Maybe [:Instance :ws/A$v1]],
                         :b :Boolean},
                :constraints [["c1"
                               '(if b
                                  true
                                  (if-value a
                                            (let [q (get a :x)]
                                              (if-value q (> q 20) false))
                                            false))]]},
      :ws/A$v1 {:fields {:x [:Maybe :Integer]}}}
     {:$instance-of :ws/B$v1,
      :b false}
     [{:choco-spec {:vars {$_0 :Bool,
                           $_1 :Bool,
                           $_2 :Int,
                           $_3 :Bool},
                    :constraints
                    #{(if $_0 true (if $_1 (if $_3 (> $_2 20) false) false))}},
       :choco-bounds {$_0 false,
                      $_1 #{true false},
                      $_2 [-1000 1000],
                      $_3 #{true false}},
       :sym-to-path [$_0 [:b] $_1 [:a :$value?] $_2 [:a :x] $_3 [:a :x :$value?]]}
      {:$instance-of :ws/B$v1,
       :b false,
       :a {:$instance-of :ws/A$v1,
           :x {:$ranges #{[21 1000]},
               :$value? true},
           :$value? true}}])

    (check-propagate
     {:ws/B$v1 {:fields {:a [:Maybe [:Instance :ws/A$v1]],
                         :b :Boolean},
                :constraints [["c1"
                               '(if b
                                  true
                                  (if-value a
                                            (let [q (get a :x)]
                                              (if-value q (> q 20) false))
                                            false))]]},
      :ws/A$v1 {:fields {:x [:Maybe :Integer]}}}
     {:$instance-of :ws/B$v1,
      :b false}
     [{:choco-spec {:vars {$_0 :Bool,
                           $_1 :Bool,
                           $_2 :Int,
                           $_3 :Bool},
                    :constraints
                    #{(if $_0 true (if $_1 (if $_3 (> $_2 20) false) false))}},
       :choco-bounds {$_0 false,
                      $_1 #{true false},
                      $_2 [-1000 1000],
                      $_3 #{true false}},
       :sym-to-path [$_0 [:b] $_1 [:a :$value?] $_2 [:a :x] $_3 [:a :x :$value?]]}
      {:$instance-of :ws/B$v1,
       :b false,
       :a {:$instance-of :ws/A$v1,
           :x {:$ranges #{[21 1000]},
               :$value? true},
           :$value? true}}])

    (check-propagate
     {:ws/B$v1 {:fields {:a [:Maybe [:Instance :ws/A$v1]],
                         :b :Boolean},
                :constraints [["c1"
                               '(if b
                                  true
                                  (if-value a
                                            (let [q (get a :x)]
                                              (if-value q (> q 20) false))
                                            false))]]},
      :ws/A$v1 {:fields {:x [:Maybe :Integer]}}}
     {:$instance-of :ws/B$v1,
      :a {:$instance-of :ws/A$v1,
          :x 30}}
     [{:choco-spec {:vars {$_0 :Bool,
                           $_1 :Int,
                           $_2 :Bool,
                           $_3 :Bool},
                    :constraints
                    #{(if $_2 true (if $_0 (if true (> $_1 20) false) false))}},
       :choco-bounds {$_0 #{true false},
                      $_1 30,
                      $_2 #{true false},
                      $_3 true},
       :sym-to-path [$_0 [:a :$value?] $_1 [:a :x] $_2 [:b] $_3 [:b :$value?]]}
      {:$instance-of :ws/B$v1,
       :a {:x 30,
           :$instance-of :ws/A$v1}}])

    (check-propagate
     {:ws/B$v1 {:fields {:a [:Maybe [:Instance :ws/A$v1]],
                         :b :Boolean},
                :constraints [["c1"
                               '(if b
                                  true
                                  (if-value a
                                            (let [q (get a :x)]
                                              (if-value q (> q 20) false))
                                            false))]]},
      :ws/A$v1 {:fields {:x [:Maybe :Integer]}}}
     {:$instance-of :ws/B$v1,
      :a {:$instance-of :ws/A$v1,
          :x 10}}
     [{:choco-spec {:vars {$_0 :Bool,
                           $_1 :Int,
                           $_2 :Bool,
                           $_3 :Bool},
                    :constraints
                    #{(if $_2 true (if $_0 (if true (> $_1 20) false) false))}},
       :choco-bounds {$_0 #{true false},
                      $_1 10,
                      $_2 #{true false},
                      $_3 true},
       :sym-to-path [$_0 [:a :$value?] $_1 [:a :x] $_2 [:b] $_3 [:b :$value?]]}
      {:$instance-of :ws/B$v1,
       :a {:x 10,
           :$instance-of :ws/A$v1},
       :b true}])

    (stanza
     "constraints from refinements\n          TODO\n          (check-propagate {:ws/B$v1 {:fields {:a :Integer}\n                                      :constraints [[\"c1\" '(> a 10)]]}\n                            :ws/A$v1 {:fields {:x [:Maybe :Integer]}\n                                      :refines-to {:ws/B$v1 {:expr nil}}}}\n                           {:$instance-of :ws/A$v1\n                            :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1}}}\n                           :moo)")

    (stanza "instance literals")

    (check-propagate {:ws/X$v1 {:fields {:x :Integer,
                                         :y :Integer},
                                :constraints [["c1" '(= 10 (+ x y))]]},
                      :ws/A$v1 {:fields {:a :Integer},
                                :constraints [["c2"
                                               '(let [q {:$type :ws/X$v1,
                                                         :x a,
                                                         :y 6}]
                                                  true)]]}}
                     {:$instance-of :ws/A$v1}
                     [{:choco-spec {:vars {$_0 :Int,
                                           $_1 :Bool},
                                    :constraints #{true (= 10 (+ $_0 6))}},
                       :choco-bounds {$_0 [-1000 1000],
                                      $_1 true},
                       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
                      {:$instance-of :ws/A$v1,
                       :a 4}])

    (check-propagate {:ws/X$v1 {:fields {:x :Integer,
                                         :y :Integer},
                                :constraints [["c1" '(= 10 (+ x y))]]},
                      :ws/A$v1 {:fields {:a :Integer},
                                :constraints [["c2"
                                               '(let [q {:$type :ws/X$v1,
                                                         :x a,
                                                         :y 7}]
                                                  true)]]}}
                     {:$instance-of :ws/A$v1}
                     [{:choco-spec {:vars {$_0 :Int,
                                           $_1 :Bool},
                                    :constraints #{true (= 10 (+ $_0 7))}},
                       :choco-bounds {$_0 [-1000 1000],
                                      $_1 true},
                       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
                      {:$instance-of :ws/A$v1,
                       :a 3}])

    (check-propagate {:ws/X$v1 {:fields {:x :Integer,
                                         :y :Integer},
                                :constraints [["c1" '(= 10 (+ x y))]]},
                      :ws/P$v1 {:fields {:q [:Instance :ws/X$v1]}},
                      :ws/A$v1 {:fields {:a :Integer},
                                :constraints [["c2"
                                               '(let [f {:$type :ws/P$v1,
                                                         :q {:$type :ws/X$v1,
                                                             :x a,
                                                             :y 7}}]
                                                  true)]]}}
                     {:$instance-of :ws/A$v1}
                     [{:choco-spec {:vars {$_0 :Int,
                                           $_1 :Bool},
                                    :constraints #{true (= 10 (+ $_0 7))}},
                       :choco-bounds {$_0 [-1000 1000],
                                      $_1 true},
                       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
                      {:$instance-of :ws/A$v1,
                       :a 3}])

    (check-propagate {:ws/X$v1 {:fields {:x :Integer,
                                         :y :Integer},
                                :constraints [["c1" '(= 10 (+ x y))]]},
                      :ws/P$v1 {:fields {:q [:Instance :ws/X$v1]}},
                      :ws/A$v1 {:fields {:a :Integer},
                                :constraints [["c2"
                                               '(let [f {:$type :ws/P$v1,
                                                         :q {:$type :ws/X$v1,
                                                             :x a,
                                                             :y 7}}]
                                                  true)]]}}
                     {:$instance-of :ws/A$v1,
                      :a 4}
                     [{:choco-spec {:vars {$_0 :Int},
                                    :constraints #{true (= 10 (+ $_0 7))}},
                       :choco-bounds {$_0 4},
                       :sym-to-path [$_0 [:a]]} {:$contradiction? true}])

    (check-propagate
     {:ws/X$v1 {:fields {:x :Integer,
                         :y :Integer},
                :constraints [["c1" '(= 10 (+ x y))]]},
      :ws/P$v1 {:fields {:q [:Instance :ws/X$v1]}},
      :ws/A$v1 {:fields {:a :Integer,
                         :b :Integer},
                :constraints [["c2"
                               '(let [f {:$type :ws/P$v1,
                                         :q {:$type :ws/X$v1,
                                             :x a,
                                             :y (let [z 2]
                                                  (+ a b z))}}]
                                  true)]]}}
     {:$instance-of :ws/A$v1,
      :a 3}
     [{:choco-spec {:vars {$_0 :Int,
                           $_1 :Int,
                           $_2 :Bool},
                    :constraints #{true (= 10 (+ $_0 (+ $_0 $_1 2)))}},
       :choco-bounds {$_0 3,
                      $_1 [-1000 1000],
                      $_2 true},
       :sym-to-path [$_0 [:a] $_1 [:b] $_2 [:b :$value?]]}
      {:$instance-of :ws/A$v1,
       :a 3,
       :b 2}])

    (stanza "oif-value-let")

    (check-propagate
     {:ws/X$v1 {:fields {:x [:Maybe :Integer]},
                :constraints [["c1" '(if-value-let [p x] (> p 10) true)]]}}
     {:$instance-of :ws/X$v1}
     [{:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(if $_1 (> $_0 10) true)}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 #{true false}},
       :sym-to-path [$_0 [:x] $_1 [:x :$value?]]}
      {:$instance-of :ws/X$v1,
       :x {:$ranges #{[-1000 1000]}}}])

    (check-propagate
     {:ws/X$v1 {:fields {:x [:Maybe :Integer]},
                :constraints [["c1" '(if-value-let [p x] (> p 10) true)]]}}
     {:$instance-of :ws/X$v1,
      :x 20}
     [{:choco-spec {:vars {$_0 :Int},
                    :constraints #{(if true (> $_0 10) true)}},
       :choco-bounds {$_0 20},
       :sym-to-path [$_0 [:x]]}
      {:$instance-of :ws/X$v1,
       :x 20}])

    (check-propagate
     {:ws/X$v1 {:fields {:x [:Maybe :Integer]},
                :constraints [["c1" '(if-value-let [p x] (> p 10) true)]]}}
     {:$instance-of :ws/X$v1,
      :x 10}
     [{:choco-spec {:vars {$_0 :Int},
                    :constraints #{(if true (> $_0 10) true)}},
       :choco-bounds {$_0 10},
       :sym-to-path [$_0 [:x]]} {:$contradiction? true}])

    (stanza "instance literals and optional")

    (check-propagate
     {:ws/X$v1 {:fields {:x :Integer,
                         :y :Integer},
                :constraints [["c1" '(= 10 (+ x y))]]},
      :ws/A$v1 {:fields {:a :Integer},
                :constraints [["c2"
                               '(if-value-let [q
                                               (when true
                                                 {:$type :ws/X$v1,
                                                  :x a,
                                                  :y 6})]
                                              true
                                              false)]]}}
     {:$instance-of :ws/A$v1}
     [{:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{true (if true (= 10 (+ $_0 6)) true)}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 true},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
      {:$instance-of :ws/A$v1,
       :a 4}])

    (check-propagate
     {:ws/X$v1 {:fields {:x :Integer,
                         :y :Integer},
                :constraints [["c1" '(= 10 (+ x y))]]},
      :ws/A$v1 {:fields {:a :Integer},
                :constraints [["c2"
                               '(if-value-let [q
                                               (when false
                                                 {:$type :ws/X$v1,
                                                  :x a,
                                                  :y 6})]
                                              true
                                              false)]]}}
     {:$instance-of :ws/A$v1}
     [{:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(= 10 (+ $_0 6)) false}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 true},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]} {:$contradiction? true}])

    (check-propagate
     {:ws/X$v1 {:fields {:x [:Maybe :Integer],
                         :y :Integer},
                :constraints [["c1" '(if-value x (= 10 (+ x y)) true)]]},
      :ws/P$v1 {:fields {:q [:Instance :ws/X$v1]}},
      :ws/A$v1 {:fields {:a [:Maybe :Integer]},
                :constraints [["c2"
                               '(if-value-let [f
                                               (get-in {:$type :ws/P$v1,
                                                        :q {:$type :ws/X$v1,
                                                            :x a,
                                                            :y 7}}
                                                       [:q :x])]
                                              true
                                              false)]]}}
     {:$instance-of :ws/A$v1,
      :a {:$value? false}}
     [{:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{$_1 (if $_1 (= 10 (+ $_0 7)) true)}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 false},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]} {:$contradiction? true}])

    (check-propagate
     {:ws/X$v1 {:fields {:x [:Maybe :Integer],
                         :y :Integer},
                :constraints [["c1" '(if-value x (= 10 (+ x y)) true)]]},
      :ws/P$v1 {:fields {:q [:Instance :ws/X$v1]}},
      :ws/A$v1 {:fields {:a :Integer},
                :constraints [["c2"
                               '(if-value-let [f
                                               (get-in {:$type :ws/P$v1,
                                                        :q {:$type :ws/X$v1,
                                                            :x a,
                                                            :y 7}}
                                                       [:q :x])]
                                              true
                                              false)]]}}
     {:$instance-of :ws/A$v1,
      :a 4}
     [{:choco-spec {:vars {$_0 :Int},
                    :constraints #{true (if true (= 10 (+ $_0 7)) true)}},
       :choco-bounds {$_0 4},
       :sym-to-path [$_0 [:a]]} {:$contradiction? true}])

    (check-propagate
     {:ws/X$v1 {:fields {:x [:Maybe :Integer],
                         :y :Integer},
                :constraints [["c1" '(if-value x (= 10 (+ x y)) true)]]},
      :ws/P$v1 {:fields {:q [:Instance :ws/X$v1]}},
      :ws/A$v1 {:fields {:a :Integer},
                :constraints [["c2"
                               '(if-value-let [f
                                               (get-in {:$type :ws/P$v1,
                                                        :q {:$type :ws/X$v1,
                                                            :x a,
                                                            :y 7}}
                                                       [:q :x])]
                                              true
                                              false)]]}}
     {:$instance-of :ws/A$v1,
      :a 3}
     [{:choco-spec {:vars {$_0 :Int},
                    :constraints #{true (if true (= 10 (+ $_0 7)) true)}},
       :choco-bounds {$_0 3},
       :sym-to-path [$_0 [:a]]}
      {:$instance-of :ws/A$v1,
       :a 3}])

    (check-propagate
     {:ws/X$v1 {:fields {:x [:Maybe :Integer],
                         :y :Integer},
                :constraints [["c1" '(if-value x (= 10 (+ x y)) true)]]},
      :ws/P$v1 {:fields {:q [:Instance :ws/X$v1]}},
      :ws/A$v1 {:fields {:a [:Maybe :Integer]},
                :constraints [["c2"
                               '(if-value-let [f
                                               (get-in {:$type :ws/P$v1,
                                                        :q {:$type :ws/X$v1,
                                                            :x a,
                                                            :y 7}}
                                                       [:q :x])]
                                              true
                                              false)]]}}
     {:$instance-of :ws/A$v1,
      :a 3}
     [{:choco-spec {:vars {$_0 :Int},
                    :constraints #{true (if true (= 10 (+ $_0 7)) true)}},
       :choco-bounds {$_0 3},
       :sym-to-path [$_0 [:a]]}
      {:$instance-of :ws/A$v1,
       :a 3}])

    (check-propagate
     {:ws/X$v1 {:fields {:x [:Maybe :Integer],
                         :y :Integer},
                :constraints [["c1" '(if-value x (= 10 (+ x y)) true)]]},
      :ws/P$v1 {:fields {:q [:Instance :ws/X$v1]}},
      :ws/A$v1 {:fields {:a [:Maybe :Integer]},
                :constraints [["c2"
                               '(if-value-let [f
                                               (get-in {:$type :ws/P$v1,
                                                        :q {:$type :ws/X$v1,
                                                            :x a,
                                                            :y 7}}
                                                       [:q :x])]
                                              true
                                              false)]]}}
     {:$instance-of :ws/A$v1}
     [{:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{$_1 (if $_1 (= 10 (+ $_0 7)) true)}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 #{true false}},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
      {:$instance-of :ws/A$v1,
       :a 3}])

    (check-propagate
     {:ws/X$v1 {:fields {:x [:Maybe :Integer],
                         :y :Integer},
                :constraints [["c1" '(if-value x (= 10 (+ x y)) true)]]},
      :ws/P$v1 {:fields {:q [:Instance :ws/X$v1]}},
      :ws/A$v1 {:fields {:a [:Maybe :Integer]},
                :constraints [["c2"
                               '(if-value-let [f
                                               (get-in {:$type :ws/P$v1,
                                                        :q {:$type :ws/X$v1,
                                                            :x a,
                                                            :y 7}}
                                                       [:q :x])]
                                              false
                                              true)]]}}
     {:$instance-of :ws/A$v1}
     [{:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(not $_1) (if $_1 (= 10 (+ $_0 7)) true)}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 #{true false}},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
      {:$instance-of :ws/A$v1,
       :a {:$value? false}}])

    (check-propagate
     {:ws/X$v1 {:fields {:x [:Maybe :Integer],
                         :y :Integer},
                :constraints [["c1" '(if-value x (= 10 (+ x y)) true)]]},
      :ws/P$v1 {:fields {:q [:Instance :ws/X$v1]}},
      :ws/A$v1 {:fields {:a [:Maybe :Integer]},
                :constraints [["c2"
                               '(if-value-let [f
                                               (get-in {:$type :ws/P$v1,
                                                        :q {:$type :ws/X$v1,
                                                            :x a,
                                                            :y 7}}
                                                       [:q :x])]
                                              true
                                              true)]]}}
     {:$instance-of :ws/A$v1}
     [{:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{true (if $_1 (= 10 (+ $_0 7)) true)}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 #{true false}},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
      {:$instance-of :ws/A$v1,
       :a {:$ranges #{[-1000 1000]}}}])

    (stanza "conditionals around instance literals")

    (check-propagate
     {:ws/X$v1 {:fields {:x :Integer,
                         :y :Integer},
                :constraints [["c1" '(= 10 (+ x y))]]},
      :ws/A$v1 {:fields {:a :Integer},
                :constraints [["c2"
                               '(if (> a 0)
                                  (let [q {:$type :ws/X$v1,
                                           :x a,
                                           :y 6}]
                                    true)
                                  true)]]}}
     {:$instance-of :ws/A$v1}
     [{:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(if (> $_0 0) true true)
                                   (if (> $_0 0) (= 10 (+ $_0 6)) true)}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 true},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
      {:$instance-of :ws/A$v1,
       :a {:$ranges #{[-1000 1000]}}}])

    (check-propagate
     {:ws/X$v1 {:fields {:x :Integer,
                         :y :Integer},
                :constraints [["c1" '(= 10 (+ x y))]]},
      :ws/A$v1 {:fields {:a :Integer},
                :constraints [["c2"
                               '(if (> a 0)
                                  (let [q {:$type :ws/X$v1,
                                           :x a,
                                           :y 6}]
                                    true)
                                  (let [q {:$type :ws/X$v1,
                                           :x (- 0 a),
                                           :y 7}]
                                    true))]]}}
     {:$instance-of :ws/A$v1,
      :a -4}
     [{:choco-spec {:vars {$_0 :Int},
                    :constraints
                    #{(if (> $_0 0) true true)
                      (if (> $_0 0) (= 10 (+ $_0 6)) true)
                      (if (not (> $_0 0)) (= 10 (+ (- 0 $_0) 7)) true)}},
       :choco-bounds {$_0 -4},
       :sym-to-path [$_0 [:a]]} {:$contradiction? true}])

    (check-propagate
     {:ws/X$v1 {:fields {:x :Integer,
                         :y :Integer},
                :constraints [["c1" '(= 10 (+ x y))]]},
      :ws/A$v1 {:fields {:a :Integer},
                :constraints [["c2"
                               '(if (> a 0)
                                  (let [q {:$type :ws/X$v1,
                                           :x a,
                                           :y 6}]
                                    true)
                                  (let [q {:$type :ws/X$v1,
                                           :x (- 0 a),
                                           :y 7}]
                                    true))]]}}
     {:$instance-of :ws/A$v1,
      :a -3}
     [{:choco-spec {:vars {$_0 :Int},
                    :constraints
                    #{(if (> $_0 0) true true)
                      (if (> $_0 0) (= 10 (+ $_0 6)) true)
                      (if (not (> $_0 0)) (= 10 (+ (- 0 $_0) 7)) true)}},
       :choco-bounds {$_0 -3},
       :sym-to-path [$_0 [:a]]}
      {:$instance-of :ws/A$v1,
       :a -3}])

    (check-propagate
     {:ws/X$v1 {:fields {:x :Integer,
                         :y :Integer},
                :constraints [["c1" '(= 10 (+ x y))]]},
      :ws/A$v1 {:fields {:a [:Maybe :Integer]},
                :constraints [["c2"
                               '(if-value-let [w a]
                                              (let [q {:$type :ws/X$v1,
                                                       :x w,
                                                       :y 6}]
                                                true)
                                              true)]]}}
     {:$instance-of :ws/A$v1,
      :a {:$value? true}}
     [{:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(if $_1 (= 10 (+ $_0 6)) true) true}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 true},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
      {:$instance-of :ws/A$v1,
       :a 4}])

    (check-propagate
     {:ws/X$v1 {:fields {:x :Integer,
                         :y :Integer},
                :constraints [["c1" '(= 10 (+ x y))]]},
      :ws/A$v1 {:fields {:a [:Maybe :Integer]},
                :constraints [["c2"
                               '(if-value-let [w a]
                                              (let [q {:$type :ws/X$v1,
                                                       :x w,
                                                       :y 6}]
                                                true)
                                              true)]]}}
     {:$instance-of :ws/A$v1,
      :a 3}
     [{:choco-spec {:vars {$_0 :Int},
                    :constraints #{true (if true (= 10 (+ $_0 6)) true)}},
       :choco-bounds {$_0 3},
       :sym-to-path [$_0 [:a]]} {:$contradiction? true}])

    (check-propagate
     {:ws/X$v1 {:fields {:x :Integer,
                         :y :Integer},
                :constraints [["c1" '(= 10 (+ x y))]]},
      :ws/A$v1 {:fields {:a [:Maybe :Integer]},
                :constraints [["c2"
                               '(if-value-let [w a]
                                              (let [q {:$type :ws/X$v1,
                                                       :x w,
                                                       :y 6}]
                                                true)
                                              true)]]}}
     {:$instance-of :ws/A$v1,
      :a 4}
     [{:choco-spec {:vars {$_0 :Int},
                    :constraints #{true (if true (= 10 (+ $_0 6)) true)}},
       :choco-bounds {$_0 4},
       :sym-to-path [$_0 [:a]]}
      {:$instance-of :ws/A$v1,
       :a 4}])

    (check-propagate
     {:ws/X$v1 {:fields {:x :Integer,
                         :y :Integer},
                :constraints [["c1" '(= 10 (+ x y))]]},
      :ws/A$v1 {:fields {:a [:Maybe :Integer]},
                :constraints [["c2"
                               '(if-value-let [w a]
                                              (let [q {:$type :ws/X$v1,
                                                       :x w,
                                                       :y 6}]
                                                true)
                                              true)]]}}
     {:$instance-of :ws/A$v1,
      :a {:$value? false}}
     [{:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(if $_1 (= 10 (+ $_0 6)) true) true}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 false},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
      {:$instance-of :ws/A$v1,
       :a {:$value? false}}])

    (check-propagate
     {:ws/X$v1 {:fields {:x :Integer,
                         :y :Integer},
                :constraints [["c1" '(= 10 (+ x y))]]},
      :ws/A$v1 {:fields {:a [:Maybe :Integer]},
                :constraints [["c2"
                               '(if-value-let [w a]
                                              (let [q {:$type :ws/X$v1,
                                                       :x w,
                                                       :y 6}]
                                                true)
                                              false)]]}}
     {:$instance-of :ws/A$v1,
      :a {:$value? false}}
     [{:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(if $_1 (= 10 (+ $_0 6)) true) $_1}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 false},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]} {:$contradiction? true}])

    (check-propagate
     {:ws/X$v1 {:fields {:x :Integer,
                         :y :Integer},
                :constraints [["c1" '(= 10 (+ x y))]]},
      :ws/A$v1 {:fields {:a [:Maybe :Integer]},
                :constraints [["c2"
                               '(if-value-let [w a]
                                              (let [q {:$type :ws/X$v1,
                                                       :x w,
                                                       :y 6}]
                                                true)
                                              false)]]}}
     {:$instance-of :ws/A$v1}
     [{:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(if $_1 (= 10 (+ $_0 6)) true) $_1}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 #{true false}},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
      {:$instance-of :ws/A$v1,
       :a 4}])))

(defn format-code [code]
  (str (zprint/zprint-str code 80 {:fn-force-nl #{:binding}
                                   :style :backtranslate
                                   :map {:force-nl? true
                                         :key-order [:abstract? :fields :constraints :refines-to
                                                     :name :expr :extrinsic?
                                                     :$instance-of
                                                     :$refines-to
                                                     :choco-spec
                                                     :choco-bounds
                                                     :sym-to-path]}})
       "\n\n"))

;; (set! *print-namespace-maps* false)

;; (spit "target/propagate-test.edn" (str "(" "do\n" (apply str (map format-code @test-atom)) ")"))

;; (time (run-tests))
