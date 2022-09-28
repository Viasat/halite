<!---
  This markdown file was generated. Do not edit.
  -->

## How to use short-circuiting to avoid runtime errors.

Several operations can throw runtime errors. This includes mathematical overflow, division by 0, index out of bounds, invoking non-existent refinement paths, and construction of invalid instances. The question is, how to write code to avoid such runtime errors?

The typical pattern to avoid such runtime errors is to first test to see if some condition is met to make the operation 'safe'. Only if that condition is met is the operator invoked. For example, to guard dividing by zero.

```clojure
(let [x 0]
  (div 100 x))


;-- result --
[:throws "h-err/divide-by-zero 0-0 : Cannot divide by zero"
 :h-err/divide-by-zero]
```

```clojure
(let [x 0]
  (if (not= x 0) (div 100 x) 0))


;-- result --
0
```

To guard index out of bounds.

```clojure
(let [x []]
  (get x 0))


;-- result --
[:throws
 "h-err/index-out-of-bounds 0-0 : Index out of bounds, 0, for vector of length 0"]
```

```clojure
(let [x []]
  (if (> (count x) 0) (get x 0) 0))


;-- result --
0
```

```clojure
(let [x [10]]
  (if (> (count x) 0) (get x 0) 0))


;-- result --
10
```

To guard instance construction.

```clojure
{:spec/Q {:spec-vars {:a "Integer"},
          :constraints [["c" '(> a 0)]]}}
```

```clojure
(let [x 0]
  {:$type :spec/Q,
   :a x})


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'spec/Q', violates constraints c"
 :h-err/invalid-instance]
```

```clojure
(let [x 0]
  (if (valid? {:$type :spec/Q,
               :a x})
    {:$type :spec/Q,
     :a x}
    {:$type :spec/Q,
     :a 1}))


;-- result --
{:$type :spec/Q,
 :a 1}
```

```clojure
(let [x 10]
  (if (valid? {:$type :spec/Q,
               :a x})
    {:$type :spec/Q,
     :a x}
    {:$type :spec/Q,
     :a 1}))


;-- result --
{:$type :spec/Q,
 :a 10}
```

This example can be refined slightly to avoid duplicating the construction.

```clojure
(let [x 0]
  (if-value-let [i
                 (valid {:$type :spec/Q,
                         :a x})]
                i
                {:$type :spec/Q,
                 :a 1}))


;-- result --
{:$type :spec/Q,
 :a 1}
```

```clojure
(let [x 10]
  (if-value-let [i
                 (valid {:$type :spec/Q,
                         :a x})]
                i
                {:$type :spec/Q,
                 :a 1}))


;-- result --
{:$type :spec/Q,
 :a 10}
```

To guard refinements.

```clojure
{:spec/P {:spec-vars {:p "Integer"},
          :refines-to {:spec/Q {:name "refine_to_Q",
                                :expr '(when (> p 0)
                                         {:$type :spec/Q,
                                          :q p})}}},
 :spec/Q {:spec-vars {:q "Integer"}}}
```

```clojure
(let [x {:$type :spec/P,
         :p 0}]
  (refine-to x :spec/Q))


;-- result --
[:throws
 "h-err/no-refinement-path 0-0 : No active refinement path from 'spec/P' to 'spec/Q'"
 :h-err/no-refinement-path]
```

```clojure
(let [x {:$type :spec/P,
         :p 0}]
  (if (refines-to? x :spec/Q)
    (refine-to x :spec/Q)
    {:$type :spec/Q,
     :q 1}))


;-- result --
{:$type :spec/Q,
 :q 1}
```

```clojure
(let [x {:$type :spec/P,
         :p 10}]
  (if (refines-to? x :spec/Q)
    (refine-to x :spec/Q)
    {:$type :spec/Q,
     :q 1}))


;-- result --
{:$type :spec/Q,
 :q 10}
```

So, 'if' and variants of 'if' such as 'when', 'if-value-let', and 'when-value-let' are the main tools for avoiding runtime errors. They each are special forms which do not eagerly evaluate their bodies at invocation time.

Some languages have short-circuiting logical operators 'and' and 'or'. However, they are not short-circuiting in this language.

```clojure
(let [x 0]
  (and (> x 0) (> (div 100 x) 0)))


;-- result --
[:throws "h-err/divide-by-zero 0-0 : Cannot divide by zero"
 :h-err/divide-by-zero]
```

```clojure
(let [x 0]
  (> x 0))


;-- result --
false
```

The same applies to 'or':

```clojure
(let [x 0]
  (or (= x 0) (> (div 100 x) 0)))


;-- result --
[:throws "h-err/divide-by-zero 0-0 : Cannot divide by zero"
 :h-err/divide-by-zero]
```

```clojure
(let [x 0]
  (= x 0))


;-- result --
true
```

Similarly, the sequence operators of 'every?', 'any?', 'map', and 'filter' are all eager and fully evaluate for all elements of the collection regardless of what happens with the evaluation of prior elements.

This raises an error even though logically, the result could be 'true' if just the first element is considered.

```clojure
(let [x [2 1 0]]
  (any? [e x] (> (div 100 e) 0)))


;-- result --
[:throws "h-err/divide-by-zero 0-0 : Cannot divide by zero"
 :h-err/divide-by-zero]
```

```clojure
(let [x [2 1]]
  (any? [e x] (> (div 100 e) 0)))


;-- result --
true
```

This raises an error even though logically, the result could be 'false' if just the first element is considered.

```clojure
(let [x [200 100 0]]
  (every? [e x] (> (div 100 e) 0)))


;-- result --
[:throws "h-err/divide-by-zero 0-0 : Cannot divide by zero"
 :h-err/divide-by-zero]
```

```clojure
(let [x [200 100]]
  (every? [e x] (> (div 100 e) 0)))


;-- result --
false
```

This raises an error even though, the result could be 50 if just the first element is actually accessed.

```clojure
(let [x [2 1 0]]
  (get (map [e x] (div 100 e)) 0))


;-- result --
[:throws "h-err/divide-by-zero 0-0 : Cannot divide by zero"
 :h-err/divide-by-zero]
```

```clojure
(let [x [2 1]]
  (get (map [e x] (div 100 e)) 0))


;-- result --
50
```

This raises an error even though, the result could be 2 if just the first element is actually accessed.

```clojure
(let [x [2 1 0]]
  (get (filter [e x] (> (div 100 e) 0)) 0))


;-- result --
[:throws "h-err/divide-by-zero 0-0 : Cannot divide by zero"
 :h-err/divide-by-zero]
```

```clojure
(let [x [2 1]]
  (get (filter [e x] (> (div 100 e) 0)) 0))


;-- result --
2
```

This means that the logical operators cannot be used to guard against runtime errors. Instead the control flow statements must be used.

### Reference

#### Basic elements:

[`integer`](../halite_basic-syntax-reference.md#integer), [`vector`](../halite_basic-syntax-reference.md#vector)

#### Operator reference:

* [`any?`](halite_full-reference.md#any_Q)
* [`div`](halite_full-reference.md#div)
* [`every?`](halite_full-reference.md#every_Q)
* [`get`](halite_full-reference.md#get)
* [`if`](halite_full-reference.md#if)
* [`if-value-let`](halite_full-reference.md#if-value-let)
* [`refine-to`](halite_full-reference.md#refine-to)
* [`refines-to?`](halite_full-reference.md#refines-to_Q)
* [`valid`](halite_full-reference.md#valid)
* [`valid?`](halite_full-reference.md#valid_Q)
* [`when`](halite_full-reference.md#when)
* [`when-value-let`](halite_full-reference.md#when-value-let)


