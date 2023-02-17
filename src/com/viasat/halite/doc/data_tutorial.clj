;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.doc.data-tutorial)

(set! *warn-on-reflection* true)

(def tutorials
  {:tutorials.notebook/notebook
   {:label "Model a notebook mechanism"
    :desc "Model a notebook mechanism"
    :contents
    ["The following is an extended example of implementing a non-trivial amount of logic in a set of specs. It is a bit \"meta\", but in this case the model will include specs that exist in workspaces where each spec has a version separate from its name. "
     "Versions must be positive values."
     {:spec-map {:tutorials.notebook/Version$v1
                 {:fields {:version [:Maybe :Integer]}
                  :constraints #{{:name "positiveVersion"
                                  :expr '(if-value version
                                                   (> version 0)
                                                   true)}}}}}

     "An identifier for a spec includes its name and version. By referencing the Version spec in a constraint we can reuse the constraint from the Version spec."
     {:spec-map-merge {:tutorials.notebook/SpecId$v1
                       {:fields {:workspaceName :String
                                 :specName :String
                                 :specVersion :Integer}
                        :constraints #{{:name "positiveVersion"
                                        :expr '(valid? {:$type :tutorials.notebook/Version$v1
                                                        :version specVersion})}}}}}

     "A notebook will consist of \"items\". Since there are different kinds of items an abstract spec is defined."
     {:spec-map-merge {:tutorials.notebook/AbstractNotebookItem$v1
                       {:abstract? true}}}

     "One kind of item is a reference to another spec."
     {:spec-map-merge {:tutorials.notebook/SpecRef$v1
                       {:fields {:workspaceName :String
                                 :specName :String
                                 :specVersion [:Maybe :Integer]}
                        :constraints #{{:name "positiveVersion"
                                        :expr '(valid? {:$type :tutorials.notebook/Version$v1
                                                        :version specVersion})}}
                        :refines-to {:tutorials.notebook/SpecId$v1
                                     {:name "specId"
                                      :expr '(when-value specVersion
                                                         {:$type :tutorials.notebook/SpecId$v1
                                                          :workspaceName workspaceName
                                                          :specName specName
                                                          :specVersion specVersion})}

                                     :tutorials.notebook/AbstractNotebookItem$v1
                                     {:name "abstractItems"
                                      :expr '{:$type :tutorials.notebook/AbstractNotebookItem$v1}}}}}}

     "Example of a \"fixed\" spec references that refers precisely to a given version of a spec."
     {:code '{:$type :tutorials.notebook/SpecRef$v1
              :workspaceName "my"
              :specName "A"
              :specVersion 1}}

     "Example of a \"floating\" spec reference that refers to the latest version of a given spec within whatever context the reference is resolved in."
     {:code '{:$type :tutorials.notebook/SpecRef$v1
              :workspaceName "my"
              :specName "A"}}

     "Another kind of notebook item is the definition of a new spec. Notice in this case the optional flag only presents two options: either it is present and set to 'true' or it is absent. So it is truly a binary. The convenience this approach provides is that it is less verbose to have it excluded it from instances where it is not set."
     {:spec-map-merge {:tutorials.notebook/NewSpec$v1
                       {:fields {:workspaceName :String
                                 :specName :String
                                 :specVersion :Integer
                                 :isEphemeral [:Maybe :Boolean]}
                        :constraints #{{:name "ephemeralFlag"
                                        :expr '(if-value isEphemeral
                                                         isEphemeral
                                                         true)}
                                       {:name "positiveVersion"
                                        :expr '(valid? {:$type :tutorials.notebook/Version$v1
                                                        :version specVersion})}}
                        :refines-to {:tutorials.notebook/SpecId$v1
                                     {:name "specId"
                                      :expr '{:$type :tutorials.notebook/SpecId$v1
                                              :workspaceName workspaceName
                                              :specName specName
                                              :specVersion specVersion}}

                                     :tutorials.notebook/AbstractNotebookItem$v1
                                     {:name "abstractItems"
                                      :expr '{:$type :tutorials.notebook/AbstractNotebookItem$v1}}}}}}

     "Some examples of 'new specs'."
     {:code '{:$type :tutorials.notebook/NewSpec$v1
              :workspaceName "my"
              :specName "A"
              :specVersion 1}}
     {:code '{:$type :tutorials.notebook/NewSpec$v1
              :workspaceName "my"
              :specName "A"
              :specVersion 0}
      :throws :auto}

     {:code '{:$type :tutorials.notebook/NewSpec$v1
              :workspaceName "my"
              :specName "A"
              :specVersion 1
              :isEphemeral true}}

     "It is not possible to set the flag to a value of 'false', instead it is to be omitted."
     {:code '{:$type :tutorials.notebook/NewSpec$v1
              :workspaceName "my"
              :specName "A"
              :specVersion 1
              :isEphemeral false}
      :throws :auto}

     "A 'new spec' can be treated as a spec identifier."
     {:code '(refine-to
              {:$type :tutorials.notebook/NewSpec$v1
               :workspaceName "my"
               :specName "A"
               :specVersion 1}
              :tutorials.notebook/SpecId$v1)
      :result {:$type :tutorials.notebook/SpecId$v1
               :workspaceName "my"
               :specName "A"
               :specVersion 1}}

     "This is an example of writing a reusable 'function' via a spec. In this case the prefix 'F' is used in the spec name to identify it as following this pattern. The fields in this spec are the input parameters to the function. The spec representing the return value from the function is prefixed with an 'R'. Since an integer value cannot be produced from a refinement, a spec is needed to hold the result."
     {:spec-map-merge {:tutorials.notebook/RInteger$v1
                       {:fields {:result [:Maybe :Integer]}}

                       :tutorials.notebook/FMaxSpecVersion$v1
                       {:fields {:specIds [:Set :tutorials.notebook/SpecId$v1]
                                 :workspaceName :String
                                 :specName :String}
                        :refines-to {:tutorials.notebook/RInteger$v1
                                     {:name "result"
                                      :expr '{:$type :tutorials.notebook/RInteger$v1
                                              :result (let [result (sort-by [v (map [si (filter [si specIds]
                                                                                                (and (= (get si :workspaceName) workspaceName)
                                                                                                     (= (get si :specName) specName)))]
                                                                                    (get si :specVersion))]
                                                                            (- 0 v))]
                                                        (when (not= 0 (count result))
                                                          (first result)))}}}}}}

     "Some examples of invoking the function to find the max version of a given spec."
     {:code '(refine-to
              {:$type :tutorials.notebook/FMaxSpecVersion$v1
               :specIds #{{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 1}
                          {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}
                          {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 2}}
               :workspaceName "my"
               :specName "A"}
              :tutorials.notebook/RInteger$v1)
      :result {:$type :tutorials.notebook/RInteger$v1 :result 2}}

     {:code '(refine-to
              {:$type :tutorials.notebook/FMaxSpecVersion$v1
               :specIds #{{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 1}
                          {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}
                          {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 2}}
               :workspaceName "my"
               :specName "B"}
              :tutorials.notebook/RInteger$v1)
      :result {:$type :tutorials.notebook/RInteger$v1 :result 1}}

     "An example of when there is no such spec in the set of specs."
     {:code '(refine-to
              {:$type :tutorials.notebook/FMaxSpecVersion$v1
               :specIds #{{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 1}
                          {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}
                          {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 2}}
               :workspaceName "my"
               :specName "C"}
              :tutorials.notebook/RInteger$v1)
      :result {:$type :tutorials.notebook/RInteger$v1}}

     "A spec which resolves a spec reference in the context of other specs. In this case, this is like a function, but there is a natural way to express the result using an existing spec."
     {:spec-map-merge
      {:tutorials.notebook/SpecRefResolver$v1
       {:fields {:existingSpecIds [:Set :tutorials.notebook/SpecId$v1]
                 :newSpecs [:Vec :tutorials.notebook/NewSpec$v1]
                 :inputSpecRef :tutorials.notebook/SpecRef$v1}
        :refines-to
        {:tutorials.notebook/SpecId$v1
         {:name "toSpecId"
          :expr
          '(let [all-spec-ids (concat existingSpecIds
                                      (map [ns newSpecs]
                                           (refine-to ns :tutorials.notebook/SpecId$v1)))
                 spec-version (get inputSpecRef :specVersion)]
             (if-value spec-version
                       (let [result (refine-to inputSpecRef :tutorials.notebook/SpecId$v1)]
                         (when (contains? (concat #{} all-spec-ids)
                                          result)
                           result))
                       (let [max-version-in-context (get (refine-to {:$type :tutorials.notebook/FMaxSpecVersion$v1
                                                                     :specIds all-spec-ids
                                                                     :workspaceName (get inputSpecRef :workspaceName)
                                                                     :specName (get inputSpecRef :specName)}
                                                                    :tutorials.notebook/RInteger$v1)
                                                         :result)]
                         (when-value max-version-in-context
                                     {:$type :tutorials.notebook/SpecId$v1
                                      :workspaceName (get inputSpecRef :workspaceName)
                                      :specName (get inputSpecRef :specName)
                                      :specVersion max-version-in-context}))))}}}}}

     "Cases where the input spec reference cannot be resolved are represented by failing to refine."
     {:code '(refines-to? {:$type :tutorials.notebook/SpecRefResolver$v1
                           :existingSpecIds #{}
                           :newSpecs []
                           :inputSpecRef {:$type :tutorials.notebook/SpecRef$v1
                                          :workspaceName "my"
                                          :specName "A"
                                          :specVersion 1}}
                          :tutorials.notebook/SpecId$v1)
      :result false}

     "For cases that can be refined, the refinement result is the result of resolving the spec reference. In this example the floating reference resolves to a new spec."
     {:code '(refine-to {:$type :tutorials.notebook/SpecRefResolver$v1
                         :existingSpecIds #{{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 1}
                                            {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}
                                            {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 2}}
                         :newSpecs [{:$type :tutorials.notebook/NewSpec$v1
                                     :workspaceName "my"
                                     :specName "C"
                                     :specVersion 1
                                     :isEphemeral true}
                                    {:$type :tutorials.notebook/NewSpec$v1
                                     :workspaceName "my"
                                     :specName "A"
                                     :specVersion 3}]
                         :inputSpecRef {:$type :tutorials.notebook/SpecRef$v1
                                        :workspaceName "my"
                                        :specName "A"}}
                        :tutorials.notebook/SpecId$v1)
      :result {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 3}}

     "In this example a floating spec reference resolves to an existing spec in the current context."
     {:code '(refine-to {:$type :tutorials.notebook/SpecRefResolver$v1
                         :existingSpecIds #{{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 1}
                                            {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}
                                            {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 2}}
                         :newSpecs [{:$type :tutorials.notebook/NewSpec$v1
                                     :workspaceName "my"
                                     :specName "C"
                                     :specVersion 1
                                     :isEphemeral true}
                                    {:$type :tutorials.notebook/NewSpec$v1
                                     :workspaceName "my"
                                     :specName "A"
                                     :specVersion 3}]
                         :inputSpecRef {:$type :tutorials.notebook/SpecRef$v1
                                        :workspaceName "my"
                                        :specName "B"}}
                        :tutorials.notebook/SpecId$v1)
      :result {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}}

     "An example of resolving a fixed spec reference."
     {:code '(refine-to {:$type :tutorials.notebook/SpecRefResolver$v1
                         :existingSpecIds #{{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 1}
                                            {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}
                                            {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 2}}
                         :newSpecs [{:$type :tutorials.notebook/NewSpec$v1
                                     :workspaceName "my"
                                     :specName "C"
                                     :specVersion 1
                                     :isEphemeral true}
                                    {:$type :tutorials.notebook/NewSpec$v1
                                     :workspaceName "my"
                                     :specName "A"
                                     :specVersion 3}]
                         :inputSpecRef {:$type :tutorials.notebook/SpecRef$v1
                                        :workspaceName "my"
                                        :specName "B"
                                        :specVersion 1}}
                        :tutorials.notebook/SpecId$v1)
      :result {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}}

     "An example of resolving a reference to an ephemeral spec."
     {:code '(refine-to {:$type :tutorials.notebook/SpecRefResolver$v1
                         :existingSpecIds #{{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 1}
                                            {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}
                                            {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 2}}
                         :newSpecs [{:$type :tutorials.notebook/NewSpec$v1
                                     :workspaceName "my"
                                     :specName "C"
                                     :specVersion 1
                                     :isEphemeral true}
                                    {:$type :tutorials.notebook/NewSpec$v1
                                     :workspaceName "my"
                                     :specName "A"
                                     :specVersion 3}]
                         :inputSpecRef {:$type :tutorials.notebook/SpecRef$v1
                                        :workspaceName "my"
                                        :specName "C"
                                        :specVersion 1}}
                        :tutorials.notebook/SpecId$v1)
      :result {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "C" :specVersion 1}}

     "A reference to a hypothetical ephemeral spec that does not exist does not resolve."
     {:code '(refines-to? {:$type :tutorials.notebook/SpecRefResolver$v1
                           :existingSpecIds #{{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 1}
                                              {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}
                                              {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 2}}
                           :newSpecs [{:$type :tutorials.notebook/NewSpec$v1
                                       :workspaceName "my"
                                       :specName "C"
                                       :specVersion 1
                                       :isEphemeral true}
                                      {:$type :tutorials.notebook/NewSpec$v1
                                       :workspaceName "my"
                                       :specName "A"
                                       :specVersion 3}]
                           :inputSpecRef {:$type :tutorials.notebook/SpecRef$v1
                                          :workspaceName "my"
                                          :specName "C"
                                          :specVersion 2}}
                          :tutorials.notebook/SpecId$v1)
      :result false}

     "A reference to a completely unknown spec name does not resolve."
     {:code '(refines-to? {:$type :tutorials.notebook/SpecRefResolver$v1
                           :existingSpecIds #{{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 1}
                                              {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}
                                              {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 2}}
                           :newSpecs [{:$type :tutorials.notebook/NewSpec$v1
                                       :workspaceName "my"
                                       :specName "C"
                                       :specVersion 1
                                       :isEphemeral true}
                                      {:$type :tutorials.notebook/NewSpec$v1
                                       :workspaceName "my"
                                       :specName "A"
                                       :specVersion 3}]
                           :inputSpecRef {:$type :tutorials.notebook/SpecRef$v1
                                          :workspaceName "my"
                                          :specName "X"
                                          :specVersion 1}}
                          :tutorials.notebook/SpecId$v1)
      :result false}

     "A notebook contains items. This is modeled as the results of having parsed the references out of the contents of the notebook. This is a vector, because the order of the items matters when resolving spec references mixed in with the creation of new specs."
     {:spec-map-merge {:tutorials.notebook/Notebook$v1
                       {:fields {:name :String
                                 :version :Integer
                                 :items [:Maybe [:Vec :tutorials.notebook/AbstractNotebookItem$v1]]}
                        :constraints #{{:name "positiveVersion"
                                        :expr '(valid? {:$type :tutorials.notebook/Version$v1
                                                        :version version})}}}}}

     "The contents of notebooks can be used as the basis for defining regression tests for a workspace."
     {:spec-map-merge {:tutorials.notebook/RegressionTest$v1
                       {:fields {:notebookName :String
                                 :notebookVersion :Integer}
                        :constraints #{{:name "positiveVersion"
                                        :expr '(valid? {:$type :tutorials.notebook/Version$v1
                                                        :version notebookVersion})}}}}}

     "Finally, we can create a top-level spec that represents a workspace and the items it contains. Two separate fields are used to represent the specs that are available in a workspace. One captures all of those that are registered. The other captures, just the \"private\" specs that are defined in this workspace, but not made available in the registry."
     {:spec-map-merge
      {:tutorials.notebook/Workspace$v1
       {:fields {:workspaceName [:Maybe :String]
                 :registryHeaderNotebookName [:Maybe :String]
                 :registrySpecIds [:Maybe [:Set :tutorials.notebook/SpecId$v1]]
                 :specIds [:Maybe [:Set :tutorials.notebook/SpecId$v1]]
                 :notebooks [:Maybe [:Set :tutorials.notebook/Notebook$v1]]
                 :tests [:Maybe [:Set :tutorials.notebook/RegressionTest$v1]]}
        :constraints #{{:name "specIdsDisjoint"
                        :expr '(if-value registrySpecIds
                                         (if-value specIds
                                                   (= 0 (count (intersection registrySpecIds specIds)))
                                                   true)
                                         true)}
                       {:name "privateSpecIdsInThisWorkspace"
                        :expr '(if-value specIds
                                         (if-value workspaceName
                                                   (= 0 (count (filter [si specIds]
                                                                       (not= (get si :workspaceName) workspaceName))))
                                                   true)
                                         true)}
                       {:name "uniqueNotebookNames"
                        :expr '(if-value notebooks
                                         (= (count (map [n notebooks]
                                                        (get n :name)))
                                            (count notebooks))
                                         true)}
                       {:name "uniqueTestNames"
                        :expr '(if-value tests
                                         (= (count (map [t tests]
                                                        (get t :notebookName)))
                                            (count tests))
                                         true)}
                       {:name "registryHeaderNotebookExists"
                        :expr '(if-value registryHeaderNotebookName
                                         (if-value notebooks
                                                   (> (count (filter [nb notebooks]
                                                                     (= (get nb :name) registryHeaderNotebookName)))
                                                      0)
                                                   false)
                                         true)}
                       {:name "registryHeaderNotebookCannotContainNewNonEphemeralSpecs"
                        :expr
                        '(if-value registryHeaderNotebookName
                                   (if-value notebooks
                                             (let [filtered (filter [nb notebooks]
                                                                    (= (get nb :name) registryHeaderNotebookName))]
                                               (if (> (count filtered) 0)
                                                 (let [nb (first filtered)
                                                       items (get nb :items)]
                                                   (if-value items
                                                             (= (count (filter [ns (map [item (filter [item items]
                                                                                                      (refines-to? item :tutorials.notebook/NewSpec$v1))]
                                                                                        (refine-to item :tutorials.notebook/NewSpec$v1))]
                                                                               (let [is-ephemeral (get ns :isEphemeral)]
                                                                                 (if-value is-ephemeral false true))))
                                                                0)
                                                             true))
                                                 true))
                                             true)
                                   true)}}}}}

     "An example of a valid workspace instance."
     {:code '{:$type :tutorials.notebook/Workspace$v1
              :workspaceName "my"
              :registrySpecIds #{}
              :specIds #{{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 1}
                         {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}
                         {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 2}}
              :notebooks #{{:$type :tutorials.notebook/Notebook$v1
                            :name "notebook1"
                            :version 1
                            :items []}}
              :tests #{}}}

     "Example workspace instance that violates a spec id constraint."
     {:code '{:$type :tutorials.notebook/Workspace$v1
              :workspaceName "my"
              :registrySpecIds #{}
              :specIds #{{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 1}
                         {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}
                         {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 2}
                         {:$type :tutorials.notebook/SpecId$v1 :workspaceName "other" :specName "A" :specVersion 2}}
              :notebooks #{{:$type :tutorials.notebook/Notebook$v1
                            :name "notebook1"
                            :version 1
                            :items []}}
              :tests #{}}
      :throws :auto}

     "Example of a workspace that violates a notebook name constraint."
     {:code '{:$type :tutorials.notebook/Workspace$v1
              :workspaceName "my"
              :registrySpecIds #{}
              :specIds #{{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 1}
                         {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}
                         {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 2}}
              :notebooks #{{:$type :tutorials.notebook/Notebook$v1
                            :name "notebook1"
                            :version 1
                            :items []}
                           {:$type :tutorials.notebook/Notebook$v1
                            :name "notebook1"
                            :version 2
                            :items []}}
              :tests #{}}
      :throws :auto}

     "Example of a workspace with a valid registry header notebook."
     {:code '(valid? {:$type :tutorials.notebook/Workspace$v1
                      :workspaceName "my"
                      :registryHeaderNotebookName "notebook1"
                      :registrySpecIds #{}
                      :specIds #{{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 1}
                                 {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}
                                 {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 2}}
                      :notebooks #{{:$type :tutorials.notebook/Notebook$v1
                                    :name "notebook1"
                                    :version 1
                                    :items [{:$type :tutorials.notebook/NewSpec$v1 :workspaceName "my" :specName "A" :specVersion 3 :isEphemeral true}]}}
                      :tests #{}})
      :result true}

     "Example of a workspace with an invalid registry header notebook."
     {:code '{:$type :tutorials.notebook/Workspace$v1
              :workspaceName "my"
              :registryHeaderNotebookName "notebook1"
              :registrySpecIds #{}
              :specIds #{{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 1}
                         {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}
                         {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 2}}
              :notebooks #{{:$type :tutorials.notebook/Notebook$v1
                            :name "notebook1"
                            :version 1
                            :items [{:$type :tutorials.notebook/NewSpec$v1 :workspaceName "my" :specName "A" :specVersion 3}]}}
              :tests #{}}
      :throws :auto}

     "Previously we created a spec to resolve a spec reference. Now we build on that by creating specs which can walk through a sequence of items in a notebook and resolve refs, in context, as they are encountered."
     {:spec-map-merge
      {:tutorials.notebook/ResolveRefsState$v1
       {:fields {:context [:Set :tutorials.notebook/SpecId$v1]
                 :resolved [:Vec :tutorials.notebook/SpecId$v1]}}

       :tutorials.notebook/RSpecIds$v1
       {:fields {:result [:Vec :tutorials.notebook/SpecId$v1]}}

       :tutorials.notebook/ResolveRefsDirect$v1
       {:fields {:specIds [:Set :tutorials.notebook/SpecId$v1]
                 :items [:Vec :tutorials.notebook/AbstractNotebookItem$v1]}
        :refines-to
        {:tutorials.notebook/RSpecIds$v1
         {:name "validRefs"
          :expr
          '{:$type :tutorials.notebook/RSpecIds$v1
            :result (get (reduce [state {:$type :tutorials.notebook/ResolveRefsState$v1
                                         :context specIds
                                         :resolved []}]
                                 [item items]
                                 (if (refines-to? item :tutorials.notebook/NewSpec$v1)
                                   (let [new-spec (refine-to item :tutorials.notebook/NewSpec$v1)]
                                     {:$type :tutorials.notebook/ResolveRefsState$v1
                                      :context (conj (get state :context)
                                                     (refine-to new-spec :tutorials.notebook/SpecId$v1))
                                      :resolved (get state :resolved)})
                                   (let [spec-ref (refine-to item :tutorials.notebook/SpecRef$v1)
                                         resolver {:$type :tutorials.notebook/SpecRefResolver$v1
                                                   :existingSpecIds (get state :context)
                                                   :newSpecs []
                                                   :inputSpecRef spec-ref}
                                         resolved (when (refines-to? resolver :tutorials.notebook/SpecId$v1)
                                                    (refine-to resolver :tutorials.notebook/SpecId$v1))]
                                     {:$type :tutorials.notebook/ResolveRefsState$v1
                                      :context (get state :context)
                                      :resolved (if-value resolved
                                                          (conj (get state :resolved) resolved)
                                                          (get state :resolved))})))
                         :resolved)}}}}

       :tutorials.notebook/ResolveRefs$v1
       {:fields {:specIds [:Set :tutorials.notebook/SpecId$v1]
                 :items [:Vec :tutorials.notebook/AbstractNotebookItem$v1]}
        :constraints #{{:name "allResolve"
                        :expr '(= (count (filter [item items]
                                                 (refines-to? item :tutorials.notebook/SpecRef$v1)))
                                  (count (get (refine-to {:$type :tutorials.notebook/ResolveRefsDirect$v1
                                                          :specIds specIds
                                                          :items items}
                                                         :tutorials.notebook/RSpecIds$v1)
                                              :result)))}}
        :refines-to {:tutorials.notebook/RSpecIds$v1
                     {:name "resolve"
                      :expr '(refine-to {:$type :tutorials.notebook/ResolveRefsDirect$v1
                                         :specIds specIds
                                         :items (filter [item items]
                                                        (if (refines-to? item :tutorials.notebook/SpecRef$v1)
                                                          (let [spec-ref (refine-to item :tutorials.notebook/SpecRef$v1)
                                                                spec-version (get spec-ref :specVersion)]
                                                            (if-value spec-version
                                                                      false
                                                                      true))
                                                          true))}
                                        :tutorials.notebook/RSpecIds$v1)}}}}}

     "If the instance can be created, then the references are valid. A degenerate case of no items is valid."
     {:code '(valid? {:$type :tutorials.notebook/ResolveRefs$v1
                      :specIds #{{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 1}
                                 {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}
                                 {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 2}}

                      :items []})
      :result true}

     "A fixed reference in a list of items is valid."
     {:code '(valid? {:$type :tutorials.notebook/ResolveRefs$v1
                      :specIds #{{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 1}
                                 {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}
                                 {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 2}}
                      :items [{:$type :tutorials.notebook/SpecRef$v1 :workspaceName "my" :specName "A" :specVersion 1}]})
      :result true}

     "Creating a spec and then referencing it in a later item is valid."
     {:code '(valid? {:$type :tutorials.notebook/ResolveRefs$v1
                      :specIds #{{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 1}
                                 {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}
                                 {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 2}}
                      :items [{:$type :tutorials.notebook/NewSpec$v1 :workspaceName "my" :specName "A" :specVersion 3}
                              {:$type :tutorials.notebook/SpecRef$v1 :workspaceName "my" :specName "A" :specVersion 3}]})
      :result true}

     "Referencing a new spec before it is created is not valid."
     {:code '(valid? {:$type :tutorials.notebook/ResolveRefs$v1
                      :specIds #{{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 1}
                                 {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}
                                 {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 2}}
                      :items [{:$type :tutorials.notebook/SpecRef$v1 :workspaceName "my" :specName "A" :specVersion 3}
                              {:$type :tutorials.notebook/NewSpec$v1 :workspaceName "my" :specName "A" :specVersion 3}]})
      :result false}

     "If the instance is valid, then the refinement can be invoked to produce the sequence of resolved references from the items."
     {:code '(get (refine-to {:$type :tutorials.notebook/ResolveRefs$v1
                              :specIds #{{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 1}
                                         {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}
                                         {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 2}}
                              :items [{:$type :tutorials.notebook/SpecRef$v1 :workspaceName "my" :specName "A"}
                                      {:$type :tutorials.notebook/NewSpec$v1 :workspaceName "my" :specName "A" :specVersion 3}
                                      {:$type :tutorials.notebook/SpecRef$v1 :workspaceName "my" :specName "A"}]}
                             :tutorials.notebook/RSpecIds$v1)
                  :result)
      :result [{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 2}
               {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 3}]}

     "When new specs are created in items, they must be numbered in a way that corresponds to their context. This allows a CAS check to be performed to ensure the specs have not been modified under the notebook. The following spec is for assessing whether a sequence of new specs are properly numbered in context both of each other and the existing specs."
     {:spec-map-merge
      {:tutorials.notebook/ApplicableNewSpecs$v1
       {:fields {:workspaceName :String
                 :specIds [:Set :tutorials.notebook/SpecId$v1]
                 :newSpecs [:Vec :tutorials.notebook/NewSpec$v1]}
        :constraints
        #{{:name "newSpecsInThisWorkspace"
           :expr '(= 0 (count (filter [ns newSpecs]
                                      (not (= (get ns :workspaceName) workspaceName)))))}
          {:name "newSpecsInOrder"
           :expr
           '(let [all-spec-names (reduce [a #{}] [ns newSpecs]
                                         (if (contains? a [(get ns :workspaceName) (get ns :specName)])
                                           a
                                           (conj a [(get ns :workspaceName) (get ns :specName)])))]
              (every? [n all-spec-names]
                      (let [max-version (get (refine-to
                                              {:$type :tutorials.notebook/FMaxSpecVersion$v1
                                               :specIds specIds
                                               :workspaceName (get n 0)
                                               :specName (get n 1)}
                                              :tutorials.notebook/RInteger$v1)
                                             :result)
                            versions (concat [(if-value max-version max-version 0)]
                                             (map [ns (filter [ns newSpecs]
                                                              (and (= (get n 0) (get ns :workspaceName))
                                                                   (= (get n 1) (get ns :specName))))]
                                                  (get ns :specVersion)))]
                        (every? [pair (map [i (range 0 (dec (count versions)))]
                                           [(get versions i)
                                            (get versions (inc i))])]
                                (= (inc (get pair 0)) (get pair 1))))))}
          {:name "nonEphemeralBuiltOnNonEphemeral"
           :expr
           '(let [all-spec-names (reduce [a #{}] [ns newSpecs]
                                         (if (contains? a [(get ns :workspaceName) (get ns :specName)])
                                           a
                                           (conj a [(get ns :workspaceName) (get ns :specName)])))]
              (every? [n all-spec-names]
                      (let [ephemeral-values (concat [false]
                                                     (map [ns (filter [ns newSpecs]
                                                                      (and (= (get n 0) (get ns :workspaceName))
                                                                           (= (get n 1) (get ns :specName))))]
                                                          (let [is-e (get ns :isEphemeral)]
                                                            (if-value is-e true false))))]
                        (every? [pair (map [i (range 0 (dec (count ephemeral-values)))]
                                           [(get ephemeral-values i)
                                            (get ephemeral-values (inc i))])]
                                (or (get pair 1)
                                    (not (get pair 0)))))))}}}}}

     "The new spec in this instance is not numbered to be the next sequential version of the existing spec."
     {:code '(valid? {:$type :tutorials.notebook/ApplicableNewSpecs$v1
                      :workspaceName "my"
                      :specIds #{{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 1}
                                 {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}
                                 {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 2}}
                      :newSpecs [{:$type :tutorials.notebook/NewSpec$v1 :workspaceName "my" :specName "A" :specVersion 4}]})
      :result false}

     "An initial version for a spec whose name does not yet exist, needs to be '1'."
     {:code '(valid? {:$type :tutorials.notebook/ApplicableNewSpecs$v1
                      :workspaceName "my"
                      :specIds #{{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 1}
                                 {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}
                                 {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 2}}
                      :newSpecs [{:$type :tutorials.notebook/NewSpec$v1 :workspaceName "my" :specName "C" :specVersion 4}]})
      :result false}

     "This new spec would clobber an existing spec version."
     {:code '(valid? {:$type :tutorials.notebook/ApplicableNewSpecs$v1
                      :workspaceName "my"
                      :specIds #{{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 1}
                                 {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}
                                 {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 2}}
                      :newSpecs [{:$type :tutorials.notebook/NewSpec$v1 :workspaceName "my" :specName "A" :specVersion 2}]})
      :result false}

     "This example has all valid new specs."
     {:code '(valid? {:$type :tutorials.notebook/ApplicableNewSpecs$v1
                      :workspaceName "my"
                      :specIds #{{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 1}
                                 {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}
                                 {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 2}}
                      :newSpecs [{:$type :tutorials.notebook/NewSpec$v1 :workspaceName "my" :specName "A" :specVersion 3}
                                 {:$type :tutorials.notebook/NewSpec$v1 :workspaceName "my" :specName "B" :specVersion 2}
                                 {:$type :tutorials.notebook/NewSpec$v1 :workspaceName "my" :specName "A" :specVersion 4}
                                 {:$type :tutorials.notebook/NewSpec$v1 :workspaceName "my" :specName "C" :specVersion 1}]})
      :result true}

     "An ephemeral spec can be created on top of an existing spec."
     {:code '(valid? {:$type :tutorials.notebook/ApplicableNewSpecs$v1
                      :workspaceName "my"
                      :specIds #{{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 1}}
                      :newSpecs [{:$type :tutorials.notebook/NewSpec$v1 :workspaceName "my" :specName "A" :specVersion 2 :isEphemeral true}]})
      :result true}

     "An ephemeral new spec can be created on top of a non-ephemeral new spec."
     {:code '(valid? {:$type :tutorials.notebook/ApplicableNewSpecs$v1
                      :workspaceName "my"
                      :specIds #{}
                      :newSpecs [{:$type :tutorials.notebook/NewSpec$v1 :workspaceName "my" :specName "C" :specVersion 1}
                                 {:$type :tutorials.notebook/NewSpec$v1 :workspaceName "my" :specName "C" :specVersion 2 :isEphemeral true}]})
      :result true}

     "Cannot create an non-ephemeral spec on top of an ephemeral spec."
     {:code '(valid? {:$type :tutorials.notebook/ApplicableNewSpecs$v1
                      :workspaceName "my"
                      :specIds #{}
                      :newSpecs [{:$type :tutorials.notebook/NewSpec$v1 :workspaceName "my" :specName "C" :specVersion 1 :isEphemeral true}
                                 {:$type :tutorials.notebook/NewSpec$v1 :workspaceName "my" :specName "C" :specVersion 2}]})
      :result false}

     "Now start to model operations on workspaces. As a result of these operations, the model will indicate some external side effects that are to be applied. The following specs model the side effects."
     {:spec-map-merge
      ;; use array-map to maintain order when loading specs through the api
      (array-map :tutorials.notebook/Effect$v1
                 {:abstract? true}

                 :tutorials.notebook/WriteSpecEffect$v1
                 {:fields {:specId :tutorials.notebook/SpecId$v1}
                  :refines-to {:tutorials.notebook/Effect$v1
                               {:name "effect"
                                :expr '{:$type :tutorials.notebook/Effect$v1}}}}

                 :tutorials.notebook/WriteNotebookEffect$v1
                 {:fields {:notebookName :String
                           :notebookVersion :Integer}
                  :refines-to {:tutorials.notebook/Effect$v1
                               {:name "effect"
                                :expr '{:$type :tutorials.notebook/Effect$v1}}}}

                 :tutorials.notebook/DeleteNotebookEffect$v1
                 {:fields {:notebookName :String
                           :notebookVersion :Integer}
                  :refines-to {:tutorials.notebook/Effect$v1
                               {:name "effect"
                                :expr '{:$type :tutorials.notebook/Effect$v1}}}}

                 :tutorials.notebook/WriteRegressionTestEffect$v1
                 {:fields {:notebookName :String
                           :notebookVersion :Integer}
                  :refines-to {:tutorials.notebook/Effect$v1
                               {:name "effect"
                                :expr '{:$type :tutorials.notebook/Effect$v1}}}}

                 :tutorials.notebook/DeleteRegressionTestEffect$v1
                 {:fields {:notebookName :String
                           :notebookVersion :Integer}
                  :refines-to {:tutorials.notebook/Effect$v1
                               {:name "effect"
                                :expr '{:$type :tutorials.notebook/Effect$v1}}}}

                 :tutorials.notebook/RunTestsEffect$v1
                 {:fields {:notebookName :String
                           :notebookVersion :Integer
                           :registrySpecs [:Maybe :Boolean]
                           :workspaceSpecs [:Maybe :Boolean]}
                  :constraints #{{:name "registrySpecsFlag"
                                  :expr '(if-value registrySpecs
                                                   registrySpecs
                                                   true)}
                                 {:name "workspaceSpecsFlag"
                                  :expr '(if-value workspaceSpecs
                                                   workspaceSpecs
                                                   true)}
                                 {:name "exclusiveFlags"
                                  :expr '(and (or (if-value registrySpecs registrySpecs false)
                                                  (if-value workspaceSpecs workspaceSpecs false))
                                              (not (and (if-value registrySpecs registrySpecs false)
                                                        (if-value workspaceSpecs workspaceSpecs false))))}}
                  :refines-to {:tutorials.notebook/Effect$v1
                               {:name "effect"
                                :expr '{:$type :tutorials.notebook/Effect$v1}}}})}

     "The result of applying an operation to a workspace is a new workspace and any effects to be applied."
     {:spec-map-merge {:tutorials.notebook/WorkspaceAndEffects$v1
                       {:fields {:workspace :tutorials.notebook/Workspace$v1
                                 :effects [:Vec :tutorials.notebook/Effect$v1]}}}}

     "The following specs define the operations involving notebooks in workspaces."
     {:spec-map-merge {:tutorials.notebook/WriteNotebook$v1
                       {:fields {:workspaceRegistryHeaderNotebookName [:Maybe :String]
                                 :workspaceNotebooks [:Set :tutorials.notebook/Notebook$v1]
                                 :notebookName :String
                                 :notebookVersion :Integer}

                        :constraints
                        #{{:name "positiveVersion"
                           :expr '(valid? {:$type :tutorials.notebook/Version$v1
                                           :version notebookVersion})}
                          {:name "priorNotebookDoesNotExist"
                           :expr
                           '(if (= notebookVersion 1)
                              (let [filtered (filter [nb workspaceNotebooks]
                                                     (= (get nb :name)
                                                        notebookName))]
                                (= (count filtered) 0))
                              true)}
                          {:name "priorNotebookExists"
                           :expr
                           '(if (> notebookVersion 1)
                              (let [filtered (filter [nb workspaceNotebooks]
                                                     (and (= (get nb :name)
                                                             notebookName)
                                                          (= (get nb :version)
                                                             (dec notebookVersion))))]
                                (= (count filtered) 1))
                              true)}
                          {:name "validWorkspace"
                           :expr
                           '(valid? {:$type :tutorials.notebook/Workspace$v1
                                     :registryHeaderNotebookName workspaceRegistryHeaderNotebookName
                                     :notebooks workspaceNotebooks})}}
                        :refines-to {:tutorials.notebook/WorkspaceAndEffects$v1
                                     {:name "newWorkspaceAndEffects"
                                      :expr
                                      '{:$type :tutorials.notebook/WorkspaceAndEffects$v1
                                        :workspace {:$type :tutorials.notebook/Workspace$v1
                                                    :notebooks (conj (filter [nb workspaceNotebooks]
                                                                             (not= (get nb :name) notebookName))
                                                                     {:$type :tutorials.notebook/Notebook$v1
                                                                      :name notebookName
                                                                      :version notebookVersion})}
                                        :effects (let [effects (conj (if (> notebookVersion 1)
                                                                       [{:$type :tutorials.notebook/DeleteNotebookEffect$v1
                                                                         :notebookName notebookName
                                                                         :notebookVersion (dec notebookVersion)}]
                                                                       [])
                                                                     {:$type :tutorials.notebook/WriteNotebookEffect$v1
                                                                      :notebookName notebookName
                                                                      :notebookVersion notebookVersion})]
                                                   (if-value workspaceRegistryHeaderNotebookName
                                                             (if (= notebookName workspaceRegistryHeaderNotebookName)
                                                               (conj effects
                                                                     {:$type :tutorials.notebook/RunTestsEffect$v1
                                                                      :notebookName notebookName
                                                                      :notebookVersion notebookVersion
                                                                      :registrySpecs true})
                                                               effects)
                                                             effects))}}}}

                       :tutorials.notebook/DeleteNotebook$v1
                       {:fields {:workspaceRegistryHeaderNotebookName [:Maybe :String]
                                 :workspaceNotebooks [:Set :tutorials.notebook/Notebook$v1]
                                 :notebookName :String
                                 :notebookVersion :Integer}

                        :constraints
                        #{{:name "positiveVersion"
                           :expr '(valid? {:$type :tutorials.notebook/Version$v1
                                           :version notebookVersion})}
                          {:name "notebookExists"
                           :expr
                           '(= (count (filter [nb workspaceNotebooks]
                                              (and (= (get nb :name)
                                                      notebookName)
                                                   (= (get nb :version)
                                                      notebookVersion)))) 1)}
                          {:name "notebookNotRegistryHeader"
                           :expr
                           '(if-value workspaceRegistryHeaderNotebookName
                                      (not= notebookName workspaceRegistryHeaderNotebookName)
                                      true)}
                          {:name "validWorkspace"
                           :expr
                           '(valid? {:$type :tutorials.notebook/Workspace$v1
                                     :registryHeaderNotebookName workspaceRegistryHeaderNotebookName
                                     :notebooks workspaceNotebooks})}}
                        :refines-to {:tutorials.notebook/WorkspaceAndEffects$v1
                                     {:name "newWorkspaceAndEffects"
                                      :expr
                                      '{:$type :tutorials.notebook/WorkspaceAndEffects$v1
                                        :workspace {:$type :tutorials.notebook/Workspace$v1
                                                    :notebooks (filter [nb workspaceNotebooks]
                                                                       (or (not (= (get nb :name) notebookName))
                                                                           (not (= (get nb :version) notebookVersion))))}
                                        :effects [{:$type :tutorials.notebook/DeleteNotebookEffect$v1
                                                   :notebookName notebookName
                                                   :notebookVersion notebookVersion}]}}}}

                       :tutorials.notebook/ApplyNotebook$v1
                       {:fields {:workspaceName :String
                                 :workspaceRegistrySpecIds [:Set :tutorials.notebook/SpecId$v1]
                                 :workspaceSpecIds [:Set :tutorials.notebook/SpecId$v1]
                                 :workspaceNotebooks [:Set :tutorials.notebook/Notebook$v1]
                                 :notebookName :String
                                 :notebookVersion :Integer}
                        :constraints
                        #{{:name "validWorkspace"
                           :expr
                           '(valid? {:$type :tutorials.notebook/Workspace$v1
                                     :workspaceName workspaceName
                                     :registrySpecIds workspaceRegistrySpecIds
                                     :specIds workspaceSpecIds
                                     :notebooks workspaceNotebooks})}
                          {:name "notebookExists"
                           :expr
                           '(> (count (filter [nb workspaceNotebooks]
                                              (and (= (get nb :name)
                                                      notebookName)
                                                   (= (get nb :version)
                                                      notebookVersion))))
                               0)}
                          {:name "notebookContainsItems"
                           :expr
                           '(let [filtered (filter [nb workspaceNotebooks]
                                                   (and (= (get nb :name)
                                                           notebookName)
                                                        (= (get nb :version)
                                                           notebookVersion)))]
                              (if (> (count filtered) 0)
                                (let [nb (first filtered)
                                      items (get nb :items)]
                                  (if-value items true false))
                                true))}
                          {:name "notebookContainsNonEphemeralNewSpecs"
                           :expr
                           '(let [filtered (filter [nb workspaceNotebooks]
                                                   (and (= (get nb :name)
                                                           notebookName)
                                                        (= (get nb :version)
                                                           notebookVersion)))]
                              (if (> (count filtered) 0)
                                (let [nb (first filtered)
                                      items (get nb :items)]
                                  (if-value items
                                            (> (count (filter [ns (map [item (filter [item items]
                                                                                     (refines-to? item :tutorials.notebook/NewSpec$v1))]
                                                                       (refine-to item :tutorials.notebook/NewSpec$v1))]
                                                              (let [is-ephemeral (get ns :isEphemeral)]
                                                                (if-value is-ephemeral false true))))
                                               0)
                                            true))
                                true))}
                          {:name "specsApplicable"
                           :expr
                           '(let [filtered (filter [nb workspaceNotebooks]
                                                   (and (= (get nb :name)
                                                           notebookName)
                                                        (= (get nb :version)
                                                           notebookVersion)))]
                              (if (> (count filtered) 0)
                                (let [nb (first filtered)
                                      items (get nb :items)]
                                  (if-value items
                                            (valid? {:$type :tutorials.notebook/ApplicableNewSpecs$v1
                                                     :workspaceName workspaceName
                                                     :specIds (concat workspaceSpecIds workspaceRegistrySpecIds)
                                                     :newSpecs (map [item (filter [item items]
                                                                                  (refines-to? item :tutorials.notebook/NewSpec$v1))]
                                                                    (refine-to item :tutorials.notebook/NewSpec$v1))})
                                            true))
                                true))}
                          {:name "specsValidRefs"
                           :expr
                           '(let [filtered (filter [nb workspaceNotebooks]
                                                   (and (= (get nb :name)
                                                           notebookName)
                                                        (= (get nb :version)
                                                           notebookVersion)))]
                              (if (> (count filtered) 0)
                                (let [nb (first filtered)
                                      items (get nb :items)]
                                  (if-value items
                                            (valid? {:$type :tutorials.notebook/ResolveRefs$v1
                                                     :specIds (concat workspaceSpecIds workspaceRegistrySpecIds)
                                                     :items items})
                                            true))
                                true))}}
                        :refines-to
                        {:tutorials.notebook/WorkspaceAndEffects$v1
                         {:name "newWorkspaceAndEffects"
                          :expr
                          '(let [filtered (filter [nb workspaceNotebooks]
                                                  (and (= (get nb :name)
                                                          notebookName)
                                                       (= (get nb :version)
                                                          notebookVersion)))]
                             (when (> (count filtered) 0)
                               (let [nb (first filtered)
                                     items (get nb :items)]
                                 (when-value items
                                             (let [new-spec-ids (map [ns (filter [ns (map [item (filter [item items]
                                                                                                        (refines-to? item :tutorials.notebook/NewSpec$v1))]
                                                                                          (refine-to item :tutorials.notebook/NewSpec$v1))]
                                                                                 (let [is-ephemeral (get ns :isEphemeral)]
                                                                                   (if-value is-ephemeral false true)))]
                                                                     (refine-to ns :tutorials.notebook/SpecId$v1))
                                                   new-notebook {:$type :tutorials.notebook/Notebook$v1
                                                                 :name (get nb :name)
                                                                 :version (inc (get nb :version))
                                                                 :items (filter [item items]
                                                                                (if (refines-to? item :tutorials.notebook/NewSpec$v1)
                                                                                  (let [ns (refine-to item :tutorials.notebook/NewSpec$v1)
                                                                                        is-ephemeral (get ns :isEphemeral)]
                                                                                    (if-value is-ephemeral true false))
                                                                                  true))}]
                                               {:$type :tutorials.notebook/WorkspaceAndEffects$v1
                                                :workspace {:$type :tutorials.notebook/Workspace$v1
                                                            :specIds (concat workspaceSpecIds new-spec-ids)
                                                            :notebooks (conj (filter [nb workspaceNotebooks]
                                                                                     (or (not= (get nb :name)
                                                                                               notebookName)
                                                                                         (not= (get nb :version)
                                                                                               notebookVersion)))
                                                                             new-notebook)}
                                                :effects (concat (map [si new-spec-ids]
                                                                      {:$type :tutorials.notebook/WriteSpecEffect$v1
                                                                       :specId si})
                                                                 [{:$type :tutorials.notebook/WriteNotebookEffect$v1
                                                                   :notebookName (get new-notebook :name)
                                                                   :notebookVersion (get new-notebook :version)}
                                                                  {:$type :tutorials.notebook/RunTestsEffect$v1
                                                                   :notebookName notebookName
                                                                   :notebookVersion notebookVersion
                                                                   :workspaceSpecs true}])})))))}}}

                       :tutorials.notebook/CreateRegressionTest$v1
                       {:fields {:workspaceRegistrySpecIds [:Set :tutorials.notebook/SpecId$v1]
                                 :workspaceNotebooks [:Set :tutorials.notebook/Notebook$v1]
                                 :workspaceTests [:Set :tutorials.notebook/RegressionTest$v1]
                                 :notebookName :String
                                 :notebookVersion :Integer}
                        :constraints
                        #{{:name "validWorkspace"
                           :expr
                           '(valid? {:$type :tutorials.notebook/Workspace$v1
                                     :registrySpecIds workspaceRegistrySpecIds
                                     :notebooks workspaceNotebooks
                                     :tests workspaceTests})}
                          {:name "notebookExists"
                           :expr
                           '(> (count (filter [nb workspaceNotebooks]
                                              (and (= (get nb :name)
                                                      notebookName)
                                                   (= (get nb :version)
                                                      notebookVersion))))
                               0)}
                          {:name "notebookContainsItems"
                           :expr
                           '(let [filtered (filter [nb workspaceNotebooks]
                                                   (and (= (get nb :name)
                                                           notebookName)
                                                        (= (get nb :version)
                                                           notebookVersion)))]
                              (if (> (count filtered) 0)
                                (let [nb (first filtered)
                                      items (get nb :items)]
                                  (if-value items true false))
                                true))}
                          {:name "notebookCannotContainNewNonEphemeralSpecs"
                           :expr
                           '(let [filtered (filter [nb workspaceNotebooks]
                                                   (and (= (get nb :name)
                                                           notebookName)
                                                        (= (get nb :version)
                                                           notebookVersion)))]
                              (if (> (count filtered) 0)
                                (let [nb (first filtered)
                                      items (get nb :items)]
                                  (if-value items
                                            (= (count (filter [ns (map [item (filter [item items]
                                                                                     (refines-to? item :tutorials.notebook/NewSpec$v1))]
                                                                       (refine-to item :tutorials.notebook/NewSpec$v1))]
                                                              (let [is-ephemeral (get ns :isEphemeral)]
                                                                (if-value is-ephemeral false true))))
                                               0)
                                            true))
                                true))}
                          {:name "specsValidRefs"
                           :expr
                           '(let [filtered (filter [nb workspaceNotebooks]
                                                   (and (= (get nb :name)
                                                           notebookName)
                                                        (= (get nb :version)
                                                           notebookVersion)))]
                              (if (> (count filtered) 0)
                                (let [nb (first filtered)
                                      items (get nb :items)]
                                  (if-value items
                                            (valid? {:$type :tutorials.notebook/ResolveRefs$v1
                                                     :specIds workspaceRegistrySpecIds
                                                     :items items})
                                            true))
                                true))}}
                        :refines-to {:tutorials.notebook/WorkspaceAndEffects$v1
                                     {:name "newWorkspaceAndEffects"
                                      :expr
                                      '(let [filtered (filter [nb workspaceNotebooks]
                                                              (and (= (get nb :name)
                                                                      notebookName)
                                                                   (= (get nb :version)
                                                                      notebookVersion)))]
                                         (when (> (count filtered) 0)
                                           (let [nb (first filtered)
                                                 items (get nb :items)]
                                             (when-value items
                                                         (let [new-test {:$type :tutorials.notebook/RegressionTest$v1
                                                                         :notebookName notebookName
                                                                         :notebookVersion notebookVersion}]
                                                           {:$type :tutorials.notebook/WorkspaceAndEffects$v1
                                                            :workspace {:$type :tutorials.notebook/Workspace$v1
                                                                        :tests (conj workspaceTests new-test)}
                                                            :effects [{:$type :tutorials.notebook/WriteRegressionTestEffect$v1
                                                                       :notebookName (get new-test :notebookName)
                                                                       :notebookVersion (get new-test :notebookVersion)}
                                                                      {:$type :tutorials.notebook/RunTestsEffect$v1
                                                                       :notebookName notebookName
                                                                       :notebookVersion notebookVersion
                                                                       :registrySpecs true}]})))))}}}

                       :tutorials.notebook/DeleteRegressionTest$v1
                       {:fields {:workspaceTests [:Set :tutorials.notebook/RegressionTest$v1]
                                 :notebookName :String
                                 :notebookVersion :Integer}
                        :constraints
                        #{{:name "validWorkspace"
                           :expr
                           '(valid? {:$type :tutorials.notebook/Workspace$v1
                                     :tests workspaceTests})}
                          {:name "testExists"
                           :expr
                           '(> (count (filter [t workspaceTests]
                                              (and (= (get t :notebookName)
                                                      notebookName)
                                                   (= (get t :notebookVersion)
                                                      notebookVersion))))
                               0)}}
                        :refines-to {:tutorials.notebook/WorkspaceAndEffects$v1
                                     {:name "newWorkspaceAndEffects"
                                      :expr
                                      '(let [filtered (filter [t workspaceTests]
                                                              (and (= (get t :notebookName)
                                                                      notebookName)
                                                                   (= (get t :notebookVersion)
                                                                      notebookVersion)))]
                                         (when (> (count filtered) 0)
                                           (let [to-remove (first filtered)]
                                             {:$type :tutorials.notebook/WorkspaceAndEffects$v1
                                              :workspace {:$type :tutorials.notebook/Workspace$v1
                                                          :tests (filter [t workspaceTests]
                                                                         (not= t to-remove))}
                                              :effects [{:$type :tutorials.notebook/DeleteRegressionTestEffect$v1
                                                         :notebookName (get to-remove :notebookName)
                                                         :notebookVersion (get to-remove :notebookVersion)}]})))}}}

                       :tutorials.notebook/UpdateRegressionTest$v1
                       {:fields {:workspaceRegistrySpecIds [:Set :tutorials.notebook/SpecId$v1]
                                 :workspaceNotebooks [:Set :tutorials.notebook/Notebook$v1]
                                 :workspaceTests [:Set :tutorials.notebook/RegressionTest$v1]
                                 :notebookName :String
                                 :notebookVersion :Integer
                                 :lastNotebookVersion :Integer}
                        :constraints
                        #{{:name "validWorkspace"
                           :expr
                           '(valid? {:$type :tutorials.notebook/Workspace$v1
                                     :registrySpecIds workspaceRegistrySpecIds
                                     :notebooks workspaceNotebooks
                                     :tests workspaceTests})}
                          {:name "notebookExists"
                           :expr
                           '(> (count (filter [nb workspaceNotebooks]
                                              (and (= (get nb :name)
                                                      notebookName)
                                                   (= (get nb :version)
                                                      notebookVersion))))
                               0)}
                          {:name "notebookContainsItems"
                           :expr
                           '(let [filtered (filter [nb workspaceNotebooks]
                                                   (and (= (get nb :name)
                                                           notebookName)
                                                        (= (get nb :version)
                                                           notebookVersion)))]
                              (if (> (count filtered) 0)
                                (let [nb (first filtered)
                                      items (get nb :items)]
                                  (if-value items true false))
                                true))}
                          {:name "testExists"
                           :expr
                           '(> (count (filter [t workspaceTests]
                                              (and (= (get t :notebookName)
                                                      notebookName)
                                                   (= (get t :notebookVersion)
                                                      lastNotebookVersion))))
                               0)}
                          {:name "notebookCannotContainNewNonEphemeralSpecs"
                           :expr
                           '(let [filtered (filter [nb workspaceNotebooks]
                                                   (and (= (get nb :name)
                                                           notebookName)
                                                        (= (get nb :version)
                                                           notebookVersion)))]
                              (if (> (count filtered) 0)
                                (let [nb (first filtered)
                                      items (get nb :items)]
                                  (if-value items
                                            (= (count (filter [ns (map [item (filter [item items]
                                                                                     (refines-to? item :tutorials.notebook/NewSpec$v1))]
                                                                       (refine-to item :tutorials.notebook/NewSpec$v1))]
                                                              (let [is-ephemeral (get ns :isEphemeral)]
                                                                (if-value is-ephemeral false true))))
                                               0)
                                            true))
                                true))}
                          {:name "specsValidRefs"
                           :expr
                           '(let [filtered (filter [nb workspaceNotebooks]
                                                   (and (= (get nb :name)
                                                           notebookName)
                                                        (= (get nb :version)
                                                           notebookVersion)))]
                              (if (> (count filtered) 0)
                                (let [nb (first filtered)
                                      items (get nb :items)]
                                  (if-value items
                                            (valid? {:$type :tutorials.notebook/ResolveRefs$v1
                                                     :specIds workspaceRegistrySpecIds
                                                     :items items})
                                            true))
                                true))}}
                        :refines-to {:tutorials.notebook/WorkspaceAndEffects$v1
                                     {:name "newWorkspaceAndEffects"
                                      :expr
                                      '(let [filtered (filter [nb workspaceNotebooks]
                                                              (and (= (get nb :name)
                                                                      notebookName)
                                                                   (= (get nb :version)
                                                                      notebookVersion)))]
                                         (when (> (count filtered) 0)
                                           (let [nb (first filtered)
                                                 items (get nb :items)]
                                             (when-value items
                                                         (let [new-test {:$type :tutorials.notebook/RegressionTest$v1
                                                                         :notebookName notebookName
                                                                         :notebookVersion notebookVersion}]
                                                           {:$type :tutorials.notebook/WorkspaceAndEffects$v1
                                                            :workspace {:$type :tutorials.notebook/Workspace$v1
                                                                        :tests (conj (filter [t workspaceTests]
                                                                                             (not= (get t :notebookName)
                                                                                                   notebookName))
                                                                                     new-test)}
                                                            :effects [{:$type :tutorials.notebook/WriteRegressionTestEffect$v1
                                                                       :notebookName (get new-test :notebookName)
                                                                       :notebookVersion (get new-test :notebookVersion)}
                                                                      {:$type :tutorials.notebook/RunTestsEffect$v1
                                                                       :notebookName notebookName
                                                                       :notebookVersion notebookVersion
                                                                       :registrySpecs true}]})))))}}}}}

     "Exercise the operation of writing a notebook to a workspace."
     {:code
      '(refine-to {:$type :tutorials.notebook/WriteNotebook$v1
                   :workspaceNotebooks #{}
                   :notebookName "notebook1"
                   :notebookVersion 1}
                  :tutorials.notebook/WorkspaceAndEffects$v1)
      :result {:$type :tutorials.notebook/WorkspaceAndEffects$v1
               :workspace {:$type :tutorials.notebook/Workspace$v1
                           :notebooks #{{:$type :tutorials.notebook/Notebook$v1 :name "notebook1" :version 1}}}
               :effects [{:$type :tutorials.notebook/WriteNotebookEffect$v1 :notebookName "notebook1" :notebookVersion 1}]}}
     "Trying to write the first version of a notebook when it already exists fails."
     {:code
      '{:$type :tutorials.notebook/WriteNotebook$v1
        :workspaceNotebooks #{{:$type :tutorials.notebook/Notebook$v1 :name "notebook1" :version 1}}
        :notebookName "notebook1"
        :notebookVersion 1}
      :throws :auto}
     "Writing a new version of a notebook succeeds"
     {:code
      '(refine-to {:$type :tutorials.notebook/WriteNotebook$v1
                   :workspaceNotebooks #{{:$type :tutorials.notebook/Notebook$v1 :name "notebook1" :version 1}}
                   :notebookName "notebook1"
                   :notebookVersion 2}
                  :tutorials.notebook/WorkspaceAndEffects$v1)
      :result {:$type :tutorials.notebook/WorkspaceAndEffects$v1
               :workspace {:$type :tutorials.notebook/Workspace$v1
                           :notebooks #{{:$type :tutorials.notebook/Notebook$v1 :name "notebook1" :version 2}}}
               :effects [{:$type :tutorials.notebook/DeleteNotebookEffect$v1 :notebookName "notebook1" :notebookVersion 1}
                         {:$type :tutorials.notebook/WriteNotebookEffect$v1 :notebookName "notebook1" :notebookVersion 2}]}}

     "Exercise the operation to delete a notebook."
     {:code
      '(refine-to {:$type :tutorials.notebook/DeleteNotebook$v1
                   :workspaceNotebooks #{{:$type :tutorials.notebook/Notebook$v1 :name "notebook1" :version 1 :items []}
                                         {:$type :tutorials.notebook/Notebook$v1 :name "notebook2" :version 1 :items []}}
                   :notebookName "notebook1"
                   :notebookVersion 1}
                  :tutorials.notebook/WorkspaceAndEffects$v1)
      :result {:$type :tutorials.notebook/WorkspaceAndEffects$v1
               :workspace {:$type :tutorials.notebook/Workspace$v1
                           :notebooks #{{:$type :tutorials.notebook/Notebook$v1 :name "notebook2" :version 1 :items []}}}
               :effects [{:$type :tutorials.notebook/DeleteNotebookEffect$v1 :notebookName "notebook1" :notebookVersion 1}]}}

     "Cannot delete a notebook that does not exist."
     {:code
      '{:$type :tutorials.notebook/DeleteNotebook$v1
        :workspaceNotebooks #{{:$type :tutorials.notebook/Notebook$v1 :name "notebook1" :version 1 :items []}
                              {:$type :tutorials.notebook/Notebook$v1 :name "notebook2" :version 1 :items []}}
        :notebookName "notebook1"
        :notebookVersion 12}
      :throws :auto}

     "Exercise the constraints on the operation to apply a notebook. If an operation instance is valid, then the pre-conditions for the operation have been met."
     {:code '(let [sis #{{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 1}
                         {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}
                         {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 2}}
                   nbs #{{:$type :tutorials.notebook/Notebook$v1
                          :name "notebook1"
                          :version 1
                          :items [{:$type :tutorials.notebook/NewSpec$v1 :workspaceName "my" :specName "A" :specVersion 3}]}}]
               [(valid? {:$type :tutorials.notebook/ApplyNotebook$v1
                         :workspaceName "my"
                         :workspaceRegistrySpecIds #{}
                         :workspaceSpecIds sis
                         :workspaceNotebooks nbs
                         :notebookName "notebook1"
                         :notebookVersion 1})
                (valid? {:$type :tutorials.notebook/ApplyNotebook$v1
                         :workspaceName "my"
                         :workspaceRegistrySpecIds #{}
                         :workspaceSpecIds sis
                         :workspaceNotebooks nbs
                         :notebookName "notebook1"
                         :notebookVersion 2})
                (valid? {:$type :tutorials.notebook/ApplyNotebook$v1
                         :workspaceName "my"
                         :workspaceRegistrySpecIds #{}
                         :workspaceSpecIds sis
                         :workspaceNotebooks nbs
                         :notebookName "notebook2"
                         :notebookVersion 1})])
      :result [true false false]}

     "If all of the new specs in the notebooks are ephemeral, then it cannot be applied."
     {:code
      '(let [sis #{{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 1}
                   {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}
                   {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 2}}
             nbs #{{:$type :tutorials.notebook/Notebook$v1
                    :name "notebook1"
                    :version 1
                    :items [{:$type :tutorials.notebook/NewSpec$v1 :workspaceName "my" :specName "A" :specVersion 3 :isEphemeral true}
                            {:$type :tutorials.notebook/SpecRef$v1 :workspaceName "my" :specName "A" :specVersion 1}
                            {:$type :tutorials.notebook/SpecRef$v1 :workspaceName "my" :specName "A" :specVersion 3}]}}]
         (valid? {:$type :tutorials.notebook/ApplyNotebook$v1
                  :workspaceName "my"
                  :workspaceRegistrySpecIds #{}
                  :workspaceSpecIds sis
                  :workspaceNotebooks nbs
                  :notebookName "notebook1"
                  :notebookVersion 1}))
      :result false}

     "If a notebook contains no new specs then it cannot be applied."
     {:code
      '(let [sis #{{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 1}
                   {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}
                   {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 2}}
             nbs #{{:$type :tutorials.notebook/Notebook$v1
                    :name "notebook1"
                    :version 1
                    :items [{:$type :tutorials.notebook/SpecRef$v1 :workspaceName "my" :specName "A" :specVersion 1}]}}]
         (valid? {:$type :tutorials.notebook/ApplyNotebook$v1
                  :workspaceName "my"
                  :workspaceRegistrySpecIds #{}
                  :workspaceSpecIds sis
                  :workspaceNotebooks nbs
                  :notebookName "notebook1"
                  :notebookVersion 1}))
      :result false}

     "A more complicated example of applying a notebook. This one includes an ephemeral new spec as well as a references to a new spec. Note that the effect to run the tests includes the results of resolving all of the floating references in the notebook."
     {:code
      '(refine-to {:$type :tutorials.notebook/ApplyNotebook$v1
                   :workspaceName "my"
                   :workspaceRegistrySpecIds #{}
                   :workspaceSpecIds #{{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 1}
                                       {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}
                                       {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 2}}
                   :workspaceNotebooks #{{:$type :tutorials.notebook/Notebook$v1
                                          :name "notebook1"
                                          :version 1
                                          :items [{:$type :tutorials.notebook/SpecRef$v1 :workspaceName "my" :specName "A"}
                                                  {:$type :tutorials.notebook/NewSpec$v1 :workspaceName "my" :specName "A" :specVersion 3}
                                                  {:$type :tutorials.notebook/NewSpec$v1 :workspaceName "my" :specName "C" :specVersion 1 :isEphemeral true}
                                                  {:$type :tutorials.notebook/SpecRef$v1 :workspaceName "my" :specName "C"}
                                                  {:$type :tutorials.notebook/SpecRef$v1 :workspaceName "my" :specName "A" :specVersion 1}
                                                  {:$type :tutorials.notebook/SpecRef$v1 :workspaceName "my" :specName "A"}
                                                  {:$type :tutorials.notebook/SpecRef$v1 :workspaceName "my" :specName "A" :specVersion 3}]}
                                         {:$type :tutorials.notebook/Notebook$v1
                                          :name "notebook2"
                                          :version 3
                                          :items [{:$type :tutorials.notebook/NewSpec$v1 :workspaceName "my" :specName "B" :specVersion 1}
                                                  {:$type :tutorials.notebook/SpecRef$v1 :workspaceName "my" :specName "A" :specVersion 1}
                                                  {:$type :tutorials.notebook/SpecRef$v1 :workspaceName "my" :specName "B" :specVersion 1}]}}
                   :notebookName "notebook1"
                   :notebookVersion 1}
                  :tutorials.notebook/WorkspaceAndEffects$v1)
      :result {:$type :tutorials.notebook/WorkspaceAndEffects$v1
               :workspace {:$type :tutorials.notebook/Workspace$v1
                           :specIds #{{:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A", :specVersion 1}
                                      {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "B" :specVersion 1}
                                      {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 2}
                                      {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 3}}
                           :notebooks #{{:$type :tutorials.notebook/Notebook$v1
                                         :name "notebook2"
                                         :version 3
                                         :items [{:$type :tutorials.notebook/NewSpec$v1 :workspaceName "my" :specName "B" :specVersion 1}
                                                 {:$type :tutorials.notebook/SpecRef$v1 :workspaceName "my" :specName "A" :specVersion 1}
                                                 {:$type :tutorials.notebook/SpecRef$v1 :workspaceName "my" :specName "B" :specVersion 1}]}
                                        {:$type :tutorials.notebook/Notebook$v1
                                         :name "notebook1"
                                         :version 2
                                         :items [{:$type :tutorials.notebook/SpecRef$v1 :workspaceName "my" :specName "A"}
                                                 {:$type :tutorials.notebook/NewSpec$v1 :workspaceName "my" :specName "C" :specVersion 1 :isEphemeral true}
                                                 {:$type :tutorials.notebook/SpecRef$v1 :workspaceName "my" :specName "C"}
                                                 {:$type :tutorials.notebook/SpecRef$v1 :workspaceName "my" :specName "A" :specVersion 1}
                                                 {:$type :tutorials.notebook/SpecRef$v1 :workspaceName "my" :specName "A"}
                                                 {:$type :tutorials.notebook/SpecRef$v1 :workspaceName "my" :specName "A" :specVersion 3}]}}}
               :effects [{:$type :tutorials.notebook/WriteSpecEffect$v1
                          :specId {:$type :tutorials.notebook/SpecId$v1 :workspaceName "my" :specName "A" :specVersion 3}}
                         {:$type :tutorials.notebook/WriteNotebookEffect$v1 :notebookName "notebook1" :notebookVersion 2}
                         {:$type :tutorials.notebook/RunTestsEffect$v1 :notebookName "notebook1" :notebookVersion 1 :workspaceSpecs true}]}}

     "A notebook can be used as the basis for a regression test suite."
     {:code
      '(refine-to {:$type :tutorials.notebook/CreateRegressionTest$v1
                   :workspaceRegistrySpecIds #{}
                   :workspaceNotebooks #{{:$type :tutorials.notebook/Notebook$v1 :name "notebook1" :version 1 :items []}}
                   :workspaceTests #{}
                   :notebookName "notebook1"
                   :notebookVersion 1}
                  :tutorials.notebook/WorkspaceAndEffects$v1)
      :result {:$type :tutorials.notebook/WorkspaceAndEffects$v1
               :workspace {:$type :tutorials.notebook/Workspace$v1
                           :tests #{{:$type :tutorials.notebook/RegressionTest$v1 :notebookName "notebook1" :notebookVersion 1}}}
               :effects [{:$type :tutorials.notebook/WriteRegressionTestEffect$v1 :notebookName "notebook1" :notebookVersion 1}
                         {:$type :tutorials.notebook/RunTestsEffect$v1 :notebookName "notebook1" :notebookVersion 1 :registrySpecs true}]}}

     "A notebook can be deleted even if it was used to create a regression test."
     {:code
      '(refine-to {:$type :tutorials.notebook/DeleteNotebook$v1
                   :workspaceNotebooks #{{:$type :tutorials.notebook/Notebook$v1 :name "notebook1" :version 1 :items []}}
                   :notebookName "notebook1"
                   :notebookVersion 1}
                  :tutorials.notebook/WorkspaceAndEffects$v1)
      :result {:$type :tutorials.notebook/WorkspaceAndEffects$v1
               :workspace {:$type :tutorials.notebook/Workspace$v1
                           :notebooks #{}}
               :effects [{:$type :tutorials.notebook/DeleteNotebookEffect$v1 :notebookName "notebook1" :notebookVersion 1}]}}

     "The notebook when it is deleted may be a different version than that used to create the regression test."
     {:code
      '(refine-to {:$type :tutorials.notebook/DeleteNotebook$v1
                   :workspaceNotebooks #{{:$type :tutorials.notebook/Notebook$v1 :name "notebook1" :version 2 :items []}}
                   :notebookName "notebook1"
                   :notebookVersion 2}
                  :tutorials.notebook/WorkspaceAndEffects$v1)
      :result {:$type :tutorials.notebook/WorkspaceAndEffects$v1
               :workspace {:$type :tutorials.notebook/Workspace$v1
                           :notebooks #{}}
               :effects [{:$type :tutorials.notebook/DeleteNotebookEffect$v1 :notebookName "notebook1" :notebookVersion 2}]}}

     "Once a notebook is deleted, then when it written again the version numbering starts over. This happens even if it was used as a regression test."
     {:code
      '(refine-to {:$type :tutorials.notebook/WriteNotebook$v1
                   :workspaceNotebooks #{}
                   :notebookName "notebook1"
                   :notebookVersion 1}
                  :tutorials.notebook/WorkspaceAndEffects$v1)
      :result {:$type :tutorials.notebook/WorkspaceAndEffects$v1
               :workspace {:$type :tutorials.notebook/Workspace$v1
                           :notebooks #{{:$type :tutorials.notebook/Notebook$v1, :name "notebook1", :version 1}}}
               :effects [{:$type :tutorials.notebook/WriteNotebookEffect$v1 :notebookName "notebook1" :notebookVersion 1}]}}

     "Only one version at a time of a given notebook name can be used as a regression test."
     {:code
      '(refine-to {:$type :tutorials.notebook/CreateRegressionTest$v1
                   :workspaceRegistrySpecIds #{}
                   :workspaceNotebooks #{{:$type :tutorials.notebook/Notebook$v1 :name "notebook1" :version 2 :items []}}
                   :workspaceTests #{{:$type :tutorials.notebook/RegressionTest$v1 :notebookName "notebook1" :notebookVersion 1}}
                   :notebookName "notebook1"
                   :notebookVersion 2}
                  :tutorials.notebook/WorkspaceAndEffects$v1)
      :throws :auto}

     "A regression test can be updated to reflect a later version of a notebook."
     {:code
      '(refine-to {:$type :tutorials.notebook/UpdateRegressionTest$v1
                   :workspaceRegistrySpecIds #{}
                   :workspaceNotebooks #{{:$type :tutorials.notebook/Notebook$v1 :name "notebook1" :version 9 :items []}}
                   :workspaceTests #{{:$type :tutorials.notebook/RegressionTest$v1 :notebookName "notebook1" :notebookVersion 1}}
                   :notebookName "notebook1"
                   :notebookVersion 9
                   :lastNotebookVersion 1}
                  :tutorials.notebook/WorkspaceAndEffects$v1)
      :result {:$type :tutorials.notebook/WorkspaceAndEffects$v1
               :workspace {:$type :tutorials.notebook/Workspace$v1
                           :tests #{{:$type :tutorials.notebook/RegressionTest$v1 :notebookName "notebook1" :notebookVersion 9}}}
               :effects [{:$type :tutorials.notebook/WriteRegressionTestEffect$v1 :notebookName "notebook1" :notebookVersion 9}
                         {:$type :tutorials.notebook/RunTestsEffect$v1 :notebookName "notebook1" :notebookVersion 9 :registrySpecs true}]}}

     "A regression test can be removed from a workspace."
     {:code
      '(refine-to {:$type :tutorials.notebook/DeleteRegressionTest$v1
                   :workspaceTests #{{:$type :tutorials.notebook/RegressionTest$v1 :notebookName "notebook1" :notebookVersion 9}
                                     {:$type :tutorials.notebook/RegressionTest$v1 :notebookName "notebook2" :notebookVersion 1}}
                   :notebookName "notebook1"
                   :notebookVersion 9}
                  :tutorials.notebook/WorkspaceAndEffects$v1)
      :result {:$type :tutorials.notebook/WorkspaceAndEffects$v1
               :workspace {:$type :tutorials.notebook/Workspace$v1
                           :tests #{{:$type :tutorials.notebook/RegressionTest$v1 :notebookName "notebook2" :notebookVersion 1}}}
               :effects [{:$type :tutorials.notebook/DeleteRegressionTestEffect$v1 :notebookName "notebook1" :notebookVersion 9}]}}]}

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
