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
{:spec/Sudoku$v1 {:spec-vars {:solution [["Integer"]]}}}
```

An instance of this spec can be constructed as:

```clojure
{:$type :spec/Sudoku$v1,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 1] [2 1 4 3]]}
```

In order to be a valid solution, certain properties must be met: each row, column, and quadrant must consist of the values 1, 2, 3, & 4. That is each number appears once and only once in each of these divisions of the grid. These necessary properties can be expressed as constraints on the spec. Let's start by expressing the constraints on each row.

```clojure
{:spec/Sudoku$v2 {:spec-vars {:solution [["Integer"]]},
                  :constraints
                    [["row_1" '(= (concat #{} (get solution 0)) #{1 4 3 2})]
                     ["row_2" '(= (concat #{} (get solution 1)) #{1 4 3 2})]
                     ["row_3" '(= (concat #{} (get solution 2)) #{1 4 3 2})]
                     ["row_4" '(= (concat #{} (get solution 3)) #{1 4 3 2})]]}}
```

Now when we create an instance it must meet these constraints. As this instance does.

```clojure
{:$type :spec/Sudoku$v2,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 1] [2 1 4 3]]}


;-- result --
{:$type :spec/Sudoku$v2,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 1] [2 1 4 3]]}
```

However, this attempt to create an instance fails. It tells us specifically which constraint failed.

```clojure
{:$type :spec/Sudoku$v2,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 2] [2 1 4 3]]}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'spec/Sudoku$v2', violates constraints row_3"
 :h-err/invalid-instance]
```

Rather than expressing each row constraint separately, they can be captured in a single constraint expression.

```clojure
{:spec/Sudoku$v3 {:spec-vars {:solution [["Integer"]]},
                  :constraints [["rows"
                                 '(every? [r solution]
                                          (= (concat #{} r) #{1 4 3 2}))]]}}
```

Again, valid solutions can be constructed.

```clojure
{:$type :spec/Sudoku$v3,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 1] [2 1 4 3]]}


;-- result --
{:$type :spec/Sudoku$v3,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 1] [2 1 4 3]]}
```

While invalid solutions fail

```clojure
{:$type :spec/Sudoku$v3,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 2] [2 1 4 3]]}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'spec/Sudoku$v3', violates constraints rows"
 :h-err/invalid-instance]
```

But, we are only checking rows, let's also check columns.

```clojure
{:spec/Sudoku$v4
   {:spec-vars {:solution [["Integer"]]},
    :constraints [["rows" '(every? [r solution] (= (concat #{} r) #{1 4 3 2}))]
                  ["columns"
                   '(every? [i [0 1 2 3]]
                            (= #{(get-in solution [3 i]) (get-in solution [1 i])
                                 (get-in solution [2 i])
                                 (get-in solution [0 i])}
                               #{1 4 3 2}))]]}}
```

First, check if a valid solution works.

```clojure
{:$type :spec/Sudoku$v4,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 1] [2 1 4 3]]}


;-- result --
{:$type :spec/Sudoku$v4,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 1] [2 1 4 3]]}
```

Now confirm that an invalid solution fails. Notice that the error indicates that both constraints are violated.

```clojure
{:$type :spec/Sudoku$v4,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 2] [2 1 4 3]]}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'spec/Sudoku$v4', violates constraints rows, columns"
 :h-err/invalid-instance]
```

Notice that we are still not detecting the following invalid solution. Specifically, while this solution meets the row and column requirements, it does not meet the quadrant requirement.

```clojure
{:$type :spec/Sudoku$v4,
 :solution [[1 2 3 4] [4 1 2 3] [3 4 1 2] [2 3 4 1]]}


;-- result --
{:$type :spec/Sudoku$v4,
 :solution [[1 2 3 4] [4 1 2 3] [3 4 1 2] [2 3 4 1]]}
