(ns ;; Exercises doc expressed as metadata, `#'see suitable.compliment.sources.t-cljs/extra-metadata`:
    ^{:doc "Dependency of test-ns namespace"}
    suitable.test-ns-dep
  (:require
   ;; Exercises libspecs expressed as strings - see https://clojurescript.org/news/2021-04-06-release#_library_property_namespaces :
   ["clojure.set" :as set]))

(defn foo-in-dep [foo] :bar)

(def x ::dep-namespaced-keyword)
