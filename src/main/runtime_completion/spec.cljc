(ns runtime-completion.spec
  (:require [clojure.spec.alpha :as s]))

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

