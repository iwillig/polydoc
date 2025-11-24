(ns polydoc.filters.sqlite-exec-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [polydoc.filters.sqlite-exec :as sqlite-exec]))

(deftest test-has-class?
  (testing "has-class? detects class in attrs"
    (let [attrs ["" ["sqlite-exec" "other"] []]]
      (is (sqlite-exec/has-class? attrs "sqlite-exec"))
      (is (sqlite-exec/has-class? attrs "other"))
      (is (not (sqlite-exec/has-class? attrs "missing"))))))

(deftest test-code-block-attrs
  (testing "code-block-attrs extracts attributes"
    (let [node {:t "CodeBlock"
                :c [["id" ["sqlite"] []] "SELECT 1"]}]
      (is (= ["id" ["sqlite"] []] (sqlite-exec/code-block-attrs node))))
    
    (testing "returns nil for non-CodeBlock"
      (is (nil? (sqlite-exec/code-block-attrs {:t "Para" :c []}))))))

(deftest test-code-block-code
  (testing "code-block-code extracts code string"
    (let [node {:t "CodeBlock"
                :c [["" [] []] "SELECT 1"]}]
      (is (= "SELECT 1" (sqlite-exec/code-block-code node))))
    
    (testing "returns nil for non-CodeBlock"
      (is (nil? (sqlite-exec/code-block-code {:t "Para" :c []}))))))

(deftest test-get-attr-value
  (testing "get-attr-value extracts key-value pairs"
    (let [attrs ["id" ["class1"] [["db" "test.db"] ["format" "table"]]]]
      (is (= "test.db" (sqlite-exec/get-attr-value attrs "db")))
      (is (= "table" (sqlite-exec/get-attr-value attrs "format")))
      (is (nil? (sqlite-exec/get-attr-value attrs "missing"))))))

(deftest test-execute-sql-memory
  (testing "execute-sql with in-memory database"
    (let [result (sqlite-exec/execute-sql
                  {:sql "SELECT 1 as num, 'hello' as text"})]
      (is (nil? (:exception result)))
      (is (nil? (:error result)))
      (is (= [{:num 1 :text "hello"}] (:result result)))))
  
  (testing "execute-sql with multiple rows"
    (let [result (sqlite-exec/execute-sql
                  {:sql "SELECT * FROM (VALUES (1, 'a'), (2, 'b'), (3, 'c'))"})]
      (is (nil? (:exception result)))
      (is (= 3 (count (:result result))))))
  
  (testing "execute-sql with syntax error"
    (let [result (sqlite-exec/execute-sql
                  {:sql "INVALID SQL"})]
      (is (some? (:exception result)))
      (is (some? (:error result)))
      (is (empty? (:result result))))))

(deftest test-execute-sql-create-table
  (testing "execute-sql with CREATE and query in single statement"
    ;; SQLite supports CTEs (Common Table Expressions) for temp data
    (let [result (sqlite-exec/execute-sql
                  {:sql "WITH test (id, name) AS (VALUES (1, 'Alice'), (2, 'Bob')) SELECT * FROM test ORDER BY id"})]
      
      (is (nil? (:exception result)))
      (is (= [{:id 1 :name "Alice"}
              {:id 2 :name "Bob"}]
             (:result result))))))

(deftest test-format-text-results
  (testing "format-text-results creates aligned table"
    (let [results [{:name "Alice" :age 30}
                   {:name "Bob" :age 25}]
          output (sqlite-exec/format-text-results results)]
      (is (string? output))
      (is (str/includes? output "Alice"))
      (is (str/includes? output "Bob"))
      (is (str/includes? output "|"))))
  
  (testing "format-text-results with empty results"
    (is (= "No results" (sqlite-exec/format-text-results [])))))

(deftest test-make-pandoc-table
  (testing "make-pandoc-table creates Table node"
    (let [results [{:name "Alice" :age 30}
                   {:name "Bob" :age 25}]
          table (sqlite-exec/make-pandoc-table results)]
      (is (= "Table" (:t table)))
      (is (vector? (:c table)))
      (is (= 6 (count (:c table))))))  ; attrs, caption, colspecs, head, bodies, foot
  
  (testing "make-pandoc-table with empty results"
    (let [table (sqlite-exec/make-pandoc-table [])]
      (is (= "Para" (:t table)))
      (is (= "No results" (get-in table [:c 0 :c]))))))

(deftest test-transform-sqlite-exec-block
  (testing "transform-sqlite-exec-block executes SQL"
    (let [node {:t "CodeBlock"
                :c [["" ["sqlite-exec"] []]
                    "SELECT 1 as num, 'test' as text"]}
          result (sqlite-exec/transform-sqlite-exec-block node)]
      (is (= "Table" (:t result)))))
  
  (testing "transform-sqlite-exec-block with 'sqlite' class"
    (let [node {:t "CodeBlock"
                :c [["" ["sqlite"] []]
                    "SELECT 42 as answer"]}
          result (sqlite-exec/transform-sqlite-exec-block node)]
      (is (= "Table" (:t result)))))
  
  (testing "transform-sqlite-exec-block with text format"
    (let [node {:t "CodeBlock"
                :c [["" ["sqlite-exec"] [["format" "text"]]]
                    "SELECT 1 as num"]}
          result (sqlite-exec/transform-sqlite-exec-block node)]
      (is (= "CodeBlock" (:t result)))
      (let [code (second (:c result))]
        (is (str/includes? code "Results:")))))
  
  (testing "transform-sqlite-exec-block with SQL error"
    (let [node {:t "CodeBlock"
                :c [["" ["sqlite-exec"] []]
                    "INVALID SQL"]}
          result (sqlite-exec/transform-sqlite-exec-block node)]
      (is (= "CodeBlock" (:t result)))
      (let [code (second (:c result))]
        (is (str/includes? code "ERROR:")))))
  
  (testing "transform-sqlite-exec-block preserves non-sqlite blocks"
    (let [node {:t "CodeBlock"
                :c [["" ["python"] []]
                    "print('hello')"]}]
      (is (= node (sqlite-exec/transform-sqlite-exec-block node))))))

(deftest test-sqlite-exec-filter-integration
  (testing "sqlite-exec-filter processes entire AST"
    (let [ast {:pandoc-api-version [1 22]
               :meta {}
               :blocks [{:t "Para"
                        :c [{:t "Str" :c "Before"}]}
                       {:t "CodeBlock"
                        :c [["" ["sqlite-exec"] []]
                            "SELECT 'hello' as greeting"]}
                       {:t "Para"
                        :c [{:t "Str" :c "After"}]}]}
          result (sqlite-exec/sqlite-exec-filter ast)
          blocks (:blocks result)]
      
      (is (= 3 (count blocks)))
      (is (= "Para" (:t (first blocks))))
      (is (= "Table" (:t (second blocks))))  ; Transformed
      (is (= "Para" (:t (nth blocks 2)))))))

(deftest test-sqlite-exec-filter-multiple-queries
  (testing "sqlite-exec-filter handles multiple queries"
    (let [ast {:pandoc-api-version [1 22]
               :meta {}
               :blocks [{:t "CodeBlock"
                        :c [["" ["sqlite"] []]
                            "SELECT 1 as first"]}
                       {:t "CodeBlock"
                        :c [["" ["sqlite"] []]
                            "SELECT 2 as second"]}]}
          result (sqlite-exec/sqlite-exec-filter ast)
          blocks (:blocks result)]
      
      (is (= 2 (count blocks)))
      (is (every? #(= "Table" (:t %)) blocks)))))
