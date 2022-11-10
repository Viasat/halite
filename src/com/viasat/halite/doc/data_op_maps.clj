;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.doc.data-op-maps)

(set! *warn-on-reflection* true)

(defn make-spec-map-fn [spec-map]
  (fn [expr]
    (let [first-pair (first spec-map)
          rest-pairs (rest spec-map)]
      (apply hash-map (mapcat identity (into [(update-in first-pair
                                                         [1
                                                          :refines-to]
                                                         (fn [refinements]
                                                           (let [first-refinement (first refinements)
                                                                 rest-refinements (rest refinements)]
                                                             (apply hash-map (mapcat identity (into [(update-in first-refinement [1 :expr]
                                                                                                                (fn [_]
                                                                                                                  {:$type :my/Result$v1
                                                                                                                   :x expr}))]
                                                                                                    rest-refinements))))))]
                                             rest-pairs))))))

(def op-maps
  {'$no-value {:sigs [["" "unset"]]
               :sigs-j [["'$no-value'" "unset"]]
               :tags #{:optional-out}
               :doc "Constant that produces the special 'unset' value which represents the lack of a value."
               :comment "Expected use is in an instance expression to indicate that a field in the instance does not have a value. Alternatives include simply omitting the field name from the instance or using a variant of a `when` expression to optionally produce a value for the field."
               :basic-ref ['unset]
               :op-ref ['when 'when-value 'when-value-let 'if-value 'if-value-let]
               :examples [{:expr-str "(if-value $no-value 7 13)"
                           :expr-str-j :auto
                           :result :auto}
                          {:spec-map {:my/Spec$v1 {:spec-vars {:x [:Maybe :Integer]}}}
                           :expr-str "{:$type :my/Spec$v1,\n :x $no-value}"
                           :expr-str-j :auto
                           :result :auto}]}
   '$this {:sigs [["" "value"]]
           :sigs-j [["<$this>" "unset"]]
           :doc "Context dependent reference to the containing object."}
   '* {:sigs [["integer integer" "integer"]
              ["fixed-decimal integer" "fixed-decimal"]]
       :sigs-j [["integer '*' integer" "integer"]
                ["fixed-decimal '*' integer" "fixed-decimal"]]
       :tags #{:integer-op :integer-out :fixed-decimal-op :fixed-decimal-out}
       :basic-ref ['integer 'fixed-decimal]
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
       :basic-ref ['integer 'fixed-decimal]
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
       :basic-ref ['integer 'fixed-decimal]
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
       :basic-ref ['integer 'fixed-decimal 'boolean]
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
        :basic-ref ['integer 'fixed-decimal 'boolean]
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
       :sigs-j [["'equalTo' '(' value ',' value {',' value} ')'" "boolean"]
                ["value '==' value" "boolean"]]
       :tags #{:integer-op :fixed-decimal-op :set-op :vector-op :boolean-out :boolean-op :instance-op
               :optional-op}
       :basic-ref ['value 'boolean]
       :doc "Determine if values are equivalent. For vectors and sets this performs a comparison of their contents."
       :throws ['l-err/result-always-known]
       :op-ref ['= 'not=]
       :examples [{:expr-str "(= 2 2)"
                   :expr-str-j :auto
                   :result :auto}
                  {:expr-str "(= #d \"2.2\" #d \"3.3\")"
                   :expr-str-j :auto
                   :result :auto}
                  {:expr-str "(= 2 3)"
                   :expr-str-j :auto
                   :result :auto}
                  {:expr-str "(= 1 1 1)"
                   :expr-str-j :auto
                   :result :auto}
                  {:expr-str "(= 1 1 2)"
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
                  {:spec-map {:my/Spec$v1 {:spec-vars {:x :Integer
                                                       :y :Integer}}}
                   :instance {:$type :my/Other$v1}
                   :expr-str "(= {:$type :my/Spec$v1 :x 1 :y -1} {:$type :my/Spec$v1 :x 1 :y 0})"
                   :expr-str-j :auto
                   :result :auto}
                  {:spec-map {:my/Spec$v1 {:spec-vars {:x :Integer
                                                       :y :Integer}}}
                   :instance {:$type :my/Other$v1}
                   :expr-str "(= {:$type :my/Spec$v1 :x 1 :y 0} {:$type :my/Spec$v1 :x 1 :y 0})"
                   :expr-str-j :auto
                   :result :auto}]}
   '=> {:sigs [["boolean boolean" "boolean"]]
        :sigs-j [["boolean '=>' boolean" "boolean"]]
        :tags #{:boolean-op :boolean-out}
        :basic-ref ['boolean]
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
        :op-ref ['and 'every? 'not 'or]}
   '> {:sigs [["((integer integer) | (fixed-decimal fixed-decimal))" "boolean"]]
       :sigs-j [["((integer '>'  integer) | (fixed-decimal '>' fixed-decimal))" "boolean"]]
       :tags #{:integer-op :fixed-decimal-op :boolean-out}
       :basic-ref ['integer 'fixed-decimal 'boolean]
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
        :basic-ref ['integer 'fixed-decimal 'boolean]
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
         :basic-ref ['integer 'fixed-decimal]
         :doc "Compute the absolute value of a number."
         :comment "Since the negative number space contains one more value than the positive number space, it is a runtime error to attempt to take the absolute value of the most negative value for a given number space."
         :throws ['h-err/abs-failure]
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
         :basic-ref ['boolean]
         :doc "Perform a logical 'and' operation on the input values."
         :comment "The operation does not short-circuit. Even if the first argument evaluates to false the other arguments are still evaluated."
         :op-ref ['=> 'every? 'not 'or]
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
          :basic-ref ['set 'vector 'boolean]
          :doc "Evaluates to true if the boolean-expression is true when the symbol is bound to some element in the collection."
          :comment "The operation does not short-circuit. The boolean-expression is evaluated for all elements even if a prior element has caused the boolean-expression to evaluate to true. Operating on an empty collection produces a false value."
          :examples [{:expr-str "(any? [x [1 2 3]] (> x 1))"
                      :expr-str-j :auto
                      :result :auto}
                     {:expr-str "(any? [x #{1 2 3}] (> x 10))"
                      :expr-str-j :auto
                      :result :auto}]
          :throws ['h-err/comprehend-binding-wrong-count
                   'h-err/comprehend-collection-invalid-type
                   'l-err/binding-target-invalid-symbol
                   'h-err/binding-target-must-be-bare-symbol
                   'h-err/not-boolean-body]
          :op-ref ['every? 'or]
          :how-to-ref [:collections/vector-containment]}
   'concat {:sigs [["vector vector" "vector"]
                   ["(set (set | vector))" "set"]]
            :sigs-j [["vector '.' 'concat' '('  vector ')'" "vector"]
                     ["(set '.' 'concat' '(' (set | vector) ')')" "set"]]
            :tags #{:set-op :vector-op :vector-out :set-out}
            :basic-ref ['vector 'set]
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
                        :result :auto}]
            :throws ['h-err/not-both-vectors
                     'h-err/size-exceeded
                     'h-err/unknown-type-collection]}
   'conj {:sigs [["set value {value}" "set"]
                 ["vector value {value}" "vector"]]
          :sigs-j [["set '.' 'conj' '(' value {',' value} ')'" "set"]
                   ["vector '.' 'conj' '(' value {',' value} ')'" "vector"]]
          :tags #{:set-op :vector-op :set-out :vector-out}
          :basic-ref ['vector 'set 'value]
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
                      :result :auto}]
          :throws ['h-err/argument-not-set-or-vector
                   'h-err/cannot-conj-unset
                   'h-err/size-exceeded
                   'h-err/unknown-type-collection]}
   'contains? {:sigs [["set value" "boolean"]]
               :sigs-j [["set '.' 'contains?' '(' value ')'" "boolean"]]
               :tags #{:set-op :boolean-out}
               :basic-ref ['set 'value 'boolean]
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
           :basic-ref ['set 'vector 'integer]
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
         :basic-ref ['integer]
         :doc "Decrement a numeric value."
         :throws ['h-err/overflow]
         :op-ref ['inc]
         :examples [{:expr-str "(dec 10)"
                     :result :auto}
                    {:expr-str "(dec 0)"
                     :result :auto}]}
   'difference {:sigs [["set set" "set"]]
                :sigs-j [["set '.' 'difference' '(' set ')'" "set"]]
                :tags #{:set-op :set-out}
                :basic-ref ['set]
                :doc "Compute the set difference of two sets."
                :comment "This produces a set which contains all of the elements from the first set which do not appear in the second set."
                :op-ref ['intersection 'union 'subset?]
                :throws ['h-err/arguments-not-sets]
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
         :basic-ref ['integer 'fixed-decimal]
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
         :op-ref ['mod]}
   'error {:sigs [["string" "nothing"]]
           :sigs-j [["'error' '(' string ')'" "nothing"]]
           :tags #{:nothing-out}
           :basic-ref ['nothing]
           :doc "Produce a runtime error with the provided string as an error message."
           :comment "Used to indicate when an unexpected condition has occurred and the data at hand is invalid. It is preferred to use constraints to capture such conditions earlier."
           :examples [{:expr-str "(error \"failure\")"
                       :expr-str-j :auto
                       :result :auto}]
           :throws ['h-err/spec-threw]}
   'every? {:sigs [["'[' symbol (set | vector) ']' boolean-expression" "boolean"]]
            :sigs-j [["'every?' '(' symbol 'in' (set | vector) ')' boolean-expression" "boolean"]]
            :tags #{:set-op :vector-op :boolean-out :special-form}
            :basic-ref ['set 'vector 'boolean]
            :doc "Evaluates to true if the boolean-expression is true when the symbol is bound to each the element in the collection."
            :comment "Does not short-circuit. The boolean-expression is evaluated for all elements, even once a prior element has evaluated to false. Operating on an empty collection produces a true value."
            :examples [{:expr-str "(every? [x [1 2 3]] (> x 0))"
                        :expr-str-j :auto
                        :result :auto}
                       {:expr-str "(every? [x #{1 2 3}] (> x 1))"
                        :expr-str-j :auto
                        :result :auto}]
            :throws ['h-err/comprehend-binding-wrong-count
                     'h-err/comprehend-collection-invalid-type
                     'l-err/binding-target-invalid-symbol
                     'h-err/binding-target-must-be-bare-symbol
                     'h-err/not-boolean-body]
            :op-ref ['any? 'and]}
   'expt {:sigs [["integer integer" "integer"]]
          :sigs-j [["'expt' '(' integer ',' integer ')'" "integer"]]
          :tags #{:integer-op :integer-out}
          :basic-ref ['integer]
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
          :throws ['h-err/overflow
                   'h-err/invalid-exponent]}
   'filter {:sigs [["'[' symbol:element set ']' boolean-expression" "set"]
                   ["'[' symbol:element vector ']' boolean-expression" "vector"]]
            :sigs-j [["'filter' '(' symbol 'in' set ')' boolean-expression" "set"]
                     ["'filter' '(' symbol 'in' vector ')' boolean-expression" "vector"]]
            :tags #{:set-op :vector-op :set-out :vector-out :special-form}
            :basic-ref ['set 'vector 'boolean]
            :doc "Produce a new collection which contains only the elements from the original collection for which the boolean-expression is true. When applied to a vector, the order of the elements in the result preserves the order from the original vector."
            :examples [{:expr-str "(filter [x [1 2 3]] (> x 2))"
                        :expr-str-j :auto
                        :result :auto}
                       {:expr-str "(filter [x #{1 2 3}] (> x 2))"
                        :expr-str-j :auto
                        :result :auto}]
            :throws ['h-err/comprehend-binding-wrong-count
                     'h-err/comprehend-collection-invalid-type
                     'l-err/binding-target-invalid-symbol
                     'h-err/binding-target-must-be-bare-symbol
                     'h-err/not-boolean-body]
            :op-ref ['map]}
   'first {:sigs [["vector" "value"]]
           :sigs-j [["vector '.' 'first()'" "value"]]
           :tags #{:vector-op}
           :basic-ref ['vector 'value]
           :doc "Produce the first element from a vector."
           :comment "To avoid runtime errors, if the vector might be empty, use 'count' to check the length first."
           :throws ['h-err/argument-empty
                    'h-err/argument-not-vector]
           :examples [{:expr-str "(first [10 20 30])"
                       :expr-str-j :auto
                       :result :auto}
                      {:expr-str "(first [])"
                       :expr-str-j :auto
                       :result :auto}]
           :op-ref ['count 'rest]}
   'get {:sigs [["(instance keyword:instance-field)" "any"]
                ["(vector integer)" "value"]]
         :sigs-j [["(instance '.' symbol:instance-field)" "any"]
                  ["(vector '[' integer ']')" "value"]]
         :tags #{:vector-op :instance-op :optional-out :instance-field-op :special-form}
         :basic-ref ['instance 'vector 'keyword 'integer 'any]
         :basic-ref-j ['instance 'vector 'symbol 'integer 'any]
         :doc "Extract the given item from the first argument. If the first argument is an instance, extract the value for the given field from the given instance. For optional fields, this may produce 'unset'. Otherwise this will always produce a value. If the first argument is a vector, then extract the value at the given index in the vector. The index in this case is zero based."
         :comment "The $type value of an instance is not considered a field that can be extracted with this operator. When dealing with instances of abstract specifications, it is necessary to refine an instance to a given specification before accessing a field of that specification."
         :throws ['h-err/index-out-of-bounds
                  'h-err/field-name-not-in-spec
                  'h-err/invalid-vector-index
                  'h-err/invalid-instance-index]
         :examples [{:expr-str "(get [10 20 30 40] 2)"
                     :expr-str-j :auto
                     :result :auto}
                    {:spec-map {:my/Spec$v1 {:spec-vars {:x :Integer
                                                         :y :Integer}}}
                     :expr-str "(get {:$type :my/Spec$v1, :x -3, :y 2} :x)"
                     :expr-str-j :auto
                     :result :auto}]
         :op-ref ['get-in]}
   'get-in {:sigs [["(instance:target | vector:target) '[' (integer | keyword:instance-field) {(integer | keyword:instance-field)} ']'" "any"]]
            :notes ["if the last element of the lookup path is an integer, then the result is a value"
                    "if the last element of the lookup path is an instance field name, then the result is an 'any'; specifically of that last field is the name of an optional field"
                    "the non-terminal field names in the lookup path must be the names of mandatory fields"]
            :sigs-j [["( (instance:target '.' symbol:instance-field) | (vector:target '[' integer ']') ){ ( ('.' symbol:instance-field) | ('[' integer ']' ) ) }"
                      "any"]]
            :tags #{:vector-op :instance-op :optional-out :instance-field-op :special-form}
            :basic-ref ['instance 'vector 'keyword 'integer 'any]
            :basic-ref-j ['instance 'vector 'symbol 'integer 'any]
            :doc "Syntactic sugar for performing the equivalent of a chained series of 'get' operations. The second argument is a vector that represents the logical path to be navigated through the first argument."
            :doc-j "A path of element accessors can be created by chaining together element access forms in sequence."
            :doc-2 "The first path element in the path is looked up in the initial target. If there are more path elements, the next path element is looked up in the result of the first lookup. This is repeated as long as there are more path elements. If this is used to lookup instance fields, then all of the field names must reference mandatory fields unless the field name is the final element of the path. The result will always be a value unless the final path element is a reference to an optional field. In this case, the result may be a value or may be 'unset'."
            :throws ['l-err/get-in-path-empty
                     'h-err/invalid-lookup-target
                     'h-err/field-name-not-in-spec
                     'h-err/index-out-of-bounds
                     'h-err/invalid-vector-index
                     'h-err/invalid-instance-index
                     'h-err/get-in-path-must-be-vector-literal]
            :examples [{:expr-str "(get-in [[10 20] [30 40]] [1 0])"
                        :expr-str-j :auto
                        :result :auto}
                       {:spec-map {:my/Spec$v1 {:spec-vars {:x :my/SubSpec$v1
                                                            :y :Integer}}
                                   :my/SubSpec$v1 {:spec-vars {:a :Integer
                                                               :b :Integer}}}
                        :expr-str "(get-in {:$type :my/Spec$v1, :x {:$type :my/SubSpec$v1, :a 20, :b 10}, :y 2} [:x :a])"
                        :expr-str-j :auto
                        :result :auto}
                       {:spec-map {:my/Spec$v1 {:spec-vars {:x :my/SubSpec$v1
                                                            :y :Integer}}
                                   :my/SubSpec$v1 {:spec-vars {:a [:Vec :Integer]
                                                               :b :Integer}}}
                        :instance {:$type :my/Other$v1}
                        :expr-str "(get-in {:$type :my/Spec$v1, :x {:$type :my/SubSpec$v1, :a [20 30 40], :b 10}, :y 2} [:x :a 1])"
                        :expr-str-j :auto
                        :result :auto}]
            :op-ref ['get]}
   'if {:sigs [["boolean any-expression any-expression" "any"]]
        :sigs-j [["'if' '(' boolean ')' any-expression 'else' any-expression" "any"]]
        :tags #{:boolean-op :control-flow :special-form}
        :basic-ref ['boolean 'any]
        :doc "If the first argument is true, then evaluate the second argument, otherwise evaluate the third argument."
        :examples [{:expr-str "(if (> 2 1) 10 -1)"
                    :expr-str-j :auto
                    :result :auto}
                   {:expr-str "(if (> 2 1) 10 (error \"fail\"))"
                    :expr-str-j :auto
                    :result :auto}]
        :op-ref ['when]}
   'if-value {:sigs [["symbol any-expression any-expression" "any"]]
              :sigs-j [["'ifValue' '(' symbol ')' any-expression 'else' any-expression" "any"]]
              :doc "Consider the value bound to the symbol. If it is a 'value', then evaluate the second argument. If instead it is 'unset' then evaluate the third argument."
              :comment "When an optional instance field needs to be referenced, it is generally necessary to guard the access with either 'if-value' or 'when-value'. In this way, both the case of the field being set and unset are explicitly handled."
              :tags #{:optional-op :control-flow :special-form}
              :basic-ref ['symbol 'any]
              :throws ['h-err/if-value-must-be-bare-symbol
                       'l-err/first-argument-not-optional]
              :op-ref ['if-value-let 'when-value]
              :explanation-ref [:language/unset]}
   'if-value-let {:sigs [["'[' symbol any:binding ']' any-expression any-expression" "any"]]
                  :sigs-j [["'ifValueLet' '(' symbol '=' any:binding ')'  any-expression 'else' any-expression" "any"]]
                  :basic-ref ['symbol 'any]
                  :tags #{:optional-op :control-flow :special-form}
                  :doc "If the binding value is a 'value' then evaluate the second argument with the symbol bound to binding. If instead, the binding value is 'unset', then evaluate the third argument without introducing a new binding for the symbol."
                  :comment "This is similar to the 'if-value' operation, but applies generally to an expression which may or may not produce a value."
                  :examples [{:spec-map-f (make-spec-map-fn {:my/Other$v1 {:refines-to {:my/Result$v1 {:name "r"
                                                                                                       :expr 'placeholder
                                                                                                       :inverted? true}}}
                                                             :my/Result$v1 {:spec-vars {:x :Integer}}
                                                             :my/Spec$v1 {:spec-vars {:n :Integer
                                                                                      :o [:Maybe :Integer]
                                                                                      :p :Integer}
                                                                          :constraints {:pc '(> p 0)
                                                                                        :pn '(< n 0)}}})
                              :instance {:$type :my/Other$v1}
                              :expr-str "(if-value-let [x (when (> 2 1) 19)] (inc x) 0)"
                              :expr-str-j :auto
                              :result :auto}
                             {:spec-map-f (make-spec-map-fn {:my/Other$v1 {:refines-to {:my/Result$v1 {:name "r"
                                                                                                       :expr 'placeholder
                                                                                                       :inverted? true}}}
                                                             :my/Result$v1 {:spec-vars {:x :Integer}},
                                                             :my/Spec$v1 {:spec-vars {:n :Integer
                                                                                      :o [:Maybe :Integer]
                                                                                      :p :Integer}
                                                                          :constraints {:pc '(> p 0)
                                                                                        :pn '(< n 0)}}})
                              :instance {:$type :my/Other$v1}
                              :expr-str "(if-value-let [x (when (> 1 2) 19)] (inc x) 0)"
                              :expr-str-j :auto
                              :result :auto}]
                  :throws ['h-err/binding-target-must-be-bare-symbol
                           'l-err/binding-expression-not-optional]
                  :op-ref ['if-value 'when-value-let]}
   'inc {:sigs [["integer" "integer"]]
         :sigs-j [["integer '+' '1'" "integer"]]
         :tags #{:integer-op :integer-out}
         :basic-ref ['integer]
         :doc "Increment a numeric value."
         :examples [{:expr-str "(inc 10)"
                     :result :auto}
                    {:expr-str "(inc 0)"
                     :result :auto}]
         :throws ['h-err/overflow]
         :op-ref ['dec]}
   'intersection {:sigs [["set set {set}" "set"]]
                  :sigs-j [["set '.' 'intersection' '(' set {',' set} ')'" "set"]]
                  :tags #{:set-op :set-out}
                  :basic-ref ['set]
                  :doc "Compute the set intersection of the sets."
                  :comment "This produces a set which only contains values that appear in each of the arguments."
                  :examples [{:expr-str "(intersection #{1 2 3} #{2 3 4})"
                              :expr-str-j :auto
                              :result :auto}
                             {:expr-str "(intersection #{1 2 3} #{2 3 4} #{3 4})"
                              :expr-str-j :auto
                              :result :auto}]
                  :throws ['h-err/arguments-not-sets]
                  :op-ref ['difference 'union 'subset?]}
   'let {:sigs [["'[' symbol value {symbol value} ']' any-expression" "any"]]
         :sigs-j [["'{' symbol '=' value ';' {symbol '=' value ';'} any-expression '}'" "any"]]
         :tags #{:special-form}
         :basic-ref ['symbol 'any]
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
         :doc-j "Evaluate the expression argument in a nested context created by binding each symbol to the corresponding value."
         :throws ['h-err/let-bindings-odd-count
                  'h-err/let-needs-bare-symbol
                  'h-err/cannot-bind-reserved-word
                  'l-err/cannot-bind-unset
                  'l-err/cannot-bind-nothing
                  'l-err/binding-target-invalid-symbol
                  'l-err/let-bindings-empty
                  'l-err/disallowed-unset-variable]}
   'map {:sigs [["'[' symbol:element set ']' value-expression" "set"]
                ["'[' symbol:element vector ']' value-expression" "vector"]]
         :sigs-j [["'map' '(' symbol:element 'in' set ')' value-expression" "set"]
                  ["'map' '(' symbol:element 'in' vector ')' value-expression" "vector"]]
         :tags #{:set-op :vector-op :set-out :vector-out :special-form}
         :basic-ref ['symbol 'value 'set 'vector]
         :tutorial-ref [:spec/grocery]
         :doc "Produce a new collection from a collection by evaluating the expression with the symbol bound to each element of the original collection, one-by-one. The results of evaluating the expression will be in the resulting collection. When operating on a vector, the order of the output vector will correspond to the order of the items in the original vector."
         :examples [{:expr-str "(map [x [10 11 12]] (inc x))"
                     :expr-str-j :auto
                     :result :auto}
                    {:expr-str "(map [x #{10 12}] (* x 2))"
                     :expr-str-j :auto
                     :result :auto}]
         :throws ['h-err/comprehend-binding-wrong-count
                  'h-err/comprehend-collection-invalid-type
                  'l-err/binding-target-invalid-symbol
                  'h-err/binding-target-must-be-bare-symbol
                  'h-err/must-produce-value]
         :op-ref ['reduce 'filter]}
   'mod {:sigs [["integer integer" "integer"]]
         :sigs-j [["integer '%' integer" "integer"]]
         :tags #{:integer-op :integer-out}
         :basic-ref ['integer]
         :doc "Computes the mathematical modulus of two numbers. Use care if one of the arguments is negative."
         :examples [{:expr-str "(mod 12 3)"
                     :expr-str-j :auto
                     :result :auto}
                    {:expr-str "(mod 14 4)"
                     :expr-str-j :auto
                     :result :auto}
                    {:expr-str "(mod 1 0)"
                     :expr-str-j :auto
                     :result :auto}
                    {:expr-str "(mod 1 3)"
                     :expr-str-j :auto
                     :result :auto}
                    {:expr-str "(mod -1 3)"
                     :expr-str-j :auto
                     :result :auto}
                    {:expr-str "(mod 1 -3)"
                     :expr-str-j :auto
                     :result :auto}
                    {:expr-str "(mod -1 -3)"
                     :expr-str-j :auto
                     :result :auto}]
         :throws ['h-err/divide-by-zero]}
   'not {:sigs [["boolean" "boolean"]]
         :sigs-j [["'!' boolean" "boolean"]]
         :tags #{:boolean-op :boolean-out}
         :basic-ref ['boolean]
         :doc "Performs logical negation of the argument."
         :examples [{:expr-str "(not true)"
                     :expr-str-j :auto
                     :result :auto}
                    {:expr-str "(not false)"
                     :expr-str-j :auto
                     :result :auto}]
         :op-ref ['=> 'and 'or]}
   'not= {:sigs [["value value {value}" "boolean"]]
          :sigs-j [["'notEqualTo' '(' value ',' value {',' value} ')'" "boolean"]
                   ["value '!=' value" "boolean"]]
          :tags #{:integer-op :fixed-decimal-op :set-op :vector-op :instance-op :boolean-op :boolean-out
                  :optional-op}
          :basic-ref ['value 'boolean]
          :doc "Produces a false value if all of the values are equal to each other. Otherwise produces a true value."
          :throws ['l-err/result-always-known]
          :examples [{;; TODO: include jadeite example of notEqualTo
                      :expr-str "(not= 2 3)"
                      :expr-str-j :auto
                      :result :auto}
                     {:expr-str "(not= #d \"2.2\" #d \"2.2\")"
                      :expr-str-j :auto
                      :result :auto}
                     {:expr-str "(not= 2 2)"
                      :expr-str-j :auto
                      :result :auto}
                     {:expr-str "(not= 1 1 2)"
                      :expr-str-j :auto
                      :result :auto}
                     {:expr-str "(not= 1 1 1)"
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
                     {:spec-map {:my/Spec$v1 {:spec-vars {:x :Integer
                                                          :y :Integer}}}
                      :expr-str "(not= {:$type :my/Spec$v1 :x 1 :y -1} {:$type :my/Spec$v1 :x 1 :y 0})"
                      :expr-str-j :auto
                      :result :auto}]
          :op-ref ['= 'not=]}
   'or {:sigs [["boolean boolean {boolean}" "boolean"]]
        :sigs-j [["boolean '||' boolean" "boolean"]]
        :tags #{:boolean-op :boolean-out}
        :basic-ref ['boolean]
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
        :op-ref ['=> 'and 'any? 'not]}
   'range {:sigs [["[integer:start] integer:end [integer:increment]" "vector"]]
           :sigs-j [["'range' '(' [integer:start ','] integer:end [',' integer:increment] ')'" "vector"]]
           :basic-ref ['integer 'vector]
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
           :throws ['h-err/size-exceeded]
           :tags #{:vector-out}}
   'reduce {:sigs [["'[' symbol:accumulator value:accumulator-init ']' '[' symbol:element vector ']' any-expression" "any"]]
            :sigs-j [["'reduce' '(' symbol:accumulator '=' value:accumulator-init ';' symbol:element 'in' vector ')' any-expression" "any"]]
            :tags #{:vector-op :special-form}
            :basic-ref ['symbol 'vector 'any]
            :tutorial-ref [:spec/grocery]
            :doc "Evalue the expression repeatedly for each element in the vector. The accumulator value will have a value of accumulator-init on the first evaluation of the expression. Subsequent evaluations of the expression will chain the prior result in as the value of the accumulator. The result of the final evaluation of the expression will be produced as the result of the reduce operation. The elements are processed in order."
            :examples [{:expr-str "(reduce [a 10] [x [1 2 3]] (+ a x))"
                        :expr-str-j :auto
                        :result :auto}]
            :throws ['h-err/binding-target-must-be-bare-symbol
                     'h-err/element-binding-target-must-be-bare-symbol
                     'h-err/element-accumulator-same-symbol
                     'h-err/accumulator-target-must-be-bare-symbol
                     'h-err/reduce-not-vector]
            :op-ref ['map 'filter]
            :notes ["'normally' a reduce will produce a value, but the body could produce a 'maybe' value or even always produce 'unset', in which case the reduce may not produce a value"]}
   'refine-to {:sigs [["instance keyword:spec-id" "instance"]]
               :sigs-j [["instance '.' 'refineTo' '(' symbol:spec-id ')'" "instance"]]
               :tags #{:instance-op :instance-out :spec-id-op}
               :basic-ref ['instance 'keyword]
               :basic-ref-j ['instance 'symbol]
               :tutorial-ref [:spec/grocery]
               :explanation-ref [:spec/refinement-terminology :spec/refinement-implications :spec/refinements-as-functions]
               :doc "Attempt to refine the given instance into an instance of type, spec-id."
               :throws ['h-err/no-refinement-path
                        'h-err/resource-spec-not-found
                        'h-err/refinement-error
                        'h-err/invalid-refinement-expression]
               :examples [{:spec-map {:my/Spec$v1 {:refines-to {:an/Other$v1 {:name "r"
                                                                              :expr '{:$type :an/Other$v1}}}}
                                      :an/Other$v1 {}}
                           :expr-str "(refine-to {:$type :my/Spec$v1} :an/Other$v1)"
                           :expr-str-j :auto
                           :result :auto
                           :doc "A basic refinement."}
                          {:spec-map {:my/Spec$v1 {:spec-vars {:p :Integer
                                                               :n :Integer}
                                                   :refines-to {:an/Other$v1 {:name "r"
                                                                              :expr '{:$type :an/Other$v1
                                                                                      :x (inc p)
                                                                                      :y (dec n)}}}}
                                      :an/Other$v1 {:spec-vars {:x :Integer
                                                                :y :Integer}}}
                           :expr-str "(refine-to {:$type :my/Spec$v1, :p 1, :n -1} :an/Other$v1)"
                           :expr-str-j :auto
                           :result :auto
                           :doc "An example of a refinement that transforms data values."}
                          {:spec-map {:my/Spec$v1 {}
                                      :an/Other$v1 {}}
                           :expr-str "(refine-to {:$type :my/Spec$v1} :an/Other$v1)"
                           :expr-str-j :auto
                           :result :auto
                           :doc "An example where the refinement being invoked does not exist."}]
               :op-ref ['refines-to?]}
   'refines-to? {:sigs [["instance keyword:spec-id" "boolean"]]
                 :sigs-j [["instance '.' 'refinesTo?' '(' symbol:spec-id ')'" "boolean"]]
                 :tags #{:instance-op :boolean-out :spec-id-op}
                 :basic-ref ['instance 'keyword 'boolean]
                 :basic-ref-j ['instance 'symbol 'boolean]
                 :doc "Determine whether it is possible to refine the given instance into an instance of type, spec-id."
                 :op-ref ['refine-to]
                 :examples [{:spec-map {:my/Spec$v1 {:refines-to {:an/Other$v1 {:name "r"
                                                                                :expr '{:$type :an/Other$v1}}}}
                                        :an/Other$v1 {}}
                             :expr-str "(refines-to? {:$type :my/Spec$v1} :an/Other$v1)"
                             :expr-str-j :auto
                             :result :auto
                             :doc "A basic refinement."}
                            {:spec-map {:my/Spec$v1 {:spec-vars {:p :Integer
                                                                 :n :Integer}
                                                     :refines-to {:an/Other$v1 {:name "r"
                                                                                :expr '{:$type :an/Other$v1
                                                                                        :x (inc p)
                                                                                        :y (dec n)}}}}
                                        :an/Other$v1 {:spec-vars {:x :Integer
                                                                  :y :Integer}}}
                             :expr-str "(refines-to? {:$type :my/Spec$v1, :p 1, :n -1} :an/Other$v1)"
                             :expr-str-j :auto
                             :result :auto
                             :doc "An example of a refinement that transforms data values."}
                            {:spec-map {:my/Spec$v1 {}
                                        :an/Other$v1 {}}
                             :expr-str "(refines-to? {:$type :my/Spec$v1} :an/Other$v1)"
                             :expr-str-j :auto
                             :result :auto
                             :doc "An example where the refinement being invoked does not exist."}]
                 :throws ['h-err/resource-spec-not-found
                          'h-err/refinement-error
                          'h-err/invalid-refinement-expression]}
   'rescale {:sigs [["fixed-decimal integer:new-scale" "(fixed-decimal | integer)"]]
             :sigs-j [["'rescale' '(' fixed-decimal ',' integer ')'" "(fixed-decimal | integer)"]]
             :tags #{:integer-out :fixed-decimal-op :fixed-decimal-out}
             :basic-ref ['fixed-decimal 'integer]
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
             :op-ref ['*]}
   'rest {:sigs [["vector" "vector"]]
          :sigs-j [["vector '.' 'rest()'" "vector"]]
          :basic-ref ['vector]
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
          :throws ['h-err/argument-not-vector]
          :tags #{:vector-op :vector-out}}
   'sort {:sigs [["(set | vector)" "vector"]]
          :sigs-j [["(set | vector) '.' 'sort()'" "vector"]]
          :tags #{:set-op :vector-op :vector-out}
          :basic-ref ['vector 'set]
          :doc "Produce a new vector by sorting all of the items in the argument. Only collections of numeric values may be sorted."
          :examples [{:expr-str "(sort [2 1 3])"
                      :expr-str-j :auto
                      :result :auto}
                     {:expr-str "(sort [#d \"3.3\" #d \"1.1\" #d \"2.2\"])"
                      :expr-str-j :auto
                      :result :auto}]
          :op-ref ['sort-by]}
   'sort-by {:sigs [["'[' symbol:element (set | vector) ']' (integer-expression | fixed-decimal-expression)" "vector"]]
             :sigs-j [["'sortBy' '(' symbol:element 'in' (set | vector) ')' (integer-expression | fixed-decimal-expression)" "vector"]]
             :tags #{:set-op :vector-op :vector-out :special-form}
             :basic-ref ['vector 'set 'integer 'symbol]
             :doc "Produce a new vector by sorting all of the items in the input collection according to the values produced by applying the expression to each element. The expression must produce a unique, sortable value for each element."
             :throws ['h-err/not-sortable-body
                      'h-err/sort-value-collision
                      'h-err/binding-target-must-be-bare-symbol
                      'l-err/binding-target-invalid-symbol]
             :examples [{:expr-str "(sort-by [x [[10 20] [30] [1 2 3]]] (first x))"
                         :expr-str-j :auto
                         :result :auto}]
             :op-ref ['sort]}
   'str {:sigs [["string string {string}" "string"]]
         :sigs-j [["'str' '(' string ',' string {',' string} ')'" "string"]]
         :basic-ref ['string]
         :doc "Combine all of the input strings together in sequence to produce a new string."
         :examples [{:expr-str "(str \"a\" \"b\")"
                     :expr-str-j :auto
                     :result :auto}
                    {:expr-str "(str \"a\" \"\" \"c\")"
                     :expr-str-j :auto
                     :result :auto}]
         :tags #{:string-op}
         :throws ['h-err/size-exceeded]}
   'subset? {:sigs [["set set" "boolean"]]
             :sigs-j [["set '.' 'subset?' '(' set ')'" "boolean"]]
             :tags #{:set-op :boolean-out}
             :basic-ref ['boolean 'set]
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
             :op-ref ['difference 'intersection 'union]}
   'union {:sigs [["set set {set}" "set"]]
           :sigs-j [["set '.' 'union' '(' set {',' set} ')'" "set"]]
           :tags #{:set-op :set-out}
           :basic-ref ['set]
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
           :throws ['h-err/arguments-not-sets
                    'h-err/unknown-type-collection]
           :op-ref ['difference 'intersection 'subset?]}
   'valid {:sigs [["instance-expression" "any"]]
           :sigs-j [["'valid' instance-expression" "any"]]
           :tags #{:instance-op :optional-out :special-form}
           :basic-ref ['instance 'any]
           :doc "Evaluate the instance-expression and produce the result. If a constraint violation occurs while evaluating the expression then produce an 'unset' value."
           :comment "This operation can be thought of as producing an instance if it is valid. This considers not just the constraints on the immediate instance, but also the constraints implied by refinements defined on the specification."

           :examples [{:spec-map {:my/Spec$v1 {:spec-vars {:p :Integer
                                                           :n :Integer}
                                               :constraints {:cp '(> p 0)
                                                             :cn '(< n 0)}}}
                       :expr-str "(valid {:$type :my/Spec$v1, :p 1, :n -1})"
                       :expr-str-j :auto
                       :result :auto
                       :doc "When the spec has constraints that the field, p, must be positive and the field, n, must be negative."}
                      {:spec-map {:my/Spec$v1 {:spec-vars {:p :Integer
                                                           :n :Integer}
                                               :constraints {:cp '(> p 0)
                                                             :cn '(< n 0)}}}
                       :expr-str "(valid {:$type :my/Spec$v1, :p 1, :n 1})"
                       :expr-str-j "valid {$type: my/Spec$v1, p: 1, n: 1}"
                       :result :auto
                       :doc "When the spec has constraints that the field, p, must be positive and the field, n, must be negative."}]
           :op-ref ['valid?]}
   'valid? {:sigs [["instance-expression" "boolean"]]
            :sigs-j [["'valid?' instance-expression" "boolean"]]
            :basic-ref ['instance 'boolean]
            :tutorial-ref [:spec/sudoku]
            :doc "Evaluate the instance expression and produce false if a constraint violation occurs during the evaluation. Otherwise, produce true."
            :comment "Similar to 'valid', but insted of possibly producing an instance, it produces a boolean indicating whether the instance was valid. This can be thought of as invoking a specification as a single predicate on a candidate instance value."
            :examples [{:spec-map {:my/Spec$v1 {:spec-vars {:p :Integer
                                                            :n :Integer}
                                                :constraints {:cp '(> p 0)
                                                              :cn '(< n 0)}}}
                        :expr-str "(valid? {:$type :my/Spec$v1, :p 1, :n -1})"
                        :expr-str-j :auto
                        :result :auto
                        :doc "When the spec has constraints that the field, p, must be positive and the field, n, must be negative."}
                       {:spec-map {:my/Spec$v1 {:spec-vars {:p :Integer
                                                            :n :Integer}
                                                :constraints {:cp '(> p 0)
                                                              :cn '(< n 0)}}}
                        :expr-str "(valid? {:$type :my/Spec$v1, :p 1, :n 0})"
                        :expr-str-j :auto
                        :result :auto
                        :doc "When the spec has constraints that the field, p, must be positive and the field, n, must be negative."}]
            :tags #{:instance-op :boolean-out :special-form}
            :op-ref ['valid]}
   'when {:sigs [["boolean any-expression" "any"]]
          :sigs-j [["'when' '(' boolean ')' any-expression" "any"]]
          :tags #{:boolean-op :optional-out :control-flow :special-form}
          :basic-ref ['boolean 'any]
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
                :basic-ref ['symbol 'any]
                :doc "Consider the value bound to the symbol. If it is a 'value', then evaluate the second argument. If instead it is 'unset' then produce unset."
                :examples [{:spec-map-f (make-spec-map-fn {:my/Spec$v1 {:refines-to {:my/Result$v1 {:expr 'placeholder
                                                                                                    :inverted? true
                                                                                                    :name "r"}}
                                                                        :spec-vars {:x [:Maybe :Integer]}}
                                                           :my/Result$v1 {:spec-vars {:x [:Maybe :Integer]}}})
                            :instance {:$type :my/Spec$v1, :x 1}
                            :expr-str "(when-value x (+ x 2))"
                            :expr-str-j "whenValue(x) {x + 2}"
                            :doc "In the context of an instance with an optional field, x, when the field is set to the value of '1'."
                            :result :auto}
                           {:spec-map-f (make-spec-map-fn {:my/Spec$v1 {:refines-to {:my/Result$v1 {:expr 'placeholder
                                                                                                    :inverted? true,
                                                                                                    :name "r"}}
                                                                        :spec-vars {:x [:Maybe :Integer]}}
                                                           :my/Result$v1 {:spec-vars {:x [:Maybe :Integer]}}})
                            :instance {:$type :my/Spec$v1}
                            :expr-str "(when-value x (+ x 2))"
                            :expr-str-j "whenValue(x) {x + 2}"
                            :doc "In the context of an instance with an optional field, x, when the field is unset."
                            :result :auto}]
                :op-ref ['if-value 'when 'when-value-let]}
   'when-value-let {:sigs [["'[' symbol any:binding']' any-expression" "any"]]
                    :sigs-j [["'whenValueLet' '(' symbol '=' any:binding ')' any-expression" "any"]]
                    :tags #{:optional-op :optional-out :control-flow :special-form}
                    :basic-ref ['symbol 'any]
                    :doc "If the binding value is a 'value' then evaluate the second argument with the symbol bound to binding. If instead, the binding value is 'unset', then produce 'unset'"
                    :examples [{:spec-map-f (make-spec-map-fn {:my/Spec$v1 {:refines-to {:my/Result$v1 {:expr 'placeholder
                                                                                                        :inverted? true
                                                                                                        :name "r"}}
                                                                            :spec-vars {:y [:Maybe :Integer]}}
                                                               :my/Result$v1 {:spec-vars {:x [:Maybe :Integer]}}})
                                :instance {:$type :my/Spec$v1, :y 1}
                                :expr-str "(when-value-let [x (when-value y (+ y 2))] (inc x))"
                                :expr-str-j "(whenValueLet ( x = (whenValue(y) {(y + 2)}) ) {(x + 1)})"
                                :result :auto
                                :doc "In the context of an instance with an optional field, y, when the field is set to the value of '1'."}
                               {:spec-map-f (make-spec-map-fn {:my/Spec$v1 {:refines-to {:my/Result$v1 {:expr 'placeholder
                                                                                                        :inverted? true
                                                                                                        :name "r"}}
                                                                            :spec-vars {:y [:Maybe :Integer]}}
                                                               :my/Result$v1 {:spec-vars {:x [:Maybe :Integer]}}})
                                :instance {:$type :my/Spec$v1}
                                :expr-str "(when-value-let [x (when-value y (+ y 2))] (inc x))"
                                :expr-str-j "(whenValueLet ( x = (whenValue(y) {(y + 2)}) ) {(x + 1)})"
                                :doc "In the context of an instance with an optional field, y, when the field is unset."
                                :result :auto}]
                    :op-ref ['if-value-let 'when 'when-value]}})
