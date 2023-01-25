<!---
  This markdown file was generated. Do not edit.
  -->

## Model a notebook mechanism



Model a notebook

Abstractly a spec ref contains the following fields.

```clojure
{:tutorials.notebook/SpecRef$v1 {:abstract? true,
                                 :fields {:isFloating :Boolean,
                                          :isNew :Boolean,
                                          :specName :String,
                                          :specVersion :Integer}}}
```

A fixed reference contains in itself the specific version of the spec it is referencing. This spec must either already exist in the workpsace or it must have been created earlier in the notebook.

```clojure
{:tutorials.notebook/FixedSpecRef$v1
   {:fields {:specName :String,
             :specVersion :Integer},
    :refines-to {:tutorials.notebook/SpecRef$v1
                   {:name "toSpecRef",
                    :expr '{:$type :tutorials.notebook/SpecRef$v1,
                            :specName specName,
                            :specVersion specVersion,
                            :isFloating false,
                            :isNew false}}}}}
```

A floating reference just points to whatever the latest spec is that is in scope at the time the reference is processed. This might be a spec created earlier in the notebook.

```clojure
{:tutorials.notebook/FloatingSpecRef$v1
   {:fields {:specName :String},
    :refines-to {:tutorials.notebook/SpecRef$v1
                   {:name "toSpecRef",
                    :expr '{:$type :tutorials.notebook/SpecRef$v1,
                            :specName specName,
                            :specVersion 0,
                            :isFloating true,
                            :isNew false}}}}}
```

The creation of a new spec of a given version is treated as a spec reference in the notebook.

```clojure
{:tutorials.notebook/NewSpecRef$v1
   {:fields {:specName :String,
             :specVersion :Integer},
    :refines-to {:tutorials.notebook/SpecRef$v1
                   {:name "toSpecRef",
                    :expr '{:$type :tutorials.notebook/SpecRef$v1,
                            :specName specName,
                            :specVersion specVersion,
                            :isFloating false,
                            :isNew true}}}}}
```

All the spec refs above need to be resolved to fixed spec references for processing. This resolver takes in all of the references that exist in a workspace context, the references from the notebook being processed, and a specific reference from the notebook. Via refinement, it produces a fixed spec reference that should be used instead in the given context.

