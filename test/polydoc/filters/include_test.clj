(ns polydoc.filters.include-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [polydoc.filters.include :as include]))

(deftest test-has-class?
  (testing "has-class? detects class in attrs"
    (let [attrs ["" ["include" "other"] []]]
      (is (include/has-class? attrs "include"))
      (is (include/has-class? attrs "other"))
      (is (not (include/has-class? attrs "missing"))))))

(deftest test-code-block-attrs
  (testing "code-block-attrs extracts attributes"
    (let [node {:t "CodeBlock"
                :c [["id" ["include"] []] "path/to/file.md"]}]
      (is (= ["id" ["include"] []] (include/code-block-attrs node))))
    
    (testing "returns nil for non-CodeBlock"
      (is (nil? (include/code-block-attrs {:t "Para" :c []}))))))

(deftest test-code-block-code
  (testing "code-block-code extracts code string"
    (let [node {:t "CodeBlock"
                :c [["" [] []] "path/to/file.md"]}]
      (is (= "path/to/file.md" (include/code-block-code node))))
    
    (testing "returns nil for non-CodeBlock"
      (is (nil? (include/code-block-code {:t "Para" :c []}))))))

(deftest test-get-attr-value
  (testing "get-attr-value extracts key-value pairs"
    (let [attrs ["id" ["class1"] [["base" "/docs"] ["mode" "code"]]]]
      (is (= "/docs" (include/get-attr-value attrs "base")))
      (is (= "code" (include/get-attr-value attrs "mode")))
      (is (nil? (include/get-attr-value attrs "missing"))))))

(deftest test-normalize-path
  (testing "normalize-path removes . and .."
    (is (= "foo/bar" (include/normalize-path "foo/./bar")))
    (is (= "foo" (include/normalize-path "foo/bar/..")))
    (is (= "../foo" (include/normalize-path "../foo")))
    (is (= "bar" (include/normalize-path "foo/../bar")))))

(deftest test-resolve-path
  (testing "resolve-path with relative path"
    (let [result (include/resolve-path "file.md" "/base")]
      (is (str/includes? result "base"))
      (is (str/ends-with? result "file.md"))))
  
  (testing "resolve-path with absolute path"
    (let [result (include/resolve-path "/absolute/path.md" "/base")]
      (is (str/starts-with? result "/absolute"))
      (is (str/ends-with? result "path.md")))))

(deftest test-read-file
  (testing "read-file reads existing file"
    (let [test-file "test/fixtures/include/simple.md"
          result (include/read-file test-file)]
      (is (:success result))
      (is (string? (:content result)))
      (is (str/includes? (:content result) "Simple Include"))))
  
  (testing "read-file handles missing file"
    (let [result (include/read-file "nonexistent.md")]
      (is (not (:success result)))
      (is (string? (:error result)))
      (is (str/includes? (:error result) "not found")))))

(deftest test-parse-markdown-to-ast
  (testing "parse-markdown-to-ast parses simple markdown"
    (let [content "# Heading\n\nParagraph text."
          result (include/parse-markdown-to-ast content)]
      (is (:success result))
      (is (vector? (:blocks result)))
      (is (pos? (count (:blocks result))))))
  
  (testing "parse-markdown-to-ast handles empty content"
    (let [result (include/parse-markdown-to-ast "")]
      (is (:success result))
      (is (vector? (:blocks result))))))

(deftest test-make-error-block
  (testing "make-error-block creates error CodeBlock"
    (let [path "test.md"
          error "Test error"
          block (include/make-error-block path error)]
      (is (= "CodeBlock" (:t block)))
      (let [content (second (:c block))]
        (is (str/includes? content "ERROR"))
        (is (str/includes? content error))
        (is (str/includes? content path))))))

