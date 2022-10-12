;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.synthesize
  (:require [clojure.set :as set]
            [clojure.walk :refer [postwalk]]
            [loom.alg]
            [loom.graph]))

;; Ideas, options...
;; Insert literal 'eval' in the generated code to avoid the hidden update-eval step ???
;; Lift out repeated pattern forms ???
;;  - (invoke $exprs [:spec/A :refines-to :spec/B] $this)   (does the get-in, eval, and call)
;;  - (refine $exprs :spec/A :spec/B $this) also (predicate $exprs :spec/A inst)
;;  - others?

(defn strip-ns [form]
  (postwalk (fn [x]
              (if (and (symbol? x)
                       (not (#{"clojure.set"} (namespace x)))
                       (not (#{"refine*"} (name x))))
                (symbol (name x))
                x))
            form))

(defn refine* [$exprs from-spec-id to-spec-id instance]
  ((get-in $exprs [from-spec-id :refines-to to-spec-id]) $exprs instance))

(defn synthesize-spec [spec-map [spec-id spec]]
  [spec-id
   {:predicate
    (strip-ns
     `(fn [$exprs $this]
        (and (map? $this)
             (= ~spec-id (:$type $this))
             ;; TODO: handle optionals
             ~@(let [mandatory-spec-vars (->> (:spec-vars spec)
                                              (remove (fn [[k v]]
                                                        (and (vector? v)
                                                             (= :Maybe (first v)))))
                                              keys)]
                 (if (= (count mandatory-spec-vars) (count (:spec-vars spec)))
                   `[(= ~(into #{:$type} (keys (:spec-vars spec))) (set (keys $this)))]
                   [`(set/subset? (set (keys $this))
                                  ~(into #{:$type} (keys (:spec-vars spec))))
                    `(set/subset? ~(into #{:$type} mandatory-spec-vars)
                                  (set (keys $this)))]))

             ;; constraints

             ~@(if (:constraints spec)
                 `[(let [{:keys ~(vec (map symbol (keys (:spec-vars spec))))} $this]
                     (and
                      ~@(->> (:constraints spec)
                             (map (fn [[_ expr]]
                                    expr)))))]
                 [])

             ;; non-inverted refinements
             ~@(->> (:refines-to spec)
                    (remove :inverted)
                    (map (fn [[to-spec-id {:keys [expr]}]]
                           (strip-ns
                            `(if-let [refined (refine* $exprs ~spec-id ~to-spec-id $this)]
                               ((get-in $exprs [~to-spec-id :predicate]) $exprs refined)
                               true))))))))
    :refines-to
    (merge

     ;; direct refinements
     (->> (:refines-to spec)
          (map (fn [[to-spec-id {:keys [expr]}]]
                 [to-spec-id
                  (strip-ns
                   `(fn [$exprs {:keys ~(vec (map symbol (keys (:spec-vars spec))))}]
                      ~expr))]))
          (into {}))

     ;; transitive refinements
     (let [refines-to-graph (loom.graph/digraph (-> spec-map
                                                    (update-vals #(->> %
                                                                       :refines-to
                                                                       keys))))]
       (->> (keys spec-map)
            (map (partial loom.alg/shortest-path refines-to-graph spec-id))
            (filter #(< 2 (count %))) ;; just multi-step refinements
            (map (fn [refinement-path]
                   [(last refinement-path)
                    (strip-ns
                     `(fn [$exprs $this]
                        (->> $this
                             (refine* $exprs ~spec-id ~(second refinement-path))
                             (refine* $exprs ~(second refinement-path) ~(last refinement-path)))))]))
            (into {}))))}])

(defn synthesize
  "Formal of definition of some halite concepts. Return an exprs-data"
  [spec-map]
  (->> spec-map
       (map (partial synthesize-spec spec-map))
       (into {})))

(defn- compile-exprs [exprs-data]
  (let [$exprs (-> exprs-data
                   (update-vals
                    (fn [{:keys [predicate refines-to]}]
                      {:predicate (eval predicate)
                       :refines-to (update-vals refines-to eval)})))]
    {:refine-to (fn [inst spec-id]
                  ;; No need to follow path here -- it will have already been flattened out
                  ;; TODO use some-> ???
                  (if-let [refinement (get-in $exprs [(:$type inst) :refines-to spec-id])]
                    (if-let [inst-refined (refinement $exprs inst)]
                      (if ((get-in $exprs [spec-id :predicate]) $exprs inst-refined)
                        inst-refined
                        (throw (ex-info "Refined instance is invalid" {})))
                      (throw (ex-info "Refinement return :Unset" {})))
                    (throw (ex-info "No path at all"))))
     :refines-to? (fn [inst spec-id]
                    (if-let [refinement (get-in $exprs [(:$type inst) :refines-to spec-id])]
                      (if-let [inst-refined (refinement $exprs inst)]
                        ((get-in $exprs [spec-id :predicate]) $exprs inst-refined)
                        false)
                      false))
     :valid (fn [inst]
              ;; TODO support arbitrary expression, not just instances? Requires calling eval here?
              (when ((get-in $exprs [(:$type inst) :predicate]) $exprs inst)
                inst))
     :valid? (fn [inst]
               ;; TODO support arbitrary expression, not just instances? Requires calling eval here?
               ((get-in $exprs [(:$type inst) :predicate]) $exprs inst))
     :validate-instance (fn [inst]
                          ;; TODO support arbitrary expression, not just instances? Requires calling eval here?
                          (if ((get-in $exprs [(:$type inst) :predicate]) $exprs inst)
                            inst
                            (throw (ex-info "Invalid instance" {:instance inst}))))}))

(defn synth-eval [exprs-data expr]
  (let [{:keys [validate-instance refine-to refines-to? valid valid?]}
        (compile-exprs exprs-data)]
    (cond
      (map? expr) (validate-instance expr)
      :default (apply (condp = (first expr)
                        'refine-to refine-to
                        'refines-to? refines-to?
                        'valid valid
                        'valid? valid?) (rest expr)))))
