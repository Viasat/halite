;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.doc.md-err
  (:require [com.viasat.halite.doc.doc-util :as doc-util]))

(set! *warn-on-reflection* true)

(defn err-md [lang {:keys [prefix get-link-f]} err-id err]
  (->> ["### "
        "<a name=\"" (doc-util/safe-op-anchor err-id) "\"></a>"
        err-id "\n\n" (:doc err) "\n\n"
        "#### Error message template:" "\n\n"
        "> " (:template err)
        "\n\n"
        (when-let [thrown-bys (:thrown-by-basic err)]
          ["#### Produced by elements:\n\n"
           (for [a (sort thrown-bys)]
             (str "* " "[`" a "`](" (get-link-f lang prefix nil "basic-syntax-reference")
                  "#" (doc-util/safe-op-anchor a) ")" "\n"))
           "\n"])
        (when-let [thrown-bys (if (= :halite lang)
                                (:thrown-by err)
                                (:thrown-by-j err))]
          ["#### Produced by operators:\n\n"
           (for [a (sort thrown-bys)]
             (str "* " "[`" a "`](" (get-link-f lang prefix nil "full-reference")
                  "#" (doc-util/safe-op-anchor a) ")" "\n"))
           "\n"])
        (when-let [alsos (:err-ref err)]
          ["See also:"
           (for [a (sort alsos)]
             [" [`" a "`](#" (doc-util/safe-op-anchor a) ")"])
           "\n\n"])
        "---\n"]))

(defn err-md-all [lang {:keys [generate-hdr-f] :as config} err-maps]
  (->> [(generate-hdr-f "Halite Error ID Reference" (str "halite_err-id-reference" (doc-util/get-language-modifier lang)) (str "/" (name lang)) "Halite err-id reference")
        "# "
        (doc-util/lang-str lang)
        " err-id reference\n\n"
        (->> err-maps
             (map (partial apply err-md lang config)))]
       flatten
       (apply str)))
