(ns polydoc.book.builder-test
  "Tests for book builder orchestration."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.java.io :as io]
            [polydoc.book.builder :as builder]
            [polydoc.book.metadata :as metadata]
            [polydoc.db.schema :as schema]
            [next.jdbc :as jdbc]
            [honey.sql :as sql]))

;; Test Fixtures

(defn test-metadata-content
  "Generate test metadata with absolute paths."
  []
  (let [temp-dir (io/file (System/getProperty "java.io.tmpdir") "polydoc-test")
        db-path (str (io/file temp-dir "test-polydoc.db"))
        output-path (str (io/file temp-dir "test-output"))]
    (str "title: Test Book\n"
         "author: Test Author\n"
         "book:\n"
         "  id: test-book\n"
         "  version: 1.0.0\n"
         "  database: " db-path "\n"
         "  output-dir: " output-path "\n"
         "  filters:\n"
         "    - clojure-exec\n"
         "    - plantuml\n"
         "sections:\n"
         "  - intro.md\n"
         "  - chapter1.md")))

(def test-intro-content
  "# Introduction

This is the introduction to the test book.

```{.clojure-exec}
(+ 1 2 3)
```")

(def test-chapter1-content
  "# Chapter 1

This is chapter 1 content.

## Section 1.1

Some content here.")

(defn create-test-files!
  "Create test files in temporary directory."
  []
  (let [temp-dir (io/file (System/getProperty "java.io.tmpdir") "polydoc-test")
        config-file (io/file temp-dir "polydoc.yml")
        intro-file (io/file temp-dir "intro.md")
        chapter-file (io/file temp-dir "chapter1.md")]
    (.mkdirs temp-dir)
    (spit config-file (test-metadata-content))
    (spit intro-file test-intro-content)
    (spit chapter-file test-chapter1-content)
    {:dir temp-dir
     :config config-file
     :intro intro-file
     :chapter chapter-file}))

(defn cleanup-test-files!
  "Clean up test files and database."
  [test-files]
  (let [temp-dir (:dir test-files)
        db-file (io/file temp-dir "test-polydoc.db")
        output-dir (io/file temp-dir "test-output")]
    ;; Delete database
    (when (.exists db-file)
      (.delete db-file))
    ;; Delete output directory
    (when (.exists output-dir)
      (doseq [file (file-seq output-dir)]
        (when (.isFile file)
          (.delete file)))
      (.delete output-dir))
    ;; Delete test files
    (doseq [file [(:config test-files)
                  (:intro test-files)
                  (:chapter test-files)]]
      (when (.exists file)
        (.delete file)))
    ;; Delete temp directory
    (when (.exists temp-dir)
      (.delete temp-dir))))

(defn with-test-files
  "Fixture to create and cleanup test files."
  [f]
  (let [test-files (create-test-files!)]
    (try
      (f)
      (finally
        (cleanup-test-files! test-files)))))

(use-fixtures :each with-test-files)

;; Helper Functions

(defn get-test-config-path
  "Get path to test configuration file."
  []
  (str (io/file (System/getProperty "java.io.tmpdir")
                "polydoc-test"
                "polydoc.yml")))

(defn get-test-output-dir
  "Get path to test output directory."
  []
  (str (io/file (System/getProperty "java.io.tmpdir")
                "polydoc-test"
                "test-output")))

(defn get-test-db-path
  "Get path to test database."
  []
  (str (io/file (System/getProperty "java.io.tmpdir")
                "polydoc-test"
                "test-polydoc.db")))

;; Database Tests

(deftest test-initialize-database
  (testing "Database initialization"
    (let [metadata (metadata/load-metadata (get-test-config-path))
          ds (builder/initialize-database metadata)]
      
      (testing "creates database file"
        (is (.exists (io/file (get-test-db-path)))))
      
      (testing "creates schema"
        (is (schema/schema-exists? ds)))
      
      (testing "schema has correct tables"
        (let [tables (jdbc/execute! ds
                       ["SELECT name FROM sqlite_master WHERE type='table'"])]
          (is (some #(= (:sqlite_master/name %) "books") tables))
          (is (some #(= (:sqlite_master/name %) "sections") tables))
          (is (some #(= (:sqlite_master/name %) "book_files") tables)))))))

(deftest test-insert-book
  (testing "Book record insertion"
    (let [metadata (metadata/load-metadata (get-test-config-path))
          ds (builder/initialize-database metadata)
          book-id (builder/insert-book ds metadata)]
      
      (testing "returns book ID"
        (is (= book-id "test-book")))
      
      (testing "inserts book record"
        (let [books (jdbc/execute! ds
                      (sql/format {:select [:*] :from [:books]}))]
          (is (= 1 (count books)))
          (is (= "test-book" (:books/book_id (first books))))
          (is (= "Test Book" (:books/title (first books))))
          (is (= "Test Author" (:books/author (first books))))
          (is (= "1.0.0" (:books/version (first books))))))
      
      (testing "handles re-insertion (upsert)"
        ;; Update title while keeping the same book-id by explicitly setting it
        (let [updated-metadata (-> metadata
                                   (assoc :title "Updated Title")
                                   (assoc-in [:book :id] "test-book"))
              _ (builder/insert-book ds updated-metadata)
              books (jdbc/execute! ds
                      (sql/format {:select [:*] :from [:books]}))]
          (is (= 1 (count books)))
          (is (= "Updated Title" (:books/title (first books)))))))))

;; Filter Tests

(deftest test-filter-registry
  (testing "Filter registry"
    (testing "contains expected filters"
      (is (contains? builder/filter-registry "clojure-exec"))
      (is (contains? builder/filter-registry "sqlite-exec"))
      (is (contains? builder/filter-registry "javascript-exec"))
      (is (contains? builder/filter-registry "plantuml"))
      (is (contains? builder/filter-registry "include")))
    
    (testing "filter functions are callable"
      (let [clj-filter (builder/get-filter-fn "clojure-exec")]
        (is (fn? clj-filter))))))

(deftest test-get-filter-fn
  (testing "Getting filter functions"
    (testing "returns function for valid filter"
      (is (fn? (builder/get-filter-fn "clojure-exec"))))
    
    (testing "returns nil for invalid filter"
      (is (nil? (builder/get-filter-fn "nonexistent-filter"))))))

;; Section Extraction Tests

(deftest test-extract-sections
  (testing "Section extraction"
    (let [sample-ast {:pandoc-api-version [1 23 1]
                     :meta {}
                     :blocks [{:t "Header"
                              :c [1 ["intro" [] []]
                                  [{:t "Str" :c "Introduction"}]]}
                             {:t "Para"
                              :c [{:t "Str" :c "Test content"}]}]}
          sections (builder/extract-sections sample-ast "test.md")]
      
      (testing "returns vector of sections"
        (is (vector? sections))
        (is (pos? (count sections))))
      
      (testing "sections have required keys"
        (let [section (first sections)]
          (is (contains? section :title))
          (is (contains? section :level))
          (is (contains? section :content))
          (is (contains? section :hash)))))))

;; Pandoc Operations Tests

(deftest test-markdown-to-ast
  (testing "Markdown to AST conversion"
    (let [intro-path (str (io/file (System/getProperty "java.io.tmpdir")
                                   "polydoc-test"
                                   "intro.md"))
          ast (builder/markdown-to-ast intro-path)]
      
      (testing "returns AST map"
        (is (map? ast)))
      
      (testing "has pandoc-api-version"
        (is (contains? ast :pandoc-api-version)))
      
      (testing "has blocks"
        (is (contains? ast :blocks))
        (is (vector? (:blocks ast)))
        (is (pos? (count (:blocks ast)))))
      
      (testing "contains header from markdown"
        (let [blocks (:blocks ast)
              headers (filter #(= (:t %) "Header") blocks)]
          (is (pos? (count headers))))))))

;; Integration Tests

(deftest test-build-book-integration
  (testing "Complete book building"
    (let [config-path (get-test-config-path)
          output-dir (get-test-output-dir)
          result (builder/build-book config-path output-dir)]
      
      (testing "returns result map"
        (is (map? result))
        (is (contains? result :metadata))
        (is (contains? result :database))
        (is (contains? result :outputs)))
      
      (testing "creates database"
        (is (.exists (io/file (:database result)))))
      
      (testing "creates output files"
        (is (contains? (:outputs result) :html))
        (let [html-file (:html (:outputs result))]
          (is (.exists html-file))
          (is (> (.length html-file) 0))))
      
      (testing "database contains book record"
        (let [ds (jdbc/get-datasource
                   {:dbtype "sqlite" :dbname (:database result)})
              books (jdbc/execute! ds
                      (sql/format {:select [:*] :from [:books]}))]
          (is (= 1 (count books)))
          (is (= "test-book" (:books/book_id (first books))))))
      
      (testing "database contains section records"
        (let [ds (jdbc/get-datasource
                   {:dbtype "sqlite" :dbname (:database result)})
              sections (jdbc/execute! ds
                         (sql/format {:select [:*] :from [:sections]}))]
          (is (pos? (count sections)))
          ;; Should have sections from both files
          (is (>= (count sections) 2)))))))

;; Error Handling Tests

(deftest test-build-book-missing-config
  (testing "Building book with missing config"
    (is (thrown? Exception
          (builder/build-book "nonexistent.yml" "output")))))

(deftest test-markdown-to-ast-missing-file
  (testing "Converting missing markdown file"
    (is (thrown? Exception
          (builder/markdown-to-ast "nonexistent.md")))))

;; Output Generation Tests

(deftest test-combine-asts
  (testing "Combining multiple ASTs"
    (let [ast1 {:pandoc-api-version [1 23 1]
                :meta {:title {:t "MetaString" :c "Doc 1"}}
                :blocks [{:t "Para" :c [{:t "Str" :c "First"}]}]}
          ast2 {:pandoc-api-version [1 23 1]
                :meta {}
                :blocks [{:t "Para" :c [{:t "Str" :c "Second"}]}]}
          combined (builder/combine-asts [ast1 ast2])]
      
      (testing "returns AST map"
        (is (map? combined)))
      
      (testing "has correct structure"
        (is (contains? combined :pandoc-api-version))
        (is (contains? combined :meta))
        (is (contains? combined :blocks)))
      
      (testing "combines blocks from both ASTs"
        (is (= 2 (count (:blocks combined))))))))
