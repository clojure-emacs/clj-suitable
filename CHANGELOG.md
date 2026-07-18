# Changelog

## master

- Match static completion candidates fuzzily, like compliment does for Clojure (`pr-fn` completes `print-function`, `cs` completes `clojure.string`).
- Rank static completion candidates with compliment-style priorities (current-ns vars first, then `cljs.core`, then namespaces and classes).
- Complete local bindings (`let`/`loop`/`fn`/`for`/`doseq`/... including destructuring) from the surrounding form, like compliment does for Clojure.
- Complete referred vars inside a `(:require [some.ns :refer [...]])` clause, scoped to that namespace.
- Only offer special forms at the head of a list, matching compliment.
- [#5](https://github.com/clojure-emacs/clj-suitable/issues/5): don't clobber the REPL's `*1`/`*2`/`*3` when computing dynamic completions.
- [#6](https://github.com/clojure-emacs/clj-suitable/issues/6): load the introspection namespace once per session instead of on every completion request.
- [#47](https://github.com/clojure-emacs/clj-suitable/issues/47), [#48](https://github.com/clojure-emacs/clj-suitable/issues/48): surface a failed introspection-namespace load in the shadow-cljs path instead of silently caching it, so browser-runtime failures are diagnosable and retried.

## 0.7.0 (2026-07-12)

- Adapt to piggieback 0.7.0's delegating repl-env so Node.js dynamic completion keeps working.
- Modernize dependencies (ClojureScript 1.12, compliment 0.8.0, cider-nrepl 0.61.0, shadow-cljs 3.x and more).
- Drop the ancient Clojure 1.8/1.9/1.10 rows from the CI matrix; test against 1.11, 1.12 and master instead.
- Migrate CI from CircleCI to GitHub Actions.
- Replace the Leiningen/`lein-git-down` build with a `deps.edn`-native `tools.build` build (see `build.clj`).
- Add a shadow-cljs integration test covering dynamic completion over a real Node runtime.

## 0.6.2 (2024-01-14)

* [#45](https://github.com/clojure-emacs/clj-suitable/issues/45): don't exclude enumerable properties from `Object`.

## 0.6.1 (2023-11-07)

- [#44](https://github.com/clojure-emacs/clj-suitable/pull/44): More robust completion for referred keywords. If a given namespace is
  required using `:as-alias`, completion candidates were missing for some CLJS environments.

## 0.6.0 (2023-11-05)

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
