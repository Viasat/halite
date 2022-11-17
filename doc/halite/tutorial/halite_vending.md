<!---
  This markdown file was generated. Do not edit.
  -->

## Model a vending machine as a state machine

Use specs to map out a state space and valid transitions

We can model the state space for a vending machine that accepts nickels, dimes, and quarters and which vends  snacks for $0.50 and beverages for $1.00.

```clojure
{:tutorials.vending/Vending$v1
   {:spec-vars {:balance [:Decimal 2],
                :beverageCount :Integer,
                :snackCount :Integer},
    :constraints #{'{:name "balance_not_negative",
                     :expr (>= balance #d "0.00")}
                   '{:name "counts_below_capacity",
                     :expr (and (<= beverageCount 20) (<= snackCount 20))}
                   '{:name "counts_not_negative",
                     :expr (and (>= beverageCount 0) (>= snackCount 0))}}}}
```

With this spec we can construct instances.

```clojure
{:$type :tutorials.vending/Vending$v1,
 :balance #d "0.00",
 :beverageCount 10,
 :snackCount 15}
```

Let us add a spec that will capture the constraints that identify a valid initial state for a vending machine.

```clojure
{:tutorials.vending/InitialVending$v1
   {:spec-vars {:balance [:Decimal 2],
                :beverageCount :Integer,
                :snackCount :Integer},
    :constraints #{'{:name "initial_state",
                     :expr (and (= #d "0.00" balance)
                                (> beverageCount 0)
                                (> snackCount 0))}},
    :refines-to {:tutorials.vending/Vending$v1
                   {:name "toVending",
                    :expr '{:$type :tutorials.vending/Vending$v1,
                            :balance balance,
                            :beverageCount beverageCount,
                            :snackCount snackCount}}}},
 :tutorials.vending/Vending$v1
   {:spec-vars {:balance [:Decimal 2],
                :beverageCount :Integer,
                :snackCount :Integer},
    :constraints #{'{:name "balance_not_negative",
                     :expr (>= balance #d "0.00")}
                   '{:name "counts_not_negative",
                     :expr (and (>= beverageCount 0) (>= snackCount 0))}}}}
```

This additional spec can be used to determine where a state is a valid initial state for the machine. For example, this is a valid initial state.

```clojure
{:$type :tutorials.vending/InitialVending$v1,
 :balance #d "0.00",
 :beverageCount 10,
 :snackCount 15}
```

The corresponding vending state can be 'extracted' from the initial state:

```clojure
(refine-to {:$type :tutorials.vending/InitialVending$v1,
            :balance #d "0.00",
            :beverageCount 10,
            :snackCount 15}
           :tutorials.vending/Vending$v1)


;-- result --
{:$type :tutorials.vending/Vending$v1,
 :balance #d "0.00",
 :beverageCount 10,
 :snackCount 15}
```

However, this is not a valid initial state.

```clojure
{:$type :tutorials.vending/InitialVending$v1,
 :balance #d "0.00",
 :beverageCount 0,
 :snackCount 15}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.vending/InitialVending$v1', violates constraints \"tutorials.vending/InitialVending$v1/initial_state\""
 :h-err/invalid-instance]
```

So now we have a model of the state space and valid initial states for the machine. However, we would like to also model valid state transitions.

```clojure
{:tutorials.vending/Vending$v1
   {:spec-vars {:balance [:Decimal 2],
                :beverageCount :Integer,
                :snackCount :Integer},
    :constraints #{'{:name "balance_not_negative",
                     :expr (>= balance #d "0.00")}
                   '{:name "counts_not_negative",
                     :expr (and (>= beverageCount 0) (>= snackCount 0))}}},
 :tutorials.vending/VendingTransition$v1
   {:spec-vars {:current :tutorials.vending/Vending$v1,
                :next :tutorials.vending/Vending$v1},
    :constraints
      #{'{:name "state_transitions",
          :expr
            (or
              (and (contains? #{#d "0.10" #d "0.25" #d "0.05"}
                              (- (get next :balance) (get current :balance)))
                   (= (get next :beverageCount) (get current :beverageCount))
                   (= (get next :snackCount) (get current :snackCount)))
              (and (= #d "0.50" (- (get current :balance) (get next :balance)))
                   (= (get next :beverageCount) (get current :beverageCount))
                   (= (get next :snackCount) (dec (get current :snackCount))))
              (and (= #d "1.00" (- (get current :balance) (get next :balance)))
                   (= (get next :beverageCount)
                      (dec (get current :beverageCount)))
                   (= (get next :snackCount) (get current :snackCount))))}}}}
```

A valid transition representing a dime being dropped into the machine.

```clojure
{:$type :tutorials.vending/VendingTransition$v1,
 :current {:$type :tutorials.vending/Vending$v1,
           :balance #d "0.00",
           :beverageCount 10,
           :snackCount 15},
 :next {:$type :tutorials.vending/Vending$v1,
        :balance #d "0.10",
        :beverageCount 10,
        :snackCount 15}}
```

An invalid transition, because the balance cannot increase by $0.07

```clojure
{:$type :tutorials.vending/VendingTransition$v1,
 :current {:$type :tutorials.vending/Vending$v1,
           :balance #d "0.00",
           :beverageCount 10,
           :snackCount 15},
 :next {:$type :tutorials.vending/Vending$v1,
        :balance #d "0.07",
        :beverageCount 10,
        :snackCount 15}}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.vending/VendingTransition$v1', violates constraints \"tutorials.vending/VendingTransition$v1/state_transitions\""
 :h-err/invalid-instance]
```

A valid transition representing a snack being vended.

```clojure
{:$type :tutorials.vending/VendingTransition$v1,
 :current {:$type :tutorials.vending/Vending$v1,
           :balance #d "0.75",
           :beverageCount 10,
           :snackCount 15},
 :next {:$type :tutorials.vending/Vending$v1,
        :balance #d "0.25",
        :beverageCount 10,
        :snackCount 14}}
```

An invalid attempted transition representing a snack being vended.

```clojure
{:$type :tutorials.vending/VendingTransition$v1,
 :current {:$type :tutorials.vending/Vending$v1,
           :balance #d "0.75",
           :beverageCount 10,
           :snackCount 15},
 :next {:$type :tutorials.vending/Vending$v1,
        :balance #d "0.25",
        :beverageCount 9,
        :snackCount 14}}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.vending/VendingTransition$v1', violates constraints \"tutorials.vending/VendingTransition$v1/state_transitions\""
 :h-err/invalid-instance]
```

