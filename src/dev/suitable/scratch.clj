(ns dev.suitable.scratch
  (:require [clojure.zip :as zip]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as st]))


(comment


  (sc.api/defsc 12)
    (sc.api/letsc 6 [obj-expr])
    (sc.api/letsc 6 [properties])
    (sc.api/letsc 2 [error])

(s/def ::non-empty-string (s/and string? not-empty))


(defn foo [x]
  {:pre [(s/valid? ::not-empty-string x )]}
  ;; {:pre [(s/valid? (s/coll-of string?) x)]}
  ;; {:pre [(s/valid? string? x)]}
  x)

(with-redefs {#'foo (fn [x] (inc x))} (foo 23))
(with-redefs [foo (fn [x] (inc x))] (foo 23))

(with-redefs)

(foo "23")
(foo "")



(defn my-inc [x]
  (inc x))
(s/fdef my-inc
      :args (s/cat :x number?)
      :ret number?)
(my-inc 0)
(my-inc "foo")
(st/instrument `my-inc)
(st/instrument `my-inc)
(s/exercise-fn `my-inc 10)
(st/unstrument `my-inc)

(defn bar [x]
  x)

(st/instrument)
(s/fdef bar
  :args (s/cat :x (s/and string? not-empty)))

(st/instrument `bar)

(s/exercise-fn `bar)

(bar "foo")
(bar "")


(require '[clojure.zip :as zip])

(sc.api/defsc 30)

(zip/node prefix)

(= (sc.api/letsc 1 [session] session) (sc.api/letsc 9 [session] session))


(sc.api/letsc 1 [session] (get @session #'suitable.middleware/*object-completion-state*))

session
(let [a (atom {})] (= a a a))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(->> properties
     )

(merge (for [{:keys [name type]} properties] {:type type :candidate (str "." name)}))
(map (fn [{:keys [name type]}] {:type type :candidate (str "." name)}) properties)

(into)
(into {} (fn [{:keys [name type]}] [:type type :candidate (str "." name)]) properties)

(require '[clojure.pprint :refer [cl-format]])


(doall
 (into {} (completing (fn [result {:keys [name type]}]
                        (do
                          (cl-format true "~A??????????~%" result)
                         [:type type :candidate (str "." name)]))) properties))

(into {} (map (fn [{:keys [name type]}] {:type type :candidate (str "." name)})) properties)
(eduction (map (fn [{:keys [name type]}] [:type type :candidate (str "." name)])) properties)
(transduce (map (fn [{:keys [name type]}] [:type type :candidate (str "." name)])) identity {} properties)

(def xform (map #(+ 2 %)))

(transduce 
 xform
 (fn
   ([] (prn "0"))
   ([result] (prn "1" result))
   ([result input] (prn "2" result input)))
 [1 2 3])

(into [-1 -2] xform (range 10))

(fn [rf]
      (fn
        ([] (rf))
        ([result] (rf result))
        ([result input]
           (rf result (f input)))
        ([result input & inputs]
           (rf result (apply f input inputs)))))

(into {} (fn [rf]
           (letfn [(f [input] (prn "input" input) input)]
             (let [f merge]
              (fn
                ([] (prn "0 =>" (rf)) (rf))
                ([result] (prn "1" result (rf result)) (rf result))
                ([result input]
                 (rf result (f input)))))))
      properties)





)



(comment

  (require '[cider.nrepl.inlined-deps.compliment.v0v3v8.compliment.core :as compliment])

  compliment/all-files

  (compliment/completions ".get" {:ns "user" :context "(__prefix__ (java.lang.Thread.))"})
  (compliment/completions "clojure.string/joi" {:ns "user" :context "(__prefix__)"})

  )