```clojure
{:tutorials.notebook/SpecRefResolver$v1
   {:fields {:inputSpecRef :tutorials.notebook/SpecRef$v1,
             :notebookSpecRefs [:Vec :tutorials.notebook/SpecRef$v1],
             :workspaceSpecRefs [:Vec :tutorials.notebook/FixedSpecRef$v1]},
    :refines-to
      {:tutorials.notebook/FixedSpecRef$v1
         {:name "toFixedSpecRef",
          :expr
            '(let [inputSpecRef (refine-to inputSpecRef
                                           :tutorials.notebook/SpecRef$v1)
                   new-spec-refs
                     (filter [sr
                              (map [sr notebookSpecRefs]
                                (refine-to sr :tutorials.notebook/SpecRef$v1))]
                       (get sr :isNew))
                   all-spec-refs
                     (concat workspaceSpecRefs
                             (map [sr new-spec-refs]
                               {:$type :tutorials.notebook/FixedSpecRef$v1,
                                :specName (get sr :specName),
                                :specVersion (get sr :specVersion)}))
                   max-version-in-workspace
                     (reduce [a 0]
                       [sr workspaceSpecRefs]
                       (cond (not= (get sr :specName)
                                   (get inputSpecRef :specName))
                               a
                             (> (get sr :specVersion) a) (get sr :specVersion)
                             a))
                   max-version-in-context
                     (reduce [a 0]
                       [sr all-spec-refs]
                       (cond (not= (get sr :specName)
                                   (get inputSpecRef :specName))
                               a
                             (> (get sr :specVersion) a) (get sr :specVersion)
                             a))]
               (cond
                 (get inputSpecRef :isFloating)
                   {:$type :tutorials.notebook/FixedSpecRef$v1,
                    :specName (get inputSpecRef :specName),
                    :specVersion (if (= max-version-in-context 0)
                                   (error (str "floating ref does not resolve: "
                                               (get inputSpecRef :specName)))
                                   max-version-in-context)}
                 (get inputSpecRef :isNew)
                   {:$type :tutorials.notebook/FixedSpecRef$v1,
                    :specName (get inputSpecRef :specName),
                    :specVersion
                      (let [v (reduce [a 0]
                                [sr workspaceSpecRefs]
                                (cond (not= (get sr :specName)
                                            (get inputSpecRef :specName))
                                        a
                                      (> (get sr :specVersion) a)
                                        (get sr :specVersion)
                                      a))
                            in-sequence?
                              (reduce [a max-version-in-workspace]
                                [v
                                 (map [sr
                                       (filter [sr new-spec-refs]
                                         (= (get sr :specName)
                                            (get inputSpecRef :specName)))]
                                   (get sr :specVersion))]
                                (if (= (inc a) v)
                                  v
                                  (error (str "new versions not in sequence: "
                                              (get inputSpecRef :specName)))))]
                        (if (or (and (= v 0)
                                     (not (= 1
                                             (get inputSpecRef :specVersion))))
                                (not (> (get inputSpecRef :specVersion) v)))
                          (error (str "CAS error on new ref: "
                                      (get inputSpecRef :specName)))
                          (get inputSpecRef :specVersion)))}
                 {:$type :tutorials.notebook/FixedSpecRef$v1,
                  :specName (get inputSpecRef :specName),
                  :specVersion
                    (let [found? (reduce [a false]
                                   [sr all-spec-refs]
                                   (or a
                                       (and (= (get sr :specName)
                                               (get inputSpecRef :specName))
                                            (= (get sr :specVersion)
                                               (get inputSpecRef :specVersion)))
                                       a))]
                      (if found?
                        (get inputSpecRef :specVersion)
                        (error (str "fixed ref not resolve: "
                                    (get inputSpecRef :specName)))))}))}}}}
```

Make a spec to hold the result of resolving all spec references in a notebook.

```clojure
{:tutorials.notebook/ResolvedSpecRefs$v1
   {:fields {:specRefs [:Vec :tutorials.notebook/FixedSpecRef$v1]}}}
```

A notebook contains spec references. This is modeled as the results of having parsed the references out of the contents of the notebook.

```clojure
{:tutorials.notebook/Notebook$v1
   {:fields {:name :String,
             :contents :String,
             :specRefs [:Vec :tutorials.notebook/SpecRef$v1],
             :version :Integer},
    :constraints #{'{:name "positiveVersion",
                     :expr (> version 0)}}}}
```

A workspace contains its own spec references. This represents the spec references contained in the registry for this workspace and its registry. In addition, the workspace contains notebooks. The workspace is only valid if all of the notebooks it contains are valid. Via refinement, the workspace can be "queried" to produce all of the resolved spec references from all of the notebooks.

```clojure
{:tutorials.notebook/Workspace$v1
   {:fields {:notebooks [:Vec :tutorials.notebook/Notebook$v1],
             :specRefs [:Vec :tutorials.notebook/FixedSpecRef$v1]},
    :constraints
      #{'{:name "uniqueNotebookNames",
          :expr (= (count (concat #{} (map [n notebooks] (get n :name))))
                   (count notebooks))}
        '{:name "validRefs",
          :expr (every?
                  [n notebooks]
                  (every?
                    [sr (get n :specRefs)]
                    (refines-to? {:$type :tutorials.notebook/SpecRefResolver$v1,
                                  :workspaceSpecRefs specRefs,
                                  :notebookSpecRefs (get n :specRefs),
                                  :inputSpecRef sr}
                                 :tutorials.notebook/FixedSpecRef$v1)))}},
    :refines-to
      {:tutorials.notebook/ResolvedSpecRefs$v1
         {:name "toResolvedSpecRef",
          :expr '{:$type :tutorials.notebook/ResolvedSpecRefs$v1,
                  :specRefs (reduce [a []]
                              [x
                               (map [n notebooks]
                                 (map [sr (get n :specRefs)]
                                   (refine-to
                                     {:$type
                                        :tutorials.notebook/SpecRefResolver$v1,
                                      :workspaceSpecRefs specRefs,
                                      :notebookSpecRefs (get n :specRefs),
                                      :inputSpecRef sr}
                                     :tutorials.notebook/FixedSpecRef$v1)))]
                              (concat a x))}}}}}
```

