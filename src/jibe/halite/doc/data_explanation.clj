;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.doc.data-explanation)

(def explanations {:spec/big-picture
                   {:label "Specs are about modeling things"
                    :desc "Specs are a general mechanism for modelling whatever is of interest."
                    :contents ["Writing a spec is carving out a subset out of the universe of all possible values and giving them a name."
                               {:spec-map {:spec/Ball {:spec-vars {:color "String"}}}}
                               "The spec gives a name to specific instances, such as the following."
                               {:code '{:$type :spec/Ball :color "red"}}
                               {:code '{:$type :spec/Ball :color "blue"}}]}})
