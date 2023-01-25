;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.doc.data-tutorial)

(set! *warn-on-reflection* true)

(def tutorials {:tutorials.notebook/notebook
                {:label "Model a notebook mechanism"
                 :contents ["Model a notebook"
                            "Abstractly a spec ref contains the following fields."
                            {:spec-map {:tutorials.notebook/SpecRef$v1
                                        {:abstract? true
                                         :fields {:specName :String
                                                  :specVersion :Integer
                                                  :isFloating :Boolean
                                                  :isNew :Boolean}}}}
                            "A fixed reference contains in itself the specific version of the spec it is referencing. This spec must either already exist in the workpsace or it must have been created earlier in the notebook."
                            {:spec-map-merge {:tutorials.notebook/FixedSpecRef$v1
                                              {:fields {:specName :String
                                                        :specVersion :Integer}
                                               :refines-to {:tutorials.notebook/SpecRef$v1 {:name "toSpecRef"
                                                                                            :expr '{:$type :tutorials.notebook/SpecRef$v1
                                                                                                    :specName specName
                                                                                                    :specVersion specVersion
                                                                                                    :isFloating false
                                                                                                    :isNew false}}}}}}
                            "A floating reference just points to whatever the latest spec is that is in scope at the time the reference is processed. This might be a spec created earlier in the notebook."
                            {:spec-map-merge {:tutorials.notebook/FloatingSpecRef$v1
                                              {:fields {:specName :String}
                                               :refines-to {:tutorials.notebook/SpecRef$v1 {:name "toSpecRef"
                                                                                            :expr '{:$type :tutorials.notebook/SpecRef$v1
                                                                                                    :specName specName
                                                                                                    :specVersion 0
                                                                                                    :isFloating true
                                                                                                    :isNew false}}}}}}
                            "The creation of a new spec of a given version is treated as a spec reference in the notebook."
                            {:spec-map-merge {:tutorials.notebook/NewSpecRef$v1
                                              {:fields {:specName :String
                                                        :specVersion :Integer}
                                               :refines-to {:tutorials.notebook/SpecRef$v1 {:name "toSpecRef"
                                                                                            :expr '{:$type :tutorials.notebook/SpecRef$v1
                                                                                                    :specName specName
                                                                                                    :specVersion specVersion
                                                                                                    :isFloating false
                                                                                                    :isNew true}}}}}}
                            "All the spec refs above need to be resolved to fixed spec references for processing. This resolver takes in all of the references that exist in a workspace context, the references from the notebook being processed, and a specific reference from the notebook. Via refinement, it produces a fixed spec reference that should be used instead in the given context."
                            {:spec-map-merge {:tutorials.notebook/SpecRefResolver$v1
                                              {:fields {:workspaceSpecRefs [:Vec :tutorials.notebook/FixedSpecRef$v1]
                                                        :notebookSpecRefs [:Vec :tutorials.notebook/SpecRef$v1]
                                                        :inputSpecRef :tutorials.notebook/SpecRef$v1}
                                               :refines-to {:tutorials.notebook/FixedSpecRef$v1
                                                            {:name "toFixedSpecRef"
                                                             :expr '(let [inputSpecRef (refine-to inputSpecRef :tutorials.notebook/SpecRef$v1)
                                                                          new-spec-refs (filter [sr (map [sr notebookSpecRefs]
                                                                                                         (refine-to sr :tutorials.notebook/SpecRef$v1))]
                                                                                                (get sr :isNew))
                                                                          all-spec-refs (concat workspaceSpecRefs
                                                                                                (map [sr new-spec-refs]
                                                                                                     {:$type :tutorials.notebook/FixedSpecRef$v1
                                                                                                      :specName (get sr :specName)
                                                                                                      :specVersion (get sr :specVersion)}))
                                                                          max-version-in-workspace (reduce [a 0] [sr workspaceSpecRefs]
                                                                                                           (cond
                                                                                                             (not= (get sr :specName)
                                                                                                                   (get inputSpecRef :specName))
                                                                                                             a

                                                                                                             (> (get sr :specVersion) a)
                                                                                                             (get sr :specVersion)

                                                                                                             a))
                                                                          max-version-in-context (reduce [a 0] [sr all-spec-refs]
                                                                                                         (cond
                                                                                                           (not= (get sr :specName)
                                                                                                                 (get inputSpecRef :specName))
                                                                                                           a

                                                                                                           (> (get sr :specVersion) a)
                                                                                                           (get sr :specVersion)

                                                                                                           a))]
                                                                      (cond
                                                                        (get inputSpecRef :isFloating)
                                                                        {:$type :tutorials.notebook/FixedSpecRef$v1
                                                                         :specName (get inputSpecRef :specName)
                                                                         :specVersion (if (= max-version-in-context 0)
                                                                                        (error (str "floating ref does not resolve: " (get inputSpecRef :specName)))
                                                                                        max-version-in-context)}

                                                                        (get inputSpecRef :isNew)
                                                                        {:$type :tutorials.notebook/FixedSpecRef$v1
                                                                         :specName (get inputSpecRef :specName)
                                                                         :specVersion (let [v (reduce [a 0] [sr workspaceSpecRefs]
                                                                                                      (cond
                                                                                                        (not= (get sr :specName)
                                                                                                              (get inputSpecRef :specName))
                                                                                                        a

                                                                                                        (> (get sr :specVersion) a)
                                                                                                        (get sr :specVersion)

                                                                                                        a))
                                                                                            in-sequence? (reduce [a max-version-in-workspace]
                                                                                                                 [v (map [sr (filter [sr new-spec-refs]
                                                                                                                                     (= (get sr :specName)
                                                                                                                                        (get inputSpecRef :specName)))]
                                                                                                                         (get sr :specVersion))]
                                                                                                                 (if (= (inc a) v)
                                                                                                                   v
                                                                                                                   (error (str "new versions not in sequence: " (get inputSpecRef :specName)))))]
                                                                                        (if (or (and (= v 0)
                                                                                                     (not (= 1 (get inputSpecRef :specVersion))))
                                                                                                (not (> (get inputSpecRef :specVersion) v)))
                                                                                          (error (str "CAS error on new ref: " (get inputSpecRef :specName)))
                                                                                          (get inputSpecRef :specVersion)))}

                                                                        {:$type :tutorials.notebook/FixedSpecRef$v1
                                                                         :specName (get inputSpecRef :specName)
                                                                         :specVersion (let [found? (reduce [a false] [sr all-spec-refs]
                                                                                                           (or
                                                                                                            a
                                                                                                            (and (= (get sr :specName)
                                                                                                                    (get inputSpecRef :specName))
                                                                                                                 (= (get sr :specVersion)
                                                                                                                    (get inputSpecRef :specVersion)))
                                                                                                            a))]
                                                                                        (if found?
                                                                                          (get inputSpecRef :specVersion)
                                                                                          (error (str "fixed ref not resolve: " (get inputSpecRef :specName)))))}))}}}}}
                            "Make a spec to hold the result of resolving all spec references in a notebook."
                            {:spec-map-merge {:tutorials.notebook/ResolvedSpecRefs$v1
                                              {:fields {:specRefs [:Vec :tutorials.notebook/FixedSpecRef$v1]}}}}

                            "A notebook contains spec references. This is modeled as the results of having parsed the references out of the contents of the notebook."
                            {:spec-map-merge {:tutorials.notebook/Notebook$v1
                                              {:fields {:name :String
                                                        :contents :String
                                                        :version :Integer
                                                        :specRefs [:Vec :tutorials.notebook/SpecRef$v1]}
                                               :constraints #{{:name "positiveVersion"
                                                               :expr '(> version 0)}}}}}
                            "A workspace contains its own spec references. This represents the spec references contained in the registry for this workspace and its registry. In addition, the workspace contains notebooks. The workspace is only valid if all of the notebooks it contains are valid. Via refinement, the workspace can be \"queried\" to produce all of the resolved spec references from all of the notebooks."

                            {:spec-map-merge {:tutorials.notebook/Workspace$v1
                                              {:fields {:specRefs [:Vec :tutorials.notebook/FixedSpecRef$v1]
                                                        :notebooks [:Vec :tutorials.notebook/Notebook$v1]}
                                               :constraints #{{:name "uniqueNotebookNames"
                                                               :expr '(= (count (concat #{} (map [n notebooks]
                                                                                                 (get n :name))))
                                                                         (count notebooks))}

                                                              {:name "validRefs"
                                                               :expr '(every? [n notebooks]
                                                                              (every? [sr (get n :specRefs)]
                                                                                      (refines-to? {:$type :tutorials.notebook/SpecRefResolver$v1
                                                                                                    :workspaceSpecRefs specRefs
                                                                                                    :notebookSpecRefs (get n :specRefs)
                                                                                                    :inputSpecRef sr}
                                                                                                   :tutorials.notebook/FixedSpecRef$v1)))}}
                                               :refines-to {:tutorials.notebook/ResolvedSpecRefs$v1
                                                            {:name "toResolvedSpecRef"
                                                             :expr '{:$type :tutorials.notebook/ResolvedSpecRefs$v1
                                                                     :specRefs (reduce [a []]
                                                                                       [x (map [n notebooks]
                                                                                               (map [sr (get n :specRefs)]
                                                                                                    (refine-to {:$type :tutorials.notebook/SpecRefResolver$v1
                                                                                                                :workspaceSpecRefs specRefs
                                                                                                                :notebookSpecRefs (get n :specRefs)
                                                                                                                :inputSpecRef sr}
                                                                                                               :tutorials.notebook/FixedSpecRef$v1)))]
                                                                                       (concat a x))}}}}}}
                            "A simple test that an empty workspace is valid."
                            {:code {:$type :tutorials.notebook/Workspace$v1
                                    :specRefs []
                                    :notebooks []}}
                            "A workspace with spec references."
                            {:code {:$type :tutorials.notebook/Workspace$v1
                                    :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 1}
                                               {:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 2}]
                                    :notebooks []}}
                            "A workspace with a notebook that references one of the specs in the workspace."
                            {:code {:$type :tutorials.notebook/Workspace$v1
                                    :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 1}
                                               {:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 2}]
                                    :notebooks [{:$type :tutorials.notebook/Notebook$v1
                                                 :name "mine"
                                                 :version 1
                                                 :contents "docs"
                                                 :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 1}]}]}}

                            "If a notebook references a non existent spec, then the workspace is not valid with that notebook included."
                            {:code {:$type :tutorials.notebook/Workspace$v1
                                    :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 1}
                                               {:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 2}]
                                    :notebooks [{:$type :tutorials.notebook/Notebook$v1
                                                 :name "mine"
                                                 :version 1
                                                 :contents "docs"
                                                 :specRefs [{:$type :tutorials.notebook/FloatingSpecRef$v1 :specName "my/B"}
                                                            {:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 1}]}]}
                             :throws :auto}

                            "The same applies if a notebook references a version of a spec that does not exist."
                            {:code {:$type :tutorials.notebook/Workspace$v1
                                    :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 1}
                                               {:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 2}]
                                    :notebooks [{:$type :tutorials.notebook/Notebook$v1
                                                 :name "mine"
                                                 :version 1
                                                 :contents "docs"
                                                 :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 3}]}]}
                             :throws :auto}

                            "Via refinement, we can see the result of resolving all spec references in all notebooks in a workspace. This shows a floating reference being resolved."
                            {:code '(refine-to {:$type :tutorials.notebook/Workspace$v1
                                                :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 3}
                                                           {:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 1}]
                                                :notebooks [{:$type :tutorials.notebook/Notebook$v1
                                                             :name "mine"
                                                             :version 1
                                                             :contents "docs"
                                                             :specRefs [{:$type :tutorials.notebook/FloatingSpecRef$v1 :specName "my/A"}]}]}
                                               :tutorials.notebook/ResolvedSpecRefs$v1)
                             :result '{:$type :tutorials.notebook/ResolvedSpecRefs$v1
                                       :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1
                                                   :specName "my/A"
                                                   :specVersion 3}]}}

                            "This shows a fixed reference being resolved."
                            {:code '(refine-to {:$type :tutorials.notebook/Workspace$v1
                                                :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 3}
                                                           {:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 1}]
                                                :notebooks [{:$type :tutorials.notebook/Notebook$v1
                                                             :name "mine"
                                                             :version 1
                                                             :contents "docs"
                                                             :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 3}]}]}
                                               :tutorials.notebook/ResolvedSpecRefs$v1)
                             :result '{:$type :tutorials.notebook/ResolvedSpecRefs$v1
                                       :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1
                                                   :specName "my/A"
                                                   :specVersion 3}]}}

                            "A new spec can be created that does not yet exist in the workspace."
                            {:code '(refine-to {:$type :tutorials.notebook/Workspace$v1
                                                :specRefs []
                                                :notebooks [{:$type :tutorials.notebook/Notebook$v1
                                                             :name "mine"
                                                             :version 1
                                                             :contents "docs"
                                                             :specRefs [{:$type :tutorials.notebook/NewSpecRef$v1 :specName "my/A" :specVersion 1}]}]}
                                               :tutorials.notebook/ResolvedSpecRefs$v1)
                             :result :auto}
                            "If a spec with a given name already exists, then a new spec must use the next incremental version of the spec."
                            {:code '(refine-to {:$type :tutorials.notebook/Workspace$v1
                                                :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 1}]
                                                :notebooks [{:$type :tutorials.notebook/Notebook$v1
                                                             :name "mine"
                                                             :version 1
                                                             :contents "docs"
                                                             :specRefs [{:$type :tutorials.notebook/NewSpecRef$v1 :specName "my/A" :specVersion 2}]}]}
                                               :tutorials.notebook/ResolvedSpecRefs$v1)
                             :result :auto}
                            "If the versions do not match then it is a CAS error."
                            {:code '(refine-to {:$type :tutorials.notebook/Workspace$v1
                                                :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 1}]
                                                :notebooks [{:$type :tutorials.notebook/Notebook$v1
                                                             :name "mine"
                                                             :version 1
                                                             :contents "docs"
                                                             :specRefs [{:$type :tutorials.notebook/NewSpecRef$v1 :specName "my/A" :specVersion 1}]}]}
                                               :tutorials.notebook/ResolvedSpecRefs$v1)
                             :throws :auto}
                            "The same applies to creating new versions of a spec."
                            {:code '(refine-to {:$type :tutorials.notebook/Workspace$v1
                                                :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 3}
                                                           {:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 1}]
                                                :notebooks [{:$type :tutorials.notebook/Notebook$v1
                                                             :name "mine"
                                                             :version 1
                                                             :contents "docs"
                                                             :specRefs [{:$type :tutorials.notebook/NewSpecRef$v1 :specName "my/A" :specVersion 3}]}]}
                                               :tutorials.notebook/ResolvedSpecRefs$v1)
                             :throws :auto}
                            "This shows both a new reference and a fixed reference to that new spec being resolved."
                            {:code '(refine-to {:$type :tutorials.notebook/Workspace$v1
                                                :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 3}
                                                           {:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 1}]
                                                :notebooks [{:$type :tutorials.notebook/Notebook$v1
                                                             :name "mine"
                                                             :version 1
                                                             :contents "docs"
                                                             :specRefs [{:$type :tutorials.notebook/NewSpecRef$v1 :specName "my/A" :specVersion 4}
                                                                        {:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 4}]}]}
                                               :tutorials.notebook/ResolvedSpecRefs$v1)
                             :result '{:$type :tutorials.notebook/ResolvedSpecRefs$v1
                                       :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1
                                                   :specName "my/A"
                                                   :specVersion 4}
                                                  {:$type :tutorials.notebook/FixedSpecRef$v1
                                                   :specName "my/A"
                                                   :specVersion 4}]}}
                            "A floating reference can point to a new spec created in the notebook."
                            {:code '(refine-to {:$type :tutorials.notebook/Workspace$v1
                                                :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 3}
                                                           {:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 1}]
                                                :notebooks [{:$type :tutorials.notebook/Notebook$v1
                                                             :name "mine"
                                                             :version 1
                                                             :contents "docs"
                                                             :specRefs [{:$type :tutorials.notebook/NewSpecRef$v1 :specName "my/A" :specVersion 4}
                                                                        {:$type :tutorials.notebook/FloatingSpecRef$v1 :specName "my/A"}]}]}
                                               :tutorials.notebook/ResolvedSpecRefs$v1)
                             :result '{:$type :tutorials.notebook/ResolvedSpecRefs$v1,
                                       :specRefs
                                       [{:$type :tutorials.notebook/FixedSpecRef$v1, :specName "my/A", :specVersion 4}
                                        {:$type :tutorials.notebook/FixedSpecRef$v1, :specName "my/A", :specVersion 4}]}}

                            "Demonstration that invalid fixed references are detected in the presence of new spec references."
                            {:code '(refine-to {:$type :tutorials.notebook/Workspace$v1
                                                :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 3}
                                                           {:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 1}]
                                                :notebooks [{:$type :tutorials.notebook/Notebook$v1
                                                             :name "mine"
                                                             :version 1
                                                             :contents "docs"
                                                             :specRefs [{:$type :tutorials.notebook/NewSpecRef$v1 :specName "my/A" :specVersion 4}
                                                                        {:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 6}]}]}
                                               :tutorials.notebook/ResolvedSpecRefs$v1)
                             :throws :auto}
                            "Multiple new versions of a spec can be created in a notebook and each of those versions can be referenced via fixed references."
                            {:code '(refine-to {:$type :tutorials.notebook/Workspace$v1
                                                :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 3}
                                                           {:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 1}]
                                                :notebooks [{:$type :tutorials.notebook/Notebook$v1
                                                             :name "mine"
                                                             :version 1
                                                             :contents "docs"
                                                             :specRefs [{:$type :tutorials.notebook/NewSpecRef$v1 :specName "my/A" :specVersion 4}
                                                                        {:$type :tutorials.notebook/NewSpecRef$v1 :specName "my/A" :specVersion 5}
                                                                        {:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 4}
                                                                        {:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 5}]}]}
                                               :tutorials.notebook/ResolvedSpecRefs$v1)
                             :result '{:$type :tutorials.notebook/ResolvedSpecRefs$v1,
                                       :specRefs
                                       [{:$type :tutorials.notebook/FixedSpecRef$v1, :specName "my/A", :specVersion 4}
                                        {:$type :tutorials.notebook/FixedSpecRef$v1, :specName "my/A", :specVersion 5}
                                        {:$type :tutorials.notebook/FixedSpecRef$v1, :specName "my/A", :specVersion 4}
                                        {:$type :tutorials.notebook/FixedSpecRef$v1, :specName "my/A", :specVersion 5}]}}
                            "Each of the new versions must match the incremental numbering scheme."
                            {:code '(refine-to {:$type :tutorials.notebook/Workspace$v1
                                                :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 3}
                                                           {:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 1}]
                                                :notebooks [{:$type :tutorials.notebook/Notebook$v1
                                                             :name "mine"
                                                             :version 1
                                                             :contents "docs"
                                                             :specRefs [{:$type :tutorials.notebook/NewSpecRef$v1 :specName "my/A" :specVersion 3}
                                                                        {:$type :tutorials.notebook/NewSpecRef$v1 :specName "my/A" :specVersion 4}]}]}
                                               :tutorials.notebook/ResolvedSpecRefs$v1)
                             :throws :auto}
                            {:code '(refine-to {:$type :tutorials.notebook/Workspace$v1
                                                :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 3}
                                                           {:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 1}]
                                                :notebooks [{:$type :tutorials.notebook/Notebook$v1
                                                             :name "mine"
                                                             :version 1
                                                             :contents "docs"
                                                             :specRefs [{:$type :tutorials.notebook/NewSpecRef$v1 :specName "my/A" :specVersion 4}
                                                                        {:$type :tutorials.notebook/NewSpecRef$v1 :specName "my/A" :specVersion 6}]}]}
                                               :tutorials.notebook/ResolvedSpecRefs$v1)
                             :throws :auto}
                            "One can imagine using the model described above to assess whether a new candidate notebook is valid. i.e. a workspace instance can be created, and the new notebook can be added to it as its sole notebook. If the resulting workspace instance is valid and the tests in the new notebook pass, then the notebook is valid."

                            "Once a notebook has been identified as valid, then it can be \"applied\" to a workspace. This has the effect of creating any new specs indicated by the notebook."

                            "A second use case is to consider whether some candidate workspace changes are valid in light of a workspace and its notebooks. In this scenario a workspace instance is created which reflects the proposed changes to the workspace. This workspace instance needs to include all of the notebooks that have been registered as regression tests. If the resulting workspace instance is valid and all of the tests in the notebooks pass, then the proposed changes can be made without violating the regression tests. When using a notebook in this mode, the new spec references that existed in the notebook when it was \"applied\" can be ignored."

                            "A regression test contains the information from a specific point-in-time of a notebook."
                            {:spec-map-merge {:tutorials.notebook/RegressionTest$v1
                                              {:fields {:notebookName :String
                                                        :notebookVersion :Integer
                                                        :contents :String
                                                        :specRefs [:Vec :tutorials.notebook/SpecRef$v1]}
                                               :constraints #{{:name "positiveVersion"
                                                               :expr '(> notebookVersion 0)}
                                                              {:name "noNewReferences"
                                                               :expr '(let [new-spec-refs (filter [sr (map [sr specRefs]
                                                                                                           (refine-to sr :tutorials.notebook/SpecRef$v1))]
                                                                                                  (get sr :isNew))]
                                                                        (= 0 (count new-spec-refs)))}}}}}

                            "This spec captures the rules governing how notebooks are used to create regression tests."
                            {:spec-map-merge {:tutorials.notebook/RegressionTestMaker$v1
                                              {:fields {:notebook :tutorials.notebook/Notebook$v1
                                                        :notebookVersion :Integer}
                                               :refines-to {:tutorials.notebook/RegressionTest$v1
                                                            {:name "makeTest"
                                                             :expr '{:$type :tutorials.notebook/RegressionTest$v1
                                                                     :notebookName (get notebook :name)
                                                                     :notebookVersion (get notebook :version)
                                                                     :contents (get notebook :contents)
                                                                     :specRefs (let [spec-refs (get notebook :specRefs)
                                                                                     new-spec-refs (filter [sr (map [sr spec-refs]
                                                                                                                    (refine-to sr :tutorials.notebook/SpecRef$v1))]
                                                                                                           (get sr :isNew))]
                                                                                 (if (= notebookVersion (get notebook :version))
                                                                                   spec-refs
                                                                                   (error "CAS error creating regression test")))}}}}}}
                            "A trivial example of creating a regression test."
                            {:code '(refine-to {:$type :tutorials.notebook/RegressionTestMaker$v1
                                                :notebook {:$type :tutorials.notebook/Notebook$v1
                                                           :name "mine"
                                                           :version 1
                                                           :contents "docs"
                                                           :specRefs []}
                                                :notebookVersion 1}
                                               :tutorials.notebook/RegressionTest$v1)
                             :result :auto}
                            "The contents and refs of the specific version of the notebook become the contents of the regression test."
                            {:code '(refine-to {:$type :tutorials.notebook/RegressionTestMaker$v1
                                                :notebook {:$type :tutorials.notebook/Notebook$v1
                                                           :name "mine"
                                                           :version 1
                                                           :contents "docs"
                                                           :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 3}
                                                                      {:$type :tutorials.notebook/FloatingSpecRef$v1 :specName "my/A"}]}
                                                :notebookVersion 1}
                                               :tutorials.notebook/RegressionTest$v1)
                             :result :auto}
                            "However, the notebook cannot contain new spec references."
                            {:code '{:$type :tutorials.notebook/RegressionTestMaker$v1
                                     :notebook {:$type :tutorials.notebook/Notebook$v1
                                                :name "mine"
                                                :version 1
                                                :contents "docs"
                                                :specRefs [{:$type :tutorials.notebook/NewSpecRef$v1 :specName "my/A" :specVersion 3}]}
                                     :notebookVersion 1}
                             :throws :auto}
                            "Also if the notebook has been updated, then the creation of the regression test fails. This allows users to detect when notebooks are changed underneath them."
                            {:code '{:$type :tutorials.notebook/RegressionTestMaker$v1
                                     :notebook {:$type :tutorials.notebook/Notebook$v1
                                                :name "mine"
                                                :version 2
                                                :contents "docs"
                                                :specRefs []}
                                     :notebookVersion 1}
                             :throws :auto}

                            "Workspaces can now be extended to include their regression tests."
                            {:spec-map-merge {:tutorials.notebook/Workspace$v2
                                              {:fields {:specRefs [:Vec :tutorials.notebook/FixedSpecRef$v1]
                                                        :notebooks [:Vec :tutorials.notebook/Notebook$v1]
                                                        :regressionTests [:Vec :tutorials.notebook/RegressionTest$v1]}
                                               :constraints #{{:name "uniqueNotebookNames"
                                                               :expr '(= (count (concat #{} (map [n notebooks]
                                                                                                 (get n :name))))
                                                                         (count notebooks))}

                                                              {:name "validRefs"
                                                               :expr '(every? [n notebooks]
                                                                              (every? [sr (get n :specRefs)]
                                                                                      (refines-to? {:$type :tutorials.notebook/SpecRefResolver$v1
                                                                                                    :workspaceSpecRefs specRefs
                                                                                                    :notebookSpecRefs (get n :specRefs)
                                                                                                    :inputSpecRef sr}
                                                                                                   :tutorials.notebook/FixedSpecRef$v1)))}

                                                              {:name "uniqueRegressionTestNotebookNames"
                                                               :expr '(= (count (concat #{} (map [t regressionTests]
                                                                                                 (get t :notebookName))))
                                                                         (count regressionTests))}}
                                               :refines-to {:tutorials.notebook/ResolvedSpecRefs$v1
                                                            {:name "toResolvedSpecRef"
                                                             :expr '{:$type :tutorials.notebook/ResolvedSpecRefs$v1
                                                                     :specRefs (reduce [a []]
                                                                                       [x (map [n notebooks]
                                                                                               (map [sr (get n :specRefs)]
                                                                                                    (refine-to {:$type :tutorials.notebook/SpecRefResolver$v1
                                                                                                                :workspaceSpecRefs specRefs
                                                                                                                :notebookSpecRefs (get n :specRefs)
                                                                                                                :inputSpecRef sr}
                                                                                                               :tutorials.notebook/FixedSpecRef$v1)))]
                                                                                       (concat a x))}}}}}}
                            "A workspace can include a regression test."
                            {:code '{:$type :tutorials.notebook/Workspace$v2
                                     :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 1}]
                                     :notebooks [{:$type :tutorials.notebook/Notebook$v1
                                                  :name "mine"
                                                  :version 1
                                                  :contents "docs"
                                                  :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 1}]}]
                                     :regressionTests [{:$type :tutorials.notebook/RegressionTest$v1
                                                        :notebookName "mine"
                                                        :notebookVersion 1
                                                        :contents "docs"
                                                        :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 1}]}]}}
                            "The notebook used as regression test can continue to be changed without affecting the regression tests."
                            {:code '{:$type :tutorials.notebook/Workspace$v2
                                     :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 1}]
                                     :notebooks [{:$type :tutorials.notebook/Notebook$v1
                                                  :name "mine"
                                                  :version 2
                                                  :contents "docs"
                                                  :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 1}]}]
                                     :regressionTests [{:$type :tutorials.notebook/RegressionTest$v1
                                                        :notebookName "mine"
                                                        :notebookVersion 1
                                                        :contents "docs"
                                                        :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 1}]}]}}
                            "However, only a single version of a given notebook can be a regression test at any point in time."
                            {:code '{:$type :tutorials.notebook/Workspace$v2
                                     :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 1}]
                                     :notebooks [{:$type :tutorials.notebook/Notebook$v1
                                                  :name "mine"
                                                  :version 2
                                                  :contents "docs"
                                                  :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 1}]}]
                                     :regressionTests [{:$type :tutorials.notebook/RegressionTest$v1
                                                        :notebookName "mine"
                                                        :notebookVersion 1
                                                        :contents "docs"
                                                        :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 1}]}
                                                       {:$type :tutorials.notebook/RegressionTest$v1
                                                        :notebookName "mine"
                                                        :notebookVersion 2
                                                        :contents "docs"
                                                        :specRefs [{:$type :tutorials.notebook/FixedSpecRef$v1 :specName "my/A" :specVersion 1}]}]}
                             :throws :auto}

                            "Validating a notebook in the context of a registry is similar to validating a notebook in the context of the workspace. The difference lies in the fact that different references are in scope, specifically specs that exist in the local workspace but which are not registered locally are omitted from the context. In addition, the notebooks at this stage can only contain fixed and floating spec references, i.e. they cannot contain new spec references."]}

                :tutorials.vending/vending
                {:label "Model a vending machine as a state machine"
                 :desc "Use specs to map out a state space and valid transitions"
                 :basic-ref ['fixed-decimal 'integer 'string 'vector 'set]
                 :how-to-ref [:refinement/convert-instances]
                 :explanation-ref [:tutorials.grocery/specs-as-predicates :tutorials.grocery/refinements-as-functions]
                 :contents ["We can model the state space for a vending machine that accepts nickels, dimes, and quarters and which vends  snacks for $0.50 and beverages for $1.00."
                            {:spec-map {:tutorials.vending/State$v1 {:fields {:balance [:Decimal 2]
                                                                              :beverageCount :Integer
                                                                              :snackCount :Integer}
                                                                     :constraints #{{:name "balance_not_negative"
                                                                                     :expr '(>= balance #d "0.00")}
                                                                                    {:name "counts_not_negative"
                                                                                     :expr '(and (>= beverageCount 0)
                                                                                                 (>= snackCount 0))}
                                                                                    {:name "counts_below_capacity"
                                                                                     :expr '(and (<= beverageCount 20)
                                                                                                 (<= snackCount 20))}}}}}
                            "With this spec we can construct instances."
                            {:code '{:$type :tutorials.vending/State$v1
                                     :balance #d "0.00"
                                     :beverageCount 10
                                     :snackCount 15}}
                            "Let us add a spec that will capture the constraints that identify a valid initial state for a vending machine."
                            {:spec-map-merge {:tutorials.vending/InitialState$v1 {:fields {:balance [:Decimal 2]
                                                                                           :beverageCount :Integer
                                                                                           :snackCount :Integer}
                                                                                  :constraints #{{:name "initial_state"
                                                                                                  :expr '(and (= #d "0.00" balance)
                                                                                                              (> beverageCount 0)
                                                                                                              (> snackCount 0))}}
                                                                                  :refines-to {:tutorials.vending/State$v1 {:name "toVending"
                                                                                                                            :expr
                                                                                                                            '{:$type :tutorials.vending/State$v1
                                                                                                                              :balance balance
                                                                                                                              :beverageCount beverageCount
                                                                                                                              :snackCount snackCount}}}}}}

                            "This additional spec can be used to determine whether a state is a valid initial state for the machine. For example, this is a valid initial state."

                            {:code {:$type :tutorials.vending/InitialState$v1
                                    :balance #d "0.00"
                                    :beverageCount 10
                                    :snackCount 15}}

                            "The corresponding vending state can be produced from the initial state:"
                            {:code '(refine-to {:$type :tutorials.vending/InitialState$v1
                                                :balance #d "0.00"
                                                :beverageCount 10
                                                :snackCount 15}
                                               :tutorials.vending/State$v1)
                             :result :auto}

                            "The following is not a valid initial state."
                            {:code '{:$type :tutorials.vending/InitialState$v1
                                     :balance #d "0.00"
                                     :beverageCount 0
                                     :snackCount 15}
                             :throws :auto}

                            "So now we have a model of the state space and valid initial states for the machine. However, we would like to also model valid state transitions."
                            {:spec-map-merge {:tutorials.vending/Transition$v1 {:fields {:current :tutorials.vending/State$v1
                                                                                         :next :tutorials.vending/State$v1}
                                                                                :constraints #{{:name "state_transitions"
                                                                                                :expr '(or (and
                                                                                                            (contains? #{#d "0.05"
                                                                                                                         #d "0.10"
                                                                                                                         #d "0.25"}
                                                                                                                       (- (get next :balance)
                                                                                                                          (get current :balance)))
                                                                                                            (= (get next :beverageCount)
                                                                                                               (get current :beverageCount))
                                                                                                            (= (get next :snackCount)
                                                                                                               (get current :snackCount)))
                                                                                                           (and
                                                                                                            (= #d "0.50" (- (get current :balance)
                                                                                                                            (get next :balance)))
                                                                                                            (= (get next :beverageCount)
                                                                                                               (get current :beverageCount))
                                                                                                            (= (get next :snackCount)
                                                                                                               (dec (get current :snackCount))))
                                                                                                           (and
                                                                                                            (= #d "1.00" (- (get current :balance)
                                                                                                                            (get next :balance)))
                                                                                                            (= (get next :beverageCount)
                                                                                                               (dec (get current :beverageCount)))
                                                                                                            (= (get next :snackCount)
                                                                                                               (get current :snackCount)))
                                                                                                           (= current next))}}}}}
                            "A valid transition representing a dime being dropped into the machine."
                            {:code '{:$type :tutorials.vending/Transition$v1
                                     :current {:$type :tutorials.vending/State$v1
                                               :balance #d "0.00"
                                               :beverageCount 10
                                               :snackCount 15}
                                     :next {:$type :tutorials.vending/State$v1
                                            :balance #d "0.10"
                                            :beverageCount 10
                                            :snackCount 15}}}
                            "An invalid transition, because the balance cannot increase by $0.07"
                            {:code '{:$type :tutorials.vending/Transition$v1
                                     :current {:$type :tutorials.vending/State$v1
                                               :balance #d "0.00"
                                               :beverageCount 10
                                               :snackCount 15}
                                     :next {:$type :tutorials.vending/State$v1
                                            :balance #d "0.07"
                                            :beverageCount 10
                                            :snackCount 15}}
                             :throws :auto}
                            "A valid transition representing a snack being vended."
                            {:code '{:$type :tutorials.vending/Transition$v1
                                     :current {:$type :tutorials.vending/State$v1
                                               :balance #d "0.75"
                                               :beverageCount 10
                                               :snackCount 15}
                                     :next {:$type :tutorials.vending/State$v1
                                            :balance #d "0.25"
                                            :beverageCount 10
                                            :snackCount 14}}}
                            "An invalid attempted transition representing a snack being vended."
                            {:code '{:$type :tutorials.vending/Transition$v1
                                     :current {:$type :tutorials.vending/State$v1
                                               :balance #d "0.75"
                                               :beverageCount 10
                                               :snackCount 15}
                                     :next {:$type :tutorials.vending/State$v1
                                            :balance #d "0.25"
                                            :beverageCount 9
                                            :snackCount 14}}
                             :throws :auto}
                            "It is a bit subtle, but our constraints also allow the state to be unchanged. This will turn out to be useful for us later."
                            {:code '{:$type :tutorials.vending/Transition$v1
                                     :current {:$type :tutorials.vending/State$v1
                                               :balance #d "0.00"
                                               :beverageCount 10
                                               :snackCount 15}
                                     :next {:$type :tutorials.vending/State$v1
                                            :balance #d "0.00"
                                            :beverageCount 10
                                            :snackCount 15}}}

                            "At this point we have modeled valid state transitions without modeling the events that trigger those transitions. That may be sufficient for what we are looking to accomplish, but let's take it further and model a possible event structure."
                            {:spec-map-merge {:tutorials.vending/AbstractEvent$v1 {:abstract? true
                                                                                   :fields {:balanceDelta [:Decimal 2]
                                                                                            :beverageDelta :Integer
                                                                                            :snackDelta :Integer}}
                                              :tutorials.vending/CoinEvent$v1 {:fields {:denomination :String}
                                                                               :constraints #{{:name "valid_coin"
                                                                                               :expr '(contains? #{"nickel" "dime" "quarter"} denomination)}}
                                                                               :refines-to {:tutorials.vending/AbstractEvent$v1 {:name "coin_event_to_abstract"
                                                                                                                                 :expr '{:$type :tutorials.vending/AbstractEvent$v1
                                                                                                                                         :balanceDelta (cond (= "nickel" denomination) #d "0.05"
                                                                                                                                                             (= "dime" denomination) #d "0.10"
                                                                                                                                                             #d "0.25")
                                                                                                                                         :beverageDelta 0
                                                                                                                                         :snackDelta 0}}}}
                                              :tutorials.vending/VendEvent$v1 {:fields {:item :String}
                                                                               :constraints #{{:name "valid_item"
                                                                                               :expr '(contains? #{"beverage" "snack"} item)}}
                                                                               :refines-to {:tutorials.vending/AbstractEvent$v1 {:name "vend_event_to_abstract"
                                                                                                                                 :expr '{:$type :tutorials.vending/AbstractEvent$v1
                                                                                                                                         :balanceDelta (if (= "snack" item)
                                                                                                                                                         #d "-0.50"
                                                                                                                                                         #d "-1.00")
                                                                                                                                         :beverageDelta (if (= "snack" item)
                                                                                                                                                          0
                                                                                                                                                          -1)
                                                                                                                                         :snackDelta (if (= "snack" item)
                                                                                                                                                       -1
                                                                                                                                                       0)}}}}}}
                            "Now we can construct the following events."
                            {:code '[{:$type :tutorials.vending/CoinEvent$v1
                                      :denomination "nickel"}
                                     {:$type :tutorials.vending/CoinEvent$v1
                                      :denomination "dime"}
                                     {:$type :tutorials.vending/CoinEvent$v1
                                      :denomination "quarter"}
                                     {:$type :tutorials.vending/VendEvent$v1
                                      :item "snack"}
                                     {:$type :tutorials.vending/VendEvent$v1
                                      :item "beverage"}]}
                            "We can verify that all of these events produce the expected abstract events."
                            {:code '(map [e [{:$type :tutorials.vending/CoinEvent$v1
                                              :denomination "nickel"}
                                             {:$type :tutorials.vending/CoinEvent$v1
                                              :denomination "dime"}
                                             {:$type :tutorials.vending/CoinEvent$v1
                                              :denomination "quarter"}
                                             {:$type :tutorials.vending/VendEvent$v1
                                              :item "snack"}
                                             {:$type :tutorials.vending/VendEvent$v1
                                              :item "beverage"}]]
                                         (refine-to e :tutorials.vending/AbstractEvent$v1))
                             :result :auto}
                            "As the next step, we add a spec which will take a vending machine state and and event as input to produce a new vending machine state as output."
                            {:spec-map-merge {:tutorials.vending/EventHandler$v1 {:fields {:current :tutorials.vending/State$v1
                                                                                           :event :tutorials.vending/AbstractEvent$v1}
                                                                                  :refines-to {:tutorials.vending/Transition$v1
                                                                                               {:name "event_handler"
                                                                                                :expr '{:$type :tutorials.vending/Transition$v1
                                                                                                        :current current
                                                                                                        :next (let [ae (refine-to event :tutorials.vending/AbstractEvent$v1)
                                                                                                                    newBalance (+ (get current :balance) (get ae :balanceDelta))
                                                                                                                    newBeverageCount (+ (get current :beverageCount) (get ae :beverageDelta))
                                                                                                                    newSnackCount (+ (get current :snackCount) (get ae :snackDelta))]
                                                                                                                (if (and (>= newBalance #d "0.00")
                                                                                                                         (>= newBeverageCount 0)
                                                                                                                         (>= newSnackCount 0))
                                                                                                                  {:$type :tutorials.vending/State$v1
                                                                                                                   :balance newBalance
                                                                                                                   :beverageCount newBeverageCount
                                                                                                                   :snackCount newSnackCount}
                                                                                                                  current))}}}}}}
                            "Note that in the event handler we place the new state into a transition instance. This will ensure that the new state represents a valid transition per the constraints in that spec. Also note that we are not changing our state if the event would cause our balance or counts to go negative. Since the transition spec allows the state to be unchanged we can simply user our current state as the new state in these cases."
                            "Let's exercise the event handler to see if works as we expect."
                            {:code '(refine-to {:$type :tutorials.vending/EventHandler$v1
                                                :current {:$type :tutorials.vending/State$v1
                                                          :balance #d "0.10"
                                                          :beverageCount 5
                                                          :snackCount 6}
                                                :event {:$type :tutorials.vending/CoinEvent$v1
                                                        :denomination "quarter"}}
                                               :tutorials.vending/Transition$v1)
                             :result :auto}

                            "If we try to process an event that cannot be handled then the state is unchanged."
                            {:code '(refine-to {:$type :tutorials.vending/EventHandler$v1
                                                :current {:$type :tutorials.vending/State$v1
                                                          :balance #d "0.10"
                                                          :beverageCount 5
                                                          :snackCount 6}
                                                :event {:$type :tutorials.vending/VendEvent$v1
                                                        :item "snack"}}
                                               :tutorials.vending/Transition$v1)
                             :result :auto}

                            "We have come this far we might as well add one more spec that ties it all together via an initial state and a sequence of events."
                            {:spec-map-merge {:tutorials.vending/Behavior$v1 {:fields {:initial :tutorials.vending/InitialState$v1
                                                                                       :events [:Vec :tutorials.vending/AbstractEvent$v1]}
                                                                              :refines-to {:tutorials.vending/State$v1
                                                                                           {:name "apply_events"
                                                                                            :expr '(reduce [a (refine-to initial :tutorials.vending/State$v1)]
                                                                                                           [e events]
                                                                                                           (get (refine-to {:$type :tutorials.vending/EventHandler$v1
                                                                                                                            :current a
                                                                                                                            :event e}
                                                                                                                           :tutorials.vending/Transition$v1)
                                                                                                                :next))}}}}}
                            "From an initial state and a sequence of events we can compute the final state."
                            {:code '(refine-to {:$type :tutorials.vending/Behavior$v1
                                                :initial {:$type :tutorials.vending/InitialState$v1
                                                          :balance #d "0.00"
                                                          :beverageCount 10
                                                          :snackCount 15}
                                                :events [{:$type :tutorials.vending/CoinEvent$v1
                                                          :denomination "quarter"}
                                                         {:$type :tutorials.vending/CoinEvent$v1
                                                          :denomination "nickel"}
                                                         {:$type :tutorials.vending/VendEvent$v1
                                                          :item "snack"}
                                                         {:$type :tutorials.vending/CoinEvent$v1
                                                          :denomination "dime"}
                                                         {:$type :tutorials.vending/CoinEvent$v1
                                                          :denomination "quarter"}
                                                         {:$type :tutorials.vending/VendEvent$v1
                                                          :item "snack"}
                                                         {:$type :tutorials.vending/CoinEvent$v1
                                                          :denomination "dime"}
                                                         {:$type :tutorials.vending/CoinEvent$v1
                                                          :denomination "nickel"}
                                                         {:$type :tutorials.vending/CoinEvent$v1
                                                          :denomination "dime"}
                                                         {:$type :tutorials.vending/CoinEvent$v1
                                                          :denomination "quarter"}
                                                         {:$type :tutorials.vending/CoinEvent$v1
                                                          :denomination "quarter"}
                                                         {:$type :tutorials.vending/CoinEvent$v1
                                                          :denomination "quarter"}
                                                         {:$type :tutorials.vending/VendEvent$v1
                                                          :item "beverage"}
                                                         {:$type :tutorials.vending/VendEvent$v1
                                                          :item "beverage"}]}
                                               :tutorials.vending/State$v1)
                             :result :auto}
                            "Note that some of the vend events were effectively ignored because the balance was too low."]}

                :tutorials.sudoku/sudoku
                {:label "Model a sudokuo puzzle"
                 :desc "Consider how to use specs to model a sudoku game."
                 :basic-ref ['integer 'vector 'instance 'set 'boolean 'spec-map]
                 :op-ref ['valid? 'get 'concat 'get-in 'every? 'any? 'not]
                 :how-to-ref [:instance/constrain-instances]
                 :contents ["Say we want to represent a sudoku solution as a two dimensional vector of integers"
                            {:code '[[1 2 3 4]
                                     [3 4 1 2]
                                     [4 3 2 1]
                                     [2 1 4 3]]}
                            "We can write a specification that contains a value of this form."
                            {:spec-map {:tutorials.sudoku/Sudoku$v1 {:fields {:solution [:Vec [:Vec :Integer]]}}}}
                            "An instance of this spec can be constructed as:"
                            {:code '{:$type :tutorials.sudoku/Sudoku$v1
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 1]
                                                [2 1 4 3]]}}
                            "In order to be a valid solution, certain properties must be met: each row, column, and quadrant must consist of the values 1, 2, 3, & 4. That is each number appears once and only once in each of these divisions of the grid. These necessary properties can be expressed as constraints on the spec. Let's start by expressing the constraints on each row."
                            {:spec-map {:tutorials.sudoku/Sudoku$v2 {:fields {:solution [:Vec [:Vec :Integer]]}
                                                                     :constraints #{{:name "row_1" :expr '(= (concat #{} (get solution 0))
                                                                                                             #{1 2 3 4})}
                                                                                    {:name "row_2" :expr '(= (concat #{} (get solution 1))
                                                                                                             #{1 2 3 4})}
                                                                                    {:name "row_3" :expr '(= (concat #{} (get solution 2))
                                                                                                             #{1 2 3 4})}
                                                                                    {:name "row_4" :expr '(= (concat #{} (get solution 3))
                                                                                                             #{1 2 3 4})}}}}}
                            "Now when we create an instance it must meet these constraints. As this instance does."
                            {:code '{:$type :tutorials.sudoku/Sudoku$v2
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 1]
                                                [2 1 4 3]]}
                             :result :auto}
                            "However, this attempt to create an instance fails. It tells us specifically which constraint failed."
                            {:code '{:$type :tutorials.sudoku/Sudoku$v2
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 2]
                                                [2 1 4 3]]}
                             :throws :auto}
                            "Rather than expressing each row constraint separately, they can be captured in a single constraint expression."
                            {:spec-map {:tutorials.sudoku/Sudoku$v3 {:fields {:solution [:Vec [:Vec :Integer]]}
                                                                     :constraints #{{:name "rows" :expr '(every? [r solution]
                                                                                                                 (= (concat #{} r)
                                                                                                                    #{1 2 3 4}))}}}}}
                            "Again, valid solutions can be constructed."
                            {:code '{:$type :tutorials.sudoku/Sudoku$v3
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 1]
                                                [2 1 4 3]]}
                             :result :auto}
                            "While invalid solutions fail"
                            {:code '{:$type :tutorials.sudoku/Sudoku$v3
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 2]
                                                [2 1 4 3]]}
                             :throws :auto}
                            "But, we are only checking rows, let's also check columns."
                            {:spec-map {:tutorials.sudoku/Sudoku$v4 {:fields {:solution [:Vec [:Vec :Integer]]}
                                                                     :constraints #{{:name "rows" :expr '(every? [r solution]
                                                                                                                 (= (concat #{} r)
                                                                                                                    #{1 2 3 4}))}
                                                                                    {:name "columns" :expr '(every? [i [0 1 2 3]]
                                                                                                                    (= #{(get-in solution [0 i])
                                                                                                                         (get-in solution [1 i])
                                                                                                                         (get-in solution [2 i])
                                                                                                                         (get-in solution [3 i])}
                                                                                                                       #{1 2 3 4}))}}}}}
                            "First, check if a valid solution works."
                            {:code '{:$type :tutorials.sudoku/Sudoku$v4
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 1]
                                                [2 1 4 3]]}
                             :result :auto}
                            "Now confirm that an invalid solution fails. Notice that the error indicates that both constraints are violated."
                            {:code '{:$type :tutorials.sudoku/Sudoku$v4
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 2]
                                                [2 1 4 3]]}
                             :throws :auto}
                            "Notice that we are still not detecting the following invalid solution. Specifically, while this solution meets the row and column requirements, it does not meet the quadrant requirement."
                            {:code '{:$type :tutorials.sudoku/Sudoku$v4
                                     :solution [[1 2 3 4]
                                                [4 1 2 3]
                                                [3 4 1 2]
                                                [2 3 4 1]]}
                             :result :auto}
                            "Let's add the quadrant checks."
                            {:spec-map {:tutorials.sudoku/Sudoku$v5 {:fields {:solution [:Vec [:Vec :Integer]]}
                                                                     :constraints #{{:name "rows" :expr '(every? [r solution]
                                                                                                                 (= (concat #{} r)
                                                                                                                    #{1 2 3 4}))}
                                                                                    {:name "columns" :expr '(every? [i [0 1 2 3]]
                                                                                                                    (= #{(get-in solution [0 i])
                                                                                                                         (get-in solution [1 i])
                                                                                                                         (get-in solution [2 i])
                                                                                                                         (get-in solution [3 i])}
                                                                                                                       #{1 2 3 4}))}
                                                                                    {:name "quadrant_1" :expr '(= #{(get-in solution [0 0])
                                                                                                                    (get-in solution [0 1])
                                                                                                                    (get-in solution [1 0])
                                                                                                                    (get-in solution [1 1])}
                                                                                                                  #{1 2 3 4})}
                                                                                    {:name "quadrant_2" :expr '(= #{(get-in solution [0 2])
                                                                                                                    (get-in solution [0 3])
                                                                                                                    (get-in solution [1 2])
                                                                                                                    (get-in solution [1 3])}
                                                                                                                  #{1 2 3 4})}
                                                                                    {:name "quadrant_3" :expr '(= #{(get-in solution [2 0])
                                                                                                                    (get-in solution [2 1])
                                                                                                                    (get-in solution [3 0])
                                                                                                                    (get-in solution [3 1])}
                                                                                                                  #{1 2 3 4})}
                                                                                    {:name "quadrant_4" :expr '(= #{(get-in solution [2 2])
                                                                                                                    (get-in solution [2 3])
                                                                                                                    (get-in solution [3 2])
                                                                                                                    (get-in solution [3 3])}
                                                                                                                  #{1 2 3 4})}}}}}
                            "Now the attempted solution, which has valid columns and rows, but not quadrants is detected as invalid. Notice the error indicates that all four quadrants were violated."
                            {:code '{:$type :tutorials.sudoku/Sudoku$v5
                                     :solution [[1 2 3 4]
                                                [4 1 2 3]
                                                [3 4 1 2]
                                                [2 3 4 1]]}
                             :throws :auto}
                            "Let's make sure that our valid solution works."
                            {:code '{:$type :tutorials.sudoku/Sudoku$v5
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 1]
                                                [2 1 4 3]]}
                             :result :auto}
                            "Let's combine the quadrant checks into one."
                            {:spec-map {:tutorials.sudoku/Sudoku$v6 {:fields {:solution [:Vec [:Vec :Integer]]}
                                                                     :constraints #{{:name "rows" :expr '(every? [r solution]
                                                                                                                 (= (concat #{} r)
                                                                                                                    #{1 2 3 4}))}
                                                                                    {:name "columns" :expr '(every? [i [0 1 2 3]]
                                                                                                                    (= #{(get-in solution [0 i])
                                                                                                                         (get-in solution [1 i])
                                                                                                                         (get-in solution [2 i])
                                                                                                                         (get-in solution [3 i])}
                                                                                                                       #{1 2 3 4}))}
                                                                                    {:name "quadrants" :expr '(every? [base [[0 0] [0 2] [2 0] [2 2]]]
                                                                                                                      (let [base-x (get base 0)
                                                                                                                            base-y (get base 1)]
                                                                                                                        (= #{(get-in solution [base-x base-y])
                                                                                                                             (get-in solution [base-x (inc base-y)])
                                                                                                                             (get-in solution [(inc base-x) base-y])
                                                                                                                             (get-in solution [(inc base-x) (inc base-y)])}
                                                                                                                           #{1 2 3 4})))}}}}}
                            "Valid solution still works."
                            {:code '{:$type :tutorials.sudoku/Sudoku$v6
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 1]
                                                [2 1 4 3]]}
                             :result :auto}
                            "Invalid solution fails."
                            {:code '{:$type :tutorials.sudoku/Sudoku$v6
                                     :solution [[1 2 3 4]
                                                [4 1 2 3]
                                                [3 4 1 2]
                                                [2 3 4 1]]}
                             :throws :auto}
                            "As an exercise, we can convert the logic of the constraints. Instead of checking that each row, column, and quadrant has the expected elements, we can write the constraints to ensure there are not any rows, columns, or quadrants that do not have the expected elements. The double negative logic is confusing, but this shows other available logical operations."
                            {:spec-map {:tutorials.sudoku/Sudoku$v7 {:fields {:solution [:Vec [:Vec :Integer]]}
                                                                     :constraints #{{:name "rows" :expr '(not (any? [r solution]
                                                                                                                    (not= (concat #{} r)
                                                                                                                          #{1 2 3 4})))}
                                                                                    {:name "columns" :expr '(not (any? [i [0 1 2 3]]
                                                                                                                       (not= #{(get-in solution [0 i])
                                                                                                                               (get-in solution [1 i])
                                                                                                                               (get-in solution [2 i])
                                                                                                                               (get-in solution [3 i])}
                                                                                                                             #{1 2 3 4})))}
                                                                                    {:name "quadrants" :expr '(not (any? [base [[0 0] [0 2] [2 0] [2 2]]]
                                                                                                                         (let [base-x (get base 0)
                                                                                                                               base-y (get base 1)]
                                                                                                                           (not= #{(get-in solution [base-x base-y])
                                                                                                                                   (get-in solution [base-x (inc base-y)])
                                                                                                                                   (get-in solution [(inc base-x) base-y])
                                                                                                                                   (get-in solution [(inc base-x) (inc base-y)])}
                                                                                                                                 #{1 2 3 4}))))}}}}}
                            "Valid solution still works."
                            {:code '{:$type :tutorials.sudoku/Sudoku$v7
                                     :solution [[1 2 3 4]
                                                [3 4 1 2]
                                                [4 3 2 1]
                                                [2 1 4 3]]}
                             :result :auto}
                            "Invalid solution fails."
                            {:code '{:$type :tutorials.sudoku/Sudoku$v7
                                     :solution [[1 2 3 4]
                                                [4 1 2 3]
                                                [3 4 1 2]
                                                [2 3 4 1]]}
                             :throws :auto}

                            "Finally, rather than having invalid solutions throw errors, we can instead produce a boolean value indicating whether the solution is valid."
                            {:code '(valid? {:$type :tutorials.sudoku/Sudoku$v7
                                             :solution [[1 2 3 4]
                                                        [3 4 1 2]
                                                        [4 3 2 1]
                                                        [2 1 4 3]]})
                             :result :auto}
                            {:code '(valid? {:$type :tutorials.sudoku/Sudoku$v7
                                             :solution [[1 2 3 4]
                                                        [3 4 1 2]
                                                        [4 3 2 2]
                                                        [2 1 4 3]]})
                             :result :auto}
                            {:code '(valid? {:$type :tutorials.sudoku/Sudoku$v7
                                             :solution [[1 2 3 4]
                                                        [4 1 2 3]
                                                        [3 4 1 2]
                                                        [2 3 4 1]]})
                             :result :auto}]}

                :tutorials.grocery/grocery
                {:label "Model a grocery delivery business"
                 :desc "Consider how to use specs to model some of the important details of a business that provides grocery delivery to subscribers."
                 :basic-ref ['vector 'instance 'set 'fixed-decimal 'integer 'string 'spec-map]
                 :op-ref ['refine-to 'reduce 'map 'get 'and '< '<= 'count]
                 :how-to-ref [:refinement/convert-instances]
                 :explanation-ref [:tutorials.grocery/specs-as-predicates :tutorials.grocery/refinements-as-functions]
                 :contents ["The following is a full model for the grocery delivery business."

                            {:spec-map {:tutorials.grocery/Country$v1 {:fields {:name :String}
                                                                       :constraints #{{:name "name_constraint" :expr '(contains? #{"Canada" "Mexico" "US"} name)}}}

                                        :tutorials.grocery/Perk$v1 {:abstract? true
                                                                    :fields {:perkId :Integer
                                                                             :feePerMonth [:Decimal 2]
                                                                             :feePerUse [:Decimal 2]
                                                                             :usesPerMonth [:Maybe :Integer]}
                                                                    :constraints #{{:name "feePerMonth_limit" :expr '(and (<= #d "0.00" feePerMonth)
                                                                                                                          (<= feePerMonth #d "199.99"))}
                                                                                   {:name "feePerUse_limit" :expr '(and (<= #d "0.00" feePerUse)
                                                                                                                        (<= feePerUse #d "14.99"))}
                                                                                   {:name "usesPerMonth_limit" :expr '(if-value usesPerMonth
                                                                                                                                (and (<= 0 usesPerMonth)
                                                                                                                                     (<= usesPerMonth 999))
                                                                                                                                true)}}}
                                        :tutorials.grocery/FreeDeliveryPerk$v1 {:fields {:usesPerMonth :Integer}
                                                                                :constraints #{{:name "usesPerMonth_limit" :expr '(< usesPerMonth 20)}}
                                                                                :refines-to {:tutorials.grocery/Perk$v1 {:name "refine_to_Perk"
                                                                                                                         :expr '{:$type :tutorials.grocery/Perk$v1
                                                                                                                                 :perkId 101
                                                                                                                                 :feePerMonth #d "2.99"
                                                                                                                                 :feePerUse #d "0.00"
                                                                                                                                 :usesPerMonth usesPerMonth}}}}
                                        :tutorials.grocery/DiscountedPrescriptionPerk$v1 {:fields {:prescriptionID :String}
                                                                                          :refines-to {:tutorials.grocery/Perk$v1 {:name "refine_to_Perk"
                                                                                                                                   :expr '{:$type :tutorials.grocery/Perk$v1
                                                                                                                                           :perkId 102
                                                                                                                                           :feePerMonth #d "3.99"
                                                                                                                                           :feePerUse #d "0.00"}}}}
                                        :tutorials.grocery/EmergencyDeliveryPerk$v1 {:refines-to {:tutorials.grocery/Perk$v1 {:name "refine_to_Perk"
                                                                                                                              :expr '{:$type :tutorials.grocery/Perk$v1
                                                                                                                                      :perkId 103
                                                                                                                                      :feePerMonth #d "0.00"
                                                                                                                                      :feePerUse #d "1.99"
                                                                                                                                      :usesPerMonth 2}}}}

                                        :tutorials.grocery/GroceryService$v1 {:fields {:deliveriesPerMonth :Integer
                                                                                       :feePerMonth [:Decimal 2]
                                                                                       :perks [:Set :tutorials.grocery/Perk$v1]
                                                                                       :subscriberCountry :tutorials.grocery/Country$v1}
                                                                              :constraints #{{:name "feePerMonth_limit" :expr '(and (< #d "5.99" feePerMonth)
                                                                                                                                    (< feePerMonth #d "12.99"))}
                                                                                             {:name "perk_limit" :expr '(<= (count perks) 2)}
                                                                                             {:name "perk_sum" :expr '(let [perkInstances (sort-by [pi (map [p perks]
                                                                                                                                                            (refine-to p :tutorials.grocery/Perk$v1))]
                                                                                                                                                   (get pi :perkId))]
                                                                                                                        (< (reduce [a #d "0.00"] [pi perkInstances]
                                                                                                                                   (+ a (get pi :feePerMonth)))
                                                                                                                           #d "6.00"))}}
                                                                              :refines-to {:tutorials.grocery/GroceryStoreSubscription$v1 {:name "refine_to_Store"
                                                                                                                                           :expr '{:$type :tutorials.grocery/GroceryStoreSubscription$v1
                                                                                                                                                   :name "Acme Foods"
                                                                                                                                                   :storeCountry subscriberCountry
                                                                                                                                                   :perkIds (map [p (sort-by [pi (map [p perks]
                                                                                                                                                                                      (refine-to p :tutorials.grocery/Perk$v1))]
                                                                                                                                                                             (get pi :perkId))]
                                                                                                                                                                 (get p :perkId))}
                                                                                                                                           :extrinsic? true}}}
                                        :tutorials.grocery/GroceryStoreSubscription$v1 {:fields {:name :String
                                                                                                 :storeCountry :tutorials.grocery/Country$v1
                                                                                                 :perkIds [:Vec :Integer]}
                                                                                        :constraints #{{:name "valid_stores" :expr '(or (= name "Acme Foods")
                                                                                                                                        (= name "Good Foods"))}
                                                                                                       {:name "storeCountryServed" :expr '(or (and (= name "Acme Foods")
                                                                                                                                                   (contains? #{"Canada" "Costa Rica" "US"} (get storeCountry :name)))
                                                                                                                                              (and (= name "Good Foods")
                                                                                                                                                   (contains? #{"Mexico" "US"} (get storeCountry :name))))}}}}}

                            "Taking it one part at a time. Consider first the country model. This is modeling the countries where the company is operating. This is a valid country instance."
                            {:code '{:$type :tutorials.grocery/Country$v1
                                     :name "Canada"}}
                            "Whereas this is not a valid instance."
                            {:code '{:$type :tutorials.grocery/Country$v1
                                     :name "Germany"}
                             :throws :auto}
                            "Next the model introduces the abstract notion of a 'perk'. These are extra options that can be added on to the base grocery subscription service. Each type of perk has a unique number assigned as its 'perkID', it has fees, and it has an optional value indicating how many times the perk can be used per month. The perk model includes certain rules that all valid perk instances must satisfy. So, for example, the following are valid perk instances under this model."
                            {:code '{:$type :tutorials.grocery/Perk$v1
                                     :perkId 1
                                     :feePerMonth #d "4.50"
                                     :feePerUse #d "0.00"
                                     :usesPerMonth 3}}
                            {:code '{:$type :tutorials.grocery/Perk$v1
                                     :perkId 2
                                     :feePerMonth #d "4.50"
                                     :feePerUse #d "1.40"}}
                            "While this is not a valid perk instance."
                            {:code '{:$type :tutorials.grocery/Perk$v1
                                     :perkId 1
                                     :feePerMonth #d "4.50"
                                     :feePerUse #d "0.00"
                                     :usesPerMonth 1000}
                             :throws :auto}
                            "The model then defines the three types of perks that are actually offered. The following are example instances of these three specs."
                            {:code '{:$type :tutorials.grocery/FreeDeliveryPerk$v1
                                     :usesPerMonth 10}}
                            {:code '{:$type :tutorials.grocery/DiscountedPrescriptionPerk$v1
                                     :prescriptionID "ABC"}}
                            {:code '{:$type :tutorials.grocery/EmergencyDeliveryPerk$v1}}
                            "The overall grocery service spec now pulls together perks along with the subscriber's country and some service specific fields. The grocery service includes constraints that place additional restrictions on the service being offered. The following is an example valid instance."
                            {:code '{:$type :tutorials.grocery/GroceryService$v1
                                     :deliveriesPerMonth 3
                                     :feePerMonth #d "9.99"
                                     :perks #{{:$type :tutorials.grocery/FreeDeliveryPerk$v1
                                               :usesPerMonth 1}}
                                     :subscriberCountry {:$type :tutorials.grocery/Country$v1
                                                         :name "Canada"}}}
                            "While the following violates the constraint that limits the total monthly charges for perks."
                            {:code '{:$type :tutorials.grocery/GroceryService$v1
                                     :deliveriesPerMonth 3
                                     :feePerMonth #d "9.99"
                                     :perks #{{:$type :tutorials.grocery/FreeDeliveryPerk$v1
                                               :usesPerMonth 1}
                                              {:$type :tutorials.grocery/DiscountedPrescriptionPerk$v1
                                               :prescriptionID "XYZ:123"}}
                                     :subscriberCountry {:$type :tutorials.grocery/Country$v1
                                                         :name "Canada"}}
                             :throws :auto}
                            "This spec models the service from the subscriber's perspective, but now the business needs to translate this into an order for a back-end grocery store to actually provide the delivery service. This involves executing the refinement to a subscription object."
                            {:code '(refine-to {:$type :tutorials.grocery/GroceryService$v1
                                                :deliveriesPerMonth 3
                                                :feePerMonth #d "9.99"
                                                :perks #{{:$type :tutorials.grocery/FreeDeliveryPerk$v1
                                                          :usesPerMonth 1}}
                                                :subscriberCountry {:$type :tutorials.grocery/Country$v1
                                                                    :name "Canada"}} :tutorials.grocery/GroceryStoreSubscription$v1)
                             :result :auto}
                            "This final object is now in a form that the grocery store understands."]}})
