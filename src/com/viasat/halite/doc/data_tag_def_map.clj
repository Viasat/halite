;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.doc.data-tag-def-map)

(def tag-def-map {:boolean-op {:label "Boolean operations"
                               :doc "Operations that operate on boolean values."
                               :type 'boolean
                               :type-mode 'op
                               :basic-ref 'boolean}
                  :boolean-out {:label "Produce booleans"
                                :doc "Operations that produce boolean output values."
                                :type 'boolean
                                :type-mode 'out
                                :basic-ref 'boolean}

                  :string-op {:label "String operations"
                              :doc "Operations that operate on string values."
                              :type 'string
                              :type-mode 'op
                              :basic-ref 'string}
                  :integer-op {:label "Integer operations"
                               :doc "Operations that operate on integer values."
                               :type 'integer
                               :type-mode 'op
                               :basic-ref 'integer}
                  :integer-out {:label "Produce integer"
                                :doc "Operations that produce integer output values."
                                :type 'integer
                                :type-mode 'out
                                :basic-ref 'integer}

                  :fixed-decimal-op {:label "Fixed-decimal operations"
                                     :doc "Operations that operate on fixed-decimal values."
                                     :type 'fixed-decimal
                                     :type-mode 'op
                                     :basic-ref 'fixed-decimal}
                  :fixed-decimal-out {:label "Produce fixed-decimals"
                                      :doc "Operations that produce fixed-decimal output values."
                                      :type 'fixed-decimal
                                      :type-mode 'out
                                      :basic-ref 'fixed-decimal}

                  :set-op {:label "Set operations"
                           :doc "Operations that operate on sets."
                           :type 'set
                           :type-mode 'op
                           :basic-ref 'set}
                  :set-out {:label "Produce sets"
                            :doc "Operations that produce sets."
                            :type 'set
                            :type-mode 'out
                            :basic-ref 'set}
                  :vector-op {:label "Vector operations"
                              :doc "Operations that operate on vectors."
                              :type 'vector
                              :type-mode 'op
                              :basic-ref 'vector}
                  :vector-out {:label "Produce vectors"
                               :doc "Operations that produce vectors."
                               :type 'vector
                               :type-mode 'out
                               :basic-ref 'vector}

                  :instance-op {:label "Instance operations"
                                :doc "Operations that operate on spec instances."
                                :type 'instance
                                :type-mode 'op
                                :basic-ref 'instance}
                  :instance-out {:label "Produce instances"
                                 :doc "Operations that produce spec instances."
                                 :type 'instance
                                 :type-mode 'out
                                 :basic-ref 'instance}
                  :instance-field-op {:label "Instance field operations"
                                      :doc "Operations that operate on fields of spec-instances."
                                      :type 'instance
                                      :type-mode 'field-op
                                      :basic-ref 'instance}
                  :spec-id-op {:label "Spec-id operations"
                               :doc "Operations that operate on spec identifiers."
                               :type 'spec-id
                               :type-mode 'op
                               :basic-ref 'keyword
                               :basic-ref-j 'symbol}

                  :optional-op {:label "Optional operations"
                                :doc "Operations that operate on optional fields and optional values in general."
                                :type 'optional
                                :type-mode 'op}
                  :optional-out {:label "Optionally produce values"
                                 :doc "Operations that produce optional values."
                                 :type 'optional
                                 :type-mode 'out}
                  :nothing-out {:label "Produce nothing"
                                :doc "Operations that produce 'nothing'."
                                :type 'nothing
                                :type-mode 'out}

                  :control-flow {:label "Control flow"
                                 :doc "Operators that control the flow of execution of the code."
                                 :type-mode 'control-flow}
                  :special-form {:label "Special forms"
                                 :doc "Operators that do not evaluate their arguments in the 'normal' way."
                                 :type-mode 'special-form}})
