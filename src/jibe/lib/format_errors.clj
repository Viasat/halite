;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.lib.format-errors
  (:require [clojure.string :as string]
            [schema.core :as s]))

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

  (clojure.pprint/pprint (:systems (analyze-err-defs)))
  (clojure.pprint/pprint (assemble-err-ids))
  (map prn (assemble-err-messages))

  (clojure.pprint/pprint (fields-by-frequency))
  (clojure.pprint/pprint (fields-by-name))

  (clojure.pprint/pprint (:field-index (analyze-err-defs)))
  (clojure.pprint/pprint (field-used-by ...)))

(defmacro deferr [err-id [data-arg] data]
  (when trace-err-defs?
    (let [t [(str (.name *ns*)) :deferr err-id (:message data)]]
      (swap! trace-atom conj t)))
  `(defn ~err-id [~data-arg]
     (merge ~data-arg
            ~(merge {:err-id (keyword (name err-id))
                     :user-visible-error? true}
                    data))))

(defn extend-err-data [data]
  (-> (merge (-> data :form meta (select-keys [:row :col :end-row :end-col]))
             data)
      (dissoc :message)))

(defn format-msg* [msg-str data-map]
  (string/replace msg-str
                  #":([a-zA-Z][a-zA-Z0-9-]*)"
                  (fn [[k n]]
                    (get data-map (keyword n) k))))

(defn format-data-map [data-map]
  (->> data-map
       (mapcat (fn [[k v]]
                 [k (cond
                      (or (seq? v) (vector? v) (set? v)) (string/join ", " (map pr-str v))
                      (= :position k) (condp = v
                                        nil "Argument"
                                        0 "First argument"
                                        1 "Second argument"
                                        "An argument")
                      (= :expected-type-description k) v
                      :default (pr-str v))]))
       (apply hash-map)))

(defn format-msg [msg-str data-map]
  (format-msg* msg-str (format-data-map data-map)))

(defmacro throw-err
  ([data]
   (when trace-err-defs?
     (let [t [(str (.name *ns*)) :throw-err (first data) (second data)]]
       (swap! trace-atom conj t)))
   `(let [data# ~data]
      (throw (ex-info (str (name (:err-id data#)) " : " (format-msg (:message data#) data#))
                      (extend-err-data data#)))))
  ([data ex]
   (when trace-err-defs?
     (let [t [(str (.name *ns*)) :throw-err (first data) (second data)]]
       (swap! trace-atom conj t)))
   `(let [data# ~data]
      (throw (ex-info (str (name (:err-id data#)) " : " (format-msg (:message data#) data#))
                      (extend-err-data data#)
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
