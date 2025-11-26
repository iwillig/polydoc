(ns polydoc.filters.javascript-exec-test
  "Tests for JavaScript execution filter using GraalVM Polyglot."
  (:require [clojure.test :refer [deftest is testing]]
            [polydoc.filters.javascript-exec :as js]))

(deftest test-has-class?
  (testing "Checking for class in CodeBlock attributes"
    (let [attrs ["" ["javascript-exec" "other-class"] []]]
      (is (js/has-class? attrs "javascript-exec"))
      (is (js/has-class? attrs "other-class"))
      (is (not (js/has-class? attrs "missing-class"))))))

(deftest test-code-block-attrs
  (testing "Extracting attributes from CodeBlock"
    (let [block {:t "CodeBlock" :c [["id" ["class1" "class2"] []] "code"]}]
      (is (= ["id" ["class1" "class2"] []] (js/code-block-attrs block))))
    
    (testing "Non-CodeBlock returns nil"
      (let [para {:t "Para" :c []}]
        (is (nil? (js/code-block-attrs para)))))))

(deftest test-code-block-code
  (testing "Extracting code from CodeBlock"
    (let [block {:t "CodeBlock" :c [["" [] []] "const x = 42;"]}]
      (is (= "const x = 42;" (js/code-block-code block))))
    
    (testing "Non-CodeBlock returns nil"
      (let [para {:t "Para" :c []}]
        (is (nil? (js/code-block-code para)))))))

(deftest test-execute-javascript-basic
  (testing "Basic JavaScript execution"
    (let [result (js/execute-javascript "1 + 2 + 3")]
      (is (= "6" (:result result)))
      (is (nil? (:exception result)))))
  
  (testing "Console.log output capture"
    (let [result (js/execute-javascript "console.log('Hello'); console.log('World'); 42")]
      (is (= "42" (:result result)))
      (is (re-find #"Hello" (:output result)))
      (is (re-find #"World" (:output result)))
      (is (nil? (:exception result))))))

(deftest test-execute-javascript-es6
  (testing "ES6 const and let"
    (let [result (js/execute-javascript "const x = 10; let y = 20; x + y")]
      (is (= "30" (:result result)))
      (is (nil? (:exception result)))))
  
  (testing "Arrow functions"
    (let [result (js/execute-javascript "const add = (a, b) => a + b; add(5, 3)")]
      (is (= "8" (:result result)))
      (is (nil? (:exception result)))))
  
  (testing "Array methods"
    (let [result (js/execute-javascript "[1, 2, 3, 4, 5].reduce((a, b) => a + b, 0)")]
      (is (= "15" (:result result)))
      (is (nil? (:exception result))))))

(deftest test-execute-javascript-errors
  (testing "Syntax errors"
    (let [result (js/execute-javascript "const x = ;")]
      (is (some? (:exception result)))
      (is (nil? (:result result)))))
  
  (testing "Runtime errors"
    (let [result (js/execute-javascript "throw new Error('test error');")]
      (is (some? (:exception result)))
      (is (re-find #"test error" (.getMessage (:exception result)))))))

(deftest test-format-execution-result
  (testing "Format result with output"
    (let [formatted (js/format-execution-result
                      {:result "42"
                       :output "Hello\nWorld\n"
                       :error ""
                       :exception nil})]
      (is (re-find #"Output:" formatted))
      (is (re-find #"Hello" formatted))
      (is (re-find #"Result:" formatted))
      (is (re-find #"42" formatted))))
  
  (testing "Format error result"
    (let [formatted (js/format-execution-result
                      {:result nil
                       :output ""
                       :error ""
                       :exception (Exception. "Test error")})]
      (is (re-find #"ERROR:" formatted))
      (is (re-find #"Test error" formatted)))))

(deftest test-transform-javascript-exec-block
  (testing "Transform javascript-exec block"
    (let [block {:t "CodeBlock"
                 :c [["" ["javascript-exec"] []]
                     "console.log('test'); 42"]}
          result (js/transform-javascript-exec-block block)]
      (is (= "CodeBlock" (:t result)))
      (is (re-find #"Original code:" (second (:c result))))
      (is (re-find #"Execution result:" (second (:c result))))
      (is (re-find #"test" (second (:c result))))
      (is (re-find #"42" (second (:c result))))))
  
  (testing "Transform js-exec alias"
    (let [block {:t "CodeBlock"
                 :c [["" ["js-exec"] []]
                     "1 + 1"]}
          result (js/transform-javascript-exec-block block)]
      (is (= "CodeBlock" (:t result)))
      (is (re-find #"Result:" (second (:c result))))))
  
  (testing "Non-matching block passes through"
    (let [block {:t "CodeBlock"
                 :c [["" ["python"] []]
                     "print('test')"]}
          result (js/transform-javascript-exec-block block)]
      (is (= block result)))))

(deftest test-javascript-exec-filter-integration
  (testing "Filter processes multiple blocks"
    (let [ast {:t "Document"
               :c [{:t "CodeBlock"
                    :c [["" ["javascript-exec"] []] "1 + 1"]}
                   {:t "CodeBlock"
                    :c [["" ["python"] []] "print('unchanged')"]}
                   {:t "CodeBlock"
                    :c [["" ["js-exec"] []] "2 + 2"]}]}
          result (js/javascript-exec-filter ast)]
      ;; First block should be transformed
      (is (re-find #"Execution result:" (get-in result [:c 0 :c 1])))
      ;; Second block should be unchanged
      (is (= "print('unchanged')" (get-in result [:c 1 :c 1])))
      ;; Third block should be transformed
      (is (re-find #"Execution result:" (get-in result [:c 2 :c 1]))))))
