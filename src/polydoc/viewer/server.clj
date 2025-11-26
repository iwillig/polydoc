(ns polydoc.viewer.server
  "HTTP server for interactive documentation viewer.
   
   Provides a web-based interface for browsing documentation with:
   - Section-by-section navigation
   - Table of contents
   - Full-text search
   - Previous/Next navigation
   
   Built with http-kit and Hiccup, styled with Pico CSS.
   Uses Component for lifecycle management.
   
   Example usage:
   
     (require '[polydoc.viewer.server :as server]
              '[com.stuartsierra.component :as component])
     
     ;; Create and start system with file database
     (def system (component/start
                   (server/viewer-system {:database \"polydoc.db\"
                                          :port 3000})))
     
     ;; Or with in-memory connection (for testing)
     (def conn (jdbc/get-connection {:dbtype \"sqlite\" :dbname \":memory:\"}))
     (def system (component/start
                   (server/viewer-system {:connection conn
                                          :port 3000})))
     
     ;; Stop system
     (component/stop system)
   
   URL Structure:
   
     /                           - First section of first book
     /book/:book-id              - First section of specified book
     /book/:book-id/section/:id  - Specific section
     /search?q=query             - Search results"
  (:require
   [clojure.string :as str]
   [com.stuartsierra.component :as component]
   [hiccup.core :refer [html]]
   [hiccup.page :as page]
   [hiccup.util]
   [honey.sql :as sql]
   [next.jdbc :as jdbc]
   [org.httpkit.server :as http]
   [polydoc.book.search :as search]))


;; Database Queries

(defn get-book
  "Get book by book_id."
  [ds book-id]
  (first
   (jdbc/execute! ds
                  (sql/format
                   {:select [:*]
                    :from [:books]
                    :where [:= :book_id book-id]}))))


(defn get-section
  "Get section by section_id."
  [ds book-id section-id]
  (first
   (jdbc/execute! ds
                  (sql/format
                   {:select [:sections.*]
                    :from [:sections]
                    :join [:books [:= :sections.book_id :books.id]]
                    :where [:and
                            [:= :books.book_id book-id]
                            [:= :sections.section_id section-id]]}))))


(defn get-sections-for-book
  "Get all sections for a book, ordered by section_order."
  [ds book-id]
  (jdbc/execute! ds
                 (sql/format
                  {:select [:sections.*]
                   :from [:sections]
                   :join [:books [:= :sections.book_id :books.id]]
                   :where [:= :books.book_id book-id]
                   :order-by [[:section_order :asc]]})))


(defn get-first-section
  "Get the first section of a book."
  [ds book-id]
  (first
   (jdbc/execute! ds
                  (sql/format
                   {:select [:sections.*]
                    :from [:sections]
                    :join [:books [:= :sections.book_id :books.id]]
                    :where [:= :books.book_id book-id]
                    :order-by [[:section_order :asc]]
                    :limit 1}))))


(defn get-previous-section
  "Get the previous section in order."
  [ds book-id current-order]
  (first
   (jdbc/execute! ds
                  (sql/format
                   {:select [:sections.*]
                    :from [:sections]
                    :join [:books [:= :sections.book_id :books.id]]
                    :where [:and
                            [:= :books.book_id book-id]
                            [:< :section_order current-order]]
                    :order-by [[:section_order :desc]]
                    :limit 1}))))


(defn get-next-section
  "Get the next section in order."
  [ds book-id current-order]
  (first
   (jdbc/execute! ds
                  (sql/format
                   {:select [:sections.*]
                    :from [:sections]
                    :join [:books [:= :sections.book_id :books.id]]
                    :where [:and
                            [:= :books.book_id book-id]
                            [:> :section_order current-order]]
                    :order-by [[:section_order :asc]]
                    :limit 1}))))


;; HTML Layout Components

(defn page-layout
  "Main page layout with Pico CSS.
  
  Options:
  - :title - Page title
  - :book - Book data map
  - :current-section - Current section ID (for TOC highlighting)"
  [opts & content]
  (page/html5
   {:lang "en"}
   [:html {:data-theme "light"}
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:title (str (:title opts) " - Polydoc")]
     [:link {:rel "stylesheet"
             :href "https://cdn.jsdelivr.net/npm/@picocss/pico@2/css/pico.min.css"}]
     [:style "
        .section-nav { 
          display: flex; 
          justify-content: space-between; 
          margin: 2rem 0; 
        }
        .toc-link { 
          text-align: center; 
          flex-grow: 1; 
          margin: 0 1rem; 
        }
        .nav-button { 
          min-width: 120px; 
        }
        .content { 
          margin: 2rem 0; 
        }
        .search-form { 
          margin: 2rem 0; 
        }
        details.toc summary { 
          text-align: center; 
          cursor: pointer; 
        }
        .toc-list { 
          list-style: none; 
          padding-left: 1rem; 
        }
        .toc-list li { 
          margin: 0.5rem 0; 
        }
        .toc-current { 
          font-weight: bold; 
          color: var(--primary); 
        }
        .search-result { 
          margin: 2rem 0; 
          padding: 1rem; 
          border: 1px solid var(--muted-border-color); 
          border-radius: 0.5rem; 
        }
      "]]
    [:body
     [:header.container
      [:nav
       [:ul
        [:li [:strong "📖 " (or (:book-title opts) "Polydoc")]]]
       [:ul
        [:li
         [:form.search-form {:action "/search" :method "get"}
          [:input {:type "search"
                   :name "q"
                   :placeholder "Search documentation..."
                   :value (:query opts)
                   :aria-label "Search"}]]]]]]
     [:main.container
      content]]]))


(defn toc-component
  "Table of contents component.
  
  Renders as a collapsible details element."
  [sections current-section-id book-id]
  [:details.toc
   [:summary "📖 Table of Contents"]
   [:ul.toc-list
    (for [section sections]
      (let [is-current? (= (:sections/section_id section) current-section-id)]
        [:li {:class (when is-current? "toc-current")}
         [:a {:href (str "/book/" book-id "/section/" (:sections/section_id section))}
          (:sections/heading_text section)]]))]])


(defn section-nav
  "Previous/Next navigation buttons."
  [book-id prev-section next-section]
  [:div.section-nav
   (if prev-section
     [:a.nav-button {:href (str "/book/" book-id "/section/" (:sections/section_id prev-section))
                     :role "button"}
      "← Previous"]
     [:div.nav-button])
   [:div.toc-link]
   (if next-section
     [:a.nav-button {:href (str "/book/" book-id "/section/" (:sections/section_id next-section))
                     :role "button"}
      "Next →"]
     [:div.nav-button])])


(defn section-page
  "Render a section page."
  [ds book-id section-id]
  (if-let [section (get-section ds book-id section-id)]
    (let [book (get-book ds book-id)
          sections (get-sections-for-book ds book-id)
          prev (get-previous-section ds book-id (:sections/section_order section))
          next (get-next-section ds book-id (:sections/section_order section))]
      (page-layout
       {:title (:sections/heading_text section)
        :book-title (:books/title book)
        :current-section section-id}

       ;; Top navigation
       (section-nav book-id prev next)

       ;; Table of contents
       (toc-component sections section-id book-id)

       ;; Section content
       [:article.content
        [:h1 (:sections/heading_text section)]
        [:div (hiccup.util/raw-string (:sections/content_html section))]]

       ;; Bottom navigation
       (section-nav book-id prev next)))

    ;; Section not found
    (page-layout
     {:title "Section Not Found"}
     [:article
      [:h1 "Section Not Found"]
      [:p "The requested section could not be found."]
      [:a {:href "/"} "Go to first section"]])))


(defn search-results-page
  "Render search results page."
  [ds query]
  (if (str/blank? query)
    ;; Empty query
    (page-layout
     {:title "Search"}
     [:article
      [:h1 "Search"]
      [:p "Enter a search query to find documentation."]])

    ;; Perform search
    (let [results (search/search ds query {:limit 20})]
      (page-layout
       {:title (str "Search: " query)
        :query query}
       [:article
        [:h1 "Search Results"]
        [:p (str (count results) " result(s) for \"" query "\"")]

        (if (empty? results)
          [:p "No results found."]

          [:div
           (for [result results]
             ;; Search returns :section-id, :heading-text, :snippet, :source-file, :book-id
             (let [section-id (:section-id result (:sections/section_id result))
                   book-id (:book-id result (:books/book_id result))]
               [:div.search-result
                [:h3
                 [:a {:href (str "/book/" book-id "/section/" section-id)}
                  (:heading-text result (:sections/heading_text result))]]
                [:p (hiccup.util/raw-string (:snippet result))]
                [:small
                 [:em "Source: " (:source-file result (:sections/source_file result))]]]))])]))))


(defn index-page
  "Root index page - redirects to first section of first book."
  [ds]
  (let [first-book (first (jdbc/execute! ds
                                         (sql/format
                                          {:select [:*]
                                           :from [:books]
                                           :limit 1})))]
    (if first-book
      (let [book-id (:books/book_id first-book)
            first-section (get-first-section ds book-id)]
        (if first-section
          ;; Redirect to first section
          {:status 302
           :headers {"Location" (str "/book/" book-id "/section/"
                                     (:sections/section_id first-section))}}
          ;; No sections
          {:status 200
           :headers {"Content-Type" "text/html; charset=utf-8"}
           :body (str (page-layout
                       {:title "No Content"}
                       [:article
                        [:h1 "No Content"]
                        [:p "No sections found in this book."]]))}))
      ;; No books
      {:status 200
       :headers {"Content-Type" "text/html; charset=utf-8"}
       :body (str (page-layout
                   {:title "No Books"}
                   [:article
                    [:h1 "No Books"]
                    [:p "No documentation books found in the database."]
                    [:p "Build a book first with: "
                     [:code "polydoc book -c polydoc.yml -o output/"]]]))})))


;; Request Routing

(defn parse-query-params
  "Parse query string into map."
  [query-string]
  (when query-string
    (into {}
          (for [param (str/split query-string #"&")]
            (let [[k v] (str/split param #"=" 2)]
              [(keyword k) (java.net.URLDecoder/decode v "UTF-8")])))))


(defn handler
  "Main HTTP request handler."
  [ds]
  (fn [req]
    (let [uri (:uri req)
          query-params (parse-query-params (:query-string req))]

      (cond
        ;; Root
        (= uri "/")
        (index-page ds)

        ;; Section page: /book/:book-id/section/:section-id
        (re-matches #"/book/([^/]+)/section/([^/]+)" uri)
        (let [[_ book-id section-id] (re-matches #"/book/([^/]+)/section/([^/]+)" uri)]
          {:status 200
           :headers {"Content-Type" "text/html; charset=utf-8"}
           :body (str (section-page ds book-id section-id))})

        ;; Search: /search?q=query
        (= uri "/search")
        {:status 200
         :headers {"Content-Type" "text/html; charset=utf-8"}
         :body (str (search-results-page ds (:q query-params)))}

        ;; Not found
        :else
        {:status 404
         :headers {"Content-Type" "text/html; charset=utf-8"}
         :body (str (page-layout
                     {:title "Not Found"}
                     [:article
                      [:h1 "404 - Not Found"]
                      [:p "The requested page could not be found."]
                      [:a {:href "/"} "Go to first section"]]))}))))


;; Component Definitions

(defrecord Database
           [db-spec connection datasource]

  component/Lifecycle

  (start
    [this]
    (println "Starting database component...")
    (cond
      ;; If connection is provided (e.g., in-memory for tests), use it directly
      connection
      (do
        (println "  Using provided connection")
        this)

      ;; If datasource is provided, use it
      datasource
      (do
        (println "  Using provided datasource")
        this)

      ;; Otherwise create datasource from db-spec
      db-spec
      (let [ds (jdbc/get-datasource db-spec)]
        (println (str "  Created datasource from: " db-spec))
        (assoc this :datasource ds))

      :else
      (throw (ex-info "Database component requires :db-spec, :datasource, or :connection"
                      {:component this}))))


  (stop
    [this]
    (println "Stopping database component...")
    ;; For file-based datasources, we don't need to close them
    ;; For provided connections, the caller is responsible for closing
    this))


(defrecord WebServer
           [port host database server]

  component/Lifecycle

  (start
    [this]
    (println (str "Starting web server on " host ":" port "..."))
    (let [;; Get database connection/datasource from Database component
          db (or (:connection database)
                 (:datasource database)
                 (throw (ex-info "Database component has no connection or datasource"
                                 {:database database})))
          ;; Start HTTP server
          srv (http/run-server (handler db) {:port port :host host})]

      (println (str "\n✓ Polydoc viewer started!"))
      (println (str "  URL: http://" host ":" port))
      (println "\nPress Ctrl+C to stop.\n")

      (assoc this :server srv)))


  (stop
    [this]
    (println "Stopping web server...")
    (when server
      (server)
      (println "Server stopped."))
    (assoc this :server nil)))


;; System Construction

(defn viewer-system
  "Create a viewer system with database and web server components.
   
   Options:
   - :database - Path to SQLite database file (creates datasource)
   - :connection - JDBC connection (for in-memory databases in tests)
   - :datasource - Pre-created datasource
   - :port - Port to listen on (default: 3000)
   - :host - Host to bind to (default: localhost)
   
   Returns a Component system map."
  [{:keys [database connection datasource port host]
    :or {port 3000 host "localhost"}}]
  (component/system-map
   :database (map->Database
              (cond
                connection {:connection connection}
                datasource {:datasource datasource}
                database {:db-spec {:dbtype "sqlite" :dbname database}}
                :else (throw (ex-info "Must provide :database, :connection, or :datasource"
                                      {:options (keys (dissoc {} :port :host))}))))
   :web-server (component/using
                (map->WebServer {:port port :host host})
                [:database])))
