;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-flow-return-path
  (:require [clojure.test :refer :all]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.fog :as fog]
            [com.viasat.halite.flow-return-path :as flow-return-path]
            [com.viasat.halite.op-flower :as op-flower]
            [com.viasat.halite.flow-expr :as flow-expr]
            [schema.test])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(def instance-literal-atom (atom {}))

(def fixtures (join-fixtures [(fn [f]
                                (reset! instance-literal-atom (atom {}))
                                (f))
                              schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(def flow-expr-empty-context {:spec-env (envs/spec-env {})
                              :spec-type-env (envs/type-env {})
                              :type-env (envs/type-env {})
                              :env (envs/env {})
                              :path []
                              :counter-atom (atom -1)
                              :instance-literal-f (fn [path instance-literal]
                                                    (swap! instance-literal-atom assoc-in path instance-literal))
                              :guards []})

(def empty-context {:env (envs/env {})
                    :path []
                    :lower-f (fn [expr]
                               (flow-expr/lower-expr flow-expr-empty-context expr))})

(deftest test-return-path
  (is (= true
         (flow-return-path/return-path empty-context 1)))

  (is (= true
         (flow-return-path/return-path empty-context
                                       '(if x 9 2))))

  (is (= #r [:x]
         (flow-return-path/return-path empty-context
                                       '(when x 1))))

  (is (= '(> #r [:x] 5)
         (flow-return-path/return-path (assoc empty-context
                                              :env (envs/env {'x 100}))
                                       '(when (> x 5) 1))))

  (is (= true
         (flow-return-path/return-path (assoc empty-context
                                              :env (envs/env {'x 100}))
                                       'x)))

  (is (= #r [:x :$value?]
         (flow-return-path/return-path empty-context
                                       'x)))

  ;; boolean ops

  (is (= '(if #r [:x] #r [:y :$value?] #r [:z :$value?])
         (flow-return-path/return-path empty-context
                                       '(if x y z))))

  (is (= '(and #r [:x] #r [:y :$value?])
         (flow-return-path/return-path empty-context
                                       '(when x y))))

  ;; if-value-let

  (is (= '(if #r [:x :$value?] #r [:y :$value?] #r [:z :$value?])
         (flow-return-path/return-path empty-context
                                       '(if-value-let [a x] y z))))

  (is (= '(if #r [:x :$value?] #r [:x :$value?] #r [:z :$value?])
         (flow-return-path/return-path empty-context
                                       '(if-value-let [a x] a z))))

  (is (= '(or #r [:x :$value?] #r [:z :$value?])
         (flow-return-path/return-path empty-context
                                       '(if-value-let [a x] (inc a) z))))

  ;; when-value-let

  (is (= '(and #r [:x :$value?] #r [:y :$value?])
         (flow-return-path/return-path empty-context
                                       '(when-value-let [a x] y))))

  (is (= '(and #r [:x :$value?] #r [:x :$value?])
         (flow-return-path/return-path empty-context
                                       '(when-value-let [a x] a))))

  ;; get
  (is (= #r [:x :$value?]
         (flow-return-path/return-path empty-context
                                       '(get {:$type :spec/A$v1 :a x} :a))))

  (is (= #r [:x :$value?]
         (flow-return-path/return-path empty-context
                                       '(get (get {:$type :spec/A$v1 :a {:$type :spec/B$v1 :b x}} :a) :b))))

  (is (= true
         (flow-return-path/return-path empty-context
                                       '(get (get {:$type :spec/A$v1 :a {:$type :spec/B$v1 :b 5}} :a) :b))))

  (is (= '(or #r [:y] #r [:x :$value?])
         (flow-return-path/return-path empty-context
                                       '(get (get {:$type :spec/A$v1
                                                   :a (if y
                                                        {:$type :spec/B$v1 :b 5}
                                                        {:$type :spec/B$v1 :b x})} :a) :b))))

  (is (= '(and #r [:x :$value?] #r [:x :a :$value?])
         (flow-return-path/return-path empty-context
                                       '(get x :a)))))

;; (run-tests)
