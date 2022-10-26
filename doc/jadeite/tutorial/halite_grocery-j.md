<!---
  This markdown file was generated. Do not edit.
  -->

## Model a grocery delivery business

Consider how to use specs to model some of the important details of a business that provides grocery delivery to subscribers.

The following is a full model for the grocery delivery business.

```java
{
  "tutorials.grocery/Country$v1" : {
    "spec-vars" : {
      "name" : "String"
    },
    "constraints" : [ [ "name_constraint", "#{\"Canada\", \"Mexico\", \"US\"}.contains?(name)" ] ]
  },
  "tutorials.grocery/Perk$v1" : {
    "abstract?" : true,
    "spec-vars" : {
      "perkId" : "Integer",
      "feePerMonth" : "Decimal2",
      "feePerUse" : "Decimal2",
      "usesPerMonth" : [ "Maybe", "Integer" ]
    },
    "constraints" : [ [ "feePerMonth_limit", "((#d \"0.00\" <= feePerMonth) && (feePerMonth <= #d \"199.99\"))" ], [ "feePerUse_limit", "((#d \"0.00\" <= feePerUse) && (feePerUse <= #d \"14.99\"))" ], [ "usesPerMonth_limit", "(ifValue(usesPerMonth) {((0 <= usesPerMonth) && (usesPerMonth <= 999))} else {true})" ] ]
  },
  "tutorials.grocery/FreeDeliveryPerk$v1" : {
    "spec-vars" : {
      "usesPerMonth" : "Integer"
    },
    "constraints" : [ [ "usesPerMonth_limit", "(usesPerMonth < 20)" ] ],
    "refines-to" : {
      "tutorials.grocery/Perk$v1" : {
        "name" : "refine_to_Perk",
        "expr" : "{$type: tutorials.grocery/Perk$v1, feePerMonth: #d \"2.99\", feePerUse: #d \"0.00\", perkId: 101, usesPerMonth: usesPerMonth}"
      }
    }
  },
  "tutorials.grocery/DiscountedPrescriptionPerk$v1" : {
    "spec-vars" : {
      "prescriptionID" : "String"
    },
    "refines-to" : {
      "tutorials.grocery/Perk$v1" : {
        "name" : "refine_to_Perk",
        "expr" : "{$type: tutorials.grocery/Perk$v1, feePerMonth: #d \"3.99\", feePerUse: #d \"0.00\", perkId: 102}"
      }
    }
  },
  "tutorials.grocery/EmergencyDeliveryPerk$v1" : {
    "refines-to" : {
      "tutorials.grocery/Perk$v1" : {
        "name" : "refine_to_Perk",
        "expr" : "{$type: tutorials.grocery/Perk$v1, feePerMonth: #d \"0.00\", feePerUse: #d \"1.99\", perkId: 103, usesPerMonth: 2}"
      }
    }
  },
  "tutorials.grocery/GroceryService$v1" : {
    "spec-vars" : {
      "deliveriesPerMonth" : "Integer",
      "feePerMonth" : "Decimal2",
      "perks" : [ "tutorials.grocery/Perk$v1" ],
      "subscriberCountry" : "tutorials.grocery/Country$v1"
    },
    "constraints" : [ [ "feePerMonth_limit", "((#d \"5.99\" < feePerMonth) && (feePerMonth < #d \"12.99\"))" ], [ "perk_limit", "(perks.count() <= 2)" ], [ "perk_sum", "({ perkInstances = sortBy(pi in map(p in perks)p.refineTo( tutorials.grocery/Perk$v1 ))pi.perkId; ((reduce( a = #d \"0.00\"; pi in perkInstances ) { (a + pi.feePerMonth) }) < #d \"6.00\") })" ] ],
    "refines-to" : {
      "tutorials.grocery/GroceryStoreSubscription$v1" : {
        "name" : "refine_to_Store",
        "expr" : "{$type: tutorials.grocery/GroceryStoreSubscription$v1, name: \"Acme Foods\", perkIds: map(p in sortBy(pi in map(p in perks)p.refineTo( tutorials.grocery/Perk$v1 ))pi.perkId)p.perkId, storeCountry: subscriberCountry}",
        "inverted?" : true
      }
    }
  },
  "tutorials.grocery/GroceryStoreSubscription$v1" : {
    "spec-vars" : {
      "name" : "String",
      "storeCountry" : "tutorials.grocery/Country$v1",
      "perkIds" : [ "Integer" ]
    },
    "constraints" : [ [ "valid_stores", "((name == \"Acme Foods\") || (name == \"Good Foods\"))" ], [ "storeCountryServed", "(((name == \"Acme Foods\") && #{\"Canada\", \"Costa Rica\", \"US\"}.contains?(storeCountry.name)) || ((name == \"Good Foods\") && #{\"Mexico\", \"US\"}.contains?(storeCountry.name)))" ] ]
  }
}
```

Taking it one part at a time. Consider first the country model. This is modeling the countries where the company is operating. This is a valid country instance.

