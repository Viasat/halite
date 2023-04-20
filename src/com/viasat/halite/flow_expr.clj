;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.flow-expr
  "Lower expressions to be in terms of simple operations on bom variables."
  (:require [clojure.math :as math]
            [clojure.pprint :as pprint]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.flow-boolean :as flow-boolean]
            [com.viasat.halite.flow-get :as flow-get]
            [com.viasat.halite.flow-return-path :as flow-return-path]
            [com.viasat.halite.instance-literal :as instance-literal]
            [com.viasat.halite.lib.fixed-decimal :as fixed-decimal]
            [com.viasat.halite.fog :as fog]
            [com.viasat.halite.type-check :as type-check]
            [com.viasat.halite.types :as types]
            [com.viasat.halite.var-ref :as var-ref]
            [com.viasat.halite.walk :as walk2]
            [schema.core :as s])
  (:import [clojure.lang IObj]
           [java.io Writer]))

(set! *warn-on-reflection* true)

(fog/init)

(instance-literal/init)

;;;;

(def ExprContext {:spec-env (s/protocol envs/SpecEnv)
                  :spec-type-env (s/protocol envs/TypeEnv) ;; holds the types coming from the contextual spec
                  :type-env (s/protocol envs/TypeEnv) ;; holds local types, e.g. from 'let' forms
                  :env (s/protocol envs/Env) ;; contains local values, e.g. from 'let' forms
                  :path [s/Any]
                  :counter-atom s/Any ;; an atom to generate unique IDs
                  (s/optional-key :valid-var-path) [s/Any] ;; path to a variable to represent the result of a 'valid' or 'valid?' invocation in an expression
                  :instance-literal-f s/Any ;; "callback" for indicating that an instance literal is encountered
                  (s/optional-key :constraint-name) String
                  :guards [s/Any]})

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

(deftype LoweredExprWrapper [expr]
  Object
  (equals [_ other]
    (and (instance? LoweredExprWrapper other)
         (= expr (.-expr ^LoweredExprWrapper other))))
  (hashCode [_]
    (.hashCode expr))

  bom/LoweredObject
  (is-lowered-object? [_] true))

(s/defn make-lowered-expr-wrapper :- LoweredExprWrapper
  [expr]
  (LoweredExprWrapper. expr))

(s/defn lowered-expr-wrapper-reader :- LoweredExprWrapper
  [expr]
  (make-lowered-expr-wrapper expr))

(s/defn lowered-expr-wrapper? :- Boolean
  [value :- s/Any]
  (instance? LoweredExprWrapper value))

(defn wrap-lowered-expr [expr]
  (LoweredExprWrapper. expr))

(s/defn unwrap-lowered-expr-wrapper
  [expr :- LoweredExprWrapper]
  (.-expr expr))

