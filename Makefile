.PHONY: clean test install deploy nrepl fig-repl kondo eastwood lint

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

install: clean
	lein with-profile -user,-dev install

# CLOJARS_USERNAME=$USER CLOJARS_PASSWORD=$(pbpaste) make deploy
deploy: clean
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
