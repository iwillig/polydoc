(ns polydoc.filters.python-exec
  "Pandoc filter for executing Python code blocks using GraalVM Polyglot.
  
  Finds CodeBlock nodes with class 'python-exec' or 'py-exec' and executes
  the code using GraalVM's Python engine, replacing the block with the
  execution result.
  
  Usage from CLI:
  
    clojure -M:main filter -t python-exec -i input.json -o output.json
  
  Or with Pandoc pipeline:
  
    pandoc doc.md -t json | \\
      clojure -M:main filter -t python-exec | \\
      pandoc -f json -o output.html
  
  Markdown example:
  
    ```{.python-exec}
    print('Hello from Python!')
    numbers = [1, 2, 3, 4, 5]
    sum(numbers)
    ```
  
  This will be replaced with:
  
    ```
    # Original code:
    print('Hello from Python!')
    numbers = [1, 2, 3, 4, 5]
    sum(numbers)
    
    # Execution result:
    Output:
    Hello from Python!
    
    Result:
    15
    ```
  
  The filter:
  - Executes code using GraalVM Polyglot Python engine
  - Supports Python 3.x syntax
  - Captures print() output
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


(defn execute-python
  "Execute Python code using GraalVM Polyglot and capture output.
  
  Returns a map with:
  - :result - The result value (as string)
  - :output - Captured stdout (print(), etc.)
  - :error - Captured stderr (warnings)
  - :exception - Exception if thrown
  
  The code is executed in a sandboxed context with no host access."
  [code]
  (let [out (ByteArrayOutputStream.)
        err (ByteArrayOutputStream.)
        result (atom nil)
        exception (atom nil)]
    (try
      (with-open [ctx (-> (Context/newBuilder (into-array String ["python"]))
                          (.out out)
                          (.err err)
                          (.allowAllAccess false)  ; Sandboxed
                          (.build))]
        (let [value (.eval ctx "python" code)]
          ;; Use .asString() for string values to avoid extra quotes
          ;; Use .toString() for other types
          (reset! result (if (.isString value)
                          (.asString value)
                          (.toString value)))))
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


(defn transform-python-exec-block
  "Transform a python-exec CodeBlock by executing it.
  
  Replaces the block with a new CodeBlock containing:
  - The original code (for reference)
  - The execution output/result"
  [node]
  (if (and (= "CodeBlock" (core/node-type node))
           (or (has-class? (code-block-attrs node) "python-exec")
               (has-class? (code-block-attrs node) "py-exec")))
    (let [code (code-block-code node)
          attrs (code-block-attrs node)
          result (execute-python code)
          output (format-execution-result result)
          new-code (str "# Original code:\n"
                        code
                        "\n\n# Execution result:\n"
                        output)]
      (make-code-block attrs new-code))
    node))


(defn python-exec-filter
  "Pandoc filter that executes Python code blocks.
  
  Finds all CodeBlock nodes with class 'python-exec' or 'py-exec'
  and executes them, replacing the block with the execution result."
  [ast]
  (core/walk-ast transform-python-exec-block ast))


(defn main
  "Main entry point for CLI usage."
  [{:keys [input output]}]
  (core/execute-filter python-exec-filter input output))
