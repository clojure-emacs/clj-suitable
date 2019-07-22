(ns suitable.middleware
  (:require [clojure.edn :as edn]
            [nrepl.middleware :refer [set-descriptor!]]
            [nrepl.transport :as transport]
            [suitable.js-completions :refer [cljs-completions]])
  (:import nrepl.transport.Transport))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn- cljs-eval
  "Abuses the nrepl handler `piggieback/do-eval` in that it injects a pseudo
  transport into it that simply captures it's output."
  [session ns code]
  (let [result (transient [])
        transport (reify Transport
                    (recv [this] this)
                    (recv [this timeout] this)
                    (send [this response] (conj! result response) this))
        eval-fn (or (resolve 'piggieback.core/do-eval)
                    (resolve 'cider.piggieback/do-eval))]
    (eval-fn {:session session :transport transport :code code :ns ns})
    (persistent! result)))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn completion-answer
  "Creates an answer message with computed completions. Note that no done state is
  set in the message as we expect the default handler to finish the completion
  response."
  [{:keys [id session] :as msg} completions]
  (merge (when id {:id id})
         (when session {:session (if (instance? clojure.lang.AReference session)
                                   (-> session meta :id)
                                   session)})
         {:completions completions}))

(def ^:private ^:dynamic *object-completion-state* nil)

(defn- empty-state [] {:context ""})

(defn handle-completion-msg-stateful
  "Tracks the completion state (contexts) and reuses old contexts if necessary.
  State is kept in session."
  [{:keys [session symbol context ns extra-metadata] :as msg} cljs-eval-fn]
  (let [prev-state (or (get @session #'*object-completion-state*) (empty-state))
        same-context? (= context ":same")
        context (if same-context? (:context prev-state) context)
        context (if (= context "nil") "" context)
        options-map {:context context :ns ns :extra-metadata extra-metadata}]
    (swap! session #(merge % {#'*object-completion-state*
                              (if same-context?
                                prev-state
                                (assoc prev-state :context context))}))
    (when-let [completions (cljs-completions cljs-eval-fn symbol options-map)]
      (completion-answer msg completions))))

(defn- cljs-dynamic-completion-handler
  "Handles op = \"complete\". Will try to fetch object completions but also allows
  the default completion handler to act."
  [next-handler {:keys [id session ns transport op symbol] :as msg}]

  (when (and (= op "complete")
             ;; cljs repl?
             (not= "" symbol)
             (some #(get @session (resolve %)) '(piggieback.core/*cljs-compiler-env*
                                                 cider.piggieback/*cljs-compiler-env*)))

    (let [cljs-eval-fn
          (fn [ns code] (let [result (cljs-eval session ns code)]
                          {:error (some->> result (map :err) (remove nil?) not-empty (apply str))
                           :value (some->> result last :value edn/read-string)}))
          answer (handle-completion-msg-stateful msg cljs-eval-fn)]
      (when answer (transport/send transport answer))))

  ;; call next-handler in any case - we want the default completions as well
  (next-handler msg))

(defn wrap-complete [handler]
  (fn [msg] (cljs-dynamic-completion-handler handler msg)))

(set-descriptor! #'wrap-complete
                 {:doc "Middleware providing runtime completion support."
                  :requires #{"clone"}
                  :expects #{"complete" "eval"}
                  :handles {"complete"
                            {:doc "Return a list of symbols matching the specified (partial) symbol."
                             :requires {"ns" "The symbol's namespace"
                                        "symbol" "The symbol to lookup"
                                        "session" "The current session"}
                             :optional {"context" "Completion context for compliment."
                                        "extra-metadata" "List of extra-metadata fields. Possible values: arglists, doc."}
                             :returns {"completions" "A list of possible completions"}}
                            ;; "complete-doc"
                            ;; {:doc "Retrieve documentation suitable for display in completion popup"
                            ;;  :requires {"ns" "The symbol's namespace"
                            ;;             "symbol" "The symbol to lookup"}
                            ;;  :returns {"completion-doc" "Symbol's documentation"}}
                            ;; "complete-flush-caches"
                            ;; {:doc "Forces the completion backend to repopulate all its caches"}
                            }})

