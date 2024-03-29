;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.lib.fixed-decimal
  "Provides a fixed decimal number library for basic math and comparisons. Treats fixed decimal
  numbers of a given precision as a separate universe of numerical values. That is, they are only
  meant to be used with other numbers of the same precision. Math operations are performed as if
  they were operating with truncating semantics on integers, except the decimal place is added at
  the appropriate place. Multiplication and division can only be performed with integer values. The
  'backing store' is treated as a Long, so if the Long would overflow, then the fixed decimal
  operation overflows."
  (:require [clojure.pprint :as pprint]
            [clojure.string :as string]
            [potemkin]
            [schema.core :as s])
  (:import [java.io Writer]))

(set! *warn-on-reflection* true)

(potemkin/defprotocol+ FixedDecimalContents
  (string-representation [this]))

(deftype FixedDecimal [^String sign ^String integer ^String fractional]
  FixedDecimalContents
  (string-representation [_]
    (str sign integer "." fractional))

  Object
  (equals [_ other]
    (and (instance? FixedDecimal other)
         (= sign (.-sign ^FixedDecimal other))
         (= integer (.-integer ^FixedDecimal other))
         (= fractional (.-fractional ^FixedDecimal other))))
  (hashCode [_]
    (.hashCode [sign integer fractional])))

