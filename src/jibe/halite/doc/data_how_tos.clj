;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.doc.data-how-tos)

(def how-tos {:collections/transform
              {:label "Transform a collection"
               :desc "Consider that you have a collection of values and need to produce a collection of new values derived from the first."
               :basic-ref ['vector 'set]
               :op-ref ['map '*]
               :contents ["A collection of values can be transformed into a new collection of values. For example, the following transforms a vector of integers into a new vector of integers."
                          {:code '(let [v [1 2 3]]
                                    (map [x v] (* x 10)))
                           :result :auto}
                          "The same works with sets."
                          {:code '(let [s #{1 2 3}]
                                    (map [x s] (* x 10)))
                           :result :auto}]
               :see-also [:collections/reduce]}

              :collections/reduce
              {:label "Transform a vector into a single value"
               :desc "Consider that you have a vector of values and you need to produce a single value that takes into account all of the values in the vector."
               :basic-ref ['vector]
               :op-ref ['reduce '*]
               :contents ["A vector of values can be transformed into a single value. For example, the following transforms a vector of integers into a single value, which is their product."
                          {:code '(let [v [1 2 3]]
                                    (reduce [a 1] [x v] (* a x)))
                           :result :auto}
                          "The values from the vector are combined with the accumulator, one-by-one in order and then the final value of the accumulator is produced."]
               :see-also [:collections/transform]}

              :collections/vector-containment
              {:label "Determine if an item is in a vector"
               :desc "Consider that you have a vector and you need to know whether it contains a specific value."
               :basic-ref ['vector '=]
               :contents ["The following code correctly determines that a target value is in a vector."
                          {:code '(let [v [10 20 30]
                                        t 20]
                                    (any? [x v] (= x t)))
                           :result :auto}
                          "The following code correctly determines that a target value is not in a vector."
                          {:code '(let [v [10 20 30]
                                        t 50]
                                    (any? [x v] (= x t)))
                           :result :auto}]
               :see-also [:collections/set-containment
                          :collections/any]}

              :collections/set-containment
              {:label "Determine if an item is in a set"
               :desc "How to determine if a given item is contained in a set?"
               :basic-ref ['set 'any? '=]
               :contents ["There is a built-in function to determine whether a value is a member of a set."
                          {:code '(contains? #{10 20 30} 20)
                           :result :auto}
                          "The following code correctly determines that a value is not in a set"
                          {:code '(contains? #{10 20 30} 50)
                           :result :auto}
                          "It is more verbose, but an alternate solutions is the same as what would be done to determine if an item is in a vector."
                          {:code '(let [s #{10 20 30}
                                        t 20]
                                    (any? [x s] (= x t)))
                           :result :auto}]
               :see-also [:collections/vector-containment
                          :collections/any]}

              :collections/any
              {:label "Determine if any item in a collection satisfies some criteria"
               :desc "How to determine if any item in a collection satisifies some criteria?"
               :basic-ref ['vector 'set 'any?]
               :contents ["The following code correctly determines that there is at least one value in the vector which makes the test expression true."
                          {:code '(let [v [10 20 30]]
                                    (any? [x v] (> x 15)))
                           :result :auto}
                          "In this example, no values make the expression true."
                          {:code '(let [v [10 20 30]]
                                    (any? [x v] (> x 100)))
                           :result :auto}
                          "Sets can be tested in the same way."
                          {:code '(let [s #{10 20 30}]
                                    (any? [x s] (> x 15)))
                           :result :auto}]
               :see-also [:collections/vector-containment
                          :collections/set-containment]}

              :collections/combine
              {:label "Combine collections together"
               :desc "Consider you have two sets or vectors and need to combine them."
               :basic-ref ['vector 'set]
               :contents ["Considering vectors first. One vector can be simply added to the end of another."
                          {:code '(let [v1 [10 20 30]
                                        v2 [40 50]]
                                    (concat v1 v2))
                           :result :auto}
                          "The same can be done with sets. In this case the sets are simply combined because sets have no instrinsic order to the elements."
                          {:code '(let [s1 #{10 20 30}
                                        s2 #{40 50}]
                                    (concat s1 s2))
                           :result :auto}
                          "There is a special set operator that is equivalent to 'concat' for sets."
                          {:code '(let [s1 #{10 20 30}
                                        s2 #{40 50}]
                                    (union s1 s2))
                           :result :auto}
                          "However, 'union' only works when all of the arguments are sets. The 'concat' operation can be used to add elements from a vector into a set."
                          {:code '(let [s #{10 20 30}
                                        v [40 50]]
                                    (concat s v))
                           :result :auto}
                          "It is not possible to use concat to add a set into a vector."
                          {:code '(let [v [10 20 30]
                                        s #{40 50}]
                                    (concat v s))
                           :throws :auto}
                          "This is not supported because a vector is ordered and generally speaking, there is not a deterministic way to add the unordered items from the set into the vector."]
               :see-also [:collections/combine-set-to-vector]}

              :collections/combine-set-to-vector
              {:label "Add contents of a set to a vector"
               :desc "A set must be sorted into a vector before it can be appended onto another vector."
               :basic-ref ['vector 'set]
               :op-ref ['concat]
               :contents ["This example shows how to combine a set of sortable items into a vector."
                          {:code '(let [v [10 20 30]
                                        s #{40 50}]
                                    (concat v (sort s)))
                           :result :auto}
                          "The same can be done with a set of values that is not intrinsically sortable, but in this case an explicit sort function must be defined."
                          {:code '(let [v [[10 20 30]]
                                        s #{[40 50] [60] [70 80 90]}]
                                    (concat v (sort-by [e s] (count e))))
                           :result :auto}
                          "Notice that the items in the set were first sorted based on the number of items in the element, then in that sort order the items were appended to the vector, 'v'."]
               :see-also [:collections/combine]}

              :collections/convert-vector-to-set
              {:label "Convert a vector into a set"
               :desc "A vector can be converted into a set via 'concat'."
               :basic-ref ['vector 'set]
               :op-ref ['concat]
               :contents ["Combine a vector into an empty set to effectively convert the vector into a set which contains the same elements."
                          {:code '(let [v [10 20 30]]
                                    (concat #{} v))
                           :result :auto}

                          "Note that duplicate elements are removed in the process."
                          {:code '(let [v [10 10 20 30]]
                                    (concat #{} v))
                           :result :auto}]
               :see-also [:collections/convert-set-to-vector
                          :collections/remove-duplicates-from-vector]}

              :collections/convert-set-to-vector
              {:label "Convert a set into a vector"
               :desc "A set can be converted into a vector by sorting it."
               :basic-ref ['vector 'set]
               :op-ref ['sort]
               :contents ["Combine a vector into an empty set to effectively convert the vector into a set which contains the same elements."
                          {:code '(let [s #{10 20 30}]
                                    (sort s))
                           :result :auto}
                          "This only works if the items in the set are intrinsically sortable."
                          {:code '(let [s #{[10 20] [30]}]
                                    (sort s))
                           :throws :auto}
                          "If the elements of the set are not sortable then use sort-by to convert the set into a vector."
                          {:code '(let [s #{[10 20] [30]}]
                                    (sort-by [e s] (count e)))
                           :result :auto}]
               :see-also [:collections/convert-vector-to-set
                          :collections/remove-duplicates-from-vector]}

              :collections/remove-duplicates-from-vector
              {:label "Remove duplicate values from a vector."
               :desc "A vector can be converted to a set and back to a vector to remove duplicates."
               :basic-ref ['vector 'set]
               :op-ref ['sort 'concat]
               :contents ["Starting with a vector that has duplicate values, it can be converted to a set and then back to a vector. In the process, the duplicates will be removed."
                          {:code '(let [v [40 10 10 20 30]]
                                    (sort (concat #{} v)))
                           :result :auto}
                          "Note that this only works if the elements of the vector are sortable. Also note that this causes the items in the vector to be sorted into their natural sort order."]
               :see-also [:collections/convert-vector-to-set
                          :collections/convert-set-to-vector]}

              :instance/spec-variables
              {:label "Spec variables"
               :desc "How to model data fields in specifications."
               :basic-ref ['instance 'vector]
               :contents ["It is possible to define a spec that does not have any fields."
                          {:spec-map {:spec/Dog$v1 {}}}
                          "Instances of this spec could be created as:"
                          {:code '{:$type :spec/Dog$v1}}
                          "It is more interesting to define data fields on specs that define the structure of instances of the spec"
                          {:spec-map {:spec/Dog$v2 {:spec-vars {:age "Integer"}}}}
                          "This spec can be instantiated as:"
                          {:code '{:$type :spec/Dog$v2, :age 3}}
                          "A spec can have multiple fields"
                          {:spec-map {:spec/Dog$v4 {:spec-vars {:name "String"
                                                                :age "Integer"
                                                                :colors ["String"]}}}}
                          {:code '{:$type :spec/Dog$v4, :name "Rex", :age 3, :colors ["brown" "white"]}}]
               :see-also [:instance/compose-instances]}

              :refinement/convert-instances
              {:label "Converting instances between specs"
               :desc "How to convert an instance from one spec type to another."
               :basic-ref ['instance]
               :op-ref ['refine-to]
               :contents ["An expression can convert an instance of one type to the instance of another type. Assume there are these two specs."
                          {:spec-map {:spec/A$v1 {:spec-vars {:b "Integer"}}
                                      :spec/X$v1 {:spec-vars {:y "Integer"}}}}
                          "The following expression converts an instance of the first spec into an instance of the second."
                          {:code '(let [a {:$type :spec/A$v1 :b 1}]
                                    {:$type :spec/X$v1 :y (get a :b)})
                           :result :auto}
                          "This work, but the language has a built-in idea of 'refinements' that allow such conversion functions to be expressed in a way that the system understands."
                          {:spec-map {:spec/A$v2 {:spec-vars {:b "Integer"}
                                                  :refines-to {:spec/X$v2 {:name "refine_to_X"
                                                                           :expr '{:$type :spec/X$v2
                                                                                   :y b}}}}
                                      :spec/X$v2 {:spec-vars {:y "Integer"}}}}
                          "The refinement can be invoked as follows:"
                          {:code '(let [a {:$type :spec/A$v2 :b 1}]
                                    (refine-to a :spec/X$v2))
                           :result :auto}]
               :see-also [:refinement/convert-instances-transitively
                          :refinement/arbitrary-expression-refinements
                          :refinement/optionally-convert-instances]}

              :refinement/convert-instances-transitively
              {:label "Converting instances between specs transitively"
               :desc "How to convert an instance from one spec type to another through an intermediate spec."
               :basic-ref ['instance]
               :op-ref ['refine-to]
               :contents ["Refinements are automatically, transitively applied to produce an instance of the target spec."
                          {:spec-map {:spec/A$v3 {:spec-vars {:b "Integer"}
                                                  :refines-to {:spec/P$v3 {:name "refine_to_P"
                                                                           :expr '{:$type :spec/P$v3
                                                                                   :q b}}}}
                                      :spec/P$v3 {:spec-vars {:q "Integer"}
                                                  :refines-to {:spec/X$v3 {:name "refine_to_X"
                                                                           :expr '{:$type :spec/X$v3
                                                                                   :y q}}}}
                                      :spec/X$v3 {:spec-vars {:y "Integer"}}}}
                          "The chain of refinements is invoked by simply refining the instance to the final target spec."
                          {:code '(let [a {:$type :spec/A$v3 :b 1}]
                                    (refine-to a :spec/X$v3))
                           :result :auto}]
               :see-also [:refinement/convert-instances]}

              :refinement/arbitrary-expression-refinements
              {:label "Arbitrary expression in refinements"
               :desc "How to write arbitrary expressions to convert instances."
               :basic-ref ['instance]
               :op-ref ['refine-to]
               :contents ["Refinement expressions can be arbitrary expressions over the fields of the instance or constant values."
                          {:spec-map {:spec/A$v4 {:spec-vars {:b "Integer"
                                                              :c "Integer"
                                                              :d "String"}
                                                  :refines-to {:spec/X$v4 {:name "refine_to_X"
                                                                           :expr '{:$type :spec/X$v4
                                                                                   :x (+ b c)
                                                                                   :y 12
                                                                                   :z (if (= "medium" d) 5 10)}}}}
                                      :spec/X$v4 {:spec-vars {:x "Integer"
                                                              :y "Integer"
                                                              :z "Integer"}}}}
                          {:code '(let [a {:$type :spec/A$v4 :b 1 :c 2 :d "large"}]
                                    (refine-to a :spec/X$v4))
                           :result :auto}]
               :see-also [:refinement/convert-instances]}

              :refinement/optionally-convert-instances
              {:label "Optionally converting instances between specs"
               :desc "Consider there are some cases where an instance can be converted to another spec, but other cases where it cannot be. Refinement expressions can include logic to optionally convert an instance."
               :basic-ref ['instance]
               :op-ref ['refine-to 'refines-to?]
               :contents ["In the following example, the refinement expression determines whether to convert an instance based on the value of 'b'."
                          {:spec-map {:spec/A$v1 {:spec-vars {:b "Integer"}
                                                  :refines-to {:spec/X$v1 {:name "refine_to_X"
                                                                           :expr '(when (> b 10)
                                                                                    {:$type :spec/X$v1
                                                                                     :y b})}}}
                                      :spec/X$v1 {:spec-vars {:y "Integer"}}}}
                          "In this example, the refinement applies."
                          {:code '(refine-to {:$type :spec/A$v1 :b 20} :spec/X$v1)
                           :result :auto}
                          "In this example, the refinement does not apply"
                          {:code '(refine-to {:$type :spec/A$v1 :b 5} :spec/X$v1)
                           :throws :auto}
                          "A refinement path can be probed to determine if it exists and if it applies to a given instance."
                          {:code '(refines-to? {:$type :spec/A$v1 :b 20} :spec/X$v1)
                           :result :auto}
                          {:code '(refines-to? {:$type :spec/A$v1 :b 5} :spec/X$v1)
                           :result :auto}]
               :see-also [:refinement/convert-instances]}

              :instance/constrain-instances
              {:label "Defining constraints on instance values"
               :desc "How to constrain the possible values for instance fields"
               :basic-ref ['instance]
               :contents ["As a starting point specs specify the fields that make up instances."
                          {:spec-map {:spec/A$v1 {:spec-vars {:b "Integer"}}}}
                          "This indicates that 'b' must be an integer, but it doesn't indicate what valid values are. The following spec includes a constraint that requires b to be greater than 100."
                          {:spec-map {:spec/A$v2 {:spec-vars {:b "Integer"}
                                                  :constraints [["constrain_b" '(> b 100)]]}}}

                          "An attempt to make an instance that satisfies this constraint is successful"
                          {:code '{:$type :spec/A$v2 :b 200}
                           :result :auto}
                          "However, an attempt to make an instance that violates this constraint fails."
                          {:code '{:$type :spec/A$v2 :b 50}
                           :throws :auto}
                          "Constraints can be arbitrary expressions that refer to multiple fields."
                          {:spec-map {:spec/A$v3 {:spec-vars {:b "Integer"
                                                              :c "Integer"}
                                                  :constraints [["constrain_b_c" '(< (+ b c) 10)]]}}}
                          "In this example, the sum of 'a' and 'b' must be less than 10"
                          {:code '{:$type :spec/A$v3 :b 2 :c 3}
                           :result :auto}
                          {:code '{:$type :spec/A$v3 :b 6 :c 7}
                           :throws :auto}]
               :see-also [:instance/multi-constrain-instances]}

              :instance/multi-constrain-instances
              {:label "Defining multiple constraints on instance values"
               :desc "How to define multiple constraints in a spec"
               :basic-ref ['instance]
               :contents ["Multiple constraints can be defined on a spec. Each constraint must have a unique name within the context of a spec."
                          {:spec-map {:spec/A$v1 {:spec-vars {:b "Integer"
                                                              :c "Integer"}
                                                  :constraints [["constrain_b" '(> b 100)]
                                                                ["constrain_c" '(< c 20)]]}}}
                          "An instance must satisfy all of the constraints to be valid"
                          {:code '{:$type :spec/A$v1 :b 101 :c 19}
                           :result :auto}

                          "Violating any of the constraints makes the instance invalid"
                          {:code '{:$type :spec/A$v1 :b 100 :c 19}
                           :throws :auto}
                          {:code '{:$type :spec/A$v1 :b 101 :c 20}
                           :throws :auto}
                          "Mutliple constraints can refer to the same variables."
                          {:spec-map {:spec/A$v2 {:spec-vars {:b "Integer"}
                                                  :constraints [["constrain_b" '(> b 100)]
                                                                ["constrain_b2" '(< b 110)]]}}}
                          {:code '{:$type :spec/A$v2 :b 105}
                           :result :auto}
                          {:code '{:$type :spec/A$v2 :b 120}
                           :throws :auto}
                          "In general, constraint extpressions can be combined with a logical 'and'. This has the same meaning because all constraints are effectively 'anded' together to produce a single logical predicate to assess whether an instance is valid. So, decomposing constraints into separate constraints is largely a matter of organizing and naming the checks to suit the modelling exercise."
                          {:spec-map {:spec/A$v3 {:spec-vars {:b "Integer"}
                                                  :constraints [["constrain_b" '(and (> b 100)
                                                                                     (< b 110))]]}}}
                          {:code '{:$type :spec/A$v3 :b 105}
                           :result :auto}
                          {:code '{:$type :spec/A$v3 :b 120}
                           :throws :auto}]
               :see-also [:instance/constrain-instances]}

              :instance/compose-instances
              {:label "Compose instances"
               :desc "How to make specs which are the composition of other specs and how to make instances of those specs."
               :basic-ref ['instance]
               :contents ["A spec variable can be of the type of another spec"
                          {:spec-map {:spec/A$v1 {:spec-vars {:b :spec/B$v1}}
                                      :spec/B$v1 {:spec-vars {:c "Integer"}}}}
                          "Composite instances are created by nesting the instances at construction time."
                          {:code '{:$type :spec/A$v1 :b {:$type :spec/B$v1 :c 1}}}]
               :see-also [:instance/spec-variables]}

              :instance/functions
              {:label "Use an instance as a function to compute a value"
               :desc "Consider there is some logic that needs to be reused in multiple contexts. How to package it up so that it can be reused?"
               :basic-ref ['instance]
               :op-ref ['refine-to]
               :contents ["It is a bit convoluted, but consider the following specs."
                          {:spec-map {:spec/Add {:spec-vars {:x "Integer"
                                                             :y "Integer"}
                                                 :refines-to {:spec/IntegerResult {:name "refine_to_result"
                                                                                   :expr '{:$type :spec/IntegerResult
                                                                                           :result (+ x y)}}}}
                                      :spec/IntegerResult {:spec-vars {:result "Integer"}}}}
                          "This makes a spec which when instantiated is allows a refinement expression to be invoked as a sort of function call."
                          {:code '(let [x 2
                                        y 3
                                        result (get (refine-to {:$type :spec/Add :x x :y y} :spec/IntegerResult) :result)]
                                    result)
                           :result :auto}
                          "This is not necessarily recommended, but it is possible."]}

              :instance/predicate
              {:label "Use an instance as a predicate"
               :desc "Consider you need to evaluate an expression as a predicate, to determine if some values relate to each other properly."
               :basic-ref ['instance]
               :op-ref ['valid?]
               :contents ["The following specification uses a constraint to capture a predicate that checks whether a value is equal to the sum of two other values."
                          {:spec-map {:spec/Sum {:spec-vars {:x "Integer"
                                                             :y "Integer"
                                                             :sum "Integer"}
                                                 :constraints [["constrain_sum" '(= sum (+ x y))]]}}}
                          "The following will attempt to instantiate an instance of the spec and indicate whether the instance satisfied the constraint. In this case it does."
                          {:code '(valid? {:$type :spec/Sum :x 2 :y 3 :sum 5})
                           :result :auto}
                          "Here is another example, in which the constraint is not met."
                          {:code '(valid? {:$type :spec/Sum :x 2 :y 3 :sum 6})
                           :result :auto}
                          "Note, for the case where the constraint is not met, a naked attempt to instantiate the instance will produce a runtime error."
                          {:code '{:$type :spec/Sum :x 2 :y 3 :sum 6}
                           :throws :auto}]}

              :number/add-integer-to-decimal
              {:label "Add an integer value to a decimal value"
               :desc "Consider you have an integer and a decimal value and you need to add them together."
               :basic-ref ['integer 'fixed-decimal]
               :op-ref ['+]
               :contents ["Since the integer and decimal values are of different types they cannot be directly added together."
                          {:code '(let [x 3
                                        y #d "2.2"]
                                    (+ y x))
                           :throws :auto}
                          "It is first necessary to convert them to the same type of value. In this case the integer is converted into a decimal value by multiplying it by one in the target decimal type."
                          {:code '(let [x 3]
                                    (* #d "1.0" x))
                           :result :auto}
                          "Now the numbers can be added together."
                          {:code '(let [x 3
                                        y #d "2.2"]
                                    (+ y (* #d "1.0" x)))
                           :result :auto}]}

              :number/perform-non-integer-division
              {:label "Divide an integer to produce a decimal result"
               :desc "Consider you have an integer value and you want to divide it by another integer to produce a decimal result."
               :basic-ref ['integer 'fixed-decimal]
               :op-ref ['div]
               :contents ["Simply performing the division provides an integer result"
                          {:code '(let [x 14
                                        y 3]
                                    (div x y))
                           :result :auto}
                          "The mod operator can provide the remainder"
                          {:code '(let [x 14
                                        y 3]
                                    (mod x y))
                           :result :auto}
                          "The remainder can be converted into a decimal"
                          {:code '(let [x 14
                                        y 3]
                                    (* #d "1.0" (mod x y)))
                           :result :auto}
                          "The remainder can be divided by the orginal divisor."
                          {:code '(let [x 14
                                        y 3]
                                    (div (* #d "1.0" (mod x y)) y))
                           :result :auto}
                          "The integer part of the division result can also be converted to a decimal"
                          {:code '(let [x 14
                                        y 3]
                                    (* #d "1.0" (div x y)))
                           :result :auto}
                          "Putting it all together gives the result of the division truncated to one decimal place"
                          {:code '(let [x 14
                                        y 3]
                                    (+ (* #d "1.0" (div x y))
                                       (div (* #d "1.0" (mod x y)) y)))
                           :result :auto}]}})
