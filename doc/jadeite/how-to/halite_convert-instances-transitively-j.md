<!---
  This markdown file was generated. Do not edit.
  -->

## Converting instances between specs transitively

How to convert an instance from one spec type to another through an intermediate spec.

Refinements are automatically, transitively applied to produce an instance of the target spec.

```java
{
  "spec/A$v3" : {
    "fields" : {
      "b" : "Integer"
    },
    "refines-to" : {
      "spec/P$v3" : {
        "name" : "refine_to_P",
        "expr" : "{$type: spec/P$v3, q: b}"
      }
    }
  },
  "spec/P$v3" : {
    "fields" : {
      "q" : "Integer"
    },
    "refines-to" : {
      "spec/X$v3" : {
        "name" : "refine_to_X",
        "expr" : "{$type: spec/X$v3, y: q}"
      }
    }
  },
  "spec/X$v3" : {
    "fields" : {
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

### Reference

#### Basic elements:

[`instance`](../halite_basic-syntax-reference-j.md#instance), [`spec-map`](../../halite_spec-syntax-reference.md)

#### Operator reference:

* [`refineTo`](../halite_full-reference-j.md#refineTo)


#### How Tos:

* [convert-instances](../how-to/halite_convert-instances-j.md)


