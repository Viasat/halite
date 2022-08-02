;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.lib.format-errors
  (:require [clojure.string :as string]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(defmacro deferr [err-id [data-arg] data]
  `(defn ~err-id [~data-arg]
     (merge ~data-arg
            ~(merge {:err-id (keyword (name err-id))
                     :user-visible-error? true}
                    data))))

(defn extend-err-data [data]
  (-> (merge (-> data :form meta (select-keys [:row :col :end-row :end-col]))
             data)
      (dissoc :message)))

(defmacro throw-err
  ([data]
   `(let [data# ~data]
      (throw (ex-info (:message data#)
                      (extend-err-data data#)))))
  ([data ex]
   `(let [data# ~data]
      (throw (ex-info (:message data#)
                      (extend-err-data data#)
                      ~ex)))))

(defmacro with-exception-data
  "Merge extra-data into any ex-info thrown from inside body"
  ([& args]
   (let [[message extra-data body] (if (map? (first args))
                                     [nil (first args) (rest args)]
                                     [(first args) (second args) (rest (rest args))])]
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
