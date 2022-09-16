<!---
  This markdown file was generated. Do not edit.
  -->

## Converting instances between specs transitively

How to convert an instance from one spec type to another through an intermediate spec.

Refinements are automatically, transitively applied to produce an instance of the target spec.

```java
{
  "spec/A$v3" : {
    "spec-vars" : {
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
    "spec-vars" : {
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

#### Basic elements:

[`instance`](../jadeite-basic-syntax-reference.md#instance)

#### Operator reference:

* [`refineTo`](../jadeite-full-reference.md#refineTo)


#### See also:

* [convert-instances](convert-instances.md)


