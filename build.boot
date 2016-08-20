(set-env!
  :resource-paths #{"src"}
  :source-paths #{"test"}
  :dependencies '[[org.clojure/clojure "1.8.0"         :scope "provided"]
                  [com.datomic/datomic-free "0.9.5206" :scope "test"]
                  [adzerk/boot-test    "1.1.0"         :scope "test"]])

(require '[adzerk.boot-test :refer [test]])

(def +version+ "0.1.0")

(task-options!
  pom {:project     'adstage/pluck-api
       :version     +version+
       :description "An extensible wrapper around Datomic's pull API."
       :url         "https://github.com/AdStage/pluck-api"
       :scm         {:url "https://github.com/AdStage/pluck-api"}
       :license     {"The MIT License (MIT)"
                     "https://opensource.org/licenses/MIT"}})

(deftask build []
  (comp
   (pom)
   (jar)
   (install)))

(deftask deploy []
  (comp
   (build)
   (push :repo "clojars" :gpg-sign (not (.endsWith +version+ "-SNAPSHOT")))))

(deftask run-tests []
  (comp
    (test :namespaces #{'adstage.pluck-api-test})))
