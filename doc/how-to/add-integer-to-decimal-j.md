<!---
  This markdown file was generated. Do not edit.
  -->

## Add an integer value to a decimal value

Consider you have an integer and a decimal value and you need to add them together.

Since the integer and decimal values are of different types they cannot be directly added together.

```java
({ x = 3; y = #d "2.2"; (y + x) })


//-- result --
[:throws "h-err/no-matching-signature 0-0 : No matching signature for '+'"]
```

It is first necessary to convert them to the same type of value. In this case the integer is converted into a decimal value by multiplying it by one in the target decimal type.

```java
({ x = 3; (#d "1.0" * x) })


//-- result --
#d "3.0"
```

Now the numbers can be added together.

```java
({ x = 3; y = #d "2.2"; (y + (#d "1.0" * x)) })


//-- result --
#d "5.2"
```

#### Basic elements:

[`fixed-decimal`](../jadeite-basic-syntax-reference.md#fixed-decimal), [`integer`](../jadeite-basic-syntax-reference.md#integer)

#### Operator reference:

* [`+`](../jadeite-full-reference.md#_A)


