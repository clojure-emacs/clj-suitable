(ns suitable.nrepl-shadow
  (:require
   [shadow.cljs.devtools.server]
   [shadow.cljs.devtools.config :as config]
   [suitable.nrepl :refer [start-clj-nrepl-server
                           start-cljs-nrepl-server
                           start-cljs-nrepl-client
                           cljs-nrepl-server
                           cljs-send-eval]]))

;;;; nrepl servers with shadow-cljs
;;; start and connect to localhost:7889 via cider for a cljs repl. connect to
;;; :7888 for the clojure side.

(defn -main [& args]
  (start-clj-nrepl-server)
  (shadow.cljs.devtools.server/start!
   (merge
    (config/load-cljs-edn)
    {:nrepl {:port 7889}})))
