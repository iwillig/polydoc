(ns polydoc.book.metadata-test
  (:require [clojure.test :refer [deftest is testing]]
            [polydoc.book.metadata :as metadata]
            [clojure.string :as str])
  (:import [java.io File]))

(deftest test-normalize-section
  (testing "String section"
    (is (= {:file "chapters/intro.md"}
           (metadata/normalize-section "chapters/intro.md"))))
  
  (testing "Map section (unchanged)"
    (is (= {:file "chapters/intro.md"
            :title "Introduction"
            :filters ["clojure-exec"]}
           (metadata/normalize-section {:file "chapters/intro.md"
                                        :title "Introduction"
                                        :filters ["clojure-exec"]}))))
  
  (testing "Map section with metadata"
    (is (= {:file "advanced.md"
            :metadata {:difficulty "advanced"}}
           (metadata/normalize-section {:file "advanced.md"
                                        :metadata {:difficulty "advanced"}})))))

(deftest test-parse-metadata-minimal
  (testing "Minimal valid metadata"
    (let [yaml-content "title: \"Test Book\"\nsections:\n  - intro.md"
          temp-file (File/createTempFile "polydoc-test-" ".yml")]
      (try
        (spit temp-file yaml-content)
        (let [result (metadata/parse-metadata (.getPath temp-file))]
          (is (= "Test Book" (:title result)))
          (is (= [{:file "intro.md"}] (:sections result))))
        (finally
          (.delete temp-file))))))

(deftest test-parse-metadata-full
  (testing "Full metadata with all fields"
    (let [yaml-content (str "title: \"Polydoc Guide\"\n"
                           "author: \"Ivan Willig\"\n"
                           "date: \"2025-11-25\"\n"
                           "lang: \"en-US\"\n"
                           "description: \"A comprehensive guide\"\n"
                           "toc: true\n"
                           "toc-depth: 3\n"
                           "toc-title: \"Contents\"\n"
                           "css:\n"
                           "  - css/style.css\n"
                           "  - css/syntax.css\n"
                           "book:\n"
                           "  id: \"polydoc-guide\"\n"
                           "  version: \"1.0.0\"\n"
                           "  database: \"polydoc.db\"\n"
                           "  output-dir: \"build/\"\n"
                           "  filters:\n"
                           "    - clojure-exec\n"
                           "    - plantuml\n"
                           "sections:\n"
                           "  - intro.md\n"
                           "  - file: advanced.md\n"
                           "    title: \"Advanced Topics\"\n"
                           "    filters:\n"
                           "      - clojure-exec")
          temp-file (File/createTempFile "polydoc-test-" ".yml")]
      (try
        (spit temp-file yaml-content)
        (let [result (metadata/parse-metadata (.getPath temp-file))]
          (is (= "Polydoc Guide" (:title result)))
          (is (= "Ivan Willig" (:author result)))
          (is (= "2025-11-25" (:date result)))
          (is (= "en-US" (:lang result)))
          (is (= "A comprehensive guide" (:description result)))
          (is (true? (:toc result)))
          (is (= 3 (:toc-depth result)))
          (is (= "Contents" (:toc-title result)))
          (is (= ["css/style.css" "css/syntax.css"] (:css result)))
          
          (is (= "polydoc-guide" (get-in result [:book :id])))
          (is (= "1.0.0" (get-in result [:book :version])))
          (is (= "polydoc.db" (get-in result [:book :database])))
          (is (= "build/" (get-in result [:book :output-dir])))
          (is (= ["clojure-exec" "plantuml"] (get-in result [:book :filters])))
          
          (is (= 2 (count (:sections result))))
          (is (= {:file "intro.md"} (first (:sections result))))
          (is (= {:file "advanced.md"
                  :title "Advanced Topics"
                  :filters ["clojure-exec"]}
                 (second (:sections result)))))
        (finally
          (.delete temp-file))))))

(deftest test-parse-metadata-invalid
  (testing "Missing required title field"
    (let [yaml-content "sections:\n  - intro.md"
          temp-file (File/createTempFile "polydoc-test-" ".yml")]
      (try
        (spit temp-file yaml-content)
        (is (thrown? Exception
                     (metadata/parse-metadata (.getPath temp-file))))
        (finally
          (.delete temp-file)))))
  
  (testing "Missing required sections field"
    (let [yaml-content "title: \"Test\""
          temp-file (File/createTempFile "polydoc-test-" ".yml")]
      (try
        (spit temp-file yaml-content)
        (is (thrown? Exception
                     (metadata/parse-metadata (.getPath temp-file))))
        (finally
          (.delete temp-file)))))
  
  (testing "Invalid section format (number instead of string/map)"
    (let [yaml-content "title: \"Test\"\nsections:\n  - 123"
          temp-file (File/createTempFile "polydoc-test-" ".yml")]
      (try
        (spit temp-file yaml-content)
        (is (thrown? Exception
                     (metadata/parse-metadata (.getPath temp-file))))
        (finally
          (.delete temp-file))))))