A simple test that an empty workspace is valid.

```clojure
{:$type :tutorials.notebook/Workspace$v1,
 :notebooks [],
 :specRefs []}
```

A workspace with spec references.

```clojure
{:$type :tutorials.notebook/Workspace$v1,
 :notebooks [],
 :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
             :specName "my/A",
             :specVersion 1}
            {:$type :tutorials.notebook/FixedSpecRef$v1,
             :specName "my/A",
             :specVersion 2}]}
```

A workspace with a notebook that references one of the specs in the workspace.

```clojure
{:$type :tutorials.notebook/Workspace$v1,
 :notebooks [{:name "mine",
              :$type :tutorials.notebook/Notebook$v1,
              :contents "docs",
              :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
                          :specName "my/A",
                          :specVersion 1}],
              :version 1}],
 :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
             :specName "my/A",
             :specVersion 1}
            {:$type :tutorials.notebook/FixedSpecRef$v1,
             :specName "my/A",
             :specVersion 2}]}
```

If a notebook references a non existent spec, then the workspace is not valid with that notebook included.

```clojure
{:$type :tutorials.notebook/Workspace$v1,
 :notebooks [{:name "mine",
              :$type :tutorials.notebook/Notebook$v1,
              :contents "docs",
              :specRefs [{:$type :tutorials.notebook/FloatingSpecRef$v1,
                          :specName "my/B"}
                         {:$type :tutorials.notebook/FixedSpecRef$v1,
                          :specName "my/A",
                          :specVersion 1}],
              :version 1}],
 :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
             :specName "my/A",
             :specVersion 1}
            {:$type :tutorials.notebook/FixedSpecRef$v1,
             :specName "my/A",
             :specVersion 2}]}


;-- result --
[:throws
 "h-err/spec-threw 0-0 : Spec threw error: \"floating ref does not resolve: my/B; h-err/spec-cycle-runtime 0-0 : Loop detected in spec dependencies\""
 :h-err/spec-threw]
```

The same applies if a notebook references a version of a spec that does not exist.

```clojure
{:$type :tutorials.notebook/Workspace$v1,
 :notebooks [{:name "mine",
              :$type :tutorials.notebook/Notebook$v1,
              :contents "docs",
              :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
                          :specName "my/A",
                          :specVersion 3}],
              :version 1}],
 :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
             :specName "my/A",
             :specVersion 1}
            {:$type :tutorials.notebook/FixedSpecRef$v1,
             :specName "my/A",
             :specVersion 2}]}


;-- result --
[:throws
 "h-err/spec-threw 0-0 : Spec threw error: \"fixed ref not resolve: my/A; h-err/spec-cycle-runtime 0-0 : Loop detected in spec dependencies\""
 :h-err/spec-threw]
```

Via refinement, we can see the result of resolving all spec references in all notebooks in a workspace. This shows a floating reference being resolved.

```clojure
(refine-to
  {:$type :tutorials.notebook/Workspace$v1,
   :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
               :specName "my/A",
               :specVersion 3}
              {:$type :tutorials.notebook/FixedSpecRef$v1,
               :specName "my/A",
               :specVersion 1}],
   :notebooks [{:$type :tutorials.notebook/Notebook$v1,
                :name "mine",
                :version 1,
                :contents "docs",
                :specRefs [{:$type :tutorials.notebook/FloatingSpecRef$v1,
                            :specName "my/A"}]}]}
  :tutorials.notebook/ResolvedSpecRefs$v1)


;-- result --
{:$type :tutorials.notebook/ResolvedSpecRefs$v1,
 :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
             :specName "my/A",
             :specVersion 3}]}
```

This shows a fixed reference being resolved.

