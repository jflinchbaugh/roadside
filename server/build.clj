(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b]))

(def lib 'com.hjsoft/server)
(def version (format "0.1.0-%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))

;; delay to defer side effects (artifact downloads)
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path "target"}))

(defn test [opts]
  (let [basis (b/create-basis {:project "deps.edn" :aliases [:test]})
        cmds (b/java-command
              {:basis basis
               :main "clojure.main"
               :jvm-opts ["--enable-preview"]
               :main-args (into ["-m" "cognitect.test-runner" "-d" "test"]
                            (mapcat (fn [[k v]] [(str k) (str v)]) opts))})]
    (b/process cmds)))

(defn uber [_]
  (clean)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis @basis
                  :ns-compile '[server.core]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis @basis
           :main 'server.core}))