(deftest test-resolve-file-path
  (testing "Relative path resolution"
    (let [base-dir "/tmp/polydoc"
          file-path "chapters/intro.md"
          resolved (metadata/resolve-file-path base-dir file-path)]
      (is (str/starts-with? resolved "/"))
      (is (str/includes? resolved "polydoc"))
      (is (str/ends-with? resolved "chapters/intro.md")))))

(deftest test-resolve-sections
  (testing "Resolve section file paths"
    (let [yaml-content "title: \"Test\"\nsections:\n  - intro.md\n  - file: advanced.md"
          temp-file (File/createTempFile "polydoc-test-" ".yml")]
      (try
        (spit temp-file yaml-content)
        (let [parsed (metadata/parse-metadata (.getPath temp-file))
              resolved (metadata/resolve-sections parsed (.getPath temp-file))]
          
          (is (= 2 (count (:sections resolved))))
          
          ;; Check that paths are now absolute
          (let [first-path (get-in resolved [:sections 0 :file])
                second-path (get-in resolved [:sections 1 :file])]
            (is (str/starts-with? first-path "/"))
            (is (str/starts-with? second-path "/"))
            (is (str/ends-with? first-path "intro.md"))
            (is (str/ends-with? second-path "advanced.md"))))
        (finally
          (.delete temp-file))))))

(deftest test-load-metadata
  (testing "Load metadata with path resolution"
    (let [yaml-content (str "title: \"Test Book\"\n"
                           "book:\n"
                           "  id: \"test-book\"\n"
                           "  filters:\n"
                           "    - clojure-exec\n"
                           "sections:\n"
                           "  - intro.md\n"
                           "  - file: chapter1.md\n"
                           "    title: \"Chapter 1\"")
          temp-file (File/createTempFile "polydoc-test-" ".yml")]
      (try
        (spit temp-file yaml-content)
        (let [result (metadata/load-metadata (.getPath temp-file))]
          (is (= "Test Book" (:title result)))
          (is (= "test-book" (get-in result [:book :id])))
          (is (= ["clojure-exec"] (get-in result [:book :filters])))
          (is (= 2 (count (:sections result))))
          
          ;; Verify paths are resolved
          (is (str/starts-with? (get-in result [:sections 0 :file]) "/"))
          (is (str/starts-with? (get-in result [:sections 1 :file]) "/")))
        (finally
          (.delete temp-file)))))
  
  (testing "File not found error"
    (is (thrown-with-msg? Exception #"not found"
                         (metadata/load-metadata "/nonexistent/polydoc.yml")))))

(deftest test-get-book-id
  (testing "Explicit book ID"
    (is (= "my-book"
           (metadata/get-book-id {:title "Test"
                                  :book {:id "my-book"}}))))
  
  (testing "Generated from title"
    (is (= "test-book"
           (metadata/get-book-id {:title "Test Book"})))
    (is (= "test-book-123"
           (metadata/get-book-id {:title "Test Book 123"})))
    (is (= "test-book"
           (metadata/get-book-id {:title "Test  Book!!"}))))
  
  (testing "No book section"
    (is (= "polydoc-guide"
           (metadata/get-book-id {:title "Polydoc Guide"})))))

(deftest test-get-database-path
  (testing "Explicit database path"
    (is (= "custom.db"
           (metadata/get-database-path {:book {:database "custom.db"}}))))
  
  (testing "Default database path"
    (is (= "polydoc.db"
           (metadata/get-database-path {})))
    (is (= "polydoc.db"
           (metadata/get-database-path {:book {}})))))

(deftest test-get-filters
  (testing "Explicit filters"
    (is (= ["clojure-exec" "plantuml"]
           (metadata/get-filters {:book {:filters ["clojure-exec" "plantuml"]}}))))
  
  (testing "No filters"
    (is (= []
           (metadata/get-filters {})))
    (is (= []
           (metadata/get-filters {:book {}})))))

(deftest test-get-output-dir
  (testing "Explicit output directory"
    (is (= "dist/"
           (metadata/get-output-dir {:book {:output-dir "dist/"}}))))
  
  (testing "Default output directory"
    (is (= "build/"
           (metadata/get-output-dir {})))
    (is (= "build/"
           (metadata/get-output-dir {:book {}})))))
