(ns polydoc.test-helpers
  (:require [next.jdbc :as jdbc]
            [ragtime.next-jdbc :as ragtime-jdbc]
            [ragtime.repl :as ragtime-repl]
            [ragtime.reporter]
            [polydoc.db.schema :as schema]
            [ragtime.strategy]
            [clojure.test :refer [use-fixtures]])
  (:import (org.sqlite.core DB)
           (org.sqlite SQLiteConnection)))


(def ^:dynamic *db* nil)
(def ^:dynamic *connection* nil)


(defn- migration-config
  [^SQLiteConnection connection]
  {:datastore  (ragtime-jdbc/sql-database connection)
   :migrations (ragtime-jdbc/load-resources "migrations")
   :reporter   ragtime.reporter/silent
   :strategy   ragtime.strategy/apply-new})

(defn- memory-sqlite-database
  []
  (next.jdbc/get-connection {:connection-uri "jdbc:sqlite::memory:"}))

(defn use-sqlite-database
  "Test fixture that provides an in-memory SQLite database with schema.

   Binds dynamic vars:
   - *connection* - JDBC connection
   - *db* - SQLite DB object (for FTS5 extension)

   The database includes:
   - Full schema (books, sections, sections_fts, book_files)
   - FTS5 extension enabled
   - Foreign key support enabled"
  [fn]
  (let [conn     (memory-sqlite-database)
        database (.getDatabase ^SQLiteConnection conn)
        _        (.enable_load_extension ^DB database true)
        ;; Enable foreign keys for CASCADE delete support
        _        (jdbc/execute! conn ["PRAGMA foreign_keys = ON"])
        config   (migration-config conn)]

    (try
      (binding [*connection* conn
                *db*         database]
        ;; Create schema
        (ragtime-repl/migrate config)
        ;; Run test
        (fn))
      (finally
        ;; Clean up - schema is automatically dropped with :memory: DB
        (doseq [_ @ragtime-repl/migration-index]
          (ragtime-repl/rollback config))
        (.close conn)))))


(comment

  ;; Use the shared test fixture
  (use-fixtures :each use-sqlite-database)


  )
