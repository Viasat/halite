;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-flower
  "Lower expressions in boms down into expressions that are supported by propagation."
  (:require [clojure.math :as math]
            [clojure.walk :as walk]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.instance-literal :as instance-literal]
            [com.viasat.halite.lib.fixed-decimal :as fixed-decimal]
            [com.viasat.halite.op-add-constraints :as op-add-constraints]
            [com.viasat.halite.op-add-value-fields :as op-add-value-fields]
            [com.viasat.halite.op-conjoin-spec-bom :as op-conjoin-spec-bom]
            [com.viasat.halite.fog :as fog]
            [com.viasat.halite.type-check :as type-check]
            [com.viasat.halite.types :as types]
            [com.viasat.halite.var-ref :as var-ref]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(fog/init)

(instance-literal/init)

;;;;

(declare flower)

;;;;

(def LowerContext {:spec-env (s/protocol envs/SpecEnv)
                   :spec-type-env (s/protocol envs/TypeEnv) ;; holds the types coming from the contextual spec
                   :type-env (s/protocol envs/TypeEnv) ;; holds local types, e.g. from 'let' forms
                   :env (s/protocol envs/Env) ;; contains local values, e.g. from 'let' forms
                   :path [s/Any]
                   :counter-atom s/Any ;; an atom to generate unique IDs
                   :instance-literal-atom s/Any ;; holds information about instance literals discovered in expressions
                   (s/optional-key :ignore-instance-literals?) Boolean ;; set to true so that expressions can be lowered again without causing duplicate instance literals to be added to the bom
                   (s/optional-key :constraint-name) String
                   :guards [s/Any]})

;;;;

(declare push-down-get)

