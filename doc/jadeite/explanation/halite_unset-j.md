<!---
  This markdown file was generated. Do not edit.
  -->

## The pseduo-value 'unset' is handled specially

The 'unset' value cannot be used in general and there are specific facilities for dealing with them when they are produced by an expression.

Some languages have a notion of 'null' that appears throughout; this language uses 'unset' instead. Potentially 'unset' values generally need to be addressed using a special "if value" kind of operator to help prevent the 'unset' value from getting passed very far. The idea is that most code should therefore not need to deal with it. If an 'unset' value does need to be created, do so with `$no-value` or a 'when' operation.

```java
'$no-value'


//-- result --
Unset
```

But, ideally users will not use '$no-value' explicitly.

An 'unset' value is expected to come into play via an optional field.

```java
{
  "spec/A" : {
    "spec-vars" : {
      "b" : [ "Maybe", "Integer" ]
    }
  }
}
```

```java
{$type: spec/A}.b


//-- result --
Unset
```

The 'unset' value cannot be used in most operations.

```java
({$type: spec/A}.b + 2)


//-- result --
[:throws "h-err/no-matching-signature 0-0 : No matching signature for '+'"]
```

The typical pattern is that when an 'unset' value might be produced, the first thing to do is to branch based on whether an actual value resulted.

```java
(ifValueLet ( x = {$type: spec/A, b: 1}.b ) {x} else {0})


//-- result --
1
```

```java
(ifValueLet ( x = {$type: spec/A}.b ) {x} else {0})


//-- result --
0
```

The operators 'if-value', 'if-value-let', 'when-value', and 'when-value-let' are specifically for dealing with expressions that maybe produce 'unset'.

There is very little that can be done with a value that is possibly 'unset'. One of the few things that can be done with them is equality checks can be performed, although this is discouraged in favor of using one of the built in 'if-value' or 'when-value' operators.

```java
(1 == {$type: spec/A}.b)


//-- result --
false
```

```java
('$no-value' == {$type: spec/A}.b)


//-- result --
true
```

The main use for 'unset' values is when constructing an instance literal.

If a field in an instance literal is never to be provided then it can simply be omitted.

```java
{$type: spec/A}


//-- result --
{$type: spec/A}
```

However, if an optional field needs to be populated sometimes then a value that may be 'unset' can be useful.

```java
{$type: spec/A, b: {$type: spec/A, b: 1}.b}


//-- result --
{$type: spec/A, b: 1}
```

```java
{$type: spec/A, b: {$type: spec/A}.b}


//-- result --
{$type: spec/A}
```

If a potentially 'unset' value needs to be fabricated then the 'when' operators can be used.

```java
({ x = 11; {$type: spec/A, b: (when((x > 10)) {x})} })


//-- result --
{$type: spec/A, b: 11}
```

```java
({ x = 1; {$type: spec/A, b: (when((x > 10)) {x})} })


//-- result --
{$type: spec/A}
```

```java
({ a = {$type: spec/A, b: 1}; {$type: spec/A, b: (whenValueLet ( x = a.b ) {(x + 1)})} })


//-- result --
{$type: spec/A, b: 2}
```

```java
({ a = {$type: spec/A}; {$type: spec/A, b: (whenValueLet ( x = a.b ) {(x + 1)})} })


//-- result --
{$type: spec/A}
```

The 'when-value' and 'if-value' operators are useful from within the context of a spec.

```java
{
  "spec/X" : {
    "spec-vars" : {
      "y" : [ "Maybe", "Integer" ],
      "z" : [ "Maybe", "Integer" ]
    },
    "refines-to" : {
      "spec/P" : {
        "name" : "refine_to_P",
        "expr" : "{$type: spec/P, q: (whenValue(y) {(y + 1)}), r: (ifValue(z) {z} else {0})}"
      }
    }
  },
  "spec/P" : {
    "spec-vars" : {
      "q" : [ "Maybe", "Integer" ],
      "r" : "Integer"
    }
  }
}
```

```java
{$type: spec/X, y: 10, z: 20}.refineTo( spec/P )


//-- result --
{$type: spec/P, q: 11, r: 20}
```

```java
{$type: spec/X}.refineTo( spec/P )


//-- result --
{$type: spec/P, r: 0}
```

The operators that branch on 'unset' values cannot be used with expressions that cannot be 'unset'.

```java
(ifValueLet ( x = 1 ) {x} else {2})


//-- result --
[:throws "l-err/binding-expression-not-optional 0-0 : Binding expression in 'if-value-let' must have an optional type"]
```

### Reference

#### Basic elements:

[`instance`](../halite_basic-syntax-reference-j.md#instance), [`integer`](../halite_basic-syntax-reference-j.md#integer)

#### Operator reference:

* [`ifValue`](../halite_full-reference-j.md#ifValue)
* [`ifValueLet`](../halite_full-reference-j.md#ifValueLet)
* [`refineTo`](../halite_full-reference-j.md#refineTo)
* [`when`](../halite_full-reference-j.md#when)
* [`whenValue`](../halite_full-reference-j.md#whenValue)
* [`whenValueLet`](../halite_full-reference-j.md#whenValueLet)


