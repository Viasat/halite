;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns viasat.choco-clj
  (:require [schema.core :as s])
  (:import [org.chocosolver.solver Model Solver]
           [org.chocosolver.solver.variables Variable BoolVar IntVar]
           [org.chocosolver.solver.constraints Constraint]
           [org.chocosolver.solver.expression.discrete.arithmetic ArExpression ArExpression$IntPrimitive]
           [org.chocosolver.solver.expression.discrete.relational ReExpression]
           [org.chocosolver.solver.propagation PropagationEngine]
           [org.chocosolver.util ESat]))

(set! *warn-on-reflection* true)

(s/defschema ChocoVarType
  (s/cond-pre
   (s/enum :Int :Bool)
   #{s/Int}))

(defn- bool-type? [var-type] (= var-type :Bool))
(defn- int-type? [var-type] (or (= var-type :Int) (set? var-type)))

(s/defschema ChocoSpec
  {:vars {s/Symbol ChocoVarType}
   :constraints #{s/Any}})

(defn- make-var [^Model m [lb ub] [var var-type]]
  [var
   [(cond
      (= var-type :Int) (.intVar m (name var) lb ub true)
      (= var-type :Bool) (.boolVar m (name var))
      (set? var-type) (.intVar m (int-array var-type))
      :else (throw (ex-info (format "Unrecognized var type: '%s'" (pr-str var-type)) {:var-type var-type})))
    var-type]])

(defn- arithmetic ^"[Lorg.chocosolver.solver.expression.discrete.arithmetic.ArExpression;"
  [args]
  (into-array ArExpression args))

(defn- relational ^"[Lorg.chocosolver.solver.expression.discrete.relational.ReExpression;"
  [args]
  (into-array ReExpression args))

(defn- make-expr [^Model m vars form]
  (cond
    (true? form) (.boolVar m true)
    (false? form) (.boolVar m false)
    (symbol? form) (get vars form)
    (int? form) (.intVar m (int form))
    (list? form) (let [op (first form)]
                   (cond
                     ;; (- x y z) => (- (- x y) z)
                     (and (= op '-) (< 3 (count form)))
                     (make-expr m vars (apply list '- (apply list (take 3 form)) (drop 3 form)))

                     (= op 'dec) (make-expr m vars (list '- (second form) 1))
                     (= op 'inc) (make-expr m vars (list '+ (second form) 1))

                     (= op 'not=) (make-expr m vars (list 'not (apply list '= (rest form))))

                     (= op 'let)
                     (let [[bindings body] (rest form)]
                       (if (empty? bindings)
                         (make-expr m vars body)
                         (let [[var expr & bindings] bindings]
                           (make-expr m (assoc vars var (make-expr m vars expr)) (list 'let bindings body)))))

                     :else
                     (let [[arg1 & other-args] (mapv (partial make-expr m vars) (rest form))]
                       (condp = op
                         '+ (.add ^ArExpression arg1 (arithmetic other-args))
                         '- (.sub ^ArExpression arg1 ^ArExpression (first other-args))
                         '* (.mul ^ArExpression arg1 (arithmetic other-args))
                         '< (.lt ^ArExpression arg1 ^ArExpression (first other-args))
                         '<= (.le ^ArExpression arg1 ^ArExpression (first other-args))
                         '> (.gt ^ArExpression arg1 ^ArExpression (first other-args))
                         '>= (.ge ^ArExpression arg1 ^ArExpression (first other-args))
                         '= (.eq ^ArExpression arg1 (arithmetic other-args))
                         'and (.and ^ReExpression arg1 (relational other-args))
                         'or (.or ^ReExpression arg1 (relational other-args))
                         'not (.not ^ReExpression arg1)
                         '=> (.imp ^ReExpression arg1 ^ReExpression (first other-args))
                         'div (.div ^ArExpression arg1 ^ArExpression (first other-args))
                         'mod (.mod ^ArExpression arg1 ^ArExpression (first other-args))
                         'expt (.pow ^ArExpression arg1 ^ArExpression (first other-args))
                         'abs (.abs ^ArExpression arg1)
                         'if (let [[then else] other-args]
                               (if (instance? ReExpression then)
                                 (.and (.imp ^ReExpression arg1 ^ReExpression then)
                                       (relational [(.imp (.not ^ReExpression arg1) ^ReExpression else)]))
                                 (.ift ^ReExpression arg1 ^ArExpression then ^ArExpression else)))
                         (throw (ex-info (format "Unsupported operator '%s'" (pr-str op)) {:form form}))))))
    :else (throw (ex-info (format "Unsupported constraint: '%s'" (pr-str form)) {:form form}))))

(defn- bool-var-as-expr ^ReExpression
  [^ReExpression expr]
  (if (instance? BoolVar expr)
    (.eq ^BoolVar expr (ArExpression$IntPrimitive. 1 (.getModel expr)))
    expr))

(defn- make-constraint [^Model m vars form]
  (let [^ReExpression expr (bool-var-as-expr (make-expr m vars form))
        constraint (.decompose expr)]
    (.post constraint)
    {:form expr :constraint constraint}))

(s/defschema ChocoModel
  {:model Model
   :vars {s/Symbol [(s/one Variable :var) ChocoVarType]}
   :constraints [{:form s/Any
                  :constraint Constraint}]})

(def ^:dynamic *default-int-bounds* [-1000 1000])

(s/defn ^:private make-model :- ChocoModel
  [spec :- ChocoSpec]
  (let [{:keys [default-int-bounds]} spec
        m (Model.)
        vars (->> spec :vars (map (partial make-var m *default-int-bounds*)) (into {}))
        vars' (update-vals vars first)]
    {:model m
     :vars vars
     :constraints (->> spec :constraints (mapv (partial make-constraint m vars')))}))

(s/defschema VarBound
  (s/cond-pre
   s/Int
   s/Bool
   #{(s/cond-pre s/Int s/Bool)}
   [(s/one s/Int :lower) (s/one s/Int :upper)]))

(s/defschema VarBounds
  {s/Symbol VarBound})

(defn- extract-bound
  [[^Variable v var-type]]
  (cond
    (bool-type? var-type) (if (.isInstantiated v)
                            (= ESat/TRUE (.getBooleanValue ^BoolVar v))
                            #{true false})
    (int-type? var-type) (if (.isInstantiated v)
                           (.getValue ^IntVar v)
                           (if (.hasEnumeratedDomain ^IntVar v)
                             (set v)
                             [(.getLB ^IntVar v) (.getUB ^IntVar v)]))))

(s/defn propagate :- VarBounds
  [spec :- ChocoSpec]
  (let [{:keys [^Model model vars]} (make-model spec)]
    (.. model getSolver propagate)
    (update-vals vars extract-bound)))

(comment

  (let [spec '{:vars {x :Int, y :Int, b :Bool}
               :constraints [(< (dec y) x)
                             (=> b (< x (+ y 1)))]}]
    (doseq [extra '[[] [(= y 24)] [(= b true)] [(= y 24) (= b true)]]]
      (prn (propagate (update spec :constraints into extra))))))
