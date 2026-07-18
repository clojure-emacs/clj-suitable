(ns suitable.browser-completion-test
  "Integration test for the shadow-cljs completion path over a *browser*
  runtime (issues #47 and #48).

  Like `suitable.shadow-completion-test`, this drives `complete-for-shadow-cljs`,
  but instead of a Node runtime it brings up a real browser runtime: it boots a
  shadow-cljs server, watches the `:browser-test` build, serves it over
  shadow's `:dev-http`, and loads it in headless Chrome. The shadow devtools
  client in the compiled app then connects back as a `:host :browser` runtime we
  can evaluate against.

  This lives in `src/test-integration` (alias `:shadow-test`) alongside the Node
  integration test. It needs JDK 21, a Node runtime with `ws`, and a Chrome
  binary. When no Chrome binary is found the test skips rather than failing, so
  it stays green in environments without a browser. Point it at a specific
  binary with the `SUITABLE_CHROME_BIN` environment variable."
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [shadow.cljs.devtools.api :as shadow]
   [shadow.cljs.devtools.server :as server]
   [suitable.complete-for-nrepl :refer [complete-for-nrepl]]))

(def ^:private build-id :browser-test)
(def ^:private out-dir "target/browser-test")
(def ^:private http-url "http://localhost:8123/index.html")

(def ^:private chrome-candidates
  ["/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
   "/Applications/Chromium.app/Contents/MacOS/Chromium"
   "/usr/bin/google-chrome"
   "/usr/bin/google-chrome-stable"
   "/usr/bin/chromium"
   "/usr/bin/chromium-browser"])

(defn- find-chrome []
  (or (System/getenv "SUITABLE_CHROME_BIN")
      (first (filter #(.canExecute (io/file %)) chrome-candidates))))

(defn- write-host-page! []
  (io/make-parents (str out-dir "/index.html"))
  (spit (str out-dir "/index.html")
        (str "<!doctype html><html><head><meta charset=\"utf-8\"></head>"
             "<body><script src=\"/js/main.js\"></script></body></html>")))

(def ^:private chrome-log
  (str (System/getProperty "java.io.tmpdir") "/suitable-chrome.log"))

(defn- launch-chrome [chrome]
  (let [profile (str (System/getProperty "java.io.tmpdir") "/suitable-chrome-" (System/nanoTime))
        pb (ProcessBuilder.
            ^java.util.List
            [chrome "--headless=new" "--disable-gpu" "--no-sandbox"
             ;; CI containers give /dev/shm too little space, which crashes the
             ;; renderer on load; write shared memory to a regular temp file.
             "--disable-dev-shm-usage" "--disable-software-rasterizer"
             "--no-first-run" "--no-default-browser-check"
             "--disable-background-networking" "--disable-component-update"
             "--disable-default-apps" "--disable-sync" "--disable-extensions"
             "--metrics-recording-only" "--mute-audio"
             (str "--user-data-dir=" profile)
             http-url])]
    (.redirectErrorStream pb true)
    (.redirectOutput pb (io/file chrome-log))
    (.start pb)))

(defn- runtime-connected?
  "The browser runtime is usable once a trivial cljs eval returns a result."
  []
  (try
    (let [{:keys [results]} (shadow/cljs-eval build-id "(+ 1 1)" {:ns 'cljs.user})]
      (seq results))
    (catch Throwable _ false)))

(defn- wait-for-runtime [timeout-ms]
  (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
    (loop []
      (cond
        (runtime-connected?) true
        (> (System/currentTimeMillis) deadline) false
        :else (do (Thread/sleep 500) (recur))))))

(defn- wait-for-build-output
  "Block until the compiled module exists, so Chrome doesn't load the host page
  before `main.js` has been written (a cold build takes several seconds; loading
  the page too early 404s and the runtime never connects)."
  [timeout-ms]
  (let [f (io/file (str out-dir "/js/main.js"))
        deadline (+ (System/currentTimeMillis) timeout-ms)]
    (loop []
      (cond
        (and (.exists f) (pos? (.length f))) true
        (> (System/currentTimeMillis) deadline) false
        :else (do (Thread/sleep 500) (recur))))))

(def ^:private ^:dynamic *runtime-up?* false)

(defn- with-shadow-browser-runtime [f]
  (if-let [chrome (find-chrome)]
    (do
      (write-host-page!)
      (server/start!)
      (shadow/watch build-id)
      (when-not (wait-for-build-output 120000)
        (throw (ex-info ":browser-test build did not produce main.js" {})))
      ;; write-host-page! again: shadow clears output-dir on the first compile,
      ;; which can delete an index.html written before the build ran.
      (write-host-page!)
      (let [proc (launch-chrome chrome)]
        (try
          (if (wait-for-runtime 120000)
            (binding [*runtime-up?* true] (f))
            (do
              (println "[browser-completion-test] Chrome process alive?" (.isAlive proc))
              (println "[browser-completion-test] --- Chrome log (" chrome-log ") ---")
              (println (try (slurp chrome-log) (catch Exception _ "(no log)")))
              (throw (ex-info "browser runtime did not connect (Chrome loaded the page but never registered a shadow runtime)" {}))))
          (finally
            (.destroy proc)
            (server/stop!)))))
    (do
      (println "[browser-completion-test] no Chrome binary found - skipping (set SUITABLE_CHROME_BIN to run)")
      (f))))

(use-fixtures :once with-shadow-browser-runtime)

(defn- complete [sym context]
  (complete-for-nrepl
   {:shadow.cljs.devtools.server.nrepl-impl/build-id build-id
    :session (atom {})
    :symbol sym
    :ns "cljs.user"
    :context context}))

(deftest browser-dynamic-completion
  (if-not *runtime-up?*
    (is true "skipped: no browser runtime")
    (do
      (testing "global completion over a browser runtime"
        (is (= [{:type "function" :candidate "js/console.log" :ns "js"}]
               (complete "js/console.lo" nil))))

      (testing "method completion via the `.` interop form (issues #47/#48)"
        ;; `(.lo js/console)` must complete to `.log`. Proven here to work over a
        ;; real browser runtime, which shows #47/#48's missing browser method
        ;; completions come from mranderson inlining (the inlined
        ;; introspection ns not loading), not the browser path itself.
        (is (= [{:type "function" :candidate ".log" :ns "js/console"}]
               (complete ".lo" "(__prefix__ js/console)")))))))
