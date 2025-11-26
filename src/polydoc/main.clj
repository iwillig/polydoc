(ns polydoc.main
  "Polydoc CLI - JVM-native Pandoc documentation system.
  
  Main entry point for the Polydoc command-line interface.
  
  Commands:
  
    filter  - Execute Pandoc filters
    book    - Build complete books (coming soon)
    search  - Search documentation (coming soon)
    view    - Interactive viewer (coming soon)
  
  Usage:
  
    clojure -M:main --help
    clojure -M:main filter --help
    clojure -M:main filter -t clojure-exec -i input.json -o output.json
  
  For more information, see README.md and examples/"
  (:gen-class)
  (:require
    [cli-matic.core :as cli]
    [polydoc.filters.clojure-exec :as clj-exec]
    [polydoc.filters.include :as include]
    [polydoc.filters.javascript-exec :as js-exec]
    [polydoc.filters.plantuml :as plantuml]
    [polydoc.filters.sqlite-exec :as sqlite-exec]))


;; Version information
(def version "0.1.0-SNAPSHOT")


;; Command handler functions
(defn filter-cmd
  "Execute a Pandoc filter"
  [{:keys [type input output]}]
  (case type
    "clojure-exec" (clj-exec/main {:input input :output output})
    "sqlite-exec" (sqlite-exec/main {:input input :output output})
    "sqlite" (sqlite-exec/main {:input input :output output})
    "plantuml" (plantuml/main {:input input :output output})
    "include" (include/main {:input input :output output})
    "javascript-exec" (js-exec/main {:input input :output output})
    "js-exec" (js-exec/main {:input input :output output})
    ;; Default case
    (binding [*out* *err*]
      (println "ERROR: Unknown filter type:" type)
      (println "Available filters: clojure-exec, sqlite-exec, plantuml, include, javascript-exec")
      (System/exit 1))))


(defn book-cmd
  "Build a book from source files"
  [{:keys [config output]}]
  (println "Book build command")
  (println "Config:" config)
  (println "Output:" output))


(defn search-cmd
  "Search documentation"
  [{:keys [database query]}]
  (println "Search command")
  (println "Database:" database)
  (println "Query:" query))


(defn view-cmd
  "Start interactive viewer"
  [{:keys [database port]}]
  (println "View command")
  (println "Database:" database)
  (println "Port:" port))


;; CLI configuration
(def CONFIGURATION
  {:app {:command "polydoc"
         :description "JVM-native Pandoc documentation system"
         :version version}

   :global-opts [{:option "verbose"
                  :short "v"
                  :type :with-flag
                  :default false
                  :as "Enable verbose output"}]

   :commands [{:command "filter"
               :description "Execute a Pandoc filter"
               :opts [{:option "type"
                       :short "t"
                       :as "Filter type (clojure-exec, sqlite-exec, plantuml, javascript-exec, include)"
                       :type :string
                       :required true}
                      {:option "input"
                       :short "i"
                       :as "Input file (default: stdin)"
                       :type :string
                       :default "-"}
                      {:option "output"
                       :short "o"
                       :as "Output file (default: stdout)"
                       :type :string
                       :default "-"}]
               :runs filter-cmd}

              {:command "book"
               :description "Build a book from source files"
               :opts [{:option "config"
                       :short "c"
                       :as "Book configuration file (YAML)"
                       :type :string
                       :required true}
                      {:option "output"
                       :short "o"
                       :as "Output directory"
                       :type :string
                       :required true}]
               :runs book-cmd}

              {:command "search"
               :description "Search documentation"
               :opts [{:option "database"
                       :short "d"
                       :as "SQLite database file"
                       :type :string
                       :required true}
                      {:option "query"
                       :short "q"
                       :as "Search query"
                       :type :string
                       :required true}]
               :runs search-cmd}

              {:command "view"
               :description "Start interactive documentation viewer"
               :opts [{:option "database"
                       :short "d"
                       :as "SQLite database file"
                       :type :string
                       :required true}
                      {:option "port"
                       :short "p"
                       :as "HTTP server port"
                       :type :int
                       :default 8080}]
               :runs view-cmd}]})


(defn -main
  [& args]
  (cli/run-cmd args CONFIGURATION))
