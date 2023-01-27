<!---
  This markdown file was generated. Do not edit.
  -->

## Model a sudokuo puzzle

Consider how to use specs to model a sudoku game.

Say we want to represent a sudoku solution as a two dimensional vector of integers

```java
[[1, 2, 3, 4], [3, 4, 1, 2], [4, 3, 2, 1], [2, 1, 4, 3]]
```

We can write a specification that contains a value of this form.

```java
{
  "tutorials.sudoku/Sudoku$v1" : {
    "fields" : {
      "solution" : [ "Vec", [ "Vec", "Integer" ] ]
    }
  }
}
```

An instance of this spec can be constructed as:

```java
{$type: tutorials.sudoku/Sudoku$v1, solution: [[1, 2, 3, 4], [3, 4, 1, 2], [4, 3, 2, 1], [2, 1, 4, 3]]}
```

In order to be a valid solution, certain properties must be met: each row, column, and quadrant must consist of the values 1, 2, 3, & 4. That is each number appears once and only once in each of these divisions of the grid. These necessary properties can be expressed as constraints on the spec. Let's start by expressing the constraints on each row.

```java
{
  "tutorials.sudoku/Sudoku$v2" : {
    "fields" : {
      "solution" : [ "Vec", [ "Vec", "Integer" ] ]
    },
    "constraints" : [ "{expr: (#{}.concat(solution[2]) == #{1, 2, 3, 4}), name: \"row_3\"}", "{expr: (#{}.concat(solution[0]) == #{1, 2, 3, 4}), name: \"row_1\"}", "{expr: (#{}.concat(solution[3]) == #{1, 2, 3, 4}), name: \"row_4\"}", "{expr: (#{}.concat(solution[1]) == #{1, 2, 3, 4}), name: \"row_2\"}" ]
  }
}
```

Now when we create an instance it must meet these constraints. As this instance does.

```java
{$type: tutorials.sudoku/Sudoku$v2, solution: [[1, 2, 3, 4], [3, 4, 1, 2], [4, 3, 2, 1], [2, 1, 4, 3]]}


//-- result --
{$type: tutorials.sudoku/Sudoku$v2, solution: [[1, 2, 3, 4], [3, 4, 1, 2], [4, 3, 2, 1], [2, 1, 4, 3]]}
```

However, this attempt to create an instance fails. It tells us specifically which constraint failed.

```java
{$type: tutorials.sudoku/Sudoku$v2, solution: [[1, 2, 3, 4], [3, 4, 1, 2], [4, 3, 2, 2], [2, 1, 4, 3]]}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.sudoku/Sudoku$v2', violates constraints \"tutorials.sudoku/Sudoku$v2/row_3\""]
```

Rather than expressing each row constraint separately, they can be captured in a single constraint expression.

```java
{
  "tutorials.sudoku/Sudoku$v3" : {
    "fields" : {
      "solution" : [ "Vec", [ "Vec", "Integer" ] ]
    },
    "constraints" : [ "{expr: (every?(r in solution)(#{}.concat(r) == #{1, 2, 3, 4})), name: \"rows\"}" ]
  }
}
```

Again, valid solutions can be constructed.

```java
{$type: tutorials.sudoku/Sudoku$v3, solution: [[1, 2, 3, 4], [3, 4, 1, 2], [4, 3, 2, 1], [2, 1, 4, 3]]}


//-- result --
{$type: tutorials.sudoku/Sudoku$v3, solution: [[1, 2, 3, 4], [3, 4, 1, 2], [4, 3, 2, 1], [2, 1, 4, 3]]}
```

While invalid solutions fail

```java
{$type: tutorials.sudoku/Sudoku$v3, solution: [[1, 2, 3, 4], [3, 4, 1, 2], [4, 3, 2, 2], [2, 1, 4, 3]]}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.sudoku/Sudoku$v3', violates constraints \"tutorials.sudoku/Sudoku$v3/rows\""]
```

But, we are only checking rows, let's also check columns.

```java
{
  "tutorials.sudoku/Sudoku$v4" : {
    "fields" : {
      "solution" : [ "Vec", [ "Vec", "Integer" ] ]
    },
    "constraints" : [ "{expr: (every?(i in [0, 1, 2, 3])(#{solution[0][i], solution[1][i], solution[2][i], solution[3][i]} == #{1, 2, 3, 4})), name: \"columns\"}", "{expr: (every?(r in solution)(#{}.concat(r) == #{1, 2, 3, 4})), name: \"rows\"}" ]
  }
}
```

First, check if a valid solution works.

