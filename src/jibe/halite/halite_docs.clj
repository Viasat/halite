;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.halite-docs
  (:require [clojure.string :as string])
  (:import [net.nextencia.rrdiagram.grammar.model GrammarToRRDiagram BNFToGrammar]
           [net.nextencia.rrdiagram.grammar.rrdiagram RRDiagramToSVG]))

(set! *warn-on-reflection* true)

(def op-maps
  {'$no-value {:sigs [["" "unset"]]
               :j-sigs [["<$no-value>" "unset"]]}
   '$this {:sigs [["" "value"]]
           :j-sigs [["<$this>" "unset"]]}
   '* {:sigs [["<integer> <integer>" "integer"]
              ["<fixed-decimal> <integer>" "fixed-decimal"]]
       :j-sigs [["<integer> '*' <integer>" "integer"]
                ["<fixed-decimal> '*' <fixed-decimal>" "fixed-decimal"]]}
   '+ {:sigs [["<integer> <integer> {<integer>}" "integer"]
              ["<fixed-decimal> <fixed-decimal> {<fixed-decimal>}" "fixed-decimal"]]
       :j-sigs [["<integer> '+' <integer>" "integer"]
                ["<fixed-decimal> '+' <fixed-decimal>" "fixed-decimal"]]}
   '- {:sigs [["<integer> <integer> {<integer>}" "integer"]
              ["<fixed-decimal> <fixed-decimal> {<fixed-decimal>}" "fixed-decimal"]]
       :j-sigs [["<integer> '-' <integer>" "integer"]
                ["<fixed-decimal> '-' <fixed-decimal>" "fixed-decimal"]]}
   '< {:sigs [["((<integer> <integer>) | (<fixed-decimal> <fixed-decimal>))" "boolean"]]
       :j-sigs [["((<integer> '<'  <integer>) | (<fixed-decimal> '<' <fixed-decimal>))" "boolean"]]}
   '<= {:sigs [["((<integer> <integer>) | (<fixed-decimal> <fixed-decimal>))" "boolean"]]
        :j-sigs [["((<integer> '<=' <integer>) | (<fixed-decimal> '<=' <fixed-decimal>))" "boolean"]]}
   '= {:sigs [["<value> <value> {<value>}" "boolean"]]
       :j-sigs [["<value> '==' <value>" "boolean"]
                ["'equalTo' '(' <value> ',' <value> {',' <value>} ')'" "boolean"]]}
   '=> {:sigs [["<boolean> <boolean>" "boolean"]]
        :j-sigs [["<boolean> '=>' <boolean>" "boolean"]]}
   '> {:sigs [["((<integer> <integer>) | (<fixed-decimal> <fixed-decimal>))" "boolean"]]
       :j-sigs [["((<integer> '>'  <integer>) | (<fixed-decimal> '>' <fixed-decimal>))" "boolean"]]}
   '>= {:sigs [["((<integer> <integer>) | (<fixed-decimal> <fixed-decimal>))" "boolean"]]
        :j-sigs [["((<integer> '>='  <integer>) | (<fixed-decimal> '>=' <fixed-decimal>))" "boolean"]]}
   'abs {:sigs [["<integer>" "integer"]
                ["<fixed-decimal>" "fixed-decimal"]]
         :j-sigs [["'abs' '(' <integer> ')'" "integer"]
                  ["'abs' '(' <fixed-decimal> ')'" "fixed-decimal"]]}
   'and {:sigs [["<boolean> {<boolean>}" "boolean"]]
         :j-sigs [["<boolean> '&&' {<boolean>}" "boolean"]]}
   'any? {:sigs [["'[' symbol (<set> | <vector>) ']' boolean-expression" "boolean"]]
          :j-sigs [["'any?' '(' symbol 'in' (<set> | <vector>) ')' boolean-expression" "boolean"]]}
   'concat {:sigs [["<vector> <vector>" "vector"]
                   ["(<set> (<set> | <vector>))" "set"]]
            :j-sigs [["<vector> '.' 'concat' '('  <vector> ')'" "vector"]
                     ["(<set> '.' 'concat' '(' (<set> | <vector>) ')')" "set"]]}
   'conj {:sigs [["<set> <value> {<value>}" "set"]
                 ["<vector> <value> {<value>}" "vector"]]
          :j-sigs [["<set> '.' 'conj' '(' <value> {',' <value>} ')'" "set"]
                   ["<vector> '.' 'conj' '(' <value> {',' <value>} ')'" "vector"]]}
   'contains? {:sigs [["<set> <value>" "boolean"]]
               :j-sigs [["<set> '.' 'contains?' '(' <value> ')'" "boolean"]]}
   'count {:sigs [["(<set> | <vector>)" "integer"]]
           :j-sigs [["(<set> | <vector>) '.' 'count()'" "integer"]]}
   'dec {:sigs [["<integer>" "integer"]]
         :j-sigs [["<integer> '-' '1' " "integer"]]}
   'difference {:sigs [["<set> <set>" "set"]]
                :j-sigs [["<set> '.' 'difference' '(' <set> ')'" "set"]]}
   'div {:sigs [["<integer> <integer>" "integer"]
                ["<fixed-decimal> <integer>" "fixed-decimal"]]
         :j-sigs [["<integer> '/' <integer>" "integer"]
                  ["<fixed-decimal> '/' <integer>" "fixed-decimal"]]}
   'error {:sigs [["<string>" "nothing"]]
           :j-sigs [["'error' '(' <string> ')'" "nothing"]]}
   'every? {:sigs [["'[' symbol (<set> | <vector>) ']' boolean-expression" "boolean"]]
            :j-sigs [["'every?' '(' symbol 'in' (<set> | <vector>) ')' boolean-expression" "boolean"]]}
   'expt {:sigs [["<integer> <integer>" "integer"]]
          :j-sigs [["'expt' '(' <integer> ',' <integer> ')'" "integer"]]}
   'filter {:sigs [["'[' symbol:element <set> ']' boolean-expression" "set"]
                   ["'[' symbol:element <vector> ']' boolean-expression" "vector"]]
            :j-sigs [["'filter' '(' symbol 'in' <set> ')' boolean-expression" "set"]
                     ["'filter' '(' symbol 'in' <vector> ')' boolean-expression" "vector"]]}
   'first {:sigs [["<vector>" "value"]]
           :j-sigs [["<vector> '.' 'first()'" "value"]]}
   'get {:sigs [["(<instance> instance-field-keyword)" "any"]
                ["(<vector> <integer>)" "value"]]
         :j-sigs [["(<instance> '.' instance-field-symbol)" "any"]
                  ["(<vector> '[' <integer> ']')" "value"]]}
   'get-in {:sigs [["(<instance> | <vector>) '[' (<integer> | instance-field-keyword) {(<integer> | instance-field-keyword)} ']'" "any"]]
            :notes ["if the last element of the lookup path is an integer, then the result is a value"
                    "if the last element of the lookup path is an instance field name, then the result is an 'any'; specifically of that last field is the name of an optional field"
                    "the non-terminal field names in the lookup path must be the names of mandatory fields"]
            :j-sigs [["( (<instance> '.' instance-field-symbol) | (<vector> '[' <integer> ']') ){ ( ('.' instance-field-symbol) | ('[' <integer> ']' ) ) }"
                      "any"]]}
   'if {:sigs [["<boolean> any-expression any-expression" "any"]]
        :j-sigs [["'if' '(' <boolean> ')' any-expression 'else' any-expression" "any"]]}
   'if-value {:sigs [["symbol any-expression any-expression" "any"]]
              :j-sigs [["'ifValue' '(' symbol ')' any-expression 'else' any-expression" "any"]]}
   'if-value-let {:sigs [["'[' symbol <any> ']' any-expression any-expression" "any"]]
                  :j-sigs [["'ifValueLet' '(' symbol '=' <any> ')'  any-expression 'else' any-expression" "any"]]}
   'inc {:sigs [["<integer>" "integer"]]
         :j-sigs [["<integer> '+' '1'" "integer"]]}
   'intersection {:sigs [["<set> <set> {<set>}" "set"]]
                  :j-sigs [["<set> '.' 'intersection' '(' <set> {',' <set>} ')'" "set"]]}
   'let {:sigs [["'[' symbol <value> {symbol <value>} ']' any-expression" "any"]]
         :j-sigs [["'{' symbol '=' <value> ';' {symbol '=' <value> ';'} any-expression '}'" "any"]]}
   'map {:sigs [["'[' symbol:element <set> ']' value-expression" "set"]
                ["'[' symbol:element <vector> ']' value-expression" "vector"]]
         :j-sigs [["'map' '(' symbol:element 'in' <set> ')' value-expression" "set"]
                  ["'map' '(' symbol:element 'in' <vector> ')' value-expression" "vector"]]}
   'mod {:sigs [["<integer> <integer>" "integer"]]
         :j-sigs [["<integer> '%' <integer>" "integer"]]}
   'not {:sigs [["<boolean>" "<boolean>"]]
         :j-sigs [["'!' <boolean>" "<boolean>"]]}
   'not= {:sigs [["<value> <value> {<value>}" "boolean"]]
          :j-sigs [["<value> '!=' <value>" "boolean"]
                   ["'notEqualTo' '(' <value> ',' <value> {',' <value>} ')'" "boolean"]]}
   'or {:sigs [["<boolean> {<boolean>}" "boolean"]]
        :j-sigs [["<boolean> '||' {<boolean>}" "boolean"]]}
   'range {:sigs [["<integer>:start [<integer>:end [<integer>:increment]]" "vector"]]
           :j-sigs [["'range' '(' <integer>:start [',' <integer>:end [',' <integer>:increment]] ')'" "vector"]]}
   'reduce {:sigs [["'[' symbol:accumulator <value>:accumulator-init ']' '[' symbol:element (set | vector) ']' any-expression" "any"]]
            :j-sigs [["'reduce' '(' symbol:accumulator '=' <value>:accumulator-init ';' symbol:element 'in' (set | vector) ')' any-expression" "any"]]
            :notes ["'normally' a reduce will produce a value, but the body could produce a 'maybe' value or even always produce 'unset', in which case the reduce may not produce a value"]}
   'refine-to {:sigs [["<instance> spec-id-keyword" "instance"]]
               :j-sigs [["<instance> '.' 'refineTo' '(' spec-id-symbol ')'" "instance"]], :throws []}
   'refines-to? {:sigs [["<instance> spec-id-keyword" "boolean"]]
                 :j-sigs [["<instance> '.' 'refinesTo?' '(' spec-id-symbol ')'" "boolean"]]}
   'rescale {:sigs [["<fixed-decimal> <integer>" "(fixed-decimal | integer)"]]
             :j-sigs [["'rescale' '(' <fixed-decimal> ',' <integer> ')'" "(fixed-decimal | integer)"]]
             :notes ["if the new scale is 0, then an integer is produced"
                     "scale must be positive"
                     "scale must be between 0 and 18 (inclusive)"]}
   'rest {:sigs [["<vector>" "vector"]]
          :j-sigs [["<vector> '.' 'rest()'" "vector"]]}
   'sort {:sigs [["<set>" "set"]
                 ["<vector>" "vector"]]
          :j-sigs [["<set> '.' 'sort()'" "set"]
                   ["<vector> '.' 'sort()'" "vector"]]}
   'sort-by {:sigs [["'[' symbol:element <set> ']' integer-expression" "set"]
                    ["'[' symbol:element <vector> ']' integer-expression" "vector"]]
             :j-sigs [["'sortBy' '(' symbol:element 'in' <set> ')' integer-expression" "set"]
                      ["'sortBy' '(' symbol:element 'in' <vector> ')' integer-expression" "vector"]]}
   'str {:sigs [["<string> {<string>}" "string"]]
         :j-sigs [["'str' '(' <string> {',' <string>} ')'" "string"]]}
   'subset? {:sigs [["<set> <set>" "boolean"]]
             :j-sigs [["<set> '.' 'subset?' '(' <set> ')'" "boolean"]]}
   'union {:sigs [["<set> <set> {<set>}" "set"]]
           :j-sigs [["<set> '.' 'union' '(' <set> {',' <set>} ')'" "set"]]}
   'valid {:sigs [["instance-expression" "any"]]
           :j-sigs [["'valid' instance-expression" "any"]]}
   'valid? {:sigs [["instance-expression" "boolean"]]
            :j-sigs [["'valid?' instance-expression" "boolean"]]}
   'when {:sigs [["<boolean> any-expression" "any"]]
          :j-sigs [["'when' '(' <boolean> ')' any-expression" "any"]]}
   'when-value {:sigs [["symbol any-expression" "any"]]
                :j-sigs [["'whenValue' '(' symbol ')' any-expression" "any"]]}
   'when-value-let {:sigs [["'[' symbol <any> ']' any-expression" "any"]]
                    :j-sigs [["'whenValueLet' '(' symbol '=' <any> ')' any-expression" "any"]]}})

(defn produce-bnf-diagrams [op-maps all-filename all-filename-j]
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
                                                              " '»' " result-bnf ";")})
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

                                           ")"))))
        j-single-rules-strs (->> op-maps
                                 (map (fn [[op {:keys [j-sigs]}]]
                                        [op (str " ( "
                                                 (->> j-sigs
                                                      (map (fn [[args-bnf result-bnf]]
                                                             (str "( " args-bnf
                                                                  " '»' " result-bnf " )")))
                                                      (string/join " |\n"))
                                                 " ) ")]))
                                 (remove nil?))]
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
        (spit all-filename rule-svg)))
    (let [rule-str (str "RULE = "
                        "("
                        (->> j-single-rules-strs
                             (sort-by first)
                             (map second)
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
        (spit all-filename-j rule-svg)))
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

;; (produce-bnf-diagrams op-maps "halite.svg" "jadeite.svg")
