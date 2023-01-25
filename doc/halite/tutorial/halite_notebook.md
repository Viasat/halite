<!---
  This markdown file was generated. Do not edit.
  -->

## Model a notebook mechanism



```clojure
{:tutorials.notebook/SpecId$v1
   {:fields {:specName :String,
             :specVersion :Integer},
    :constraints #{'{:name "positiveVersion",
                     :expr (valid? {:$type :tutorials.notebook/Version$v1,
                                    :version specVersion})}}},
 :tutorials.notebook/SpecRef$v1
   {:fields {:specName :String,
             :specVersion [:Maybe :Integer]},
    :constraints #{'{:name "positiveVersion",
                     :expr (valid? {:$type :tutorials.notebook/Version$v1,
                                    :version specVersion})}},
    :refines-to {:tutorials.notebook/SpecId$v1
                   {:name "specId",
                    :expr '(when-value specVersion
                                       {:$type :tutorials.notebook/SpecId$v1,
                                        :specName specName,
                                        :specVersion specVersion})}}},
 :tutorials.notebook/Version$v1
   {:fields {:version [:Maybe :Integer]},
    :constraints #{'{:name "positiveVersion",
                     :expr (if-value version (> version 0) true)}}}}
```

```clojure
{:$type :tutorials.notebook/SpecRef$v1,
 :specName "my/A",
 :specVersion 1}
```

```clojure
{:$type :tutorials.notebook/SpecRef$v1,
 :specName "my/A"}
```

```clojure
{:tutorials.notebook/NewSpec$v1
   {:fields {:isEphemeral [:Maybe :Boolean],
             :specName :String,
             :specVersion :Integer},
    :constraints #{'{:name "ephemeralFlag",
                     :expr (if-value isEphemeral isEphemeral true)}
                   '{:name "positiveVersion",
                     :expr (valid? {:$type :tutorials.notebook/Version$v1,
                                    :version specVersion})}},
    :refines-to {:tutorials.notebook/SpecId$v1
                   {:name "specId",
                    :expr '{:$type :tutorials.notebook/SpecId$v1,
                            :specName specName,
                            :specVersion specVersion}}}}}
```

```clojure
{:$type :tutorials.notebook/NewSpec$v1,
 :specName "my/A",
 :specVersion 1}
```

```clojure
{:$type :tutorials.notebook/NewSpec$v1,
 :specName "my/A",
 :specVersion 0}


;-- result --
[:throws
 "h-err/spec-threw 0-0 : Spec threw error: \"h-err/spec-cycle-runtime 0-0 : Loop detected in spec dependencies\""
 :h-err/spec-threw]
```

```clojure
{:$type :tutorials.notebook/NewSpec$v1,
 :isEphemeral true,
 :specName "my/A",
 :specVersion 1}
```

```clojure
{:$type :tutorials.notebook/NewSpec$v1,
 :isEphemeral false,
 :specName "my/A",
 :specVersion 1}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.notebook/NewSpec$v1', violates constraints \"tutorials.notebook/NewSpec$v1/ephemeralFlag\""
 :h-err/invalid-instance]
```

```clojure
(refine-to {:$type :tutorials.notebook/NewSpec$v1,
            :specName "my/A",
            :specVersion 1}
           :tutorials.notebook/SpecId$v1)


;-- result --
{:$type :tutorials.notebook/SpecId$v1,
 :specName "my/A",
 :specVersion 1}
```

```clojure
{:tutorials.notebook/FMaxSpecVersion$v1
   {:fields {:specIds [:Vec :tutorials.notebook/SpecId$v1],
             :specName :String},
    :refines-to
      {:tutorials.notebook/RInteger$v1
         {:name "result",
          :expr '{:$type :tutorials.notebook/RInteger$v1,
                  :result (let [result
                                  (reduce [a 0]
                                    [si specIds]
                                    (cond (not= (get si :specName) specName) a
                                          (> (get si :specVersion) a)
                                            (get si :specVersion)
                                          a))]
                            (when (not= 0 result) result))}}}},
 :tutorials.notebook/RInteger$v1 {:fields {:result [:Maybe :Integer]}}}
```

