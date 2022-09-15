;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.doc.md-err
  (:require [jibe.halite.doc.utils :as utils]))

(defn err-md [lang err-id err]
  (->> ["### "
        "<a name=\"" (utils/safe-op-anchor err-id) "\"></a>"
        err-id "\n\n" (:doc err) "\n\n"
        "#### Error message template:" "\n\n"
        "> " (:message err)
        "\n\n"
        (when-let [thrown-bys (:thrown-by-basic err)]
          ["#### Produced by elements:\n\n"
           (for [a (sort thrown-bys)]
             (str "* " "[`" a "`](" (if (= :halite lang)
                                      "halite-basic-syntax-reference.md"
                                      "jadeite-basic-syntax-reference.md")
                  "#" (utils/safe-op-anchor a) ")" "\n"))
           "\n"])
        (when-let [thrown-bys (if (= :halite lang)
                                (:thrown-by err)
                                (:thrown-by-j err))]
          ["#### Produced by operators:\n\n"
           (for [a (sort thrown-bys)]
             (str "* " "[`" a "`](" (if (= :halite lang)
                                      "halite-full-reference.md"
                                      "jadeite-full-reference.md")
                  "#" (utils/safe-op-anchor a) ")" "\n"))
           "\n"])
        (when-let [alsos (:see-also err)]
          ["See also:"
           (for [a (sort alsos)]
             [" [`" a "`](#" (utils/safe-op-anchor a) ")"])
           "\n\n"])
        "---\n"]
       flatten (apply str)))
