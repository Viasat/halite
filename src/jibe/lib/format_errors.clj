;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.lib.format-errors
  (:require [clojure.pprint :as pprint]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [schema.core :as s])
  (:import [clojure.lang Namespace]
           [java.lang StackTraceElement]))

(set! *warn-on-reflection* true)

(def trace-err-defs? false)

(def trace-atom (atom []))

(defn extract-system-name-from-err-id [err-id]
  (second (re-matches #"([a-z]+)-[a-z-]+" (name err-id))))

(defn update-fields [fields new-fields]
  (loop [m fields
         [f & more-f] new-fields]
    (let [m' (update m f #(if % (inc %) 1))]
      (if more-f
        (recur m' more-f)
        m'))))

(defn analyze-err-defs []
  (loop [err-defs {}
         fields {}
         field-index {}
         systems {}
         [t & more-t] @trace-atom]
    (let [[err-defs' fields' field-index' systems']
          (condp = (second t)
            :deferr (let [[ns-name _ err-id message] t]
                      (when (contains? err-defs err-id)
                        (throw (ex-info "duplicate err-id" {:err-id err-id})))
                      [(assoc err-defs err-id {:message message
                                               :fields #{}})
                       fields
                       field-index
                       (update systems (extract-system-name-from-err-id err-id) #(if % (conj % ns-name) #{ns-name}))])

            :throw-err (let [[ns-name _ err-id data] t
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
  (update-vals (group-by extract-system-name-from-err-id (keys (:err-defs (analyze-err-defs)))) (comp vec sort)))

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

(defmacro deferr [err-id [data-arg] data]
  (when trace-err-defs?
    (let [t [(str (.name *ns*)) :deferr err-id (:message data)]]
      (swap! trace-atom conj t)))
  `(defn ~err-id [~data-arg]
     (merge ~data-arg
            ~(merge {:err-id (keyword (last (string/split (str (ns-name *ns*)) #"\."))
                                      (name err-id))
                     :user-visible-error? true}
                    data))))

(defn extend-err-data [data]
  (-> (merge (-> data :form meta (select-keys [:row :col :end-row :end-col]))
             data)
      (dissoc :message)
      (assoc :message-template (:message data))))

(defn format-msg* [msg-str data-map]
  (-> msg-str
      (string/replace
       #"(?:^|\s|[^a-zA-Z0-9]):([a-zA-Z][a-zA-Z0-9-]*)"
       (fn [[k n]]
         (let [v (get data-map (keyword n))]
           (when (nil? v)
             (log/error (str "string template key not found: " {:key n :msg-str msg-str})))
           (string/replace k (str ":" n) (or v (str ":" n))))))
      (string/replace "<colon>" ":")))

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

(defn format-data-map [data-map]
  (->> data-map
       (mapcat (fn [[k v]]
                 [k (if (or (seq? v) (vector? v) (set? v))
                      (string/join ", " (map pr-str v))
                      (pr-str v))]))
       (apply hash-map)))

(defn format-msg [msg-str data-map]
  (format-msg* msg-str (format-data-map data-map)))

(defn site-code [^Namespace ns form]
  (str (mod (.hashCode (str (ns-name ns))) 1000) "-" (:line (meta form))))

(def ^:dynamic *squash-throw-site* false)

(defmacro throw-err
  ([data]
   (when trace-err-defs?
     (let [t [(str (.name *ns*)) :throw-err (first data) (second data)]]
       (swap! trace-atom conj t)))
   `(let [data# ~data
          site-code# ~(site-code *ns* &form)]
      (throw (ex-info (str (namespace (:err-id data#)) "/" (name (:err-id data#)) " " (if *squash-throw-site*
                                                                                        "0-0"
                                                                                        site-code#) " : " (format-msg (:message data#) data#))
                      (assoc (extend-err-data data#)
                             :throw-site (if *squash-throw-site*
                                           "0-0"
                                           site-code#))))))
  ([data ex]
   (when trace-err-defs?
     (let [t [(str (.name *ns*)) :throw-err (first data) (second data)]]
       (swap! trace-atom conj t)))
   `(let [data# ~data
          site-code# ~(site-code *ns* &form)]
      (throw (ex-info (str (namespace (:err-id data#)) "/" (name (:err-id data#)) " " (if *squash-throw-site*
                                                                                        "0-0"
                                                                                        site-code#) " : " (format-msg (:message data#) data#))
                      (assoc (extend-err-data data#)
                             :throw-site (if *squash-throw-site*
                                           "0-0"
                                           site-code#))
                      ~ex)))))

(defmacro with-exception-data
  "Merge extra-data into any ex-info thrown from inside body"
  ([& args]
   (let [[message extra-data body] (if (map? (first args))
                                     [nil (first args) (rest args)]
                                     [(first args) (second args) (rest (rest args))])]
     (when trace-err-defs?
       (let [t [(str (.name *ns*)) :with-exception-data extra-data]]
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
