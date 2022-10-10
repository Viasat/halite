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
[:throws "h-err/refinement-loop 0-0 : Loop detected in refinement graph"
 :h-err/refinement-loop]
```

Similarly transitive refinement loops are not allowed. For example, a pair of specs that refine to each other is not allowed.

```clojure
{:spec/A {:refines-to {:spec/B {:name "refine_to_B",
                                :expr '{:$type :spec/B}}}},
 :spec/B {:refines-to {:spec/A {:name "refine_to_A",
                                :expr '{:$type :spec/A}}}}}
```

```clojure
{:$type :spec/A}


;-- result --
[:throws "h-err/refinement-loop 0-0 : Loop detected in refinement graph"
 :h-err/refinement-loop]
```

It is a bit more subtle, but a cyclical dependency that crosses both a refinement and a composition relationship is also disallowed.

```clojure
{:spec/A {:refines-to {:spec/B {:name "refine_to_B",
                                :expr '{:$type :spec/B,
                                        :a {:$type :spec/A}}}}},
 :spec/B {:spec-vars {:a :spec/A}}}
```

```clojure
{:$type :spec/A}


;-- result --
[:throws "h-err/refinement-loop 0-0 : Loop detected in refinement graph"
 :h-err/refinement-loop]
```

Diamonds are a bit different than a recursive refinement, but they too are disallowed and produce a similar error.

```clojure
{:spec/A {:spec-vars {:a "Integer"}},
 :spec/B {:refines-to {:spec/A {:name "refine_to_A",
                                :expr '{:$type :spec/A,
                                        :a 1}}}},
 :spec/C {:refines-to {:spec/A {:name "refine_to_A",
                                :expr '{:$type :spec/A,
                                        :a 2}}}},
 :spec/D {:refines-to {:spec/B {:name "refine_to_B",
                                :expr '{:$type :spec/B}},
                       :spec/C {:name "refine_to_C",
                                :expr '{:$type :spec/C}}}}}
```

```clojure
(refine-to {:$type :spec/D} :spec/A)


;-- result --
[:throws "h-err/refinement-diamond 0-0 : Diamond detected in refinement graph"
 :h-err/refinement-diamond]
```