```clojure
(refine-to {:$type :tutorials.notebook/Workspace$v1,
            :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
                        :specName "my/A",
                        :specVersion 3}
                       {:$type :tutorials.notebook/FixedSpecRef$v1,
                        :specName "my/A",
                        :specVersion 1}],
            :notebooks [{:$type :tutorials.notebook/Notebook$v1,
                         :name "mine",
                         :version 1,
                         :contents "docs",
                         :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
                                     :specName "my/A",
                                     :specVersion 3}]}]}
           :tutorials.notebook/ResolvedSpecRefs$v1)


;-- result --
{:$type :tutorials.notebook/ResolvedSpecRefs$v1,
 :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
             :specName "my/A",
             :specVersion 3}]}
```

A new spec can be created that does not yet exist in the workspace.

```clojure
(refine-to {:$type :tutorials.notebook/Workspace$v1,
            :specRefs [],
            :notebooks [{:$type :tutorials.notebook/Notebook$v1,
                         :name "mine",
                         :version 1,
                         :contents "docs",
                         :specRefs [{:$type :tutorials.notebook/NewSpecRef$v1,
                                     :specName "my/A",
                                     :specVersion 1}]}]}
           :tutorials.notebook/ResolvedSpecRefs$v1)


;-- result --
{:$type :tutorials.notebook/ResolvedSpecRefs$v1,
 :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
             :specName "my/A",
             :specVersion 1}]}
```

If a spec with a given name already exists, then a new spec must use the next incremental version of the spec.

```clojure
(refine-to {:$type :tutorials.notebook/Workspace$v1,
            :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
                        :specName "my/A",
                        :specVersion 1}],
            :notebooks [{:$type :tutorials.notebook/Notebook$v1,
                         :name "mine",
                         :version 1,
                         :contents "docs",
                         :specRefs [{:$type :tutorials.notebook/NewSpecRef$v1,
                                     :specName "my/A",
                                     :specVersion 2}]}]}
           :tutorials.notebook/ResolvedSpecRefs$v1)


;-- result --
{:$type :tutorials.notebook/ResolvedSpecRefs$v1,
 :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
             :specName "my/A",
             :specVersion 2}]}
```

If the versions do not match then it is a CAS error.

```clojure
(refine-to {:$type :tutorials.notebook/Workspace$v1,
            :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
                        :specName "my/A",
                        :specVersion 1}],
            :notebooks [{:$type :tutorials.notebook/Notebook$v1,
                         :name "mine",
                         :version 1,
                         :contents "docs",
                         :specRefs [{:$type :tutorials.notebook/NewSpecRef$v1,
                                     :specName "my/A",
                                     :specVersion 1}]}]}
           :tutorials.notebook/ResolvedSpecRefs$v1)


;-- result --
[:throws
 "h-err/spec-threw 0-0 : Spec threw error: \"new versions not in sequence: my/A; h-err/spec-cycle-runtime 0-0 : Loop detected in spec dependencies\""
 :h-err/spec-threw]
```

The same applies to creating new versions of a spec.

```clojure
(refine-to {:$type :tutorials.notebook/Workspace$v1,
            :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
                        :specName "my/A",
                        :specVersion 3}
                       {:$type :tutorials.notebook/FixedSpecRef$v1,
                        :specName "my/A",
                        :specVersion 1}],
            :notebooks [{:$type :tutorials.notebook/Notebook$v1,
                         :name "mine",
                         :version 1,
                         :contents "docs",
                         :specRefs [{:$type :tutorials.notebook/NewSpecRef$v1,
                                     :specName "my/A",
                                     :specVersion 3}]}]}
           :tutorials.notebook/ResolvedSpecRefs$v1)


;-- result --
[:throws
 "h-err/spec-threw 0-0 : Spec threw error: \"new versions not in sequence: my/A; h-err/spec-cycle-runtime 0-0 : Loop detected in spec dependencies\""
 :h-err/spec-threw]
```

This shows both a new reference and a fixed reference to that new spec being resolved.

