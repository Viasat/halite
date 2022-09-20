;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.doc.data-tutorial)

(def tutorials {:spec/sudoku
                {:label "Model a sudokuo puzzle"
                 :desc "Consider how to use specs to model a sudoku game."
                 :basic-ref ['integer 'vector 'instance 'set 'boolean]
                 :op-ref ['valid? 'get 'concat 'get-in 'every? 'any? 'not]
                 :contents ["Say we want to represent a sudoku solution as a two dimensional vector of integers"
                            {:code '[[1 2 3 4]
                                     [3 4 1 2]
                                     [4 3 2 1]
                                     [2 1 4 3]]}
                            "We can write a specification that contains a value of this form."
                            {:spec-map {:spec/Sudoku$v1 {:spec-vars {:solution [["Integer"]]}}}}
                            "An instance of this spec can be constructed as:"
                            {:code '{:$type :spec/Sudoku$v1
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 1]
                                                [2 1 4 3]]}}
                            "In order to be a valid solution, certain properties must be met: each row, column, and quadrant must consist of the values 1, 2, 3, & 4. That is each number appears once and only once in each of these divisions of the grid. These necessary properties can be expressed as constraints on the spec. Let's start by expressing the constraints on each row."
                            {:spec-map {:spec/Sudoku$v2 {:spec-vars {:solution [["Integer"]]}
                                                         :constraints [["row_1" '(= (concat #{} (get solution 0))
                                                                                    #{1 2 3 4})]
                                                                       ["row_2" '(= (concat #{} (get solution 1))
                                                                                    #{1 2 3 4})]
                                                                       ["row_3" '(= (concat #{} (get solution 2))
                                                                                    #{1 2 3 4})]
                                                                       ["row_4" '(= (concat #{} (get solution 3))
                                                                                    #{1 2 3 4})]]}}}
                            "Now when we create an instance it must meet these constraints. As this instance does."
                            {:code '{:$type :spec/Sudoku$v2
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 1]
                                                [2 1 4 3]]}
                             :result :auto}
                            "However, this attempt to create an instance fails. It tells us specifically which constraint failed."
                            {:code '{:$type :spec/Sudoku$v2
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 2]
                                                [2 1 4 3]]}
                             :throws :auto}
                            "Rather than expressing each row constraint separately, they can be captured in a single constraint expression."
                            {:spec-map {:spec/Sudoku$v3 {:spec-vars {:solution [["Integer"]]}
                                                         :constraints [["rows" '(every? [r solution]
                                                                                        (= (concat #{} r)
                                                                                           #{1 2 3 4}))]]}}}
                            "Again, valid solutions can be constructed."
                            {:code '{:$type :spec/Sudoku$v3
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 1]
                                                [2 1 4 3]]}
                             :result :auto}
                            "While invalid solutions fail"
                            {:code '{:$type :spec/Sudoku$v3
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 2]
                                                [2 1 4 3]]}
                             :throws :auto}
                            "But, we are only checking rows, let's also check columns."
                            {:spec-map {:spec/Sudoku$v4 {:spec-vars {:solution [["Integer"]]}
                                                         :constraints [["rows" '(every? [r solution]
                                                                                        (= (concat #{} r)
                                                                                           #{1 2 3 4}))]
                                                                       ["columns" '(every? [i [0 1 2 3]]
                                                                                           (= #{(get-in solution [0 i])
                                                                                                (get-in solution [1 i])
                                                                                                (get-in solution [2 i])
                                                                                                (get-in solution [3 i])}
                                                                                              #{1 2 3 4}))]]}}}
                            "First, check if a valid solution works."
                            {:code '{:$type :spec/Sudoku$v4
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 1]
                                                [2 1 4 3]]}
                             :result :auto}
                            "Now confirm that an invalid solution fails. Notice that the error indicates that both constraints are violated."
                            {:code '{:$type :spec/Sudoku$v4
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 2]
                                                [2 1 4 3]]}
                             :throws :auto}
                            "Notice that we are still not detecting the following invalid solution. Specifically, while this solution meets the row and column requirements, it does not meet the quadrant requirement."
                            {:code '{:$type :spec/Sudoku$v4
                                     :solution [[1 2 3 4]
                                                [4 1 2 3]
                                                [3 4 1 2]
                                                [2 3 4 1]]}
                             :result :auto}
                            "Let's add the quadrant checks."
                            {:spec-map {:spec/Sudoku$v5 {:spec-vars {:solution [["Integer"]]}
                                                         :constraints [["rows" '(every? [r solution]
                                                                                        (= (concat #{} r)
                                                                                           #{1 2 3 4}))]
                                                                       ["columns" '(every? [i [0 1 2 3]]
                                                                                           (= #{(get-in solution [0 i])
                                                                                                (get-in solution [1 i])
                                                                                                (get-in solution [2 i])
                                                                                                (get-in solution [3 i])}
                                                                                              #{1 2 3 4}))]
                                                                       ["quadrant_1" '(= #{(get-in solution [0 0])
                                                                                           (get-in solution [0 1])
                                                                                           (get-in solution [1 0])
                                                                                           (get-in solution [1 1])}
                                                                                         #{1 2 3 4})]
                                                                       ["quadrant_2" '(= #{(get-in solution [0 2])
                                                                                           (get-in solution [0 3])
                                                                                           (get-in solution [1 2])
                                                                                           (get-in solution [1 3])}
                                                                                         #{1 2 3 4})]
                                                                       ["quadrant_3" '(= #{(get-in solution [2 0])
                                                                                           (get-in solution [2 1])
                                                                                           (get-in solution [3 0])
                                                                                           (get-in solution [3 1])}
                                                                                         #{1 2 3 4})]
                                                                       ["quadrant_4" '(= #{(get-in solution [2 2])
                                                                                           (get-in solution [2 3])
                                                                                           (get-in solution [3 2])
                                                                                           (get-in solution [3 3])}
                                                                                         #{1 2 3 4})]]}}}
                            "Now the attempted solution, which has valid columns and rows, but not quadrants is detected as invalid. Notice the error indicates that all four quadrants were violated."
                            {:code '{:$type :spec/Sudoku$v5
                                     :solution [[1 2 3 4]
                                                [4 1 2 3]
                                                [3 4 1 2]
                                                [2 3 4 1]]}
                             :throws :auto}
                            "Let's make sure that our valid solution works."
                            {:code '{:$type :spec/Sudoku$v5
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 1]
                                                [2 1 4 3]]}
                             :result :auto}
                            "Let's combine the quadrant checks into one."
                            {:spec-map {:spec/Sudoku$v6 {:spec-vars {:solution [["Integer"]]}
                                                         :constraints [["rows" '(every? [r solution]
                                                                                        (= (concat #{} r)
                                                                                           #{1 2 3 4}))]
                                                                       ["columns" '(every? [i [0 1 2 3]]
                                                                                           (= #{(get-in solution [0 i])
                                                                                                (get-in solution [1 i])
                                                                                                (get-in solution [2 i])
                                                                                                (get-in solution [3 i])}
                                                                                              #{1 2 3 4}))]
                                                                       ["quadrants" '(every? [base [[0 0] [0 2] [2 0] [2 2]]]
                                                                                             (let [base-x (get base 0)
                                                                                                   base-y (get base 1)]
                                                                                               (= #{(get-in solution [base-x base-y])
                                                                                                    (get-in solution [base-x (inc base-y)])
                                                                                                    (get-in solution [(inc base-x) base-y])
                                                                                                    (get-in solution [(inc base-x) (inc base-y)])}
                                                                                                  #{1 2 3 4})))]]}}}
                            "Valid solution still works."
                            {:code '{:$type :spec/Sudoku$v6
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 1]
                                                [2 1 4 3]]}
                             :result :auto}
                            "Invalid solution fails."
                            {:code '{:$type :spec/Sudoku$v6
                                     :solution [[1 2 3 4]
                                                [4 1 2 3]
                                                [3 4 1 2]
                                                [2 3 4 1]]}
                             :throws :auto}
                            "As an exercise, we can convert the logic of the constraints. Instead of checking that each row, column, and quadrant has the expected elements, we can write the constraints to ensure there are not any rows, columns, or quadrants that do not have the expected elements. The double negative logic is confusing, but this shows other available logical operations."
                            {:spec-map {:spec/Sudoku$v7 {:spec-vars {:solution [["Integer"]]}
                                                         :constraints [["rows" '(not (any? [r solution]
                                                                                           (not= (concat #{} r)
                                                                                                 #{1 2 3 4})))]
                                                                       ["columns" '(not (any? [i [0 1 2 3]]
                                                                                              (not= #{(get-in solution [0 i])
                                                                                                      (get-in solution [1 i])
                                                                                                      (get-in solution [2 i])
                                                                                                      (get-in solution [3 i])}
                                                                                                    #{1 2 3 4})))]
                                                                       ["quadrants" '(not (any? [base [[0 0] [0 2] [2 0] [2 2]]]
                                                                                                (let [base-x (get base 0)
                                                                                                      base-y (get base 1)]
                                                                                                  (not= #{(get-in solution [base-x base-y])
                                                                                                          (get-in solution [base-x (inc base-y)])
                                                                                                          (get-in solution [(inc base-x) base-y])
                                                                                                          (get-in solution [(inc base-x) (inc base-y)])}
                                                                                                        #{1 2 3 4}))))]]}}}
                            "Valid solution still works."
                            {:code '{:$type :spec/Sudoku$v7
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 1]
                                                [2 1 4 3]]}
                             :result :auto}
                            "Invalid solution fails."
                            {:code '{:$type :spec/Sudoku$v7
                                     :solution [[1 2 3 4]
                                                [4 1 2 3]
                                                [3 4 1 2]
                                                [2 3 4 1]]}
                             :throws :auto}

                            "Finally, rather than having invalid solutions throw errors, we can instead produce a boolean value indicating whether the solution is valid."
                            {:code '(valid? {:$type :spec/Sudoku$v7
                                             :solution [[1 2 3 4]
                                                        [3 4 1 2]
                                                        [4 3 2 1]
                                                        [2 1 4 3]]})
                             :result :auto}
                            {:code '(valid? {:$type :spec/Sudoku$v7
                                             :solution [[1 2 3 4]
                                                        [3 4 1 2]
                                                        [4 3 2 2]
                                                        [2 1 4 3]]})
                             :result :auto}
                            {:code '(valid? {:$type :spec/Sudoku$v7
                                             :solution [[1 2 3 4]
                                                        [4 1 2 3]
                                                        [3 4 1 2]
                                                        [2 3 4 1]]})
                             :result :auto}]}})
