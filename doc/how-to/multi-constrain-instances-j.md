## Defining Multiple Constraints on Instance Values

How to define multiple constraints in a spec

Multiple constraints can be defined on a spec. Each constraint must have a unique name within the context of a spec.

```java
{
  "spec/A$v1" : {
    "spec-vars" : {
      "b" : "Integer",
      "c" : "Integer"
    },
    "constraints" : [ [ "constrain_b", [ ">", "b", 100 ] ], [ "constrain_c", [ "<", "c", 20 ] ] ]
  }
}
```

An instance must satisfy all of the constraints to be valid

```java
{$type: spec/A$v1, b: 101, c: 19}


//-- result --
{$type: spec/A$v1, b: 101, c: 19}
```

Violating any of the constraints makes the instance invalid

```java
{$type: spec/A$v1, b: 100, c: 19}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'spec/A$v1', violates constraints constrain_b"]
```

```java
{$type: spec/A$v1, b: 101, c: 20}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'spec/A$v1', violates constraints constrain_c"]
```

Mutliple constraints can refer to the same variables.

```java
{
  "spec/A$v2" : {
    "spec-vars" : {
      "b" : "Integer"
    },
    "constraints" : [ [ "constrain_b", [ ">", "b", 100 ] ], [ "constrain_b2", [ "<", "b", 110 ] ] ]
  }
}
```

```java
{$type: spec/A$v2, b: 105}


//-- result --
{$type: spec/A$v2, b: 105}
```

```java
{$type: spec/A$v2, b: 120}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'spec/A$v2', violates constraints constrain_b2"]
```

#### Basic elements:

[`instance`](jadeite-basic-syntax-reference.md#instance)

