;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.halite-docs
  (:require [clojure.string :as string])
  (:import [net.nextencia.rrdiagram.grammar.model GrammarToRRDiagram BNFToGrammar]
           [net.nextencia.rrdiagram.grammar.rrdiagram RRDiagramToSVG]))

(set! *warn-on-reflection* true)

(def op-maps
  {'$no-value {:sigs [["" "unset"]]}
   '$this {:sigs [["" "value"]]}
   '* {:sigs [["<integer> <integer>" "integer"]
              ["<fixed-decimal> <integer>" "fixed-decimal"]]}
   '+ {:sigs [["<integer> <integer> {<integer>}" "integer"]
              ["<fixed-decimal> <fixed-decimal> {<fixed-decimal>}" "fixed-decimal"]]}
   '- {:sigs [["<integer> <integer> {<integer>}" "integer"]
              ["<fixed-decimal> <fixed-decimal> {<fixed-decimal>}" "fixed-decimal"]]}
   '< {:sigs [["((<integer> <integer>) | (<fixed-decimal> <fixed-decimal>))" "boolean"]]}
   '<= {:sigs [["((<integer> <integer>) | (<fixed-decimal> <fixed-decimal>))" "boolean"]]}
   '= {:sigs [["<value> <value> {<value>}" "boolean"]]}
   '=> {:sigs [["<boolean> <boolean>" "boolean"]]}
   '> {:sigs [["((<integer> <integer>) | (<fixed-decimal> <fixed-decimal>))" "boolean"]]}
   '>= {:sigs [["((<integer> <integer>) | (<fixed-decimal> <fixed-decimal>))" "boolean"]]}
   'abs {:sigs [["<integer>" "integer"]
                ["<fixed-decimal>" "fixed-decimal"]]}
   'and {:sigs [["<boolean> {<boolean>}" "boolean"]]}
   'any? {:sigs [["'[' symbol (<set> | <vector>) ']' boolean-expression" "boolean"]]}
   'concat {:sigs [["<vector> <vector>" "vector"]
                   ["(<set> <set> | <set> <vector>)" "set"]]}
   'conj {:sigs [["<set> <value> {<value>}" "set"]
                 ["<vector> <value> {<value>}" "vector"]]}
   'contains? {:sigs [["<set> <value>" "boolean"]]}
   'count {:sigs [["(<set> | <vector>)" "integer"]]}
   'dec {:sigs [["<integer>" "integer"]]}
   'difference {:sigs [["<set> <set>" "set"]]}
   'div {:sigs [["<integer> <integer>" "integer"]
                ["<fixed-decimal> <integer>" "fixed-decimal"]]}
   'error {:sigs [["<string>" "nothing"]]}
   'every? {:sigs [["'[' symbol (<set> | <vector>) ']' boolean-expression" "boolean"]]}
   'expt {:sigs [["<integer> <integer>" "integer"]]}
   'filter {:sigs [["'[' symbol:element <set> ']' boolean-expression" "set"]
                   ["'[' symbol:element <vector> ']' boolean-expression" "vector"]]}
   'first {:sigs [["<vector>" "value"]]}
   'get {:sigs [["(<instance> instance-field-name)" "any"]
                ["(<vector> <integer>)" "value"]]}
   'get-in {:sigs [["(<instance> | <vector>) '[' (<integer> | instance-field-name) {(<integer> | instance-field-name)} ']'" "any"]]
            :notes ["if the last element of the lookup path is an integer, then the result is a value"
                    "if the last element of the lookup path is an instance field name, then the result is an 'any'; specifically of that last field is the name of an optional field"
                    "the non-terminal field names in the lookup path must be the names of mandatory fields"]}
   'if {:sigs [["<boolean> any-expression any-expression" "any"]]}
   'if-value {:sigs [["symbol any-expression any-expression" "any"]]}
   'if-value-let {:sigs [["'[' symbol <any> ']' any-expression any-expression" "any"]]}
   'inc {:sigs [["<integer>" "integer"]]}
   'intersection {:sigs [["<set> <set> {<set>}" "set"]]}
   'let {:sigs [["'[' symbol <value> {symbol <value>} ']' any-expression" "any"]]}
   'map {:sigs [["'[' symbol:element <set> ']' value-expression" "set"]
                ["'[' symbol:element <vector> ']' value-expression" "vector"]]}
   'mod {:sigs [["<integer> <integer>" "integer"]]}
   'not {:sigs [["<boolean>" "<boolean>"]]}
   'not= {:sigs [["<value> <value> {<value>}" "boolean"]]}
   'or {:sigs [["<boolean> {<boolean>}" "boolean"]]}
   'range {:sigs [["<integer>:start [<integer>:end [<integer>:increment]]" "vector"]]}
   'reduce {:sigs [["'[' symbol:accumulator <value>:accumulator-init ']' '[' symbol:element (set | vector) ']' any-expression" "any"]]
            :notes ["'normally' a reduce will produce a value, but the body could produce a 'maybe' value or even always produce 'unset', in which case the reduce may not produce a value"]}
   'refine-to {:sigs [["<instance> spec-id" "instance"]], :throws []}
   'refines-to? {:sigs [["<instance> spec-id" "boolean"]]}
   'rescale {:sigs [["<fixed-decimal> <integer>" "(fixed-decimal | integer)"]]}
   'rest {:sigs [["<vector>" "vector"]]}
   'sort {:sigs [["<set>" "set"]
                 ["<vector>" "vector"]]}
   'sort-by {:sigs [["'[' symbol:element <set> ']' integer-expression" "set"]
                    ["'[' symbol:element <vector> ']' integer-expression" "vector"]]}
   'str {:sigs [["<string> {<string>}" "string"]]}
   'subset? {:sigs [["<set> <set>" "boolean"]]}
   'union {:sigs [["<set> {<set>}" "set"]]}
   'valid {:sigs [["instance-expression" "any"]]}
   'valid? {:sigs [["instance-expression" "boolean"]]}
   'when {:sigs [["<boolean> any-expression" "any"]]}
   'when-value {:sigs [["symbol any-expression" "any"]]}
   'when-value-let {:sigs [["'[' symbol <any> ']' any-expression" "any"]]}})