```clojure
(refine-to {:$type :tutorials.notebook/Workspace$v1,
            :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
                        :specName "my/A",
                        :specVersion 3}
                       {:$type :tutorials.notebook/FixedSpecRef$v1,
                        :specName "my/A",
                        :specVersion 1}],
            :notebooks [{:$type :tutorials.notebook/Notebook$v1,
                         :name "mine",
                         :version 1,
                         :contents "docs",
                         :specRefs [{:$type :tutorials.notebook/NewSpecRef$v1,
                                     :specName "my/A",
                                     :specVersion 4}
                                    {:$type :tutorials.notebook/FixedSpecRef$v1,
                                     :specName "my/A",
                                     :specVersion 4}]}]}
           :tutorials.notebook/ResolvedSpecRefs$v1)


;-- result --
{:$type :tutorials.notebook/ResolvedSpecRefs$v1,
 :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
             :specName "my/A",
             :specVersion 4}
            {:$type :tutorials.notebook/FixedSpecRef$v1,
             :specName "my/A",
             :specVersion 4}]}
```

A floating reference can point to a new spec created in the notebook.

```clojure
(refine-to
  {:$type :tutorials.notebook/Workspace$v1,
   :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
               :specName "my/A",
               :specVersion 3}
              {:$type :tutorials.notebook/FixedSpecRef$v1,
               :specName "my/A",
               :specVersion 1}],
   :notebooks [{:$type :tutorials.notebook/Notebook$v1,
                :name "mine",
                :version 1,
                :contents "docs",
                :specRefs [{:$type :tutorials.notebook/NewSpecRef$v1,
                            :specName "my/A",
                            :specVersion 4}
                           {:$type :tutorials.notebook/FloatingSpecRef$v1,
                            :specName "my/A"}]}]}
  :tutorials.notebook/ResolvedSpecRefs$v1)


;-- result --
{:$type :tutorials.notebook/ResolvedSpecRefs$v1,
 :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
             :specName "my/A",
             :specVersion 4}
            {:$type :tutorials.notebook/FixedSpecRef$v1,
             :specName "my/A",
             :specVersion 4}]}
```

Demonstration that invalid fixed references are detected in the presence of new spec references.

```clojure
(refine-to {:$type :tutorials.notebook/Workspace$v1,
            :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
                        :specName "my/A",
                        :specVersion 3}
                       {:$type :tutorials.notebook/FixedSpecRef$v1,
                        :specName "my/A",
                        :specVersion 1}],
            :notebooks [{:$type :tutorials.notebook/Notebook$v1,
                         :name "mine",
                         :version 1,
                         :contents "docs",
                         :specRefs [{:$type :tutorials.notebook/NewSpecRef$v1,
                                     :specName "my/A",
                                     :specVersion 4}
                                    {:$type :tutorials.notebook/FixedSpecRef$v1,
                                     :specName "my/A",
                                     :specVersion 6}]}]}
           :tutorials.notebook/ResolvedSpecRefs$v1)


;-- result --
[:throws
 "h-err/spec-threw 0-0 : Spec threw error: \"fixed ref not resolve: my/A; h-err/spec-cycle-runtime 0-0 : Loop detected in spec dependencies\""
 :h-err/spec-threw]
```

Multiple new versions of a spec can be created in a notebook and each of those versions can be referenced via fixed references.

```clojure
(refine-to {:$type :tutorials.notebook/Workspace$v1,
            :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
                        :specName "my/A",
                        :specVersion 3}
                       {:$type :tutorials.notebook/FixedSpecRef$v1,
                        :specName "my/A",
                        :specVersion 1}],
            :notebooks [{:$type :tutorials.notebook/Notebook$v1,
                         :name "mine",
                         :version 1,
                         :contents "docs",
                         :specRefs [{:$type :tutorials.notebook/NewSpecRef$v1,
                                     :specName "my/A",
                                     :specVersion 4}
                                    {:$type :tutorials.notebook/NewSpecRef$v1,
                                     :specName "my/A",
                                     :specVersion 5}
                                    {:$type :tutorials.notebook/FixedSpecRef$v1,
                                     :specName "my/A",
                                     :specVersion 4}
                                    {:$type :tutorials.notebook/FixedSpecRef$v1,
                                     :specName "my/A",
                                     :specVersion 5}]}]}
           :tutorials.notebook/ResolvedSpecRefs$v1)


;-- result --
{:$type :tutorials.notebook/ResolvedSpecRefs$v1,
 :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
             :specName "my/A",
             :specVersion 4}
            {:$type :tutorials.notebook/FixedSpecRef$v1,
             :specName "my/A",
             :specVersion 5}
            {:$type :tutorials.notebook/FixedSpecRef$v1,
             :specName "my/A",
             :specVersion 4}
            {:$type :tutorials.notebook/FixedSpecRef$v1,
             :specName "my/A",
             :specVersion 5}]}
```

