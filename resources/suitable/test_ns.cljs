(ns ^{:doc "A test namespace"} suitable.test-ns
  (:refer-clojure :exclude [unchecked-byte while])
  (:require [clojure.string]
            [suitable.test-ns-dep :as test-dep :refer [foo-in-dep]])
  (:require-macros [suitable.test-macros :as test-macros :refer [my-add]])
  (:import [goog.ui IdGenerator]))

(defrecord TestRecord [a b c])

(def x ::some-namespaced-keyword)

(defn issue-28
  []
  (str "https://github.com/clojure-emacs/cljs-tooling/issues/28"))

(defn test-public-fn
  []
  42)

(defn- test-private-fn
  []
  (inc (test-public-fn)))
