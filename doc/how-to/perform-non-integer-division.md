<!---
  This markdown file was generated. Do not edit.
  -->

## Divide an integer to produce a decimal result

Consider you have an integer value and you want to divide it by another integer to produce a decimal result.

Simply performing the division provides an integer result

```clojure
(let [x 14
      y 3]
  (div x y))


;-- result --
4
```

The mod operator can provide the remainder

```clojure
(let [x 14
      y 3]
  (mod x y))


;-- result --
2
```

The remainder can be converted into a decimal

```clojure
(let [x 14
      y 3]
  (* #d "1.0" (mod x y)))


;-- result --
#d "2.0"
```

The remainder can be divided by the orginal divisor.

```clojure
(let [x 14
      y 3]
  (div (* #d "1.0" (mod x y)) y))


;-- result --
#d "0.6"
```

The integer part of the division result can also be converted to a decimal

```clojure
(let [x 14
      y 3]
  (* #d "1.0" (div x y)))


;-- result --
#d "4.0"
```

Putting it all together gives the result of the division truncated to one decimal place

```clojure
(let [x 14
      y 3]
  (+ (* #d "1.0" (div x y)) (div (* #d "1.0" (mod x y)) y)))


;-- result --
#d "4.6"
```

#### Basic elements:

[`fixed-decimal`](../halite-basic-syntax-reference.md#fixed-decimal), [`integer`](../halite-basic-syntax-reference.md#integer)

#### Operator reference:

* [`div`](../halite-full-reference.md#div)


