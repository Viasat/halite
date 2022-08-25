;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.halite-docs
  (:require [clojure.edn :as edn]
            [clojure.string :as string]
            [jibe.halite-guide :as halite-guide]
            [jibe.lib.fixed-decimal :as fixed-decimal]
            [jibe.logic.expression :as expression]
            [jibe.logic.jadeite :as jadeite]
            [jibe.logic.resource-spec-construct :as resource-spec-construct :refer [workspace spec variables constraints refinements]])
  (:import [jibe.halite_guide HCInfo]
           [net.nextencia.rrdiagram.grammar.model GrammarToRRDiagram BNFToGrammar]
           [net.nextencia.rrdiagram.grammar.rrdiagram RRDiagramToSVG]))

(set! *warn-on-reflection* true)

;; TODO:
;; define jadeite operator precedence
;; specify use of parens and {} in jadeite

(def misc-notes ["'whitespace' refers to characters such as spaces, tabs, and newlines."
                 "Whitespace is generally not called out in the following diagrams. However, it is specified for a few syntactic constructs that explicitly rely on whitespace."])

(def misc-notes-halite ["For halite, whitespace also includes the comma. The comma can be used as an optional delimiter in sequences to improve readability."])

(def basic-bnf ['non-numeric-character {:bnf "'A-Z' | 'a-z' | '*' | '!' | '$' | '=' | '<' | '>' | '_' | '.' | '?'"
                                        :tags #{:symbol-all :symbol-all-j}}
                'plus-minus-character {:bnf "'+' | '-'"
                                       :tags #{:symbol-all :symbol-all-j}}
                'symbol-character {:bnf "non-numeric-character | plus-minus-character | '0-9'"
                                   :tags #{:symbol-all :symbol-all-j}}
                'simple-symbol {:bnf "plus-minus-character | ((non-numeric-character | plus-minus-character) [{symbol-character}])"
                                :tags #{:symbol-all :symbol-all-j}}
                'symbol {:bnf "simple-symbol [ '/' simple-symbol]"
                         :bnf-j "(simple-symbol [ '/' simple-symbol]) | ('’' simple-symbol [ '/' simple-symbol] '’')"
                         :doc "Symbols are identifiers that allow values and operations to be named. The following are reserved and cannot be used as user defined symbols: true, false, nil."
                         :comment "Symbols are used to identify operators, variables in expressions, and specifications."
                         :comment-j "Symbols are used to identify operators, variables in expressions, specifications, and fields within specifications."
                         :comment-2 "Symbols are not values. There are no expressions that produce symbols. Anywhere that a symbol is called for in an operator argument list, a literal symbol must be provided. Symbols passed as arguments to operators are not evaluated. Symbols used within expressions in general are evaluated prior to invoking the operator."
                         :comment-3 "A common pattern in operator arguments is to provide a sequence of alternating symbols and values within square brackets. In these cases each symbol is bound to the corresponding value in pair-wise fashion."
                         :comment-3-j nil
                         :examples [{:expr-str "a"}
                                    {:expr-str "a.b"}
                                    {:expr-str "a/b"}]
                         :tags #{:symbol-all :symbol-all-j}}
                'keyword {:bnf "':' symbol"
                          :bnf-j nil
                          :doc "Keywords are identifiers that are used for instance field names. The following are reserved and cannot be used as user defined keywords: :true, :false, :nil."
                          :comment "Keywords are not values. There are no expressions that produce keywords. Anywhere that a keyword is called for in an operator arugment list, a literal keyword must be provided. Keywords themselves cannot be evaluated."
                          :examples [{:expr-str ":age"}
                                     {:expr-str ":x/y"}]
                          :tags #{:symbol-all}}

                'boolean {:bnf "true | false"}
                'string {:bnf " '\"' {char | '\\' ('\\' | '\"' | 't' | 'n' | ('u' hex-digit hex-digit hex-digit hex-digit))} '\"'"
                         :doc "Strings are sequences of characters. Strings can be multi-line. Quotation marks can be included if escaped with a \\. A backslash can be included with the character sequence: \\\\ . Strings can include special characters, e.g. \\t for a tab and \\n for a newline, as well as unicode via \\uNNNN. Unicode can also be directly entered in strings. Additional character representations may work but the only representations that are guaranteed to work are those documented here."
                         :examples [{:expr-str "\"\""}
                                    {:expr-str "\"hello\""}
                                    {:expr-str "\"say \\\"hi\\\" now\" "}
                                    {:expr-str "\"one \\\\ two\""}
                                    {:expr-str "\"\\t\\n\""}
                                    {:expr-str "\"☺\""}
                                    {:expr-str "\"\\u263A\""}]}
                'integer {:bnf "[plus-minus-character] '0-9' {'0-9'}"
                          :doc "Signed numeric integer values with no decimal places. Alternative integer representations may work, but the only representation that is guaranteed to work on an ongoing basis is that documented here."
                          :examples [{:expr-str "0"}
                                     {:expr-str "1"}
                                     {:expr-str "+1"}
                                     {:expr-str "-1"}
                                     {:expr-str "9223372036854775807"}
                                     {:expr-str "-9223372036854775808"}]}

                'fixed-decimal {:bnf "'#' 'd' [whitespace] '\"' ['-'] ('0' | ('1-9' {'0-9'})) '.' '0-9' {'0-9'} '\"'"
                                :doc "Signed numeric values with decimal places."
                                :examples [{:expr-str "#d \"1.1\""}
                                           {:expr-str "#d \"-1.1\""}
                                           {:expr-str "#d \"1.00\""}
                                           {:expr-str "#d \"0.00\""}]}

                'instance {:bnf "'{' ':$type' keyword:spec-id {keyword value} '}' "
                           :bnf-j "'{' '$type' ':' symbol:spec-id {',' symbol ':' value } '}'"
                           :doc "Represents an instance of a specification."
                           :comment "The contents of the instance are specified in pair-wise fashion with alternating field names and field values."
                           :comment-2 "The special field name ':$type' is mandatory but cannot be used as the other fields are."
                           :comment-2-j "The special field name '$type' is mandatory but cannot be used as the other fields are."
                           :examples [{:expr-str "{:$type :text/Spec$v1 :x 1 :y -1}"
                                       :expr-str-j "{$type: my/Spec$v1, x: 1, y: -1}"}]}
                'vector {:bnf "'[' [whitespace] { value whitespace} [value] [whitespace] ']'"
                         :bnf-j "'[' [whitespace] [value] [whitespace] {',' [whitespace] value [whitespace]} [whitespace]']'"
                         :doc "A collection of values in a prescribed sequence."
                         :examples [{:expr-str "[]"}
                                    {:expr-str "[1 2 3]"
                                     :expr-str-j "[1, 2, 3]"}]}
                'set {:bnf "'#' '{' [whitespace] { value [whitespace]} [value] [whitespace] '}'"
                      :bnf-j "'#' '{' [whitespace] [value] [whitespace] {',' [whitespace] value [whitespace]} '}'"
                      :doc "A collection of values in an unordered set. Duplicates are not allowed."
                      :comment "The members of sets are not directly accessible. If it is necessary to access the members of a set, it is recommended to design the data structures going into the sets in such a way that the set can be sorted into a vector for access."
                      :examples [{:expr-str "#{}"}
                                 {:expr-str "#{1 2 3}"
                                  :expr-str-j "#{1, 2, 3}"}]}

                'value {:bnf "boolean | string | integer | fixed-decimal | instance | vector | set"
                        :doc "Expressions and many literals produce values."}
                'any {:bnf "value | unset"
                      :doc "Refers to either the presence of absence of a value."}])

