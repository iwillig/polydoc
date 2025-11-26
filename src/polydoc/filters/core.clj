(ns polydoc.filters.core
  "Core functionality for Pandoc filters.
  
  Provides:
  - JSON AST reading/writing
  - AST walking utilities
  - Filter composition
  - Error handling
  
  Example usage:
  
    ;; Read Pandoc AST from stdin
    (def ast (read-ast \"-\"))
    
    ;; Walk and transform nodes
    (def transformed
      (walk-ast
        (fn [node]
          (if (= (node-type node) \"Para\")
            (make-node \"Plain\" (node-content node))
            node))
        ast))
    
    ;; Write to stdout
    (write-ast transformed \"-\")
    
  See also:
  - polydoc.filters.clojure-exec for filter implementation examples
  - examples/ directory for complete filter examples"
  (:require
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.pprint]
   [clojure.walk :as walk]))


;; AST I/O Functions

(defn read-ast
  "Read Pandoc JSON AST from input.
   
   Input can be:
   - A string path to a file
   - \"-\" for stdin
   - An InputStream
   - A Reader
   
   Returns the parsed AST as Clojure data."
  [input]
  ;; Use read-str instead of read to avoid pushback buffer overflow
  ;; with large JSON documents from Pandoc
  (let [json-str (cond
                   (= input "-") (slurp *in*)
                   (string? input) (slurp input)
                   :else (slurp input))]
    (json/read-str json-str :key-fn keyword)))


(defn write-ast
  "Write Pandoc JSON AST to output.
   
   Output can be:
   - A string path to a file
   - \"-\" for stdout
   - An OutputStream
   - A Writer
   
   The AST is written as JSON."
  [ast output]
  (cond
    ;; File path - use with-open to ensure writer is closed
    (and (string? output) (not= output "-"))
    (with-open [writer (io/writer output)]
      (json/write ast writer)
      nil)

    ;; Stdout - write to current *out*
    (= output "-")
    (do
      (json/write ast *out*)
      (flush)
      nil)

    ;; Provided writer - write without closing
    :else
    (do
      (json/write ast output)
      nil)))


;; AST Walking Utilities

(defn ast-node?
  "Check if a value is a Pandoc AST node.
  
  A node is a map with a :t key (type tag)."
  [x]
  (and (map? x) (contains? x :t)))


(defn node-type
  "Get the type of an AST node."
  [node]
  (:t node))


(defn node-content
  "Get the content of an AST node."
  [node]
  (:c node))


(defn make-node
  "Create an AST node with type and content."
  [type content]
  {:t type :c content})


(defn walk-ast
  "Walk the AST and apply a function to each node.
  
  The function should take a node and return either:
  - A modified node
  - The original node (unchanged)
  - nil (to remove the node)
  
  Uses postwalk so children are processed before parents."
  [f ast]
  (walk/postwalk
   (fn [node]
     (if (ast-node? node)
       (f node)
       node))
   ast))


(defn filter-nodes
  "Filter AST nodes by type.
  
  Returns a sequence of all nodes matching the given type."
  [ast type]
  (let [matches (atom [])]
    (walk-ast
     (fn [node]
       (when (= (node-type node) type)
         (swap! matches conj node))
       node)
     ast)
    @matches))


;; Filter Composition

(defn compose-filters
  "Compose multiple filter functions into a single filter.
  
  Filters are applied left-to-right (like -> threading)."
  [& filters]
  (fn [ast]
    (reduce (fn [acc f] (f acc)) ast filters)))


;; Error Handling

(defn safe-filter
  "Wrap a filter function with error handling.
  
  If the filter throws an exception, logs the error and returns
  the original AST unchanged."
  [filter-fn]
  (fn [ast]
    (try
      (filter-fn ast)
      (catch Exception e
        (binding [*out* *err*]
          (println "ERROR in filter:" (.getMessage e))
          (println "Stack trace:")
          (.printStackTrace e *err*))
        ast))))


;; Filter Execution

(defn execute-filter
  "Execute a filter on input and write to output.
  
  Arguments:
  - filter-fn: Function that takes an AST and returns a modified AST
  - input: Input source (path, stdin, or reader)
  - output: Output destination (path, stdout, or writer)
  
  Returns nil on success, throws on error."
  [filter-fn input output]
  (let [ast (read-ast input)
        filtered (filter-fn ast)]
    (write-ast filtered output)))


;; Utilities

(defn debug-ast
  "Print AST structure for debugging.
  
  Prints to stderr to avoid interfering with stdout."
  [ast]
  (binding [*out* *err*]
    (println "AST structure:")
    (clojure.pprint/pprint ast))
  ast)
