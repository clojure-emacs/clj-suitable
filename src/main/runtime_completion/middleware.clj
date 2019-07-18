(ns runtime-completion.middleware
  (:require [clojure.edn :as edn]
            [clojure.pprint :refer [cl-format]]
            [clojure.set :refer [rename-keys]]
            [clojure.spec.alpha :as s]
            [clojure.string :refer [starts-with?]]
            [clojure.zip :as zip]
            [nrepl.middleware :refer [set-descriptor!]]
            [nrepl.transport :as transport]
            [runtime-completion.ast :refer [tree-zipper]]
            [runtime-completion.spec :as spec] )
  (:import nrepl.transport.Transport))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

;; (println (cljs-eval session "(properties-by-prototype js/console)" ns))
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

(def ^:private obj-expr-eval-template "(do
  (require 'runtime-completion.core)
  (runtime-completion.core/property-names-and-types ~A))")

(defn- js-properties-of-object
  [obj-expr {:keys [session ns]}]
  {:pre [(s/valid? ::spec/non-empty-string obj-expr)]
   :post [(s/valid? (s/keys :error (s/nilable string?)
                            :properties (s/coll-of (s/keys {:name ::spec/non-empty-string
                                                            :hierarchy int?
                                                            :type ::spec/non-empty-string}))) %)]}
  (let [result (cljs-eval session ns (cl-format nil obj-expr-eval-template obj-expr))
        error-descr (some->> result (map :err) (remove nil?) not-empty (apply str))
        props (some->> result last :value edn/read-string)]
    {:error error-descr
     :properties props}))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn find-prefix [form]
  (loop [node (tree-zipper form)]
    (if (= '__prefix__ (zip/node node))
      node
      (when-not (zip/end? node)
        (recur (zip/next node))))))

(defn expr-for-parent-obj
  "Given the context and symbol of a completion request, will try to find an
  expression that evaluates to the object being accessed."
  [{:keys [ns context symbol]}]
  (when-let [form (with-in-str context (read *in* nil nil))]
    (let [prefix (find-prefix form)
          left-sibling (zip/left prefix)
          first? (nil? left-sibling)
          first-sibling (and (not first?) (some-> prefix zip/leftmost zip/node))
          dot-fn? (starts-with? symbol ".")]

      (cond
        (and first? dot-fn?)
        (some-> prefix zip/right zip/node str)

        ;; a .. form: if __prefix__ is a prop deeper than one level we need the ..
        ;; expr up to that point. If just the object that is accessed is left of
        ;; prefix, we can take that verbatim.
        ;; (.. js/console log) => js/console
        ;; (.. js/console memory jsHeapSizeLimit) => (.. js/console memory)
        (and first-sibling (= ".." (str first-sibling)) left-sibling)
        (let [lefts (-> prefix zip/lefts)]
          (if (<= (count lefts) 2)
            (str (last lefts))
            (str lefts)))

        ;; (.. js/window -console (log "foo")) => (.. js/window -console)
        (and first? (-> prefix zip/up zip/leftmost zip/node str (= "..")))
        (-> prefix zip/up zip/lefts str)))))

(defn handle-completion-msg
  "Given some context (the toplevel form that has changed) and a symbol string
  that represents the last typed input, we try to find out if the context/symbol
  are object access (property access or method call). If so, we try to extract a
  form that we can evaluate to get the object that is accessed. If we get the
  object, we enumerate it's properties and methods and generate a list of
  matching completions for those."
  [{:keys [id session transport op ns symbol extra-metadata] :as msg} context]
  {:pre [(s/valid? ::spec/non-empty-string symbol)
         (s/valid? ::spec/non-empty-string context)]
   :post [(s/valid? ::spec/completions %)]}
  (if-let [obj-expr (expr-for-parent-obj {:ns ns :symbol symbol :context context})]
    (let [{:keys [properties error]} (js-properties-of-object obj-expr msg)]
      (for [{:keys [name type]} properties
            :let [candidate (str (if (= "var" type) ".-" ".") name)]
            :when (starts-with? candidate symbol)]
        {:type type :candidate candidate}))))

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
  [{:keys [session context] :as msg}]
  (let [prev-state (or (get @session #'*object-completion-state*) (empty-state))
        same-context? (= context ":same")
        context (if same-context? (:context prev-state) context)]
    (when-let [completions (handle-completion-msg msg context)]
      (swap! session #(merge % {#'*object-completion-state*
                                (if same-context?
                                  prev-state
                                  (assoc prev-state :context context))}))
      (completion-answer msg completions))))

(defn- cljs-dynamic-completion-handler
  [next-handler {:keys [id session transport op symbol] :as msg}]

  (when (and (= op "complete")
           ;; cljs repl?
             (not= "" symbol)
             (some #(get @session (resolve %)) '(piggieback.core/*cljs-compiler-env*
                                                 cider.piggieback/*cljs-compiler-env*)))

    (when-let [answer (handle-completion-msg-stateful msg)]
      (transport/send transport answer)))

  ;; call next-handler in any case - we want the default completions as well
  (next-handler msg))

(defn wrap-cljs-dynamic-completions [handler]
  (fn [msg] (cljs-dynamic-completion-handler handler msg)))

(set-descriptor! #'wrap-cljs-dynamic-completions
                 {:requires #{"clone"}
                  :expects #{"complete" "eval"}
                  :handles {}})
