(ns suitable.main
  (:require [cljs.test :refer-macros [run-tests]]
            [suitable.js-introspection-test]))

(enable-console-print!)

(run-tests 'suitable.js-introspection-test)
