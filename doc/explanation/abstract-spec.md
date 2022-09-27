<!---
  This markdown file was generated. Do not edit.
  -->

## What is an abstract spec?

An abstract spec defines instances which cannot be used in the construction of other instances.

Say we have an abstract concept of squareness.

```clojure
{:spec/Square {:abstract? true,
               :spec-vars {:height "Integer",
                           :width "Integer"}}}
```

The spec can be instantiated in a standalone fashion.

```clojure
{:$type :spec/Square,
 :height 5,
 :width 5}
```

However, this spec cannot be instantiated in the context of another instance. So consider the following two specs, where a concrete spec uses an abstract spec in composition.

```clojure
{:spec/Painting {:spec-vars {:painter "String",
                             :square :spec/Square}},
 :spec/Square {:abstract? true,
               :spec-vars {:height "Integer",
                           :width "Integer"}}}
```

The instance of the abstract spec cannot be used in the construction of the painting instance.

```clojure
{:$type :spec/Painting,
 :painter "van Gogh",
 :square {:$type :spec/Square,
          :height 5,
          :width 5}}


;-- result --
[:throws "h-err/no-abstract 0-0 : Instance cannot contain abstract value"
 :h-err/no-abstract]
```

To create an instance of the composite painting spec, we need to define an additional spec which refines to the abstract spec, square.

```clojure
{:spec/Canvas {:spec-vars {:size "String"},
               :refines-to {:spec/Square {:name "refine_to_square",
                                          :expr '(if (= "small" size)
                                                   {:$type :spec/Square,
                                                    :width 5,
                                                    :height 5}
                                                   {:$type :spec/Square,
                                                    :width 10,
                                                    :height 10})}}},
 :spec/Painting {:spec-vars {:painter "String",
                             :square :spec/Square}},
 :spec/Square {:abstract? true,
               :spec-vars {:height "Integer",
                           :width "Integer"}}}
```

Now we can instantiate a painting using an instance of the concrete canvas spec.

```clojure
{:$type :spec/Painting,
 :painter "van Gogh",
 :square {:$type :spec/Canvas,
          :size "large"}}


;-- result --
{:$type :spec/Painting,
 :painter "van Gogh",
 :square {:$type :spec/Canvas,
          :size "large"}}
```

We can determine the size of the square.

```clojure
(let [painting {:$type :spec/Painting,
                :square {:$type :spec/Canvas,
                         :size "large"},
                :painter "van Gogh"}]
  (get (refine-to (get painting :square) :spec/Square) :width))


;-- result --
10
```

An abstract spec is a spec that can be used to define some constraints on the value in a composite spec without indicating precisely what type of instance is used in the composition. In this example, the painting spec is defined to include a square without any reference to the canvas.

Consider another spec context, where an alternate spec is defined that refines to square.

```clojure
{:spec/Painting {:spec-vars {:painter "String",
                             :square :spec/Square}},
 :spec/Square {:abstract? true,
               :spec-vars {:height "Integer",
                           :width "Integer"}},
 :spec/Wall {:refines-to {:spec/Square {:name "refine_to_square",
                                        :expr '{:$type :spec/Square,
                                                :width 100,
                                                :height 100}}}}}
```

In this example, the exact same painting spec is used, but now a new spec is used to provide the square abstraction.

```clojure
{:$type :spec/Painting,
 :painter "van Gogh",
 :square {:$type :spec/Wall}}


;-- result --
{:$type :spec/Painting,
 :painter "van Gogh",
 :square {:$type :spec/Wall}}
```

Once again, we can use the same code as before to retrieve the size of the square for this painting.

```clojure
(let [painting {:$type :spec/Painting,
                :square {:$type :spec/Wall},
                :painter "van Gogh"}]
  (get (refine-to (get painting :square) :spec/Square) :width))


;-- result --
100
```

So the abstract spec allows us to write code that composes and uses instances without knowing the specific type of the instances at the time that we write the code.

### Reference

#### Explanations:

* [refinement-terminology](../explanation/refinement-terminology.md)


