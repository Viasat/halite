<!---
  This markdown file was generated. Do not edit.
  -->

## Divide an integer to produce a decimal result

Consider you have an integer value and you want to divide it by another integer to produce a decimal result.

Simply performing the division provides an integer result

```java
({ x = 14; y = 3; (x / y) })


//-- result --
4
```

The mod operator can provide the remainder

```java
({ x = 14; y = 3; (x % y) })


//-- result --
2
```

The remainder can be converted into a decimal

```java
({ x = 14; y = 3; (#d "1.0" * (x % y)) })


//-- result --
#d "2.0"
```

The remainder can be divided by the orginal divisor.

```java
({ x = 14; y = 3; ((#d "1.0" * (x % y)) / y) })


//-- result --
#d "0.6"
```

The integer part of the division result can also be converted to a decimal

```java
({ x = 14; y = 3; (#d "1.0" * (x / y)) })


//-- result --
#d "4.0"
```

Putting it all together gives the result of the division truncated to one decimal place

```java
({ x = 14; y = 3; ((#d "1.0" * (x / y)) + ((#d "1.0" * (x % y)) / y)) })


//-- result --
#d "4.6"
```

### Reference

#### Basic elements:

[`fixed-decimal`](../halite_basic-syntax-reference-j.md#fixed-decimal), [`integer`](../halite_basic-syntax-reference-j.md#integer)

#### Operator reference:

* [`/`](../halite_full-reference-j.md#/)


