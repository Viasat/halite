<!---
  This markdown file was generated. Do not edit.
  -->

## Variables with abstract types

How to use variables which are defined to be the type of abstract specs.

Consider the following specs, where a pet is composed of an animal object and a name. The animal field is declared to have a type of the abstract spec, 'spec/Animal'.

```clojure
{:spec/Animal {:abstract? true,
               :spec-vars {:species "String"}},
 :spec/Cat {:spec-vars {:lives "Integer"},
            :refines-to {:spec/Animal {:name "refine_to_Animal",
                                       :expr '{:$type :spec/Animal,
                                               :species "Feline"}}}},
 :spec/Dog {:spec-vars {:breed "String"},
            :refines-to {:spec/Animal {:name "refine_to_Animal",
                                       :expr '{:$type :spec/Animal,
                                               :species "Canine"}}}},
 :spec/Pet {:spec-vars {:name "String",
                        :animal :spec/Animal}}}
```

The animal spec cannot be directly used to make a pet instance.

```clojure
{:name "Silver",
 :$type :spec/Pet,
 :animal {:$type :spec/Animal,
          :species "Equine"}}


;-- result --
[:throws "h-err/no-abstract 0-0 : Instance cannot contain abstract value"
 :h-err/no-abstract]
```

Instead, to construct a pet instance, a dog or cat instance must be used for the animal field.

```clojure
{:name "Rex",
 :$type :spec/Pet,
 :animal {:$type :spec/Dog,
          :breed "Golden Retriever"}}
```

```clojure
{:name "Tom",
 :$type :spec/Pet,
 :animal {:$type :spec/Cat,
          :lives 9}}
```

In order to access the value in the animal field as an animal object, the value must be refined to its abstract type.

```clojure
(let [pet {:$type :spec/Pet,
           :animal {:$type :spec/Dog,
                    :breed "Golden Retriever"},
           :name "Rex"}]
  (get (refine-to (get pet :animal) :spec/Animal) :species))


;-- result --
"Canine"
```

```clojure
(let [pet {:$type :spec/Pet,
           :animal {:$type :spec/Cat,
                    :lives 9},
           :name "Tom"}]
  (get (refine-to (get pet :animal) :spec/Animal) :species))


;-- result --
"Feline"
```

Even if we know the concrete type of the field value we cannot access it as that type. Instead the field must be refined to its abstract type before being accessed.

```clojure
(let [pet {:$type :spec/Pet,
           :animal {:$type :spec/Dog,
                    :breed "Golden Retriever"},
           :name "Rex"}]
  (get-in pet [:animal :breed]))


;-- result --
[:throws
 "h-err/invalid-lookup-target 0-0 : Lookup target must be an instance of known type or non-empty vector"]
```

#### Basic elements:

[`instance`](../halite-basic-syntax-reference.md#instance)

#### Operator reference:

* [`get`](../halite-full-reference.md#get)
* [`get-in`](../halite-full-reference.md#get-in)
* [`refine-to`](../halite-full-reference.md#refine-to)


#### How tos:

* [compose-instances](compose-instances.md)
* [string-enum](string-enum.md)


