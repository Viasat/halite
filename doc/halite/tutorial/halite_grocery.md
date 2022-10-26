<!---
  This markdown file was generated. Do not edit.
  -->

## Model a grocery delivery business

Consider how to use specs to model some of the important details of a business that provides grocery delivery to subscribers.

The following is a full model for the grocery delivery business.

```clojure
{:tutorials.grocery/Country$v1
   {:spec-vars {:name "String"},
    :constraints [["name_constraint"
                   '(contains? #{"Canada" "Mexico" "US"} name)]]},
 :tutorials.grocery/DiscountedPrescriptionPerk$v1
   {:spec-vars {:prescriptionID "String"},
    :refines-to {:tutorials.grocery/Perk$v1
                   {:name "refine_to_Perk",
                    :expr '{:$type :tutorials.grocery/Perk$v1,
                            :perkId 102,
                            :feePerMonth #d "3.99",
                            :feePerUse #d "0.00"}}}},
 :tutorials.grocery/EmergencyDeliveryPerk$v1
   {:refines-to {:tutorials.grocery/Perk$v1
                   {:name "refine_to_Perk",
                    :expr '{:$type :tutorials.grocery/Perk$v1,
                            :perkId 103,
                            :feePerMonth #d "0.00",
                            :feePerUse #d "1.99",
                            :usesPerMonth 2}}}},
 :tutorials.grocery/FreeDeliveryPerk$v1
   {:spec-vars {:usesPerMonth "Integer"},
    :constraints [["usesPerMonth_limit" '(< usesPerMonth 20)]],
    :refines-to {:tutorials.grocery/Perk$v1
                   {:name "refine_to_Perk",
                    :expr '{:$type :tutorials.grocery/Perk$v1,
                            :perkId 101,
                            :feePerMonth #d "2.99",
                            :feePerUse #d "0.00",
                            :usesPerMonth usesPerMonth}}}},
 :tutorials.grocery/GroceryService$v1
   {:spec-vars {:deliveriesPerMonth "Integer",
                :feePerMonth "Decimal2",
                :perks #{:tutorials.grocery/Perk$v1},
                :subscriberCountry :tutorials.grocery/Country$v1},
    :constraints
      [["feePerMonth_limit"
        '(and (< #d "5.99" feePerMonth) (< feePerMonth #d "12.99"))]
       ["perk_limit" '(<= (count perks) 2)]
       ["perk_sum"
        '(let [perkInstances
                 (sort-by
                   [pi (map [p perks] (refine-to p :tutorials.grocery/Perk$v1))]
                   (get pi :perkId))]
           (< (reduce [a #d "0.00"]
                [pi perkInstances]
                (+ a (get pi :feePerMonth)))
              #d "6.00"))]],
    :refines-to
      {:tutorials.grocery/GroceryStoreSubscription$v1
         {:name "refine_to_Store",
          :expr '{:$type :tutorials.grocery/GroceryStoreSubscription$v1,
                  :name "Acme Foods",
                  :storeCountry subscriberCountry,
                  :perkIds
                    (map [p
                          (sort-by [pi
                                    (map [p perks]
                                      (refine-to p :tutorials.grocery/Perk$v1))]
                                   (get pi :perkId))]
                      (get p :perkId))},
          :inverted? true}}},
 :tutorials.grocery/GroceryStoreSubscription$v1
   {:spec-vars {:name "String",
                :perkIds ["Integer"],
                :storeCountry :tutorials.grocery/Country$v1},
    :constraints
      [["valid_stores" '(or (= name "Acme Foods") (= name "Good Foods"))]
       ["storeCountryServed"
        '(or (and (= name "Acme Foods")
                  (contains? #{"Canada" "US" "Costa Rica"}
                             (get storeCountry :name)))
             (and (= name "Good Foods")
                  (contains? #{"Mexico" "US"} (get storeCountry :name))))]]},
 :tutorials.grocery/Perk$v1
   {:abstract? true,
    :spec-vars {:feePerMonth "Decimal2",
                :feePerUse "Decimal2",
                :perkId "Integer",
                :usesPerMonth [:Maybe "Integer"]},
    :constraints [["feePerMonth_limit"
                   '(and (<= #d "0.00" feePerMonth)
                         (<= feePerMonth #d "199.99"))]
                  ["feePerUse_limit"
                   '(and (<= #d "0.00" feePerUse) (<= feePerUse #d "14.99"))]
                  ["usesPerMonth_limit"
                   '(if-value usesPerMonth
                              (and (<= 0 usesPerMonth) (<= usesPerMonth 999))
                              true)]]}}
```

Taking it one part at a time. Consider first the country model. This is modeling the countries where the company is operating. This is a valid country instance.

```clojure
{:name "Canada",
 :$type :tutorials.grocery/Country$v1}
```

Whereas this is not a valid instance.

```clojure
{:name "Germany",
 :$type :tutorials.grocery/Country$v1}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.grocery/Country$v1', violates constraints name_constraint"
 :h-err/invalid-instance]
```