```

Let's add the quadrant checks.

```clojure
{:spec/Sudoku$v5
   {:spec-vars {:solution [["Integer"]]},
    :constraints [["rows" '(every? [r solution] (= (concat #{} r) #{1 4 3 2}))]
                  ["columns"
                   '(every? [i [0 1 2 3]]
                            (= #{(get-in solution [3 i]) (get-in solution [1 i])
                                 (get-in solution [2 i])
                                 (get-in solution [0 i])}
                               #{1 4 3 2}))]
                  ["quadrant_1"
                   '(= #{(get-in solution [0 0]) (get-in solution [1 1])
                         (get-in solution [1 0]) (get-in solution [0 1])}
                       #{1 4 3 2})]
                  ["quadrant_2"
                   '(= #{(get-in solution [0 2]) (get-in solution [1 2])
                         (get-in solution [0 3]) (get-in solution [1 3])}
                       #{1 4 3 2})]
                  ["quadrant_3"
                   '(= #{(get-in solution [2 1]) (get-in solution [3 0])
                         (get-in solution [3 1]) (get-in solution [2 0])}
                       #{1 4 3 2})]
                  ["quadrant_4"
                   '(= #{(get-in solution [3 2]) (get-in solution [2 3])
                         (get-in solution [2 2]) (get-in solution [3 3])}
                       #{1 4 3 2})]]}}
```

Now the attempted solution, which has valid columns and rows, but not quadrants is detected as invalid. Notice the error indicates that all four quadrants were violated.

```clojure
{:$type :spec/Sudoku$v5,
 :solution [[1 2 3 4] [4 1 2 3] [3 4 1 2] [2 3 4 1]]}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'spec/Sudoku$v5', violates constraints quadrant_1, quadrant_2, quadrant_3, quadrant_4"
 :h-err/invalid-instance]
```

Let's make sure that our valid solution works.

```clojure
{:$type :spec/Sudoku$v5,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 1] [2 1 4 3]]}


;-- result --
{:$type :spec/Sudoku$v5,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 1] [2 1 4 3]]}
```

Let's combine the quadrant checks into one.

```clojure
{:spec/Sudoku$v6
   {:spec-vars {:solution [["Integer"]]},
    :constraints [["rows" '(every? [r solution] (= (concat #{} r) #{1 4 3 2}))]
                  ["columns"
                   '(every? [i [0 1 2 3]]
                            (= #{(get-in solution [3 i]) (get-in solution [1 i])
                                 (get-in solution [2 i])
                                 (get-in solution [0 i])}
                               #{1 4 3 2}))]
                  ["quadrants"
                   '(every? [base [[0 0] [0 2] [2 0] [2 2]]]
                            (let [base-x (get base 0)
                                  base-y (get base 1)]
                              (= #{(get-in solution [base-x base-y])
                                   (get-in solution [(inc base-x) (inc base-y)])
                                   (get-in solution [(inc base-x) base-y])
                                   (get-in solution [base-x (inc base-y)])}
                                 #{1 4 3 2})))]]}}
```

Valid solution still works.

```clojure
{:$type :spec/Sudoku$v6,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 1] [2 1 4 3]]}


;-- result --
{:$type :spec/Sudoku$v6,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 1] [2 1 4 3]]}
```

Invalid solution fails.

```clojure
{:$type :spec/Sudoku$v6,
 :solution [[1 2 3 4] [4 1 2 3] [3 4 1 2] [2 3 4 1]]}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'spec/Sudoku$v6', violates constraints quadrants"
 :h-err/invalid-instance]
```

As an exercise, we can convert the logic of the constraints. Instead of checking that each row, column, and quadrant has the expected elements, we can write the constraints to ensure there are not any rows, columns, or quadrants that do not have the expected elements. The double negative logic is confusing, but this shows other available logical operations.

```clojure
{:spec/Sudoku$v7
   {:spec-vars {:solution [["Integer"]]},
    :constraints
      [["rows" '(not (any? [r solution] (not= (concat #{} r) #{1 4 3 2})))]
       ["columns"
        '(not (any? [i [0 1 2 3]]
                    (not= #{(get-in solution [3 i]) (get-in solution [1 i])
                            (get-in solution [2 i]) (get-in solution [0 i])}
                          #{1 4 3 2})))]
       ["quadrants"
        '(not (any? [base [[0 0] [0 2] [2 0] [2 2]]]
                    (let [base-x (get base 0)
                          base-y (get base 1)]
                      (not= #{(get-in solution [base-x base-y])
                              (get-in solution [(inc base-x) (inc base-y)])
                              (get-in solution [(inc base-x) base-y])
                              (get-in solution [base-x (inc base-y)])}
                            #{1 4 3 2}))))]]}}
```

Valid solution still works.

```clojure
{:$type :spec/Sudoku$v7,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 1] [2 1 4 3]]}


;-- result --
{:$type :spec/Sudoku$v7,
 :solution [[1 2 3 4] [3 4 1 2] [4 3 2 1] [2 1 4 3]]}
```

Invalid solution fails.

```clojure
{:$type :spec/Sudoku$v7,
 :solution [[1 2 3 4] [4 1 2 3] [3 4 1 2] [2 3 4 1]]}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'spec/Sudoku$v7', violates constraints quadrants"
 :h-err/invalid-instance]
```

Finally, rather than having invalid solutions throw errors, we can instead produce a boolean value indicating whether the solution is valid.

```clojure
(valid? {:$type :spec/Sudoku$v7,
         :solution [[1 2 3 4] [3 4 1 2] [4 3 2 1] [2 1 4 3]]})


;-- result --
true
```

```clojure
(valid? {:$type :spec/Sudoku$v7,
         :solution [[1 2 3 4] [3 4 1 2] [4 3 2 2] [2 1 4 3]]})


;-- result --
false
```

```clojure
(valid? {:$type :spec/Sudoku$v7,
         :solution [[1 2 3 4] [4 1 2 3] [3 4 1 2] [2 3 4 1]]})


;-- result --
false
```

### Reference

#### Basic elements:

[`boolean`](../halite_basic-syntax-reference.md#boolean), [`instance`](../halite_basic-syntax-reference.md#instance), [`integer`](../halite_basic-syntax-reference.md#integer), [`set`](../halite_basic-syntax-reference.md#set), [`vector`](../halite_basic-syntax-reference.md#vector)

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


