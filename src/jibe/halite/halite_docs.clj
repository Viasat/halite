;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.halite-docs
  (:require [clojure.string :as string])
  (:import [net.nextencia.rrdiagram.grammar.model GrammarToRRDiagram BNFToGrammar]
           [net.nextencia.rrdiagram.grammar.rrdiagram RRDiagramToSVG]))

(set! *warn-on-reflection* true)

(def misc-notes ["Commas are considered whitespace in halite expressions."])

(def basic-bnf ['basic-character {:bnf "'A-Z' | 'a-z' | '*' | '!' | '$' | '=' | '<' | '>' | '_' | '.' | '?'"}
                'plus-minus-character {:bnf "'+' | '-'"}
                'symbol-character {:bnf "basic-character | plus-minus-character | '0-9'"}
                'simple-symbol {:bnf "plus-minus-character | ((basic-character | plus-minus-character) [{symbol-character}])"}
                'symbol {:bnf "simple-symbol [ '/' simple-symbol]"
                         :j-bnf "(simple-symbol [ '/' simple-symbol]) | ('’' simple-symbol [ '/' simple-symbol] '’')"
                         :examples [{:str "a"
                                     :str-j "a"}
                                    {:expr-str "a.b"
                                     :expr-str-j "a.b"}]}
                'keyword {:bnf "':' symbol"
                          :j-bnf nil
                          :examples [{:str ":age"}
                                     {:str ":x/y"}]}

                'boolean {:bnf "true | false"}
                'string {:bnf " '\"' {char | '\\' ('\\' | '\"' | 't' | 'n' | ('u' hex-digit hex-digit hex-digit hex-digit))} '\"'"
                         :doc "Strings are sequences of characters. Strings can be multi-line. Quotation marks can be included if escaped with a \\. A backslash can be included with the character sequence: \\\\ . Strings can include special characters, e.g. \\t for a tab and \\n for a newline, as well as unicode via \\uNNNN. Unicode can also be directly entered in strings. Additional character representations may work but the only representations that are guaranteed to work are those documented here."
                         :examples [{:expr-str ""
                                     :expr-str-j ""}
                                    {:expr-str "hello"
                                     :expr-str-j "hello"}
                                    {:expr-str "say \"hi\" now"
                                     :expr-str-j "say \"hi\" now"}
                                    {:expr-str "one \\ two"
                                     :expr-str-j "one \\ two"}
                                    {:expr-str "\t\n"
                                     :expr-str-j "\t\n"}
                                    {:expr-str "☺"
                                     :expr-str-j "☺"}
                                    {:expr-str "\u263A"
                                     :expr-str-j "\u263A"}]}
                'integer {:bnf "[plus-minus-character] '0-9' {'0-9'}"
                          :doc "Signed numeric integer values with no decimal places. Alternative integer representations may work, but the only representation that is guaranteed to work on an ongoing basis is that documented here."
                          :examples [{:expr-str "0"
                                      :expr-str-j "0"}
                                     {:expr-str "1"
                                      :expr-str-j "1"}
                                     {:expr-str "+1"
                                      :expr-str-j "+1"}
                                     {:expr-str "-1"
                                      :expr-str-j "-1"}
                                     {:expr-str "9223372036854775807"
                                      :expr-str-j "9223372036854775807"}
                                     {:expr-str "-9223372036854775808"
                                      :expr-str-j "-9223372036854775808"}]}

                'fixed-decimal {:bnf "'#' 'd' [whitespace] '\"' ['-'] ('0' | ('1-9' {'0-9'})) '.' '0-9' {'0-9'} '\"'"
                                :doc "Signed numeric values with decimal places."
                                :examples [{:expr-str "#d \"1.1\""
                                            :expr-str-j "#d \"1.1\""}
                                           {:expr-str "#d \"-1.1\""
                                            :expr-str-j "#d \"-1.1\""}
                                           {:expr-str "#d \"1.00\""
                                            :expr-str-j "#d \"1.00\""}
                                           {:expr-str "#d \"0.00\""
                                            :expr-str-j "#d \"0.00\""}]}

                'instance {:bnf "'{' ':$type' keyword:spec-id {keyword value} '}' "
                           :j-bnf "'{' '$type' ':' symbol:spec-id {',' symbol ':' value } '}'"
                           :doc "Represents an instance of a specification."
                           :examples [{:str "{:$type :text/Spec$v1 :x 1 :y -1}"
                                       :str-j "{$type: my/Spec$v1, x: 1, y: -1}"}]}
                'vector {:bnf "'[' [whitespace] { value whitespace} [value] [whitespace] ']'"
                         :j-bnf "'[' [whitespace] [value] [whitespace] {',' [whitespace] value [whitespace]} [whitespace]']'"
                         :doc "A collection of values in a prescribed sequence."
                         :examples [{:expr-str "[]"
                                     :expr-str-j "[]"}
                                    {:expr-str "[1 2 3]"
                                     :expr-str-j "[1, 2, 3]"}]}
                'set {:bnf "'#' '{' [whitespace] { value [whitespace]} [value] [whitespace] '}'"
                      :j-bnf "'#' '{' [whitespace] [value] [whitespace] {',' [whitespace] value [whitespace]} '}'"
                      :doc "A collection of values in an unordered set. Duplicates are not allowed."
                      :examples [{:expr-str "#{}"
                                  :expr-str-j "#{}"}
                                 {:expr-str "#{1 2 3}"
                                  :expr-str-j "#{1, 2, 3}"}]}

                'value {:bnf "boolean | string | integer | fixed-decimal | instance | vector | set"}
                'any {:bnf "value | unset"}])

