(ns suitable.middleware
  (:require [suitable.complete-for-nrepl :refer [complete-for-nrepl]]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;; rk 2019-07-23: this is adapted from refactor_nrepl.middleware
;; Compatibility with the legacy tools.nrepl and the new nREPL 0.4.x.
;; The assumption is that if someone is using old lein repl or boot repl
;; they'll end up using the tools.nrepl, otherwise the modern one.
(when-not (resolve 'set-descriptor!)
  (if (find-ns 'clojure.tools.nrepl)
    (require
     '[clojure.tools.nrepl.middleware :refer [set-descriptor!]]
     '[nrepl.misc :refer [response-for]]
     '[clojure.tools.nrepl.transport :as transport])
    (require
     '[nrepl.middleware :refer [set-descriptor!]]
     '[nrepl.misc :refer [response-for]]
     '[nrepl.transport :as transport])))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn- completion-answer
  "Creates an answer message with computed completions. Note that no done state is
  set in the message as we expect the default handler to finish the completion
  response."
  ([msg completions]
   (completion-answer msg completions false))
  ([{:keys [id session] :as _msg} completions done?]
   (cond-> {:completions completions}
     id (assoc :id id)
     session (assoc :session (if (instance? clojure.lang.AReference session)
                               (-> session meta :id)
                               session))
     done? (assoc :status #{"done"}))))

(defn- cljs-dynamic-completion-handler
  "Handles op = \"complete\". Will try to fetch object completions and puts them
  on the wire with transport but also allows the default completion handler to
  act."
  [standalone? next-handler {:keys [_id session _ns transport op symbol] :as msg}]

  (if (and
       ;; completion request?
       (= op "complete") (not= "" symbol)
       ;; cljs?
       (some #(get @session (resolve %)) '(piggieback.core/*cljs-compiler-env*
                                           cider.piggieback/*cljs-compiler-env*)))

    (let [completions (complete-for-nrepl msg)]
      ;; in standalone mode we send an answer, even if we have no completions
      (if standalone?
        (transport/send transport (completion-answer msg completions true))

        ;; otherwise we send if we have some completions but leave it to the
        ;; other middleware to send additional + marking the message as done
        (do (when completions
              (transport/send transport (completion-answer msg completions)))
            (next-handler msg))))

    (next-handler msg)))

(defn wrap-complete [handler]
  (fn [msg] (cljs-dynamic-completion-handler false handler msg)))

(defn wrap-complete-standalone [handler]
  (fn [msg] (cljs-dynamic-completion-handler true handler msg)))

(set-descriptor! #'wrap-complete
                 {:doc "Middleware providing runtime completion support - in addition to normal cljs completions."
                  :requires #{"clone"}
                  :expects #{"complete" "eval"}
                  :handles {;; "complete"
                            ;; {:doc "Return a list of symbols matching the specified (partial) symbol."
                            ;;  :requires {"ns" "The symbol's namespace"
                            ;;             "symbol" "The symbol to lookup"
                            ;;             "session" "The current session"}
                            ;;  :optional {"context" "Completion context for compliment."
                            ;;             "extra-metadata" "List of extra-metadata fields. Possible values: arglists, doc."}
                            ;;  :returns {"completions" "A list of possible completions"}}
                            ;; "complete-doc"
                            ;; {:doc "Retrieve documentation suitable for display in completion popup"
                            ;;  :requires {"ns" "The symbol's namespace"
                            ;;             "symbol" "The symbol to lookup"}
                            ;;  :returns {"completion-doc" "Symbol's documentation"}}
                            ;; "complete-flush-caches"
                            ;; {:doc "Forces the completion backend to repopulate all its caches"}
                            }})

(set-descriptor! #'wrap-complete-standalone
                 {:doc "Middleware providing runtime completion support."
                  :requires #{"clone"}
                  :expects #{"complete" "eval"}
                  :handles {}})
