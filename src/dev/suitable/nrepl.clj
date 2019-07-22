(ns suitable.nrepl
  (:require cider.nrepl
            cider.piggieback
            [clojure.pprint :refer [cl-format pprint]]
            figwheel.main
            figwheel.main.api
            nrepl.core
            nrepl.server
            refactor-nrepl.middleware
            [suitable.middleware :refer [wrap-complete]]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

;; a la https://github.com/nrepl/piggieback/issues/91
;; 1. start nrepl server with piggieback
;; 2. get session
;; 3. send cljs start form (e.g. nashorn or figwheel)
;; 4. ...profit!

;; 1. start nrepl server with piggieback
(defonce clj-nrepl-server (atom nil))




(defn start-clj-nrepl-server []
  (let [middlewares (map resolve cider.nrepl/cider-middleware)
        middlewares (conj middlewares (resolve 'refactor-nrepl.middleware/wrap-refactor))
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

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn restart-cljs-server []
  (when @cljs-nrepl-server
    (nrepl.server/stop-server @cljs-nrepl-server))
  (require 'figwheel.main.api)
  (try (figwheel.main.api/stop-all) (catch Exception e (prn e)))

  (start-cljs-nrepl-server)
  (start-cljs-nrepl-client))

(defn -main [& args]
  (start-clj-nrepl-server)

  (start-cljs-nrepl-server)
  (start-cljs-nrepl-client)
  ;; (cljs-send-eval "(require 'figwheel.main) (figwheel.main/start :fig)")
  )

;; (restart-cljs-server)



;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
  (pprint (doall (nrepl.core/message sess1 {:op :eval :code "(require 'cider.piggieback) (require 'cljs.repl.nashorn) (cider.piggieback/cljs-repl (cljs.repl.nashorn/repl-env))"})))
  (pprint (doall (nrepl.core/message sess1 {:op :eval :code "(require 'figwheel.main) (figwheel.main/start :fig)"})))
  (pprint (doall (nrepl.core/message sess1 {:op :eval :code "(require 'figwheel.main) (figwheel.main/stop-builds :fig)"})))
  (pprint (doall (nrepl.core/message sess1 {:op :eval :code ":cljs/quit"})))
  (pprint (doall (nrepl.core/message sess1 {:op :eval :code "js/console"})))
  (pprint (doall (nrepl.core/message sess1 {:op :eval :code "123"})))
  (nrepl.core/message sess1 {:op :eval :code "(list 1 2 3)"})
  )



(comment

  (do (start-nrepl-server)
      (start-nrepl-client)
      (send-eval "(require 'figwheel.main) (figwheel.main/start :fig)"))

  (require 'figwheel.main.api) (figwheel.main.api/cljs-repl "fig")
  123
  (send-eval "(require 'suitable.core)")
  (send-eval "123")

  (send-eval "(require 'figwheel.main.api) (figwheel.main.api/cljs-repl \"fig\")")
  (send-eval ":cljs/quit")

  (@send-msg {:op :close})

  (@send-msg {:op :complete :symbol "js/co" :ns "cljs.user" :context nil})
  (@send-msg {:op :complete :symbol "cljs." :ns "suitable.core" :context nil})

  (require '[cider.nrepl.inlined-deps.cljs-tooling.v0v3v1.cljs-tooling.complete :as cljs-complete])
  (require '[cider.nrepl.middleware.util.cljs :as cljs])

  (let [ns (symbol "cljs.user")
        prefix (str "cljs.co")
        extra-metadata (set (map keyword nil))
        cljs-env (some-> (figwheel.main.api/repl-env "fig") :repl-options deref :compiler-env deref)]
    (cljs-complete/completions cljs-env prefix {:context-ns ns
                                                :extra-metadata extra-metadata}))

  ;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-



  (send-eval "123")


  (send-eval "(require 'figwheel.main) (figwheel.main/start :fig)")
  (send-eval "*ns*")
  (send-eval "js/console")


  (-> @figwheel.main/build-registry (get "fig"))
  (figwheel.main.watching/reset-watch!)

  (def server (-> @figwheel.main/build-registry (get "fig")  :repl-env :server deref))
  (.stop server)
  (figwheel.main/stop-builds :fig)

  )

