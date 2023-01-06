<!---
  This markdown file was generated. Do not edit.
  -->

## Spec fields

How to model data fields in specifications.

It is possible to define a spec that does not have any fields.

```java
{
  "spec/Dog$v1" : { }
}
```

Instances of this spec could be created as:

```java
{$type: spec/Dog$v1}
```

It is more interesting to define data fields on specs that define the structure of instances of the spec

```java
{
  "spec/Dog$v2" : {
    "fields" : {
      "age" : "Integer"
    }
  }
}
```

This spec can be instantiated as:

```java
{$type: spec/Dog$v2, age: 3}
```

A spec can have multiple fields

```java
{
  "spec/Dog$v4" : {
    "fields" : {
      "name" : "String",
      "age" : "Integer",
      "colors" : [ "Vec", "String" ]
    }
  }
}
```

```java
{$type: spec/Dog$v4, age: 3, colors: ["brown", "white"], name: "Rex"}
```

### Reference

#### Basic elements:

[`instance`](../halite_basic-syntax-reference-j.md#instance), [`spec-map`](../../halite_spec-syntax-reference.md), [`vector`](../halite_basic-syntax-reference-j.md#vector)

#### How Tos:

* [compose-instances](../how-to/halite_compose-instances-j.md)
* [string-enum](../how-to/halite_string-enum-j.md)


