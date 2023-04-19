;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-bom-pipeline
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-pipeline :as bom-pipeline]
            [com.viasat.halite.op-add-types :as op-add-types]
            [com.viasat.halite.op-canon :as op-canon]
            [com.viasat.halite.op-canon-refinements :as op-canon-refinements]
            [com.viasat.halite.op-canon-up :as op-canon-up]
            [com.viasat.halite.op-conjoin-spec-bom :as op-conjoin-spec-bom]
            [com.viasat.halite.op-contradictions :as op-contradictions]
            [com.viasat.halite.op-find-concrete :as op-find-concrete]
            [com.viasat.halite.op-mandatory :as op-mandatory]
            [com.viasat.halite.op-push-down-to-concrete :as op-push-down-to-concrete]
            [com.viasat.halite.op-syntax-check :as op-syntax-check]
            [com.viasat.halite.op-type-check :as op-type-check]
            [schema.test]
            [zprint.core :as zprint])
  (:import [clojure.lang ExceptionInfo]
           [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(defn- find-namespace [ns-symbol]
  (first (filter #(= ns-symbol (ns-name %)) (all-ns))))

(defn namespace-fixture
  "Used to set the namespace for when the test is invoked from lein. This is required because of the weird 'eval' call in the tests."
  [ns-symbol]
  (fn [f]
    (binding [*ns* (find-namespace ns-symbol)]
      (f))))

(def fixtures (join-fixtures [schema.test/validate-schemas (namespace-fixture 'com.viasat.halite.test-bom-pipeline)]))

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

(defn third [x]
  (->> x rest rest first))

(defn fourth [x]
  (->> x rest rest rest first))

(defmacro check-propagate [spec-env bom & more]
  `(let [expected-result# ~(third (first more))
         comment# ~(fourth (first more))
         spec-env0# (quote ~spec-env)
         spec-env# (eval spec-env0#)
         bom0# ~bom
         {lowered-bom# :lowered-bom
          choco-data# :choco-data
          result# :result} (bom-pipeline/propagate spec-env# bom0#)
         test-result-block# (if comment#
                              [lowered-bom# choco-data# result# comment#]
                              [lowered-bom# choco-data# result#])]
     (is (= (quote ~(second (first more))) choco-data#))
     (is (= (quote ~(first (first more))) lowered-bom#))
     (is (= expected-result# result#))
     (swap! test-atom conj (list '~'check-propagate spec-env0# bom0# test-result-block#))
     test-result-block#))

(defn stanza [s]
  (swap! test-atom conj (list 'stanza s)))

(def test-data
  "Generating all of this code inside of a method was blowing the java method size limit"
  '[(stanza "composition with a basic bom")

    (stanza "boolean fields")

    (check-propagate {:ws/A$v1 {:fields {:x :Boolean,
                                         :y :Boolean},
                                :constraints [["c1" '(if x true y)]]}}
                     {:$instance-of :ws/A$v1}
                     [{:$instance-of :ws/A$v1,
                       :x {:$value? true,
                           :$primitive-type :Boolean},
                       :y {:$value? true,
                           :$primitive-type :Boolean},
                       :$constraints {"c1" (or #r [:x] #r [:y])}}
                      {:choco-spec {:vars {$_0 :Bool,
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
                                :constraints [["c1" '(if x true y)]]}}
                     {:$instance-of :ws/A$v1,
                      :x false}
                     [{:$instance-of :ws/A$v1,
                       :x false,
                       :y {:$value? true,
                           :$primitive-type :Boolean},
                       :$constraints {"c1" (or #r [:x] #r [:y])}}
                      {:choco-spec {:vars {$_0 :Bool,
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

    (check-propagate {:ws/A$v1 {:fields {:x :Boolean,
                                         :y :Boolean},
                                :constraints [["c1" '(if x true y)]]}}
                     {:$instance-of :ws/A$v1,
                      :y false}
                     [{:$instance-of :ws/A$v1,
                       :y false,
                       :x {:$value? true,
                           :$primitive-type :Boolean},
                       :$constraints {"c1" (or #r [:x] #r [:y])}}
                      {:choco-spec {:vars {$_0 :Bool,
                                           $_1 :Bool,
                                           $_2 :Bool},
                                    :constraints #{(or $_1 $_0)}},
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
                     [{:$instance-of :ws/A$v1,
                       :x true,
                       :y {:$value? true,
                           :$primitive-type :Boolean},
                       :$constraints {}}
                      {:choco-spec {:vars {$_0 :Bool,
                                           $_1 :Bool,
                                           $_2 :Bool},
                                    :constraints #{}},
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
                     [{:$instance-of :ws/A$v1,
                       :x {:$value? true,
                           :$primitive-type :Boolean},
                       :y {:$value? true,
                           :$primitive-type :Boolean},
                       :$constraints {"c1" (or #r [:x] #r [:y])}}
                      {:choco-spec {:vars {$_0 :Bool,
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
                     [{:$instance-of :ws/A$v1,
                       :x false,
                       :y {:$value? true,
                           :$primitive-type :Boolean},
                       :$constraints {"c1" (or #r [:x] #r [:y])}}
                      {:choco-spec {:vars {$_0 :Bool,
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
                     [{:$instance-of :ws/A$v1,
                       :y {:$value? true,
                           :$primitive-type :Boolean},
                       :x {:$primitive-type :Boolean,
                           :$value? {:$primitive-type :Boolean}}}
                      {:choco-spec {:vars {$_0 :Bool,
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
                     [{:$instance-of :ws/A$v1,
                       :y {:$value? true,
                           :$primitive-type :Boolean},
                       :x {:$primitive-type :Boolean,
                           :$value? {:$primitive-type :Boolean}},
                       :$constraints {"c1" (or (not #r [:x :$value?]) #r [:y])}}
                      {:choco-spec {:vars {$_0 :Bool,
                                           $_1 :Bool,
                                           $_2 :Bool,
                                           $_3 :Bool},
                                    :constraints #{(or (not $_3) $_0)}},
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
                     [{:$instance-of :ws/A$v1,
                       :x true,
                       :y {:$value? true,
                           :$primitive-type :Boolean},
                       :$constraints {"c1" #r [:y]}}
                      {:choco-spec {:vars {$_0 :Bool,
                                           $_1 :Bool,
                                           $_2 :Bool},
                                    :constraints #{$_1}},
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
     [{:$instance-of :ws/A$v1,
       :x {:$value? true,
           :$primitive-type :Integer},
       :y {:$value? true,
           :$primitive-type :Integer},
       :$constraints {"c1" (> #r [:x] #r [:y])}}
      {:choco-spec {:vars {$_0 :Int,
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
       :x {:$ranges #{[-999 1001]}},
       :y {:$ranges #{[-1000 1000]}}}])

    (stanza "constraint across two integer fields")

    (check-propagate
     {:ws/A$v1 {:fields {:x :Integer,
                         :y :Integer},
                :constraints [["c1" '(= 15 (+ x y))]
                              ["c2" '(or (= x 1) (= x 2) (= x 3))]
                              ["c3" '(or (= y 10) (= y 12) (= y 14))]]}}
     {:$instance-of :ws/A$v1}
     [{:$instance-of :ws/A$v1,
       :x {:$enum #{1 3 2},
           :$value? true,
           :$primitive-type :Integer},
       :y {:$enum #{12 14 10},
           :$value? true,
           :$primitive-type :Integer},
       :$constraints {"c1" (= 15 (+ #r [:x] #r [:y])),
                      "c2" (or (= #r [:x] 1) (= #r [:x] 2) (= #r [:x] 3)),
                      "c3" (or (= #r [:y] 10) (= #r [:y] 12) (= #r [:y] 14))}}
      {:choco-spec {:vars {$_0 :Int,
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
                     [{:$instance-of :ws/A$v1,
                       :a {:$value? true,
                           :$primitive-type [:Decimal 2]}}
                      {:choco-spec {:vars {$_0 :Int,
                                           $_1 :Bool},
                                    :constraints #{}},
                       :choco-bounds {$_0 [-1000 1000],
                                      $_1 true},
                       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
                      {:$instance-of :ws/A$v1,
                       :a {:$ranges #{[#d "-10.00" #d "10.01"]}}}])

    (check-propagate
     {:ws/A$v1 {:fields {:a [:Decimal 2],
                         :b [:Decimal 2]},
                :constraints [["c1" '(= #d "1.05" (+ a b))]]}}
     {:$instance-of :ws/A$v1}
     [{:$instance-of :ws/A$v1,
       :a {:$value? true,
           :$primitive-type [:Decimal 2]},
       :b {:$value? true,
           :$primitive-type [:Decimal 2]},
       :$constraints {"c1" (= 105 (+ #r [:a] #r [:b]))}}
      {:choco-spec {:vars {$_0 :Int,
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
       :a {:$ranges #{[#d "-8.95" #d "10.01"]}},
       :b {:$ranges #{[#d "-8.95" #d "10.01"]}}}])

    (check-propagate
     {:ws/A$v1 {:fields {:a [:Decimal 2],
                         :b [:Decimal 2]},
                :constraints [["c1" '(= #d "1.05" (+ a b))]]}}
     {:$instance-of :ws/A$v1,
      :a {:$enum #{#d "0.05" #d "0.06"}}}
     [{:$instance-of :ws/A$v1,
       :a {:$enum #{#d "0.05" #d "0.06"},
           :$value? true,
           :$primitive-type [:Decimal 2]},
       :b {:$value? true,
           :$primitive-type [:Decimal 2]},
       :$constraints {"c1" (= 105 (+ #r [:a] #r [:b]))}}
      {:choco-spec {:vars {$_0 :Int,
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
       :b {:$ranges #{[#d "0.99" #d "1.01"]}}}])

    (check-propagate
     {:ws/A$v1 {:fields {:a [:Decimal 2]},
                :constraints [["c1" '(= (rescale #d "5.01" 3) (rescale a 3))]]}}
     {:$instance-of :ws/A$v1}
     [{:$instance-of :ws/A$v1,
       :a {:$value? true,
           :$primitive-type [:Decimal 2]},
       :$constraints {"c1" (= 5010 (* #r [:a] 10))}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(= 5010 (* $_0 10))}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 true},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
      {:$instance-of :ws/A$v1,
       :a #d "5.01"}])

    (check-propagate
     {:ws/A$v1 {:fields {:a [:Decimal 2]},
                :constraints [["c1" '(= (rescale #d "5.01" 1) (rescale a 1))]]}}
     {:$instance-of :ws/A$v1}
     [{:$instance-of :ws/A$v1,
       :a {:$value? true,
           :$primitive-type [:Decimal 2]},
       :$constraints {"c1" (= 50 (div #r [:a] 10))}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(= 50 (div $_0 10))}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 true},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
      {:$instance-of :ws/A$v1,
       :a {:$ranges #{[#d "-5.09" #d "5.10"]}}}])

    (check-propagate {:ws/A$v1 {:fields {:a [:Decimal 2]},
                                :constraints [["c1" '(= 5 (rescale a 0))]]}}
                     {:$instance-of :ws/A$v1}
                     [{:$instance-of :ws/A$v1,
                       :a {:$value? true,
                           :$primitive-type [:Decimal 2]},
                       :$constraints {"c1" (= 5 (div #r [:a] 100))}}
                      {:choco-spec {:vars {$_0 :Int,
                                           $_1 :Bool},
                                    :constraints #{(= 5 (div $_0 100))}},
                       :choco-bounds {$_0 [-1000 1000],
                                      $_1 true},
                       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
                      {:$instance-of :ws/A$v1,
                       :a {:$ranges #{[#d "-5.99" #d "6.00"]}}}])

    (check-propagate {:ws/A$v1 {:fields {:a [:Decimal 2]},
                                :constraints [["c1" '(= 503 (rescale a 2))]]}}
                     {:$instance-of :ws/A$v1}
                     [{:$instance-of :ws/A$v1,
                       :a {:$value? true,
                           :$primitive-type [:Decimal 2]},
                       :$constraints {"c1" (= 503 #r [:a])}}
                      {:choco-spec {:vars {$_0 :Int,
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
                     [{:$instance-of :ws/A$v1,
                       :a {:$value? true,
                           :$primitive-type [:Decimal 2]},
                       :$constraints {"c1" (= 30 (div #r [:a] 10))}}
                      {:choco-spec {:vars {$_0 :Int,
                                           $_1 :Bool},
                                    :constraints #{(= 30 (div $_0 10))}},
                       :choco-bounds {$_0 [-1000 1000],
                                      $_1 true},
                       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
                      {:$instance-of :ws/A$v1,
                       :a {:$ranges #{[#d "-3.09" #d "3.10"]}}}])

    (check-propagate
     {:ws/A$v1 {:fields {:a [:Decimal 2]},
                :constraints [["c1" '(= #d "3.0" (rescale (+ a #d "0.01") 1))]]}}
     {:$instance-of :ws/A$v1}
     [{:$instance-of :ws/A$v1,
       :a {:$value? true,
           :$primitive-type [:Decimal 2]},
       :$constraints {"c1" (= 30 (div (+ #r [:a] 1) 10))}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(= 30 (div (+ $_0 1) 10))}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 true},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
      {:$instance-of :ws/A$v1,
       :a {:$ranges #{[#d "-3.10" #d "3.09"]}}}])

    (check-propagate {:ws/A$v1 {:fields {:a [:Decimal 2]}}}
                     {:$instance-of :ws/A$v1,
                      :a #d "3.14"}
                     [{:$instance-of :ws/A$v1,
                       :a 314}
                      {:choco-spec {:vars {$_0 :Int},
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
     [{:$instance-of :ws/A$v1,
       :a {:$primitive-type [:Decimal 2],
           :$value? {:$primitive-type :Boolean}},
       :$constraints {"c1" (= 30 (if #r [:a :$value?] (div (+ #r [:a] 1) 10) 20))}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(= 30 (if $_1 (div (+ $_0 1) 10) 20))}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 #{true false}},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
      {:$instance-of :ws/A$v1,
       :a {:$ranges #{[#d "-3.10" #d "3.09"]},
           :$value? true}}])

    (check-propagate
     {:ws/A$v1 {:fields {:a [:Maybe [:Decimal 2]]},
                :constraints
                [["c1"
                  '(= #d "3.00"
                      (if-value a (rescale (+ a #d "0.01") 1) #d "3.00"))]]}}
     {:$instance-of :ws/A$v1}
     [{:$instance-of :ws/A$v1,
       :a {:$primitive-type [:Decimal 2],
           :$value? {:$primitive-type :Boolean}},
       :$constraints {"c1" (= 300
                              (if #r [:a :$value?] (div (+ #r [:a] 1) 10) 300))}}
      {:choco-spec {:vars {$_0 :Int,
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
     [{:$instance-of :ws/A$v1,
       :a {:$primitive-type [:Decimal 2],
           :$value? {:$primitive-type :Boolean}},
       :$constraints {"c1" (= 30 (if #r [:a :$value?] (div (+ #r [:a] 1) 10) 30))}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(= 30 (if $_1 (div (+ $_0 1) 10) 30))}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 #{true false}},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
      {:$instance-of :ws/A$v1,
       :a {:$ranges #{[#d "-10.00" #d "10.01"]}}}])

    (check-propagate
     {:ws/A$v1 {:fields {:a [:Maybe [:Decimal 2]]},
                :constraints
                [["c1"
                  '(= #d "3.0"
                      (if-value a (rescale (+ a #d "0.01") 1) #d "3.0"))]]}}
     {:$instance-of :ws/A$v1,
      :a {:$value? true}}
     [{:$instance-of :ws/A$v1,
       :a {:$value? true,
           :$primitive-type [:Decimal 2]},
       :$constraints {"c1" (= 30 (if true (div (+ #r [:a] 1) 10) 30))}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(= 30 (if true (div (+ $_0 1) 10) 30))}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 true},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
      {:$instance-of :ws/A$v1,
       :a {:$ranges #{[#d "-3.10" #d "3.09"]},
           :$value? true}}])

    (check-propagate
     {:ws/A$v1 {:fields {:a [:Maybe [:Decimal 2]]},
                :constraints
                [["c1"
                  '(= #d "3.0"
                      (if-value a (rescale (+ a #d "0.01") 1) #d "3.0"))]]}}
     {:$instance-of :ws/A$v1,
      :a {:$value? false}}
     [{:$instance-of :ws/A$v1,
       :a {:$value? false,
           :$primitive-type [:Decimal 2]},
       :$constraints {"c1" (= 30 (if #r [:a :$value?] (div (+ #r [:a] 1) 10) 30))}}
      {:choco-spec {:vars {$_0 :Int,
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
     [{:$instance-of :ws/A$v1,
       :a 400,
       :$constraints {"c1" false}}
      {:choco-spec {:vars {$_0 :Int},
                    :constraints #{false}},
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
     [{:$instance-of :ws/B$v1,
       :a {:$value? true,
           :$instance-of :ws/A$v1,
           :x {:$enum #{1 3 2},
               :$value? true,
               :$primitive-type :Integer},
           :$constraints
           {"c2" (or (= #r [:a :x] 1) (= #r [:a :x] 2) (= #r [:a :x] 3))}},
       :y {:$enum #{12 14 10},
           :$value? true,
           :$primitive-type :Integer},
       :$constraints {"c1" (= 15 (+ #r [:a :x] #r [:y])),
                      "c3" (or (= #r [:y] 10) (= #r [:y] 12) (= #r [:y] 14))}}
      {:choco-spec {:vars {$_0 :Bool,
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

    (check-propagate
     {:ws/B$v1 {:fields {:a [:Instance :ws/A$v1],
                         :y :Integer},
                :constraints [["c1" '(= 15 (+ (get a :x) y))]
                              ["c3" '(or (= y 10) (= y 12) (= y 14))]]},
      :ws/A$v1 {:fields {:x :Integer},
                :constraints [["c2" '(or (= x 1) (= x 2) (= x 3))]]}}
     {:$instance-of :ws/B$v1,
      :a {:$instance-of :ws/A$v1,
          :x 3}}
     [{:$instance-of :ws/B$v1,
       :a {:x 3,
           :$instance-of :ws/A$v1,
           :$value? true,
           :$constraints {}},
       :y {:$enum #{12 14 10},
           :$value? true,
           :$primitive-type :Integer},
       :$constraints {"c1" (= 15 (+ 3 #r [:y])),
                      "c3" (or (= #r [:y] 10) (= #r [:y] 12) (= #r [:y] 14))}}
      {:choco-spec {:vars {$_0 :Bool,
                           $_1 :Int,
                           $_2 :Int,
                           $_3 :Bool},
                    :constraints #{(= 15 (+ 3 $_2))
                                   (or (= $_2 10) (= $_2 12) (= $_2 14))}},
       :choco-bounds {$_0 true,
                      $_1 3,
                      $_2 #{12 14 10},
                      $_3 true},
       :sym-to-path [$_0 [:a :$value?] $_1 [:a :x] $_2 [:y] $_3 [:y :$value?]]}
      {:$instance-of :ws/B$v1,
       :a {:x 3,
           :$instance-of :ws/A$v1},
       :y 12}])

    (check-propagate
     {:ws/B$v1 {:fields {:a [:Instance :ws/A$v1],
                         :y :Integer},
                :constraints [["c1" '(= 15 (+ (get a :x) y))]
                              ["c3" '(or (= y 10) (= y 12) (= y 14))]]},
      :ws/A$v1 {:fields {:x :Integer},
                :constraints [["c2" '(or (= x 1) (= x 2) (= x 3))]]}}
     {:$instance-of :ws/B$v1,
      :a {:$instance-of :ws/A$v1,
          :x 4}}
     [{:$instance-of :ws/B$v1,
       :a {:x {:$value? false,
               :$primitive-type :Integer},
           :$instance-of :ws/A$v1,
           :$value? true,
           :$constraints
           {"c2" (or (= #r [:a :x] 1) (= #r [:a :x] 2) (= #r [:a :x] 3))}},
       :y {:$enum #{12 14 10},
           :$value? true,
           :$primitive-type :Integer},
       :$constraints {"c1" (= 15 (+ #r [:a :x] #r [:y])),
                      "c3" (or (= #r [:y] 10) (= #r [:y] 12) (= #r [:y] 14))}}
      {:choco-spec {:vars {$_0 :Bool,
                           $_1 :Int,
                           $_2 :Bool,
                           $_3 :Int,
                           $_4 :Bool},
                    :constraints #{(or (= $_3 10) (= $_3 12) (= $_3 14))
                                   (or (= $_1 1) (= $_1 2) (= $_1 3))
                                   (= 15 (+ $_1 $_3))}},
       :choco-bounds {$_0 true,
                      $_1 [-1000 1000],
                      $_2 false,
                      $_3 #{12 14 10},
                      $_4 true},
       :sym-to-path [$_0 [:a :$value?] $_1 [:a :x] $_2 [:a :x :$value?] $_3 [:y]
                     $_4 [:y :$value?]]}
      {:$instance-of :ws/B$v1,
       :a {:x {:$value? false},
           :$instance-of :ws/A$v1},
       :y {:$enum #{12 14 10}}}])

    (stanza "optional integer field")

    (check-propagate {:ws/A$v1 {:fields {:x [:Maybe :Integer]}}}
                     {:$instance-of :ws/A$v1}
                     [{:$instance-of :ws/A$v1,
                       :x {:$primitive-type :Integer,
                           :$value? {:$primitive-type :Boolean}}}
                      {:choco-spec {:vars {$_0 :Int,
                                           $_1 :Bool},
                                    :constraints #{}},
                       :choco-bounds {$_0 [-1000 1000],
                                      $_1 #{true false}},
                       :sym-to-path [$_0 [:x] $_1 [:x :$value?]]}
                      {:$instance-of :ws/A$v1,
                       :x {:$ranges #{[-1000 1001]}}}])

    (check-propagate
     {:ws/A$v1 {:fields {:x [:Maybe :Integer]},
                :constraints [["c1" '(if-value x (and (> x 20) (< x 30)) true)]]}}
     {:$instance-of :ws/A$v1}
     [{:$instance-of :ws/A$v1,
       :x {:$enum #{27 24 21 22 29 28 25 23 26},
           :$primitive-type :Integer,
           :$value? {:$primitive-type :Boolean}},
       :$constraints {"c1" (or (not #r [:x :$value?])
                               (and (> #r [:x] 20) (< #r [:x] 30)))}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(or (not $_1) (and (> $_0 20) (< $_0 30)))}},
       :choco-bounds {$_0 #{27 24 21 22 29 28 25 23 26},
                      $_1 #{true false}},
       :sym-to-path [$_0 [:x] $_1 [:x :$value?]]}
      {:$instance-of :ws/A$v1,
       :x {:$enum #{27 24 21 22 29 28 25 23 26}}}])

    (check-propagate {:ws/A$v1 {:fields {:x [:Maybe :Integer]},
                                :constraints [["c1" '(if-value x false true)]]}}
                     {:$instance-of :ws/A$v1}
                     [{:$instance-of :ws/A$v1,
                       :x {:$primitive-type :Integer,
                           :$value? {:$primitive-type :Boolean}},
                       :$constraints {"c1" (not #r [:x :$value?])}}
                      {:choco-spec {:vars {$_0 :Int,
                                           $_1 :Bool},
                                    :constraints #{(not $_1)}},
                       :choco-bounds {$_0 [-1000 1000],
                                      $_1 #{true false}},
                       :sym-to-path [$_0 [:x] $_1 [:x :$value?]]}
                      {:$instance-of :ws/A$v1,
                       :x {:$value? false}}])

    (check-propagate {:ws/A$v1 {:fields {:x [:Maybe :Integer]},
                                :constraints [["c1" '(if-value x true false)]]}}
                     {:$instance-of :ws/A$v1}
                     [{:$instance-of :ws/A$v1,
                       :x {:$primitive-type :Integer,
                           :$value? {:$primitive-type :Boolean}},
                       :$constraints {"c1" #r [:x :$value?]}}
                      {:choco-spec {:vars {$_0 :Int,
                                           $_1 :Bool},
                                    :constraints #{$_1}},
                       :choco-bounds {$_0 [-1000 1000],
                                      $_1 #{true false}},
                       :sym-to-path [$_0 [:x] $_1 [:x :$value?]]}
                      {:$instance-of :ws/A$v1,
                       :x {:$ranges #{[-1000 1001]},
                           :$value? true}}])

    (check-propagate
     {:ws/A$v1 {:fields {:x [:Maybe :Integer],
                         :y [:Maybe :Integer]},
                :constraints [["c1" '(if-value x (if-value y true false) false)]]}}
     {:$instance-of :ws/A$v1}
     [{:$instance-of :ws/A$v1,
       :x {:$primitive-type :Integer,
           :$value? {:$primitive-type :Boolean}},
       :y {:$primitive-type :Integer,
           :$value? {:$primitive-type :Boolean}},
       :$constraints {"c1" (and #r [:x :$value?] #r [:y :$value?])}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool,
                           $_2 :Int,
                           $_3 :Bool},
                    :constraints #{(and $_1 $_3)}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 #{true false},
                      $_2 [-1000 1000],
                      $_3 #{true false}},
       :sym-to-path [$_0 [:x] $_1 [:x :$value?] $_2 [:y] $_3 [:y :$value?]]}
      {:$instance-of :ws/A$v1,
       :x {:$ranges #{[-1000 1001]},
           :$value? true},
       :y {:$ranges #{[-1000 1001]},
           :$value? true}}])

    (check-propagate
     {:ws/A$v1 {:fields {:x [:Maybe :Integer],
                         :y [:Maybe :Integer]},
                :constraints [["c1" '(if-value x false (if-value y true false))]]}}
     {:$instance-of :ws/A$v1}
     [{:$instance-of :ws/A$v1,
       :x {:$primitive-type :Integer,
           :$value? {:$primitive-type :Boolean}},
       :y {:$primitive-type :Integer,
           :$value? {:$primitive-type :Boolean}},
       :$constraints {"c1" (and (not #r [:x :$value?]) #r [:y :$value?])}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool,
                           $_2 :Int,
                           $_3 :Bool},
                    :constraints #{(and (not $_1) $_3)}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 #{true false},
                      $_2 [-1000 1000],
                      $_3 #{true false}},
       :sym-to-path [$_0 [:x] $_1 [:x :$value?] $_2 [:y] $_3 [:y :$value?]]}
      {:$instance-of :ws/A$v1,
       :x {:$value? false},
       :y {:$ranges #{[-1000 1001]},
           :$value? true}}])

    (check-propagate
     {:ws/A$v1 {:fields {:x [:Maybe :Integer],
                         :y [:Maybe :Integer]},
                :constraints [["c1" '(if-value x false (if-value y false true))]]}}
     {:$instance-of :ws/A$v1}
     [{:$instance-of :ws/A$v1,
       :x {:$primitive-type :Integer,
           :$value? {:$primitive-type :Boolean}},
       :y {:$primitive-type :Integer,
           :$value? {:$primitive-type :Boolean}},
       :$constraints {"c1" (and (not #r [:x :$value?]) (not #r [:y :$value?]))}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool,
                           $_2 :Int,
                           $_3 :Bool},
                    :constraints #{(and (not $_1) (not $_3))}},
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
     [{:$instance-of :ws/B$v1,
       :a {:$value? true,
           :$instance-of :ws/A$v1,
           :x {:$enum #{1 3 2},
               :$value? true,
               :$primitive-type :Integer},
           :$constraints
           {"c2" (or (= #r [:a :x] 1) (= #r [:a :x] 2) (= #r [:a :x] 3))}},
       :y {:$enum #{12 14 10},
           :$primitive-type :Integer,
           :$value? {:$primitive-type :Boolean}},
       :$constraints {"c1" (or (not #r [:y :$value?])
                               (= 15 (+ #r [:a :x] #r [:y]))),
                      "c3" (or (not #r [:y :$value?])
                               (or (= #r [:y] 10) (= #r [:y] 12) (= #r [:y] 14)))}}
      {:choco-spec {:vars {$_0 :Bool,
                           $_1 :Int,
                           $_2 :Bool,
                           $_3 :Int,
                           $_4 :Bool},
                    :constraints #{(or (not $_4) (= 15 (+ $_1 $_3)))
                                   (or (= $_1 1) (= $_1 2) (= $_1 3))
                                   (or (not $_4)
                                       (or (= $_3 10) (= $_3 12) (= $_3 14)))}},
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
     [{:$instance-of :ws/B$v1,
       :a {:$value? true,
           :$instance-of :ws/A$v1,
           :x {:$enum #{1 3 2},
               :$value? true,
               :$primitive-type :Integer},
           :$constraints
           {"c2" (or (= #r [:a :x] 1) (= #r [:a :x] 2) (= #r [:a :x] 3))}},
       :y {:$enum #{12 14 10},
           :$primitive-type :Integer,
           :$value? {:$primitive-type :Boolean}},
       :$constraints {"c1" (and #r [:y :$value?] (= 15 (+ #r [:a :x] #r [:y]))),
                      "c3" (or (not #r [:y :$value?])
                               (or (= #r [:y] 10) (= #r [:y] 12) (= #r [:y] 14)))}}
      {:choco-spec {:vars {$_0 :Bool,
                           $_1 :Int,
                           $_2 :Bool,
                           $_3 :Int,
                           $_4 :Bool},
                    :constraints #{(and $_4 (= 15 (+ $_1 $_3)))
                                   (or (= $_1 1) (= $_1 2) (= $_1 3))
                                   (or (not $_4)
                                       (or (= $_3 10) (= $_3 12) (= $_3 14)))}},
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
     [{:$instance-of :ws/B$v1,
       :a {:$instance-of :ws/A$v1,
           :x {:$value? true,
               :$primitive-type :Integer},
           :$value? {:$primitive-type :Boolean}}}
      {:choco-spec {:vars {$_0 :Bool,
                           $_1 :Int,
                           $_2 :Bool},
                    :constraints #{}},
       :choco-bounds {$_0 #{true false},
                      $_1 [-1000 1000],
                      $_2 true},
       :sym-to-path [$_0 [:a :$value?] $_1 [:a :x] $_2 [:a :x :$value?]]}
      {:$instance-of :ws/B$v1,
       :a {:$instance-of :ws/A$v1,
           :x {:$ranges #{[-1000 1001]}}}}])

    (check-propagate {:ws/B$v1 {:fields {:a [:Maybe [:Instance :ws/A$v1]]},
                                :constraints [["c1" '(if-value a false true)]]},
                      :ws/A$v1 {:fields {}}}
                     {:$instance-of :ws/B$v1}
                     [{:$instance-of :ws/B$v1,
                       :a {:$instance-of :ws/A$v1,
                           :$value? {:$primitive-type :Boolean}},
                       :$constraints {"c1" (not #r [:a :$value?])}}
                      {:choco-spec {:vars {$_0 :Bool},
                                    :constraints #{(not $_0)}},
                       :choco-bounds {$_0 #{true false}},
                       :sym-to-path [$_0 [:a :$value?]]}
                      {:$instance-of :ws/B$v1,
                       :a {:$value? false}}])

    (check-propagate {:ws/B$v1 {:fields {:a [:Maybe [:Instance :ws/A$v1]]},
                                :constraints [["c1" '(if-value a true false)]]},
                      :ws/A$v1 {:fields {}}}
                     {:$instance-of :ws/B$v1}
                     [{:$instance-of :ws/B$v1,
                       :a {:$instance-of :ws/A$v1,
                           :$value? {:$primitive-type :Boolean}},
                       :$constraints {"c1" #r [:a :$value?]}}
                      {:choco-spec {:vars {$_0 :Bool},
                                    :constraints #{$_0}},
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
     [{:$instance-of :ws/B$v1,
       :a {:$instance-of :ws/A$v1,
           :x {:$primitive-type :Integer,
               :$value? {:$primitive-type :Boolean}},
           :$value? {:$primitive-type :Boolean}},
       :$constraints {"c1" (and #r [:a :$value?] (not #r [:a :x :$value?]))}}
      {:choco-spec {:vars {$_0 :Bool,
                           $_1 :Int,
                           $_2 :Bool},
                    :constraints #{(and $_0 (not $_2))}},
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
     [{:$instance-of :ws/B$v1,
       :a {:$instance-of :ws/A$v1,
           :x {:$primitive-type :Integer,
               :$value? {:$primitive-type :Boolean}},
           :$value? {:$primitive-type :Boolean}},
       :$constraints {"c1" (and #r [:a :$value?] #r [:a :x :$value?])}}
      {:choco-spec {:vars {$_0 :Bool,
                           $_1 :Int,
                           $_2 :Bool},
                    :constraints #{(and $_0 $_2)}},
       :choco-bounds {$_0 #{true false},
                      $_1 [-1000 1000],
                      $_2 #{true false}},
       :sym-to-path [$_0 [:a :$value?] $_1 [:a :x] $_2 [:a :x :$value?]]}
      {:$instance-of :ws/B$v1,
       :a {:$instance-of :ws/A$v1,
           :x {:$ranges #{[-1000 1001]},
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
     [{:$instance-of :ws/B$v1,
       :b false,
       :a {:$instance-of :ws/A$v1,
           :x {:$primitive-type :Integer,
               :$value? {:$primitive-type :Boolean}},
           :$value? {:$primitive-type :Boolean}},
       :$constraints {"c1" (or #r [:b]
                               (and #r [:a :$value?] #r [:a :x :$value?]))}}
      {:choco-spec {:vars {$_0 :Bool,
                           $_1 :Bool,
                           $_2 :Int,
                           $_3 :Bool},
                    :constraints #{(or $_0 (and $_1 $_3))}},
       :choco-bounds {$_0 false,
                      $_1 #{true false},
                      $_2 [-1000 1000],
                      $_3 #{true false}},
       :sym-to-path [$_0 [:b] $_1 [:a :$value?] $_2 [:a :x] $_3 [:a :x :$value?]]}
      {:$instance-of :ws/B$v1,
       :b false,
       :a {:$instance-of :ws/A$v1,
           :x {:$ranges #{[-1000 1001]},
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
     [{:$instance-of :ws/B$v1,
       :b false,
       :a {:$instance-of :ws/A$v1,
           :x {:$primitive-type :Integer,
               :$value? {:$primitive-type :Boolean}},
           :$value? {:$primitive-type :Boolean}},
       :$constraints {"c1" (or #r [:b]
                               (and #r [:a :$value?]
                                    (and #r [:a :x :$value?] (> #r [:a :x] 20))))}}
      {:choco-spec {:vars {$_0 :Bool,
                           $_1 :Bool,
                           $_2 :Int,
                           $_3 :Bool},
                    :constraints #{(or $_0 (and $_1 (and $_3 (> $_2 20))))}},
       :choco-bounds {$_0 false,
                      $_1 #{true false},
                      $_2 [-1000 1000],
                      $_3 #{true false}},
       :sym-to-path [$_0 [:b] $_1 [:a :$value?] $_2 [:a :x] $_3 [:a :x :$value?]]}
      {:$instance-of :ws/B$v1,
       :b false,
       :a {:$instance-of :ws/A$v1,
           :x {:$ranges #{[21 1001]},
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
     [{:$instance-of :ws/B$v1,
       :b false,
       :a {:$instance-of :ws/A$v1,
           :x {:$primitive-type :Integer,
               :$value? {:$primitive-type :Boolean}},
           :$value? {:$primitive-type :Boolean}},
       :$constraints {"c1" (or #r [:b]
                               (and #r [:a :$value?]
                                    (and #r [:a :x :$value?] (> #r [:a :x] 20))))}}
      {:choco-spec {:vars {$_0 :Bool,
                           $_1 :Bool,
                           $_2 :Int,
                           $_3 :Bool},
                    :constraints #{(or $_0 (and $_1 (and $_3 (> $_2 20))))}},
       :choco-bounds {$_0 false,
                      $_1 #{true false},
                      $_2 [-1000 1000],
                      $_3 #{true false}},
       :sym-to-path [$_0 [:b] $_1 [:a :$value?] $_2 [:a :x] $_3 [:a :x :$value?]]}
      {:$instance-of :ws/B$v1,
       :b false,
       :a {:$instance-of :ws/A$v1,
           :x {:$ranges #{[21 1001]},
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
     [{:$instance-of :ws/B$v1,
       :a {:x 30,
           :$instance-of :ws/A$v1,
           :$value? {:$primitive-type :Boolean}},
       :b {:$value? true,
           :$primitive-type :Boolean},
       :$constraints {"c1" (or #r [:b] #r [:a :$value?])}}
      {:choco-spec {:vars {$_0 :Bool,
                           $_1 :Int,
                           $_2 :Bool,
                           $_3 :Bool},
                    :constraints #{(or $_2 $_0)}},
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
     [{:$instance-of :ws/B$v1,
       :a {:x 10,
           :$instance-of :ws/A$v1,
           :$value? {:$primitive-type :Boolean}},
       :b {:$value? true,
           :$primitive-type :Boolean},
       :$constraints {"c1" #r [:b]}}
      {:choco-spec {:vars {$_0 :Bool,
                           $_1 :Int,
                           $_2 :Bool,
                           $_3 :Bool},
                    :constraints #{$_2}},
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

    (check-propagate
     {:ws/X$v1 {:fields {:x :Integer,
                         :y :Integer},
                :constraints [["c1" '(= 10 (+ x y))]]},
      :ws/A$v1 {:fields {:a :Integer},
                :constraints [["c2"
                               '(let [q {:$type :ws/X$v1,
                                         :x a,
                                         :y 6}]
                                  true)]]}}
     {:$instance-of :ws/A$v1}
     [{:$instance-of :ws/A$v1,
       :a {:$value? true,
           :$primitive-type :Integer},
       :$constraints {},
       :$instance-literals {"c2$0" {:x {:$expr #r [:a]},
                                    :y 6,
                                    :$instance-literal-type :ws/X$v1,
                                    :$constraints {"c1" (= 10 (+ #r [:a] 6))}}}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(= 10 (+ $_0 6))}},
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
                               '(let [q {:$type :ws/X$v1,
                                         :x a,
                                         :y 7}]
                                  true)]]}}
     {:$instance-of :ws/A$v1}
     [{:$instance-of :ws/A$v1,
       :a {:$value? true,
           :$primitive-type :Integer},
       :$constraints {},
       :$instance-literals {"c2$0" {:x {:$expr #r [:a]},
                                    :y 7,
                                    :$instance-literal-type :ws/X$v1,
                                    :$constraints {"c1" (= 10 (+ #r [:a] 7))}}}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(= 10 (+ $_0 7))}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 true},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
      {:$instance-of :ws/A$v1,
       :a 3}])

    (check-propagate
     {:ws/X$v1 {:fields {:x :Integer,
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
     [{:$instance-of :ws/A$v1,
       :a {:$value? true,
           :$primitive-type :Integer},
       :$constraints {},
       :$instance-literals
       {"c2$0" {:x {:$expr #r [:a]},
                :y 7,
                :$instance-literal-type :ws/X$v1,
                :$constraints {"c1" (= 10 (+ #r [:a] 7))}},
        "c2$1" {:q {:$expr #instance {:x #r [:a], :y 7, :$type :ws/X$v1}},
                :$instance-literal-type :ws/P$v1}}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(= 10 (+ $_0 7))}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 true},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
      {:$instance-of :ws/A$v1,
       :a 3}])

    (check-propagate
     {:ws/X$v1 {:fields {:x :Integer,
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
     [{:$instance-of :ws/A$v1,
       :a 4,
       :$constraints {},
       :$instance-literals
       {"c2$0" {:x {:$expr #r [:a]},
                :y 7,
                :$instance-literal-type :ws/X$v1,
                :$constraints {"c1" false}},
        "c2$1" {:q {:$expr #instance {:x #r [:a], :y 7, :$type :ws/X$v1}},
                :$instance-literal-type :ws/P$v1}}}
      {:choco-spec {:vars {$_0 :Int},
                    :constraints #{false}},
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
     [{:$instance-of :ws/A$v1,
       :a 3,
       :b {:$value? true,
           :$primitive-type :Integer},
       :$constraints {},
       :$instance-literals
       {"c2$0" {:x {:$expr #r [:a]},
                :y {:$expr (+ #r [:a] #r [:b] 2)},
                :$instance-literal-type :ws/X$v1,
                :$constraints {"c1" (= 10 (+ 3 (+ 3 #r [:b] 2)))}},
        "c2$1"
        {:q
         {:$expr
          #instance {:x #r [:a], :y (+ #r [:a] #r [:b] 2), :$type :ws/X$v1}},
         :$instance-literal-type :ws/P$v1}}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Int,
                           $_2 :Bool},
                    :constraints #{(= 10 (+ 3 (+ 3 $_1 2)))}},
       :choco-bounds {$_0 3,
                      $_1 [-1000 1000],
                      $_2 true},
       :sym-to-path [$_0 [:a] $_1 [:b] $_2 [:b :$value?]]}
      {:$instance-of :ws/A$v1,
       :a 3,
       :b 2}])

    (stanza "if-value-let")

    (check-propagate
     {:ws/X$v1 {:fields {:x [:Maybe :Integer]},
                :constraints [["c1" '(if-value-let [p x] (> p 10) true)]]}}
     {:$instance-of :ws/X$v1}
     [{:$instance-of :ws/X$v1,
       :x {:$primitive-type :Integer,
           :$value? {:$primitive-type :Boolean}},
       :$constraints {"c1" (or (not #r [:x :$value?]) (> #r [:x] 10))}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(or (not $_1) (> $_0 10))}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 #{true false}},
       :sym-to-path [$_0 [:x] $_1 [:x :$value?]]}
      {:$instance-of :ws/X$v1,
       :x {:$ranges #{[-1000 1001]}}}])

    (check-propagate
     {:ws/X$v1 {:fields {:x [:Maybe :Integer]},
                :constraints [["c1" '(if-value-let [p x] (> p 10) true)]]}}
     {:$instance-of :ws/X$v1,
      :x 20}
     [{:$instance-of :ws/X$v1,
       :x 20,
       :$constraints {}}
      {:choco-spec {:vars {$_0 :Int},
                    :constraints #{}},
       :choco-bounds {$_0 20},
       :sym-to-path [$_0 [:x]]}
      {:$instance-of :ws/X$v1,
       :x 20}])

    (check-propagate
     {:ws/X$v1 {:fields {:x [:Maybe :Integer]},
                :constraints [["c1" '(if-value-let [p x] (> p 10) true)]]}}
     {:$instance-of :ws/X$v1,
      :x 10}
     [{:$instance-of :ws/X$v1,
       :x 10,
       :$constraints {"c1" false}}
      {:choco-spec {:vars {$_0 :Int},
                    :constraints #{false}},
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
     [{:$instance-of :ws/A$v1,
       :a {:$value? true,
           :$primitive-type :Integer},
       :$constraints {},
       :$instance-literals {"c2$0" {:x {:$expr #r [:a]},
                                    :y 6,
                                    :$instance-literal-type :ws/X$v1,
                                    :$constraints {"c1" (= 10 (+ #r [:a] 6))}}}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(= 10 (+ $_0 6))}},
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
     [{:$instance-of :ws/A$v1,
       :a {:$value? true,
           :$primitive-type :Integer},
       :$constraints {"c2" false},
       :$instance-literals {"c2$0" {:x {:$expr #r [:a]},
                                    :y 6,
                                    :$instance-literal-type :ws/X$v1,
                                    :$constraints {"c1" (= 10 (+ #r [:a] 6))}}}}
      {:choco-spec {:vars {$_0 :Int,
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
     [{:$instance-of :ws/A$v1,
       :a {:$value? false,
           :$primitive-type :Integer},
       :$constraints {"c2" #r [:a :$value?]},
       :$instance-literals
       {"c2$0" {:x {:$expr #r [:a]},
                :y 7,
                :$instance-literal-type :ws/X$v1,
                :$constraints {"c1" (or (not #r [:a :$value?])
                                        (= 10 (+ #r [:a] 7)))}},
        "c2$1" {:q {:$expr #instance {:x #r [:a], :y 7, :$type :ws/X$v1}},
                :$instance-literal-type :ws/P$v1}}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{$_1 (or (not $_1) (= 10 (+ $_0 7)))}},
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
     [{:$instance-of :ws/A$v1,
       :a 4,
       :$constraints {},
       :$instance-literals
       {"c2$0" {:x {:$expr #r [:a]},
                :y 7,
                :$instance-literal-type :ws/X$v1,
                :$constraints {"c1" false}},
        "c2$1" {:q {:$expr #instance {:x #r [:a], :y 7, :$type :ws/X$v1}},
                :$instance-literal-type :ws/P$v1}}}
      {:choco-spec {:vars {$_0 :Int},
                    :constraints #{false}},
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
     [{:$instance-of :ws/A$v1,
       :a 3,
       :$constraints {},
       :$instance-literals
       {"c2$0" {:x {:$expr #r [:a]},
                :y 7,
                :$instance-literal-type :ws/X$v1,
                :$constraints {}},
        "c2$1" {:q {:$expr #instance {:x #r [:a], :y 7, :$type :ws/X$v1}},
                :$instance-literal-type :ws/P$v1}}}
      {:choco-spec {:vars {$_0 :Int},
                    :constraints #{}},
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
     [{:$instance-of :ws/A$v1,
       :a 3,
       :$constraints {},
       :$instance-literals
       {"c2$0" {:x {:$expr #r [:a]},
                :y 7,
                :$instance-literal-type :ws/X$v1,
                :$constraints {}},
        "c2$1" {:q {:$expr #instance {:x #r [:a], :y 7, :$type :ws/X$v1}},
                :$instance-literal-type :ws/P$v1}}}
      {:choco-spec {:vars {$_0 :Int},
                    :constraints #{}},
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
     [{:$instance-of :ws/A$v1,
       :a {:$primitive-type :Integer,
           :$value? {:$primitive-type :Boolean}},
       :$constraints {"c2" #r [:a :$value?]},
       :$instance-literals
       {"c2$0" {:x {:$expr #r [:a]},
                :y 7,
                :$instance-literal-type :ws/X$v1,
                :$constraints {"c1" (or (not #r [:a :$value?])
                                        (= 10 (+ #r [:a] 7)))}},
        "c2$1" {:q {:$expr #instance {:x #r [:a], :y 7, :$type :ws/X$v1}},
                :$instance-literal-type :ws/P$v1}}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{$_1 (or (not $_1) (= 10 (+ $_0 7)))}},
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
     [{:$instance-of :ws/A$v1,
       :a {:$primitive-type :Integer,
           :$value? {:$primitive-type :Boolean}},
       :$constraints {"c2" (not #r [:a :$value?])},
       :$instance-literals
       {"c2$0" {:x {:$expr #r [:a]},
                :y 7,
                :$instance-literal-type :ws/X$v1,
                :$constraints {"c1" (or (not #r [:a :$value?])
                                        (= 10 (+ #r [:a] 7)))}},
        "c2$1" {:q {:$expr #instance {:x #r [:a], :y 7, :$type :ws/X$v1}},
                :$instance-literal-type :ws/P$v1}}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(not $_1) (or (not $_1) (= 10 (+ $_0 7)))}},
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
     [{:$instance-of :ws/A$v1,
       :a {:$primitive-type :Integer,
           :$value? {:$primitive-type :Boolean}},
       :$constraints {},
       :$instance-literals
       {"c2$0" {:x {:$expr #r [:a]},
                :y 7,
                :$instance-literal-type :ws/X$v1,
                :$constraints {"c1" (or (not #r [:a :$value?])
                                        (= 10 (+ #r [:a] 7)))}},
        "c2$1" {:q {:$expr #instance {:x #r [:a], :y 7, :$type :ws/X$v1}},
                :$instance-literal-type :ws/P$v1}}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(or (not $_1) (= 10 (+ $_0 7)))}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 #{true false}},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
      {:$instance-of :ws/A$v1,
       :a {:$ranges #{[-1000 1001]}}}])

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
     [{:$instance-of :ws/A$v1,
       :a {:$value? true,
           :$primitive-type :Integer},
       :$constraints {},
       :$instance-literals {"c2$0" {:x {:$expr #r [:a]},
                                    :y 6,
                                    :$instance-literal-type :ws/X$v1,
                                    :$constraints {"c1" (or (not (> #r [:a] 0))
                                                            (= 10
                                                               (+ #r [:a] 6)))}}}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(or (not (> $_0 0)) (= 10 (+ $_0 6)))}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 true},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
      {:$instance-of :ws/A$v1,
       :a {:$ranges #{[-1000 5]}}}])

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
     [{:$instance-of :ws/A$v1,
       :a -4,
       :$constraints {},
       :$instance-literals {"c2$0" {:x {:$expr #r [:a]},
                                    :y 6,
                                    :$instance-literal-type :ws/X$v1,
                                    :$constraints {"c1" (not (> #r [:a] 0))}},
                            "c2$1" {:x {:$expr (- 0 #r [:a])},
                                    :y 7,
                                    :$instance-literal-type :ws/X$v1,
                                    :$constraints {"c1" (> #r [:a] 0)}}}}
      {:choco-spec {:vars {$_0 :Int},
                    :constraints #{(> $_0 0) (not (> $_0 0))}},
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
     [{:$instance-of :ws/A$v1,
       :a -3,
       :$constraints {},
       :$instance-literals {"c2$0" {:x {:$expr #r [:a]},
                                    :y 6,
                                    :$instance-literal-type :ws/X$v1,
                                    :$constraints {"c1" (not (> #r [:a] 0))}},
                            "c2$1" {:x {:$expr (- 0 #r [:a])},
                                    :y 7,
                                    :$instance-literal-type :ws/X$v1,
                                    :$constraints {}}}}
      {:choco-spec {:vars {$_0 :Int},
                    :constraints #{(not (> $_0 0))}},
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
     [{:$instance-of :ws/A$v1,
       :a {:$value? true,
           :$primitive-type :Integer},
       :$constraints {},
       :$instance-literals {"c2$0" {:x {:$expr #r [:a]},
                                    :y 6,
                                    :$instance-literal-type :ws/X$v1,
                                    :$constraints {"c1" (or (not #r [:a :$value?])
                                                            (= 10
                                                               (+ #r [:a] 6)))}}}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(or (not $_1) (= 10 (+ $_0 6)))}},
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
     [{:$instance-of :ws/A$v1,
       :a 3,
       :$constraints {},
       :$instance-literals {"c2$0" {:x {:$expr #r [:a]},
                                    :y 6,
                                    :$instance-literal-type :ws/X$v1,
                                    :$constraints {"c1" (not #r [:a :$value?])}}}}
      {:choco-spec {:vars {$_0 :Int},
                    :constraints #{(not true)}},
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
     [{:$instance-of :ws/A$v1,
       :a 4,
       :$constraints {},
       :$instance-literals {"c2$0" {:x {:$expr #r [:a]},
                                    :y 6,
                                    :$instance-literal-type :ws/X$v1,
                                    :$constraints {}}}}
      {:choco-spec {:vars {$_0 :Int},
                    :constraints #{}},
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
     [{:$instance-of :ws/A$v1,
       :a {:$value? false,
           :$primitive-type :Integer},
       :$constraints {},
       :$instance-literals {"c2$0" {:x {:$expr #r [:a]},
                                    :y 6,
                                    :$instance-literal-type :ws/X$v1,
                                    :$constraints {"c1" (or (not #r [:a :$value?])
                                                            (= 10
                                                               (+ #r [:a] 6)))}}}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(or (not $_1) (= 10 (+ $_0 6)))}},
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
     [{:$instance-of :ws/A$v1,
       :a {:$value? false,
           :$primitive-type :Integer},
       :$constraints {"c2" #r [:a :$value?]},
       :$instance-literals {"c2$0" {:x {:$expr #r [:a]},
                                    :y 6,
                                    :$instance-literal-type :ws/X$v1,
                                    :$constraints {"c1" (or (not #r [:a :$value?])
                                                            (= 10
                                                               (+ #r [:a] 6)))}}}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{$_1 (or (not $_1) (= 10 (+ $_0 6)))}},
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
     [{:$instance-of :ws/A$v1,
       :a {:$primitive-type :Integer,
           :$value? {:$primitive-type :Boolean}},
       :$constraints {"c2" #r [:a :$value?]},
       :$instance-literals {"c2$0" {:x {:$expr #r [:a]},
                                    :y 6,
                                    :$instance-literal-type :ws/X$v1,
                                    :$constraints {"c1" (or (not #r [:a :$value?])
                                                            (= 10
                                                               (+ #r [:a] 6)))}}}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{$_1 (or (not $_1) (= 10 (+ $_0 6)))}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 #{true false}},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
      {:$instance-of :ws/A$v1,
       :a 4}])

    (stanza "deep let")

    (check-propagate {:ws/A$v1 {:fields {:x :Boolean,
                                         :y :Boolean},
                                :constraints [["c1"
                                               '(let [a x]
                                                  (let [b a]
                                                    (let [c b]
                                                      (if c true y))))]]}}
                     {:$instance-of :ws/A$v1,
                      :x false}
                     [{:$instance-of :ws/A$v1,
                       :x false,
                       :y {:$value? true,
                           :$primitive-type :Boolean},
                       :$constraints {"c1" (or #r [:x] #r [:y])}}
                      {:choco-spec {:vars {$_0 :Bool,
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

    (check-propagate {:ws/A$v1 {:fields {:x :Boolean,
                                         :y :Boolean},
                                :constraints [["c1"
                                               '(let [a x]
                                                  (let [b a]
                                                    (let [c b]
                                                      (if c true y))))]]}}
                     {:$instance-of :ws/A$v1,
                      :x true}
                     [{:$instance-of :ws/A$v1,
                       :x true,
                       :y {:$value? true,
                           :$primitive-type :Boolean},
                       :$constraints {}}
                      {:choco-spec {:vars {$_0 :Bool,
                                           $_1 :Bool,
                                           $_2 :Bool},
                                    :constraints #{}},
                       :choco-bounds {$_0 true,
                                      $_1 #{true false},
                                      $_2 true},
                       :sym-to-path [$_0 [:x] $_1 [:y] $_2 [:y :$value?]]}
                      {:$instance-of :ws/A$v1,
                       :x true}])

    (stanza "complex let")

    (check-propagate
     {:ws/A$v1 {:fields {:x :Integer,
                         :y :Integer},
                :constraints [["c1"
                               '(let [a (+ x y)]
                                  (< a 20))]]}}
     {:$instance-of :ws/A$v1}
     [{:$instance-of :ws/A$v1,
       :x {:$value? true,
           :$primitive-type :Integer},
       :y {:$value? true,
           :$primitive-type :Integer},
       :$constraints {"c1" (< (+ #r [:x] #r [:y]) 20)}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool,
                           $_2 :Int,
                           $_3 :Bool},
                    :constraints #{(< (+ $_0 $_2) 20)}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 true,
                      $_2 [-1000 1000],
                      $_3 true},
       :sym-to-path [$_0 [:x] $_1 [:x :$value?] $_2 [:y] $_3 [:y :$value?]]}
      {:$instance-of :ws/A$v1,
       :x {:$ranges #{[-1000 1001]}},
       :y {:$ranges #{[-1000 1001]}}}])

    (check-propagate
     {:ws/A$v1 {:fields {:x :Integer,
                         :y :Integer},
                :constraints [["c1"
                               '(let [a (+ x y)]
                                  (< a 20))]]}}
     {:$instance-of :ws/A$v1,
      :x {:$ranges #{[15 30]}},
      :y {:$ranges #{[0 1000]}}}
     [{:$instance-of :ws/A$v1,
       :x {:$enum #{20 27 24 15 21 22 29 28 25 17 23 19 26 16 18},
           :$value? true,
           :$primitive-type :Integer},
       :y {:$ranges #{[0 1000]},
           :$value? true,
           :$primitive-type :Integer},
       :$constraints {"c1" (< (+ #r [:x] #r [:y]) 20)}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool,
                           $_2 :Int,
                           $_3 :Bool},
                    :constraints #{(< (+ $_0 $_2) 20)}},
       :choco-bounds {$_0 #{20 27 24 15 21 22 29 28 25 17 23 19 26 16 18},
                      $_1 true,
                      $_2 [0 1000],
                      $_3 true},
       :sym-to-path [$_0 [:x] $_1 [:x :$value?] $_2 [:y] $_3 [:y :$value?]]}
      {:$instance-of :ws/A$v1,
       :x {:$enum #{15 17 19 16 18}},
       :y {:$enum #{0 1 4 3 2}}}])

    (check-propagate
     {:ws/A$v1 {:fields {:x :Integer,
                         :y :Integer},
                :constraints [["c1"
                               '(let [a x]
                                  (let [b y]
                                    (< (let [c (+ a b)]
                                         (let [d (inc c)]
                                           (- d 1)))
                                       20)))]]}}
     {:$instance-of :ws/A$v1,
      :x {:$ranges #{[15 30]}},
      :y {:$ranges #{[0 1000]}}}
     [{:$instance-of :ws/A$v1,
       :x {:$enum #{20 27 24 15 21 22 29 28 25 17 23 19 26 16 18},
           :$value? true,
           :$primitive-type :Integer},
       :y {:$ranges #{[0 1000]},
           :$value? true,
           :$primitive-type :Integer},
       :$constraints {"c1" (< (- (inc (+ #r [:x] #r [:y])) 1) 20)}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool,
                           $_2 :Int,
                           $_3 :Bool},
                    :constraints #{(< (- (inc (+ $_0 $_2)) 1) 20)}},
       :choco-bounds {$_0 #{20 27 24 15 21 22 29 28 25 17 23 19 26 16 18},
                      $_1 true,
                      $_2 [0 1000],
                      $_3 true},
       :sym-to-path [$_0 [:x] $_1 [:x :$value?] $_2 [:y] $_3 [:y :$value?]]}
      {:$instance-of :ws/A$v1,
       :x {:$enum #{15 17 19 16 18}},
       :y {:$enum #{0 1 4 3 2}}}])

    (stanza "inequality edges")

    (check-propagate
     {:ws/A$v1 {:fields {:x :Integer,
                         :y :Integer,
                         :a :Integer,
                         :b :Integer},
                :constraints [["cx" '(and (> x 0) (< x 5))]
                              ["cy" '(and (>= y 0) (< y 5))]
                              ["ca" '(and (> a 0) (<= a 5))]
                              ["cb" '(and (>= b 0) (<= b 5))]]}}
     {:$instance-of :ws/A$v1}
     [{:$instance-of :ws/A$v1,
       :x {:$enum #{1 4 3 2},
           :$value? true,
           :$primitive-type :Integer},
       :y {:$enum #{0 1 4 3 2},
           :$value? true,
           :$primitive-type :Integer},
       :a {:$enum #{1 4 3 2 5},
           :$value? true,
           :$primitive-type :Integer},
       :b {:$enum #{0 1 4 3 2 5},
           :$value? true,
           :$primitive-type :Integer},
       :$constraints {"cx" (and (> #r [:x] 0) (< #r [:x] 5)),
                      "cy" (and (>= #r [:y] 0) (< #r [:y] 5)),
                      "ca" (and (> #r [:a] 0) (<= #r [:a] 5)),
                      "cb" (and (>= #r [:b] 0) (<= #r [:b] 5))}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool,
                           $_2 :Int,
                           $_3 :Bool,
                           $_4 :Int,
                           $_5 :Bool,
                           $_6 :Int,
                           $_7 :Bool},
                    :constraints
                    #{(and (> $_4 0) (<= $_4 5)) (and (>= $_2 0) (< $_2 5))
                      (and (> $_0 0) (< $_0 5)) (and (>= $_6 0) (<= $_6 5))}},
       :choco-bounds {$_0 #{1 4 3 2},
                      $_1 true,
                      $_2 #{0 1 4 3 2},
                      $_3 true,
                      $_4 #{1 4 3 2 5},
                      $_5 true,
                      $_6 #{0 1 4 3 2 5},
                      $_7 true},
       :sym-to-path [$_0 [:x] $_1 [:x :$value?] $_2 [:y] $_3 [:y :$value?] $_4 [:a]
                     $_5 [:a :$value?] $_6 [:b] $_7 [:b :$value?]]}
      {:$instance-of :ws/A$v1,
       :x {:$enum #{1 4 3 2}},
       :y {:$enum #{0 1 4 3 2}},
       :a {:$enum #{1 4 3 2 5}},
       :b {:$enum #{0 1 4 3 2 5}}}])

    (check-propagate
     {:ws/A$v1 {:fields {:x :Integer,
                         :y :Integer,
                         :a :Integer,
                         :b :Integer},
                :constraints [["cx" '(and (> x -800) (< x 900))]
                              ["cy" '(and (>= y -800) (< y 900))]
                              ["ca" '(and (> a -800) (<= a 900))]
                              ["cb" '(and (>= b -800) (<= b 900))]]}}
     {:$instance-of :ws/A$v1}
     [{:$instance-of :ws/A$v1,
       :x {:$ranges #{[-799 900]},
           :$value? true,
           :$primitive-type :Integer},
       :y {:$ranges #{[-800 900]},
           :$value? true,
           :$primitive-type :Integer},
       :a {:$ranges #{[-799 901]},
           :$value? true,
           :$primitive-type :Integer},
       :b {:$ranges #{[-800 901]},
           :$value? true,
           :$primitive-type :Integer},
       :$constraints {"cx" (and (> #r [:x] -800) (< #r [:x] 900)),
                      "cy" (and (>= #r [:y] -800) (< #r [:y] 900)),
                      "ca" (and (> #r [:a] -800) (<= #r [:a] 900)),
                      "cb" (and (>= #r [:b] -800) (<= #r [:b] 900))}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool,
                           $_2 :Int,
                           $_3 :Bool,
                           $_4 :Int,
                           $_5 :Bool,
                           $_6 :Int,
                           $_7 :Bool},
                    :constraints #{(and (>= $_6 -800) (<= $_6 900))
                                   (and (>= $_2 -800) (< $_2 900))
                                   (and (> $_4 -800) (<= $_4 900))
                                   (and (> $_0 -800) (< $_0 900))}},
       :choco-bounds {$_0 [-799 900],
                      $_1 true,
                      $_2 [-800 900],
                      $_3 true,
                      $_4 [-799 901],
                      $_5 true,
                      $_6 [-800 901],
                      $_7 true},
       :sym-to-path [$_0 [:x] $_1 [:x :$value?] $_2 [:y] $_3 [:y :$value?] $_4 [:a]
                     $_5 [:a :$value?] $_6 [:b] $_7 [:b :$value?]]}
      {:$instance-of :ws/A$v1,
       :x {:$ranges #{[-799 900]}},
       :y {:$ranges #{[-800 900]}},
       :a {:$ranges #{[-799 901]}},
       :b {:$ranges #{[-800 901]}}}])

    (stanza "valid?")

    (check-propagate
     {:ws/X$v1 {:fields {:x :Integer,
                         :y :Integer},
                :constraints [["c1" '(= 10 (+ x y))]]},
      :ws/A$v1 {:fields {:a :Integer},
                :constraints [["c2"
                               '(valid? {:$type :ws/X$v1,
                                         :x a,
                                         :y 6})]]}}
     {:$instance-of :ws/A$v1}
     [{:$instance-of :ws/A$v1,
       :a {:$value? true,
           :$primitive-type :Integer},
       :$constraints {"c2" (= 10 (+ #r [:a] 6))},
       :$instance-literals {"c2$0" {:x {:$expr #r [:a]},
                                    :y 6,
                                    :$instance-literal-type :ws/X$v1,
                                    :$valid-var-path [:$valid-vars "c2$1"]}},
       :$valid-vars {"c2$1" (= 10 (+ #r [:a] 6))}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(= 10 (+ $_0 6))}},
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
                               '(valid? {:$type :ws/X$v1,
                                         :x a,
                                         :y 6})]]}}
     {:$instance-of :ws/A$v1,
      :a 4}
     [{:$instance-of :ws/A$v1,
       :a 4,
       :$constraints {"c2" true},
       :$instance-literals {"c2$0" {:x {:$expr #r [:a]},
                                    :y 6,
                                    :$instance-literal-type :ws/X$v1,
                                    :$valid-var-path [:$valid-vars "c2$1"]}},
       :$valid-vars {"c2$1" true}}
      {:choco-spec {:vars {$_0 :Int},
                    :constraints #{true}},
       :choco-bounds {$_0 4},
       :sym-to-path [$_0 [:a]]}
      {:$instance-of :ws/A$v1,
       :a 4}])

    (check-propagate
     {:ws/X$v1 {:fields {:x :Integer,
                         :y :Integer},
                :constraints [["c1" '(= 10 (+ x y))]]},
      :ws/A$v1 {:fields {:a :Integer},
                :constraints [["c2"
                               '(valid? {:$type :ws/X$v1,
                                         :x a,
                                         :y 6})]]}}
     {:$instance-of :ws/A$v1,
      :a 5}
     [{:$instance-of :ws/A$v1,
       :a 5,
       :$constraints {"c2" false},
       :$instance-literals {"c2$0" {:x {:$expr #r [:a]},
                                    :y 6,
                                    :$instance-literal-type :ws/X$v1,
                                    :$valid-var-path [:$valid-vars "c2$1"]}},
       :$valid-vars {"c2$1" false}}
      {:choco-spec {:vars {$_0 :Int},
                    :constraints #{false}},
       :choco-bounds {$_0 5},
       :sym-to-path [$_0 [:a]]} {:$contradiction? true}])

    (stanza "valid")

    (check-propagate
     {:ws/X$v1 {:fields {:x :Integer,
                         :y :Integer},
                :constraints [["c1" '(= 10 (+ x y))]]},
      :ws/A$v1 {:fields {:a :Integer},
                :constraints [["c2"
                               '(< (if-value-let [i
                                                  (valid {:$type :ws/X$v1,
                                                          :x a,
                                                          :y 6})]
                                                 (get i :x)
                                                 12)
                                   11)]]}}
     {:$instance-of :ws/A$v1}
     [{:$instance-of :ws/A$v1,
       :a {:$value? true,
           :$primitive-type :Integer},
       :$constraints {"c2" (< (if (= 10 (+ #r [:a] 6)) #r [:a] 12) 11)},
       :$instance-literals {"c2$0" {:x {:$expr #r [:a]},
                                    :y 6,
                                    :$instance-literal-type :ws/X$v1,
                                    :$valid-var-path [:$valid-vars "c2$1"]}},
       :$valid-vars {"c2$1" (= 10 (+ #r [:a] 6))}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(< (if (= 10 (+ $_0 6)) $_0 12) 11)}},
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
                               '(> (if-value-let [i
                                                  (valid {:$type :ws/X$v1,
                                                          :x a,
                                                          :y 6})]
                                                 (get i :x)
                                                 12)
                                   11)]]}}
     {:$instance-of :ws/A$v1}
     [{:$instance-of :ws/A$v1,
       :a {:$value? true,
           :$primitive-type :Integer},
       :$constraints {"c2" (> (if (= 10 (+ #r [:a] 6)) #r [:a] 12) 11)},
       :$instance-literals {"c2$0" {:x {:$expr #r [:a]},
                                    :y 6,
                                    :$instance-literal-type :ws/X$v1,
                                    :$valid-var-path [:$valid-vars "c2$1"]}},
       :$valid-vars {"c2$1" (= 10 (+ #r [:a] 6))}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(> (if (= 10 (+ $_0 6)) $_0 12) 11)}},
       :choco-bounds {$_0 [-1000 1000],
                      $_1 true},
       :sym-to-path [$_0 [:a] $_1 [:a :$value?]]}
      {:$instance-of :ws/A$v1,
       :a {:$ranges #{[-1000 1001]}}}
      "Ideally this would omit the value '4', but we are not doing split ranges in choco."])

    (check-propagate
     {:ws/X$v1 {:fields {:x :Integer,
                         :y :Integer},
                :constraints [["c1" '(= 1006 (+ x y))]]},
      :ws/A$v1 {:fields {:a :Integer},
                :constraints [["c2"
                               '(> (if-value-let [i
                                                  (valid {:$type :ws/X$v1,
                                                          :x a,
                                                          :y 6})]
                                                 (- (get i :x) 996)
                                                 12)
                                   11)]]}}
     {:$instance-of :ws/A$v1}
     [{:$instance-of :ws/A$v1,
       :a {:$value? true,
           :$primitive-type :Integer},
       :$constraints {"c2" (> (if (= 1006 (+ #r [:a] 6)) (- #r [:a] 996) 12) 11)},
       :$instance-literals {"c2$0" {:x {:$expr #r [:a]},
                                    :y 6,
                                    :$instance-literal-type :ws/X$v1,
                                    :$valid-var-path [:$valid-vars "c2$1"]}},
       :$valid-vars {"c2$1" (= 1006 (+ #r [:a] 6))}}
      {:choco-spec {:vars {$_0 :Int,
                           $_1 :Bool},
                    :constraints #{(> (if (= 1006 (+ $_0 6)) (- $_0 996) 12) 11)}},
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
                               '(> (if-value-let [i
                                                  (valid {:$type :ws/X$v1,
                                                          :x a,
                                                          :y 6})]
                                                 (get i :x)
                                                 12)
                                   11)]]}}
     {:$instance-of :ws/A$v1,
      :a 4}
     [{:$instance-of :ws/A$v1,
       :a 4,
       :$constraints {"c2" (> (if true 4 12) 11)},
       :$instance-literals {"c2$0" {:x {:$expr #r [:a]},
                                    :y 6,
                                    :$instance-literal-type :ws/X$v1,
                                    :$valid-var-path [:$valid-vars "c2$1"]}},
       :$valid-vars {"c2$1" true}}
      {:choco-spec {:vars {$_0 :Int},
                    :constraints #{(> (if true 4 12) 11)}},
       :choco-bounds {$_0 4},
       :sym-to-path [$_0 [:a]]} {:$contradiction? true}])])

(deftest test-propagate
  (doseq [t test-data]
    (eval t)))

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

;; (spit "target/propagate-test.edn" (str "[" (apply str (map format-code @test-atom)) "]"))

;; (time (run-tests))
