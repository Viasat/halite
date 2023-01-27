<!---
  This markdown file was generated. Do not edit.
  -->

## Model a vending machine as a state machine

Use specs to map out a state space and valid transitions

We can model the state space for a vending machine that accepts nickels, dimes, and quarters and which vends  snacks for $0.50 and beverages for $1.00.

```java
{
  "tutorials.vending/State$v1" : {
    "fields" : {
      "balance" : [ "Decimal", 2 ],
      "beverageCount" : "Integer",
      "snackCount" : "Integer"
    },
    "constraints" : [ "{expr: ((beverageCount >= 0) && (snackCount >= 0)), name: \"counts_not_negative\"}", "{expr: ((beverageCount <= 20) && (snackCount <= 20)), name: \"counts_below_capacity\"}", "{expr: (balance >= #d \"0.00\"), name: \"balance_not_negative\"}" ]
  }
}
```

With this spec we can construct instances.

```java
{$type: tutorials.vending/State$v1, balance: #d "0.00", beverageCount: 10, snackCount: 15}
```

Let us add a spec that will capture the constraints that identify a valid initial state for a vending machine.

```java
{
  "tutorials.vending/InitialState$v1" : {
    "fields" : {
      "balance" : [ "Decimal", 2 ],
      "beverageCount" : "Integer",
      "snackCount" : "Integer"
    },
    "constraints" : [ "{expr: ((#d \"0.00\" == balance) && (beverageCount > 0) && (snackCount > 0)), name: \"initial_state\"}" ],
    "refines-to" : {
      "tutorials.vending/State$v1" : {
        "name" : "toVending",
        "expr" : "{$type: tutorials.vending/State$v1, balance: balance, beverageCount: beverageCount, snackCount: snackCount}"
      }
    }
  }
}
```

This additional spec can be used to determine whether a state is a valid initial state for the machine. For example, this is a valid initial state.

```java
{$type: tutorials.vending/InitialState$v1, balance: #d "0.00", beverageCount: 10, snackCount: 15}
```

The corresponding vending state can be produced from the initial state:

```java
{$type: tutorials.vending/InitialState$v1, balance: #d "0.00", beverageCount: 10, snackCount: 15}.refineTo( tutorials.vending/State$v1 )


//-- result --
{$type: tutorials.vending/State$v1, balance: #d "0.00", beverageCount: 10, snackCount: 15}
```

The following is not a valid initial state.

```java
{$type: tutorials.vending/InitialState$v1, balance: #d "0.00", beverageCount: 0, snackCount: 15}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.vending/InitialState$v1', violates constraints \"tutorials.vending/InitialState$v1/initial_state\""]
```

So now we have a model of the state space and valid initial states for the machine. However, we would like to also model valid state transitions.

```java
{
  "tutorials.vending/Transition$v1" : {
    "fields" : {
      "current" : "tutorials.vending/State$v1",
      "next" : "tutorials.vending/State$v1"
    },
    "constraints" : [ "{expr: ((#{#d \"0.05\", #d \"0.10\", #d \"0.25\"}.contains?((next.balance - current.balance)) && (next.beverageCount == current.beverageCount) && (next.snackCount == current.snackCount)) || ((#d \"0.50\" == (current.balance - next.balance)) && (next.beverageCount == current.beverageCount) && (next.snackCount == (current.snackCount - 1))) || ((#d \"1.00\" == (current.balance - next.balance)) && (next.beverageCount == (current.beverageCount - 1)) && (next.snackCount == current.snackCount)) || (current == next)), name: \"state_transitions\"}" ]
  }
}
```

A valid transition representing a dime being dropped into the machine.

```java
{$type: tutorials.vending/Transition$v1, current: {$type: tutorials.vending/State$v1, balance: #d "0.00", beverageCount: 10, snackCount: 15}, next: {$type: tutorials.vending/State$v1, balance: #d "0.10", beverageCount: 10, snackCount: 15}}
```

An invalid transition, because the balance cannot increase by $0.07

```java
{$type: tutorials.vending/Transition$v1, current: {$type: tutorials.vending/State$v1, balance: #d "0.00", beverageCount: 10, snackCount: 15}, next: {$type: tutorials.vending/State$v1, balance: #d "0.07", beverageCount: 10, snackCount: 15}}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.vending/Transition$v1', violates constraints \"tutorials.vending/Transition$v1/state_transitions\""]
```

A valid transition representing a snack being vended.

```java
{$type: tutorials.vending/Transition$v1, current: {$type: tutorials.vending/State$v1, balance: #d "0.75", beverageCount: 10, snackCount: 15}, next: {$type: tutorials.vending/State$v1, balance: #d "0.25", beverageCount: 10, snackCount: 14}}
```

An invalid attempted transition representing a snack being vended.

```java
{$type: tutorials.vending/Transition$v1, current: {$type: tutorials.vending/State$v1, balance: #d "0.75", beverageCount: 10, snackCount: 15}, next: {$type: tutorials.vending/State$v1, balance: #d "0.25", beverageCount: 9, snackCount: 14}}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.vending/Transition$v1', violates constraints \"tutorials.vending/Transition$v1/state_transitions\""]
```

