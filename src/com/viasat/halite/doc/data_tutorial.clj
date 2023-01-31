;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.doc.data-tutorial)

(set! *warn-on-reflection* true)

(def tutorials
  {:tutorials.notebook/notebook
   {:label "Model a notebook mechanism"
    :contents
    [{:spec-map {:tutorials.notebook/Version$v1
                 {:fields {:version [:Maybe :Integer]}
                  :constraints #{{:name "positiveVersion"
                                  :expr '(if-value version
                                                   (> version 0)
                                                   true)}}}

                 :tutorials.notebook/SpecId$v1
                 {:fields {:specName :String
                           :specVersion :Integer}
                  :constraints #{{:name "positiveVersion"
                                  :expr '(valid? {:$type :tutorials.notebook/Version$v1
                                                  :version specVersion})}}}

                 :tutorials.notebook/AbstractNotebookItem$v1
                 {:abstract? true}

                 :tutorials.notebook/SpecRef$v1
                 {:fields {:specName :String
                           :specVersion [:Maybe :Integer]}
                  :constraints #{{:name "positiveVersion"
                                  :expr '(valid? {:$type :tutorials.notebook/Version$v1
                                                  :version specVersion})}}
                  :refines-to {:tutorials.notebook/SpecId$v1
                               {:name "specId"
                                :expr '(when-value specVersion
                                                   {:$type :tutorials.notebook/SpecId$v1
                                                    :specName specName
                                                    :specVersion specVersion})}

                               :tutorials.notebook/AbstractNotebookItem$v1
                               {:name "abstractItems"
                                :expr '{:$type :tutorials.notebook/AbstractNotebookItem$v1}}}}}}

     {:code '{:$type :tutorials.notebook/SpecRef$v1
              :specName "my/A"
              :specVersion 1}}

     {:code '{:$type :tutorials.notebook/SpecRef$v1
              :specName "my/A"}}

     {:spec-map-merge {:tutorials.notebook/NewSpec$v1
                       {:fields {:specName :String
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
                                              :specName specName
                                              :specVersion specVersion}}

                                     :tutorials.notebook/AbstractNotebookItem$v1
                                     {:name "abstractItems"
                                      :expr '{:$type :tutorials.notebook/AbstractNotebookItem$v1}}}}}}

     {:code '{:$type :tutorials.notebook/NewSpec$v1
              :specName "my/A"
              :specVersion 1}}
     {:code '{:$type :tutorials.notebook/NewSpec$v1
              :specName "my/A"
              :specVersion 0}
      :throws :auto}

     {:code '{:$type :tutorials.notebook/NewSpec$v1
              :specName "my/A"
              :specVersion 1
              :isEphemeral true}}

     {:code '{:$type :tutorials.notebook/NewSpec$v1
              :specName "my/A"
              :specVersion 1
              :isEphemeral false}
      :throws :auto}

     {:code '(refine-to
              {:$type :tutorials.notebook/NewSpec$v1
               :specName "my/A"
               :specVersion 1}
              :tutorials.notebook/SpecId$v1)
      :result {:$type :tutorials.notebook/SpecId$v1
               :specName "my/A"
               :specVersion 1}}

     {:spec-map-merge {:tutorials.notebook/RInteger$v1
                       {:fields {:result [:Maybe :Integer]}}

                       :tutorials.notebook/FMaxSpecVersion$v1
                       {:fields {:specIds [:Vec :tutorials.notebook/SpecId$v1]
                                 :specName :String}
                        :refines-to {:tutorials.notebook/RInteger$v1
                                     {:name "result"
                                      :expr '{:$type :tutorials.notebook/RInteger$v1
                                              :result (let [result (reduce [a 0] [si specIds]
                                                                           (cond
                                                                             (not= (get si :specName) specName)
                                                                             a

                                                                             (> (get si :specVersion) a)
                                                                             (get si :specVersion)

                                                                             a))]
                                                        (when (not= 0 result)
                                                          result))}}}}}}
     {:code '(refine-to
              {:$type :tutorials.notebook/FMaxSpecVersion$v1
               :specIds [{:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 1}
                         {:$type :tutorials.notebook/SpecId$v1 :specName "my/B" :specVersion 1}
                         {:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 2}]
               :specName "my/A"}
              :tutorials.notebook/RInteger$v1)
      :result {:$type :tutorials.notebook/RInteger$v1 :result 2}}
     {:code '(refine-to
              {:$type :tutorials.notebook/FMaxSpecVersion$v1
               :specIds [{:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 1}
                         {:$type :tutorials.notebook/SpecId$v1 :specName "my/B" :specVersion 1}
                         {:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 2}]
               :specName "my/B"}
              :tutorials.notebook/RInteger$v1)
      :result {:$type :tutorials.notebook/RInteger$v1 :result 1}}
     {:code '(refine-to
              {:$type :tutorials.notebook/FMaxSpecVersion$v1
               :specIds [{:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 1}
                         {:$type :tutorials.notebook/SpecId$v1 :specName "my/B" :specVersion 1}
                         {:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 2}]
               :specName "my/C"}
              :tutorials.notebook/RInteger$v1)
      :result {:$type :tutorials.notebook/RInteger$v1}}

     {:spec-map-merge
      {:tutorials.notebook/SpecRefResolver$v1
       {:fields {:existingSpecIds [:Vec :tutorials.notebook/SpecId$v1]
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
                                                                     :specName (get inputSpecRef :specName)}
                                                                    :tutorials.notebook/RInteger$v1)
                                                         :result)]
                         (when-value max-version-in-context
                                     {:$type :tutorials.notebook/SpecId$v1
                                      :specName (get inputSpecRef :specName)
                                      :specVersion max-version-in-context}))))}}}}}

     {:code '(refines-to? {:$type :tutorials.notebook/SpecRefResolver$v1
                           :existingSpecIds []
                           :newSpecs []
                           :inputSpecRef {:$type :tutorials.notebook/SpecRef$v1
                                          :specName "my/A"
                                          :specVersion 1}}
                          :tutorials.notebook/SpecId$v1)
      :result false}

     {:code '(refine-to {:$type :tutorials.notebook/SpecRefResolver$v1
                         :existingSpecIds [{:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 1}
                                           {:$type :tutorials.notebook/SpecId$v1 :specName "my/B" :specVersion 1}
                                           {:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 2}]
                         :newSpecs [{:$type :tutorials.notebook/NewSpec$v1
                                     :specName "my/C"
                                     :specVersion 1
                                     :isEphemeral true}
                                    {:$type :tutorials.notebook/NewSpec$v1
                                     :specName "my/A"
                                     :specVersion 3}]
                         :inputSpecRef {:$type :tutorials.notebook/SpecRef$v1
                                        :specName "my/A"}}
                        :tutorials.notebook/SpecId$v1)
      :result {:$type :tutorials.notebook/SpecId$v1, :specName "my/A", :specVersion 3}}

     {:code '(refine-to {:$type :tutorials.notebook/SpecRefResolver$v1
                         :existingSpecIds [{:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 1}
                                           {:$type :tutorials.notebook/SpecId$v1 :specName "my/B" :specVersion 1}
                                           {:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 2}]
                         :newSpecs [{:$type :tutorials.notebook/NewSpec$v1
                                     :specName "my/C"
                                     :specVersion 1
                                     :isEphemeral true}
                                    {:$type :tutorials.notebook/NewSpec$v1
                                     :specName "my/A"
                                     :specVersion 3}]
                         :inputSpecRef {:$type :tutorials.notebook/SpecRef$v1
                                        :specName "my/B"}}
                        :tutorials.notebook/SpecId$v1)
      :result {:$type :tutorials.notebook/SpecId$v1, :specName "my/B", :specVersion 1}}

     {:code '(refine-to {:$type :tutorials.notebook/SpecRefResolver$v1
                         :existingSpecIds [{:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 1}
                                           {:$type :tutorials.notebook/SpecId$v1 :specName "my/B" :specVersion 1}
                                           {:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 2}]
                         :newSpecs [{:$type :tutorials.notebook/NewSpec$v1
                                     :specName "my/C"
                                     :specVersion 1
                                     :isEphemeral true}
                                    {:$type :tutorials.notebook/NewSpec$v1
                                     :specName "my/A"
                                     :specVersion 3}]
                         :inputSpecRef {:$type :tutorials.notebook/SpecRef$v1
                                        :specName "my/B"
                                        :specVersion 1}}
                        :tutorials.notebook/SpecId$v1)
      :result {:$type :tutorials.notebook/SpecId$v1, :specName "my/B", :specVersion 1}}
     {:code '(refine-to {:$type :tutorials.notebook/SpecRefResolver$v1
                         :existingSpecIds [{:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 1}
                                           {:$type :tutorials.notebook/SpecId$v1 :specName "my/B" :specVersion 1}
                                           {:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 2}]
                         :newSpecs [{:$type :tutorials.notebook/NewSpec$v1
                                     :specName "my/C"
                                     :specVersion 1
                                     :isEphemeral true}
                                    {:$type :tutorials.notebook/NewSpec$v1
                                     :specName "my/A"
                                     :specVersion 3}]
                         :inputSpecRef {:$type :tutorials.notebook/SpecRef$v1
                                        :specName "my/C"
                                        :specVersion 1}}
                        :tutorials.notebook/SpecId$v1)
      :result {:$type :tutorials.notebook/SpecId$v1, :specName "my/C", :specVersion 1}}

     {:code '(refines-to? {:$type :tutorials.notebook/SpecRefResolver$v1
                           :existingSpecIds [{:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 1}
                                             {:$type :tutorials.notebook/SpecId$v1 :specName "my/B" :specVersion 1}
                                             {:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 2}]
                           :newSpecs [{:$type :tutorials.notebook/NewSpec$v1
                                       :specName "my/C"
                                       :specVersion 1
                                       :isEphemeral true}
                                      {:$type :tutorials.notebook/NewSpec$v1
                                       :specName "my/A"
                                       :specVersion 3}]
                           :inputSpecRef {:$type :tutorials.notebook/SpecRef$v1
                                          :specName "my/C"
                                          :specVersion 2}}
                          :tutorials.notebook/SpecId$v1)
      :result false}
     {:code '(refines-to? {:$type :tutorials.notebook/SpecRefResolver$v1
                           :existingSpecIds [{:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 1}
                                             {:$type :tutorials.notebook/SpecId$v1 :specName "my/B" :specVersion 1}
                                             {:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 2}]
                           :newSpecs [{:$type :tutorials.notebook/NewSpec$v1
                                       :specName "my/C"
                                       :specVersion 1
                                       :isEphemeral true}
                                      {:$type :tutorials.notebook/NewSpec$v1
                                       :specName "my/A"
                                       :specVersion 3}]
                           :inputSpecRef {:$type :tutorials.notebook/SpecRef$v1
                                          :specName "my/X"
                                          :specVersion 1}}
                          :tutorials.notebook/SpecId$v1)
      :result false}

     "Make a spec to hold the result of resolving all spec references in a notebook."
     {:spec-map-merge {:tutorials.notebook/ResolvedSpecRefs$v1
                       {:fields {:specRefs [:Vec :tutorials.notebook/SpecId$v1]}}}}

     "A notebook contains spec references. This is modeled as the results of having parsed the references out of the contents of the notebook."
     {:spec-map-merge {:tutorials.notebook/Notebook$v1
                       {:fields {:name :String
                                 :version :Integer
                                 :items [:Vec :tutorials.notebook/AbstractNotebookItem$v1]}
                        :constraints #{{:name "positiveVersion"
                                        :expr '(valid? {:$type :tutorials.notebook/Version$v1
                                                        :version version})}}}

                       :tutorials.notebook/RegressionTest$v1
                       {:fields {:notebookName :String
                                 :notebookVersion :Integer}
                        :constraints #{{:name "positiveVersion"
                                        :expr '(valid? {:$type :tutorials.notebook/Version$v1
                                                        :version notebookVersion})}}}

                       :tutorials.notebook/Workspace$v1
                       {:fields {:registrySpecIds [:Vec :tutorials.notebook/SpecId$v1]
                                 :specIds [:Vec :tutorials.notebook/SpecId$v1]
                                 :notebooks [:Vec :tutorials.notebook/Notebook$v1]
                                 :tests [:Vec :tutorials.notebook/RegressionTest$v1]}
                        :constraints #{{:name "uniqueSpecIds"
                                        :expr '(= (count (concat #{} specIds))
                                                  (count specIds))}
                                       {:name "uniqueRegistrySpecIds"
                                        :expr '(= (count (concat #{} registrySpecIds))
                                                  (count registrySpecIds))}
                                       {:name "specIdsDisjoint"
                                        :expr '(= 0 (count (intersection (concat #{} registrySpecIds)
                                                                         (concat #{} specIds))))}
                                       {:name "uniqueNotebookNames"
                                        :expr '(= (count (concat #{} (map [n notebooks]
                                                                          (get n :name))))
                                                  (count notebooks))}
                                       {:name "uniqueTestNames"
                                        :expr '(= (count (concat #{} (map [t tests]
                                                                          (get t :notebookName))))
                                                  (count tests))}}}}}

     {:code '{:$type :tutorials.notebook/Workspace$v1
              :registrySpecIds []
              :specIds [{:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 1}
                        {:$type :tutorials.notebook/SpecId$v1 :specName "my/B" :specVersion 1}
                        {:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 2}]
              :notebooks [{:$type :tutorials.notebook/Notebook$v1
                           :name "notebook1"
                           :version 1
                           :items []}]
              :tests []}}
     {:code '{:$type :tutorials.notebook/Workspace$v1
              :registrySpecIds []
              :specIds [{:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 1}
                        {:$type :tutorials.notebook/SpecId$v1 :specName "my/B" :specVersion 1}
                        {:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 2}
                        {:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 2}]
              :notebooks [{:$type :tutorials.notebook/Notebook$v1
                           :name "notebook1"
                           :version 1
                           :items []}]
              :tests []}
      :throws :auto}
     {:code '{:$type :tutorials.notebook/Workspace$v1
              :registrySpecIds []
              :specIds [{:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 1}
                        {:$type :tutorials.notebook/SpecId$v1 :specName "my/B" :specVersion 1}
                        {:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 2}]
              :notebooks [{:$type :tutorials.notebook/Notebook$v1
                           :name "notebook1"
                           :version 1
                           :items []}
                          {:$type :tutorials.notebook/Notebook$v1
                           :name "notebook1"
                           :version 2
                           :items []}]
              :tests []}
      :throws :auto}

     {:spec-map-merge
      {:tutorials.notebook/ResolveRefsState$v1
       {:fields {:context [:Vec :tutorials.notebook/SpecId$v1]
                 :resolved [:Vec :tutorials.notebook/SpecId$v1]}}

       :tutorials.notebook/RSpecIds$v1
       {:fields {:result [:Vec :tutorials.notebook/SpecId$v1]}}

       :tutorials.notebook/ResolveRefsDirect$v1
       {:fields {:specIds [:Vec :tutorials.notebook/SpecId$v1]
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
       {:fields {:specIds [:Vec :tutorials.notebook/SpecId$v1]
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
                                         :items items}
                                        :tutorials.notebook/RSpecIds$v1)}}}}}

     {:code '(valid? {:$type :tutorials.notebook/ResolveRefs$v1
                      :specIds [{:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 1}
                                {:$type :tutorials.notebook/SpecId$v1 :specName "my/B" :specVersion 1}
                                {:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 2}]

                      :items []})
      :result true}

     {:code '(valid? {:$type :tutorials.notebook/ResolveRefs$v1
                      :specIds [{:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 1}
                                {:$type :tutorials.notebook/SpecId$v1 :specName "my/B" :specVersion 1}
                                {:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 2}]
                      :items [{:$type :tutorials.notebook/SpecRef$v1 :specName "my/A" :specVersion 1}]})
      :result true}

     {:code '(valid? {:$type :tutorials.notebook/ResolveRefs$v1
                      :specIds [{:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 1}
                                {:$type :tutorials.notebook/SpecId$v1 :specName "my/B" :specVersion 1}
                                {:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 2}]
                      :items [{:$type :tutorials.notebook/NewSpec$v1 :specName "my/A" :specVersion 3}
                              {:$type :tutorials.notebook/SpecRef$v1 :specName "my/A" :specVersion 3}]})
      :result true}

     {:code '(valid? {:$type :tutorials.notebook/ResolveRefs$v1
                      :specIds [{:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 1}
                                {:$type :tutorials.notebook/SpecId$v1 :specName "my/B" :specVersion 1}
                                {:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 2}]
                      :items [{:$type :tutorials.notebook/SpecRef$v1 :specName "my/A" :specVersion 3}
                              {:$type :tutorials.notebook/NewSpec$v1 :specName "my/A" :specVersion 3}]})
      :result false}

     {:code '(get (refine-to {:$type :tutorials.notebook/ResolveRefs$v1
                              :specIds [{:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 1}
                                        {:$type :tutorials.notebook/SpecId$v1 :specName "my/B" :specVersion 1}
                                        {:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 2}]
                              :items [{:$type :tutorials.notebook/SpecRef$v1 :specName "my/A"}
                                      {:$type :tutorials.notebook/NewSpec$v1 :specName "my/A" :specVersion 3}
                                      {:$type :tutorials.notebook/SpecRef$v1 :specName "my/A"}]}
                             :tutorials.notebook/RSpecIds$v1)
                  :result)
      :result [{:$type :tutorials.notebook/SpecId$v1, :specName "my/A", :specVersion 2}
               {:$type :tutorials.notebook/SpecId$v1, :specName "my/A", :specVersion 3}]}

     {:spec-map-merge
      {:tutorials.notebook/ApplicableNewSpecs$v1
       {:fields {:specIds [:Vec :tutorials.notebook/SpecId$v1]
                 :newSpecs [:Vec :tutorials.notebook/NewSpec$v1]}
        :constraints
        #{{:name "newSpecsInOrder"
           :expr
           '(let [all-spec-names (reduce [a #{}] [ns newSpecs]
                                         (if (contains? a (get ns :specName))
                                           a
                                           (conj a (get ns :specName))))]
              (every? [n all-spec-names]
                      (let [max-version (get (refine-to
                                              {:$type :tutorials.notebook/FMaxSpecVersion$v1
                                               :specIds specIds
                                               :specName n}
                                              :tutorials.notebook/RInteger$v1)
                                             :result)
                            versions (concat [(if-value max-version max-version 0)]
                                             (map [ns (filter [ns newSpecs]
                                                              (= n (get ns :specName)))]
                                                  (get ns :specVersion)))]
                        (every? [pair (map [i (range 0 (dec (count versions)))]
                                           [(get versions i)
                                            (get versions (inc i))])]
                                (= (inc (get pair 0)) (get pair 1))))))}}}}}

     {:code '(valid? {:$type :tutorials.notebook/ApplicableNewSpecs$v1
                      :specIds [{:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 1}
                                {:$type :tutorials.notebook/SpecId$v1 :specName "my/B" :specVersion 1}
                                {:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 2}]
                      :newSpecs [{:$type :tutorials.notebook/NewSpec$v1 :specName "my/A" :specVersion 4}]})
      :result false}
     {:code '(valid? {:$type :tutorials.notebook/ApplicableNewSpecs$v1
                      :specIds [{:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 1}
                                {:$type :tutorials.notebook/SpecId$v1 :specName "my/B" :specVersion 1}
                                {:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 2}]
                      :newSpecs [{:$type :tutorials.notebook/NewSpec$v1 :specName "my/C" :specVersion 4}]})
      :result false}
     {:code '(valid? {:$type :tutorials.notebook/ApplicableNewSpecs$v1
                      :specIds [{:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 1}
                                {:$type :tutorials.notebook/SpecId$v1 :specName "my/B" :specVersion 1}
                                {:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 2}]
                      :newSpecs [{:$type :tutorials.notebook/NewSpec$v1 :specName "my/A" :specVersion 2}]})
      :result false}
     {:code '(valid? {:$type :tutorials.notebook/ApplicableNewSpecs$v1
                      :specIds [{:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 1}
                                {:$type :tutorials.notebook/SpecId$v1 :specName "my/B" :specVersion 1}
                                {:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 2}]
                      :newSpecs [{:$type :tutorials.notebook/NewSpec$v1 :specName "my/A" :specVersion 3}
                                 {:$type :tutorials.notebook/NewSpec$v1 :specName "my/B" :specVersion 2}
                                 {:$type :tutorials.notebook/NewSpec$v1 :specName "my/A" :specVersion 4}
                                 {:$type :tutorials.notebook/NewSpec$v1 :specName "my/C" :specVersion 1}]})
      :result true}

     {:spec-map-merge
      {:tutorials.notebook/Effect$v1
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

       :tutorials.notebook/WriteRegressionTestEffect$v1
       {:fields {:notebookName :String
                 :notebookVersion :Integer}
        :refines-to {:tutorials.notebook/Effect$v1
                     {:name "effect"
                      :expr '{:$type :tutorials.notebook/Effect$v1}}}}

       :tutorials.notebook/WorkspaceAndEffects$v1
       {:fields {:workspace :tutorials.notebook/Workspace$v1
                 :effects [:Vec :tutorials.notebook/Effect$v1]}}

       :tutorials.notebook/WriteNotebook$v1
       {:fields {:workspace :tutorials.notebook/Workspace$v1
                 :notebookName :String
                 :notebookVersion :Integer
                 :notebookItems [:Vec :tutorials.notebook/AbstractNotebookItem$v1]}

        :constraints
        #{{:name "positiveVersion"
           :expr '(valid? {:$type :tutorials.notebook/Version$v1
                           :version notebookVersion})}
          {:name "priorNotebookExists"
           :expr
           '(if (> notebookVersion 1)
              (let [filtered (filter [nb (get workspace :notebooks)]
                                     (and (= (get nb :name)
                                             notebookName)
                                          (= (get nb :version)
                                             (dec notebookVersion))))]
                (= (count filtered) 1))
              true)}}
        :refines-to {:tutorials.notebook/WorkspaceAndEffects$v1
                     {:name "newWorkspaceAndEffects"
                      :expr
                      '{:$type :tutorials.notebook/WorkspaceAndEffects$v1
                        :workspace {:$type :tutorials.notebook/Workspace$v1
                                    :registrySpecIds (get workspace :registrySpecIds)
                                    :specIds (get workspace :specIds)
                                    :notebooks (conj (get workspace :notebooks)
                                                     {:$type :tutorials.notebook/Notebook$v1
                                                      :name notebookName
                                                      :version notebookVersion
                                                      :items notebookItems})
                                    :tests (get workspace :tests)}
                        :effects [{:$type :tutorials.notebook/WriteNotebookEffect$v1
                                   :notebookName notebookName
                                   :notebookVersion notebookVersion}]}}}}

       :tutorials.notebook/ApplyNotebook$v1
       {:fields {:workspace :tutorials.notebook/Workspace$v1
                 :notebookName :String
                 :notebookVersion :Integer}
        :constraints
        #{{:name "notebookExists"
           :expr
           '(> (count (filter [nb (get workspace :notebooks)]
                              (and (= (get nb :name)
                                      notebookName)
                                   (= (get nb :version)
                                      notebookVersion))))
               0)}
          {:name "notebookContainsNonEphemeralNewSpecs"
           :expr
           '(let [filtered (filter [nb (get workspace :notebooks)]
                                   (and (= (get nb :name)
                                           notebookName)
                                        (= (get nb :version)
                                           notebookVersion)))]
              (if (> (count filtered) 0)
                (let [nb (first filtered)]
                  (> (count (filter [ns (map [item (filter [item (get nb :items)]
                                                           (refines-to? item :tutorials.notebook/NewSpec$v1))]
                                             (refine-to item :tutorials.notebook/NewSpec$v1))]
                                    (let [is-ephemeral (get ns :isEphemeral)]
                                      (if-value is-ephemeral false true))))
                     0))
                true))}
          {:name "specsApplicable"
           :expr
           '(let [filtered (filter [nb (get workspace :notebooks)]
                                   (and (= (get nb :name)
                                           notebookName)
                                        (= (get nb :version)
                                           notebookVersion)))]
              (if (> (count filtered) 0)
                (let [nb (first filtered)]
                  (valid? {:$type :tutorials.notebook/ApplicableNewSpecs$v1
                           :specIds (concat (get workspace :specIds) (get workspace :registrySpecIds))
                           :newSpecs (map [item (filter [item (get nb :items)]
                                                        (refines-to? item :tutorials.notebook/NewSpec$v1))]
                                          (refine-to item :tutorials.notebook/NewSpec$v1))}))
                true))}
          {:name "specsValidRefs"
           :expr
           '(let [filtered (filter [nb (get workspace :notebooks)]
                                   (and (= (get nb :name)
                                           notebookName)
                                        (= (get nb :version)
                                           notebookVersion)))]
              (if (> (count filtered) 0)
                (let [nb (first filtered)]
                  (valid? {:$type :tutorials.notebook/ResolveRefs$v1
                           :specIds (concat (get workspace :specIds) (get workspace :registrySpecIds))
                           :items (get nb :items)}))
                true))}}
        :refines-to {:tutorials.notebook/WorkspaceAndEffects$v1
                     {:name "newWorkspaceAndEffects"
                      :expr
                      '(let [filtered (filter [nb (get workspace :notebooks)]
                                              (and (= (get nb :name)
                                                      notebookName)
                                                   (= (get nb :version)
                                                      notebookVersion)))]
                         (when (> (count filtered) 0)
                           (let [nb (first filtered)
                                 new-spec-ids (map [ns (filter [ns (map [item (filter [item (get nb :items)]
                                                                                      (refines-to? item :tutorials.notebook/NewSpec$v1))]
                                                                        (refine-to item :tutorials.notebook/NewSpec$v1))]
                                                               (let [is-ephemeral (get ns :isEphemeral)]
                                                                 (if-value is-ephemeral false true)))]
                                                   (refine-to ns :tutorials.notebook/SpecId$v1))
                                 new-notebook {:$type :tutorials.notebook/Notebook$v1
                                               :name (get nb :name)
                                               :version (inc (get nb :version))
                                               :items (filter [item (get nb :items)]
                                                              (if (refines-to? item :tutorials.notebook/NewSpec$v1)
                                                                (let [ns (refine-to item :tutorials.notebook/NewSpec$v1)
                                                                      is-ephemeral (get ns :isEphemeral)]
                                                                  (if-value is-ephemeral true false))
                                                                true))}]
                             {:$type :tutorials.notebook/WorkspaceAndEffects$v1
                              :workspace {:$type :tutorials.notebook/Workspace$v1
                                          :registrySpecIds (get workspace :registrySpecIds)
                                          :specIds (concat (get workspace :specIds)
                                                           new-spec-ids)
                                          :notebooks (conj (filter [nb (get workspace :notebooks)]
                                                                   (or (not= (get nb :name)
                                                                             notebookName)
                                                                       (not= (get nb :version)
                                                                             notebookVersion)))
                                                           new-notebook)
                                          :tests (get workspace :tests)}
                              :effects (conj (map [si new-spec-ids]
                                                  {:$type :tutorials.notebook/WriteSpecEffect$v1
                                                   :specId si})
                                             {:$type :tutorials.notebook/WriteNotebookEffect$v1
                                              :notebookName (get new-notebook :name)
                                              :notebookVersion (get new-notebook :version)})})))}}}

       :tutorials.notebook/CreateRegressionTest$v1
       {:fields {:workspace :tutorials.notebook/Workspace$v1
                 :notebookName :String
                 :notebookVersion :Integer}
        :constraints
        #{{:name "notebookExists"
           :expr
           '(> (count (filter [nb (get workspace :notebooks)]
                              (and (= (get nb :name)
                                      notebookName)
                                   (= (get nb :version)
                                      notebookVersion))))
               0)}
          {:name "notebookCannotContainNewNonEphemeralSpecs"
           :expr
           '(let [filtered (filter [nb (get workspace :notebooks)]
                                   (and (= (get nb :name)
                                           notebookName)
                                        (= (get nb :version)
                                           notebookVersion)))]
              (if (> (count filtered) 0)
                (let [nb (first filtered)]
                  (= (count (filter [ns (map [item (filter [item (get nb :items)]
                                                           (refines-to? item :tutorials.notebook/NewSpec$v1))]
                                             (refine-to item :tutorials.notebook/NewSpec$v1))]
                                    (let [is-ephemeral (get ns :isEphemeral)]
                                      (if-value is-ephemeral false true))))
                     0))
                true))}
          {:name "specsValidRefs"
           :expr
           '(let [filtered (filter [nb (get workspace :notebooks)]
                                   (and (= (get nb :name)
                                           notebookName)
                                        (= (get nb :version)
                                           notebookVersion)))]
              (if (> (count filtered) 0)
                (let [nb (first filtered)]
                  (valid? {:$type :tutorials.notebook/ResolveRefs$v1
                           :specIds (concat (get workspace :specIds) (get workspace :registrySpecIds))
                           :items (get nb :items)}))
                true))}}
        :refines-to {:tutorials.notebook/WorkspaceAndEffects$v1
                     {:name "newWorkspaceAndEffects"
                      :expr
                      '(let [filtered (filter [nb (get workspace :notebooks)]
                                              (and (= (get nb :name)
                                                      notebookName)
                                                   (= (get nb :version)
                                                      notebookVersion)))]
                         (when (> (count filtered) 0)
                           (let [nb (first filtered)
                                 new-test {:$type :tutorials.notebook/RegressionTest$v1
                                           :notebookName notebookName
                                           :notebookVersion notebookVersion}]
                             {:$type :tutorials.notebook/WorkspaceAndEffects$v1
                              :workspace {:$type :tutorials.notebook/Workspace$v1
                                          :registrySpecIds (get workspace :registrySpecIds)
                                          :specIds (get workspace :specIds)
                                          :notebooks (get workspace :notebooks)
                                          :tests (conj (get workspace :tests)
                                                       new-test)}
                              :effects [{:$type :tutorials.notebook/WriteRegressionTestEffect$v1
                                         :notebookName (get new-test :notebookName)
                                         :notebookVersion (get new-test :notebookVersion)}]})))}}}

       :tutorials.notebook/UpdateRegressionTest$v1
       {:fields {:workspace :tutorials.notebook/Workspace$v1
                 :notebookName :String
                 :notebookVersion :Integer
                 :lastNotebookVersion :Integer}
        :constraints
        #{{:name "notebookExists"
           :expr
           '(> (count (filter [nb (get workspace :notebooks)]
                              (and (= (get nb :name)
                                      notebookName)
                                   (= (get nb :version)
                                      notebookVersion))))
               0)}
          {:name "testExists"
           :expr
           '(> (count (filter [t (get workspace :tests)]
                              (and (= (get t :notebookName)
                                      notebookName)
                                   (= (get t :notebookVersion)
                                      lastNotebookVersion))))
               0)}
          {:name "notebookCannotContainNewNonEphemeralSpecs"
           :expr
           '(let [filtered (filter [nb (get workspace :notebooks)]
                                   (and (= (get nb :name)
                                           notebookName)
                                        (= (get nb :version)
                                           notebookVersion)))]
              (if (> (count filtered) 0)
                (let [nb (first filtered)]
                  (= (count (filter [ns (map [item (filter [item (get nb :items)]
                                                           (refines-to? item :tutorials.notebook/NewSpec$v1))]
                                             (refine-to item :tutorials.notebook/NewSpec$v1))]
                                    (let [is-ephemeral (get ns :isEphemeral)]
                                      (if-value is-ephemeral false true))))
                     0))
                true))}
          {:name "specsValidRefs"
           :expr
           '(let [filtered (filter [nb (get workspace :notebooks)]
                                   (and (= (get nb :name)
                                           notebookName)
                                        (= (get nb :version)
                                           notebookVersion)))]
              (if (> (count filtered) 0)
                (let [nb (first filtered)]
                  (valid? {:$type :tutorials.notebook/ResolveRefs$v1
                           :specIds (concat (get workspace :specIds) (get workspace :registrySpecIds))
                           :items (get nb :items)}))
                true))}}
        :refines-to {:tutorials.notebook/WorkspaceAndEffects$v1
                     {:name "newWorkspaceAndEffects"
                      :expr
                      '(let [filtered (filter [nb (get workspace :notebooks)]
                                              (and (= (get nb :name)
                                                      notebookName)
                                                   (= (get nb :version)
                                                      notebookVersion)))]
                         (when (> (count filtered) 0)
                           (let [nb (first filtered)
                                 new-test {:$type :tutorials.notebook/RegressionTest$v1
                                           :notebookName notebookName
                                           :notebookVersion notebookVersion}]
                             {:$type :tutorials.notebook/WorkspaceAndEffects$v1
                              :workspace {:$type :tutorials.notebook/Workspace$v1
                                          :registrySpecIds (get workspace :registrySpecIds)
                                          :specIds (get workspace :specIds)
                                          :notebooks (get workspace :notebooks)
                                          :tests (conj (filter [t (get workspace :tests)]
                                                               (not= (get t :notebookName)
                                                                     notebookName))
                                                       new-test)}
                              :effects [{:$type :tutorials.notebook/WriteRegressionTestEffect$v1
                                         :notebookName (get new-test :notebookName)
                                         :notebookVersion (get new-test :notebookVersion)}]})))}}}}}

     {:code '(let [ws {:$type :tutorials.notebook/Workspace$v1
                       :registrySpecIds []
                       :specIds [{:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 1}
                                 {:$type :tutorials.notebook/SpecId$v1 :specName "my/B" :specVersion 1}
                                 {:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 2}]
                       :notebooks [{:$type :tutorials.notebook/Notebook$v1
                                    :name "notebook1"
                                    :version 1
                                    :items [{:$type :tutorials.notebook/NewSpec$v1 :specName "my/A" :specVersion 3}]}]
                       :tests []}]
               [(valid? {:$type :tutorials.notebook/ApplyNotebook$v1
                         :workspace ws
                         :notebookName "notebook1"
                         :notebookVersion 1})
                (valid? {:$type :tutorials.notebook/ApplyNotebook$v1
                         :workspace ws
                         :notebookName "notebook1"
                         :notebookVersion 2})
                (valid? {:$type :tutorials.notebook/ApplyNotebook$v1
                         :workspace ws
                         :notebookName "notebook2"
                         :notebookVersion 1})])
      :result [true false false]}

     "If all of the new specs in the notebooks are ephemeral, then it cannot be applied."
     {:code
      '(let [ws {:$type :tutorials.notebook/Workspace$v1
                 :registrySpecIds []
                 :specIds [{:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 1}
                           {:$type :tutorials.notebook/SpecId$v1 :specName "my/B" :specVersion 1}
                           {:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 2}]
                 :notebooks [{:$type :tutorials.notebook/Notebook$v1
                              :name "notebook1"
                              :version 1
                              :items [{:$type :tutorials.notebook/NewSpec$v1 :specName "my/A" :specVersion 3 :isEphemeral true}
                                      {:$type :tutorials.notebook/SpecRef$v1 :specName "my/A" :specVersion 1}
                                      {:$type :tutorials.notebook/SpecRef$v1 :specName "my/A" :specVersion 3}]}]
                 :tests []}]
         (valid? {:$type :tutorials.notebook/ApplyNotebook$v1
                  :workspace ws
                  :notebookName "notebook1"
                  :notebookVersion 1}))
      :result false}

     {:code
      '(let [ws {:$type :tutorials.notebook/Workspace$v1
                 :registrySpecIds []
                 :specIds [{:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 1}
                           {:$type :tutorials.notebook/SpecId$v1 :specName "my/B" :specVersion 1}
                           {:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 2}]
                 :notebooks [{:$type :tutorials.notebook/Notebook$v1
                              :name "notebook1"
                              :version 1
                              :items [{:$type :tutorials.notebook/SpecRef$v1 :specName "my/A" :specVersion 1}]}]
                 :tests []}]
         (valid? {:$type :tutorials.notebook/ApplyNotebook$v1
                  :workspace ws
                  :notebookName "notebook1"
                  :notebookVersion 1}))
      :result false}

     {:code
      '(refine-to {:$type :tutorials.notebook/WriteNotebook$v1
                   :workspace {:$type :tutorials.notebook/Workspace$v1
                               :registrySpecIds []
                               :specIds []
                               :notebooks []
                               :tests []}
                   :notebookName "notebook1"
                   :notebookVersion 1
                   :notebookItems []}
                  :tutorials.notebook/WorkspaceAndEffects$v1)
      :result {:$type :tutorials.notebook/WorkspaceAndEffects$v1
               :workspace {:$type :tutorials.notebook/Workspace$v1
                           :registrySpecIds []
                           :specIds []
                           :notebooks [{:$type :tutorials.notebook/Notebook$v1, :name "notebook1", :version 1, :items []}]
                           :tests []}
               :effects [{:$type :tutorials.notebook/WriteNotebookEffect$v1
                          :notebookName "notebook1"
                          :notebookVersion 1}]}}

     {:code
      '(refine-to {:$type :tutorials.notebook/ApplyNotebook$v1
                   :workspace {:$type :tutorials.notebook/Workspace$v1
                               :registrySpecIds []
                               :specIds [{:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 1}
                                         {:$type :tutorials.notebook/SpecId$v1 :specName "my/B" :specVersion 1}
                                         {:$type :tutorials.notebook/SpecId$v1 :specName "my/A" :specVersion 2}]
                               :notebooks [{:$type :tutorials.notebook/Notebook$v1
                                            :name "notebook1"
                                            :version 1
                                            :items [{:$type :tutorials.notebook/NewSpec$v1 :specName "my/A" :specVersion 3}
                                                    {:$type :tutorials.notebook/NewSpec$v1 :specName "my/C" :specVersion 1 :isEphemeral true}
                                                    {:$type :tutorials.notebook/SpecRef$v1 :specName "my/A" :specVersion 1}
                                                    {:$type :tutorials.notebook/SpecRef$v1 :specName "my/A" :specVersion 3}]}
                                           {:$type :tutorials.notebook/Notebook$v1
                                            :name "notebook2"
                                            :version 3
                                            :items [{:$type :tutorials.notebook/NewSpec$v1 :specName "my/B" :specVersion 1}
                                                    {:$type :tutorials.notebook/SpecRef$v1 :specName "my/A" :specVersion 1}
                                                    {:$type :tutorials.notebook/SpecRef$v1 :specName "my/B" :specVersion 1}]}]
                               :tests []}
                   :notebookName "notebook1"
                   :notebookVersion 1}

                  :tutorials.notebook/WorkspaceAndEffects$v1)
      :result {:$type :tutorials.notebook/WorkspaceAndEffects$v1
               :workspace {:$type :tutorials.notebook/Workspace$v1
                           :registrySpecIds []
                           :specIds [{:$type :tutorials.notebook/SpecId$v1, :specName "my/A", :specVersion 1}
                                     {:$type :tutorials.notebook/SpecId$v1, :specName "my/B", :specVersion 1}
                                     {:$type :tutorials.notebook/SpecId$v1, :specName "my/A", :specVersion 2}
                                     {:$type :tutorials.notebook/SpecId$v1, :specName "my/A", :specVersion 3}]
                           :notebooks [{:$type :tutorials.notebook/Notebook$v1
                                        :name "notebook2"
                                        :version 3
                                        :items [{:$type :tutorials.notebook/NewSpec$v1, :specName "my/B", :specVersion 1}
                                                {:$type :tutorials.notebook/SpecRef$v1, :specName "my/A", :specVersion 1}
                                                {:$type :tutorials.notebook/SpecRef$v1, :specName "my/B", :specVersion 1}]}
                                       {:$type :tutorials.notebook/Notebook$v1
                                        :name "notebook1"
                                        :version 2
                                        :items [{:$type :tutorials.notebook/NewSpec$v1 :specName "my/C" :specVersion 1 :isEphemeral true}
                                                {:$type :tutorials.notebook/SpecRef$v1, :specName "my/A", :specVersion 1}
                                                {:$type :tutorials.notebook/SpecRef$v1, :specName "my/A", :specVersion 3}]}]
                           :tests []}
               :effects [{:$type :tutorials.notebook/WriteSpecEffect$v1
                          :specId {:$type :tutorials.notebook/SpecId$v1, :specName "my/A", :specVersion 3}}
                         {:$type :tutorials.notebook/WriteNotebookEffect$v1
                          :notebookName "notebook1"
                          :notebookVersion 2}]}}

     {:code
      '(refine-to {:$type :tutorials.notebook/CreateRegressionTest$v1
                   :workspace {:$type :tutorials.notebook/Workspace$v1
                               :registrySpecIds []
                               :specIds []
                               :notebooks [{:$type :tutorials.notebook/Notebook$v1, :name "notebook1", :version 1, :items []}]
                               :tests []}
                   :notebookName "notebook1"
                   :notebookVersion 1}
                  :tutorials.notebook/WorkspaceAndEffects$v1)
      :result {:$type :tutorials.notebook/WorkspaceAndEffects$v1
               :workspace {:$type :tutorials.notebook/Workspace$v1
                           :registrySpecIds []
                           :specIds []
                           :notebooks [{:$type :tutorials.notebook/Notebook$v1, :name "notebook1", :version 1, :items []}]
                           :tests [{:$type :tutorials.notebook/RegressionTest$v1,:notebookName "notebook1",:notebookVersion 1}]}
               :effects [{:$type :tutorials.notebook/WriteRegressionTestEffect$v1,:notebookName "notebook1",:notebookVersion 1}]}}

     {:code
      '(refine-to {:$type :tutorials.notebook/CreateRegressionTest$v1
                   :workspace {:$type :tutorials.notebook/Workspace$v1
                               :registrySpecIds []
                               :specIds []
                               :notebooks [{:$type :tutorials.notebook/Notebook$v1, :name "notebook1", :version 2, :items []}]
                               :tests [{:$type :tutorials.notebook/RegressionTest$v1,:notebookName "notebook1",:notebookVersion 1}]}
                   :notebookName "notebook1"
                   :notebookVersion 2}
                  :tutorials.notebook/WorkspaceAndEffects$v1)
      :throws :auto}

     {:code
      '(refine-to {:$type :tutorials.notebook/UpdateRegressionTest$v1
                   :workspace {:$type :tutorials.notebook/Workspace$v1
                               :registrySpecIds []
                               :specIds []
                               :notebooks [{:$type :tutorials.notebook/Notebook$v1, :name "notebook1", :version 9, :items []}]
                               :tests [{:$type :tutorials.notebook/RegressionTest$v1,:notebookName "notebook1",:notebookVersion 1}]}
                   :notebookName "notebook1"
                   :notebookVersion 9
                   :lastNotebookVersion 1}
                  :tutorials.notebook/WorkspaceAndEffects$v1)
      :result {:$type :tutorials.notebook/WorkspaceAndEffects$v1
               :workspace {:$type :tutorials.notebook/Workspace$v1
                           :registrySpecIds []
                           :specIds []
                           :notebooks [{:$type :tutorials.notebook/Notebook$v1, :name "notebook1", :version 9, :items []}]
                           :tests [{:$type :tutorials.notebook/RegressionTest$v1,:notebookName "notebook1",:notebookVersion 9}]}
               :effects [{:$type :tutorials.notebook/WriteRegressionTestEffect$v1,:notebookName "notebook1",:notebookVersion 9}]}}]}

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
