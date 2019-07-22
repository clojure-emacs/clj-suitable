# Suitable - reddit: An addon for Figwheel and Emacs Cider to aid live exploratory development in ClojureScript

This project is a backend for interactive repls and editors that provide
completion / intellisense support.

It is mainly useful for cljs / js interop as it tries to access the properties
and methods of objects at runtime via reflection to generate completion
candidates out of them. This can be extremely useful and productive if you're
experimenting around with unknown APIs.

Currently Emacs (via Cider) and figwheel.main are supported.

## Demo

The animation shows how various properties and methods of the native DOM can be accessed (Tab is used to show completions for the expression at the cursor)

![](doc/2019_07_22_suitable-figwheel.gif)

## Setup

### figwheel.main with rebel-readline

Please note that you need to use [rebel-readline](https://github.com/bhauman/rebel-readline) with figwheel for that to work. Plain repls have no completion feature.

#### Tools CLI

First make sure that the [normal Tools CLI setup](https://figwheel.org/#setting-up-a-build-with-tools-cli) works.

Then modify your `deps.edn` so that org.rksm/suitable and it's setup code are included:

```clojure
 :aliases {:suitable {:extra-deps {org.rksm/suitable {:mvn/version "0.1.0"}}
	                  :main-opts ["-e" "(require,'suitable.hijack-rebel-readline-complete)"
                                  "-m" "figwheel.main"
                                  "--build" "dev" "--repl"]}}
```

Also add a preload to your `dev.cljs.edn`:

```clojure
{:main hello-world.core
 :preloads [suitable.js-introspection]}
```

Finally start a figwheel repl via `clj -A:suitable`.

#### leiningen

First make sure that the [normal leiningen setup](https://figwheel.org/#setting-up-a-build-with-leiningen) works.

Add `[org.rksm/suitable "0.1.0"]` to your dependencies vector.

Then you can start a repl with `lein trampoline run -m suitable.figwheel.main -- -b dev -r`

### Emacs Cider

For usage with `cider-jack-in-cljs` add these two lines to your emacs config:

```lisp
(cider-add-to-alist 'cider-jack-in-cljs-dependencies "org.rksm/suitable" "0.1.0")
(add-to-list 'cider-jack-in-cljs-nrepl-middlewares "suitable.middleware/wrap-complete")
```

That's it, your normal completion (e.g. via company) should pick up the completions provided by suitable.

### Custom nREPL server

To load suitable into a custom server you can load it using this monstrosity:

```shell
clj -Sdeps '{:deps {cider/cider-nrepl {:mvn/version "0.21.1"} org.rksm/suitable {:mvn/version "0.1.0"} cider/piggieback {:mvn/version"0.4.1"}}' -m nrepl.cmdline --middleware "[cider.nrepl/cider-middleware,cider.piggieback/wrap-cljs-repl,suitable.middleware/wrap-complete]"
```

Or from within Clojure:

```clojure
(ns my-own-nrepl-server
  (:require cider.nrepl
            cider.piggieback
            nrepl.server
            suitable.middleware))

(defn start-cljs-nrepl-server []
  (let [middlewares (map resolve cider.nrepl/cider-middleware)
        middlewares (conj middlewares #'cider.piggieback/wrap-cljs-repl)
        middlewares (conj middlewares #'suitable.middleware/wrap-complete)
        handler (apply nrepl.server/default-handler middlewares)]
    (nrepl.server/start-server :handler handler)))
```

## How does it work?


## License

This project is [MIT licensed](LICENSE).
