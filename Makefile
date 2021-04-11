.PHONY: clean test all install deploy nrepl fig-repl

CLJ_FILES := $(shell find src -iname *.cljs -o -iname *.cljc  -o -iname *.clj)
SRC_FILES := ${CLJ_FILES} deps.edn fig.cljs.edn Makefile pom.xml

clean:
	@-rm -rf target/public/cljs-out \
		suitable.jar \
		.cpcache \
		target \
		out \
		.cljs_node_repl \
		.rebel_readline_history

test: ${SRC_FILES}
	clojure  -A:test -d src/test

suitable.jar: ${SRC_FILES}
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