Each of the new versions must match the incremental numbering scheme.

```clojure
(refine-to {:$type :tutorials.notebook/Workspace$v1,
            :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
                        :specName "my/A",
                        :specVersion 3}
                       {:$type :tutorials.notebook/FixedSpecRef$v1,
                        :specName "my/A",
                        :specVersion 1}],
            :notebooks [{:$type :tutorials.notebook/Notebook$v1,
                         :name "mine",
                         :version 1,
                         :contents "docs",
                         :specRefs [{:$type :tutorials.notebook/NewSpecRef$v1,
                                     :specName "my/A",
                                     :specVersion 3}
                                    {:$type :tutorials.notebook/NewSpecRef$v1,
                                     :specName "my/A",
                                     :specVersion 4}]}]}
           :tutorials.notebook/ResolvedSpecRefs$v1)


;-- result --
[:throws
 "h-err/spec-threw 0-0 : Spec threw error: \"new versions not in sequence: my/A; h-err/spec-cycle-runtime 0-0 : Loop detected in spec dependencies\""
 :h-err/spec-threw]
```

```clojure
(refine-to {:$type :tutorials.notebook/Workspace$v1,
            :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
                        :specName "my/A",
                        :specVersion 3}
                       {:$type :tutorials.notebook/FixedSpecRef$v1,
                        :specName "my/A",
                        :specVersion 1}],
            :notebooks [{:$type :tutorials.notebook/Notebook$v1,
                         :name "mine",
                         :version 1,
                         :contents "docs",
                         :specRefs [{:$type :tutorials.notebook/NewSpecRef$v1,
                                     :specName "my/A",
                                     :specVersion 4}
                                    {:$type :tutorials.notebook/NewSpecRef$v1,
                                     :specName "my/A",
                                     :specVersion 6}]}]}
           :tutorials.notebook/ResolvedSpecRefs$v1)


;-- result --
[:throws
 "h-err/spec-threw 0-0 : Spec threw error: \"new versions not in sequence: my/A; h-err/spec-cycle-runtime 0-0 : Loop detected in spec dependencies\""
 :h-err/spec-threw]
```

One can imagine using the model described above to assess whether a new candidate notebook is valid. i.e. a workspace instance can be created, and the new notebook can be added to it as its sole notebook. If the resulting workspace instance is valid and the tests in the new notebook pass, then the notebook is valid.

Once a notebook has been identified as valid, then it can be "applied" to a workspace. This has the effect of creating any new specs indicated by the notebook.

A second use case is to consider whether some candidate workspace changes are valid in light of a workspace and its notebooks. In this scenario a workspace instance is created which reflects the proposed changes to the workspace. This workspace instance needs to include all of the notebooks that have been registered as regression tests. If the resulting workspace instance is valid and all of the tests in the notebooks pass, then the proposed changes can be made without violating the regression tests. When using a notebook in this mode, the new spec references that existed in the notebook when it was "applied" can be ignored.

A regression test contains the information from a specific point-in-time of a notebook.

```clojure
{:tutorials.notebook/RegressionTest$v1
   {:fields {:contents :String,
             :notebookName :String,
             :notebookVersion :Integer,
             :specRefs [:Vec :tutorials.notebook/SpecRef$v1]},
    :constraints
      #{'{:name "noNewReferences",
          :expr (let [new-spec-refs
                        (filter [sr
                                 (map [sr specRefs]
                                   (refine-to sr
                                              :tutorials.notebook/SpecRef$v1))]
                          (get sr :isNew))]
                  (= 0 (count new-spec-refs)))}
        '{:name "positiveVersion",
          :expr (> notebookVersion 0)}}}}
```

