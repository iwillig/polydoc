(ns polydoc.filters.include
  "Pandoc filter for including external files.
  
  Finds CodeBlock nodes with class 'include' and replaces them with the
  contents of the specified file. Supports nested includes with cycle detection.
  
  Usage from CLI:
  
    clojure -M:main filter -t include -i input.json -o output.json
  
  Or with Pandoc pipeline:
  
    pandoc doc.md -t json | \\
      clojure -M:main filter -t include | \\
      pandoc -f json -o output.html
  
  Markdown examples:
  
    ```{.include}
    path/to/file.md
    ```
  
  With relative path resolution:
  
    ```{.include base=\"/docs\"}
    chapters/intro.md
    ```
  
  Include as code block (don't parse):
  
    ```{.include mode=code}
    examples/hello.clj
    ```
  
  The filter:
  - Resolves relative paths
  - Detects include cycles
  - Supports multiple include modes (parse, code, raw)
  - Handles missing files gracefully
  
  Attributes:
  - base: Base directory for relative paths (default: current directory)
  - mode: Include mode - 'parse' (default), 'code', or 'raw'
  - lang: Language for code mode syntax highlighting
  
  Include modes:
  - parse: Parse included file as Markdown (default)
  - code: Include as CodeBlock with syntax highlighting
  - raw: Include as RawBlock without parsing
  
  Example with code mode:
  
    ```{.include mode=code lang=clojure}
    src/example.clj
    ```
  
  See examples/ directory for more usage examples."
  (:require
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]
   [clojure.string :as str]
   [polydoc.filters.core :as core])
  (:import
   (java.nio.file
    Paths)))


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


(defn get-attr-value
  "Get value of a key-value attribute."
  [attrs key]
  (let [[_id _classes kvs] attrs]
    (some (fn [[k v]] (when (= k key) v)) kvs)))


(defn normalize-path
  "Normalize a file path, removing '..' and '.' components."
  [^String path]
  (-> (Paths/get path (into-array String []))
      .normalize
      .toString))


(defn resolve-path
  "Resolve a file path relative to a base directory.
  
  Args:
  - path: File path (relative or absolute)
  - base: Base directory (default: current directory)
  
  Returns absolute normalized path."
  [path base]
  (let [base-dir (or base ".")
        base-path (-> (io/file base-dir) .getAbsoluteFile .toPath)
        file-path (Paths/get path (into-array String []))]
    (-> (if (.isAbsolute file-path)
          file-path
          (.resolve base-path file-path))
        .normalize
        .toString)))


(defn read-file
  "Read file contents as string.
  
  Returns a map with:
  - :success - true if read succeeded
  - :content - file content string
  - :error - error message if failed"
  [^String path]
  (try
    (let [file (io/file path)]
      (if (.exists file)
        {:success true
         :content (slurp file)}
        {:success false
         :error (str "File not found: " path)}))
    (catch Exception e
      {:success false
       :error (str "Error reading file: " (.getMessage e))})))


(defn parse-markdown-to-ast
  "Parse Markdown content to Pandoc AST.
  
  Uses pandoc command-line tool to convert Markdown to JSON AST.
  
  Returns a map with:
  - :success - true if parsing succeeded
  - :blocks - parsed AST blocks
  - :error - error message if failed"
  [content]
  (try
    (let [result (shell/sh "pandoc" "-f" "markdown" "-t" "json"
                           :in content)]
      (if (zero? (:exit result))
        (let [ast (json/read-str (:out result) :key-fn keyword)
              blocks (:blocks ast)]
          {:success true
           :blocks blocks})
        {:success false
         :error (str "Pandoc failed: " (:err result))}))
    (catch Exception e
      {:success false
       :error (str "Error parsing Markdown: " (.getMessage e))})))


(defn make-error-block
  "Create a CodeBlock showing the error."
  [path error]
  (core/make-node
   "CodeBlock"
   [["" ["include-error"] []]
    (str "ERROR including file: " path "\n"
         error)]))


(defn make-code-block
  "Create a CodeBlock from file content."
  [content lang]
  (core/make-node
   "CodeBlock"
   [["" (if lang [lang] []) []]
    content]))


(defn make-raw-block
  "Create a RawBlock from file content."
  [content]
  (core/make-node
   "RawBlock"
   ["markdown" content]))


(defn detect-cycle
  "Check if including a file would create a cycle.
  
  Args:
  - path: File path being included
  - stack: Vector of currently processing file paths
  
  Returns true if cycle detected, false otherwise."
  [path stack]
  (boolean (some #(= path %) stack)))


(defn transform-include-block
  "Transform an include code block into file contents.
  
  If the block has class 'include', reads and includes the file.
  Otherwise returns the block unchanged.
  
  Args:
  - node: AST node to transform
  - opts: Options map with:
    - :include-stack - vector of currently processing files (for cycle detection)
    - :base-dir - base directory for relative paths"
  [node opts]
  (if-let [attrs (code-block-attrs node)]
    (if (has-class? attrs "include")
      (let [file-path-raw (str/trim (code-block-code node))
            base-dir (:base-dir opts ".")
            base-attr (get-attr-value attrs "base")
            effective-base (or base-attr base-dir)
            resolved-path (resolve-path file-path-raw effective-base)
            mode (or (get-attr-value attrs "mode") "parse")
            lang (get-attr-value attrs "lang")
            include-stack (:include-stack opts [])]

        ;; Check for cycles
        (if (detect-cycle resolved-path include-stack)
          (make-error-block file-path-raw
                            (str "Include cycle detected: "
                                 (str/join " -> " (conj include-stack resolved-path))))

          ;; Read file
          (let [read-result (read-file resolved-path)]
            (if (:success read-result)
              (let [content (:content read-result)]
                (case mode
                  "code"
                  (make-code-block content lang)

                  "raw"
                  (make-raw-block content)

                  "parse"
                  (let [parse-result (parse-markdown-to-ast content)]
                    (if (:success parse-result)
                      ;; Return blocks as a Div to keep them grouped
                      (core/make-node
                       "Div"
                       [["" ["included"] [["source" resolved-path]]]
                        (:blocks parse-result)])
                      (make-error-block file-path-raw (:error parse-result))))

                  ;; Unknown mode
                  (make-error-block file-path-raw
                                    (str "Unknown include mode: " mode))))

              ;; File read failed
              (make-error-block file-path-raw (:error read-result))))))

      ;; Not an include block, return unchanged
      node)

    ;; Not a CodeBlock, return unchanged
    node))


(defn include-filter
  "Main filter function for file inclusion.
  
  Walks the AST and transforms include code blocks to file contents.
  
  Args:
  - ast: Pandoc AST
  - opts: Options map (optional):
    - :base-dir - base directory for relative paths
    - :max-depth - maximum include depth (default: 10)"
  ([ast] (include-filter ast {}))
  ([ast opts]
   (let [max-depth (or (:max-depth opts) 10)
         current-depth (or (:current-depth opts) 0)]

     (if (>= current-depth max-depth)
       ;; Max depth reached, return ast unchanged
       ast

       ;; Process includes
       (core/walk-ast
        #(transform-include-block % (assoc opts :current-depth current-depth))
        ast)))))


(defn main
  "Main entry point for CLI usage."
  [{:keys [input output]}]
  (core/execute-filter include-filter input output))