(deftest test-make-code-block
  (testing "make-code-block creates CodeBlock"
    (let [content "(defn hello [])"
          lang "clojure"
          block (include/make-code-block content lang)]
      (is (= "CodeBlock" (:t block)))
      (is (= content (second (:c block))))
      (let [attrs (first (:c block))]
        (is (some #(= "clojure" %) (second attrs))))))
  
  (testing "make-code-block without language"
    (let [content "plain text"
          block (include/make-code-block content nil)]
      (is (= "CodeBlock" (:t block)))
      (is (= content (second (:c block)))))))

(deftest test-make-raw-block
  (testing "make-raw-block creates RawBlock"
    (let [content "**bold** text"
          block (include/make-raw-block content)]
      (is (= "RawBlock" (:t block)))
      (is (= ["markdown" content] (:c block))))))

(deftest test-detect-cycle
  (testing "detect-cycle finds cycles"
    (is (include/detect-cycle "/path/a" ["/path/a"]))
    (is (include/detect-cycle "/path/b" ["/path/a" "/path/b"]))
    (is (not (include/detect-cycle "/path/c" ["/path/a" "/path/b"])))))

(deftest test-transform-include-block-code-mode
  (testing "transform-include-block with code mode"
    (let [node {:t "CodeBlock"
                :c [["" ["include"] [["mode" "code"] ["lang" "clojure"]]]
                    "test/fixtures/include/code.clj"]}
          result (include/transform-include-block node {})]
      (is (= "CodeBlock" (:t result)))
      (let [content (second (:c result))]
        (is (str/includes? content "defn hello"))))))

(deftest test-transform-include-block-parse-mode
  (testing "transform-include-block with parse mode"
    (let [node {:t "CodeBlock"
                :c [["" ["include"] []]
                    "test/fixtures/include/simple.md"]}
          result (include/transform-include-block node {})]
      ;; Parse mode wraps in Div
      (is (= "Div" (:t result)))
      (let [blocks (second (:c result))]
        (is (vector? blocks))
        (is (pos? (count blocks)))))))

(deftest test-transform-include-block-raw-mode
  (testing "transform-include-block with raw mode"
    (let [node {:t "CodeBlock"
                :c [["" ["include"] [["mode" "raw"]]]
                    "test/fixtures/include/simple.md"]}
          result (include/transform-include-block node {})]
      (is (= "RawBlock" (:t result)))
      (is (str/includes? (second (:c result)) "Simple Include")))))

(deftest test-transform-include-block-missing-file
  (testing "transform-include-block with missing file"
    (let [node {:t "CodeBlock"
                :c [["" ["include"] []]
                    "nonexistent.md"]}
          result (include/transform-include-block node {})]
      (is (= "CodeBlock" (:t result)))
      (let [content (second (:c result))]
        (is (str/includes? content "ERROR"))
        (is (str/includes? content "not found"))))))

(deftest test-transform-include-block-cycle-detection
  (testing "transform-include-block detects cycles"
    (let [path "test/fixtures/include/simple.md"
          resolved-path (include/resolve-path path ".")
          node {:t "CodeBlock"
                :c [["" ["include"] []]
                    path]}
          opts {:include-stack [resolved-path]}
          result (include/transform-include-block node opts)]
      ;; Should detect cycle since file is in stack
      (is (= "CodeBlock" (:t result)))
      (let [content (second (:c result))]
        (is (str/includes? content "ERROR"))
        (is (str/includes? content "cycle"))))))

(deftest test-transform-include-block-preserves-non-include
  (testing "transform-include-block preserves non-include blocks"
    (let [node {:t "CodeBlock"
                :c [["" ["python"] []]
                    "print('hello')"]}]
      (is (= node (include/transform-include-block node {}))))))

(deftest test-include-filter-integration
  (testing "include-filter processes entire AST"
    (let [ast {:pandoc-api-version [1 22]
               :meta {}
               :blocks [{:t "Para"
                        :c [{:t "Str" :c "Before"}]}
                       {:t "CodeBlock"
                        :c [["" ["include"] [["mode" "code"] ["lang" "clojure"]]]
                            "test/fixtures/include/code.clj"]}
                       {:t "Para"
                        :c [{:t "Str" :c "After"}]}]}
          result (include/include-filter ast)
          blocks (:blocks result)]
      
      (is (= 3 (count blocks)))
      (is (= "Para" (:t (first blocks))))
      ;; Second block should be transformed to CodeBlock with code
      (is (= "CodeBlock" (:t (second blocks))))
      (let [content (second (:c (second blocks)))]
        (is (str/includes? content "defn hello")))
      (is (= "Para" (:t (nth blocks 2)))))))

(deftest test-include-filter-multiple-includes
  (testing "include-filter handles multiple includes"
    (let [ast {:pandoc-api-version [1 22]
               :meta {}
               :blocks [{:t "CodeBlock"
                        :c [["" ["include"] [["mode" "code"]]]
                            "test/fixtures/include/code.clj"]}
                       {:t "CodeBlock"
                        :c [["" ["include"] [["mode" "raw"]]]
                            "test/fixtures/include/simple.md"]}]}
          result (include/include-filter ast)
          blocks (:blocks result)]
      
      (is (= 2 (count blocks)))
      (is (= "CodeBlock" (:t (first blocks))))
      (is (= "RawBlock" (:t (second blocks)))))))

(deftest test-include-filter-max-depth
  (testing "include-filter respects max-depth"
    (let [ast {:pandoc-api-version [1 22]
               :meta {}
               :blocks [{:t "CodeBlock"
                        :c [["" ["include"] []]
                            "test/fixtures/include/simple.md"]}]}
          ;; Set max-depth to 0 to prevent any processing
          result (include/include-filter ast {:max-depth 0 :current-depth 0})
          blocks (:blocks result)]
      
      ;; Should return unchanged since we're at max depth
      (is (= 1 (count blocks)))
      (is (= "CodeBlock" (:t (first blocks)))))))
