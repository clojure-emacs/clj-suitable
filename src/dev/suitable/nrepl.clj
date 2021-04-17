(ns suitable.nrepl
  (:require cider.nrepl
            cider.piggieback
            [clojure.pprint :refer [cl-format pprint]]
            nrepl.core
            nrepl.server
            [suitable.middleware :refer [wrap-complete]]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

;; a la https://github.com/nrepl/piggieback/issues/91
;; 1. start nrepl server with piggieback
;; 2. get session
;; 3. send cljs start form (e.g. figwheel)
;; 4. ...profit!

;; 1. start nrepl server with piggieback
(defonce clj-nrepl-server (atom nil))


(defn start-clj-nrepl-server []
  (let [middlewares (map resolve cider.nrepl/cider-middleware)
        middlewares (if-let [rf (resolve 'refactor-nrepl.middleware/wrap-refactor)]
                      (conj middlewares rf) middlewares)
        handler (apply nrepl.server/default-handler middlewares)]
    (pprint middlewares)
    (reset! clj-nrepl-server (nrepl.server/start-server :handler handler :port 7888)))
  (cl-format true "clj nrepl server started~%"))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defonce cljs-nrepl-server (atom nil))
(defonce cljs-send-msg (atom nil))
(defonce cljs-client (atom nil))
(defonce cljs-client-session (atom nil))

(defn start-cljs-nrepl-server []
  (let [middlewares (map resolve cider.nrepl/cider-middleware)
        middlewares (conj middlewares #'cider.piggieback/wrap-cljs-repl)
        middlewares (conj middlewares #'wrap-complete)
        ;; handler (nrepl.server/default-handler #'cider.piggieback/wrap-cljs-repl)
        handler (apply nrepl.server/default-handler middlewares)]
    (reset! cljs-nrepl-server (nrepl.server/start-server :handler handler :port 7889)))
  (cl-format true "cljs nrepl server started~%"))

(defn start-cljs-nrepl-client []
  (let [conn (nrepl.core/connect :port 7889)
        c (nrepl.core/client conn 1000)
        sess (nrepl.core/client-session c)]
    (reset! cljs-client c)
    (reset! cljs-client-session sess)
    (cl-format true "nrepl client started~%")
    (reset! cljs-send-msg
            (fn [msg] (let [response-seq (nrepl.core/message sess msg)]
                         (cl-format true "nrepl msg send~%")
                         (pprint (doall response-seq)))))))

(defn cljs-send-eval [code]
  (@cljs-send-msg {:op :eval :code code}))
