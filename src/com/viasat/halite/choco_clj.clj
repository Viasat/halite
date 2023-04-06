;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.choco-clj
  (:require [clojure.set :as set]
            [schema.core :as s])
  (:import [org.chocosolver.solver Model Solver]
           [org.chocosolver.solver.constraints Propagator Constraint]
           [org.chocosolver.solver.constraints.extension Tuples]
           [org.chocosolver.solver.expression.discrete.arithmetic ArExpression ArExpression$IntPrimitive]
           [org.chocosolver.solver.expression.discrete.relational ReExpression]
           [org.chocosolver.solver.propagation PropagationEngine]
           [org.chocosolver.solver.variables Variable BoolVar IntVar]
           [org.chocosolver.util ESat]
           [org.chocosolver.util.tools VariableUtils]))

;; (set! *warn-on-reflection* true)

(s/defschema IntRange
  [(s/one s/Int :lower) (s/one s/Int :upper)])

(s/defschema VarBound
  (s/cond-pre
   s/Int
   s/Bool
   #{(s/cond-pre s/Int s/Bool)}
   IntRange))

(s/defschema VarBounds
  {s/Symbol VarBound})

(s/defschema ChocoVarType
  (s/cond-pre
   (s/enum :Int :Bool)
   VarBound))

(s/defschema VarTypes
  {s/Symbol ChocoVarType})

(defn- bool-type? [var-type]
  (or (= var-type :Bool)
      (boolean? var-type)
      (and (set? var-type) (boolean? (first var-type)))))

(defn- int-type? [var-type]
  (or (= var-type :Int)
      (int? var-type)
      (and (set? var-type) (int? (first var-type)))
      (vector? var-type)))

