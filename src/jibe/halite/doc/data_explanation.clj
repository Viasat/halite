;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.doc.data-explanation)

(def explanations {:spec/big-picture
                   {:label "Specs are about modeling things"
                    :desc "Specs are a general mechanism for modelling whatever is of interest."
                    :contents ["Writing a spec is carving out a subset out of the universe of all possible values and giving them a name."
                               {:spec-map {:spec/Ball {:spec-vars {:color "String"}
                                                       :constraints [["color_constraint" '(contains? #{"red" "blue" "green"} color)]]}}}
                               "Instances of specs are represented as maps. Depending on programming languages, maps are also known as associative arrays or dictionarys."
                               "The spec gives a name to the set of values which are maps that contain a type field with a value of 'spec/Ball' and which have a 'color' field with a string value. For example, this set includes the following specific instances."
                               {:code '{:$type :spec/Ball :color "red"}}
                               {:code '{:$type :spec/Ball :color "blue"}}
                               "If instead we defined this spec, then we are further constraining the set of values in the 'spec/Ball' set. Specifically, this means that any instance which is otherwise a valid 'spec/Ball', but does not have one of these three prescribed colors is not a valid 'spec/Ball'."
                               {:spec-map {:spec/Ball {:spec-vars {:color "String"}
                                                       :constraints [["color_constraint" '(contains? #{"red" "blue" "green"} color)]]}}}
                               "The following is not a valid 'spec/Ball' and in fact it is not a valid value at all in the universe of all possible values. This is because every map with a type of 'spec/Ball' must satisfy the 'spec/Ball' spec in order to be a valid value."
                               {:code '{:$type :spec/Ball :color "yellow"}
                                :throws :auto}
                               "The spec can be considered a predicate which only produces a value of true if it is applied to a valid instance of the spec itself. This operation is captured in the following code."
                               {:code '(valid? {:$type :spec/Ball :color "red"})
                                :result :auto}
                               {:code '(valid? {:$type :spec/Ball :color "yellow"})
                                :throws :auto}
                               "A spec context defines all of the specs that are in play when evaluating expressions. By definition, these specs define disjoint sets of valid instance values. There is never any overlap in the instances that are valid for any two different specs."
                               "However, it is possible to convert an instance of one spec into an instance of another spec. This is referred to as 'refinement'. Specs can include refinement expressions indicating how to convert them."
                               {:spec-map {:spec/Round {:spec-vars {:radius "Integer"}}
                                           :spec/Ball {:spec-vars {:color "String"}
                                                       :constraints [["color_constraint" '(contains? #{"red" "blue" "green"} color)]]
                                                       :refines-to {:spec/Round {:name "refine_to_round"
                                                                                 :expr '{:$type :spec/Round
                                                                                         :radius 5}}}}}}
                               "The following shows how to invoke refinements."
                               {:code '(refine-to {:$type :spec/Ball :color "red"} :spec/Round)
                                :result :auto}
                               "A refinement defines a many-to-one mapping from one set of values to another set of values. In this example, it is a mapping from the values in the set 'spec/Ball' to the values in the set 'spec/Round'. Specifically, in this example, all 'spec/Ball' instances map to the same 'spec/Round' instance, but that is just the detail of this refinement definition."
                               "Note, a refinement is not the same as a sub-type relationship. This is not saying that 'spec/Ball' is a sub-type of 'spec/Ball'. In fact this is formally seen by the fact that the two sets are disjoint. An instance of 'spec/Ball' is never itself an instance of 'spec/Round'. Rather the refinement establishes a relationship between values from the two sets."]}

                   :language/functional
                   {:label "Language is functional"
                    :desc "Both Halite, and its alternate representation, Jadeite, are purely functional languages."
                    :contents ["Halite is a functional language in the following ways:"
                               "The result of all operations are purely the result of the input values provided to the operator."
                               "The only result of operations is their return value, i.e. there are no side-effects*."
                               "Every operation is an expression that produces a value, i.e. there are no statements."
                               "All of the values are immutable."
                               "Every operation is deterministic, i.e. the value produced by an expression is the same every time the expression is evaluated. There are no random numbers, there is no I/O, there is no mutable state. For example, this is why there is no operation to 'reduce' a set, because the order of the reduction would not be well-defined given the language semantics."
                               "From some perspectives, Halite is not a functional language. For example:"
                               "There is one category of side-effect, expressions can produce errors. This is seen in the mathematical operators which can overflow or produce a 'divide by zero' error. Similarly, attempting to access an element beyond the bounds of a vector, attempting to construct an invalid instance, attempting an impossible refinement all produce errors at runtime, and the 'error' operator which explicitly raises a runtime error. These runtime errors are part of the semantics of the language. Typically, these errors are guarded against by use of an 'if' statement which conditionally evaluates an expression which might produce an error. These runtime errors are part of the language, unlike underlying system errors such as running out of memory, devices failing, or bugs in the stack of code that is evaluating an expression. Such system errors are not part of the language, but of course can occur at any time."
                               "It does not allow functions to be used as values."
                               "It does not support higher-ordered functions, i.e. functions are not assigned to variables, passed to operators or returned from operators. Instead the language relies on comprehensions for processing sequences of values."
                               "It does not allow for user-defined functions. Of course, specs themselves are a kind of 'user-defined function'."
                               "Considered from the perspective of errors as side-effects, the 'let' binding expressions can be viewed as statements which have the effect of producing errors."]}})
