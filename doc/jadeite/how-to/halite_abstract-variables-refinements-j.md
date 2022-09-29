<!---
  This markdown file was generated. Do not edit.
  -->

## Variables with abstract types used in refinements

How to use variables which are defined to be the type of abstract specs in refinements.

The way to use an abstract field value as the result value in a refinement is to refine it to its abstract type. This is necessary because the type of a refinement expression must exactly match the declared type of the refinement.

```java
{
  "spec/Animal" : {
    "abstract?" : true,
    "spec-vars" : {
      "species" : "String"
    }
  },
  "spec/Pet$v1" : {
    "spec-vars" : {
      "animal" : "spec/Animal",
      "name" : "String"
    },
    "refines-to" : {
      "spec/Animal" : {
        "name" : "refine_to_Animal",
        "expr" : "animal.refineTo( spec/Animal )"
      }
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

```java
({ pet = {$type: spec/Pet$v1, animal: {$type: spec/Dog, breed: "Golden Retriever"}, name: "Rex"}; pet.refineTo( spec/Animal ) })


//-- result --
{$type: spec/Animal, species: "Canine"}
```

```java
({ pet = {$type: spec/Pet$v1, animal: {$type: spec/Cat, lives: 9}, name: "Tom"}; pet.refineTo( spec/Animal ) })


//-- result --
{$type: spec/Animal, species: "Feline"}
```

Even if we happen to know the concrete type of an abstract field is of the right type for a refinement it cannot be used.

```java
{
  "spec/Animal" : {
    "abstract?" : true,
    "spec-vars" : {
      "species" : "String"
    }
  },
  "spec/Pet$v2" : {
    "spec-vars" : {
      "animal" : "spec/Animal",
      "name" : "String"
    },
    "refines-to" : {
      "spec/Dog" : {
        "name" : "refine_to_Dog",
        "expr" : "animal"
      }
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
  }
}
```

In this example, even though we know the value in the animal field is a dog, the attempted refinement cannot be executed.

```java
({ pet = {$type: spec/Pet$v2, animal: {$type: spec/Dog, breed: "Golden Retriever"}, name: "Rex"}; pet.refineTo( spec/Dog ) })


//-- result --
[:throws "h-err/invalid-refinement-expression 0-0 : Refinement expression, 'animal', is not of the expected type"]
```

If instead, we attempt to define the refinement of type animal, but still try to use the un-refined field value as the result of the refinement, it still fails.

```java
{
  "spec/Animal" : {
    "abstract?" : true,
    "spec-vars" : {
      "species" : "String"
    }
  },
  "spec/Pet$v3" : {
    "spec-vars" : {
      "animal" : "spec/Animal",
      "name" : "String"
    },
    "refines-to" : {
      "spec/Animal" : {
        "name" : "refine_to_Animal",
        "expr" : "animal"
      }
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

The refinement fails in this example, because the value being produced by the refinement expression is a dog, when it must be an animal to match the declared type of the refinement.

```java
({ pet = {$type: spec/Pet$v3, animal: {$type: spec/Dog, breed: "Golden Retriever"}, name: "Rex"}; pet.refineTo( spec/Animal ) })


//-- result --
[:throws "h-err/invalid-refinement-expression 0-0 : Refinement expression, 'animal', is not of the expected type"]
```

In fact, there is no way to make this refinement work because the animal field cannot be constructed with an abstract instance.

```java
({ pet = {$type: spec/Pet$v3, animal: {$type: spec/Animal, species: "Equine"}, name: "Rex"}; pet.refineTo( spec/Animal ) })


//-- result --
[:throws "h-err/no-abstract 0-0 : Instance cannot contain abstract value"]
```

### Reference

#### Basic elements:

[`instance`](../halite_basic-syntax-reference-j.md#instance)

#### Operator reference:

* [`refineTo`](../halite_full-reference-j.md#refineTo)


#### How Tos:

* [compose-instances](../how-to/halite_compose-instances-j.md)
* [string-enum](../how-to/halite_string-enum-j.md)


