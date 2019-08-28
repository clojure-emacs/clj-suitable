(ns suitable.js-completions
  (:refer-clojure :exclude [replace])
  (:require [clojure.pprint :refer [cl-format]]
            [clojure.string :refer [replace split starts-with?]]
            [clojure.zip :as zip]
            [suitable.ast :refer [tree-zipper]]))

(def debug? false)

(defn js-properties-of-object
  "Returns the properties of the object we get by evaluating `obj-expr` filtered
  by all those that start with `prefix`."
  ([cljs-eval-fn ns obj-expr]
   (js-properties-of-object cljs-eval-fn ns obj-expr nil))
  ([cljs-eval-fn ns obj-expr prefix]
   (try
     ;; :Not using a single expressiont / eval call here like
     ;; (do (require ...) (runtime ...))
     ;; to avoid
     ;; Compile Warning:  Use of undeclared Var
     ;;   suitable.js-introspection/property-names-and-types
     (let [template "(suitable.js-introspection/property-names-and-types ~A ~S)"
           code (cl-format nil template obj-expr prefix)]
       (cljs-eval-fn ns "(require 'suitable.js-introspection)")
       (cljs-eval-fn ns code))
     (catch #?(:clj Exception :cljs js/Error) e {:error e}))))

(defn find-prefix [form]
  "Tree search for the symbol '__prefix. Returns a zipper."
  (loop [node (tree-zipper form)]
    (if (= '__prefix__ (zip/node node))
      node
      (when-not (zip/end? node)
        (recur (zip/next node))))))

(defn thread-form?
  "True if form looks like the name of a thread macro."
  [form]
  (->> form
       str
       (re-find #"->")
       nil?
       not))

(defn doto-form? [form]
  (= form 'doto))

(defn expr-for-parent-obj
  "Given the context and symbol of a completion request, will try to find an
  expression that evaluates to the object being accessed."
  [symbol context]
  (when-let [form (if (string? context)
                    (try
                      (with-in-str context (read *in* nil nil))
                      (catch Exception _e
                        (when debug?
                          (binding [*out* *err*]
                            (cl-format true "[suitable] Error reading context: ~s" context)))))
                    context)]
    (let [prefix (find-prefix form)
          left-sibling (zip/left prefix)
          first? (nil? left-sibling)
          first-sibling (and (not first?) (some-> prefix zip/leftmost zip/node))
          first-sibling-in-parent (some-> prefix zip/up zip/leftmost zip/node)
          threaded? (if first? (thread-form? first-sibling-in-parent) (thread-form? first-sibling) )
          doto? (if first? (doto-form? first-sibling-in-parent) (doto-form? first-sibling))
          dot-fn? (starts-with? symbol ".")]

      (letfn [(with-type [type maybe-expr]
                (when maybe-expr
                  {:type type
                   :obj-expr maybe-expr}))]
       (cond
         (nil? prefix) nil

         ;; is it a threading macro?
         threaded?
         (with-type :-> (if first?
                          ;; parent is the thread
                          (-> prefix zip/up zip/lefts str)
                          ;; thread on same level
                          (-> prefix zip/lefts str)))

         doto?
         (with-type :doto (if first?
                            ;; parent is the thread
                            (-> prefix zip/up zip/leftmost zip/right zip/node str)
                            ;; thread on same level
                            (-> prefix zip/leftmost zip/right zip/node str)))

         ;; a .. form: if __prefix__ is a prop deeper than one level we need the ..
         ;; expr up to that point. If just the object that is accessed is left of
         ;; prefix, we can take that verbatim.
         ;; (.. js/console log) => js/console
         ;; (.. js/console -memory -jsHeapSizeLimit) => (.. js/console -memory)
         (and first-sibling (#{"." ".."} (str first-sibling)) left-sibling)
         (with-type :.. (let [lefts (-> prefix zip/lefts)]
                          (if (<= (count lefts) 2)
                            (str (last lefts))
                            (str lefts))))

         ;; (.. js/window -console (log "foo")) => (.. js/window -console)
         (and first? (-> prefix zip/up zip/leftmost zip/node str (= "..")))
         (with-type :.. (let [lefts (-> prefix zip/up zip/lefts)]
                          (if (<= (count lefts) 2)
                            (str (last lefts))
                            (str lefts))))

         ;; simple (.foo bar)
         (and first? dot-fn?)
         (with-type :. (some-> prefix zip/right zip/node str)))))))

(def global-expr-re #"^js/((?:[^\.]+\.)*)([^\.]*)$")
(def dot-dash-prefix-re #"^\.-?")
(def dash-prefix-re #"^-")
(def dot-prefix-re #"\.")

(defn analyze-symbol-and-context
  "Build a configuration that we can use to fetch the properties from an object
  that is the result of some `obj-expr` when evaled and that is used to convert
  those properties into candidates for completion."
  [symbol context]
  (if (starts-with? symbol "js/")

    ;; symbol is a global like js/console or global/property like js/console.log
    (let [[_ dotted-obj-expr prefix] (re-matches global-expr-re symbol)
          obj-expr-parts (keep not-empty (split dotted-obj-expr dot-prefix-re))
          ;; builds an expr like
          ;; "(this-as this (.. this -window))" for symbol = "js/window.console"
          ;; or "(this-as this this)" for symbol = "js/window"
          obj-expr (cl-format nil "(this-as this ~[this~:;(.. this ~{-~A~^ ~})~])"
                              (count obj-expr-parts) obj-expr-parts)]
      obj-expr-parts
      {:prefix prefix
       :prepend-to-candidate (str "js/" dotted-obj-expr)
       :vars-have-dashes? false
       :obj-expr obj-expr
       :type :global})

    ;; symbol is just a property name embedded in some expr
    (let [{:keys [type] :as expr-and-type} (expr-for-parent-obj symbol context)]
      (assoc expr-and-type
             :prepend-to-candidate (if (starts-with? symbol ".") "." "")
             :prefix (case type
                       :.. (replace symbol dash-prefix-re "")
                       (replace symbol dot-dash-prefix-re ""))
             :vars-have-dashes? true))))

(defn cljs-completions
  "Given some context (the toplevel form that has changed) and a symbol string
  that represents the last typed input, we try to find out if the context/symbol
  are object access (property access or method call). If so, we try to extract a
  form that we can evaluate to get the object that is accessed. If we get the
  object, we enumerate it's properties and methods and generate a list of
  matching completions for those.

  The arguments to this function are

  1. `cljs-eval-fn`: a function that given a namespace (as string) and cljs
  code (string) will evaluate it and return the value as a clojure object. See
  `suitable.middleware/cljs-dynamic-completion-handler` for how to
  setup an eval function with nREPL.

  The last two arguments mirror the interface of `compliment.core/completions`
  from https://github.com/alexander-yakushev/compliment:

  2. A symbol (as string) to complete, basically the prefix.

  3. An options map that should have at least the keys :ns and :context. :ns is
  the name (string) of the namespace the completion request is coming
  from. :context is a s-expression (as string) of the toplevel form the symbol
  comes from, the symbol being replaced by \"__prefix__\". See the compliment
  library for details on this format.
  Currently unsupported options that compliment implements
  are :extra-metadata :sort-order and :plain-candidates."
  [cljs-eval-fn symbol {:keys [ns context] :as options-map}]
  (let [{:keys [prefix prepend-to-candidate vars-have-dashes? obj-expr type]}
        (analyze-symbol-and-context symbol context)
        global? (#{:global} type)]
    (when-let [{error :error properties :value} (and obj-expr (js-properties-of-object cljs-eval-fn ns obj-expr prefix))]
      (if error
        (when debug?
          (binding [*out* *err*]
            (println "[suitable] error in suitable cljs-completions:" error)))
        (for [{:keys [name type]} properties
              :let [maybe-dash (if (and vars-have-dashes? (= "var" type)) "-" "")
                    candidate (str prepend-to-candidate maybe-dash name)]
              :when (starts-with? candidate symbol)]
          {:type type :candidate candidate :ns (if global? "js" obj-expr)})))))
