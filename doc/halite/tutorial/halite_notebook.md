<!---
  This markdown file was generated. Do not edit.
  -->

## Model a notebook mechanism

Model a notebook mechanism

The following is an extended example of implementing a non-trivial amount of logic in a set of specs. It is a bit "meta", but in this case the model will include specs that exist in workspaces where each spec has a version separate from its name. 

Versions must be positive values.

```clojure
{:tutorials.notebook/Version$v1
   {:fields {:version [:Maybe :Integer]},
    :constraints #{'{:name "positiveVersion",
                     :expr (if-value version (> version 0) true)}}}}
```

An identifier for a spec includes its name and version. By referencing the Version spec in a constraint we can reuse the constraint from the Version spec.

```clojure
{:tutorials.notebook/SpecId$v1
   {:fields {:specName :String,
             :specVersion :Integer,
             :workspaceName :String},
    :constraints #{'{:name "positiveVersion",
                     :expr (valid? {:$type :tutorials.notebook/Version$v1,
                                    :version specVersion})}}}}
```

A notebook will consist of "items". Since there are different kinds of items an abstract spec is defined.

```clojure
{:tutorials.notebook/AbstractNotebookItem$v1 {:abstract? true}}
```

One kind of item is a reference to another spec.

```clojure
{:tutorials.notebook/SpecRef$v1
   {:fields {:specName :String,
             :specVersion [:Maybe :Integer],
             :workspaceName :String},
    :constraints #{'{:name "positiveVersion",
                     :expr (valid? {:$type :tutorials.notebook/Version$v1,
                                    :version specVersion})}},
    :refines-to {:tutorials.notebook/AbstractNotebookItem$v1
                   {:name "abstractItems",
                    :expr '{:$type
                              :tutorials.notebook/AbstractNotebookItem$v1}},
                 :tutorials.notebook/SpecId$v1
                   {:name "specId",
                    :expr '(when-value specVersion
                                       {:$type :tutorials.notebook/SpecId$v1,
                                        :workspaceName workspaceName,
                                        :specName specName,
                                        :specVersion specVersion})}}}}
```

Example of a "fixed" spec references that refers precisely to a given version of a spec.

```clojure
{:$type :tutorials.notebook/SpecRef$v1,
 :specName "A",
 :specVersion 1,
 :workspaceName "my"}
```

Example of a "floating" spec reference that refers to the latest version of a given spec within whatever context the reference is resolved in.

```clojure
{:$type :tutorials.notebook/SpecRef$v1,
 :specName "A",
 :workspaceName "my"}
```

Another kind of notebook item is the definition of a new spec. Notice in this case the optional flag only presents two options: either it is present and set to 'true' or it is absent. So it is truly a binary. The convenience this approach provides is that it is less verbose to have it excluded it from instances where it is not set.

```clojure
{:tutorials.notebook/NewSpec$v1
   {:fields {:isEphemeral [:Maybe :Boolean],
             :specName :String,
             :specVersion :Integer,
             :workspaceName :String},
    :constraints #{'{:name "ephemeralFlag",
                     :expr (if-value isEphemeral isEphemeral true)}
                   '{:name "positiveVersion",
                     :expr (valid? {:$type :tutorials.notebook/Version$v1,
                                    :version specVersion})}},
    :refines-to {:tutorials.notebook/AbstractNotebookItem$v1
                   {:name "abstractItems",
                    :expr '{:$type
                              :tutorials.notebook/AbstractNotebookItem$v1}},
                 :tutorials.notebook/SpecId$v1
                   {:name "specId",
                    :expr '{:$type :tutorials.notebook/SpecId$v1,
                            :workspaceName workspaceName,
                            :specName specName,
                            :specVersion specVersion}}}}}
```

Some examples of 'new specs'.

```clojure
{:$type :tutorials.notebook/NewSpec$v1,
 :specName "A",
 :specVersion 1,
 :workspaceName "my"}
```

```clojure
{:$type :tutorials.notebook/NewSpec$v1,
 :specName "A",
 :specVersion 0,
 :workspaceName "my"}


;-- result --
[:throws
 "h-err/spec-threw 0-0 : Spec threw error: \"h-err/spec-cycle-runtime 0-0 : Loop detected in spec dependencies\""
 :h-err/spec-threw]
```

```clojure
{:$type :tutorials.notebook/NewSpec$v1,
 :isEphemeral true,
 :specName "A",
 :specVersion 1,
 :workspaceName "my"}
```

It is not possible to set the flag to a value of 'false', instead it is to be omitted.

```clojure
{:$type :tutorials.notebook/NewSpec$v1,
 :isEphemeral false,
 :specName "A",
 :specVersion 1,
 :workspaceName "my"}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.notebook/NewSpec$v1', violates constraints \"tutorials.notebook/NewSpec$v1/ephemeralFlag\""
 :h-err/invalid-instance]
```

A 'new spec' can be treated as a spec identifier.

```clojure
(refine-to {:$type :tutorials.notebook/NewSpec$v1,
            :workspaceName "my",
            :specName "A",
            :specVersion 1}
           :tutorials.notebook/SpecId$v1)


;-- result --
{:$type :tutorials.notebook/SpecId$v1,
 :specName "A",
 :specVersion 1,
 :workspaceName "my"}
```

This is an example of writing a reusable 'function' via a spec. In this case the prefix 'F' is used in the spec name to identify it as following this pattern. The fields in this spec are the input parameters to the function. The spec representing the return value from the function is prefixed with an 'R'. Since an integer value cannot be produced from a refinement, a spec is needed to hold the result.

```clojure
{:tutorials.notebook/FMaxSpecVersion$v1
   {:fields {:specIds [:Vec :tutorials.notebook/SpecId$v1],
             :specName :String,
             :workspaceName :String},
    :refines-to
      {:tutorials.notebook/RInteger$v1
         {:name "result",
          :expr '{:$type :tutorials.notebook/RInteger$v1,
                  :result (let [result (reduce [a 0]
                                         [si specIds]
                                         (cond (or (not= (get si :workspaceName)
                                                         workspaceName)
                                                   (not= (get si :specName)
                                                         specName))
                                                 a
                                               (> (get si :specVersion) a)
                                                 (get si :specVersion)
                                               a))]
                            (when (not= 0 result) result))}}}},
 :tutorials.notebook/RInteger$v1 {:fields {:result [:Maybe :Integer]}}}
```

Some examples of invoking the function to find the max version of a given spec.

```clojure
(refine-to {:$type :tutorials.notebook/FMaxSpecVersion$v1,
            :specIds [{:$type :tutorials.notebook/SpecId$v1,
                       :workspaceName "my",
                       :specName "A",
                       :specVersion 1}
                      {:$type :tutorials.notebook/SpecId$v1,
                       :workspaceName "my",
                       :specName "B",
                       :specVersion 1}
                      {:$type :tutorials.notebook/SpecId$v1,
                       :workspaceName "my",
                       :specName "A",
                       :specVersion 2}],
            :workspaceName "my",
            :specName "A"}
           :tutorials.notebook/RInteger$v1)


;-- result --
{:$type :tutorials.notebook/RInteger$v1,
 :result 2}
```

```clojure
(refine-to {:$type :tutorials.notebook/FMaxSpecVersion$v1,
            :specIds [{:$type :tutorials.notebook/SpecId$v1,
                       :workspaceName "my",
                       :specName "A",
                       :specVersion 1}
                      {:$type :tutorials.notebook/SpecId$v1,
                       :workspaceName "my",
                       :specName "B",
                       :specVersion 1}
                      {:$type :tutorials.notebook/SpecId$v1,
                       :workspaceName "my",
                       :specName "A",
                       :specVersion 2}],
            :workspaceName "my",
            :specName "B"}
           :tutorials.notebook/RInteger$v1)


;-- result --
{:$type :tutorials.notebook/RInteger$v1,
 :result 1}
```

An example of when there is no such spec in the set of specs.

```clojure
(refine-to {:$type :tutorials.notebook/FMaxSpecVersion$v1,
            :specIds [{:$type :tutorials.notebook/SpecId$v1,
                       :workspaceName "my",
                       :specName "A",
                       :specVersion 1}
                      {:$type :tutorials.notebook/SpecId$v1,
                       :workspaceName "my",
                       :specName "B",
                       :specVersion 1}
                      {:$type :tutorials.notebook/SpecId$v1,
                       :workspaceName "my",
                       :specName "A",
                       :specVersion 2}],
            :workspaceName "my",
            :specName "C"}
           :tutorials.notebook/RInteger$v1)


;-- result --
{:$type :tutorials.notebook/RInteger$v1}
```

A spec which resolves a spec reference in the context of other specs. In this case, this is like a function, but there is a natural way to express the result using an existing spec.

