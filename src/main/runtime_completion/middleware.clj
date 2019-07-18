(ns runtime-completion.middleware
  (:require [clojure.edn :as edn]
            [clojure.pprint :refer [cl-format]]
            [clojure.set :refer [rename-keys]]
            [clojure.string :refer [starts-with?]]
            [clojure.zip :as zip]
            [nrepl.middleware :refer [set-descriptor!]]
            [nrepl.transport :as transport]
            [runtime-completion.ast :refer [tree-zipper]])
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
  (require 'cljs-object-completion.core)
  (cljs-object-completion.core/property-names-and-types ~A))")

(defn- js-properties-of-object [obj-expr {:keys [session ns]}]
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
  [{:keys [id session transport op ns symbol context extra-metadata] :as msg} {prev-context :context :as prev-state}]

  (cl-format true "~A: ~A~%" op (select-keys msg [:ns :symbol :context :extra-metadata]))


  (let [same-context? (= context ":same")
        context (if same-context? prev-context context)]
    (assert context "no context from message or prev-state!")

    (when-let [obj-expr (expr-for-parent-obj {:ns ns :symbol symbol :context context})]
      (let [{:keys [properties error]} (js-properties-of-object obj-expr msg)
            completions (if properties
                          (do
                            (->> properties
                                 (cl-format true "~A has the following properties:~%~{  ~S~^~%~}~%" obj-expr))
                            (map #(-> % (rename-keys {:name :candidate}) (dissoc :hierarchy)) properties))
                          [])]

        {:state (if same-context?
                  prev-state
                  (assoc prev-state :context context))
         :completions completions}))))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(def ^:private ^:dynamic *object-completion-state* nil)

(defn- empty-state [] {:context ""})

(defn- cljs-dynamic-completion-handler
  [next-handler {:keys [id session transport op] :as msg}]

  (when (and (= op "complete")
           ;; cljs repl?
           (some #(get @session (resolve %)) '(piggieback.core/*cljs-compiler-env*
                                               cider.piggieback/*cljs-compiler-env*)))

    (let [prev-state (or (get @session #'*object-completion-state*) (empty-state))
          {:keys [state completions]} (handle-completion-msg msg prev-state)
          completions [{:candidate "cljs.hello", :type "var"}]
          answer (merge (when id {:id id})
                        (when session {:session (if (instance? clojure.lang.AReference session)
                                                  (-> session meta :id)
                                                  session)})
                        {:completions completions})]

      (swap! session #(merge % {#'*object-completion-state* state}))
      (transport/send transport answer)))

  ;; call next-handler in any case - we want the default completions as well
  (next-handler msg))

(defn wrap-cljs-dynamic-completions [handler]
  (fn [msg] (cljs-dynamic-completion-handler handler msg)))

(set-descriptor! #'wrap-cljs-dynamic-completions
                 {:requires #{"clone"}
                  :expects #{"complete" "eval"}
                  :handles {}})
