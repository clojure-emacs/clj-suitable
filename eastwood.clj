;; avoid a corner case in Eastwood / tools.analyzer:
(require 'figwheel.main.api)

(disable-warning
 {:linter :constant-test
  :if-inside-macroexpansion-of #{'suitable.complete-for-nrepl/with-cljs-env}})
