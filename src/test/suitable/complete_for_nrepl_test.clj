(ns suitable.complete-for-nrepl-test
  (:require [clojure.test :as t :refer [deftest is run-tests use-fixtures testing compose-fixtures]]
            [clojure.java.shell]
            [cider.piggieback :as piggieback]
            [cider.nrepl :refer [cider-nrepl-handler cider-middleware]]
            [nrepl.core :as nrepl]
            [nrepl.server :refer [start-server default-handler]]
            [suitable.middleware :refer [wrap-complete-standalone]]))

(require 'cljs.repl)

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

(defmacro with-repl-env
  {:style/indent 1}
  [renv-form & body]
  `(do
     (alter-var-root #'handler (constantly (->> cider-middleware
                                                (map resolve)
                                                (reverse)
                                                (cons #'piggieback/wrap-cljs-repl)
                                                (cons #'wrap-complete-standalone)
                                                (reverse)
                                                (apply default-handler))))
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

  (require 'cljs.repl.node)
  (with-repl-env (cljs.repl.node/repl-env)
    (testing "cljs repl is active"
      (let [response (message {:op :eval
                               :code (nrepl/code (js/Object.))})
            explanation (pr-str response)]
        (is (= "cljs.user" (:ns response))
            explanation)
        (is (= ["#js{}"] (:value response))
            explanation)
        (is (= #{"done"} (:status response))
            explanation)))))

(deftest suitable-node
  (require 'cljs.repl.node)
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
            (pr-str response))))))

(comment
  (run-tests))
