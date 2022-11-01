;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-synth
  (:require [com.viasat.halite.synth :refer [synth spec-map-eval] :as synth]
            [com.viasat.halite :as halite]
            [com.viasat.halite.envs :as halite-envs]
            [clojure.test :as t :refer [deftest is]]
            [schema.test :refer [validate-schemas]])
  (:import [clojure.lang ExceptionInfo]))

(clojure.test/use-fixtures :once validate-schemas)

(deftest test-basic
  (let [spec-map
        {:spec/A {}
         :spec/B {:refines-to {:spec/A {:name "as_A"
                                        :expr '{:$type :spec/A}}}}}]

    (is (= {:spec/A {:valid?-fn '(fn [$this]
                                   (and (map? $this)
                                        (= :spec/A (:$type $this))
                                        (= #{:$type} (set (keys $this)))))
                     :refine-fns {}}
            :spec/B {:valid?-fn '(fn [$this]
                                   (and (map? $this)
                                        (= :spec/B (:$type $this))
                                        (= #{:$type} (set (keys $this)))
                                        ;; non-inverted refinement
                                        (if-let [refined (refine* :spec/B :spec/A $this)]
                                          (valid?* :spec/A refined)
                                          true)))
                     :refine-fns {:spec/A '(fn [$this]
                                             (user-eval $this '{:$type :spec/A}))}}}
           (synth spec-map)))

    (is (= {:$type :spec/B}
           (spec-map-eval spec-map '{:$type :spec/B})))

    (is (= {:$type :spec/A}
           (spec-map-eval spec-map '(refine-to {:$type :spec/B} :spec/A))))

    (is (= true
           (spec-map-eval spec-map '(refines-to? {:$type :spec/B} :spec/A))))

    (is (= {:$type :spec/B}
           (spec-map-eval spec-map '(valid {:$type :spec/B}))))

    (is (= true
           (spec-map-eval spec-map '(valid? {:$type :spec/B}))))))

(deftest test-transitive-refinements
  (let [spec-map
        {:spec/A {}
         :spec/B {:refines-to {:spec/A {:name "as_A"
                                        :expr '{:$type :spec/A}}}}
         :spec/C {:refines-to {:spec/B {:name "as_B"
                                        :expr '{:$type :spec/B}}}}}]

    (is (= {:spec/A {:valid?-fn '(fn [$this]
                                   (and (map? $this)
                                        (= :spec/A (:$type $this))
                                        (= #{:$type} (set (keys $this)))))
                     :refine-fns {}}
            :spec/B {:valid?-fn '(fn [$this]
                                   (and (map? $this)
                                        (= :spec/B (:$type $this))
                                        (= #{:$type} (set (keys $this)))
                                        (if-let [refined (refine* :spec/B :spec/A $this)]
                                          (valid?* :spec/A refined)
                                          true)))
                     :refine-fns {:spec/A '(fn [$this]
                                             (user-eval $this '{:$type :spec/A}))}}
            :spec/C {:valid?-fn '(fn [$this]
                                   (and (map? $this)
                                        (= :spec/C (:$type $this))
                                        (= #{:$type} (set (keys $this)))
                                        (if-let [refined (refine* :spec/C :spec/B $this)]
                                          (valid?* :spec/B refined)
                                          true)))
                     :refine-fns {:spec/A '(fn [$this]
                                             (->> $this
                                                  (refine* :spec/C :spec/B)
                                                  (refine* :spec/B :spec/A)))
                                  :spec/B '(fn [$this]
                                             (user-eval $this '{:$type :spec/B}))}}}
           (synth spec-map)))
    (is (= {:$type :spec/A}
           (spec-map-eval spec-map '(refine-to {:$type :spec/C} :spec/A))))
    (is (= true
           (spec-map-eval spec-map '(refines-to? {:$type :spec/C} :spec/A))))))

(deftest test-transitive-refinements-with-invalid-inverted-step
  (let [spec-map
        {:spec/Object {}
         :spec/Falsey {:spec-vars {:f "Boolean"}
                       :constraints [[:cf '(not f)]]
                       :refines-to {:spec/Object {:name "as_Object"
                                                  :expr '{:$type :spec/Object}}}}
         :spec/Truthy {:spec-vars {:t "Boolean"}
                       :constraints [[:ct 't]]
                       :refines-to {:spec/Falsey {:name "as_Falsey"
                                                  :expr '{:$type :spec/Falsey
                                                          :f t}
                                                  :inverted? true}}}}]
    (is (= '{:spec/Object {:valid?-fn (fn [$this]
                                        (and (map? $this)
                                             (= :spec/Object (:$type $this))
                                             (= #{:$type} (set (keys $this)))))
                           :refine-fns {}}
             :spec/Falsey {:valid?-fn (fn [$this]
                                        (and (map? $this)
                                             (= :spec/Falsey (:$type $this))
                                             (= #{:$type :f} (set (keys $this)))
                                             (and (user-eval $this '(not f)))
                                             (if-let [refined (refine* :spec/Falsey :spec/Object $this)]
                                               (valid?* :spec/Object refined)
                                               true)))
                           :refine-fns {:spec/Object (fn [$this]
                                                       (user-eval $this '{:$type :spec/Object}))}}
             :spec/Truthy {:valid?-fn (fn [$this]
                                        (and (map? $this)
                                             (= :spec/Truthy (:$type $this))
                                             (= #{:$type :t} (set (keys $this)))
                                             (and (user-eval $this 't))))
                           :refine-fns {:spec/Falsey (fn [$this]
                                                       (user-eval $this '{:$type :spec/Falsey, :f t}))
                                        :spec/Object (fn [$this]
                                                       (let [next (refine* :spec/Truthy :spec/Falsey $this)]
                                                         (when (not (valid?* :spec/Falsey next))
                                                           (throw (ex-info "failed in refinement" {})))
                                                         (refine* :spec/Falsey :spec/Object next)))}}}
           (synth spec-map)))
    (is (thrown-with-msg? ExceptionInfo #"failed in refinement"
                          (spec-map-eval spec-map '(refine-to {:$type :spec/Truthy :t true} :spec/Object))))
    (is (thrown-with-msg? ExceptionInfo #"instance is invalid"
                          (spec-map-eval spec-map '(refine-to {:$type :spec/Truthy :t true} :spec/Falsey))))))

(deftest test-constraints
  (is (= {:spec/A {:valid?-fn '(fn [$this]
                                 (and
                                  (map? $this)
                                  (= :spec/A (:$type $this))
                                  (= #{:$type :x} (set (keys $this)))
                                  (and (user-eval $this '(> x 12)))))
                   :refine-fns {}}}
         (synth {:spec/A {:spec-vars {:x "Integer"}
                          :constraints [[:c '(> x 12)]]}})))
  (is (= {:spec/A {:valid?-fn '(fn [$this]
                                 (and
                                  (map? $this)
                                  (= :spec/A (:$type $this))
                                  (= #{:$type :x} (set (keys $this)))
                                  (and (user-eval $this '(> x 12))
                                       (user-eval $this '(< x 24)))))
                   :refine-fns {}}}
         (synth {:spec/A {:spec-vars {:x "Integer"}
                          :constraints [[:c '(> x 12)]
                                        [:c2 '(< x 24)]]}}))))

(deftest test-optional-field
  (let [spec-map {:spec/A {:spec-vars {:x [:Maybe "Integer"]}}}]
    (is (= {:spec/A {:valid?-fn '(fn [$this]
                                   (and
                                    (map? $this)
                                    (= :spec/A (:$type $this))
                                    (subset? (set (keys $this)) #{:$type :x})
                                    (subset? #{:$type} (set (keys $this)))))
                     :refine-fns {}}}
           (synth spec-map)))
    (is (= {:$type :spec/A}
           (spec-map-eval spec-map '{:$type :spec/A})))
    (is (= {:$type :spec/A :x 1}
           (spec-map-eval spec-map '{:$type :spec/A :x 1})))
    (is (nil?
         (spec-map-eval spec-map '(valid {:$type :spec/A :x 1 :y 0})))))

  (let [spec-map {:spec/A {:spec-vars {:x [:Maybe "Integer"]
                                       :y "Integer"}}}]
    (is (= {:spec/A {:valid?-fn '(fn [$this]
                                   (and
                                    (map? $this)
                                    (= :spec/A (:$type $this))
                                    (subset? (set (keys $this)) #{:$type :x :y})
                                    (subset? #{:$type :y} (set (keys $this)))))
                     :refine-fns {}}}
           (synth spec-map)))
    (is (= {:$type :spec/A :y 0}
           (spec-map-eval spec-map '{:$type :spec/A :y 0})))
    (is (= {:$type :spec/A :x 1 :y 0}
           (spec-map-eval spec-map {:$type :spec/A :x 1 :y 0})))
    (is (nil?
         (spec-map-eval spec-map '(valid {:$type :spec/A :x 1 :y 0 :z 2}))))))

(defn halite-user-eval [spec-map $this expr]
  (let [spec-info (get spec-map (:$type $this))]
    (halite/eval-expr spec-map
                      (halite-envs/type-env-from-spec spec-map spec-info)
                      (halite-envs/env-from-inst spec-info $this)
                      expr)))

(deftest test-halite-evaluator
  (let [spec-map
        {:spec/A {}
         :spec/B {:refines-to {:spec/A {:name "as_A"
                                        :expr '(valid {:$type :spec/A})}}}
         :spec/C {:refines-to {:spec/B {:name "as_B"
                                        :expr '{:$type :spec/B}}}}}]

    (is (= {:$type :spec/A}
           (spec-map-eval spec-map
                          (partial halite-user-eval spec-map)
                          '(refine-to {:$type :spec/C} :spec/A))))))

(defn halite-eval [spec-map expr]
  (halite/eval-expr spec-map
                    (halite-envs/type-env {})
                    (halite-envs/env {})
                    expr))

(defmacro compare-to-halite [spec-map expr]
  `(is (= (try (halite-eval ~spec-map ~expr)
               (catch Throwable t#
                 [:throws]))
          (try (spec-map-eval ~spec-map ~expr)
               (catch Throwable t#
                 [:throws])))))

(deftest test-against-halite
  (let [spec-map {:spec/A {:spec-vars {:a "Boolean"}
                           :constraints [[:ca 'a]]}
                  :spec/B {:spec-vars {:b "Boolean"
                                       :c "Boolean"}
                           :refines-to {:spec/A {:name "as_A"
                                                 :expr '{:$type :spec/A
                                                         :a (and b c)}}}}
                  :spec/C {:spec-vars {:b "Boolean"
                                       :c "Boolean"}
                           :refines-to {:spec/A {:name "as_A"
                                                 :expr '{:$type :spec/A
                                                         :a (and b c)}
                                                 :inverted? true}}}}]
    (compare-to-halite spec-map '{:$type :spec/B :b true :c false})
    (compare-to-halite spec-map '(refine-to {:$type :spec/B :b true :c false} :spec/A))

    (compare-to-halite spec-map '{:$type :spec/B :b true :c true})
    (compare-to-halite spec-map '(refine-to {:$type :spec/B :b true :c true} :spec/A))

    (compare-to-halite spec-map '{:$type :spec/C :b true :c false})
    (compare-to-halite spec-map '(refine-to {:$type :spec/C :b true :c false} :spec/A))

    (compare-to-halite spec-map '{:$type :spec/C :b true :c true})
    (compare-to-halite spec-map '(refine-to {:$type :spec/C :b true :c true} :spec/A))))

;; (t/run-tests)
