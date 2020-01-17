.PHONY: clean test all install depoy nrepl fig-repl

clean:
	@-rm -rf target/public/cljs-out \
		suitable.jar \
		.cpcache \
		target \
		out \
		.cljs_nashorn_repl \
		.cljs_node_repl \
		nashorn_code_cache \
		.rebel_readline_history

test:
	clojure  -A:test -d src/test

suitable.jar: deps.edn src/**/*
	clojure -A:pack \
		mach.pack.alpha.skinny \
		--no-libs \
		--project-path suitable.jar

pom.xml: deps.edn
	clojure -Spom

all: pom.xml suitable.jar

install: all
	mvn install:install-file -Dfile=suitable.jar -DpomFile=pom.xml

deploy: all
	mvn deploy:deploy-file -e \
		-DpomFile=pom.xml \
		-Dfile=suitable.jar \
		-Durl=https://clojars.org/repo \
		-DrepositoryId=clojars

fig-repl:
	clojure -A:fig-repl

nrepl:
	clojure -A:dev -R:test:nrepl
