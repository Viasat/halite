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
    "spec-vars" : {
      "species" : "String"
    }
  },
  "spec/Pet" : {
    "spec-vars" : {
      "animal" : "spec/Animal",
      "name" : "String"
    }
  },
  "spec/Dog" : {
    "spec-vars" : {
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
    "spec-vars" : {
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
[:throws "h-err/no-abstract 0-0 : Instance cannot contain abstract value"]
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

[`instance`](../jadeite-basic-syntax-reference.md#instance)

#### Operator reference:

* [`ACCESSOR`](../jadeite-full-reference.md#ACCESSOR)
* [`ACCESSOR-CHAIN`](../jadeite-full-reference.md#ACCESSOR-CHAIN)
* [`refineTo`](../jadeite-full-reference.md#refineTo)


#### How tos:

* [compose-instances](../how-to/compose-instances.md)
* [string-enum](../how-to/string-enum.md)


