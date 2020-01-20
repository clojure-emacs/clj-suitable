# suitable - ClojureScript Completion Toolkit

[![Clojars Project](https://img.shields.io/clojars/v/org.rksm/suitable.svg)](https://clojars.org/org.rksm/suitable)

suitable provides static and dynamic code completion for ClojureScript tools.

It integrates a) with the CLJS analyzer and using the compilation state for
"static" symbol completion. This functionality was formerly part of
[compliment](https://github.com/alexander-yakushev/compliment).

It b) can use a CLJS repl session to query and inspect JavaScript runtime state,
allowing code completion for JavaScript objects and interfaces.

## TODO: describe static completion / compliment feature

## Dynamic code completion for CLJS repls

The dynamic code completion features allow for exploratory development by
inspecting the runtime. For example you work with DOM objects but can't remember
how to query for child elements. Type `(.| js/document)` (with `|` marking the
postion of your cursor) and press TAB. Methods and properties of `js/document`
will appear — including `querySelector` and `querySelectorAll`.

Currently Emacs (via CIDER) and figwheel.main are supported. If you want support
for your favorite tool please let me know and I'll look into it (no promises,
though).

<!-- It also provides a [compliment custom -->
<!-- source](https://github.com/alexander-yakushev/compliment/wiki/Custom-sources] -->
<!-- for ClojureScript so that tooling can depend on it. -->

### Dynamic code completion: *CAVEATS*

The dynamic code completion *evaluates* code on completion requests! It does
this by trying to [enumerate the
properties](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/getOwnPropertyDescriptors)
of JavaScript objects, so in the example above it would fetch all properties of
the `js/document` object. Given that pretty much everything is possible (e.g.
through getters), this alone can cause side effects.

Moreover, also chains of methods and properties will be evaluated upon
completion requests. So for example, asking for completions for the code /
cursor position `(-> js/some-object (.deleteAllMyFilesAndStartAWar) .|)` will
evaluate the JavaScript code `some-object.deleteAllMyFilesAndStartAWar()`!
Please be aware of this behavior when using the dynamic code completion
features.

## Dynamic completion Demo

The animation shows how various properties and methods of the native DOM can be
accessed (Tab is used to show completions for the expression at the cursor):

![](doc/2019_07_22_suitable-figwheel.gif)

## Setup

### figwheel.main with rebel-readline

Please note that you need to use
[rebel-readline](https://github.com/bhauman/rebel-readline) with figwheel for
that to work. Plain repls have no completion feature.

#### Tools CLI

First make sure that the [normal Tools CLI setup](https://figwheel.org/#setting-up-a-build-with-tools-cli) works.

Then modify `deps.edn` and `dev.cljs.edn`, you should end up with the files looking like below:

- `deps.edn`

```clojure
{:deps {com.bhauman/figwheel-main {:mvn/version "RELEASE"}
        com.bhauman/rebel-readline-cljs {:mvn/version "RELEASE"}}
 :paths ["src" "resources" "target"]
 :aliases {:suitable {:extra-deps {org.rksm/suitable {:mvn/version "RELEASE"}}
	              :main-opts ["-e" "(require,'suitable.hijack-rebel-readline-complete)"
                                  "-m" "figwheel.main"
                                  "--build" "dev" "--repl"]}}}
```

- `dev.cljs.edn`

```clojure
{:main example.core
 :preloads [suitable.js-introspection]}
```

- `src/example/core.cljs`

```clojure
(ns example.core)
```

You can now start a figwheel repl via `clj -A:suitable` and use TAB to complete.

#### leiningen

First make sure that the [normal leiningen setup](https://figwheel.org/#setting-up-a-build-with-leiningen) works.

Add `[org.rksm/suitable "0.3.1"]` to your dependencies vector.

Then you can start a repl with `lein trampoline run -m suitable.figwheel.main -- -b dev -r`

### Emacs CIDER

Suitable is now a default middleware in CIDER (as of CIDER 0.22.0)! So no extra
installation steps are required.

<!-- For usage with `cider-jack-in-cljs` add these two lines to your emacs config: -->

<!-- ```lisp -->
<!-- (cider-add-to-alist 'cider-jack-in-cljs-dependencies "org.rksm/suitable" "0.3.1") -->
<!-- (add-to-list 'cider-jack-in-cljs-nrepl-middlewares "suitable.middleware/wrap-complete") -->
<!-- ``` -->

<!-- That's it, your normal completion (e.g. via company) should pick up the completions provided by suitable. -->

### Custom nREPL server

To load suitable into a custom server you can load it using this monstrosity:

```shell
clj -Sdeps '{:deps {cider/cider-nrepl {:mvn/version "0.22.0"} cider/piggieback {:mvn/version"0.4.1"}}}' -m nrepl.cmdline --middleware "[cider.nrepl/cider-middleware,cider.piggieback/wrap-cljs-repl]"
```

Or from within Clojure:

```clojure
(ns my-own-nrepl-server
  (:require cider.nrepl
            cider.piggieback
            nrepl.server))

(defn start-cljs-nrepl-server []
(let [middlewares (conj (map resolve cider.nrepl/cider-middleware)
                        #'cider.piggieback/wrap-cljs-repl)
      handler (apply nrepl.server/default-handler middlewares)]
  (nrepl.server/start-server :handler handler))
```

## How does it work?

suitable uses the same input as the widely used
[compliment](https://github.com/alexander-yakushev/compliment). This means it
gets a prefix string and a context form from the tool it is connected to. For
example you type `(.l| js/console)` with "|" marking where your cursor is. The
input we get would then be: prefix = `.l` and context = `(__prefix__ js/console)`.

suitable recognizes various ways how CLJS can access properties and methods,
such as `.`, `..`, `doto`, and threading forms. Also direct global access is
supported such as `js/console.log`. suitable will then figure out the expression
that defines the "parent object" that the property / method we want to use
belongs to. For the example above it would be `js/console`. The system then uses
the [EcmaScript property descriptor API](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/defineProperty)
to enumerate the object members. Those are converted into completion candidates
and send back to the tooling.

## License

This project is [MIT licensed](LICENSE).
