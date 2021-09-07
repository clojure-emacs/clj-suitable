(ns suitable.js-completion-test
  (:require [clojure.pprint :refer [cl-format]]
            [clojure.string :refer [starts-with?]]
            [clojure.test :as t :refer [deftest is testing]]
            [suitable.js-completions :as sut]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;; helpers

(defn candidate
  ([completion ns]
   (candidate completion ns (cond
                              (re-find #"^(?:js/|\.-|-)" completion) "var"
                              (starts-with? completion ".") "function"
                              :else "function")))
  ([completion ns type]
   {:type type, :candidate completion :ns ns}))


(defn fake-cljs-eval-fn [expected-obj-expression expected-prefix properties]
  (fn [_ns code]
    (when-let [[_ obj-expr prefix]
               (re-matches
                #"^\(suitable.js-introspection/property-names-and-types (.*) \"(.*)\"\)"
                code)]
      (if (and (= obj-expr expected-obj-expression)
               (= prefix expected-prefix))
        {:error nil
         :value properties}
        (is false (cl-format nil "expected obj-expr / prefix~% ~S / ~S~% passed to fake-js-props-fn does not match actual expr / prefix~% ~S / ~S"
                             expected-obj-expression expected-prefix
                             obj-expr prefix))))))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;; expr-for-parent-obj

(deftest expr-for-parent-obj
  (let [tests [ ;; should not trigger object completion
               {:desc "no object in sight"
                :symbol-and-context    [".log" "(__prefix__)"]
                :expected nil}

               {:desc "Normal object/class name is typed, not method or special name."
                :symbol-and-context    ["bar" "(.log __prefix__)"]
                :expected nil}

               ;; should trigger object completion
               {:desc ". method"
                :symbol-and-context    [".log" "(__prefix__ js/console)"]
                :expected {:type :. :obj-expr "js/console"}}

               {:desc ". method nested"
                :symbol-and-context    [".log" "(__prefix__ (.-console js/window) \"foo\")"]
                :expected {:type :. :obj-expr "(.-console js/window)"}}

               {:desc ".- prop"
                :symbol-and-context    [".-memory" "(__prefix__ js/console)"]
                :expected {:type :. :obj-expr "js/console"}}

               {:desc ".- prop nested"
                :symbol-and-context    [".-memory" "(__prefix__ (.-console js/window) \"foo\")"]
                :expected {:type :. :obj-expr "(.-console js/window)"}}

               {:desc ".. method"
                :symbol-and-context    ["log" "(.. js/console __prefix__)"]
                :expected {:type :.. :obj-expr "js/console"}}

               {:desc ".. method nested"
                :symbol-and-context    ["log" "(.. js/console (__prefix__ \"foo\"))"]
                :expected {:type :.. :obj-expr "js/console"}}

               {:desc ".. method chained"
                :symbol-and-context    ["log" "(.. js/window -console __prefix__)"]
                :expected {:type :.. :obj-expr "(.. js/window -console)"}}

               {:desc ".. method chained and nested"
                :symbol-and-context    ["log" "(.. js/window -console (__prefix__ \"foo\"))"]
                :expected {:type :.. :obj-expr "(.. js/window -console)"}}

               {:desc ".. prop"
                :symbol-and-context    ["-memory" "(.. js/console __prefix__)"]
                :expected {:type :.. :obj-expr "js/console"}}

               {:desc "->"
                :symbol-and-context    [".log" "(-> js/console __prefix__)"]
                :expected {:type :-> :obj-expr "(-> js/console)"}}

               {:desc "-> (.)"
                :symbol-and-context    [".log" "(-> js/console (__prefix__ \"foo\"))"]
                :expected {:type :-> :obj-expr "(-> js/console)"}}

               {:desc "-> chained"
                :symbol-and-context    [".log" "(-> js/window .-console __prefix__)"]
                :expected {:type :-> :obj-expr "(-> js/window .-console)"}}

               {:desc "-> (.)"
                :symbol-and-context    [".log" "(-> js/window .-console (__prefix__ \"foo\"))"]
                :expected {:type :-> :obj-expr "(-> js/window .-console)"}}

               {:desc "doto"
                :symbol-and-context    [".log" "(doto (. js/window -console) __prefix__)"]
                :expected {:type :doto :obj-expr "(. js/window -console)"}}

               {:desc "doto (.)"
                :symbol-and-context    [".log" "(doto (. js/window -console) (__prefix__ \"foo\"))"]
                :expected {:type :doto :obj-expr "(. js/window -console)"}}

               {:desc "doto (.)"
                :symbol-and-context    ["js/cons" "(doto (. js/window -console) (__prefix__ \"foo\"))"]
                :expected {:type :doto :obj-expr "(. js/window -console)"}}

               {:desc "no prefix"
                :symbol-and-context    ["xx" "(foo bar (baz))"]
                :expected nil}

               {:desc "broken form"
                :symbol-and-context    ["xx" "(foo "]
                :expected nil}]]

    (doseq [{[symbol context] :symbol-and-context :keys [expected desc]} tests]
      (is (= expected (sut/expr-for-parent-obj symbol context)) desc))))


;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;; cljs-completions

(deftest global
  (let [cljs-eval-fn (fake-cljs-eval-fn "(this-as this this)" "c" [{:name "console" :hierarchy 1 :type "var"}
                                                                   {:name "confirm" :hierarchy 1 :type "function"}])]
    (is (= [(candidate "js/console" "js" "var")
            (candidate "js/confirm" "js" "function")]
           (sut/cljs-completions cljs-eval-fn "js/c" {:ns "cljs.user" :context ""})))))

(deftest global-prop
  (testing "console"
    (let [cljs-eval-fn (fake-cljs-eval-fn "(this-as this (.. this -console))" "lo"
                                          [{:name "log" :hierarchy 1 :type "function"}
                                           {:name "clear" :hierarchy 1 :type "function"}])]
      (is (= [(candidate "js/console.log" "js" "function")]
             (sut/cljs-completions cljs-eval-fn "js/console.lo" {:ns "cljs.user" :context "js/console"})))))

  (testing "window.console"
    (let [cljs-eval-fn (fake-cljs-eval-fn "(this-as this (.. this -window -console))" "lo"
                                          [{:name "log" :hierarchy 1 :type "function"}
                                           {:name "clear" :hierarchy 1 :type "function"}])]
      (is (= [(candidate "js/window.console.log" "js" "function")]
             (sut/cljs-completions cljs-eval-fn "js/window.console.lo" {:ns "cljs.user" :context "js/console"}))))))

(deftest simple
  (let [cljs-eval-fn (fake-cljs-eval-fn "js/console" "l" [{:name "log" :hierarchy 1 :type "function"}
                                                          {:name "clear" :hierarchy 1 :type "function"}])]
    (is (= [(candidate ".log" "js/console")]
           (sut/cljs-completions cljs-eval-fn ".l" {:ns "cljs.user" :context "(__prefix__ js/console)"})))
    (is (= [(candidate "log" "js/console")]
           (sut/cljs-completions cljs-eval-fn "l" {:ns "cljs.user" :context "(. js/console __prefix__)"})))
    ;; note: we're testing context with a form here
    (is (= [(candidate "log" "js/console")]
           (sut/cljs-completions cljs-eval-fn "l" {:ns "cljs.user" :context '(.. js/console (__prefix__ "foo"))})))))

(deftest dotdot-completion
  (let [cljs-eval-fn (fake-cljs-eval-fn "js/foo" "ba" [{:name "bar" :hierarchy 1 :type "var"}
                                                       {:name "baz" :hierarchy 1 :type "function"}])]
    (is (= [(candidate "-bar" "js/foo")]
           (sut/cljs-completions cljs-eval-fn "-ba" {:ns "cljs.user" :context "(.. js/foo __prefix__)"})))
    (is (= [(candidate "baz" "js/foo")]
           (sut/cljs-completions cljs-eval-fn "ba" {:ns "cljs.user" :context "(.. js/foo __prefix__)"})))))

(deftest dotdot-completion-chained+nested
  (let [cljs-eval-fn (fake-cljs-eval-fn "(.. js/foo zork)" "ba" [{:name "bar" :hierarchy 1 :type "var"}
                                                                 {:name "baz" :hierarchy 1 :type "function"}])]
    (is (= [(candidate "-bar" "(.. js/foo zork)")]
           (sut/cljs-completions cljs-eval-fn "-ba" {:ns "cljs.user" :context "(.. js/foo zork (__prefix__ \"foo\"))"})))
    (is (= [(candidate "baz" "(.. js/foo zork)")]
           (sut/cljs-completions cljs-eval-fn "ba" {:ns "cljs.user" :context "(.. js/foo zork (__prefix__ \"foo\"))"})))))


(deftest dotdot-completion-chained+nested-2
  (let [cljs-eval-fn (fake-cljs-eval-fn "(.. js/foo zork)" "ba" [{:name "bar" :hierarchy 1 :type "var"}
                                                                 {:name "baz" :hierarchy 1 :type "function"}])]
    (is (= [(candidate "-bar" "(.. js/foo zork)")]
           (sut/cljs-completions cljs-eval-fn "-ba" {:ns "cljs.user" :context "(.. js/foo zork (__prefix__ \"foo\"))"})))
    (is (= [(candidate "baz" "(.. js/foo zork)")]
           (sut/cljs-completions cljs-eval-fn "ba" {:ns "cljs.user" :context "(.. js/foo zork (__prefix__ \"foo\"))"})))))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment

  (run-tests 'suitable.js-completion-test)

  (with-fake-js-completions cljs-eval-fn
    "js/console" "l" [{:name "log" :hierarchy 1 :type "function"}
                      {:name "clear" :hierarchy 1 :type "function"}]
    (sut/cljs-completions cljs-eval-fn ".l" {:ns "cljs.user" :context "(__prefix__ js/console)"}))

  (with-fake-js-completions cljs-eval-fn
    "(this-as this this)" "c" [{:name "console" :hierarchy 1 :type "var"}]
    (sut/cljs-completions cljs-eval-fn "js/c" {:ns "cljs.user" :context ""}))

  (sut/expr-for-parent-obj {:ns nil :symbol "foo" :context "(__prefix__ foo)"})
  (sut/expr-for-parent-obj {:ns "cljs.user" :symbol ".l" :context "(__prefix__ js/console)"})

  (with-redefs [sut/js-properties-of-object (fn [obj-expr msg] [])]
    (sut/cljs-completions {:ns "cljs.user" :symbol ".l" :context "(__prefix__ js/console)"} nil))

  )
