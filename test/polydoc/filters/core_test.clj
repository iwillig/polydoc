(ns polydoc.filters.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [polydoc.filters.core :as core])
  (:import [java.io StringWriter StringReader]))

(deftest test-ast-node?
  (testing "Identifies AST nodes"
    (is (true? (core/ast-node? {:t "Para" :c []})))
    (is (false? (core/ast-node? {})))
    (is (false? (core/ast-node? {:c []})))))

(deftest test-node-type
  (testing "Extracts node type"
    (is (= "Para" (core/node-type {:t "Para" :c []})))))

(deftest test-node-content
  (testing "Extracts node content"
    (is (= [1 2 3] (core/node-content {:t "Para" :c [1 2 3]})))))

(deftest test-make-node
  (testing "Creates AST nodes"
    (is (= {:t "Para" :c ["content"]}
           (core/make-node "Para" ["content"])))))

(deftest test-walk-ast
  (testing "Walks AST and transforms nodes"
    (let [ast {:pandoc-api-version [1 23]
               :meta {}
               :blocks [{:t "Para" :c ["Hello"]}
                        {:t "CodeBlock" :c [[] "code"]}]}
          result (core/walk-ast
                  (fn [node]
                    (if (= (core/node-type node) "Para")
                      (core/make-node "Para" ["Modified"])
                      node))
                  ast)]
      (is (= "Modified" (-> result :blocks first :c first))))))

(deftest test-filter-nodes
  (testing "Filters nodes by type"
    (let [ast {:blocks [{:t "Para" :c ["p1"]}
                        {:t "CodeBlock" :c [[] "code"]}
                        {:t "Para" :c ["p2"]}]}
          paras (core/filter-nodes ast "Para")]
      (is (= 2 (count paras)))
      (is (every? #(= "Para" (:t %)) paras)))))

(deftest test-compose-filters
  (testing "Composes multiple filters"
    (let [add-one (fn [ast] (update ast :value inc))
          double-it (fn [ast] (update ast :value * 2))
          composed (core/compose-filters add-one double-it)
          result (composed {:value 5})]
      (is (= 12 (:value result))))))

(deftest test-safe-filter
  (testing "Handles filter errors gracefully"
    (let [broken-filter (fn [_] (throw (Exception. "Boom!")))
          safe (core/safe-filter broken-filter)
          ast {:blocks []}]
      (is (= ast (safe ast))))))


(deftest test-read-ast-large-json
  (testing "Reads large JSON without pushback buffer overflow"
    ;; This test ensures we can read large Pandoc ASTs that would
    ;; overflow the default pushback buffer (1024 bytes)
    (let [;; Create a large AST with many blocks (> 1024 bytes)
          large-ast {:pandoc-api-version [1 23 1]
                     :meta {}
                     :blocks (vec (repeat 100 {:t "Para"
                                               :c [{:t "Str" :c "This is a test paragraph with some content."}]}))}
          ;; Write to string
          json-str (with-out-str
                     (json/write large-ast *out*))
          ;; Create temp file
          temp-file (java.io.File/createTempFile "large-ast-test" ".json")]
      (try
        ;; Write large JSON to file
        (spit temp-file json-str)
        
        ;; Test reading from file - should not overflow
        (let [result (core/read-ast (.getPath temp-file))]
          (is (map? result))
          (is (= 100 (count (:blocks result)))))
        
        ;; Test reading from reader - should not overflow
        (with-open [reader (io/reader temp-file)]
          (let [result (core/read-ast reader)]
            (is (map? result))
            (is (= 100 (count (:blocks result))))))
        
        (finally
          (.delete temp-file))))))


(deftest test-write-ast-to-stdout
  (testing "Writes AST to stdout with dynamic *out* binding"
    ;; This test ensures write-ast correctly uses the dynamic *out*
    ;; binding instead of capturing it at function definition time
    (let [ast {:pandoc-api-version [1 23 1]
               :meta {}
               :blocks [{:t "Para" :c [{:t "Str" :c "test"}]}]}
          output (StringWriter.)]
      
      ;; Bind *out* dynamically and write to "-" (stdout)
      (binding [*out* output]
        (core/write-ast ast "-"))
      
      ;; Verify output was written to our StringWriter
      (let [result (str output)]
        (is (not= "" result))
        (is (re-find #"pandoc-api-version" result))
        (is (re-find #"test" result))))))


(deftest test-write-ast-to-file
  (testing "Writes AST to file path"
    (let [ast {:pandoc-api-version [1 23 1]
               :meta {}
               :blocks [{:t "Para" :c [{:t "Str" :c "file test"}]}]}
          temp-file (java.io.File/createTempFile "write-ast-test" ".json")]
      (try
        ;; Write to file
        (core/write-ast ast (.getPath temp-file))
        
        ;; Read back and verify
        (let [content (slurp temp-file)]
          (is (re-find #"pandoc-api-version" content))
          (is (re-find #"file test" content)))
        
        (finally
          (.delete temp-file))))))


(deftest test-write-ast-returns-nil
  (testing "write-ast returns nil, not writer object"
    (let [ast {:pandoc-api-version [1 23 1]
               :meta {}
               :blocks []}
          temp-file (java.io.File/createTempFile "return-test" ".json")]
      (try
        ;; Write to file should return nil
        (is (nil? (core/write-ast ast (.getPath temp-file))))
        
        ;; Write to stdout should return nil
        (binding [*out* (StringWriter.)]
          (is (nil? (core/write-ast ast "-"))))
        
        (finally
          (.delete temp-file))))))


(deftest test-read-ast-from-string-reader
  (testing "Reads AST from StringReader (stdin simulation)"
    (let [ast {:pandoc-api-version [1 23 1]
               :meta {}
               :blocks [{:t "Para" :c [{:t "Str" :c "stdin test"}]}]}
          json-str (with-out-str
                     (clojure.data.json/write ast *out*))
          reader (StringReader. json-str)
          result (core/read-ast reader)]
      
      (is (map? result))
      (is (= [1 23 1] (:pandoc-api-version result)))
      (is (= 1 (count (:blocks result)))))))