This spec captures the rules governing how notebooks are used to create regression tests.

```clojure
{:tutorials.notebook/RegressionTestMaker$v1
   {:fields {:notebook :tutorials.notebook/Notebook$v1,
             :notebookVersion :Integer},
    :refines-to
      {:tutorials.notebook/RegressionTest$v1
         {:name "makeTest",
          :expr '{:$type :tutorials.notebook/RegressionTest$v1,
                  :notebookName (get notebook :name),
                  :notebookVersion (get notebook :version),
                  :contents (get notebook :contents),
                  :specRefs
                    (let [spec-refs (get notebook :specRefs)
                          new-spec-refs (filter
                                          [sr
                                           (map [sr spec-refs]
                                             (refine-to
                                               sr
                                               :tutorials.notebook/SpecRef$v1))]
                                          (get sr :isNew))]
                      (if (= notebookVersion (get notebook :version))
                        spec-refs
                        (error "CAS error creating regression test")))}}}}}
```

A trivial example of creating a regression test.

```clojure
(refine-to {:$type :tutorials.notebook/RegressionTestMaker$v1,
            :notebook {:$type :tutorials.notebook/Notebook$v1,
                       :name "mine",
                       :version 1,
                       :contents "docs",
                       :specRefs []},
            :notebookVersion 1}
           :tutorials.notebook/RegressionTest$v1)


;-- result --
{:$type :tutorials.notebook/RegressionTest$v1,
 :contents "docs",
 :notebookName "mine",
 :notebookVersion 1,
 :specRefs []}
```

The contents and refs of the specific version of the notebook become the contents of the regression test.

```clojure
(refine-to
  {:$type :tutorials.notebook/RegressionTestMaker$v1,
   :notebook {:$type :tutorials.notebook/Notebook$v1,
              :name "mine",
              :version 1,
              :contents "docs",
              :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
                          :specName "my/A",
                          :specVersion 3}
                         {:$type :tutorials.notebook/FloatingSpecRef$v1,
                          :specName "my/A"}]},
   :notebookVersion 1}
  :tutorials.notebook/RegressionTest$v1)


;-- result --
{:$type :tutorials.notebook/RegressionTest$v1,
 :contents "docs",
 :notebookName "mine",
 :notebookVersion 1,
 :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
             :specName "my/A",
             :specVersion 3}
            {:$type :tutorials.notebook/FloatingSpecRef$v1,
             :specName "my/A"}]}
```

However, the notebook cannot contain new spec references.

```clojure
{:$type :tutorials.notebook/RegressionTestMaker$v1,
 :notebook {:name "mine",
            :$type :tutorials.notebook/Notebook$v1,
            :contents "docs",
            :specRefs [{:$type :tutorials.notebook/NewSpecRef$v1,
                        :specName "my/A",
                        :specVersion 3}],
            :version 1},
 :notebookVersion 1}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.notebook/RegressionTestMaker$v1', violates constraints \"tutorials.notebook/RegressionTest$v1/noNewReferences\""
 :h-err/invalid-instance]
```

Also if the notebook has been updated, then the creation of the regression test fails. This allows users to detect when notebooks are changed underneath them.

```clojure
{:$type :tutorials.notebook/RegressionTestMaker$v1,
 :notebook {:name "mine",
            :$type :tutorials.notebook/Notebook$v1,
            :contents "docs",
            :specRefs [],
            :version 2},
 :notebookVersion 1}


;-- result --
[:throws
 "h-err/spec-threw 0-0 : Spec threw error: \"CAS error creating regression test\""
 :h-err/spec-threw]
```

Workspaces can now be extended to include their regression tests.

