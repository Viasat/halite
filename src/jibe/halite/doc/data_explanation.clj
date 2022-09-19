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
                               "Note, a refinement is not the same as a sub-type relationship. This is not saying that 'spec/Ball' is a sub-type of 'spec/Ball'. In fact this is formally seen by the fact that the two sets are disjoint. An instance of 'spec/Ball' is never itself an instance of 'spec/Round'. Rather the refinement establishes a relationship between values from the two sets."]}})
