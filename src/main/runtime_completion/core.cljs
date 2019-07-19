(ns runtime-completion.core
  (:require [clojure.spec.alpha :as s]
            [clojure.string :refer [starts-with?]]
            [goog.object :refer [get] :rename {get oget}]
            [runtime-completion.spec :as spec]))

(defn properties-by-prototype
  ""
  [obj]
  (loop [obj obj protos []]
    (if obj
      (recur
       (js/Object.getPrototypeOf obj)
       (conj protos {:obj obj :props (js/Object.getOwnPropertyDescriptors obj)}))
      protos)))

(defn property-names-and-types
  ([js-obj] (property-names-and-types js-obj nil))
  ([js-obj prefix]
   {:pre [(s/valid? (complement nil?) js-obj)
          (s/valid? (s/nilable string?) prefix)]
    :post [(s/valid? (s/coll-of ::spec/obj-property) %)]}
   (let [seen (transient #{})]
     (for [[i {:keys [obj props]}] (map-indexed vector (properties-by-prototype js-obj))
           key (js-keys props)
           :when (and (not (get seen key))
                      (or (empty? prefix)
                          (starts-with? key prefix)))]
       (let [prop (oget props key)]
         (conj! seen key)
         {:name key
          :hierarchy i
          :type (try
                  (if-let [value (or (oget prop "value")
                                     (-> prop (oget "get")
                                         (apply [])))]
                    (if (fn? value) "function" "var")
                    "var")
                  (catch js/Error e "var"))})))))


(comment
  (require '[cljs.pprint :refer [pprint]])
  ;; (-> js/console property-names-and-types pprint)
  (-> js/document.body property-names-and-types pprint)

  (let [obj (new (fn [x] (this-as this (goog.object/set this "foo" 23))))]
    (pprint (property-names-and-types obj)))

  (oget js/console "log")
  (-> js/console property-names-and-types pprint)
  (-> js/window property-names-and-types pprint)
  )
