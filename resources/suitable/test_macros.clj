(ns ^{:doc "A test macro namespace"} suitable.test-macros)

(defmacro my-add
  "This is an addition macro"
  [a b]
  `(+ ~a ~b))

(defmacro my-sub
  "This is a subtraction macro"
  [a b]
  `(- ~a ~b))