```clojure
(refine-to {:$type :tutorials.notebook/FMaxSpecVersion$v1,
            :specIds [{:$type :tutorials.notebook/SpecId$v1,
                       :specName "my/A",
                       :specVersion 1}
                      {:$type :tutorials.notebook/SpecId$v1,
                       :specName "my/B",
                       :specVersion 1}
                      {:$type :tutorials.notebook/SpecId$v1,
                       :specName "my/A",
                       :specVersion 2}],
            :specName "my/A"}
           :tutorials.notebook/RInteger$v1)


;-- result --
{:$type :tutorials.notebook/RInteger$v1,
 :result 2}
```

```clojure
(refine-to {:$type :tutorials.notebook/FMaxSpecVersion$v1,
            :specIds [{:$type :tutorials.notebook/SpecId$v1,
                       :specName "my/A",
                       :specVersion 1}
                      {:$type :tutorials.notebook/SpecId$v1,
                       :specName "my/B",
                       :specVersion 1}
                      {:$type :tutorials.notebook/SpecId$v1,
                       :specName "my/A",
                       :specVersion 2}],
            :specName "my/B"}
           :tutorials.notebook/RInteger$v1)


;-- result --
{:$type :tutorials.notebook/RInteger$v1,
 :result 1}
```

```clojure
(refine-to {:$type :tutorials.notebook/FMaxSpecVersion$v1,
            :specIds [{:$type :tutorials.notebook/SpecId$v1,
                       :specName "my/A",
                       :specVersion 1}
                      {:$type :tutorials.notebook/SpecId$v1,
                       :specName "my/B",
                       :specVersion 1}
                      {:$type :tutorials.notebook/SpecId$v1,
                       :specName "my/A",
                       :specVersion 2}],
            :specName "my/C"}
           :tutorials.notebook/RInteger$v1)


;-- result --
{:$type :tutorials.notebook/RInteger$v1}
```

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
                   spec-name (get inputSpecRef :specVersion)]
               (if-value
                 spec-name
                 (let [result (refine-to inputSpecRef
                                         :tutorials.notebook/SpecId$v1)]
                   (if (contains? (concat #{} all-spec-ids) result)
                     result
                     (error (str "fixed ref does not resolve: "
                                 (get inputSpecRef :specName)))))
                 {:$type :tutorials.notebook/SpecId$v1,
                  :specName (get inputSpecRef :specName),
                  :specVersion
                    (let [max-version-in-context
                            (get
                              (refine-to
                                {:$type :tutorials.notebook/FMaxSpecVersion$v1,
                                 :specIds all-spec-ids,
                                 :specName (get inputSpecRef :specName)}
                                :tutorials.notebook/RInteger$v1)
                              :result)]
                      (if-value max-version-in-context
                                max-version-in-context
                                (error (str "floating ref does not resolve: "
                                            (get inputSpecRef
                                                 :specName)))))}))}}}}
```

```clojure
(refine-to {:$type :tutorials.notebook/SpecRefResolver$v1,
            :existingSpecIds [],
            :newSpecs [],
            :inputSpecRef {:$type :tutorials.notebook/SpecRef$v1,
                           :specName "my/A",
                           :specVersion 1}}
           :tutorials.notebook/SpecId$v1)


;-- result --
[:throws
 "h-err/spec-threw 0-0 : Spec threw error: \"fixed ref does not resolve: my/A\""
 :h-err/spec-threw]
```

```clojure
(refine-to {:$type :tutorials.notebook/SpecRefResolver$v1,
            :existingSpecIds [{:$type :tutorials.notebook/SpecId$v1,
                               :specName "my/A",
                               :specVersion 1}
                              {:$type :tutorials.notebook/SpecId$v1,
                               :specName "my/B",
                               :specVersion 1}
                              {:$type :tutorials.notebook/SpecId$v1,
                               :specName "my/A",
                               :specVersion 2}],
            :newSpecs [{:$type :tutorials.notebook/NewSpec$v1,
                        :specName "my/C",
                        :specVersion 1,
                        :isEphemeral true}
                       {:$type :tutorials.notebook/NewSpec$v1,
                        :specName "my/A",
                        :specVersion 3}],
            :inputSpecRef {:$type :tutorials.notebook/SpecRef$v1,
                           :specName "my/A"}}
           :tutorials.notebook/SpecId$v1)


;-- result --
{:$type :tutorials.notebook/SpecId$v1,
 :specName "my/A",
 :specVersion 3}
