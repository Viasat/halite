;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.doc.md-spec
  (:require [clojure.string :as string]
            [com.viasat.halite.doc.md-basic :as md-basic]
            [com.viasat.halite.doc.utils :as utils]))

(set! *warn-on-reflection* true)

(defn spec-md [{:keys [generate-hdr-f embed-bnf-f]}]
  (->> [(generate-hdr-f "Specification Syntax Reference" "halite_spec-syntax-reference" nil "Specification syntax reference.")
        "A spec-map is a data structure used to define specs that are in context for evaluating some expressions.\n\n"
        (md-basic/diagram-description "elements in spec-maps")
        md-basic/element-name-description
        md-basic/label-description

        "Specs include variables which have types as:\n\n"
        (embed-bnf-f "type")
        "The variables for a spec are defined in a field-map:\n\n"
        (embed-bnf-f "field-map")
        "Constraints on those variables are defined as:\n\n"
        (embed-bnf-f "constraints")
        "Any applicable refinements are defined as:\n\n"
        (embed-bnf-f "refinement-map")
        "All the specs in scope are packaged up into a spec-map:\n\n"
        (embed-bnf-f "spec-map")
        "Note, of course each key can only appear once in each map that defines a spec. The diagram shows it this way just so it is easier to read."]
       flatten
       (apply str)))
