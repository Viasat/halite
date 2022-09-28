<!---
  This markdown file was generated. Do not edit.
  -->

## Use an instance as a function to compute a value

Consider there is some logic that needs to be reused in multiple contexts. How to package it up so that it can be reused?

It is a bit convoluted, but consider the following specs.

```java
{
  "spec/Add" : {
    "spec-vars" : {
      "x" : "Integer",
      "y" : "Integer"
    },
    "refines-to" : {
      "spec/IntegerResult" : {
        "name" : "refine_to_result",
        "expr" : "{$type: spec/IntegerResult, result: (x + y)}"
      }
    }
  },
  "spec/IntegerResult" : {
    "spec-vars" : {
      "result" : "Integer"
    }
  }
}
```

This makes a spec which when instantiated is allows a refinement expression to be invoked as a sort of function call.

```java
({ x = 2; y = 3; result = {$type: spec/Add, x: x, y: y}.refineTo( spec/IntegerResult ).result; result })


//-- result --
5
```

This is not necessarily recommended, but it is possible.

### Reference

#### Basic elements:

[`instance`](../halite_basic-syntax-reference-j.md#instance)

#### Operator reference:

* [`refineTo`](halite_full-reference-j.md#refineTo)


