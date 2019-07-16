(ns cljs-object-completion.middleware-tests
  (:require [cljs-object-completion.middleware :as sut]
            [clojure.test :as t :refer [is deftest run-tests testing]]))

(comment
  (run-tests)

  (.log js/console "foo")
  (.. js/console (log "foo"))
  js/console
  (js/console.log "foo")
  (-> js/console (.log "foo"))
  (doto js/console (.log "foo"))
)



(deftest completion-structions-for-client
  (let [state {:special-namespaces ["js"]}
        tests [;; should not trigger object completion
               {:desc "no object in sight"
                :input    {:ns 'foo :symbol ".log", :context "(__prefix__)"}
                :expected nil}

               {:desc "Normal object/class name is typed, not method or special name."
                :input    {:ns 'foo, :symbol "bar", :context "(.log __prefix__)"}
                :expected nil}

               ;; should trigger object completion
               {:desc ". method"
                :input    {:ns 'foo, :symbol ".log", :context "(__prefix__ js/console)"}
                :expected "js/console"}

               {:desc ". method nested"
                :input    {:ns 'foo, :symbol ".log", :context "(__prefix__ (.-console js/window) \"foo\")"}
                :expected "(.-console js/window)"}

               {:desc ".- prop"
                :input    {:ns 'foo, :symbol ".-memory", :context "(__prefix__ js/console)"}
                :expected "js/console"}

               {:desc ".- prop nested"
                :input    {:ns 'foo, :symbol ".-memory", :context "(__prefix__ (.-console js/window) \"foo\")"}
                :expected "(.-console js/window)"}

               {:desc ".. method"
                :input    {:ns 'foo, :symbol "log", :context "(.. js/console __prefix__)"}
                :expected "js/console"}

               {:desc ".. method chained"
                :input    {:ns 'foo, :symbol "log", :context "(.. js/window -console __prefix__)"}
                :expected "(.. js/window -console)"}

               {:desc ".. method chained and nested"
                :input    {:ns 'foo, :symbol "log", :context "(.. js/window -console (__prefix__ \"foo\"))"}
                :expected "(.. js/window -console)"}

               {:desc ".. prop"
                :input    {:ns 'foo, :symbol "-memory", :context "(.. js/console __prefix__)"}
                :expected "js/console"}]]

    (doseq [{:keys [input expected desc]}
            ;; [(-> tests reverse (nth 1))]
            tests
            ]
      (is (= expected (sut/client-instructions input state)) desc))))