(s/defschema ChocoSpec
  {:vars VarTypes
   :constraints #{s/Any}})

(def ^:dynamic *default-int-bounds* [-1000 1000])

(defn- make-var [^Model m [var var-type]]
  (let [[default-lb default-ub] *default-int-bounds*]
    (assert (and default-lb default-ub) "*default-int-bounds* must be set")
    [var
     [(cond
        (= var-type :Int) (.intVar m (name var) (int default-lb) (int default-ub) true)
        (= var-type :Bool) (.boolVar m (name var))
        (int? var-type) (.intVar m (name var) (int var-type))
        (boolean? var-type) (.boolVar m (name var) (boolean var-type))
        (set? var-type) (cond
                          (empty? var-type)
                          (throw (ex-info (format "Invalid spec: var '%s' has empty domain" var)
                                          {:var var :var-type var-type}))

                          (= 1 (count var-type))
                          (first (second (make-var m [var (first var-type)])))

                          (every? boolean? var-type)
                          (.boolVar m (name var))

                          (every? integer? var-type)
                          (.intVar m (name var) (int-array var-type))

                          :else (throw (ex-info (format "Invalid spec: var '%s' has invalid domain" var)
                                                {:var var :var-type var-type})))
        (vector? var-type) (let [[lb ub] var-type]
                             (.intVar m (name var) (int lb) (int ub) true))
        :else (throw (ex-info (format "Unrecognized var type: '%s'" (pr-str var-type)) {:var-type var-type})))
      var-type]]))

(defn- arithmetic ^"[Lorg.chocosolver.solver.expression.discrete.arithmetic.ArExpression;"
  [args]
  (if (= 1 (count args))
    (first args)
    (into-array ArExpression args)))

(defn- relational ^"[Lorg.chocosolver.solver.expression.discrete.relational.ReExpression;"
  [args]
  (into-array ReExpression args))

(defn- int-vars ^"[Lorg.chocosolver.solver.variables.IntVar;"
  [ivars]
  (into-array IntVar ivars))

(defn- bool-vars ^"[Lorg.chocosolver.solver.variables.BoolVar;"
  [bvars]
  (into-array BoolVar bvars))

(defn- post-guarded
  "Reify c, and post the constraint 'guard implies c'"
  [^ReExpression guard ^Constraint c]
  (let [c? (.reify c)]
    (.post (.imp guard c?))))

(defn- mod-workaround
  "We've got problems with the mod operator.

  First, there's an inconsistency between the expression
  API and the actual propagators. The propagators implement the semantics of the Java remainder (%) operator,
  but the expression API seems to compute bounds as if the semantics were consistent with e.g. Knuth/Clojure
  mod.

  Second, the propagators implement the semantics of the Java remanider (%) operator! We want Knuth/Clojure
  mod.

  To avoid incorrect bound computations, we only constrain the result of the operation when
  both operands are known to be non-negative (in which case Clojure mod and Java % have the same behavior)."
  [^Model m ^ReExpression guard ^ArExpression a ^ArExpression n]
  (let [[lb ub] *default-int-bounds*
        r (.intVar m (int lb) (int ub))]
    (if (.isInstantiatedTo (.intVar n) 0)
      (.post (.not guard))
      (let [c (.mod m (.intVar a) (.intVar n) r)]
        (post-guarded guard (.decompose (.ne n (.intVar m (int 0)))))
        (post-guarded
         guard
         (.decompose
          (.imp
           (.and (.ge a (.intVar m (int 0)))
                 (relational [(.ge n (.intVar m (int 0)))]))
           (.reify c))))))
    r))

(defn- bounds-for-pow
  "Ported from org.chocosolver.util.tools.VariableUtils/boundsForPow, but using
  Math.pow to avoid its bug regarding negative exponents."
  [^IntVar x, ^IntVar y]
  (let [min-x (.getLB x), max-x (.getUB x)
        min-y (.getLB y), max-y (.getUB y)
        nums (map int [0 1
                       (Math/pow min-x max-y)
                       (Math/pow max-x max-y)
                       (Math/pow (inc min-x) max-y)
                       (Math/pow (dec max-x) max-y)
                       (Math/pow min-x (max 0 (dec max-y)))
                       (Math/pow max-x (max 0 (dec max-y)))
                       (Math/pow (inc min-x) (max 0 (dec max-y)))
                       (Math/pow (dec max-x) (max 0 (dec max-y)))])]
    [(apply min nums) (apply max nums)]))

(defn- pow-workaround
  "The org.chocosolver.util.tools.VariableUtils/boundsForPow method, used by the expression building machinery
  to compute initial bounds for the variable representing an exponentiation, is wrong in the case
  where the base is in [1,3] and the exponent has a negative lower or upper bound.

  The problem comes from org.chocosolver.util.tools.MathUtils.pow, which uses bitshifting
  in the special case where the base is 2, even when the exponent is negative.

  Ported from org.chocosolver.solver.expression.discrete.arithmetic.BiArExpression/intVar.

  Secondly, the Choco .pow function implements a sort of 'integer' exponentiation:
  a^b = 0 for all b < 0. Our expt function will instead be undefined for negative exponents."
  [^Model m ^ReExpression guard ^ArExpression a ^ArExpression b]
  (let [avar (.intVar a), bvar (.intVar b)
        [lb ub] (bounds-for-pow avar bvar)
        rvar (.intVar m (.generateName m "pow_exp_") (int lb) (int ub))
        tuples (Tuples. true)]
    ;; guard implies b >= 0
    (post-guarded guard (.decompose (.ge b (.intVar m (int 0)))))
    ;; build table of solutions to r = a ^ b
    (doseq [^int val1 avar, val2 bvar]
      (when (<= 0 val2)
        (let [r (int (Math/pow val1 val2))]
          (when (.contains rvar r)
            (.add tuples (int-array [val1 val2 r]))))))
    ;; guard implies a^b = r
    (post-guarded guard (.table m (int-vars [avar bvar rvar]) tuples))
    rvar))

(defn- var-from-expr ^IntVar
  [expr]
  (cond
    (instance? ArExpression expr) (.intVar ^ArExpression expr)
    (instance? ReExpression expr) (.boolVar ^ReExpression expr)
    (instance? IntVar expr) expr
    :else (throw (ex-info "Not an IntVar" {:v expr}))))

(defn- force-initialization [var-or-expr]
  (if (instance? Long 1)
    var-or-expr
    (do
      (var-from-expr var-or-expr)
      var-or-expr)))

(defn- unsatisfiable
  [^Model m ^ReExpression guard]
  (let [bvar (.boolVar m (.generateName m "unsatisfiable"))
        unsat-prop (proxy [Propagator] [(bool-vars [bvar (.boolVar guard)])]
                     (propagate [evtmask])
                     (isEntailed [] ESat/FALSE))
        ^"[Lorg.chocosolver.solver.constraints.Propagator;" props (into-array [unsat-prop])]
    (post-guarded guard (Constraint. "UNSAT" props))
    bvar))

(def ^:dynamic *expr-stack* nil)

(defn- switch-arg-order [& args]
  (if (and (or (instance? Long (first args))
               (instance? Boolean (first args)))
           (not (or (instance? Long (second args))
                    (instance? Boolean (second args)))))
    (into [(second args) (first args)] (drop 2 args))
    args))

(defn- ensure-var [m x]
  (if (instance? Long x)
    (force-initialization (.intVar m (int x)))
    x))

(defn- make-expr
  "Add a Choco variable to the model m whose value represents the value of the given form,
  and return a ReExpression or ArExpression (often, but not always, just the variable itself)
  representing it.

  The vars argument defines the current lexical scope as a map of symbols to ArExpressions.

  The guard argument is a ReExpression that 'guards' the evaluation of form: if the guard is true,
  then form is evaluated. The guard is used to embue 'if' with \"short-circuiting\" behavior.
  Partial functions (e.g. div) should be turned into constraints that are implied when guard is true, such
  that they do not unconditionally constraint their arguments."
  [^Model m vars ^ReExpression guard form]
  (let [result (force-initialization
                (cond
                  (true? form) (.boolVar m true)
                  (false? form) (.boolVar m false)
                  (symbol? form) (get vars form)
                  (int? form) form
                  (list? form) (let [op (first form)]
                                 (cond
                                   ;; (- x y z) => (- (- x y) z)
                                   (and (= op '-) (< 3 (count form)))
                                   (make-expr m vars guard (apply list '- (apply list (take 3 form)) (drop 3 form)))

                                   (= op 'dec) (make-expr m vars guard (list '- (second form) 1))
                                   (= op 'inc) (make-expr m vars guard (list '+ (second form) 1))

                                   (= op 'not=) (make-expr m vars guard (list 'not (apply list '= (rest form))))

                                   (= op 'let)
                                   (let [[bindings body] (rest form)]
                                     (if (empty? bindings)
                                       (make-expr m vars guard body)
                                       (let [[var expr & bindings] bindings]
                                         (make-expr m (assoc vars var (make-expr m vars guard expr)) guard (list 'let bindings body)))))

                                   (= op 'if)
                                   (let [[pred-form then-form else-form] (rest form)
                                         ^ReExpression pred (make-expr m vars guard pred-form)
                                         not-pred (.not pred)
                                         then (make-expr m vars (.and guard (relational [pred])) then-form)
                                         else (make-expr m vars (.and guard (relational [not-pred])) else-form)]
                                     (let [then-var (var-from-expr (ensure-var m then))
                                           else-var (var-from-expr (ensure-var m else))
                                           pred-var (.boolVar ^ReExpression pred)
                                           lb (min (.getLB then-var) (.getLB else-var))
                                           ub (max (.getUB then-var) (.getUB else-var))
                                           rvar (.intVar m (str form) (int lb) (int ub))]
                                       (.post
                                        (.element m rvar (int-vars [else-var then-var]) pred-var 0))
                                       rvar))

                                   (= op 'unsatisfiable)
                                   (unsatisfiable m guard)

                                   :else
                                   (let [[arg1 & other-args] (mapv (partial make-expr m vars guard) (rest form))
                                         [arg1' & other-args'] (apply switch-arg-order arg1 other-args)
                                         switch? (not= arg1 arg1')
                                         switch-arg1 (if switch? arg1' arg1)
                                         switch-other-args (if switch? other-args' other-args)]
                                     (condp = op
                                       '+ (.add ;; ^ArExpression
                                           (ensure-var m switch-arg1) (arithmetic switch-other-args))
                                       '- (.sub ^ArExpression (ensure-var m arg1) ^ArExpression (ensure-var m (first other-args)))
                                       '* (.mul ;; ^ArExpression
                                           (ensure-var m arg1)
                                           (->> other-args
                                                (map (partial ensure-var m))
                                                arithmetic))
                                       '< (if switch?
                                            (.gt ;; ^ArExpression
                                             arg1'
                                             ;; ^ArExpression
                                             (first other-args'))
                                            (.lt ;; ^ArExpression
                                             (ensure-var m arg1)
                                             ;; ^ArExpression
                                             (first other-args)))
                                       '<= (if switch?
                                             (.ge ;; ^ArExpression
                                              arg1'
                                              ;; ^ArExpression
                                              (first other-args'))
                                             (.le ;; ^ArExpression
                                              (ensure-var m arg1)
                                              ;; ^ArExpression
                                              (first other-args)))
                                       '> (if switch?
                                            (.lt ;; ^ArExpression
                                             arg1'
                                             ;; ^ArExpression
                                             (first other-args'))
                                            (.gt ;; ^ArExpression
                                             arg1
                                             ;; ^ArExpression
                                             (first other-args)))
                                       '>= (if switch?
                                             (.le ;; ^ArExpression
                                              arg1'
                                              ;; ^ArExpression
                                              (first other-args'))
                                             (.ge ;; ^ArExpression
                                              (ensure-var m arg1)
                                              ;; ^ArExpression
                                              (first other-args)))
                                       '= (.eq ;; ^ArExpression
                                           (ensure-var m switch-arg1)
                                           (arithmetic switch-other-args))
                                       'and (.and ^ReExpression arg1 (relational other-args))
                                       'or (do
                                             (swap! *expr-stack* conj ['or arg1 (relational other-args)])
                                             (.or ^ReExpression arg1 (relational other-args)))
                                       'not (.not ^ReExpression arg1)
                                       '=> (.imp ^ReExpression arg1 ^ReExpression (first other-args))
                                       'div (let [avar (.intVar ^ArExpression (ensure-var m arg1))
                                                  bvar (.intVar ^ArExpression (ensure-var m (first other-args)))
                                                  [lb ub] (VariableUtils/boundsForDivision avar bvar)
                                                  rvar (.intVar m (.generateName m (str form)) (int lb) (int ub))]
                                              (post-guarded guard (.div m avar bvar rvar))
                                              rvar)
                                       ;; TODO: Implement our own mod propagators?
                                       ;;'mod (.mod (.abs ^ArExpression arg1) ^ArExpression (first other-args))
                                       'mod (mod-workaround m guard (ensure-var m arg1) (ensure-var m (first other-args)))
                                       'expt (pow-workaround m guard (ensure-var m arg1) (ensure-var m (first other-args)))
                                       ;;'expt (.pow ^ArExpression arg1 ^ArExpression (first other-args))
                                       'abs (.abs ^ArExpression (ensure-var m arg1))
                                       (throw (ex-info (format "Unsupported operator '%s'" (pr-str op)) {:form form}))))))
                  :else (throw (ex-info (format "Unsupported constraint: '%s'" (pr-str form)) {:form form}))))]
    (swap! *expr-stack* conj
           (cond
             (true? form) [:boolean]
             (false? form) [:boolean]
             (symbol? form) [:symbol]
             (int? form) [:int]
             (list? form) (let [op (first form)]
                            (when-not (#{'or 'let} op)
                              [op]))))
    result))

(defn- bool-var-as-expr ^ReExpression
  [^ReExpression expr]
  (if (instance? BoolVar expr)
    (.eq ^BoolVar expr (.boolVar (.getModel expr) true))
    expr))

(defn- make-constraint [^Model m vars form]
  (binding [*expr-stack* (atom '())]
    (let [base-expr (make-expr m vars (.boolVar m true) form)
          [op arg1 other-args] (->> *expr-stack* deref (remove nil?) first)]
      (if (= op 'or)
        (do
          (.addConstructiveDisjunction m
                                       (into-array Constraint
                                                   (into
                                                    [(.decompose ^ReExpression (bool-var-as-expr arg1))]
                                                    (map (fn [x]
                                                           (.decompose ^ReExpression (bool-var-as-expr x))) other-args))))
          nil)
        (let [^ReExpression expr (bool-var-as-expr base-expr)
              constraint (.decompose expr)]
          (.post constraint)
          {:form expr :constraint constraint})))))

(s/defschema ChocoModel
  {:model Model
   :vars {s/Symbol [(s/one Variable :var) ChocoVarType]}
   :constraints [{:form s/Any
                  :constraint Constraint}]})

(s/defn ^:private make-model :- ChocoModel
  [spec :- ChocoSpec]
  (let [{:keys [default-int-bounds]} spec
        m (Model.)
        vars (->> spec :vars (map (partial make-var m)) (into {}))
        vars' (update-vals vars first)]
    {:model m
     :vars vars
     :constraints (->> spec :constraints (mapv (partial make-constraint m vars')) (remove nil?))}))

(s/defn ^:private intersect-bound* :- ChocoVarType
  [a :- ChocoVarType, b :- ChocoVarType]
  (cond
    (= :Int a) b
    (= :Int b) a

    (= :Bool a) b
    (= :Bool b) a

    (or (int? a) (boolean? a)) (recur #{a} b)
    (or (int? b) (boolean? b)) (recur a #{b})

    (and (set? a) (set? b)) (set/intersection a b)
    (and (set? a) (vector? b)) (let [[lb ub] b]
                                 (set (filter #(<= lb % ub) a)))
    (and (vector? a) (set? b)) (recur b a)
    (and (vector? a) (vector? b)) (let [[lba uba] a, [lbb ubb] b]
                                    [(max lba lbb) (min uba ubb)])
    :else (throw (ex-info (format "Cannot intersect bounds '%s' and '%s'" a b)
                          {:a a :b b}))))

(s/defn ^:private simplify-bound :- ChocoVarType
  [bound :- ChocoVarType]
  (cond
    (and (set? bound) (= 1 (count bound))) (first bound)
    (vector? bound) (let [[lb ub] bound]
                      (cond
                        (= lb ub) lb
                        (< ub lb) #{}
                        :else bound))
    :else bound))

(s/defn intersect-bound :- ChocoVarType
  [a :- ChocoVarType, b :- ChocoVarType]
  (simplify-bound (intersect-bound* a b)))

(defn- ensure-compatibility [a b]
  (when-not (= (set (keys a)) (set (keys b)))
    (throw (ex-info "Cannot intersect bounds with different sets of variables"
                    {:a a :b b}))))

(s/defn intersect-bounds :- VarTypes
  [a :- VarTypes, b :- VarTypes]
  (ensure-compatibility a b)
  (->> (keys a)
       (map #(intersect-bound (a %) (b %)))
       (zipmap (keys a))))

(s/defn ^:private union-bound* :- ChocoVarType
  [a :- ChocoVarType, b :- ChocoVarType]
  (cond
    (= :Int a) a
    (= :Int b) b

    (= :Bool a) a
    (= :Bool b) b

    (or (int? a) (boolean? a)) (recur #{a} b)
    (or (int? b) (boolean? b)) (recur a #{b})

    (and (set? a) (set? b)) (set/union a b)
    (and (set? a) (vector? b)) (let [[lb ub] b, lb' (apply min a), ub' (apply max a)]
                                 [(min lb lb') (max ub ub')])
    (and (vector? a) (set? b)) (recur b a)
    (and (vector? a) (vector? b)) (let [[lba uba] a, [lbb ubb] b]
                                    [(min lba lbb) (max uba ubb)])
    :else (throw (ex-info (format "Cannot intersect bounds '%s' and '%s'" a b)
                          {:a a :b b}))))

(s/defn union-bound :- ChocoVarType
  [a :- ChocoVarType, b :- ChocoVarType]
  (simplify-bound (union-bound* a b)))

(s/defn union-bounds :- VarTypes
  [a :- VarTypes, b :- VarTypes]
  (ensure-compatibility a b)
  (->> (keys a)
       (map #(union-bound (a %) (b %)))
       (zipmap (keys a))))

(defn- fold-in-bound
  [bounds [var-sym bound]]
  (if (contains? bounds var-sym)
    (update bounds var-sym intersect-bound bound)
    (assoc bounds var-sym bound)))

(defn- fold-in-bounds [spec bounds]
  (update spec :vars #(reduce fold-in-bound % bounds)))

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
  ([spec :- ChocoSpec] (propagate spec {}))
  ([spec :- ChocoSpec, initial-bounds :- VarBounds]
   (let [{:keys [^Model model vars]} (-> spec (fold-in-bounds initial-bounds) (make-model))]
     (.. model getSolver propagate)
     (update-vals vars extract-bound))))
