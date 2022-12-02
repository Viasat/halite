<!---
  This markdown file was generated. Do not edit.
  -->

# Halite basic syntax and types reference

The syntax diagrams are a graphical representation of the grammar rules for the different elements.

In the diagrams when a rule starts with 'element_name:', this is not part of the syntax for the grammar element, but is instead naming the grammar element so it can be referenced in subsequent diagrams.

In the diagrams when a grammar element appears as 'x:label' the label is simply a descriptive label to convey to the reader the meaining of the element.

## <a name="non-numeric-character"></a>non-numeric-character



!["'A-Z' | 'a-z' | '*' | '!' | '$' | '=' | '<' | '>' | '_' | '.' | '?'"](../halite-bnf-diagrams/basic-syntax/non-numeric-character.svg)

---
## <a name="plus-minus-character"></a>plus-minus-character



!["'+' | '-'"](../halite-bnf-diagrams/basic-syntax/plus-minus-character.svg)

---
## <a name="symbol-character"></a>symbol-character



!["non-numeric-character | plus-minus-character | '0-9'"](../halite-bnf-diagrams/basic-syntax/symbol-character.svg)

---
## <a name="bare-symbol"></a>bare-symbol



!["plus-minus-character | ((non-numeric-character | plus-minus-character) {symbol-character})"](../halite-bnf-diagrams/basic-syntax/bare-symbol.svg)

---
## <a name="symbol"></a>symbol

Symbols are identifiers that allow values and operations to be named. The following are reserved and cannot be used as user defined symbols: true, false, nil.

!["bare-symbol [ '/' bare-symbol]"](../halite-bnf-diagrams/basic-syntax/symbol.svg)

Symbols are used to identify operators, variables in expressions, and specifications. Symbols are not values. There are no expressions that produce symbols. Anywhere that a symbol is called for in an operator argument list, a literal symbol must be provided. Symbols passed as arguments to operators are not evaluated. Symbols used within expressions in general are evaluated prior to invoking the operator. A common pattern in operator arguments is to provide a sequence of alternating symbols and values within square brackets. In these cases each symbol is bound to the corresponding value in pair-wise fashion.

<table><tr><td colspan="1">

```clojure
a
```

</td><td colspan="1">

```clojure
a.b
```

</td><td colspan="1">

```clojure
a/b
```

</td></tr></table>

### Possible errors:

