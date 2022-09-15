## Arbitrary Expression in Refinements

How to write arbitrary expressions to convert instances.

Refinement expressions can be arbitrary expressions over the fields of the instance or constant values.

```java
{
  "spec/A$v4" : {
    "spec-vars" : {
      "b" : "Integer",
      "c" : "Integer",
      "d" : "String"
    },
    "refines-to" : {
      "spec/X$v4" : {
        "name" : "refine_to_X",
        "expr" : {
          "$type" : "spec/X$v4",
          "x" : [ "+", "b", "c" ],
          "y" : 12,
          "z" : [ "if", [ "=", "medium", "d" ], 5, 10 ]
        }
      }
    }
  },
  "spec/X$v4" : {
    "spec-vars" : {
      "x" : "Integer",
      "y" : "Integer",
      "z" : "Integer"
    }
  }
}
```

```java
({ a = {$type: spec/A$v4, b: 1, c: 2, d: "large"}; a.refineTo( spec/X$v4 ) })


//-- result --
{$type: spec/X$v4, x: 3, y: 12, z: 10}
```

#### Basic elements:

[`instance`](jadeite-basic-syntax-reference.md#instance)

