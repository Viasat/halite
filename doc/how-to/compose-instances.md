## Compose Instances

How to make specs which are the composition of other specs and how to make instances of those specs.

A spec variable can be of the type of another spec

```clojure
#:spec{:A$v1 {:spec-vars {:b :spec/B$v1}},
       :B$v1 {:spec-vars {:c "Integer"}}}
```

Composite instances are created by nesting the instances at construction time.

```clojure
{:$type :spec/A$v1, :b {:$type :spec/B$v1, :c 1}}
```

#### Basic elements:

[`instance`](halite-basic-syntax-reference.md#instance)

