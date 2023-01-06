<!---
  This markdown file was generated. Do not edit.
  -->

## Use an instance as a predicate

Consider you need to evaluate an expression as a predicate, to determine if some values relate to each other properly.

The following specification uses a constraint to capture a predicate that checks whether a value is equal to the sum of two other values.

```java
{
  "spec/Sum" : {
    "fields" : {
      "x" : "Integer",
      "y" : "Integer",
      "sum" : "Integer"
    },
    "constraints" : [ "{expr: (sum == (x + y)), name: \"constrain_sum\"}" ]
  }
}
```

The following will attempt to instantiate an instance of the spec and indicate whether the instance satisfied the constraint. In this case it does.

```java
(valid? {$type: spec/Sum, sum: 5, x: 2, y: 3})


//-- result --
true
```

Here is another example, in which the constraint is not met.

```java
(valid? {$type: spec/Sum, sum: 6, x: 2, y: 3})


//-- result --
false
```

Note, for the case where the constraint is not met, a naked attempt to instantiate the instance will produce a runtime error.

```java
{$type: spec/Sum, sum: 6, x: 2, y: 3}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'spec/Sum', violates constraints \"spec/Sum/constrain_sum\""]
```

### Reference

#### Basic elements:

[`instance`](../halite_basic-syntax-reference-j.md#instance), [`spec-map`](../../halite_spec-syntax-reference.md)

#### Operator reference:

* [`valid?`](../halite_full-reference-j.md#valid_Q)


