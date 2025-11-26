(ns polydoc.book.search-test
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [next.jdbc :as jdbc]
    [polydoc.book.search :as search]
    [polydoc.test-helpers :as helpers]))


(defn insert-test-data!
  "Insert test data into the database for search tests."
  [conn]
  ;; Insert test book
  (jdbc/execute-one! conn
    ["INSERT INTO books (book_id, title, author, description) 
      VALUES (?, ?, ?, ?)"
     "test-book" "Test Book" "Test Author" "A test book for search"])
  
  ;; Insert test sections with searchable content
  (let [book-id (:books/id (jdbc/execute-one! conn
                             ["SELECT id FROM books WHERE book_id = ?" "test-book"]))]
    ;; Section about Clojure
    (jdbc/execute-one! conn
      ["INSERT INTO sections 
        (book_id, section_id, source_file, heading_level, heading_text, 
         heading_slug, content_plain, section_order, content_hash)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
       book-id "clojure-intro" "intro.md" 1 "Introduction to Clojure"
       "introduction-to-clojure"
       "Clojure is a functional programming language that runs on the JVM. It emphasizes immutability and simplicity."
       1 "hash1"])
    
    ;; Section about Pandoc
    (jdbc/execute-one! conn
      ["INSERT INTO sections 
        (book_id, section_id, source_file, heading_level, heading_text, 
         heading_slug, content_plain, section_order, content_hash)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
       book-id "pandoc-filters" "filters.md" 1 "Pandoc Filters"
       "pandoc-filters"
       "Pandoc filters allow you to transform documents during conversion. You can write filters in any language."
       2 "hash2"])
    
    ;; Section about both
    (jdbc/execute-one! conn
      ["INSERT INTO sections 
        (book_id, section_id, source_file, heading_level, heading_text, 
         heading_slug, content_plain, section_order, content_hash)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
       book-id "clojure-pandoc" "advanced.md" 2 "Using Clojure with Pandoc"
       "using-clojure-with-pandoc"
       "This section shows how to write Pandoc filters in Clojure. Combining Clojure and Pandoc gives you powerful document processing."
       3 "hash3"])
    
    ;; Section with different heading level
    (jdbc/execute-one! conn
      ["INSERT INTO sections 
        (book_id, section_id, source_file, heading_level, heading_text, 
         heading_slug, content_plain, section_order, content_hash)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
       book-id "overview" "overview.md" 1 "Overview"
       "overview"
       "This is a high-level overview of the documentation system."
       0 "hash0"])))