It is a bit subtle, but our constraints also allow the state to be unchanged. This will turn out to be useful for us later.

```java
{$type: tutorials.vending/Transition$v1, current: {$type: tutorials.vending/State$v1, balance: #d "0.00", beverageCount: 10, snackCount: 15}, next: {$type: tutorials.vending/State$v1, balance: #d "0.00", beverageCount: 10, snackCount: 15}}
```

At this point we have modeled valid state transitions without modeling the events that trigger those transitions. That may be sufficient for what we are looking to accomplish, but let's take it further and model a possible event structure.

```java
{
  "tutorials.vending/AbstractEvent$v1" : {
    "abstract?" : true,
    "fields" : {
      "balanceDelta" : [ "Decimal", 2 ],
      "beverageDelta" : "Integer",
      "snackDelta" : "Integer"
    }
  },
  "tutorials.vending/CoinEvent$v1" : {
    "fields" : {
      "denomination" : "String"
    },
    "constraints" : [ "{expr: #{\"dime\", \"nickel\", \"quarter\"}.contains?(denomination), name: \"valid_coin\"}" ],
    "refines-to" : {
      "tutorials.vending/AbstractEvent$v1" : {
        "name" : "coin_event_to_abstract",
        "expr" : "{$type: tutorials.vending/AbstractEvent$v1, balanceDelta: (if((\"nickel\" == denomination)) {#d \"0.05\"} else {(if((\"dime\" == denomination)) {#d \"0.10\"} else {#d \"0.25\"})}), beverageDelta: 0, snackDelta: 0}"
      }
    }
  },
  "tutorials.vending/VendEvent$v1" : {
    "fields" : {
      "item" : "String"
    },
    "constraints" : [ "{expr: #{\"beverage\", \"snack\"}.contains?(item), name: \"valid_item\"}" ],
    "refines-to" : {
      "tutorials.vending/AbstractEvent$v1" : {
        "name" : "vend_event_to_abstract",
        "expr" : "{$type: tutorials.vending/AbstractEvent$v1, balanceDelta: (if((\"snack\" == item)) {#d \"-0.50\"} else {#d \"-1.00\"}), beverageDelta: (if((\"snack\" == item)) {0} else {-1}), snackDelta: (if((\"snack\" == item)) {-1} else {0})}"
      }
    }
  }
}
```

Now we can construct the following events.

```java
[{$type: tutorials.vending/CoinEvent$v1, denomination: "nickel"}, {$type: tutorials.vending/CoinEvent$v1, denomination: "dime"}, {$type: tutorials.vending/CoinEvent$v1, denomination: "quarter"}, {$type: tutorials.vending/VendEvent$v1, item: "snack"}, {$type: tutorials.vending/VendEvent$v1, item: "beverage"}]
```

We can verify that all of these events produce the expected abstract events.

```java
(map(e in [{$type: tutorials.vending/CoinEvent$v1, denomination: "nickel"}, {$type: tutorials.vending/CoinEvent$v1, denomination: "dime"}, {$type: tutorials.vending/CoinEvent$v1, denomination: "quarter"}, {$type: tutorials.vending/VendEvent$v1, item: "snack"}, {$type: tutorials.vending/VendEvent$v1, item: "beverage"}])e.refineTo( tutorials.vending/AbstractEvent$v1 ))


//-- result --
[{$type: tutorials.vending/AbstractEvent$v1, balanceDelta: #d "0.05", beverageDelta: 0, snackDelta: 0}, {$type: tutorials.vending/AbstractEvent$v1, balanceDelta: #d "0.10", beverageDelta: 0, snackDelta: 0}, {$type: tutorials.vending/AbstractEvent$v1, balanceDelta: #d "0.25", beverageDelta: 0, snackDelta: 0}, {$type: tutorials.vending/AbstractEvent$v1, balanceDelta: #d "-0.50", beverageDelta: 0, snackDelta: -1}, {$type: tutorials.vending/AbstractEvent$v1, balanceDelta: #d "-1.00", beverageDelta: -1, snackDelta: 0}]
```

As the next step, we add a spec which will take a vending machine state and and event as input to produce a new vending machine state as output.

```java
{
  "tutorials.vending/EventHandler$v1" : {
    "fields" : {
      "current" : "tutorials.vending/State$v1",
      "event" : "tutorials.vending/AbstractEvent$v1"
    },
    "refines-to" : {
      "tutorials.vending/Transition$v1" : {
        "name" : "event_handler",
        "expr" : "{$type: tutorials.vending/Transition$v1, current: current, next: ({ ae = event.refineTo( tutorials.vending/AbstractEvent$v1 ); newBalance = (current.balance + ae.balanceDelta); newBeverageCount = (current.beverageCount + ae.beverageDelta); newSnackCount = (current.snackCount + ae.snackDelta); (if(((newBalance >= #d \"0.00\") && (newBeverageCount >= 0) && (newSnackCount >= 0))) {{$type: tutorials.vending/State$v1, balance: newBalance, beverageCount: newBeverageCount, snackCount: newSnackCount}} else {current}) })}"
      }
    }
  }
}
```

