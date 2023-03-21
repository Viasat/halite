;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns load-kaocha
  (:require [kaocha.repl]
            [kaocha.stacktrace]))

(defn run [& args]
  (binding [kaocha.stacktrace/*stacktrace-filters* (conj kaocha.stacktrace/*stacktrace-filters*
                                                         "kaocha" "nrepl" "load_kaocha")]
    (apply kaocha.repl/run args)))

(in-ns 'kaocha.report)

(defn print-output [m]
  (let [output (get-in m [:kaocha/testable :kaocha.plugin.capture-output/output])
        buffer (get-in m [:kaocha/testable :kaocha.plugin.capture-output/buffer])
        out (or output (and buffer (capture/read-buffer buffer)))]
    (when (seq out)
      (println "───── Test output ───────────────────────────────────────────────────────")
      (println (str/trim-newline out))
      (println "─────────────────────────────────────────────────────────────────────────"))))