* [`h-err/invalid-symbol-char`](halite_err-id-reference.md#h-err/invalid-symbol-char)
* [`h-err/invalid-symbol-length`](halite_err-id-reference.md#h-err/invalid-symbol-length)

---
## <a name="keyword"></a>keyword

Keywords are identifiers that are used for instance field names. The following are reserved and cannot be used as user defined keywords: :true, :false, :nil.

!["':' symbol"](../halite-bnf-diagrams/basic-syntax/keyword.svg)

Keywords are not values. There are no expressions that produce keywords. Anywhere that a keyword is called for in an operator arugment list, a literal keyword must be provided. Keywords themselves cannot be evaluated.  

<table><tr><td colspan="1">

```clojure
:age
```

</td><td colspan="1">

```clojure
:x/y
```

</td></tr></table>

### Possible errors:

* [`h-err/invalid-keyword-char`](halite_err-id-reference.md#h-err/invalid-keyword-char)
* [`h-err/invalid-keyword-length`](halite_err-id-reference.md#h-err/invalid-keyword-length)

### Tags:

 [Instance field operations](halite_instance-field-op-reference.md),  [Instance operations](halite_instance-op-reference.md),  [Produce instances](halite_instance-out-reference.md),  [Spec-id operations](halite_spec-id-op-reference.md)

---
## <a name="boolean"></a>boolean



!["true | false"](../halite-bnf-diagrams/basic-syntax/boolean.svg)

### Tags:

 [Boolean operations](halite_boolean-op-reference.md),  [Produce booleans](halite_boolean-out-reference.md)

---
## <a name="string"></a>string

Strings are sequences of characters. Strings can be multi-line. Quotation marks can be included if escaped with a \. A backslash can be included with the character sequence: \\ . Strings can include special characters, e.g. \t for a tab and \n for a newline, as well as unicode via \uNNNN. Unicode can also be directly entered in strings. Additional character representations may work but the only representations that are guaranteed to work are those documented here.

![" '\"' {char | '\\' ('\\' | '\"' | 't' | 'n' | ('u' hex-digit hex-digit hex-digit hex-digit))} '\"'"](../halite-bnf-diagrams/basic-syntax/string.svg)

<table><tr><td colspan="1">

```clojure
""
```

</td><td colspan="1">

```clojure
"hello"
```

</td><td colspan="1">

```clojure
"say \"hi\" now"

;-- result --
"say \"hi\" now"
```

</td><td colspan="1">

```clojure
"one \\ two"

;-- result --
"one \\ two"
```

</td><td colspan="1">

```clojure
"\t\n"

;-- result --
"\t\n"
```

</td></tr><tr><td colspan="1">

```clojure
"☺"

;-- result --
"☺"
```

</td><td colspan="1">

```clojure
"\u263A"

;-- result --
"☺"
```

</td></tr></table>

### Possible errors:

* [`h-err/size-exceeded`](halite_err-id-reference.md#h-err/size-exceeded)

### Tags:

 [String operations](halite_string-op-reference.md)

---
## <a name="integer"></a>integer

Signed, eight byte numeric integer values. Alternative integer representations may work, but the only representation that is guaranteed to work on an ongoing basis is that documented here. The largest positive integer is 9223372036854775807. The most negative integer is -9223372036854775808.

Some language targets (eg. bounds-propagation) may use 4 instead of 8 bytes. On overflow, math operations never wrap; instead the evaluator will throw a runtime error.

!["[plus-minus-character] '0-9' {'0-9'}"](../halite-bnf-diagrams/basic-syntax/integer.svg)

<table><tr><td colspan="1">

```clojure
0
```

</td><td colspan="1">

```clojure
1
```

</td><td colspan="1">

```clojure
+1
```

</td><td colspan="1">

```clojure
-1
```

</td><td colspan="1">

```clojure
9223372036854775807
```

</td></tr><tr><td colspan="1">

```clojure
-9223372036854775808
```

</td><td colspan="1">

```clojure
-0
```

</td></tr></table>

### Possible errors:

* [`h-err/overflow`](halite_err-id-reference.md#h-err/overflow)

### Tags:

 [Integer operations](halite_integer-op-reference.md),  [Produce integer](halite_integer-out-reference.md)

---
## <a name="fixed-decimal"></a>fixed-decimal

Signed numeric values with decimal places. The scale (i.e. the number of digits to the right of the decimal place), must be between one and 18. Conceptually, the entire numeric value must fit into the same number of bytes as an 'integer'. So the largest fixed-decimal value with a scale of one is: #d "922337203685477580.7", and the most negative value with a scale of one is: #d "-922337203685477580.8". Similarly, the largest fixed-decimal value with a scale of 18 is: #d "9.223372036854775807" and the most negative value with a scale of 18 is: #d "-9.223372036854775808". The scale of the fixed-decimal value can be set to what is needed, but as more precision is added to the right of the decimal place, fewer digits are available to the left of the decimal place.

!["'#' 'd' [whitespace] '\"' ['-'] ('0' | ('1-9' {'0-9'})) '.' '0-9' {'0-9'} '\"'"](../halite-bnf-diagrams/basic-syntax/fixed-decimal.svg)

<table><tr><td colspan="1">

```clojure
#d "1.1"
```

</td><td colspan="1">

```clojure
#d "-1.1"
```

</td><td colspan="1">

```clojure
#d "1.00"
```

</td><td colspan="1">

```clojure
#d "0.00"
```

</td></tr><tr><td colspan="2">

```clojure
#d "922337203685477580.7"
```

</td><td colspan="2">

```clojure
#d "-922337203685477580.8"
```

</td></tr><tr><td colspan="2">

```clojure
#d "9.223372036854775807"
```

</td><td colspan="2">

```clojure
#d "-9.223372036854775808"
```

</td></tr></table>

### Possible errors:

* [`h-err/overflow`](halite_err-id-reference.md#h-err/overflow)

### Tags:

 [Fixed-decimal operations](halite_fixed-decimal-op-reference.md),  [Produce fixed-decimals](halite_fixed-decimal-out-reference.md)

---
## <a name="instance"></a>instance

Represents an instance of a specification.

!["'{' ':$type' keyword:spec-id {keyword value} '}' "](../halite-bnf-diagrams/basic-syntax/instance.svg)

The contents of the instance are specified in pair-wise fashion with alternating field names and field values. The special field name ':$type' is mandatory but cannot be used as the other fields are. 

<table><tr><td colspan="2">

```clojure
{:$type :text/Spec$v1 :x 1 :y -1}
```

</td></tr></table>

### Possible errors:

* [`h-err/field-name-not-in-spec`](halite_err-id-reference.md#h-err/field-name-not-in-spec)
* [`h-err/field-value-of-wrong-type`](halite_err-id-reference.md#h-err/field-value-of-wrong-type)
* [`h-err/instance-threw`](halite_err-id-reference.md#h-err/instance-threw)
* [`h-err/invalid-instance`](halite_err-id-reference.md#h-err/invalid-instance)
* [`h-err/invalid-type-value`](halite_err-id-reference.md#h-err/invalid-type-value)
* [`h-err/missing-required-vars`](halite_err-id-reference.md#h-err/missing-required-vars)
* [`h-err/missing-type-field`](halite_err-id-reference.md#h-err/missing-type-field)
* [`h-err/no-abstract`](halite_err-id-reference.md#h-err/no-abstract)
* [`h-err/not-boolean-constraint`](halite_err-id-reference.md#h-err/not-boolean-constraint)
* [`h-err/refinement-diamond`](halite_err-id-reference.md#h-err/refinement-diamond)
* [`h-err/resource-spec-not-found`](halite_err-id-reference.md#h-err/resource-spec-not-found)
* [`h-err/spec-cycle-runtime`](halite_err-id-reference.md#h-err/spec-cycle-runtime)

### Tags:

 [Instance field operations](halite_instance-field-op-reference.md),  [Instance operations](halite_instance-op-reference.md),  [Produce instances](halite_instance-out-reference.md)

### How tos:

* [spec-fields](how-to/halite_spec-fields.md)


### Tutorials:

* [sudoku](tutorial/halite_sudoku.md)


---
## <a name="vector"></a>vector

A collection of values in a prescribed sequence.

!["'[' [whitespace] { value whitespace} [value] [whitespace] ']'"](../halite-bnf-diagrams/basic-syntax/vector.svg)

<table><tr><td colspan="1">

```clojure
[]
```

</td><td colspan="1">

```clojure
[1 2 3]
```

</td><td colspan="1">

```clojure
[#{1 2} #{3}]
```

</td></tr></table>

### Possible errors:

* [`h-err/literal-must-evaluate-to-value`](halite_err-id-reference.md#h-err/literal-must-evaluate-to-value)
* [`h-err/size-exceeded`](halite_err-id-reference.md#h-err/size-exceeded)
* [`h-err/unknown-type-collection`](halite_err-id-reference.md#h-err/unknown-type-collection)

### Tags:

 [Vector operations](halite_vector-op-reference.md),  [Produce vectors](halite_vector-out-reference.md)

---
## <a name="set"></a>set

A collection of values in an unordered set. Duplicates are not allowed.

!["'#' '{' [whitespace] { value [whitespace]} [value] [whitespace] '}'"](../halite-bnf-diagrams/basic-syntax/set.svg)

The members of sets are not directly accessible. If it is necessary to access the members of a set, it is recommended to design the data structures going into the sets in such a way that the set can be sorted into a vector for access.  

<table><tr><td colspan="1">

```clojure
#{}
```

</td><td colspan="1">

```clojure
#{1 2 3}
```

</td><td colspan="1">

```clojure
#{[1 2] [3]}
```

</td></tr></table>

### Possible errors:

* [`h-err/literal-must-evaluate-to-value`](halite_err-id-reference.md#h-err/literal-must-evaluate-to-value)
* [`h-err/size-exceeded`](halite_err-id-reference.md#h-err/size-exceeded)
* [`h-err/unknown-type-collection`](halite_err-id-reference.md#h-err/unknown-type-collection)

### Tags:

 [Set operations](halite_set-op-reference.md),  [Produce sets](halite_set-out-reference.md)

---
## <a name="value"></a>value

Expressions and many literals produce values.

!["boolean | string | integer | fixed-decimal | instance | vector | set"](../halite-bnf-diagrams/basic-syntax/value.svg)

---
## <a name="unset"></a>unset

A pseudo-value that represents the lack of a value.

!["unset"](../halite-bnf-diagrams/basic-syntax/unset.svg)

### Explanations:

* [unset](explanation/halite_unset.md)


---
## <a name="nothing"></a>nothing

The absence of a value.

!["nothing"](../halite-bnf-diagrams/basic-syntax/nothing.svg)

---
## <a name="any"></a>any

Refers to either the presence of absence of a value, or a pseudo-value indicating the lack of a value.

!["value | unset"](../halite-bnf-diagrams/basic-syntax/any.svg)

---
## <a name="comment"></a>comment

Comments that are not evaluated as part of the expression.

!["';' comment"](../halite-bnf-diagrams/basic-syntax/comment.svg)

---
# Type Graph![type graph](../types.dot.png)