(def ^:dynamic *reader-symbol* 'lowered)

(defn print-lowered-expr-wrapper [^LoweredExprWrapper lowered-expr-wrapper ^Writer writer]
  (.write writer (str "#" *reader-symbol* " " (pr-str (.-expr lowered-expr-wrapper)))))

(defmethod print-method LoweredExprWrapper [lowered-expr-wrapper writer]
  (print-lowered-expr-wrapper lowered-expr-wrapper writer))

(defmethod print-dup LoweredExprWrapper [lowered-expr-wrapper writer]
  (print-lowered-expr-wrapper lowered-expr-wrapper writer))

(.addMethod ^clojure.lang.MultiFn pprint/simple-dispatch LoweredExprWrapper
            (fn [lowered-expr-wrapper]
              (print-lowered-expr-wrapper lowered-expr-wrapper *out*)))

;;;;

(declare flower)

(s/defn ^:private flower-fog :- bom/LoweredExpr
  [context :- ExprContext
   expr :- bom/Expr]
  (->> (fog/make-fog (expression-type context expr))
       bom/flag-lowered))

(s/defn ^:private flower-symbol :- bom/LoweredExpr
  [context :- ExprContext
   sym :- s/Symbol]
  (->> (let [{:keys [env path]} context]
         (if (= '$no-value sym)
           sym
           (if (contains? (envs/bindings env) sym)
             (let [resolved ((envs/bindings env) sym)]
               (if (lowered-expr-wrapper? resolved)
                 (unwrap-lowered-expr-wrapper resolved)
                 (flower context resolved)))
             (var-ref/make-var-ref (conj path (keyword sym))))))))

(s/defn ^:private flower-instance :- bom/LoweredExpr
  [context :- ExprContext
   expr :- bom/Expr]
  (->> (let [{:keys [spec-env type-env env path counter-atom instance-literal-f constraint-name guards
                     valid-var-path]} context
             new-contents (-> expr
                              (dissoc :$type)
                              (update-vals (partial flower context)))
             path (:id-path (meta expr))]
         (when (nil? path)
           (throw (ex-info "did not find expected id-path in metadata" {:expr expr
                                                                        :meta (meta expr)})))

         ;; this seems too aggressive, but is it too permissive to leave it out altogether?
         #_(if (->> new-contents
                    vals
                    (some non-root-fog?))
             (flower-fog context expr))

         ;; compute instance-literal constraints
         (let [env' (reduce (fn [env [field-name value]]
                              (flow-return-path/add-binding env (symbol field-name) value))
                            env
                            (-> expr
                                (dissoc :$type)))
               type-env' (reduce (fn ([type-env [field-name value]]
                                      envs/extend-scope type-env
                                      (symbol field-name)
                                      (expression-type
                                       context
                                       (get expr field-name))))
                                 type-env
                                 new-contents)
               context' (assoc context :env env' :type-env type-env')]
           (instance-literal-f path
                               (let [nc (-> new-contents
                                            (update-vals (fn [val]
                                                           (if (bom/is-bom-value? val)
                                                             val
                                                             {:$expr val})))
                                            (assoc :$instance-literal-type (:$type expr)
                                                   :$guards guards))
                                     nc (if (nil? valid-var-path)
                                          nc
                                          (assoc nc :$valid-var-path valid-var-path))]
                                 nc)))
         (instance-literal/make-instance-literal path
                                                 (-> new-contents
                                                     (assoc :$type (:$type expr)))))
       bom/flag-lowered))

(s/defn ^:private flower-get :- bom/LoweredExpr
  [context :- ExprContext
   expr :- bom/Expr]
  (->> (let [[_ target accessor] expr
             target' (flower context target)
             v (cond
                 (var-ref/var-ref? target') (var-ref/extend-path target' (if (vector? accessor)
                                                                           accessor
                                                                           [accessor]))

                 (instance-literal/instance-literal? target') (-> target'
                                                                  instance-literal/get-bindings
                                                                  (get accessor))

                 :default (throw (ex-info "unexpected target of get" {:expr expr
                                                                      :target' target'})))]
         (if (nil? v)
           'no-value
           v))
       bom/flag-lowered))

