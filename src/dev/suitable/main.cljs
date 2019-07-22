(ns suitable.main
  (:require [cljs.test :refer-macros [run-tests]]
            [suitable.tests]))

(enable-console-print!)

(run-tests 'suitable.tests)

