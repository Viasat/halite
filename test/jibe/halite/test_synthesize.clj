;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.test-synthesize
  (:require
   [jibe.halite.synthesize :refer [synthesize] :as synth]
   [clojure.test :as t :refer [deftest is]]
   [schema.test :refer [validate-schemas]]))

(clojure.test/use-fixtures :once validate-schemas)

(deftest thing1
  (let [exprs-data
        (synthesize
         {:spec/A {}
          :spec/B {:refines-to {:spec/A {:name "as_A"
                                         :expr '{:$type :spec/A}}}}})]

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
                     :refine-fns {:spec/A '(fn [{:keys []}]
                                             {:$type :spec/A})}}}
           exprs-data))

    (is (= {:$type :spec/B}
           (synth/synth-eval exprs-data '{:$type :spec/B})))

    (is (= {:$type :spec/A}
           (synth/synth-eval exprs-data '(refine-to {:$type :spec/B} :spec/A))))

    (is (= true
           (synth/synth-eval exprs-data '(refines-to? {:$type :spec/B} :spec/A))))

    (is (= {:$type :spec/B}
           (synth/synth-eval exprs-data '(valid {:$type :spec/B}))))

    (is (= true
           (synth/synth-eval exprs-data '(valid? {:$type :spec/B}))))))

(deftest test-transitive-refinements
  (let [exprs-data
        (synthesize
         {:spec/A {}
          :spec/B {:refines-to {:spec/A {:name "as_A"
                                         :expr '{:$type :spec/A}}}}
          :spec/C {:refines-to {:spec/B {:name "as_B"
                                         :expr '{:$type :spec/B}}}}})]

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
                     :refine-fns {:spec/A '(fn [{:keys []}]
                                             {:$type :spec/A})}}
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
                                  :spec/B '(fn [{:keys []}]
                                             {:$type :spec/B})}}}
           exprs-data))
    (is (= {:$type :spec/A}
           (synth/synth-eval exprs-data '(refine-to {:$type :spec/C} :spec/A))))
    (is (= true
           (synth/synth-eval exprs-data '(refines-to? {:$type :spec/C} :spec/A))))))

(deftest test-constraints
  (is (= {:spec/A {:valid?-fn '(fn [$this]
                                 (and
                                  (map? $this)
                                  (= :spec/A (:$type $this))
                                  (= #{:$type :x} (set (keys $this)))
                                  (let [{:keys [x]} $this]
                                    (and (> x 12)))))
                   :refine-fns {}}}
         (synthesize {:spec/A {:spec-vars {:x "Integer"}
                               :constraints [["c" '(> x 12)]]}})))
  (is (= {:spec/A {:valid?-fn '(fn [$this]
                                 (and
                                  (map? $this)
                                  (= :spec/A (:$type $this))
                                  (= #{:$type :x} (set (keys $this)))
                                  (let [{:keys [x]} $this]
                                    (and (> x 12)
                                         (< x 24)))))
                   :refine-fns {}}}
         (synthesize {:spec/A {:spec-vars {:x "Integer"}
                               :constraints [["c" '(> x 12)]
                                             ["c2" '(< x 24)]]}}))))

(deftest test-optional-field
  (let [exprs-data (synthesize {:spec/A {:spec-vars {:x [:Maybe "Integer"]}}})]
    (is (= {:spec/A {:valid?-fn '(fn [$this]
                                   (and
                                    (map? $this)
                                    (= :spec/A (:$type $this))
                                    (subset? (set (keys $this)) #{:$type :x})
                                    (subset? #{:$type} (set (keys $this)))))
                     :refine-fns {}}}
           exprs-data))
    (is (= {:$type :spec/A}
           (synth/synth-eval exprs-data '{:$type :spec/A})))
    (is (= {:$type :spec/A :x 1}
           (synth/synth-eval exprs-data '{:$type :spec/A :x 1})))
    (is (nil?
         (synth/synth-eval exprs-data '(valid {:$type :spec/A :x 1 :y 0})))))

  (let [exprs-data (synthesize {:spec/A {:spec-vars {:x [:Maybe "Integer"]
                                                     :y "Integer"}}})]
    (is (= {:spec/A {:valid?-fn '(fn [$this]
                                   (and
                                    (map? $this)
                                    (= :spec/A (:$type $this))
                                    (subset? (set (keys $this)) #{:$type :x :y})
                                    (subset? #{:$type :y} (set (keys $this)))))
                     :refine-fns {}}}
           exprs-data))
    (is (= {:$type :spec/A :y 0}
           (synth/synth-eval exprs-data '{:$type :spec/A :y 0})))
    (is (= {:$type :spec/A :x 1 :y 0}
           (synth/synth-eval exprs-data {:$type :spec/A :x 1 :y 0})))
    (is (nil?
         (synth/synth-eval exprs-data '(valid {:$type :spec/A :x 1 :y 0 :z 2}))))))

;; (t/run-tests)
