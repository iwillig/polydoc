(ns polydoc.filters.python-exec-test
  "Tests for Python execution filter using GraalVM Polyglot."
  (:require [clojure.test :refer [deftest is testing]]
            [polydoc.filters.python-exec :as py]))

(deftest test-has-class?
  (testing "Checking for class in CodeBlock attributes"
    (let [attrs ["" ["python-exec" "other-class"] []]]
      (is (py/has-class? attrs "python-exec"))
      (is (py/has-class? attrs "other-class"))
      (is (not (py/has-class? attrs "missing-class"))))))

(deftest test-code-block-attrs
  (testing "Extracting attributes from CodeBlock"
    (let [block {:t "CodeBlock" :c [["id" ["class1" "class2"] []] "code"]}]
      (is (= ["id" ["class1" "class2"] []] (py/code-block-attrs block))))
    
    (testing "Non-CodeBlock returns nil"
      (let [para {:t "Para" :c []}]
        (is (nil? (py/code-block-attrs para)))))))

(deftest test-code-block-code
  (testing "Extracting code from CodeBlock"
    (let [block {:t "CodeBlock" :c [["" [] []] "x = 42"]}]
      (is (= "x = 42" (py/code-block-code block))))
    
    (testing "Non-CodeBlock returns nil"
      (let [para {:t "Para" :c []}]
        (is (nil? (py/code-block-code para)))))))

(deftest test-execute-python-basic
  (testing "Basic Python execution"
    (let [result (py/execute-python "1 + 2 + 3")]
      (is (= "6" (:result result)))
      (is (nil? (:exception result)))))
  
  (testing "Print output capture"
    (let [result (py/execute-python "print('Hello')\nprint('World')\n42")]
      (is (= "42" (:result result)))
      (is (re-find #"Hello" (:output result)))
      (is (re-find #"World" (:output result)))
      (is (nil? (:exception result))))))

(deftest test-execute-python-variables
  (testing "Variable assignment and usage"
    (let [result (py/execute-python "x = 10\ny = 20\nx + y")]
      (is (= "30" (:result result)))
      (is (nil? (:exception result)))))
  
  (testing "List operations"
    (let [result (py/execute-python "numbers = [1, 2, 3, 4, 5]\nsum(numbers)")]
      (is (= "15" (:result result)))
      (is (nil? (:exception result)))))
  
  (testing "String operations"
    (let [result (py/execute-python "'hello'.upper()")]
      (is (= "HELLO" (:result result)))
      (is (nil? (:exception result))))))

(deftest test-execute-python-functions
  (testing "Function definition and call"
    (let [result (py/execute-python "def add(a, b):\n    return a + b\n\nadd(5, 3)")]
      (is (= "8" (:result result)))
      (is (nil? (:exception result)))))
  
  (testing "Lambda functions"
    (let [result (py/execute-python "add = lambda a, b: a + b\nadd(7, 8)")]
      (is (= "15" (:result result)))
      (is (nil? (:exception result))))))

(deftest test-execute-python-errors
  (testing "Syntax errors"
    (let [result (py/execute-python "x = ")]
      (is (some? (:exception result)))
      (is (nil? (:result result)))))
  
  (testing "Runtime errors"
    (let [result (py/execute-python "raise Exception('test error')")]
      (is (some? (:exception result)))
      (is (re-find #"test error" (.getMessage (:exception result)))))))

(deftest test-format-execution-result
  (testing "Format result with output"
    (let [formatted (py/format-execution-result
                      {:result "42"
                       :output "Hello\nWorld\n"
                       :error ""
                       :exception nil})]
      (is (re-find #"Output:" formatted))
      (is (re-find #"Hello" formatted))
      (is (re-find #"Result:" formatted))
      (is (re-find #"42" formatted))))
  
  (testing "Format error result"
    (let [formatted (py/format-execution-result
                      {:result nil
                       :output ""
                       :error ""
                       :exception (Exception. "Test error")})]
      (is (re-find #"ERROR:" formatted))
      (is (re-find #"Test error" formatted)))))

(deftest test-transform-python-exec-block
  (testing "Transform python-exec block"
    (let [block {:t "CodeBlock"
                 :c [["" ["python-exec"] []]
                     "print('test')\n42"]}
          result (py/transform-python-exec-block block)]
      (is (= "CodeBlock" (:t result)))
      (is (re-find #"Original code:" (second (:c result))))
      (is (re-find #"Execution result:" (second (:c result))))
      (is (re-find #"test" (second (:c result))))
      (is (re-find #"42" (second (:c result))))))
  
  (testing "Transform py-exec alias"
    (let [block {:t "CodeBlock"
                 :c [["" ["py-exec"] []]
                     "1 + 1"]}
          result (py/transform-python-exec-block block)]
      (is (= "CodeBlock" (:t result)))
      (is (re-find #"Result:" (second (:c result))))))
  
  (testing "Non-matching block passes through"
    (let [block {:t "CodeBlock"
                 :c [["" ["javascript"] []]
                     "console.log('test')"]}
          result (py/transform-python-exec-block block)]
      (is (= block result)))))

(deftest test-python-exec-filter-integration
  (testing "Filter processes multiple blocks"
    (let [ast {:t "Document"
               :c [{:t "CodeBlock"
                    :c [["" ["python-exec"] []] "1 + 1"]}
                   {:t "CodeBlock"
                    :c [["" ["javascript"] []] "console.log('unchanged')"]}
                   {:t "CodeBlock"
                    :c [["" ["py-exec"] []] "2 + 2"]}]}
          result (py/python-exec-filter ast)]
      ;; First block should be transformed
      (is (re-find #"Execution result:" (get-in result [:c 0 :c 1])))
      ;; Second block should be unchanged
      (is (= "console.log('unchanged')" (get-in result [:c 1 :c 1])))
      ;; Third block should be transformed
      (is (re-find #"Execution result:" (get-in result [:c 2 :c 1]))))))
