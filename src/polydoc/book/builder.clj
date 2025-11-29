(ns polydoc.book.builder
  "Book builder orchestration.
  
  Coordinates the complete book building process:
  1. Load metadata from polydoc.yml
  2. Initialize database
  3. Process section files (apply filters)
  4. Extract and index sections
  5. Generate output
  
  Example usage:
  
    (require '[polydoc.book.builder :as builder])
    
    ;; Build a book from polydoc.yml
    (builder/build-book \"polydoc.yml\" \"output-dir\")
    
    ;; Or with more control
    (let [metadata (metadata/load-metadata \"polydoc.yml\")
          db (builder/initialize-database metadata)]
      (builder/process-sections metadata db)
      (builder/generate-output metadata db \"output-dir\"))
  
  See also:
  - polydoc.book.metadata for configuration loading
  - polydoc.db.schema for database structure
  - polydoc.filters.core for filter execution"
  (:require
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]
   [clojure.string :as str]
   [honey.sql :as sql]
   [next.jdbc :as jdbc]
   [polydoc.book.metadata :as metadata]
   [polydoc.db.schema :as schema]
   [polydoc.filters.clojure-exec :as clj-exec]
   [polydoc.filters.include :as include]
   [polydoc.filters.plantuml :as plantuml]
   [polydoc.filters.sqlite-exec :as sqlite-exec]))


;; Database Operations

(defn initialize-database
  "Initialize database for a book.
  
  Creates schema if needed and returns datasource.
  Uses database path from metadata."
  [metadata]
  (let [db-path (metadata/get-database-path metadata)
        db-spec {:dbtype "sqlite" :dbname db-path}
        ds (jdbc/get-datasource db-spec)]
    ;; Create schema if it doesn't exist
    (when-not (schema/schema-exists? ds)
      (schema/create-schema! ds))
    ds))


(defn insert-book
  "Insert book metadata into database.
  
  Returns the book ID."
  [ds metadata]
  (let [book-id (metadata/get-book-id metadata)
        title (:title metadata)
        author (or (:author metadata) "Unknown")
        version (or (:version metadata) "1.0.0")]
    ;; Use INSERT OR REPLACE to handle re-builds
    (jdbc/execute! ds
                   (sql/format
                    {:insert-into :books
                     :values [{:book_id book-id
                               :title title
                               :author author
                               :version version}]
                     :on-conflict :book_id
                     :do-update-set {:title title
                                     :author author
                                     :version version
                                     :updated_at [:datetime "now"]}}))
    book-id))


;; Filter Execution

(def filter-registry
  "Map of filter names to filter functions."
  {"clojure-exec" clj-exec/clojure-exec-filter
   "sqlite-exec" sqlite-exec/sqlite-exec-filter
   "sqlite" sqlite-exec/sqlite-exec-filter
   "plantuml" plantuml/plantuml-filter
   "include" include/include-filter})


(defn get-filter-fn
  "Get filter function by name.
  
  Returns nil if filter not found."
  [filter-name]
  (get filter-registry filter-name))


(defn apply-filters
  "Apply configured filters to AST.
  
  Takes metadata and AST, returns filtered AST."
  [metadata ast]
  (let [filter-names (metadata/get-filters metadata)
        filter-fns (keep get-filter-fn filter-names)]
    (if (empty? filter-fns)
      ast
      (reduce (fn [acc f] (f acc)) ast filter-fns))))


;; Pandoc Operations

(defn markdown-to-ast
  "Convert markdown file to Pandoc AST.
  
  Uses pandoc CLI to generate JSON AST."
  [file-path]
  (let [result (shell/sh "pandoc" "-t" "json" (str file-path))]
    (if (zero? (:exit result))
      (json/read-str (:out result) :key-fn keyword)
      (throw (ex-info "Failed to convert markdown to AST"
                      {:file file-path
                       :exit (:exit result)
                       :error (:err result)})))))


(defn ast-to-html
  "Convert Pandoc AST to HTML.
  
  Uses pandoc CLI to generate HTML."
  [ast output-path]
  (let [json-str (json/write-str ast)
        result (shell/sh "pandoc" "-f" "json" "-t" "html" "-o" output-path
                         :in json-str)]
    (when-not (zero? (:exit result))
      (throw (ex-info "Failed to convert AST to HTML"
                      {:output output-path
                       :exit (:exit result)
                       :error (:err result)})))))


(defn ast-to-html-string
  "Convert Pandoc AST to HTML string.
  
  Returns HTML as a string rather than writing to file."
  [ast]
  (let [json-str (json/write-str ast)
        result (shell/sh "pandoc" "-f" "json" "-t" "html"
                         :in json-str)]
    (if (zero? (:exit result))
      (:out result)
      (throw (ex-info "Failed to convert AST to HTML"
                      {:exit (:exit result)
                       :error (:err result)})))))


(defn ast-to-plain-text
  "Convert Pandoc AST to plain text.
  
  Strips formatting for search indexing."
  [ast]
  (let [json-str (json/write-str ast)
        result (shell/sh "pandoc" "-f" "json" "-t" "plain"
                         :in json-str)]
    (if (zero? (:exit result))
      (str/trim (:out result))
      (throw (ex-info "Failed to convert AST to plain text"
                      {:exit (:exit result)
                       :error (:err result)})))))


;; Section Extraction (Placeholder - Task 235)

(defn slugify
  "Convert text to URL-safe slug.
   
   Converts to lowercase, replaces spaces with hyphens, removes special chars."
  [text]
  (-> text
      str/lower-case
      (str/replace #"[^\w\s-]" "")
      (str/replace #"\s+" "-")
      (str/replace #"-+" "-")
      str/trim))


(defn generate-section-id
  "Generate unique section ID from file path and order.
   
   Format: filename-N where N is the section order."
  [file-path order]
  (let [filename (.getName (io/file file-path))
        base-name (first (str/split filename #"\."))]
    (str base-name "-" order)))


(defn extract-sections
  "Extract sections from AST for indexing.
   
   TODO: This is a placeholder. Full implementation in Task 235.
   
   For now, just creates one section per file with the entire content.
   Returns data in intermediate format (not database schema)."
  [ast file-path]
  [{:title (str "Content from " (.getName (io/file file-path)))
    :level 1
    :content (json/write-str ast)
    :ast ast  ;; Include AST for HTML/plain text generation
    :hash (str (hash ast))}])


(defn insert-section
  "Insert a section into database.
   
   Maps section data from intermediate format to database schema columns.
   Generates HTML and plain text from AST for display and search.
   
   Intermediate format (from extract-sections):
   {:title, :level, :content, :hash, :ast}
   
   Database schema:
   {:section_id, :source_file, :heading_level, :heading_text, :heading_slug,
    :content_markdown, :content_html, :content_plain, :section_order, :content_hash}"
  [ds book-id section-data file-path section-order]
  (let [section-id (generate-section-id file-path section-order)
        heading-text (:title section-data)
        heading-slug (slugify heading-text)
        ;; Generate HTML and plain text from AST
        section-ast (:ast section-data)
        content-html (when section-ast (ast-to-html-string section-ast))
        content-plain (when section-ast (ast-to-plain-text section-ast))]
    (jdbc/execute! ds
                   (sql/format
                    {:insert-into :sections
                     :values [{:book_id book-id
                               :section_id section-id
                               :source_file (str file-path)
                               :heading_level (:level section-data)
                               :heading_text heading-text
                               :heading_slug heading-slug
                               :content_markdown (:content section-data)
                               :content_html content-html
                               :content_plain content-plain
                               :section_order section-order
                               :parent_section_id nil  ;; Placeholder
                               :content_hash (:hash section-data)
                               :metadata_json nil}]}))))


;; File Processing

(defn process-file
  "Process a single section file.
  
  Steps:
  1. Convert markdown to AST
  2. Apply configured filters
  3. Extract sections
  4. Insert sections into database
  
  Returns the processed AST."
  [metadata ds book-id section-info]
  (let [file-path (:file section-info)
        _ (println "Processing:" file-path)

        ;; Step 1: Convert to AST
        ast (markdown-to-ast file-path)

        ;; Step 2: Apply filters
        filtered-ast (apply-filters metadata ast)

        ;; Step 3: Extract sections
        sections (extract-sections filtered-ast file-path)

        ;; Step 4: Insert into database with section order
        _ (doseq [[idx section] (map-indexed vector sections)]
            (insert-section ds book-id section file-path idx))]

    filtered-ast))


(defn process-sections
  "Process all section files.
  
  Processes each file in metadata :sections, applying filters
  and inserting into database.
  
  Returns sequence of processed ASTs."
  [metadata ds]
  (let [book-id (metadata/get-book-id metadata)
        sections (:sections metadata)]
    (println "Processing" (count sections) "section files...")
    (doall
     (map #(process-file metadata ds book-id %) sections))))


;; Output Generation

(defn combine-asts
  "Combine multiple ASTs into a single document AST.
  
  Takes a sequence of ASTs and combines their content into
  one unified document."
  [asts]
  (let [api-version (or (:pandoc-api-version (first asts))
                        [1 23 1])
        blocks (mapcat #(get-in % [:blocks]) asts)
        meta (or (:meta (first asts)) {})]
    {:pandoc-api-version api-version
     :meta meta
     :blocks (vec blocks)}))


(defn generate-html-output
  "Generate HTML output file.
  
  Combines all processed ASTs and converts to HTML."
  [asts output-dir book-id]
  (let [combined-ast (combine-asts asts)
        output-file (io/file output-dir (str book-id ".html"))]
    (io/make-parents output-file)
    (println "Generating HTML:" (.getPath output-file))
    (ast-to-html combined-ast (.getPath output-file))
    output-file))


(defn generate-output
  "Generate output files from processed sections.
  
  Creates:
  - HTML output file
  - (Future: PDF, ePub, etc.)
  
  Returns map of output files."
  [metadata asts output-dir]
  (let [book-id (metadata/get-book-id metadata)
        output-dir-file (io/file output-dir)]
    (.mkdirs output-dir-file)
    {:html (generate-html-output asts output-dir book-id)}))


;; Main Build Function

(defn build-book
  "Build a complete book from configuration.
  
  Main entry point for book building. Takes a polydoc.yml path
  and output directory.
  
  Steps:
  1. Load metadata
  2. Initialize database
  3. Insert book record
  4. Process all section files
  5. Generate output
  
  Returns map with:
  - :metadata - Loaded configuration
  - :database - Database path
  - :outputs - Map of generated output files
  
  Example:
  
    (build-book \"polydoc.yml\" \"output\")
    => {:metadata {...}
        :database \"polydoc.db\"
        :outputs {:html #object[java.io.File \"output/my-book.html\"]}}}"
  [config-path output-dir]
  (println "Building book from:" config-path)

  ;; Step 1: Load metadata
  (let [metadata (metadata/load-metadata config-path)
        _ (println "Loaded metadata for book:" (metadata/get-book-id metadata))

        ;; Step 2: Initialize database
        ds (initialize-database metadata)
        db-path (metadata/get-database-path metadata)
        _ (println "Database initialized:" db-path)

        ;; Step 3: Insert book record
        book-id (insert-book ds metadata)
        _ (println "Book record created:" book-id)

        ;; Step 4: Process sections
        processed-asts (process-sections metadata ds)
        _ (println "Processed" (count processed-asts) "sections")

        ;; Step 5: Generate output
        outputs (generate-output metadata processed-asts output-dir)
        _ (println "Generated output files:" (keys outputs))]

    {:metadata metadata
     :database db-path
     :outputs outputs}))