```clojure
{:tutorials.notebook/SpecRefResolver$v1
   {:fields {:existingSpecIds [:Vec :tutorials.notebook/SpecId$v1],
             :inputSpecRef :tutorials.notebook/SpecRef$v1,
             :newSpecs [:Vec :tutorials.notebook/NewSpec$v1]},
    :refines-to
      {:tutorials.notebook/SpecId$v1
         {:name "toSpecId",
          :expr
            '(let [all-spec-ids
                     (concat existingSpecIds
                             (map [ns newSpecs]
                               (refine-to ns :tutorials.notebook/SpecId$v1)))
                   spec-version (get inputSpecRef :specVersion)]
               (if-value
                 spec-version
                 (let [result (refine-to inputSpecRef
                                         :tutorials.notebook/SpecId$v1)]
                   (when (contains? (concat #{} all-spec-ids) result) result))
                 (let [max-version-in-context
                         (get
                           (refine-to
                             {:$type :tutorials.notebook/FMaxSpecVersion$v1,
                              :specIds all-spec-ids,
                              :workspaceName (get inputSpecRef :workspaceName),
                              :specName (get inputSpecRef :specName)}
                             :tutorials.notebook/RInteger$v1)
                           :result)]
                   (when-value
                     max-version-in-context
                     {:$type :tutorials.notebook/SpecId$v1,
                      :workspaceName (get inputSpecRef :workspaceName),
                      :specName (get inputSpecRef :specName),
                      :specVersion max-version-in-context}))))}}}}
```

Cases where the input spec reference cannot be resolved are represented by failing to refine.

```clojure
(refines-to? {:$type :tutorials.notebook/SpecRefResolver$v1,
              :existingSpecIds [],
              :newSpecs [],
              :inputSpecRef {:$type :tutorials.notebook/SpecRef$v1,
                             :workspaceName "my",
                             :specName "A",
                             :specVersion 1}}
             :tutorials.notebook/SpecId$v1)


;-- result --
false
```

For cases that can be refined, the refinement result is the result of resolving the spec reference. In this example the floating reference resolves to a new spec.

```clojure
(refine-to {:$type :tutorials.notebook/SpecRefResolver$v1,
            :existingSpecIds [{:$type :tutorials.notebook/SpecId$v1,
                               :workspaceName "my",
                               :specName "A",
                               :specVersion 1}
                              {:$type :tutorials.notebook/SpecId$v1,
                               :workspaceName "my",
                               :specName "B",
                               :specVersion 1}
                              {:$type :tutorials.notebook/SpecId$v1,
                               :workspaceName "my",
                               :specName "A",
                               :specVersion 2}],
            :newSpecs [{:$type :tutorials.notebook/NewSpec$v1,
                        :workspaceName "my",
                        :specName "C",
                        :specVersion 1,
                        :isEphemeral true}
                       {:$type :tutorials.notebook/NewSpec$v1,
                        :workspaceName "my",
                        :specName "A",
                        :specVersion 3}],
            :inputSpecRef {:$type :tutorials.notebook/SpecRef$v1,
                           :workspaceName "my",
                           :specName "A"}}
           :tutorials.notebook/SpecId$v1)


;-- result --
{:$type :tutorials.notebook/SpecId$v1,
 :specName "A",
 :specVersion 3,
 :workspaceName "my"}
```

In this example a floating spec reference resolves to an existing spec in the current context.

```clojure
(refine-to {:$type :tutorials.notebook/SpecRefResolver$v1,
            :existingSpecIds [{:$type :tutorials.notebook/SpecId$v1,
                               :workspaceName "my",
                               :specName "A",
                               :specVersion 1}
                              {:$type :tutorials.notebook/SpecId$v1,
                               :workspaceName "my",
                               :specName "B",
                               :specVersion 1}
                              {:$type :tutorials.notebook/SpecId$v1,
                               :workspaceName "my",
                               :specName "A",
                               :specVersion 2}],
            :newSpecs [{:$type :tutorials.notebook/NewSpec$v1,
                        :workspaceName "my",
                        :specName "C",
                        :specVersion 1,
                        :isEphemeral true}
                       {:$type :tutorials.notebook/NewSpec$v1,
                        :workspaceName "my",
                        :specName "A",
                        :specVersion 3}],
            :inputSpecRef {:$type :tutorials.notebook/SpecRef$v1,
                           :workspaceName "my",
                           :specName "B"}}
           :tutorials.notebook/SpecId$v1)


;-- result --
{:$type :tutorials.notebook/SpecId$v1,
 :specName "B",
 :specVersion 1,
 :workspaceName "my"}
```

An example of resolving a fixed spec reference.

```clojure
(refine-to {:$type :tutorials.notebook/SpecRefResolver$v1,
            :existingSpecIds [{:$type :tutorials.notebook/SpecId$v1,
                               :workspaceName "my",
                               :specName "A",
                               :specVersion 1}
                              {:$type :tutorials.notebook/SpecId$v1,
                               :workspaceName "my",
                               :specName "B",
                               :specVersion 1}
                              {:$type :tutorials.notebook/SpecId$v1,
                               :workspaceName "my",
                               :specName "A",
                               :specVersion 2}],
            :newSpecs [{:$type :tutorials.notebook/NewSpec$v1,
                        :workspaceName "my",
                        :specName "C",
                        :specVersion 1,
                        :isEphemeral true}
                       {:$type :tutorials.notebook/NewSpec$v1,
                        :workspaceName "my",
                        :specName "A",
                        :specVersion 3}],
            :inputSpecRef {:$type :tutorials.notebook/SpecRef$v1,
                           :workspaceName "my",
                           :specName "B",
                           :specVersion 1}}
           :tutorials.notebook/SpecId$v1)


;-- result --
{:$type :tutorials.notebook/SpecId$v1,
 :specName "B",
 :specVersion 1,
 :workspaceName "my"}
```

An example of resolving a reference to an ephemeral spec.

```clojure
(refine-to {:$type :tutorials.notebook/SpecRefResolver$v1,
            :existingSpecIds [{:$type :tutorials.notebook/SpecId$v1,
                               :workspaceName "my",
                               :specName "A",
                               :specVersion 1}
                              {:$type :tutorials.notebook/SpecId$v1,
                               :workspaceName "my",
                               :specName "B",
                               :specVersion 1}
                              {:$type :tutorials.notebook/SpecId$v1,
                               :workspaceName "my",
                               :specName "A",
                               :specVersion 2}],
            :newSpecs [{:$type :tutorials.notebook/NewSpec$v1,
                        :workspaceName "my",
                        :specName "C",
                        :specVersion 1,
                        :isEphemeral true}
                       {:$type :tutorials.notebook/NewSpec$v1,
                        :workspaceName "my",
                        :specName "A",
                        :specVersion 3}],
            :inputSpecRef {:$type :tutorials.notebook/SpecRef$v1,
                           :workspaceName "my",
                           :specName "C",
                           :specVersion 1}}
           :tutorials.notebook/SpecId$v1)


;-- result --
{:$type :tutorials.notebook/SpecId$v1,
 :specName "C",
 :specVersion 1,
 :workspaceName "my"}
```

A reference to a hypothetical ephemeral spec that does not exist does not resolve.

```clojure
(refines-to? {:$type :tutorials.notebook/SpecRefResolver$v1,
              :existingSpecIds [{:$type :tutorials.notebook/SpecId$v1,
                                 :workspaceName "my",
                                 :specName "A",
                                 :specVersion 1}
                                {:$type :tutorials.notebook/SpecId$v1,
                                 :workspaceName "my",
                                 :specName "B",
                                 :specVersion 1}
                                {:$type :tutorials.notebook/SpecId$v1,
                                 :workspaceName "my",
                                 :specName "A",
                                 :specVersion 2}],
              :newSpecs [{:$type :tutorials.notebook/NewSpec$v1,
                          :workspaceName "my",
                          :specName "C",
                          :specVersion 1,
                          :isEphemeral true}
                         {:$type :tutorials.notebook/NewSpec$v1,
                          :workspaceName "my",
                          :specName "A",
                          :specVersion 3}],
              :inputSpecRef {:$type :tutorials.notebook/SpecRef$v1,
                             :workspaceName "my",
                             :specName "C",
                             :specVersion 2}}
             :tutorials.notebook/SpecId$v1)


;-- result --
false
```

A reference to a completely unknown spec name does not resolve.

```clojure
(refines-to? {:$type :tutorials.notebook/SpecRefResolver$v1,
              :existingSpecIds [{:$type :tutorials.notebook/SpecId$v1,
                                 :workspaceName "my",
                                 :specName "A",
                                 :specVersion 1}
                                {:$type :tutorials.notebook/SpecId$v1,
                                 :workspaceName "my",
                                 :specName "B",
                                 :specVersion 1}
                                {:$type :tutorials.notebook/SpecId$v1,
                                 :workspaceName "my",
                                 :specName "A",
                                 :specVersion 2}],
              :newSpecs [{:$type :tutorials.notebook/NewSpec$v1,
                          :workspaceName "my",
                          :specName "C",
                          :specVersion 1,
                          :isEphemeral true}
                         {:$type :tutorials.notebook/NewSpec$v1,
                          :workspaceName "my",
                          :specName "A",
                          :specVersion 3}],
              :inputSpecRef {:$type :tutorials.notebook/SpecRef$v1,
                             :workspaceName "my",
                             :specName "X",
                             :specVersion 1}}
             :tutorials.notebook/SpecId$v1)


;-- result --
false
```

Make a spec to hold the result of resolving all spec references in a notebook.

