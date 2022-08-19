;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.halite-docs
  (:require [clojure.string :as string])
  (:import [net.nextencia.rrdiagram.grammar.model GrammarToRRDiagram BNFToGrammar]
           [net.nextencia.rrdiagram.grammar.rrdiagram RRDiagramToSVG]))

(set! *warn-on-reflection* true)

(def basic-bnf ['basic-character {:bnf "'A-Z' | 'a-z' | '*' | '!' | '$' | '=' | '<' | '>' | '_' | '.'"}
                'plus-minus-character {:bnf "'+' | '-'"}
                'symbol-character {:bnf "basic-character | plus-minus-character | '0-9'"}
                'simple-symbol {:bnf "plus-minus-character | ((basic-character | (plus-minus-character (basic-character | plus-minus-character))) [{symbol-character}])"}
                'symbol {:bnf "simple-symbol [ '/' simple-symbol]"
                         :j-bnf "(simple-symbol [ '/' simple-symbol]) | ('’' simple-symbol [ '/' simple-symbol] '’')"}
                'keyword {:bnf "':' symbol"
                          :j-bnf nil}

                'boolean {:bnf "true | false"}
                'string {:bnf " '\"' {char} '\"'"}
                'integer {:bnf "[plus-minus-character] '0-9' {'0-9'}"}

                'fixed-decimal {:bnf "'#d' '\"' ['-'] '0-9' {'0-9'} '.' '0-9' {'0-9'} '\"'"}

                'instance {:bnf "'{' ':$type' keyword:spec-id {keyword value} '}' "
                           :j-bnf "'{' '$type' ':' symbol:spec-id {',' symbol ':' value } '}'"}
                'vector {:bnf "'[' { value } ']'"
                         :j-bnf "'[' [value] {',' value } ']'"}
                'set {:bnf "'#' '{' { value } '}'"
                      :j-bnf "'#' '{' [value] {',' value} '}'"}

                'value {:bnf "boolean | string | integer | fixed-decimal | instance | vector | set"}
                'any {:bnf "value | unset"}])

