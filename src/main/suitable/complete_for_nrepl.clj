(ns suitable.complete-for-nrepl
  (:require [clojure.edn :as edn]
            [suitable.js-completions :refer [cljs-completions]]
            cljs.repl
            [clojure.string :as string]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;;/2019-08-15 rk: FIXME! When being build as part of cider-nrepl, names of
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

(defn- cljs-eval
  "Grabs the necessary compiler and repl envs from the message and uses the plain
  cljs.repl interface for evaluation. Returns a map with :value and :error. Note
  that :value will be a still stringified edn value."
  [session ns code]
  (let [s @session
        renv (get s #'cider.piggieback/*cljs-repl-env*)
        cenv (get s #'cider.piggieback/*cljs-compiler-env*)
        opts (get s #'cider.piggieback/*cljs-repl-options*)
        ;; when run with mranderson as an inlined dep, the ns and it's interns
        ;; aren't recognized correctly by the analyzer, suppress an undefined
        ;; var warining for `js-properties-of-object`
        ;; TODO only add when run as inline-dep?? 
        opts (assoc opts :warnings {:undeclared-var false})]
    (binding [cljs.env/*compiler* cenv
              cljs.analyzer/*cljs-ns* (if (string? ns) (symbol ns) ns)]
      (try
        (let [result (cljs.repl/eval-cljs renv @cenv (edn/read-string code) opts)]
          (swap! session assoc
                 #'cider.piggieback/*cljs-compiler-env* cenv
                 #'cider.piggieback/*cljs-repl-env* renv)
          {:value result})
        (catch Exception e {:error e})))))

(defn ensure-suitable-cljs-is-loaded [session]
  (let [s @session
        renv (get s #'cider.piggieback/*cljs-repl-env*)
        cenv (get s #'cider.piggieback/*cljs-compiler-env*)
        opts (get s #'cider.piggieback/*cljs-repl-options*)]
    (binding [cljs.env/*compiler* cenv
              cljs.analyzer/*cljs-ns* 'cljs.user]
      (when (not= "true" (:value (cljs.repl/evaluate
                                  renv "<suitable>" 1
                                  ;; see above, would be suitable.js_introspection
                                  (format "!!goog.getObjectByName('%s')" munged-js-introspection-js-name))))
        ;; see above, would be suitable.js-introspection
        (try
          (cljs.repl/load-namespace renv (read-string munged-js-introspection-ns) opts)
          (catch Exception e
            ;; when run with mranderson, cljs does not seem to handle the ns
            ;; annotation correctly and does not recognize the namespace even
            ;; though it loads correctly.
            (when-not (and (string/includes? munged-js-introspection-ns "inlined-deps")
                           (string/includes? (string/lower-case (str e)) "does not provide a namespace"))
              (throw e))))
        (cljs.repl/evaluate renv "<suitable>" 1 (format "goog.require(\"%s\"); console.log(\"suitable loaded\"); "
                                                        munged-js-introspection-js-name))
        ;; wait as depending on the implemention of goog.require provide by the
        ;; cljs repl might be async. See
        ;; https://github.com/rksm/clj-suitable/issues/1 for more details.
        (Thread/sleep 100)

        (swap! session assoc
               #'cider.piggieback/*cljs-compiler-env* cenv
               #'cider.piggieback/*cljs-repl-env* renv)))))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(def ^:private ^:dynamic *object-completion-state* nil)

(defn- empty-state [] {:context ""})

(defn handle-completion-msg!
  "Tracks the completion state (contexts) and reuses old contexts if necessary.
  State is kept in session."
  [{:keys [session symbol context ns extra-metadata] :as msg} cljs-eval-fn]
  (let [prev-state (or (get @session #'*object-completion-state*) (empty-state))
        prev-context (:context prev-state)
        context (cond
                  (= context ":same") prev-context
                  (= context "nil") ""
                  :else context)
        options-map {:context context :ns ns :extra-metadata extra-metadata}]

    (ensure-suitable-cljs-is-loaded session)

    (when (not= prev-context context)
      (swap! session #(merge % {#'*object-completion-state*
                                (assoc prev-state :context context)})))

    (cljs-completions cljs-eval-fn symbol options-map)))

(defn complete-for-nrepl
  "Computes the completions using the cljs environment found in msg."
  [{:keys [session ns] :as msg}]
  (let [cljs-eval-fn
        (fn [ns code] (let [result (cljs-eval session ns code)]
                        {:error (some->> result :error str)
                         :value (some->> result :value edn/read-string)}))]
    (handle-completion-msg! msg cljs-eval-fn)))

