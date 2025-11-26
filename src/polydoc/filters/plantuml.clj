(ns polydoc.filters.plantuml
  "Pandoc filter for rendering PlantUML diagrams.
  
  Finds CodeBlock nodes with class 'plantuml' or 'uml' and renders them
  to SVG images using the PlantUML command-line tool.
  
  Usage from CLI:
  
    clojure -M:main filter -t plantuml -i input.json -o output.json
  
  Or with Pandoc pipeline:
  
    pandoc doc.md -t json | \\
      clojure -M:main filter -t plantuml | \\
      pandoc -f json -o output.html
  
  Markdown example:
  
    ```{.plantuml}
    @startuml
    Alice -> Bob: Hello
    Bob -> Alice: Hi!
    @enduml
    ```
  
  This will be replaced with an Image node containing the rendered SVG.
  
  The filter:
  - Shells out to `plantuml` command
  - Uses pipe mode for efficiency
  - Generates SVG format by default
  - Handles errors gracefully
  - Supports attributes for output format
  
  Attributes:
  - format: Output format (svg, png, txt, pdf) - default: svg
  
  Example with custom format:
  
    ```{.plantuml format=png}
    @startuml
    class User
    @enduml
    ```
  
  See examples/ directory for more usage examples."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [polydoc.filters.core :as core])
  (:import
   (java.util
    Base64)))


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


(defn format->plantuml-flag
  "Convert format name to PlantUML command-line flag."
  [format]
  (case (str/lower-case (or format "svg"))
    "svg" "-tsvg"
    "png" "-tpng"
    "txt" "-ttxt"
    "pdf" "-tpdf"
    "eps" "-teps"
    "latex" "-tlatex"
    "utxt" "-tutxt"
    "-tsvg")) ;; default

(defn render-plantuml
  "Render PlantUML code to image using command-line tool.
  
  Returns a map with:
  - :success - true if rendering succeeded
  - :output - rendered image data (base64 for binary, text for txt)
  - :format - output format
  - :error - error message if failed"
  [{:keys [code format]}]
  (let [format-flag (format->plantuml-flag format)
        format-str (or format "svg")
        is-text? (contains? #{"txt" "utxt"} (str/lower-case format-str))]
    (try
      ;; Use pipe mode: stdin -> stdout
      (let [process (-> (ProcessBuilder. ["plantuml" "-pipe" format-flag])
                        (.redirectError java.lang.ProcessBuilder$Redirect/INHERIT)
                        .start)
            stdin (.getOutputStream process)
            stdout (.getInputStream process)]

        ;; Write PlantUML code to stdin
        (with-open [writer (io/writer stdin)]
          (.write writer code))

        ;; Read output
        (let [output-bytes (.readAllBytes stdout)
              exit-code (.waitFor process)]

          (if (zero? exit-code)
            {:success true
             :output (if is-text?
                       (String. output-bytes "UTF-8")
                       (.encodeToString (Base64/getEncoder) output-bytes))
             :format format-str
             :is-text is-text?}
            {:success false
             :error (str "PlantUML exited with code " exit-code)
             :format format-str})))

      (catch Exception e
        {:success false
         :error (str "Failed to execute PlantUML: " (.getMessage e))
         :format format-str}))))


(defn make-error-block
  "Create a CodeBlock showing the error."
  [code error]
  (core/make-node
   "CodeBlock"
   [["" ["plantuml-error"] []]
    (str "ERROR rendering PlantUML:\n"
         error
         "\n\nOriginal code:\n"
         code)]))


(defn make-image-node
  "Create a Pandoc Image node from rendered output."
  [output format is-text? _code]
  (if is-text?
    ;; For text output, use CodeBlock
    (core/make-node
     "CodeBlock"
     [["" ["plantuml-output"] []]
      output])

    ;; For binary output, use Image with data URI
    (let [mime-type (case (str/lower-case format)
                      "svg" "image/svg+xml"
                      "png" "image/png"
                      "pdf" "application/pdf"
                      "eps" "application/postscript"
                      "image/svg+xml")
          data-uri (str "data:" mime-type ";base64," output)]
      (core/make-node
       "Para"
       [(core/make-node
         "Image"
         [["" ["plantuml"] []]  ;; attrs
          []                     ;; caption (empty)
          [data-uri ""]])]))))   ;; target (url, title)

(defn transform-plantuml-block
  "Transform a PlantUML code block into an image.
  
  If the block has class 'plantuml' or 'uml', renders it.
  Otherwise returns the block unchanged."
  [node]
  (if-let [attrs (code-block-attrs node)]
    (if (or (has-class? attrs "plantuml")
            (has-class? attrs "uml"))
      (let [code (code-block-code node)
            format (get-attr-value attrs "format")
            result (render-plantuml {:code code :format format})]

        (if (:success result)
          (make-image-node (:output result)
                           (:format result)
                           (:is-text result)
                           code)
          (make-error-block code (:error result))))

      ;; Not a PlantUML block, return unchanged
      node)

    ;; Not a CodeBlock, return unchanged
    node))


(defn plantuml-filter
  "Main filter function for PlantUML rendering.
  
  Walks the AST and transforms PlantUML code blocks to images."
  [ast]
  (core/walk-ast transform-plantuml-block ast))


(defn main
  "Main entry point for CLI usage."
  [{:keys [input output]}]
  (core/execute-filter plantuml-filter input output))
