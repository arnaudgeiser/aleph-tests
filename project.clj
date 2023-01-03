(defproject aleph-tests "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [aleph "0.6.0"]
                 [manifold "0.3.0"]
                 [com.clojure-goes-fast/clj-async-profiler "1.0.3"]
                 [org.clj-commons/byte-streams "0.3.1"]]
  :jvm-opts ["-Djdk.attach.allowAttachSelf"]
  :repl-options {:init-ns aleph-tests.core})