Note that in the event handler we place the new state into a transition instance. This will ensure that the new state represents a valid transition per the constraints in that spec. Also note that we are not changing our state if the event would cause our balance or counts to go negative. Since the transition spec allows the state to be unchanged we can simply user our current state as the new state in these cases.

Let's exercise the event handler to see if works as we expect.

```java
{$type: tutorials.vending/EventHandler$v1, current: {$type: tutorials.vending/State$v1, balance: #d "0.10", beverageCount: 5, snackCount: 6}, event: {$type: tutorials.vending/CoinEvent$v1, denomination: "quarter"}}.refineTo( tutorials.vending/Transition$v1 )


//-- result --
{$type: tutorials.vending/Transition$v1, current: {$type: tutorials.vending/State$v1, balance: #d "0.10", beverageCount: 5, snackCount: 6}, next: {$type: tutorials.vending/State$v1, balance: #d "0.35", beverageCount: 5, snackCount: 6}}
```

If we try to process an event that cannot be handled then the state is unchanged.

```java
{$type: tutorials.vending/EventHandler$v1, current: {$type: tutorials.vending/State$v1, balance: #d "0.10", beverageCount: 5, snackCount: 6}, event: {$type: tutorials.vending/VendEvent$v1, item: "snack"}}.refineTo( tutorials.vending/Transition$v1 )


//-- result --
{$type: tutorials.vending/Transition$v1, current: {$type: tutorials.vending/State$v1, balance: #d "0.10", beverageCount: 5, snackCount: 6}, next: {$type: tutorials.vending/State$v1, balance: #d "0.10", beverageCount: 5, snackCount: 6}}
```

We have come this far we might as well add one more spec that ties it all together via an initial state and a sequence of events.

```java
{
  "tutorials.vending/Behavior$v1" : {
    "fields" : {
      "initial" : "tutorials.vending/InitialState$v1",
      "events" : [ "Vec", "tutorials.vending/AbstractEvent$v1" ]
    },
    "refines-to" : {
      "tutorials.vending/State$v1" : {
        "name" : "apply_events",
        "expr" : "(reduce( a = initial.refineTo( tutorials.vending/State$v1 ); e in events ) { {$type: tutorials.vending/EventHandler$v1, current: a, event: e}.refineTo( tutorials.vending/Transition$v1 ).next })"
      }
    }
  }
}
```

From an initial state and a sequence of events we can compute the final state.

```java
{$type: tutorials.vending/Behavior$v1, events: [{$type: tutorials.vending/CoinEvent$v1, denomination: "quarter"}, {$type: tutorials.vending/CoinEvent$v1, denomination: "nickel"}, {$type: tutorials.vending/VendEvent$v1, item: "snack"}, {$type: tutorials.vending/CoinEvent$v1, denomination: "dime"}, {$type: tutorials.vending/CoinEvent$v1, denomination: "quarter"}, {$type: tutorials.vending/VendEvent$v1, item: "snack"}, {$type: tutorials.vending/CoinEvent$v1, denomination: "dime"}, {$type: tutorials.vending/CoinEvent$v1, denomination: "nickel"}, {$type: tutorials.vending/CoinEvent$v1, denomination: "dime"}, {$type: tutorials.vending/CoinEvent$v1, denomination: "quarter"}, {$type: tutorials.vending/CoinEvent$v1, denomination: "quarter"}, {$type: tutorials.vending/CoinEvent$v1, denomination: "quarter"}, {$type: tutorials.vending/VendEvent$v1, item: "beverage"}, {$type: tutorials.vending/VendEvent$v1, item: "beverage"}], initial: {$type: tutorials.vending/InitialState$v1, balance: #d "0.00", beverageCount: 10, snackCount: 15}}.refineTo( tutorials.vending/State$v1 )


//-- result --
{$type: tutorials.vending/State$v1, balance: #d "0.15", beverageCount: 9, snackCount: 14}
```

Note that some of the vend events were effectively ignored because the balance was too low.

### Reference

#### Basic elements:

[`fixed-decimal`](../halite_basic-syntax-reference-j.md#fixed-decimal), [`integer`](../halite_basic-syntax-reference-j.md#integer), [`set`](../halite_basic-syntax-reference-j.md#set), [`string`](../halite_basic-syntax-reference-j.md#string), [`vector`](../halite_basic-syntax-reference-j.md#vector)

#### How Tos:

* [convert-instances](../how-to/halite_convert-instances-j.md)


#### Explanations:

* [refinements-as-functions](../explanation/halite_refinements-as-functions-j.md)
* [specs-as-predicates](../explanation/halite_specs-as-predicates-j.md)


