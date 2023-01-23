;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.synth
  (:require [clojure.set :as set :refer [subset?]]
            [clojure.walk :refer [postwalk]]
            [loom.alg]
            [loom.graph]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

;; user-eval
;; - will be given a Clojure map (a candidate instance) and only and exactly the
;;   expressions given as refinements and constraints in the specs.
;; - for constraints, must return a boolean; for refinements must return a Clojure map or nil

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

(s/defn synth-spec :- {s/Keyword SpecFnMap}
  [spec-map [spec-id spec]]
  {spec-id
   {:valid?-fn
    (strip-ns
     `(fn [$this]
        (and (map? $this)
             (= ~spec-id (:$type $this))
             ;; TODO: handle optionals
             ~@(let [mandatory-fields (->> (:fields spec)
                                           (remove (fn [[k v]]
                                                     (and (vector? v)
                                                          (= :Maybe (first v)))))
                                           keys)]
                 (if (= (count mandatory-fields) (count (:fields spec)))
                   `[(= ~(into #{:$type} (keys (:fields spec))) (set (keys $this)))]
                   [`(set/subset? (set (keys $this))
                                  ~(into #{:$type} (keys (:fields spec))))
                    `(set/subset? ~(into #{:$type} mandatory-fields)
                                  (set (keys $this)))]))

             ;; constraints
             ~@(if (:constraints spec)
                 `[(and
                    ~@(->> (:constraints spec)
                           (sort-by :name)
                           (map (fn [{:keys [expr]}]
                                  `(user-eval $this '~expr)))))]
                 [])

             ;; intrinsic refinements
             ~@(->> (:refines-to spec)
                    (remove (comp :extrinsic? val))
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
                   `(fn [$this] (user-eval $this '~expr)))]))
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
                        ~(if (get-in spec-map [spec-id :refines-to (second refinement-path) :extrinsic?])
                           `(let [next (refine* ~spec-id ~(second refinement-path) $this)]
                              (when (not (valid?* ~(second refinement-path) next))
                                (throw (ex-info "failed in refinement" {})))
                              (refine* ~(second refinement-path) ~(last refinement-path) next))
                           `(->> $this
                                 (refine* ~spec-id ~(second refinement-path))
                                 (refine* ~(second refinement-path) ~(last refinement-path))))))]))
            (into {}))))}})

(defn synth
  "Formal definition of some halite concepts. Return an exprs-data"
  [spec-map]
  (->> spec-map
       (map (partial synth-spec spec-map))
       (apply merge)))

(s/defn exprs-interface
  "Returns Clojure code that when evaluated returns a map of our public
  interface functions."
  [exprs-data :- {s/Keyword SpecFnMap}]
  (strip-ns
   `(fn [user-eval]
      (letfn [(get-exprs [] ~exprs-data)

              (get-refine-fn* [from-spec-id to-spec-id]
                (get-in (get-exprs) [from-spec-id :refine-fns to-spec-id]))
              (refine* [from-spec-id to-spec-id instance]
                ((get-in (get-exprs) [from-spec-id :refine-fns to-spec-id]) instance))
              (valid?* [spec-id instance]
                ((get-in (get-exprs) [spec-id :valid?-fn]) instance))]

        {'refine-to (fn [inst spec-id]
                      ;; No need to follow path here -- it will have already been flattened out
                      ;; TODO use some-> ???
                      (if-let [refine-fn (get-refine-fn* (:$type inst) spec-id)]
                        (if-let [inst-refined (refine-fn inst)]
                          (if (valid?* spec-id inst-refined)
                            inst-refined
                            (throw (ex-info "Refined instance is invalid"
                                            {:invalid-instance inst-refined})))
                          (throw (ex-info "Refinement return :Unset" {})))
                        (throw (ex-info "No path at all"))))
         'refines-to? (fn [inst spec-id]
                        (if-let [refine-fn (get-refine-fn* (:$type inst) spec-id)]
                          (if-let [inst-refined (refine-fn inst)]
                            (valid?* spec-id inst-refined)
                            false)
                          false))
         'valid (fn [inst]
                  ;; TODO support arbitrary expression, not just instances? Requires calling eval here?
                  (when (valid?* (:$type inst) inst)
                    inst))
         'valid? (fn [inst]
                   ;; TODO support arbitrary expression, not just instances? Requires calling eval here?
                   (valid?* (:$type inst) inst))
         'validate-instance (fn [inst]
                              ;; TODO support arbitrary expression, not just instances? Requires calling eval here?
                              (if (valid?* (:$type inst) inst)
                                inst
                                (throw (ex-info "Invalid instance" {:instance inst}))))}))))

(defn clj-user-eval-form [spec-map $this expr]
  (strip-ns
   `(let [{:keys ~(vec (map symbol (keys (:fields (spec-map (:$type $this))))))} ~$this]
      ~expr)))

(def this-ns *ns*)

(defn eval-form
  "eval's the given form in this namespace so that unqualified symbols have a
  stable meaning. This is particulary important with forms generated by
  strip-ns."
  [form]
  (binding [*ns* this-ns]
    (eval form)))

(defn clj-user-eval [spec-map $this expr]
  (let [form (clj-user-eval-form spec-map $this expr)]
    (try
      (eval-form form)
      (catch Exception ex
        (ex-info (str "Exception evaluting user form: " (.getMessage ex) "\n" (pr-str form))
                 {:form form} ex)))))

(defn spec-map-eval
  ([spec-map expr]
   (spec-map-eval spec-map (partial clj-user-eval spec-map) expr))
  ([spec-map user-eval expr]
   (let [interface-fn (-> spec-map synth exprs-interface eval-form)
         {:syms [validate-instance] :as fns} (interface-fn user-eval)]
     (if (map? expr)
       (validate-instance expr)
       (apply (get fns (first expr)) (rest expr))))))
