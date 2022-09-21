<!---
  This markdown file was generated. Do not edit.
  -->

## Specs are about modeling things

Specs are a general mechanism for modelling whatever is of interest.

Writing a spec is carving out a subset out of the universe of all possible values and giving them a name.

```clojure
{:spec/Ball {:spec-vars {:color "String"},
             :constraints [["color_constraint"
                            '(contains? #{"blue" "green" "red"} color)]]}}
```

Instances of specs are represented as maps. Depending on programming languages, maps are also known as associative arrays or dictionarys.

The spec gives a name to the set of values which are maps that contain a type field with a value of 'spec/Ball' and which have a 'color' field with a string value. For example, this set includes the following specific instances.

```clojure
{:$type :spec/Ball,
 :color "red"}
```

```clojure
{:$type :spec/Ball,
 :color "blue"}
```

If instead we defined this spec, then we are further constraining the set of values in the 'spec/Ball' set. Specifically, this means that any instance which is otherwise a valid 'spec/Ball', but does not have one of these three prescribed colors is not a valid 'spec/Ball'.

```clojure
{:spec/Ball {:spec-vars {:color "String"},
             :constraints [["color_constraint"
                            '(contains? #{"blue" "green" "red"} color)]]}}
```

The following is not a valid 'spec/Ball' and in fact it is not a valid value at all in the universe of all possible values. This is because every map with a type of 'spec/Ball' must satisfy the 'spec/Ball' spec in order to be a valid value.

```clojure
{:$type :spec/Ball,
 :color "yellow"}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'spec/Ball', violates constraints color_constraint"
 :h-err/invalid-instance]
```

The spec can be considered a predicate which only produces a value of true if it is applied to a valid instance of the spec itself. This operation is captured in the following code.

```clojure
(valid? {:$type :spec/Ball,
         :color "red"})


;-- result --
true
```

```clojure
(valid? {:$type :spec/Ball,
         :color "yellow"})


;-- result --
false
```

A spec context defines all of the specs that are in play when evaluating expressions. By definition, these specs define disjoint sets of valid instance values. There is never any overlap in the instances that are valid for any two different specs.

However, it is possible to convert an instance of one spec into an instance of another spec. This is referred to as 'refinement'. Specs can include refinement expressions indicating how to convert them.

```clojure
{:spec/Ball {:spec-vars {:color "String"},
             :constraints [["color_constraint"
                            '(contains? #{"blue" "green" "red"} color)]],
             :refines-to {:spec/Round {:name "refine_to_round",
                                       :expr '{:$type :spec/Round,
                                               :radius 5}}}},
 :spec/Round {:spec-vars {:radius "Integer"}}}
```

The following shows how to invoke refinements.

```clojure
(refine-to {:$type :spec/Ball,
            :color "red"}
           :spec/Round)


;-- result --
{:$type :spec/Round,
 :radius 5}
```

A refinement defines a many-to-one mapping from one set of values to another set of values. In this example, it is a mapping from the values in the set 'spec/Ball' to the values in the set 'spec/Round'. Specifically, in this example, all 'spec/Ball' instances map to the same 'spec/Round' instance, but that is just the detail of this refinement definition.

Note, a refinement is not the same as a sub-type relationship. This is not saying that 'spec/Ball' is a sub-type of 'spec/Ball'. In fact this is formally seen by the fact that the two sets are disjoint. An instance of 'spec/Ball' is never itself an instance of 'spec/Round'. Rather the refinement establishes a relationship between values from the two sets.