(defn produce-bnf-diagrams [op-maps all-filename]
  (let [op-keys (keys op-maps)
        rules-strs (->> op-maps
                        (mapcat (fn [[op {:keys [sigs]}]]
                                  (->> sigs
                                       (map (fn [i [args-bnf result-bnf]]
                                              {:op-name op
                                               :sig-index i
                                               :rule-str (str (string/replace op "=" "equal") i " = "
                                                              (when-not (string/starts-with? op "$") "'('")
                                                              "'" op "' " args-bnf
                                                              (when-not (string/starts-with? op "$") "')'")
                                                              " '»' " result-bnf ";")
                                               :rule-str-comp (str "("
                                                                   (when-not (string/starts-with? op "$") "'('")
                                                                   "'" op "' " args-bnf
                                                                   (when-not (string/starts-with? op "$") "')'")
                                                                   " '»' " result-bnf ")")})
                                            (range))))))
        single-rules-strs (->> op-maps
                               (map (fn [[op {:keys [sigs]}]]
                                      (str "("
                                           (when-not (string/starts-with? op "$") "'('")
                                           "'" op "' "
                                           " ( "
                                           (->> sigs
                                                (map (fn [[args-bnf result-bnf]]
                                                       (str "( " args-bnf
                                                            (when-not (string/starts-with? op "$") "')'")
                                                            " '»' " result-bnf " )")))
                                                (string/join " |\n"))
                                           " ) "

                                           ")"))))]
    (let [rule-str (str "RULE = "
                        "("
                        (->> rules-strs
                             (map :rule-str-comp)
                             sort
                             (string/join " |\n"))
                        ")"
                        ";")]
      (let [gtrd (GrammarToRRDiagram.)
            rts (RRDiagramToSVG.)
            rule-svg (->> rule-str
                          (.convert (BNFToGrammar.))
                          .getRules
                          (into [])
                          (map #(.convert gtrd %))
                          (map #(.convert rts %))
                          first)]
        (spit all-filename rule-svg)))
    (let [rule-str (str "RULE = "
                        "("
                        (->> single-rules-strs
                             sort
                             (string/join " |\n"))
                        ")"
                        ";")]
      (let [gtrd (GrammarToRRDiagram.)
            rts (RRDiagramToSVG.)
            rule-svg (->> rule-str
                          (.convert (BNFToGrammar.))
                          .getRules
                          (into [])
                          (map #(.convert gtrd %))
                          (map #(.convert rts %))
                          first)]
        (spit "out2.svg" rule-svg)))
    (->> rules-strs
         (map (fn [{:keys [op-name sig-index ^String rule-str]}]
                (let [gtrd (GrammarToRRDiagram.)
                      rts (RRDiagramToSVG.)
                      rule-svg (->> rule-str
                                    (.convert (BNFToGrammar.))
                                    .getRules
                                    (into [])
                                    (map #(.convert gtrd %))
                                    (map #(.convert rts %))
                                    first)]
                  (spit (str op-name "-" sig-index ".svg") rule-svg))))
         dorun)))

;; (produce-bnf-diagrams op-maps "halite.svg")