(defn expand-examples [op-maps]
  (->> op-maps
       (mapcat (fn [[op m]]
                 [op (if (:examples m)
                       (assoc m :examples (mapv (fn [example]
                                                  (let [{:keys [expr-str expr-str-j result result-j workspace-f instance]} example]
                                                    (if expr-str
                                                      (let [{:keys [h-result j-result j-expr]}
                                                            (if workspace-f
                                                              (let [workspace (workspace-f expr-str)
                                                                    ^HCInfo i (halite-guide/hc-body
                                                                               [workspace]
                                                                               :my
                                                                               (list 'get
                                                                                     (list 'refine-to instance :my/Result$v1)
                                                                                     :x))]
                                                                {:h-result (.-h-result i)
                                                                 :j-result (.-j-result i)
                                                                 :j-expr (jadeite/to-jadeite (edn/read-string
                                                                                              {:readers {'d fixed-decimal/fixed-decimal-reader}}
                                                                                              expr-str))})
                                                              (let [i (halite-guide/h*
                                                                       (edn/read-string
                                                                        {:readers {'d fixed-decimal/fixed-decimal-reader}}
                                                                        expr-str)
                                                                       true)]
                                                                {:h-result (.-h-result i)
                                                                 :j-result (.-j-result i)
                                                                 :j-expr (.-j-expr i)}))

                                                            err-result? (and (vector? h-result)
                                                                             (= :throws (first h-result)))
                                                            to-merge (apply merge [(when (= expr-str-j :auto)
                                                                                     {:expr-str-j j-expr})
                                                                                   (when (= result :auto)
                                                                                     (if err-result?
                                                                                       {:err-result (str (namespace (get h-result 2))
                                                                                                         "/"
                                                                                                         (name (get h-result 2)))}
                                                                                       {:result (pr-str h-result)}))
                                                                                   (when (or (= result-j :auto)
                                                                                             (and expr-str-j
                                                                                                  (not (contains? example :result-j))))
                                                                                     (if err-result?
                                                                                       {:err-result-j (str (namespace (get h-result 2))
                                                                                                           "/"
                                                                                                           (name (get h-result 2)))}
                                                                                       {:result-j j-result}))])
                                                            base-example (if (contains? to-merge :err-result)
                                                                           (dissoc example :result)
                                                                           example)
                                                            base-example (if (contains? to-merge :err-result-j)
                                                                           (dissoc base-example :result-j)
                                                                           base-example)]
                                                        (merge base-example to-merge))
                                                      example)))
                                                (:examples m)))
                       m)]))
       (apply sorted-map)))

(defn make-workspace-fn [workspace]
  (fn [expr-str] (update-in workspace
                            [:specs
                             0
                             :jibe.data.model/spec-refinements
                             0
                             :jibe.data.model/refinement-e]
                            (fn [_] (expression/parse :halite
                                                      (string/replace
                                                       "{:$type :my/Result$v1, :x <expr>}"
                                                       "<expr>"
                                                       expr-str))))))

(def op-maps
  (expand-examples
   {'$no-value {:sigs [["" "unset"]]
                :sigs-j [["'$no-value'" "unset"]]
                :tags #{:optional-out}
                :doc "Constant that produces the special 'unset' value which represents the lack of a value."
                :comment "Expected use is in an instance expression to indicate that a field in the instance does not have a value. However, it is suggested that alternatives include simply omitting the field name from the instance or using a variant of a 'when' expression to optionally produce a value for the field."
                :see-also ['when 'when-value 'when-value-let]}
    '$this {:sigs [["" "value"]]
            :sigs-j [["<$this>" "unset"]]
            :tags #{}
            :doc "Context dependent reference to the containing object."}
    '* {:sigs [["integer integer" "integer"]
               ["fixed-decimal integer" "fixed-decimal"]]
        :sigs-j [["integer '*' integer" "integer"]
                 ["fixed-decimal '*' integer" "fixed-decimal"]]
        :tags #{:integer-op :integer-out :fixed-decimal-op :fixed-decimal-out}
        :doc "Multiply two numbers together."
        :comment "Note that fixed-decimal values cannot be multiplied together. Rather the multiplication operator is used to scale a fixed-decimal value within the number space of a given scale of fixed-decimal. This can also be used to effectively convert an arbitrary integer value into a fixed-decimal number space by multiplying the integer by unity in the fixed-decimal number space of the desired scale."
        :throws ['h-err/overflow]
        :examples [{:expr-str "(* 2 3)"
                    :expr-str-j :auto
                    :result :auto}
                   {:expr-str "(* #d \"2.2\" 3)"
                    :expr-str-j :auto
                    :result :auto}
                   {:expr-str "(* 2 3 4)"
                    :expr-str-j :auto
                    :result :auto}]}
    '+ {:sigs [["integer integer {integer}" "integer"]
               ["fixed-decimal fixed-decimal {fixed-decimal}" "fixed-decimal"]]
        :sigs-j [["integer '+' integer" "integer"]
                 ["fixed-decimal '+' fixed-decimal" "fixed-decimal"]]
        :tags #{:integer-op :integer-out :fixed-decimal-op :fixed-decimal-out}
        :doc "Add two numbers together."
        :throws ['h-err/overflow]
        :examples [{:expr-str "(+ 2 3)"
                    :expr-str-j :auto
                    :result :auto}
                   {:expr-str "(+ #d \"2.2\" #d \"3.3\")"
                    :expr-str-j :auto
                    :result :auto}
                   {:expr-str "(+ 2 3 4)"
                    :expr-str-j :auto
                    :result :auto}
                   {:expr-str "(+ 2 -3)"
                    :expr-str-j :auto
                    :result :auto}]}
    '- {:sigs [["integer integer {integer}" "integer"]
               ["fixed-decimal fixed-decimal {fixed-decimal}" "fixed-decimal"]]
        :sigs-j [["integer '-' integer" "integer"]
                 ["fixed-decimal '-' fixed-decimal" "fixed-decimal"]]
        :tags #{:integer-op :integer-out :fixed-decimal-op :fixed-decimal-out}

        :doc "Subtract one number from another."
        :examples [{:expr-str "(- 2 3)"
                    :expr-str-j :auto
                    :result :auto}
                   {:expr-str "(- #d \"2.2\" #d \"3.3\")"
                    :expr-str-j :auto
                    :result :auto}
                   {:expr-str "(- 2 3 4)"
                    :expr-str-j :auto
                    :result :auto}
                   {:expr-str "(- 2 -3)"
                    :expr-str-j :auto
                    :result :auto}]
        :throws ['h-err/overflow]}
    '< {:sigs [["((integer integer) | (fixed-decimal fixed-decimal))" "boolean"]]
        :sigs-j [["((integer '<'  integer) | (fixed-decimal '<' fixed-decimal))" "boolean"]]
        :tags #{:integer-op :fixed-decimal-op :boolean-out}
        :doc "Determine if a number is strictly less than another."
        :examples [{:expr-str "(< 2 3)"
                    :expr-str-j :auto
                    :result :auto}
                   {:expr-str "(< #d \"2.2\" #d \"3.3\")"
                    :expr-str-j :auto
                    :result :auto}
                   {:expr-str "(< 2 2)"
                    :expr-str-j :auto
                    :result :auto}]}
    '<= {:sigs [["((integer integer) | (fixed-decimal fixed-decimal))" "boolean"]]
         :sigs-j [["((integer '<=' integer) | (fixed-decimal '<=' fixed-decimal))" "boolean"]]
         :tags #{:integer-op :fixed-decimal-op :boolean-out}
         :doc "Determine if a number is less than or equal to another."
         :examples [{:expr-str "(<= 2 3)"
                     :expr-str-j :auto
                     :result :auto}
                    {:expr-str "(<= #d \"2.2\" #d \"3.3\")"
                     :expr-str-j :auto
                     :result :auto}
                    {:expr-str "(<= 2 2)"
                     :expr-str-j :auto
                     :result :auto}]}
    '= {:sigs [["value value {value}" "boolean"]]
        :sigs-j [["value '==' value" "boolean"]
                 ["'equalTo' '(' value ',' value {',' value} ')'" "boolean"]]
        :tags #{:integer-op :fixed-decimal-op :set-op :vector-op :boolean-out :instance-op}
        :doc "Determine if two values are equivalent. For vectors and sets this performs a comparison of their contents."
        :see-also ['not=]
        :examples [{:expr-str "(= 2 2)"
                    :expr-str-j :auto
                    :result :auto}
                   {:expr-str "(= #d \"2.2\" #d \"3.3\")"
                    :expr-str-j :auto
                    :result :auto}
                   {:expr-str "(= 2 3)"
                    :expr-str-j :auto
                    :result :auto}
                   {:expr-str "(= \"hi\" \"hi\")"
                    :expr-str-j :auto
                    :result :auto}
                   {:expr-str "(= [1 2 3] [1 2 3])"
                    :expr-str-j :auto
                    :result :auto}
                   {:expr-str "(= [1 2 3] #{1 2 3})"
                    :expr-str-j :auto
                    :result :auto}
                   {:expr-str "(= #{3 1 2} #{1 2 3})"
                    :expr-str-j :auto
                    :result :auto}
                   {:expr-str "(= [#{1 2} #{3}] [#{1 2} #{3}])"
                    :expr-str-j :auto
                    :result :auto}
                   {:expr-str "(= [#{1 2} #{3}] [#{1 2} #{4}])"
                    :expr-str-j :auto
                    :result :auto}
                   {:workspace-f (make-workspace-fn (workspace :my
                                                               {:my/Spec []
                                                                :my/Result []}
                                                               (spec :Result :concrete
                                                                     (variables [:x "Boolean"])
                                                                     (refinements
                                                                      [:r :from :my/Other$v1 [:halite "placeholder"]]))
                                                               (spec :Other :concrete)
                                                               (spec :Spec :concrete
                                                                     (variables [:x "Integer"]
                                                                                [:y "Integer"]))))
                    :instance {:$type :my/Other$v1}
                    :expr-str "(= {:$type :my/Spec$v1 :x 1 :y -1} {:$type :my/Spec$v1 :x 1 :y 0})"
                    :expr-str-j :auto
                    :result :auto}
                   {:workspace-f (make-workspace-fn (workspace :my
                                                               {:my/Spec []
                                                                :my/Result []}
                                                               (spec :Result :concrete
                                                                     (variables [:x "Boolean"])
                                                                     (refinements
                                                                      [:r :from :my/Other$v1 [:halite "placeholder"]]))
                                                               (spec :Other :concrete)
                                                               (spec :Spec :concrete
                                                                     (variables [:x "Integer"]
                                                                                [:y "Integer"]))))
                    :instance {:$type :my/Other$v1}
                    :expr-str "(= {:$type :my/Spec$v1 :x 1 :y 0} {:$type :my/Spec$v1 :x 1 :y 0})"
                    :expr-str-j :auto
                    :result :auto}]}
    '=> {:sigs [["boolean boolean" "boolean"]]
         :sigs-j [["boolean '=>' boolean" "boolean"]]
         :tags #{:boolean-op :boolean-out}
         :doc "Performs logical implication. If the first value is true, then the second value must also be true for the result to be true. If the first value is false, then the result is true."
         :examples [{:expr-str "(=> (> 2 1) (< 1 2))"
                     :expr-str-j :auto
                     :result :auto}
                    {:expr-str "(=> (> 2 1) (> 1 2))"
                     :expr-str-j :auto
                     :result :auto}
                    {:expr-str "(=> (> 1 2) false)"
                     :expr-str-j :auto
                     :result :auto}]
         :see-also ['and 'every? 'not 'or]}
    '> {:sigs [["((integer integer) | (fixed-decimal fixed-decimal))" "boolean"]]
        :sigs-j [["((integer '>'  integer) | (fixed-decimal '>' fixed-decimal))" "boolean"]]
        :tags #{:integer-op :fixed-decimal-op :boolean-out}
        :doc "Determine if a number is strictly greater than another."
        :examples [{:expr-str "(> 3 2)"
                    :expr-str-j :auto
                    :result :auto}
                   {:expr-str "(> #d \"3.3\" #d \"2.2\" )"
                    :expr-str-j :auto
                    :result :auto}
                   {:expr-str "(> 2 2)"
                    :expr-str-j :auto
                    :result :auto}]}
    '>= {:sigs [["((integer integer) | (fixed-decimal fixed-decimal))" "boolean"]]
         :sigs-j [["((integer '>='  integer) | (fixed-decimal '>=' fixed-decimal))" "boolean"]]
         :tags #{:integer-op :fixed-decimal-op :boolean-out}
         :doc "Determine if a number is greater than or equal to another."
         :examples [{:expr-str "(>= 3 2)"
                     :expr-str-j :auto
                     :result :auto}
                    {:expr-str "(>= #d \"3.3\" #d \"2.2\" )"
                     :expr-str-j :auto
                     :result :auto}
                    {:expr-str "(>= 2 2)"
                     :expr-str-j :auto
                     :result :auto}]}
    'abs {:sigs [["integer" "integer"]
                 ["fixed-decimal" "fixed-decimal"]]
          :sigs-j [["'abs' '(' integer ')'" "integer"]
                   ["'abs' '(' fixed-decimal ')'" "fixed-decimal"]]
          :tags #{:integer-op :integer-out :fixed-decimal-op :fixed-decimal-out}
          :doc "Compute the absolute value of a number."
          :comment "Since the negative number space contains one more value than the positive number space, it is a runtime error to attempt to take the absolute value of the most negative value for a given number space."
          :throws ["Cannot compute absolute value most max negative value"]
          :examples [{:expr-str "(abs -1)"
                      :expr-str-j :auto
                      :result :auto}
                     {:expr-str "(abs 1)"
                      :expr-str-j :auto
                      :result :auto}
                     {:expr-str "(abs #d \"-1.0\")"
                      :expr-str-j :auto
                      :result :auto}]}
    'and {:sigs [["boolean boolean {boolean}" "boolean"]]
          :sigs-j [["boolean '&&' boolean" "boolean"]]
          :tags #{:boolean-op :boolean-out}
          :doc "Perform a logical 'and' operation on the input values."
          :comment "The operation does not short-circuit. Even if the first argument evaluates to false the other arguments are still evaluated."
          :see-also ['=> 'every? 'not 'or]
          :examples [{:expr-str "(and true false)"
                      :expr-str-j :auto
                      :result :auto}
                     {:expr-str "(and true true)"
                      :expr-str-j :auto
                      :result :auto}
                     {:expr-str "(and (> 2 1) (> 3 2) (> 4 3))"
                      :expr-str-j :auto
                      :result :auto}]}
    'any? {:sigs [["'[' symbol (set | vector) ']' boolean-expression" "boolean"]]
           :sigs-j [["'any?' '(' symbol 'in' (set | vector) ')' boolean-expression" "boolean"]]
           :tags #{:set-op :vector-op :boolean-out :special-form}
           :doc "Evaluates to true if the boolean-expression is true when the symbol is bound to some element in the collection."
           :comment "The operation does not short-circuit. The boolean-expression is evaluated for all elements even if a prior element has caused the boolean-expression to evaluate to true. Operating on an empty collection produces a false value."
           :examples [{:expr-str "(any? [x [1 2 3]] (> x 1))"
                       :expr-str-j :auto
                       :result :auto}
                      {:expr-str "(any? [x #{1 2 3}] (> x 10))"
                       :expr-str-j :auto
                       :result :auto}]
           :see-also ['every? 'or]}
    'concat {:sigs [["vector vector" "vector"]
                    ["(set (set | vector))" "set"]]
             :sigs-j [["vector '.' 'concat' '('  vector ')'" "vector"]
                      ["(set '.' 'concat' '(' (set | vector) ')')" "set"]]
             :tags #{:set-op :vector-op :vector-out :set-out}
             :doc "Combine two collections into one."
             :comment "Invoking this operation with a vector and an empty set has the effect of converting a vector into a set with duplicate values removed."
             :examples [{:expr-str "(concat [1 2] [3])"
                         :expr-str-j :auto
                         :result :auto}
                        {:expr-str "(concat #{1 2} [3 4])"
                         :expr-str-j :auto
                         :result :auto}
                        {:expr-str "(concat [] [])"
                         :expr-str-j :auto
                         :result :auto}]}
    'conj {:sigs [["set value {value}" "set"]
                  ["vector value {value}" "vector"]]
           :sigs-j [["set '.' 'conj' '(' value {',' value} ')'" "set"]
                    ["vector '.' 'conj' '(' value {',' value} ')'" "vector"]]
           :tags #{:set-op :vector-op :set-out :vector-out}
           :doc "Add individual items to a collection."
           :comment "Only definite values may be put into collections, i.e. collections cannot contain 'unset' values."
           :examples [{:expr-str "(conj [1 2] 3)"
                       :expr-str-j :auto
                       :result :auto}
                      {:expr-str "(conj #{1 2} 3 4)"
                       :expr-str-j :auto
                       :result :auto}
                      {:expr-str "(conj [] 1)"
                       :expr-str-j :auto
                       :result :auto}]}
    'contains? {:sigs [["set value" "boolean"]]
                :sigs-j [["set '.' 'contains?' '(' value ')'" "boolean"]]
                :tags #{:set-op :boolean-out}
                :doc "Determine if a specific value is in a set."
                :comment "Since collections themselves are compared by their contents, this works for collections nested inside of sets."
                :examples [{:expr-str "(contains? #{\"a\" \"b\"} \"a\")"
                            :expr-str-j :auto
                            :result :auto}
                           {:expr-str "(contains? #{\"a\" \"b\"} \"c\")"
                            :expr-str-j :auto
                            :result :auto}
                           {:expr-str "(contains? #{#{1 2} #{3}} #{1 2})"
                            :expr-str-j :auto
                            :result :auto}
                           {:expr-str "(contains? #{[1 2] [3]} [4])"
                            :expr-str-j :auto
                            :result :auto}]}
    'count {:sigs [["(set | vector)" "integer"]]
            :sigs-j [["(set | vector) '.' 'count()'" "integer"]]
            :tags #{:integer-out :set-op :vector-op}
            :doc "Return how many items are in a collection."
            :examples [{:expr-str "(count [10 20 30])"
                        :expr-str-j :auto
                        :result :auto}
                       {:expr-str "(count #{\"a\" \"b\"})"
                        :expr-str-j :auto
                        :result :auto}
                       {:expr-str "(count [])"
                        :expr-str-j :auto
                        :result :auto}]}
    'dec {:sigs [["integer" "integer"]]
          :sigs-j [["integer '-' '1' " "integer"]]
          :tags #{:integer-op :integer-out}
          :doc "Decrement a numeric value."
          :throws ['h-err/overflow]
          :see-also ['inc]
          :examples [{:expr-str "(dec 10)"
                      :result :auto}
                     {:expr-str "(dec 0)"
                      :result :auto}]}
    'difference {:sigs [["set set" "set"]]
                 :sigs-j [["set '.' 'difference' '(' set ')'" "set"]]
                 :tags #{:set-op :set-out}
                 :doc "Compute the set difference of two sets."
                 :comment "This produces a set which contains all of the elements from the first set which do not appear in the second set."
                 :see-also ['intersection 'union 'subset?]

                 :examples [{:expr-str "(difference #{1 2 3} #{1 2})"
                             :expr-str-j :auto
                             :result :auto}
                            {:expr-str "(difference #{1 2 3} #{})"
                             :expr-str-j :auto
                             :result :auto}
                            {:expr-str "(difference #{1 2 3} #{1 2 3 4})"
                             :expr-str-j :auto
                             :result :auto}
                            {:expr-str "(difference #{[1 2] [3]} #{[1 2]})"
                             :expr-str-j :auto
                             :result :auto}]}
    'div {:sigs [["integer integer" "integer"]
                 ["fixed-decimal integer" "fixed-decimal"]]
          :sigs-j [["integer '/' integer" "integer"]
                   ["fixed-decimal '/' integer" "fixed-decimal"]]
          :tags #{:integer-op :integer-out :fixed-decimal-op :fixed-decimal-out}
          :doc "Divide the first number by the second. When the first argument is an integer the result is truncated to an integer value. When the first argument is a fixed-decimal the result is truncated to the same precision as the first argument."
          :comment "As with multiplication, fixed-decimal values cannot be divided by each other, instead a fixed-decimal value can be scaled down within the number space of that scale."
          :throws ['h-err/divide-by-zero]
          :examples [{:expr-str "(div 12 3)"
                      :expr-str-j :auto
                      :result :auto}
                     {:expr-str "(div #d \"12.3\" 3)"
                      :expr-str-j :auto
                      :result :auto}
                     {:expr-str "(div 14 4)"
                      :expr-str-j :auto
                      :result :auto}
                     {:expr-str "(div #d \"14.3\" 3)"
                      :expr-str-j :auto
                      :result :auto}
                     {:expr-str "(div 1 0)"
                      :expr-str-j :auto
                      :result :auto}]
          :see-also ['mod]}
    'error {:sigs [["string" "nothing"]]
            :sigs-j [["'error' '(' string ')'" "nothing"]]
            :tags #{:nothing-out}
            :doc "Produce a runtime error with the provided string as an error message."
            :comment "Used to indicate when an unexpected condition has occurred and the data at hand is invalid. It is preferred to use constraints to capture such conditions earlier."
            :examples [{:expr-str "(error \"failure\")"
                        :expr-str-j :auto
                        :result :auto}]
            :throws ["Always"]}
    'every? {:sigs [["'[' symbol (set | vector) ']' boolean-expression" "boolean"]]
             :sigs-j [["'every?' '(' symbol 'in' (set | vector) ')' boolean-expression" "boolean"]]
             :tags #{:set-op :vector-op :boolean-out :special-form}
             :doc "Evaluates to true if the boolean-expression is true when the symbol is bound to each the element in the collection."
             :comment "Does not short-circuit. The boolean-expression is evaluated for all elements, even once a prior element has evaluated to false. Operating on an empty collection produces a true value."
             :examples [{:expr-str "(every? [x [1 2 3]] (> x 0))"
                         :expr-str-j :auto
                         :result :auto}
                        {:expr-str "(every? [x #{1 2 3}] (> x 1))"
                         :expr-str-j :auto
                         :result :auto}]
             :see-also ['any? 'and]}
    'expt {:sigs [["integer integer" "integer"]]
           :sigs-j [["'expt' '(' integer ',' integer ')'" "integer"]]
           :tags #{:integer-op :integer-out}
           :doc "Compute the numeric result of raising the first argument to the power given by the second argument. The exponent argument cannot be negative."
           :examples [{:expr-str "(expt 2 3)"
                       :expr-str-j :auto
                       :result :auto}
                      {:expr-str "(expt -2 3)"
                       :expr-str-j :auto
                       :result :auto}
                      {:expr-str "(expt 2 0)"
                       :expr-str-j :auto
                       :result :auto}
                      {:expr-str "(expt 2 -1)"
                       :expr-str-j :auto
                       :result :auto}]
           :throws ["On overflow"
                    'h-err/invalid-exponent]}
    'filter {:sigs [["'[' symbol:element set ']' boolean-expression" "set"]
                    ["'[' symbol:element vector ']' boolean-expression" "vector"]]
             :sigs-j [["'filter' '(' symbol 'in' set ')' boolean-expression" "set"]
                      ["'filter' '(' symbol 'in' vector ')' boolean-expression" "vector"]]
             :tags #{:set-op :vector-op :set-out :vector-out :special-form}
             :doc "Produce a new collection which contains only the elements from the original collection for which the boolean-expression is true. When applied to a vector, the order of the elements in the result preserves the order from the original vector."
             :examples [{:expr-str "(filter [x [1 2 3]] (> x 2))"
                         :expr-str-j :auto
                         :result :auto}
                        {:expr-str "(filter [x #{1 2 3}] (> x 2))"
                         :expr-str-j :auto
                         :result :auto}]
             :see-also ['map 'filter]}
    'first {:sigs [["vector" "value"]]
            :sigs-j [["vector '.' 'first()'" "value"]]
            :tags #{:vector-op}
            :doc "Produce the first element from a vector."
            :comment "To avoid runtime errors, if the vector might be empty, use 'count' to check the length first."
            :throws ['h-err/argument-empty]
            :examples [{:expr-str "(first [10 20 30])"
                        :expr-str-j :auto
                        :result :auto}
                       {:expr-str "(first [])"
                        :expr-str-j :auto
                        :result :auto}]
            :see-also ['count 'rest]}
    'get {:sigs [["(instance keyword:instance-field)" "any"]
                 ["(vector integer)" "value"]]
          :sigs-j [["(instance '.' symbol:instance-field)" "any"]
                   ["(vector '[' integer ']')" "value"]]
          :tags #{:vector-op :instance-op :optional-out :instance-field-op}
          :doc "Extract the given item from the first argument. If the first argument is an instance, extract the value for the given field from the given instance. For optional fields, this may produce 'unset'. Otherwise this will always produce a value. If the first argument is a vector, then extract the value at the given index in the vector. The index in this case is zero based."
          :comment "The $type value of an instance is not considered a field that can be extracted with this operator. When dealing with instances of abstract specifications, it is necessary to refine an instance to a given specification before accessing a field of that specification."
          :throws [['h-err/index-out-of-bounds]
                   ['h-err/variables-not-in-spec]]
          :examples [{:expr-str "(get [10 20 30 40] 2)"
                      :expr-str-j :auto
                      :result :auto}
                     {:workspace-f (make-workspace-fn (workspace :my
                                                                 {:my/Spec []
                                                                  :my/SubSpec []
                                                                  :my/Result []}
                                                                 (spec :Result :concrete
                                                                       (variables [:x "Integer"])
                                                                       (refinements
                                                                        [:r :from :my/Other$v1 [:halite "placeholder"]]))
                                                                 (spec :Other :concrete)
                                                                 (spec :Spec :concrete
                                                                       (variables [:x "Integer"]
                                                                                  [:y "Integer"]))))
                      :instance {:$type :my/Other$v1}
                      :expr-str "(get {:$type :my/Spec$v1, :x -3, :y 2} :x)"
                      :expr-str-j :auto
                      :result :auto}]
          :see-also ['get-in]}
    'get-in {:sigs [["(instance:target | vector:target) '[' (integer | keyword:instance-field) {(integer | keyword:instance-field)} ']'" "any"]]
             :notes ["if the last element of the lookup path is an integer, then the result is a value"
                     "if the last element of the lookup path is an instance field name, then the result is an 'any'; specifically of that last field is the name of an optional field"
                     "the non-terminal field names in the lookup path must be the names of mandatory fields"]
             :sigs-j [["( (instance:target '.' symbol:instance-field) | (vector:target '[' integer ']') ){ ( ('.' symbol:instance-field) | ('[' integer ']' ) ) }"
                       "any"]]
             :tags #{:vector-op :instance-op :optional-out :instance-field-op}
             :doc "Syntactic sugar for performing the equivalent of a chained series of 'get' operations. The second argument is a vector that represents the logical path to be navigated through the first argument."
             :doc-j "A path of element accessors can be created by chaining together element access forms in sequence."
             :doc-2 "The first path element in the path is looked up in the initial target. If there are more path elements, the next path element is looked up in the result of the first lookup. This is repeated as long as there are more path elements. If this is used to lookup instance fields, then all of the field names must reference mandatory fields unless the field name is the final element of the path. The result will always be a value unless the final path element is a reference to an optional field. In this case, the result may be a value or may be 'unset'."
             :throws ['l-err/get-in-path-cannot-be-empty
                      'h-err/invalid-lookup-target
                      'h-err/variables-not-in-spec
                      'h-err/index-out-of-bounds]
             :examples [{:expr-str "(get-in [[10 20] [30 40]] [1 0])"
                         :expr-str-j :auto
                         :result :auto}
                        {:workspace-f (make-workspace-fn (workspace :my
                                                                    {:my/Spec []
                                                                     :my/SubSpec []
                                                                     :my/Result []}
                                                                    (spec :Result :concrete
                                                                          (variables [:x "Integer"])
                                                                          (refinements
                                                                           [:r :from :my/Other$v1 [:halite "placeholder"]]))
                                                                    (spec :Other :concrete)
                                                                    (spec :Spec :concrete
                                                                          (variables [:x :my/SubSpec$v1]
                                                                                     [:y "Integer"]))
                                                                    (spec :SubSpec :concrete
                                                                          (variables [:a "Integer"]
                                                                                     [:b "Integer"]))))
                         :instance {:$type :my/Other$v1}
                         :expr-str "(get-in {:$type :my/Spec$v1, :x {:$type :my/SubSpec$v1, :a 20, :b 10}, :y 2} [:x :a])"
                         :expr-str-j :auto
                         :result :auto}
                        {:workspace-f (make-workspace-fn (workspace :my
                                                                    {:my/Spec []
                                                                     :my/SubSpec []
                                                                     :my/Result []}
                                                                    (spec :Result :concrete
                                                                          (variables [:x "Integer"])
                                                                          (refinements
                                                                           [:r :from :my/Other$v1 [:halite "placeholder"]]))
                                                                    (spec :Other :concrete)
                                                                    (spec :Spec :concrete
                                                                          (variables [:x :my/SubSpec$v1]
                                                                                     [:y "Integer"]))
                                                                    (spec :SubSpec :concrete
                                                                          (variables [:a ["Integer"]]
                                                                                     [:b "Integer"]))))
                         :instance {:$type :my/Other$v1}
                         :expr-str "(get-in {:$type :my/Spec$v1, :x {:$type :my/SubSpec$v1, :a [20 30 40], :b 10}, :y 2} [:x :a 1])"
                         :expr-str-j :auto
                         :result :auto}]
             :see-also ['get]}
    'if {:sigs [["boolean any-expression any-expression" "any"]]
         :sigs-j [["'if' '(' boolean ')' any-expression 'else' any-expression" "any"]]
         :tags #{:boolean-op :control-flow :special-form}
         :doc "If the first argument is true, then evaluate the second argument, otherwise evaluate the third argument."
         :examples [{:expr-str "(if (> 2 1) 10 -1)"
                     :expr-str-j :auto
                     :result :auto}
                    {:expr-str "(if (> 2 1) 10 (error \"fail\"))"
                     :expr-str-j :auto
                     :result :auto}]
         :see-also ['when]}
    'if-value {:sigs [["symbol any-expression any-expression" "any"]]
               :sigs-j [["'ifValue' '(' symbol ')' any-expression 'else' any-expression" "any"]]
               :doc "Consider the value bound to the symbol. If it is a 'value', then evaluate the second argument. If instead it is 'unset' then evaluate the third argument."
               :comment "When an optional instance field needs to be referenced, it is generally necessary to guard the access with either 'if-value' or 'when-value'. In this way, both the case of the field being set and unset are explicitly handled."
               :tags #{:optional-op :control-flow :special-form}
               :see-also ['if-value-let 'when-value]}
    'if-value-let {:sigs [["'[' symbol any:binding ']' any-expression any-expression" "any"]]
                   :sigs-j [["'ifValueLet' '(' symbol '=' any:binding ')'  any-expression 'else' any-expression" "any"]]
                   :tags #{:optional-op :control-flow :special-form}
                   :doc "If the binding value is a 'value' then evaluate the second argument with the symbol bound to binding. If instead, the binding value is 'unset', then evaluate the third argument without introducing a new binding for the symbol."
                   :comment "This is similar to the 'if-value' operation, but applies generally to an expression which may or may not produce a value."
                   :examples [{:workspace-f (make-workspace-fn (workspace :my
                                                                          {:my/Spec []
                                                                           :my/Result []}
                                                                          (spec :Result :concrete
                                                                                (variables [:x "Integer"])
                                                                                (refinements
                                                                                 [:r :from :my/Other$v1 [:halite "placeholder"]]))
                                                                          (spec :Other :concrete)
                                                                          (spec :Spec :concrete
                                                                                (variables [:p "Integer"]
                                                                                           [:n "Integer"]
                                                                                           [:o "Integer" :optional])
                                                                                (constraints [:pc [:halite "(> p 0)"]]
                                                                                             [:pn [:halite "(< n 0)"]]))))
                               :instance {:$type :my/Other$v1}
                               :expr-str "(if-value-let [x (when (> 2 1) 19)] (inc x) 0)"
                               :expr-str-j :auto
                               :result :auto}
                              {:workspace-f (make-workspace-fn (workspace :my
                                                                          {:my/Spec []
                                                                           :my/Result []}
                                                                          (spec :Result :concrete
                                                                                (variables [:x "Integer"])
                                                                                (refinements
                                                                                 [:r :from :my/Other$v1 [:halite "placeholder"]]))
                                                                          (spec :Other :concrete)
                                                                          (spec :Spec :concrete
                                                                                (variables [:p "Integer"]
                                                                                           [:n "Integer"]
                                                                                           [:o "Integer" :optional])
                                                                                (constraints [:pc [:halite "(> p 0)"]]
                                                                                             [:pn [:halite "(< n 0)"]]))))
                               :instance {:$type :my/Other$v1}
                               :expr-str "(if-value-let [x (when (> 1 2) 19)] (inc x) 0)"
                               :expr-str-j :auto
                               :result :auto}]
                   :see-also ['if-value 'when-value-let]}
    'inc {:sigs [["integer" "integer"]]
          :sigs-j [["integer '+' '1'" "integer"]]
          :tags #{:integer-op :integer-out}
          :doc "Increment a numeric value."
          :examples [{:expr-str "(inc 10)"
                      :result :auto}
                     {:expr-str "(inc 0)"
                      :result :auto}]
          :throws ['h-err/overflow]
          :see-also ['dec]}
    'intersection {:sigs [["set set {set}" "set"]]
                   :sigs-j [["set '.' 'intersection' '(' set {',' set} ')'" "set"]]
                   :tags #{:set-op :set-out}
                   :doc "Compute the set intersection of the sets."
                   :comment "This produces a set which only contains values that appear in each of the arguments."
                   :examples [{:expr-str "(intersection #{1 2 3} #{2 3 4})"
                               :expr-str-j :auto
                               :result :auto}
                              {:expr-str "(intersection #{1 2 3} #{2 3 4} #{3 4})"
                               :expr-str-j :auto
                               :result :auto}]
                   :see-also ['difference 'union 'subset?]}
    'let {:sigs [["'[' symbol value {symbol value} ']' any-expression" "any"]]
          :sigs-j [["'{' symbol '=' value ';' {symbol '=' value ';'} any-expression '}'" "any"]]
          :tags #{:special-form}
          :doc "Evaluate the expression argument in a nested context created by considering the first argument in a pairwise fashion and binding each symbol to the corresponding value."
          :comment "Allows names to be given to values so that they can be referenced by the any-expression."
          :examples [{:expr-str "(let [x 1] (inc x))"
                      :expr-str-j :auto
                      :result :auto}
                     {:expr-str "(let [x 1 y 2] (+ x y))"
                      :expr-str-j :auto
                      :result :auto}
                     {:expr-str "(let [x 1] (let [x 2] x))"
                      :expr-str-j :auto
                      :result :auto
                      :doc "The values associated with symbols can be changed in nested contexts."}]
          :doc-j "Evaluate the expression argument in a nested context created by binding each symbol to the corresponding value."}
    'map {:sigs [["'[' symbol:element set ']' value-expression" "set"]
                 ["'[' symbol:element vector ']' value-expression" "vector"]]
          :sigs-j [["'map' '(' symbol:element 'in' set ')' value-expression" "set"]
                   ["'map' '(' symbol:element 'in' vector ')' value-expression" "vector"]]
          :tags #{:set-op :vector-op :set-out :vector-out :special-form}
          :doc "Produce a new collection from a collection by evaluating the expression with the symbol bound to each element of the original collection, one-by-one. The results of evaluating the expression will be in the resulting collection. When operating on a vector, the order of the output vector will correspond to the order of the items in the original vector."
          :examples [{:expr-str "(map [x [10 11 12]] (inc x))"
                      :expr-str-j :auto
                      :result :auto}
                     {:expr-str "(map [x #{10 12}] (* x 2))"
                      :expr-str-j :auto
                      :result :auto}]
          :see-also ['reduce 'filter]}
    'mod {:sigs [["integer integer" "integer"]]
          :sigs-j [["integer '%' integer" "integer"]]
          :tags #{:integer-op :integer-out}
          :doc "Computes the mathematical modulus of two numbers. Use care if one of the arguments is negative."
          :examples [{:expr-str "(mod 12 3)"
                      :expr-str-j :auto
                      :result :auto}
                     {:expr-str "(mod 14 4)"
                      :expr-str-j :auto
                      :result :auto}
                     {:expr-str "(mod 1 0)"
                      :expr-str-j :auto
                      :result :auto}]
          :throws ['h-err/divide-by-zero]}
    'not {:sigs [["boolean" "boolean"]]
          :sigs-j [["'!' boolean" "boolean"]]
          :tags #{:boolean-op :boolean-out}
          :doc "Performs logical negation of the argument."
          :examples [{:expr-str "(not true)"
                      :expr-str-j :auto
                      :result :auto}
                     {:expr-str "(not false)"
                      :expr-str-j :auto
                      :result :auto}]
          :see-also ['=> 'and 'or]}
    'not= {:sigs [["value value {value}" "boolean"]]
           :sigs-j [["value '!=' value" "boolean"]
                    ["'notEqualTo' '(' value ',' value {',' value} ')'" "boolean"]]
           :tags #{:integer-op :fixed-decimal-op :set-op :vector-op :instance-op :boolean-op :boolean-out}
           :doc "Produces a false value if all of the values are equal to each other. Otherwise produces a true value."
           :examples [{:expr-str "(not= 2 3)"
                       :expr-str-j :auto
                       :result :auto}
                      {:expr-str "(not= #d \"2.2\" #d \"2.2\")"
                       :expr-str-j :auto
                       :result :auto}
                      {:expr-str "(not= 2 2)"
                       :expr-str-j :auto
                       :result :auto}
                      {:expr-str "(not= \"hi\" \"bye\")"
                       :expr-str-j :auto
                       :result :auto}
                      {:expr-str "(not= [1 2 3] [1 2 3 4])"
                       :expr-str-j :auto
                       :result :auto}
                      {:expr-str "(not= [1 2 3] #{1 2 3})"
                       :expr-str-j :auto
                       :result :auto}
                      {:expr-str "(not= #{3 1 2} #{1 2 3})"
                       :expr-str-j :auto
                       :result :auto}
                      {:expr-str "(not= [#{1 2} #{3}] [#{1 2} #{3}])"
                       :expr-str-j :auto
                       :result :auto}
                      {:expr-str "(not= [#{1 2} #{3}] [#{1 2} #{4}])"
                       :expr-str-j :auto
                       :result :auto}
                      {:workspace-f (make-workspace-fn (workspace :my
                                                                  {:my/Spec []
                                                                   :my/Result []}
                                                                  (spec :Result :concrete
                                                                        (variables [:x "Boolean"])
                                                                        (refinements
                                                                         [:r :from :my/Other$v1 [:halite "placeholder"]]))
                                                                  (spec :Other :concrete)
                                                                  (spec :Spec :concrete
                                                                        (variables [:x "Integer"]
                                                                                   [:y "Integer"]))))
                       :instance {:$type :my/Other$v1}
                       :expr-str "(not= {:$type :my/Spec$v1 :x 1 :y -1} {:$type :my/Spec$v1 :x 1 :y 0})"
                       :expr-str-j :auto
                       :result :auto}
                      {}]
           :see-also ['=]}
    'or {:sigs [["boolean boolean {boolean}" "boolean"]]
         :sigs-j [["boolean '||' boolean" "boolean"]]
         :tags #{:boolean-op :boolean-out}
         :doc "Perform a logical 'or' operation on the input values."
         :comment "The operation does not short-circuit. Even if the first argument evaluates to true the other arguments are still evaluated."
         :examples [{:expr-str "(or true false)"
                     :expr-str-j :auto
                     :result :auto}
                    {:expr-str "(or false false)"
                     :expr-str-j :auto
                     :result :auto}
                    {:expr-str "(or (> 1 2) (> 2 3) (> 4 3))"
                     :expr-str-j :auto
                     :result :auto}]
         :see-also ['=> 'and 'any? 'not]}
    'range {:sigs [["[integer:start] integer:end [integer:increment]" "vector"]]
            :sigs-j [["'range' '(' [integer:start ','] integer:end [',' integer:increment] ')'" "vector"]]
            :doc "Produce a vector that contains integers in order starting at either the start value or 0 if no start is provided. The final element of the vector will be no more than one less than the end value. If an increment is provided then only every increment integer will be included in the result."
            :examples [{:expr-str "(range 3)"
                        :expr-str-j :auto
                        :result :auto}
                       {:expr-str "(range 10 12)"
                        :expr-str-j :auto
                        :result :auto}
                       {:expr-str "(range 10 21 5)"
                        :expr-str-j :auto
                        :result :auto}]
            :tags #{:vector-out}}
    'reduce {:sigs [["'[' symbol:accumulator value:accumulator-init ']' '[' symbol:element vector ']' any-expression" "any"]]
             :sigs-j [["'reduce' '(' symbol:accumulator '=' value:accumulator-init ';' symbol:element 'in' vector ')' any-expression" "any"]]
             :tags #{:vector-op :special-form}
             :doc "Evalue the expression repeatedly for each element in the vector. The accumulator value will have a value of accumulator-init on the first evaluation of the expression. Subsequent evaluations of the expression will chain the prior result in as the value of the accumulator. The result of the final evaluation of the expression will be produced as the result of the reduce operation. The elements are processed in order."
             :examples [{:expr-str "(reduce [a 10] [x [1 2 3]] (+ a x))"
                         :expr-str-j :auto
                         :result :auto}]
             :see-also ['map 'filter]
             :notes ["'normally' a reduce will produce a value, but the body could produce a 'maybe' value or even always produce 'unset', in which case the reduce may not produce a value"]}
    'refine-to {:sigs [["instance keyword:spec-id" "instance"]]
                :sigs-j [["instance '.' 'refineTo' '(' symbol:spec-id ')'" "instance"]]
                :tags #{:instance-op :instance-out :spec-id-op}
                :doc "Attempt to refine the given instance into an instance of type, spec-id."
                :throws ['h-err/no-active-refinement-path
                         "Spec not found"]
                :examples [{:workspace-f (make-workspace-fn (workspace :my
                                                                       {:my/Spec []
                                                                        :my/Result []
                                                                        :my/Other []}
                                                                       (spec :Result :concrete
                                                                             (variables [:x :my/Other$v1])
                                                                             (refinements
                                                                              [:r :from :my/Third$v1 [:halite "placeholder"]]))
                                                                       (spec :Third :concrete)
                                                                       (spec :Other :concrete
                                                                             (variables [:x "Integer"]
                                                                                        [:y "Integer"]))
                                                                       (spec :Spec :concrete
                                                                             (variables [:p "Integer"]
                                                                                        [:n "Integer"])
                                                                             (refinements
                                                                              [:r :to :my/Other$v1 [:halite "{:$type :my/Other$v1 :x (inc p) :y (inc n)}"]]))))
                            :instance {:$type :my/Third$v1}
                            :expr-str "(refine-to {:$type :my/Spec$v1, :p 1, :n -1} :my/Other$v1)"
                            :expr-str-j "{$type: my/Spec$v1, n: -1, p: 1}.refineTo( my/Other$v1 )"
                            :result :auto
                            :doc "Assuming a spec has a refinement defined to another."}
                           {:workspace-f (make-workspace-fn (workspace :my
                                                                       {:my/Spec []
                                                                        :my/Result []
                                                                        :my/Other []}
                                                                       (spec :Result :concrete
                                                                             (variables [:x :my/Other$v1])
                                                                             (refinements
                                                                              [:r :from :my/Third$v1 [:halite "placeholder"]]))
                                                                       (spec :Third :concrete)
                                                                       (spec :Other :concrete
                                                                             (variables [:x "Integer"]
                                                                                        [:y "Integer"]))
                                                                       (spec :Spec :concrete
                                                                             (variables [:p "Integer"]
                                                                                        [:n "Integer"]))))
                            :instance {:$type :my/Third$v1}
                            :expr-str "(refine-to {:$type :my/Spec$v1, :p 1, :n -1} :my/Other$v1)"
                            :expr-str-j "{$type: my/Spec$v1, n: -1, p: 1}.refineTo( my/Other$v1 )"
                            :result :auto
                            :doc "Assuming a spec does note have a refinement defined to another."}]
                :see-also ['refines-to?]}
    'refines-to? {:sigs [["instance keyword:spec-id" "boolean"]]
                  :sigs-j [["instance '.' 'refinesTo?' '(' symbol:spec-id ')'" "boolean"]]
                  :tags #{:instance-op :boolean-out :spec-id-op}
                  :doc "Determine whether it is possible to refine the given instance into an instance of type, spec-id."
                  :see-also ['refine-to]
                  :examples [{:workspace-f (make-workspace-fn (workspace :my
                                                                         {:my/Spec []
                                                                          :my/Result []
                                                                          :my/Other []}
                                                                         (spec :Result :concrete
                                                                               (variables [:x "Boolean" :optional])
                                                                               (refinements
                                                                                [:r :from :my/Third$v1 [:halite "placeholder"]]))
                                                                         (spec :Third :concrete)
                                                                         (spec :Other :concrete
                                                                               (variables [:x "Integer"]
                                                                                          [:y "Integer"]))
                                                                         (spec :Spec :concrete
                                                                               (variables [:p "Integer"]
                                                                                          [:n "Integer"])
                                                                               (refinements
                                                                                [:r :to :my/Other$v1 [:halite "{:$type :my/Other$v1 :x (inc p) :y (inc n)}"]]))))
                              :instance {:$type :my/Third$v1}
                              :expr-str "(refines-to? {:$type :my/Spec$v1, :p 1, :n -1} :my/Other$v1)"
                              :expr-str-j "{$type: my/Spec$v1, n: -1, p: 1}.refinesTo?( my/Other$v1 )"
                              :result :auto
                              :doc "Assuming a spec has a refinement defined to another."}
                             {:workspace-f (make-workspace-fn (workspace :my
                                                                         {:my/Spec []
                                                                          :my/Result []
                                                                          :my/Other []}
                                                                         (spec :Result :concrete
                                                                               (variables [:x "Boolean" :optional])
                                                                               (refinements
                                                                                [:r :from :my/Third$v1 [:halite "placeholder"]]))
                                                                         (spec :Third :concrete)
                                                                         (spec :Other :concrete
                                                                               (variables [:x "Integer"]
                                                                                          [:y "Integer"]))
                                                                         (spec :Spec :concrete
                                                                               (variables [:p "Integer"]
                                                                                          [:n "Integer"]))))
                              :instance {:$type :my/Third$v1}
                              :expr-str "(refines-to? {:$type :my/Spec$v1, :p 1, :n -1} :my/Other$v1)"
                              :expr-str-j "{$type: my/Spec$v1, n: -1, p: 1}.refinesTo?( my/Other$v1 )"
                              :result :auto
                              :doc "Assuming a spec does note have a refinement defined to another."}]
                  :throws ["Spec not found"]}
    'rescale {:sigs [["fixed-decimal integer:new-scale" "(fixed-decimal | integer)"]]
              :sigs-j [["'rescale' '(' fixed-decimal ',' integer ')'" "(fixed-decimal | integer)"]]
              :tags #{:integer-out :fixed-decimal-op :fixed-decimal-out}
              :doc "Produce a number by adjusting the scale of the fixed-decimal to the new-scale. If the scale is being reduced, the original number is truncated. If the scale is being increased, then the original number is padded with zeroes in the decimal places. If the new-scale is zero, then the result is an integer."
              :comment "Arithmetic on numeric values never produce results in different number spaces. This operation provides an explicit way to convert a fixed-decimal value into a value with the scale of a different number space. This includes the ability to convert a fixed-decimal value into an integer."
              :notes ["if the new scale is 0, then an integer is produced"
                      "scale cannot be negative"
                      "scale must be between 0 and 18 (inclusive)"]
              :examples [{:expr-str "(rescale #d \"1.23\" 1)"
                          :expr-str-j :auto
                          :result :auto}
                         {:expr-str "(rescale #d \"1.23\" 2)"
                          :expr-str-j :auto
                          :result :auto}
                         {:expr-str "(rescale #d \"1.23\" 3)"
                          :expr-str-j :auto
                          :result :auto}
                         {:expr-str "(rescale #d \"1.23\" 0)"
                          :expr-str-j :auto
                          :result :auto}]
              :throws ['h-err/arg-type-mismatch]
              :see-also ['*]}
    'rest {:sigs [["vector" "vector"]]
           :sigs-j [["vector '.' 'rest()'" "vector"]]
           :doc "Produce a new vector which contains the same element of the argument, in the same order, except the first element is removed. If there are no elements in the argument, then an empty vector is produced."
           :examples [{:expr-str "(rest [1 2 3])"
                       :expr-str-j :auto
                       :result :auto}
                      {:expr-str "(rest [1 2])"
                       :expr-str-j :auto
                       :result :auto}
                      {:expr-str "(rest [1])"
                       :expr-str-j :auto
                       :result :auto}
                      {:expr-str "(rest [])"
                       :expr-str-j :auto
                       :result :auto}]
           :tags #{:vector-op :vector-out}}
    'sort {:sigs [["(set | vector)" "vector"]]
           :sigs-j [["(set | vector) '.' 'sort()'" "vector"]]
           :tags #{:set-op :vector-op :vector-out}
           :doc "Produce a new vector by sorting all of the items in the argument. Only collections of numeric values may be sorted."
           :throws ["Elements not sortable"]
           :examples [{:expr-str "(sort [2 1 3])"
                       :expr-str-j :auto
                       :result :auto}
                      {:expr-str "(sort [#d \"3.3\" #d \"1.1\" #d \"2.2\"])"
                       :expr-str-j :auto
                       :result :auto}]
           :see-also ['sort-by]}
    'sort-by {:sigs [["'[' symbol:element (set | vector) ']' (integer-expression | fixed-decimal-expression)" "vector"]]
              :sigs-j [["'sortBy' '(' symbol:element 'in' (set | vector) ')' (integer-expression | fixed-decimal-expression)" "vector"]]
              :tags #{:set-op :vector-op :vector-out :special-form}
              :doc "Produce a new vector by sorting all of the items in the input collection according to the values produced by applying the expression to each element. The expression must produce a unique, sortable value for each element."
              :throws ['h-err/not-sortable-body
                       'h-err/sort-value-collision]
              :examples [{:expr-str "(sort-by [x [[10 20] [30] [1 2 3]]] (first x))"
                          :expr-str-j :auto
                          :result :auto}]
              :see-also ['sort]}
    'str {:sigs [["string string {string}" "string"]]
          :sigs-j [["'str' '(' string ',' string {',' string} ')'" "string"]]
          :doc "Combine all of the input strings together in sequence to produce a new string."
          :examples [{:expr-str "(str \"a\" \"b\")"
                      :expr-str-j :auto
                      :result :auto}
                     {:expr-str "(str \"a\" \"\" \"c\")"
                      :expr-str-j :auto
                      :result :auto}]
          :tags #{:string-op}}
    'subset? {:sigs [["set set" "boolean"]]
              :sigs-j [["set '.' 'subset?' '(' set ')'" "boolean"]]
              :tags #{:set-op :boolean-out}
              :doc "Return false if there are any items in the first set which do not appear in the second set. Otherwise return true."
              :comment "According to this operation, a set is always a subset of itself and every set is a subset of the empty set. Using this operation and an equality check in combination allows a 'superset?' predicate to be computed."
              :examples [{:expr-str "(subset? #{1 2} #{1 2 3 4})"
                          :expr-str-j :auto
                          :result :auto}
                         {:expr-str "(subset? #{1 5} #{1 2})"
                          :expr-str-j :auto
                          :result :auto}
                         {:expr-str "(subset? #{1 2} #{1 2})"
                          :expr-str-j :auto
                          :result :auto}]
              :see-also ['difference 'intersection 'union]}
    'union {:sigs [["set set {set}" "set"]]
            :sigs-j [["set '.' 'union' '(' set {',' set} ')'" "set"]]
            :tags #{:set-op :set-out}
            :examples [{:expr-str "(union #{1} #{2 3})"
                        :expr-str-j :auto
                        :result :auto}
                       {:expr-str "(union #{1} #{2 3} #{4})"
                        :expr-str-j :auto
                        :result :auto}
                       {:expr-str "(union #{1} #{})"
                        :expr-str-j :auto
                        :result :auto}]
            :doc "Compute the union of all the sets."
            :comment "This produces a set which contains all of the values that appear in any of the arguments."
            :see-also ['difference 'intersection 'subset?]}
    'valid {:sigs [["instance-expression" "any"]]
            :sigs-j [["'valid' instance-expression" "any"]]
            :tags #{:instance-op :optional-out :special-form}
            :doc "Evaluate the instance-expression and produce the result. If a constraint violation occurs while evaluating the expression then produce an 'unset' value."
            :comment "This operation can be thought of as producing an instance if it is valid. This considers not just the constraints on the immediate instance, but also the constraints implied by refinements defined on the specification."

            :examples [{:workspace-f (make-workspace-fn (workspace :my
                                                                   {:my/Spec []
                                                                    :my/Result []}
                                                                   (spec :Result :concrete
                                                                         (variables [:x :my/Spec$v1, :optional])
                                                                         (refinements
                                                                          [:r :from :my/Other$v1 [:halite "placeholder"]]))
                                                                   (spec :Other :concrete)
                                                                   (spec :Spec :concrete
                                                                         (variables [:p "Integer"]
                                                                                    [:n "Integer"]
                                                                                    [:o "Integer" :optional])
                                                                         (constraints [:pc [:halite "(> p 0)"]]
                                                                                      [:pn [:halite "(< n 0)"]]))))
                        :instance {:$type :my/Other$v1}
                        :expr-str "(valid {:$type :my/Spec$v1, :p 1, :n -1})"
                        :expr-str-j "valid {$type: my/Spec$v1, p: 1, n: -1}"
                        :result :auto
                        :doc "When the spec has constraints that the field, p, must be positive and the field, n, must be negative."}
                       {:workspace-f (make-workspace-fn (workspace :my
                                                                   {:my/Spec []
                                                                    :my/Result []}
                                                                   (spec :Result :concrete
                                                                         (variables [:x :my/Spec$v1, :optional])
                                                                         (refinements
                                                                          [:r :from :my/Other$v1 [:halite "placeholder"]]))
                                                                   (spec :Other :concrete)
                                                                   (spec :Spec :concrete
                                                                         (variables [:p "Integer"]
                                                                                    [:n "Integer"]
                                                                                    [:o "Integer" :optional])
                                                                         (constraints [:pc [:halite "(> p 0)"]]
                                                                                      [:pn [:halite "(< n 0)"]]))))
                        :instance {:$type :my/Other$v1}
                        :expr-str "(valid {:$type :my/Spec$v1, :p 1, :n 1})"
                        :expr-str-j "valid {$type: my/Spec$v1, p: 1, n: 1}"
                        :result :auto
                        :doc "When the spec has constraints that the field, p, must be positive and the field, n, must be negative."}]
            :see-also ['valid?]}
    'valid? {:sigs [["instance-expression" "boolean"]]
             :sigs-j [["'valid?' instance-expression" "boolean"]]
             :doc "Evaluate the instance expression and produce false if a constraint violation occurs during the evaluation. Otherwise, produce true."
             :comment "Similar to 'valid', but insted of possibly producing an instance, it produces a boolean indicating whether the instance was valid. This can be thought of as invoking a specification as a single predicate on a candidate instance value."
             :examples [{:workspace-f (make-workspace-fn (workspace :my
                                                                    {:my/Spec []
                                                                     :my/Result []}
                                                                    (spec :Result :concrete
                                                                          (variables [:x "Boolean" :optional])
                                                                          (refinements
                                                                           [:r :from :my/Other$v1 [:halite "placeholder"]]))
                                                                    (spec :Other :concrete)
                                                                    (spec :Spec :concrete
                                                                          (variables [:p "Integer"]
                                                                                     [:n "Integer"]
                                                                                     [:o "Integer" :optional])
                                                                          (constraints [:pc [:halite "(> p 0)"]]
                                                                                       [:pn [:halite "(< n 0)"]]))))
                         :instance {:$type :my/Other$v1}
                         :expr-str "(valid? {:$type :my/Spec$v1, :p 1, :n -1})"
                         :expr-str-j "valid? {$type: my/Spec$v1, p: 1, n: -1}"
                         :result :auto
                         :doc "When the spec has constraints that the field, p, must be positive and the field, n, must be negative."}
                        {:workspace-f (make-workspace-fn (workspace :my
                                                                    {:my/Spec []
                                                                     :my/Result []}
                                                                    (spec :Result :concrete
                                                                          (variables [:x "Boolean" :optional])
                                                                          (refinements
                                                                           [:r :from :my/Other$v1 [:halite "placeholder"]]))
                                                                    (spec :Other :concrete)
                                                                    (spec :Spec :concrete
                                                                          (variables [:p "Integer"]
                                                                                     [:n "Integer"]
                                                                                     [:o "Integer" :optional])
                                                                          (constraints [:pc [:halite "(> p 0)"]]
                                                                                       [:pn [:halite "(< n 0)"]]))))
                         :instance {:$type :my/Other$v1}
                         :expr-str "(valid? {:$type :my/Spec$v1, :p 1, :n 0})"
                         :expr-str-j "valid? {$type: my/Spec$v1, p: 1, n: 0}"
                         :result :auto
                         :doc "When the spec has constraints that the field, p, must be positive and the field, n, must be negative."}]
             :tags #{:instance-op :boolean-out :special-form}
             :see-also ['valid]}
    'when {:sigs [["boolean any-expression" "any"]]
           :sigs-j [["'when' '(' boolean ')' any-expression" "any"]]
           :tags #{:boolean-op :optional-out :control-flow :special-form}
           :doc "If the first argument is true, then evaluate the second argument, otherwise produce 'unset'."
           :examples [{:expr-str "(when (> 2 1) \"bigger\")"
                       :expr-str-j :auto
                       :result :auto}
                      {:expr-str "(when (< 2 1) \"bigger\")"
                       :expr-str-j :auto
                       :result :auto}]
           :comment "A primary use of this operator is in instance expression to optionally provide a value for a an optional field."}
    'when-value {:sigs [["symbol any-expression:binding" "any"]]
                 :sigs-j [["'whenValue' '(' symbol ')' any-expression" "any"]]
                 :tags #{:optional-op :optional-out :control-flow :special-form}
                 :doc "Consider the value bound to the symbol. If it is a 'value', then evaluate the second argument. If instead it is 'unset' then produce unset."
                 :examples [{:workspace-f (make-workspace-fn (workspace :my
                                                                        {:my/Spec []
                                                                         :my/Result []}
                                                                        (spec :Result :concrete
                                                                              (variables [:x "Integer" :optional])
                                                                              (refinements
                                                                               [:r :from :my/Spec$v1 [:halite "placeholder"]]))
                                                                        (spec :Spec :concrete
                                                                              (variables [:x "Integer" :optional]))))
                             :instance {:$type :my/Spec$v1, :x 1}
                             :expr-str "(when-value x (+ x 2))"
                             :expr-str-j "whenValue(x) {x + 2}"
                             :doc "In the context of an instance with an optional field, x, when the field is set to the value of '1'."
                             :result :auto}
                            {:workspace-f (make-workspace-fn (workspace :my
                                                                        {:my/Spec []
                                                                         :my/Result []}
                                                                        (spec :Result :concrete
                                                                              (variables [:x "Integer" :optional])
                                                                              (refinements
                                                                               [:r :from :my/Spec$v1 [:halite "placeholder"]]))
                                                                        (spec :Spec :concrete
                                                                              (variables [:x "Integer" :optional]))))
                             :instance {:$type :my/Spec$v1}
                             :expr-str "(when-value x (+ x 2))"
                             :expr-str-j "whenValue(x) {x + 2}"
                             :doc "In the context of an instance with an optional field, x, when the field is unset."
                             :result :auto}]
                 :see-also ['if-value 'when 'when-value-let]}
    'when-value-let {:sigs [["'[' symbol any:binding']' any-expression" "any"]]
                     :sigs-j [["'whenValueLet' '(' symbol '=' any:binding ')' any-expression" "any"]]
                     :tags #{:optional-op :optional-out :control-flow :special-form}
                     :doc "If the binding value is a 'value' then evaluate the second argument with the symbol bound to binding. If instead, the binding value is 'unset', then produce 'unset'"
                     :examples [{:workspace-f (make-workspace-fn (workspace :my
                                                                            {:my/Spec []
                                                                             :my/Result []}
                                                                            (spec :Result :concrete
                                                                                  (variables [:x "Integer" :optional])
                                                                                  (refinements
                                                                                   [:r :from :my/Spec$v1 [:halite "placeholder"]]))
                                                                            (spec :Spec :concrete
                                                                                  (variables [:y "Integer" :optional]))))
                                 :instance {:$type :my/Spec$v1, :y 1}
                                 :expr-str "(when-value-let [x (when-value y (+ y 2))] (inc x))"
                                 :expr-str-j "(whenValueLet ( x = (whenValue(y) {(y + 2)}) ) {(x + 1)})"
                                 :result :auto
                                 :doc "In the context of an instance with an optional field, y, when the field is set to the value of '1'."}
                                {:workspace-f (make-workspace-fn (workspace :my
                                                                            {:my/Spec []
                                                                             :my/Result []}
                                                                            (spec :Result :concrete
                                                                                  (variables [:x "Integer" :optional])
                                                                                  (refinements
                                                                                   [:r :from :my/Spec$v1 [:halite "placeholder"]]))
                                                                            (spec :Spec :concrete
                                                                                  (variables [:y "Integer" :optional]))))
                                 :instance {:$type :my/Spec$v1}
                                 :expr-str "(when-value-let [x (when-value y (+ y 2))] (inc x))"
                                 :expr-str-j "(whenValueLet ( x = (whenValue(y) {(y + 2)}) ) {(x + 1)})"
                                 :doc "In the context of an instance with an optional field, y, when the field is unset."
                                 :result :auto}]
                     :see-also ['if-value-let 'when 'when-value]}}))

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

(defn- rule-from-partitioned-bnf [partitioned-bnf k-f]
  (str "RULE = "
       "("
       (->> partitioned-bnf
            (map (fn [[n m]]
                   (let [bnf (k-f m)]
                     (when bnf
                       (str "("
                            "'" n ":' " "(" bnf ")"
                            ")")))))
            (remove nil?)
            (string/join " |\n"))
       ")"
       ";"))

(defn produce-basic-bnf-diagrams [all-file-name all-file-name-j basic-bnf]
  (produce-diagram (str "doc/halite-bnf-diagrams/basic-syntax/" all-file-name)
                   (rule-from-partitioned-bnf (partition 2 basic-bnf) :bnf))
  (produce-diagram (str "doc/halite-bnf-diagrams/basic-syntax/" all-file-name-j)
                   (rule-from-partitioned-bnf (partition 2 basic-bnf) (fn [bnf-map] (get bnf-map :bnf-j (:bnf bnf-map)))))

  (->> (partition 2 basic-bnf)
       (map (fn [[n {:keys [bnf]}]]
              (when bnf
                (produce-diagram (str "doc/halite-bnf-diagrams/basic-syntax/" n ".svg") (str "RULE = " "(" bnf ")" ";")))))
       dorun)

  (->> (partition 2 basic-bnf)
       (map (fn [[n bnf-map]]
              (let [bnf-j (get bnf-map :bnf-j (:bnf bnf-map))]
                (when bnf-j
                  (produce-diagram (str "doc/halite-bnf-diagrams/basic-syntax/" n "-j" ".svg") (str "RULE = " "(" bnf-j ")" ";"))))))
       dorun))

(defn produce-basic-bnf-diagrams-for-tag [basic-bnf tag]
  (let [filtered-partitioned-bnf (->> (partition 2 basic-bnf)
                                      (filter (fn [[k v]]
                                                (let [{:keys [tags]} v]
                                                  (when tags
                                                    (tags tag))))))]
    (produce-diagram (str "doc/halite-bnf-diagrams/basic-syntax/" (name tag) ".svg")
                     (rule-from-partitioned-bnf filtered-partitioned-bnf :bnf))
    (produce-diagram (str "doc/halite-bnf-diagrams/basic-syntax/" (str (name tag) "-j" ".svg"))
                     (rule-from-partitioned-bnf filtered-partitioned-bnf (fn [bnf-map] (get bnf-map :bnf-j (:bnf bnf-map)))))))

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
        rules-strs-j (->> op-maps
                          (mapcat (fn [[op {:keys [sigs-j]}]]
                                    (->> sigs-j
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
        single-rules-strs-j (->> op-maps
                                 (map (fn [[op {:keys [sigs-j]}]]
                                        [op (str " ( "
                                                 (->> sigs-j
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
                        (->> single-rules-strs-j
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
    (->> rules-strs-j
         (map (fn [{:keys [op-name sig-index ^String rule-str]}]
                (produce-diagram (str "doc/halite-bnf-diagrams/op/" (str (safe-op-name op-name) "-" sig-index "-j" ".svg")) rule-str)))
         dorun)))

(def safe-char-map
  (let [weird "*!$?=<>_+."
        norml "SBDQELGUAP"]
    (zipmap weird (map #(str "_" %) norml))))

(defn safe-op-anchor [s]
  (apply str (map #(safe-char-map % %) (str s))))

(defn url-encode [s]
  (java.net.URLEncoder/encode (str s)))

(defn text-width [s]
  (apply max 0 (map count (re-seq #".*" s))))

(defn text-tile-rows [texts]
  (let [chars-per-col 20
        cols-per-row 5]
    (reduce (fn [rows text]
              (let [cols (inc (quot (dec (text-width text)) chars-per-col))
                    tile {:text text, :cols cols}
                    last-row (peek rows)]
                (if (or (empty? rows)
                        (< cols-per-row (+ cols (:cols last-row))))
                  (conj rows {:cols cols :tiles [tile]})
                  (conj (pop rows) (-> last-row
                                       (update :cols + cols)
                                       (update :tiles conj tile))))))
            []
            texts)))

(defn example-text [lang e]
  (str (e ({:halite :expr-str, :jadeite :expr-str-j} lang))
       ({:halite  "\n\n;-- result --;\n"
         :jadeite "\n\n### result ###\n"}
        lang)
       (or (:result e)
           (:err-result e))))

(defn example-text-no-result [lang e]
  (str (if (= :halite lang)
         (:expr-str e)
         (or (:expr-str-j e)
             (:expr-str e)))))

(defn full-md [lang op-name op]
  (->> ["### "
        "<a name=\"" (safe-op-anchor op-name) "\"></a>"
        op-name "\n\n" (if (= :halite lang) (:doc op) (or (:doc-j op) (:doc op))) "\n\n"
        (when-let [d2 (:doc-2 op)] [d2 "\n\n"])
        (map-indexed
         (fn [i sig]
           ["![" (pr-str sig) "](./halite-bnf-diagrams/op/"
            (url-encode (safe-op-name op-name)) "-" i (when (= :jadeite lang) "-j") ".svg)\n\n"])
         (op ({:halite :sigs, :jadeite :sigs-j} lang)))
        (when-let [c (:comment op)] [c "\n\n"])
        (when-let [es (:examples op)]
          ["<table>"
           (for [row (text-tile-rows (map (partial example-text lang) es))]
             ["<tr>"
              (for [tile (:tiles row)]
                ["<td colspan=\"" (:cols tile) "\">\n\n"
                 "```" ({:halite "clojure", :jadeite "java"} lang) "\n"
                 (:text tile)
                 "\n```\n\n</td>"])
              "</tr>"])
           "</table>\n\n"])
        (when-let [t (:throws op)]
          ["#### Possible errors:\n\n"
           (for [msg t] (str "* " msg "\n"))
           "\n"])
        (when-let [alsos (:see-also op)]
          ["See also:"
           (for [a alsos]
             [" [`" a "`](#" (safe-op-anchor a) ")"])
           "\n\n"])
        "---\n"]
       flatten (apply str)))

(def generated-msg
  "<!---
  This markdown file was generated. Do not edit.
  -->\n\n")

(defn produce-full-md []
  (->> op-maps
       sort
       (map (partial apply full-md :halite))
       (apply str generated-msg "# Halite operator reference (all operators)\n\n")
       (spit "doc/halite-full-reference.md"))
  (->> op-maps
       sort
       (map (partial apply full-md :jadeite))
       (apply str generated-msg "# Jadeite operator reference (all operators)\n\n")
       (spit "doc/jadeite-full-reference.md")))

(defn basic-md [lang op-name op]
  (let [bnf (if (= :halite lang)
              (:bnf op)
              (if (contains? (set (keys op)) :bnf-j)
                (:bnf-j op)
                (or (:bnf-j op)
                    (:bnf op))))]
    (when bnf
      (->> ["### "
            "<a name=\"" (safe-op-anchor op-name) "\"></a>"
            op-name "\n\n" (if (= :halite lang) (or (:doc-j op) (:doc op))) "\n\n"
            (when-let [d2 (:doc-2 op)] [d2 "\n\n"])
            ["![" (pr-str bnf) "](./halite-bnf-diagrams/basic-syntax/"
             (url-encode (safe-op-name op-name)) (when (= :jadeite lang) "-j") ".svg)\n\n"]

            (when-let [c (:comment op)] [c "\n\n"])
            (when-let [es (:examples op)]
              ["<table>"
               (for [row (text-tile-rows (map (partial example-text-no-result lang) es))]
                 ["<tr>"
                  (for [tile (:tiles row)]
                    ["<td colspan=\"" (:cols tile) "\">\n\n"
                     "```" ({:halite "clojure", :jadeite "java"} lang) "\n"
                     (:text tile)
                     "\n```\n\n</td>"])
                  "</tr>"])
               "</table>\n\n"])
            "---\n"]
           flatten (apply str)))))

(defn produce-basic-md []
  (->> basic-bnf
       (partition 2)
       (map (partial apply basic-md :halite))
       (apply str generated-msg "# Halite basic syntax reference\n\n")
       (spit "doc/halite-basic-syntax-reference.md"))
  (->> basic-bnf
       (partition 2)
       (map (partial apply basic-md :jadeite))
       (apply str generated-msg "# Jadeite basic syntax reference\n\n")
       (spit "doc/jadeite-basic-syntax-reference.md")))

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
    (produce-basic-bnf-diagrams-for-tag basic-bnf :symbol-all)

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
         dorun)

    (produce-basic-md)
    (produce-full-md)))
