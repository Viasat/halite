<!---
  This markdown file was generated. Do not edit.
  -->

## Converting instances between specs

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
        "expr" : "{$type: spec/X$v2, y: b}"
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

### Reference

#### Basic elements:

[`instance`](../halite_basic-syntax-reference-j.md#instance)

#### Operator reference:

* [`refineTo`](../halite_full-reference-j.md#refineTo)


#### How Tos:

* [arbitrary-expression-refinements](../how-to/halite_arbitrary-expression-refinements-j.md)
* [convert-instances-transitively](../how-to/halite_convert-instances-transitively-j.md)
* [optionally-convert-instances](../how-to/halite_optionally-convert-instances-j.md)


#### Tutorials:

* [grocery](../tutorial/halite_grocery-j.md)


