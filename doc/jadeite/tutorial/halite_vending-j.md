<!---
  This markdown file was generated. Do not edit.
  -->

## Model a vending machine as a state machine

Use specs to map out a state space and valid transitions

We can model the state space for a vending machine that accepts nickels, dimes, and quarters and which vends  snacks for $0.50 and beverages for $1.00.

```java
{
  "tutorials.vending/Vending$v1" : {
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
{$type: tutorials.vending/Vending$v1, balance: #d "0.00", beverageCount: 10, snackCount: 15}
```

Let us add a spec that will capture the constraints that identify a valid initial state for a vending machine.

```java
{
  "tutorials.vending/Vending$v1" : {
    "fields" : {
      "balance" : [ "Decimal", 2 ],
      "beverageCount" : "Integer",
      "snackCount" : "Integer"
    },
    "constraints" : [ "{expr: ((beverageCount >= 0) && (snackCount >= 0)), name: \"counts_not_negative\"}", "{expr: (balance >= #d \"0.00\"), name: \"balance_not_negative\"}" ]
  },
  "tutorials.vending/InitialVending$v1" : {
    "fields" : {
      "balance" : [ "Decimal", 2 ],
      "beverageCount" : "Integer",
      "snackCount" : "Integer"
    },
    "constraints" : [ "{expr: ((#d \"0.00\" == balance) && (beverageCount > 0) && (snackCount > 0)), name: \"initial_state\"}" ],
    "refines-to" : {
      "tutorials.vending/Vending$v1" : {
        "name" : "toVending",
        "expr" : "{$type: tutorials.vending/Vending$v1, balance: balance, beverageCount: beverageCount, snackCount: snackCount}"
      }
    }
  }
}
```

This additional spec can be used to determine where a state is a valid initial state for the machine. For example, this is a valid initial state.

```java
{$type: tutorials.vending/InitialVending$v1, balance: #d "0.00", beverageCount: 10, snackCount: 15}
```

The corresponding vending state can be 'extracted' from the initial state:

```java
{$type: tutorials.vending/InitialVending$v1, balance: #d "0.00", beverageCount: 10, snackCount: 15}.refineTo( tutorials.vending/Vending$v1 )


//-- result --
{$type: tutorials.vending/Vending$v1, balance: #d "0.00", beverageCount: 10, snackCount: 15}
```

However, this is not a valid initial state.

```java
{$type: tutorials.vending/InitialVending$v1, balance: #d "0.00", beverageCount: 0, snackCount: 15}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.vending/InitialVending$v1', violates constraints \"tutorials.vending/InitialVending$v1/initial_state\""]
```

So now we have a model of the state space and valid initial states for the machine. However, we would like to also model valid state transitions.

```java
{
  "tutorials.vending/Vending$v1" : {
    "fields" : {
      "balance" : [ "Decimal", 2 ],
      "beverageCount" : "Integer",
      "snackCount" : "Integer"
    },
    "constraints" : [ "{expr: ((beverageCount >= 0) && (snackCount >= 0)), name: \"counts_not_negative\"}", "{expr: (balance >= #d \"0.00\"), name: \"balance_not_negative\"}" ]
  },
  "tutorials.vending/VendingTransition$v1" : {
    "fields" : {
      "current" : "tutorials.vending/Vending$v1",
      "next" : "tutorials.vending/Vending$v1"
    },
    "constraints" : [ "{expr: ((#{#d \"0.05\", #d \"0.10\", #d \"0.25\"}.contains?((next.balance - current.balance)) && (next.beverageCount == current.beverageCount) && (next.snackCount == current.snackCount)) || ((#d \"0.50\" == (current.balance - next.balance)) && (next.beverageCount == current.beverageCount) && (next.snackCount == (current.snackCount - 1))) || ((#d \"1.00\" == (current.balance - next.balance)) && (next.beverageCount == (current.beverageCount - 1)) && (next.snackCount == current.snackCount))), name: \"state_transitions\"}" ]
  }
}
```

A valid transition representing a dime being dropped into the machine.

```java
{$type: tutorials.vending/VendingTransition$v1, current: {$type: tutorials.vending/Vending$v1, balance: #d "0.00", beverageCount: 10, snackCount: 15}, next: {$type: tutorials.vending/Vending$v1, balance: #d "0.10", beverageCount: 10, snackCount: 15}}
```

An invalid transition, because the balance cannot increase by $0.07

```java
{$type: tutorials.vending/VendingTransition$v1, current: {$type: tutorials.vending/Vending$v1, balance: #d "0.00", beverageCount: 10, snackCount: 15}, next: {$type: tutorials.vending/Vending$v1, balance: #d "0.07", beverageCount: 10, snackCount: 15}}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.vending/VendingTransition$v1', violates constraints \"tutorials.vending/VendingTransition$v1/state_transitions\""]
```

A valid transition representing a snack being vended.

```java
{$type: tutorials.vending/VendingTransition$v1, current: {$type: tutorials.vending/Vending$v1, balance: #d "0.75", beverageCount: 10, snackCount: 15}, next: {$type: tutorials.vending/Vending$v1, balance: #d "0.25", beverageCount: 10, snackCount: 14}}
```

An invalid attempted transition representing a snack being vended.

```java
{$type: tutorials.vending/VendingTransition$v1, current: {$type: tutorials.vending/Vending$v1, balance: #d "0.75", beverageCount: 10, snackCount: 15}, next: {$type: tutorials.vending/Vending$v1, balance: #d "0.25", beverageCount: 9, snackCount: 14}}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.vending/VendingTransition$v1', violates constraints \"tutorials.vending/VendingTransition$v1/state_transitions\""]
```

