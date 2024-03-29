(ns suitable.compliment.sources.cljs
  "Standalone auto-complete library based on cljs analyzer state"
  (:refer-clojure :exclude [meta])
  (:require [clojure.set :as set]
            [compliment.sources :refer [defsource]]
            [compliment.utils :as utils :refer [*extra-metadata*]]
            [suitable.compliment.sources.cljs.analysis :as ana])
  (:import java.io.StringWriter))

(defn unquote-first
  "Handles some weird double-quoting in the analyzer"
  [form]
  (if (= (first form) 'quote)
    (first (rest form))
    form))

(defn normalize-arglists
  "Take metadata arglists and normalize them.
  Normalization being mainly removing the quote of the first element and
  wrapping in a list."
  [arglists]
  (some->> arglists
           (unquote-first)
           (map pr-str)
           (apply list)))

(defn- candidate-extra
  [extra-metadata candidate-meta]
  (when (and (seq extra-metadata) candidate-meta)
    (let [extra (select-keys candidate-meta extra-metadata)]
      (cond-> extra
        (:arglists extra) (update :arglists normalize-arglists)))))

(defn- candidate-data
  "Returns a map of candidate data for the given arguments."
  ([candidate ns type]
   (candidate-data candidate ns type nil nil))
  ([candidate ns type meta extra-metadata]
   (merge {:candidate (str candidate)
           :type type}
          (when ns {:ns (str ns)})
          (when (seq extra-metadata)
            (candidate-extra extra-metadata meta)))))

(defn- var->type
  "Returns the candidate type corresponding to the given metadata map."
  [var]
  (condp #(get %2 %1) var
    :protocol :protocol-function
    :fn-var :function
    :record :record
    :protocols :type
    :protocol-symbol :protocol
    :var))

(def ^:private special-forms
  '#{& . case* catch def defrecord* deftype* do finally fn* if js* let*
     letfn* loop* new ns quote recur set! throw try})