(def op-maps
  {'$no-value {:sigs [["" "unset"]]
               :j-sigs [["<$no-value>" "unset"]]
               :tags #{:optional-out}}
   '$this {:sigs [["" "value"]]
           :j-sigs [["<$this>" "unset"]]
           :tags #{}}
   '* {:sigs [["integer integer" "integer"]
              ["fixed-decimal integer" "fixed-decimal"]]
       :j-sigs [["integer '*' integer" "integer"]
                ["fixed-decimal '*' fixed-decimal" "fixed-decimal"]]
       :tags #{:integer-op :integer-out :fixed-decimal-op :fixed-decimal-out}}
   '+ {:sigs [["integer integer {integer}" "integer"]
              ["fixed-decimal fixed-decimal {fixed-decimal}" "fixed-decimal"]]
       :j-sigs [["integer '+' integer" "integer"]
                ["fixed-decimal '+' fixed-decimal" "fixed-decimal"]]
       :tags #{:integer-op :integer-out :fixed-decimal-op :fixed-decimal-out}}
   '- {:sigs [["integer integer {integer}" "integer"]
              ["fixed-decimal fixed-decimal {fixed-decimal}" "fixed-decimal"]]
       :j-sigs [["integer '-' integer" "integer"]
                ["fixed-decimal '-' fixed-decimal" "fixed-decimal"]]
       :tags #{:integer-op :integer-out :fixed-decimal-op :fixed-decimal-out}}
   '< {:sigs [["((integer integer) | (fixed-decimal fixed-decimal))" "boolean"]]
       :j-sigs [["((integer '<'  integer) | (fixed-decimal '<' fixed-decimal))" "boolean"]]
       :tags #{:integer-op :fixed-decimal-op :boolean-out}}
   '<= {:sigs [["((integer integer) | (fixed-decimal fixed-decimal))" "boolean"]]
        :j-sigs [["((integer '<=' integer) | (fixed-decimal '<=' fixed-decimal))" "boolean"]]
        :tags #{:integer-op :fixed-decimal-op :boolean-out}}
   '= {:sigs [["value value {value}" "boolean"]]
       :j-sigs [["value '==' value" "boolean"]
                ["'equalTo' '(' value ',' value {',' value} ')'" "boolean"]]
       :tags #{:integer-op :fixed-decimal-op :set-op :vector-op :boolean-out :instance-op}}
   '=> {:sigs [["boolean boolean" "boolean"]]
        :j-sigs [["boolean '=>' boolean" "boolean"]]
        :tags #{:boolean-op :boolean-out}}
   '> {:sigs [["((integer integer) | (fixed-decimal fixed-decimal))" "boolean"]]
       :j-sigs [["((integer '>'  integer) | (fixed-decimal '>' fixed-decimal))" "boolean"]]
       :tags #{:integer-op :fixed-decimal-op :boolean-out}}
   '>= {:sigs [["((integer integer) | (fixed-decimal fixed-decimal))" "boolean"]]
        :j-sigs [["((integer '>='  integer) | (fixed-decimal '>=' fixed-decimal))" "boolean"]]
        :tags #{:integer-op :fixed-decimal-op :boolean-out}}
   'abs {:sigs [["integer" "integer"]
                ["fixed-decimal" "fixed-decimal"]]
         :j-sigs [["'abs' '(' integer ')'" "integer"]
                  ["'abs' '(' fixed-decimal ')'" "fixed-decimal"]]
         :tags #{:integer-op :integer-out :fixed-decimal-op :fixed-decimal-out}}
   'and {:sigs [["boolean {boolean}" "boolean"]]
         :j-sigs [["boolean '&&' {boolean}" "boolean"]]
         :tags #{:boolean-op :boolean-out}}
   'any? {:sigs [["'[' symbol (set | vector) ']' boolean-expression" "boolean"]]
          :j-sigs [["'any?' '(' symbol 'in' (set | vector) ')' boolean-expression" "boolean"]]
          :tags #{:set-op :vector-op :boolean-out :special-form}}
   'concat {:sigs [["vector vector" "vector"]
                   ["(set (set | vector))" "set"]]
            :j-sigs [["vector '.' 'concat' '('  vector ')'" "vector"]
                     ["(set '.' 'concat' '(' (set | vector) ')')" "set"]]
            :tags #{:set-op :vector-op :vector-out :set-out}}
   'conj {:sigs [["set value {value}" "set"]
                 ["vector value {value}" "vector"]]
          :j-sigs [["set '.' 'conj' '(' value {',' value} ')'" "set"]
                   ["vector '.' 'conj' '(' value {',' value} ')'" "vector"]]
          :tags #{:set-op :vector-op :set-out :vector-out}}
   'contains? {:sigs [["set value" "boolean"]]
               :j-sigs [["set '.' 'contains?' '(' value ')'" "boolean"]]
               :tags #{:set-op :boolean-out}}
   'count {:sigs [["(set | vector)" "integer"]]
           :j-sigs [["(set | vector) '.' 'count()'" "integer"]]
           :tags #{:integer-out :set-op :vector-op}}
   'dec {:sigs [["integer" "integer"]]
         :j-sigs [["integer '-' '1' " "integer"]]
         :tags #{:integer-op :integer-out}}
   'difference {:sigs [["set set" "set"]]
                :j-sigs [["set '.' 'difference' '(' set ')'" "set"]]
                :tags #{:set-op :set-out}}
   'div {:sigs [["integer integer" "integer"]
                ["fixed-decimal integer" "fixed-decimal"]]
         :j-sigs [["integer '/' integer" "integer"]
                  ["fixed-decimal '/' integer" "fixed-decimal"]]
         :tags #{:integer-op :integer-out :fixed-decimal-op :fixed-decimal-out}}
   'error {:sigs [["" "nothing"]]
           :j-sigs [["'error' '('  ')'" "nothing"]]
           :tags #{:nothing-out}}
   'every? {:sigs [["'[' symbol (set | vector) ']' boolean-expression" "boolean"]]
            :j-sigs [["'every?' '(' symbol 'in' (set | vector) ')' boolean-expression" "boolean"]]
            :tags #{:set-op :vector-op :boolean-out :special-form}}
   'expt {:sigs [["integer integer" "integer"]]
          :j-sigs [["'expt' '(' integer ',' integer ')'" "integer"]]
          :tags #{:integer-op :integer-out}}
   'filter {:sigs [["'[' symbol:element set ']' boolean-expression" "set"]
                   ["'[' symbol:element vector ']' boolean-expression" "vector"]]
            :j-sigs [["'filter' '(' symbol 'in' set ')' boolean-expression" "set"]
                     ["'filter' '(' symbol 'in' vector ')' boolean-expression" "vector"]]
            :tags #{:set-op :vector-op :set-out :vector-out :special-form}}
   'first {:sigs [["vector" "value"]]
           :j-sigs [["vector '.' 'first()'" "value"]]
           :tags #{:vector-op}}
   'get {:sigs [["(instance keyword:instance-field)" "any"]
                ["(vector integer)" "value"]]
         :j-sigs [["(instance '.' symbol:instance-field)" "any"]
                  ["(vector '[' integer ']')" "value"]]
         :tags #{:vector-op :instance-op :optional-out :instance-field-op}}
   'get-in {:sigs [["(instance | vector) '[' (integer | keyword:instance-field) {(integer | keyword:instance-field)} ']'" "any"]]
            :notes ["if the last element of the lookup path is an integer, then the result is a value"
                    "if the last element of the lookup path is an instance field name, then the result is an 'any'; specifically of that last field is the name of an optional field"
                    "the non-terminal field names in the lookup path must be the names of mandatory fields"]
            :j-sigs [["( (instance '.' symbol:instance-field) | (vector '[' integer ']') ){ ( ('.' symbol:instance-field) | ('[' integer ']' ) ) }"
                      "any"]]
            :tags #{:vector-op :instance-op :optional-out :instance-field-op}}
   'if {:sigs [["boolean any-expression any-expression" "any"]]
        :j-sigs [["'if' '(' boolean ')' any-expression 'else' any-expression" "any"]]
        :tags #{:boolean-op :control-flow :special-form}}
   'if-value {:sigs [["symbol any-expression any-expression" "any"]]
              :j-sigs [["'ifValue' '(' symbol ')' any-expression 'else' any-expression" "any"]]
              :tags #{:optional-op :control-flow :special-form}}
   'if-value-let {:sigs [["'[' symbol any ']' any-expression any-expression" "any"]]
                  :j-sigs [["'ifValueLet' '(' symbol '=' any ')'  any-expression 'else' any-expression" "any"]]
                  :tags #{:optional-op :control-flow :special-form}}
   'inc {:sigs [["integer" "integer"]]
         :j-sigs [["integer '+' '1'" "integer"]]
         :tags #{:integer-op :integer-out}}
   'intersection {:sigs [["set set {set}" "set"]]
                  :j-sigs [["set '.' 'intersection' '(' set {',' set} ')'" "set"]]
                  :tags #{:set-op :set-out}}
   'let {:sigs [["'[' symbol value {symbol value} ']' any-expression" "any"]]
         :j-sigs [["'{' symbol '=' value ';' {symbol '=' value ';'} any-expression '}'" "any"]]
         :tags #{:special-form}}
   'map {:sigs [["'[' symbol:element set ']' value-expression" "set"]
                ["'[' symbol:element vector ']' value-expression" "vector"]]
         :j-sigs [["'map' '(' symbol:element 'in' set ')' value-expression" "set"]
                  ["'map' '(' symbol:element 'in' vector ')' value-expression" "vector"]]
         :tags #{:set-op :vector-op :set-out :vector-out :special-form}}
   'mod {:sigs [["integer integer" "integer"]]
         :j-sigs [["integer '%' integer" "integer"]]
         :tags #{:integer-op :integer-out}}
   'not {:sigs [["boolean" "boolean"]]
         :j-sigs [["'!' boolean" "boolean"]]
         :tags #{:boolean-op :boolean-out}}
   'not= {:sigs [["value value {value}" "boolean"]]
          :j-sigs [["value '!=' value" "boolean"]
                   ["'notEqualTo' '(' value ',' value {',' value} ')'" "boolean"]]
          :tags #{:integer-op :fixed-decimal-op :set-op :vector-op :instance-op :boolean-op :boolean-out}}
   'or {:sigs [["boolean {boolean}" "boolean"]]
        :j-sigs [["boolean '||' {boolean}" "boolean"]]
        :tags #{:boolean-op :boolean-out}}
   'range {:sigs [["integer:start [integer:end [integer:increment]]" "vector"]]
           :j-sigs [["'range' '(' integer:start [',' integer:end [',' integer:increment]] ')'" "vector"]]
           :tags #{:vector-out}}
   'reduce {:sigs [["'[' symbol:accumulator value:accumulator-init ']' '[' symbol:element (set | vector) ']' any-expression" "any"]]
            :j-sigs [["'reduce' '(' symbol:accumulator '=' value:accumulator-init ';' symbol:element 'in' (set | vector) ')' any-expression" "any"]]
            :tags #{:set-op :vector-op :special-form}
            :notes ["'normally' a reduce will produce a value, but the body could produce a 'maybe' value or even always produce 'unset', in which case the reduce may not produce a value"]}
   'refine-to {:sigs [["instance keyword:spec-id" "instance"]]
               :j-sigs [["instance '.' 'refineTo' '(' symbol:spec-id ')'" "instance"]]
               :tags #{:instance-op :instance-out :spec-id-op}
               :throws []}
   'refines-to? {:sigs [["instance keyword:spec-id" "boolean"]]
                 :j-sigs [["instance '.' 'refinesTo?' '(' symbol:spec-id ')'" "boolean"]]
                 :tags #{:instance-op :boolean-out :spec-id-op}}
   'rescale {:sigs [["fixed-decimal integer" "(fixed-decimal | integer)"]]
             :j-sigs [["'rescale' '(' fixed-decimal ',' integer ')'" "(fixed-decimal | integer)"]]
             :tags #{:integer-out :fixed-decimal-op :fixed-decimal-out}
             :notes ["if the new scale is 0, then an integer is produced"
                     "scale must be positive"
                     "scale must be between 0 and 18 (inclusive)"]}
   'rest {:sigs [["vector" "vector"]]
          :j-sigs [["vector '.' 'rest()'" "vector"]]
          :tags #{:vector-op :vector-out}}
   'sort {:sigs [["(set | vector)" "vector"]]
          :j-sigs [["(set | vector) '.' 'sort()'" "vector"]]
          :tags #{:set-op :vector-op :vector-out}
          :notes ["can only sort vectors/sets of integers or fixed-decimals"]}
   'sort-by {:sigs [["'[' symbol:element (set | vector) ']' (integer-expression | fixed-decimal-expression)" "vector"]]
             :j-sigs [["'sortBy' '(' symbol:element 'in' (set | vector) ')' (integer-expression | fixed-decimal-expression)" "vector"]]
             :tags #{:set-op :vector-op :vector-out :special-form}}
   'str {:sigs [["string {string}" "string"]]
         :j-sigs [["'str' '(' string {',' string} ')'" "string"]]
         :tags #{:string-op}}
   'subset? {:sigs [["set set" "boolean"]]
             :j-sigs [["set '.' 'subset?' '(' set ')'" "boolean"]]
             :tags #{:set-op :boolean-out}}
   'union {:sigs [["set set {set}" "set"]]
           :j-sigs [["set '.' 'union' '(' set {',' set} ')'" "set"]]
           :tags #{:set-op :set-out}}
   'valid {:sigs [["instance-expression" "any"]]
           :j-sigs [["'valid' instance-expression" "any"]]
           :tags #{:instance-op :optional-out :special-form}}
   'valid? {:sigs [["instance-expression" "boolean"]]
            :j-sigs [["'valid?' instance-expression" "boolean"]]
            :tags #{:instance-op :boolean-out :special-form}}
   'when {:sigs [["boolean any-expression" "any"]]
          :j-sigs [["'when' '(' boolean ')' any-expression" "any"]]
          :tags #{:boolean-op :optional-out :control-flow :special-form}}
   'when-value {:sigs [["symbol any-expression" "any"]]
                :j-sigs [["'whenValue' '(' symbol ')' any-expression" "any"]]
                :tags #{:optional-op :optional-out :control-flow :special-form}}
   'when-value-let {:sigs [["'[' symbol any ']' any-expression" "any"]]
                    :j-sigs [["'whenValueLet' '(' symbol '=' any ')' any-expression" "any"]]
                    :tags #{:optional-op :optional-out :control-flow :special-form}}})

