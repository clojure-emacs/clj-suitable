(ns suitable.spec
  (:require [clojure.spec.alpha :as s]
            [suitable.js-completions :as suitable-js]))

(s/def ::non-empty-string (s/and string? not-empty))

(s/def ::type #{"var" "function"})
(s/def ::name ::non-empty-string)
(s/def ::candidate ::non-empty-string)
(s/def ::ns ::non-empty-string)
(s/def ::hierarchy int?)

(s/def ::context ::non-empty-string)
(s/def ::state (s/keys :req-un [::context]))

(s/def ::completion (s/keys :req-un [::type ::candidate] :opt-un [::ns]))
(s/def ::completions (s/coll-of ::completion))
(s/def ::completions-and-state (s/keys :req-un [::state ::completions]))

(s/def ::obj-property (s/keys :req-un [::name ::hierarchy ::type]))

(s/fdef suitable-js/js-properties-of-object
  :args (s/cat :obj-expr ::non-empty-string
               :prefix (s/nilable string?))
  #_ :ret #_ (s/keys :error (s/nilable string?)
                     :value (s/coll-of (s/keys {:name non-empty-string
                                                :hierarchy int?
                                                :type non-empty-string}))))

;; (require 'clojure.spec.test.alpha)
;; (clojure.spec.test.alpha/check ['suitable.js-completions/js-properties-of-object])
