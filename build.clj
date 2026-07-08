(ns build
  "Build and deployment for suitable.

  Common tasks:
    clojure -T:build jar                        ; build the jar
    PROJECT_VERSION=x.y.z clojure -T:build install  ; install to the local ~/.m2
    PROJECT_VERSION=x.y.z clojure -T:build deploy   ; deploy to Clojars

  The version is taken from the PROJECT_VERSION env var, so releases are driven
  by CI from a git tag (see .github/workflows/ci.yml)."
  (:require
   [clojure.tools.build.api :as b]
   [deps-deploy.deps-deploy :as dd]))

(def lib 'org.rksm/suitable)
(def version (or (not-empty (System/getenv "PROJECT_VERSION")) "0.0.0"))
(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn- pom-template []
  {:class-dir class-dir
   :lib lib
   :version version
   :basis (b/create-basis {:project "deps.edn"})
   :src-dirs ["src/main"]
   :scm {:url "https://github.com/clojure-emacs/clj-suitable"
         :connection "scm:git:https://github.com/clojure-emacs/clj-suitable.git"
         :developerConnection "scm:git:ssh://git@github.com/clojure-emacs/clj-suitable.git"
         :tag (str "v" version)}
   :pom-data [[:description "ClojureScript completion toolkit providing static and dynamic code completion."]
              [:url "https://github.com/clojure-emacs/clj-suitable"]
              [:licenses
               [:license
                [:name "MIT"]
                [:url "https://opensource.org/licenses/MIT"]]]]})

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (clean nil)
  (b/write-pom (pom-template))
  (b/copy-dir {:src-dirs ["src/main" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file})
  (println "Wrote" jar-file))

(defn install [_]
  (jar nil)
  (b/install {:basis (b/create-basis {:project "deps.edn"})
              :lib lib
              :version version
              :jar-file jar-file
              :class-dir class-dir}))

(defn deploy [_]
  (jar nil)
  (dd/deploy {:installer :remote
              :artifact (b/resolve-path jar-file)
              :pom-file (b/pom-path {:lib lib :class-dir class-dir})}))