```java
{$type: tutorials.grocery/Country$v1, name: "Canada"}
```

Whereas this is not a valid instance.

```java
{$type: tutorials.grocery/Country$v1, name: "Germany"}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.grocery/Country$v1', violates constraints name_constraint"]
```

Next the model introduces the abstract notion of a 'perk'. These are extra options that can be added on to the base grocery subscription service. Each type of perk has a unique number assigned as its 'perkID', it has fees, and it has an optional value indicating how many times the perk can be used per month. The perk model includes certain rules that all valid perk instances must satisfy. So, for example, the following are valid perk instances under this model.

```java
{$type: tutorials.grocery/Perk$v1, feePerMonth: #d "4.50", feePerUse: #d "0.00", perkId: 1, usesPerMonth: 3}
```

```java
{$type: tutorials.grocery/Perk$v1, feePerMonth: #d "4.50", feePerUse: #d "1.40", perkId: 2}
```

While this is not a valid perk instance.

```java
{$type: tutorials.grocery/Perk$v1, feePerMonth: #d "4.50", feePerUse: #d "0.00", perkId: 1, usesPerMonth: 1000}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.grocery/Perk$v1', violates constraints usesPerMonth_limit"]
```

The model then defines the three types of perks that are actually offered. The following are example instances of these three specs.

```java
{$type: tutorials.grocery/FreeDeliveryPerk$v1, usesPerMonth: 10}
```

```java
{$type: tutorials.grocery/DiscountedPrescriptionPerk$v1, prescriptionID: "ABC"}
```

```java
{$type: tutorials.grocery/EmergencyDeliveryPerk$v1}
```

The overall grocery service spec now pulls together perks along with the subscriber's country and some service specific fields. The grocery service includes constraints that place additional restrictions on the service being offered. The following is an example valid instance.

```java
{$type: tutorials.grocery/GroceryService$v1, deliveriesPerMonth: 3, feePerMonth: #d "9.99", perks: #{{$type: tutorials.grocery/FreeDeliveryPerk$v1, usesPerMonth: 1}}, subscriberCountry: {$type: tutorials.grocery/Country$v1, name: "Canada"}}
```

While the following violates the constraint that limits the total monthly charges for perks.

```java
{$type: tutorials.grocery/GroceryService$v1, deliveriesPerMonth: 3, feePerMonth: #d "9.99", perks: #{{$type: tutorials.grocery/DiscountedPrescriptionPerk$v1, prescriptionID: "XYZ:123"}, {$type: tutorials.grocery/FreeDeliveryPerk$v1, usesPerMonth: 1}}, subscriberCountry: {$type: tutorials.grocery/Country$v1, name: "Canada"}}


//-- result --
[:throws "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.grocery/GroceryService$v1', violates constraints perk_sum"]
```

This spec models the service from the subscriber's perspective, but now the business needs to translate this into an order for a back-end grocery store to actually provide the delivery service. This involves executing the refinement to a subscription object.

```java
{$type: tutorials.grocery/GroceryService$v1, deliveriesPerMonth: 3, feePerMonth: #d "9.99", perks: #{{$type: tutorials.grocery/FreeDeliveryPerk$v1, usesPerMonth: 1}}, subscriberCountry: {$type: tutorials.grocery/Country$v1, name: "Canada"}}.refineTo( tutorials.grocery/GroceryStoreSubscription$v1 )


//-- result --
{$type: tutorials.grocery/GroceryStoreSubscription$v1, name: "Acme Foods", perkIds: [101], storeCountry: {$type: tutorials.grocery/Country$v1, name: "Canada"}}
```

This final object is now in a form that the grocery store understands.

### Reference

#### Basic elements:

[`fixed-decimal`](../halite_basic-syntax-reference-j.md#fixed-decimal), [`instance`](../halite_basic-syntax-reference-j.md#instance), [`integer`](../halite_basic-syntax-reference-j.md#integer), [`set`](../halite_basic-syntax-reference-j.md#set), [`spec-map`](../../halite_spec-syntax-reference.md), [`string`](../halite_basic-syntax-reference-j.md#string), [`vector`](../halite_basic-syntax-reference-j.md#vector)

#### Operator reference:

* [`&&`](../halite_full-reference-j.md#&&)
* [`<`](../halite_full-reference-j.md#_L)
* [`<=`](../halite_full-reference-j.md#_L_E)
* [`ACCESSOR`](../halite_full-reference-j.md#ACCESSOR)
* [`count`](../halite_full-reference-j.md#count)
* [`map`](../halite_full-reference-j.md#map)
* [`reduce`](../halite_full-reference-j.md#reduce)
* [`refineTo`](../halite_full-reference-j.md#refineTo)


#### How Tos:

* [convert-instances](../how-to/halite_convert-instances-j.md)


#### Explanations:

* [refinements-as-functions](../explanation/halite_refinements-as-functions-j.md)
* [specs-as-predicates](../explanation/halite_specs-as-predicates-j.md)


