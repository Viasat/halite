;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.doc.data-tutorial)

(set! *warn-on-reflection* true)

(def tutorials {:tutorials.vending/vending
                {:label "Model a vending machine as a state machine"
                 :desc "Use specs to map out a state space and valid transitions"
                 :contents ["We can model the state space for a vending machine that accepts nickels, dimes, and quarters and which vends  snacks for $0.50 and beverages for $1.00."
                            {:spec-map {:tutorials.vending/Vending$v1 {:fields {:balance [:Decimal 2]
                                                                                :beverageCount :Integer
                                                                                :snackCount :Integer}
                                                                       :constraints #{{:name "balance_not_negative"
                                                                                       :expr '(>= balance #d "0.00")}
                                                                                      {:name "counts_not_negative"
                                                                                       :expr '(and (>= beverageCount 0)
                                                                                                   (>= snackCount 0))}
                                                                                      {:name "counts_below_capacity"
                                                                                       :expr '(and (<= beverageCount 20)
                                                                                                   (<= snackCount 20))}}}}}
                            "With this spec we can construct instances."
                            {:code '{:$type :tutorials.vending/Vending$v1
                                     :balance #d "0.00"
                                     :beverageCount 10
                                     :snackCount 15}}
                            "Let us add a spec that will capture the constraints that identify a valid initial state for a vending machine."
                            {:spec-map {:tutorials.vending/Vending$v1 {:fields {:balance [:Decimal 2]
                                                                                :beverageCount :Integer
                                                                                :snackCount :Integer}
                                                                       :constraints #{{:name "balance_not_negative"
                                                                                       :expr '(>= balance #d "0.00")}
                                                                                      {:name "counts_not_negative"
                                                                                       :expr '(and (>= beverageCount 0)
                                                                                                   (>= snackCount 0))}}}
                                        :tutorials.vending/InitialVending$v1 {:fields {:balance [:Decimal 2]
                                                                                       :beverageCount :Integer
                                                                                       :snackCount :Integer}
                                                                              :constraints #{{:name "initial_state"
                                                                                              :expr '(and (= #d "0.00" balance)
                                                                                                          (> beverageCount 0)
                                                                                                          (> snackCount 0))}}
                                                                              :refines-to {:tutorials.vending/Vending$v1 {:name "toVending"
                                                                                                                          :expr
                                                                                                                          '{:$type :tutorials.vending/Vending$v1
                                                                                                                            :balance balance
                                                                                                                            :beverageCount beverageCount
                                                                                                                            :snackCount snackCount}}}}}}

                            "This additional spec can be used to determine where a state is a valid initial state for the machine. For example, this is a valid initial state."
                            {:code {:$type :tutorials.vending/InitialVending$v1
                                    :balance #d "0.00"
                                    :beverageCount 10
                                    :snackCount 15}}
                            "The corresponding vending state can be 'extracted' from the initial state:"
                            {:code '(refine-to {:$type :tutorials.vending/InitialVending$v1
                                                :balance #d "0.00"
                                                :beverageCount 10
                                                :snackCount 15}
                                               :tutorials.vending/Vending$v1)
                             :result :auto}

                            "However, this is not a valid initial state."
                            {:code '{:$type :tutorials.vending/InitialVending$v1
                                     :balance #d "0.00"
                                     :beverageCount 0
                                     :snackCount 15}
                             :throws :auto}

                            "So now we have a model of the state space and valid initial states for the machine. However, we would like to also model valid state transitions."
                            {:spec-map {:tutorials.vending/Vending$v1 {:fields {:balance [:Decimal 2]
                                                                                :beverageCount :Integer
                                                                                :snackCount :Integer}
                                                                       :constraints #{{:name "balance_not_negative"
                                                                                       :expr '(>= balance #d "0.00")}
                                                                                      {:name "counts_not_negative"
                                                                                       :expr '(and (>= beverageCount 0)
                                                                                                   (>= snackCount 0))}}}
                                        :tutorials.vending/VendingTransition$v1 {:fields {:current :tutorials.vending/Vending$v1
                                                                                          :next :tutorials.vending/Vending$v1}
                                                                                 :constraints #{{:name "state_transitions"
                                                                                                 :expr '(or (and
                                                                                                             (contains? #{#d "0.05"
                                                                                                                          #d "0.10"
                                                                                                                          #d "0.25"}
                                                                                                                        (- (get next :balance)
                                                                                                                           (get current :balance)))
                                                                                                             (= (get next :beverageCount)
                                                                                                                (get current :beverageCount))
                                                                                                             (= (get next :snackCount)
                                                                                                                (get current :snackCount)))
                                                                                                            (and
                                                                                                             (= #d "0.50" (- (get current :balance)
                                                                                                                             (get next :balance)))
                                                                                                             (= (get next :beverageCount)
                                                                                                                (get current :beverageCount))
                                                                                                             (= (get next :snackCount)
                                                                                                                (dec (get current :snackCount))))
                                                                                                            (and
                                                                                                             (= #d "1.00" (- (get current :balance)
                                                                                                                             (get next :balance)))
                                                                                                             (= (get next :beverageCount)
                                                                                                                (dec (get current :beverageCount)))
                                                                                                             (= (get next :snackCount)
                                                                                                                (get current :snackCount))))}}}}}
                            "A valid transition representing a dime being dropped into the machine."
                            {:code '{:$type :tutorials.vending/VendingTransition$v1
                                     :current {:$type :tutorials.vending/Vending$v1
                                               :balance #d "0.00"
                                               :beverageCount 10
                                               :snackCount 15}
                                     :next {:$type :tutorials.vending/Vending$v1
                                            :balance #d "0.10"
                                            :beverageCount 10
                                            :snackCount 15}}}
                            "An invalid transition, because the balance cannot increase by $0.07"
                            {:code '{:$type :tutorials.vending/VendingTransition$v1
                                     :current {:$type :tutorials.vending/Vending$v1
                                               :balance #d "0.00"
                                               :beverageCount 10
                                               :snackCount 15}
                                     :next {:$type :tutorials.vending/Vending$v1
                                            :balance #d "0.07"
                                            :beverageCount 10
                                            :snackCount 15}}
                             :throws :auto}
                            "A valid transition representing a snack being vended."
                            {:code '{:$type :tutorials.vending/VendingTransition$v1
                                     :current {:$type :tutorials.vending/Vending$v1
                                               :balance #d "0.75"
                                               :beverageCount 10
                                               :snackCount 15}
                                     :next {:$type :tutorials.vending/Vending$v1
                                            :balance #d "0.25"
                                            :beverageCount 10
                                            :snackCount 14}}}
                            "An invalid attempted transition representing a snack being vended."
                            {:code '{:$type :tutorials.vending/VendingTransition$v1
                                     :current {:$type :tutorials.vending/Vending$v1
                                               :balance #d "0.75"
                                               :beverageCount 10
                                               :snackCount 15}
                                     :next {:$type :tutorials.vending/Vending$v1
                                            :balance #d "0.25"
                                            :beverageCount 9
                                            :snackCount 14}}
                             :throws :auto}]}

                :tutorials.sudoku/sudoku
                {:label "Model a sudokuo puzzle"
                 :desc "Consider how to use specs to model a sudoku game."
                 :basic-ref ['integer 'vector 'instance 'set 'boolean 'spec-map]
                 :op-ref ['valid? 'get 'concat 'get-in 'every? 'any? 'not]
                 :how-to-ref [:instance/constrain-instances]
                 :contents ["Say we want to represent a sudoku solution as a two dimensional vector of integers"
                            {:code '[[1 2 3 4]
                                     [3 4 1 2]
                                     [4 3 2 1]
                                     [2 1 4 3]]}
                            "We can write a specification that contains a value of this form."
                            {:spec-map {:tutorials.sudoku/Sudoku$v1 {:fields {:solution [:Vec [:Vec :Integer]]}}}}
                            "An instance of this spec can be constructed as:"
                            {:code '{:$type :tutorials.sudoku/Sudoku$v1
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 1]
                                                [2 1 4 3]]}}
                            "In order to be a valid solution, certain properties must be met: each row, column, and quadrant must consist of the values 1, 2, 3, & 4. That is each number appears once and only once in each of these divisions of the grid. These necessary properties can be expressed as constraints on the spec. Let's start by expressing the constraints on each row."
                            {:spec-map {:tutorials.sudoku/Sudoku$v2 {:fields {:solution [:Vec [:Vec :Integer]]}
                                                                     :constraints #{{:name "row_1" :expr '(= (concat #{} (get solution 0))
                                                                                                             #{1 2 3 4})}
                                                                                    {:name "row_2" :expr '(= (concat #{} (get solution 1))
                                                                                                             #{1 2 3 4})}
                                                                                    {:name "row_3" :expr '(= (concat #{} (get solution 2))
                                                                                                             #{1 2 3 4})}
                                                                                    {:name "row_4" :expr '(= (concat #{} (get solution 3))
                                                                                                             #{1 2 3 4})}}}}}
                            "Now when we create an instance it must meet these constraints. As this instance does."
                            {:code '{:$type :tutorials.sudoku/Sudoku$v2
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 1]
                                                [2 1 4 3]]}
                             :result :auto}
                            "However, this attempt to create an instance fails. It tells us specifically which constraint failed."
                            {:code '{:$type :tutorials.sudoku/Sudoku$v2
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 2]
                                                [2 1 4 3]]}
                             :throws :auto}
                            "Rather than expressing each row constraint separately, they can be captured in a single constraint expression."
                            {:spec-map {:tutorials.sudoku/Sudoku$v3 {:fields {:solution [:Vec [:Vec :Integer]]}
                                                                     :constraints #{{:name "rows" :expr '(every? [r solution]
                                                                                                                 (= (concat #{} r)
                                                                                                                    #{1 2 3 4}))}}}}}
                            "Again, valid solutions can be constructed."
                            {:code '{:$type :tutorials.sudoku/Sudoku$v3
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 1]
                                                [2 1 4 3]]}
                             :result :auto}
                            "While invalid solutions fail"
                            {:code '{:$type :tutorials.sudoku/Sudoku$v3
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 2]
                                                [2 1 4 3]]}
                             :throws :auto}
                            "But, we are only checking rows, let's also check columns."
                            {:spec-map {:tutorials.sudoku/Sudoku$v4 {:fields {:solution [:Vec [:Vec :Integer]]}
                                                                     :constraints #{{:name "rows" :expr '(every? [r solution]
                                                                                                                 (= (concat #{} r)
                                                                                                                    #{1 2 3 4}))}
                                                                                    {:name "columns" :expr '(every? [i [0 1 2 3]]
                                                                                                                    (= #{(get-in solution [0 i])
                                                                                                                         (get-in solution [1 i])
                                                                                                                         (get-in solution [2 i])
                                                                                                                         (get-in solution [3 i])}
                                                                                                                       #{1 2 3 4}))}}}}}
                            "First, check if a valid solution works."
                            {:code '{:$type :tutorials.sudoku/Sudoku$v4
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 1]
                                                [2 1 4 3]]}
                             :result :auto}
                            "Now confirm that an invalid solution fails. Notice that the error indicates that both constraints are violated."
                            {:code '{:$type :tutorials.sudoku/Sudoku$v4
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 2]
                                                [2 1 4 3]]}
                             :throws :auto}
                            "Notice that we are still not detecting the following invalid solution. Specifically, while this solution meets the row and column requirements, it does not meet the quadrant requirement."
                            {:code '{:$type :tutorials.sudoku/Sudoku$v4
                                     :solution [[1 2 3 4]
                                                [4 1 2 3]
                                                [3 4 1 2]
                                                [2 3 4 1]]}
                             :result :auto}
                            "Let's add the quadrant checks."
                            {:spec-map {:tutorials.sudoku/Sudoku$v5 {:fields {:solution [:Vec [:Vec :Integer]]}
                                                                     :constraints #{{:name "rows" :expr '(every? [r solution]
                                                                                                                 (= (concat #{} r)
                                                                                                                    #{1 2 3 4}))}
                                                                                    {:name "columns" :expr '(every? [i [0 1 2 3]]
                                                                                                                    (= #{(get-in solution [0 i])
                                                                                                                         (get-in solution [1 i])
                                                                                                                         (get-in solution [2 i])
                                                                                                                         (get-in solution [3 i])}
                                                                                                                       #{1 2 3 4}))}
                                                                                    {:name "quadrant_1" :expr '(= #{(get-in solution [0 0])
                                                                                                                    (get-in solution [0 1])
                                                                                                                    (get-in solution [1 0])
                                                                                                                    (get-in solution [1 1])}
                                                                                                                  #{1 2 3 4})}
                                                                                    {:name "quadrant_2" :expr '(= #{(get-in solution [0 2])
                                                                                                                    (get-in solution [0 3])
                                                                                                                    (get-in solution [1 2])
                                                                                                                    (get-in solution [1 3])}
                                                                                                                  #{1 2 3 4})}
                                                                                    {:name "quadrant_3" :expr '(= #{(get-in solution [2 0])
                                                                                                                    (get-in solution [2 1])
                                                                                                                    (get-in solution [3 0])
                                                                                                                    (get-in solution [3 1])}
                                                                                                                  #{1 2 3 4})}
                                                                                    {:name "quadrant_4" :expr '(= #{(get-in solution [2 2])
                                                                                                                    (get-in solution [2 3])
                                                                                                                    (get-in solution [3 2])
                                                                                                                    (get-in solution [3 3])}
                                                                                                                  #{1 2 3 4})}}}}}
                            "Now the attempted solution, which has valid columns and rows, but not quadrants is detected as invalid. Notice the error indicates that all four quadrants were violated."
                            {:code '{:$type :tutorials.sudoku/Sudoku$v5
                                     :solution [[1 2 3 4]
                                                [4 1 2 3]
                                                [3 4 1 2]
                                                [2 3 4 1]]}
                             :throws :auto}
                            "Let's make sure that our valid solution works."
                            {:code '{:$type :tutorials.sudoku/Sudoku$v5
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 1]
                                                [2 1 4 3]]}
                             :result :auto}
                            "Let's combine the quadrant checks into one."
                            {:spec-map {:tutorials.sudoku/Sudoku$v6 {:fields {:solution [:Vec [:Vec :Integer]]}
                                                                     :constraints #{{:name "rows" :expr '(every? [r solution]
                                                                                                                 (= (concat #{} r)
                                                                                                                    #{1 2 3 4}))}
                                                                                    {:name "columns" :expr '(every? [i [0 1 2 3]]
                                                                                                                    (= #{(get-in solution [0 i])
                                                                                                                         (get-in solution [1 i])
                                                                                                                         (get-in solution [2 i])
                                                                                                                         (get-in solution [3 i])}
                                                                                                                       #{1 2 3 4}))}
                                                                                    {:name "quadrants" :expr '(every? [base [[0 0] [0 2] [2 0] [2 2]]]
                                                                                                                      (let [base-x (get base 0)
                                                                                                                            base-y (get base 1)]
                                                                                                                        (= #{(get-in solution [base-x base-y])
                                                                                                                             (get-in solution [base-x (inc base-y)])
                                                                                                                             (get-in solution [(inc base-x) base-y])
                                                                                                                             (get-in solution [(inc base-x) (inc base-y)])}
                                                                                                                           #{1 2 3 4})))}}}}}
                            "Valid solution still works."
                            {:code '{:$type :tutorials.sudoku/Sudoku$v6
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 1]
                                                [2 1 4 3]]}
                             :result :auto}
                            "Invalid solution fails."
                            {:code '{:$type :tutorials.sudoku/Sudoku$v6
                                     :solution [[1 2 3 4]
                                                [4 1 2 3]
                                                [3 4 1 2]
                                                [2 3 4 1]]}
                             :throws :auto}
                            "As an exercise, we can convert the logic of the constraints. Instead of checking that each row, column, and quadrant has the expected elements, we can write the constraints to ensure there are not any rows, columns, or quadrants that do not have the expected elements. The double negative logic is confusing, but this shows other available logical operations."
                            {:spec-map {:tutorials.sudoku/Sudoku$v7 {:fields {:solution [:Vec [:Vec :Integer]]}
                                                                     :constraints #{{:name "rows" :expr '(not (any? [r solution]
                                                                                                                    (not= (concat #{} r)
                                                                                                                          #{1 2 3 4})))}
                                                                                    {:name "columns" :expr '(not (any? [i [0 1 2 3]]
                                                                                                                       (not= #{(get-in solution [0 i])
                                                                                                                               (get-in solution [1 i])
                                                                                                                               (get-in solution [2 i])
                                                                                                                               (get-in solution [3 i])}
                                                                                                                             #{1 2 3 4})))}
                                                                                    {:name "quadrants" :expr '(not (any? [base [[0 0] [0 2] [2 0] [2 2]]]
                                                                                                                         (let [base-x (get base 0)
                                                                                                                               base-y (get base 1)]
                                                                                                                           (not= #{(get-in solution [base-x base-y])
                                                                                                                                   (get-in solution [base-x (inc base-y)])
                                                                                                                                   (get-in solution [(inc base-x) base-y])
                                                                                                                                   (get-in solution [(inc base-x) (inc base-y)])}
                                                                                                                                 #{1 2 3 4}))))}}}}}
                            "Valid solution still works."
                            {:code '{:$type :tutorials.sudoku/Sudoku$v7
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 1]
                                                [2 1 4 3]]}
                             :result :auto}
                            "Invalid solution fails."
                            {:code '{:$type :tutorials.sudoku/Sudoku$v7
                                     :solution [[1 2 3 4]
                                                [4 1 2 3]
                                                [3 4 1 2]
                                                [2 3 4 1]]}
                             :throws :auto}

                            "Finally, rather than having invalid solutions throw errors, we can instead produce a boolean value indicating whether the solution is valid."
                            {:code '(valid? {:$type :tutorials.sudoku/Sudoku$v7
                                             :solution [[1 2 3 4]
                                                        [3 4 1 2]
                                                        [4 3 2 1]
                                                        [2 1 4 3]]})
                             :result :auto}
                            {:code '(valid? {:$type :tutorials.sudoku/Sudoku$v7
                                             :solution [[1 2 3 4]
                                                        [3 4 1 2]
                                                        [4 3 2 2]
                                                        [2 1 4 3]]})
                             :result :auto}
                            {:code '(valid? {:$type :tutorials.sudoku/Sudoku$v7
                                             :solution [[1 2 3 4]
                                                        [4 1 2 3]
                                                        [3 4 1 2]
                                                        [2 3 4 1]]})
                             :result :auto}]}

                :tutorials.grocery/grocery
                {:label "Model a grocery delivery business"
                 :desc "Consider how to use specs to model some of the important details of a business that provides grocery delivery to subscribers."
                 :basic-ref ['vector 'instance 'set 'fixed-decimal 'integer 'string 'spec-map]
                 :op-ref ['refine-to 'reduce 'map 'get 'and '< '<= 'count]
                 :how-to-ref [:refinement/convert-instances]
                 :explanation-ref [:tutorials.grocery/specs-as-predicates :tutorials.grocery/refinements-as-functions]
                 :contents ["The following is a full model for the grocery delivery business."

                            {:spec-map {:tutorials.grocery/Country$v1 {:fields {:name :String}
                                                                       :constraints #{{:name "name_constraint" :expr '(contains? #{"Canada" "Mexico" "US"} name)}}}

                                        :tutorials.grocery/Perk$v1 {:abstract? true
                                                                    :fields {:perkId :Integer
                                                                             :feePerMonth [:Decimal 2]
                                                                             :feePerUse [:Decimal 2]
                                                                             :usesPerMonth [:Maybe :Integer]}
                                                                    :constraints #{{:name "feePerMonth_limit" :expr '(and (<= #d "0.00" feePerMonth)
                                                                                                                          (<= feePerMonth #d "199.99"))}
                                                                                   {:name "feePerUse_limit" :expr '(and (<= #d "0.00" feePerUse)
                                                                                                                        (<= feePerUse #d "14.99"))}
                                                                                   {:name "usesPerMonth_limit" :expr '(if-value usesPerMonth
                                                                                                                                (and (<= 0 usesPerMonth)
                                                                                                                                     (<= usesPerMonth 999))
                                                                                                                                true)}}}
                                        :tutorials.grocery/FreeDeliveryPerk$v1 {:fields {:usesPerMonth :Integer}
                                                                                :constraints #{{:name "usesPerMonth_limit" :expr '(< usesPerMonth 20)}}
                                                                                :refines-to {:tutorials.grocery/Perk$v1 {:name "refine_to_Perk"
                                                                                                                         :expr '{:$type :tutorials.grocery/Perk$v1
                                                                                                                                 :perkId 101
                                                                                                                                 :feePerMonth #d "2.99"
                                                                                                                                 :feePerUse #d "0.00"
                                                                                                                                 :usesPerMonth usesPerMonth}}}}
                                        :tutorials.grocery/DiscountedPrescriptionPerk$v1 {:fields {:prescriptionID :String}
                                                                                          :refines-to {:tutorials.grocery/Perk$v1 {:name "refine_to_Perk"
                                                                                                                                   :expr '{:$type :tutorials.grocery/Perk$v1
                                                                                                                                           :perkId 102
                                                                                                                                           :feePerMonth #d "3.99"
                                                                                                                                           :feePerUse #d "0.00"}}}}
                                        :tutorials.grocery/EmergencyDeliveryPerk$v1 {:refines-to {:tutorials.grocery/Perk$v1 {:name "refine_to_Perk"
                                                                                                                              :expr '{:$type :tutorials.grocery/Perk$v1
                                                                                                                                      :perkId 103
                                                                                                                                      :feePerMonth #d "0.00"
                                                                                                                                      :feePerUse #d "1.99"
                                                                                                                                      :usesPerMonth 2}}}}

                                        :tutorials.grocery/GroceryService$v1 {:fields {:deliveriesPerMonth :Integer
                                                                                       :feePerMonth [:Decimal 2]
                                                                                       :perks [:Set :tutorials.grocery/Perk$v1]
                                                                                       :subscriberCountry :tutorials.grocery/Country$v1}
                                                                              :constraints #{{:name "feePerMonth_limit" :expr '(and (< #d "5.99" feePerMonth)
                                                                                                                                    (< feePerMonth #d "12.99"))}
                                                                                             {:name "perk_limit" :expr '(<= (count perks) 2)}
                                                                                             {:name "perk_sum" :expr '(let [perkInstances (sort-by [pi (map [p perks]
                                                                                                                                                            (refine-to p :tutorials.grocery/Perk$v1))]
                                                                                                                                                   (get pi :perkId))]
                                                                                                                        (< (reduce [a #d "0.00"] [pi perkInstances]
                                                                                                                                   (+ a (get pi :feePerMonth)))
                                                                                                                           #d "6.00"))}}
                                                                              :refines-to {:tutorials.grocery/GroceryStoreSubscription$v1 {:name "refine_to_Store"
                                                                                                                                           :expr '{:$type :tutorials.grocery/GroceryStoreSubscription$v1
                                                                                                                                                   :name "Acme Foods"
                                                                                                                                                   :storeCountry subscriberCountry
                                                                                                                                                   :perkIds (map [p (sort-by [pi (map [p perks]
                                                                                                                                                                                      (refine-to p :tutorials.grocery/Perk$v1))]
                                                                                                                                                                             (get pi :perkId))]
                                                                                                                                                                 (get p :perkId))}
                                                                                                                                           :inverted? true}}}
                                        :tutorials.grocery/GroceryStoreSubscription$v1 {:fields {:name :String
                                                                                                 :storeCountry :tutorials.grocery/Country$v1
                                                                                                 :perkIds [:Vec :Integer]}
                                                                                        :constraints #{{:name "valid_stores" :expr '(or (= name "Acme Foods")
                                                                                                                                        (= name "Good Foods"))}
                                                                                                       {:name "storeCountryServed" :expr '(or (and (= name "Acme Foods")
                                                                                                                                                   (contains? #{"Canada" "Costa Rica" "US"} (get storeCountry :name)))
                                                                                                                                              (and (= name "Good Foods")
                                                                                                                                                   (contains? #{"Mexico" "US"} (get storeCountry :name))))}}}}}

                            "Taking it one part at a time. Consider first the country model. This is modeling the countries where the company is operating. This is a valid country instance."
                            {:code '{:$type :tutorials.grocery/Country$v1
                                     :name "Canada"}}
                            "Whereas this is not a valid instance."
                            {:code '{:$type :tutorials.grocery/Country$v1
                                     :name "Germany"}
                             :throws :auto}
                            "Next the model introduces the abstract notion of a 'perk'. These are extra options that can be added on to the base grocery subscription service. Each type of perk has a unique number assigned as its 'perkID', it has fees, and it has an optional value indicating how many times the perk can be used per month. The perk model includes certain rules that all valid perk instances must satisfy. So, for example, the following are valid perk instances under this model."
                            {:code '{:$type :tutorials.grocery/Perk$v1
                                     :perkId 1
                                     :feePerMonth #d "4.50"
                                     :feePerUse #d "0.00"
                                     :usesPerMonth 3}}
                            {:code '{:$type :tutorials.grocery/Perk$v1
                                     :perkId 2
                                     :feePerMonth #d "4.50"
                                     :feePerUse #d "1.40"}}
                            "While this is not a valid perk instance."
                            {:code '{:$type :tutorials.grocery/Perk$v1
                                     :perkId 1
                                     :feePerMonth #d "4.50"
                                     :feePerUse #d "0.00"
                                     :usesPerMonth 1000}
                             :throws :auto}
                            "The model then defines the three types of perks that are actually offered. The following are example instances of these three specs."
                            {:code '{:$type :tutorials.grocery/FreeDeliveryPerk$v1
                                     :usesPerMonth 10}}
                            {:code '{:$type :tutorials.grocery/DiscountedPrescriptionPerk$v1
                                     :prescriptionID "ABC"}}
                            {:code '{:$type :tutorials.grocery/EmergencyDeliveryPerk$v1}}
                            "The overall grocery service spec now pulls together perks along with the subscriber's country and some service specific fields. The grocery service includes constraints that place additional restrictions on the service being offered. The following is an example valid instance."
                            {:code '{:$type :tutorials.grocery/GroceryService$v1
                                     :deliveriesPerMonth 3
                                     :feePerMonth #d "9.99"
                                     :perks #{{:$type :tutorials.grocery/FreeDeliveryPerk$v1
                                               :usesPerMonth 1}}
                                     :subscriberCountry {:$type :tutorials.grocery/Country$v1
                                                         :name "Canada"}}}
                            "While the following violates the constraint that limits the total monthly charges for perks."
                            {:code '{:$type :tutorials.grocery/GroceryService$v1
                                     :deliveriesPerMonth 3
                                     :feePerMonth #d "9.99"
                                     :perks #{{:$type :tutorials.grocery/FreeDeliveryPerk$v1
                                               :usesPerMonth 1}
                                              {:$type :tutorials.grocery/DiscountedPrescriptionPerk$v1
                                               :prescriptionID "XYZ:123"}}
                                     :subscriberCountry {:$type :tutorials.grocery/Country$v1
                                                         :name "Canada"}}
                             :throws :auto}
                            "This spec models the service from the subscriber's perspective, but now the business needs to translate this into an order for a back-end grocery store to actually provide the delivery service. This involves executing the refinement to a subscription object."
                            {:code '(refine-to {:$type :tutorials.grocery/GroceryService$v1
                                                :deliveriesPerMonth 3
                                                :feePerMonth #d "9.99"
                                                :perks #{{:$type :tutorials.grocery/FreeDeliveryPerk$v1
                                                          :usesPerMonth 1}}
                                                :subscriberCountry {:$type :tutorials.grocery/Country$v1
                                                                    :name "Canada"}} :tutorials.grocery/GroceryStoreSubscription$v1)
                             :result :auto}
                            "This final object is now in a form that the grocery store understands."]}})
