<!---
  This markdown file was generated. Do not edit.
  -->

# Jadeite basic syntax and types reference

The syntax diagrams are a graphical representation of the grammar rules for the different elements.

In the diagrams when a grammar element appears as '<x>:<label>' the label is simply a descriptive label to convey to the reader the meaining of the element.

## <a name="non-numeric-character"></a>non-numeric-character



!["'A-Z' | 'a-z' | '*' | '!' | '$' | '=' | '<' | '>' | '_' | '.' | '?'"](../halite-bnf-diagrams/basic-syntax/non-numeric-character-j.svg)

---
## <a name="plus-minus-character"></a>plus-minus-character



!["'+' | '-'"](../halite-bnf-diagrams/basic-syntax/plus-minus-character-j.svg)

---
## <a name="symbol-character"></a>symbol-character



!["non-numeric-character | plus-minus-character | '0-9'"](../halite-bnf-diagrams/basic-syntax/symbol-character-j.svg)

---
## <a name="bare-symbol"></a>bare-symbol



!["plus-minus-character | ((non-numeric-character | plus-minus-character) {symbol-character})"](../halite-bnf-diagrams/basic-syntax/bare-symbol-j.svg)

---
## <a name="symbol"></a>symbol

Symbols are identifiers that allow values and operations to be named. The following are reserved and cannot be used as user defined symbols: true, false, nil.

!["(bare-symbol [ '/' bare-symbol]) | ('’' bare-symbol [ '/' bare-symbol] '’')"](../halite-bnf-diagrams/basic-syntax/symbol-j.svg)

Symbols are used to identify operators, variables in expressions, specifications, and fields within specifications. Symbols are not values. There are no expressions that produce symbols. Anywhere that a symbol is called for in an operator argument list, a literal symbol must be provided. Symbols passed as arguments to operators are not evaluated. Symbols used within expressions in general are evaluated prior to invoking the operator. The following are also legal symbols, but they are reserved for system use: &&, ||, /, %, |

<table><tr><td colspan="1">

```java
a
```

</td><td colspan="1">

```java
a.b
```

</td><td colspan="1">

```java
'a/b'
```

</td></tr></table>

### Possible errors:

