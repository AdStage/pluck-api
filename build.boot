(set-env!
  :resource-paths #{"src"}
  :source-paths #{"test"}
  :dependencies '[[adzerk/bootlaces "0.1.13"           :scope "test"]
                  [org.clojure/clojure "1.8.0"         :scope "provided"]
                  [com.datomic/datomic-free "0.9.5206" :scope "provided"]
                  [adzerk/boot-test    "1.1.0"         :scope "test"]])

(require
 '[adzerk.bootlaces :refer :all]
 '[clojure.java.io  :as io]
 '[boot.pod         :as pod]
 '[adzerk.boot-test :refer [test]])

(def +version+ "0.1.0-SNAPSHOT")

(task-options!
  pom {:project     'adstage/pluck-api
       :version     +version+
       :description "An extensible wrapper around Datomic's pull API."
       :url         "https://github.com/AdStage/pluck-api"
       :scm         {:url "https://github.com/AdStage/pluck-api"}
       :license     {"The MIT License (MIT)"
                     "https://opensource.org/licenses/MIT"}}
  push {:repo "deploy-clojars"})

(deftask build []
  (comp
   (pom)
   (jar)
   (install)))

(deftask run-tests []
  (comp
    (test :namespaces #{'adstage.pluck-api-test})))
