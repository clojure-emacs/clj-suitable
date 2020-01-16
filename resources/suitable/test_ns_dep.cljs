(ns ^{:doc "Dependency of test-ns namespace"} suitable.test-ns-dep)

(defn foo-in-dep [foo] :bar)

(def x ::dep-namespaced-keyword)
