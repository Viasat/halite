<!---
  This markdown file was generated. Do not edit.
  -->

## The pseduo-value 'unset' is handled specially

The 'unset' value cannot be used in general and there are specific facilities for dealing with them when they are produced by an expression.

Some languages have the notion of 'null' that appears throughout. The idea of 'null', is referred to as 'unset' in this language. The language attempts to keep contain the 'unset' value and prevent it from infecting code that should not need to deal with it. If it does need to be referred to, it is as '$no-value'.

```clojure
$no-value


;-- result --
:Unset
```

But, ideally users will not use '$no-value' explicitly.

An 'unset' value is expected to come into play via an optional field.

```clojure
{:spec/A {:spec-vars {:b [:Maybe "Integer"]}}}
```

```clojure
(get {:$type :spec/A} :b)


;-- result --
:Unset
```

The 'unset' value cannot be used in most operations.

```clojure
(+ (get {:$type :spec/A} :b) 2)


;-- result --
[:throws "h-err/no-matching-signature 0-0 : No matching signature for '+'"]
```

The typical pattern is that when an 'unset' value might be produced, the first thing to do is to branch based on whether an actual value resulted.

```clojure
(if-value-let [x
               (get {:$type :spec/A,
                     :b 1}
                    :b)]
              x
              0)


;-- result --
1
```

```clojure
(if-value-let [x (get {:$type :spec/A} :b)] x 0)


;-- result --
0
```

The operators 'if-value', 'if-value-let', 'when-value', and 'when-value-let' are specifically for dealing with expressions that maybe produce 'unset'.

There is very little that can be done with a value that is possibly 'unset'. One of the few things that can be done with them is equality checks can be performed, although this is discouraged in favor of using one of the built in 'if-value' or 'when-value' operators.

```clojure
(= 1 (get {:$type :spec/A} :b))


;-- result --
false
```

```clojure
(= $no-value (get {:$type :spec/A} :b))


;-- result --
true
```

The main use for 'unset' values is when constructing an instance literal.

If a field in an instance literal is never to be provided then it can simply be omitted.

```clojure
{:$type :spec/A}


;-- result --
{:$type :spec/A}
```

However, if an optional field needs to be populated sometimes then a value that may be 'unset' can be useful.

```clojure
{:$type :spec/A,
 :b (get {:$type :spec/A,
          :b 1}
         :b)}


;-- result --
{:$type :spec/A,
 :b 1}
```

```clojure
{:$type :spec/A,
 :b (get {:$type :spec/A} :b)}


;-- result --
{:$type :spec/A}
```

If a potentially 'unset' value needs to be fabricated then the 'when' operators can be used.

```clojure
(let [x 11]
  {:$type :spec/A,
   :b (when (> x 10) x)})


;-- result --
{:$type :spec/A,
 :b 11}
```

```clojure
(let [x 1]
  {:$type :spec/A,
   :b (when (> x 10) x)})


;-- result --
{:$type :spec/A}
```

```clojure
(let [a {:$type :spec/A,
         :b 1}]
  {:$type :spec/A,
   :b (when-value-let [x (get a :b)] (inc x))})


;-- result --
{:$type :spec/A,
 :b 2}
```

```clojure
(let [a {:$type :spec/A}]
  {:$type :spec/A,
   :b (when-value-let [x (get a :b)] (inc x))})


;-- result --
{:$type :spec/A}
```

The 'when-value' and 'if-value' operators are useful from within the context of a spec.

```clojure
{:spec/P {:spec-vars {:q [:Maybe "Integer"],
                      :r "Integer"}},
 :spec/X {:spec-vars {:y [:Maybe "Integer"],
                      :z [:Maybe "Integer"]},
          :refines-to {:spec/P {:name "refine_to_P",
                                :expr '{:$type :spec/P,
                                        :q (when-value y (inc y)),
                                        :r (if-value z z 0)}}}}}
```

```clojure
(refine-to {:$type :spec/X,
            :y 10,
            :z 20}
           :spec/P)


;-- result --
{:$type :spec/P,
 :q 11,
 :r 20}
```

```clojure
(refine-to {:$type :spec/X} :spec/P)


;-- result --
{:$type :spec/P,
 :r 0}
```

The operators that branch on 'unset' values cannot be used with expressions that cannot be 'unset'.

```clojure
(if-value-let [x 1] x 2)


;-- result --
[:throws
 "l-err/binding-expression-not-optional 0-0 : Binding expression in 'if-value-let' must have an optional type"]
```

#### Basic elements:

[`instance`](../halite-basic-syntax-reference.md#instance), [`integer`](../halite-basic-syntax-reference.md#integer)

#### Operator reference:

* [`if-value`](../halite-full-reference.md#if-value)
* [`if-value-let`](../halite-full-reference.md#if-value-let)
* [`refine-to`](../halite-full-reference.md#refine-to)
* [`when`](../halite-full-reference.md#when)
* [`when-value`](../halite-full-reference.md#when-value)
* [`when-value-let`](../halite-full-reference.md#when-value-let)


