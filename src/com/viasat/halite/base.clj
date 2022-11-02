;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.base
  (:require [com.viasat.halite.h-err :as h-err]
            [com.viasat.halite.types :as types]
            [com.viasat.halite.lib.fixed-decimal :as fixed-decimal]
            [com.viasat.halite.lib.format-errors :refer [throw-err]]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(defn integer-or-long? [value]
  (or (instance? Long value)
      (instance? Integer value)))

(defn fixed-decimal? [value]
  (fixed-decimal/fixed-decimal? value))

;;;;

(s/defschema UserConstraintName types/BareKeyword)

(s/defschema ConstraintName (s/conditional
                             string? s/Str
                             :else s/Keyword))

(s/defschema Limits {(s/optional-key :string-literal-length) (s/maybe s/Int)
                     (s/optional-key :string-runtime-length) (s/maybe s/Int)
                     (s/optional-key :vector-literal-count) (s/maybe s/Int)
                     (s/optional-key :vector-runtime-count) (s/maybe s/Int)
                     (s/optional-key :set-literal-count) (s/maybe s/Int)
                     (s/optional-key :set-runtime-count) (s/maybe s/Int)
                     (s/optional-key :list-literal-count) (s/maybe s/Int)
                     (s/optional-key :expression-nesting-depth) (s/maybe s/Int)})

(def ^:dynamic *limits* {:string-literal-length nil
                         :string-runtime-length nil
                         :vector-literal-count nil
                         :vector-runtime-count nil
                         :set-literal-count nil
                         :set-runtime-count nil
                         :list-literal-count nil
                         :expression-nesting-depth nil})

(s/defn check-count [object-type count-limit c context]
  (when (> (count c) count-limit)
    (throw-err (h-err/size-exceeded (merge context {:object-type object-type
                                                    :actual-count (count c)
                                                    :count-limit count-limit
                                                    :value c}))))
  c)

(s/defn check-limit [limit-key v]
  (when-let [limit (get *limits* limit-key)]
    (condp = limit-key
      :string-literal-length (check-count 'String limit v {})
      :string-runtime-length (check-count 'String limit v {})
      :vector-literal-count (check-count 'Vector limit v {})
      :vector-runtime-count (check-count 'Vector limit v {})
      :set-literal-count (check-count 'Set limit v {})
      :set-runtime-count (check-count 'Set limit v {})
      :list-literal-count (check-count 'List limit v {})))
  v)

;;

(def builtin-symbols '#{contains? dec < range sort <= * expt > mod subset? - or not >= div => inc + abs str count and error})

;;

(def reserved-words
  "Symbols beginning with $ that are currently defined by halite."
  '#{$no-value})

(def external-reserved-words
  "Any symbol beginning with $ may be defined in any future version of halite,
  except for symbols in this list, which halite itself promises not to use so
  that they can be safely added to environments by projects (such as jibe) that
  _use_ halite."
  '#{$this})

;;

(s/defn refines-to? :- Boolean
  [inst spec-type :- types/HaliteType]
  (let [spec-id (types/spec-id spec-type)]
    (or (= spec-id (:$type inst))
        (boolean (get (:refinements (meta inst)) spec-id)))))

;;

(s/defschema FnSignature
  {:arg-types [types/HaliteType]
   (s/optional-key :variadic-tail) types/HaliteType
   :return-type types/HaliteType})

(s/defschema Builtin
  {:signatures (s/constrained [FnSignature] seq)
   :impl clojure.lang.IFn})

(defn make-signatures [signatures]
  (vec (for [[arg-types return-type] (partition 2 signatures)
             :let [n (count arg-types)
                   variadic? (and (< 1 n) (= :& (nth arg-types (- n 2))))]]
         (cond-> {:arg-types (cond-> arg-types variadic? (subvec 0 (- n 2)))
                  :return-type return-type}
           variadic? (assoc :variadic-tail (last arg-types))))))

;;

(defmacro math-f [integer-f fixed-decimal-f]
  `(fn [& args#]
     (apply (if (fixed-decimal? (first args#)) ~fixed-decimal-f ~integer-f) args#)))

(def hstr  (math-f str  fixed-decimal/string-representation))
(def hneg? (math-f neg? fixed-decimal/fneg?))
(def h+    (math-f +    fixed-decimal/f+))
(def h-    (math-f -    fixed-decimal/f-))
(def h*    (math-f *    fixed-decimal/f*))
(def hquot (math-f quot fixed-decimal/fquot))
(def habs  (comp #(if (hneg? %)
                    (throw-err (h-err/abs-failure {:value %}))
                    %)
                 (math-f abs #(try (fixed-decimal/fabs %)
                                   (catch NumberFormatException ex
                                     (throw-err (h-err/abs-failure {:value %})))))))
(def h<=   (math-f <=   fixed-decimal/f<=))
(def h>=   (math-f >=   fixed-decimal/f>=))
(def h<    (math-f <    fixed-decimal/f<))
(def h>    (math-f >    fixed-decimal/f>))
