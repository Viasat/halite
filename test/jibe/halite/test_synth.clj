;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.test-synth
  (:require
   [jibe.halite.synth :refer [synth spec-map-eval] :as synth]
   [jibe.halite :as halite]
   [jibe.halite.halite-envs :as halite-envs]
   [clojure.test :as t :refer [deftest is]]
   [schema.test :refer [validate-schemas]]))

(clojure.test/use-fixtures :once validate-schemas)

(deftest thing1
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

(deftest test-constraints
  (is (= {:spec/A {:valid?-fn '(fn [$this]
                                 (and
                                  (map? $this)
                                  (= :spec/A (:$type $this))
                                  (= #{:$type :x} (set (keys $this)))
                                  (and (user-eval $this '(> x 12)))))
                   :refine-fns {}}}
         (synth {:spec/A {:spec-vars {:x "Integer"}
                          :constraints [["c" '(> x 12)]]}})))
  (is (= {:spec/A {:valid?-fn '(fn [$this]
                                 (and
                                  (map? $this)
                                  (= :spec/A (:$type $this))
                                  (= #{:$type :x} (set (keys $this)))
                                  (and (user-eval $this '(> x 12))
                                       (user-eval $this '(< x 24)))))
                   :refine-fns {}}}
         (synth {:spec/A {:spec-vars {:x "Integer"}
                          :constraints [["c" '(> x 12)]
                                        ["c2" '(< x 24)]]}}))))

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
                                        :expr '{:$type :spec/B}}}}}
        senv (update-vals spec-map #(merge {:spec-vars {}, :refines-to {}, :constraints []} %))]

    (is (= {:$type :spec/A}
           (spec-map-eval spec-map
                          (partial halite-user-eval senv)
                          '(refine-to {:$type :spec/C} :spec/A))))))

;; (t/run-tests)
