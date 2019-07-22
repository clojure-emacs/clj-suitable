(ns runtime-completion.tests
  (:require [cljs.test :refer-macros [deftest is run-tests testing]]
            [goog.object :refer [get set] :rename {get oget, set oset}]
            [runtime-completion.js-introspection :as inspector]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;; helpers

(defn- find-prop-named [obj name]
  (->> obj inspector/property-names-and-types
       (filter (comp (set [name]) :name))
       first))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;; test data

(def obj-1 (new (fn [x] (this-as this
                          (oset this "foo" 23)
                          (oset this "bar" (fn [] 23))))))

(def obj-2 (new (fn [x] (this-as this (oset this "foo" 23)))))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;; tests

(deftest find-props-in-obj
  (is (= (find-prop-named obj-1 "foo") {:name "foo", :hierarchy 0, :type "var"}))
  (is (= (find-prop-named obj-1 "bar") {:name "bar", :hierarchy 0, :type "function"})))

