Spec Variables

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
  "spec/Dog$v3" : {
    "spec-vars" : {
      "name" : "String",
      "age" : "Integer"
    }
  }
}
```

Now instances are created with multiple fields

```java
{$type: spec/Dog$v3, age: 3, name: "Rex"}
```

Specs can include collections

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

Specs can reference other specs via composition

```java
{
  "spec/Owner" : {
    "spec-vars" : {
      "owner_name" : "String"
    }
  },
  "spec/Dog$v5" : {
    "spec-vars" : {
      "name" : "String",
      "age" : "Integer",
      "colors" : [ "String" ],
      "owner" : "spec/Owner"
    }
  }
}
```

```java
{$type: spec/Dog$v5, age: 3, colors: ["brown", "white"], name: "Rex", owner: {$type: spec/Owner, 'owner_name': "Sam"}}
```

#### Basic elements:

[`instance`](jadeite-basic-syntax-reference.md#instance), [`vector`](jadeite-basic-syntax-reference.md#vector)

