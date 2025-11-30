(ns polydoc.generator.book
  "Generate new book projects."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]))


(defn slugify
  "Convert title to book-id slug.
  
  Converts to lowercase, replaces spaces with hyphens, removes special chars."
  [title]
  (-> title
      str/lower-case
      (str/replace #"[^\w\s-]" "")
      (str/replace #"\s+" "-")
      (str/replace #"-+" "-")
      (str/replace #"^-|-$" "")))


(defn generate-polydoc-yml
  "Generate polydoc.yml content.
  
  Creates minimal or full configuration based on provided options."
  [{:keys [title author book-id]}]
  (let [id (or book-id (slugify title))]
    (if author
      (str "title: \"" title "\"\n"
           "author: \"" author "\"\n"
           "book:\n"
           "  id: \"" id "\"\n"
           "  version: \"0.1.0\"\n"
           "sections:\n"
           "  - sections/01-introduction.md\n"
           "  - sections/02-getting-started.md\n"
           "  - sections/03-advanced.md\n")
      (str "title: \"" title "\"\n"
           "sections:\n"
           "  - sections/01-introduction.md\n"
           "  - sections/02-getting-started.md\n"
           "  - sections/03-advanced.md\n"))))


(defn generate-introduction-md
  "Generate introduction markdown."
  [title]
  (str "# Introduction\n"
       "\n"
       "Welcome to " title "!\n"
       "\n"
       "This is a starter template to help you get started with Polydoc documentation.\n"
       "\n"
       "## What is Polydoc?\n"
       "\n"
       "Polydoc is a JVM-native Pandoc documentation system that allows you to:\n"
       "\n"
       "- Execute code blocks during documentation generation\n"
       "- Build searchable documentation books\n"
       "- Apply custom filters to your content\n"
       "\n"
       "## Getting Started\n"
       "\n"
       "Edit the `polydoc.yml` file to configure your book settings.\n"
       "\n"
       "Add your content in the `sections/` directory.\n"
       "\n"
       "Build your book with:\n"
       "\n"
       "```bash\n"
       "clojure -M:main book -c polydoc.yml -o output/\n"
       "```\n"))


(defn generate-getting-started-md
  "Generate getting started markdown."
  []
  (str "# Getting Started\n"
       "\n"
       "Start writing your documentation here.\n"
       "\n"
       "## Section Structure\n"
       "\n"
       "You can organize your content into multiple sections.\n"
       "\n"
       "Each section is a separate markdown file referenced in `polydoc.yml`.\n"
       "\n"
       "## Adding New Sections\n"
       "\n"
       "1. Create a new markdown file in the `sections/` directory\n"
       "2. Add it to the `sections:` list in `polydoc.yml`\n"
       "3. Rebuild your book\n"))


(defn generate-advanced-md
  "Generate advanced topics markdown."
  []
  (str "# Advanced Topics\n"
       "\n"
       "## Code Execution\n"
       "\n"
       "Polydoc supports executing code blocks during build.\n"
       "\n"
       "```clojure clojure-exec\n"
       "(+ 1 2 3)\n"
       "```\n"
       "\n"
       "## Filters\n"
       "\n"
       "Available filters:\n"
       "\n"
       "- `clojure-exec` - Execute Clojure code\n"
       "- `sqlite-exec` - Execute SQL queries\n"
       "- `plantuml` - Render UML diagrams\n"
       "- `include` - Include external files\n"
       "\n"
       "Configure filters in `polydoc.yml`:\n"
       "\n"
       "```yaml\n"
       "book:\n"
       "  filters:\n"
       "    - clojure-exec\n"
       "    - plantuml\n"
       "```\n"))


(defn generate-readme-md
  "Generate README.md for the project."
  [title]
  (str "# " title "\n"
       "\n"
       "Documentation project built with Polydoc.\n"
       "\n"
       "## Building\n"
       "\n"
       "Build the documentation:\n"
       "\n"
       "```bash\n"
       "clojure -M:main book -c polydoc.yml -o output/\n"
       "```\n"
       "\n"
       "## Structure\n"
       "\n"
       "- `polydoc.yml` - Book configuration\n"
       "- `sections/` - Markdown content files\n"
       "- `output/` - Generated output (after build)\n"
       "\n"
       "## Configuration\n"
       "\n"
       "Edit `polydoc.yml` to customize:\n"
       "\n"
       "- Book title, author, and metadata\n"
       "- Sections to include\n"
       "- Filters to apply\n"
       "- Output settings\n"))


(defn create-directory-structure
  "Create directory structure for new book."
  [output-dir]
  (let [sections-dir (io/file output-dir "sections")]
    (.mkdirs sections-dir)))


(defn write-file
  "Write content to file."
  [file-path content]
  (io/make-parents file-path)
  (spit file-path content))


(defn generate-book
  "Generate a new book project.
  
  Options:
  - :output-dir - Directory to create book in (default: current dir)
  - :title - Book title (default: 'My Book')
  - :author - Author name (optional)
  - :book-id - Book ID (optional, auto-generated from title)
  
  Returns a map with:
  - :output-dir - Absolute path to generated book
  - :title - Book title
  - :book-id - Book ID
  - :files - List of generated files"
  [{:keys [output-dir title author book-id]}]
  (let [output-dir (or output-dir ".")
        title (or title "My Book")
        book-id (or book-id (slugify title))]
    
    ;; Create directory structure
    (create-directory-structure output-dir)
    
    ;; Generate files
    (write-file (io/file output-dir "polydoc.yml")
                (generate-polydoc-yml {:title title
                                       :author author
                                       :book-id book-id}))
    
    (write-file (io/file output-dir "sections" "01-introduction.md")
                (generate-introduction-md title))
    
    (write-file (io/file output-dir "sections" "02-getting-started.md")
                (generate-getting-started-md))
    
    (write-file (io/file output-dir "sections" "03-advanced.md")
                (generate-advanced-md))
    
    (write-file (io/file output-dir "README.md")
                (generate-readme-md title))
    
    ;; Return summary
    {:output-dir (.getCanonicalPath (io/file output-dir))
     :title title
     :book-id book-id
     :files ["polydoc.yml"
             "sections/01-introduction.md"
             "sections/02-getting-started.md"
             "sections/03-advanced.md"
             "README.md"]}))
