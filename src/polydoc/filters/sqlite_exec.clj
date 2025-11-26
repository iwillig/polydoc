(ns polydoc.filters.sqlite-exec
  "Pandoc filter for executing SQLite queries.
  
  Finds CodeBlock nodes with class 'sqlite-exec' or 'sqlite' and executes
  the SQL queries, replacing the block with formatted query results.
  
  Usage from CLI:
  
    clojure -M:main filter -t sqlite-exec -i input.json -o output.json
  
  Or with Pandoc pipeline:
  
    pandoc doc.md -t json | \\
      clojure -M:main filter -t sqlite-exec | \\
      pandoc -f json -o output.html
  
  Markdown example:
  
    ```{.sqlite-exec db=\"mydata.db\"}
    SELECT name, count(*) as total
    FROM users
    GROUP BY name
    ORDER BY total DESC
    LIMIT 5;
    ```
  
  This will be replaced with a formatted table showing query results.
  
  The filter:
  - Executes SQL queries against SQLite databases
  - Supports in-memory databases (default)
  - Accepts db attribute for file-based databases
  - Formats results as Pandoc tables or text
  - Handles errors gracefully
  
  Attributes:
  - db: Path to SQLite database file (optional, defaults to in-memory :memory:)
  - format: Output format - 'table' or 'text' (default: 'table')
  
  See examples/ directory for more usage examples."
  (:require
    [clojure.string :as str]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]
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


(defn get-attr-value
  "Get value of an attribute from CodeBlock attrs.
  
  Attrs format: [id classes [[key val] ...]]"
  [attrs key]
  (let [[_id _classes kvs] attrs]
    (some (fn [[k v]] (when (= k key) v)) kvs)))


(defn execute-sql
  "Execute SQL query against SQLite database.
  
  Options:
  - :db - Database path (defaults to :memory:)
  - :sql - SQL query string
  
  Returns a map with:
  - :result - Vector of result maps
  - :error - Error message if query failed
  - :exception - Exception if thrown"
  [{:keys [db sql]}]
  (let [db-spec {:dbtype "sqlite"
                 :dbname (or db ":memory:")}
        result (atom [])
        error (atom nil)
        exception (atom nil)]
    (try
      (with-open [conn (jdbc/get-connection db-spec)]
        (reset! result
                (jdbc/execute! conn
                               [sql]
                               {:builder-fn rs/as-unqualified-lower-maps})))
      (catch Exception e
        (reset! exception e)
        (reset! error (.getMessage e))))
    {:result @result
     :error @error
     :exception @exception}))


(defn format-table-row
  "Format a single row as Pandoc table cells."
  [row columns]
  (mapv (fn [col]
          {:t "Plain"
           :c [{:t "Str" :c (str (get row col ""))}]})
        columns))


(defn make-pandoc-table
  "Create a Pandoc Table node from query results.
  
  Pandoc Table structure (simplified):
  {:t \"Table\"
   :c [attrs
       caption
       colspecs
       head
       bodies
       foot]}"
  [results]
  (if (empty? results)
    {:t "Para"
     :c [{:t "Str" :c "No results"}]}
    (let [columns (vec (keys (first results)))
          num-cols (count columns)

          ;; Header row
          header-cells (mapv (fn [col]
                               {:t "Plain"
                                :c [{:t "Str" :c (str col)}]})
                             columns)

          ;; Data rows
          data-rows (mapv #(format-table-row % columns) results)

          ;; Column specifications: [align width]
          ;; AlignDefault, ColWidthDefault
          colspecs (vec (repeat num-cols
                                [{:t "AlignDefault"} {:t "ColWidthDefault"}]))

          ;; Table attributes
          attrs ["" [] []]

          ;; Caption (empty)
          caption {:t "Caption" :c [nil []]}

          ;; Table head
          head {:t "TableHead"
                :c [["" [] []]  ; head attrs
                    [{:t "Row"
                      :c [["" [] []]  ; row attrs
                          header-cells]}]]}

          ;; Table body
          bodies [{:t "TableBody"
                   :c [["" [] []]  ; body attrs
                       0           ; row head columns
                       []          ; head rows
                       (mapv (fn [row-cells]
                               {:t "Row"
                                :c [["" [] []]  ; row attrs
                                    row-cells]})
                             data-rows)]}]

          ;; Table foot (empty)
          foot {:t "TableFoot" :c [["" [] []] []]}]

      {:t "Table"
       :c [attrs caption colspecs head bodies foot]})))


(defn format-text-results
  "Format query results as plain text table."
  [results]
  (if (empty? results)
    "No results"
    (let [columns (keys (first results))
          col-widths (reduce
                       (fn [widths row]
                         (merge-with max
                                     widths
                                     (zipmap columns
                                             (map #(count (str (get row %))) columns))))
                       (zipmap columns (map #(count (str %)) columns))
                       results)

          format-row (fn [row]
                       (str/join " | "
                                 (map #(let [val (str (get row %))
                                             width (get col-widths %)]
                                         (format (str "%-" width "s") val))
                                      columns)))

          header (str/join " | "
                           (map #(let [width (get col-widths %)]
                                   (format (str "%-" width "s") (str %)))
                                columns))
          separator (str/join "-+-"
                              (map #(str/join (repeat (get col-widths %) "-"))
                                   columns))]

      (str/join "\n"
                (concat [header separator]
                        (map format-row results))))))


(defn transform-sqlite-exec-block
  "Transform a sqlite-exec CodeBlock by executing the SQL.
  
  Replaces the block with query results formatted as a Pandoc table."
  [node]
  (if (and (= "CodeBlock" (core/node-type node))
           (or (has-class? (code-block-attrs node) "sqlite-exec")
               (has-class? (code-block-attrs node) "sqlite")))
    (let [code (code-block-code node)
          attrs (code-block-attrs node)
          db-path (get-attr-value attrs "db")
          format-type (or (get-attr-value attrs "format") "table")

          result (execute-sql {:db db-path :sql code})]

      (if (:exception result)
        ;; Error: return CodeBlock with error message
        (core/make-node "CodeBlock"
                        [attrs
                         (str ";; SQL Query:\n"
                              code
                              "\n\n;; ERROR:\n"
                              (:error result))])

        ;; Success: format results
        (if (= format-type "table")
          (make-pandoc-table (:result result))
          (core/make-node "CodeBlock"
                          [attrs
                           (str ";; SQL Query:\n"
                                code
                                "\n\n;; Results:\n"
                                (format-text-results (:result result)))]))))
    node))


(defn sqlite-exec-filter
  "Pandoc filter that executes SQLite queries.
  
  Finds all CodeBlock nodes with class 'sqlite-exec' or 'sqlite' and
  executes them, replacing the block with formatted query results."
  [ast]
  (core/walk-ast transform-sqlite-exec-block ast))


(defn main
  "Main entry point for CLI usage."
  [{:keys [input output]}]
  (core/execute-filter sqlite-exec-filter input output))
