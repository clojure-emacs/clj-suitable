(ns suitable.utils
  (:require
   [cljs.env]
   [cljs.repl]))

(defn wrapped-cljs-repl-eval
  "cljs-eval-fn for `suitable.cljs-completions` that can be used when a
  repl-env and compiler env are accessible, e.g. when running a normal repl."
  [repl-env compiler-env]
  (fn [_ns code]
    (try
      {:value (->> code
                   read-string
                   (cljs.repl/eval-cljs repl-env compiler-env)
                   read-string)}
      (catch Exception e {:error e}))))
