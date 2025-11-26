(ns polydoc.book.metadata
  "Parse and validate polydoc.yml metadata files."
  (:require
   [clj-yaml.core :as yaml]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [malli.core :as m]
   [malli.error :as me]))


;; Malli schema for polydoc.yml validation
(def PolydocMetadata
  "Schema for polydoc.yml metadata structure.
  
  Note: Uses [:sequential ...] instead of [:vector ...] because clj-yaml
  returns lists, not vectors, for YAML sequences."
  [:map
   ;; Standard Pandoc metadata fields
   [:title :string]
   [:author {:optional true} :string]
   [:date {:optional true} :string]
   [:lang {:optional true} :string]
   [:description {:optional true} :string]
   [:toc {:optional true} :boolean]
   [:toc-depth {:optional true} :int]
   [:toc-title {:optional true} :string]
   [:css {:optional true} [:sequential :string]]

   ;; Book-specific configuration
   [:book {:optional true}
    [:map
     [:id :string]
     [:version {:optional true} :string]
     [:database {:optional true} :string]
     [:output-dir {:optional true} :string]
     [:filters {:optional true} [:sequential :string]]]]

   ;; Sections (files to include in book)
   [:sections
    [:sequential
     [:or
      :string  ;; Simple format: just file path
      [:map    ;; Extended format: file + options
       [:file :string]
       [:title {:optional true} :string]
       [:filters {:optional true} [:sequential :string]]
       [:metadata {:optional true} :map]]]]]])


(defn normalize-section
  "Normalize section entry to full map format.
  
  Converts string entries to maps:
    \"chapters/intro.md\" => {:file \"chapters/intro.md\"}
  
  Map entries are returned unchanged."
  [section]
  (if (string? section)
    {:file section}
    section))


(defn parse-metadata
  "Parse polydoc.yml file and validate against schema.
  
  Reads YAML file, parses it, validates structure with Malli,
  and normalizes sections to consistent map format.
  
  Returns validated metadata map with all sequences converted to vectors.
  Throws ex-info if validation fails."
  [yaml-file]
  (let [content (slurp yaml-file)
        data (yaml/parse-string content :keywords true)]

    ;; Validate with Malli
    (if (m/validate PolydocMetadata data)
      ;; Normalize and convert sequences to vectors
      (-> data
          ;; Convert css list to vector
          (update :css #(when % (vec %)))
          ;; Convert book.filters list to vector
          (update-in [:book :filters] #(when % (vec %)))
          ;; Convert sections list to vector and normalize each section
          (update :sections (fn [sections]
                              (mapv normalize-section sections))))
      ;; Validation failed - throw with humanized errors
      (throw (ex-info "Invalid polydoc.yml metadata"
                      {:errors (me/humanize (m/explain PolydocMetadata data))
                       :file yaml-file})))))


(defn resolve-file-path
  "Resolve file path relative to base directory.
  
  Returns canonical absolute path."
  [base-dir file-path]
  (.getCanonicalPath (io/file base-dir file-path)))


(defn resolve-sections
  "Resolve all section file paths relative to metadata file directory.
  
  Takes metadata map and the path to the polydoc.yml file.
  Updates all :file paths in :sections to absolute canonical paths."
  [metadata metadata-file]
  (let [base-dir (.getParent (io/file metadata-file))]
    (update metadata :sections
            (fn [sections]
              (mapv (fn [section]
                      (update section :file #(resolve-file-path base-dir %)))
                    sections)))))


(defn load-metadata
  "Load and parse polydoc.yml, resolving all file paths.
  
  Main entry point for loading book metadata.
  
  Steps:
  1. Parse YAML file
  2. Validate against schema
  3. Normalize sections
  4. Resolve all file paths to absolute paths
  
  Returns fully processed metadata map ready for book building.
  Throws ex-info if file doesn't exist or validation fails."
  [yaml-file]
  (when-not (.exists (io/file yaml-file))
    (throw (ex-info "polydoc.yml file not found"
                    {:file yaml-file})))

  (-> yaml-file
      parse-metadata
      (resolve-sections yaml-file)))


(defn get-book-id
  "Extract book ID from metadata.
  
  Returns the book.id field if present, otherwise generates
  a default ID from the title."
  [metadata]
  (or (get-in metadata [:book :id])
      (-> (:title metadata)
          str/lower-case
          (str/replace #"[^a-z0-9-]" "-")
          (str/replace #"-+" "-")
          (str/replace #"^-|-$" ""))))


(defn get-database-path
  "Extract database path from metadata.
  
  Returns the book.database field if present, otherwise
  returns default 'polydoc.db'."
  [metadata]
  (or (get-in metadata [:book :database])
      "polydoc.db"))


(defn get-filters
  "Extract global filters from metadata.
  
  Returns vector of filter names to apply to all sections
  (unless overridden at section level)."
  [metadata]
  (get-in metadata [:book :filters] []))


(defn get-output-dir
  "Extract output directory from metadata.
  
  Returns the book.output-dir field if present, otherwise
  returns default 'build/'."
  [metadata]
  (or (get-in metadata [:book :output-dir])
      "build/"))
