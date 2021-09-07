(ns suitable.hijack-rebel-readline-complete
  (:require [cljs-tooling.complete :as cljs-complete]
            cljs.env
            cljs.repl
            [rebel-readline.cljs.service.local :as rebel-cljs]
            [rebel-readline.clojure.line-reader :as clj-reader]
            [suitable.js-completions :refer [cljs-completions]]
            [suitable.utils :refer [wrapped-cljs-repl-eval]]))


;; This is a rather huge hack. rebel-readline doesn't really have any hooks for
;; other service provider to add to existing rebel-readline services. So what
;; we're doing here is to boldly redefine
;; `rebel-readline.cljs.service.local/-complete`. This is clearly very brittle
;; but the only way I found to piggieback our runtime completions without too
;; much setup code for the user to implement.

(defmethod clj-reader/-complete ::rebel-cljs/service [_ word {:keys [ns context]}]
  (let [options (cond-> nil
                  ns (assoc :current-ns ns))
        renv cljs.repl/*repl-env*
        cenv @cljs.env/*compiler*
        suitables (cljs-completions
                   (wrapped-cljs-repl-eval renv cenv)
                   word {:ns ns :context context})
        completions (cljs-complete/completions cenv word options)]
    (concat suitables completions)))
