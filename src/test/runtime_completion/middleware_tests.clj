(ns runtime-completion.middleware-tests
  (:require [clojure.test :as t :refer [deftest is run-tests testing]]
            [runtime-completion.middleware :as sut]))

(comment
  (run-tests)

  (.log js/console "foo")
  (.. js/console (log "foo"))
  js/console
  (js/console.log "foo")
  (-> js/console (.log "foo"))
  (doto js/console (.log "foo"))
)



(deftest cljs-object-expression-extraction
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

               {:desc ".. method nested"
                :input    {:ns 'foo, :symbol "log", :context "(.. js/console (__prefix__ \"foo\"))"}
                :expected "js/console"}

               {:desc ".. method chained"
                :input    {:ns 'foo, :symbol "log", :context "(.. js/window -console __prefix__)"}
                :expected "(.. js/window -console)"}

               {:desc ".. method chained and nested"
                :input    {:ns 'foo, :symbol "log", :context "(.. js/window -console (__prefix__ \"foo\"))"}
                :expected "(.. js/window -console)"}

               {:desc ".. prop"
                :input    {:ns 'foo, :symbol "-memory", :context "(.. js/console __prefix__)"}
                :expected "js/console"}

               {:desc "->"
                :input    {:ns 'foo, :symbol ".log", :context "(-> js/console __prefix__)"}
                :expected "(-> js/console)"}

               {:desc "-> (.)"
                :input    {:ns 'foo, :symbol ".log", :context "(-> js/console (__prefix__ \"foo\"))"}
                :expected "(-> js/console)"}

               {:desc "-> chained"
                :input    {:ns 'foo, :symbol ".log", :context "(-> js/window .-console __prefix__)"}
                :expected "(-> js/window .-console)"}

               {:desc "-> (.)"
                :input    {:ns 'foo, :symbol ".log", :context "(-> js/window .-console (__prefix__ \"foo\"))"}
                :expected "(-> js/window .-console)"}

               {:desc "doto"
                :input    {:ns 'foo, :symbol ".log", :context "(doto (. js/window -console) __prefix__)"}
                :expected "(. js/window -console)"}

               {:desc "doto (.)"
                :input    {:ns 'foo, :symbol ".log", :context "(doto (. js/window -console) (__prefix__ \"foo\"))"}
                :expected "(. js/window -console)"}]]

    (doseq [{:keys [input expected desc]} tests]
      (is (= expected (sut/expr-for-parent-obj input)) desc))))


(deftest handle-completion-msg-test
  (with-redefs [sut/js-properties-of-object
                (fn [obj-expr msg prefix]
                  (is (= "log" prefix))
                  {:error nil
                   :properties [{:name "log" :hierarchy 1 :type "function"}
                                {:name "clear" :hierarchy 1 :type "function"}]})]
    (is (= [{:type "function", :candidate ".log" :ns "js/console"}]
           (sut/handle-completion-msg {:ns "cljs.user" :symbol ".l"} "(__prefix__ js/console)")))
    (is (= [{:type "function", :candidate "log" :ns "js/console"}]
           (sut/handle-completion-msg {:ns "cljs.user" :symbol "l"} "(.. js/console (__prefix__ \"foo\"))")))))

(comment

  (sut/expr-for-parent-obj {:ns nil :symbol "foo" :context "(__prefix__ foo)"})
  (sut/expr-for-parent-obj {:ns "cljs.user" :symbol ".l" :context "(__prefix__ js/console)"})

  (with-redefs [sut/js-properties-of-object (fn [obj-expr msg] [])]
    (sut/handle-completion-msg {:ns "cljs.user" :symbol ".l" :context "(__prefix__ js/console)"} nil))
  )
