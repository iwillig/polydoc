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


(deftest test-pandoc-table-caption-format
  (testing "Caption is array format [short, long-blocks], not object"
    (let [results [{:id 1 :name "test"}]
          table (sqlite-exec/make-pandoc-table results)
          caption (nth (:c table) 1)]  ; Caption is 2nd element in table :c
      
      ;; Caption must be a vector [short-caption, long-caption-blocks]
      (is (vector? caption) "Caption should be a vector")
      (is (= 2 (count caption)) "Caption should have 2 elements")
      (is (nil? (first caption)) "Short caption should be nil")
      (is (vector? (second caption)) "Long caption blocks should be a vector")
      
      ;; Caption should NOT be an object like {:t "Caption" :c ...}
      (is (not (map? caption)) "Caption should not be a map/object"))))


(deftest test-pandoc-table-head-format
  (testing "TableHead is array format [attrs, rows], not object"
    (let [results [{:id 1 :name "test"}]
          table (sqlite-exec/make-pandoc-table results)
          head (nth (:c table) 3)]  ; TableHead is 4th element
      
      ;; TableHead must be array: [attrs, [rows]]
      (is (vector? head) "TableHead should be a vector")
      (is (= 2 (count head)) "TableHead should have 2 elements [attrs, rows]")
      
      (let [[attrs rows] head]
        (is (vector? attrs) "TableHead attrs should be a vector")
        (is (= 3 (count attrs)) "TableHead attrs should be [id, classes, kvs]")
        (is (vector? rows) "TableHead rows should be a vector")
        
        ;; Each row is [row-attrs, cells]
        (is (every? vector? rows) "Each row should be a vector")
        (let [[row-attrs cells] (first rows)]
          (is (vector? row-attrs) "Row attrs should be a vector")
          (is (vector? cells) "Row cells should be a vector")))
      
      ;; TableHead should NOT be an object like {:t "TableHead" :c ...}
      (is (not (map? head)) "TableHead should not be a map/object"))))


(deftest test-pandoc-table-body-format
  (testing "TableBody is array format in bodies array"
    (let [results [{:id 1 :name "test"}
                   {:id 2 :name "test2"}]
          table (sqlite-exec/make-pandoc-table results)
          bodies (nth (:c table) 4)]  ; Bodies is 5th element
      
      ;; Bodies is an array of body elements
      (is (vector? bodies) "Bodies should be a vector")
      (is (pos? (count bodies)) "Bodies should not be empty")
      
      ;; Each body is [attrs, row-head-cols, head-rows, body-rows]
      (let [body (first bodies)]
        (is (vector? body) "Body should be a vector")
        (is (= 4 (count body)) "Body should have 4 elements")
        
        (let [[attrs row-head-cols head-rows body-rows] body]
          (is (vector? attrs) "Body attrs should be a vector")
          (is (number? row-head-cols) "Row head cols should be a number")
          (is (vector? head-rows) "Head rows should be a vector")
          (is (vector? body-rows) "Body rows should be a vector")
          
          ;; Each body row is [row-attrs, cells]
          (is (= 2 (count body-rows)) "Should have 2 data rows")
          (is (every? vector? body-rows) "Each row should be a vector")
          
          (let [[row-attrs cells] (first body-rows)]
            (is (vector? row-attrs) "Row attrs should be a vector")
            (is (vector? cells) "Row cells should be a vector"))))
      
      ;; Body should NOT be an object like {:t "TableBody" :c ...}
      (is (not (some map? bodies)) "Bodies should not contain map/object"))))


(deftest test-pandoc-table-cell-format
  (testing "Table cells have full structure [attrs, align, rowspan, colspan, blocks]"
    (let [results [{:id 1 :name "test"}]
          table (sqlite-exec/make-pandoc-table results)
          head (nth (:c table) 3)
          [_attrs rows] head
          [_row-attrs cells] (first rows)
          cell (first cells)]
      
      ;; Cell must be [attrs, alignment, rowspan, colspan, blocks]
      (is (vector? cell) "Cell should be a vector")
      (is (= 5 (count cell)) "Cell should have 5 elements")
      
      (let [[attrs alignment rowspan colspan blocks] cell]
        (is (vector? attrs) "Cell attrs should be a vector")
        (is (= 3 (count attrs)) "Cell attrs should be [id, classes, kvs]")
        
        (is (map? alignment) "Alignment should be a map")
        (is (= "AlignDefault" (:t alignment)) "Alignment should have :t key")
        
        (is (number? rowspan) "Rowspan should be a number")
        (is (= 1 rowspan) "Default rowspan should be 1")
        
        (is (number? colspan) "Colspan should be a number")
        (is (= 1 colspan) "Default colspan should be 1")
        
        (is (vector? blocks) "Blocks should be a vector")
        (is (pos? (count blocks)) "Blocks should not be empty"))
      
      ;; Cell should NOT be just blocks like {:t "Plain" :c ...}
      (is (not (map? cell)) "Cell should not be a simple map/object"))))


(deftest test-pandoc-table-foot-format
  (testing "TableFoot is array format [attrs, rows]"
    (let [results [{:id 1}]
          table (sqlite-exec/make-pandoc-table results)
          foot (nth (:c table) 5)]  ; TableFoot is 6th element
      
      ;; TableFoot must be array: [attrs, rows]
      (is (vector? foot) "TableFoot should be a vector")
      (is (= 2 (count foot)) "TableFoot should have 2 elements")
      
      (let [[attrs rows] foot]
        (is (vector? attrs) "TableFoot attrs should be a vector")
        (is (vector? rows) "TableFoot rows should be a vector")
        (is (empty? rows) "TableFoot rows should be empty for our tables"))
      
      ;; TableFoot should NOT be an object
      (is (not (map? foot)) "TableFoot should not be a map/object"))))

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
