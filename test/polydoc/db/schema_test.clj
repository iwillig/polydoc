(ns polydoc.db.schema-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [polydoc.db.schema :as schema]
            [polydoc.test-helpers :as helpers]
            [next.jdbc :as jdbc]
            [honey.sql :as sql]))

;; Use test fixture for tests that don't need custom setup
(use-fixtures :each helpers/use-sqlite-database)

(deftest test-create-schema
  (testing "Schema creation with single connection"
    ;; This test uses its own connection to test fresh schema creation
    (with-open [conn (jdbc/get-connection {:dbtype "sqlite" :dbname ":memory:"})]
      (schema/create-schema! conn)
      
      (is (schema/schema-exists? conn)
          "Schema should exist after creation")
      
      (is (= "001-initial-schema" (schema/schema-version conn))
          "Schema version should be 001-initial-schema"))))

(deftest test-tables-exist
  (testing "Tables exist after creation using fixture"
    ;; This test uses the fixture-provided connection
    (let [tables (jdbc/execute! helpers/*connection*
                                (sql/format {:select [:name]
                                            :from [:sqlite_master]
                                            :where [:= :type "table"]}))
          table-names (set (map :sqlite_master/name tables))]
      
      (is (contains? table-names "books")
          "books table should exist")
      (is (contains? table-names "sections")
          "sections table should exist")
      (is (contains? table-names "book_files")
          "book_files table should exist")
      (is (contains? table-names "sections_fts")
          "sections_fts FTS5 table should exist")
      (is (contains? table-names "ragtime_migrations")
          "ragtime_migrations table should exist"))))

(deftest test-schema-idempotent
  (testing "Creating schema multiple times is safe"
    (with-open [conn (jdbc/get-connection {:dbtype "sqlite" :dbname ":memory:"})]
      (schema/create-schema! conn)
      (schema/create-schema! conn)  ; Should not error
      
      (is (= "001-initial-schema" (schema/schema-version conn))
          "Schema version should still be 001-initial-schema"))))

(deftest test-drop-schema
  (testing "Drop schema removes all tables"
    (with-open [conn (jdbc/get-connection {:dbtype "sqlite" :dbname ":memory:"})]
      (schema/create-schema! conn)
      (is (schema/schema-exists? conn))
      
      (schema/drop-schema! conn)
      (is (not (schema/schema-exists? conn))
          "Schema should not exist after drop"))))
