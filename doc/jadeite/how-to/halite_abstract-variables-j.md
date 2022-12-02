<!---
  This markdown file was generated. Do not edit.
  -->

## Variables with abstract types

How to use variables which are defined to be the type of abstract specs.

Consider the following specs, where a pet is composed of an animal object and a name. The animal field is declared to have a type of the abstract spec, 'spec/Animal'.

```java
{
  "spec/Animal" : {
    "abstract?" : true,
    "fields" : {
      "species" : "String"
    }
  },
  "spec/Pet" : {
    "fields" : {
      "animal" : "spec/Animal",
      "name" : "String"
    }
  },
  "spec/Dog" : {
    "fields" : {
      "breed" : "String"
    },
    "refines-to" : {
      "spec/Animal" : {
        "name" : "refine_to_Animal",
        "expr" : "{$type: spec/Animal, species: \"Canine\"}"
      }
    }
  },
  "spec/Cat" : {
    "fields" : {
      "lives" : "Integer"
    },
    "refines-to" : {
      "spec/Animal" : {
        "name" : "refine_to_Animal",
        "expr" : "{$type: spec/Animal, species: \"Feline\"}"
      }
    }
  }
}
```

The animal spec cannot be directly used to make a pet instance.

```java
{$type: spec/Pet, animal: {$type: spec/Animal, species: "Equine"}, name: "Silver"}


//-- result --
[:throws "h-err/instance-threw 0-0 : Instance threw error: \"Instance cannot contain abstract value\""]
```

Instead, to construct a pet instance, a dog or cat instance must be used for the animal field.

```java
{$type: spec/Pet, animal: {$type: spec/Dog, breed: "Golden Retriever"}, name: "Rex"}
```

```java
{$type: spec/Pet, animal: {$type: spec/Cat, lives: 9}, name: "Tom"}
```

In order to access the value in the animal field as an animal object, the value must be refined to its abstract type.

```java
({ pet = {$type: spec/Pet, animal: {$type: spec/Dog, breed: "Golden Retriever"}, name: "Rex"}; pet.animal.refineTo( spec/Animal ).species })


//-- result --
"Canine"
```

```java
({ pet = {$type: spec/Pet, animal: {$type: spec/Cat, lives: 9}, name: "Tom"}; pet.animal.refineTo( spec/Animal ).species })


//-- result --
"Feline"
```

Even if we know the concrete type of the field value we cannot access it as that type. Instead the field must be refined to its abstract type before being accessed.

```java
({ pet = {$type: spec/Pet, animal: {$type: spec/Dog, breed: "Golden Retriever"}, name: "Rex"}; pet.animal.breed })


//-- result --
[:throws "h-err/invalid-lookup-target 0-0 : Lookup target must be an instance of known type or non-empty vector"]
```

### Reference

#### Basic elements:

[`instance`](../halite_basic-syntax-reference-j.md#instance), [`spec-map`](../../halite_spec-syntax-reference.md)

#### Operator reference:

* [`ACCESSOR`](../halite_full-reference-j.md#ACCESSOR)
* [`ACCESSOR-CHAIN`](../halite_full-reference-j.md#ACCESSOR-CHAIN)
* [`refineTo`](../halite_full-reference-j.md#refineTo)


#### How Tos:

* [compose-instances](../how-to/halite_compose-instances-j.md)
* [string-enum](../how-to/halite_string-enum-j.md)