(s/defn ^:private flower-let :- bom/LoweredExpr
  [context :- ExprContext
   expr :- bom/Expr]
  (->> (let [{:keys [type-env env path]} context
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
                                                   env' (flow-return-path/add-binding env sym binding-e)]
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
           body'))
       bom/ensure-flag-lowered))

(s/defn ^:private flower-if :- bom/LoweredExpr
  [context :- ExprContext
   expr :- bom/Expr]
  (->> (let [{:keys [guards]} context
             [_ target then-clause else-clause] expr
             target' (flower context target)
             then-clause' (flower (assoc context
                                         :guards (conj guards target'))
                                  then-clause)
             else-clause' (flower (assoc context
                                         :guards (conj guards (flow-boolean/make-not target')))
                                  else-clause)
             args' [target' then-clause' else-clause']]
         (if (some non-root-fog? args')
           (flower-fog context expr)
           (apply flow-boolean/make-if args')))
       bom/flag-lowered))

(s/defn ^:private flower-when :- bom/LoweredExpr
  [context :- ExprContext
   expr :- bom/Expr]
  (->> (let [{:keys [guards]} context
             [_ target then-clause] expr
             target' (flower context target)
             then-clause' (flower (assoc context
                                         :guards (conj guards target'))
                                  then-clause)
             args' [target' then-clause']]
         (if (some non-root-fog? args')
           (flower-fog context expr)
           (apply list 'when args')))
       bom/flag-lowered))

(s/defn ^:private flower-if-value :- bom/LoweredExpr
  [context :- ExprContext
   expr :- bom/Expr]
  (->> (let [{:keys [spec-type-env type-env guards]} context
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
               (flow-boolean/make-if target' then-clause' else-clause')))))
       bom/flag-lowered))

#_(defn- dump-context [context]
    (let [{:keys [env spec-type-env]} context]
      (clojure.pprint/pprint {:env (envs/bindings env)
                              :spec-type-env (envs/scope spec-type-env)})))

(s/defn ^:private flower-if-value-let* :- bom/LoweredExpr
  [context :- ExprContext
   guard? :- Boolean
   expr :- bom/Expr]
  (->> (let [{:keys [env type-env guards]} context
             [_ [sym target] then-clause else-clause] expr
             target' (flower context target)
             return-path-target (flow-return-path/return-path
                                 (-> context
                                     (select-keys [:env :path])
                                     (assoc :lower-f #(flower context %)))
                                 target)
             inner-target (if (and (seq? target)
                                   (= 'valid (first target)))
                            ;; sniff out the value inside target
                            (second target)
                            target)
             inner-target' (if (nil? target')
                             '$no-value
                             (if (and (seq? target)
                                      (= 'valid (first target)))
                               ;; sniff out the value inside target
                               (second (rest target'))
                               target'))
             then-clause' (flower (assoc context
                                         :guards (if guard?
                                                   (conj guards (flower-if-value-let* context
                                                                                      false
                                                                                      (list 'if-value-let ['x_0 target] true false)))
                                                   guards)
                                         :type-env (envs/extend-scope type-env
                                                                      sym
                                                                      (types/no-maybe (expression-type context inner-target)))
                                         :env (flow-return-path/add-binding env
                                                                            sym
                                                                            (wrap-lowered-expr inner-target')))
                                  then-clause)
             else-clause' (when-not (nil? else-clause)
                            (flower (assoc context
                                           :guards (if guard?
                                                     (conj guards (flower-if-value-let* context
                                                                                        false
                                                                                        (list 'if-value-let ['x_0 target] false true)))
                                                     guards))
                                    else-clause))]
         (if (or (non-root-fog? target')
                 (non-root-fog? then-clause')
                 (and (not (nil? else-clause'))
                      (non-root-fog? else-clause')))
           (flower-fog context expr)
           (if (nil? else-clause)
             (flow-boolean/make-when return-path-target
                                     then-clause')
             (flow-boolean/make-if return-path-target
                                   then-clause'
                                   else-clause'))))
       bom/ensure-flag-lowered))

(s/defn ^:private flower-if-value-let :- bom/LoweredExpr
  [context :- ExprContext
   expr :- bom/Expr]
  (flower-if-value-let* context true expr))

(s/defn ^:private flower-when-value-let :- bom/LoweredExpr
  [context :- ExprContext
   expr :- bom/Expr]
  (flower-if-value-let* context true expr))

(s/defn ^:private flower-refine-to :- bom/LoweredExpr
  [op :- s/Symbol
   context :- ExprContext
   expr :- bom/Expr]
  (->> (let [[_ instance spec-id] expr
             instance' (flower context instance)]
         (if (non-root-fog? instance')
           (flower-fog context expr)
           (if (= 'refine-to op)
             (cond
               (instance-literal/instance-literal? instance')
               (var-ref/make-var-ref (conj (instance-literal/get-path instance') :$refinements spec-id))

               (var-ref/var-ref? instance')
               (var-ref/make-var-ref (conj (var-ref/get-path instance') :$refinements spec-id))

               :default (throw (ex-info "refine-to does not yet handle this case" {:expr expr
                                                                                   :instance' instance'})))
             (list op instance' spec-id))))
       bom/flag-lowered))

(s/defn ^:private flower-rescale :- bom/LoweredExpr
  [context :- ExprContext
   expr :- bom/Expr]
  (->> (let [[_ target-form new-scale] expr
             target-scale (types/decimal-scale (expression-type context target-form))
             target-form' (flower context target-form)]
         (let [shift (- target-scale new-scale)]
           (if (= shift 0)
             target-form'
             (list (if (> shift 0) 'div '*)
                   target-form'
                   (->> shift abs (math/pow 10) long)))))
       bom/flag-lowered))

(s/defn ^:private flower-valid? :- bom/LoweredExpr
  [context :- ExprContext
   expr :- bom/Expr]
  (->> (let [valid-var-path (:id-path (meta expr))
             [_ sub-expr] expr]
         (when (nil? valid-var-path)
           (throw (ex-info "did not find expected id-path in metadata" {:expr expr
                                                                        :meta (meta expr)})))
         ;; for the side-effects of finding the instance literals
         (flower (assoc context :valid-var-path valid-var-path) sub-expr)
         (var-ref/make-var-ref valid-var-path))
       bom/flag-lowered))

(s/defn ^:private flower-valid :- bom/LoweredExpr
  [context :- ExprContext
   expr :- bom/Expr]
  (->> (let [{:keys [constraint-name path counter-atom]} context
             valid-var-path (:id-path (meta expr))
             [_ sub-expr] expr]
         (when (nil? valid-var-path)
           (throw (ex-info "did not find :id-path in metadata" {:expr expr
                                                                :meta (meta expr)})))
         (list 'when (var-ref/make-var-ref valid-var-path)
               (flower (assoc context :valid-var-path valid-var-path) sub-expr)))
       bom/flag-lowered))

(s/defn ^:private flower-fn-application :- bom/LoweredExpr
  [context :- ExprContext
   expr :- bom/Expr]
  (->> (let [[op & args] expr
             args' (->> args
                        (map (partial flower context)))]
         (if (some non-root-fog? args')
           (flower-fog context expr)
           (condp = op
             'and (apply flow-boolean/make-and args')
             'or (apply flow-boolean/make-or args')
             'not (apply flow-boolean/make-not args')
             (apply list op args'))))
       bom/flag-lowered))

(s/defn flower-fixed-decimal :- s/Int
  [expr :- bom/Expr]
  (->> (-> expr fixed-decimal/extract-long second)
       bom/flag-lowered))

(s/defn flower :- bom/LoweredExpr
  [context :- ExprContext
   expr :- bom/Expr]
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

                  'valid? (flower-valid? context expr)
                  'valid (flower-valid context expr)

                  ;; else:
                  (flower-fn-application context expr))
    (vector? expr) (flower-fog context expr)
    (set? expr) (flower-fog context expr)
    :else (throw (ex-info "unrecognized halite form" {:expr expr}))))

;;;;

(defn- preserve-meta [in out]
  (let [m (meta in)]
    (if m
      (with-meta out m)
      out)))

(s/defn lower-sugar :- bom/Expr
  "Lower syntactic sugar of 'get-in and 'cond."
  [expr :- bom/Expr]
  (->> expr
       (walk2/postwalk* (fn [old-expr expr]
                          (preserve-meta old-expr
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
                                                     (flow-boolean/make-if pred then if-expr))
                                                   (last expr)
                                                   (reverse (partition 2 (rest expr))))

                                           :default
                                           expr))))))

(s/defn lower-expr :- bom/LoweredExpr
  [context
   expr :- bom/Expr]
  (->> expr
       lower-sugar
       (flower (assoc context
                      :guards []))))

;;;;

(defn init []
  ;; this is here for other modules to call to force this namespace to be loaded
  )
