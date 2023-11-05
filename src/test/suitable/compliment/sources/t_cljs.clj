(ns suitable.compliment.sources.t-cljs
  (:require
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.string :as string]
   [clojure.test :as test :refer [deftest is testing use-fixtures]]
   [compliment.utils :refer [*extra-metadata*]]
   [suitable.cljs.env :as cljs-env]
   [suitable.compliment.sources.cljs :as cljs-sources]))

(use-fixtures :once
  (fn [f]
    (binding [cljs-sources/*compiler-env* (cljs-env/create-test-env)]
      (f))))

(defn completions
  [prefix & [ns]]
  (cljs-sources/candidates prefix (some-> ns find-ns) nil))

(defn set=
  [coll1 coll2 & colls]
  (apply = (set coll1) (set coll2) (map set colls)))

(deftest sanity
  (testing "Nothing returned for non-existent prefix"
    (is (= '()
           (completions "abcdefghijk")
           (completions "abcdefghijk" 'suitable.test-ns))))

  (testing "cljs.core candidates returned for new namespaces"
    (is (= (completions "")
           (completions "" 'abcdefghijk))))

  (let [all-candidates (completions "" 'suitable.test-ns)]
    (testing "All candidates have a string for :candidate"
      (is (every? (comp string? :candidate) all-candidates)))

    (testing "All candidates that should have an :ns, do"
      (let [filter-fn #(not (#{:import :keyword :namespace :class} (:type %)))
            filtered-candidates (filter filter-fn all-candidates)]
        (is (every? :ns filtered-candidates))))

    (testing "All candidates have a valid :type"
      (let [valid-types #{:function
                          :class
                          :keyword
                          :macro
                          :namespace
                          :protocol
                          :protocol-function
                          :record
                          :special-form
                          :type
                          :var}]
        (is (every? (comp valid-types :type) all-candidates))))))

(deftest special-form-completions
  (testing "Special form"
    (is (= '({:candidate "throw" :ns "cljs.core" :type :special-form})
           (completions "thr")))

    (is (set= '({:candidate "def" :ns "cljs.core" :type :special-form}
                {:candidate "do" :ns "cljs.core" :type :special-form}
                {:candidate "defrecord*" :ns "cljs.core" :type :special-form}
                {:candidate "deftype*" :ns "cljs.core" :type :special-form})
              (->> (completions "d")
                   (filter #(= :special-form (:type %))))))))

(deftest string-requires
  ;; See: https://github.com/clojure-emacs/clj-suitable/issues/22
  (testing "Libspecs expressed under the newer string notation"
    (let [ns-sym 'suitable.test-ns-dep
          ns-filename (str (-> ns-sym
                               str
                               (string/replace "." "/")
                               (string/replace "-" "_")
                               (str ".cljs")))]
      (assert (-> ns-filename io/resource slurp (string/includes? "[\"clojure.set\" :as set]"))
              "The exercised ns has in fact a string require")
      (is (seq (#'cljs-sources/candidates* "set" ns-sym))
          "Can be successfully run without throwing errors"))))

(deftest namespace-completions
  (testing "Namespace"
    (is (set= '({:candidate "suitable.test-ns" :type :namespace}
                {:candidate "suitable.test-ns-dep" :type :namespace})
              (completions "suitable.t"))))

  (testing "Macro Namespace"
    (is (set= '({:candidate "suitable.test-ns" :type :namespace}
                {:candidate "suitable.test-ns-dep" :type :namespace}
                {:candidate "suitable.test-macros" :type :namespace})
              (completions "suitable.t" 'suitable.test-ns))))

  (testing "Namespace alias"
    (is (= '()
           (completions "test-d")
           (completions "test-d" 'cljs.user)))
    (is (= '({:candidate "test-dep" :ns "suitable.test-ns-dep" :type :namespace})
           (completions "test-d" 'suitable.test-ns)))))

(deftest macro-namespace-completions
  (testing "Macro namespace"
    (is (= '()
           (completions "suitable.test-macros")
           (completions "suitable.test-macros" 'cljs.user)))
    (is (= '({:candidate "suitable.test-macros" :type :namespace})
           (completions "suitable.test-m" 'suitable.test-ns))))

  (testing "Macro namespace alias"
    (is (= '()
           (completions "test-m")))
    (is (= '({:candidate "test-macros" :ns "suitable.test-macros" :type :namespace})
           (completions "test-m" 'suitable.test-ns)))))

(deftest fn-completions
  (testing "cljs.core fn"
    (is (set= '({:candidate "unchecked-add" :ns "cljs.core" :type :function}
                {:candidate "unchecked-add-int" :ns "cljs.core" :type :function})
              (completions "unchecked-a")
              (completions "unchecked-a" 'cljs.user)))
    (is (set= '({:candidate "cljs.core/unchecked-add" :ns "cljs.core" :type :function}
                {:candidate "cljs.core/unchecked-add-int" :ns "cljs.core" :type :function})
              (completions "cljs.core/unchecked-a")
              (completions "cljs.core/unchecked-a" 'cljs.user))))

  (testing "Excluded cljs.core fn"
    (is (= '()
           (completions "unchecked-b" 'suitable.test-ns)))
    (is (= '({:candidate "cljs.core/unchecked-byte" :ns "cljs.core" :type :function})
           (completions "cljs.core/unchecked-b" 'suitable.test-ns))))

  (testing "Namespace-qualified fn"
    (is (= '({:candidate "suitable.test-ns/issue-28" :ns "suitable.test-ns" :type :function})
           (completions "suitable.test-ns/iss")
           (completions "suitable.test-ns/iss" 'cljs.user)
           (completions "suitable.test-ns/iss" 'suitable.test-ns))))

  (testing "Referred fn"
    (is (= '()
           (completions "bla")
           (completions "bla" 'suitable.test-ns)))
    (is (= '({:candidate "foo-in-dep" :ns "suitable.test-ns-dep" :type :function})
           (completions "foo" 'suitable.test-ns))))

  (testing "Local fn"
    (is (= '({:candidate "test-public-fn" :ns "suitable.test-ns" :type :function})
           (completions "test-pu" 'suitable.test-ns))))

  (testing "Private fn"
    (is (= '()
           (completions "test-pri")
           (completions "test-pri" 'cljs.user)))
    (is (= '({:candidate "test-private-fn" :ns "suitable.test-ns" :type :function})
           (completions "test-pri" 'suitable.test-ns))))

  (testing "Fn shadowing macro with same name"
    (is (= '({:candidate "identical?" :ns "cljs.core" :type :function})
           (completions "identical?")))))

(deftest macro-completions
  (testing "cljs.core macro"
    (is (set= '({:candidate "cond->" :ns "cljs.core" :type :macro}
                {:candidate "cond->>" :ns "cljs.core" :type :macro})
              (completions "cond-")
              (completions "cond-" 'suitable.test-ns)))
    (is (set= '({:candidate "cljs.core/cond->" :ns "cljs.core" :type :macro}
                {:candidate "cljs.core/cond->>" :ns "cljs.core" :type :macro})
              (completions "cljs.core/cond-")
              (completions "cljs.core/cond-" 'suitable.test-ns))))

  (testing "Excluded cljs.core macro"
    (is (= '()
           (completions "whil" 'suitable.test-ns)))
    (is (= '({:candidate "cljs.core/while" :ns "cljs.core" :type :macro})
           (completions "cljs.core/whil" 'suitable.test-ns))))

  (testing "Namespace-qualified macro"
    (is (= '()
           (completions "suitable.test-macros/non-existent")
           (completions "suitable.test-macros/non-existent" 'cljs.user)))
    (is (set= '({:candidate "suitable.test-macros/my-add" :ns "suitable.test-macros" :type :macro}
                {:candidate "suitable.test-macros/my-sub" :ns "suitable.test-macros" :type :macro})
              (completions "suitable.test-macros/my-" 'suitable.test-ns))))

  (testing "Referred macro"
    (is (= '()
           (completions "non-existent")
           (completions "non-existent" 'suitable.test-ns)))
    ;; only my-add cause it is the only one referred
    (is (= '({:candidate "my-add" :ns "suitable.test-macros" :type :macro})
           (completions "my-" 'suitable.test-ns)))))

(deftest import-completions
  (testing "Import"
    (is (= '()
           (completions "IdGen")
           (completions "IdGen" 'cljs.user)))
    (is (= '({:candidate "IdGenerator" :type :class})
           (completions "IdGen" 'suitable.test-ns))))

  (testing "Namespace-qualified import"
    ;; TODO Investigate if this is a bug in the implementation.
    ;;
    ;; This test used to pass as would not complete unless you have :import in
    ;; the ns. It might a change in behavior in the way the compiler fills its
    ;; env with the newer versions.
    ;;
    ;; (is (= '()
    ;;        (completions "goog.ui.IdGen")
    ;;        (completions "goog.ui.IdGen" 'cljs.user)))

    (is (= '({:candidate "goog.ui.IdGenerator" :type :namespace})
           (completions "goog.ui.IdGen" 'suitable.test-ns)))))

(deftest keyword-completions
  (testing "Keyword"
    (is (empty? (set/difference (set '({:candidate ":refer-macros" :type :keyword}
                                       {:candidate ":require-macros" :type :keyword}))
                                (set (completions ":re")))) "the completion should include ClojureScript-specific keywords."))

  (testing "Local keyword"
    (is (= '({:candidate ":one" :type :keyword})
           (completions ":on" 'suitable.test-ns))))

  (testing "Keyword from another namespace"
    (is (= '({:candidate ":four" :type :keyword})
           (completions ":fo" 'suitable.test-ns))))

  (testing "Local namespaced keyword"
    (is (= '({:candidate "::some-namespaced-keyword" :ns "suitable.test-ns" :type :keyword})
           (completions "::so" 'suitable.test-ns)))

    (is (= '()
           (completions "::i" 'suitable.test-ns))))

  (testing "Referred namespaced keyword"
    (is (= '()
           (completions "::test-dep/f" 'suitable.test-ns)))

    (is (= '({:candidate "::test-dep/dep-namespaced-keyword" :ns "suitable.test-ns-dep" :type :keyword})
           (completions "::test-dep/d" 'suitable.test-ns))))

  (testing "Referred namespaced keyword :as-alias"
    (is (= '({:candidate "::aliased/kw" :type :keyword :ns "suitable.test-ns-alias"})
           (completions "::aliased/k" 'suitable.test-ns)))))

(deftest protocol-completions
  (testing "Protocol"
    (is (set= '({:candidate "IIndexed" :ns "cljs.core" :type :protocol}
                {:candidate "IIterable" :ns "cljs.core" :type :protocol})
              (completions "II"))))

  (testing "Protocol fn"
    (is (set= '({:candidate "-with-meta" :ns "cljs.core" :type :protocol-function}
                {:candidate "-write" :ns "cljs.core" :type :protocol-function})
              (completions "-w")))))

(deftest record-completions
  (testing "Record"
    (is (= '({:candidate "TestRecord" :ns "suitable.test-ns" :type :record})
           (completions "Te" 'suitable.test-ns)))))

(deftest type-completions
  (testing "Type"
    (is (set= '({:candidate "ES6Iterator" :ns "cljs.core" :type :type}
                {:candidate "ES6IteratorSeq" :ns "cljs.core" :type :type})
              (completions "ES6I")))))

(deftest extra-metadata
  (testing "Extra metadata: namespace :doc"
    (binding [*extra-metadata* #{:doc}]
      (is (set= '({:candidate "suitable.test-ns" :doc "A test namespace" :type :namespace}
                  {:candidate "suitable.test-ns-dep" :doc "Dependency of test-ns namespace" :type :namespace})
                (completions "suitable.test-")))))

  (testing "Extra metadata: aliased namespace :doc"
    (binding [*extra-metadata* #{:doc}]
      (is (= '({:candidate "test-dep" :doc "Dependency of test-ns namespace" :ns "suitable.test-ns-dep" :type :namespace})
             (completions "test-d" 'suitable.test-ns)))))

  (testing "Extra metadata: macro namespace :doc"
    (binding [*extra-metadata* #{:doc}]
      (is (= '({:candidate "suitable.test-macros" :doc "A test macro namespace" :type :namespace})
             (completions "suitable.test-m" 'suitable.test-ns)))))

  (testing "Extra metadata: normal var :arglists"
    (binding [*extra-metadata* #{:arglists}]
      (is (set= '({:candidate "unchecked-add" :ns "cljs.core" :arglists ("[]" "[x]" "[x y]" "[x y & more]") :type :function}
                  {:candidate "unchecked-add-int" :ns "cljs.core" :arglists ("[]" "[x]" "[x y]" "[x y & more]") :type :function})
                (completions "unchecked-a" 'cljs.user)))))

  (testing "Extra metadata: normal var :doc"
    (binding [*extra-metadata* #{:doc}]
      (is (set= '({:candidate "unchecked-add" :ns "cljs.core" :doc "Returns the sum of nums. (+) returns 0." :type :function}
                  {:candidate "unchecked-add-int" :ns "cljs.core" :doc "Returns the sum of nums. (+) returns 0." :type :function})
                (completions "unchecked-a" 'cljs.user)))))

  (testing "Extra metadata: macro :arglists"
    (binding [*extra-metadata* #{:arglists}]
      (is (= '({:candidate "defprotocol" :ns "cljs.core" :arglists ("[psym & doc+methods]") :type :macro})
             (completions "defproto" 'cljs.user)))))

  (testing "Extra metadata: referred var :arglists"
    (binding [*extra-metadata* #{:arglists}]
      (is (= '({:candidate "foo-in-dep" :ns "suitable.test-ns-dep" :arglists ("[foo]") :type :function})
             (completions "foo" 'suitable.test-ns)))))

  (testing "Extra metadata: referred macro :arglists"
    (binding [*extra-metadata* #{:arglists}]
      (is (= '({:candidate "my-add" :ns "suitable.test-macros" :arglists ("[a b]") :type :macro})
             (completions "my-a" 'suitable.test-ns))))))

(deftest predicates
  (testing "The plain-symbol? predicate"
    (is (cljs-sources/plain-symbol? "foo") "should detect a \"plain\" symbol")
    (is (not (cljs-sources/plain-symbol? "foo.bar")) "should NOT match a namespace")
    (is (not (cljs-sources/plain-symbol? ":foo")) "should NOT match a keyword")
    (is (not (cljs-sources/plain-symbol? "::foo/bar")) "should NOT match a qualified keyword")
    (is (not (cljs-sources/plain-symbol? "foo/bar")) "should NOT match a qualified symbol")))

(deftest docstring-generation
  (testing "symbol docstring"
    (is (string? (cljs-sources/doc "map" nil)) "should return the map docstring, defaulting to the cljs.core namespace")
    (is (string? (cljs-sources/doc "map" (find-ns 'cljs.core))) "should return the map docstring")
    (is (string? (cljs-sources/doc "my-add" (find-ns 'suitable.test-macros)))))

  (testing "namespace docstring"
    (is (= "-------------------------\n\n  A test macro namespace\n" (cljs-sources/doc "suitable.test-macros" nil)))
    (is (= "-------------------------\n\n  A test namespace\n" (cljs-sources/doc "suitable.test-ns" nil)))))
