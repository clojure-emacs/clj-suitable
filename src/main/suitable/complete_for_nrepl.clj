(ns suitable.complete-for-nrepl
  (:require [clojure.edn :as edn]
            [suitable.js-completions :refer [cljs-completions]]
            [clojure.string :as string]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;; 2019-08-15 rk: FIXME! When being build as part of cider-nrepl, names of
;; cider-nrepl dependencies get munged by mranderson. This also munges cljs
;; namespaces but not references to them. So as a hack we grab the name of this
;; ns (can't use *ns* when bundled so abusing `dummy-var` for that) which has
;; the munged prefix. We then convert that into the cljs namespace we need. This
;; of course breaks when suitable.complete-for-nrepl is renamed(!).
(def dummy-var)
(def this-ns (:ns (meta #'dummy-var)))
(def munged-js-introspection-ns (string/replace (name (clojure.core/ns-name this-ns)) #"complete-for-nrepl$" "js-introspection"))
(def munged-js-introspection-js-name (symbol (string/replace munged-js-introspection-ns #"-" "_")))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(def debug? false)

(defonce ^{:private true} resolved-vars (atom nil))

(defn- resolve-vars!
  "This lazy loads runtime state we depend on so that there are no static
  dependencies to piggieback or cljs."
  []
  (or
   @resolved-vars
   (let [piggieback-vars (cond
                           (resolve 'cider.piggieback/*cljs-compiler-env*)
                           {:cenv-var (resolve 'cider.piggieback/*cljs-compiler-env*)
                            :renv-var (resolve 'cider.piggieback/*cljs-repl-env*)
                            :opts-var (resolve 'cider.piggieback/*cljs-repl-options*)}

                           (resolve 'piggieback.core/*cljs-compiler-env*)
                           {:cenv-var (resolve 'piggieback.core/*cljs-compiler-env*)
                            :renv-var (resolve 'piggieback.core/*cljs-repl-env*)
                            :opts-var (resolve 'piggieback.core/*cljs-repl-options*)}

                           :else nil)

         cljs-vars (do
                     (require 'cljs.repl)
                     (require 'cljs.analyzer)
                     (require 'cljs.env)
                     {:cljs-cenv-var (resolve 'cljs.env/*compiler*)
                      :cljs-ns-var (resolve 'cljs.analyzer/*cljs-ns*)
                      :cljs-repl-setup-fn (resolve 'cljs.repl/setup)
                      :cljs-evaluate-fn (resolve 'cljs.repl/evaluate)
                      :cljs-eval-cljs-fn (resolve 'cljs.repl/eval-cljs)
                      :cljs-load-namespace-fn (resolve 'cljs.repl/load-namespace)})]

     (reset! resolved-vars
             (merge cljs-vars piggieback-vars)))))

(defn- extract-cljs-state
  [session]
  (let [s @session
        {:keys [cenv-var renv-var opts-var]} (resolve-vars!)]
    {:cenv (get s cenv-var)
     :renv (get s renv-var)
     :opts (get s opts-var)}))

(defn- update-cljs-state!
  [session cenv renv]
  (let [{:keys [cenv-var renv-var]} (resolve-vars!)]
    (swap! session assoc
           cenv-var cenv
           renv-var renv)))

(defmacro with-cljs-env
  "Binds `cljs.env/*compiler*`, `cljs.analyzer/*cljs-ns*`, assigns bindings from
  `resolve-vars!` and runs `body`."
  [cenv ns cljs-bindings & body]
  (let [vars (gensym)]
    `(let [~vars (resolve-vars!)]
       (with-bindings {(:cljs-cenv-var ~vars) ~cenv
                       (:cljs-ns-var ~vars) (if (string? ~ns) (symbol ~ns) ~ns)}
         (let ~(into []
                     (apply concat
                            (for [sym cljs-bindings]
                              `(~sym ((keyword '~sym) ~vars)))))
           ~@body)))))

(defn- cljs-eval
  "Grabs the necessary compiler and repl envs from the message and uses the plain
  cljs.repl interface for evaluation. Returns a map with :value and :error. Note
  that :value will be a still stringified edn value."
  [session ns code]
  (let [{:keys [cenv renv opts]} (extract-cljs-state session)
        ;; when run with mranderson as an inlined dep, the ns and it's interns
        ;; aren't recognized correctly by the analyzer, suppress an undefined
        ;; var warining for `js-properties-of-object`
        ;; TODO only add when run as inline-dep??
        opts (assoc opts :warnings {:undeclared-var false})]
    (with-cljs-env cenv ns
      [cljs-eval-cljs-fn]
      (try
        (let [result (cljs-eval-cljs-fn renv @cenv (read-string code) opts)]
          (update-cljs-state! session cenv renv)
          {:value result})
        (catch Exception e {:error e})))))

(defn node-env?
  "Returns true iff RENV is a NodeEnv or more precisely a piggiebacked delegating
  NodeEnv. Since the renv is wrapped we can't just compare the type but have to
  do some string munging according to
  `cider.piggieback/generate-delegating-repl-env`."
  [renv]
  (= (some-> 'cljs.repl.node.NodeEnv
             ^Class (resolve)
             .getName
             (string/replace "." "_"))
     (-> renv
         class
         .getName
         (string/replace #".*Delegating" ""))))

(defn ensure-suitable-cljs-is-loaded [session]
  (let [{:keys [cenv renv opts]} (extract-cljs-state session)]
    (with-cljs-env cenv 'cljs.user
      [cljs-repl-setup-fn cljs-load-namespace-fn cljs-evaluate-fn]

      (when (node-env? renv)
        ;; rk 2019-09-02 FIXME
        ;; Due to this issue:
        ;; https://github.com/clojure-emacs/cider-nrepl/pull/644#issuecomment-526953982
        ;; we can't just eval with a node env but have to make sure that it's
        ;; local buffer is initialized for this thread.
        (cljs-repl-setup-fn renv opts))

      (when (not= "true" (some-> (cljs-evaluate-fn
                                  renv "<suitable>" 1
                                  ;; see above, would be suitable.js_introspection
                                  (format "!!goog.getObjectByName('%s')" munged-js-introspection-js-name)) :value))
        (try
          ;; see above, would be suitable.js-introspection
          (cljs-load-namespace-fn renv (read-string munged-js-introspection-ns) opts)
          (catch Exception e
            ;; when run with mranderson, cljs does not seem to handle the ns
            ;; annotation correctly and does not recognize the namespace even
            ;; though it loads correctly.
            (when-not (and (string/includes? munged-js-introspection-ns "inlined-deps")
                           (string/includes? (string/lower-case (str e)) "does not provide a namespace"))
              (throw e))))
        (cljs-evaluate-fn renv "<suitable>" 1 (format "goog.require(\"%s\");%s"
                                                      munged-js-introspection-js-name
                                                      (if debug? " console.log(\"suitable loaded\");" "")))
        ;; wait as depending on the implemention of goog.require provide by the
        ;; cljs repl might be async. See
        ;; https://github.com/rksm/clj-suitable/issues/1 for more details.
        (Thread/sleep 100)

        (update-cljs-state! session cenv renv)))))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(def ^:private ^:dynamic *object-completion-state* nil)

(defn- empty-state [] {:context ""})

(defn handle-completion-msg!
  "Tracks the completion state (contexts) and reuses old contexts if necessary.
  State is kept in session."
  [{:keys [session symbol context ns extra-metadata] :as _msg}
   cljs-eval-fn
   ensure-loaded-fn]
  (let [prev-state (or (get @session #'*object-completion-state*) (empty-state))
        prev-context (:context prev-state)
        context (cond
                  (nil? context) ""
                  (= context ":same") prev-context
                  (= context "nil") ""
                  :else context)
        options-map {:context context :ns ns :extra-metadata extra-metadata}]

    ;; make sure we can call the object completion api in cljs, i.e. loads
    ;; suitable cljs code if necessary.
    (ensure-loaded-fn session)

    (when (not= prev-context context)
      (swap! session #(merge % {#'*object-completion-state*
                                (assoc prev-state :context context)})))

    (try
      (cljs-completions cljs-eval-fn symbol options-map)
      (catch Exception e
        (if debug?
          (do (println "suitable error")
              (.printStackTrace e))
          (println (format "suitable error (enable %s/debug for more details): %s" this-ns (.getMessage e))))))))

(defn- complete-for-default-cljs-env
  [{:keys [session] :as msg}]
  (let [cljs-eval-fn
        (fn [ns code] (let [result (cljs-eval session ns code)]
                        {:error (some->> result :error str)
                         :value (some->> result :value edn/read-string)}))
        ensure-loaded-fn ensure-suitable-cljs-is-loaded]
    (handle-completion-msg! msg cljs-eval-fn ensure-loaded-fn)))

(defn- shadow-cljs? [msg]
  (:shadow.cljs.devtools.server.nrepl-impl/build-id msg))

(defn- complete-for-shadow-cljs
  "Shadow-cljs handles evals quite differently from normal cljs so we need some
  special handling here."
  [msg]
  (let [build-id (-> msg :shadow.cljs.devtools.server.nrepl-impl/build-id)
        cljs-eval-fn
        (fn [ns code]
          (let [result ((resolve 'shadow.cljs.devtools.api/cljs-eval) build-id code {:ns (symbol ns)})]
            {:error (some->> result :err str)
             :value (some->> result :results first edn/read-string)}))
        ensure-loaded-fn (fn [_] nil)]
    (handle-completion-msg! msg cljs-eval-fn ensure-loaded-fn)))

(defn complete-for-nrepl
  "Computes the completions using the cljs environment found in msg."
  [msg]
  (if (shadow-cljs? msg)
    (complete-for-shadow-cljs msg)
    (complete-for-default-cljs-env msg)))