```

```clojure
(refine-to {:$type :tutorials.notebook/SpecRefResolver$v1,
            :existingSpecIds [{:$type :tutorials.notebook/SpecId$v1,
                               :specName "my/A",
                               :specVersion 1}
                              {:$type :tutorials.notebook/SpecId$v1,
                               :specName "my/B",
                               :specVersion 1}
                              {:$type :tutorials.notebook/SpecId$v1,
                               :specName "my/A",
                               :specVersion 2}],
            :newSpecs [{:$type :tutorials.notebook/NewSpec$v1,
                        :specName "my/C",
                        :specVersion 1,
                        :isEphemeral true}
                       {:$type :tutorials.notebook/NewSpec$v1,
                        :specName "my/A",
                        :specVersion 3}],
            :inputSpecRef {:$type :tutorials.notebook/SpecRef$v1,
                           :specName "my/B"}}
           :tutorials.notebook/SpecId$v1)


;-- result --
{:$type :tutorials.notebook/SpecId$v1,
 :specName "my/B",
 :specVersion 1}
```

```clojure
(refine-to {:$type :tutorials.notebook/SpecRefResolver$v1,
            :existingSpecIds [{:$type :tutorials.notebook/SpecId$v1,
                               :specName "my/A",
                               :specVersion 1}
                              {:$type :tutorials.notebook/SpecId$v1,
                               :specName "my/B",
                               :specVersion 1}
                              {:$type :tutorials.notebook/SpecId$v1,
                               :specName "my/A",
                               :specVersion 2}],
            :newSpecs [{:$type :tutorials.notebook/NewSpec$v1,
                        :specName "my/C",
                        :specVersion 1,
                        :isEphemeral true}
                       {:$type :tutorials.notebook/NewSpec$v1,
                        :specName "my/A",
                        :specVersion 3}],
            :inputSpecRef {:$type :tutorials.notebook/SpecRef$v1,
                           :specName "my/B",
                           :specVersion 1}}
           :tutorials.notebook/SpecId$v1)


;-- result --
{:$type :tutorials.notebook/SpecId$v1,
 :specName "my/B",
 :specVersion 1}
```

```clojure
(refine-to {:$type :tutorials.notebook/SpecRefResolver$v1,
            :existingSpecIds [{:$type :tutorials.notebook/SpecId$v1,
                               :specName "my/A",
                               :specVersion 1}
                              {:$type :tutorials.notebook/SpecId$v1,
                               :specName "my/B",
                               :specVersion 1}
                              {:$type :tutorials.notebook/SpecId$v1,
                               :specName "my/A",
                               :specVersion 2}],
            :newSpecs [{:$type :tutorials.notebook/NewSpec$v1,
                        :specName "my/C",
                        :specVersion 1,
                        :isEphemeral true}
                       {:$type :tutorials.notebook/NewSpec$v1,
                        :specName "my/A",
                        :specVersion 3}],
            :inputSpecRef {:$type :tutorials.notebook/SpecRef$v1,
                           :specName "my/C",
                           :specVersion 1}}
           :tutorials.notebook/SpecId$v1)


;-- result --
{:$type :tutorials.notebook/SpecId$v1,
 :specName "my/C",
 :specVersion 1}
```

```clojure
(refine-to {:$type :tutorials.notebook/SpecRefResolver$v1,
            :existingSpecIds [{:$type :tutorials.notebook/SpecId$v1,
                               :specName "my/A",
                               :specVersion 1}
                              {:$type :tutorials.notebook/SpecId$v1,
                               :specName "my/B",
                               :specVersion 1}
                              {:$type :tutorials.notebook/SpecId$v1,
                               :specName "my/A",
                               :specVersion 2}],
            :newSpecs [{:$type :tutorials.notebook/NewSpec$v1,
                        :specName "my/C",
                        :specVersion 1,
                        :isEphemeral true}
                       {:$type :tutorials.notebook/NewSpec$v1,
                        :specName "my/A",
                        :specVersion 3}],
            :inputSpecRef {:$type :tutorials.notebook/SpecRef$v1,
                           :specName "my/C",
                           :specVersion 2}}
           :tutorials.notebook/SpecId$v1)


;-- result --
[:throws
 "h-err/spec-threw 0-0 : Spec threw error: \"fixed ref does not resolve: my/C\""
 :h-err/spec-threw]