```java
{$type: tutorials.sudoku/Sudoku$v4, solution: [[1, 2, 3, 4], [3, 4, 1, 2], [4, 3, 2, 1], [2, 1, 4, 3]]}


//-- result --
{$type: tutorials.sudoku/Sudoku$v4, solution: [[1, 2, 3, 4], [3, 4, 1, 2], [4, 3, 2, 1], [2, 1, 4, 3]]}
```

Now confirm that an invalid solution fails. Notice that the error indicates that both constraints are violated.

```java
{$type: tutorials.sudoku/Sudoku$v4, solution: [[1, 2, 3, 4], [3, 4, 1, 2], [4, 3, 2, 2], [2, 1, 4, 3]]}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.sudoku/Sudoku$v4', violates constraints \"tutorials.sudoku/Sudoku$v4/columns\", \"tutorials.sudoku/Sudoku$v4/rows\""]
```

Notice that we are still not detecting the following invalid solution. Specifically, while this solution meets the row and column requirements, it does not meet the quadrant requirement.

```java
{$type: tutorials.sudoku/Sudoku$v4, solution: [[1, 2, 3, 4], [4, 1, 2, 3], [3, 4, 1, 2], [2, 3, 4, 1]]}


//-- result --
{$type: tutorials.sudoku/Sudoku$v4, solution: [[1, 2, 3, 4], [4, 1, 2, 3], [3, 4, 1, 2], [2, 3, 4, 1]]}
```

Let's add the quadrant checks.

```java
{
  "tutorials.sudoku/Sudoku$v5" : {
    "fields" : {
      "solution" : [ "Vec", [ "Vec", "Integer" ] ]
    },
    "constraints" : [ "{expr: (#{solution[0][0], solution[0][1], solution[1][0], solution[1][1]} == #{1, 2, 3, 4}), name: \"quadrant_1\"}", "{expr: (#{solution[2][2], solution[2][3], solution[3][2], solution[3][3]} == #{1, 2, 3, 4}), name: \"quadrant_4\"}", "{expr: (every?(i in [0, 1, 2, 3])(#{solution[0][i], solution[1][i], solution[2][i], solution[3][i]} == #{1, 2, 3, 4})), name: \"columns\"}", "{expr: (#{solution[0][2], solution[0][3], solution[1][2], solution[1][3]} == #{1, 2, 3, 4}), name: \"quadrant_2\"}", "{expr: (#{solution[2][0], solution[2][1], solution[3][0], solution[3][1]} == #{1, 2, 3, 4}), name: \"quadrant_3\"}", "{expr: (every?(r in solution)(#{}.concat(r) == #{1, 2, 3, 4})), name: \"rows\"}" ]
  }
}
```

Now the attempted solution, which has valid columns and rows, but not quadrants is detected as invalid. Notice the error indicates that all four quadrants were violated.

```java
{$type: tutorials.sudoku/Sudoku$v5, solution: [[1, 2, 3, 4], [4, 1, 2, 3], [3, 4, 1, 2], [2, 3, 4, 1]]}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.sudoku/Sudoku$v5', violates constraints \"tutorials.sudoku/Sudoku$v5/quadrant_1\", \"tutorials.sudoku/Sudoku$v5/quadrant_2\", \"tutorials.sudoku/Sudoku$v5/quadrant_3\", \"tutorials.sudoku/Sudoku$v5/quadrant_4\""]
```

Let's make sure that our valid solution works.

```java
{$type: tutorials.sudoku/Sudoku$v5, solution: [[1, 2, 3, 4], [3, 4, 1, 2], [4, 3, 2, 1], [2, 1, 4, 3]]}


//-- result --
{$type: tutorials.sudoku/Sudoku$v5, solution: [[1, 2, 3, 4], [3, 4, 1, 2], [4, 3, 2, 1], [2, 1, 4, 3]]}
```

Let's combine the quadrant checks into one.

```java
{
  "tutorials.sudoku/Sudoku$v6" : {
    "fields" : {
      "solution" : [ "Vec", [ "Vec", "Integer" ] ]
    },
    "constraints" : [ "{expr: (every?(base in [[0, 0], [0, 2], [2, 0], [2, 2]])({ 'base-x' = base[0]; 'base-y' = base[1]; (#{solution['base-x']['base-y'], solution['base-x'][('base-y' + 1)], solution[('base-x' + 1)]['base-y'], solution[('base-x' + 1)][('base-y' + 1)]} == #{1, 2, 3, 4}) })), name: \"quadrants\"}", "{expr: (every?(i in [0, 1, 2, 3])(#{solution[0][i], solution[1][i], solution[2][i], solution[3][i]} == #{1, 2, 3, 4})), name: \"columns\"}", "{expr: (every?(r in solution)(#{}.concat(r) == #{1, 2, 3, 4})), name: \"rows\"}" ]
  }
}
```

Valid solution still works.

