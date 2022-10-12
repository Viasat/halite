;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.synthesize
  (:require [clojure.set :as set :refer [subset?]]
            [clojure.walk :refer [postwalk]]
            [schema.core :as s]
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
              (if (symbol? x)
                (symbol (name x))
                x))
            form))

(s/defschema SpecFnMap
  {:valid?-fn (s/pred seq?)
   :refine-fns {s/Keyword (s/pred seq?)}})

(s/defn synthesize-spec :- {s/Keyword SpecFnMap}
  [spec-map [spec-id spec]]
  {spec-id
   {:valid?-fn
    (strip-ns
     `(fn [$this]
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
                            `(if-let [refined (refine* ~spec-id ~to-spec-id $this)]
                               (valid?* ~to-spec-id refined)
                               true))))))))
    :refine-fns
    (merge

     ;; direct refinements
     (->> (:refines-to spec)
          (map (fn [[to-spec-id {:keys [expr]}]]
                 [to-spec-id
                  (strip-ns
                   `(fn [{:keys ~(vec (map symbol (keys (:spec-vars spec))))}]
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
                     `(fn [$this]
                        (->> $this
                             (refine* ~spec-id ~(second refinement-path))
                             (refine* ~(second refinement-path) ~(last refinement-path)))))]))
            (into {}))))}})

(defn synthesize
  "Formal definition of some halite concepts. Return an exprs-data"
  [spec-map]
  (->> spec-map
       (map (partial synthesize-spec spec-map))
       (apply merge)))

(s/defn exprs-interface
  "Returns Clojure code that when evaluated returns a map of our public
  interface functions."
  [exprs-data :- {s/Keyword SpecFnMap}]
  (strip-ns
   `(letfn [(get-exprs [] ~exprs-data)

            (get-refine-fn* [from-spec-id to-spec-id]
              (get-in (get-exprs) [from-spec-id :refine-fns to-spec-id]))
            (refine* [from-spec-id to-spec-id instance]
              ((get-in (get-exprs) [from-spec-id :refine-fns to-spec-id]) instance))
            (valid?* [spec-id instance]
              ((get-in (get-exprs) [spec-id :valid?-fn]) instance))]

      {:refine-to (fn [inst spec-id]
                    ;; No need to follow path here -- it will have already been flattened out
                    ;; TODO use some-> ???
                    (if-let [refine-fn (get-refine-fn* (:$type inst) spec-id)]
                      (if-let [inst-refined (refine-fn inst)]
                        (if (valid?* spec-id inst-refined)
                          inst-refined
                          (throw (ex-info "Refined instance is invalid" {})))
                        (throw (ex-info "Refinement return :Unset" {})))
                      (throw (ex-info "No path at all"))))
       :refines-to? (fn [inst spec-id]
                      (if-let [refine-fn (get-refine-fn* (:$type inst) spec-id)]
                        (if-let [inst-refined (refine-fn inst)]
                          (valid?* spec-id inst-refined)
                          false)
                        false))
       :valid (fn [inst]
                ;; TODO support arbitrary expression, not just instances? Requires calling eval here?
                (when (valid?* (:$type inst) inst)
                  inst))
       :valid? (fn [inst]
                 ;; TODO support arbitrary expression, not just instances? Requires calling eval here?
                 (valid?* (:$type inst) inst))
       :validate-instance (fn [inst]
                            ;; TODO support arbitrary expression, not just instances? Requires calling eval here?
                            (if (valid?* (:$type inst) inst)
                              inst
                              (throw (ex-info "Invalid instance" {:instance inst}))))})))

(def this-ns *ns*)

(defn- eval-form [form]
  (binding [*ns* this-ns]
    (eval form)))

(defn synth-eval [exprs-data expr]
  (let [{:keys [validate-instance refine-to refines-to? valid valid?]}
        (eval-form (exprs-interface exprs-data))]
    (cond
      (map? expr) (validate-instance expr)
      :default (apply (condp = (first expr)
                        'refine-to refine-to
                        'refines-to? refines-to?
                        'valid valid
                        'valid? valid?) (rest expr)))))
