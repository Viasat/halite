## Spec Variables

How to model data fields in specifications.

It is possible to define a spec that does not have any fields.

```clojure
#:spec{:Dog$v1 {}}
```

Instances of this spec could be created as:

```clojure
{:$type :spec/Dog$v1}
```

It is more interesting to define data fields on specs that define the structure of instances of the spec

```clojure
#:spec{:Dog$v2 {:spec-vars {:age "Integer"}}}
```

This spec can be instantiated as:

```clojure
{:$type :spec/Dog$v2, :age 3}
```

A spec can have multiple fields

```clojure
#:spec{:Dog$v3 {:spec-vars {:name "String", :age "Integer"}}}
```

Now instances are created with multiple fields

```clojure
{:$type :spec/Dog$v3, :name "Rex", :age 3}
```

Specs can include collections

```clojure
#:spec{:Dog$v4
       {:spec-vars
        {:name "String", :age "Integer", :colors ["String"]}}}
```

```clojure
{:$type :spec/Dog$v4, :name "Rex", :age 3, :colors ["brown" "white"]}
```

Specs can reference other specs via composition

```clojure
#:spec{:Owner {:spec-vars {:owner_name "String"}},
       :Dog$v5
       {:spec-vars
        {:name "String",
         :age "Integer",
         :colors ["String"],
         :owner :spec/Owner}}}
```

```clojure
{:$type :spec/Dog$v5, :name "Rex", :age 3, :colors ["brown" "white"], :owner {:$type :spec/Owner, :owner_name "Sam"}}
```

#### Basic elements:

[`instance`](halite-basic-syntax-reference.md#instance), [`vector`](halite-basic-syntax-reference.md#vector)

