(ns polydoc.db.schema
  "Database schema management using Ragtime migrations."
  (:require
    [clojure.java.io :as io]
    [clojure.edn :as edn]
    [next.jdbc :as jdbc]
    [ragtime.next-jdbc :as ragtime-jdbc]
    [ragtime.repl :as ragtime-repl]))


(defn load-edn-migrations
  "Load EDN migration files from resources/migrations directory.
   
   Returns: Vector of migration maps with :id, :up, and :down keys"
  []
  (let [migrations-dir (io/resource "migrations")
        migration-files (->> migrations-dir
                             io/file
                             file-seq
                             (filter #(.isFile %))
                             (filter #(.endsWith (.getName %) ".edn"))
                             sort)]
    (mapv (fn [file]
            (-> file slurp edn/read-string))
          migration-files)))


(defn migration->ragtime
  "Convert EDN migration to Ragtime migration format.
   
   Parameters:
   - migration: Map with :id, :up, and :down keys
   
   Returns: Ragtime migration record"
  [migration]
  (ragtime-jdbc/sql-migration migration))


(defn migrations
  "Get all Ragtime migrations from EDN files.
   
   Returns: Vector of Ragtime migration records"
  []
  (mapv migration->ragtime (load-edn-migrations)))


(defn ragtime-config
  "Create Ragtime configuration for the given database.
   
   Parameters:
   - db: JDBC datasource or db-spec map
   
   Returns: Ragtime configuration map"
  [db]
  (let [;; If db is a map, convert it to a datasource
        datasource (if (map? db)
                     (jdbc/get-datasource db)
                     db)]
    {:datastore (ragtime-jdbc/sql-database datasource)
     :migrations (migrations)}))


(defn migrate!
  "Run all pending migrations.
   
   Parameters:
   - db: JDBC datasource, connection, or db-spec map
   
   Returns: nil
   
   Example:
   (migrate! {:connection-uri \"jdbc:sqlite:polydoc.db\"})"
  [db]
  (ragtime-repl/migrate (ragtime-config db)))


(defn rollback!
  "Rollback the last applied migration.
   
   Parameters:
   - db: JDBC datasource or connection spec
   - amount: Number of migrations to rollback (default 1)
   
   Returns: nil
   
   Example:
   (rollback! ds)     ; Rollback 1 migration
   (rollback! ds 2)   ; Rollback 2 migrations"
  ([db]
   (rollback! db 1))
  ([db amount]
   (ragtime-repl/rollback (ragtime-config db) amount)))


(defn create-schema!
  "Create database schema by running all migrations.
   
   This is a convenience function that calls migrate!
   
   Parameters:
   - db: JDBC datasource or connection spec
   
   Returns: nil
   
   Example:
   (def ds (jdbc/get-datasource {:dbtype \"sqlite\" :dbname \"polydoc.db\"}))
   (create-schema! ds)"
  [db]
  (migrate! db))


(defn schema-version
  "Get current schema version from Ragtime migration table.
   
   Parameters:
   - db: JDBC datasource or connection
   
   Returns: String migration ID of last applied migration, or nil if no migrations
   
   Example:
   (schema-version ds)
   ;; => \"001-initial-schema\""
  [db]
  (try
    (let [result (jdbc/execute-one! 
                   db 
                   ["SELECT id FROM ragtime_migrations ORDER BY created_at DESC LIMIT 1"])]
      (:ragtime_migrations/id result))
    (catch Exception _
      nil)))


(defn schema-exists?
  "Check if schema has been created (any migrations applied).
   
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
   
   Rolls back all migrations to completely remove the schema.
   
   Parameters:
   - db: JDBC datasource or connection
   
   Returns: nil"
  [db]
  (try
    ;; Count how many migrations are applied
    (let [count-result (jdbc/execute-one! 
                         db 
                         ["SELECT COUNT(*) as count FROM ragtime_migrations"])
          migration-count (:count count-result 0)]
      (when (pos? migration-count)
        (rollback! db migration-count)))
    (catch Exception _
      ;; If ragtime_migrations doesn't exist, nothing to rollback
      nil)))
