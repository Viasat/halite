<!---
  This markdown file was generated. Do not edit.
  -->

## Defining multiple constraints on instance values

How to define multiple constraints in a spec

Multiple constraints can be defined on a spec. Each constraint must have a unique name within the context of a spec.

```java
{
  "spec/A$v1" : {
    "spec-vars" : {
      "b" : "Integer",
      "c" : "Integer"
    },
    "constraints" : [ "{expr: (c < 20), name: \"constrain_c\"}", "{expr: (b > 100), name: \"constrain_b\"}" ]
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
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'spec/A$v1', violates constraints \"spec/A$v1/constrain_b\""]
```

```java
{$type: spec/A$v1, b: 101, c: 20}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'spec/A$v1', violates constraints \"spec/A$v1/constrain_c\""]
```

Mutliple constraints can refer to the same variables.

```java
{
  "spec/A$v2" : {
    "spec-vars" : {
      "b" : "Integer"
    },
    "constraints" : [ "{expr: (b > 100), name: \"constrain_b\"}", "{expr: (b < 110), name: \"constrain_b2\"}" ]
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
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'spec/A$v2', violates constraints \"spec/A$v2/constrain_b2\""]
```

In general, constraint extpressions can be combined with a logical 'and'. This has the same meaning because all constraints are effectively 'anded' together to produce a single logical predicate to assess whether an instance is valid. So, decomposing constraints into separate constraints is largely a matter of organizing and naming the checks to suit the modelling exercise.

```java
{
  "spec/A$v3" : {
    "spec-vars" : {
      "b" : "Integer"
    },
    "constraints" : [ "{expr: ((b > 100) && (b < 110)), name: \"constrain_b\"}" ]
  }
}
```

```java
{$type: spec/A$v3, b: 105}


//-- result --
{$type: spec/A$v3, b: 105}
```

```java
{$type: spec/A$v3, b: 120}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'spec/A$v3', violates constraints \"spec/A$v3/constrain_b\""]
```

### Reference

#### Basic elements:

[`instance`](../halite_basic-syntax-reference-j.md#instance), [`spec-map`](../../halite_spec-syntax-reference.md)

#### How Tos:

* [constrain-instances](../how-to/halite_constrain-instances-j.md)


