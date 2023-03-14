<!---
  This markdown file was generated. Do not edit.
  -->

## Model a sudokuo puzzle

Consider how to use specs to model a sudoku game.

Say we want to represent a sudoku solution as a two dimensional vector of integers

```clojure
[[1 2 3 4] [3 4 1 2] [4 3 2 1] [2 1 4 3]]
```

We can write a specification that contains a value of this form.

```clojure
{:tutorials.sudoku/Sudoku$v1 {:fields {:solution [:Vec [:Vec :Integer]]}}}
```

An instance of this spec can be constructed as:

```clojure
{:$type :tutorials.sudoku/Sudoku$v1,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 1] [2 1 4 3]]}
```

In order to be a valid solution, certain properties must be met: each row, column, and quadrant must consist of the values 1, 2, 3, and 4. That is each number appears once and only once in each of these divisions of the grid. These necessary properties can be expressed as constraints on the spec. Let's start by expressing the constraints on each row.

```clojure
{:tutorials.sudoku/Sudoku$v2
   {:fields {:solution [:Vec [:Vec :Integer]]},
    :constraints #{'{:name "row_1",
                     :expr (= (concat #{} (get solution 0)) #{1 4 3 2})}
                   '{:name "row_2",
                     :expr (= (concat #{} (get solution 1)) #{1 4 3 2})}
                   '{:name "row_3",
                     :expr (= (concat #{} (get solution 2)) #{1 4 3 2})}
                   '{:name "row_4",
                     :expr (= (concat #{} (get solution 3)) #{1 4 3 2})}}}}
```

Now when we create an instance it must meet these constraints. As this instance does.

```clojure
{:$type :tutorials.sudoku/Sudoku$v2,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 1] [2 1 4 3]]}


;-- result --
{:$type :tutorials.sudoku/Sudoku$v2,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 1] [2 1 4 3]]}
```

However, this attempt to create an instance fails. It tells us specifically which constraint failed.

```clojure
{:$type :tutorials.sudoku/Sudoku$v2,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 2] [2 1 4 3]]}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.sudoku/Sudoku$v2', violates constraints \"tutorials.sudoku/Sudoku$v2/row_3\""
 :h-err/invalid-instance]
```

Rather than expressing each row constraint separately, they can be captured in a single constraint expression.

```clojure
{:tutorials.sudoku/Sudoku$v3 {:fields {:solution [:Vec [:Vec :Integer]]},
                              :constraints #{'{:name "rows",
                                               :expr (every? [r solution]
                                                             (= (concat #{} r)
                                                                #{1 4 3 2}))}}}}
```

Again, valid solutions can be constructed.

```clojure
{:$type :tutorials.sudoku/Sudoku$v3,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 1] [2 1 4 3]]}


;-- result --
{:$type :tutorials.sudoku/Sudoku$v3,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 1] [2 1 4 3]]}
```

While invalid solutions fail

```clojure
{:$type :tutorials.sudoku/Sudoku$v3,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 2] [2 1 4 3]]}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.sudoku/Sudoku$v3', violates constraints \"tutorials.sudoku/Sudoku$v3/rows\""
 :h-err/invalid-instance]
```

But, we are only checking rows, let's also check columns.

```clojure
{:tutorials.sudoku/Sudoku$v4
   {:fields {:solution [:Vec [:Vec :Integer]]},
    :constraints
      #{'{:name "columns",
          :expr (every? [i [0 1 2 3]]
                        (= #{(get-in solution [3 i]) (get-in solution [1 i])
                             (get-in solution [2 i]) (get-in solution [0 i])}
                           #{1 4 3 2}))}
        '{:name "rows",
          :expr (every? [r solution] (= (concat #{} r) #{1 4 3 2}))}}}}
```

First, check if a valid solution works.

```clojure
{:$type :tutorials.sudoku/Sudoku$v4,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 1] [2 1 4 3]]}


;-- result --
{:$type :tutorials.sudoku/Sudoku$v4,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 1] [2 1 4 3]]}
```

Now confirm that an invalid solution fails. Notice that the error indicates that both constraints are violated.

