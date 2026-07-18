(ns suitable.browser-test-init
  "Trivial entry point for the :browser-test shadow build. Loading the compiled
  module in a (headless) browser is enough to make a shadow-cljs runtime connect
  back to the server; see suitable.browser-completion-test.")

(defn init []
  (js/console.log "suitable browser-test runtime ready"))
