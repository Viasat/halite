;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.lib.format-errors
  (:require [clojure.string :as string]
            [schema.core :as s]))

(defmacro deferr [err-id [data-arg] data]
  `(defn ~err-id [~data-arg]
     (merge ~data-arg
            ~(merge {:err-id (keyword (name err-id))
                     :user-visible-error? true}
                    data))))

(defn extend-err-data [data]
  (-> (merge (-> data :form meta (select-keys [:row :col :end-row :end-col]))
             data)
      (dissoc :msg)))

(defmacro throw-err [data]
  `(let [data# ~data]
     (throw (ex-info (:msg data#)
                     (extend-err-data data#)))))


(defmacro with-exception-data
  "Merge extra-data into any ex-info thrown from inside body"
  [extra-data & body]
  (assert (map? extra-data))
  `(try
     ~@body
     (catch clojure.lang.ExceptionInfo e#
       (throw (ex-info (.getMessage e#)
                       (merge ~extra-data (ex-data e#))
                       e#)))))
