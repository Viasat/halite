<!---
  This markdown file was generated. Do not edit.
  -->

## Recursive refinements

Specs cannot be defined to recursively refine to themselves.

For example, the following spec that refines to itself is not allowed.

```clojure
{:spec/Mirror {:refines-to {:spec/Mirror {:name "refine_to_Mirror",
                                          :expr '{:$type :spec/Mirror}}}}}
```

```clojure
{:$type :spec/Mirror}


;-- result --
[:throws "h-err/spec-cycle-runtime 0-0 : Loop detected in spec dependencies"
 :h-err/spec-cycle-runtime]
```

Similarly transitive refinement loops are not allowed. For example, a pair of specs that refine to each other is not allowed.

```clojure
{:spec/Back {:refines-to {:spec/Bounce {:name "refine_to_Bounce",
                                        :expr '{:$type :spec/Bounce}}}},
 :spec/Bounce {:refines-to {:spec/Back {:name "refine_to_Back",
                                        :expr '{:$type :spec/Back}}}}}
```

```clojure
{:$type :spec/Bounce}


;-- result --
[:throws "h-err/spec-cycle-runtime 0-0 : Loop detected in spec dependencies"
 :h-err/spec-cycle-runtime]
```

It is a bit more subtle, but a cyclical dependency that crosses both a refinement and a composition relationship is also disallowed.

```clojure
{:spec/Car {:refines-to {:spec/Garage {:name "refine_to_Garage",
                                       :expr '{:$type :spec/Garage,
                                               :car {:$type :spec/Car}}}}},
 :spec/Garage {:fields {:car :spec/Car}}}
```

```clojure
{:$type :spec/Car}


;-- result --
[:throws "h-err/spec-cycle-runtime 0-0 : Loop detected in spec dependencies"
 :h-err/spec-cycle-runtime]
```

Diamonds are a bit different than a recursive refinement, but they too are disallowed and produce a similar error.

```clojure
{:spec/Destination {:fields {:d :Integer}},
 :spec/Path1 {:refines-to {:spec/Destination {:name "refine_to_Destination",
                                              :expr '{:$type :spec/Destination,
                                                      :d 1}}}},
 :spec/Path2 {:refines-to {:spec/Destination {:name "refine_to_Destination",
                                              :expr '{:$type :spec/Destination,
                                                      :d 2}}}},
 :spec/Start {:refines-to {:spec/Path1 {:name "refine_to_Path1",
                                        :expr '{:$type :spec/Path1}},
                           :spec/Path2 {:name "refine_to_Path2",
                                        :expr '{:$type :spec/Path2}}}}}
```

```clojure
(refine-to {:$type :spec/Start} :spec/Destination)


;-- result --
[:throws "h-err/refinement-diamond 0-0 : Diamond detected in refinement graph"
 :h-err/refinement-diamond]
```

Generally, dependency cycles between specs are not allowed. The following spec-map is detected as invalid.

```clojure
{:spec/Self {:constraints #{'{:name "example",
                              :expr (= 1 (count [{:$type :spec/Self}]))}}}}


;-- result --
[:throws "h-err/spec-cycle 0-0 : Cycle detected in spec dependencies"
 :h-err/spec-cycle]
```

Although this error can be detected in advance, if an attempt is made to use the spec, then a similar runtime error is produced.

```clojure
{:$type :spec/Self}


;-- result --
[:throws "h-err/spec-cycle-runtime 0-0 : Loop detected in spec dependencies"
 :h-err/spec-cycle-runtime]
```

