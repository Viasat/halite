## Converting Instances Between Specs

How to convert an instance from one spec type to another.

An expression can convert an instance of one type to the instance of another type. Assume there are these two specs.

```java
{
  "spec/A$v1" : {
    "spec-vars" : {
      "b" : "Integer"
    }
  },
  "spec/X$v1" : {
    "spec-vars" : {
      "y" : "Integer"
    }
  }
}
```

The following expression converts an instance of the first spec into an instance of the second.

```java
({ a = {$type: spec/A$v1, b: 1}; {$type: spec/X$v1, y: a.b} })


//-- result --
{$type: spec/X$v1, y: 1}
```

This work, but the language has a built-in idea of 'refinements' that allow such conversion functions to be expressed in a way that the system understands.

```java
{
  "spec/A$v2" : {
    "spec-vars" : {
      "b" : "Integer"
    },
    "refines-to" : {
      "spec/X$v2" : {
        "name" : "refine_to_X",
        "expr" : {
          "$type" : "spec/X$v2",
          "y" : "b"
        }
      }
    }
  },
  "spec/X$v2" : {
    "spec-vars" : {
      "y" : "Integer"
    }
  }
}
```

The refinement can be invoked as follows:

```java
({ a = {$type: spec/A$v2, b: 1}; a.refineTo( spec/X$v2 ) })


//-- result --
{$type: spec/X$v2, y: 1}
```

The refinements are automatically, transitively applied to produce an instance of the target spec.

```java
{
  "spec/A$v3" : {
    "spec-vars" : {
      "b" : "Integer"
    },
    "refines-to" : {
      "spec/P$v3" : {
        "name" : "refine_to_P",
        "expr" : {
          "$type" : "spec/P$v3",
          "q" : "b"
        }
      }
    }
  },
  "spec/P$v3" : {
    "spec-vars" : {
      "q" : "Integer"
    },
    "refines-to" : {
      "spec/X$v3" : {
        "name" : "refine_to_X",
        "expr" : {
          "$type" : "spec/X$v3",
          "y" : "q"
        }
      }
    }
  },
  "spec/X$v3" : {
    "spec-vars" : {
      "y" : "Integer"
    }
  }
}
```

The chain of refinements is invoked by simply refining the instance to the final target spec.

```java
({ a = {$type: spec/A$v3, b: 1}; a.refineTo( spec/X$v3 ) })


//-- result --
{$type: spec/X$v3, y: 1}
```

The refinements expressions are arbitrary expressions over the fields of the instance or constant values.

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

