(ns suitable.complete-for-nrepl-test
  (:require
   [cider.piggieback :as piggieback]
   [cider.piggieback.cljs :as piggieback-cljs]
   [clojure.java.shell]
   [clojure.string :as string]
   [clojure.test :as t :refer [are deftest is run-tests testing]]
   [nrepl.core :as nrepl]
   [nrepl.server :refer [default-handler start-server]]
   [suitable.complete-for-nrepl :as sut]
   [suitable.middleware :refer [wrap-complete-standalone]]))

(require 'cljs.repl)
(require 'cljs.repl.node)

(def ^:dynamic *session* nil)
(def ^:dynamic ^nrepl.server.Server *server* nil)
(def ^:dynamic ^nrepl.transport.FnTransport *transport* nil)

(defn message
  ([msg]
   (message msg true))

  ([msg combine-responses?]
   {:pre [*session*]}
   (let [responses (nrepl/message *session* msg)]
     (if combine-responses?
       (nrepl/combine-responses responses)
       responses))))

(def handler nil)
(def server nil)
(def transport nil)
(def client nil)
(def session nil)

(defn stop []
  (message {:op :eval :code (nrepl/code :cljs/quit)})
  (.close *transport*)
  (.close *server*))

(defn await-cljs-completion-ready
  "Blocks until the cljs completion path is live, i.e. a plain `js/Ob` completion
  comes back with candidates.

  The standalone completion middleware only answers `complete` once piggieback
  has stored the cljs compiler-env in the session; until the Node repl has
  finished booting, the op falls through to nREPL's `unknown-op` and the request
  returns no completions. Gating on a working completion here removes that
  startup race from the tests (a real cider-nrepl stack always has a completion
  handler, so it never hits `unknown-op`)."
  [timeout-ms]
  (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
    (loop []
      (let [resp (message {:op "complete" :ns "cljs.user" :symbol "js/Ob"})]
        (cond
          (seq (:completions resp)) true
          (> (System/currentTimeMillis) deadline)
          (throw (ex-info "cljs completion never became ready" {:last-response resp}))
          :else (do (Thread/sleep 200) (recur)))))))

(defmacro with-repl-env
  {:style/indent 1}
  [renv-form & body]
  `(do
     (alter-var-root #'handler (constantly (default-handler #'piggieback/wrap-cljs-repl #'wrap-complete-standalone)))
     (alter-var-root #'server (constantly (start-server :handler handler)))
     (alter-var-root #'transport (constantly (nrepl/connect :port (:port server))))
     (alter-var-root #'client (constantly (nrepl/client transport (:port server))))
     (alter-var-root #'session (constantly (doto (nrepl/client-session client)
                                             assert)))
     (binding [*server* server
               *transport* transport
               *session* session]
       (try
         (dorun (message
                 {:op :eval
                  :code (nrepl/code (require '[cider.piggieback :as piggieback])
                                    (piggieback/cljs-repl ~renv-form))}))
         (dorun (message {:op :eval
                          :code (nrepl/code (require 'clojure.data))}))
         (await-cljs-completion-ready 30000)

         (do
           ~@body)
         (finally
           (stop))))))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(deftest sanity-test-node
  (let [{:keys [exit]
         :as v} (clojure.java.shell/sh "node" "--version")]
    (assert (zero? exit)
            (pr-str v)))

  (with-repl-env (cljs.repl.node/repl-env)
    (testing "cljs repl is active"
      (let [response (message {:op :eval
                               :code (nrepl/code (js/Object.))})
            explanation (pr-str response)]
        (is (= "cljs.user" (:ns response))
            explanation)
        ;; ClojureScript 1.11 prints `#js{}` while 1.12 prints `#js {}`
        (is (= ["#js{}"] (mapv #(string/replace % #"#js\s+" "#js") (:value response)))
            explanation)
        (is (= #{"done"} (:status response))
            explanation)))))

(deftest preserves-repl-history
  ;; https://github.com/clojure-emacs/clj-suitable/issues/5
  (with-repl-env (cljs.repl.node/repl-env)
    (testing "a completion request must not clobber *1/*2/*3"
      (is (= ["42"] (:value (message {:op :eval :code "42"}))))
      (message {:op "complete"
                :ns "cljs.user"
                :symbol ".key"
                :context "(__prefix__ js/Object)"})
      (is (= ["42"] (:value (message {:op :eval :code "*1"})))
          "*1 should still hold the last user value, not the completion's result"))))

(deftest suitable-node
  (with-repl-env (cljs.repl.node/repl-env)
    (testing "js global completion"
      (let [response (message {:op "complete"
                               :ns "cljs.user"
                               :symbol "js/Ob"})
            candidates (:completions response)]
        (is (= [{:candidate "js/Object", :ns "js", :type "function"}] candidates))))

    (testing "manages context state"
      (message {:op "complete"
                :ns "cljs.user"
                :symbol ".xxxx"
                :context "(__prefix__ js/Object)"})
      (let [response (message {:op "complete"
                               :ns "cljs.user"
                               :symbol ".key"
                               :context ":same"})
            candidates (:completions response)]
        (is (= [{:ns "js/Object", :candidate ".keys" :type "function"}] candidates)
            (pr-str response))))

    (testing "enumerable items are filtered out"
      (are [context candidates] (= candidates
                                   (let [response   (message {:op      "complete"
                                                              :ns      "cljs.user"
                                                              :symbol  ".-"
                                                              :context context})]
                                     (:completions response)))
        "(__prefix__ (js/String \"abc\"))"
        [{:candidate ".-length", :ns "(js/String \"abc\")", :type "var"}]

        "(-> (js/String \"abc\") __prefix__)"
        [{:candidate ".-length", :ns "(-> (js/String \"abc\"))", :type "var"}]

        "(__prefix__ #js [1 2 3])"
        []

        "(__prefix__ (array 1 2 3))"
        [{:candidate ".-length", :ns "(array 1 2 3)", :type "var"}]

        "(__prefix__ (js/Array. 1 2 3))"
        [{:candidate ".-length", :ns "(js/Array. 1 2 3)", :type "var"}]

        "(__prefix__ (js/Set. (js/Array. 1 2 3)))"
        [{:candidate ".-size", :ns "(js/Set. (js/Array. 1 2 3))", :type "var"}]))))

(deftest require-introspection-ns
  ;; https://github.com/clojure-emacs/clj-suitable/issues/47
  (testing "loads once and marks the session loaded on success"
    (let [calls   (atom [])
          eval-fn (fn [ns code] (swap! calls conj [ns code]) {:value nil :error nil})
          session (atom {})]
      (sut/require-introspection-ns! eval-fn session "cljs.user")
      (is (= 1 (count @calls)) "evals the require once")
      (is (true? (::sut/js-introspection-loaded @session)))
      (sut/require-introspection-ns! eval-fn session "cljs.user")
      (is (= 1 (count @calls)) "does not re-require once loaded")))

  (testing "a failed load is surfaced and left uncached, so it is retried"
    (let [calls   (atom [])
          eval-fn (fn [ns code] (swap! calls conj [ns code]) {:value nil :error "cider is not defined"})
          session (atom {})
          out     (with-out-str (sut/require-introspection-ns! eval-fn session "cljs.user"))]
      (is (not (contains? @session ::sut/js-introspection-loaded))
          "must not mark loaded when the require errored")
      (is (string/includes? out "could not load the introspection namespace")
          "surfaces the failure instead of swallowing it")
      (with-out-str (sut/require-introspection-ns! eval-fn session "cljs.user"))
      (is (= 2 (count @calls)) "retries the load after a failure"))))

(deftest node-env?
  (is (false? (sut/node-env? nil)))
  (is (false? (sut/node-env? 42)))
  (is (sut/node-env? (cljs.repl.node/repl-env)))
  ;; a node env wrapped in piggieback's delegating repl-env must still be detected
  (is (sut/node-env? (piggieback-cljs/delegating-repl-env (cljs.repl.node/repl-env)))))

(comment
  (run-tests))
