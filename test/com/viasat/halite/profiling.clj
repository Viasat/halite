;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.profiling)

;; Tools for collecting timing information on clojure functions. See comment
;; form at the end for usage examples.

(defonce infos (make-array Long/TYPE 1000))
(alter-meta! #'infos assoc :tag (type infos))

(defonce *labels (atom {}))

(defn register-offset! [label]
  (let [offset (-> (swap! *labels (fn [labels]
                                    (assoc labels label
                                           (or (get labels label)
                                               (* 2 (count labels))))))
                   (get label))]
    (aset infos offset 0)
    (aset infos (inc offset) 0)
    offset))

(defn tally! [^long calls-offset, ^long nanos-offset, ^long start-nanos]
  (aset infos calls-offset (inc (aget infos calls-offset)))
  (aset infos nanos-offset
        (+ (aget infos nanos-offset)
           (- (System/nanoTime) start-nanos))))

(defmacro defprof [label & body]
  (let [offset (register-offset! label)]
    `(let [start# (System/nanoTime)]
       (try
         ~@body
         (finally
           (tally! ~offset ~(inc offset) start#))))))

(defonce *prof-vars (atom #{}))
(def *in-out (atom []))

(defn prof-var [arg]
  (let [[v opts] (if (var? arg)
                   [arg nil]
                   [(first arg) (apply array-map (rest arg))])]
    (swap! *prof-vars conj v)
    (alter-var-root
     v
     (fn [f]
       (let [orig (-> f meta ::orig (or f))
             label (symbol (-> v meta :ns ns-name str)
                           (-> v meta :name str))
             offset (register-offset! label)
             new-fn (if (:in-out? opts)
                      (fn profiling [& args]
                        (let [start (System/nanoTime)]
                          (try
                            (let [rtn (apply orig args)]
                              (swap! *in-out conj {:var v :args args :return rtn})
                              rtn)
                            (catch Throwable ex
                              (swap! *in-out conj {:var v :args args :throw ex})
                              (throw ex))
                            (finally
                              (tally! offset (inc offset) start)))))
                      (fn profiling [& args]
                        (let [start (System/nanoTime)]
                          (try
                            (apply orig args)
                            (finally
                              (tally! offset (inc offset) start))))))]
         (vary-meta new-fn assoc ::orig orig))))))

(defn unprof-var [v]
  (alter-var-root v #(-> % meta ::orig (or %)))
  (swap! *prof-vars disj v))

(defn unprof-all-vars []
  (run! unprof-var @*prof-vars))

(defn report []
  (keep (fn [[label offset]]
          (let [calls (aget infos offset)
                nanos (aget infos (inc offset))]
            (when (pos? calls)
              {:label label :calls calls
               :total-ms (/ (quot nanos 100000) 10.0)
               :avg-ms (/ (quot (quot nanos calls) 100000)
                          10.0)})))
        (sort-by #(-> % key str) @*labels)))

(comment

  (require '[com.viasat.halite.transpile.ssa :as ssa]
           '[com.viasat.halite.types :as halite-types])

  (unprof-all-vars)
  (run! prof-var [#'halite-types/meet

                  #'ssa/find-form
                  #'ssa/form-to-ssa
                  #'ssa/form-from-ssa
                  #'ssa/ensure-node
                  #'ssa/let-to-ssa
                  #'ssa/if-to-ssa
                  #'ssa/refine-to-to-ssa
                  #'ssa/do!-to-ssa
                  #'ssa/error-to-ssa
                  #'ssa/app-to-ssa
                  #'ssa/prune-ssa-graph]))

(comment

  (clojure.pprint/print-table (jibe.util.prof/report))

  (->> @jibe.util.prof/*in-out
       (filter #(= #'xtdb.api/q (:var %)))
       (map #(:args %))
       frequencies
       (sort-by val)
       (take-last 5))

  (->> @jibe.util.prof/*in-out
       (filter #(= #'jibe.logic.snapshot-store-xtdb/xtdb-snapshot-at (:var %)))
       count)

  (->> @jibe.util.prof/*in-out
       (filter #(= #'xtdb.pull/->pull-result (:var %)))
       (map #(count (:return %)))
       frequencies
       (map (partial zipmap [:result-size :query-count]))
       (sort-by (comp - :result-size))))
