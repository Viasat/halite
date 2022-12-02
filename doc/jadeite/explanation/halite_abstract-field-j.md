<!---
  This markdown file was generated. Do not edit.
  -->

## What is an abstract field?

If a field in a spec has a type of an abstract spec, then the field can hold values that refine to that abstract spec.

Say we have an abstract car.

```java
{
  "spec/Car" : {
    "abstract?" : true,
    "fields" : {
      "make" : "String",
      "year" : "Integer"
    }
  },
  "spec/Ford" : {
    "abstract?" : false,
    "fields" : {
      "year" : "Integer"
    },
    "refines-to" : {
      "spec/Car" : {
        "name" : "as_car",
        "expr" : "{$type: spec/Car, make: \"Ford\", year: year}"
      }
    }
  },
  "spec/Chevy" : {
    "abstract?" : false,
    "fields" : {
      "year" : "Integer"
    },
    "refines-to" : {
      "spec/Car" : {
        "name" : "as_car",
        "expr" : "{$type: spec/Car, make: \"Chevy\", year: year}"
      }
    }
  },
  "spec/Garage" : {
    "abstract?" : false,
    "fields" : {
      "car" : "spec/Car"
    }
  }
}
```

Since the garage has a field of an abstract type, it can hold an instance of either Ford or Chevy.

```java
{$type: spec/Garage, car: {$type: spec/Ford, year: 2020}}
```

```java
{$type: spec/Garage, car: {$type: spec/Chevy, year: 2021}}
```

However, it cannot hold a direct instance of car, because an abstract instance cannot be used in the construction of an instance.

```java
{$type: spec/Garage, car: {$type: spec/Car, make: "Honda", year: 2022}}


//-- result --
[:throws "h-err/instance-threw 0-0 : Instance threw error: \"Instance cannot contain abstract value\""]
```

In an interesting way, declaring a field to be of the type of an abstract instance, means it can hold any instance except for an instance of that type.

### Reference

#### Basic elements:

[`instance`](../halite_basic-syntax-reference-j.md#instance), [`spec-map`](../../halite_spec-syntax-reference.md)