(defn produce-diagram [out-file-name ^String rule-str]
  (let [gtrd (GrammarToRRDiagram.)
        rts (RRDiagramToSVG.)
        rule-svg (->> rule-str
                      (.convert (BNFToGrammar.))
                      .getRules
                      (into [])
                      (map #(.convert gtrd %))
                      (map #(.convert rts %))
                      first)]
    (spit out-file-name rule-svg)))

(defn produce-basic-bnf-diagrams [all-file-name all-file-name-j basic-bnf]
  (produce-diagram (str "doc/halite-bnf-diagrams/basic-syntax/" all-file-name)
                   (str "RULE = "
                        "("
                        (->> (partition 2 basic-bnf)
                             (map (fn [[n {:keys [bnf]}]]
                                    (when bnf
                                      (str "("
                                           "'" n ":' " "(" bnf ")"
                                           ")"))))
                             (remove nil?)
                             (string/join " |\n"))
                        ")"
                        ";"))
  (produce-diagram (str "doc/halite-bnf-diagrams/basic-syntax/" all-file-name-j)
                   (str "RULE = "
                        "("
                        (->> (partition 2 basic-bnf)
                             (map (fn [[n bnf-map]]
                                    (let [j-bnf (get bnf-map :j-bnf (:bnf bnf-map))]
                                      (when j-bnf
                                        (str "("
                                             "'" n ":' " "(" j-bnf ")"
                                             ")")))))
                             (remove nil?)
                             (string/join " |\n"))
                        ")"
                        ";"))

  (->> (partition 2 basic-bnf)
       (map (fn [[n {:keys [bnf]}]]
              (when bnf
                (produce-diagram (str "doc/halite-bnf-diagrams/basic-syntax/" n ".svg") (str "RULE = " "(" bnf ")" ";")))))
       dorun)

  (->> (partition 2 basic-bnf)
       (map (fn [[n bnf-map]]
              (let [j-bnf (get bnf-map :j-bnf (:bnf bnf-map))]
                (when j-bnf
                  (produce-diagram (str "doc/halite-bnf-diagrams/basic-syntax/" n "-j" ".svg") (str "RULE = " "(" j-bnf ")" ";"))))))
       dorun))

(defn safe-op-name [s]
  (get {'+ 'plus
        '- 'minus} s s))

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
        j-rules-strs (->> op-maps
                          (mapcat (fn [[op {:keys [j-sigs]}]]
                                    (->> j-sigs
                                         (map (fn [i [args-bnf result-bnf]]
                                                {:op-name op
                                                 :sig-index i
                                                 :rule-str (str (string/replace op "=" "equal") i " = "
                                                                (str "( " args-bnf
                                                                     " '»' " result-bnf " )"))})
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
      (produce-diagram (str "doc/halite-bnf-diagrams/" all-filename) rule-str))
    (let [rule-str (str "RULE = "
                        "("
                        (->> j-single-rules-strs
                             (sort-by first)
                             (map second)
                             (string/join " |\n"))
                        ")"
                        ";")]
      (produce-diagram (str "doc/halite-bnf-diagrams/" all-filename-j) rule-str))
    (->> rules-strs
         (map (fn [{:keys [op-name sig-index ^String rule-str]}]
                (produce-diagram (str "doc/halite-bnf-diagrams/op/" (str (safe-op-name op-name) "-" sig-index ".svg")) rule-str)))
         dorun)
    (->> j-rules-strs
         (map (fn [{:keys [op-name sig-index ^String rule-str]}]
                (produce-diagram (str "doc/halite-bnf-diagrams/op/" (str (safe-op-name op-name) "-" sig-index "-j" ".svg")) rule-str)))
         dorun)))

;;

(defn query-ops
  [tag]
  (apply sorted-map (mapcat identity (filter (fn [[op m]]
                                               ((:tags m) tag))
                                             op-maps))))

(defn produce-bnf-diagram-for-tag [tag]
  (produce-bnf-diagrams
   (query-ops tag)
   (str (name tag) ".svg")
   (str (name tag) "-j" ".svg")))

(comment
  (do
    (produce-basic-bnf-diagrams "basic-all.svg" "basic-all-j.svg" basic-bnf)

    (produce-bnf-diagrams op-maps "halite.svg" "jadeite.svg")

    (->> [:boolean-op :boolean-out
          :string-op
          :integer-op :integer-out
          :fixed-decimal-op :fixed-decimal-out

          :set-op :set-out
          :vector-op :vector-out

          :instance-op :instance-out :instance-field-op
          :spec-id-op

          :optional-op :optional-out
          :nothing-out

          :control-flow
          :special-form]
         (map produce-bnf-diagram-for-tag)
         dorun)))
