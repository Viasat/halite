<!---
  This markdown file was generated. Do not edit.
  -->

## Model a vending machine as a state machine

Use specs to map out a state space and valid transitions

We can model the state space for a vending machine that accepts nickels, dimes, and quarters and which vends  snacks for $0.50 and beverages for $1.00.

```clojure
{:tutorials.vending/State$v1
   {:fields {:balance [:Decimal 2],
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
{:$type :tutorials.vending/State$v1,
 :balance #d "0.00",
 :beverageCount 10,
 :snackCount 15}
```

Let us add a spec that will capture the constraints that identify a valid initial state for a vending machine.

```clojure
{:tutorials.vending/InitialState$v1
   {:fields {:balance [:Decimal 2],
             :beverageCount :Integer,
             :snackCount :Integer},
    :constraints #{'{:name "initial_state",
                     :expr (and (= #d "0.00" balance)
                                (> beverageCount 0)
                                (> snackCount 0))}},
    :refines-to {:tutorials.vending/State$v1
                   {:name "toVending",
                    :expr '{:$type :tutorials.vending/State$v1,
                            :balance balance,
                            :beverageCount beverageCount,
                            :snackCount snackCount}}}}}
```

This additional spec can be used to determine whether a state is a valid initial state for the machine. For example, this is a valid initial state.

```clojure
{:$type :tutorials.vending/InitialState$v1,
 :balance #d "0.00",
 :beverageCount 10,
 :snackCount 15}
```

The corresponding vending state can be produced from the initial state:

```clojure
(refine-to {:$type :tutorials.vending/InitialState$v1,
            :balance #d "0.00",
            :beverageCount 10,
            :snackCount 15}
           :tutorials.vending/State$v1)


;-- result --
{:$type :tutorials.vending/State$v1,
 :balance #d "0.00",
 :beverageCount 10,
 :snackCount 15}
```

The following is not a valid initial state.

```clojure
{:$type :tutorials.vending/InitialState$v1,
 :balance #d "0.00",
 :beverageCount 0,
 :snackCount 15}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.vending/InitialState$v1', violates constraints \"tutorials.vending/InitialState$v1/initial_state\""
 :h-err/invalid-instance]
```

So now we have a model of the state space and valid initial states for the machine. However, we would like to also model valid state transitions.

```clojure
{:tutorials.vending/Transition$v1
   {:fields {:current :tutorials.vending/State$v1,
             :next :tutorials.vending/State$v1},
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
                   (= (get next :snackCount) (get current :snackCount)))
              (= current next))}}}}
```

A valid transition representing a dime being dropped into the machine.

```clojure
{:$type :tutorials.vending/Transition$v1,
 :current {:$type :tutorials.vending/State$v1,
           :balance #d "0.00",
           :beverageCount 10,
           :snackCount 15},
 :next {:$type :tutorials.vending/State$v1,
        :balance #d "0.10",
        :beverageCount 10,
        :snackCount 15}}
```

An invalid transition, because the balance cannot increase by $0.07

```clojure
{:$type :tutorials.vending/Transition$v1,
 :current {:$type :tutorials.vending/State$v1,
           :balance #d "0.00",
           :beverageCount 10,
           :snackCount 15},
 :next {:$type :tutorials.vending/State$v1,
        :balance #d "0.07",
        :beverageCount 10,
        :snackCount 15}}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.vending/Transition$v1', violates constraints \"tutorials.vending/Transition$v1/state_transitions\""
 :h-err/invalid-instance]
```

A valid transition representing a snack being vended.

```clojure
{:$type :tutorials.vending/Transition$v1,
 :current {:$type :tutorials.vending/State$v1,
           :balance #d "0.75",
           :beverageCount 10,
           :snackCount 15},
 :next {:$type :tutorials.vending/State$v1,
        :balance #d "0.25",
        :beverageCount 10,
        :snackCount 14}}
```

An invalid attempted transition representing a snack being vended.

```clojure
{:$type :tutorials.vending/Transition$v1,
 :current {:$type :tutorials.vending/State$v1,
           :balance #d "0.75",
           :beverageCount 10,
           :snackCount 15},
 :next {:$type :tutorials.vending/State$v1,
        :balance #d "0.25",
        :beverageCount 9,
        :snackCount 14}}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.vending/Transition$v1', violates constraints \"tutorials.vending/Transition$v1/state_transitions\""
 :h-err/invalid-instance]
```

It is a bit subtle, but our constraints also allow the state to be unchanged. This will turn out to be useful for us later.

```clojure
{:$type :tutorials.vending/Transition$v1,
 :current {:$type :tutorials.vending/State$v1,
           :balance #d "0.00",
           :beverageCount 10,
           :snackCount 15},
 :next {:$type :tutorials.vending/State$v1,
        :balance #d "0.00",
        :beverageCount 10,
        :snackCount 15}}
```

