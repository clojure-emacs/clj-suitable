# Changelog

## master

- [#39](https://github.com/clojure-emacs/clj-suitable/issues/39): Exclude enumerable from JS completion candidates.
- Expand completion for keywords referred using `:as-alias`.

## 0.5.1 (2023-10-31)

- [#41](https://github.com/clojure-emacs/clj-suitable/pull/41): Expand completion for non-namespaced keywords.

## 0.5.0 (2023-07-28)

* [#30](https://github.com/clojure-emacs/clj-suitable/issues/30): don't run side-effects for pure-clojurescript (non-interop) `->` chains.

## 0.4.1 (2021-10-02)

* [#22](https://github.com/clojure-emacs/clj-suitable/issues/22): Gracefully handle string requires.
* [#14](https://github.com/clojure-emacs/clj-suitable/issues/14): Fix a NullPointerException / Fix Node.js detection

## 0.4.0 (2021-04-18)

* Fix dynamic completion for `shadow-cljs`.
