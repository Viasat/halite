;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.lib.fixed-decimal
  "Provides a fixed decimal number library for basic math and comparisons. Treats fixed decimal
  numbers of a given precision as a separate universe of numerical values. That is, they are only
  meant to be used with other numbers of the same precision. Math operations are performed as if
  they were operating with truncating semantics on integers, except the decimal place is added at
  the appropriate place. Multiplication and division can only be performed with integer values. The
  'backing store' is treated as a Long, so if the Long would overflow, then the fixed decimal
  operation overflows."
  (:require [clojure.string :as string]
            [internal :as s])
  (:import [java.math BigDecimal RoundingMode]))

(set! *warn-on-reflection* true)

(defprotocol FixedDecimalContents
  (string-representation [this]))

(deftype FixedDecimal [value]
  FixedDecimalContents
  (string-representation [_]
    value)

  Object
  (equals [_ other]
    (and (instance? FixedDecimal other)
         (= value (.value ^FixedDecimal other))))
  (hashCode [_]
    (.hashCode value)))

(def ^:dynamic *reader-symbol* 'd)

(defn print-fixed [fixed ^java.io.Writer writer]
  (.write writer (str "#" *reader-symbol* " \"" (string-representation fixed) "\"")))

(defmethod print-method FixedDecimal [fixed writer]
  (print-fixed fixed writer))

(defmethod print-dup FixedDecimal [fixed writer]
  (print-fixed fixed writer))

(.addMethod ^clojure.lang.MultiFn clojure.pprint/simple-dispatch FixedDecimal
            (fn [fixed]
              (print-fixed fixed *out*)))

;;;;

(s/defn- fixed->BigDecimal :- BigDecimal
  [f :- FixedDecimal]
  (let [s (string-representation f)]
    (when (nil? (re-matches #"-?[0-9]+.?[0-9]*" s))
      ;; manually throw NumberFormatException rather than relying on BigDecimal, because the error
      ;; message from some JVMs was nil in this case
      (throw (NumberFormatException. (str "Character is neither a decimal digit number nore a decimal point: " s))))
    (BigDecimal. ^String s)))

(def max-scale 18)

(s/defn- valid-scale? :- Boolean
  [f :- FixedDecimal]
  (let [n (fixed->BigDecimal f)]
    (< 0 (.scale ^BigDecimal n) (inc max-scale))))

(s/defn- ten-to-the :- s/Int
  [n :- s/Int]
  (cond (zero? n) 1
        (pos? n)  (reduce * 1 (repeat n     10))
        (neg? n)  (reduce / 1 (repeat (- n) 10))))

(s/defn- extract-long-from-big-decimal :- s/Int
  [bd :- BigDecimal]
  (let [scale (.scale ^BigDecimal bd)]
    (-> bd
        ^BigDecimal (* (ten-to-the scale))
        (.setScale 0)
        .toPlainString
        Long/parseLong)))

(s/defn- extract-long :- [(s/one s/Int :scale)
                          (s/one Long :long)]
  [f :- FixedDecimal]
  (let [n (fixed->BigDecimal f)
        scale (.scale ^BigDecimal n)]
    ;; this verbose approach is used to convert the bigdecimal to a long, because the built-in
    ;; 'long' function exhibited odd behaviors near the long limits
    ;; e.g. (long 922337203685477580.7M) => 922337203685477632 ?!
    [scale (extract-long-from-big-decimal n)]))

(s/defn get-scale :- Integer
  "Retrieve the number of decimal places in the fixed decimal value."
  [f :- FixedDecimal]
  (let [bd (fixed->BigDecimal f)]
    (.scale ^BigDecimal bd)))

(s/defn fneg? :- Boolean
  "Return true if the fixed decimal value is less than 0."
  [f :- FixedDecimal]
  (let [[_ n] (extract-long f)]
    (neg? n)))

(s/defn- fixed :- FixedDecimal
  "Constructor function"
  [s :- String]
  (when (empty? s)
    (throw (ex-info "cannot construct fixed decimal number from empty string" {:string s})))
  (let [f (FixedDecimal. s)]
    (when-not (valid-scale? f)
      (throw (ex-info (str "invalid scale: " (pr-str f)) {:string s})))
    (let [[_ n] (extract-long f)]
      (when (and (zero? n)
                 (string/starts-with? s "-"))
        (throw (ex-info (str "cannot be negative 0: " (pr-str f)) {:string s}))))
    (extract-long f) ;; check for overflow
    f))

(s/defn- BigDecimal->fixed :- FixedDecimal
  [bd :- BigDecimal]
  (fixed (str bd)))

(s/defn fixed-decimal-reader :- FixedDecimal
  "Convert a value from the clojure reader into a fixed decimal value. Intended to be used to read
  clojure tagged literals. Expects a string as input"
  [s :- String]
  (fixed s))

(s/defn fixed-decimal? :- Boolean
  "Is the value a fixed decimal? Only returns true for objects created by this module."
  [value :- s/Any]
  (instance? FixedDecimal value))

;;;;

(s/defn- package-long :- FixedDecimal
  [scale :- s/Int
   n :- Long]
  (BigDecimal->fixed (/ (.setScale (bigdec n) scale)
                        (ten-to-the scale))))

(defn- is-fixed? [f]
  (cond
    (and (instance? FixedDecimal f)
         (valid-scale? f)) true
    (instance? Long f) false
    (instance? Integer f) false
    :default (throw (ex-info (str "unknown numeric type: " [f (class f)]) {:n f
                                                                           :class-n (class f)}))))

(s/defn- assert-fixed?
  [x :- s/Any]
  (when-not (is-fixed? x)
    (throw (ex-info (str "not fixed number: " [x (class x)]) {:value x
                                                              :class (class x)}))))

(defn- assert-matching-fixed? [& args]
  (when-not (and (every? is-fixed? args)
                 (apply = (map #(.scale ^BigDecimal (fixed->BigDecimal %)) args)))
    (throw (ex-info (str "not fixed numbers with matching scale: " [args (map class args)]) {:args args
                                                                                             :classes (map class args)}))))

(let [f-op (fn [op]
             (fn [& args]
               (apply assert-matching-fixed? args)
               (let [[s _] (extract-long (first args))]
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
  (let [[scale n] (extract-long f)]
    (package-long scale (abs n))))

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
    (package-long scale (long (apply / n args)))))

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
  (extract-long f) ;; as an additional assertion
  (let [bd (fixed->BigDecimal f)
        result-big-decimal (cond
                             (instance? Long scale) (.setScale ^BigDecimal bd ^Long scale RoundingMode/DOWN)
                             (instance? Integer scale) (.setScale ^BigDecimal bd ^Integer scale RoundingMode/DOWN))]
    (if (pos? scale)
      (BigDecimal->fixed result-big-decimal)
      (extract-long-from-big-decimal result-big-decimal))))