(def ^:dynamic *reader-symbol* 'd)

(defn print-fixed [fixed ^Writer writer]
  (.write writer (str "#" *reader-symbol* " \"" (string-representation fixed) "\"")))

(defmethod print-method FixedDecimal [fixed writer]
  (print-fixed fixed writer))

(defmethod print-dup FixedDecimal [fixed writer]
  (print-fixed fixed writer))

(.addMethod ^clojure.lang.MultiFn pprint/simple-dispatch FixedDecimal
            (fn [fixed]
              (print-fixed fixed *out*)))

;;;;

(s/defn ^:private parse-fixed-decimal-str :- (s/maybe FixedDecimal)
  [s]
  (when-let [matches (re-matches #"(-?)([0-9]+).?([0-9]*)" s)]
    (let [[_ sign integer fractional] matches]
      (FixedDecimal. sign integer fractional))))

(s/defn get-scale :- Integer
  "Retrieve the number of decimal places in the fixed decimal value."
  [f :- FixedDecimal]
  (count (.fractional f)))

(s/defn extract-long :- [(s/one s/Int :scale)
                         (s/one Long :long)]
  [f :- FixedDecimal]
  (let [long-value (parse-long (str (.-sign f) (.-integer f) (.-fractional f)))]
    (when (nil? long-value)
      (throw (NumberFormatException. (str "Invalid fixed-decimal string: " (string-representation f)))))
    [(get-scale f) long-value]))

(defn- assert-extract-long [f]
  (extract-long f)
  nil)

(s/defn package-long :- FixedDecimal
  "Interpret the long value as if it were a fixed decimal with the given scale. Assumes that scale
  is a positive value."
  [scale :- s/Int
   n :- Long]
  (let [s (if (neg? n)
            (subs (str n) 1)
            (str n))
        fractional-start (- (count s) scale)]
    (FixedDecimal. (if (neg? n) "-" "")
                   (if (neg? fractional-start)
                     "0"
                     (let [integer (subs s 0 fractional-start)]
                       (if (zero? (count integer))
                         "0"
                         integer)))
                   (if (neg? fractional-start)
                     (str (apply str (repeat (- scale (count s)) "0")) s)
                     (subs s fractional-start)))))

(s/defn sort-key :- Long
  [f :- FixedDecimal]
  "Produce a value to use as the sort value for the fixed-decimal. Note: this only provides for
  sorting of values with a given scale"
  (second (extract-long f)))

;;;;

(def max-scale 18)

(s/defn ^:private valid-scale? :- Boolean
  [f :- FixedDecimal]
  (< 0 (get-scale f) (inc max-scale)))

(s/defn ^:private assert-valid-scale
  [f :- FixedDecimal
   ex-data]
  (when-not (valid-scale? f)
    (throw (ex-info (str "invalid scale: " (pr-str f)) ex-data))))

(defn- is-fixed? [f]
  (cond
    (and (instance? FixedDecimal f)
         (valid-scale? f)) true
    (instance? Long f) false
    (instance? Integer f) false
    :default (throw (ex-info (str "unknown numeric type: " [f (class f)]) {:n f
                                                                           :class-n (class f)}))))

(s/defn fixed-decimal? :- Boolean
  "Is the value a fixed decimal? Only returns true for objects created by this module."
  [value :- s/Any]
  (instance? FixedDecimal value))

(s/defn ^:private assert-fixed?
  [x :- s/Any]
  (when-not (is-fixed? x)
    (throw (ex-info (str "not fixed number: " [x (class x)]) {:value x
                                                              :class (class x)}))))

(s/defn fneg? :- Boolean
  "Return true if the fixed decimal value is less than 0."
  [f :- FixedDecimal]
  (= "-" (.-sign f)))

(s/defn set-scale :- (s/conditional
                      fixed-decimal? FixedDecimal
                      :else Long)
  "Produce a fixed decimal or integer value by changing the scale of the fixed decimal number, 'f', to
  'scale'. The scale argument must be an integer. If the effect of changing the scale is to add
  precision then the original value is padded with zeros. If the effect is to reduce precision then
  the original value is truncated. If the new scale is 0, then an integer is returned."
  [f :- FixedDecimal
   scale :- s/Int]
  (when-not (or (instance? Long scale)
                (instance? Integer scale))
    (throw (ex-info (str "unexpected scale: " [scale (class scale)]) {:scale scale
                                                                      :class-scale (class scale)})))
  (when (neg? scale)
    (throw (ex-info (str "invalid scale: " scale) {:scale scale})))

  (assert-fixed? f)
  (assert-extract-long f)

  (let [current-scale (get-scale f)
        scale-difference (- scale current-scale)
        new-f (cond
                (= 0 scale) (let [long-value (parse-long (.-integer f))]
                              (if (fneg? f)
                                (- long-value)
                                long-value))

                (= 0 scale-difference) f

                (pos? scale-difference) (FixedDecimal. (.-sign f)
                                                       (.-integer f)
                                                       (str (.-fractional f) (apply str (repeat scale-difference "0"))))

                (neg? scale-difference) (FixedDecimal. (.-sign f)
                                                       (.-integer f)
                                                       (subs (.-fractional f) 0 scale)))]
    (when (fixed-decimal? new-f)
      (assert-valid-scale new-f {:string (string-representation new-f)})
      (assert-extract-long new-f))
    new-f))

(s/defn ^:private fixed :- FixedDecimal
  "Constructor function"
  [s :- String]
  (when (empty? s)
    (throw (ex-info "cannot construct fixed decimal number from empty string" {:string s})))
  (if-let [f (parse-fixed-decimal-str s)]
    (do (assert-valid-scale f {:string s})
        (let [[_ n] (extract-long f)]
          (when (and (zero? n)
                     (string/starts-with? s "-"))
            (throw (ex-info (str "cannot be negative 0: " (pr-str f)) {:string s}))))
        f)
    (throw (NumberFormatException. (str "Invalid fixed-decimal string: " s)))))

(s/defn fixed-decimal-reader :- FixedDecimal
  "Convert a value from the clojure reader into a fixed decimal value. Intended to be used to read
  clojure tagged literals. Expects a string as input"
  [s :- String]
  (let [offset (if (= (first s) \-) 1 0)]
    (when (and (= (get s offset) \0)
               (not= (get s (inc offset)) \.))
      (throw (ex-info (str "cannot have a leading 0 here: " (pr-str s)) {:string s}))))
  (fixed s))

;;;;

(defn- assert-matching-fixed? [& args]
  (when-not (and (every? is-fixed? args)
                 (apply = (map get-scale args)))
    (throw (ex-info (str "not fixed numbers with matching scale: " [args (map class args)]) {:args args
                                                                                             :classes (map class args)}))))

(let [f-op (fn [op]
             (fn [& args]
               (apply assert-matching-fixed? args)
               (let [s (get-scale (first args))]
                 (package-long s (apply op (map (comp second extract-long) args))))))]
  "Define addition and substraction on fixed decimal arguments. Overflows according to the size of a Long with positions
  carved out for decimal places."
  (def f+ (f-op +))
  (def f- (f-op -)))

(let [f-comp (fn [op]
               (fn [& args]
                 (apply assert-matching-fixed? args)
                 (apply op (map (comp second extract-long) args))))]
  "Define inequality functions on fixed decimal arguments."
  (def f< (f-comp <))
  (def f<= (f-comp <=))
  (def f> (f-comp >))
  (def f>= (f-comp >=)))

(s/defn fabs :- FixedDecimal
  "Return absolue value of argument as a fixed decimal."
  [f :- FixedDecimal]
  (assert-fixed? f)
  (let [new-f (FixedDecimal. "" (.-integer f) (.-fractional f))]
    (assert-extract-long new-f)
    new-f))

(s/defn f* :- FixedDecimal
  "Muliply the first argument, a fixed decimal, by all of the other arguments, which must be
  integers. Return a fixed decimal value. Overflows according to the size of a Long with positions
  carved out for decimal places."
  [f :- FixedDecimal
   & args :- [s/Int]]
  (when-not (and (is-fixed? f)
                 (not (some is-fixed? args)))
    (throw (ex-info (str "not fixed and integer types: " [f (class f) args (map class args)])
                    {:f f
                     :class-f (class f)
                     :args args
                     :class-args (map class args)})))
  (let [[scale n] (extract-long f)]
    (package-long scale (apply * n args))))

(s/defn fquot :- FixedDecimal
  "Divide the first argument, a fixed decimal, by all of the other arguments, which must be
  integers. Return a fixed decimal value. Truncates the results to the precision of the original
  fixed decimal."
  [f :- FixedDecimal
   & args :- [s/Int]]
  (when-not (and (is-fixed? f)
                 (not (some is-fixed? args)))
    (throw (ex-info (str "not fixed and integer types: " [f (class f) args (map class args)])
                    {:f f
                     :class-f (class f)
                     :args args
                     :class-args (map class args)})))
  (let [[scale n] (extract-long f)]
    (package-long scale (long
                         (reduce (fn [r arg]
                                   (when (and (= r Long/MIN_VALUE)
                                              (= arg -1))
                                     (throw (ex-info "overflow" {:overflow? true})))
                                   (/ r arg))
                                 n
                                 args)))))

(s/defn shift-scale :- (s/conditional
                        fixed-decimal? FixedDecimal
                        :else Long)
  "Shift the decimal point to the right. Can only shift up to the scale of the fixed-decimal."
  [f :- FixedDecimal
   shift :- s/Int]
  (when (neg? shift)
    (throw (ex-info (str "shift amount cannot be negative: " shift) {:f f
                                                                     :shift shift})))
  (let [[scale n] (extract-long f)]
    (cond
      (> shift scale) (throw (ex-info (str "invalid scale") {:f f :shift shift}))
      (= shift scale) n
      :else (package-long (- scale shift) n))))

(defn init []
  ;; this is here for other modules to call to force this namespace to be loaded
  )
