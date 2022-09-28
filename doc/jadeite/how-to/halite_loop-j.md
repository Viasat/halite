<!---
  This markdown file was generated. Do not edit.
  -->

## How to write a loop

There is no explicit language construct to write a loop. So how to write one?

Most languages will have some sort of 'for' loop or 'do while' look construct. In many cases the need for looping is subsumed by the collection operators that are present. For example, rather than writing a loop to extract values from a collection, 'filter' can be used.

```java
({ x = [5, 17, 23, 35]; filter(e in x)(e > 20) })


//-- result --
[23, 35]
```

Similarly if we need to make a new collection derived from a collection, rather than writing a loop, we can use 'map'.

```java
({ x = [5, 17, 23, 35]; map(e in x)(e + 1) })


//-- result --
[6, 18, 24, 36]
```

If we need to test whether a predicate holds for items in a collection, rather than writing a loop we can use 'every?' and 'any?'.

```java
({ x = [5, 17, 23, 35]; every?(e in x)(e > 0) })


//-- result --
true
```

```java
({ x = [5, 17, 23, 35]; any?(e in x)(e > 20) })


//-- result --
true
```

Finally if we need to create a single value from a collection, rather than writing a loop, we can use 'reduce'.

```java
({ x = [5, 17, 23, 35]; (reduce( a = 0; e in x ) { (a + e) }) })


//-- result --
80
```

So, if the loop is for dealing with a collection then the built-in operators can be used. But that leaves the question: what if there is no collection to use as the basis for the loop? In that case a collection of the desired size can be created on demand. In this example a collection of 10 items is created so that we can effectively 'loop' over it and add 3 to the accumulator each time through the loop.

```java
({ x = range(10); (reduce( a = 0; e in x ) { (a + 3) }) })


//-- result --
30
```

That leaves the case of an infinite loop, or a loop that continues until some aribtrary expression evaluates to true. Infinite loops are not allowed by design. Looping until an expression evaluates to true is not supported and arguably not necessary. The language does not have side-effects or mutable state and in fact produces deterministic results. So the notion of looping until 'something else happens' does not make sense. Which leaves the case of looping a deterministic number of times, when then number of iterations is not known by the author of the code. For example, the following 'loops' dividing the intitial value by 2 until it cannot be divided further, then it returns the remainder. Rather than looping until the value is less than 2, this just loops a fixed number of times and the author of the code needs to know how many times is necessary to loop in order to fully divide the initial number.

```java
({ x = 21; (reduce( a = x; e in range(10) ) { (if((a >= 2)) {(a / 2)} else {a}) }) })


//-- result --
1
```

Of course this example is contrived, because the 'mod' operator is available.

### Reference

#### Basic elements:

[`integer`](../halite_basic-syntax-reference-j.md#integer), [`vector`](../halite_basic-syntax-reference-j.md#vector)

#### Operator reference:

* [`/`](halite_full-reference-j.md#/)
* [`filter`](halite_full-reference-j.md#filter)
* [`map`](halite_full-reference-j.md#map)
* [`range`](halite_full-reference-j.md#range)
* [`reduce`](halite_full-reference-j.md#reduce)


