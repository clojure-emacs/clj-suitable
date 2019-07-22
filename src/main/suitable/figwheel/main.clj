(ns suitable.figwheel.main
  (:require figwheel.main
            ;; requiring this will override the rebel-readline completion
            ;; service
            suitable.hijack-rebel-readline-complete))

(def extra-figwheel-args ["--compile-opts" "{:preloads [suitable.js-introspection]}"])

(defn -main [& args]
  (apply figwheel.main/-main (concat extra-figwheel-args args)))