```clojure
{:tutorials.notebook/ResolvedSpecRefs$v1
   {:fields {:specRefs [:Vec :tutorials.notebook/SpecId$v1]}}}
```

A notebook contains spec references. This is modeled as the results of having parsed the references out of the contents of the notebook. Constraints are added to make some of the vector fields have set semantics (i.e. to not allow duplicates). This is done rather than representing the fields as sets because sequence operations cannot be deterministically applied to sets.

```clojure
{:tutorials.notebook/Notebook$v1
   {:fields {:name :String,
             :items [:Vec :tutorials.notebook/AbstractNotebookItem$v1],
             :version :Integer},
    :constraints #{'{:name "positiveVersion",
                     :expr (valid? {:$type :tutorials.notebook/Version$v1,
                                    :version version})}}}}
```

The contents of notebooks can be used as the basis for defining regression tests for a workspace.

```clojure
{:tutorials.notebook/RegressionTest$v1
   {:fields {:notebookName :String,
             :notebookVersion :Integer},
    :constraints #{'{:name "positiveVersion",
                     :expr (valid? {:$type :tutorials.notebook/Version$v1,
                                    :version notebookVersion})}}}}
```

Finally, we can create a top-level spec that represents a workspace and the items it contains. Two separate fields are used to represent the specs that are available in a workspace. One captures all of those that are registered. The other captures, just the "private" specs that are defined in this workspace, but not made available in the registry.

```clojure
{:tutorials.notebook/Workspace$v1
   {:fields {:notebooks [:Vec :tutorials.notebook/Notebook$v1],
             :registrySpecIds [:Vec :tutorials.notebook/SpecId$v1],
             :specIds [:Vec :tutorials.notebook/SpecId$v1],
             :tests [:Vec :tutorials.notebook/RegressionTest$v1],
             :workspaceName :String},
    :constraints
      #{'{:name "privateSpecIdsInThisWorkspace",
          :expr (= 0
                   (count (filter [si specIds]
                            (not= (get si :workspaceName) workspaceName))))}
        '{:name "specIdsDisjoint",
          :expr (= 0
                   (count (intersection (concat #{} registrySpecIds)
                                        (concat #{} specIds))))}
        '{:name "uniqueNotebookNames",
          :expr (= (count (concat #{} (map [n notebooks] (get n :name))))
                   (count notebooks))}
        '{:name "uniqueRegistrySpecIds",
          :expr (= (count (concat #{} registrySpecIds))
                   (count registrySpecIds))}
        '{:name "uniqueSpecIds",
          :expr (= (count (concat #{} specIds)) (count specIds))}
        '{:name "uniqueTestNames",
          :expr (= (count (concat #{} (map [t tests] (get t :notebookName))))
                   (count tests))}}}}
```

An example of a valid workspace instance.

```clojure
{:$type :tutorials.notebook/Workspace$v1,
 :notebooks [{:name "notebook1",
              :$type :tutorials.notebook/Notebook$v1,
              :items [],
              :version 1}],
 :registrySpecIds [],
 :specIds [{:$type :tutorials.notebook/SpecId$v1,
            :specName "A",
            :specVersion 1,
            :workspaceName "my"}
           {:$type :tutorials.notebook/SpecId$v1,
            :specName "B",
            :specVersion 1,
            :workspaceName "my"}
           {:$type :tutorials.notebook/SpecId$v1,
            :specName "A",
            :specVersion 2,
            :workspaceName "my"}],
 :tests [],
 :workspaceName "my"}
```

Example workspace instance that violates a spec id constraint.

```clojure
{:$type :tutorials.notebook/Workspace$v1,
 :notebooks [{:name "notebook1",
              :$type :tutorials.notebook/Notebook$v1,
              :items [],
              :version 1}],
 :registrySpecIds [],
 :specIds [{:$type :tutorials.notebook/SpecId$v1,
            :specName "A",
            :specVersion 1,
            :workspaceName "my"}
           {:$type :tutorials.notebook/SpecId$v1,
            :specName "B",
            :specVersion 1,
            :workspaceName "my"}
           {:$type :tutorials.notebook/SpecId$v1,
            :specName "A",
            :specVersion 2,
            :workspaceName "my"}
           {:$type :tutorials.notebook/SpecId$v1,
            :specName "A",
            :specVersion 2,
            :workspaceName "my"}],
 :tests [],
 :workspaceName "my"}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.notebook/Workspace$v1', violates constraints \"tutorials.notebook/Workspace$v1/uniqueSpecIds\""
 :h-err/invalid-instance]
```

Example of a workspace that violates a notebook name constraint.

```clojure
{:$type :tutorials.notebook/Workspace$v1,
 :notebooks [{:name "notebook1",
              :$type :tutorials.notebook/Notebook$v1,
              :items [],
              :version 1}
             {:name "notebook1",
              :$type :tutorials.notebook/Notebook$v1,
              :items [],
              :version 2}],
 :registrySpecIds [],
 :specIds [{:$type :tutorials.notebook/SpecId$v1,
            :specName "A",
            :specVersion 1,
            :workspaceName "my"}
           {:$type :tutorials.notebook/SpecId$v1,
            :specName "B",
            :specVersion 1,
            :workspaceName "my"}
           {:$type :tutorials.notebook/SpecId$v1,
            :specName "A",
            :specVersion 2,
            :workspaceName "my"}],
 :tests [],
 :workspaceName "my"}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.notebook/Workspace$v1', violates constraints \"tutorials.notebook/Workspace$v1/uniqueNotebookNames\""
 :h-err/invalid-instance]
```

```clojure
{:tutorials.notebook/RSpecIds$v1
   {:fields {:result [:Vec :tutorials.notebook/SpecId$v1]}},
 :tutorials.notebook/ResolveRefs$v1
   {:fields {:items [:Vec :tutorials.notebook/AbstractNotebookItem$v1],
             :specIds [:Vec :tutorials.notebook/SpecId$v1]},
    :constraints
      #{'{:name "allResolve",
          :expr (= (count (filter [item items]
                            (refines-to? item :tutorials.notebook/SpecRef$v1)))
                   (count
                     (get (refine-to
                            {:$type :tutorials.notebook/ResolveRefsDirect$v1,
                             :specIds specIds,
                             :items items}
                            :tutorials.notebook/RSpecIds$v1)
                          :result)))}},
    :refines-to {:tutorials.notebook/RSpecIds$v1
                   {:name "resolve",
                    :expr '(refine-to
                             {:$type :tutorials.notebook/ResolveRefsDirect$v1,
                              :specIds specIds,
                              :items items}
                             :tutorials.notebook/RSpecIds$v1)}}},
 :tutorials.notebook/ResolveRefsDirect$v1
   {:fields {:items [:Vec :tutorials.notebook/AbstractNotebookItem$v1],
             :specIds [:Vec :tutorials.notebook/SpecId$v1]},
    :refines-to
      {:tutorials.notebook/RSpecIds$v1
         {:name "validRefs",
          :expr
            '{:$type :tutorials.notebook/RSpecIds$v1,
              :result
                (get
                  (reduce [state
                           {:$type :tutorials.notebook/ResolveRefsState$v1,
                            :context specIds,
                            :resolved []}]
                    [item items]
                    (if (refines-to? item :tutorials.notebook/NewSpec$v1)
                      (let [new-spec (refine-to item
                                                :tutorials.notebook/NewSpec$v1)]
                        {:$type :tutorials.notebook/ResolveRefsState$v1,
                         :context (conj (get state :context)
                                        (refine-to
                                          new-spec
                                          :tutorials.notebook/SpecId$v1)),
                         :resolved (get state :resolved)})
                      (let [spec-ref (refine-to item
                                                :tutorials.notebook/SpecRef$v1)
                            resolver {:$type
                                        :tutorials.notebook/SpecRefResolver$v1,
                                      :existingSpecIds (get state :context),
                                      :newSpecs [],
                                      :inputSpecRef spec-ref}
                            resolved
                              (when (refines-to? resolver
                                                 :tutorials.notebook/SpecId$v1)
                                (refine-to resolver
                                           :tutorials.notebook/SpecId$v1))]
                        {:$type :tutorials.notebook/ResolveRefsState$v1,
                         :context (get state :context),
                         :resolved (if-value resolved
                                             (conj (get state :resolved)
                                                   resolved)
                                             (get state :resolved))})))
                  :resolved)}}}},
 :tutorials.notebook/ResolveRefsState$v1
   {:fields {:context [:Vec :tutorials.notebook/SpecId$v1],
             :resolved [:Vec :tutorials.notebook/SpecId$v1]}}}
```

```clojure
(valid? {:$type :tutorials.notebook/ResolveRefs$v1,
         :specIds [{:$type :tutorials.notebook/SpecId$v1,
                    :workspaceName "my",
                    :specName "A",
                    :specVersion 1}
                   {:$type :tutorials.notebook/SpecId$v1,
                    :workspaceName "my",
                    :specName "B",
                    :specVersion 1}
                   {:$type :tutorials.notebook/SpecId$v1,
                    :workspaceName "my",
                    :specName "A",
                    :specVersion 2}],
         :items []})


;-- result --
true
```