(def ^:private special-form-candidates
  "Candidate data for all special forms."
  (for [form special-forms]
    (candidate-data form 'cljs.core :special-form)))

(defn- all-ns-candidates
  "Returns candidate data for all namespaces in the environment."
  [env extra-metadata]
  ;; recent CLJS versions include data about macro namespaces in the
  ;; compiler env, but we should not include them in completions or pass
  ;; them to format-ns unless they're actually required (which is handled
  ;; by macro-ns-candidates below)
  (for [[ns meta] (ana/all-ns env)]
    (candidate-data ns nil :namespace meta extra-metadata)))

(defn- ns-candidates
  "Returns candidate data for all referred namespaces (and their aliases) in context-ns."
  [env context-ns extra-metadata]
  (for [[alias ns] (ana/ns-aliases env context-ns)]
    (candidate-data alias
                    (when-not (= alias ns) ns)
                    :namespace
                    (ana/ns-meta ns)
                    extra-metadata)))

(defn- macro-ns-candidates
  "Returns candidate data for all referred macro namespaces (and their aliases) in
  context-ns."
  [env context-ns extra-metadata]
  (for [[alias ns] (ana/macro-ns-aliases env context-ns)]
    (candidate-data alias
                    (when-not (= alias ns)
                      ns)
                    :namespace
                    ;; given macros are Clojure code we can simply find-ns
                    ;; the meta should probably be in the compiler env instead
                    (ana/ns-meta ns)
                    extra-metadata)))

(defn- referred-var-candidates
  "Returns candidate data for all referred vars in context-ns."
  [env context-ns extra-metadata]
  (for [[refer qualified-sym] (ana/referred-vars env context-ns)
        :let [ns (namespace qualified-sym)
              meta (ana/qualified-symbol-meta env qualified-sym)
              type (var->type meta)]]
    (candidate-data refer ns type meta extra-metadata)))

(defn- referred-macro-candidates
  "Returns candidate data for all referred macros in context-ns."
  [env context-ns extra-metadata]
  (for [[refer qualified-sym] (ana/referred-macros env context-ns)
        :let [ns (namespace qualified-sym)
              meta (ana/macro-meta env qualified-sym)]]
    (candidate-data refer ns :macro meta extra-metadata)))

(defn- var-candidates
  [vars extra-metadata]
  (for [[name meta] vars
        :let [qualified-name (:name meta)
              ns (some-> qualified-name namespace)
              type (var->type meta)]]
    (candidate-data name ns type meta extra-metadata)))

(defn- ns-var-candidates
  "Returns candidate data for all vars defined in ns."
  [env ns extra-metadata]
  (var-candidates (ana/ns-vars env ns) extra-metadata))

(defn- core-var-candidates
  "Returns candidate data for all cljs.core vars visible in context-ns."
  [env ns extra-metadata]
  (var-candidates (ana/core-vars env ns) extra-metadata))

(defn- macro-candidates
  [macros extra-metadata]
  (for [[name var] macros
        :let [meta (ana/var-meta var)
              ns (:ns meta)]]
    (candidate-data name ns :macro meta extra-metadata)))

(defn- core-macro-candidates
  "Returns candidate data for all cljs.core macros visible in ns."
  [env ns extra-metadata]
  (macro-candidates (ana/core-macros env ns) extra-metadata))

(defn- import-candidates
  "Returns candidate data for all imports in context-ns."
  [env context-ns]
  (flatten
   (for [[import qualified-name] (ana/imports env context-ns)]
     [(candidate-data import nil :class)
      (candidate-data qualified-name nil :class)])))

(defn- keyword-candidates
  "Returns candidate data for all keyword constants in the environment."
  [env]
  (map #(candidate-data % nil :keyword) (ana/keyword-constants env)))

(defn- namespaced-keyword-candidates
  "Returns all namespaced keywords defined in context-ns."
  [env context-ns]
  (when context-ns
    (for [kw (ana/keyword-constants env)
          :when (= context-ns (ana/as-sym (namespace kw)))]
      (candidate-data (str "::" (name kw)) context-ns :keyword))))

(defn- referred-namespaced-keyword-candidates
  "Returns all namespaced keywords referred in context-ns."
  [env context-ns]
  (when context-ns
    (let [aliases (->> (ana/ns-aliases env context-ns)
                       (filter (fn [[k v]] (not= k v)))
                       (into {})
                       (set/map-invert))]
      (for [kw (ana/keyword-constants env)
            :let [ns (ana/as-sym (namespace kw))
                  alias (get aliases ns)]
            :when alias]
        (candidate-data (str "::" alias "/" (name kw)) ns :keyword)))))

(defn- unscoped-candidates
  "Returns all non-namespace-qualified potential candidates in context-ns."
  [env context-ns extra-metadata]
  (concat special-form-candidates
          (all-ns-candidates env extra-metadata)
          (ns-candidates env context-ns extra-metadata)
          (macro-ns-candidates env context-ns extra-metadata)
          (referred-var-candidates env context-ns extra-metadata)
          (referred-macro-candidates env context-ns extra-metadata)
          (ns-var-candidates env context-ns extra-metadata)
          (core-var-candidates env context-ns extra-metadata)
          (core-macro-candidates env context-ns extra-metadata)
          (import-candidates env context-ns)
          (keyword-candidates env)
          (namespaced-keyword-candidates env context-ns)
          (referred-namespaced-keyword-candidates env context-ns)))

(defn- prefix-candidate
  [prefix candidate-data]
  (let [candidate (:candidate candidate-data)
        prefixed-candidate (str prefix "/" candidate)]
    (assoc candidate-data :candidate prefixed-candidate)))

(defn- prefix-candidates
  [prefix candidates]
  (map #(prefix-candidate prefix %) candidates))

(defn- ->ns
  [env symbol-ns context-ns]
  (if (ana/find-ns env symbol-ns)
    symbol-ns
    (ana/ns-alias env symbol-ns context-ns)))

(defn- ->macro-ns
  [env symbol-ns context-ns]
  (if (= symbol-ns 'cljs.core)
    symbol-ns
    (ana/macro-ns-alias env symbol-ns context-ns)))

(defn- ns-public-var-candidates
  "Returns candidate data for all public vars defined in ns."
  [env ns extra-metadata]
  (var-candidates (ana/public-vars env ns) extra-metadata))

(defn- ns-macro-candidates
  "Returns candidate data for all macros defined in ns."
  [env ns extra-metadata]
  (-> env
      (ana/public-macros ns)
      (macro-candidates extra-metadata)))

(defn- scoped-candidates
  "Returns all candidates for the namespace of sym. Sym must be
  namespace-qualified. Macro candidates are included if the namespace has its
  macros required in context-ns."
  [env sym context-ns extra-metadata]
  (let [sym-ns (-> sym ana/as-sym ana/namespace-sym)
        computed-ns (->ns env sym-ns context-ns)
        macro-ns (->macro-ns env sym-ns context-ns)
        sym-ns-as-string (str sym-ns)]
    (mapcat #(prefix-candidates sym-ns-as-string %)
            [(ns-public-var-candidates env computed-ns extra-metadata)
             (when macro-ns
               (ns-macro-candidates env macro-ns extra-metadata))])))

(defn- potential-candidates
  "Returns all candidates for sym. If sym is namespace-qualified, the candidates
  for that namespace will be returned (including macros if the namespace has its
  macros required in context-ns). Otherwise, all non-namespace-qualified
  candidates for context-ns will be returned."
  [env context-ns ^String sym extra-metadata]
  (if (or (= (.indexOf sym "/") -1) (.startsWith sym ":"))
    (unscoped-candidates env context-ns extra-metadata)
    (scoped-candidates env sym context-ns extra-metadata)))

(defn- distinct-candidates
  "Filters candidates to have only one entry for each value of :candidate. If
  multiple such entries do exist, the first occurrence is used."
  [candidates]
  (map first (vals (group-by :candidate candidates))))

(defn- candidate-match?
  [candidate prefix]
  (.startsWith ^String (:candidate candidate) prefix))

(defn plain-symbol?
  "Tests if prefix is a symbol with no / (qualified), : (keyword) and
  . (segmented namespace)."
  [s]
  (re-matches #"[^\/\:\.]+" s))

(defn nscl-symbol?
  "Tests if prefix looks like a namespace or classname."
  [x]
  (re-matches #"[^\/\:\.][^\/\:]+" x))

(def ^:dynamic *compiler-env* nil)

(defn- candidates* [prefix context-ns]
  (->> (potential-candidates *compiler-env* context-ns prefix *extra-metadata*)
       (distinct-candidates)
       (filter #(candidate-match? % prefix))))

(defn candidates
  "Returns a sequence of candidate data for completions matching the given
  prefix string and options.

  It requires the compliment.sources.cljs/*compiler-env* var to be dynamically
  bound to the ClojureScript compiler env."
  [prefix ns _context]
  (let [context-ns (try
                     (ns-name ns)
                     (catch Exception _
                       nil))]
    (candidates* prefix context-ns)))

(defn generate-docstring
  "Generates a docstring from a given var metadata.

  Copied from `cljs.repl` with some minor modifications."
  [m]
  (binding [*out* (StringWriter.)]
    (println "-------------------------")
    (println (or (:spec m) (str (when-let [ns (:ns m)] (str ns "/")) (:name m))))
    (when (:protocol m)
      (println "Protocol"))
    (cond
      (:forms m) (doseq [f (:forms m)]
                   (println "  " f))
      (:arglists m) (let [arglists (:arglists m)]
                      (if (or (:macro m)
                              (:repl-special-function m))
                        (prn arglists)
                        (prn
                         (if (= 'quote (first arglists))
                           (second arglists)
                           arglists)))))
    (if (:special-form m)
      (do
        (println "Special Form")
        (println " " (:doc m))
        (if (contains? m :url)
          (when (:url m)
            (println (str "\n  Please see http://clojure.org/" (:url m))))
          (println (str "\n  Please see http://clojure.org/special_forms#"
                        (:name m)))))
      (do
        (when (:macro m)
          (println "Macro"))
        (when (:spec m)
          (println "Spec"))
        (when (:repl-special-function m)
          (println "REPL Special Function"))
        (println " " (:doc m))
        (when (:protocol m)
          (doseq [[name {:keys [doc arglists]}] (:methods m)]
            (println)
            (println " " name)
            (println " " arglists)
            (when doc
              (println " " doc))))
        ;; Specs are handled separately in cider-nrepl
        ;;
        ;; (when n
        ;;   (when-let [fnspec (spec/get-spec (symbol (str (ns-name n)) (name nm)))]
        ;;     (print "Spec")
        ;;     (doseq [role [:args :ret :fn]]
        ;;       (when-let [spec (get fnspec role)]
        ;;         (print (str "\n " (name role) ":") (spec/describe spec))))))
        ))
    (str *out*)))

(defn doc
  [s ns]
  (let [ns-sym (some-> ns ns-name)]
    (some->
     (cond
       ;; This is needed because compliment defaults to 'user in the absence of
       ;; a ns. Additionally, in order to preserve the Clojure's behavior we
       ;; try against cljs.core if nothing is found for cljs.user
       (or (= ns-sym 'user) (= ns-sym 'cljs.user))
       (or (ana/qualified-symbol-meta *compiler-env* (symbol "cljs.user" s))
           (ana/macro-meta *compiler-env* (symbol "cljs.user" s))
           (ana/qualified-symbol-meta *compiler-env* (symbol "cljs.core" s))
           (ana/macro-meta *compiler-env* (symbol "cljs.core" s)))

       (plain-symbol? s) (let [ns-sym (cond
                                        (nil? ns) 'cljs.core
                                        (= ns 'user) 'cljs.user
                                        :else (ns-name ns))
                               qualified-sym (symbol (str ns-sym) s)]
                           (or (ana/qualified-symbol-meta *compiler-env* qualified-sym)
                               (ana/macro-meta *compiler-env* qualified-sym)))
       (nscl-symbol? s) (-> s symbol ana/ns-meta)
       :else nil)
     (not-empty)
     (generate-docstring))))

(defsource ::cljs-source
  :candidates #'candidates
  :doc #'doc)
