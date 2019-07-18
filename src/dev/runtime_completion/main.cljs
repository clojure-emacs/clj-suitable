(ns runtime-completion.main
  (:require [cljs.test :refer-macros [run-tests]]
            [runtime-completion.tests]))

(enable-console-print!)

(run-tests 'runtime-completion.tests)