```java
{$type: tutorials.sudoku/Sudoku$v6, solution: [[1, 2, 3, 4], [3, 4, 1, 2], [4, 3, 2, 1], [2, 1, 4, 3]]}


//-- result --
{$type: tutorials.sudoku/Sudoku$v6, solution: [[1, 2, 3, 4], [3, 4, 1, 2], [4, 3, 2, 1], [2, 1, 4, 3]]}
```

Invalid solution fails.

```java
{$type: tutorials.sudoku/Sudoku$v6, solution: [[1, 2, 3, 4], [4, 1, 2, 3], [3, 4, 1, 2], [2, 3, 4, 1]]}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.sudoku/Sudoku$v6', violates constraints \"tutorials.sudoku/Sudoku$v6/quadrants\""]
```

As an exercise, we can convert the logic of the constraints. Instead of checking that each row, column, and quadrant has the expected elements, we can write the constraints to ensure there are not any rows, columns, or quadrants that do not have the expected elements. The double negative logic is confusing, but this shows other available logical operations.

```java
{
  "tutorials.sudoku/Sudoku$v7" : {
    "fields" : {
      "solution" : [ "Vec", [ "Vec", "Integer" ] ]
    },
    "constraints" : [ "{expr: !(any?(i in [0, 1, 2, 3])(#{solution[0][i], solution[1][i], solution[2][i], solution[3][i]} != #{1, 2, 3, 4})), name: \"columns\"}", "{expr: !(any?(r in solution)(#{}.concat(r) != #{1, 2, 3, 4})), name: \"rows\"}", "{expr: !(any?(base in [[0, 0], [0, 2], [2, 0], [2, 2]])({ 'base-x' = base[0]; 'base-y' = base[1]; (#{solution['base-x']['base-y'], solution['base-x'][('base-y' + 1)], solution[('base-x' + 1)]['base-y'], solution[('base-x' + 1)][('base-y' + 1)]} != #{1, 2, 3, 4}) })), name: \"quadrants\"}" ]
  }
}
```

Valid solution still works.

```java
{$type: tutorials.sudoku/Sudoku$v7, solution: [[1, 2, 3, 4], [3, 4, 1, 2], [4, 3, 2, 1], [2, 1, 4, 3]]}


//-- result --
{$type: tutorials.sudoku/Sudoku$v7, solution: [[1, 2, 3, 4], [3, 4, 1, 2], [4, 3, 2, 1], [2, 1, 4, 3]]}
```

Invalid solution fails.

```java
{$type: tutorials.sudoku/Sudoku$v7, solution: [[1, 2, 3, 4], [4, 1, 2, 3], [3, 4, 1, 2], [2, 3, 4, 1]]}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.sudoku/Sudoku$v7', violates constraints \"tutorials.sudoku/Sudoku$v7/quadrants\""]
```

Finally, rather than having invalid solutions throw errors, we can instead produce a boolean value indicating whether the solution is valid.

```java
(valid? {$type: tutorials.sudoku/Sudoku$v7, solution: [[1, 2, 3, 4], [3, 4, 1, 2], [4, 3, 2, 1], [2, 1, 4, 3]]})


//-- result --
true
```

```java
(valid? {$type: tutorials.sudoku/Sudoku$v7, solution: [[1, 2, 3, 4], [3, 4, 1, 2], [4, 3, 2, 2], [2, 1, 4, 3]]})


//-- result --
false
```

```java
(valid? {$type: tutorials.sudoku/Sudoku$v7, solution: [[1, 2, 3, 4], [4, 1, 2, 3], [3, 4, 1, 2], [2, 3, 4, 1]]})


//-- result --
false
```

### Reference

#### Basic elements:

[`boolean`](../halite_basic-syntax-reference-j.md#boolean), [`instance`](../halite_basic-syntax-reference-j.md#instance), [`integer`](../halite_basic-syntax-reference-j.md#integer), [`set`](../halite_basic-syntax-reference-j.md#set), [`spec-map`](../../halite_spec-syntax-reference.md), [`vector`](../halite_basic-syntax-reference-j.md#vector)

#### Operator reference:

* [`!`](../halite_full-reference-j.md#_B)
* [`ACCESSOR`](../halite_full-reference-j.md#ACCESSOR)
* [`ACCESSOR-CHAIN`](../halite_full-reference-j.md#ACCESSOR-CHAIN)
* [`any?`](../halite_full-reference-j.md#any_Q)
* [`concat`](../halite_full-reference-j.md#concat)
* [`every?`](../halite_full-reference-j.md#every_Q)
* [`valid?`](../halite_full-reference-j.md#valid_Q)


#### How Tos:

* [constrain-instances](../how-to/halite_constrain-instances-j.md)


