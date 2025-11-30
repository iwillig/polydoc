(ns polydoc.main
  "Polydoc CLI - JVM-native Pandoc documentation system.
  
  Main entry point for the Polydoc command-line interface.
  
  Commands:
  
    filter  - Execute Pandoc filters
    book    - Build complete books from polydoc.yml
    search  - Search documentation with FTS5 full-text search
    config  - Display configuration as JSON
    gen     - Generate new Polydoc projects
    view    - Interactive viewer (coming soon)
  
  Usage:
  
    clojure -M:main --help
    clojure -M:main filter --help
    clojure -M:main filter -t clojure-exec -i input.json -o output.json
    clojure -M:main book -c polydoc.yml -o output/
    clojure -M:main search -d polydoc.db -q \"search query\"
    clojure -M:main config -c polydoc.yml
    clojure -M:main gen book --title \"My Book\" --output-dir ./my-book
  
  For more information, see README.md and examples/"
  (:gen-class)
  (:require
   [cli-matic.core :as cli]
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [com.stuartsierra.component :as component]
   [next.jdbc :as jdbc]
   [polydoc.book.builder :as builder]
   [polydoc.book.metadata :as metadata]
   [polydoc.book.search :as search]
   [polydoc.filters.clojure-exec :as clj-exec]
   [polydoc.filters.include :as include]
   [polydoc.filters.plantuml :as plantuml]
   [polydoc.filters.sqlite-exec :as sqlite-exec]
   [polydoc.generator.book :as gen-book]
   [polydoc.viewer.server :as viewer]))


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
    ;; Default case
    (binding [*out* *err*]
      (println "ERROR: Unknown filter type:" type)
      (println "Available filters: clojure-exec, sqlite-exec, plantuml, include")
      (System/exit 1))))


(defn book-cmd
  "Build a book from source files"
  [{:keys [config output]}]
  (try
    (let [result (builder/build-book config output)]
      (println "\n✓ Book build complete!")
      (println "  Database:" (:database result))
      (println "  HTML output:" (get-in result [:outputs :html]))
      0)
    (catch Exception e
      (binding [*out* *err*]
        (println "\nERROR building book:" (.getMessage e))
        (when-let [cause (.getCause e)]
          (println "Caused by:" (.getMessage cause)))
        (.printStackTrace e *err*)
        (System/exit 1)))))


