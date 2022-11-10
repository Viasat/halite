;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.doc.data-tutorial)

(set! *warn-on-reflection* true)

(def tutorials {:spec/sudoku
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
                            {:spec-map {:spec/Sudoku$v1 {:spec-vars {:solution [:Vec [:Vec :Integer]]}}}}
                            "An instance of this spec can be constructed as:"
                            {:code '{:$type :spec/Sudoku$v1
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 1]
                                                [2 1 4 3]]}}
                            "In order to be a valid solution, certain properties must be met: each row, column, and quadrant must consist of the values 1, 2, 3, & 4. That is each number appears once and only once in each of these divisions of the grid. These necessary properties can be expressed as constraints on the spec. Let's start by expressing the constraints on each row."
                            {:spec-map {:spec/Sudoku$v2 {:spec-vars {:solution [:Vec [:Vec :Integer]]}
                                                         :constraints {:row_1 '(= (concat #{} (get solution 0))
                                                                                  #{1 2 3 4})
                                                                       :row_2 '(= (concat #{} (get solution 1))
                                                                                  #{1 2 3 4})
                                                                       :row_3 '(= (concat #{} (get solution 2))
                                                                                  #{1 2 3 4})
                                                                       :row_4 '(= (concat #{} (get solution 3))
                                                                                  #{1 2 3 4})}}}}
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
                            {:spec-map {:spec/Sudoku$v3 {:spec-vars {:solution [:Vec [:Vec :Integer]]}
                                                         :constraints {:rows '(every? [r solution]
                                                                                      (= (concat #{} r)
                                                                                         #{1 2 3 4}))}}}}
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
                            {:spec-map {:spec/Sudoku$v4 {:spec-vars {:solution [:Vec [:Vec :Integer]]}
                                                         :constraints {:rows '(every? [r solution]
                                                                                      (= (concat #{} r)
                                                                                         #{1 2 3 4}))
                                                                       :columns '(every? [i [0 1 2 3]]
                                                                                         (= #{(get-in solution [0 i])
                                                                                              (get-in solution [1 i])
                                                                                              (get-in solution [2 i])
                                                                                              (get-in solution [3 i])}
                                                                                            #{1 2 3 4}))}}}}
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
                            {:spec-map {:spec/Sudoku$v5 {:spec-vars {:solution [:Vec [:Vec :Integer]]}
                                                         :constraints {:rows '(every? [r solution]
                                                                                      (= (concat #{} r)
                                                                                         #{1 2 3 4}))
                                                                       :columns '(every? [i [0 1 2 3]]
                                                                                         (= #{(get-in solution [0 i])
                                                                                              (get-in solution [1 i])
                                                                                              (get-in solution [2 i])
                                                                                              (get-in solution [3 i])}
                                                                                            #{1 2 3 4}))
                                                                       :quadrant_1 '(= #{(get-in solution [0 0])
                                                                                         (get-in solution [0 1])
                                                                                         (get-in solution [1 0])
                                                                                         (get-in solution [1 1])}
                                                                                       #{1 2 3 4})
                                                                       :quadrant_2 '(= #{(get-in solution [0 2])
                                                                                         (get-in solution [0 3])
                                                                                         (get-in solution [1 2])
                                                                                         (get-in solution [1 3])}
                                                                                       #{1 2 3 4})
                                                                       :quadrant_3 '(= #{(get-in solution [2 0])
                                                                                         (get-in solution [2 1])
                                                                                         (get-in solution [3 0])
                                                                                         (get-in solution [3 1])}
                                                                                       #{1 2 3 4})
                                                                       :quadrant_4 '(= #{(get-in solution [2 2])
                                                                                         (get-in solution [2 3])
                                                                                         (get-in solution [3 2])
                                                                                         (get-in solution [3 3])}
                                                                                       #{1 2 3 4})}}}}
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
                            {:spec-map {:spec/Sudoku$v6 {:spec-vars {:solution [:Vec [:Vec :Integer]]}
                                                         :constraints {:rows '(every? [r solution]
                                                                                      (= (concat #{} r)
                                                                                         #{1 2 3 4}))
                                                                       :columns '(every? [i [0 1 2 3]]
                                                                                         (= #{(get-in solution [0 i])
                                                                                              (get-in solution [1 i])
                                                                                              (get-in solution [2 i])
                                                                                              (get-in solution [3 i])}
                                                                                            #{1 2 3 4}))
                                                                       :quadrants '(every? [base [[0 0] [0 2] [2 0] [2 2]]]
                                                                                           (let [base-x (get base 0)
                                                                                                 base-y (get base 1)]
                                                                                             (= #{(get-in solution [base-x base-y])
                                                                                                  (get-in solution [base-x (inc base-y)])
                                                                                                  (get-in solution [(inc base-x) base-y])
                                                                                                  (get-in solution [(inc base-x) (inc base-y)])}
                                                                                                #{1 2 3 4})))}}}}
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
                            {:spec-map {:spec/Sudoku$v7 {:spec-vars {:solution [:Vec [:Vec :Integer]]}
                                                         :constraints {:rows '(not (any? [r solution]
                                                                                         (not= (concat #{} r)
                                                                                               #{1 2 3 4})))
                                                                       :columns '(not (any? [i [0 1 2 3]]
                                                                                            (not= #{(get-in solution [0 i])
                                                                                                    (get-in solution [1 i])
                                                                                                    (get-in solution [2 i])
                                                                                                    (get-in solution [3 i])}
                                                                                                  #{1 2 3 4})))
                                                                       :quadrants '(not (any? [base [[0 0] [0 2] [2 0] [2 2]]]
                                                                                              (let [base-x (get base 0)
                                                                                                    base-y (get base 1)]
                                                                                                (not= #{(get-in solution [base-x base-y])
                                                                                                        (get-in solution [base-x (inc base-y)])
                                                                                                        (get-in solution [(inc base-x) base-y])
                                                                                                        (get-in solution [(inc base-x) (inc base-y)])}
                                                                                                      #{1 2 3 4}))))}}}}
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
                             :result :auto}]}

                :tutorials.grocery/grocery
                {:label "Model a grocery delivery business"
                 :desc "Consider how to use specs to model some of the important details of a business that provides grocery delivery to subscribers."
                 :basic-ref ['vector 'instance 'set 'fixed-decimal 'integer 'string 'spec-map]
                 :op-ref ['refine-to 'reduce 'map 'get 'and '< '<= 'count]
                 :how-to-ref [:refinement/convert-instances]
                 :explanation-ref [:tutorials.grocery/specs-as-predicates :tutorials.grocery/refinements-as-functions]
                 :contents ["The following is a full model for the grocery delivery business."

                            {:spec-map {:tutorials.grocery/Country$v1 {:spec-vars {:name :String}
                                                                       :constraints {:name_constraint '(contains? #{"Canada" "Mexico" "US"} name)}}

                                        :tutorials.grocery/Perk$v1 {:abstract? true
                                                                    :spec-vars {:perkId :Integer
                                                                                :feePerMonth [:Decimal 2]
                                                                                :feePerUse [:Decimal 2]
                                                                                :usesPerMonth [:Maybe :Integer]}
                                                                    :constraints {:feePerMonth_limit '(and (<= #d "0.00" feePerMonth)
                                                                                                           (<= feePerMonth #d "199.99"))
                                                                                  :feePerUse_limit '(and (<= #d "0.00" feePerUse)
                                                                                                         (<= feePerUse #d "14.99"))
                                                                                  :usesPerMonth_limit '(if-value usesPerMonth
                                                                                                                 (and (<= 0 usesPerMonth)
                                                                                                                      (<= usesPerMonth 999))
                                                                                                                 true)}}
                                        :tutorials.grocery/FreeDeliveryPerk$v1 {:spec-vars {:usesPerMonth :Integer}
                                                                                :constraints {:usesPerMonth_limit '(< usesPerMonth 20)}
                                                                                :refines-to {:tutorials.grocery/Perk$v1 {:name "refine_to_Perk"
                                                                                                                         :expr '{:$type :tutorials.grocery/Perk$v1
                                                                                                                                 :perkId 101
                                                                                                                                 :feePerMonth #d "2.99"
                                                                                                                                 :feePerUse #d "0.00"
                                                                                                                                 :usesPerMonth usesPerMonth}}}}
                                        :tutorials.grocery/DiscountedPrescriptionPerk$v1 {:spec-vars {:prescriptionID :String}
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

                                        :tutorials.grocery/GroceryService$v1 {:spec-vars {:deliveriesPerMonth :Integer
                                                                                          :feePerMonth [:Decimal 2]
                                                                                          :perks [:Set :tutorials.grocery/Perk$v1]
                                                                                          :subscriberCountry :tutorials.grocery/Country$v1}
                                                                              :constraints {:feePerMonth_limit '(and (< #d "5.99" feePerMonth)
                                                                                                                     (< feePerMonth #d "12.99"))
                                                                                            :perk_limit '(<= (count perks) 2)
                                                                                            :perk_sum '(let [perkInstances (sort-by [pi (map [p perks]
                                                                                                                                             (refine-to p :tutorials.grocery/Perk$v1))]
                                                                                                                                    (get pi :perkId))]
                                                                                                         (< (reduce [a #d "0.00"] [pi perkInstances]
                                                                                                                    (+ a (get pi :feePerMonth)))
                                                                                                            #d "6.00"))}
                                                                              :refines-to {:tutorials.grocery/GroceryStoreSubscription$v1 {:name "refine_to_Store"
                                                                                                                                           :expr '{:$type :tutorials.grocery/GroceryStoreSubscription$v1
                                                                                                                                                   :name "Acme Foods"
                                                                                                                                                   :storeCountry subscriberCountry
                                                                                                                                                   :perkIds (map [p (sort-by [pi (map [p perks]
                                                                                                                                                                                      (refine-to p :tutorials.grocery/Perk$v1))]
                                                                                                                                                                             (get pi :perkId))]
                                                                                                                                                                 (get p :perkId))}
                                                                                                                                           :inverted? true}}}
                                        :tutorials.grocery/GroceryStoreSubscription$v1 {:spec-vars {:name :String
                                                                                                    :storeCountry :tutorials.grocery/Country$v1
                                                                                                    :perkIds [:Vec :Integer]}
                                                                                        :constraints {:valid_stores '(or (= name "Acme Foods")
                                                                                                                         (= name "Good Foods"))
                                                                                                      :storeCountryServed '(or (and (= name "Acme Foods")
                                                                                                                                    (contains? #{"Canada" "Costa Rica" "US"} (get storeCountry :name)))
                                                                                                                               (and (= name "Good Foods")
                                                                                                                                    (contains? #{"Mexico" "US"} (get storeCountry :name))))}}}}

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
