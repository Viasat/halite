<!---
  This markdown file was generated. Do not edit.
  -->

## Variables with abstract types used in refinements

How to use variables which are defined to be the type of abstract specs in refinements.

The way to use an abstract field value as the result value in a refinement is to refine it to its abstract type. This is necessary because the type of a refinement expression must exactly match the declared type of the refinement.

```clojure
{:spec/Animal {:abstract? true,
               :spec-vars {:species :String}},
 :spec/Cat {:spec-vars {:lives :Integer},
            :refines-to {:spec/Animal {:name "refine_to_Animal",
                                       :expr '{:$type :spec/Animal,
                                               :species "Feline"}}}},
 :spec/Dog {:spec-vars {:breed :String},
            :refines-to {:spec/Animal {:name "refine_to_Animal",
                                       :expr '{:$type :spec/Animal,
                                               :species "Canine"}}}},
 :spec/Pet$v1 {:spec-vars {:name :String,
                           :animal :spec/Animal},
               :refines-to {:spec/Animal {:name "refine_to_Animal",
                                          :expr '(refine-to animal
                                                            :spec/Animal)}}}}
```

```clojure
(let [pet {:$type :spec/Pet$v1,
           :animal {:$type :spec/Dog,
                    :breed "Golden Retriever"},
           :name "Rex"}]
  (refine-to pet :spec/Animal))


;-- result --
{:$type :spec/Animal,
 :species "Canine"}
```

```clojure
(let [pet {:$type :spec/Pet$v1,
           :animal {:$type :spec/Cat,
                    :lives 9},
           :name "Tom"}]
  (refine-to pet :spec/Animal))


;-- result --
{:$type :spec/Animal,
 :species "Feline"}
```

Even if we happen to know the concrete type of an abstract field is of the right type for a refinement it cannot be used.

```clojure
{:spec/Animal {:abstract? true,
               :spec-vars {:species :String}},
 :spec/Dog {:spec-vars {:breed :String},
            :refines-to {:spec/Animal {:name "refine_to_Animal",
                                       :expr '{:$type :spec/Animal,
                                               :species "Canine"}}}},
 :spec/Pet$v2 {:spec-vars {:name :String,
                           :animal :spec/Animal},
               :refines-to {:spec/Dog {:name "refine_to_Dog",
                                       :expr 'animal}}}}
```

In this example, even though we know the value in the animal field is a dog, the attempted refinement cannot be executed.

```clojure
(let [pet {:$type :spec/Pet$v2,
           :animal {:$type :spec/Dog,
                    :breed "Golden Retriever"},
           :name "Rex"}]
  (refine-to pet :spec/Dog))


;-- result --
[:throws
 "h-err/invalid-refinement-expression 0-0 : Refinement expression, 'animal', is not of the expected type"
 :h-err/invalid-refinement-expression]
```

If instead, we attempt to define the refinement of type animal, but still try to use the un-refined field value as the result of the refinement, it still fails.

```clojure
{:spec/Animal {:abstract? true,
               :spec-vars {:species :String}},
 :spec/Cat {:spec-vars {:lives :Integer},
            :refines-to {:spec/Animal {:name "refine_to_Animal",
                                       :expr '{:$type :spec/Animal,
                                               :species "Feline"}}}},
 :spec/Dog {:spec-vars {:breed :String},
            :refines-to {:spec/Animal {:name "refine_to_Animal",
                                       :expr '{:$type :spec/Animal,
                                               :species "Canine"}}}},
 :spec/Pet$v3 {:spec-vars {:name :String,
                           :animal :spec/Animal},
               :refines-to {:spec/Animal {:name "refine_to_Animal",
                                          :expr 'animal}}}}
```

The refinement fails in this example, because the value being produced by the refinement expression is a dog, when it must be an animal to match the declared type of the refinement.

```clojure
(let [pet {:$type :spec/Pet$v3,
           :animal {:$type :spec/Dog,
                    :breed "Golden Retriever"},
           :name "Rex"}]
  (refine-to pet :spec/Animal))


;-- result --
[:throws
 "h-err/invalid-refinement-expression 0-0 : Refinement expression, 'animal', is not of the expected type"
 :h-err/invalid-refinement-expression]
```

In fact, there is no way to make this refinement work because the animal field cannot be constructed with an abstract instance.

```clojure
(let [pet {:$type :spec/Pet$v3,
           :animal {:$type :spec/Animal,
                    :species "Equine"},
           :name "Rex"}]
  (refine-to pet :spec/Animal))


;-- result --
[:throws "h-err/no-abstract 0-0 : Instance cannot contain abstract value"
 :h-err/no-abstract]
```

### Reference

#### Basic elements:

[`instance`](../halite_basic-syntax-reference.md#instance), [`spec-map`](../../halite_spec-syntax-reference.md)

#### Operator reference:

* [`refine-to`](../halite_full-reference.md#refine-to)


#### How Tos:

* [compose-instances](../how-to/halite_compose-instances.md)
* [string-enum](../how-to/halite_string-enum.md)


