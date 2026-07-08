(ns suitable.shadow-completion-test
  "Integration test for the shadow-cljs completion path.

  Unlike `suitable.complete-for-nrepl-test` (which drives a piggieback Node
  REPL), this exercises `complete-for-shadow-cljs`: it boots a real shadow-cljs
  server, spins up a Node runtime, and asks for completions through the same
  code path shadow-cljs uses.

  It is deliberately isolated in `src/test-integration` (with its own
  `:shadow-test` alias) because shadow-cljs 3.x requires JDK 21 and a Node
  runtime with the `ws` npm package available - see .github/workflows/ci.yml."
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [shadow.cljs.devtools.api :as shadow]
   [shadow.cljs.devtools.server :as server]
   [shadow.cljs.devtools.server.repl-impl :as repl-impl]
   [shadow.cljs.devtools.server.runtime :as runtime]
   [suitable.complete-for-nrepl :refer [complete-for-nrepl]]))

(def ^:private build-id :node-repl)

(defn- wait-for-runtime
  "Block until the node runtime has connected to BUILD-ID, or TIMEOUT-MS passes."
  [timeout-ms]
  (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
    (loop []
      (cond
        (seq (shadow/repl-runtimes build-id)) true
        (> (System/currentTimeMillis) deadline) false
        :else (do (Thread/sleep 200) (recur))))))

(defn- with-shadow-node-runtime [f]
  (server/start!)
  (try
    (repl-impl/node-repl* (runtime/get-instance!) {})
    (assert (wait-for-runtime 90000)
            "shadow-cljs node runtime did not connect (is Node installed with the `ws` package?)")
    (f)
    (finally
      (server/stop!))))

(use-fixtures :once with-shadow-node-runtime)

(defn- complete [sym context]
  (complete-for-nrepl
   {:shadow.cljs.devtools.server.nrepl-impl/build-id build-id
    :session (atom {})
    :symbol sym
    :ns "cljs.user"
    :context context}))

(deftest shadow-cljs-dynamic-completion
  (testing "global completion"
    (is (= [{:type "function" :candidate "js/Object" :ns "js"}]
           (complete "js/Ob" nil)))
    (is (= [{:type "function" :candidate "js/console.log" :ns "js"}]
           (complete "js/console.lo" nil))))

  (testing "method completion via the `.` interop form (issue #48)"
    ;; `(.lo js/console)` must complete to `.log`. This works over shadow-cljs's
    ;; Node runtime, so #48's missing method completions are specific to the
    ;; browser runtime or a client that doesn't send completion context.
    (is (= [{:type "function" :candidate ".log" :ns "js/console"}]
           (complete ".lo" "(__prefix__ js/console)")))))
