(defproject klobb "0.1.0-SNAPSHOT"
  :description "A Jekyll-inspired blog engine."
  :url "http://github.com/danneu/klobb"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [ring "1.2.0"]
                 [markdown-clj "0.9.29"]
                 [de.ubercode.clostache/clostache "1.3.1"]
                 [clj-time "0.6.0"]
                 [compojure "1.1.5"]]
  :plugins [[lein-ring "0.8.5"]]
  :ring {:handler klobb.handler/app}
  :main klobb.handler
  :aot [klobb.handler]

  ;; TODO: This was convenient when I had a sample blog
  ;;       in the target/klobb-danneu/ directory so I could
  ;;       test the uberjar alongside the lein-ring dev server
  ;;       but this shouldn't be necessary once the root-path
  ;;       is normalized between dev and uberjar prod.
  ;;
  ;; :uberjar-name "klobb-demo/klobb.jar"
  )