At this point we have modeled valid state transitions without modeling the events that trigger those transitions. That may be sufficient for what we are looking to accomplish, but let's take it further and model a possible event structure.

```clojure
{:tutorials.vending/AbstractEvent$v1 {:abstract? true,
                                      :fields {:balanceDelta [:Decimal 2],
                                               :beverageDelta :Integer,
                                               :snackDelta :Integer}},
 :tutorials.vending/CoinEvent$v1
   {:fields {:denomination :String},
    :constraints #{'{:name "valid_coin",
                     :expr (contains? #{"dime" "nickel" "quarter"}
                                      denomination)}},
    :refines-to {:tutorials.vending/AbstractEvent$v1
                   {:name "coin_event_to_abstract",
                    :expr '{:$type :tutorials.vending/AbstractEvent$v1,
                            :balanceDelta
                              (cond (= "nickel" denomination) #d "0.05"
                                    (= "dime" denomination) #d "0.10"
                                    #d "0.25"),
                            :beverageDelta 0,
                            :snackDelta 0}}}},
 :tutorials.vending/VendEvent$v1
   {:fields {:item :String},
    :constraints #{'{:name "valid_item",
                     :expr (contains? #{"beverage" "snack"} item)}},
    :refines-to {:tutorials.vending/AbstractEvent$v1
                   {:name "vend_event_to_abstract",
                    :expr '{:$type :tutorials.vending/AbstractEvent$v1,
                            :balanceDelta
                              (if (= "snack" item) #d "-0.50" #d "-1.00"),
                            :beverageDelta (if (= "snack" item) 0 -1),
                            :snackDelta (if (= "snack" item) -1 0)}}}}}
```

Now we can construct the following events.

```clojure
[{:$type :tutorials.vending/CoinEvent$v1,
  :denomination "nickel"}
 {:$type :tutorials.vending/CoinEvent$v1,
  :denomination "dime"}
 {:$type :tutorials.vending/CoinEvent$v1,
  :denomination "quarter"}
 {:$type :tutorials.vending/VendEvent$v1,
  :item "snack"}
 {:$type :tutorials.vending/VendEvent$v1,
  :item "beverage"}]
```

We can verify that all of these events produce the expected abstract events.

```clojure
(map
  [e
   [{:$type :tutorials.vending/CoinEvent$v1,
     :denomination "nickel"}
    {:$type :tutorials.vending/CoinEvent$v1,
     :denomination "dime"}
    {:$type :tutorials.vending/CoinEvent$v1,
     :denomination "quarter"}
    {:$type :tutorials.vending/VendEvent$v1,
     :item "snack"}
    {:$type :tutorials.vending/VendEvent$v1,
     :item "beverage"}]]
  (refine-to e :tutorials.vending/AbstractEvent$v1))


;-- result --
[{:$type :tutorials.vending/AbstractEvent$v1,
  :balanceDelta #d "0.05",
  :beverageDelta 0,
  :snackDelta 0}
 {:$type :tutorials.vending/AbstractEvent$v1,
  :balanceDelta #d "0.10",
  :beverageDelta 0,
  :snackDelta 0}
 {:$type :tutorials.vending/AbstractEvent$v1,
  :balanceDelta #d "0.25",
  :beverageDelta 0,
  :snackDelta 0}
 {:$type :tutorials.vending/AbstractEvent$v1,
  :balanceDelta #d "-0.50",
  :beverageDelta 0,
  :snackDelta -1}
 {:$type :tutorials.vending/AbstractEvent$v1,
  :balanceDelta #d "-1.00",
  :beverageDelta -1,
  :snackDelta 0}]
```

As the next step, we add a spec which will take a vending machine state and and event as input to produce a new vending machine state as output.

```clojure
{:tutorials.vending/EventHandler$v1
   {:fields {:current :tutorials.vending/State$v1,
             :event :tutorials.vending/AbstractEvent$v1},
    :refines-to
      {:tutorials.vending/Transition$v1
         {:name "event_handler",
          :expr '{:$type :tutorials.vending/Transition$v1,
                  :current current,
                  :next (let [ae (refine-to event
                                            :tutorials.vending/AbstractEvent$v1)
                              newBalance (+ (get current :balance)
                                            (get ae :balanceDelta))
                              newBeverageCount (+ (get current :beverageCount)
                                                  (get ae :beverageDelta))
                              newSnackCount (+ (get current :snackCount)
                                               (get ae :snackDelta))]
                          (if (and (>= newBalance #d "0.00")
                                   (>= newBeverageCount 0)
                                   (>= newSnackCount 0))
                            {:$type :tutorials.vending/State$v1,
                             :balance newBalance,
                             :beverageCount newBeverageCount,
                             :snackCount newSnackCount}
                            current))}}}}}
```

Note that in the event handler we place the new state into a transition instance. This will ensure that the new state represents a valid transition per the constraints in that spec. Also note that we are not changing our state if the event would cause our balance or counts to go negative. Since the transition spec allows the state to be unchanged we can simply user our current state as the new state in these cases.

