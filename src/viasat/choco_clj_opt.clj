;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns viasat.choco-clj-opt
  "An extended Choco front-end that simulates 'optional' variables, whose domains may be
  empty in a solution."
  (:require [schema.core :as s]
            [viasat.choco-clj :as choco-clj])
  (:import [org.chocosolver.solver.exception ContradictionException]))

(s/defschema ChocoVarType choco-clj/ChocoVarType)

(s/defschema ChocoSpec
  (assoc choco-clj/ChocoSpec (s/optional-key :optionals) #{s/Symbol}))

(s/defschema VarBound
  (s/cond-pre
   s/Int
   s/Bool
   (s/enum :Unset)
   #{(s/cond-pre s/Int s/Bool (s/enum :Unset))}
   [(s/one s/Int :lower) (s/one s/Int :upper) (s/optional (s/enum :Unset) :settedness)]))

(s/defschema VarBounds
  {s/Symbol VarBound})

(def ^:dynamic *default-int-bounds* [-1000 1000])

(s/defschema ^:private InclusionWitnessMap {s/Symbol s/Symbol})

(defn- lower-expr
  [witness-map included? expr]
  (cond
    (or (int? expr) (boolean? expr)) expr
    (symbol? expr) (if (included? expr)
                     expr
                     (if (witness-map expr)
                       (throw (ex-info (format "Invalid expression: '%s' may only be referenced in the 'then' branch of 'if-value" expr)
                                       {:expr expr}))
                       (throw (ex-info (format "Undefined: %s" expr) {:expr expr}))))
    (seq? expr) (let [[op & args] expr]
                  (condp = op
                    'if-value (let [[var-sym then else] args]
                                (when-not (contains? witness-map var-sym)
                                  (throw (ex-info (format "Invalid expression: '%s' is not an optional variable" var-sym)
                                                  {:expr expr})))
                                (list 'if (witness-map var-sym)
                                      (lower-expr witness-map (conj included? var-sym) then)
                                      (lower-expr witness-map included? else)))
                    'let (let [[included? bindings]
                               ,,(reduce
                                  (fn [[included? bindings] [sym subexpr]]
                                    [(conj included? sym)
                                     (conj bindings sym (lower-expr witness-map included? subexpr))])
                                  [included? []]
                                  (partition 2 (first args)))]
                           (list 'let bindings (lower-expr witness-map included? (last args))))
                    (apply list op (map (partial lower-expr witness-map included?) args))))
    :else (throw (ex-info "Invalid expression" {:expr expr}))))

(s/defn ^:private lower-spec :- [(s/one choco-clj/ChocoSpec :spec)
                                 (s/one InclusionWitnessMap :witness-map)]
  [{:keys [optionals vars constraints] :or {optionals #{}}} :- ChocoSpec]
  (when-not (every? symbol? optionals)
    (throw (ex-info "Invalid optional specs" {:optionals (filter (comp symbol?) optionals)})))
  (doseq [var-sym optionals]
    (when-not (contains? vars var-sym)
      (throw (ex-info (format "Variable not defined: '%s' var-sym") {}))))

  ;; TODO: Deal with name collisions
  (let [witness-map (zipmap optionals (map #(symbol (str (name %) "?")) optionals))
        mandatories (set (remove (partial contains? witness-map) (keys vars)))]
    [{:vars (merge vars (zipmap (vals witness-map) (repeat :Bool)))
      :constraints (->> constraints
                        (map (partial lower-expr witness-map mandatories))
                        set)}
     witness-map]))

(s/defn ^:private lower-bounds :- choco-clj/VarBounds
  [witness-map :- InclusionWitnessMap, bounds :- VarBounds]
  (reduce
   (fn [acc [var-sym bound]]
     (if-let [witness-sym (witness-map var-sym)]
       (cond
         (= bound :Unset) (assoc acc witness-sym false)

         (set? bound) (let [real-vals (set (filter #(or (int? %) (boolean? %)) bound))]
                        (cond-> acc
                          (seq real-vals) (assoc var-sym real-vals)
                          (not (contains? bound :Unset)) (assoc witness-sym true)))

         (or (int? bound) (boolean? bound)) (assoc acc var-sym bound witness-sym true)

         (vector? bound) (let [[lb ub settedness] bound]
                           (cond-> (assoc acc var-sym [lb ub])
                             (nil? settedness) (assoc witness-sym true)))

         :else (throw (ex-info "Invalid bound" {:bound bound})))

       (assoc acc var-sym bound)))
   {}
   bounds))

(s/defn ^:private include-unset :- VarBound
  [bound :- VarBound]
  (cond
    (or (boolean? bound) (int? bound)) #{:Unset bound}
    (or (set? bound) (vector? bound)) (conj bound :Unset)
    :else (throw (ex-info (format "Unrecognized bound '%s'" bound) {:bound bound}))))

(s/defn ^:private lift-bound :- VarBounds
  [witness-map :- InclusionWitnessMap, lowered-bounds :- VarBounds, var-sym :- s/Symbol]
  (let [witness (witness-map var-sym)
        inclusion (lowered-bounds witness)
        result (dissoc lowered-bounds witness)]
    (cond-> result
      (false? inclusion) (assoc var-sym :Unset)
      (set? inclusion) (update var-sym include-unset))))

(s/defn ^:private propagate-optional :- choco-clj/VarBounds
  [spec :- choco-clj/ChocoSpec
   witness-map :- InclusionWitnessMap
   bounds :- choco-clj/VarBounds
   var-sym :- s/Symbol]
  (let [witness (witness-map var-sym)
        inclusion (bounds witness)]
    (if (set? inclusion)
      (let [[included-bounds excluded-bounds] (for [incl [true false]]
                                                (try
                                                  (choco-clj/propagate spec (assoc bounds witness incl))
                                                  (catch ContradictionException ex
                                                    ex)))]
        (when (and (instance? ContradictionException included-bounds)
                   (instance? ContradictionException excluded-bounds))
          (throw excluded-bounds))
        (-> (cond
              (instance? ContradictionException included-bounds) excluded-bounds
              (instance? ContradictionException excluded-bounds) included-bounds
              :else (-> included-bounds
                        (choco-clj/union-bounds excluded-bounds)
                        (assoc var-sym (get included-bounds var-sym))))
            (choco-clj/intersect-bounds bounds)))
      bounds)))

(s/defn propagate :- VarBounds
  [spec :- ChocoSpec & [initial-bounds :- VarBounds]]
  (binding [choco-clj/*default-int-bounds* *default-int-bounds*]
    (let [[lowered-spec witness-map] (lower-spec spec)]
      (as-> (or initial-bounds {}) bounds
        (lower-bounds witness-map bounds)
        (choco-clj/propagate lowered-spec bounds)
        (reduce
         (partial propagate-optional lowered-spec witness-map)
         bounds
         (:optionals spec))
        (reduce
         (partial lift-bound witness-map)
         bounds
         (:optionals spec))))))
