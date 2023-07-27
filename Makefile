.PHONY: clean test install deploy nrepl fig-repl kondo eastwood lint

VERSION ?= 1.10

clean:
	@-rm -rf target/public/cljs-out \
		suitable.jar \
		.cpcache \
		target \
		out \
		.cljs_node_repl \
		.rebel_readline_history
	lein with-profile -user clean

test: clean
	clojure -A:test:$(VERSION) -d src/test

kondo:
	clojure -M:dev-figwheel:fig-repl:dev-shadow:test:kondo

eastwood:
	clojure -M:dev-figwheel:fig-repl:dev-shadow:test:eastwood

lint: kondo eastwood

install: clean check-install-env
	lein with-profile -user,-dev install

deploy: clean check-ci-env
	lein with-profile -user,-dev deploy clojars

# starts a figwheel repl with suitable enabled
fig-repl:
	clojure -M:fig-repl

# useful for development, see comment in src/dev/suitable/nrepl_figwheel.clj
nrepl-figwheel:
	clojure -M:test:dev-figwheel

# useful for development, see comment in src/dev/suitable/nrepl_.clj
nrepl-shadow:
	clojure -M:test:dev-shadow

check-ci-env:
ifndef CLOJARS_USERNAME
	$(error CLOJARS_USERNAME is undefined)
endif
ifndef CLOJARS_PASSWORD
	$(error CLOJARS_PASSWORD is undefined)
endif
ifndef CIRCLE_TAG
	$(error CIRCLE_TAG is undefined. Please only perform deployments by publishing git tags. CI will do the rest.)
endif

check-install-env:
ifndef PROJECT_VERSION
	$(error Please set PROJECT_VERSION as an env var beforehand.)
endif