```

```clojure
(refine-to {:$type :tutorials.notebook/SpecRefResolver$v1,
            :existingSpecIds [{:$type :tutorials.notebook/SpecId$v1,
                               :specName "my/A",
                               :specVersion 1}
                              {:$type :tutorials.notebook/SpecId$v1,
                               :specName "my/B",
                               :specVersion 1}
                              {:$type :tutorials.notebook/SpecId$v1,
                               :specName "my/A",
                               :specVersion 2}],
            :newSpecs [{:$type :tutorials.notebook/NewSpec$v1,
                        :specName "my/C",
                        :specVersion 1,
                        :isEphemeral true}
                       {:$type :tutorials.notebook/NewSpec$v1,
                        :specName "my/A",
                        :specVersion 3}],
            :inputSpecRef {:$type :tutorials.notebook/SpecRef$v1,
                           :specName "my/X",
                           :specVersion 1}}
           :tutorials.notebook/SpecId$v1)


;-- result --
[:throws
 "h-err/spec-threw 0-0 : Spec threw error: \"fixed ref does not resolve: my/X\""
 :h-err/spec-threw]
```

Make a spec to hold the result of resolving all spec references in a notebook.

```clojure
{:tutorials.notebook/ResolvedSpecRefs$v1
   {:fields {:specRefs [:Vec :tutorials.notebook/SpecId$v1]}}}
