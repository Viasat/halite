;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite-base
  (:require [jibe.h-err :as h-err]
            [jibe.halite.halite-types :as halite-types]
            [jibe.lib.fixed-decimal :as fixed-decimal]
            [jibe.lib.format-errors :refer [throw-err]]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(defn integer-or-long? [value]
  (or (instance? Long value)
      (instance? Integer value)))

(defn fixed-decimal? [value]
  (fixed-decimal/fixed-decimal? value))

;;;;

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
  [inst spec-type :- halite-types/HaliteType]
  (let [spec-id (halite-types/spec-id spec-type)]
    (or (= spec-id (:$type inst))
        (boolean (get (:refinements (meta inst)) spec-id)))))

;;

(s/defschema FnSignature
  {:arg-types [halite-types/HaliteType]
   (s/optional-key :variadic-tail) halite-types/HaliteType
   :return-type halite-types/HaliteType})

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
