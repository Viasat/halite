<!---
  This markdown file was generated. Do not edit.
  -->

## Compose instances

How to make specs which are the composition of other specs and how to make instances of those specs.

A spec variable can be of the type of another spec

```java
{
  "spec/A$v1" : {
    "spec-vars" : {
      "b" : "spec/B$v1"
    }
  },
  "spec/B$v1" : {
    "spec-vars" : {
      "c" : "Integer"
    }
  }
}
```

Composite instances are created by nesting the instances at construction time.

```java
{$type: spec/A$v1, b: {$type: spec/B$v1, c: 1}}
```

### Reference

#### Basic elements:

[`instance`](../halite_basic-syntax-reference-j.md#instance)

#### How Tos:

* [spec-variables](../how-to/halite_spec-variables-j.md)


