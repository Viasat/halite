<!---
  This markdown file was generated. Do not edit.
  -->

## Compose instances

How to make specs which are the composition of other specs and how to make instances of those specs.

A spec variable can be of the type of another spec

```clojure
{:spec/A$v1 {:fields {:b :spec/B$v1}},
 :spec/B$v1 {:fields {:c :Integer}}}
```

Composite instances are created by nesting the instances at construction time.

```clojure
{:$type :spec/A$v1,
 :b {:$type :spec/B$v1,
     :c 1}}
```

### Reference

#### Basic elements:

[`instance`](../halite_basic-syntax-reference.md#instance), [`spec-map`](../../halite_spec-syntax-reference.md)

#### How Tos:

* [spec-fields](../how-to/halite_spec-fields.md)


