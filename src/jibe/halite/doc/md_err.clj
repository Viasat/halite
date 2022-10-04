;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.doc.md-err
  (:require [jibe.halite.doc.utils :as utils]))

(defn err-md [lang {:keys [mode prefix]} err-id err]
  (->> ["### "
        "<a name=\"" (utils/safe-op-anchor err-id) "\"></a>"
        err-id "\n\n" (:doc err) "\n\n"
        "#### Error message template:" "\n\n"
        "> " (:message err)
        "\n\n"
        (when-let [thrown-bys (:thrown-by-basic err)]
          ["#### Produced by elements:\n\n"
           (for [a (sort thrown-bys)]
             (str "* " "[`" a "`](" (utils/get-reference lang mode prefix "basic-syntax-reference")
                  "#" (utils/safe-op-anchor a) ")" "\n"))
           "\n"])
        (when-let [thrown-bys (if (= :halite lang)
                                (:thrown-by err)
                                (:thrown-by-j err))]
          ["#### Produced by operators:\n\n"
           (for [a (sort thrown-bys)]
             (str "* " "[`" a "`](" (utils/get-reference lang mode prefix "full-reference")
                  "#" (utils/safe-op-anchor a) ")" "\n"))
           "\n"])
        (when-let [alsos (:err-ref err)]
          ["See also:"
           (for [a (sort alsos)]
             [" [`" a "`](#" (utils/safe-op-anchor a) ")"])
           "\n\n"])
        "---\n"]))

(defn err-md-all [lang {:keys [mode] :as config} err-maps]
  (->> [(when (= :user-guide mode)
          (utils/generate-user-guide-hdr "Halite Error ID Reference" "halite_err-id-reference" (str "/" (name lang)) "Halite err-id reference"))
        utils/generated-msg
        "# "
        (utils/lang-str lang)
        " err-id reference\n\n"
        (->> err-maps
             (map (partial apply err-md lang config)))]
       flatten
       (apply str)))