(defn- make-and [& args]
  (let [args (->> args
                  (remove #(= true %)))]
    (cond
      (= 0 (count args)) true
      (= 1 (count args)) (first args)
      (some #(= false %) args) false
      :default (apply list 'and args))))

(defn- make-or [& args]
  (let [args (->> args
                  (remove #(= false %)))]
    (cond
      (= 0 (count args)) false
      (= 1 (count args)) (first args)
      (some #(= true %) args) true
      :default (apply list 'or args))))

(defn- make-not [x]
  (cond
    (= true x) false
    (= false x) true
    (and (seq? x) (= 'not (first x))) (second x)
    :default (list 'not x)))

(defn- make-if [target then-clause else-clause]
  (cond
    (= true target) then-clause
    (= false target) else-clause
    (and (= true then-clause) (= true else-clause)) true
    (and (= false then-clause) (= false else-clause)) false
    (and (= true then-clause) (= false else-clause)) target
    (and (= false then-clause) (= true else-clause)) (make-not target)
    (= true else-clause) (make-or (make-not target) then-clause)
    (= true then-clause) (make-or target else-clause)
    (= false else-clause) (make-and target then-clause)
    (= false then-clause) (make-and (make-not target) else-clause)
    ;; cannot do these simplifications because the clauses might not be boolean types
    ;; (= target then-clause) (make-or then-clause else-clause)
    ;; (= target else-clause) (make-or 'or then-clause else-clause)
    :default (list 'if target then-clause else-clause)))

(s/defn ^:private push-down-get-if
  [context :- LowerContext
   field
   expr]
  (let [[_ target then-clause else-clause] expr]
    (make-if target
             (push-down-get context field then-clause)
             (push-down-get context field else-clause))))

(s/defn ^:private push-down-get-when
  [context :- LowerContext
   field
   expr]
  (let [[_ target then-clause] expr]
    (list 'when
          target
          (push-down-get context field then-clause))))

(s/defn ^:private push-down-get-if-value
  [context :- LowerContext
   field
   expr]
  (let [[_ target then-clause else-clause] expr]
    (list 'if-value
          target
          (push-down-get context field then-clause)
          (push-down-get context field else-clause))))

(s/defn ^:private push-down-get-when-value
  [context :- LowerContext
   field
   expr]
  (let [[_ target then-clause] expr]
    (list 'when-value
          target
          (push-down-get context field then-clause))))

(s/defn ^:private push-down-get-if-value-let
  [context :- LowerContext
   field
   expr]
  (let [{:keys [env]} context
        [_ [sym target] then-clause else-clause] expr]
    (list 'if-value-let [sym target]
          (push-down-get context field then-clause)
          (push-down-get context field else-clause))))

(s/defn ^:private push-down-get-when-value-let
  [context :- LowerContext
   field
   expr]
  (let [{:keys [env]} context
        [_ [sym target] then-clause] expr]
    (list 'when-value-let [sym target]
          (push-down-get context field then-clause))))

(s/defn ^:private push-down-get-get
  [context :- LowerContext
   field
   expr]
  (let [{:keys [env]} context
        [_ target accessor] expr]
    (push-down-get context field (push-down-get context accessor target))))

(s/defn ^:private push-down-get
  [context :- LowerContext
   field
   expr]
  (cond
    (symbol? expr) (let [{:keys [path]} context]
                     (with-meta (make-if
                                 (var-ref/make-var-ref (conj path (keyword expr) :$value?))
                                 (var-ref/make-var-ref (conj path (keyword expr) field :$value?))
                                 false)
                       {:done? true}))
    (map? expr) (get expr field)
    (vector? expr) (get expr field)
    (seq? expr) (condp = (first expr)
                  'if (push-down-get-if context field expr)
                  'when (push-down-get-when context field expr)
                  'if-value (push-down-get-if-value context field expr)
                  'when-value (push-down-get-when-value context field expr)
                  'if-value-let (push-down-get-if-value-let context field expr)
                  'when-value-let (push-down-get-when-value-let context field expr) ;; same general form as if-value-let
                  'get (push-down-get-get context field expr))
    :default (throw (ex-info "unexpected expr to push-down-get" {:expr expr}))))

;;;;

;; Turn an expression into a boolean expression indicating whether or not the expression produces a value.

(declare return-path)

(s/defn ^:private return-path-if
  [context :- LowerContext
   expr]
  (let [[_ target then-clause else-clause] expr]
    (make-if (flower context target)
             (return-path context then-clause)
             (return-path context else-clause))))

(s/defn ^:private return-path-when
  [context :- LowerContext
   expr]
  (let [[_ target then-clause] expr]
    (make-if (flower context target)
             (return-path context then-clause)
             false)))

(s/defn ^:private return-path-if-value
  [context :- LowerContext
   expr]
  (let [[_ target then-clause else-clause] expr]
    (make-if (return-path target)
             (return-path context then-clause)
             (return-path context else-clause))))

(s/defn ^:private return-path-when-value
  [context :- LowerContext
   expr]
  (let [[_ target then-clause] expr]
    (make-if (return-path context target)
             (return-path context then-clause)
             false)))

(s/defn ^:private return-path-if-value-let
  [context :- LowerContext
   expr]
  (let [{:keys [env]} context
        [_ [sym target] then-clause else-clause] expr]
    (make-if (return-path context target)
             (return-path (assoc context :env (envs/bind env sym target))
                          then-clause)
             (if (nil? else-clause)
               false
               (return-path context else-clause)))))

(s/defn ^:private return-path-get
  [context :- LowerContext
   expr]
  (let [[_ target accessor] expr
        result (push-down-get context accessor target)]
    (if (:done? (meta result))
      result
      (return-path context result))))

(s/defn ^:private return-path-symbol
  [context :- LowerContext
   sym]
  (let [{:keys [env path]} context]
    (if (= '$no-value sym)
      false
      (if (contains? (envs/bindings env) sym)
        (return-path context ((envs/bindings env) sym))
        (var-ref/make-var-ref (conj path (keyword sym) :$value?))))))

(s/defn ^:private return-path
  [context :- LowerContext
   expr]
  (cond
    (boolean? expr) true
    (base/integer-or-long? expr) true
    (base/fixed-decimal? expr) true
    (string? expr) true
    (symbol? expr) (return-path-symbol context expr)
    (keyword? expr) (throw (ex-info "unexpected expr to return-path" {:expr expr}))
    (map? expr) true
    (seq? expr) (condp = (first expr)
                  'if (return-path-if context expr)
                  'when (return-path-when context expr)
                  'if-value (return-path-if-value context expr)
                  'when-value (return-path-when-value context expr)
                  'if-value-let (return-path-if-value-let context expr)
                  'when-value-let (return-path-if-value-let context expr) ;; same general form as if-value-let
                  'get (return-path-get context expr)
                  'get-in (ex-info "return-path not implemented for expr" {:expr expr})
                  'inc true
                  (throw (ex-info "return-path not implemented for expr" {:expr expr})))
    (set? expr) true
    (vector? expr) true
    :default (throw (ex-info "unexpected expr to return-path" {:expr expr}))))

;;;;

(defn- combine-envs [spec-type-env type-env]
  (reduce (fn [effective-type-env [sym value]]
            (envs/extend-scope effective-type-env sym value))
          spec-type-env
          (envs/scope type-env)))

(defn- expression-type [context expr]
  (let [{:keys [spec-env spec-type-env type-env path]} context]
    (type-check/type-check spec-env
                           (combine-envs spec-type-env type-env)
                           expr)))

(defn- non-root-fog? [x]
  (and (fog/fog? x)
       (not (#{:Integer :Boolean} (fog/get-type x)))))

;;;;

(defn- next-id [counter-atom]
  (swap! counter-atom inc))

;;

(s/defn ^:private flower-fog
  [context :- LowerContext
   expr]
  (fog/make-fog (expression-type context expr)))

(s/defn ^:private flower-symbol
  [context :- LowerContext
   sym]
  (let [{:keys [env path]} context]
    (if (= '$no-value sym)
      sym
      (if (contains? (envs/bindings env) sym)
        (let [resolved ((envs/bindings env) sym)]
          (if (symbol? resolved)
            (flower context resolved)
            resolved))
        (var-ref/make-var-ref (conj path (keyword sym)))))))

(s/defn ^:private flower-instance
  [context :- LowerContext
   expr]
  (let [{:keys [spec-env type-env env path counter-atom instance-literal-atom constraint-name guards ignore-instance-literals?]} context
        new-contents (-> expr
                         (dissoc :$type)
                         (update-vals (partial flower context)))
        path (conj path (str constraint-name "$" (next-id counter-atom)))]
    (if (->> new-contents
             vals
             (some non-root-fog?))
      (flower-fog context expr)
      (do
        ;; compute instance-literal constraints
        (let [env' (reduce (fn [env [field-name value]]
                             (envs/bind env (symbol field-name) value))
                           env
                           new-contents)
              type-env' (reduce (fn ([type-env [field-name value]]
                                     envs/extend-scope type-env
                                     (symbol field-name)
                                     (expression-type
                                      context
                                      (get expr field-name))))
                                type-env
                                new-contents)
              context' (assoc context :env env' :type-env type-env')]
          (when-not ignore-instance-literals?
            (swap! instance-literal-atom assoc-in
                   path
                   (-> new-contents
                       (update-vals (fn [val]
                                      (if (bom/is-primitive-value? val)
                                        val
                                        {:$expr val})))
                       (assoc :$instance-literal-type (:$type expr)
                              :$guards guards)))))
        (instance-literal/make-instance-literal (-> new-contents
                                                    (assoc :$type (:$type expr))))))))

(s/defn ^:private flower-get
  [context :- LowerContext
   expr]
  (let [[_ target accessor] expr
        target' (flower context target)]
    (cond
      (var-ref/var-ref? target') (var-ref/extend-path target' (if (vector? accessor)
                                                                accessor
                                                                [accessor]))

      (instance-literal/instance-literal? target') (-> target'
                                                       instance-literal/get-bindings
                                                       (get accessor))

      :default (throw (ex-info "unexpected target of get" {:expr expr
                                                           :target' target'})))))

(s/defn ^:private flower-let
  [context :- LowerContext
   expr]
  (let [{:keys [type-env env path]} context
        [bindings body] (rest expr)
        {type-env' :type-env
         env' :env
         bindings' :bindings} (reduce (fn [{:keys [type-env env bindings]} [sym binding-e]]
                                        (let [binding-e' (flower (assoc context
                                                                        :type-env type-env
                                                                        :env env)
                                                                 binding-e)
                                              type-env' (envs/extend-scope type-env
                                                                           sym
                                                                           (expression-type context binding-e))
                                              env' (envs/bind env sym binding-e')]
                                          {:type-env type-env'
                                           :env env'
                                           :bindings (into bindings
                                                           [sym binding-e'])}))
                                      {:type-env type-env
                                       :env env
                                       :bindings []}
                                      (partition 2 bindings))
        body' (flower (assoc context
                             :type-env type-env'
                             :env env')
                      body)]
    (if (or (->> bindings'
                 (partition 2)
                 (map second)
                 (some non-root-fog?))
            (non-root-fog? body'))
      (flower-fog context expr)
      body')))

(s/defn ^:private flower-if
  [context :- LowerContext
   expr]
  (let [{:keys [guards]} context
        [_ target then-clause else-clause] expr
        target' (flower context target)
        then-clause' (flower (assoc context
                                    :guards (conj guards target'))
                             then-clause)
        else-clause' (flower (assoc context
                                    :guards (conj guards (make-not target')))
                             else-clause)
        args' [target' then-clause' else-clause']]
    (if (some non-root-fog? args')
      (flower-fog context expr)
      (apply make-if args'))))

(s/defn ^:private flower-when
  [context :- LowerContext
   expr]
  (let [{:keys [guards]} context
        [_ target then-clause] expr
        target' (flower context target)
        then-clause' (flower (assoc context
                                    :guards (conj guards target'))
                             then-clause)
        args' [target' then-clause']]
    (if (some non-root-fog? args')
      (flower-fog context expr)
      (apply list 'when args'))))

(s/defn ^:private flower-if-value
  [context :- LowerContext
   expr]
  (let [{:keys [spec-type-env type-env guards]} context
        [_ target then-clause else-clause] expr
        target' (flower context target)]
    (when-not (var-ref/var-ref? target')
      (throw (ex-info "unexpected target of if-value or when-value" {:expr expr
                                                                     :target' target'
                                                                     :context context})))
    (when-not (->> target
                   (envs/lookup-type* (combine-envs type-env spec-type-env)))
      (throw (ex-info "symbol not found" {:target target
                                          :type-env (envs/scope type-env)})))
    (let [then-clause' (flower (assoc context
                                      :type-env (envs/extend-scope type-env
                                                                   target
                                                                   (->> target
                                                                        (envs/lookup-type*
                                                                         (if (->> target
                                                                                  (envs/lookup-type* spec-type-env))
                                                                           spec-type-env
                                                                           type-env))
                                                                        types/no-maybe)))
                               then-clause)
          else-clause' (some->> else-clause
                                (flower context))
          target' (var-ref/extend-path target' [:$value?])]
      (if (or (non-root-fog? then-clause')
              (and (not (nil? else-clause'))
                   (non-root-fog? else-clause')))
        (flower-fog context expr)
        (if (nil? else-clause')
          (list 'when target' then-clause')
          (make-if target' then-clause' else-clause'))))))

#_(defn- dump-context [context]
    (let [{:keys [env spec-type-env]} context]
      (clojure.pprint/pprint {:env (envs/bindings env)
                              :spec-type-env (envs/scope spec-type-env)})))

(s/defn ^:private flower-if-value-let*
  [context :- LowerContext
   guard?
   expr]
  (let [{:keys [env type-env guards]} context
        [_ [sym target] then-clause else-clause] expr
        target' (flower context target)
        return-path-target (return-path context target)
        then-clause' (flower (assoc context
                                    :guards (if guard?
                                              (conj guards (flower-if-value-let* (assoc context
                                                                                        :ignore-instance-literals? true)
                                                                                 false
                                                                                 (list 'if-value-let ['x_0 target] true false)))
                                              guards)
                                    :type-env (envs/extend-scope type-env
                                                                 sym
                                                                 (types/no-maybe (expression-type context target)))
                                    :env (envs/bind env
                                                    sym
                                                    target))
                             then-clause)
        else-clause' (flower (assoc context
                                    :guards (if guard?
                                              (conj guards (flower-if-value-let* (assoc context
                                                                                        :ignore-instance-literals? true)
                                                                                 false
                                                                                 (list 'if-value-let ['x_0 target] false true)))
                                              guards))
                             else-clause)]
    (if (or (non-root-fog? target')
            (non-root-fog? then-clause')
            (non-root-fog? else-clause'))
      (flower-fog context expr)
      (make-if return-path-target
               then-clause'
               else-clause'))))

(s/defn ^:private flower-if-value-let
  [context :- LowerContext
   expr]
  (flower-if-value-let* context true expr))

(s/defn ^:private flower-when-value-let
  [context :- LowerContext
   expr]
  (let [{:keys [type-env guards]} context
        [_ [sym target] body] expr
        target' (flower context target)
        body' (flower (assoc context
                             :guards (conj guards (flower-if-value-let* context
                                                                        false
                                                                        (list 'if-value-let ['x_0 target] true false)))

                             :type-env (envs/extend-scope type-env
                                                          sym
                                                          (types/no-maybe (expression-type context target))))
                      body)]
    (if (or (non-root-fog? target')
            (non-root-fog? body'))
      (flower-fog context expr)
      (list 'when-value-let [sym target']
            body'))))

(s/defn ^:private flower-refine-to
  [op
   context :- LowerContext
   expr]
  (let [[_ instance spec-id] expr
        instance' (flower context instance)]
    (if (non-root-fog? instance')
      (flower-fog context expr)
      (list op instance' spec-id))))

(s/defn ^:private flower-rescale
  [context :- LowerContext
   expr]
  (let [[_ target-form new-scale] expr
        target-scale (types/decimal-scale (expression-type context target-form))
        target-form' (flower context target-form)]
    (let [shift (- target-scale new-scale)]
      (if (= shift 0)
        target-form'
        (list (if (> shift 0) 'div '*)
              target-form'
              (->> shift abs (math/pow 10) long))))))

(s/defn ^:private flower-fn-application
  [context :- LowerContext
   expr]
  (let [[op & args] expr
        args' (->> args
                   (map (partial flower context)))]
    (if (some non-root-fog? args')
      (flower-fog context expr)
      (condp = op
        'and (apply make-and args')
        'or (apply make-or args')
        'not (apply make-not args')
        (apply list op args')))))

(defn- flower-fixed-decimal [expr]
  (-> expr fixed-decimal/extract-long second))

(s/defn flower
  [context :- LowerContext
   expr]
  (cond
    (boolean? expr) expr
    (base/integer-or-long? expr) expr
    (base/fixed-decimal? expr) (flower-fixed-decimal expr)
    (string? expr) expr
    (symbol? expr) (flower-symbol context expr)
    (map? expr) (flower-instance context expr)
    (seq? expr) (condp = (first expr)
                  'get (flower-get context expr)
                  'get-in (throw (ex-info "unrecognized halite form" {:expr expr}))
                  'let (flower-let context expr)
                  'if (flower-if context expr)
                  'when (flower-when context expr)
                  'if-value (flower-if-value context expr)
                  'when-value (flower-if-value context expr) ;; also handles 'when-value
                  'if-value-let (flower-if-value-let context expr)
                  'when-value-let (flower-when-value-let context expr)
                  'refine-to (flower-refine-to 'refine-to context expr)
                  'refines-to? (flower-refine-to 'refines-to? context expr) ;; same form as refine-to
                  'every? (flower-fog context expr)
                  'any? (flower-fog context expr) ;; same form as every?
                  'map (flower-fog context expr) ;; same form as every?
                  'filter (flower-fog context expr) ;; same form as every?
                  'sort-by (flower-fog context expr) ;; same form as every?
                  'reduce (flower-fog context expr)

                  'rescale (flower-rescale context expr)
                  ;; else:
                  (flower-fn-application context expr))
    (vector? expr) (flower-fog context expr)
    (set? expr) (flower-fog context expr)
    :else (throw (ex-info "unrecognized halite form" {:expr expr}))))

;;;;

(defn pre-lower [expr]
  (->> expr
       (walk/postwalk (fn [expr]
                        (cond
                          (and (seq? expr)
                               (= 'get-in (first expr)))
                          (let [[_ target accessors] expr]
                            (loop [[a & more-a] accessors
                                   r target]
                              (if a
                                (recur more-a (list 'get r a))
                                r)))

                          (and (seq? expr)
                               (= 'cond (first expr)))
                          (reduce (fn [if-expr [pred then]]
                                    (make-if pred then if-expr))
                                  (last expr)
                                  (reverse (partition 2 (rest expr))))

                          :default
                          expr)))))

(defn lower-expr [context expr]
  (->> expr
       pre-lower
       (flower context)))

(defn inline-constants [bom expr]
  (->> expr
       (walk/postwalk (fn [expr]
                        (cond
                          (var-ref/var-ref? expr)
                          (let [v (get-in bom (var-ref/get-path expr))]
                            (if (bom/is-primitive-value? v)
                              (if (fixed-decimal/fixed-decimal? v)
                                (flower-fixed-decimal v)
                                v)
                              expr))

                          (and (seq? expr) (= 'not (first expr))) (apply make-not (rest expr))
                          (and (seq? expr) (= 'or (first expr))) (apply make-or (rest expr))
                          (and (seq? expr) (= 'and (first expr))) (apply make-and (rest expr))

                          :default expr)))))

;;

(defn- add-guards-to-constraints [bom]
  (let [guards (:$guards bom)]
    (-> bom
        (assoc :$constraints (some-> bom
                                     :$constraints
                                     (update-vals (fn [constraint]
                                                    (loop [[g & more-g] guards
                                                           r constraint]
                                                      (if g
                                                        (recur more-g
                                                               (make-if g constraint true))
                                                        r))))))
        (dissoc :$guards)
        base/no-nil-entries)))

;;;;

(bom-op/def-bom-multimethod flower-op*
  [context bom]
  #{Integer
    String
    Boolean
    bom/PrimitiveBom
    bom/ExpressionBom
    bom/NoValueBom
    bom/YesValueBom
    bom/ContradictionBom
    #{}
    []
    bom/InstanceValue}
  bom

  FixedDecimal
  (flower-fixed-decimal bom)

  #{bom/ConcreteInstanceBom
    bom/AbstractInstanceBom
    bom/InstanceLiteralBom}
  (let [{:keys [spec-env path]} context
        instance-literal-atom (atom {})
        context (-> context
                    (assoc :instance-literal-atom instance-literal-atom)
                    (dissoc :constraint-name))
        spec-id (bom/get-spec-id bom)
        spec-info (envs/lookup-spec spec-env spec-id)
        bom' (-> bom
                 (merge (->> bom
                             bom/to-bare-instance-bom
                             (map (fn [[field-name field-bom]]
                                    [field-name (flower-op* (assoc context :path (conj path field-name)) field-bom)]))
                             (into {})))
                 (assoc :$constraints (some->> bom
                                               :$constraints
                                               (map (fn [[constraint-name x]]
                                                      (let [lowered-x (->> x
                                                                           (lower-expr (assoc context
                                                                                              :spec-type-env (envs/type-env-from-spec spec-info)
                                                                                              :constraint-name constraint-name
                                                                                              :guards []))
                                                                           (inline-constants bom))]
                                                        (when-not (= true lowered-x)
                                                          [constraint-name lowered-x]))))
                                               (into {})))
                 (assoc :$refinements (some-> bom
                                              :$refinements
                                              (map (fn [[other-spec-id sub-bom]]
                                                     [other-spec-id (flower-op* (assoc context :path (conj path other-spec-id)) sub-bom)]))
                                              (into {})))
                 (assoc :$concrete-choices (some-> bom
                                                   :$concrete-choices
                                                   (map (fn [[other-spec-id sub-bom]]
                                                          [other-spec-id (flower-op* (assoc context :path (conj path other-spec-id)) sub-bom)]))
                                                   (into {})))
                 base/no-nil-entries)]
    (if (some #(= false %) (vals (:$constraints bom')))
      bom/contradiction-bom
      (-> bom'
          (assoc :$instance-literals (->> @instance-literal-atom
                                          (map (fn [[instance-literal-id instance-literal-bom]]
                                                 [instance-literal-id
                                                  (let [spec-id (bom/get-spec-id instance-literal-bom)
                                                        bare-instance-bom (bom/to-bare-instance-bom instance-literal-bom)]
                                                    (->> instance-literal-bom
                                                         ;; (op-conjoin-spec-bom/conjoin-spec-bom-op spec-env)
                                                         ;; (op-add-value-fields/add-value-fields-op spec-env)
                                                         (op-add-constraints/add-constraints-op spec-env)
                                                         (flower-op* (assoc context
                                                                            :path (conj (:path context)
                                                                                        :$instance-literals
                                                                                        instance-literal-id)
                                                                            :env
                                                                            (reduce (fn [env [field-name val]]
                                                                                      (envs/bind env (symbol field-name)
                                                                                                 (if (bom/is-expression-bom? val)
                                                                                                   (:$expr val)
                                                                                                   val)))
                                                                                    (:env context)
                                                                                    bare-instance-bom)))
                                                         add-guards-to-constraints))]))

                                          (into {})
                                          base/no-empty))
          base/no-nil-entries))))

(s/defn flower-op
  [spec-env :- (s/protocol envs/SpecEnv)
   bom]
  (flower-op* {:spec-env spec-env
               :type-env (envs/type-env {})
               :env (envs/env {})
               :path []
               :counter-atom (atom -1)
               :instance-literal-atom (atom {})}
              bom))