```clojure
(valid? {:$type :tutorials.notebook/ResolveRefs$v1,
         :specIds [{:$type :tutorials.notebook/SpecId$v1,
                    :workspaceName "my",
                    :specName "A",
                    :specVersion 1}
                   {:$type :tutorials.notebook/SpecId$v1,
                    :workspaceName "my",
                    :specName "B",
                    :specVersion 1}
                   {:$type :tutorials.notebook/SpecId$v1,
                    :workspaceName "my",
                    :specName "A",
                    :specVersion 2}],
         :items [{:$type :tutorials.notebook/SpecRef$v1,
                  :workspaceName "my",
                  :specName "A",
                  :specVersion 1}]})


;-- result --
true
```

```clojure
(valid? {:$type :tutorials.notebook/ResolveRefs$v1,
         :specIds [{:$type :tutorials.notebook/SpecId$v1,
                    :workspaceName "my",
                    :specName "A",
                    :specVersion 1}
                   {:$type :tutorials.notebook/SpecId$v1,
                    :workspaceName "my",
                    :specName "B",
                    :specVersion 1}
                   {:$type :tutorials.notebook/SpecId$v1,
                    :workspaceName "my",
                    :specName "A",
                    :specVersion 2}],
         :items [{:$type :tutorials.notebook/NewSpec$v1,
                  :workspaceName "my",
                  :specName "A",
                  :specVersion 3}
                 {:$type :tutorials.notebook/SpecRef$v1,
                  :workspaceName "my",
                  :specName "A",
                  :specVersion 3}]})


;-- result --
true
```

```clojure
(valid? {:$type :tutorials.notebook/ResolveRefs$v1,
         :specIds [{:$type :tutorials.notebook/SpecId$v1,
                    :workspaceName "my",
                    :specName "A",
                    :specVersion 1}
                   {:$type :tutorials.notebook/SpecId$v1,
                    :workspaceName "my",
                    :specName "B",
                    :specVersion 1}
                   {:$type :tutorials.notebook/SpecId$v1,
                    :workspaceName "my",
                    :specName "A",
                    :specVersion 2}],
         :items [{:$type :tutorials.notebook/SpecRef$v1,
                  :workspaceName "my",
                  :specName "A",
                  :specVersion 3}
                 {:$type :tutorials.notebook/NewSpec$v1,
                  :workspaceName "my",
                  :specName "A",
                  :specVersion 3}]})


;-- result --
false
```

```clojure
(get (refine-to {:$type :tutorials.notebook/ResolveRefs$v1,
                 :specIds [{:$type :tutorials.notebook/SpecId$v1,
                            :workspaceName "my",
                            :specName "A",
                            :specVersion 1}
                           {:$type :tutorials.notebook/SpecId$v1,
                            :workspaceName "my",
                            :specName "B",
                            :specVersion 1}
                           {:$type :tutorials.notebook/SpecId$v1,
                            :workspaceName "my",
                            :specName "A",
                            :specVersion 2}],
                 :items [{:$type :tutorials.notebook/SpecRef$v1,
                          :workspaceName "my",
                          :specName "A"}
                         {:$type :tutorials.notebook/NewSpec$v1,
                          :workspaceName "my",
                          :specName "A",
                          :specVersion 3}
                         {:$type :tutorials.notebook/SpecRef$v1,
                          :workspaceName "my",
                          :specName "A"}]}
                :tutorials.notebook/RSpecIds$v1)
     :result)


;-- result --
[{:$type :tutorials.notebook/SpecId$v1,
  :specName "A",
  :specVersion 2,
  :workspaceName "my"}
 {:$type :tutorials.notebook/SpecId$v1,
  :specName "A",
  :specVersion 3,
  :workspaceName "my"}]
```

```clojure
{:tutorials.notebook/ApplicableNewSpecs$v1
   {:fields {:newSpecs [:Vec :tutorials.notebook/NewSpec$v1],
             :specIds [:Vec :tutorials.notebook/SpecId$v1],
             :workspaceName :String},
    :constraints
      #{'{:name "newSpecsInOrder",
          :expr
            (let [all-spec-names
                    (reduce [a #{}]
                      [ns newSpecs]
                      (if (contains? a
                                     [(get ns :workspaceName)
                                      (get ns :specName)])
                        a
                        (conj a [(get ns :workspaceName) (get ns :specName)])))]
              (every?
                [n all-spec-names]
                (let [max-version
                        (get (refine-to
                               {:$type :tutorials.notebook/FMaxSpecVersion$v1,
                                :specIds specIds,
                                :workspaceName (get n 0),
                                :specName (get n 1)}
                               :tutorials.notebook/RInteger$v1)
                             :result)
                      versions
                        (concat
                          [(if-value max-version max-version 0)]
                          (map [ns
                                (filter [ns newSpecs]
                                  (and (= (get n 0) (get ns :workspaceName))
                                       (= (get n 1) (get ns :specName))))]
                            (get ns :specVersion)))]
                  (every? [pair
                           (map [i (range 0 (dec (count versions)))]
                             [(get versions i) (get versions (inc i))])]
                          (= (inc (get pair 0)) (get pair 1))))))}
        '{:name "newSpecsInThisWorkspace",
          :expr (= 0
                   (count (filter [ns newSpecs]
                            (not (= (get ns :workspaceName) workspaceName)))))}
        '{:name "nonEphemeralBuiltOnNonEphemeral",
          :expr (let [all-spec-names (reduce [a #{}]
                                       [ns newSpecs]
                                       (if (contains? a
                                                      [(get ns :workspaceName)
                                                       (get ns :specName)])
                                         a
                                         (conj a
                                               [(get ns :workspaceName)
                                                (get ns :specName)])))]
                  (every?
                    [n all-spec-names]
                    (let [ephemeral-values
                            (concat
                              [false]
                              (map [ns
                                    (filter [ns newSpecs]
                                      (and (= (get n 0) (get ns :workspaceName))
                                           (= (get n 1) (get ns :specName))))]
                                (let [is-e (get ns :isEphemeral)]
                                  (if-value is-e true false))))]
                      (every? [pair
                               (map [i (range 0 (dec (count ephemeral-values)))]
                                 [(get ephemeral-values i)
                                  (get ephemeral-values (inc i))])]
                              (or (get pair 1) (not (get pair 0)))))))}}}}
```

```clojure
(valid? {:$type :tutorials.notebook/ApplicableNewSpecs$v1,
         :workspaceName "my",
         :specIds [{:$type :tutorials.notebook/SpecId$v1,
                    :workspaceName "my",
                    :specName "A",
                    :specVersion 1}
                   {:$type :tutorials.notebook/SpecId$v1,
                    :workspaceName "my",
                    :specName "B",
                    :specVersion 1}
                   {:$type :tutorials.notebook/SpecId$v1,
                    :workspaceName "my",
                    :specName "A",
                    :specVersion 2}],
         :newSpecs [{:$type :tutorials.notebook/NewSpec$v1,
                     :workspaceName "my",
                     :specName "A",
                     :specVersion 4}]})


;-- result --
false
```

```clojure
(valid? {:$type :tutorials.notebook/ApplicableNewSpecs$v1,
         :workspaceName "my",
         :specIds [{:$type :tutorials.notebook/SpecId$v1,
                    :workspaceName "my",
                    :specName "A",
                    :specVersion 1}
                   {:$type :tutorials.notebook/SpecId$v1,
                    :workspaceName "my",
                    :specName "B",
                    :specVersion 1}
                   {:$type :tutorials.notebook/SpecId$v1,
                    :workspaceName "my",
                    :specName "A",
                    :specVersion 2}],
         :newSpecs [{:$type :tutorials.notebook/NewSpec$v1,
                     :workspaceName "my",
                     :specName "C",
                     :specVersion 4}]})


;-- result --
false
```

```clojure
(valid? {:$type :tutorials.notebook/ApplicableNewSpecs$v1,
         :workspaceName "my",
         :specIds [{:$type :tutorials.notebook/SpecId$v1,
                    :workspaceName "my",
                    :specName "A",
                    :specVersion 1}
                   {:$type :tutorials.notebook/SpecId$v1,
                    :workspaceName "my",
                    :specName "B",
                    :specVersion 1}
                   {:$type :tutorials.notebook/SpecId$v1,
                    :workspaceName "my",
                    :specName "A",
                    :specVersion 2}],
         :newSpecs [{:$type :tutorials.notebook/NewSpec$v1,
                     :workspaceName "my",
                     :specName "A",
                     :specVersion 2}]})


;-- result --
false
```

```clojure
(valid? {:$type :tutorials.notebook/ApplicableNewSpecs$v1,
         :workspaceName "my",
         :specIds [{:$type :tutorials.notebook/SpecId$v1,
                    :workspaceName "my",
                    :specName "A",
                    :specVersion 1}
                   {:$type :tutorials.notebook/SpecId$v1,
                    :workspaceName "my",
                    :specName "B",
                    :specVersion 1}
                   {:$type :tutorials.notebook/SpecId$v1,
                    :workspaceName "my",
                    :specName "A",
                    :specVersion 2}],
         :newSpecs [{:$type :tutorials.notebook/NewSpec$v1,
                     :workspaceName "my",
                     :specName "A",
                     :specVersion 3}
                    {:$type :tutorials.notebook/NewSpec$v1,
                     :workspaceName "my",
                     :specName "B",
                     :specVersion 2}
                    {:$type :tutorials.notebook/NewSpec$v1,
                     :workspaceName "my",
                     :specName "A",
                     :specVersion 4}
                    {:$type :tutorials.notebook/NewSpec$v1,
                     :workspaceName "my",
                     :specName "C",
                     :specVersion 1}]})


;-- result --
true
```

