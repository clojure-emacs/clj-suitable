(ns cljs-object-completion.middleware
  (:require [cljs-object-completion.ast :refer [print-tree tree-zipper]]
            [clojure.pprint :refer [cl-format]]
            [clojure.string :refer [starts-with?]]
            [clojure.zip :as zip]
            [nrepl.middleware :refer [set-descriptor!]]
            [nrepl.transport :as transport])
  (:import nrepl.transport.Transport))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-



;; FIXME remove

(defn find-prefix [form]
  (loop [node (tree-zipper form)]
    (if (= '__prefix__ (zip/node node))
      node
      (when-not (zip/end? node)
        (println (zip/node node))
        (recur (zip/next node))))))

(defn client-instructions
  [{:keys [ns context symbol] :as completion-input} {:keys [special-namespaces] :as state}]
  (let [form (read-string context)
        prefix (find-prefix form)
        left-sibling (zip/left prefix)
        first? (nil? left-sibling)
        first-sibling (and (not first?) (some-> prefix zip/leftmost zip/node))
        dot-fn? (starts-with? symbol ".")]

    (and first? (-> prefix zip/up zip/leftmost zip/node str))

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
      (and first? (-> prefix zip/up zip/leftmost zip/node str))
      (-> prefix zip/up zip/lefts str))))


(comment

  (def context "(.foo __prefix__)")
  (clojure.walk/prewalk-demo '(hello w (o r l) c))
  (require '[clojure.zip :as zip])

  (def node (zip/seq-zip '(foo [bar __prefix__])))
  (-> node zip/next zip/next zip/down zip/node)

  (-> (find-prefix '(foo [bar __prefix__])) zip/path)
  (-> (find-prefix '(foo [bar __prefix__])) (zip/leftmost) zip/node name (starts-with? "."))
  (-> (find-prefix '(foo [bar __prefix__])) (zip/rights))


  (print-tree (-> node zip/next zip/next zip/down zip/node))
  (print-tree node)

  )


(defonce state (atom nil))

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


(defn cljs-dynamic-completion-handler
  [next-handler {:keys [id session transport op ns symbol context extra-metadata] :as msg}]


  (when (and (= op "complete")
             (some #(get-in @session [(resolve %)]) '(piggieback.core/*cljs-compiler-env*
                                                      cider.piggieback/*cljs-compiler-env*))
             )
    (cl-format true "~A: ~A~%" op (select-keys msg [:ns :symbol :context :extra-metadata]))

    (let [answer (merge (when id {:id id})
                        (when session {:session (if (instance? clojure.lang.AReference session)
                                                  (-> session meta :id)
                                                  session)}))]

      ;; (println (cljs-eval session "(properties-by-prototype js/console)" ns))
      (reset! state {:handler next-handler
                     :session session
                     :ns ns})
      (transport/send transport (assoc answer :completions [{:candidate "cljs.hello", :type "var"}]))))

  ;; call next-handler for the default completions
  (next-handler msg))


(defn wrap-cljs-dynamic-completions [handler]
  (fn [msg] (cljs-dynamic-completion-handler handler msg)))

(set-descriptor! #'wrap-cljs-dynamic-completions
                 {:requires #{"clone"}
                  :expects #{"complete" "eval"}
                  :handles {}})