```clojure
{:tutorials.notebook/Workspace$v2
   {:fields {:notebooks [:Vec :tutorials.notebook/Notebook$v1],
             :regressionTests [:Vec :tutorials.notebook/RegressionTest$v1],
             :specRefs [:Vec :tutorials.notebook/FixedSpecRef$v1]},
    :constraints
      #{'{:name "uniqueNotebookNames",
          :expr (= (count (concat #{} (map [n notebooks] (get n :name))))
                   (count notebooks))}
        '{:name "uniqueRegressionTestNotebookNames",
          :expr (= (count (concat #{}
                                  (map [t regressionTests]
                                    (get t :notebookName))))
                   (count regressionTests))}
        '{:name "validRefs",
          :expr (every?
                  [n notebooks]
                  (every?
                    [sr (get n :specRefs)]
                    (refines-to? {:$type :tutorials.notebook/SpecRefResolver$v1,
                                  :workspaceSpecRefs specRefs,
                                  :notebookSpecRefs (get n :specRefs),
                                  :inputSpecRef sr}
                                 :tutorials.notebook/FixedSpecRef$v1)))}},
    :refines-to
      {:tutorials.notebook/ResolvedSpecRefs$v1
         {:name "toResolvedSpecRef",
          :expr '{:$type :tutorials.notebook/ResolvedSpecRefs$v1,
                  :specRefs (reduce [a []]
                              [x
                               (map [n notebooks]
                                 (map [sr (get n :specRefs)]
                                   (refine-to
                                     {:$type
                                        :tutorials.notebook/SpecRefResolver$v1,
                                      :workspaceSpecRefs specRefs,
                                      :notebookSpecRefs (get n :specRefs),
                                      :inputSpecRef sr}
                                     :tutorials.notebook/FixedSpecRef$v1)))]
                              (concat a x))}}}}}
```

A workspace can include a regression test.

```clojure
{:$type :tutorials.notebook/Workspace$v2,
 :notebooks [{:name "mine",
              :$type :tutorials.notebook/Notebook$v1,
              :contents "docs",
              :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
                          :specName "my/A",
                          :specVersion 1}],
              :version 1}],
 :regressionTests [{:$type :tutorials.notebook/RegressionTest$v1,
                    :contents "docs",
                    :notebookName "mine",
                    :notebookVersion 1,
                    :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
                                :specName "my/A",
                                :specVersion 1}]}],
 :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
             :specName "my/A",
             :specVersion 1}]}
```

The notebook used as regression test can continue to be changed without affecting the regression tests.

```clojure
{:$type :tutorials.notebook/Workspace$v2,
 :notebooks [{:name "mine",
              :$type :tutorials.notebook/Notebook$v1,
              :contents "docs",
              :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
                          :specName "my/A",
                          :specVersion 1}],
              :version 2}],
 :regressionTests [{:$type :tutorials.notebook/RegressionTest$v1,
                    :contents "docs",
                    :notebookName "mine",
                    :notebookVersion 1,
                    :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
                                :specName "my/A",
                                :specVersion 1}]}],
 :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
             :specName "my/A",
             :specVersion 1}]}
```

However, only a single version of a given notebook can be a regression test at any point in time.

```clojure
{:$type :tutorials.notebook/Workspace$v2,
 :notebooks [{:name "mine",
              :$type :tutorials.notebook/Notebook$v1,
              :contents "docs",
              :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
                          :specName "my/A",
                          :specVersion 1}],
              :version 2}],
 :regressionTests [{:$type :tutorials.notebook/RegressionTest$v1,
                    :contents "docs",
                    :notebookName "mine",
                    :notebookVersion 1,
                    :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
                                :specName "my/A",
                                :specVersion 1}]}
                   {:$type :tutorials.notebook/RegressionTest$v1,
                    :contents "docs",
                    :notebookName "mine",
                    :notebookVersion 2,
                    :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
                                :specName "my/A",
                                :specVersion 1}]}],
 :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1,
             :specName "my/A",
             :specVersion 1}]}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.notebook/Workspace$v2', violates constraints \"tutorials.notebook/Workspace$v2/uniqueRegressionTestNotebookNames\""
 :h-err/invalid-instance]
```

Validating a notebook in the context of a registry is similar to validating a notebook in the context of the workspace. The difference lies in the fact that different references are in scope, specifically specs that exist in the local workspace but which are not registered locally are omitted from the context. In addition, the notebooks at this stage can only contain fixed and floating spec references, i.e. they cannot contain new spec references.

