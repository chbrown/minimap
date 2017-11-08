(defproject imap "0.1.1"
  :description "A minimal and partial native IMAP client with no dependencies on javax.mail"
  :url "https://github.com/ar7hur/minimap"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :profiles {:dev {:source-paths ["dev"]}}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [byte-transforms "0.1.4"]
                 [cheshire "5.3.1"]
                 [clj-time "0.8.0"]
                 [instaparse "1.3.3"]])
