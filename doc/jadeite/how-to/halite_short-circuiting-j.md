<!---
  This markdown file was generated. Do not edit.
  -->

## How to use short-circuiting to avoid runtime errors.

Several operations can throw runtime errors. This includes mathematical overflow, division by 0, index out of bounds, invoking non-existent refinement paths, and construction of invalid instances. The question is, how to write code to avoid such runtime errors?

The typical pattern to avoid such runtime errors is to first test to see if some condition is met to make the operation 'safe'. Only if that condition is met is the operator invoked. For example, to guard dividing by zero.

```java
({ x = 0; (100 / x) })


//-- result --
[:throws "h-err/divide-by-zero 0-0 : Cannot divide by zero"]
```

```java
({ x = 0; (if((x != 0)) {(100 / x)} else {0}) })


//-- result --
0
```

To guard index out of bounds.

```java
({ x = []; x[0] })


//-- result --
[:throws "h-err/index-out-of-bounds 0-0 : Index out of bounds, 0, for vector of length 0"]
```

```java
({ x = []; (if((x.count() > 0)) {x[0]} else {0}) })


//-- result --
0
```

```java
({ x = [10]; (if((x.count() > 0)) {x[0]} else {0}) })


//-- result --
10
```

To guard instance construction.

```java
{
  "spec/Q" : {
    "spec-vars" : {
      "a" : "Integer"
    },
    "constraints" : [ [ "c", "(a > 0)" ] ]
  }
}
```

```java
({ x = 0; {$type: spec/Q, a: x} })


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'spec/Q', violates constraints c"]
```

```java
({ x = 0; (if((valid? {$type: spec/Q, a: x})) {{$type: spec/Q, a: x}} else {{$type: spec/Q, a: 1}}) })


//-- result --
{$type: spec/Q, a: 1}
```

```java
({ x = 10; (if((valid? {$type: spec/Q, a: x})) {{$type: spec/Q, a: x}} else {{$type: spec/Q, a: 1}}) })


//-- result --
{$type: spec/Q, a: 10}
```

This example can be refined slightly to avoid duplicating the construction.

```java
({ x = 0; (ifValueLet ( i = (valid {$type: spec/Q, a: x}) ) {i} else {{$type: spec/Q, a: 1}}) })


//-- result --
{$type: spec/Q, a: 1}
```

```java
({ x = 10; (ifValueLet ( i = (valid {$type: spec/Q, a: x}) ) {i} else {{$type: spec/Q, a: 1}}) })


//-- result --
{$type: spec/Q, a: 10}
```

To guard refinements.

```java
{
  "spec/Q" : {
    "spec-vars" : {
      "q" : "Integer"
    }
  },
  "spec/P" : {
    "spec-vars" : {
      "p" : "Integer"
    },
    "refines-to" : {
      "spec/Q" : {
        "name" : "refine_to_Q",
        "expr" : "(when((p > 0)) {{$type: spec/Q, q: p}})"
      }
    }
  }
}
```

```java
({ x = {$type: spec/P, p: 0}; x.refineTo( spec/Q ) })


//-- result --
[:throws "h-err/no-refinement-path 0-0 : No active refinement path from 'spec/P' to 'spec/Q'"]
```

```java
({ x = {$type: spec/P, p: 0}; (if(x.refinesTo?( spec/Q )) {x.refineTo( spec/Q )} else {{$type: spec/Q, q: 1}}) })


//-- result --
{$type: spec/Q, q: 1}
```

```java
({ x = {$type: spec/P, p: 10}; (if(x.refinesTo?( spec/Q )) {x.refineTo( spec/Q )} else {{$type: spec/Q, q: 1}}) })


//-- result --
{$type: spec/Q, q: 10}
```

So, 'if' and variants of 'if' such as 'when', 'if-value-let', and 'when-value-let' are the main tools for avoiding runtime errors. They each are special forms which do not eagerly evaluate their bodies at invocation time.

Some languages have short-circuiting logical operators 'and' and 'or'. However, they are not short-circuiting in this language.

```java
({ x = 0; ((x > 0) && ((100 / x) > 0)) })


//-- result --
[:throws "h-err/divide-by-zero 0-0 : Cannot divide by zero"]
```

```java
({ x = 0; (x > 0) })


//-- result --
false
```

The same applies to 'or':

```java
({ x = 0; ((x == 0) || ((100 / x) > 0)) })


//-- result --
[:throws "h-err/divide-by-zero 0-0 : Cannot divide by zero"]
```

```java
({ x = 0; (x == 0) })


//-- result --
true
```

Similarly, the sequence operators of 'every?', 'any?', 'map', and 'filter' are all eager and fully evaluate for all elements of the collection regardless of what happens with the evaluation of prior elements.

This raises an error even though logically, the result could be 'true' if just the first element is considered.

```java
({ x = [2, 1, 0]; any?(e in x)((100 / e) > 0) })


//-- result --
[:throws "h-err/divide-by-zero 0-0 : Cannot divide by zero"]
```

```java
({ x = [2, 1]; any?(e in x)((100 / e) > 0) })


//-- result --
true
```

This raises an error even though logically, the result could be 'false' if just the first element is considered.

```java
({ x = [200, 100, 0]; every?(e in x)((100 / e) > 0) })


//-- result --
[:throws "h-err/divide-by-zero 0-0 : Cannot divide by zero"]
```

```java
({ x = [200, 100]; every?(e in x)((100 / e) > 0) })


//-- result --
false
```

This raises an error even though, the result could be 50 if just the first element is actually accessed.

```java
({ x = [2, 1, 0]; map(e in x)(100 / e)[0] })


//-- result --
[:throws "h-err/invalid-lookup-target 0-0 : Lookup target must be an instance of known type or non-empty vector"]
```

```java
({ x = [2, 1]; map(e in x)(100 / e)[0] })


//-- result --
[:throws "h-err/invalid-lookup-target 0-0 : Lookup target must be an instance of known type or non-empty vector"]
```

This raises an error even though, the result could be 2 if just the first element is actually accessed.

```java
({ x = [2, 1, 0]; filter(e in x)((100 / e) > 0)[0] })


//-- result --
[:throws "h-err/invalid-lookup-target 0-0 : Lookup target must be an instance of known type or non-empty vector"]
```

```java
({ x = [2, 1]; filter(e in x)((100 / e) > 0)[0] })


//-- result --
[:throws "h-err/invalid-lookup-target 0-0 : Lookup target must be an instance of known type or non-empty vector"]
```

This means that the logical operators cannot be used to guard against runtime errors. Instead the control flow statements must be used.

### Reference

#### Basic elements:

[`integer`](../halite_basic-syntax-reference-j.md#integer), [`vector`](../halite_basic-syntax-reference-j.md#vector)

#### Operator reference:

* [`/`](../halite_full-reference-j.md#/)
* [`ACCESSOR`](../halite_full-reference-j.md#ACCESSOR)
* [`any?`](../halite_full-reference-j.md#any_Q)
* [`every?`](../halite_full-reference-j.md#every_Q)
* [`if`](../halite_full-reference-j.md#if)
* [`ifValueLet`](../halite_full-reference-j.md#ifValueLet)
* [`refineTo`](../halite_full-reference-j.md#refineTo)
* [`refinesTo?`](../halite_full-reference-j.md#refinesTo_Q)
* [`valid`](../halite_full-reference-j.md#valid)
* [`valid?`](../halite_full-reference-j.md#valid_Q)
* [`when`](../halite_full-reference-j.md#when)
* [`whenValueLet`](../halite_full-reference-j.md#whenValueLet)


