;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.lib.format-errors
  (:require [clojure.pprint :as pprint]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [schema.core :as s])
  (:import [clojure.lang Namespace]
           [java.lang StackTraceElement]))

(set! *warn-on-reflection* true)

(def trace-err-defs? false)

(def trace-atom (atom []))

(def runtime-trace-atom (atom []))

(defn get-type-name [v]
  (cond
    (nil? v) 'nil
    (vector? v) (->> v
                     (map get-type-name)
                     set
                     vec)
    (set? v) (->> v
                  (map get-type-name)
                  set)
    (seq? v) (->> v
                  (map get-type-name)
                  set
                  (apply list))
    :default (symbol (.getName (class v)))))

(defn analyze-runtime-usage []
  (loop [[t & more-t] @runtime-trace-atom
         type-by-err-id {}
         type-by-field {}]
    (if t
      (let [[field-name err-id _ data-value] t
            data-type (get-type-name data-value)]
        (recur more-t
               (update-in type-by-err-id
                          [(symbol (namespace err-id)) (symbol (name err-id)) field-name]
                          #(if % (conj % data-type) #{data-type}))
               (update-in type-by-field
                          [field-name]
                          #(if % (conj % data-type) #{data-type}))))
      [type-by-err-id
       type-by-field])))

(def field-map-atom (atom {}))

(defn merge-field-map [field-map]
  (swap! field-map-atom merge field-map))

(def ^:dynamic *throw-on-schema-failure* false)

(defn check-data [data-map]
  (->> data-map
       (map (fn [[k v]]
              (when-let [field-schema (get @field-map-atom k)]
                (when-let [schema-failure (s/check field-schema v)]
                  (log/error (str "exception data of invalid type for field '" k "': " (pr-str schema-failure)))
                  (when *throw-on-schema-failure*
                    (throw (ex-info "schema failure on exception data" {:key k
                                                                        :value v
                                                                        :schema-failure schema-failure})))))))
       dorun))

(defn extract-system-name-from-err-id [err-id]
  (symbol (namespace err-id)))

(defn update-fields [fields new-fields]
  (loop [m fields
         [f & more-f] new-fields]
    (let [m' (update m f #(if % (inc %) 1))]
      (if more-f
        (recur m' more-f)
        m'))))

(defn with-prefix [err-id ns-name]
  (let [system-name (last (string/split (str ns-name) #"\."))]
    [system-name (symbol system-name (str err-id))]))

(defn analyze-err-defs []
  (loop [err-defs {}
         fields {}
         field-index {}
         systems {}
         [t & more-t] @trace-atom]
    (let [[err-defs' fields' field-index' systems']
          (condp = (second t)
            :deferr (let [[ns-name _ err-id message] t
                          [system-name err-id] (with-prefix err-id ns-name)]
                      (when (contains? err-defs err-id)
                        (throw (ex-info "duplicate err-id" {:err-id err-id})))
                      [(assoc err-defs err-id {:message message
                                               :fields #{}})
                       fields
                       field-index
                       (update systems system-name #(if % (conj % ns-name) #{ns-name}))])

            :throw-err (let [[ns-name _ err-id data] t
                             [system-name err-id] (with-prefix err-id ns-name)
                             new-fields (if (map? data)
                                          (set (keys data))
                                          #{'?})]
                         [(update-in err-defs [err-id :fields] into new-fields)
                          (update-fields fields new-fields)
                          (loop [m field-index
                                 [f & more-f] new-fields]
                            (let [m' (update m f #(if % (conj % err-id) #{err-id}))]
                              (if more-f
                                (recur m' more-f)
                                m')))
                          systems])

            :with-exception-data (let [[ns-name _ data] t
                                       new-fields (if (map? data)
                                                    (set (keys data))
                                                    #{'?})]
                                   [err-defs
                                    (update-fields fields new-fields)
                                    field-index
                                    systems]))]
      (if more-t
        (recur err-defs' fields' field-index' systems' more-t)
        {:err-defs err-defs'
         :fields fields'
         :field-index field-index'
         :systems systems'}))))

(defn assemble-err-ids []
  (-> (group-by extract-system-name-from-err-id (keys (:err-defs (analyze-err-defs))))
      (update-vals (comp vec sort (partial map (comp symbol name))))))

(defn find-string [x]
  (cond
    (string? x) x
    (and (list? x) (= 'format (first x))) (find-string (second x))
    :else ""))

(defn assemble-err-messages []
  (vec (sort-by find-string
                (map :message (vals (:err-defs (analyze-err-defs)))))))

(defn field-used-by [field]
  (get (:field-index (analyze-err-defs)) field))

(defn fields-by-frequency []
  (vec (reverse (sort-by second (:fields (analyze-err-defs))))))

(defn fields-by-name []
  (vec (sort-by (comp str first) (:fields (analyze-err-defs)))))

(comment
  (analyze-err-defs)

  (pprint/pprint (:systems (analyze-err-defs)))
  (pprint/pprint (assemble-err-ids))
  (map prn (assemble-err-messages))

  (pprint/pprint (fields-by-frequency))
  (pprint/pprint (fields-by-name))

  (pprint/pprint (:field-index (analyze-err-defs)))
  (pprint/pprint (field-used-by ...)))

(defonce error-atom (atom {}))

(defmacro deferr [err-id [data-arg] data]
  (let [computed-err-id (keyword (last (string/split (str (ns-name *ns*)) #"\."))
                                 (name err-id))]
    (swap! error-atom assoc (symbol computed-err-id) (assoc data
                                                            :ns-name (ns-name *ns*)))
    (when trace-err-defs?
      (let [t [(ns-name *ns*) :deferr err-id (:template data)]]
        (swap! trace-atom conj t)))
    `(defn ~err-id [~data-arg]
       (merge ~data-arg
              ~(merge {:err-id computed-err-id
                       :user-visible-error? true}
                      data)))))

(defn extend-err-data [data]
  (merge (-> data :form meta (select-keys [:row :col :end-row :end-col]))
         data))

(defn format-msg* [err-id msg-str data-map original-data-map]
  (-> msg-str
      (string/replace
       #"(?:^|\s|[^a-zA-Z0-9]):([a-zA-Z][a-zA-Z0-9-]*)"
       (fn [[k n]]
         (let [v (get data-map (keyword n))]
           (when (nil? v)
             (log/error (str "string template key not found: " {:err-id err-id :key n :msg-str msg-str})))
           (when trace-err-defs?
             (let [original-v (get original-data-map (keyword n))
                   t [(keyword n)
                      (symbol err-id)
                      (if (nil? original-v)
                        'nil
                        (symbol (.getName (class original-v))))
                      original-v]]
               (swap! runtime-trace-atom conj t)))
           (string/replace k (str ":" n) (or v (str ":" n))))))
      (string/replace "\\colon" ":")))

;;

(deftype Text [s]
  ;; contains a string value, which when printed omits surrounding quotes
  Object
  (equals [_ t]
    (and (isa? (class t) Text)
         (.equals s (.s ^Text t))))
  (hashCode [_]
    (.hashCode s))
  (toString [_]
    s))

(defn print-text [t ^java.io.Writer writer]
  (.write writer (str t)))

(defmethod print-method Text [t writer]
  (print-text t writer))

(defmethod print-dup Text [t writer]
  (print-text t writer))

(.addMethod ^clojure.lang.MultiFn pprint/simple-dispatch Text
            (fn [t]
              (print-text t *out*)))

(defn text
  "Package up a string such that it will be interpolated into string templates without surrounding quotes"
  [s]
  (Text. s))

;;

(defn- truncated-str
  "Convert the object x to a string, if the string is longer than n characters then truncate it and
  add the truncate-suffix to indicate it was truncated"
  [n truncate-suffix x]
  (let [result (str x)]
    (if (> (count result)
           n)
      (str (.substring result 0 (max (- n (count truncate-suffix)) 0))
           truncate-suffix)
      result)))

(defn- truncate-msg
  [msg]
  (truncated-str 2048 "..." msg))

(defn format-data-map [data-map]
  (->> data-map
       (mapcat (fn [[k v]]
                 [k (if (and (not (= :form k))
                             (or (seq? v) (vector? v) (set? v)))
                      (string/join ", " (map pr-str v))
                      (pr-str v))]))
       (apply hash-map)))

(defn format-msg [{:keys [err-id template] :as data-map}]
  (truncate-msg (format-msg* err-id template (format-data-map data-map) data-map)))

(defn site-code [^Namespace ns form]
  (str (mod (.hashCode (str (ns-name ns))) 1000) "-" (:line (meta form))))

(def ^:dynamic *squash-throw-site* false)

(defn format-long-msg [{:keys [err-id throw-site] :as data-map}]
  (truncate-msg (str (namespace err-id) "/" (name err-id) " " throw-site " : " (format-msg data-map))))

(defmacro throw-err
  [data & more]
  (when trace-err-defs?
    (let [t [(ns-name *ns*) :throw-err (first data) (second data)]]
      (swap! trace-atom conj t)))
  `(let [site-code# (if *squash-throw-site*
                      "0-0"
                      ~(site-code *ns* &form))
         data# (assoc (extend-err-data ~data) :throw-site site-code#)]
     (check-data data#)
     (throw (ex-info (format-long-msg data#)
                     data#
                     ~@more))))

(defmacro with-exception-data
  "Merge extra-data into any ex-info thrown from inside body"
  ([& args]
   (let [[message extra-data body] (if (map? (first args))
                                     [nil (first args) (rest args)]
                                     [(first args) (second args) (rest (rest args))])]
     (when trace-err-defs?
       (let [t [(ns-name *ns*) :with-exception-data extra-data]]
         (swap! trace-atom conj t)))
     (assert (map? extra-data))
     (if message
       `(try
          ~@body
          (catch clojure.lang.ExceptionInfo e#
            (throw (ex-info ~message
                            (merge (extend-err-data ~extra-data) (ex-data e#))
                            e#))))
       `(try
          ~@body
          (catch clojure.lang.ExceptionInfo e#
            (throw (ex-info (.getMessage e#)
                            (merge (extend-err-data ~extra-data) (ex-data e#))
                            e#))))))))
