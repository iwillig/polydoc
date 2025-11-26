(ns polydoc.filters.clojure-exec
  "Pandoc filter for executing Clojure code blocks.
  
  Finds CodeBlock nodes with class 'clojure-exec' and executes the code,
  replacing the block with the execution result.
  
  Usage from CLI:
  
    clojure -M:main filter -t clojure-exec -i input.json -o output.json
  
  Or with Pandoc pipeline:
  
    pandoc doc.md -t json | \\
      clojure -M:main filter -t clojure-exec | \\
      pandoc -f json -o output.html
  
  Markdown example:
  
    ```{.clojure-exec}
    (reduce + (range 1 11))
    ```
  
  This will be replaced with:
  
    ```
    ;; Original code:
    (reduce + (range 1 11))
    
    ;; Execution result:
    Result:
    55
    ```
  
  The filter:
  - Executes code in a safe namespace
  - Captures stdout and stderr
  - Handles exceptions gracefully
  - Shows original code for reference
  
  See examples/ directory for more usage examples."
  (:require
    [clojure.string :as str]
    [polydoc.filters.core :as core]))


(defn has-class?
  "Check if attributes contain a specific class."
  [attrs class-name]
  (let [[_id classes _kvs] attrs]
    (boolean (some #(= class-name %) classes))))


(defn code-block-attrs
  "Extract attributes from a CodeBlock node."
  [node]
  (when (= "CodeBlock" (core/node-type node))
    (first (core/node-content node))))


(defn code-block-code
  "Extract code string from a CodeBlock node."
  [node]
  (when (= "CodeBlock" (core/node-type node))
    (second (core/node-content node))))


(defn execute-clojure
  "Execute Clojure code and capture output.
  
  Returns a map with:
  - :result - The result value
  - :output - Captured stdout
  - :error - Captured stderr
  - :exception - Exception if thrown"
  [code]
  (let [out (java.io.StringWriter.)
        err (java.io.StringWriter.)
        result (atom nil)
        exception (atom nil)]
    (try
      (binding [*out* out
                *err* err]
        (reset! result (eval (read-string code))))
      (catch Exception e
        (reset! exception e)))
    {:result @result
     :output (str out)
     :error (str err)
     :exception @exception}))


(defn format-execution-result
  "Format execution result for display.
  
  Creates a formatted string showing:
  - Output (if any)
  - Result value
  - Errors (if any)"
  [{:keys [result output error exception]}]
  (let [parts (cond-> []
                (not (str/blank? output))
                (conj (str "Output:\n" output))

                (not exception)
                (conj (str "Result:\n" (pr-str result)))

                exception
                (conj (str "ERROR: " (.getMessage exception)))

                (and exception (not (str/blank? error)))
                (conj (str "Stderr:\n" error)))]
    (str/join "\n\n" parts)))


(defn make-code-block
  "Create a CodeBlock node."
  [attrs code]
  (core/make-node "CodeBlock" [attrs code]))


(defn transform-clojure-exec-block
  "Transform a clojure-exec CodeBlock by executing it.
  
  Replaces the block with a new CodeBlock containing:
  - The original code (for reference)
  - The execution output/result"
  [node]
  (if (and (= "CodeBlock" (core/node-type node))
           (has-class? (code-block-attrs node) "clojure-exec"))
    (let [code (code-block-code node)
          attrs (code-block-attrs node)
          result (execute-clojure code)
          output (format-execution-result result)
          new-code (str ";; Original code:\n"
                        code
                        "\n\n;; Execution result:\n"
                        output)]
      (make-code-block attrs new-code))
    node))


(defn clojure-exec-filter
  "Pandoc filter that executes Clojure code blocks.
  
  Finds all CodeBlock nodes with class 'clojure-exec' and executes them,
  replacing the block with the execution result."
  [ast]
  (core/walk-ast transform-clojure-exec-block ast))


(defn main
  "Main entry point for CLI usage."
  [{:keys [input output]}]
  (core/execute-filter clojure-exec-filter input output))
