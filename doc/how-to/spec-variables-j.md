<!---
  This markdown file was generated. Do not edit.
  -->

## Spec variables

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
    "spec-vars" : {
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
    "spec-vars" : {
      "name" : "String",
      "age" : "Integer",
      "colors" : [ "String" ]
    }
  }
}
```

```java
{$type: spec/Dog$v4, age: 3, colors: ["brown", "white"], name: "Rex"}
```

#### Basic elements:

[`instance`](../jadeite-basic-syntax-reference.md#instance), [`vector`](../jadeite-basic-syntax-reference.md#vector)

#### See also:

* [compose-instances](compose-instances.md)


