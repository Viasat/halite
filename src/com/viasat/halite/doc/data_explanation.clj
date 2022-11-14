;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.doc.data-explanation)

(set! *warn-on-reflection* true)

(def explanations {:spec/big-picture
                   {:label "Specs are about modeling things"
                    :desc "Specs are a general mechanism for modelling whatever is of interest."
                    :basic-ref ['spec-map 'instance]
                    :op-ref ['valid? 'refine-to]
                    :contents ["Writing a spec is carving out a subset out of the universe of all possible values and giving them a name."
                               {:spec-map {:spec/Ball {:spec-vars {:color :String}
                                                       :constraints #{{:name "color_constraint" :expr '(contains? #{"red" "blue" "green"} color)}}}}}
                               "Instances of specs are represented as maps. Depending on programming languages, maps are also known as associative arrays or dictionarys."
                               "The spec gives a name to the set of values which are maps that contain a type field with a value of 'spec/Ball' and which have a 'color' field with a string value. For example, this set includes the following specific instances."
                               {:code '{:$type :spec/Ball :color "red"}}
                               {:code '{:$type :spec/Ball :color "blue"}}
                               "If instead we defined this spec, then we are further constraining the set of values in the 'spec/Ball' set. Specifically, this means that any instance which is otherwise a valid 'spec/Ball', but does not have one of these three prescribed colors is not a valid 'spec/Ball'."
                               {:spec-map {:spec/Ball {:spec-vars {:color :String}
                                                       :constraints #{{:name "color_constraint" :expr '(contains? #{"red" "blue" "green"} color)}}}}}
                               "The following is not a valid 'spec/Ball' and in fact it is not a valid value at all in the universe of all possible values. This is because every map with a type of 'spec/Ball' must satisfy the 'spec/Ball' spec in order to be a valid value."
                               {:code '{:$type :spec/Ball :color "yellow"}
                                :throws :auto}
                               "The spec can be considered a predicate which only produces a value of true if it is applied to a valid instance of the spec itself. This operation is captured in the following code."
                               {:code '(valid? {:$type :spec/Ball :color "red"})
                                :result :auto}
                               {:code '(valid? {:$type :spec/Ball :color "yellow"})
                                :result :auto}
                               "A spec context defines all of the specs that are in play when evaluating expressions. By definition, these specs define disjoint sets of valid instance values. There is never any overlap in the instances that are valid for any two different specs."
                               "However, it is possible to convert an instance of one spec into an instance of another spec. This is referred to as 'refinement'. Specs can include refinement expressions indicating how to convert them."
                               {:spec-map {:spec/Round {:spec-vars {:radius :Integer}}
                                           :spec/Ball {:spec-vars {:color :String}
                                                       :constraints #{{:name "color_constraint" :expr '(contains? #{"red" "blue" "green"} color)}}
                                                       :refines-to {:spec/Round {:name "refine_to_round"
                                                                                 :expr '{:$type :spec/Round
                                                                                         :radius 5}}}}}}
                               "The following shows how to invoke refinements."
                               {:code '(refine-to {:$type :spec/Ball :color "red"} :spec/Round)
                                :result :auto}
                               "A refinement defines a many-to-one mapping from one set of values to another set of values. In this example, it is a mapping from the values in the set 'spec/Ball' to the values in the set 'spec/Round'. Specifically, in this example, all 'spec/Ball' instances map to the same 'spec/Round' instance, but that is just the detail of this refinement definition."
                               "Note, a refinement is not the same as a sub-type relationship. This is not saying that 'spec/Ball' is a sub-type of 'spec/Ball'. In fact this is formally seen by the fact that the two sets are disjoint. An instance of 'spec/Ball' is never itself an instance of 'spec/Round'. Rather the refinement establishes a relationship between values from the two sets."]}

                   :spec/abstract-spec
                   {:label "What is an abstract spec?"
                    :desc "An abstract spec defines instances which cannot be used in the construction of other instances."
                    :explanation-ref [:spec/refinement-terminology]
                    :basic-ref ['spec-map 'instance]
                    :op-ref ['refine-to]
                    :contents ["Say we have an abstract concept of squareness."
                               {:spec-map {:spec/Square {:abstract? true
                                                         :spec-vars {:width :Integer
                                                                     :height :Integer}}}}
                               "The spec can be instantiated in a standalone fashion."
                               {:code '{:$type :spec/Square :width 5 :height 5}}
                               "However, this spec cannot be instantiated in the context of another instance. So consider the following two specs, where a concrete spec uses an abstract spec in composition."
                               {:spec-map {:spec/Square {:abstract? true
                                                         :spec-vars {:width :Integer
                                                                     :height :Integer}}
                                           :spec/Painting {:spec-vars {:square :spec/Square
                                                                       :painter :String}}}}
                               "The instance of the abstract spec cannot be used in the construction of the painting instance."
                               {:code '{:$type :spec/Painting
                                        :square {:$type :spec/Square :width 5 :height 5}
                                        :painter "van Gogh"}
                                :throws :auto}
                               "To create an instance of the composite painting spec, we need to define an additional spec which refines to the abstract spec, square."
                               {:spec-map {:spec/Square {:abstract? true
                                                         :spec-vars {:width :Integer
                                                                     :height :Integer}}
                                           :spec/Painting {:spec-vars {:square :spec/Square
                                                                       :painter :String}}
                                           :spec/Canvas {:spec-vars {:size :String}
                                                         :refines-to {:spec/Square {:name "refine_to_square"
                                                                                    :expr '(if (= "small" size)
                                                                                             {:$type :spec/Square
                                                                                              :width 5
                                                                                              :height 5}
                                                                                             {:$type :spec/Square
                                                                                              :width 10
                                                                                              :height 10})}}}}}
                               "Now we can instantiate a painting using an instance of the concrete canvas spec."
                               {:code '{:$type :spec/Painting
                                        :square {:$type :spec/Canvas :size "large"}
                                        :painter "van Gogh"}
                                :result :auto}
                               "We can determine the size of the square."
                               {:code '(let [painting {:$type :spec/Painting
                                                       :square {:$type :spec/Canvas :size "large"}
                                                       :painter "van Gogh"}]
                                         (get (refine-to (get painting :square) :spec/Square) :width))
                                :result :auto}
                               "An abstract spec is a spec that can be used to define some constraints on the value in a composite spec without indicating precisely what type of instance is used in the composition. In this example, the painting spec is defined to include a square without any reference to the canvas."
                               "Consider another spec context, where an alternate spec is defined that refines to square."
                               {:spec-map {:spec/Square {:abstract? true
                                                         :spec-vars {:width :Integer
                                                                     :height :Integer}}
                                           :spec/Painting {:spec-vars {:square :spec/Square
                                                                       :painter :String}}
                                           :spec/Wall {:refines-to {:spec/Square {:name "refine_to_square"
                                                                                  :expr '{:$type :spec/Square
                                                                                          :width 100
                                                                                          :height 100}}}}}}
                               "In this example, the exact same painting spec is used, but now a new spec is used to provide the square abstraction."
                               {:code '{:$type :spec/Painting
                                        :square {:$type :spec/Wall}
                                        :painter "van Gogh"}
                                :result :auto}
                               "Once again, we can use the same code as before to retrieve the size of the square for this painting."
                               {:code '(let [painting {:$type :spec/Painting
                                                       :square {:$type :spec/Wall}
                                                       :painter "van Gogh"}]
                                         (get (refine-to (get painting :square) :spec/Square) :width))
                                :result :auto}
                               "So the abstract spec allows us to write code that composes and uses instances without knowing the specific type of the instances at the time that we write the code."]}

                   :spec/abstract-field
                   {:label "What is an abstract field?"
                    :desc "If a field in a spec has a type of an abstract spec, then the field can hold values that refine to that abstract spec."
                    :basic-ref ['spec-map 'instance]
                    :contents ["Say we have an abstract car."
                               {:spec-map {:spec/Car {:abstract? true
                                                      :spec-vars {:make :String
                                                                  :year :Integer}}
                                           :spec/Ford {:abstract? false
                                                       :spec-vars {:year :Integer}
                                                       :refines-to {:spec/Car {:name "as_car"
                                                                               :expr '{:$type :spec/Car
                                                                                       :make "Ford"
                                                                                       :year year}}}}
                                           :spec/Chevy {:abstract? false
                                                        :spec-vars {:year :Integer}
                                                        :refines-to {:spec/Car {:name "as_car"
                                                                                :expr '{:$type :spec/Car
                                                                                        :make "Chevy"
                                                                                        :year year}}}}
                                           :spec/Garage {:abstract? false
                                                         :spec-vars {:car :spec/Car}}}}
                               "Since the garage has a field of an abstract type, it can hold an instance of either Ford or Chevy."
                               {:code '{:$type :spec/Garage :car {:$type :spec/Ford :year 2020}}}
                               {:code '{:$type :spec/Garage :car {:$type :spec/Chevy :year 2021}}}
                               "However, it cannot hold a direct instance of car, because an abstract instance cannot be used in the construction of an instance."
                               {:code '{:$type :spec/Garage :car {:$type :spec/Car :make "Honda" :year 2022}}
                                :throws :auto}
                               "In an interesting way, declaring a field to be of the type of an abstract instance, means it can hold any instance except for an instance of that type."]}

                   :spec/refinement-implications
                   {:label "What constraints are implied by refinement?"
                    :desc "Specs can be defined as refining other specs. When this is done what constraints are implied by the refinement?"
                    :how-to-ref [:refinement/arbitrary-expression-refinements]
                    :explanation-ref [:spec/abstract-spec :spec/refinement-terminology]
                    :basic-ref ['spec-map 'instance]
                    :op-ref ['refine-to 'valid? 'refines-to?]
                    :contents ["One spec can be defined to be a refinement of another spec. First consider a square which has a width and height. The constraint, which makes it a square, requires these two values to be equal."
                               {:spec-map {:spec/Square {:spec-vars {:width :Integer
                                                                     :height :Integer}
                                                         :constraints #{{:name "square" :expr '(= width height)}}}}}
                               "So the following is a valid spec/Square"
                               {:code '{:$type :spec/Square :width 5 :height 5}}
                               "While the following is not a valid spec/Square"
                               {:code '{:$type :spec/Square :width 5 :height 6}
                                :throws :auto}
                               "Now consider a new spec, 'spec/Box', and we define that it refines to 'spec/Square'."
                               {:spec-map {:spec/Square {:spec-vars {:width :Integer
                                                                     :height :Integer}
                                                         :constraints #{{:name "square" :expr '(= width height)}}}
                                           :spec/Box$v1 {:spec-vars {:width :Integer
                                                                     :length :Integer}
                                                         :refines-to {:spec/Square {:name "refine_to_square"
                                                                                    :expr '{:$type :spec/Square
                                                                                            :width width
                                                                                            :height length}}}}}}
                               "The refinement allows a 'spec/Square' instance to be computed from a 'spec/Box'"
                               {:code '(refine-to {:$type :spec/Box$v1 :width 5 :length 5} :spec/Square)
                                :result :auto}
                               "But furthermore, notice that the refinement has by implication created a constraint on 'spec/Box' itself."
                               {:code '{:$type :spec/Box$v1 :width 5 :length 6}
                                :throws :auto}
                               "It is not possible to construct an instance of 'spec/Box' which is not a 'spec/Square' because we have said that all instances of 'spec/Box' can be translated into a 'spec/Square', and more speficically into a valid instance of 'spec/Square, which is the only kind of instance that the system recognizes."
                               "If this was not the intent, and rather the intent was to indicate that some instances of 'spec/Box' can be converted into 'spec/Square' instances then the refinement would be defined as:"
                               {:spec-map {:spec/Square {:spec-vars {:width :Integer
                                                                     :height :Integer}
                                                         :constraints #{{:name "square" :expr '(= width height)}}}
                                           :spec/Box$v2 {:spec-vars {:width :Integer
                                                                     :length :Integer}
                                                         :refines-to {:spec/Square {:name "refine_to_square"
                                                                                    :expr '(when (= width length)
                                                                                             {:$type :spec/Square
                                                                                              :width width
                                                                                              :height length})}}}}}
                               "Now it is possible to construct a box that is not a square."
                               {:code '{:$type :spec/Box$v2 :width 5 :length 6}
                                :result :auto}
                               "But, if we attempt to refine such a box to a square, an error is produced:"
                               {:code '(refine-to {:$type :spec/Box$v2 :width 5 :length 6} :spec/Square)
                                :throws :auto}
                               "Alternatively, we can simply ask whether the box can be converted to a square:"
                               {:code '(refines-to? {:$type :spec/Box$v2 :width 5 :length 6} :spec/Square)
                                :result :auto}
                               "Another way of defining the refinement is to declare it to be 'inverted?'. What this means is that the refinement will be applied where possible, and where it results in a contradiction then a runtime error is produced."
                               {:spec-map {:spec/Square {:spec-vars {:width :Integer
                                                                     :height :Integer}
                                                         :constraints #{{:name "square" :expr '(= width height)}}}
                                           :spec/Box$v3 {:spec-vars {:width :Integer
                                                                     :length :Integer}
                                                         :refines-to {:spec/Square {:name "refine_to_square"
                                                                                    :expr '{:$type :spec/Square
                                                                                            :width width
                                                                                            :height length}
                                                                                    :inverted? true}}}}}
                               "Note that in this version of the refinement the guard clause in the refinement expression has been removed, which means the refinement applies to all instances of box. However, the refinement has been declared to be 'inverted?'. This means that even if the resulting square instance would violate the constraints of spec/Square, the spec/Box instance is still valid."
                               {:code '{:$type :spec/Box$v3 :width 5 :length 6}
                                :result :auto}
                               "The box itself is valid, but now attempting to refine a non-square box into a square will produce a runtime error."
                               {:code '(refine-to {:$type :spec/Box$v3 :width 5 :length 6} :spec/Square)
                                :throws :auto}
                               "Of course for a square box the refinement works as expected."
                               {:code '(refine-to {:$type :spec/Box$v3 :width 5 :length 5} :spec/Square)
                                :result :auto}
                               "A way of summarizing the two approaches to a refinement are: for 'normal' refinements, the refinement implies that the author of the spec is intending to incorporate all of the constraints implied by the refinement into the spec at hand. However, for an inverted refinement, the spec at hand is being defined independent of constraints implied by the refinement. Instead, it is the responsibility of the refinement expression to deal with all of the implications of the constraints of the spec being refined to. If the refinement expression does not take all the implications into account, then a runtime error results."
                               "As an advanced topic, there is a 'valid?' operator which deals with immediate constraint violations in the spec at hand, but it does not handle the case of the application of an inverted refinement leading to a constraint violation."
                               {:code '(valid? (refine-to {:$type :spec/Box$v3 :width 5 :length 6} :spec/Square))
                                :throws :auto}]}

                   :spec/specs-as-predicates
                   {:label "Considering a spec as a predicate"
                    :desc "A spec can be considered a giant predicate which when applied to a value returns 'true' if the value is a valid instance and 'false' or a runtime error otherwise."
                    :basic-ref ['instance 'spec-map]
                    :op-ref ['valid?]
                    :contents ["The type indications of spec variables can be considered as predicates."
                               {:spec-map {:spec/X$v1 {:spec-vars {:x :String}}}}
                               "If an instance is made with the correct type for a field value, then the predicate produces 'true'."
                               {:code '{:$type :spec/X$v1 :x "hi"}
                                :result :auto}
                               "If an instance is made without the correct type for a field value, then the predicate produces an error."
                               {:code '{:$type :spec/X$v1 :x 25}
                                :throws :auto}
                               "If a specification defines multiple spec vars then the result is a logical conjunct."
                               {:spec-map {:spec/X$v2 {:spec-vars {:x :String
                                                                   :y :Integer}}}}
                               "All of the fields must be of the correct type. This is a conjunct: the field x must be a string and the field y must be an integer"
                               {:code '{:$type :spec/X$v2 :x "hi" :y 100}
                                :result :auto}
                               "Violating either conditions causes the overall value to produce an error."
                               {:code '{:$type :spec/X$v2 :x "hi" :y "bye"}
                                :throws :auto}
                               "Violating either conditions causes the overall value to produce an error."
                               {:code '{:$type :spec/X$v2 :x 5 :y 100}
                                :throws :auto}
                               "Similarly, each constraint by itself is a predicate and is combined in a conjunction with all of the spec variable type checks."
                               {:spec-map {:spec/X$v3 {:spec-vars {:x :String
                                                                   :y :Integer}
                                                       :constraints #{{:name "valid_y" :expr '(> y 0)}}}}}
                               {:code '{:$type :spec/X$v3 :x "hi" :y 100}
                                :result :auto}
                               "So if any of the types are wrong or if the constraint is violated then an error is produced."
                               {:code '{:$type :spec/X$v3 :x "hi" :y -1}
                                :throws :auto}
                               "If there are multiple constraints, they are all logically combined into a single conjunction for the spec."
                               {:spec-map {:spec/X$v5 {:spec-vars {:x :String
                                                                   :y :Integer}
                                                       :constraints #{{:name "valid_x" :expr '(contains? #{"hi" "bye"} x)}
                                                                      {:name "valid_y" :expr '(> y 0)}}}}}
                               {:code '{:$type :spec/X$v5 :x "hi" :y 100}
                                :result :auto}
                               "Again, violating any one constraint causes an error to be produced."
                               {:code '{:$type :spec/X$v5 :x "hello" :y 100}
                                :throws :auto}
                               "Finally, the refinements can also bring in additional constraints which are combined into the overall conjunction for the spec."
                               {:spec-map {:spec/A {:spec-vars {:b :Integer}
                                                    :constraints #{{:name "valid_b" :expr '(< b 10)}}}
                                           :spec/X$v6 {:spec-vars {:x :String
                                                                   :y :Integer}
                                                       :constraints #{{:name "valid_x" :expr '(contains? #{"hi" "bye"} x)}
                                                                      {:name "valid_y" :expr '(> y 0)}}
                                                       :refines-to {:spec/A {:name "refine_to_A"
                                                                             :expr '{:$type :spec/A
                                                                                     :b y}}}}}}
                               {:code '{:$type :spec/X$v6 :x "hi" :y 9}
                                :result :auto}
                               "If one of the constraints implied by a refinement is violated, then an error is produced."
                               {:code '{:$type :spec/X$v6 :x "hi" :y 12}
                                :throws :auto}
                               "Implications of each additional refinement are combined into the single conujunction for this spec."
                               {:spec-map {:spec/A {:spec-vars {:b :Integer}
                                                    :constraints #{{:name "valid_b" :expr '(< b 10)}}}
                                           :spec/P {:spec-vars {:q :String}
                                                    :constraints #{{:name "valid_q" :expr '(= q "hi")}}}
                                           :spec/X$v7 {:spec-vars {:x :String
                                                                   :y :Integer}
                                                       :constraints #{{:name "valid_x" :expr '(contains? #{"hi" "bye"} x)}
                                                                      {:name "valid_y" :expr '(> y 0)}}
                                                       :refines-to {:spec/A {:name "refine_to_A"
                                                                             :expr '{:$type :spec/A
                                                                                     :b y}}
                                                                    :spec/P {:name "refine_to_P"
                                                                             :expr '{:$type :spec/P
                                                                                     :q x}}}}}}
                               {:code '{:$type :spec/X$v7 :x "hi" :y 9}
                                :result :auto}
                               "Violate one of the implied refinement constraints."
                               {:code '{:$type :spec/X$v7 :x "bye" :y 9}
                                :throws :auto}
                               "Violation of constraints can be detected by using the 'valid?' operator. This works for constraints in the spec explicitly as well as constraints implied via refinements."
                               {:code '(valid? {:$type :spec/X$v7 :x "hi" :y 9})
                                :result :auto}
                               {:code '(valid? {:$type :spec/X$v7 :x "hola" :y 9})
                                :result :auto}
                               {:code '(valid? {:$type :spec/X$v7 :x "bye" :y 9})
                                :result :auto}
                               "However, the 'valid?' operator cannot be used to handle cases that would violate the required types of specs variables."
                               {:code '(valid? {:$type :spec/X$v7 :x 1 :y 9})
                                :throws :auto}]}

                   :spec/refinement-terminology
                   {:label "Clarify terminology around refinements"
                    :desc "The primary intent of a refinement is to be a mechanism to translate instances of more concrete specifications into more abstract specifications."
                    :contents ["The refinement has a direction in terms of converting X to Y."
                               {:spec-map {:spec/Y$v1 {}
                                           :spec/X$v1 {:refines-to {:spec/Y$v1 {:name "refine_to_Y"
                                                                                :expr '{:$type :spec/Y$v1}}}}}}
                               {:code '(refine-to {:$type :spec/X$v1} :spec/Y$v1)
                                :result :auto}

                               "This direction of the spec is the same, regardless of whether the refinement is inverted."
                               {:spec-map {:spec/Y$v2 {}
                                           :spec/X$v2 {:refines-to {:spec/Y$v2 {:name "refine_to_Y"
                                                                                :expr '{:$type :spec/Y$v2}
                                                                                :inverted? true}}}}}
                               "The inverted flag determines whether the constraints of Y are applied to all instances of X, but it does not affect the basic 'direction' of the refinement. i.e. the refinement still converts instances of X into instances of Y."
                               {:code '(refine-to {:$type :spec/X$v2} :spec/Y$v2)
                                :result :auto}
                               "The use of the word 'to' in 'refine-to' and 'refines-to?' refers to this direction of the refinement mapping. So 'refine-to' means to 'execute a refinement', specifically a refinement that converts instances 'to' instances of the indicated spec."]
                    :basic-ref ['instance 'spec-map]
                    :op-ref ['refine-to]
                    :how-to-ref [:refinement/convert-instances]
                    :explanation-ref [:spec/abstract-spec :spec/refinement-implications :spec/refinements-as-functions]}

                   :spec/refinements-as-functions
                   {:label "Refinements as general purpose functions"
                    :desc "Refinements can be used as general purpose instance conversion functions."
                    :contents ["A refinement can be defined that does not convert from a concrete instance to a more abstract instance, but in fact converts in the opposite direction."
                               {:spec-map {:spec/Car {:refines-to {:spec/Ford {:name "refine_to_ford"
                                                                               :expr '{:$type :spec/Ford
                                                                                       :model "Mustang"
                                                                                       :year 2000}}}}
                                           :spec/Ford {:spec-vars {:model :String
                                                                   :year :Integer}}}}
                               "In this example a highly abstract instance, just called a car, is converted into a concrete instance that has more detailed information."
                               {:code '(refine-to {:$type :spec/Car} :spec/Ford)
                                :result :auto}]
                    :basic-ref ['instance 'spec-map]
                    :op-ref ['refine-to]
                    :how-to-ref [:refinement/convert-instances]
                    :tutorial-ref [:spec/grocery]
                    :explanation-ref [:spec/abstract-spec :spec/refinement-implications :spec/refinement-terminology]}

                   :language/functional
                   {:label "Language is functional"
                    :desc "Both Halite, and its alternate representation, Jadeite, are purely functional languages."
                    :contents ["Halite is a functional language in the following ways:"
                               "* The result of all operations are purely the result of the input values provided to the operator."
                               "* The only result of operations is their return value, i.e. there are no side-effects*."
                               "* Every operation is an expression that produces a value, i.e. there are no statements."
                               "* All of the values are immutable."
                               "* Every operation is deterministic, i.e. the value produced by an expression is the same every time the expression is evaluated. There are no random numbers, there is no I/O, there is no mutable state. For example, this is why there is no operation to 'reduce' a set, because the order of the reduction would not be well-defined given the language semantics."
                               "From some perspectives, Halite is not a functional language. For example:"
                               "* There is one category of side-effect, expressions can produce errors. This is seen in the mathematical operators which can overflow or produce a 'divide by zero' error. Similarly, attempting to access an element beyond the bounds of a vector, attempting to construct an invalid instance, attempting an impossible refinement all produce errors at runtime, and the 'error' operator which explicitly raises a runtime error. These runtime errors are part of the semantics of the language. Typically, these errors are guarded against by use of an 'if' statement which conditionally evaluates an expression which might produce an error. These runtime errors are part of the language, unlike underlying system errors such as running out of memory, devices failing, or bugs in the stack of code that is evaluating an expression. Such system errors are not part of the language, but of course can occur at any time."
                               "* It does not allow functions to be used as values."
                               "* It does not support higher-ordered functions, i.e. functions are not assigned to variables, passed to operators or returned from operators. Instead the language relies on comprehensions for processing sequences of values."
                               "* It does not allow for user-defined functions. Of course, specs themselves are a kind of 'user-defined function'."
                               "* Considered from the perspective of errors as side-effects, the 'let' binding expressions can be viewed as statements which have the effect of producing errors."]}

                   :language/unset
                   {:label "The pseduo-value 'unset' is handled specially"
                    :desc "The 'unset' value cannot be used in general and there are specific facilities for dealing with them when they are produced by an expression."
                    :basic-ref ['instance 'integer]
                    :op-ref ['$no-value 'if-value 'when-value 'if-value-let 'when-value-let 'when 'refine-to]
                    :contents ["Some languages have a notion of 'null' that appears throughout; this language uses 'unset' instead. Potentially 'unset' values generally need to be addressed using a special \"if value\" kind of operator to help prevent the 'unset' value from getting passed very far. The idea is that most code should therefore not need to deal with it. If an 'unset' value does need to be created, do so with `$no-value` or a 'when' operation."
                               {:code '$no-value
                                :result :auto}
                               "But, ideally users will not use '$no-value' explicitly."
                               "An 'unset' value is expected to come into play via an optional field."
                               {:spec-map {:spec/A {:spec-vars {:b [:Maybe :Integer]}}}}
                               {:code '(get {:$type :spec/A} :b)
                                :result :auto}
                               "The 'unset' value cannot be used in most operations."
                               {:code '(+ (get {:$type :spec/A} :b) 2)
                                :throws :auto}
                               "The typical pattern is that when an 'unset' value might be produced, the first thing to do is to branch based on whether an actual value resulted."
                               {:code '(if-value-let [x (get {:$type :spec/A :b 1} :b)] x 0)
                                :result :auto}
                               {:code '(if-value-let [x (get {:$type :spec/A} :b)] x 0)
                                :result :auto}
                               "The operators 'if-value', 'if-value-let', 'when-value', and 'when-value-let' are specifically for dealing with expressions that maybe produce 'unset'."
                               "There is very little that can be done with a value that is possibly 'unset'. One of the few things that can be done with them is equality checks can be performed, although this is discouraged in favor of using one of the built in 'if-value' or 'when-value' operators."
                               {:code '(= 1 (get {:$type :spec/A} :b))
                                :result :auto}
                               {:code '(= $no-value (get {:$type :spec/A} :b))
                                :result :auto}
                               "The main use for 'unset' values is when constructing an instance literal."
                               "If a field in an instance literal is never to be provided then it can simply be omitted."
                               {:code '{:$type :spec/A}
                                :result :auto}
                               "However, if an optional field needs to be populated sometimes then a value that may be 'unset' can be useful."
                               {:code '{:$type :spec/A
                                        :b (get {:$type :spec/A :b 1} :b)}
                                :result :auto}
                               {:code '{:$type :spec/A
                                        :b (get {:$type :spec/A} :b)}
                                :result :auto}
                               "If a potentially 'unset' value needs to be fabricated then the 'when' operators can be used."
                               {:code '(let [x 11]
                                         {:$type :spec/A
                                          :b (when (> x 10)
                                               x)})
                                :result :auto}
                               {:code '(let [x 1]
                                         {:$type :spec/A
                                          :b (when (> x 10)
                                               x)})
                                :result :auto}
                               {:code '(let [a {:$type :spec/A :b 1}]
                                         {:$type :spec/A
                                          :b (when-value-let [x (get a :b)]
                                                             (inc x))})
                                :result :auto}
                               {:code '(let [a {:$type :spec/A}]
                                         {:$type :spec/A
                                          :b (when-value-let [x (get a :b)]
                                                             (inc x))})
                                :result :auto}
                               "The 'when-value' and 'if-value' operators are useful from within the context of a spec."
                               {:spec-map {:spec/X {:spec-vars {:y [:Maybe :Integer]
                                                                :z [:Maybe :Integer]}
                                                    :refines-to {:spec/P {:name "refine_to_P"
                                                                          :expr '{:$type :spec/P
                                                                                  :q (when-value y
                                                                                                 (inc y))
                                                                                  :r (if-value z
                                                                                               z
                                                                                               0)}}}}
                                           :spec/P {:spec-vars {:q [:Maybe :Integer]
                                                                :r :Integer}}}}
                               {:code '(refine-to {:$type :spec/X :y 10 :z 20} :spec/P)
                                :result :auto}
                               {:code '(refine-to {:$type :spec/X} :spec/P)
                                :result :auto}
                               "The operators that branch on 'unset' values cannot be used with expressions that cannot be 'unset'."
                               {:code '(if-value-let [x 1] x 2)
                                :throws :auto}]}})
