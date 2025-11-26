(ns polydoc.filters.javascript-exec
  "Pandoc filter for executing JavaScript code blocks using GraalVM Polyglot.
  
  Finds CodeBlock nodes with class 'javascript-exec' or 'js-exec' and executes
  the code using GraalVM's JavaScript engine, replacing the block with the
  execution result.
  
  Usage from CLI:
  
    clojure -M:main filter -t javascript-exec -i input.json -o output.json
  
  Or with Pandoc pipeline:
  
    pandoc doc.md -t json | \\
      clojure -M:main filter -t javascript-exec | \\
      pandoc -f json -o output.html
  
  Markdown example:
  
    ```{.javascript-exec}
    console.log('Hello from JavaScript!');
    const sum = [1, 2, 3, 4, 5].reduce((a, b) => a + b, 0);
    sum
    ```
  
  This will be replaced with:
  
    ```
    // Original code:
    console.log('Hello from JavaScript!');
    const sum = [1, 2, 3, 4, 5].reduce((a, b) => a + b, 0);
    sum
    
    // Execution result:
    Output:
    Hello from JavaScript!
    
    Result:
    15
    ```
  
  The filter:
  - Executes code using GraalVM Polyglot JavaScript engine
  - Supports ES6+ syntax (const, let, arrow functions, etc.)
  - Captures console.log output
  - Handles exceptions gracefully
  - Shows original code for reference
  - Sandboxed execution (no host access by default)
  
  See examples/ directory for more usage examples."
  (:require
    [clojure.string :as str]
    [polydoc.filters.core :as core])
  (:import
    (java.io
      ByteArrayOutputStream)
    (org.graalvm.polyglot
      Context)))


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


(defn execute-javascript
  "Execute JavaScript code using GraalVM Polyglot and capture output.
  
  Returns a map with:
  - :result - The result value (as string)
  - :output - Captured stdout (console.log, etc.)
  - :error - Captured stderr (warnings)
  - :exception - Exception if thrown
  
  The code is executed in a sandboxed context with no host access."
  [code]
  (let [out (ByteArrayOutputStream.)
        err (ByteArrayOutputStream.)
        result (atom nil)
        exception (atom nil)]
    (try
      (with-open [ctx (-> (Context/newBuilder (into-array String ["js"]))
                          (.out out)
                          (.err err)
                          (.allowAllAccess false)  ; Sandboxed
                          (.build))]
        (let [value (.eval ctx "js" code)]
          (reset! result (.toString value))))
      (catch Exception e
        (reset! exception e)))
    {:result @result
     :output (.toString out)
     :error (.toString err)
     :exception @exception}))


(defn format-execution-result
  "Format execution result for display.
  
  Creates a formatted string showing:
  - Output (if any)
  - Result value
  - Errors (if any)"
  [{:keys [result output error exception]}]
  (let [;; Filter out GraalVM warnings from stderr
        filtered-error (when error
                         (str/join "\n"
                                   (filter #(not (or (str/includes? % "Truffle log output")
                                                     (str/includes? % "polyglot engine")
                                                     (str/includes? % "JVMCI")
                                                     (str/includes? % "WarnInterpreterOnly")))
                                           (str/split-lines error))))
        parts (cond-> []
                (not (str/blank? output))
                (conj (str "Output:\n" output))

                (not exception)
                (conj (str "Result:\n" result))

                exception
                (conj (str "ERROR: " (.getMessage exception)))

                (and (not exception) (not (str/blank? filtered-error)))
                (conj (str "Warnings:\n" filtered-error)))]
    (str/join "\n\n" parts)))


(defn make-code-block
  "Create a CodeBlock node."
  [attrs code]
  (core/make-node "CodeBlock" [attrs code]))


(defn transform-javascript-exec-block
  "Transform a javascript-exec CodeBlock by executing it.
  
  Replaces the block with a new CodeBlock containing:
  - The original code (for reference)
  - The execution output/result"
  [node]
  (if (and (= "CodeBlock" (core/node-type node))
           (or (has-class? (code-block-attrs node) "javascript-exec")
               (has-class? (code-block-attrs node) "js-exec")))
    (let [code (code-block-code node)
          attrs (code-block-attrs node)
          result (execute-javascript code)
          output (format-execution-result result)
          new-code (str "// Original code:\n"
                        code
                        "\n\n// Execution result:\n"
                        output)]
      (make-code-block attrs new-code))
    node))


(defn javascript-exec-filter
  "Pandoc filter that executes JavaScript code blocks.
  
  Finds all CodeBlock nodes with class 'javascript-exec' or 'js-exec'
  and executes them, replacing the block with the execution result."
  [ast]
  (core/walk-ast transform-javascript-exec-block ast))


(defn main
  "Main entry point for CLI usage."
  [{:keys [input output]}]
  (core/execute-filter javascript-exec-filter input output))
