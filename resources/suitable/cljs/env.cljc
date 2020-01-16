(ns suitable.cljs.env
  (:require [cljs.env :as env]
            #?@(:clj [[cljs.analyzer.api :as ana]
                      [cljs.build.api :as build]
                      [cljs.compiler.api :as comp]
                      [clojure.java.io :as io]])))

(def test-namespace "suitable/test_ns.cljs" )

(defn create-test-env []
  #?(:clj
     (let [opts (build/add-implicit-options {:cache-analysis true, :output-dir "target/out"})
           env (env/default-compiler-env opts)]
       (comp/with-core-cljs env opts
         (fn []
           (let [r (io/resource test-namespace)]
             (assert r (str "Cannot find " test-namespace " on the classpath, did you set it up correctly?"))
             (ana/analyze-file env r opts))))
       @env)

     :cljs
     (do (repl/eval '(require (quote suitable.test-ns)) 'suitable.test-env)
         @env/*compiler*)))
