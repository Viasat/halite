<!---
  This markdown file was generated. Do not edit.
  -->

## Defining constraints on instance values

How to constrain the possible values for instance fields

As a starting point specs specify the fields that make up instances.

```java
{
  "spec/A$v1" : {
    "fields" : {
      "b" : "Integer"
    }
  }
}
```

This indicates that 'b' must be an integer, but it doesn't indicate what valid values are. The following spec includes a constraint that requires b to be greater than 100.

```java
{
  "spec/A$v2" : {
    "fields" : {
      "b" : "Integer"
    },
    "constraints" : [ "{expr: (b > 100), name: \"constrain_b\"}" ]
  }
}
```

An attempt to make an instance that satisfies this constraint is successful

```java
{$type: spec/A$v2, b: 200}


//-- result --
{$type: spec/A$v2, b: 200}
```

However, an attempt to make an instance that violates this constraint fails.

```java
{$type: spec/A$v2, b: 50}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'spec/A$v2', violates constraints \"spec/A$v2/constrain_b\""]
```

Constraints can be arbitrary expressions that refer to multiple fields.

```java
{
  "spec/A$v3" : {
    "fields" : {
      "b" : "Integer",
      "c" : "Integer"
    },
    "constraints" : [ "{expr: ((b + c) < 10), name: \"constrain_b_c\"}" ]
  }
}
```

In this example, the sum of 'a' and 'b' must be less than 10

```java
{$type: spec/A$v3, b: 2, c: 3}


//-- result --
{$type: spec/A$v3, b: 2, c: 3}
```

```java
{$type: spec/A$v3, b: 6, c: 7}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'spec/A$v3', violates constraints \"spec/A$v3/constrain_b_c\""]
```

### Reference

#### Basic elements:

[`instance`](../halite_basic-syntax-reference-j.md#instance), [`spec-map`](../../halite_spec-syntax-reference.md)

#### How Tos:

* [multi-constrain-instances](../how-to/halite_multi-constrain-instances-j.md)


#### Tutorials:

* [sudoku](../tutorial/halite_sudoku-j.md)