(defn search-cmd
  "Search documentation"
  [{:keys [database query limit book-id]}]
  (try
    ;; Validate database exists
    (when-not (.exists (io/file database))
      (binding [*out* *err*]
        (println "ERROR: Database file not found:" database)
        (System/exit 1)))

    ;; Connect to database
    (let [ds (jdbc/get-datasource {:dbtype "sqlite" :dbname database})
          search-opts (cond-> {:limit (or limit 10)}
                        book-id (assoc :book-id book-id))
          results (search/search ds query search-opts)
          total-count (search/count-results ds query search-opts)]

      ;; Display results
      (if (seq results)
        (do
          ;; Header
          (println "Search results for:" query)
          (println "Found" total-count "results (showing" (count results) ")")
          (println)

          ;; Results
          (doseq [[idx result] (map-indexed vector results)]
            (let [{:sections/keys [heading_text heading_level source_file]
                   :keys [snippet]} result]
              (println (str (inc idx) ".")
                       heading_text
                       (str "(level " heading_level ")"))
              (println "    File:" source_file)
              ;; Strip HTML tags from snippet for terminal display
              (let [clean-snippet (-> snippet
                                      (str/replace #"<mark>" "**")
                                      (str/replace #"</mark>" "**"))]
                (println "    " clean-snippet))
              (println))))

        ;; No results
        (println "No results found for:" query)))

    (catch Exception e
      (binding [*out* *err*]
        (println "ERROR:" (.getMessage e))
        (when (.getCause e)
          (println "Caused by:" (.getMessage (.getCause e))))
        (System/exit 1)))))


(defn view-cmd
  "Start interactive viewer"
  [{:keys [database port]}]
  (try
    ;; Validate database exists
    (when-not (.exists (io/file database))
      (binding [*out* *err*]
        (println "ERROR: Database file not found:" database)
        (System/exit 1)))
    
    ;; Create and start viewer system
    (println "\nStarting Polydoc viewer...")
    (println "Database:" database)
    (println "Port:" port)
    (println "\nServer starting at http://localhost:" port)
    (println "Press Ctrl+C to stop\n")
    
    (let [_system (component/start
                   (viewer/viewer-system {:database database
                                         :port port}))]
      ;; Keep the server running
      ;; Block the main thread to prevent exit
      @(promise))
    
    (catch Exception e
      (binding [*out* *err*]
        (println "ERROR:" (.getMessage e))
        (when-let [cause (.getCause e)]
          (println "Caused by:" (.getMessage cause)))
        (.printStackTrace e *err*)
        (System/exit 1)))))


(defn config-status-cmd
  "Display configuration as JSON"
  [{:keys [config]}]
  (try
    (let [config-metadata (metadata/load-metadata config)]
      (println (json/write-str config-metadata))
      0)
    (catch Exception e
      (binding [*out* *err*]
        (println "ERROR:" (.getMessage e))
        (when-let [cause (.getCause e)]
          (println "Caused by:" (.getMessage cause)))
        (System/exit 1)))))


(defn gen-book-cmd
  "Generate a new book project"
  [{:keys [output-dir title author book-id interactive]}]
  (try
    (when interactive
      (println "Interactive mode not yet implemented. Using provided options."))
    
    (let [result (gen-book/generate-book
                  {:output-dir output-dir
                   :title title
                   :author author
                   :book-id book-id})]
      
      (println "\nBook project generated successfully!")
      (println "\nLocation:" (:output-dir result))
      (println "Title:" (:title result))
      (println "Book ID:" (:book-id result))
      (println "\nGenerated files:")
      (doseq [file (:files result)]
        (println "  -" file))
      (println "\nNext steps:")
      (println "  1. Edit polydoc.yml to customize your book")
      (println "  2. Add your content in sections/")
      (println "  3. Build with: clojure -M:main book -c polydoc.yml -o output/")
      0)
    
    (catch Exception e
      (binding [*out* *err*]
        (println "ERROR:" (.getMessage e))
        (when-let [cause (.getCause e)]
          (println "Caused by:" (.getMessage cause)))
        (.printStackTrace e *err*)
        (System/exit 1)))))


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
                       :as "Filter type (clojure-exec, sqlite-exec, plantuml, include)"
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
               :description "Search documentation (FTS5 full-text search)"
               :opts [{:option "database"
                       :short "d"
                       :as "SQLite database file"
                       :type :string
                       :required true}
                      {:option "query"
                       :short "q"
                       :as "Search query (FTS5 syntax: AND, OR, NOT, \"exact phrase\")"
                       :type :string
                       :required true}
                      {:option "limit"
                       :short "l"
                       :as "Maximum number of results to show"
                       :type :int
                       :default 10}
                      {:option "book-id"
                       :short "b"
                       :as "Filter results by book ID"
                       :type :int}]
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
                :runs view-cmd}

               {:command "config"
                :description "Display configuration as JSON"
                :opts [{:option "config"
                        :short "c"
                        :as "Book configuration file (YAML)"
                        :type :string
                        :required true}]
                :runs config-status-cmd}

               {:command "gen"
                :description "Generate new Polydoc projects"
                :subcommands [{:command "book"
                               :description "Generate a new book project"
                               :opts [{:option "output-dir"
                                       :short "o"
                                       :as "Output directory (default: current directory)"
                                       :type :string
                                       :default "."}
                                      {:option "title"
                                       :short "t"
                                       :as "Book title"
                                       :type :string
                                       :default "My Book"}
                                      {:option "author"
                                       :short "a"
                                       :as "Author name"
                                       :type :string}
                                      {:option "book-id"
                                       :short "b"
                                       :as "Book ID (auto-generated from title if not provided)"
                                       :type :string}
                                      {:option "interactive"
                                       :short "i"
                                       :as "Interactive mode"
                                       :type :with-flag
                                       :default false}]
                               :runs gen-book-cmd}]}]})


(defn -main
  [& args]
  (cli/run-cmd args CONFIGURATION))
