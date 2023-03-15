;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-synth
  (:require [clojure.test :refer :all]
            [com.viasat.halite :as halite]
            [com.viasat.halite.synth :as synth]
            [schema.test :as schema.test])
  (:import [clojure.lang ExceptionInfo]))

(use-fixtures :once schema.test/validate-schemas)

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
                                        ;; intrinsic refinement
                                        (if-let [refined (refine* :spec/B :spec/A $this)]
                                          (valid?* :spec/A refined)
                                          true)))
                     :refine-fns {:spec/A '(fn [$this]
                                             (user-eval $this '{:$type :spec/A}))}}}
           (synth/synth spec-map)))

    (is (= {:$type :spec/B}
           (synth/spec-map-eval spec-map '{:$type :spec/B})))

    (is (= {:$type :spec/A}
           (synth/spec-map-eval spec-map '(refine-to {:$type :spec/B} :spec/A))))

    (is (= true
           (synth/spec-map-eval spec-map '(refines-to? {:$type :spec/B} :spec/A))))

    (is (= {:$type :spec/B}
           (synth/spec-map-eval spec-map '(valid {:$type :spec/B}))))

    (is (= true
           (synth/spec-map-eval spec-map '(valid? {:$type :spec/B}))))))

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
           (synth/synth spec-map)))
    (is (= {:$type :spec/A}
           (synth/spec-map-eval spec-map '(refine-to {:$type :spec/C} :spec/A))))
    (is (= true
           (synth/spec-map-eval spec-map '(refines-to? {:$type :spec/C} :spec/A))))))

(deftest test-transitive-refinements-with-invalid-extrinsic-step
  (let [spec-map
        {:spec/Object {}
         :spec/Falsey {:fields {:f :Boolean}
                       :constraints #{{:name "cf" :expr '(not f)}}
                       :refines-to {:spec/Object {:name "as_Object"
                                                  :expr '{:$type :spec/Object}}}}
         :spec/Truthy {:fields {:t :Boolean}
                       :constraints #{{:name "ct" :expr 't}}
                       :refines-to {:spec/Falsey {:name "as_Falsey"
                                                  :expr '{:$type :spec/Falsey
                                                          :f t}
                                                  :extrinsic? true}}}}]
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
           (synth/synth spec-map)))
    (is (thrown-with-msg? ExceptionInfo #"failed in refinement"
                          (synth/spec-map-eval spec-map '(refine-to {:$type :spec/Truthy :t true} :spec/Object))))
    (is (thrown-with-msg? ExceptionInfo #"instance is invalid"
                          (synth/spec-map-eval spec-map '(refine-to {:$type :spec/Truthy :t true} :spec/Falsey))))))

(deftest test-constraints
  (is (= {:spec/A {:valid?-fn '(fn [$this]
                                 (and
                                  (map? $this)
                                  (= :spec/A (:$type $this))
                                  (= #{:$type :x} (set (keys $this)))
                                  (and (user-eval $this '(> x 12)))))
                   :refine-fns {}}}
         (synth/synth {:spec/A {:fields {:x :Integer}
                                :constraints #{{:name "c" :expr '(> x 12)}}}})))
  (is (= {:spec/A {:valid?-fn '(fn [$this]
                                 (and
                                  (map? $this)
                                  (= :spec/A (:$type $this))
                                  (= #{:$type :x} (set (keys $this)))
                                  (and (user-eval $this '(> x 12))
                                       (user-eval $this '(< x 24)))))
                   :refine-fns {}}}
         (synth/synth {:spec/A {:fields {:x :Integer}
                                :constraints #{{:name "c" :expr '(> x 12)}
                                               {:name "c2" :expr '(< x 24)}}}}))))

(deftest test-optional-field
  (let [spec-map {:spec/A {:fields {:x [:Maybe :Integer]}}}]
    (is (= {:spec/A {:valid?-fn '(fn [$this]
                                   (and
                                    (map? $this)
                                    (= :spec/A (:$type $this))
                                    (subset? (set (keys $this)) #{:$type :x})
                                    (subset? #{:$type} (set (keys $this)))))
                     :refine-fns {}}}
           (synth/synth spec-map)))
    (is (= {:$type :spec/A}
           (synth/spec-map-eval spec-map '{:$type :spec/A})))
    (is (= {:$type :spec/A :x 1}
           (synth/spec-map-eval spec-map '{:$type :spec/A :x 1})))
    (is (nil?
         (synth/spec-map-eval spec-map '(valid {:$type :spec/A :x 1 :y 0})))))

  (let [spec-map {:spec/A {:fields {:x [:Maybe :Integer]
                                    :y :Integer}}}]
    (is (= {:spec/A {:valid?-fn '(fn [$this]
                                   (and
                                    (map? $this)
                                    (= :spec/A (:$type $this))
                                    (subset? (set (keys $this)) #{:$type :x :y})
                                    (subset? #{:$type :y} (set (keys $this)))))
                     :refine-fns {}}}
           (synth/synth spec-map)))
    (is (= {:$type :spec/A :y 0}
           (synth/spec-map-eval spec-map '{:$type :spec/A :y 0})))
    (is (= {:$type :spec/A :x 1 :y 0}
           (synth/spec-map-eval spec-map {:$type :spec/A :x 1 :y 0})))
    (is (nil?
         (synth/spec-map-eval spec-map '(valid {:$type :spec/A :x 1 :y 0 :z 2}))))))

(defn halite-user-eval [spec-map $this expr]
  (let [spec-info (get spec-map (:$type $this))]
    (halite/eval-expr spec-map
                      (halite/type-env-from-spec spec-map spec-info)
                      (halite/env-from-inst spec-info $this)
                      expr)))

(deftest test-halite-evaluator
  (let [spec-map
        {:spec/A {}
         :spec/B {:refines-to {:spec/A {:name "as_A"
                                        :expr '(valid {:$type :spec/A})}}}
         :spec/C {:refines-to {:spec/B {:name "as_B"
                                        :expr '{:$type :spec/B}}}}}]

    (is (= {:$type :spec/A}
           (synth/spec-map-eval spec-map
                                (partial halite-user-eval spec-map)
                                '(refine-to {:$type :spec/C} :spec/A))))))

(defn halite-eval [spec-map expr]
  (halite/eval-expr spec-map
                    (halite/type-env {})
                    (halite/env {})
                    expr))

(defmacro compare-to-halite [spec-map expr]
  `(is (= (try (halite-eval ~spec-map ~expr)
               (catch Throwable t#
                 [:throws]))
          (try (synth/spec-map-eval ~spec-map ~expr)
               (catch Throwable t#
                 [:throws])))))

(deftest test-against-halite
  (let [spec-map {:spec/A {:fields {:a :Boolean}
                           :constraints #{{:name "ca" :expr 'a}}}
                  :spec/B {:fields {:b :Boolean
                                    :c :Boolean}
                           :refines-to {:spec/A {:name "as_A"
                                                 :expr '{:$type :spec/A
                                                         :a (and b c)}}}}
                  :spec/C {:fields {:b :Boolean
                                    :c :Boolean}
                           :refines-to {:spec/A {:name "as_A"
                                                 :expr '{:$type :spec/A
                                                         :a (and b c)}
                                                 :extrinsic? true}}}}]
    (compare-to-halite spec-map '{:$type :spec/B :b true :c false})
    (compare-to-halite spec-map '(refine-to {:$type :spec/B :b true :c false} :spec/A))

    (compare-to-halite spec-map '{:$type :spec/B :b true :c true})
    (compare-to-halite spec-map '(refine-to {:$type :spec/B :b true :c true} :spec/A))

    (compare-to-halite spec-map '{:$type :spec/C :b true :c false})
    (compare-to-halite spec-map '(refine-to {:$type :spec/C :b true :c false} :spec/A))

    (compare-to-halite spec-map '{:$type :spec/C :b true :c true})
    (compare-to-halite spec-map '(refine-to {:$type :spec/C :b true :c true} :spec/A))))

;; TODO: test whether errors in extrinsic refinements are handled properly by refines-to?

;; (run-tests)
