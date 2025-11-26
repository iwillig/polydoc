(ns polydoc.viewer.server-test
  "Integration tests for HTTP viewer using Etaoin browser automation."
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [com.stuartsierra.component :as component]
    [etaoin.api :as e]
    [etaoin.keys]
    [polydoc.viewer.server :as server]
    [polydoc.test-helpers :as helpers]
    [next.jdbc :as jdbc]
    [honey.sql :as sql]))


;; Test Configuration

(def test-port 3333)
(def test-url (str "http://localhost:" test-port))


;; Test Fixtures

(def ^:dynamic *system* nil)
(def ^:dynamic *driver* nil)


(defn fixture-server
  "Start test server with in-memory database before tests, stop after."
  [f]
  ;; Use the test helpers fixture to create an in-memory database with schema
  (helpers/use-sqlite-database
    (fn []
      ;; Insert test book using the fixture's connection
      (jdbc/execute! helpers/*connection*
                     (sql/format
                       {:insert-into :books
                        :values [{:book_id "test-book"
                                  :title "Test Documentation"
                                  :author "Test Author"
                                  :version "1.0.0"}]}))
      
      ;; Get the book's auto-generated ID
      (let [book (jdbc/execute-one! helpers/*connection*
                                    (sql/format {:select [:id :book_id]
                                                 :from [:books]
                                                 :where [:= :book_id "test-book"]}))
            book-id-num (:books/id book)]
        
        ;; Insert test sections
        (jdbc/execute! helpers/*connection*
                       (sql/format
                         {:insert-into :sections
                          :values [{:book_id book-id-num
                                    :section_id "intro"
                                    :source_file "intro.md"
                                    :heading_level 1
                                    :heading_text "Introduction"
                                    :heading_slug "introduction"
                                    :content_markdown "{}"
                                    :content_html "<h1>Introduction</h1><p>Welcome to the test documentation.</p>"
                                    :content_plain "Introduction\n\nWelcome to the test documentation."
                                    :section_order 0
                                    :content_hash "intro-hash"}
                                   {:book_id book-id-num
                                    :section_id "chapter-1"
                                    :source_file "chapter1.md"
                                    :heading_level 1
                                    :heading_text "Chapter 1: Getting Started"
                                    :heading_slug "chapter-1-getting-started"
                                    :content_markdown "{}"
                                    :content_html "<h1>Chapter 1: Getting Started</h1><p>This chapter covers the basics.</p>"
                                    :content_plain "Chapter 1: Getting Started\n\nThis chapter covers the basics."
                                    :section_order 1
                                    :content_hash "ch1-hash"}
                                   {:book_id book-id-num
                                    :section_id "chapter-2"
                                    :source_file "chapter2.md"
                                    :heading_level 1
                                    :heading_text "Chapter 2: Advanced Topics"
                                    :heading_slug "chapter-2-advanced-topics"
                                    :content_markdown "{}"
                                    :content_html "<h1>Chapter 2: Advanced Topics</h1><p>Advanced features and techniques.</p>"
                                    :content_plain "Chapter 2: Advanced Topics\n\nAdvanced features and techniques."
                                    :section_order 2
                                    :content_hash "ch2-hash"}]})))
      
      ;; Create and start the viewer system with the in-memory connection
      (let [sys (component/start
                  (server/viewer-system {:connection helpers/*connection*
                                         :port test-port
                                         :host "localhost"}))]
        (binding [*system* sys]
          (try
            ;; Give server time to start
            (Thread/sleep 1000)
            (f)
            (finally
              (component/stop sys))))))))


(defn fixture-driver
  "Create browser driver for each test."
  [f]
  (e/with-firefox {:size [1920 1080]} drv
    (binding [*driver* drv]
      (f))))


(use-fixtures :once fixture-server)
(use-fixtures :each fixture-driver)


;; Tests

(deftest test-index-page-redirect
  (testing "Root page redirects to first section"
    (e/go *driver* test-url)
    (e/wait-visible *driver* {:tag :h1})
    
    ;; Should redirect to first section (Introduction)
    (is (e/has-text? *driver* "Introduction"))
    (is (e/has-text? *driver* "Welcome to the test documentation"))
    
    ;; Check URL contains section ID
    (let [current-url (e/get-url *driver*)]
      (is (re-find #"/book/test-book/section/intro" current-url)))))


(deftest test-section-navigation
  (testing "Previous and Next navigation buttons"
    ;; Start at first section
    (e/go *driver* (str test-url "/book/test-book/section/intro"))
    (e/wait-visible *driver* {:tag :h1})
    
    ;; First section should only have "Next →" button
    (is (e/has-text? *driver* "Next"))
    
    ;; Click Next using class selector (more reliable than text matching)
    (e/click *driver* [{:tag :a :class :nav-button :fn/has-text "Next"}])
    (e/wait-visible *driver* {:tag :h1})
    
    ;; Should be on Chapter 1
    (is (e/has-text? *driver* "Chapter 1: Getting Started"))
    
    ;; Should have both Previous and Next buttons
    (is (e/has-text? *driver* "Previous"))
    (is (e/has-text? *driver* "Next"))
    
    ;; Click Next again
    (e/click *driver* [{:tag :a :class :nav-button :fn/has-text "Next"}])
    (e/wait-visible *driver* {:tag :h1})
    
    ;; Should be on Chapter 2 (last section)
    (is (e/has-text? *driver* "Chapter 2: Advanced Topics"))
    
    ;; Last section should only have "Previous" button
    (is (e/has-text? *driver* "Previous"))
    
    ;; Click Previous using class selector
    (e/click *driver* [{:tag :a :class :nav-button :fn/has-text "Previous"}])
    (e/wait-visible *driver* {:tag :h1})
    
    ;; Back to Chapter 1
    (is (e/has-text? *driver* "Chapter 1: Getting Started"))))


(deftest test-table-of-contents
  (testing "Table of contents displays and links work"
    (e/go *driver* (str test-url "/book/test-book/section/chapter-1"))
    (e/wait-visible *driver* {:tag :h1})
    
    ;; TOC should be visible (as a details/summary)
    (is (e/visible? *driver* [{:tag :details :class :toc}]))
    
    ;; Click to expand TOC
    (e/click *driver* [{:tag :details :class :toc} {:tag :summary}])
    (e/wait 0.5)
    
    ;; All sections should be listed
    (is (e/has-text? *driver* "Introduction"))
    (is (e/has-text? *driver* "Chapter 1: Getting Started"))
    (is (e/has-text? *driver* "Chapter 2: Advanced Topics"))
    
    ;; Click on a TOC link
    (e/click *driver* [{:tag :details :class :toc} {:tag :a :fn/text "Introduction"}])
    (e/wait-visible *driver* {:tag :h1})
    
    ;; Should navigate to Introduction section
    (is (e/has-text? *driver* "Welcome to the test documentation"))))


(deftest test-search-functionality
  (testing "Search input and results"
    (e/go *driver* (str test-url "/book/test-book/section/intro"))
    (e/wait-visible *driver* {:tag :input :type :search})
    
    ;; Fill search input and submit by pressing Enter
    (e/fill *driver* {:tag :input :type :search} "basics")
    (e/fill *driver* {:tag :input :type :search} etaoin.keys/enter)
    
    ;; Wait for search results page
    (e/wait-visible *driver* {:tag :h1})
    (is (e/has-text? *driver* "Search Results"))
    
    ;; Should find Chapter 1 (mentions "basics")
    (is (e/has-text? *driver* "Chapter 1: Getting Started"))
    
    ;; Click on search result
    (e/click *driver* [{:class :search-result} {:tag :a}])
    (e/wait-visible *driver* {:tag :h1})
    
    ;; Should navigate to that section
    (is (e/has-text? *driver* "Chapter 1: Getting Started"))))


(deftest test-empty-search
  (testing "Empty search query shows message"
    (e/go *driver* (str test-url "/search"))
    (e/wait-visible *driver* {:tag :h1})
    
    (is (e/has-text? *driver* "Search"))
    (is (e/has-text? *driver* "Enter a search query"))))


(deftest test-pico-css-styling
  (testing "Pico CSS is loaded and page is styled"
    (e/go *driver* (str test-url "/book/test-book/section/intro"))
    (e/wait-visible *driver* {:tag :h1})
    
    ;; Check that Pico CSS link is in head
    (let [css-links (e/query-all *driver* {:tag :link :rel :stylesheet})]
      (is (seq css-links) "Should have stylesheet links"))
    
    ;; Check light mode is set
    (let [html-elem (e/query *driver* {:tag :html})]
      (is (= "light" (e/get-element-attr-el *driver* html-elem :data-theme))))))


(deftest test-section-content-display
  (testing "Section HTML content is rendered correctly"
    (e/go *driver* (str test-url "/book/test-book/section/intro"))
    (e/wait-visible *driver* {:tag :h1})
    
    ;; Content should be in an article
    (is (e/exists? *driver* [{:tag :article :class :content}]))
    
    ;; Heading should be rendered
    (is (e/has-text? *driver* [{:tag :article :class :content} {:tag :h1}] "Introduction"))
    
    ;; HTML content should be rendered
    (is (e/has-text? *driver* "Welcome to the test documentation"))))


(deftest test-404-not-found
  (testing "Non-existent pages return 404"
    (e/go *driver* (str test-url "/book/test-book/section/nonexistent"))
    (e/wait-visible *driver* {:tag :h1})
    
    (is (e/has-text? *driver* "Section Not Found"))
    (is (e/has-text? *driver* "Go to first section"))))


(deftest test-responsive-layout
  (testing "Layout works at different viewport sizes"
    ;; Test mobile size
    (e/set-window-size *driver* 375 667) ; iPhone size
    (e/go *driver* (str test-url "/book/test-book/section/intro"))
    (e/wait-visible *driver* {:tag :h1})
    
    ;; Content should still be visible
    (is (e/visible? *driver* {:tag :h1}))
    (is (e/visible? *driver* {:tag :input :type :search}))
    
    ;; Test desktop size
    (e/set-window-size *driver* 1920 1080)
    (e/wait 0.5)
    
    ;; Everything should still work
    (is (e/visible? *driver* {:tag :h1}))
    (is (e/visible? *driver* {:tag :input :type :search}))))
