(ns klobb.handler
  (:use [compojure.core]
        [ring.util response]
        [ring.middleware file-info]
        [klobb.middleware ensure-trailing-slash])
  (:require [clostache.parser :refer [render]]
            [klobb.markdown :as markdown]
            [clojure.string :as str
             :refer [trim replace-first split]]
            [compojure.handler :as handler]
            [ring.adapter.jetty :refer [run-jetty]]
            [compojure.route :as route])
  (:import [java.io File])
  (:gen-class))

;; Util ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def jar-path
  (-> clojure.lang.Atom
      (.getProtectionDomain)
      (.getCodeSource)
      (.getLocation)
      (.getPath)))

;; Comical attempt to normalize filepath in dev and prod.
;;
;; - When run from repl or lein ring server, jar-path is
;;   the /.m2/respository/.../clojure-x.x.x.jar.
;; - When run from uberjar $ java -jar klobb.jar, then
;;   jar-path is ~/.../klobb.jar.
;;
;; TODO: Do something robust. And perhaps include a test
;;       blog in the test/ directory that's handy during
;;       klobb development.
(def root-path
  (if (re-find #".m2" jar-path)
    "/Users/danneu/Code/Clojure/klobb/target/klobb-danneu/"
    (str/replace jar-path #"\/[a-zA-Z-\d\.]+.jar" "/")))  ; lol

(defn expand-path
  "Returns absolute filesystem path for given
   relative-to-blog-root path.

   Example: index.mustache -> /Users/.../path/to/index.mustache"
  [path]
  (str root-path path))

;; File Contents ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; FIXME: Sloppy approach.
;;
;; The contents of index.mustache, pages, layouts, and post files
;; are in the form of:
;;
;; ```
;; {:foo 1
;;  :bar 2}
;;
;; Here is the file text.
;; ```
;;
;; (parse-opts file-content) returns the map (or just {} if no map).
;; (parse-body file-content) returns the non-opts text.

(defn parse-opts [content]
  (if-let [s (re-find #"\A\{{1}[^\}\{]*\}{1}" content)]
    (read-string s)
    {}))

(defn parse-body [s]
  (trim (replace-first s #"\A\{[^\}]+\}" "")))

;; File tree ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Currently there are four places to look for files:
;;
;; /index.mustache       - the index file
;; /layouts/*.mustache   - layouts
;; /pages/*.md           - pages
;; /posts/*/content.md   - posts
;;
;; What I have done below is parse the layouts,
;; posts, and pages into maps that look something like this:
;;
;; {:content-path "/Users/.../layouts/post.md"
;;  :filename "post.md"
;;  :opts {:layout "default"}}
;;
;; Then I've created post-lookup, layout-lookup, and page-lookup
;; maps so that I can retrieve the map representation of a file
;; by looking it up by its slug (if it's a page or post) or
;; its name (if it's a layout).
;;
;; Haven't decided how I want to generalize it yet nor what
;; I want the abstraction to look like.
;;
;; TODO: Generalize, abstraction, and dry up.
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Caching
;;
;; I decided to slurp the necessary files on every request
;; which is most convenient during development.
;;
;; An in-memory caching layer can be implemented on top of it,
;; like in the route layer. For example, if in production mode,
;; the slurp can happen on first request and then sit in memory
;; like Rails caching.
;;
;; Ultimately, there will be an option to pre-render the entire
;; blog into static files like Jekyll.
;; 
;; TODO: Implement caching.
;; FIXME: Turn the lookups into functions so that the lookups
;;        are rebuilt on every request. Right now files are only
;;        reslurped on every request and klobb won't pick up
;;        new files and opts changes in existing files.

;; Layouts

(defn gen-layouts
  "Returns map representations of layouts.
   {:filename \"post\"
    :ext \"mustache\"
    :content-path \"/Users/path/to/layouts/post.mustache\"
    :opts {:layout \"post\"}}"
  []
  (let [layouts-path (expand-path "layouts/")]
    (for [file (.listFiles (File. layouts-path))
          :let [filename-with-ext (.getName file)]
          :when (re-find #".mustache" filename-with-ext)]
      (let [[filename ext] (split filename-with-ext #"\.")
            path (str layouts-path filename-with-ext)]
        {:filename filename
         :ext ext
         :content-path path
         :opts (parse-opts (slurp path))}))))

;; Lookup
(def layout-lookup
  (into {} (map #(vector (:filename %) %) (gen-layouts))))

;; Posts

(defn gen-post
  "Pass it path to post folder"
  [folder-path]
  (let [content (slurp (str folder-path "content.md"))
        folder-name (.getName (File. folder-path))
        default-opts {:created-at (re-find #"\d+-\d+-\d+"
                                          folder-name)
                      :published? true
                      :layout "post"
                      :slug folder-name}]
    {:folder-name folder-name
     :folder-path folder-path
     :content-path (str folder-path "content.md")
     :opts (merge default-opts
                  (parse-opts content))}))

(defn gen-posts []
  (let [posts-path (expand-path "posts/")]
    (for [folder (.listFiles (File. posts-path))
          :let [folder-path (str (.getAbsolutePath folder) "/")]
          :when #(.isDirectory %)]
      (gen-post folder-path))))

(def post-lookup (into {} (map #(vector (-> % :opts :slug) %)
                               (gen-posts))))

;; Pages

(defn page-paths []
  (for [file (.listFiles (File. (expand-path "pages")))
        :let [filename-with-ext (.getName file)]
        :when (re-find #".md" filename-with-ext)]
    (.getAbsolutePath file)))

(defn gen-page [path]
  (let [[filename ext] (str/split (.getName (File. path)) #"\.")]
    {:filename filename
     :slug filename
     :ext ext
     :content-path path
     :opts (merge {:layout "default"}
                  (parse-opts (slurp path)))}))
(defn gen-pages []
  (map gen-page (page-paths)))

(def page-lookup (into {} (map #(vector (-> % :slug) %)
                               (gen-pages))))

;; File rendering
;;
;; TODO: Decide on an abstraction to dry up the render-* functions.
;;
;; Since pages, the index, posts, and even layouts can all specify
;; a {:layout _} option, rendering any file should be a recursive
;; process that embeds files into their given layout until no
;; layouts are left.

(defn render-index []
  (let [content (slurp (expand-path "index.mustache"))
        opts (parse-opts content)
        body (parse-body content)
        index-html (render body {:posts (map :opts (gen-posts))})
        layout (layout-lookup (:layout opts))]
    (render (slurp (:content-path layout))
            {:yield index-html})))

(defn render-page [slug]
  (let [page (page-lookup slug)
        content (slurp (:content-path page))
        opts (:opts page)
        body (parse-body content)
        page-html (markdown/to-html body)
        layout (layout-lookup (:layout opts))]
    (render (slurp (:content-path layout)) {:yield page-html})))

(defn render-post [slug]
  (let [post (post-lookup slug)
        post-markdown (parse-body (slurp (:content-path post)))
        post-html (markdown/to-html post-markdown)]
    ;; If no layout, then return post html.
    ;; If there is layout, then render new html.
    (loop [this-file post
           prev-html post-html]
      ;; If no layout, then return the html we have
      (if-let [layout (layout-lookup (-> this-file :opts :layout))]
        (recur layout
               (render (parse-body (slurp (:content-path layout)))
                       (merge (:opts this-file)
                              {:yield prev-html})))
        prev-html))))

;; Routes ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defroutes app-routes
  (GET "/" [] (render-index))
  (GET "/:slug/" [slug] (render-page slug))
  (GET "/posts/:slug/" [slug] (render-post slug))
  (GET "/posts/:slug/*" [slug *]
    (let [{folder-path :folder-path} (post-lookup slug)
          asset-path (str folder-path *)]
      (file-response asset-path)))
  (route/files "/" {:root (expand-path "public")})
  (route/not-found "Not Found"))

(def app
  (ensure-trailing-slash (handler/site app-routes)))

;; Server

(defn start-server [port]
  (run-jetty app {:port port}))

(defn -main [& args]
  (let [port (Integer. (or (first args) "5004"))]
    (start-server port)))
