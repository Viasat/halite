<!---
  This markdown file was generated. Do not edit.
  -->

## Use an instance as a predicate

Consider you need to evaluate an expression as a predicate, to determine if some values relate to each other properly.

The following specification uses a constraint to capture a predicate that checks whether a value is equal to the sum of two other values.

```clojure
{:spec/Sum {:fields {:sum :Integer,
                     :x :Integer,
                     :y :Integer},
            :constraints #{'{:name "constrain_sum",
                             :expr (= sum (+ x y))}}}}
```

The following will attempt to instantiate an instance of the spec and indicate whether the instance satisfied the constraint. In this case it does.

```clojure
(valid? {:$type :spec/Sum,
         :x 2,
         :y 3,
         :sum 5})


;-- result --
true
```

Here is another example, in which the constraint is not met.

```clojure
(valid? {:$type :spec/Sum,
         :x 2,
         :y 3,
         :sum 6})


;-- result --
false
```

Note, for the case where the constraint is not met, a naked attempt to instantiate the instance will produce a runtime error.

```clojure
{:$type :spec/Sum,
 :sum 6,
 :x 2,
 :y 3}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'spec/Sum', violates constraints \"spec/Sum/constrain_sum\""
 :h-err/invalid-instance]
```

### Reference

#### Basic elements:

[`instance`](../halite_basic-syntax-reference.md#instance), [`spec-map`](../../halite_spec-syntax-reference.md)

#### Operator reference:

* [`valid?`](../halite_full-reference.md#valid_Q)


