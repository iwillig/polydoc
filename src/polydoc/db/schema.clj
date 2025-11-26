(ns polydoc.db.schema
  "Database schema definition and creation for book storage and search."
  (:require
    [honey.sql :as sql]
    [next.jdbc :as jdbc]))


(def ^:const schema-version-number 1)


(def schema-sql
  "SQL statements to create the database schema."
  [;; Schema version tracking
   "CREATE TABLE IF NOT EXISTS schema_version (
      version INTEGER PRIMARY KEY,
      applied_at DATETIME DEFAULT CURRENT_TIMESTAMP
    )"

   ;; Books table - stores book-level metadata
   "CREATE TABLE IF NOT EXISTS books (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      book_id TEXT UNIQUE NOT NULL,
      title TEXT NOT NULL,
      author TEXT,
      description TEXT,
      version TEXT,
      lang TEXT DEFAULT 'en-US',
      metadata_json TEXT,
      output_path TEXT,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
    )"

   "CREATE INDEX IF NOT EXISTS idx_books_book_id ON books(book_id)"

   ;; Sections table - extracted from document headings
   "CREATE TABLE IF NOT EXISTS sections (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      book_id INTEGER NOT NULL,
      section_id TEXT NOT NULL,
      source_file TEXT NOT NULL,
      heading_level INTEGER NOT NULL,
      heading_text TEXT NOT NULL,
      heading_slug TEXT NOT NULL,
      content_markdown TEXT,
      content_html TEXT,
      content_plain TEXT,
      section_order INTEGER NOT NULL,
      parent_section_id TEXT,
      content_hash TEXT NOT NULL,
      metadata_json TEXT,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
    )"

   "CREATE INDEX IF NOT EXISTS idx_sections_book_id ON sections(book_id)"
   "CREATE INDEX IF NOT EXISTS idx_sections_section_id ON sections(section_id)"
   "CREATE INDEX IF NOT EXISTS idx_sections_source_file ON sections(source_file)"
   "CREATE INDEX IF NOT EXISTS idx_sections_content_hash ON sections(content_hash)"
   "CREATE INDEX IF NOT EXISTS idx_sections_order ON sections(book_id, section_order)"

   ;; FTS5 virtual table for full-text search
   "CREATE VIRTUAL TABLE IF NOT EXISTS sections_fts USING fts5(
      section_id UNINDEXED,
      heading_text,
      content_plain,
      source_file UNINDEXED,
      content='sections',
      content_rowid='id'
    )"

   ;; FTS5 triggers to keep search index in sync
   "CREATE TRIGGER IF NOT EXISTS sections_ai AFTER INSERT ON sections BEGIN
      INSERT INTO sections_fts(rowid, section_id, heading_text, content_plain, source_file)
      VALUES (new.id, new.section_id, new.heading_text, new.content_plain, new.source_file);
    END"

   "CREATE TRIGGER IF NOT EXISTS sections_ad AFTER DELETE ON sections BEGIN
      DELETE FROM sections_fts WHERE rowid = old.id;
    END"

   "CREATE TRIGGER IF NOT EXISTS sections_au AFTER UPDATE ON sections BEGIN
      UPDATE sections_fts 
      SET heading_text = new.heading_text,
          content_plain = new.content_plain
      WHERE rowid = new.id;
    END"

   ;; Book files table - track source files for incremental builds
   "CREATE TABLE IF NOT EXISTS book_files (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      book_id INTEGER NOT NULL,
      file_path TEXT NOT NULL,
      file_order INTEGER NOT NULL,
      file_hash TEXT NOT NULL,
      filters_json TEXT,
      last_modified DATETIME,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
      UNIQUE(book_id, file_path)
    )"

   "CREATE INDEX IF NOT EXISTS idx_book_files_book_id ON book_files(book_id)"
   "CREATE INDEX IF NOT EXISTS idx_book_files_hash ON book_files(file_hash)"])


(defn create-schema!
  "Create database schema if it doesn't exist.
   
   Parameters:
   - db: JDBC datasource or connection
   
   Returns: nil
   
   Example:
   (def ds (jdbc/get-datasource {:dbtype \"sqlite\" :dbname \"polydoc.db\"}))
   (create-schema! ds)"
  [db]
  (jdbc/with-transaction [tx db]
                         (doseq [stmt schema-sql]
                           (jdbc/execute! tx [stmt]))

                         ;; Record schema version using HoneySQL
                         (jdbc/execute! tx (sql/format {:insert-into :schema_version
                                                        :values [{:version schema-version-number}]
                                                        :on-conflict {:do-nothing true}}))))


(defn schema-version
  "Get current schema version from database.
   
   Parameters:
   - db: JDBC datasource or connection
   
   Returns: Integer version number or nil if schema doesn't exist
   
   Example:
   (schema-version ds)
   ;; => 1"
  [db]
  (try
    (:schema_version/version
      (jdbc/execute-one! db (sql/format {:select [:version]
                                         :from [:schema_version]
                                         :order-by [[:version :desc]]
                                         :limit 1})))
    (catch Exception _
      nil)))


(defn schema-exists?
  "Check if schema has been created.
   
   Parameters:
   - db: JDBC datasource or connection
   
   Returns: Boolean
   
   Example:
   (schema-exists? ds)
   ;; => true"
  [db]
  (some? (schema-version db)))


(defn drop-schema!
  "Drop all tables (WARNING: destroys all data).
   
   Parameters:
   - db: JDBC datasource or connection
   
   Returns: nil"
  [db]
  (jdbc/with-transaction [tx db]
                         (jdbc/execute! tx ["DROP TABLE IF EXISTS book_files"])
                         (jdbc/execute! tx ["DROP TRIGGER IF EXISTS sections_au"])
                         (jdbc/execute! tx ["DROP TRIGGER IF EXISTS sections_ad"])
                         (jdbc/execute! tx ["DROP TRIGGER IF EXISTS sections_ai"])
                         (jdbc/execute! tx ["DROP TABLE IF EXISTS sections_fts"])
                         (jdbc/execute! tx ["DROP TABLE IF EXISTS sections"])
                         (jdbc/execute! tx ["DROP TABLE IF EXISTS books"])
                         (jdbc/execute! tx ["DROP TABLE IF EXISTS schema_version"])))
