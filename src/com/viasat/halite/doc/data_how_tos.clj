;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.doc.data-how-tos)

(set! *warn-on-reflection* true)

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
               :how-to-ref [:collections/reduce]}

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
               :how-to-ref [:collections/transform]}

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
               :how-to-ref [:collections/set-containment
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
               :how-to-ref [:collections/vector-containment
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
               :how-to-ref [:collections/vector-containment
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
               :how-to-ref [:collections/combine-set-to-vector]}

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
               :how-to-ref [:collections/combine]}

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
               :how-to-ref [:collections/convert-set-to-vector
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
               :how-to-ref [:collections/convert-vector-to-set
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
               :how-to-ref [:collections/convert-vector-to-set
                            :collections/convert-set-to-vector]}

              :instance/spec-fields
              {:label "Spec fields"
               :desc "How to model data fields in specifications."
               :basic-ref ['instance 'vector 'spec-map]
               :contents ["It is possible to define a spec that does not have any fields."
                          {:spec-map {:spec/Dog$v1 {}}}
                          "Instances of this spec could be created as:"
                          {:code '{:$type :spec/Dog$v1}}
                          "It is more interesting to define data fields on specs that define the structure of instances of the spec"
                          {:spec-map {:spec/Dog$v2 {:fields {:age :Integer}}}}
                          "This spec can be instantiated as:"
                          {:code '{:$type :spec/Dog$v2, :age 3}}
                          "A spec can have multiple fields"
                          {:spec-map {:spec/Dog$v4 {:fields {:name :String
                                                             :age :Integer
                                                             :colors [:Vec :String]}}}}
                          {:code '{:$type :spec/Dog$v4, :name "Rex", :age 3, :colors ["brown" "white"]}}]
               :how-to-ref [:instance/compose-instances
                            :instance/string-enum]}

              :instance/abstract-variables
              {:label "Variables with abstract types"
               :desc "How to use variables which are defined to be the type of abstract specs."
               :basic-ref ['instance 'spec-map]
               :op-ref ['refine-to 'get-in 'get]
               :contents ["Consider the following specs, where a pet is composed of an animal object and a name. The animal field is declared to have a type of the abstract spec, 'spec/Animal'."
                          {:spec-map {:spec/Animal {:abstract? true
                                                    :fields {:species :String}}
                                      :spec/Pet {:fields {:animal :spec/Animal
                                                          :name :String}}
                                      :spec/Dog {:fields {:breed :String}
                                                 :refines-to {:spec/Animal {:name "refine_to_Animal"
                                                                            :expr '{:$type :spec/Animal
                                                                                    :species "Canine"}}}}
                                      :spec/Cat {:fields {:lives :Integer}
                                                 :refines-to {:spec/Animal {:name "refine_to_Animal"
                                                                            :expr '{:$type :spec/Animal
                                                                                    :species "Feline"}}}}}}
                          "The animal spec cannot be directly used to make a pet instance."
                          {:code '{:$type :spec/Pet
                                   :animal {:$type :spec/Animal :species "Equine"}
                                   :name "Silver"}
                           :throws :auto}
                          "Instead, to construct a pet instance, a dog or cat instance must be used for the animal field."
                          {:code '{:$type :spec/Pet
                                   :animal {:$type :spec/Dog :breed "Golden Retriever"}
                                   :name "Rex"}}
                          {:code '{:$type :spec/Pet
                                   :animal {:$type :spec/Cat :lives 9}
                                   :name "Tom"}}
                          "In order to access the value in the animal field as an animal object, the value must be refined to its abstract type."
                          {:code '(let [pet {:$type :spec/Pet
                                             :animal {:$type :spec/Dog :breed "Golden Retriever"}
                                             :name "Rex"}]
                                    (get (refine-to (get pet :animal) :spec/Animal) :species))
                           :result :auto}
                          {:code '(let [pet {:$type :spec/Pet
                                             :animal {:$type :spec/Cat :lives 9}
                                             :name "Tom"}]
                                    (get (refine-to (get pet :animal) :spec/Animal) :species))
                           :result :auto}
                          "Even if we know the concrete type of the field value we cannot access it as that type. Instead the field must be refined to its abstract type before being accessed."
                          {:code '(let [pet {:$type :spec/Pet
                                             :animal {:$type :spec/Dog :breed "Golden Retriever"}
                                             :name "Rex"}]
                                    (get-in pet [:animal :breed]))
                           :throws :auto}]
               :how-to-ref [:instance/compose-instances
                            :instance/string-enum]}

              :instance/abstract-variables-refinements
              {:label "Variables with abstract types used in refinements"
               :desc "How to use variables which are defined to be the type of abstract specs in refinements."
               :basic-ref ['instance 'spec-map]
               :op-ref ['refine-to]
               :contents ["The way to use an abstract field value as the result value in a refinement is to refine it to its abstract type. This is necessary because the type of a refinement expression must exactly match the declared type of the refinement."
                          {:spec-map {:spec/Animal {:abstract? true
                                                    :fields {:species :String}}
                                      :spec/Pet$v1 {:fields {:animal :spec/Animal
                                                             :name :String}
                                                    :refines-to {:spec/Animal {:name "refine_to_Animal"
                                                                               :expr '(refine-to animal :spec/Animal)}}}
                                      :spec/Dog {:fields {:breed :String}
                                                 :refines-to {:spec/Animal {:name "refine_to_Animal"
                                                                            :expr '{:$type :spec/Animal
                                                                                    :species "Canine"}}}}
                                      :spec/Cat {:fields {:lives :Integer}
                                                 :refines-to {:spec/Animal {:name "refine_to_Animal"
                                                                            :expr '{:$type :spec/Animal
                                                                                    :species "Feline"}}}}}}
                          {:code '(let [pet {:$type :spec/Pet$v1
                                             :animal {:$type :spec/Dog :breed "Golden Retriever"}
                                             :name "Rex"}]
                                    (refine-to pet :spec/Animal))
                           :result :auto}
                          {:code '(let [pet {:$type :spec/Pet$v1
                                             :animal {:$type :spec/Cat :lives 9}
                                             :name "Tom"}]
                                    (refine-to pet :spec/Animal))
                           :result :auto}

                          "Even if we happen to know the concrete type of an abstract field is of the right type for a refinement it cannot be used."
                          {:spec-map {:spec/Animal {:abstract? true
                                                    :fields {:species :String}}
                                      :spec/Pet$v2 {:fields {:animal :spec/Animal
                                                             :name :String}
                                                    :refines-to {:spec/Dog {:name "refine_to_Dog"
                                                                            :expr 'animal}}}
                                      :spec/Dog {:fields {:breed :String}
                                                 :refines-to {:spec/Animal {:name "refine_to_Animal"
                                                                            :expr '{:$type :spec/Animal
                                                                                    :species "Canine"}}}}}}
                          "In this example, even though we know the value in the animal field is a dog, the attempted refinement cannot be executed."
                          {:code '(let [pet {:$type :spec/Pet$v2
                                             :animal {:$type :spec/Dog :breed "Golden Retriever"}
                                             :name "Rex"}]
                                    (refine-to pet :spec/Dog))
                           :throws :auto}
                          "If instead, we attempt to define the refinement of type animal, but still try to use the un-refined field value as the result of the refinement, it still fails."
                          {:spec-map {:spec/Animal {:abstract? true
                                                    :fields {:species :String}}
                                      :spec/Pet$v3 {:fields {:animal :spec/Animal
                                                             :name :String}
                                                    :refines-to {:spec/Animal {:name "refine_to_Animal"
                                                                               :expr 'animal}}}
                                      :spec/Dog {:fields {:breed :String}
                                                 :refines-to {:spec/Animal {:name "refine_to_Animal"
                                                                            :expr '{:$type :spec/Animal
                                                                                    :species "Canine"}}}}
                                      :spec/Cat {:fields {:lives :Integer}
                                                 :refines-to {:spec/Animal {:name "refine_to_Animal"
                                                                            :expr '{:$type :spec/Animal
                                                                                    :species "Feline"}}}}}}
                          "The refinement fails in this example, because the value being produced by the refinement expression is a dog, when it must be an animal to match the declared type of the refinement."
                          {:code '(let [pet {:$type :spec/Pet$v3
                                             :animal {:$type :spec/Dog :breed "Golden Retriever"}
                                             :name "Rex"}]
                                    (refine-to pet :spec/Animal))
                           :throws :auto}
                          "In fact, there is no way to make this refinement work because the animal field cannot be constructed with an abstract instance."
                          {:code '(let [pet {:$type :spec/Pet$v3
                                             :animal {:$type :spec/Animal :species "Equine"}
                                             :name "Rex"}]
                                    (refine-to pet :spec/Animal))
                           :throws :auto}]
               :how-to-ref [:instance/compose-instances
                            :instance/string-enum]}

              :instance/string-enum
              {:label "String as enumeration"
               :desc "How to model an enumeration as a string"
               :basic-ref ['instance 'spec-map]
               :contents ["Say we want to model a shirt size and the valid values are \"small\", \"medium\", and \"large\". We can start by modeling the size as a string."
                          {:spec-map {:spec/Shirt$v1 {:fields {:size :String}}}}
                          "This is a start, but it allows invalid size values."
                          {:code '{:$type :spec/Shirt$v1 :size "XL"}
                           :result :auto}
                          "So we can add a constraint to limit the values to what we expect."
                          {:spec-map {:spec/Shirt$v2 {:fields {:size :String}
                                                      :constraints #{{:name "size_constraint" :expr '(contains? #{"small" "medium" "large"} size)}}}}}
                          "Now the shirt with the invalid size cannot be constructed."
                          {:code '{:$type :spec/Shirt$v2 :size "XL"}
                           :throws :auto}
                          "But a shirt with a valid size can be constructed."
                          {:code '{:$type :spec/Shirt$v2 :size "medium"}
                           :result :auto}]
               :how-to-ref [:instance/spec-fields]}

              :refinement/convert-instances
              {:label "Converting instances between specs"
               :desc "How to convert an instance from one spec type to another."
               :basic-ref ['instance 'spec-map]
               :op-ref ['refine-to]
               :contents ["An expression can convert an instance of one type to the instance of another type. Assume there are these two specs."
                          {:spec-map {:spec/A$v1 {:fields {:b :Integer}}
                                      :spec/X$v1 {:fields {:y :Integer}}}}
                          "The following expression converts an instance of the first spec into an instance of the second."
                          {:code '(let [a {:$type :spec/A$v1 :b 1}]
                                    {:$type :spec/X$v1 :y (get a :b)})
                           :result :auto}
                          "This work, but the language has a built-in idea of 'refinements' that allow such conversion functions to be expressed in a way that the system understands."
                          {:spec-map {:spec/A$v2 {:fields {:b :Integer}
                                                  :refines-to {:spec/X$v2 {:name "refine_to_X"
                                                                           :expr '{:$type :spec/X$v2
                                                                                   :y b}}}}
                                      :spec/X$v2 {:fields {:y :Integer}}}}
                          "The refinement can be invoked as follows:"
                          {:code '(let [a {:$type :spec/A$v2 :b 1}]
                                    (refine-to a :spec/X$v2))
                           :result :auto}]
               :how-to-ref [:refinement/convert-instances-transitively
                            :refinement/arbitrary-expression-refinements
                            :refinement/optionally-convert-instances]
               :tutorial-ref [:spec/grocery]}

              :refinement/convert-instances-transitively
              {:label "Converting instances between specs transitively"
               :desc "How to convert an instance from one spec type to another through an intermediate spec."
               :basic-ref ['instance 'spec-map]
               :op-ref ['refine-to]
               :contents ["Refinements are automatically, transitively applied to produce an instance of the target spec."
                          {:spec-map {:spec/A$v3 {:fields {:b :Integer}
                                                  :refines-to {:spec/P$v3 {:name "refine_to_P"
                                                                           :expr '{:$type :spec/P$v3
                                                                                   :q b}}}}
                                      :spec/P$v3 {:fields {:q :Integer}
                                                  :refines-to {:spec/X$v3 {:name "refine_to_X"
                                                                           :expr '{:$type :spec/X$v3
                                                                                   :y q}}}}
                                      :spec/X$v3 {:fields {:y :Integer}}}}
                          "The chain of refinements is invoked by simply refining the instance to the final target spec."
                          {:code '(let [a {:$type :spec/A$v3 :b 1}]
                                    (refine-to a :spec/X$v3))
                           :result :auto}]
               :how-to-ref [:refinement/convert-instances]}

              :refinement/arbitrary-expression-refinements
              {:label "Arbitrary expression in refinements"
               :desc "How to write arbitrary expressions to convert instances."
               :basic-ref ['instance 'spec-map]
               :op-ref ['refine-to]
               :contents ["Refinement expressions can be arbitrary expressions over the fields of the instance or constant values."
                          {:spec-map {:spec/A$v4 {:fields {:b :Integer
                                                           :c :Integer
                                                           :d :String}
                                                  :refines-to {:spec/X$v4 {:name "refine_to_X"
                                                                           :expr '{:$type :spec/X$v4
                                                                                   :x (+ b c)
                                                                                   :y 12
                                                                                   :z (if (= "medium" d) 5 10)}}}}
                                      :spec/X$v4 {:fields {:x :Integer
                                                           :y :Integer
                                                           :z :Integer}}}}
                          {:code '(let [a {:$type :spec/A$v4 :b 1 :c 2 :d "large"}]
                                    (refine-to a :spec/X$v4))
                           :result :auto}]
               :how-to-ref [:refinement/convert-instances]
               :explanation-ref [:spec/refinement-implications]}

              :refinement/optionally-convert-instances
              {:label "Optionally converting instances between specs"
               :desc "Consider there are some cases where an instance can be converted to another spec, but other cases where it cannot be. Refinement expressions can include logic to optionally convert an instance."
               :basic-ref ['instance 'spec-map]
               :op-ref ['refine-to 'refines-to?]
               :contents ["In the following example, the refinement expression determines whether to convert an instance based on the value of 'b'."
                          {:spec-map {:spec/A$v1 {:fields {:b :Integer}
                                                  :refines-to {:spec/X$v1 {:name "refine_to_X"
                                                                           :expr '(when (> b 10)
                                                                                    {:$type :spec/X$v1
                                                                                     :y b})}}}
                                      :spec/X$v1 {:fields {:y :Integer}}}}
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
               :how-to-ref [:refinement/convert-instances]}

              :instance/constrain-instances
              {:label "Defining constraints on instance values"
               :desc "How to constrain the possible values for instance fields"
               :basic-ref ['instance 'spec-map]
               :contents ["As a starting point specs specify the fields that make up instances."
                          {:spec-map {:spec/A$v1 {:fields {:b :Integer}}}}
                          "This indicates that 'b' must be an integer, but it doesn't indicate what valid values are. The following spec includes a constraint that requires b to be greater than 100."
                          {:spec-map {:spec/A$v2 {:fields {:b :Integer}
                                                  :constraints #{{:name "constrain_b" :expr '(> b 100)}}}}}

                          "An attempt to make an instance that satisfies this constraint is successful"
                          {:code '{:$type :spec/A$v2 :b 200}
                           :result :auto}
                          "However, an attempt to make an instance that violates this constraint fails."
                          {:code '{:$type :spec/A$v2 :b 50}
                           :throws :auto}
                          "Constraints can be arbitrary expressions that refer to multiple fields."
                          {:spec-map {:spec/A$v3 {:fields {:b :Integer
                                                           :c :Integer}
                                                  :constraints #{{:name "constrain_b_c" :expr '(< (+ b c) 10)}}}}}
                          "In this example, the sum of 'a' and 'b' must be less than 10"
                          {:code '{:$type :spec/A$v3 :b 2 :c 3}
                           :result :auto}
                          {:code '{:$type :spec/A$v3 :b 6 :c 7}
                           :throws :auto}]
               :how-to-ref [:instance/multi-constrain-instances]
               :tutorial-ref [:spec/sudoku]}

              :instance/multi-constrain-instances
              {:label "Defining multiple constraints on instance values"
               :desc "How to define multiple constraints in a spec"
               :basic-ref ['instance 'spec-map]
               :contents ["Multiple constraints can be defined on a spec. Each constraint must have a unique name within the context of a spec."
                          {:spec-map {:spec/A$v1 {:fields {:b :Integer
                                                           :c :Integer}
                                                  :constraints #{{:name "constrain_b" :expr '(> b 100)}
                                                                 {:name "constrain_c" :expr '(< c 20)}}}}}
                          "An instance must satisfy all of the constraints to be valid"
                          {:code '{:$type :spec/A$v1 :b 101 :c 19}
                           :result :auto}

                          "Violating any of the constraints makes the instance invalid"
                          {:code '{:$type :spec/A$v1 :b 100 :c 19}
                           :throws :auto}
                          {:code '{:$type :spec/A$v1 :b 101 :c 20}
                           :throws :auto}
                          "Mutliple constraints can refer to the same variables."
                          {:spec-map {:spec/A$v2 {:fields {:b :Integer}
                                                  :constraints #{{:name "constrain_b" :expr '(> b 100)}
                                                                 {:name "constrain_b2" :expr '(< b 110)}}}}}
                          {:code '{:$type :spec/A$v2 :b 105}
                           :result :auto}
                          {:code '{:$type :spec/A$v2 :b 120}
                           :throws :auto}
                          "In general, constraint extpressions can be combined with a logical 'and'. This has the same meaning because all constraints are effectively 'anded' together to produce a single logical predicate to assess whether an instance is valid. So, decomposing constraints into separate constraints is largely a matter of organizing and naming the checks to suit the modelling exercise."
                          {:spec-map {:spec/A$v3 {:fields {:b :Integer}
                                                  :constraints #{{:name "constrain_b" :expr '(and (> b 100)
                                                                                                  (< b 110))}}}}}
                          {:code '{:$type :spec/A$v3 :b 105}
                           :result :auto}
                          {:code '{:$type :spec/A$v3 :b 120}
                           :throws :auto}]
               :how-to-ref [:instance/constrain-instances]}

              :instance/compose-instances
              {:label "Compose instances"
               :desc "How to make specs which are the composition of other specs and how to make instances of those specs."
               :basic-ref ['instance 'spec-map]
               :contents ["A spec variable can be of the type of another spec"
                          {:spec-map {:spec/A$v1 {:fields {:b :spec/B$v1}}
                                      :spec/B$v1 {:fields {:c :Integer}}}}
                          "Composite instances are created by nesting the instances at construction time."
                          {:code '{:$type :spec/A$v1 :b {:$type :spec/B$v1 :c 1}}}]
               :how-to-ref [:instance/spec-fields]}

              :instance/functions
              {:label "Use an instance as a function to compute a value"
               :desc "Consider there is some logic that needs to be reused in multiple contexts. How to package it up so that it can be reused?"
               :basic-ref ['instance 'spec-map]
               :op-ref ['refine-to]
               :contents ["It is a bit convoluted, but consider the following specs."
                          {:spec-map {:spec/Add {:fields {:x :Integer
                                                          :y :Integer}
                                                 :refines-to {:spec/IntegerResult {:name "refine_to_result"
                                                                                   :expr '{:$type :spec/IntegerResult
                                                                                           :result (+ x y)}}}}
                                      :spec/IntegerResult {:fields {:result :Integer}}}}
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
               :basic-ref ['instance 'spec-map]
               :op-ref ['valid?]
               :contents ["The following specification uses a constraint to capture a predicate that checks whether a value is equal to the sum of two other values."
                          {:spec-map {:spec/Sum {:fields {:x :Integer
                                                          :y :Integer
                                                          :sum :Integer}
                                                 :constraints #{{:name "constrain_sum" :expr '(= sum (+ x y))}}}}}
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
                           :result :auto}]}

              :flow-control/loop
              {:label "How to write a loop"
               :desc "There is no explicit language construct to write a loop. So how to write one?"
               :contents ["Most languages will have some sort of 'for' loop or 'do while' look construct. In many cases the need for looping is subsumed by the collection operators that are present. For example, rather than writing a loop to extract values from a collection, 'filter' can be used."
                          {:code '(let [x [5 17 23 35]]
                                    (filter [e x] (> e 20)))
                           :result :auto}
                          "Similarly if we need to make a new collection derived from a collection, rather than writing a loop, we can use 'map'."
                          {:code '(let [x [5 17 23 35]]
                                    (map [e x] (inc e)))
                           :result :auto}
                          "If we need to test whether a predicate holds for items in a collection, rather than writing a loop we can use 'every?' and 'any?'."
                          {:code '(let [x [5 17 23 35]]
                                    (every? [e x] (> e 0)))
                           :result :auto}
                          {:code '(let [x [5 17 23 35]]
                                    (any? [e x] (> e 20)))
                           :result :auto}

                          "Finally if we need to create a single value from a collection, rather than writing a loop, we can use 'reduce'."
                          {:code '(let [x [5 17 23 35]]
                                    (reduce [a 0] [e x] (+ a e)))
                           :result :auto}
                          "So, if the loop is for dealing with a collection then the built-in operators can be used. But that leaves the question: what if there is no collection to use as the basis for the loop? In that case a collection of the desired size can be created on demand. In this example a collection of 10 items is created so that we can effectively 'loop' over it and add 3 to the accumulator each time through the loop."
                          {:code '(let [x (range 10)]
                                    (reduce [a 0] [e x] (+ a 3)))
                           :result :auto}
                          "That leaves the case of an infinite loop, or a loop that continues until some aribtrary expression evaluates to true. Infinite loops are not allowed by design. Looping until an expression evaluates to true is not supported and arguably not necessary. The language does not have side-effects or mutable state and in fact produces deterministic results. So the notion of looping until 'something else happens' does not make sense. Which leaves the case of looping a deterministic number of times, when then number of iterations is not known by the author of the code. For example, the following 'loops' dividing the intitial value by 2 until it cannot be divided further, then it returns the remainder. Rather than looping until the value is less than 2, this just loops a fixed number of times and the author of the code needs to know how many times is necessary to loop in order to fully divide the initial number."
                          {:code '(let [x 21]
                                    (reduce [a x] [e (range 10)] (if (>= a 2)
                                                                   (div a 2)
                                                                   a)))
                           :result :auto}
                          "Of course this example is contrived, because the 'mod' operator is available."]
               :basic-ref ['vector 'integer]
               :op-ref ['reduce 'map 'filter 'range 'div]}

              :flow-control/short-circuiting
              {:label "How to use short-circuiting to avoid runtime errors."
               :desc "Several operations can throw runtime errors. This includes mathematical overflow, division by 0, index out of bounds, invoking non-existent refinement paths, and construction of invalid instances. The question is, how to write code to avoid such runtime errors?"
               :contents ["The typical pattern to avoid such runtime errors is to first test to see if some condition is met to make the operation 'safe'. Only if that condition is met is the operator invoked. For example, to guard dividing by zero."
                          {:code '(let [x 0]
                                    (div 100 x))
                           :throws :auto}
                          {:code '(let [x 0]
                                    (if (not= x 0)
                                      (div 100 x)
                                      0))
                           :result :auto}

                          "To guard index out of bounds."
                          {:code '(let [x []]
                                    (get x 0))
                           :throws :auto}
                          {:code '(let [x []]
                                    (if (> (count x) 0)
                                      (get x 0)
                                      0))
                           :skip-lint? true
                           :result :auto}
                          {:code '(let [x [10]]
                                    (if (> (count x) 0)
                                      (get x 0)
                                      0))
                           :result :auto}

                          "To guard instance construction."
                          {:spec-map {:spec/Q {:fields {:a :Integer}
                                               :constraints #{{:name "c" :expr '(> a 0)}}}}}
                          {:code '(let [x 0]
                                    {:$type :spec/Q :a x})
                           :throws :auto}
                          {:code '(let [x 0]
                                    (if (valid? {:$type :spec/Q :a x})
                                      {:$type :spec/Q :a x}
                                      {:$type :spec/Q :a 1}))
                           :result :auto}
                          {:code '(let [x 10]
                                    (if (valid? {:$type :spec/Q :a x})
                                      {:$type :spec/Q :a x}
                                      {:$type :spec/Q :a 1}))
                           :result :auto}
                          "This example can be refined slightly to avoid duplicating the construction."
                          {:code '(let [x 0]
                                    (if-value-let [i (valid {:$type :spec/Q :a x})]
                                                  i
                                                  {:$type :spec/Q :a 1}))
                           :result :auto}
                          {:code '(let [x 10]
                                    (if-value-let [i (valid {:$type :spec/Q :a x})]
                                                  i
                                                  {:$type :spec/Q :a 1}))
                           :result :auto}

                          "To guard refinements."
                          {:spec-map {:spec/Q {:fields {:q :Integer}}
                                      :spec/P {:fields {:p :Integer}
                                               :refines-to {:spec/Q {:name "refine_to_Q"
                                                                     :expr '(when (> p 0)
                                                                              {:$type :spec/Q :q p})}}}}}
                          {:code '(let [x {:$type :spec/P :p 0}]
                                    (refine-to x :spec/Q))
                           :throws :auto}
                          {:code '(let [x {:$type :spec/P :p 0}]
                                    (if (refines-to? x :spec/Q)
                                      (refine-to x :spec/Q)
                                      {:$type :spec/Q :q 1}))
                           :result :auto}
                          {:code '(let [x {:$type :spec/P :p 10}]
                                    (if (refines-to? x :spec/Q)
                                      (refine-to x :spec/Q)
                                      {:$type :spec/Q :q 1}))
                           :result :auto}
                          "So, 'if' and variants of 'if' such as 'when', 'if-value-let', and 'when-value-let' are the main tools for avoiding runtime errors. They each are special forms which do not eagerly evaluate their bodies at invocation time."
                          "Some languages have short-circuiting logical operators 'and' and 'or'. However, they are not short-circuiting in this language."
                          {:code '(let [x 0]
                                    (and (> x 0) (> (div 100 x) 0)))
                           :throws :auto}
                          {:code '(let [x 0]
                                    (> x 0))
                           :result :auto}
                          "The same applies to 'or':"
                          {:code '(let [x 0]
                                    (or (= x 0) (> (div 100 x) 0)))
                           :throws :auto}
                          {:code '(let [x 0]
                                    (= x 0))
                           :result :auto}
                          "Similarly, the sequence operators of 'every?', 'any?', 'map', and 'filter' are all eager and fully evaluate for all elements of the collection regardless of what happens with the evaluation of prior elements."
                          "This raises an error even though logically, the result could be 'true' if just the first element is considered."
                          {:code '(let [x [2 1 0]]
                                    (any? [e x] (> (div 100 e) 0)))
                           :throws :auto}
                          {:code '(let [x [2 1]]
                                    (any? [e x] (> (div 100 e) 0)))
                           :result :auto}
                          "This raises an error even though logically, the result could be 'false' if just the first element is considered."
                          {:code '(let [x [200 100 0]]
                                    (every? [e x] (> (div 100 e) 0)))
                           :throws :auto}
                          {:code '(let [x [200 100]]
                                    (every? [e x] (> (div 100 e) 0)))
                           :result :auto}
                          "This raises an error even though, the result could be 50 if just the first element is actually accessed."
                          {:code '(let [x [2 1 0]]
                                    (get (map [e x] (div 100 e)) 0))
                           :throws :auto}
                          {:code '(let [x [2 1]]
                                    (get (map [e x] (div 100 e)) 0))
                           :result :auto}
                          "This raises an error even though, the result could be 2 if just the first element is actually accessed."
                          {:code '(let [x [2 1 0]]
                                    (get (filter [e x] (> (div 100 e) 0)) 0))
                           :throws :auto}
                          {:code '(let [x [2 1]]
                                    (get (filter [e x] (> (div 100 e) 0)) 0))
                           :result :auto}
                          "This means that the logical operators cannot be used to guard against runtime errors. Instead the control flow statements must be used."]
               :basic-ref ['vector 'integer 'spec-map]
               :op-ref ['any? 'every? 'get 'div 'refine-to 'refines-to? 'if 'if-value-let 'when 'when-value-let 'valid 'valid?]}

              :instance/recurse
              {:label "Recursive instances"
               :desc "Specs can be defined to be recursive."
               :contents [{:spec-map {:spec/Cell {:fields {:value :Integer
                                                           :next [:Maybe :spec/Cell]}}}}
                          {:code '{:$type :spec/Cell :value 10}}
                          {:code '{:$type :spec/Cell :value 10 :next {:$type :spec/Cell :value 11}}}]}

              :instance/recursive-refinement
              {:label "Recursive refinements"
               :desc "Specs cannot be defined to recursively refine to themselves."
               :contents ["For example, the following spec that refines to itself is not allowed."
                          {:spec-map {:spec/Mirror {:refines-to {:spec/Mirror {:name "refine_to_Mirror"
                                                                               :expr '{:$type :spec/Mirror}}}}}}
                          {:code '{:$type :spec/Mirror}
                           :throws :auto}
                          "Similarly transitive refinement loops are not allowed. For example, a pair of specs that refine to each other is not allowed."
                          {:spec-map {:spec/Bounce {:refines-to {:spec/Back {:name "refine_to_Back"
                                                                             :expr '{:$type :spec/Back}}}}
                                      :spec/Back {:refines-to {:spec/Bounce {:name "refine_to_Bounce"
                                                                             :expr '{:$type :spec/Bounce}}}}}}
                          {:code '{:$type :spec/Bounce}
                           :throws :auto}

                          "It is a bit more subtle, but a cyclical dependency that crosses both a refinement and a composition relationship is also disallowed."
                          {:spec-map {:spec/Car {:refines-to {:spec/Garage {:name "refine_to_Garage"
                                                                            :expr '{:$type :spec/Garage
                                                                                    :car {:$type :spec/Car}}}}}
                                      :spec/Garage {:fields {:car :spec/Car}}}}
                          {:code '{:$type :spec/Car}
                           :throws :auto}

                          "Diamonds are a bit different than a recursive refinement, but they too are disallowed and produce a similar error."
                          {:spec-map {:spec/Destination {:fields {:d :Integer}}
                                      :spec/Path1 {:refines-to {:spec/Destination {:name "refine_to_Destination"
                                                                                   :expr '{:$type :spec/Destination
                                                                                           :d 1}}}}
                                      :spec/Path2 {:refines-to {:spec/Destination {:name "refine_to_Destination"
                                                                                   :expr '{:$type :spec/Destination
                                                                                           :d 2}}}}
                                      :spec/Start {:refines-to {:spec/Path1 {:name "refine_to_Path1"
                                                                             :expr '{:$type :spec/Path1}}
                                                                :spec/Path2 {:name "refine_to_Path2"
                                                                             :expr '{:$type :spec/Path2}}}}}}
                          {:code '(refine-to {:$type :spec/Start} :spec/Destination)
                           :throws :auto}

                          "Generally, dependency cycles between specs are not allowed. The following spec-map is detected as invalid."
                          {:spec-map {:spec/Self {:constraints #{{:name "example" :expr '(= 1 (count [{:$type :spec/Self}]))}}}}
                           :throws :auto}
                          "Although this error can be detected in advance, if an attempt is made to use the spec, then a similar runtime error is produced."
                          {:code '{:$type :spec/Self}
                           :throws :auto}]}})
