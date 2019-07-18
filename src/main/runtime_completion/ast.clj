(ns runtime-completion.ast
  (:require [clojure.pprint :refer :all]
            [clojure.zip :as z])
  (:import [clojure.lang IPersistentList IPersistentMap IPersistentVector ISeq]))

; Thx @ Alex Miller! http://www.ibm.com/developerworks/library/j-treevisit/
(defmulti tree-branch? class)
(defmethod tree-branch? :default [_] false)
(defmethod tree-branch? IPersistentVector [v] (not-empty v))
(defmethod tree-branch? IPersistentMap [m] (not-empty m))
(defmethod tree-branch? IPersistentList [l] true)
(defmethod tree-branch? ISeq [s] true)
(prefer-method tree-branch? IPersistentList ISeq)

(defmulti tree-children class)
(defmethod tree-children IPersistentVector [v] v)
(defmethod tree-children IPersistentMap [m] (->> m seq (apply concat)))
(defmethod tree-children IPersistentList [l] l)
(defmethod tree-children ISeq [s] s)
(prefer-method tree-children IPersistentList ISeq)

(defmulti tree-make-node (fn [node children] (class node)))
(defmethod tree-make-node IPersistentVector [v children]
  (vec children))
(defmethod tree-make-node IPersistentMap [m children]
  (apply hash-map children))
(defmethod tree-make-node IPersistentList [_ children]
  children)
(defmethod tree-make-node ISeq [node children]
  (apply list children))
(prefer-method tree-make-node IPersistentList ISeq)

(defn tree-zipper [node]
  (z/zipper tree-branch? tree-children tree-make-node node))

(defn print-tree
  "for debugging"
  [node]
  (let [all (take-while (complement z/end?) (iterate z/next (tree-zipper node)))]
    (binding [*print-right-margin* 20]
     (pprint
      (->> all
        (map z/node) (zipmap (range))
        sort)))))
