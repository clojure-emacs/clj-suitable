(ns runtime-completion.tests
  (:require [cljs.test :refer-macros [deftest is run-tests testing]]
            [goog.object :refer [get set] :rename {get oget, set oset}]
            [runtime-completion.core :as compl]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;; helpers

(defn- find-prop-named [obj name]
  (->> obj compl/property-names-and-types
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

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment

  ;; (.. js/console (lo "foo"))
  ;; complete: {:ns runtime-completion.tests, :symbol l, :context (.. js/console (__prefix__ "foo"))}
  (cljs.hello)
  ;; done
  (.log js/console "foo")
  (.-memory js/console)
  (.-jsHeapSizeLimit (.-memory js/console))
  (.log (.-console js/window) "foo")
  (.. js/console (log "foo"))
  (.. js/window -console)
  (.. js/console -memory -jsHeapSizeLimit)

  ;; todo
  js/console
  (js/console.log "foo")
  (-> js/console (.log "foo"))
  (doto js/console (.log "foo"))
  (-> js/console .-memory .-jsHeapSizeLimit)
  js/console.memory.jsHeapSizeLimit

  (run-tests)

  (foo )
  )


