;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.synthesize
  (:require [clojure.walk :refer [postwalk]]))

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

(defn synthesize-spec [spec-map [spec-id spec]]
  [spec-id
   {:predicate
    (strip-ns
     `(fn [$exprs $this]
        (and (map? $this)
             (= ~spec-id (:$type $this))
             ;; TODO: handle optionals
             (= ~(into #{:$type} (keys (:spec-vars spec)))
                (set (keys $this)))

             ;; TODO insert constraints

             ;; non-inverted refinements
             ~@(->> (:refines-to spec)
                    (remove :inverted)
                    (map (fn [[to-spec-id {:keys [expr]}]]
                           (strip-ns
                            `(if-let [refined ((get-in $exprs [~spec-id :refines-to ~to-spec-id])
                                               $exprs $this)]
                               ((get-in $exprs [~to-spec-id :predicate]) $exprs refined)
                               true))))))))
    :refines-to
    (->> (:refines-to spec) ;; TODO transitive refinements
         (map (fn [[to-spec-id {:keys [expr]}]]
                [to-spec-id
                 (strip-ns
                  `(fn [$exprs {:keys ~(vec (map symbol (keys (:spec-vars spec))))}]
                     ~expr))]))
         (into {}))}])

(defn synthesize
  "Formal of definition of some halite concepts. Return an exprs-data"
  [spec-map]
  (->> spec-map
       (map (partial synthesize-spec spec-map))
       (into {})))

(defn compile-exprs [exprs-data]
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