```clojure
{:$type :tutorials.sudoku/Sudoku$v4,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 2] [2 1 4 3]]}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.sudoku/Sudoku$v4', violates constraints \"tutorials.sudoku/Sudoku$v4/columns\", \"tutorials.sudoku/Sudoku$v4/rows\""
 :h-err/invalid-instance]
```

Notice that we are still not detecting the following invalid solution. Specifically, while this solution meets the row and column requirements, it does not meet the quadrant requirement.

```clojure
{:$type :tutorials.sudoku/Sudoku$v4,
 :solution [[1 2 3 4] [4 1 2 3] [3 4 1 2] [2 3 4 1]]}


;-- result --
{:$type :tutorials.sudoku/Sudoku$v4,
 :solution [[1 2 3 4] [4 1 2 3] [3 4 1 2] [2 3 4 1]]}
```

Let's add the quadrant checks.

```clojure
{:tutorials.sudoku/Sudoku$v5
   {:fields {:solution [:Vec [:Vec :Integer]]},
    :constraints
      #{'{:name "columns",
          :expr (every? [i [0 1 2 3]]
                        (= #{(get-in solution [3 i]) (get-in solution [1 i])
                             (get-in solution [2 i]) (get-in solution [0 i])}
                           #{1 4 3 2}))}
        '{:name "quadrant_1",
          :expr (= #{(get-in solution [0 0]) (get-in solution [1 1])
                     (get-in solution [1 0]) (get-in solution [0 1])}
                   #{1 4 3 2})}
        '{:name "quadrant_2",
          :expr (= #{(get-in solution [0 2]) (get-in solution [1 2])
                     (get-in solution [0 3]) (get-in solution [1 3])}
                   #{1 4 3 2})}
        '{:name "quadrant_3",
          :expr (= #{(get-in solution [2 1]) (get-in solution [3 0])
                     (get-in solution [3 1]) (get-in solution [2 0])}
                   #{1 4 3 2})}
        '{:name "quadrant_4",
          :expr (= #{(get-in solution [3 2]) (get-in solution [2 3])
                     (get-in solution [2 2]) (get-in solution [3 3])}
                   #{1 4 3 2})}
        '{:name "rows",
          :expr (every? [r solution] (= (concat #{} r) #{1 4 3 2}))}}}}
```

Now the attempted solution, which has valid columns and rows, but not quadrants is detected as invalid. Notice the error indicates that all four quadrants were violated.

```clojure
{:$type :tutorials.sudoku/Sudoku$v5,
 :solution [[1 2 3 4] [4 1 2 3] [3 4 1 2] [2 3 4 1]]}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.sudoku/Sudoku$v5', violates constraints \"tutorials.sudoku/Sudoku$v5/quadrant_1\", \"tutorials.sudoku/Sudoku$v5/quadrant_2\", \"tutorials.sudoku/Sudoku$v5/quadrant_3\", \"tutorials.sudoku/Sudoku$v5/quadrant_4\""
 :h-err/invalid-instance]
```

Let's make sure that our valid solution works.

```clojure
{:$type :tutorials.sudoku/Sudoku$v5,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 1] [2 1 4 3]]}


;-- result --
{:$type :tutorials.sudoku/Sudoku$v5,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 1] [2 1 4 3]]}
```

Let's combine the quadrant checks into one.

```clojure
{:tutorials.sudoku/Sudoku$v6
   {:fields {:solution [:Vec [:Vec :Integer]]},
    :constraints
      #{'{:name "columns",
          :expr (every? [i [0 1 2 3]]
                        (= #{(get-in solution [3 i]) (get-in solution [1 i])
                             (get-in solution [2 i]) (get-in solution [0 i])}
                           #{1 4 3 2}))}
        '{:name "quadrants",
          :expr (every? [base [[0 0] [0 2] [2 0] [2 2]]]
                        (let [base-x (get base 0)
                              base-y (get base 1)]
                          (= #{(get-in solution [base-x base-y])
                               (get-in solution [(inc base-x) (inc base-y)])
                               (get-in solution [(inc base-x) base-y])
                               (get-in solution [base-x (inc base-y)])}
                             #{1 4 3 2})))}
        '{:name "rows",
          :expr (every? [r solution] (= (concat #{} r) #{1 4 3 2}))}}}}
```

Valid solution still works.