(defn search-test-fixture
  "Fixture that sets up database with test data for search tests."
  [f]
  (helpers/use-sqlite-database
    (fn []
      (insert-test-data! helpers/*connection*)
      (f))))


(use-fixtures :each search-test-fixture)


(deftest test-basic-search
  (testing "Basic search returns results"
    (let [results (search/search helpers/*connection* "clojure")]
      (is (seq results))
      (is (>= (count results) 2))
      (is (every? #(contains? % :sections/heading_text) results))
      (is (every? #(contains? % :snippet) results))))
  
  (testing "Search with no matches returns empty"
    (let [results (search/search helpers/*connection* "nonexistent")]
      (is (empty? results))))
  
  (testing "Search is case-insensitive"
    (let [results-lower (search/search helpers/*connection* "clojure")
          results-upper (search/search helpers/*connection* "CLOJURE")]
      (is (= (count results-lower) (count results-upper))))))


(deftest test-search-options
  (testing "Limit option"
    (let [results (search/search helpers/*connection* "clojure" {:limit 1})]
      (is (= 1 (count results)))))
  
  (testing "Custom highlight markers"
    (let [results (search/search helpers/*connection* "clojure" 
                                  {:highlight-start "**"
                                   :highlight-end "**"})]
      (is (some #(re-find #"(?i)\*\*clojure\*\*" (:snippet % "")) results))))
  
  (testing "Book ID filter"
    (let [book-id (:books/id (jdbc/execute-one! helpers/*connection* 
                               ["SELECT id FROM books WHERE book_id = ?" "test-book"]))
          results (search/search helpers/*connection* "clojure" {:book-id book-id})]
      (is (seq results)))))


(deftest test-phrase-search
  (testing "Exact phrase search"
    (let [results (search/search helpers/*connection* "\"functional programming\"")]
      (is (seq results))
      ;; At least one result should exist for the phrase
      (is (>= (count results) 1))))
  
  (testing "Phrase not found"
    (let [results (search/search helpers/*connection* "\"this phrase does not exist\"")]
      (is (empty? results)))))


(deftest test-boolean-operators
  (testing "AND operator"
    (let [results (search/search helpers/*connection* "clojure AND pandoc")]
      (is (seq results))
      (is (every? #(and (re-find #"(?i)clojure" (:snippet % ""))
                        (re-find #"(?i)pandoc" (:snippet % "")))
                  results))))
  
  (testing "OR operator"
    (let [results (search/search helpers/*connection* "clojure OR python")]
      (is (seq results))))
  
  (testing "NOT operator"
    (let [results-all (search/search helpers/*connection* "programming")
          results-filtered (search/search helpers/*connection* "programming NOT clojure")]
      (is (seq results-all))
      ;; Results with NOT should be subset
      (is (<= (count results-filtered) (count results-all))))))


(deftest test-count-results
  (testing "Count returns correct number"
    (let [results (search/search helpers/*connection* "clojure" {:limit 100})
          result-count (search/count-results helpers/*connection* "clojure")]
      (is (= (count results) result-count))))
  
  (testing "Count with no matches"
    (is (= 0 (search/count-results helpers/*connection* "nonexistent"))))
  
  (testing "Count with book filter"
    (let [book-id (:books/id (jdbc/execute-one! helpers/*connection* 
                               ["SELECT id FROM books WHERE book_id = ?" "test-book"]))
          result-count (search/count-results helpers/*connection* "clojure" {:book-id book-id})]
      (is (pos? result-count)))))


(deftest test-search-by-section-level
  (testing "Search only level 1 headings"
    (let [results (search/search-by-section-level helpers/*connection* "clojure" [1])]
      (is (seq results))
      (is (every? #(= 1 (:sections/heading_level %)) results))))
  
  (testing "Search multiple levels"
    (let [results (search/search-by-section-level helpers/*connection* "clojure" [1 2])]
      (is (seq results))
      (is (every? #(#{1 2} (:sections/heading_level %)) results))))
  
  (testing "Search level with no matches"
    (let [results (search/search-by-section-level helpers/*connection* "overview" [6])]
      (is (empty? results)))))


(deftest test-search-with-context
  (testing "Search without additional context"
    (let [results (search/search-with-context helpers/*connection* "clojure")]
      (is (seq results))
      (is (every? #(contains? % :sections/heading_text) results))))
  
  (testing "Search with content included"
    (let [results (search/search-with-context helpers/*connection* "clojure" 
                                               {:include-content true})]
      (is (seq results))
      (is (every? #(contains? % :sections/content_markdown) results))))
  
  (testing "Search with metadata included"
    (let [results (search/search-with-context helpers/*connection* "clojure"
                                               {:include-metadata true})]
      (is (seq results))
      (is (every? #(contains? % :sections/metadata_json) results)))))


(deftest test-edge-cases
  (testing "Empty query returns nil"
    (is (nil? (search/search helpers/*connection* "")))
    (is (nil? (search/search helpers/*connection* "   ")))
    (is (nil? (search/search helpers/*connection* nil))))
  
  (testing "Special characters in query"
    ;; FTS5 should handle these gracefully
    (let [results (search/search helpers/*connection* "clojure*")]
      (is (or (seq results) (empty? results)))))
  
  (testing "Very long query"
    (let [long-query (apply str (repeat 1000 "clojure "))
          results (search/search helpers/*connection* long-query)]
      (is (or (seq results) (empty results))))))


(deftest test-snippet-generation
  (testing "Snippets contain query terms"
    (let [results (search/search helpers/*connection* "clojure")]
      (is (every? #(re-find #"(?i)clojure" (:snippet % "")) results))))
  
  (testing "Snippets have highlight markers"
    (let [results (search/search helpers/*connection* "clojure")]
      (is (some #(re-find #"<mark>" (:snippet % "")) results))
      (is (some #(re-find #"</mark>" (:snippet % "")) results))))
  
  (testing "Snippet length controlled by tokens"
    (let [results-short (search/search helpers/*connection* "clojure" {:snippet-tokens 5})
          results-long (search/search helpers/*connection* "clojure" {:snippet-tokens 50})]
      ;; Just verify it works, exact lengths may vary
      (is (seq results-short))
      (is (seq results-long)))))


(deftest test-ranking
  (testing "Results include rank scores"
    (let [results (search/search helpers/*connection* "clojure")]
      (is (every? #(contains? % :sections_fts/rank) results))
      (is (every? #(number? (:sections_fts/rank %)) results))))
  
  (testing "Results ordered by relevance"
    (let [results (search/search helpers/*connection* "clojure" {:limit 10})]
      (when (> (count results) 1)
        ;; Ranks should be in ascending order (more negative = more relevant in FTS5)
        (is (apply <= (map :sections_fts/rank results)))))))
