(ns suitable.nrepl-figwheel
  (:require nrepl.server
            [suitable.nrepl :refer [start-clj-nrepl-server
                                    start-cljs-nrepl-server
                                    start-cljs-nrepl-client
                                    cljs-nrepl-server]]))

;;;; nrepl servers with figwheel
;;; This is useful for development. It will start two nrepl servers. The outer
;;; one on localhost:7888 allows to develop the clojure code. The inner server
;;; is a cljs repl and connected to figwheel. You can connect with cider
;;; 'figwheel-main build 'fig and test the cljs side of things (e.g. visit
;;; suitable.main). Also open localhost:9500 in a web browser to get the
;;; figwheel js enviornment loaded.

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
  (start-cljs-nrepl-client))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
  (do (start-nrepl-server)
      (start-nrepl-client)
      (cljs-send-eval "(require 'figwheel.main) (figwheel.main/start :fig)"))

  (cljs-send-eval "(require 'suitable.core)")
  (cljs-send-eval "123")

  (cljs-send-eval "(require 'figwheel.main.api) (figwheel.main.api/cljs-repl \"fig\")")
  (cljs-send-eval ":cljs/quit")
  )