Let's exercise the event handler to see if works as we expect.

```clojure
(refine-to {:$type :tutorials.vending/EventHandler$v1,
            :current {:$type :tutorials.vending/State$v1,
                      :balance #d "0.10",
                      :beverageCount 5,
                      :snackCount 6},
            :event {:$type :tutorials.vending/CoinEvent$v1,
                    :denomination "quarter"}}
           :tutorials.vending/Transition$v1)


;-- result --
{:$type :tutorials.vending/Transition$v1,
 :current {:$type :tutorials.vending/State$v1,
           :balance #d "0.10",
           :beverageCount 5,
           :snackCount 6},
 :next {:$type :tutorials.vending/State$v1,
        :balance #d "0.35",
        :beverageCount 5,
        :snackCount 6}}
```

If we try to process an event that cannot be handled then the state is unchanged.

```clojure
(refine-to {:$type :tutorials.vending/EventHandler$v1,
            :current {:$type :tutorials.vending/State$v1,
                      :balance #d "0.10",
                      :beverageCount 5,
                      :snackCount 6},
            :event {:$type :tutorials.vending/VendEvent$v1,
                    :item "snack"}}
           :tutorials.vending/Transition$v1)


;-- result --
{:$type :tutorials.vending/Transition$v1,
 :current {:$type :tutorials.vending/State$v1,
           :balance #d "0.10",
           :beverageCount 5,
           :snackCount 6},
 :next {:$type :tutorials.vending/State$v1,
        :balance #d "0.10",
        :beverageCount 5,
        :snackCount 6}}
```

We have come this far we might as well add one more spec that ties it all together via an initial state and a sequence of events.

```clojure
{:tutorials.vending/Behavior$v1
   {:fields {:events [:Vec :tutorials.vending/AbstractEvent$v1],
             :initial :tutorials.vending/InitialState$v1},
    :refines-to
      {:tutorials.vending/State$v1
         {:name "apply_events",
          :expr '(reduce [a (refine-to initial :tutorials.vending/State$v1)]
                   [e events]
                   (get (refine-to {:$type :tutorials.vending/EventHandler$v1,
                                    :current a,
                                    :event e}
                                   :tutorials.vending/Transition$v1)
                        :next))}}}}
```

From an initial state and a sequence of events we can compute the final state.

```clojure
(refine-to {:$type :tutorials.vending/Behavior$v1,
            :initial {:$type :tutorials.vending/InitialState$v1,
                      :balance #d "0.00",
                      :beverageCount 10,
                      :snackCount 15},
            :events [{:$type :tutorials.vending/CoinEvent$v1,
                      :denomination "quarter"}
                     {:$type :tutorials.vending/CoinEvent$v1,
                      :denomination "nickel"}
                     {:$type :tutorials.vending/VendEvent$v1,
                      :item "snack"}
                     {:$type :tutorials.vending/CoinEvent$v1,
                      :denomination "dime"}
                     {:$type :tutorials.vending/CoinEvent$v1,
                      :denomination "quarter"}
                     {:$type :tutorials.vending/VendEvent$v1,
                      :item "snack"}
                     {:$type :tutorials.vending/CoinEvent$v1,
                      :denomination "dime"}
                     {:$type :tutorials.vending/CoinEvent$v1,
                      :denomination "nickel"}
                     {:$type :tutorials.vending/CoinEvent$v1,
                      :denomination "dime"}
                     {:$type :tutorials.vending/CoinEvent$v1,
                      :denomination "quarter"}
                     {:$type :tutorials.vending/CoinEvent$v1,
                      :denomination "quarter"}
                     {:$type :tutorials.vending/CoinEvent$v1,
                      :denomination "quarter"}
                     {:$type :tutorials.vending/VendEvent$v1,
                      :item "beverage"}
                     {:$type :tutorials.vending/VendEvent$v1,
                      :item "beverage"}]}
           :tutorials.vending/State$v1)


;-- result --
{:$type :tutorials.vending/State$v1,
 :balance #d "0.15",
 :beverageCount 9,
 :snackCount 14}
```

Note that some of the vend events were effectively ignored because the balance was too low.

### Reference

#### Basic elements:

[`fixed-decimal`](../halite_basic-syntax-reference.md#fixed-decimal), [`integer`](../halite_basic-syntax-reference.md#integer), [`set`](../halite_basic-syntax-reference.md#set), [`string`](../halite_basic-syntax-reference.md#string), [`vector`](../halite_basic-syntax-reference.md#vector)

#### How Tos:

* [convert-instances](../how-to/halite_convert-instances.md)


#### Explanations:

* [refinements-as-functions](../explanation/halite_refinements-as-functions.md)
* [specs-as-predicates](../explanation/halite_specs-as-predicates.md)


