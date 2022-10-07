<!---
  This markdown file was generated. Do not edit.
  -->

## Recursive instances

Specs can be defined to be recursive.

```clojure
{:spec/Cell {:spec-vars {:next [:Maybe :spec/Cell],
                         :value "Integer"}}}
```

```clojure
{:$type :spec/Cell,
 :value 10}
```

```clojure
{:$type :spec/Cell,
 :next {:$type :spec/Cell,
        :value 11},
 :value 10}
```

