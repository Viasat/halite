;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.doc.data-basic-bnf)

(set! *warn-on-reflection* true)

(def basic-bnf-vector
  ['non-numeric-character {:bnf "'A-Z' | 'a-z' | '*' | '!' | '$' | '=' | '<' | '>' | '_' | '.' | '?'"}
   'plus-minus-character {:bnf "'+' | '-'"}
   'symbol-character {:bnf "non-numeric-character | plus-minus-character | '0-9'"}
   'bare-symbol {:bnf "plus-minus-character | ((non-numeric-character | plus-minus-character) {symbol-character})"}
   'symbol {:bnf "bare-symbol [ '/' bare-symbol]"
            :bnf-j "(bare-symbol [ '/' bare-symbol]) | ('’' bare-symbol [ '/' bare-symbol] '’')"
            :doc "Symbols are identifiers that allow values and operations to be named. The following are reserved and cannot be used as user defined symbols: true, false, nil."
            :comment "Symbols are used to identify operators, variables in expressions, and specifications."
            :comment-j "Symbols are used to identify operators, variables in expressions, specifications, and fields within specifications."
            :comment-2 "Symbols are not values. There are no expressions that produce symbols. Anywhere that a symbol is called for in an operator argument list, a literal symbol must be provided. Symbols passed as arguments to operators are not evaluated. Symbols used within expressions in general are evaluated prior to invoking the operator."
            :comment-3 "A common pattern in operator arguments is to provide a sequence of alternating symbols and values within square brackets. In these cases each symbol is bound to the corresponding value in pair-wise fashion."
            :comment-3-j "The following are also legal symbols, but they are reserved for system use: &&, ||, /, %, |"
            :throws ['h-err/invalid-symbol-char
                     'h-err/invalid-symbol-length]
            :examples [{:expr-str "a"
                        :expr-str-j "a"}
                       {:expr-str "a.b"
                        :expr-str-j "a.b"}
                       {:expr-str "a/b"
                        :expr-str-j :auto}]
            :tags #{}
            :tags-j #{'spec-id-op 'instance-field-op 'instance-op 'instance-out}}
   'keyword {:bnf "':' symbol"
             :bnf-j nil
             :doc "Keywords are identifiers that are used for instance field names. The following are reserved and cannot be used as user defined keywords: :true, :false, :nil."
             :comment "Keywords are not values. There are no expressions that produce keywords. Anywhere that a keyword is called for in an operator arugment list, a literal keyword must be provided. Keywords themselves cannot be evaluated."
             :examples [{:expr-str ":age"
                         :expr-str-j :auto}
                        {:expr-str ":x/y"
                         :expr-str-j :auto}]
             :tags #{'spec-id-op 'instance-field-op 'instance-op 'instance-out}
             :tags-j #{}
             :throws ['h-err/invalid-keyword-char
                      'h-err/invalid-keyword-length]}

   'boolean {:bnf "true | false"
             :tags #{'boolean-op 'boolean-out}}
   'string {:bnf " '\"' {char | '\\' ('\\' | '\"' | 't' | 'n' | ('u' hex-digit hex-digit hex-digit hex-digit))} '\"'"
            :doc "Strings are sequences of characters. Strings can be multi-line. Quotation marks can be included if escaped with a \\. A backslash can be included with the character sequence: \\\\ . Strings can include special characters, e.g. \\t for a tab and \\n for a newline, as well as unicode via \\uNNNN. Unicode can also be directly entered in strings. Additional character representations may work but the only representations that are guaranteed to work are those documented here."
            :examples [{:expr-str "\"\""
                        :expr-str-j :auto}
                       {:expr-str "\"hello\""
                        :expr-str-j :auto}
                       {:expr-str "\"say \\\"hi\\\" now\""
                        :expr-str-j :auto
                        :result :auto}
                       {:expr-str "\"one \\\\ two\""
                        :expr-str-j :auto
                        :result :auto}
                       {:expr-str "\"\\t\\n\""
                        :expr-str-j :auto
                        :result :auto}
                       {:expr-str "\"☺\""
                        :expr-str-j :auto
                        :result :auto}
                       {:expr-str "\"\\u263A\""
                        :expr-str-j "\"\\u263A\""
                        :result :auto}]
            :tags #{'string-op}
            :throws ['h-err/size-exceeded]}
   'integer {:bnf "[plus-minus-character] '0-9' {'0-9'}"
             :doc "Signed, eight byte numeric integer values. Alternative integer representations may work, but the only representation that is guaranteed to work on an ongoing basis is that documented here. The largest positive integer is 9223372036854775807. The most negative integer is -9223372036854775808.\n\nSome language targets (eg. bounds-propagation) may use 4 instead of 8 bytes. On overflow, math operations never wrap; instead the evaluator will throw a runtime error."
             :throws ['h-err/overflow]
             :examples [{:expr-str "0"
                         :expr-str-j :auto}
                        {:expr-str "1"
                         :expr-str-j :auto}
                        {:expr-str "+1"
                         :expr-str-j :auto}
                        {:expr-str "-1"
                         :expr-str-j :auto}
                        {:expr-str "9223372036854775807"
                         :expr-str-j :auto}
                        {:expr-str "-9223372036854775808"
                         :expr-str-j :auto}
                        {:expr-str "-0"
                         :expr-str-j :auto}]
             :tags #{'integer-out 'integer-op}}

   'fixed-decimal {:bnf "'#' 'd' [whitespace] '\"' ['-'] ('0' | ('1-9' {'0-9'})) '.' '0-9' {'0-9'} '\"'"
                   :doc "Signed numeric values with decimal places. The scale (i.e. the number of digits to the right of the decimal place), must be between one and 18. Conceptually, the entire numeric value must fit into the same number of bytes as an 'integer'. So the largest fixed-decimal value with a scale of one is: #d \"922337203685477580.7\", and the most negative value with a scale of one is: #d \"-922337203685477580.8\". Similarly, the largest fixed-decimal value with a scale of 18 is: #d \"9.223372036854775807\" and the most negative value with a scale of 18 is: #d \"-9.223372036854775808\". The scale of the fixed-decimal value can be set to what is needed, but as more precision is added to the right of the decimal place, fewer digits are available to the left of the decimal place."
                   :throws ['h-err/overflow]
                   :examples [{:expr-str "#d \"1.1\""
                               :expr-str-j :auto}
                              {:expr-str "#d \"-1.1\""
                               :expr-str-j :auto}
                              {:expr-str "#d \"1.00\""
                               :expr-str-j :auto}
                              {:expr-str "#d \"0.00\""
                               :expr-str-j :auto}
                              {:expr-str "#d \"922337203685477580.7\""
                               :expr-str-j :auto}
                              {:expr-str "#d \"-922337203685477580.8\""
                               :expr-str-j :auto}
                              {:expr-str "#d \"9.223372036854775807\""
                               :expr-str-j :auto}
                              {:expr-str "#d \"-9.223372036854775808\""
                               :expr-str-j :auto}]
                   :tags #{'fixed-decimal-op 'fixed-decimal-out}}

   'instance {:bnf "'{' ':$type' keyword:spec-id {keyword value} '}' "
              :bnf-j "'{' '$type' ':' symbol:spec-id {',' symbol ':' value } '}'"
              :doc "Represents an instance of a specification."
              :comment "The contents of the instance are specified in pair-wise fashion with alternating field names and field values."
              :comment-2 "The special field name ':$type' is mandatory but cannot be used as the other fields are."
              :comment-2-j "The special field name '$type' is mandatory but cannot be used as the other fields are."
              :examples [{:expr-str "{:$type :text/Spec$v1 :x 1 :y -1}"
                          :expr-str-j "{$type: my/Spec$v1, x: 1, y: -1}"}]
              :throws ['h-err/no-abstract
                       'h-err/resource-spec-not-found
                       'h-err/missing-type-field
                       'h-err/missing-required-vars
                       'h-err/invalid-instance
                       'h-err/field-value-of-wrong-type
                       'h-err/field-name-not-in-spec
                       'h-err/invalid-type-value
                       'h-err/not-boolean-constraint ;; is this right?
                       'h-err/spec-cycle-runtime
                       'h-err/refinement-diamond]
              :how-to-ref [:instance/spec-variables]
              :tutorial-ref [:spec/sudoku]
              :tags #{'instance-out 'instance-op 'instance-field-op}}
   'vector {:bnf "'[' [whitespace] { value whitespace} [value] [whitespace] ']'"
            :bnf-j "'[' [whitespace] [value] [whitespace] {',' [whitespace] value [whitespace]} [whitespace]']'"
            :doc "A collection of values in a prescribed sequence."
            :examples [{:expr-str "[]"
                        :expr-str-j :auto}
                       {:expr-str "[1 2 3]"
                        :expr-str-j :auto}
                       {:expr-str "[#{1 2} #{3}]"
                        :expr-str-j :auto}]
            :throws ['h-err/literal-must-evaluate-to-value
                     'h-err/size-exceeded
                     'h-err/unknown-type-collection]
            :tags #{'vector-op 'vector-out}}
   'set {:bnf "'#' '{' [whitespace] { value [whitespace]} [value] [whitespace] '}'"
         :bnf-j "'#' '{' [whitespace] [value] [whitespace] {',' [whitespace] value [whitespace]} '}'"
         :doc "A collection of values in an unordered set. Duplicates are not allowed."
         :comment "The members of sets are not directly accessible. If it is necessary to access the members of a set, it is recommended to design the data structures going into the sets in such a way that the set can be sorted into a vector for access."
         :examples [{:expr-str "#{}"
                     :expr-str-j :auto}
                    {:expr-str "#{1 2 3}"
                     :expr-str-j :auto}
                    {:expr-str "#{[1 2] [3]}"
                     :expr-str-j :auto}]
         :throws ['h-err/literal-must-evaluate-to-value
                  'h-err/size-exceeded
                  'h-err/unknown-type-collection]
         :tags #{'set-op 'set-out}}
   'value {:bnf "boolean | string | integer | fixed-decimal | instance | vector | set"
           :doc "Expressions and many literals produce values."}
   'unset {:bnf "unset"
           :doc "A pseudo-value that represents the lack of a value."
           :explanation-ref [:language/unset]}
   'nothing {:bnf "nothing"
             :doc "The absence of a value."}
   'any {:bnf "value | unset"
         :doc "Refers to either the presence of absence of a value, or a pseudo-value indicating the lack of a value."}
   'comment {:bnf "';' comment"
             :bnf-j "'//' comment"
             :doc "Comments that are not evaluated as part of the expression."}])