```clojure
{:$type :tutorials.sudoku/Sudoku$v6,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 1] [2 1 4 3]]}


;-- result --
{:$type :tutorials.sudoku/Sudoku$v6,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 1] [2 1 4 3]]}
```

Invalid solution fails.

```clojure
{:$type :tutorials.sudoku/Sudoku$v6,
 :solution [[1 2 3 4] [4 1 2 3] [3 4 1 2] [2 3 4 1]]}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.sudoku/Sudoku$v6', violates constraints \"tutorials.sudoku/Sudoku$v6/quadrants\""
 :h-err/invalid-instance]
```

As an exercise, we can convert the logic of the constraints. Instead of checking that each row, column, and quadrant has the expected elements, we can write the constraints to ensure there are not any rows, columns, or quadrants that do not have the expected elements. The double negative logic is confusing, but this shows other available logical operations.

```clojure
{:tutorials.sudoku/Sudoku$v7
   {:fields {:solution [:Vec [:Vec :Integer]]},
    :constraints
      #{'{:name "columns",
          :expr (not
                  (any? [i [0 1 2 3]]
                        (not= #{(get-in solution [3 i]) (get-in solution [1 i])
                                (get-in solution [2 i]) (get-in solution [0 i])}
                              #{1 4 3 2})))}
        '{:name "quadrants",
          :expr (not
                  (any? [base [[0 0] [0 2] [2 0] [2 2]]]
                        (let [base-x (get base 0)
                              base-y (get base 1)]
                          (not= #{(get-in solution [base-x base-y])
                                  (get-in solution [(inc base-x) (inc base-y)])
                                  (get-in solution [(inc base-x) base-y])
                                  (get-in solution [base-x (inc base-y)])}
                                #{1 4 3 2}))))}
        '{:name "rows",
          :expr (not (any? [r solution] (not= (concat #{} r) #{1 4 3 2})))}}}}
```

Valid solution still works.

```clojure
{:$type :tutorials.sudoku/Sudoku$v7,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 1] [2 1 4 3]]}


;-- result --
{:$type :tutorials.sudoku/Sudoku$v7,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 1] [2 1 4 3]]}
```

Invalid solution fails.

```clojure
{:$type :tutorials.sudoku/Sudoku$v7,
 :solution [[1 2 3 4] [4 1 2 3] [3 4 1 2] [2 3 4 1]]}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.sudoku/Sudoku$v7', violates constraints \"tutorials.sudoku/Sudoku$v7/quadrants\""
 :h-err/invalid-instance]
```

Finally, rather than having invalid solutions throw errors, we can instead produce a boolean value indicating whether the solution is valid.

```clojure
(valid? {:$type :tutorials.sudoku/Sudoku$v7,
         :solution [[1 2 3 4] [3 4 1 2] [4 3 2 1] [2 1 4 3]]})


;-- result --
true
```

```clojure
(valid? {:$type :tutorials.sudoku/Sudoku$v7,
         :solution [[1 2 3 4] [3 4 1 2] [4 3 2 2] [2 1 4 3]]})


;-- result --
false
```

```clojure
(valid? {:$type :tutorials.sudoku/Sudoku$v7,
         :solution [[1 2 3 4] [4 1 2 3] [3 4 1 2] [2 3 4 1]]})


;-- result --
false
```

### Reference

#### Basic elements:

[`boolean`](../halite_basic-syntax-reference.md#boolean), [`instance`](../halite_basic-syntax-reference.md#instance), [`integer`](../halite_basic-syntax-reference.md#integer), [`set`](../halite_basic-syntax-reference.md#set), [`spec-map`](../../halite_spec-syntax-reference.md), [`vector`](../halite_basic-syntax-reference.md#vector)

#### Operator reference:

* [`any?`](../halite_full-reference.md#any_Q)
* [`concat`](../halite_full-reference.md#concat)
* [`every?`](../halite_full-reference.md#every_Q)
* [`get`](../halite_full-reference.md#get)
* [`get-in`](../halite_full-reference.md#get-in)
* [`not`](../halite_full-reference.md#not)
* [`valid?`](../halite_full-reference.md#valid_Q)


#### How Tos:

* [constrain-instances](../how-to/halite_constrain-instances.md)


