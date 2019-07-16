(ns cljs-object-completion.main
  (:require [cljs.test :refer-macros [run-tests]]
            [cljs-object-completion.tests]))

(enable-console-print!)

(run-tests 'cljs-object-completion.tests)