```clojure
(valid? {:$type :tutorials.notebook/ApplicableNewSpecs$v1,
         :workspaceName "my",
         :specIds [],
         :newSpecs [{:$type :tutorials.notebook/NewSpec$v1,
                     :workspaceName "my",
                     :specName "C",
                     :specVersion 1}
                    {:$type :tutorials.notebook/NewSpec$v1,
                     :workspaceName "my",
                     :specName "C",
                     :specVersion 2,
                     :isEphemeral true}]})


;-- result --
true
```

Cannot create an non-ephemeral spec "on top" of an ephemeral spec.

```clojure
(valid? {:$type :tutorials.notebook/ApplicableNewSpecs$v1,
         :workspaceName "my",
         :specIds [],
         :newSpecs [{:$type :tutorials.notebook/NewSpec$v1,
                     :workspaceName "my",
                     :specName "C",
                     :specVersion 1,
                     :isEphemeral true}
                    {:$type :tutorials.notebook/NewSpec$v1,
                     :workspaceName "my",
                     :specName "C",
                     :specVersion 2}]})


;-- result --
false
```

```clojure
{:tutorials.notebook/ApplyNotebook$v1
   {:fields {:notebookName :String,
             :notebookVersion :Integer,
             :workspace :tutorials.notebook/Workspace$v1},
    :constraints
      #{'{:name "notebookContainsNonEphemeralNewSpecs",
          :expr
            (let [filtered (filter [nb (get workspace :notebooks)]
                             (and (= (get nb :name) notebookName)
                                  (= (get nb :version) notebookVersion)))]
              (if (> (count filtered) 0)
                (let [nb (first filtered)]
                  (> (count
                       (filter
                         [ns
                          (map [item
                                (filter [item (get nb :items)]
                                  (refines-to? item
                                               :tutorials.notebook/NewSpec$v1))]
                            (refine-to item :tutorials.notebook/NewSpec$v1))]
                         (let [is-ephemeral (get ns :isEphemeral)]
                           (if-value is-ephemeral false true))))
                     0))
                true))}
        '{:name "notebookExists",
          :expr (> (count (filter [nb (get workspace :notebooks)]
                            (and (= (get nb :name) notebookName)
                                 (= (get nb :version) notebookVersion))))
                   0)}
        '{:name "specsApplicable",
          :expr (let [filtered (filter [nb (get workspace :notebooks)]
                                 (and (= (get nb :name) notebookName)
                                      (= (get nb :version) notebookVersion)))]
                  (if (> (count filtered) 0)
                    (let [nb (first filtered)]
                      (valid?
                        {:$type :tutorials.notebook/ApplicableNewSpecs$v1,
                         :workspaceName (get workspace :workspaceName),
                         :specIds (concat (get workspace :specIds)
                                          (get workspace :registrySpecIds)),
                         :newSpecs
                           (map [item
                                 (filter [item (get nb :items)]
                                   (refines-to?
                                     item
                                     :tutorials.notebook/NewSpec$v1))]
                             (refine-to item :tutorials.notebook/NewSpec$v1))}))
                    true))}
        '{:name "specsValidRefs",
          :expr (let [filtered (filter [nb (get workspace :notebooks)]
                                 (and (= (get nb :name) notebookName)
                                      (= (get nb :version) notebookVersion)))]
                  (if (> (count filtered) 0)
                    (let [nb (first filtered)]
                      (valid?
                        {:$type :tutorials.notebook/ResolveRefs$v1,
                         :specIds (concat (get workspace :specIds)
                                          (get workspace :registrySpecIds)),
                         :items (get nb :items)}))
                    true))}},
    :refines-to
      {:tutorials.notebook/WorkspaceAndEffects$v1
         {:name "newWorkspaceAndEffects",
          :expr
            '(let [filtered (filter [nb (get workspace :notebooks)]
                              (and (= (get nb :name) notebookName)
                                   (= (get nb :version) notebookVersion)))]
               (when (> (count filtered) 0)
                 (let [nb (first filtered)
                       new-spec-ids
                         (map
                           [ns
                            (filter [ns
                                     (map [item
                                           (filter [item (get nb :items)]
                                             (refines-to?
                                               item
                                               :tutorials.notebook/NewSpec$v1))]
                                       (refine-to
                                         item
                                         :tutorials.notebook/NewSpec$v1))]
                              (let [is-ephemeral (get ns :isEphemeral)]
                                (if-value is-ephemeral false true)))]
                           (refine-to ns :tutorials.notebook/SpecId$v1))
                       new-notebook
                         {:$type :tutorials.notebook/Notebook$v1,
                          :name (get nb :name),
                          :version (inc (get nb :version)),
                          :items (filter [item (get nb :items)]
                                   (if (refines-to?
                                         item
                                         :tutorials.notebook/NewSpec$v1)
                                     (let [ns (refine-to
                                                item
                                                :tutorials.notebook/NewSpec$v1)
                                           is-ephemeral (get ns :isEphemeral)]
                                       (if-value is-ephemeral true false))
                                     true))}]
                   {:$type :tutorials.notebook/WorkspaceAndEffects$v1,
                    :workspace
                      {:$type :tutorials.notebook/Workspace$v1,
                       :workspaceName (get workspace :workspaceName),
                       :registrySpecIds (get workspace :registrySpecIds),
                       :specIds (concat (get workspace :specIds) new-spec-ids),
                       :notebooks (conj (filter [nb (get workspace :notebooks)]
                                          (or (not= (get nb :name) notebookName)
                                              (not= (get nb :version)
                                                    notebookVersion)))
                                        new-notebook),
                       :tests (get workspace :tests)},
                    :effects (concat
                               (map [si new-spec-ids]
                                 {:$type :tutorials.notebook/WriteSpecEffect$v1,
                                  :specId si})
                               [{:$type
                                   :tutorials.notebook/WriteNotebookEffect$v1,
                                 :notebookName (get new-notebook :name),
                                 :notebookVersion (get new-notebook :version)}
                                {:$type :tutorials.notebook/RunTestsEffect$v1,
                                 :notebookName notebookName,
                                 :notebookVersion notebookVersion,
                                 :workspaceSpecs true}])})))}}},
 :tutorials.notebook/CreateRegressionTest$v1
   {:fields {:notebookName :String,
             :notebookVersion :Integer,
             :workspace :tutorials.notebook/Workspace$v1},
    :constraints
      #{'{:name "notebookCannotContainNewNonEphemeralSpecs",
          :expr
            (let [filtered (filter [nb (get workspace :notebooks)]
                             (and (= (get nb :name) notebookName)
                                  (= (get nb :version) notebookVersion)))]
              (if (> (count filtered) 0)
                (let [nb (first filtered)]
                  (= (count
                       (filter
                         [ns
                          (map [item
                                (filter [item (get nb :items)]
                                  (refines-to? item
                                               :tutorials.notebook/NewSpec$v1))]
                            (refine-to item :tutorials.notebook/NewSpec$v1))]
                         (let [is-ephemeral (get ns :isEphemeral)]
                           (if-value is-ephemeral false true))))
                     0))
                true))}
        '{:name "notebookExists",
          :expr (> (count (filter [nb (get workspace :notebooks)]
                            (and (= (get nb :name) notebookName)
                                 (= (get nb :version) notebookVersion))))
                   0)}
        '{:name "specsValidRefs",
          :expr (let [filtered (filter [nb (get workspace :notebooks)]
                                 (and (= (get nb :name) notebookName)
                                      (= (get nb :version) notebookVersion)))]
                  (if (> (count filtered) 0)
                    (let [nb (first filtered)]
                      (valid? {:$type :tutorials.notebook/ResolveRefs$v1,
                               :specIds (get workspace :registrySpecIds),
                               :items (get nb :items)}))
                    true))}},
    :refines-to
      {:tutorials.notebook/WorkspaceAndEffects$v1
         {:name "newWorkspaceAndEffects",
          :expr
            '(let [filtered (filter [nb (get workspace :notebooks)]
                              (and (= (get nb :name) notebookName)
                                   (= (get nb :version) notebookVersion)))]
               (when (> (count filtered) 0)
                 (let [nb (first filtered)
                       new-test {:$type :tutorials.notebook/RegressionTest$v1,
                                 :notebookName notebookName,
                                 :notebookVersion notebookVersion}]
                   {:$type :tutorials.notebook/WorkspaceAndEffects$v1,
                    :workspace {:$type :tutorials.notebook/Workspace$v1,
                                :workspaceName (get workspace :workspaceName),
                                :registrySpecIds (get workspace
                                                      :registrySpecIds),
                                :specIds (get workspace :specIds),
                                :notebooks (get workspace :notebooks),
                                :tests (conj (get workspace :tests) new-test)},
                    :effects
                      [{:$type :tutorials.notebook/WriteRegressionTestEffect$v1,
                        :notebookName (get new-test :notebookName),
                        :notebookVersion (get new-test :notebookVersion)}
                       {:$type :tutorials.notebook/RunTestsEffect$v1,
                        :notebookName notebookName,
                        :notebookVersion notebookVersion,
                        :registrySpecs true}]})))}}},
 :tutorials.notebook/DeleteNotebook$v1
   {:fields {:notebookName :String,
             :notebookVersion :Integer,
             :workspace :tutorials.notebook/Workspace$v1},
    :constraints
      #{'{:name "notebookExists",
          :expr (if (> notebookVersion 1)
                  (let [filtered (filter [nb (get workspace :notebooks)]
                                   (and (= (get nb :name) notebookName)
                                        (= (get nb :version) notebookVersion)))]
                    (= (count filtered) 1))
                  true)}
        '{:name "positiveVersion",
          :expr (valid? {:$type :tutorials.notebook/Version$v1,
                         :version notebookVersion})}},
    :refines-to
      {:tutorials.notebook/WorkspaceAndEffects$v1
         {:name "newWorkspaceAndEffects",
          :expr '{:$type :tutorials.notebook/WorkspaceAndEffects$v1,
                  :workspace {:$type :tutorials.notebook/Workspace$v1,
                              :workspaceName (get workspace :workspaceName),
                              :registrySpecIds (get workspace :registrySpecIds),
                              :specIds (get workspace :specIds),
                              :notebooks (filter [nb (get workspace :notebooks)]
                                           (or (not (= (get nb :name)
                                                       notebookName))
                                               (not (= (get nb :version)
                                                       notebookVersion)))),
                              :tests (get workspace :tests)},
                  :effects [{:$type :tutorials.notebook/DeleteNotebookEffect$v1,
                             :notebookName notebookName,
                             :notebookVersion notebookVersion}]}}}},
 :tutorials.notebook/DeleteNotebookEffect$v1
   {:fields {:notebookName :String,
             :notebookVersion :Integer},
    :refines-to {:tutorials.notebook/Effect$v1
                   {:name "effect",
                    :expr '{:$type :tutorials.notebook/Effect$v1}}}},
 :tutorials.notebook/DeleteRegressionTest$v1
   {:fields {:notebookName :String,
             :notebookVersion :Integer,
             :workspace :tutorials.notebook/Workspace$v1},
    :constraints
      #{'{:name "testExists",
          :expr (> (count (filter [t (get workspace :tests)]
                            (and (= (get t :notebookName) notebookName)
                                 (= (get t :notebookVersion) notebookVersion))))
                   0)}},
    :refines-to
      {:tutorials.notebook/WorkspaceAndEffects$v1
         {:name "newWorkspaceAndEffects",
          :expr
            '(let [filtered (filter [t (get workspace :tests)]
                              (and (= (get t :notebookName) notebookName)
                                   (= (get t :notebookVersion)
                                      notebookVersion)))]
               (let [to-remove (first filtered)]
                 {:$type :tutorials.notebook/WorkspaceAndEffects$v1,
                  :workspace {:$type :tutorials.notebook/Workspace$v1,
                              :workspaceName (get workspace :workspaceName),
                              :registrySpecIds (get workspace :registrySpecIds),
                              :specIds (get workspace :specIds),
                              :notebooks (get workspace :notebooks),
                              :tests (filter [t (get workspace :tests)]
                                       (not= t to-remove))},
                  :effects
                    [{:$type :tutorials.notebook/DeleteRegressionTestEffect$v1,
                      :notebookName (get to-remove :notebookName),
                      :notebookVersion (get to-remove :notebookVersion)}]}))}}},
 :tutorials.notebook/DeleteRegressionTestEffect$v1
   {:fields {:notebookName :String,
             :notebookVersion :Integer},
    :refines-to {:tutorials.notebook/Effect$v1
                   {:name "effect",
                    :expr '{:$type :tutorials.notebook/Effect$v1}}}},
 :tutorials.notebook/Effect$v1 {:abstract? true},
 :tutorials.notebook/RunTestsEffect$v1
   {:fields {:notebookName :String,
             :notebookVersion :Integer,
             :registrySpecs [:Maybe :Boolean],
             :workspaceSpecs [:Maybe :Boolean]},
    :constraints
      #{'{:name "exclusiveFlags",
          :expr (and (or (if-value registrySpecs registrySpecs false)
                         (if-value workspaceSpecs workspaceSpecs false))
                     (not (and
                            (if-value registrySpecs registrySpecs false)
                            (if-value workspaceSpecs workspaceSpecs false))))}
        '{:name "registrySpecsFlag",
          :expr (if-value registrySpecs registrySpecs true)}
        '{:name "workspaceSpecsFlag",
          :expr (if-value workspaceSpecs workspaceSpecs true)}},
    :refines-to {:tutorials.notebook/Effect$v1
                   {:name "effect",
                    :expr '{:$type :tutorials.notebook/Effect$v1}}}},
 :tutorials.notebook/UpdateRegressionTest$v1
   {:fields {:lastNotebookVersion :Integer,
             :notebookName :String,
             :notebookVersion :Integer,
             :workspace :tutorials.notebook/Workspace$v1},
    :constraints
      #{'{:name "notebookCannotContainNewNonEphemeralSpecs",
          :expr
            (let [filtered (filter [nb (get workspace :notebooks)]
                             (and (= (get nb :name) notebookName)
                                  (= (get nb :version) notebookVersion)))]
              (if (> (count filtered) 0)
                (let [nb (first filtered)]
                  (= (count
                       (filter
                         [ns
                          (map [item
                                (filter [item (get nb :items)]
                                  (refines-to? item
                                               :tutorials.notebook/NewSpec$v1))]
                            (refine-to item :tutorials.notebook/NewSpec$v1))]
                         (let [is-ephemeral (get ns :isEphemeral)]
                           (if-value is-ephemeral false true))))
                     0))
                true))}
        '{:name "notebookExists",
          :expr (> (count (filter [nb (get workspace :notebooks)]
                            (and (= (get nb :name) notebookName)
                                 (= (get nb :version) notebookVersion))))
                   0)}
        '{:name "specsValidRefs",
          :expr (let [filtered (filter [nb (get workspace :notebooks)]
                                 (and (= (get nb :name) notebookName)
                                      (= (get nb :version) notebookVersion)))]
                  (if (> (count filtered) 0)
                    (let [nb (first filtered)]
                      (valid?
                        {:$type :tutorials.notebook/ResolveRefs$v1,
                         :specIds (concat (get workspace :specIds)
                                          (get workspace :registrySpecIds)),
                         :items (get nb :items)}))
                    true))}
        '{:name "testExists",
          :expr (> (count
                     (filter [t (get workspace :tests)]
                       (and (= (get t :notebookName) notebookName)
                            (= (get t :notebookVersion) lastNotebookVersion))))
                   0)}},
    :refines-to
      {:tutorials.notebook/WorkspaceAndEffects$v1
         {:name "newWorkspaceAndEffects",
          :expr
            '(let [filtered (filter [nb (get workspace :notebooks)]
                              (and (= (get nb :name) notebookName)
                                   (= (get nb :version) notebookVersion)))]
               (when (> (count filtered) 0)
                 (let [nb (first filtered)
                       new-test {:$type :tutorials.notebook/RegressionTest$v1,
                                 :notebookName notebookName,
                                 :notebookVersion notebookVersion}]
                   {:$type :tutorials.notebook/WorkspaceAndEffects$v1,
                    :workspace
                      {:$type :tutorials.notebook/Workspace$v1,
                       :workspaceName (get workspace :workspaceName),
                       :registrySpecIds (get workspace :registrySpecIds),
                       :specIds (get workspace :specIds),
                       :notebooks (get workspace :notebooks),
                       :tests (conj (filter [t (get workspace :tests)]
                                      (not= (get t :notebookName) notebookName))
                                    new-test)},
                    :effects
                      [{:$type :tutorials.notebook/WriteRegressionTestEffect$v1,
                        :notebookName (get new-test :notebookName),
                        :notebookVersion (get new-test :notebookVersion)}
                       {:$type :tutorials.notebook/RunTestsEffect$v1,
                        :notebookName notebookName,
                        :notebookVersion notebookVersion,
                        :registrySpecs true}]})))}}},
 :tutorials.notebook/WorkspaceAndEffects$v1
   {:fields {:effects [:Vec :tutorials.notebook/Effect$v1],
             :workspace :tutorials.notebook/Workspace$v1}},
 :tutorials.notebook/WriteNotebook$v1
   {:fields {:notebookItems [:Vec :tutorials.notebook/AbstractNotebookItem$v1],
             :notebookName :String,
             :notebookVersion :Integer,
             :workspace :tutorials.notebook/Workspace$v1},
    :constraints #{'{:name "positiveVersion",
                     :expr (valid? {:$type :tutorials.notebook/Version$v1,
                                    :version notebookVersion})}
                   '{:name "priorNotebookExists",
                     :expr (if (> notebookVersion 1)
                             (let [filtered
                                     (filter [nb (get workspace :notebooks)]
                                       (and (= (get nb :name) notebookName)
                                            (= (get nb :version)
                                               (dec notebookVersion))))]
                               (= (count filtered) 1))
                             true)}},
    :refines-to
      {:tutorials.notebook/WorkspaceAndEffects$v1
         {:name "newWorkspaceAndEffects",
          :expr '{:$type :tutorials.notebook/WorkspaceAndEffects$v1,
                  :workspace {:$type :tutorials.notebook/Workspace$v1,
                              :workspaceName (get workspace :workspaceName),
                              :registrySpecIds (get workspace :registrySpecIds),
                              :specIds (get workspace :specIds),
                              :notebooks
                                (conj (get workspace :notebooks)
                                      {:$type :tutorials.notebook/Notebook$v1,
                                       :name notebookName,
                                       :version notebookVersion,
                                       :items notebookItems}),
                              :tests (get workspace :tests)},
                  :effects [{:$type :tutorials.notebook/WriteNotebookEffect$v1,
                             :notebookName notebookName,
                             :notebookVersion notebookVersion}]}}}},
 :tutorials.notebook/WriteNotebookEffect$v1
   {:fields {:notebookName :String,
             :notebookVersion :Integer},
    :refines-to {:tutorials.notebook/Effect$v1
                   {:name "effect",
                    :expr '{:$type :tutorials.notebook/Effect$v1}}}},
 :tutorials.notebook/WriteRegressionTestEffect$v1
   {:fields {:notebookName :String,
             :notebookVersion :Integer},
    :refines-to {:tutorials.notebook/Effect$v1
                   {:name "effect",
                    :expr '{:$type :tutorials.notebook/Effect$v1}}}},
 :tutorials.notebook/WriteSpecEffect$v1
   {:fields {:specId :tutorials.notebook/SpecId$v1},
    :refines-to {:tutorials.notebook/Effect$v1
                   {:name "effect",
                    :expr '{:$type :tutorials.notebook/Effect$v1}}}}}
```

