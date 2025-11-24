(ns polydoc.filters.clojure-exec-test
  (:require [clojure.test :refer [deftest is testing]]
            [polydoc.filters.clojure-exec :as clj-exec]))

(deftest test-has-class?
  (testing "Detects class in attributes"
    (let [attrs ["id" ["clojure-exec" "other"] []]]
      (is (true? (clj-exec/has-class? attrs "clojure-exec")))
      (is (false? (clj-exec/has-class? attrs "not-there"))))))

(deftest test-code-block-attrs
  (testing "Extracts attributes from CodeBlock"
    (let [node {:t "CodeBlock" :c [["id" ["clj"] []] "code"]}
          attrs (clj-exec/code-block-attrs node)]
      (is (= ["id" ["clj"] []] attrs)))))

(deftest test-code-block-code
  (testing "Extracts code from CodeBlock"
    (let [node {:t "CodeBlock" :c [["" [] []] "(+ 1 2)"]}
          code (clj-exec/code-block-code node)]
      (is (= "(+ 1 2)" code)))))

(deftest test-execute-clojure
  (testing "Executes Clojure code successfully"
    (let [result (clj-exec/execute-clojure "(+ 1 2)")]
      (is (= 3 (:result result)))
      (is (nil? (:exception result)))))
  
  (testing "Captures output"
    (let [result (clj-exec/execute-clojure "(do (println \"hello\") 42)")]
      (is (= 42 (:result result)))
      (is (re-find #"hello" (:output result)))))
  
  (testing "Captures exceptions"
    (let [result (clj-exec/execute-clojure "(/ 1 0)")]
      (is (some? (:exception result)))
      (is (instance? Exception (:exception result))))))

(deftest test-format-execution-result
  (testing "Formats successful result"
    (let [result {:result 42 :output "" :error "" :exception nil}
          formatted (clj-exec/format-execution-result result)]
      (is (re-find #"Result:" formatted))
      (is (re-find #"42" formatted))))
  
  (testing "Formats result with output"
    (let [result {:result 42 :output "hello\n" :error "" :exception nil}
          formatted (clj-exec/format-execution-result result)]
      (is (re-find #"Output:" formatted))
      (is (re-find #"hello" formatted))
      (is (re-find #"Result:" formatted))))
  
  (testing "Formats exception"
    (let [ex (Exception. "boom")
          result {:result nil :output "" :error "" :exception ex}
          formatted (clj-exec/format-execution-result result)]
      (is (re-find #"ERROR:" formatted))
      (is (re-find #"boom" formatted)))))

(deftest test-transform-clojure-exec-block
  (testing "Transforms clojure-exec blocks"
    (let [node {:t "CodeBlock"
                :c [["" ["clojure-exec"] []]
                    "(+ 1 2)"]}
          result (clj-exec/transform-clojure-exec-block node)]
      (is (= "CodeBlock" (:t result)))
      (is (re-find #"Original code:" (second (:c result))))
      (is (re-find #"Execution result:" (second (:c result))))))
  
  (testing "Leaves other blocks unchanged"
    (let [node {:t "CodeBlock"
                :c [["" ["javascript"] []]
                    "console.log('hi')"]}
          result (clj-exec/transform-clojure-exec-block node)]
      (is (= node result)))))

(deftest test-clojure-exec-filter
  (testing "Filters entire AST"
    (let [ast {:blocks [{:t "Para" :c ["text"]}
                        {:t "CodeBlock"
                         :c [["" ["clojure-exec"] []]
                             "(+ 1 2)"]}
                        {:t "CodeBlock"
                         :c [["" ["python"] []]
                             "print('hi')"]}]}
          result (clj-exec/clojure-exec-filter ast)
          code-blocks (filter #(= "CodeBlock" (:t %)) (:blocks result))]
      ;; Should have 2 code blocks still
      (is (= 2 (count code-blocks)))
      ;; First one should be transformed
      (is (re-find #"Execution result:" (second (:c (first code-blocks)))))
      ;; Second one should be unchanged
      (is (= "print('hi')" (second (:c (second code-blocks))))))))

;; Edge Case Tests
(deftest test-edge-cases
  (testing "Empty code block"
    (let [node {:t "CodeBlock"
                :c [["" ["clojure-exec"] []]
                    ""]}
          result (clj-exec/transform-clojure-exec-block node)]
      ;; Should still transform, even with empty code
      (is (= "CodeBlock" (:t result)))))
  
  (testing "Whitespace-only code block"
    (let [node {:t "CodeBlock"
                :c [["" ["clojure-exec"] []]
                    "   \n  \t  "]}
          result (clj-exec/transform-clojure-exec-block node)]
      ;; Should still transform
      (is (= "CodeBlock" (:t result)))))
  
  (testing "Syntax error in code"
    (let [node {:t "CodeBlock"
                :c [["" ["clojure-exec"] []]
                    "((("]}
          result (clj-exec/transform-clojure-exec-block node)]
      ;; Should capture the error
      (is (re-find #"ERROR:" (second (:c result))))))
  
  (testing "Multiple expressions (only first is evaluated)"
    (let [result (clj-exec/execute-clojure "(+ 1 2) (* 3 4)")]
      ;; read-string only reads first form
      (is (= 3 (:result result)))))
  
  (testing "Code with side effects"
    (let [result (clj-exec/execute-clojure "(do (def x 10) x)")]
      (is (= 10 (:result result)))))
  
  (testing "Code returning nil"
    (let [result (clj-exec/execute-clojure "nil")]
      (is (nil? (:result result)))
      (is (nil? (:exception result)))))
  
  (testing "Code returning collection"
    (let [result (clj-exec/execute-clojure "[1 2 3]")]
      (is (= [1 2 3] (:result result)))))
  
  (testing "Non-CodeBlock nodes unchanged"
    (let [node {:t "Para" :c ["text"]}
          result (clj-exec/transform-clojure-exec-block node)]
      (is (= node result)))))

(deftest test-real-world-examples
  (testing "Arithmetic calculation"
    (let [result (clj-exec/execute-clojure "(reduce + (range 10))")]
      (is (= 45 (:result result)))))
  
  (testing "String manipulation"
    (let [result (clj-exec/execute-clojure 
                   "(clojure.string/upper-case \"hello\")")]
      (is (= "HELLO" (:result result)))))
  
  (testing "Data structure manipulation"
    (let [result (clj-exec/execute-clojure
                   "(assoc {:a 1} :b 2)")]
      (is (= {:a 1 :b 2} (:result result)))))
  
  (testing "Code with multiple lines"
    (let [code "(let [x 10\n      y 20]\n  (+ x y))"
          result (clj-exec/execute-clojure code)]
      (is (= 30 (:result result))))))
