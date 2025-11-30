(ns polydoc.generator.book-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [polydoc.book.metadata :as metadata]
   [polydoc.generator.book :as gen]))


(deftest test-slugify
  (testing "Convert titles to slugs"
    (is (= "my-book" (gen/slugify "My Book")))
    (is (= "my-book" (gen/slugify "My Book!")))
    (is (= "my-book-123" (gen/slugify "My Book 123")))
    (is (= "test-book" (gen/slugify "Test  Book!!!")))
    (is (= "hello-world" (gen/slugify "Hello-World")))))


(deftest test-generate-polydoc-yml
  (testing "Generate minimal polydoc.yml"
    (let [yml (gen/generate-polydoc-yml {:title "Test Book"})]
      (is (.contains yml "title: \"Test Book\""))
      (is (.contains yml "sections:"))
      (is (not (.contains yml "author:")))))
  
  (testing "Generate full polydoc.yml with author"
    (let [yml (gen/generate-polydoc-yml {:title "Test Book"
                                          :author "Test Author"})]
      (is (.contains yml "title: \"Test Book\""))
      (is (.contains yml "author: \"Test Author\""))
      (is (.contains yml "book:"))
      (is (.contains yml "id: \"test-book\""))))
  
  (testing "Use provided book-id"
    (let [yml (gen/generate-polydoc-yml {:title "Test Book"
                                          :author "Test Author"
                                          :book-id "custom-id"})]
      (is (.contains yml "id: \"custom-id\"")))))


(deftest test-generate-introduction-md
  (testing "Generate introduction markdown"
    (let [md (gen/generate-introduction-md "My Book")]
      (is (.contains md "# Introduction"))
      (is (.contains md "Welcome to My Book!"))
      (is (.contains md "Polydoc"))
      (is (.contains md "Getting Started")))))


(deftest test-generate-getting-started-md
  (testing "Generate getting started markdown"
    (let [md (gen/generate-getting-started-md)]
      (is (.contains md "# Getting Started"))
      (is (.contains md "Section Structure")))))


(deftest test-generate-advanced-md
  (testing "Generate advanced topics markdown"
    (let [md (gen/generate-advanced-md)]
      (is (.contains md "# Advanced Topics"))
      (is (.contains md "Code Execution"))
      (is (.contains md "clojure-exec"))
      (is (.contains md "Filters")))))


(deftest test-generate-readme-md
  (testing "Generate README markdown"
    (let [md (gen/generate-readme-md "My Book")]
      (is (.contains md "# My Book"))
      (is (.contains md "Polydoc"))
      (is (.contains md "Building")))))


(defn delete-directory-recursive
  "Delete directory and all its contents."
  [dir]
  (when (.exists dir)
    (doseq [file (reverse (file-seq dir))]
      (.delete file))))


(deftest test-generate-book
  (testing "Generate book structure with author"
    (let [temp-dir (io/file (System/getProperty "java.io.tmpdir")
                             (str "polydoc-test-" (System/currentTimeMillis)))
          result (gen/generate-book {:output-dir (.getPath temp-dir)
                                      :title "Test Book"
                                      :author "Test Author"})]
      (try
        ;; Check return value
        (is (= "Test Book" (:title result)))
        (is (= "test-book" (:book-id result)))
        (is (= 5 (count (:files result))))
        
        ;; Check files exist
        (is (.exists (io/file temp-dir "polydoc.yml")))
        (is (.exists (io/file temp-dir "sections" "01-introduction.md")))
        (is (.exists (io/file temp-dir "sections" "02-getting-started.md")))
        (is (.exists (io/file temp-dir "sections" "03-advanced.md")))
        (is (.exists (io/file temp-dir "README.md")))
        
        ;; Check polydoc.yml content
        (let [yml-content (slurp (io/file temp-dir "polydoc.yml"))]
          (is (.contains yml-content "Test Book"))
          (is (.contains yml-content "Test Author"))
          (is (.contains yml-content "test-book")))
        
        ;; Validate generated polydoc.yml
        (let [config-path (.getPath (io/file temp-dir "polydoc.yml"))
              loaded-metadata (metadata/load-metadata config-path)]
          (is (= "Test Book" (:title loaded-metadata)))
          (is (= "Test Author" (:author loaded-metadata)))
          (is (= 3 (count (:sections loaded-metadata)))))
        
        (finally
          ;; Cleanup
          (delete-directory-recursive temp-dir)))))
  
  (testing "Generate book structure without author (minimal)"
    (let [temp-dir (io/file (System/getProperty "java.io.tmpdir")
                             (str "polydoc-test-" (System/currentTimeMillis)))
          result (gen/generate-book {:output-dir (.getPath temp-dir)
                                      :title "Minimal Book"})]
      (try
        ;; Check return value
        (is (= "Minimal Book" (:title result)))
        (is (= "minimal-book" (:book-id result)))
        
        ;; Check polydoc.yml content
        (let [yml-content (slurp (io/file temp-dir "polydoc.yml"))]
          (is (.contains yml-content "Minimal Book"))
          (is (not (.contains yml-content "author:")))
          (is (not (.contains yml-content "book:"))))
        
        ;; Validate generated polydoc.yml
        (let [config-path (.getPath (io/file temp-dir "polydoc.yml"))
              loaded-metadata (metadata/load-metadata config-path)]
          (is (= "Minimal Book" (:title loaded-metadata)))
          (is (nil? (:author loaded-metadata))))
        
        (finally
          ;; Cleanup
          (delete-directory-recursive temp-dir)))))
  
  (testing "Generate book with custom book-id"
    (let [temp-dir (io/file (System/getProperty "java.io.tmpdir")
                             (str "polydoc-test-" (System/currentTimeMillis)))
          result (gen/generate-book {:output-dir (.getPath temp-dir)
                                      :title "Test Book"
                                      :book-id "custom-id"})]
      (try
        (is (= "custom-id" (:book-id result)))
        (is (= "Test Book" (:title result)))
        
        (finally
          ;; Cleanup
          (delete-directory-recursive temp-dir))))))