Next the model introduces the abstract notion of a 'perk'. These are extra options that can be added on to the base grocery subscription service. Each type of perk has a unique number assigned as its 'perkID', it has fees, and it has an optional value indicating how many times the perk can be used per month. The perk model includes certain rules that all valid perk instances must satisfy. So, for example, the following are valid perk instances under this model.

```clojure
{:$type :tutorials.grocery/Perk$v1,
 :feePerMonth #d "4.50",
 :feePerUse #d "0.00",
 :perkId 1,
 :usesPerMonth 3}
```

```clojure
{:$type :tutorials.grocery/Perk$v1,
 :feePerMonth #d "4.50",
 :feePerUse #d "1.40",
 :perkId 2}
```

While this is not a valid perk instance.

```clojure
{:$type :tutorials.grocery/Perk$v1,
 :feePerMonth #d "4.50",
 :feePerUse #d "0.00",
 :perkId 1,
 :usesPerMonth 1000}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.grocery/Perk$v1', violates constraints usesPerMonth_limit"
 :h-err/invalid-instance]
```

The model then defines the three types of perks that are actually offered. The following are example instances of these three specs.

```clojure
{:$type :tutorials.grocery/FreeDeliveryPerk$v1,
 :usesPerMonth 10}
```

```clojure
{:$type :tutorials.grocery/DiscountedPrescriptionPerk$v1,
 :prescriptionID "ABC"}
```

```clojure
{:$type :tutorials.grocery/EmergencyDeliveryPerk$v1}
```

The overall grocery service spec now pulls together perks along with the subscriber's country and some service specific fields. The grocery service includes constraints that place additional restrictions on the service being offered. The following is an example valid instance.

```clojure
{:$type :tutorials.grocery/GroceryService$v1,
 :deliveriesPerMonth 3,
 :feePerMonth #d "9.99",
 :perks #{{:$type :tutorials.grocery/FreeDeliveryPerk$v1,
           :usesPerMonth 1}},
 :subscriberCountry {:name "Canada",
                     :$type :tutorials.grocery/Country$v1}}
```

While the following violates the constraint that limits the total monthly charges for perks.

```clojure
{:$type :tutorials.grocery/GroceryService$v1,
 :deliveriesPerMonth 3,
 :feePerMonth #d "9.99",
 :perks #{{:$type :tutorials.grocery/DiscountedPrescriptionPerk$v1,
           :prescriptionID "XYZ:123"}
          {:$type :tutorials.grocery/FreeDeliveryPerk$v1,
           :usesPerMonth 1}},
 :subscriberCountry {:name "Canada",
                     :$type :tutorials.grocery/Country$v1}}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.grocery/GroceryService$v1', violates constraints perk_sum"
 :h-err/invalid-instance]
```

This spec models the service from the subscriber's perspective, but now the business needs to translate this into an order for a back-end grocery store to actually provide the delivery service. This involves executing the refinement to a subscription object.

```clojure
(refine-to {:$type :tutorials.grocery/GroceryService$v1,
            :deliveriesPerMonth 3,
            :feePerMonth #d "9.99",
            :perks #{{:$type :tutorials.grocery/FreeDeliveryPerk$v1,
                      :usesPerMonth 1}},
            :subscriberCountry {:$type :tutorials.grocery/Country$v1,
                                :name "Canada"}}
           :tutorials.grocery/GroceryStoreSubscription$v1)


;-- result --
{:name "Acme Foods",
 :$type :tutorials.grocery/GroceryStoreSubscription$v1,
 :perkIds [101],
 :storeCountry {:name "Canada",
                :$type :tutorials.grocery/Country$v1}}
```

This final object is now in a form that the grocery store understands.

### Reference

#### Basic elements:

[`fixed-decimal`](../halite_basic-syntax-reference.md#fixed-decimal), [`instance`](../halite_basic-syntax-reference.md#instance), [`integer`](../halite_basic-syntax-reference.md#integer), [`set`](../halite_basic-syntax-reference.md#set), [`spec-map`](../../halite_spec-syntax-reference.md), [`string`](../halite_basic-syntax-reference.md#string), [`vector`](../halite_basic-syntax-reference.md#vector)

#### Operator reference:

* [`<`](../halite_full-reference.md#_L)
* [`<=`](../halite_full-reference.md#_L_E)
* [`and`](../halite_full-reference.md#and)
* [`count`](../halite_full-reference.md#count)
* [`get`](../halite_full-reference.md#get)
* [`map`](../halite_full-reference.md#map)
* [`reduce`](../halite_full-reference.md#reduce)
* [`refine-to`](../halite_full-reference.md#refine-to)


#### How Tos:

* [convert-instances](../how-to/halite_convert-instances.md)


#### Explanations:

* [refinements-as-functions](../explanation/halite_refinements-as-functions.md)
* [specs-as-predicates](../explanation/halite_specs-as-predicates.md)