* [`h-err/invalid-symbol-char`](halite_err-id-reference-j.md#h-err/invalid-symbol-char)
* [`h-err/invalid-symbol-length`](halite_err-id-reference-j.md#h-err/invalid-symbol-length)

### Tags:

 [Instance field operations](halite_instance-field-op-reference-j.md),  [Instance operations](halite_instance-op-reference-j.md),  [Produce instances](halite_instance-out-reference-j.md),  [Spec-id operations](halite_spec-id-op-reference-j.md)

---
## <a name="boolean"></a>boolean



!["true | false"](../halite-bnf-diagrams/basic-syntax/boolean-j.svg)

### Tags:

 [Boolean operations](halite_boolean-op-reference-j.md),  [Produce booleans](halite_boolean-out-reference-j.md)

---
## <a name="string"></a>string

Strings are sequences of characters. Strings can be multi-line. Quotation marks can be included if escaped with a \. A backslash can be included with the character sequence: \\ . Strings can include special characters, e.g. \t for a tab and \n for a newline, as well as unicode via \uNNNN. Unicode can also be directly entered in strings. Additional character representations may work but the only representations that are guaranteed to work are those documented here.

![" '\"' {char | '\\' ('\\' | '\"' | 't' | 'n' | ('u' hex-digit hex-digit hex-digit hex-digit))} '\"'"](../halite-bnf-diagrams/basic-syntax/string-j.svg)

<table><tr><td colspan="1">

```java
""
```

</td><td colspan="1">

```java
"hello"
```

</td><td colspan="1">

```java
"say \"hi\" now"

//-- result --
"say \"hi\" now"
```

</td><td colspan="1">

```java
"one \\ two"

//-- result --
"one \\ two"
```

</td><td colspan="1">

```java
"\t\n"

//-- result --
"\t\n"
```

</td></tr><tr><td colspan="1">

```java
"☺"

//-- result --
"☺"
```

</td><td colspan="1">

```java
"\u263A"

//-- result --
"☺"
```

</td></tr></table>

### Possible errors:

* [`h-err/size-exceeded`](halite_err-id-reference-j.md#h-err/size-exceeded)

### Tags:

 [String operations](halite_string-op-reference-j.md)

---
## <a name="integer"></a>integer

Signed, eight byte numeric integer values. Alternative integer representations may work, but the only representation that is guaranteed to work on an ongoing basis is that documented here. The largest positive integer is 9223372036854775807. The most negative integer is -9223372036854775808.

Some language targets (eg. bounds-propagation) may use 4 instead of 8 bytes. On overflow, math operations never wrap; instead the evaluator will throw a runtime error.

!["[plus-minus-character] '0-9' {'0-9'}"](../halite-bnf-diagrams/basic-syntax/integer-j.svg)

<table><tr><td colspan="1">

```java
0
```

</td><td colspan="1">

```java
1
```

</td><td colspan="1">

```java
1
```

</td><td colspan="1">

```java
-1
```

</td><td colspan="1">

```java
9223372036854775807
```

</td></tr><tr><td colspan="1">

```java
-9223372036854775808
```

</td><td colspan="1">

```java
0
```

</td></tr></table>

### Possible errors:

* [`h-err/overflow`](halite_err-id-reference-j.md#h-err/overflow)

### Tags:

 [Integer operations](halite_integer-op-reference-j.md),  [Produce integer](halite_integer-out-reference-j.md)

---
## <a name="fixed-decimal"></a>fixed-decimal

Signed numeric values with decimal places. The scale (i.e. the number of digits to the right of the decimal place), must be between one and 18. Conceptually, the entire numeric value must fit into the same number of bytes as an 'integer'. So the largest fixed-decimal value with a scale of one is: #d "922337203685477580.7", and the most negative value with a scale of one is: #d "-922337203685477580.8". Similarly, the largest fixed-decimal value with a scale of 18 is: #d "9.223372036854775807" and the most negative value with a scale of 18 is: #d "-9.223372036854775808". The scale of the fixed-decimal value can be set to what is needed, but as more precision is added to the right of the decimal place, fewer digits are available to the left of the decimal place.

!["'#' 'd' [whitespace] '\"' ['-'] ('0' | ('1-9' {'0-9'})) '.' '0-9' {'0-9'} '\"'"](../halite-bnf-diagrams/basic-syntax/fixed-decimal-j.svg)

<table><tr><td colspan="1">

```java
#d "1.1"
```

</td><td colspan="1">

```java
#d "-1.1"
```

</td><td colspan="1">

```java
#d "1.00"
```

</td><td colspan="1">

```java
#d "0.00"
```

</td></tr><tr><td colspan="2">

```java
#d "922337203685477580.7"
```

</td><td colspan="2">

```java
#d "-922337203685477580.8"
```

</td></tr><tr><td colspan="2">

```java
#d "9.223372036854775807"
```

</td><td colspan="2">

```java
#d "-9.223372036854775808"
```

</td></tr></table>

### Possible errors:

* [`h-err/overflow`](halite_err-id-reference-j.md#h-err/overflow)

### Tags:

 [Fixed-decimal operations](halite_fixed-decimal-op-reference-j.md),  [Produce fixed-decimals](halite_fixed-decimal-out-reference-j.md)

---
## <a name="instance"></a>instance

Represents an instance of a specification.

!["'{' '$type' ':' symbol:spec-id {',' symbol ':' value } '}'"](../halite-bnf-diagrams/basic-syntax/instance-j.svg)

The contents of the instance are specified in pair-wise fashion with alternating field names and field values. The special field name '$type' is mandatory but cannot be used as the other fields are. 

<table><tr><td colspan="2">

```java
{$type: my/Spec$v1, x: 1, y: -1}
```

</td></tr></table>

### Possible errors:

* [`h-err/field-name-not-in-spec`](halite_err-id-reference-j.md#h-err/field-name-not-in-spec)
* [`h-err/field-value-of-wrong-type`](halite_err-id-reference-j.md#h-err/field-value-of-wrong-type)
* [`h-err/invalid-instance`](halite_err-id-reference-j.md#h-err/invalid-instance)
* [`h-err/invalid-type-value`](halite_err-id-reference-j.md#h-err/invalid-type-value)
* [`h-err/missing-required-vars`](halite_err-id-reference-j.md#h-err/missing-required-vars)
* [`h-err/missing-type-field`](halite_err-id-reference-j.md#h-err/missing-type-field)
* [`h-err/no-abstract`](halite_err-id-reference-j.md#h-err/no-abstract)
* [`h-err/not-boolean-constraint`](halite_err-id-reference-j.md#h-err/not-boolean-constraint)
* [`h-err/resource-spec-not-found`](halite_err-id-reference-j.md#h-err/resource-spec-not-found)

### Tags:

 [Instance field operations](halite_instance-field-op-reference-j.md),  [Instance operations](halite_instance-op-reference-j.md),  [Produce instances](halite_instance-out-reference-j.md)

### How tos:

* [spec-variables](how-to/halite_spec-variables-j.md)


### Tutorials:

* [sudoku](tutorial/halite_sudoku-j.md)


---
## <a name="vector"></a>vector

A collection of values in a prescribed sequence.

!["'[' [whitespace] [value] [whitespace] {',' [whitespace] value [whitespace]} [whitespace]']'"](../halite-bnf-diagrams/basic-syntax/vector-j.svg)

<table><tr><td colspan="1">

```java
[]
```

</td><td colspan="1">

```java
[1, 2, 3]
```

</td><td colspan="1">

```java
[#{1, 2}, #{3}]
```

</td></tr></table>

### Possible errors:

* [`h-err/literal-must-evaluate-to-value`](halite_err-id-reference-j.md#h-err/literal-must-evaluate-to-value)
* [`h-err/size-exceeded`](halite_err-id-reference-j.md#h-err/size-exceeded)

### Tags:

 [Vector operations](halite_vector-op-reference-j.md),  [Produce vectors](halite_vector-out-reference-j.md)

---
## <a name="set"></a>set

A collection of values in an unordered set. Duplicates are not allowed.

!["'#' '{' [whitespace] [value] [whitespace] {',' [whitespace] value [whitespace]} '}'"](../halite-bnf-diagrams/basic-syntax/set-j.svg)

The members of sets are not directly accessible. If it is necessary to access the members of a set, it is recommended to design the data structures going into the sets in such a way that the set can be sorted into a vector for access.  

<table><tr><td colspan="1">

```java
#{}
```

</td><td colspan="1">

```java
#{1, 2, 3}
```

</td><td colspan="1">

```java
#{[1, 2], [3]}
```

</td></tr></table>

### Possible errors:

* [`h-err/literal-must-evaluate-to-value`](halite_err-id-reference-j.md#h-err/literal-must-evaluate-to-value)
* [`h-err/size-exceeded`](halite_err-id-reference-j.md#h-err/size-exceeded)

### Tags:

 [Set operations](halite_set-op-reference-j.md),  [Produce sets](halite_set-out-reference-j.md)

---
## <a name="value"></a>value

Expressions and many literals produce values.

!["boolean | string | integer | fixed-decimal | instance | vector | set"](../halite-bnf-diagrams/basic-syntax/value-j.svg)

---
## <a name="unset"></a>unset

A pseudo-value that represents the lack of a value.

!["unset"](../halite-bnf-diagrams/basic-syntax/unset-j.svg)

### Explanations:

* [unset](explanation/halite_unset-j.md)


---
## <a name="nothing"></a>nothing

The absence of a value.

!["nothing"](../halite-bnf-diagrams/basic-syntax/nothing-j.svg)

---
## <a name="any"></a>any

Refers to either the presence of absence of a value, or a pseudo-value indicating the lack of a value.

!["value | unset"](../halite-bnf-diagrams/basic-syntax/any-j.svg)

---
## <a name="comment"></a>comment

Comments that are not evaluated as part of the expression.

!["'//' comment"](../halite-bnf-diagrams/basic-syntax/comment-j.svg)

---
# Type Graph![type graph](../types.dot.png)