(def op-maps
  {'$no-value {:sigs [["" "unset"]]
               :j-sigs [["'$no-value'" "unset"]]
               :tags #{:optional-out}
               :doc "Constant that produces the special 'unset' value which represents the lack of a value."
               :comment "Expected use is in an instance expression to indicate that a field in the instance does not have a value. However, it is suggested that alternatives include simply omitting the field name from the instance or using a variant of a 'when' expression to optionally produce a value for the field."
               :see-also ['when 'when-value 'when-value-let]}
   '$this {:sigs [["" "value"]]
           :j-sigs [["<$this>" "unset"]]
           :tags #{}
           :doc "Context dependent reference to the containing object."}
   '* {:sigs [["integer integer" "integer"]
              ["fixed-decimal integer" "fixed-decimal"]]
       :j-sigs [["integer '*' integer" "integer"]
                ["fixed-decimal '*' integer" "fixed-decimal"]]
       :tags #{:integer-op :integer-out :fixed-decimal-op :fixed-decimal-out}
       :doc "Multiply two numbers together."
       :comment "Note that fixed-decimal values cannot be multiplied together. Rather the multiplication operator is used to scale a fixed-decimal value within the number space of a given scale of fixed-decimal. This can also be used to effectively convert an arbitrary integer value into a fixed-decimal number space by multiplying the integer by unity in the fixed-decimal number space of the desired scale."
       :throws ["On overflow"]
       :examples [{:expr-str "(* 2 3)"
                   :result :auto}
                  {:expr-str "(* #d \"2.2\" 3)"
                   :result :auto}]}
   '+ {:sigs [["integer integer {integer}" "integer"]
              ["fixed-decimal fixed-decimal {fixed-decimal}" "fixed-decimal"]]
       :j-sigs [["integer '+' integer" "integer"]
                ["fixed-decimal '+' fixed-decimal" "fixed-decimal"]]
       :tags #{:integer-op :integer-out :fixed-decimal-op :fixed-decimal-out}
       :doc "Add two numbers together."
       :throws ["On overflow"]}
   '- {:sigs [["integer integer {integer}" "integer"]
              ["fixed-decimal fixed-decimal {fixed-decimal}" "fixed-decimal"]]
       :j-sigs [["integer '-' integer" "integer"]
                ["fixed-decimal '-' fixed-decimal" "fixed-decimal"]]
       :tags #{:integer-op :integer-out :fixed-decimal-op :fixed-decimal-out}

       :doc "Subtract one number from another."
       :throws ["On overflow"]}
   '< {:sigs [["((integer integer) | (fixed-decimal fixed-decimal))" "boolean"]]
       :j-sigs [["((integer '<'  integer) | (fixed-decimal '<' fixed-decimal))" "boolean"]]
       :tags #{:integer-op :fixed-decimal-op :boolean-out}
       :doc "Determine if a number is strictly less than another."}
   '<= {:sigs [["((integer integer) | (fixed-decimal fixed-decimal))" "boolean"]]
        :j-sigs [["((integer '<=' integer) | (fixed-decimal '<=' fixed-decimal))" "boolean"]]
        :tags #{:integer-op :fixed-decimal-op :boolean-out}
        :doc "Determine if a number is less than or equal to another."}
   '= {:sigs [["value value {value}" "boolean"]]
       :j-sigs [["value '==' value" "boolean"]
                ["'equalTo' '(' value ',' value {',' value} ')'" "boolean"]]
       :tags #{:integer-op :fixed-decimal-op :set-op :vector-op :boolean-out :instance-op}
       :doc "Determine if two values are equivalent. For vectors and sets this performs a comparison of their contents."
       :see-also ['not=]}
   '=> {:sigs [["boolean boolean" "boolean"]]
        :j-sigs [["boolean '=>' boolean" "boolean"]]
        :tags #{:boolean-op :boolean-out}
        :doc "Performs logical implication. If the first value is true, then the second value must also be true for the result to be true. If the first value is false, then the result is true."}
   '> {:sigs [["((integer integer) | (fixed-decimal fixed-decimal))" "boolean"]]
       :j-sigs [["((integer '>'  integer) | (fixed-decimal '>' fixed-decimal))" "boolean"]]
       :tags #{:integer-op :fixed-decimal-op :boolean-out}
       :doc "Determine if a number is strictly greater than another."}
   '>= {:sigs [["((integer integer) | (fixed-decimal fixed-decimal))" "boolean"]]
        :j-sigs [["((integer '>='  integer) | (fixed-decimal '>=' fixed-decimal))" "boolean"]]
        :tags #{:integer-op :fixed-decimal-op :boolean-out}
        :doc "Determine if a number is greater than or equal to another."}
   'abs {:sigs [["integer" "integer"]
                ["fixed-decimal" "fixed-decimal"]]
         :j-sigs [["'abs' '(' integer ')'" "integer"]
                  ["'abs' '(' fixed-decimal ')'" "fixed-decimal"]]
         :tags #{:integer-op :integer-out :fixed-decimal-op :fixed-decimal-out}
         :doc "Compute the absolute value of a number."
         :comment "Since the negative number space contains one more value than the positive number space, it is a runtime error to attempt to take the absolute value of the most negative value for a given number space."
         :throws ["Cannot compute absolute value most max negative value"]}
   'and {:sigs [["boolean {boolean}" "boolean"]]
         :j-sigs [["boolean '&&' {boolean}" "boolean"]]
         :tags #{:boolean-op :boolean-out}
         :doc "Perform a logical 'and' operation on the input values."
         :comment "The operation does not short-circuit. Even if the first argument evaluates to false the other arguments are still evaluated."
         :see-also ['=> 'every? 'not 'or]}
   'any? {:sigs [["'[' symbol (set | vector) ']' boolean-expression" "boolean"]]
          :j-sigs [["'any?' '(' symbol 'in' (set | vector) ')' boolean-expression" "boolean"]]
          :tags #{:set-op :vector-op :boolean-out :special-form}
          :doc "Evaluates to true if the boolean-expression is true when the symbol is bound to some element in the collection."
          :comment "The operation does not short-circuit. The boolean-expression is evaluated for all elements even if a prior element has caused the boolean-expression to evaluate to true. Operating on an empty collection produces a false value."
          :see-also ['every? 'or]}
   'concat {:sigs [["vector vector" "vector"]
                   ["(set (set | vector))" "set"]]
            :j-sigs [["vector '.' 'concat' '('  vector ')'" "vector"]
                     ["(set '.' 'concat' '(' (set | vector) ')')" "set"]]
            :tags #{:set-op :vector-op :vector-out :set-out}
            :doc "Combine two collections into one."
            :comment "Invoking this operation with a vector and an empty set has the effect of converting a vector into a set with duplicate values removed."}
   'conj {:sigs [["set value {value}" "set"]
                 ["vector value {value}" "vector"]]
          :j-sigs [["set '.' 'conj' '(' value {',' value} ')'" "set"]
                   ["vector '.' 'conj' '(' value {',' value} ')'" "vector"]]
          :tags #{:set-op :vector-op :set-out :vector-out}
          :doc "Add individual items to a collection."
          :comment "Only definite values may be put into collections, i.e. collections cannot contain 'unset' values."}
   'contains? {:sigs [["set value" "boolean"]]
               :j-sigs [["set '.' 'contains?' '(' value ')'" "boolean"]]
               :tags #{:set-op :boolean-out}
               :doc "Determine if a specific value is in a set."
               :comment "Since collections themselves are compared by their contents, this works for collections nested inside of sets."}
   'count {:sigs [["(set | vector)" "integer"]]
           :j-sigs [["(set | vector) '.' 'count()'" "integer"]]
           :tags #{:integer-out :set-op :vector-op}
           :doc "Return how many items are in a collection."}
   'dec {:sigs [["integer" "integer"]]
         :j-sigs [["integer '-' '1' " "integer"]]
         :tags #{:integer-op :integer-out}
         :doc "Decrement a numeric value."
         :throws ["On overflow"]
         :see-also ['inc]}
   'difference {:sigs [["set set" "set"]]
                :j-sigs [["set '.' 'difference' '(' set ')'" "set"]]
                :tags #{:set-op :set-out}
                :doc "Compute the set difference of two sets."
                :comment "This produces a set which contains all of the elements from the first set which do not appear in the second set."
                :see-also ['intersection 'union 'subset?]}
   'div {:sigs [["integer integer" "integer"]
                ["fixed-decimal integer" "fixed-decimal"]]
         :j-sigs [["integer '/' integer" "integer"]
                  ["fixed-decimal '/' integer" "fixed-decimal"]]
         :tags #{:integer-op :integer-out :fixed-decimal-op :fixed-decimal-out}
         :doc "Divide the first number by the second. When the first argument is an integer the result is truncated to an integer value. When the first argument is a fixed-decimal the result is truncated to the same precision as the first argument."
         :comment "As with multiplication, fixed-decimal values cannot be divided by each other, instead a fixed-decimal value can be scaled down within the number space of that scale."
         :throws ["When the second argument is 0"]
         :see-also ['mod]}
   'error {:sigs [["string" "nothing"]]
           :j-sigs [["'error' '(' string ')'" "nothing"]]
           :tags #{:nothing-out}
           :doc "Produce a runtime error with the provided string as an error message."
           :comment "Used to indicate when an unexpected condition has occurred and the data at hand is invalid. It is preferred to use constraints to capture such conditions earlier."
           :throws ["Always"]}
   'every? {:sigs [["'[' symbol (set | vector) ']' boolean-expression" "boolean"]]
            :j-sigs [["'every?' '(' symbol 'in' (set | vector) ')' boolean-expression" "boolean"]]
            :tags #{:set-op :vector-op :boolean-out :special-form}
            :doc "Evaluates to true if the boolean-expression is true when the symbol is bound to each the element in the collection."
            :comment "Does not short-circuit. The boolean-expression is evaluated for all elements, even once a prior element has evaluated to false. Operating on an empty collection produces a true value."
            :see-also ['any? 'and]}
   'expt {:sigs [["integer integer" "integer"]]
          :j-sigs [["'expt' '(' integer ',' integer ')'" "integer"]]
          :tags #{:integer-op :integer-out}
          :doc "Compute the numeric result of raising the first argument to the power given by the second argument."
          :throws ["On overflow"]}
   'filter {:sigs [["'[' symbol:element set ']' boolean-expression" "set"]
                   ["'[' symbol:element vector ']' boolean-expression" "vector"]]
            :j-sigs [["'filter' '(' symbol 'in' set ')' boolean-expression" "set"]
                     ["'filter' '(' symbol 'in' vector ')' boolean-expression" "vector"]]
            :tags #{:set-op :vector-op :set-out :vector-out :special-form}
            :doc "Produce a new collection which contains only the elements from the original collection for which the boolean-expression is true. When applied to a vector, the order of the elements in the result preserves the order from the original vector."
            :see-also ['map 'filter]}
   'first {:sigs [["vector" "value"]]
           :j-sigs [["vector '.' 'first()'" "value"]]
           :tags #{:vector-op}
           :doc "Produce the first element from a vector."
           :comment "To avoid runtime errors, if the vector might be empty, use 'count' to check the length first."
           :throws ["When invoked on an empty vector."]
           :see-also ['count 'rest]}
   'get {:sigs [["(instance keyword:instance-field)" "any"]
                ["(vector integer)" "value"]]
         :j-sigs [["(instance '.' symbol:instance-field)" "any"]
                  ["(vector '[' integer ']')" "value"]]
         :tags #{:vector-op :instance-op :optional-out :instance-field-op}
         :doc "Extract the given item from the first argument. If the first argument is an instance, extract the value for the given field from the given instance. For optional fields, this may produce 'unset'. Otherwise this will always produce a value. If the first argument is a vector, then extract the value at the given index in the vector. The index in this case is zero based."
         :comment "The $type value of an instance is not considered a field that can be extracted with this operator. When dealing with instances of abstract specifications, it is necessary to refine an instance to a given specification before accessing a field of that specification."
         :throws [['h-err/index-out-of-bounds]
                  ['h-err/variables-not-in-spec]]
         :see-also ['get-in]}
   'get-in {:sigs [["(instance:target | vector:target) '[' (integer | keyword:instance-field) {(integer | keyword:instance-field)} ']'" "any"]]
            :notes ["if the last element of the lookup path is an integer, then the result is a value"
                    "if the last element of the lookup path is an instance field name, then the result is an 'any'; specifically of that last field is the name of an optional field"
                    "the non-terminal field names in the lookup path must be the names of mandatory fields"]
            :j-sigs [["( (instance:target '.' symbol:instance-field) | (vector:target '[' integer ']') ){ ( ('.' symbol:instance-field) | ('[' integer ']' ) ) }"
                      "any"]]
            :tags #{:vector-op :instance-op :optional-out :instance-field-op}
            :doc "Syntactic sugar for performing the equivalent of a chained series of 'get' operations. The second argument is a vector that represents the logical path to be navigated through the first argument."
            :j-doc "A path of element accessors can be created by chaining together element access forms in sequence."
            :doc-2 "The first path element in the path is looked up in the initial target. If there are more path elements, the next path element is looked up in the result of the first lookup. This is repeated as long as there are more path elements. If this is used to lookup instance fields, then all of the field names must reference mandatory fields unless the field name is the final element of the path. The result will always be a value unless the final path element is a reference to an optional field. In this case, the result may be a value or may be 'unset'."
            :throws ['l-err/get-in-path-cannot-be-empty
                     'h-err/invalid-lookup-target
                     'h-err/variables-not-in-spec
                     'h-err/index-out-of-bounds]
            :see-also ['get]}
   'if {:sigs [["boolean any-expression any-expression" "any"]]
        :j-sigs [["'if' '(' boolean ')' any-expression 'else' any-expression" "any"]]
        :tags #{:boolean-op :control-flow :special-form}
        :doc "If the first argument is true, then evaluate the second argument, otherwise evaluate the third argument."
        :see-also ['when]}
   'if-value {:sigs [["symbol any-expression any-expression" "any"]]
              :j-sigs [["'ifValue' '(' symbol ')' any-expression 'else' any-expression" "any"]]
              :doc "Consider the value bound to the symbol. If it is a 'value', then evaluate the second argument. If instead it is 'unset' then evaluate the third argument."
              :comment "When an optional instance field needs to be referenced, it is generally necessary to guard the access with either 'if-value' or 'when-value'. In this way, both the case of the field being set and unset are explicitly handled."
              :tags #{:optional-op :control-flow :special-form}
              :see-also ['if-value-let 'when-value]}
   'if-value-let {:sigs [["'[' symbol any:binding ']' any-expression any-expression" "any"]]
                  :j-sigs [["'ifValueLet' '(' symbol '=' any:binding ')'  any-expression 'else' any-expression" "any"]]
                  :tags #{:optional-op :control-flow :special-form}
                  :doc "If the binding value is a 'value' then evaluate the second argument with the symbol bound to binding. If instead, the binding value is 'unset', then evaluate the third argument without introducing a new binding for the symbol."
                  :comment "This is similar to the 'if-value' operation, but applies generally to an expression which may or may not produce a value."
                  :see-also ['if-value 'when-value-let]}
   'inc {:sigs [["integer" "integer"]]
         :j-sigs [["integer '+' '1'" "integer"]]
         :tags #{:integer-op :integer-out}
         :doc "Increment a numeric value."
         :throws ["On overflow"]
         :see-also ['dec]}
   'intersection {:sigs [["set set {set}" "set"]]
                  :j-sigs [["set '.' 'intersection' '(' set {',' set} ')'" "set"]]
                  :tags #{:set-op :set-out}
                  :doc "Compute the set intersection of the sets."
                  :comment "This produces a set which only contains values that appear in each of the arguments."
                  :see-also ['difference 'union 'subset?]}
   'let {:sigs [["'[' symbol value {symbol value} ']' any-expression" "any"]]
         :j-sigs [["'{' symbol '=' value ';' {symbol '=' value ';'} any-expression '}'" "any"]]
         :tags #{:special-form}
         :doc "Evaluate the expression argument in a nested context created by considering the first argument in a pairwise fashion and binding each symbol to the corresponding value."
         :comment "Allows names to be given to values so that they can be referenced by the any-expression."
         :j-doc "Evaluate the expression argument in a nested context created by binding each symbol to the corresponding value."}
   'map {:sigs [["'[' symbol:element set ']' value-expression" "set"]
                ["'[' symbol:element vector ']' value-expression" "vector"]]
         :j-sigs [["'map' '(' symbol:element 'in' set ')' value-expression" "set"]
                  ["'map' '(' symbol:element 'in' vector ')' value-expression" "vector"]]
         :tags #{:set-op :vector-op :set-out :vector-out :special-form}
         :doc "Produce a new collection from a collection by evaluating the expression with the symbol bound to each element of the original collection, one-by-one. The results of evaluating the expression will be in the resulting collection. When operating on a vector, the order of the output vector will correspond to the order of the items in the original vector."
         :see-also ['reduce 'filter]}
   'mod {:sigs [["integer integer" "integer"]]
         :j-sigs [["integer '%' integer" "integer"]]
         :tags #{:integer-op :integer-out}
         :doc "Computes the mathematical modulus of two numbers. Use care if one of the arguments is negative."
         :thows ["Second argument is 0"]}
   'not {:sigs [["boolean" "boolean"]]
         :j-sigs [["'!' boolean" "boolean"]]
         :tags #{:boolean-op :boolean-out}
         :doc "Performs logical negation of the argument."
         :see-also ['=> 'and 'or]}
   'not= {:sigs [["value value {value}" "boolean"]]
          :j-sigs [["value '!=' value" "boolean"]
                   ["'notEqualTo' '(' value ',' value {',' value} ')'" "boolean"]]
          :tags #{:integer-op :fixed-decimal-op :set-op :vector-op :instance-op :boolean-op :boolean-out}
          :doc "Produces a false value if all of the values are equal to each other. Otherwise produces a true value."
          :see-also ['=]}
   'or {:sigs [["boolean {boolean}" "boolean"]]
        :j-sigs [["boolean '||' {boolean}" "boolean"]]
        :tags #{:boolean-op :boolean-out}
        :doc "Perform a logical 'or' operation on the input values."
        :comment "The operation does not short-circuit. Even if the first argument evaluates to true the other arguments are still evaluated."
        :see-also ['=> 'and 'any? 'not]}
   'range {:sigs [["[integer:start] integer:end [integer:increment]" "vector"]]
           :j-sigs [["'range' '(' [integer:start ','] integer:end [',' integer:increment] ')'" "vector"]]
           :doc "Produce a vector that contains integers in order starting at either the start value or 0 if no start is provided. The final element of the vector will be no more than one less than the end value. If an increment is provided then only every increment integer will be included in the result."
           :tags #{:vector-out}}
   'reduce {:sigs [["'[' symbol:accumulator value:accumulator-init ']' '[' symbol:element vector ']' any-expression" "any"]]
            :j-sigs [["'reduce' '(' symbol:accumulator '=' value:accumulator-init ';' symbol:element 'in' vector ')' any-expression" "any"]]
            :tags #{:vector-op :special-form}
            :doc "Evalue the expression repeatedly for each element in the vector. The accumulator value will have a value of accumulator-init on the first evaluation of the expression. Subsequent evaluations of the expression will chain the prior result in as the value of the accumulator. The result of the final evaluation of the expression will be produced as the result of the reduce operation. The elements are processed in order."
            :see-also ['map 'filter]
            :notes ["'normally' a reduce will produce a value, but the body could produce a 'maybe' value or even always produce 'unset', in which case the reduce may not produce a value"]}
   'refine-to {:sigs [["instance keyword:spec-id" "instance"]]
               :j-sigs [["instance '.' 'refineTo' '(' symbol:spec-id ')'" "instance"]]
               :tags #{:instance-op :instance-out :spec-id-op}
               :doc "Attempt to refine the given instance into an instance of type, spec-id."
               :throws ["No refinement path"
                        "Spec not found"]
               :see-also ['refines-to?]}
   'refines-to? {:sigs [["instance keyword:spec-id" "boolean"]]
                 :j-sigs [["instance '.' 'refinesTo?' '(' symbol:spec-id ')'" "boolean"]]
                 :tags #{:instance-op :boolean-out :spec-id-op}
                 :doc "Determine whether it is possible to refine the given instance into an instance of type, spec-id."
                 :see-also ['refine-to]
                 :throws ["Spec not found"]}
   'rescale {:sigs [["fixed-decimal integer:new-scale" "(fixed-decimal | integer)"]]
             :j-sigs [["'rescale' '(' fixed-decimal ',' integer ')'" "(fixed-decimal | integer)"]]
             :tags #{:integer-out :fixed-decimal-op :fixed-decimal-out}
             :doc "Produce a number by adjusting the scale of the fixed-decimal to the new-scale. If the scale is being reduced, the original number is truncated. If the scale is being increased, then the original number is padded with zeroes in the decimal places. If the new-scale is zero, then the result is an integer."
             :comment "Arithmetic on numeric values never produce results in different number spaces. This operation provides an explicit way to convert a fixed-decimal value into a value with the scale of a different number space. This includes the ability to convert a fixed-decimal value into an integer."
             :notes ["if the new scale is 0, then an integer is produced"
                     "scale must be positive"
                     "scale must be between 0 and 18 (inclusive)"]
             :see-also ['*]}
   'rest {:sigs [["vector" "vector"]]
          :j-sigs [["vector '.' 'rest()'" "vector"]]
          :doc "Produce a new vector which contains the same element of the argument, in the same order, except the first element is removed. If there are no elements in the argument, then an empty vector is produced."
          :tags #{:vector-op :vector-out}}
   'sort {:sigs [["(set | vector)" "vector"]]
          :j-sigs [["(set | vector) '.' 'sort()'" "vector"]]
          :tags #{:set-op :vector-op :vector-out}
          :doc "Produce a new vector by sorting all of the items in the argument. Only collections of numeric values may be sorted."
          :throws ["Elements not sortable"]
          :see-also ['sort-by]}
   'sort-by {:sigs [["'[' symbol:element (set | vector) ']' (integer-expression | fixed-decimal-expression)" "vector"]]
             :j-sigs [["'sortBy' '(' symbol:element 'in' (set | vector) ')' (integer-expression | fixed-decimal-expression)" "vector"]]
             :tags #{:set-op :vector-op :vector-out :special-form}
             :doc "Produce a new vector by sorting all of the items in the input collection according to the values produced by applying the expression to each element. The expression must produce a unique, sortable value for each element."
             :throws ["Expression does not produce sortable values"
                      'sort-value-collision]
             :see-also ['sort]}
   'str {:sigs [["string {string}" "string"]]
         :j-sigs [["'str' '(' string {',' string} ')'" "string"]]
         :doc "Combine all of the input strings together in sequence to produce a new string."
         :tags #{:string-op}}
   'subset? {:sigs [["set set" "boolean"]]
             :j-sigs [["set '.' 'subset?' '(' set ')'" "boolean"]]
             :tags #{:set-op :boolean-out}
             :doc "Return false if there are any items in the first set which do not appear in the second set. Otherwise return true."
             :comment "According to this operation, a set is always a subset of itself and every set is a subset of the empty set. Using this operation and an equality check in combination allows a 'superset?' predicate to be computed."
             :see-also ['difference 'intersection 'union]}
   'union {:sigs [["set set {set}" "set"]]
           :j-sigs [["set '.' 'union' '(' set {',' set} ')'" "set"]]
           :tags #{:set-op :set-out}
           :doc "Compute the union of all the sets."
           :comment "This produces a set which contains all of the values that appear in any of the arguments."
           :see-also ['difference 'intersection 'subset?]}
   'valid {:sigs [["instance-expression" "any"]]
           :j-sigs [["'valid' instance-expression" "any"]]
           :tags #{:instance-op :optional-out :special-form}
           :doc "Evaluate the instance-expression and produce the result. If a constraint violation occurs while evaluating the expression then produce an 'unset' value."
           :comment "This operation can be thought of as producing an instance if it is valid. This considers not just the constraints on the immediate instance, but also the constraints implied by refinements defined on the specification."
           :see-also ['valid?]}
   'valid? {:sigs [["instance-expression" "boolean"]]
            :j-sigs [["'valid?' instance-expression" "boolean"]]
            :doc "Evaluate the instance expression and produce false if a constraint violation occurs during the evaluation. Otherwise, produce true."
            :comment "Similar to 'valid', but insted of possibly producing an instance, it produces a boolean indicating whether the instance was valid. This can be thought of as invoking a specification as a single predicate on a candidate instance value."
            :tags #{:instance-op :boolean-out :special-form}
            :see-also ['valid]}
   'when {:sigs [["boolean any-expression" "any"]]
          :j-sigs [["'when' '(' boolean ')' any-expression" "any"]]
          :tags #{:boolean-op :optional-out :control-flow :special-form}
          :doc "If the first argument is true, then evaluate the second argument, otherwise produce 'unset'."
          :comment "A primary use of this operator is in instance expression to optionally provide a value for a an optional field."}
   'when-value {:sigs [["symbol any-expression:binding" "any"]]
                :j-sigs [["'whenValue' '(' symbol ')' any-expression" "any"]]
                :tags #{:optional-op :optional-out :control-flow :special-form}
                :doc "Consider the value bound to the symbol. If it is a 'value', then evaluate the second argument. If instead it is 'unset' then produce unset."
                :see-also ['if-value 'when 'when-value-let]}
   'when-value-let {:sigs [["'[' symbol any:binding']' any-expression" "any"]]
                    :j-sigs [["'whenValueLet' '(' symbol '=' any:binding ')' any-expression" "any"]]
                    :tags #{:optional-op :optional-out :control-flow :special-form}
                    :doc "If the binding value is a 'value' then evaluate the second argument with the symbol bound to binding. If instead, the binding value is 'unset', then produce 'unset'"
                    :see-also ['if-value-let 'when 'when-value]}})

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