```clojure
(let [ws {:$type :tutorials.notebook/Workspace$v1,
          :workspaceName "my",
          :registrySpecIds [],
          :specIds [{:$type :tutorials.notebook/SpecId$v1,
                     :workspaceName "my",
                     :specName "A",
                     :specVersion 1}
                    {:$type :tutorials.notebook/SpecId$v1,
                     :workspaceName "my",
                     :specName "B",
                     :specVersion 1}
                    {:$type :tutorials.notebook/SpecId$v1,
                     :workspaceName "my",
                     :specName "A",
                     :specVersion 2}],
          :notebooks [{:$type :tutorials.notebook/Notebook$v1,
                       :name "notebook1",
                       :version 1,
                       :items [{:$type :tutorials.notebook/NewSpec$v1,
                                :workspaceName "my",
                                :specName "A",
                                :specVersion 3}]}],
          :tests []}]
  [(valid? {:$type :tutorials.notebook/ApplyNotebook$v1,
            :workspace ws,
            :notebookName "notebook1",
            :notebookVersion 1})
   (valid? {:$type :tutorials.notebook/ApplyNotebook$v1,
            :workspace ws,
            :notebookName "notebook1",
            :notebookVersion 2})
   (valid? {:$type :tutorials.notebook/ApplyNotebook$v1,
            :workspace ws,
            :notebookName "notebook2",
            :notebookVersion 1})])


;-- result --
[true false false]
```

