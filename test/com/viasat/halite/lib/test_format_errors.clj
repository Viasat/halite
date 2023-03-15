;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.lib.test-format-errors
  (:require [clojure.test :refer :all]
            [com.viasat.halite.lib.format-errors :as format-errors]
            [schema.core :as s]
            [clojure.tools.logging :as log]))

(set! *warn-on-reflection* true)

(deftest test-truncate
  (is (= (str (apply str (repeat 2045 \a)) "...")
         (#'format-errors/truncate-msg (apply str (repeat 2096 \a))))))

(format-errors/merge-field-map {:type s/Symbol})

(deftest test-analyze-runtime-usage
  (binding [*ns* (the-ns 'com.viasat.halite.lib.test-format-errors)]
    (with-redefs [format-errors/trace-err-defs? true
                  format-errors/trace-atom (atom [])
                  format-errors/field-map-atom (atom {})]

      (eval
       '(format-errors/deferr test-err [data]
                              {:template "This is error is just a test: :mystr, :mynil, :mything"
                               :extra :stuff}))

      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"This is error is just a test: \"this is mystr\""
                            (eval '(format-errors/with-exception-data {:more :data}
                                     (format-errors/throw-err (test-err {:mystr "this is mystr"
                                                                         :mynil nil
                                                                         :mything [#{(list 5)}]}))))))

      (is (= '[{test-format-errors {test-err {:mystr #{java.lang.String},
                                              :mynil #{nil},
                                              :mything #{[#{(java.lang.Long)}]}}}}
               {:mystr #{java.lang.String},
                :mynil #{nil},
                :mything #{[#{(java.lang.Long)}]}}]
             (format-errors/analyze-runtime-usage)))

      (is (= '{:err-defs
               {test-format-errors/test-err
                {:message "This is error is just a test: :mystr, :mynil, :mything",
                 :fields #{:mything :mynil :mystr}}},
               :fields {:more 1, :mything 1, :mynil 1, :mystr 1},
               :field-index
               {:mything #{test-format-errors/test-err},
                :mynil #{test-format-errors/test-err},
                :mystr #{test-format-errors/test-err}},
               :systems {"test-format-errors" #{com.viasat.halite.lib.test-format-errors}}}
             (format-errors/analyze-err-defs)))

      (is (= '{test-format-errors [test-err]}
             (format-errors/assemble-err-ids)))

      (is (= ["This is error is just a test: :mystr, :mynil, :mything"]
             (format-errors/assemble-err-messages))))))

(deftest test-check-data
  (with-redefs [log/log* (fn [logger level t message]
                           (is (re-find #"invalid type for field ':type'" message)))]
    (is (thrown-with-msg? Exception #"schema failure on exception data"
                          (binding [format-errors/*throw-on-schema-failure* true]
                            (format-errors/throw-err {:type "bad type data"}))))))

;; (run-tests)
