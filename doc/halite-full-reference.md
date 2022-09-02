<!---
  This markdown file was generated. Do not edit.
  -->

# Halite operator reference (all operators)

### <a name="_Dno-value"></a>$no-value

Constant that produces the special 'unset' value which represents the lack of a value.

![["" "unset"]](./halite-bnf-diagrams/op/%24no-value-0.svg)

Expected use is in an instance expression to indicate that a field in the instance does not have a value. However, it is suggested that alternatives include simply omitting the field name from the instance or using a variant of a 'when' expression to optionally produce a value for the field.

See also: [`when`](#when) [`when-value`](#when-value) [`when-value-let`](#when-value-let)

Tags: [Optionally produce values](halite-optional-out-reference.md)

---
### <a name="_Dthis"></a>$this

Context dependent reference to the containing object.

![["" "value"]](./halite-bnf-diagrams/op/%24this-0.svg)

---
### <a name="_S"></a>*

Multiply two numbers together.

![["integer integer" "integer"]](./halite-bnf-diagrams/op/*-0.svg)

![["fixed-decimal integer" "fixed-decimal"]](./halite-bnf-diagrams/op/*-1.svg)

Note that fixed-decimal values cannot be multiplied together. Rather the multiplication operator is used to scale a fixed-decimal value within the number space of a given scale of fixed-decimal. This can also be used to effectively convert an arbitrary integer value into a fixed-decimal number space by multiplying the integer by unity in the fixed-decimal number space of the desired scale.

<table><tr><td colspan="1">

```clojure
(* 2 3)

;-- result --;
6
```

</td><td colspan="1">

```clojure
(* #d "2.2" 3)

;-- result --;
#d "6.6"
```

</td><td colspan="1">

```clojure
(* 2 3 4)

;-- result --;
24
```

</td></tr></table>

#### Possible errors:

* [`h-err/overflow`](halite-err-id-reference.md#h-err/overflow)

Tags: [Fixed-decimal operations](halite-fixed-decimal-op-reference.md),  [Produce fixed-decimals](halite-fixed-decimal-out-reference.md),  [Integer operations](halite-integer-op-reference.md),  [Produce integer](halite-integer-out-reference.md)

---
### <a name="_A"></a>+

Add two numbers together.

![["integer integer {integer}" "integer"]](./halite-bnf-diagrams/op/plus-0.svg)

![["fixed-decimal fixed-decimal {fixed-decimal}" "fixed-decimal"]](./halite-bnf-diagrams/op/plus-1.svg)

<table><tr><td colspan="1">

```clojure
(+ 2 3)

;-- result --;
5
```

</td><td colspan="2">

```clojure
(+ #d "2.2" #d "3.3")

;-- result --;
#d "5.5"
```

</td><td colspan="1">

```clojure
(+ 2 3 4)

;-- result --;
9
```

</td><td colspan="1">

```clojure
(+ 2 -3)

;-- result --;
-1
```

</td></tr></table>

#### Possible errors:

* [`h-err/overflow`](halite-err-id-reference.md#h-err/overflow)

Tags: [Fixed-decimal operations](halite-fixed-decimal-op-reference.md),  [Produce fixed-decimals](halite-fixed-decimal-out-reference.md),  [Integer operations](halite-integer-op-reference.md),  [Produce integer](halite-integer-out-reference.md)

---
### <a name="-"></a>-

Subtract one number from another.

![["integer integer {integer}" "integer"]](./halite-bnf-diagrams/op/minus-0.svg)

![["fixed-decimal fixed-decimal {fixed-decimal}" "fixed-decimal"]](./halite-bnf-diagrams/op/minus-1.svg)

<table><tr><td colspan="1">

```clojure
(- 2 3)

;-- result --;
-1
```

</td><td colspan="2">

```clojure
(- #d "2.2" #d "3.3")

;-- result --;
#d "-1.1"
```

</td><td colspan="1">

```clojure
(- 2 3 4)

;-- result --;
-5
```

</td><td colspan="1">

```clojure
(- 2 -3)

;-- result --;
5
```

</td></tr></table>

#### Possible errors:

* [`h-err/overflow`](halite-err-id-reference.md#h-err/overflow)

Tags: [Fixed-decimal operations](halite-fixed-decimal-op-reference.md),  [Produce fixed-decimals](halite-fixed-decimal-out-reference.md),  [Integer operations](halite-integer-op-reference.md),  [Produce integer](halite-integer-out-reference.md)

---
### <a name="_L"></a><

Determine if a number is strictly less than another.

![["((integer integer) | (fixed-decimal fixed-decimal))" "boolean"]](./halite-bnf-diagrams/op/%3C-0.svg)

<table><tr><td colspan="1">

```clojure
(< 2 3)

;-- result --;
true
```

</td><td colspan="2">

```clojure
(< #d "2.2" #d "3.3")

;-- result --;
true
```

</td><td colspan="1">

```clojure
(< 2 2)

;-- result --;
false
```

</td></tr></table>

Tags: [Produce booleans](halite-boolean-out-reference.md),  [Fixed-decimal operations](halite-fixed-decimal-op-reference.md),  [Integer operations](halite-integer-op-reference.md)

---
### <a name="_L_E"></a><=

Determine if a number is less than or equal to another.

![["((integer integer) | (fixed-decimal fixed-decimal))" "boolean"]](./halite-bnf-diagrams/op/%3C%3D-0.svg)

<table><tr><td colspan="1">

```clojure
(<= 2 3)

;-- result --;
true
```

</td><td colspan="2">

```clojure
(<= #d "2.2" #d "3.3")

;-- result --;
true
```

</td><td colspan="1">

```clojure
(<= 2 2)

;-- result --;
true
```

</td></tr></table>

Tags: [Produce booleans](halite-boolean-out-reference.md),  [Fixed-decimal operations](halite-fixed-decimal-op-reference.md),  [Integer operations](halite-integer-op-reference.md)

---
### <a name="_E"></a>=

Determine if two values are equivalent. For vectors and sets this performs a comparison of their contents.

![["value value {value}" "boolean"]](./halite-bnf-diagrams/op/%3D-0.svg)

<table><tr><td colspan="1">

```clojure
(= 2 2)

;-- result --;
true
```

</td><td colspan="2">

```clojure
(= #d "2.2" #d "3.3")

;-- result --;
false
```

</td><td colspan="1">

```clojure
(= 2 3)

;-- result --;
false
```

</td><td colspan="1">

```clojure
(= "hi" "hi")

;-- result --;
true
```

</td></tr><tr><td colspan="1">

```clojure
(= [1 2 3] [1 2 3])

;-- result --;
true
```

</td><td colspan="1">

```clojure
(= [1 2 3] #{1 2 3})

;-- result --;
false
```

</td><td colspan="2">

```clojure
(= #{3 1 2} #{1 2 3})

;-- result --;
true
```

</td></tr><tr><td colspan="2">

```clojure
(= [#{1 2} #{3}] [#{1 2} #{3}])

;-- result --;
true
```

</td><td colspan="2">

```clojure
(= [#{1 2} #{3}] [#{1 2} #{4}])

;-- result --;
false
```

</td></tr><tr><td colspan="4">

```clojure
(= {:$type :my/Spec$v1 :x 1 :y -1} {:$type :my/Spec$v1 :x 1 :y 0})

;-- result --;
false
```

</td></tr><tr><td colspan="4">

```clojure
(= {:$type :my/Spec$v1 :x 1 :y 0} {:$type :my/Spec$v1 :x 1 :y 0})

;-- result --;
true
```

</td></tr></table>

#### Possible errors:

* [`l-err/result-always-known`](halite-err-id-reference.md#l-err/result-always-known)

See also: [`not=`](#not_E)

Tags: [Produce booleans](halite-boolean-out-reference.md),  [Fixed-decimal operations](halite-fixed-decimal-op-reference.md),  [Instance operations](halite-instance-op-reference.md),  [Integer operations](halite-integer-op-reference.md),  [Set operations](halite-set-op-reference.md),  [Vector operations](halite-vector-op-reference.md)

---
### <a name="_E_G"></a>=>

Performs logical implication. If the first value is true, then the second value must also be true for the result to be true. If the first value is false, then the result is true.

![["boolean boolean" "boolean"]](./halite-bnf-diagrams/op/%3D%3E-0.svg)

<table><tr><td colspan="1">

```clojure
(=> (> 2 1) (< 1 2))

;-- result --;
true
```

</td><td colspan="1">

```clojure
(=> (> 2 1) (> 1 2))

;-- result --;
false
```

</td><td colspan="1">

```clojure
(=> (> 1 2) false)

;-- result --;
true
```

</td></tr></table>

See also: [`and`](#and) [`every?`](#every_Q) [`not`](#not) [`or`](#or)

Tags: [Boolean operations](halite-boolean-op-reference.md),  [Produce booleans](halite-boolean-out-reference.md)

---
### <a name="_G"></a>>

Determine if a number is strictly greater than another.

![["((integer integer) | (fixed-decimal fixed-decimal))" "boolean"]](./halite-bnf-diagrams/op/%3E-0.svg)

<table><tr><td colspan="1">

```clojure
(> 3 2)

;-- result --;
true
```

</td><td colspan="2">

```clojure
(> #d "3.3" #d "2.2" )

;-- result --;
true
```

</td><td colspan="1">

```clojure
(> 2 2)

;-- result --;
false
```

</td></tr></table>

Tags: [Produce booleans](halite-boolean-out-reference.md),  [Fixed-decimal operations](halite-fixed-decimal-op-reference.md),  [Integer operations](halite-integer-op-reference.md)

---
### <a name="_G_E"></a>>=

Determine if a number is greater than or equal to another.

![["((integer integer) | (fixed-decimal fixed-decimal))" "boolean"]](./halite-bnf-diagrams/op/%3E%3D-0.svg)

<table><tr><td colspan="1">

```clojure
(>= 3 2)

;-- result --;
true
```

</td><td colspan="2">

```clojure
(>= #d "3.3" #d "2.2" )

;-- result --;
true
```

</td><td colspan="1">

```clojure
(>= 2 2)

;-- result --;
true
```

</td></tr></table>

Tags: [Produce booleans](halite-boolean-out-reference.md),  [Fixed-decimal operations](halite-fixed-decimal-op-reference.md),  [Integer operations](halite-integer-op-reference.md)

---
### <a name="abs"></a>abs

Compute the absolute value of a number.

![["integer" "integer"]](./halite-bnf-diagrams/op/abs-0.svg)

![["fixed-decimal" "fixed-decimal"]](./halite-bnf-diagrams/op/abs-1.svg)

Since the negative number space contains one more value than the positive number space, it is a runtime error to attempt to take the absolute value of the most negative value for a given number space.

<table><tr><td colspan="1">

```clojure
(abs -1)

;-- result --;
1
```

</td><td colspan="1">

```clojure
(abs 1)

;-- result --;
1
```

</td><td colspan="1">

```clojure
(abs #d "-1.0")

;-- result --;
#d "1.0"
```

</td></tr></table>

#### Possible errors:

* [`h-err/abs-failure`](halite-err-id-reference.md#h-err/abs-failure)

Tags: [Fixed-decimal operations](halite-fixed-decimal-op-reference.md),  [Produce fixed-decimals](halite-fixed-decimal-out-reference.md),  [Integer operations](halite-integer-op-reference.md),  [Produce integer](halite-integer-out-reference.md)

---
### <a name="and"></a>and

Perform a logical 'and' operation on the input values.

![["boolean boolean {boolean}" "boolean"]](./halite-bnf-diagrams/op/and-0.svg)

The operation does not short-circuit. Even if the first argument evaluates to false the other arguments are still evaluated.

<table><tr><td colspan="1">

```clojure
(and true false)

;-- result --;
false
```

</td><td colspan="1">

```clojure
(and true true)

;-- result --;
true
```

</td><td colspan="2">

```clojure
(and (> 2 1) (> 3 2) (> 4 3))

;-- result --;
true
```

</td></tr></table>

See also: [`=>`](#_E_G) [`every?`](#every_Q) [`not`](#not) [`or`](#or)

Tags: [Boolean operations](halite-boolean-op-reference.md),  [Produce booleans](halite-boolean-out-reference.md)

---
### <a name="any_Q"></a>any?

Evaluates to true if the boolean-expression is true when the symbol is bound to some element in the collection.

![["'[' symbol (set | vector) ']' boolean-expression" "boolean"]](./halite-bnf-diagrams/op/any%3F-0.svg)

The operation does not short-circuit. The boolean-expression is evaluated for all elements even if a prior element has caused the boolean-expression to evaluate to true. Operating on an empty collection produces a false value.

<table><tr><td colspan="2">

```clojure
(any? [x [1 2 3]] (> x 1))

;-- result --;
true
```

</td><td colspan="2">

```clojure
(any? [x #{1 2 3}] (> x 10))

;-- result --;
false
```

</td></tr></table>

#### Possible errors:

* [`h-err/binding-target-must-be-bare-symbol`](halite-err-id-reference.md#h-err/binding-target-must-be-bare-symbol)
* [`h-err/comprehend-binding-wrong-count`](halite-err-id-reference.md#h-err/comprehend-binding-wrong-count)
* [`h-err/comprehend-collection-invalid-type`](halite-err-id-reference.md#h-err/comprehend-collection-invalid-type)
* [`h-err/not-boolean-body`](halite-err-id-reference.md#h-err/not-boolean-body)
* [`l-err/binding-target-invalid-symbol`](halite-err-id-reference.md#l-err/binding-target-invalid-symbol)

See also: [`every?`](#every_Q) [`or`](#or)

Tags: [Produce booleans](halite-boolean-out-reference.md),  [Set operations](halite-set-op-reference.md),  [Special forms](halite-special-form-reference.md),  [Vector operations](halite-vector-op-reference.md)

---
### <a name="concat"></a>concat

Combine two collections into one.

![["vector vector" "vector"]](./halite-bnf-diagrams/op/concat-0.svg)

![["(set (set | vector))" "set"]](./halite-bnf-diagrams/op/concat-1.svg)

Invoking this operation with a vector and an empty set has the effect of converting a vector into a set with duplicate values removed.

<table><tr><td colspan="1">

```clojure
(concat [1 2] [3])

;-- result --;
[1 2 3]
```

</td><td colspan="2">

```clojure
(concat #{1 2} [3 4])

;-- result --;
#{1 4 3 2}
```

</td><td colspan="1">

```clojure
(concat [] [])

;-- result --;
[]
```

</td></tr></table>

#### Possible errors:

* [`h-err/not-both-vectors`](halite-err-id-reference.md#h-err/not-both-vectors)

Tags: [Set operations](halite-set-op-reference.md),  [Produce sets](halite-set-out-reference.md),  [Vector operations](halite-vector-op-reference.md),  [Produce vectors](halite-vector-out-reference.md)

---
### <a name="conj"></a>conj

Add individual items to a collection.

![["set value {value}" "set"]](./halite-bnf-diagrams/op/conj-0.svg)

![["vector value {value}" "vector"]](./halite-bnf-diagrams/op/conj-1.svg)

Only definite values may be put into collections, i.e. collections cannot contain 'unset' values.

<table><tr><td colspan="1">

```clojure
(conj [1 2] 3)

;-- result --;
[1 2 3]
```

</td><td colspan="1">

```clojure
(conj #{1 2} 3 4)

;-- result --;
#{1 4 3 2}
```

</td><td colspan="1">

```clojure
(conj [] 1)

;-- result --;
[1]
```

</td></tr></table>

#### Possible errors:

* [`h-err/argument-not-set-or-vector`](halite-err-id-reference.md#h-err/argument-not-set-or-vector)
* [`h-err/cannot-conj-unset`](halite-err-id-reference.md#h-err/cannot-conj-unset)

Tags: [Set operations](halite-set-op-reference.md),  [Produce sets](halite-set-out-reference.md),  [Vector operations](halite-vector-op-reference.md),  [Produce vectors](halite-vector-out-reference.md)

---
### <a name="contains_Q"></a>contains?

Determine if a specific value is in a set.

![["set value" "boolean"]](./halite-bnf-diagrams/op/contains%3F-0.svg)

Since collections themselves are compared by their contents, this works for collections nested inside of sets.

<table><tr><td colspan="2">

```clojure
(contains? #{"a" "b"} "a")

;-- result --;
true
```

</td><td colspan="2">

```clojure
(contains? #{"a" "b"} "c")

;-- result --;
false
```

</td></tr><tr><td colspan="2">

```clojure
(contains? #{#{1 2} #{3}} #{1 2})

;-- result --;
true
```

</td><td colspan="2">

```clojure
(contains? #{[1 2] [3]} [4])

;-- result --;
false
```

</td></tr></table>

Tags: [Produce booleans](halite-boolean-out-reference.md),  [Set operations](halite-set-op-reference.md)

---
### <a name="count"></a>count

Return how many items are in a collection.

![["(set | vector)" "integer"]](./halite-bnf-diagrams/op/count-0.svg)

<table><tr><td colspan="1">

```clojure
(count [10 20 30])

;-- result --;
3
```

</td><td colspan="1">

```clojure
(count #{"a" "b"})

;-- result --;
2
```

</td><td colspan="1">

```clojure
(count [])

;-- result --;
0
```

</td></tr></table>

Tags: [Produce integer](halite-integer-out-reference.md),  [Set operations](halite-set-op-reference.md),  [Vector operations](halite-vector-op-reference.md)

---
### <a name="dec"></a>dec

Decrement a numeric value.

![["integer" "integer"]](./halite-bnf-diagrams/op/dec-0.svg)

<table><tr><td colspan="1">

```clojure
(dec 10)

;-- result --;
9
```

</td><td colspan="1">

```clojure
(dec 0)

;-- result --;
-1
```

</td></tr></table>

#### Possible errors:

* [`h-err/overflow`](halite-err-id-reference.md#h-err/overflow)

See also: [`inc`](#inc)

Tags: [Integer operations](halite-integer-op-reference.md),  [Produce integer](halite-integer-out-reference.md)

---
### <a name="difference"></a>difference

Compute the set difference of two sets.

![["set set" "set"]](./halite-bnf-diagrams/op/difference-0.svg)

This produces a set which contains all of the elements from the first set which do not appear in the second set.

<table><tr><td colspan="2">

```clojure
(difference #{1 2 3} #{1 2})

;-- result --;
#{3}
```

</td><td colspan="2">

```clojure
(difference #{1 2 3} #{})

;-- result --;
#{1 3 2}
```

</td></tr><tr><td colspan="2">

```clojure
(difference #{1 2 3} #{1 2 3 4})

;-- result --;
#{}
```

</td><td colspan="2">

```clojure
(difference #{[1 2] [3]} #{[1 2]})

;-- result --;
#{[3]}
```

</td></tr></table>

#### Possible errors:

* [`h-err/arguments-not-sets`](halite-err-id-reference.md#h-err/arguments-not-sets)

See also: [`intersection`](#intersection) [`subset?`](#subset_Q) [`union`](#union)

Tags: [Set operations](halite-set-op-reference.md),  [Produce sets](halite-set-out-reference.md)

---
### <a name="div"></a>div

Divide the first number by the second. When the first argument is an integer the result is truncated to an integer value. When the first argument is a fixed-decimal the result is truncated to the same precision as the first argument.

![["integer integer" "integer"]](./halite-bnf-diagrams/op/div-0.svg)

![["fixed-decimal integer" "fixed-decimal"]](./halite-bnf-diagrams/op/div-1.svg)

As with multiplication, fixed-decimal values cannot be divided by each other, instead a fixed-decimal value can be scaled down within the number space of that scale.

<table><tr><td colspan="1">

```clojure
(div 12 3)

;-- result --;
4
```

</td><td colspan="1">

```clojure
(div #d "12.3" 3)

;-- result --;
#d "4.1"
```

</td><td colspan="1">

```clojure
(div 14 4)

;-- result --;
3
```

</td><td colspan="1">

```clojure
(div #d "14.3" 3)

;-- result --;
#d "4.7"
```

</td><td colspan="1">

```clojure
(div 1 0)

;-- result --;
h-err/divide-by-zero
```

</td></tr></table>

#### Possible errors:

* [`h-err/divide-by-zero`](halite-err-id-reference.md#h-err/divide-by-zero)

See also: [`mod`](#mod)

Tags: [Fixed-decimal operations](halite-fixed-decimal-op-reference.md),  [Produce fixed-decimals](halite-fixed-decimal-out-reference.md),  [Integer operations](halite-integer-op-reference.md),  [Produce integer](halite-integer-out-reference.md)

---
### <a name="error"></a>error

Produce a runtime error with the provided string as an error message.

![["string" "nothing"]](./halite-bnf-diagrams/op/error-0.svg)

Used to indicate when an unexpected condition has occurred and the data at hand is invalid. It is preferred to use constraints to capture such conditions earlier.

<table><tr><td colspan="1">

```clojure
(error "failure")

;-- result --;
h-err/spec-threw
```

</td></tr></table>

#### Possible errors:

* [`h-err/spec-threw`](halite-err-id-reference.md#h-err/spec-threw)

Tags: [Produce nothing](halite-nothing-out-reference.md)

---
### <a name="every_Q"></a>every?

Evaluates to true if the boolean-expression is true when the symbol is bound to each the element in the collection.

![["'[' symbol (set | vector) ']' boolean-expression" "boolean"]](./halite-bnf-diagrams/op/every%3F-0.svg)

Does not short-circuit. The boolean-expression is evaluated for all elements, even once a prior element has evaluated to false. Operating on an empty collection produces a true value.

<table><tr><td colspan="2">

```clojure
(every? [x [1 2 3]] (> x 0))

;-- result --;
true
```

</td><td colspan="2">

```clojure
(every? [x #{1 2 3}] (> x 1))

;-- result --;
false
```

</td></tr></table>

#### Possible errors:

* [`h-err/binding-target-must-be-bare-symbol`](halite-err-id-reference.md#h-err/binding-target-must-be-bare-symbol)
* [`h-err/comprehend-binding-wrong-count`](halite-err-id-reference.md#h-err/comprehend-binding-wrong-count)
* [`h-err/comprehend-collection-invalid-type`](halite-err-id-reference.md#h-err/comprehend-collection-invalid-type)
* [`h-err/not-boolean-body`](halite-err-id-reference.md#h-err/not-boolean-body)
* [`l-err/binding-target-invalid-symbol`](halite-err-id-reference.md#l-err/binding-target-invalid-symbol)

See also: [`and`](#and) [`any?`](#any_Q)

Tags: [Produce booleans](halite-boolean-out-reference.md),  [Set operations](halite-set-op-reference.md),  [Special forms](halite-special-form-reference.md),  [Vector operations](halite-vector-op-reference.md)

---
### <a name="expt"></a>expt

Compute the numeric result of raising the first argument to the power given by the second argument. The exponent argument cannot be negative.

![["integer integer" "integer"]](./halite-bnf-diagrams/op/expt-0.svg)

<table><tr><td colspan="1">

```clojure
(expt 2 3)

;-- result --;
8
```

</td><td colspan="1">

```clojure
(expt -2 3)

;-- result --;
-8
```

</td><td colspan="1">

```clojure
(expt 2 0)

;-- result --;
1
```

</td><td colspan="2">

```clojure
(expt 2 -1)

;-- result --;
h-err/invalid-exponent
```

</td></tr></table>

#### Possible errors:

* [`h-err/invalid-exponent`](halite-err-id-reference.md#h-err/invalid-exponent)
* [`h-err/overflow`](halite-err-id-reference.md#h-err/overflow)

Tags: [Integer operations](halite-integer-op-reference.md),  [Produce integer](halite-integer-out-reference.md)

---
### <a name="filter"></a>filter

Produce a new collection which contains only the elements from the original collection for which the boolean-expression is true. When applied to a vector, the order of the elements in the result preserves the order from the original vector.

![["'[' symbol:element set ']' boolean-expression" "set"]](./halite-bnf-diagrams/op/filter-0.svg)

![["'[' symbol:element vector ']' boolean-expression" "vector"]](./halite-bnf-diagrams/op/filter-1.svg)

<table><tr><td colspan="2">

```clojure
(filter [x [1 2 3]] (> x 2))

;-- result --;
[3]
```

</td><td colspan="2">

```clojure
(filter [x #{1 2 3}] (> x 2))

;-- result --;
#{3}
```

</td></tr></table>

#### Possible errors:

* [`h-err/binding-target-must-be-bare-symbol`](halite-err-id-reference.md#h-err/binding-target-must-be-bare-symbol)
* [`h-err/comprehend-binding-wrong-count`](halite-err-id-reference.md#h-err/comprehend-binding-wrong-count)
* [`h-err/comprehend-collection-invalid-type`](halite-err-id-reference.md#h-err/comprehend-collection-invalid-type)
* [`h-err/not-boolean-body`](halite-err-id-reference.md#h-err/not-boolean-body)
* [`l-err/binding-target-invalid-symbol`](halite-err-id-reference.md#l-err/binding-target-invalid-symbol)

See also: [`filter`](#filter) [`map`](#map)

Tags: [Set operations](halite-set-op-reference.md),  [Produce sets](halite-set-out-reference.md),  [Special forms](halite-special-form-reference.md),  [Vector operations](halite-vector-op-reference.md),  [Produce vectors](halite-vector-out-reference.md)

---
### <a name="first"></a>first

Produce the first element from a vector.

![["vector" "value"]](./halite-bnf-diagrams/op/first-0.svg)

To avoid runtime errors, if the vector might be empty, use 'count' to check the length first.

<table><tr><td colspan="1">

```clojure
(first [10 20 30])

;-- result --;
10
```

</td><td colspan="1">

```clojure
(first [])

;-- result --;
h-err/argument-empty
```

</td></tr></table>

#### Possible errors:

* [`h-err/argument-empty`](halite-err-id-reference.md#h-err/argument-empty)
* [`h-err/argument-not-vector`](halite-err-id-reference.md#h-err/argument-not-vector)

See also: [`count`](#count) [`rest`](#rest)

Tags: [Vector operations](halite-vector-op-reference.md)

---
### <a name="get"></a>get

Extract the given item from the first argument. If the first argument is an instance, extract the value for the given field from the given instance. For optional fields, this may produce 'unset'. Otherwise this will always produce a value. If the first argument is a vector, then extract the value at the given index in the vector. The index in this case is zero based.

![["(instance keyword:instance-field)" "any"]](./halite-bnf-diagrams/op/get-0.svg)

![["(vector integer)" "value"]](./halite-bnf-diagrams/op/get-1.svg)

The $type value of an instance is not considered a field that can be extracted with this operator. When dealing with instances of abstract specifications, it is necessary to refine an instance to a given specification before accessing a field of that specification.

<table><tr><td colspan="2">

```clojure
(get [10 20 30 40] 2)

;-- result --;
30
```

</td><td colspan="3">

```clojure
(get {:$type :my/Spec$v1, :x -3, :y 2} :x)

;-- result --;
-3
```

</td></tr></table>

#### Possible errors:

* [`h-err/field-name-not-in-spec`](halite-err-id-reference.md#h-err/field-name-not-in-spec)
* [`h-err/index-out-of-bounds`](halite-err-id-reference.md#h-err/index-out-of-bounds)
* [`h-err/invalid-instance-index`](halite-err-id-reference.md#h-err/invalid-instance-index)
* [`h-err/invalid-vector-index`](halite-err-id-reference.md#h-err/invalid-vector-index)

See also: [`get-in`](#get-in)

Tags: [Instance field operations](halite-instance-field-op-reference.md),  [Instance operations](halite-instance-op-reference.md),  [Optionally produce values](halite-optional-out-reference.md),  [Special forms](halite-special-form-reference.md),  [Vector operations](halite-vector-op-reference.md)

---
### <a name="get-in"></a>get-in

Syntactic sugar for performing the equivalent of a chained series of 'get' operations. The second argument is a vector that represents the logical path to be navigated through the first argument.

The first path element in the path is looked up in the initial target. If there are more path elements, the next path element is looked up in the result of the first lookup. This is repeated as long as there are more path elements. If this is used to lookup instance fields, then all of the field names must reference mandatory fields unless the field name is the final element of the path. The result will always be a value unless the final path element is a reference to an optional field. In this case, the result may be a value or may be 'unset'.

![["(instance:target | vector:target) '[' (integer | keyword:instance-field) {(integer | keyword:instance-field)} ']'" "any"]](./halite-bnf-diagrams/op/get-in-0.svg)

<table><tr><td colspan="2">

```clojure
(get-in [[10 20] [30 40]] [1 0])

;-- result --;
30
```

</td></tr><tr><td colspan="5">

```clojure
(get-in {:$type :my/Spec$v1, :x {:$type :my/SubSpec$v1, :a 20, :b 10}, :y 2} [:x :a])

;-- result --;
20
```

</td></tr><tr><td colspan="5">

```clojure
(get-in {:$type :my/Spec$v1, :x {:$type :my/SubSpec$v1, :a [20 30 40], :b 10}, :y 2} [:x :a 1])

;-- result --;
30
```

</td></tr></table>

#### Possible errors:

* [`h-err/field-name-not-in-spec`](halite-err-id-reference.md#h-err/field-name-not-in-spec)
* [`h-err/get-in-path-must-be-vector-literal`](halite-err-id-reference.md#h-err/get-in-path-must-be-vector-literal)
* [`h-err/index-out-of-bounds`](halite-err-id-reference.md#h-err/index-out-of-bounds)
* [`h-err/invalid-instance-index`](halite-err-id-reference.md#h-err/invalid-instance-index)
* [`h-err/invalid-lookup-target`](halite-err-id-reference.md#h-err/invalid-lookup-target)
* [`h-err/invalid-vector-index`](halite-err-id-reference.md#h-err/invalid-vector-index)
* [`l-err/get-in-path-empty`](halite-err-id-reference.md#l-err/get-in-path-empty)

See also: [`get`](#get)

Tags: [Instance field operations](halite-instance-field-op-reference.md),  [Instance operations](halite-instance-op-reference.md),  [Optionally produce values](halite-optional-out-reference.md),  [Special forms](halite-special-form-reference.md),  [Vector operations](halite-vector-op-reference.md)

---
### <a name="if"></a>if

If the first argument is true, then evaluate the second argument, otherwise evaluate the third argument.

![["boolean any-expression any-expression" "any"]](./halite-bnf-diagrams/op/if-0.svg)

<table><tr><td colspan="1">

```clojure
(if (> 2 1) 10 -1)

;-- result --;
10
```

</td><td colspan="2">

```clojure
(if (> 2 1) 10 (error "fail"))

;-- result --;
10
```

</td></tr></table>

See also: [`when`](#when)

Tags: [Boolean operations](halite-boolean-op-reference.md),  [Control flow](halite-control-flow-reference.md),  [Special forms](halite-special-form-reference.md)

---
### <a name="if-value"></a>if-value

Consider the value bound to the symbol. If it is a 'value', then evaluate the second argument. If instead it is 'unset' then evaluate the third argument.

![["symbol any-expression any-expression" "any"]](./halite-bnf-diagrams/op/if-value-0.svg)

When an optional instance field needs to be referenced, it is generally necessary to guard the access with either 'if-value' or 'when-value'. In this way, both the case of the field being set and unset are explicitly handled.

#### Possible errors:

* [`h-err/if-value-must-be-bare-symbol`](halite-err-id-reference.md#h-err/if-value-must-be-bare-symbol)
* [`l-err/first-argument-not-optional`](halite-err-id-reference.md#l-err/first-argument-not-optional)

See also: [`if-value-let`](#if-value-let) [`when-value`](#when-value)

Tags: [Control flow](halite-control-flow-reference.md),  [Optional operations](halite-optional-op-reference.md),  [Special forms](halite-special-form-reference.md)

---
### <a name="if-value-let"></a>if-value-let

If the binding value is a 'value' then evaluate the second argument with the symbol bound to binding. If instead, the binding value is 'unset', then evaluate the third argument without introducing a new binding for the symbol.

![["'[' symbol any:binding ']' any-expression any-expression" "any"]](./halite-bnf-diagrams/op/if-value-let-0.svg)

This is similar to the 'if-value' operation, but applies generally to an expression which may or may not produce a value.

<table><tr><td colspan="3">

```clojure
(if-value-let [x (when (> 2 1) 19)] (inc x) 0)

;-- result --;
20
```

</td></tr><tr><td colspan="3">

```clojure
(if-value-let [x (when (> 1 2) 19)] (inc x) 0)

;-- result --;
0
```

</td></tr></table>

#### Possible errors:

* [`h-err/binding-target-must-be-bare-symbol`](halite-err-id-reference.md#h-err/binding-target-must-be-bare-symbol)
* [`l-err/binding-expression-not-optional`](halite-err-id-reference.md#l-err/binding-expression-not-optional)

See also: [`if-value`](#if-value) [`when-value-let`](#when-value-let)

Tags: [Control flow](halite-control-flow-reference.md),  [Optional operations](halite-optional-op-reference.md),  [Special forms](halite-special-form-reference.md)

---
### <a name="inc"></a>inc

Increment a numeric value.

![["integer" "integer"]](./halite-bnf-diagrams/op/inc-0.svg)

<table><tr><td colspan="1">

```clojure
(inc 10)

;-- result --;
11
```

</td><td colspan="1">

```clojure
(inc 0)

;-- result --;
1
```

</td></tr></table>

#### Possible errors:

* [`h-err/overflow`](halite-err-id-reference.md#h-err/overflow)

See also: [`dec`](#dec)

Tags: [Integer operations](halite-integer-op-reference.md),  [Produce integer](halite-integer-out-reference.md)

---
### <a name="intersection"></a>intersection

Compute the set intersection of the sets.

![["set set {set}" "set"]](./halite-bnf-diagrams/op/intersection-0.svg)

This produces a set which only contains values that appear in each of the arguments.

<table><tr><td colspan="2">

```clojure
(intersection #{1 2 3} #{2 3 4})

;-- result --;
#{3 2}
```

</td><td colspan="2">

```clojure
(intersection #{1 2 3} #{2 3 4} #{3 4})

;-- result --;
#{3}
```

</td></tr></table>

#### Possible errors:

* [`h-err/arguments-not-sets`](halite-err-id-reference.md#h-err/arguments-not-sets)

See also: [`difference`](#difference) [`subset?`](#subset_Q) [`union`](#union)

Tags: [Set operations](halite-set-op-reference.md),  [Produce sets](halite-set-out-reference.md)

---
### <a name="let"></a>let

Evaluate the expression argument in a nested context created by considering the first argument in a pairwise fashion and binding each symbol to the corresponding value.

![["'[' symbol value {symbol value} ']' any-expression" "any"]](./halite-bnf-diagrams/op/let-0.svg)

Allows names to be given to values so that they can be referenced by the any-expression.

<table><tr><td colspan="1">

```clojure
(let [x 1] (inc x))

;-- result --;
2
```

</td><td colspan="2">

```clojure
(let [x 1 y 2] (+ x y))

;-- result --;
3
```

</td><td colspan="2">

```clojure
(let [x 1] (let [x 2] x))

;-- result --;
2
```

</td></tr></table>

#### Possible errors:

* [`h-err/cannot-bind-reserved-word`](halite-err-id-reference.md#h-err/cannot-bind-reserved-word)
* [`h-err/let-bindings-odd-count`](halite-err-id-reference.md#h-err/let-bindings-odd-count)
* [`h-err/let-needs-bare-symbol`](halite-err-id-reference.md#h-err/let-needs-bare-symbol)
* [`l-err/binding-target-invalid-symbol`](halite-err-id-reference.md#l-err/binding-target-invalid-symbol)
* [`l-err/cannot-bind-nothing`](halite-err-id-reference.md#l-err/cannot-bind-nothing)
* [`l-err/cannot-bind-unset`](halite-err-id-reference.md#l-err/cannot-bind-unset)
* [`l-err/disallowed-unset-variable`](halite-err-id-reference.md#l-err/disallowed-unset-variable)
* [`l-err/let-bindings-empty`](halite-err-id-reference.md#l-err/let-bindings-empty)

Tags: [Special forms](halite-special-form-reference.md)

---
### <a name="map"></a>map

Produce a new collection from a collection by evaluating the expression with the symbol bound to each element of the original collection, one-by-one. The results of evaluating the expression will be in the resulting collection. When operating on a vector, the order of the output vector will correspond to the order of the items in the original vector.

![["'[' symbol:element set ']' value-expression" "set"]](./halite-bnf-diagrams/op/map-0.svg)

![["'[' symbol:element vector ']' value-expression" "vector"]](./halite-bnf-diagrams/op/map-1.svg)

<table><tr><td colspan="2">

```clojure
(map [x [10 11 12]] (inc x))

;-- result --;
[11 12 13]
```

</td><td colspan="2">

```clojure
(map [x #{10 12}] (* x 2))

;-- result --;
#{20 24}
```

</td></tr></table>

#### Possible errors:

* [`h-err/binding-target-must-be-bare-symbol`](halite-err-id-reference.md#h-err/binding-target-must-be-bare-symbol)
* [`h-err/comprehend-binding-wrong-count`](halite-err-id-reference.md#h-err/comprehend-binding-wrong-count)
* [`h-err/comprehend-collection-invalid-type`](halite-err-id-reference.md#h-err/comprehend-collection-invalid-type)
* [`h-err/must-produce-value`](halite-err-id-reference.md#h-err/must-produce-value)
* [`l-err/binding-target-invalid-symbol`](halite-err-id-reference.md#l-err/binding-target-invalid-symbol)

See also: [`filter`](#filter) [`reduce`](#reduce)

Tags: [Set operations](halite-set-op-reference.md),  [Produce sets](halite-set-out-reference.md),  [Special forms](halite-special-form-reference.md),  [Vector operations](halite-vector-op-reference.md),  [Produce vectors](halite-vector-out-reference.md)

---
### <a name="mod"></a>mod

Computes the mathematical modulus of two numbers. Use care if one of the arguments is negative.

![["integer integer" "integer"]](./halite-bnf-diagrams/op/mod-0.svg)

<table><tr><td colspan="1">

```clojure
(mod 12 3)

;-- result --;
0
```

</td><td colspan="1">

```clojure
(mod 14 4)

;-- result --;
2
```

</td><td colspan="1">

```clojure
(mod 1 0)

;-- result --;
h-err/divide-by-zero
```

</td></tr></table>

#### Possible errors:

* [`h-err/divide-by-zero`](halite-err-id-reference.md#h-err/divide-by-zero)

Tags: [Integer operations](halite-integer-op-reference.md),  [Produce integer](halite-integer-out-reference.md)

---
### <a name="not"></a>not

Performs logical negation of the argument.

![["boolean" "boolean"]](./halite-bnf-diagrams/op/not-0.svg)

<table><tr><td colspan="1">

```clojure
(not true)

;-- result --;
false
```

</td><td colspan="1">

```clojure
(not false)

;-- result --;
true
```

</td></tr></table>

See also: [`=>`](#_E_G) [`and`](#and) [`or`](#or)

Tags: [Boolean operations](halite-boolean-op-reference.md),  [Produce booleans](halite-boolean-out-reference.md)

---
### <a name="not_E"></a>not=

Produces a false value if all of the values are equal to each other. Otherwise produces a true value.

![["value value {value}" "boolean"]](./halite-bnf-diagrams/op/not%3D-0.svg)

<table><tr><td colspan="1">

```clojure
(not= 2 3)

;-- result --;
true
```

</td><td colspan="2">

```clojure
(not= #d "2.2" #d "2.2")

;-- result --;
false
```

</td><td colspan="1">

```clojure
(not= 2 2)

;-- result --;
false
```

</td><td colspan="1">

```clojure
(not= "hi" "bye")

;-- result --;
true
```

</td></tr><tr><td colspan="2">

```clojure
(not= [1 2 3] [1 2 3 4])

;-- result --;
true
```

</td><td colspan="2">

```clojure
(not= [1 2 3] #{1 2 3})

;-- result --;
true
```

</td></tr><tr><td colspan="2">

```clojure
(not= #{3 1 2} #{1 2 3})

;-- result --;
false
```

</td><td colspan="2">

```clojure
(not= [#{1 2} #{3}] [#{1 2} #{3}])

;-- result --;
false
```

</td></tr><tr><td colspan="2">

```clojure
(not= [#{1 2} #{3}] [#{1 2} #{4}])

;-- result --;
true
```

</td></tr><tr><td colspan="4">

```clojure
(not= {:$type :my/Spec$v1 :x 1 :y -1} {:$type :my/Spec$v1 :x 1 :y 0})

;-- result --;
true
```

</td><td colspan="1">

```clojure

```

</td></tr></table>

#### Possible errors:

* [`l-err/result-always-known`](halite-err-id-reference.md#l-err/result-always-known)

See also: [`=`](#_E)

Tags: [Boolean operations](halite-boolean-op-reference.md),  [Produce booleans](halite-boolean-out-reference.md),  [Fixed-decimal operations](halite-fixed-decimal-op-reference.md),  [Instance operations](halite-instance-op-reference.md),  [Integer operations](halite-integer-op-reference.md),  [Set operations](halite-set-op-reference.md),  [Vector operations](halite-vector-op-reference.md)

---
### <a name="or"></a>or

Perform a logical 'or' operation on the input values.

![["boolean boolean {boolean}" "boolean"]](./halite-bnf-diagrams/op/or-0.svg)

The operation does not short-circuit. Even if the first argument evaluates to true the other arguments are still evaluated.

<table><tr><td colspan="1">

```clojure
(or true false)

;-- result --;
true
```

</td><td colspan="1">

```clojure
(or false false)

;-- result --;
false
```

</td><td colspan="2">

```clojure
(or (> 1 2) (> 2 3) (> 4 3))

;-- result --;
true
```

</td></tr></table>

See also: [`=>`](#_E_G) [`and`](#and) [`any?`](#any_Q) [`not`](#not)

Tags: [Boolean operations](halite-boolean-op-reference.md),  [Produce booleans](halite-boolean-out-reference.md)

---
### <a name="range"></a>range

Produce a vector that contains integers in order starting at either the start value or 0 if no start is provided. The final element of the vector will be no more than one less than the end value. If an increment is provided then only every increment integer will be included in the result.

![["[integer:start] integer:end [integer:increment]" "vector"]](./halite-bnf-diagrams/op/range-0.svg)

<table><tr><td colspan="1">

```clojure
(range 3)

;-- result --;
[0 1 2]
```

</td><td colspan="1">

```clojure
(range 10 12)

;-- result --;
[10 11]
```

</td><td colspan="1">

```clojure
(range 10 21 5)

;-- result --;
[10 15 20]
```

</td></tr></table>

Tags: [Produce vectors](halite-vector-out-reference.md)

---
### <a name="reduce"></a>reduce

Evalue the expression repeatedly for each element in the vector. The accumulator value will have a value of accumulator-init on the first evaluation of the expression. Subsequent evaluations of the expression will chain the prior result in as the value of the accumulator. The result of the final evaluation of the expression will be produced as the result of the reduce operation. The elements are processed in order.

![["'[' symbol:accumulator value:accumulator-init ']' '[' symbol:element vector ']' any-expression" "any"]](./halite-bnf-diagrams/op/reduce-0.svg)

<table><tr><td colspan="2">

```clojure
(reduce [a 10] [x [1 2 3]] (+ a x))

;-- result --;
16
```

</td></tr></table>

#### Possible errors:

* [`h-err/accumulator-target-must-be-bare-symbol`](halite-err-id-reference.md#h-err/accumulator-target-must-be-bare-symbol)
* [`h-err/binding-target-must-be-bare-symbol`](halite-err-id-reference.md#h-err/binding-target-must-be-bare-symbol)
* [`h-err/element-accumulator-same-symbol`](halite-err-id-reference.md#h-err/element-accumulator-same-symbol)
* [`h-err/element-binding-target-must-be-bare-symbol`](halite-err-id-reference.md#h-err/element-binding-target-must-be-bare-symbol)
* [`h-err/reduce-not-vector`](halite-err-id-reference.md#h-err/reduce-not-vector)

See also: [`filter`](#filter) [`map`](#map)

Tags: [Special forms](halite-special-form-reference.md),  [Vector operations](halite-vector-op-reference.md)

---
### <a name="refine-to"></a>refine-to

Attempt to refine the given instance into an instance of type, spec-id.

![["instance keyword:spec-id" "instance"]](./halite-bnf-diagrams/op/refine-to-0.svg)

<table><tr><td colspan="3">

```clojure
(refine-to {:$type :my/Spec$v1, :p 1, :n -1} :my/Other$v1)

;-- result --;
{:$type :my/Other$v1, :x 2, :y 0}
```

</td></tr><tr><td colspan="3">

```clojure
(refine-to {:$type :my/Spec$v1, :p 1, :n -1} :my/Other$v1)

;-- result --;
h-err/no-refinement-path
```

</td></tr></table>

#### Possible errors:

* [`h-err/invalid-refinement-expression`](halite-err-id-reference.md#h-err/invalid-refinement-expression)
* [`h-err/no-refinement-path`](halite-err-id-reference.md#h-err/no-refinement-path)
* [`h-err/refinement-error`](halite-err-id-reference.md#h-err/refinement-error)
* [`h-err/resource-spec-not-found`](halite-err-id-reference.md#h-err/resource-spec-not-found)

See also: [`refines-to?`](#refines-to_Q)

Tags: [Instance operations](halite-instance-op-reference.md),  [Produce instances](halite-instance-out-reference.md),  [Spec-id operations](halite-spec-id-op-reference.md)

---
### <a name="refines-to_Q"></a>refines-to?

Determine whether it is possible to refine the given instance into an instance of type, spec-id.

![["instance keyword:spec-id" "boolean"]](./halite-bnf-diagrams/op/refines-to%3F-0.svg)

<table><tr><td colspan="3">

```clojure
(refines-to? {:$type :my/Spec$v1, :p 1, :n -1} :my/Other$v1)

;-- result --;
true
```

</td></tr><tr><td colspan="3">

```clojure
(refines-to? {:$type :my/Spec$v1, :p 1, :n -1} :my/Other$v1)

;-- result --;
false
```

</td></tr></table>

#### Possible errors:

* [`h-err/invalid-refinement-expression`](halite-err-id-reference.md#h-err/invalid-refinement-expression)
* [`h-err/refinement-error`](halite-err-id-reference.md#h-err/refinement-error)
* [`h-err/resource-spec-not-found`](halite-err-id-reference.md#h-err/resource-spec-not-found)

See also: [`refine-to`](#refine-to)

Tags: [Produce booleans](halite-boolean-out-reference.md),  [Instance operations](halite-instance-op-reference.md),  [Spec-id operations](halite-spec-id-op-reference.md)

---
### <a name="rescale"></a>rescale

Produce a number by adjusting the scale of the fixed-decimal to the new-scale. If the scale is being reduced, the original number is truncated. If the scale is being increased, then the original number is padded with zeroes in the decimal places. If the new-scale is zero, then the result is an integer.

![["fixed-decimal integer:new-scale" "(fixed-decimal | integer)"]](./halite-bnf-diagrams/op/rescale-0.svg)

Arithmetic on numeric values never produce results in different number spaces. This operation provides an explicit way to convert a fixed-decimal value into a value with the scale of a different number space. This includes the ability to convert a fixed-decimal value into an integer.

<table><tr><td colspan="2">

```clojure
(rescale #d "1.23" 1)

;-- result --;
#d "1.2"
```

</td><td colspan="2">

```clojure
(rescale #d "1.23" 2)

;-- result --;
#d "1.23"
```

</td></tr><tr><td colspan="2">

```clojure
(rescale #d "1.23" 3)

;-- result --;
#d "1.230"
```

</td><td colspan="2">

```clojure
(rescale #d "1.23" 0)

;-- result --;
1
```

</td></tr></table>

See also: [`*`](#_S)

Tags: [Fixed-decimal operations](halite-fixed-decimal-op-reference.md),  [Produce fixed-decimals](halite-fixed-decimal-out-reference.md),  [Produce integer](halite-integer-out-reference.md)

---
### <a name="rest"></a>rest

Produce a new vector which contains the same element of the argument, in the same order, except the first element is removed. If there are no elements in the argument, then an empty vector is produced.

![["vector" "vector"]](./halite-bnf-diagrams/op/rest-0.svg)

<table><tr><td colspan="1">

```clojure
(rest [1 2 3])

;-- result --;
[2 3]
```

</td><td colspan="1">

```clojure
(rest [1 2])

;-- result --;
[2]
```

</td><td colspan="1">

```clojure
(rest [1])

;-- result --;
[]
```

</td><td colspan="1">

```clojure
(rest [])

;-- result --;
[]
```

</td></tr></table>

#### Possible errors:

* [`h-err/argument-not-vector`](halite-err-id-reference.md#h-err/argument-not-vector)

Tags: [Vector operations](halite-vector-op-reference.md),  [Produce vectors](halite-vector-out-reference.md)

---
### <a name="sort"></a>sort

Produce a new vector by sorting all of the items in the argument. Only collections of numeric values may be sorted.

![["(set | vector)" "vector"]](./halite-bnf-diagrams/op/sort-0.svg)

<table><tr><td colspan="1">

```clojure
(sort [2 1 3])

;-- result --;
[1 2 3]
```

</td><td colspan="2">

```clojure
(sort [#d "3.3" #d "1.1" #d "2.2"])

;-- result --;
[#d "1.1" #d "2.2" #d "3.3"]
```

</td></tr></table>

See also: [`sort-by`](#sort-by)

Tags: [Set operations](halite-set-op-reference.md),  [Vector operations](halite-vector-op-reference.md),  [Produce vectors](halite-vector-out-reference.md)

---
### <a name="sort-by"></a>sort-by

Produce a new vector by sorting all of the items in the input collection according to the values produced by applying the expression to each element. The expression must produce a unique, sortable value for each element.

![["'[' symbol:element (set | vector) ']' (integer-expression | fixed-decimal-expression)" "vector"]](./halite-bnf-diagrams/op/sort-by-0.svg)

<table><tr><td colspan="3">

```clojure
(sort-by [x [[10 20] [30] [1 2 3]]] (first x))

;-- result --;
[[1 2 3] [10 20] [30]]
```

</td></tr></table>

#### Possible errors:

* [`h-err/binding-target-must-be-bare-symbol`](halite-err-id-reference.md#h-err/binding-target-must-be-bare-symbol)
* [`h-err/not-sortable-body`](halite-err-id-reference.md#h-err/not-sortable-body)
* [`h-err/sort-value-collision`](halite-err-id-reference.md#h-err/sort-value-collision)
* [`l-err/binding-target-invalid-symbol`](halite-err-id-reference.md#l-err/binding-target-invalid-symbol)

See also: [`sort`](#sort)

Tags: [Set operations](halite-set-op-reference.md),  [Special forms](halite-special-form-reference.md),  [Vector operations](halite-vector-op-reference.md),  [Produce vectors](halite-vector-out-reference.md)

---
### <a name="str"></a>str

Combine all of the input strings together in sequence to produce a new string.

![["string string {string}" "string"]](./halite-bnf-diagrams/op/str-0.svg)

<table><tr><td colspan="1">

```clojure
(str "a" "b")

;-- result --;
"ab"
```

</td><td colspan="1">

```clojure
(str "a" "" "c")

;-- result --;
"ac"
```

</td></tr></table>

#### Possible errors:

* [`h-err/size-exceeded`](halite-err-id-reference.md#h-err/size-exceeded)

Tags: [String operations](halite-string-op-reference.md)

---
### <a name="subset_Q"></a>subset?

Return false if there are any items in the first set which do not appear in the second set. Otherwise return true.

![["set set" "boolean"]](./halite-bnf-diagrams/op/subset%3F-0.svg)

According to this operation, a set is always a subset of itself and every set is a subset of the empty set. Using this operation and an equality check in combination allows a 'superset?' predicate to be computed.

<table><tr><td colspan="2">

```clojure
(subset? #{1 2} #{1 2 3 4})

;-- result --;
true
```

</td><td colspan="2">

```clojure
(subset? #{1 5} #{1 2})

;-- result --;
false
```

</td></tr><tr><td colspan="2">

```clojure
(subset? #{1 2} #{1 2})

;-- result --;
true
```

</td></tr></table>

See also: [`difference`](#difference) [`intersection`](#intersection) [`union`](#union)

Tags: [Produce booleans](halite-boolean-out-reference.md),  [Set operations](halite-set-op-reference.md)

---
### <a name="union"></a>union

Compute the union of all the sets.

![["set set {set}" "set"]](./halite-bnf-diagrams/op/union-0.svg)

This produces a set which contains all of the values that appear in any of the arguments.

<table><tr><td colspan="1">

```clojure
(union #{1} #{2 3})

;-- result --;
#{1 3 2}
```

</td><td colspan="2">

```clojure
(union #{1} #{2 3} #{4})

;-- result --;
#{1 4 3 2}
```

</td><td colspan="1">

```clojure
(union #{1} #{})

;-- result --;
#{1}
```

</td></tr></table>

#### Possible errors:

* [`h-err/arguments-not-sets`](halite-err-id-reference.md#h-err/arguments-not-sets)

See also: [`difference`](#difference) [`intersection`](#intersection) [`subset?`](#subset_Q)

Tags: [Set operations](halite-set-op-reference.md),  [Produce sets](halite-set-out-reference.md)

---
### <a name="valid"></a>valid

Evaluate the instance-expression and produce the result. If a constraint violation occurs while evaluating the expression then produce an 'unset' value.

![["instance-expression" "any"]](./halite-bnf-diagrams/op/valid-0.svg)

This operation can be thought of as producing an instance if it is valid. This considers not just the constraints on the immediate instance, but also the constraints implied by refinements defined on the specification.

<table><tr><td colspan="3">

```clojure
(valid {:$type :my/Spec$v1, :p 1, :n -1})

;-- result --;
{:$type :my/Spec$v1, :p 1, :n -1}
```

</td><td colspan="2">

```clojure
(valid {:$type :my/Spec$v1, :p 1, :n 1})

;-- result --;
:Unset
```

</td></tr></table>

See also: [`valid?`](#valid_Q)

Tags: [Instance operations](halite-instance-op-reference.md),  [Optionally produce values](halite-optional-out-reference.md),  [Special forms](halite-special-form-reference.md)

---
### <a name="valid_Q"></a>valid?

Evaluate the instance expression and produce false if a constraint violation occurs during the evaluation. Otherwise, produce true.

![["instance-expression" "boolean"]](./halite-bnf-diagrams/op/valid%3F-0.svg)

Similar to 'valid', but insted of possibly producing an instance, it produces a boolean indicating whether the instance was valid. This can be thought of as invoking a specification as a single predicate on a candidate instance value.

<table><tr><td colspan="3">

```clojure
(valid? {:$type :my/Spec$v1, :p 1, :n -1})

;-- result --;
true
```

</td></tr><tr><td colspan="3">

```clojure
(valid? {:$type :my/Spec$v1, :p 1, :n 0})

;-- result --;
false
```

</td></tr></table>

See also: [`valid`](#valid)

Tags: [Produce booleans](halite-boolean-out-reference.md),  [Instance operations](halite-instance-op-reference.md),  [Special forms](halite-special-form-reference.md)

---
### <a name="when"></a>when

If the first argument is true, then evaluate the second argument, otherwise produce 'unset'.

![["boolean any-expression" "any"]](./halite-bnf-diagrams/op/when-0.svg)

A primary use of this operator is in instance expression to optionally provide a value for a an optional field.

<table><tr><td colspan="2">

```clojure
(when (> 2 1) "bigger")

;-- result --;
"bigger"
```

</td><td colspan="2">

```clojure
(when (< 2 1) "bigger")

;-- result --;
:Unset
```

</td></tr></table>

Tags: [Boolean operations](halite-boolean-op-reference.md),  [Control flow](halite-control-flow-reference.md),  [Optionally produce values](halite-optional-out-reference.md),  [Special forms](halite-special-form-reference.md)

---
### <a name="when-value"></a>when-value

Consider the value bound to the symbol. If it is a 'value', then evaluate the second argument. If instead it is 'unset' then produce unset.

![["symbol any-expression:binding" "any"]](./halite-bnf-diagrams/op/when-value-0.svg)

<table><tr><td colspan="2">

```clojure
(when-value x (+ x 2))

;-- result --;
3
```

</td><td colspan="2">

```clojure
(when-value x (+ x 2))

;-- result --;
:Unset
```

</td></tr></table>

See also: [`if-value`](#if-value) [`when`](#when) [`when-value-let`](#when-value-let)

Tags: [Control flow](halite-control-flow-reference.md),  [Optional operations](halite-optional-op-reference.md),  [Optionally produce values](halite-optional-out-reference.md),  [Special forms](halite-special-form-reference.md)

---
### <a name="when-value-let"></a>when-value-let

If the binding value is a 'value' then evaluate the second argument with the symbol bound to binding. If instead, the binding value is 'unset', then produce 'unset'

![["'[' symbol any:binding']' any-expression" "any"]](./halite-bnf-diagrams/op/when-value-let-0.svg)

<table><tr><td colspan="3">

```clojure
(when-value-let [x (when-value y (+ y 2))] (inc x))

;-- result --;
4
```

</td></tr><tr><td colspan="3">

```clojure
(when-value-let [x (when-value y (+ y 2))] (inc x))

;-- result --;
:Unset
```

</td></tr></table>

See also: [`if-value-let`](#if-value-let) [`when`](#when) [`when-value`](#when-value)

Tags: [Control flow](halite-control-flow-reference.md),  [Optional operations](halite-optional-op-reference.md),  [Optionally produce values](halite-optional-out-reference.md),  [Special forms](halite-special-form-reference.md)

---