If all of the new specs in the notebooks are ephemeral, then it cannot be applied.

```clojure
(let [ws {:$type :tutorials.notebook/Workspace$v1,
          :workspaceName "my",
          :registrySpecIds [],
          :specIds [{:$type :tutorials.notebook/SpecId$v1,
                     :workspaceName "my",
                     :specName "A",
                     :specVersion 1}
                    {:$type :tutorials.notebook/SpecId$v1,
                     :workspaceName "my",
                     :specName "B",
                     :specVersion 1}
                    {:$type :tutorials.notebook/SpecId$v1,
                     :workspaceName "my",
                     :specName "A",
                     :specVersion 2}],
          :notebooks [{:$type :tutorials.notebook/Notebook$v1,
                       :name "notebook1",
                       :version 1,
                       :items [{:$type :tutorials.notebook/NewSpec$v1,
                                :workspaceName "my",
                                :specName "A",
                                :specVersion 3,
                                :isEphemeral true}
                               {:$type :tutorials.notebook/SpecRef$v1,
                                :workspaceName "my",
                                :specName "A",
                                :specVersion 1}
                               {:$type :tutorials.notebook/SpecRef$v1,
                                :workspaceName "my",
                                :specName "A",
                                :specVersion 3}]}],
          :tests []}]
  (valid? {:$type :tutorials.notebook/ApplyNotebook$v1,
           :workspace ws,
           :notebookName "notebook1",
           :notebookVersion 1}))


;-- result --
false
```

```clojure
(let [ws {:$type :tutorials.notebook/Workspace$v1,
          :workspaceName "my",
          :registrySpecIds [],
          :specIds [{:$type :tutorials.notebook/SpecId$v1,
                     :workspaceName "my",
                     :specName "A",
                     :specVersion 1}
                    {:$type :tutorials.notebook/SpecId$v1,
                     :workspaceName "my",
                     :specName "B",
                     :specVersion 1}
                    {:$type :tutorials.notebook/SpecId$v1,
                     :workspaceName "my",
                     :specName "A",
                     :specVersion 2}],
          :notebooks [{:$type :tutorials.notebook/Notebook$v1,
                       :name "notebook1",
                       :version 1,
                       :items [{:$type :tutorials.notebook/SpecRef$v1,
                                :workspaceName "my",
                                :specName "A",
                                :specVersion 1}]}],
          :tests []}]
  (valid? {:$type :tutorials.notebook/ApplyNotebook$v1,
           :workspace ws,
           :notebookName "notebook1",
           :notebookVersion 1}))


;-- result --
false
```

```clojure
(refine-to {:$type :tutorials.notebook/WriteNotebook$v1,
            :workspace {:$type :tutorials.notebook/Workspace$v1,
                        :workspaceName "my",
                        :registrySpecIds [],
                        :specIds [],
                        :notebooks [],
                        :tests []},
            :notebookName "notebook1",
            :notebookVersion 1,
            :notebookItems []}
           :tutorials.notebook/WorkspaceAndEffects$v1)


;-- result --
{:$type :tutorials.notebook/WorkspaceAndEffects$v1,
 :effects [{:$type :tutorials.notebook/WriteNotebookEffect$v1,
            :notebookName "notebook1",
            :notebookVersion 1}],
 :workspace {:$type :tutorials.notebook/Workspace$v1,
             :notebooks [{:name "notebook1",
                          :$type :tutorials.notebook/Notebook$v1,
                          :items [],
                          :version 1}],
             :registrySpecIds [],
             :specIds [],
             :tests [],
             :workspaceName "my"}}
```

```clojure
(refine-to {:$type :tutorials.notebook/DeleteNotebook$v1,
            :workspace {:$type :tutorials.notebook/Workspace$v1,
                        :workspaceName "my",
                        :registrySpecIds [],
                        :specIds [],
                        :notebooks [{:$type :tutorials.notebook/Notebook$v1,
                                     :name "notebook1",
                                     :version 1,
                                     :items []}
                                    {:$type :tutorials.notebook/Notebook$v1,
                                     :name "notebook2",
                                     :version 1,
                                     :items []}],
                        :tests []},
            :notebookName "notebook1",
            :notebookVersion 1}
           :tutorials.notebook/WorkspaceAndEffects$v1)


;-- result --
{:$type :tutorials.notebook/WorkspaceAndEffects$v1,
 :effects [{:$type :tutorials.notebook/DeleteNotebookEffect$v1,
            :notebookName "notebook1",
            :notebookVersion 1}],
 :workspace {:$type :tutorials.notebook/Workspace$v1,
             :notebooks [{:name "notebook2",
                          :$type :tutorials.notebook/Notebook$v1,
                          :items [],
                          :version 1}],
             :registrySpecIds [],
             :specIds [],
             :tests [],
             :workspaceName "my"}}
```

