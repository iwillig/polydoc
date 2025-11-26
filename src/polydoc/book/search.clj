(ns polydoc.book.search
  "Full-text search functionality using SQLite FTS5."
  (:require
    [honey.sql :as sql]
    [next.jdbc :as jdbc]
    [clojure.string :as str]))


(defn search
  "Search book sections using FTS5 full-text search.
   
   Parameters:
   - db: JDBC datasource or connection
   - query: Search query string (FTS5 syntax)
   - options: Map of search options
     - :limit - Maximum results (default: 10)
     - :book-id - Filter by book ID (optional)
     - :highlight-start - Start marker for matches (default: \"<mark>\")
     - :highlight-end - End marker for matches (default: \"</mark>\")
     - :snippet-tokens - Tokens around match in snippet (default: 20)
   
   Returns: Vector of result maps with keys:
     - :rowid - Section ID in database
     - :section-id - Section identifier
     - :heading-text - Section heading
     - :heading-level - Heading level (1-6)
     - :source-file - Source file path
     - :snippet - Text snippet with highlighted matches
     - :rank - Search relevance rank
   
   Examples:
   (search ds \"clojure\")
   (search ds \"clojure AND pandoc\" {:limit 20})
   (search ds \"\\\"exact phrase\\\"\" {:book-id 1})"
  ([db query]
   (search db query {}))
  ([db query {:keys [limit book-id highlight-start highlight-end snippet-tokens]
              :or {limit 10
                   highlight-start "<mark>"
                   highlight-end "</mark>"
                   snippet-tokens 20}}]
   (when (and query (not (str/blank? query)))
     (let [;; Base FTS5 search query
           search-sql (str "SELECT 
                             fts.rowid as rowid,
                             s.section_id,
                             s.heading_text,
                             s.heading_level,
                             s.source_file,
                             snippet(sections_fts, 1, ?, ?, '...', ?) as snippet,
                             rank as rank
                           FROM sections_fts fts
                           JOIN sections s ON fts.rowid = s.id
                           WHERE sections_fts MATCH ?")
           ;; Add book filter if specified
           sql-with-book (if book-id
                           (str search-sql " AND s.book_id = ?")
                           search-sql)
           ;; Add ordering and limit
           final-sql (str sql-with-book " ORDER BY rank LIMIT ?")
           ;; Build params
           params (if book-id
                    [highlight-start highlight-end snippet-tokens query book-id limit]
                    [highlight-start highlight-end snippet-tokens query limit])]
       (jdbc/execute! db (into [final-sql] params))))))


(defn search-with-context
  "Search with additional context from sections table.
   
   Like `search` but includes full section content and metadata.
   
   Parameters:
   - db: JDBC datasource
   - query: Search query string
   - options: Same as `search` plus:
     - :include-content - Include full content (default: false)
     - :include-metadata - Include metadata JSON (default: false)
   
   Returns: Vector of result maps with additional fields:
     - :content-markdown - Full markdown content (if :include-content)
     - :content-html - Full HTML content (if :include-content)
     - :metadata-json - Section metadata (if :include-metadata)
   
   Example:
   (search-with-context ds \"clojure\" {:include-content true :limit 5})"
  ([db query]
   (search-with-context db query {}))
  ([db query options]
   (let [{:keys [include-content include-metadata]} options
         base-results (search db query options)]
     (if (or include-content include-metadata)
       ;; Fetch additional data for each result
       (mapv (fn [result]
               (let [section-data (jdbc/execute-one! 
                                    db
                                    (sql/format {:select (cond-> [:section_id]
                                                           include-content (into [:content_markdown :content_html])
                                                           include-metadata (conj :metadata_json))
                                                 :from [:sections]
                                                 :where [:= :id (:sections_fts/rowid result)]}))]
                 (merge result section-data)))
             base-results)
       base-results))))


(defn suggest-queries
  "Generate query suggestions based on partial input.
   
   Uses the FTS5 index to find common terms that match the prefix.
   
   Parameters:
   - db: JDBC datasource
   - prefix: Partial query string
   - options: Map of options
     - :limit - Maximum suggestions (default: 5)
   
   Returns: Vector of suggestion strings
   
   Example:
   (suggest-queries ds \"clo\")
   ;; => [\"clojure\" \"clojurescript\" \"clone\"]"
  ([db prefix]
   (suggest-queries db prefix {}))
  ([db prefix {:keys [limit] :or {limit 5}}]
   (when (and prefix (not (str/blank? prefix)))
     (let [;; Search for prefix* to find matching terms
           query (str prefix "*")
           results (search db query {:limit limit})]
       ;; Extract unique terms from headings
       (->> results
            (mapcat #(-> % :sections/heading_text str/lower-case (str/split #"\s+")))
            (filter #(str/starts-with? % (str/lower-case prefix)))
            distinct
            (take limit)
            vec)))))


(defn count-results
  "Count total search results for a query.
   
   Parameters:
   - db: JDBC datasource
   - query: Search query string
   - options: Map with optional :book-id filter
   
   Returns: Integer count of matching sections
   
   Example:
   (count-results ds \"clojure\")
   ;; => 42"
  ([db query]
   (count-results db query {}))
  ([db query {:keys [book-id]}]
   (when (and query (not (str/blank? query)))
     (let [sql (if book-id
                 "SELECT COUNT(*) as cnt 
                  FROM sections_fts fts
                  JOIN sections s ON fts.rowid = s.id
                  WHERE sections_fts MATCH ? AND s.book_id = ?"
                 "SELECT COUNT(*) as cnt 
                  FROM sections_fts 
                  WHERE sections_fts MATCH ?")
           params (if book-id [query book-id] [query])
           result (jdbc/execute-one! db (into [sql] params))]
       (:cnt result 0)))))


(defn search-by-section-level
  "Search within specific heading levels.
   
   Parameters:
   - db: JDBC datasource
   - query: Search query string
   - levels: Vector of heading levels (1-6) to search
   - options: Same as `search`
   
   Returns: Vector of result maps
   
   Example:
   (search-by-section-level ds \"overview\" [1 2])  ; Only top-level sections"
  ([db query levels]
   (search-by-section-level db query levels {}))
  ([db query levels options]
   (when (and query (not (str/blank? query)) (seq levels))
     (let [{:keys [limit book-id highlight-start highlight-end snippet-tokens]
            :or {limit 10
                 highlight-start "<mark>"
                 highlight-end "</mark>"
                 snippet-tokens 20}} options
           placeholders (str/join "," (repeat (count levels) "?"))
           sql (str "SELECT 
                       fts.rowid as rowid,
                       s.section_id,
                       s.heading_text,
                       s.heading_level,
                       s.source_file,
                       snippet(sections_fts, 1, ?, ?, '...', ?) as snippet,
                       rank as rank
                     FROM sections_fts fts
                     JOIN sections s ON fts.rowid = s.id
                     WHERE sections_fts MATCH ? 
                       AND s.heading_level IN (" placeholders ")")
           sql-with-book (if book-id
                           (str sql " AND s.book_id = ?")
                           sql)
           final-sql (str sql-with-book " ORDER BY rank LIMIT ?")
           params (cond-> [highlight-start highlight-end snippet-tokens query]
                    true (into levels)
                    book-id (conj book-id)
                    true (conj limit))]
       (jdbc/execute! db (into [final-sql] params))))))


(comment
  ;; Usage examples
  ;; (require '[next.jdbc :as jdbc]) - already required at top
  
  (def ds (jdbc/get-datasource {:dbtype "sqlite" :dbname "polydoc.db"}))
  
  ;; Basic search
  (search ds "clojure")
  
  ;; Search with options
  (search ds "clojure AND pandoc" {:limit 20})
  
  ;; Search with exact phrase
  (search ds "\"exact phrase\"")
  
  ;; Search with book filter
  (search ds "clojure" {:book-id 1 :limit 5})
  
  ;; Search with context
  (search-with-context ds "clojure" {:include-content true})
  
  ;; Count results
  (count-results ds "clojure")
  
  ;; Search by heading level
  (search-by-section-level ds "overview" [1 2] {:limit 10})
  
  ;; Query suggestions
  (suggest-queries ds "clo"))