```

A notebook contains spec references. This is modeled as the results of having parsed the references out of the contents of the notebook.

```clojure
{:tutorials.notebook/Notebook$v1
   {:fields {:name :String,
             :contents :String,
             :newSpecs [:Vec :tutorials.notebook/NewSpec$v1],
             :specRefs [:Vec :tutorials.notebook/SpecRef$v1],
             :version :Integer},
    :constraints #{'{:name "positiveVersion",
                     :expr (valid? {:$type :tutorials.notebook/Version$v1,
                                    :version version})}}},
 :tutorials.notebook/Workspace$v1
   {:fields {:notebooks [:Vec :tutorials.notebook/Notebook$v1],
             :specIds [:Vec :tutorials.notebook/SpecId$v1]},
    :constraints #{'{:name "uniqueNotebookNames",
                     :expr (= (count (concat #{}
                                             (map [n notebooks] (get n :name))))
                              (count notebooks))}
                   '{:name "uniqueSpecIds",
                     :expr (= (count (concat #{} specIds)) (count specIds))}}}}
```

```clojure
{:$type :tutorials.notebook/Workspace$v1,
 :notebooks [{:name "notebook1",
              :$type :tutorials.notebook/Notebook$v1,
              :contents "contents1",
              :newSpecs [],
              :specRefs [],
              :version 1}],
 :specIds [{:$type :tutorials.notebook/SpecId$v1,
            :specName "my/A",
            :specVersion 1}
           {:$type :tutorials.notebook/SpecId$v1,
            :specName "my/B",
            :specVersion 1}
           {:$type :tutorials.notebook/SpecId$v1,
            :specName "my/A",
            :specVersion 2}]}
```

```clojure
{:$type :tutorials.notebook/Workspace$v1,
 :notebooks [{:name "notebook1",
              :$type :tutorials.notebook/Notebook$v1,
              :contents "contents1",
              :newSpecs [],
              :specRefs [],
              :version 1}],
 :specIds [{:$type :tutorials.notebook/SpecId$v1,
            :specName "my/A",
            :specVersion 1}
           {:$type :tutorials.notebook/SpecId$v1,
            :specName "my/B",
            :specVersion 1}
           {:$type :tutorials.notebook/SpecId$v1,
            :specName "my/A",
            :specVersion 2}
           {:$type :tutorials.notebook/SpecId$v1,
            :specName "my/A",
            :specVersion 2}]}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.notebook/Workspace$v1', violates constraints \"tutorials.notebook/Workspace$v1/uniqueSpecIds\""
 :h-err/invalid-instance]
```

```clojure
{:$type :tutorials.notebook/Workspace$v1,
 :notebooks [{:name "notebook1",
              :$type :tutorials.notebook/Notebook$v1,
              :contents "contents1",
              :newSpecs [],
              :specRefs [],
              :version 1}
             {:name "notebook1",
              :$type :tutorials.notebook/Notebook$v1,
              :contents "contents1",
              :newSpecs [],
              :specRefs [],
              :version 2}],
 :specIds [{:$type :tutorials.notebook/SpecId$v1,
            :specName "my/A",
            :specVersion 1}
           {:$type :tutorials.notebook/SpecId$v1,
            :specName "my/B",
            :specVersion 1}
           {:$type :tutorials.notebook/SpecId$v1,
            :specName "my/A",
            :specVersion 2}]}


;-- result --
[:throws
 "h-err/invalid-instance 0-0 : Invalid instance of 'tutorials.notebook/Workspace$v1', violates constraints \"tutorials.notebook/Workspace$v1/uniqueNotebookNames\""
 :h-err/invalid-instance]
```

```clojure
{:tutorials.notebook/FAreNotebookRefsValid$v1
   {:fields {:notebook :tutorials.notebook/Notebook$v1,
             :workspace :tutorials.notebook/Workspace$v1},
    :refines-to
      {:tutorials.notebook/RBoolean$v1
         {:name "result",
          :expr '{:$type :tutorials.notebook/RBoolean$v1,
                  :result
                    (every?
                      [sr (get notebook :specRefs)]
                      (let [_ (refine-to
                                {:$type :tutorials.notebook/SpecRefResolver$v1,
                                 :existingSpecIds (get workspace :specIds),
                                 :newSpecs (get notebook :newSpecs),
                                 :inputSpecRef sr}
                                :tutorials.notebook/SpecId$v1)]
                        true))}}}},
 :tutorials.notebook/RBoolean$v1 {:fields {:result :Boolean}}}
```

```clojure
(get (refine-to {:$type :tutorials.notebook/FAreNotebookRefsValid$v1,
                 :workspace {:$type :tutorials.notebook/Workspace$v1,
                             :specIds [{:$type :tutorials.notebook/SpecId$v1,
                                        :specName "my/A",
                                        :specVersion 1}
                                       {:$type :tutorials.notebook/SpecId$v1,
                                        :specName "my/B",
                                        :specVersion 1}
                                       {:$type :tutorials.notebook/SpecId$v1,
                                        :specName "my/A",
                                        :specVersion 2}],
                             :notebooks []},
                 :notebook {:$type :tutorials.notebook/Notebook$v1,
                            :name "notebook1",
                            :contents "contents1",
                            :version 1,
                            :newSpecs [],
                            :specRefs []}}
                :tutorials.notebook/RBoolean$v1)
     :result)


;-- result --
true
```

```clojure
(get (refine-to {:$type :tutorials.notebook/FAreNotebookRefsValid$v1,
                 :workspace {:$type :tutorials.notebook/Workspace$v1,
                             :specIds [{:$type :tutorials.notebook/SpecId$v1,
                                        :specName "my/A",
                                        :specVersion 1}
                                       {:$type :tutorials.notebook/SpecId$v1,
                                        :specName "my/B",
                                        :specVersion 1}
                                       {:$type :tutorials.notebook/SpecId$v1,
                                        :specName "my/A",
                                        :specVersion 2}],
                             :notebooks []},
                 :notebook {:$type :tutorials.notebook/Notebook$v1,
                            :name "notebook1",
                            :contents "contents1",
                            :version 1,
                            :newSpecs [],
                            :specRefs [{:$type :tutorials.notebook/SpecRef$v1,
                                        :specName "my/A",
                                        :specVersion 1}]}}
                :tutorials.notebook/RBoolean$v1)
     :result)


;-- result --
true
```

```clojure
(get (refine-to {:$type :tutorials.notebook/FAreNotebookRefsValid$v1,
                 :workspace {:$type :tutorials.notebook/Workspace$v1,
                             :specIds [{:$type :tutorials.notebook/SpecId$v1,
                                        :specName "my/A",
                                        :specVersion 1}
                                       {:$type :tutorials.notebook/SpecId$v1,
                                        :specName "my/B",
                                        :specVersion 1}
                                       {:$type :tutorials.notebook/SpecId$v1,
                                        :specName "my/A",
                                        :specVersion 2}],
                             :notebooks []},
                 :notebook {:$type :tutorials.notebook/Notebook$v1,
                            :name "notebook1",
                            :contents "contents1",
                            :version 1,
                            :newSpecs [],
                            :specRefs [{:$type :tutorials.notebook/SpecRef$v1,
                                        :specName "my/X",
                                        :specVersion 1}]}}
                :tutorials.notebook/RBoolean$v1)
     :result)


;-- result --
[:throws
 "h-err/spec-threw 0-0 : Spec threw error: \"fixed ref does not resolve: my/X\""
 :h-err/spec-threw]
```