```clojure
(refine-to
  {:$type :tutorials.notebook/ApplyNotebook$v1,
   :workspace {:$type :tutorials.notebook/Workspace$v1,
               :workspaceName "my",
               :registrySpecIds [],
               :specIds [{:$type :tutorials.notebook/SpecId$v1,
                          :workspaceName "my",
                          :specName "A",
                          :specVersion 1}
                         {:$type :tutorials.notebook/SpecId$v1,
                          :workspaceName "my",
                          :specName "B",
                          :specVersion 1}
                         {:$type :tutorials.notebook/SpecId$v1,
                          :workspaceName "my",
                          :specName "A",
                          :specVersion 2}],
               :notebooks [{:$type :tutorials.notebook/Notebook$v1,
                            :name "notebook1",
                            :version 1,
                            :items [{:$type :tutorials.notebook/NewSpec$v1,
                                     :workspaceName "my",
                                     :specName "A",
                                     :specVersion 3}
                                    {:$type :tutorials.notebook/NewSpec$v1,
                                     :workspaceName "my",
                                     :specName "C",
                                     :specVersion 1,
                                     :isEphemeral true}
                                    {:$type :tutorials.notebook/SpecRef$v1,
                                     :workspaceName "my",
                                     :specName "A",
                                     :specVersion 1}
                                    {:$type :tutorials.notebook/SpecRef$v1,
                                     :workspaceName "my",
                                     :specName "A",
                                     :specVersion 3}]}
                           {:$type :tutorials.notebook/Notebook$v1,
                            :name "notebook2",
                            :version 3,
                            :items [{:$type :tutorials.notebook/NewSpec$v1,
                                     :workspaceName "my",
                                     :specName "B",
                                     :specVersion 1}
                                    {:$type :tutorials.notebook/SpecRef$v1,
                                     :workspaceName "my",
                                     :specName "A",
                                     :specVersion 1}
                                    {:$type :tutorials.notebook/SpecRef$v1,
                                     :workspaceName "my",
                                     :specName "B",
                                     :specVersion 1}]}],
               :tests []},
   :notebookName "notebook1",
   :notebookVersion 1}
  :tutorials.notebook/WorkspaceAndEffects$v1)


;-- result --
{:$type :tutorials.notebook/WorkspaceAndEffects$v1,
 :effects [{:$type :tutorials.notebook/WriteSpecEffect$v1,
            :specId {:$type :tutorials.notebook/SpecId$v1,
                     :specName "A",
                     :specVersion 3,
                     :workspaceName "my"}}
           {:$type :tutorials.notebook/WriteNotebookEffect$v1,
            :notebookName "notebook1",
            :notebookVersion 2}
           {:$type :tutorials.notebook/RunTestsEffect$v1,
            :notebookName "notebook1",
            :notebookVersion 1,
            :workspaceSpecs true}],
 :workspace
   {:$type :tutorials.notebook/Workspace$v1,
    :notebooks [{:name "notebook2",
                 :$type :tutorials.notebook/Notebook$v1,
                 :items [{:$type :tutorials.notebook/NewSpec$v1,
                          :specName "B",
                          :specVersion 1,
                          :workspaceName "my"}
                         {:$type :tutorials.notebook/SpecRef$v1,
                          :specName "A",
                          :specVersion 1,
                          :workspaceName "my"}
                         {:$type :tutorials.notebook/SpecRef$v1,
                          :specName "B",
                          :specVersion 1,
                          :workspaceName "my"}],
                 :version 3}
                {:name "notebook1",
                 :$type :tutorials.notebook/Notebook$v1,
                 :items [{:$type :tutorials.notebook/NewSpec$v1,
                          :isEphemeral true,
                          :specName "C",
                          :specVersion 1,
                          :workspaceName "my"}
                         {:$type :tutorials.notebook/SpecRef$v1,
                          :specName "A",
                          :specVersion 1,
                          :workspaceName "my"}
                         {:$type :tutorials.notebook/SpecRef$v1,
                          :specName "A",
                          :specVersion 3,
                          :workspaceName "my"}],
                 :version 2}],
    :registrySpecIds [],
    :specIds [{:$type :tutorials.notebook/SpecId$v1,
               :specName "A",
               :specVersion 1,
               :workspaceName "my"}
              {:$type :tutorials.notebook/SpecId$v1,
               :specName "B",
               :specVersion 1,
               :workspaceName "my"}
              {:$type :tutorials.notebook/SpecId$v1,
               :specName "A",
               :specVersion 2,
               :workspaceName "my"}
              {:$type :tutorials.notebook/SpecId$v1,
               :specName "A",
               :specVersion 3,
               :workspaceName "my"}],
    :tests [],
    :workspaceName "my"}}
```

```clojure
(refine-to {:$type :tutorials.notebook/CreateRegressionTest$v1,
            :workspace {:$type :tutorials.notebook/Workspace$v1,
                        :workspaceName "my",
                        :registrySpecIds [],
                        :specIds [],
                        :notebooks [{:$type :tutorials.notebook/Notebook$v1,
                                     :name "notebook1",
                                     :version 1,
                                     :items []}],
                        :tests []},
            :notebookName "notebook1",
            :notebookVersion 1}
           :tutorials.notebook/WorkspaceAndEffects$v1)


;-- result --
{:$type :tutorials.notebook/WorkspaceAndEffects$v1,
 :effects [{:$type :tutorials.notebook/WriteRegressionTestEffect$v1,
            :notebookName "notebook1",
            :notebookVersion 1}
           {:$type :tutorials.notebook/RunTestsEffect$v1,
            :notebookName "notebook1",
            :notebookVersion 1,
            :registrySpecs true}],
 :workspace {:$type :tutorials.notebook/Workspace$v1,
             :notebooks [{:name "notebook1",
                          :$type :tutorials.notebook/Notebook$v1,
                          :items [],
                          :version 1}],
             :registrySpecIds [],
             :specIds [],
             :tests [{:$type :tutorials.notebook/RegressionTest$v1,
                      :notebookName "notebook1",
                      :notebookVersion 1}],
             :workspaceName "my"}}
```

```clojure
(refine-to {:$type :tutorials.notebook/CreateRegressionTest$v1,
            :workspace {:$type :tutorials.notebook/Workspace$v1,
                        :workspaceName "my",
                        :registrySpecIds [],
                        :specIds [],
                        :notebooks [{:$type :tutorials.notebook/Notebook$v1,
                                     :name "notebook1",
                                     :version 2,
                                     :items []}],
                        :tests [{:$type :tutorials.notebook/RegressionTest$v1,
                                 :notebookName "notebook1",
                                 :notebookVersion 1}]},
            :notebookName "notebook1",
            :notebookVersion 2}
           :tutorials.notebook/WorkspaceAndEffects$v1)


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.notebook/CreateRegressionTest$v1', violates constraints \"tutorials.notebook/Workspace$v1/uniqueTestNames\""
 :h-err/invalid-instance]
```

```clojure
(refine-to {:$type :tutorials.notebook/UpdateRegressionTest$v1,
            :workspace {:$type :tutorials.notebook/Workspace$v1,
                        :workspaceName "my",
                        :registrySpecIds [],
                        :specIds [],
                        :notebooks [{:$type :tutorials.notebook/Notebook$v1,
                                     :name "notebook1",
                                     :version 9,
                                     :items []}],
                        :tests [{:$type :tutorials.notebook/RegressionTest$v1,
                                 :notebookName "notebook1",
                                 :notebookVersion 1}]},
            :notebookName "notebook1",
            :notebookVersion 9,
            :lastNotebookVersion 1}
           :tutorials.notebook/WorkspaceAndEffects$v1)


;-- result --
{:$type :tutorials.notebook/WorkspaceAndEffects$v1,
 :effects [{:$type :tutorials.notebook/WriteRegressionTestEffect$v1,
            :notebookName "notebook1",
            :notebookVersion 9}
           {:$type :tutorials.notebook/RunTestsEffect$v1,
            :notebookName "notebook1",
            :notebookVersion 9,
            :registrySpecs true}],
 :workspace {:$type :tutorials.notebook/Workspace$v1,
             :notebooks [{:name "notebook1",
                          :$type :tutorials.notebook/Notebook$v1,
                          :items [],
                          :version 9}],
             :registrySpecIds [],
             :specIds [],
             :tests [{:$type :tutorials.notebook/RegressionTest$v1,
                      :notebookName "notebook1",
                      :notebookVersion 9}],
             :workspaceName "my"}}
```

```clojure
(refine-to {:$type :tutorials.notebook/DeleteRegressionTest$v1,
            :workspace {:$type :tutorials.notebook/Workspace$v1,
                        :workspaceName "my",
                        :registrySpecIds [],
                        :specIds [],
                        :notebooks [{:$type :tutorials.notebook/Notebook$v1,
                                     :name "notebook1",
                                     :version 9,
                                     :items []}],
                        :tests [{:$type :tutorials.notebook/RegressionTest$v1,
                                 :notebookName "notebook1",
                                 :notebookVersion 9}
                                {:$type :tutorials.notebook/RegressionTest$v1,
                                 :notebookName "notebook2",
                                 :notebookVersion 1}]},
            :notebookName "notebook1",
            :notebookVersion 9}
           :tutorials.notebook/WorkspaceAndEffects$v1)


;-- result --
{:$type :tutorials.notebook/WorkspaceAndEffects$v1,
 :effects [{:$type :tutorials.notebook/DeleteRegressionTestEffect$v1,
            :notebookName "notebook1",
            :notebookVersion 9}],
 :workspace {:$type :tutorials.notebook/Workspace$v1,
             :notebooks [{:name "notebook1",
                          :$type :tutorials.notebook/Notebook$v1,
                          :items [],
                          :version 9}],
             :registrySpecIds [],
             :specIds [],
             :tests [{:$type :tutorials.notebook/RegressionTest$v1,
                      :notebookName "notebook2",
                      :notebookVersion 1}],
             :workspaceName "my"}}
```

