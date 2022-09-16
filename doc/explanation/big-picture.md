<!---
  This markdown file was generated. Do not edit.
  -->

## Specs are about modeling things

Specs are a general mechanism for modelling whatever is of interest.

Writing a spec is carving out a subset out of the universe of all possible values and giving them a name.

```clojure
{:spec/Ball {:spec-vars {:color "String"}}}
```

The spec gives a name to specific instances, such as the following.

```clojure
{:$type :spec/Ball,
 :color "red"}
```

```clojure
{:$type :spec/Ball,
 :color "blue"}
```

