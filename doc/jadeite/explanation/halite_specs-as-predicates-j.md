<!---
  This markdown file was generated. Do not edit.
  -->

## Considering a spec as a predicate

A spec can be considered a giant predicate which when applied to a value returns 'true' if the value is a valid instance and 'false' or a runtime error otherwise.

The type indications of spec variables can be considered as predicates.

```java
{
  "spec/X$v1" : {
    "spec-vars" : {
      "x" : "String"
    }
  }
}
```

If an instance is made with the correct type for a field value, then the predicate produces 'true'.

```java
{$type: spec/X$v1, x: "hi"}


//-- result --
{$type: spec/X$v1, x: "hi"}
```

If an instance is made without the correct type for a field value, then the predicate produces an error.

```java
{$type: spec/X$v1, x: 25}


//-- result --
[:throws "h-err/field-value-of-wrong-type 0-0 : Value of 'x' has wrong type"]
```

If a specification defines multiple spec vars then the result is a logical conjunct.

```java
{
  "spec/X$v2" : {
    "spec-vars" : {
      "x" : "String",
      "y" : "Integer"
    }
  }
}
```

All of the fields must be of the correct type. This is a conjunct: the field x must be a string and the field y must be an integer

```java
{$type: spec/X$v2, x: "hi", y: 100}


//-- result --
{$type: spec/X$v2, x: "hi", y: 100}
```

Violating either conditions causes the overall value to produce an error.

```java
{$type: spec/X$v2, x: "hi", y: "bye"}


//-- result --
[:throws "h-err/field-value-of-wrong-type 0-0 : Value of 'y' has wrong type"]
```

Violating either conditions causes the overall value to produce an error.

```java
{$type: spec/X$v2, x: 5, y: 100}


//-- result --
[:throws "h-err/field-value-of-wrong-type 0-0 : Value of 'x' has wrong type"]
```

Similarly, each constraint by itself is a predicate and is combined in a conjunction with all of the spec variable type checks.

```java
{
  "spec/X$v3" : {
    "spec-vars" : {
      "x" : "String",
      "y" : "Integer"
    },
    "constraints" : [ "{expr: (y > 0), name: \"valid_y\"}" ]
  }
}
```

```java
{$type: spec/X$v3, x: "hi", y: 100}


//-- result --
{$type: spec/X$v3, x: "hi", y: 100}
```

So if any of the types are wrong or if the constraint is violated then an error is produced.

```java
{$type: spec/X$v3, x: "hi", y: -1}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'spec/X$v3', violates constraints valid_y"]
```

If there are multiple constraints, they are all logically combined into a single conjunction for the spec.

```java
{
  "spec/X$v5" : {
    "spec-vars" : {
      "x" : "String",
      "y" : "Integer"
    },
    "constraints" : [ "{expr: (y > 0), name: \"valid_y\"}", "{expr: #{\"bye\", \"hi\"}.contains?(x), name: \"valid_x\"}" ]
  }
}
```

```java
{$type: spec/X$v5, x: "hi", y: 100}


//-- result --
{$type: spec/X$v5, x: "hi", y: 100}
```

Again, violating any one constraint causes an error to be produced.

```java
{$type: spec/X$v5, x: "hello", y: 100}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'spec/X$v5', violates constraints valid_x"]
```

Finally, the refinements can also bring in additional constraints which are combined into the overall conjunction for the spec.

```java
{
  "spec/A" : {
    "spec-vars" : {
      "b" : "Integer"
    },
    "constraints" : [ "{expr: (b < 10), name: \"valid_b\"}" ]
  },
  "spec/X$v6" : {
    "spec-vars" : {
      "x" : "String",
      "y" : "Integer"
    },
    "constraints" : [ "{expr: (y > 0), name: \"valid_y\"}", "{expr: #{\"bye\", \"hi\"}.contains?(x), name: \"valid_x\"}" ],
    "refines-to" : {
      "spec/A" : {
        "name" : "refine_to_A",
        "expr" : "{$type: spec/A, b: y}"
      }
    }
  }
}
```

```java
{$type: spec/X$v6, x: "hi", y: 9}


//-- result --
{$type: spec/X$v6, x: "hi", y: 9}
```

If one of the constraints implied by a refinement is violated, then an error is produced.

```java
{$type: spec/X$v6, x: "hi", y: 12}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'spec/A', violates constraints valid_b"]
```

Implications of each additional refinement are combined into the single conujunction for this spec.

```java
{
  "spec/A" : {
    "spec-vars" : {
      "b" : "Integer"
    },
    "constraints" : [ "{expr: (b < 10), name: \"valid_b\"}" ]
  },
  "spec/P" : {
    "spec-vars" : {
      "q" : "String"
    },
    "constraints" : [ "{expr: (q == \"hi\"), name: \"valid_q\"}" ]
  },
  "spec/X$v7" : {
    "spec-vars" : {
      "x" : "String",
      "y" : "Integer"
    },
    "constraints" : [ "{expr: (y > 0), name: \"valid_y\"}", "{expr: #{\"bye\", \"hi\"}.contains?(x), name: \"valid_x\"}" ],
    "refines-to" : {
      "spec/A" : {
        "name" : "refine_to_A",
        "expr" : "{$type: spec/A, b: y}"
      },
      "spec/P" : {
        "name" : "refine_to_P",
        "expr" : "{$type: spec/P, q: x}"
      }
    }
  }
}
```

```java
{$type: spec/X$v7, x: "hi", y: 9}


//-- result --
{$type: spec/X$v7, x: "hi", y: 9}
```

Violate one of the implied refinement constraints.

```java
{$type: spec/X$v7, x: "bye", y: 9}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'spec/P', violates constraints valid_q"]
```

Violation of constraints can be detected by using the 'valid?' operator. This works for constraints in the spec explicitly as well as constraints implied via refinements.

```java
(valid? {$type: spec/X$v7, x: "hi", y: 9})


//-- result --
true
```

```java
(valid? {$type: spec/X$v7, x: "hola", y: 9})


//-- result --
false
```

```java
(valid? {$type: spec/X$v7, x: "bye", y: 9})


//-- result --
false
```

However, the 'valid?' operator cannot be used to handle cases that would violate the required types of specs variables.

```java
(valid? {$type: spec/X$v7, x: 1, y: 9})


//-- result --
[:throws "h-err/field-value-of-wrong-type 0-0 : Value of 'x' has wrong type"]
```

### Reference

#### Basic elements:

[`instance`](../halite_basic-syntax-reference-j.md#instance), [`spec-map`](../../halite_spec-syntax-reference.md)

#### Operator reference:

* [`valid?`](../halite_full-reference-j.md#valid_Q)


