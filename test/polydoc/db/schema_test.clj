(ns polydoc.db.schema-test
  (:require [clojure.test :refer [deftest is testing]]
            [polydoc.db.schema :as schema]
            [next.jdbc :as jdbc]
            [honey.sql :as sql]))

(deftest test-create-schema
  (let [db-spec {:dbtype "sqlite" :dbname ":memory:"}]
    
    (testing "Schema creation with single connection"
      (with-open [conn (jdbc/get-connection db-spec)]
        (schema/create-schema! conn)
        
        (is (schema/schema-exists? conn)
            "Schema should exist after creation")
        
        (is (= 1 (schema/schema-version conn))
            "Schema version should be 1")))
    
    (testing "Tables exist after creation"
      (with-open [conn (jdbc/get-connection db-spec)]
        (schema/create-schema! conn)
        
        (let [tables (jdbc/execute! conn 
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
          (is (contains? table-names "schema_version")
              "schema_version table should exist"))))))

(deftest test-schema-idempotent
  (testing "Creating schema multiple times is safe"
    (with-open [conn (jdbc/get-connection {:dbtype "sqlite" :dbname ":memory:"})]
      (schema/create-schema! conn)
      (schema/create-schema! conn)  ; Should not error
      
      (is (= 1 (schema/schema-version conn))
          "Schema version should still be 1"))))

(deftest test-drop-schema
  (testing "Drop schema removes all tables"
    (with-open [conn (jdbc/get-connection {:dbtype "sqlite" :dbname ":memory:"})]
      (schema/create-schema! conn)
      (is (schema/schema-exists? conn))
      
      (schema/drop-schema! conn)
      (is (not (schema/schema-exists? conn))
          "Schema should not exist after drop"))))
