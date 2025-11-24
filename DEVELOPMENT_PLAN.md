# Polydoc Development Plan

## Project Overview

**Goal:** Build a JVM-native Pandoc documentation system with advanced filtering capabilities, compiled to native code via GraalVM.

**Current Status:** Skeleton project with dependencies configured, basic REPL setup, minimal main function.

**Target:** Production-ready CLI tool with multiple filters, book building, search, and interactive viewer.

---

## Phase 1: Core Infrastructure

### 1.1 CLI Framework Setup

**Goal:** Establish cli-matic command structure and basic filter framework.

**Tasks:**

1. **Implement base CLI structure** (Priority: HIGH)
   - Configure cli-matic CONFIGURATION map
   - Add version, help, and global options
   - Create command hierarchy (filter, book, search, view)
   - Test basic command parsing

2. **Create filter base namespace** (Priority: HIGH)
   - `polydoc.filters.core` - shared filter utilities
   - AST validation functions
   - Common element matchers (code-block?, header?, etc.)
   - JSON I/O helpers

3. **Test infrastructure** (Priority: MEDIUM)
   - Sample Pandoc AST fixtures
   - Filter test helpers
   - Integration test framework
   - CI configuration

**Validation:**
```bash
polydoc --help              # Shows all commands
polydoc filter --help       # Shows filter subcommands
polydoc --version           # Shows version
```

**REPL Testing:**
```clojure
(require '[polydoc.main :as main])
(require '[polydoc.filters.core :as fcore])

;; Test CLI parsing
(main/-main "filter" "--help")

;; Test AST utilities
(def sample-ast {:blocks [{:t "Para" :c []}]})
(fcore/validate-ast sample-ast)  ; => true
```

### 1.2 First Filter Implementation

**Goal:** Implement run-clojure filter as reference implementation.

**Tasks:**

1. **Create polydoc.filters.run-clojure** (Priority: HIGH)
   - Identify Clojure code blocks
   - Execute code in safe namespace
   - Capture output and result
   - Format as modified AST
   - Error handling

2. **Add CLI command** (Priority: HIGH)
   - Register in main.clj
   - Add documentation
   - Test with Pandoc pipeline

3. **Test suite** (Priority: HIGH)
   - Valid Clojure code execution
   - Syntax errors
   - Runtime exceptions
   - Output capture
   - Edge cases (empty blocks, no code, etc.)

**Validation:**
```bash
# Create test document
echo '```clojure
(+ 1 2 3)
```' | pandoc -t json | polydoc filter run-clojure | pandoc -f json

# Should show code + result
```

**REPL Testing:**
```clojure
(require '[polydoc.filters.run-clojure :as rc])

;; Test code execution
(rc/eval-clojure "(+ 1 2 3)")
;; => {:success true :result "6" :output ""}

;; Test with error
(rc/eval-clojure "(/ 1 0)")
;; => {:success false :error "Divide by zero"}

;; Test AST transformation
(def code-block {:t "CodeBlock" :c [["" ["clojure"] []] "(+ 1 2)"]})
(rc/process-block code-block)
;; => Updated block with result
```

### 1.3 Documentation and Examples

**Tasks:**

1. **Create examples/ directory** (Priority: MEDIUM)
   - Sample markdown files
   - Expected outputs
   - README with usage examples

2. **Update project README** (Priority: MEDIUM)
   - Installation instructions
   - Quick start guide
   - Filter usage examples
   - Development setup

3. **Add docstrings** (Priority: LOW)
   - All public functions
   - Namespace documentation
   - Example code in docstrings

---

## Phase 2: Additional Filters

### 2.1 SQLite Filter

**Goal:** Execute SQLite queries and include results.

**Tasks:**

1. **Implement polydoc.filters.run-sqlite** (Priority: HIGH)
   - Detect sqlite code blocks
   - Manage database connection
   - Execute queries
   - Format results as tables/code
   - Connection pooling for repeated queries

2. **Table formatting** (Priority: MEDIUM)
   - Convert query results to Pandoc tables
   - Handle different data types
   - Pretty-print formatting

**REPL Testing:**
```clojure
(require '[polydoc.filters.run-sqlite :as sqlite])

;; Test query execution
(sqlite/execute-query "SELECT 1 as col" ":memory:")
;; => {:success true :columns ["col"] :rows [[1]]}

;; Test table generation
(sqlite/results->table {:columns ["a" "b"] :rows [[1 2] [3 4]]})
;; => Pandoc Table AST
```

### 2.2 PlantUML Filter

**Goal:** Render PlantUML diagrams to images.

**Tasks:**

1. **Implement polydoc.filters.render-plantuml** (Priority: MEDIUM)
   - Detect plantuml code blocks
   - Call PlantUML jar
   - Save images to output directory
   - Replace code block with image reference

2. **Image management** (Priority: MEDIUM)
   - Generate unique image names (hash-based)
   - Handle output directory creation
   - Clean up old images

**REPL Testing:**
```clojure
(require '[polydoc.filters.render-plantuml :as puml])

;; Test PlantUML rendering
(puml/render-diagram "@startuml\nAlice -> Bob\n@enduml" "./images")
;; => {:success true :image-path "./images/abc123.png"}

;; Test AST transformation
(puml/process-plantuml-block code-block "./images")
;; => Image AST element
```

### 2.3 Language Execution Filters

**Goal:** Execute JavaScript, Python, and shell code blocks.

**Tasks:**

1. **Implement polydoc.filters.run-javascript** (Priority: LOW)
   - Node.js execution
   - Output capture
   - Error handling

2. **Implement polydoc.filters.run-python** (Priority: LOW)
   - Python3 execution
   - REPL-style output
   - Error formatting

3. **Implement polydoc.filters.run-shell** (Priority: LOW)
   - Bash/sh execution
   - Command output
   - Exit code handling

**Shared utilities:** Extract common shell execution logic to `polydoc.filters.shell-exec`.

---

## Phase 3: Book Building System

### 3.1 Database Schema

**Goal:** Design and implement book indexing database.

**Tasks:**

1. **Create polydoc.db.schema** (Priority: HIGH)
   - Define tables (books, sections, metadata)
   - Create migrations
   - Add FTS5 virtual tables
   - Indexing functions

2. **Migration system** (Priority: HIGH)
   - Use Ragtime or custom migrations
   - Version tracking
   - Up/down migrations

**Schema:**
```sql
CREATE TABLE books (
    book_id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    metadata TEXT,
    created_at TEXT DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sections (
    section_id INTEGER PRIMARY KEY,
    book_id INTEGER NOT NULL,
    title TEXT,
    content TEXT,
    hash TEXT UNIQUE,
    level INTEGER,
    order_index INTEGER,
    FOREIGN KEY(book_id) REFERENCES books(book_id)
);

CREATE VIRTUAL TABLE sections_fts USING fts5(
    title, 
    content, 
    content=sections,
    content_rowid=section_id
);

-- Triggers for FTS sync
CREATE TRIGGER sections_ai AFTER INSERT ON sections BEGIN
    INSERT INTO sections_fts(rowid, title, content) 
    VALUES (new.section_id, new.title, new.content);
END;
```

**REPL Testing:**
```clojure
(require '[polydoc.db.schema :as schema])

;; Test schema creation
(schema/create-database! "test.db")

;; Test migrations
(schema/migrate! "test.db")

;; Test FTS trigger
(jdbc/execute! ds
  (sql/format {:insert-into :sections
               :values [{:book_id 1 :title "Test" :content "Content"}]}))
;; FTS table should be updated automatically
```

### 3.2 TOC Parser

**Goal:** Parse table of contents files and resolve document paths.

**Tasks:**

1. **Implement polydoc.book.toc** (Priority: HIGH)
   - Parse YAML TOC format
   - Validate structure
   - Resolve file paths
   - Build document order

**TOC Format:**
```yaml
title: "My Book"
author: "Author Name"
version: "1.0.0"
chapters:
  - title: "Introduction"
    file: "intro.md"
  - title: "Getting Started"
    file: "getting-started.md"
    sections:
      - title: "Installation"
        file: "getting-started/install.md"
      - title: "Configuration"
        file: "getting-started/config.md"
  - title: "Advanced Topics"
    file: "advanced.md"
```

**REPL Testing:**
```clojure
(require '[polydoc.book.toc :as toc])

;; Test TOC parsing
(toc/parse-toc "book.yaml")
;; => {:title "My Book" :chapters [...]}

;; Test file resolution
(toc/resolve-files toc-data "./docs")
;; => Absolute paths for all files
```

### 3.3 Book Builder

**Goal:** Combine documents, extract sections, build index.

**Tasks:**

1. **Implement polydoc.book.builder** (Priority: HIGH)
   - Read TOC
   - Process each document
   - Extract sections from AST
   - Insert into database
   - Generate combined output

2. **Section extraction** (Priority: HIGH)
   - Walk AST for headers
   - Extract text content
   - Compute content hash
   - Preserve document order

**REPL Testing:**
```clojure
(require '[polydoc.book.builder :as builder])

;; Test section extraction
(builder/extract-sections ast)
;; => [{:level 1 :title "..." :content "..." :hash "..."}]

;; Test book building
(builder/build-book {:toc "book.yaml"
                     :output-db "book.db"
                     :output-html "book.html"})
;; => Creates database and HTML
```

---

## Phase 4: Search System

### 4.1 Search API

**Goal:** Implement full-text search with SQLite FTS5.

**Tasks:**

1. **Implement polydoc.search.core** (Priority: HIGH)
   - Query FTS5 table
   - Rank results
   - Highlight matches
   - Format output

2. **Advanced search features** (Priority: MEDIUM)
   - Boolean operators
   - Phrase search
   - Field-specific search
   - Filter by section level

**REPL Testing:**
```clojure
(require '[polydoc.search.core :as search])

;; Test basic search
(search/search ds "clojure filter")
;; => [{:title "..." :snippet "..." :rank 1.5}]

;; Test phrase search
(search/search ds "\"code execution\"")

;; Test field search
(search/search ds "title:introduction")
```

### 4.2 CLI Search Command

**Goal:** Command-line search interface.

**Tasks:**

1. **Implement search command** (Priority: HIGH)
   - Accept query string
   - Format results for terminal
   - Add pagination
   - Color highlighting with bling

**Validation:**
```bash
polydoc search -q "pandoc filter" -d book.db
# Shows ranked results with snippets

polydoc search -q "title:introduction" -d book.db --limit 10
# Shows top 10 title matches
```

---

## Phase 5: Interactive Viewer

### 5.1 HTTP Server

**Goal:** Serve documentation via HTTP with search.

**Tasks:**

1. **Implement polydoc.viewer.server** (Priority: MEDIUM)
   - http-kit server
   - Static file serving
   - Search endpoint
   - Navigation endpoint

2. **HTML templates** (Priority: MEDIUM)
   - Main layout with search box
   - Section display
   - Search results page
   - Navigation sidebar

**REPL Testing:**
```clojure
(require '[polydoc.viewer.server :as server])

;; Test server startup
(server/start-server {:db "book.db" :port 8080})
;; => Server running

;; Test in browser: http://localhost:8080
```

### 5.2 Client-Side Features

**Goal:** Interactive JavaScript for search and navigation.

**Tasks:**

1. **Search autocomplete** (Priority: LOW)
   - AJAX search suggestions
   - Keyboard navigation
   - Debounced queries

2. **Section navigation** (Priority: MEDIUM)
   - Sticky TOC sidebar
   - Scroll-to-section
   - URL anchors

---

## Phase 6: Testing & Quality

### 6.1 Comprehensive Test Suite

**Tasks:**

1. **Unit tests** (Priority: HIGH)
   - All filter functions
   - Database operations
   - Search functionality
   - CLI command handlers

2. **Integration tests** (Priority: HIGH)
   - End-to-end filter pipeline
   - Book building from TOC
   - Search with real data
   - HTTP server tests

3. **Property-based tests** (Priority: MEDIUM)
   - AST transformations preserve structure
   - Database constraints
   - Search ranking consistency

**Test Coverage Target:** >80%

**REPL Testing:**
```clojure
(require '[kaocha.repl :as k])

;; Run all tests
(k/run-all)

;; Run specific namespace
(k/run 'polydoc.filters.run-clojure-test)

;; Run with coverage
(k/run-all {:kaocha.plugin.cloverage/opts {:output "target/coverage"}})
```

### 6.2 Documentation

**Tasks:**

1. **User guide** (Priority: HIGH)
   - Installation
   - Quick start
   - Filter reference
   - Book building tutorial
   - Search guide
   - Viewer setup

2. **Developer guide** (Priority: MEDIUM)
   - Architecture overview
   - Adding new filters
   - Database schema
   - Testing guide
   - Contributing guidelines

3. **API documentation** (Priority: LOW)
   - Generate with Codox
   - Host on GitHub Pages
   - Examples for all public functions

---

## Phase 7: GraalVM Native Compilation

### 7.1 Native Image Configuration

**Goal:** Compile to native binary with GraalVM.

**Tasks:**

1. **Reflection configuration** (Priority: HIGH)
   - Generate reflection-config.json
   - Test all reflection usage
   - Add metadata hints

2. **Resource configuration** (Priority: HIGH)
   - Include necessary resources
   - Bundle templates
   - Package static assets

3. **Build optimization** (Priority: MEDIUM)
   - Reduce binary size
   - Optimize startup time
   - Test on multiple platforms

**Build Process:**
```bash
# 1. AOT compile
clojure -M -e "(compile 'polydoc.main)"

# 2. Build uberjar
clojure -M:uberdeps --main-class polydoc.main

# 3. Native image
native-image \
  --no-fallback \
  --initialize-at-build-time \
  -H:ReflectionConfigurationFiles=reflection-config.json \
  -H:ResourceConfigurationFiles=resource-config.json \
  -jar target/polydoc.jar \
  polydoc

# 4. Test binary
./polydoc --version
./polydoc filter run-clojure < test.json
```

### 7.2 Platform Testing

**Tasks:**

1. **Test on platforms** (Priority: HIGH)
   - Linux (amd64, arm64)
   - macOS (Intel, Apple Silicon)
   - Windows (if needed)

2. **CI/CD setup** (Priority: MEDIUM)
   - GitHub Actions
   - Build matrix for platforms
   - Release automation
   - Upload artifacts

---

## Phase 8: Polish & Release

### 8.1 Performance Optimization

**Tasks:**

1. **Profile and optimize** (Priority: MEDIUM)
   - Identify bottlenecks
   - Optimize hot paths
   - Cache expensive operations
   - Benchmark filters

2. **Database optimization** (Priority: MEDIUM)
   - Index tuning
   - Query optimization
   - Connection pooling
   - Batch operations

**Benchmarks:**
```bash
# Filter performance
time polydoc filter run-clojure < large.json

# Search performance
time polydoc search -q "test" -d large.db

# Book build performance
time polydoc book build -t large-book.yaml
```

### 8.2 Error Handling & UX

**Tasks:**

1. **Improve error messages** (Priority: HIGH)
   - User-friendly errors
   - Helpful suggestions
   - Context information
   - Debug mode

2. **Progress indicators** (Priority: MEDIUM)
   - Book building progress
   - Filter processing status
   - Search feedback

3. **Validation & warnings** (Priority: MEDIUM)
   - TOC validation
   - AST validation
   - Missing file warnings
   - Deprecated feature warnings

### 8.3 Release Preparation

**Tasks:**

1. **Homebrew formula** (Priority: HIGH)
   - Create formula
   - Test installation
   - Submit to homebrew-core

2. **Release documentation** (Priority: HIGH)
   - Changelog
   - Migration guide
   - Breaking changes
   - Upgrade path

3. **Versioning strategy** (Priority: MEDIUM)
   - Semantic versioning
   - Git tags
   - Release notes
   - GitHub releases

---

## Success Criteria

### Functional Requirements

- [ ] All planned filters implemented and tested
- [ ] Book building works with complex TOCs
- [ ] Search returns relevant results
- [ ] Viewer provides good UX
- [ ] Native binary works on all platforms
- [ ] Test coverage >80%

### Performance Requirements

- [ ] Filter processing <100ms for typical documents
- [ ] Search queries <50ms on database with 1000 sections
- [ ] Native binary startup <10ms
- [ ] Book build for 50 documents <5 seconds

### Quality Requirements

- [ ] Zero critical bugs
- [ ] All linting passes
- [ ] Documentation complete
- [ ] Examples work
- [ ] CI/CD green

---

## Risk Mitigation

### Technical Risks

**Risk:** GraalVM compilation issues with dependencies
- **Mitigation:** Test compilation early, choose GraalVM-compatible libraries
- **Fallback:** Provide uberjar as alternative

**Risk:** Pandoc AST changes between versions
- **Mitigation:** Version detection, compatibility layer
- **Fallback:** Document supported Pandoc versions

**Risk:** Performance issues with large documents
- **Mitigation:** Incremental processing, caching, lazy evaluation
- **Fallback:** Batch mode with progress indicators

### Process Risks

**Risk:** Scope creep
- **Mitigation:** Strict phase adherence, feature freeze after Phase 7
- **Fallback:** Move features to v2.0

**Risk:** Testing gaps
- **Mitigation:** TDD for critical paths, integration tests early
- **Fallback:** Extended testing phase if needed

---

## Development Workflow

### Daily Workflow

1. **Start REPL:** `bb nrepl`
2. **Load dev namespace:** `(dev)`
3. **Make changes** in editor
4. **Test in REPL:**
   ```clojure
   (refresh)  ; Reload
   (lint)     ; Check style
   (run-all)  ; Run tests
   ```
5. **Test CLI:** `bb main <command>`
6. **Commit** when tests pass

### Code Review Checklist

- [ ] Tests pass
- [ ] Linting clean
- [ ] Documentation updated
- [ ] Examples work
- [ ] REPL tested
- [ ] CLI tested
- [ ] Performance acceptable

---

## Resources Needed

### Tools

- Clojure 1.12.3+
- Pandoc 2.0+
- GraalVM (latest)
- PlantUML jar
- SQLite 3.35+

### Documentation

- Pandoc AST spec
- cli-matic docs
- next.jdbc guide
- GraalVM native-image docs
- SQLite FTS5 reference

### External Services

- GitHub (repository, CI/CD)
- Homebrew tap (distribution)

---

## Next Steps

**Immediate:**

1. Create Phase 1.1 branch
2. Implement basic CLI structure
3. Test with simple commands
4. Verify REPL workflow

**Phase 1 Goals:**

1. Complete CLI framework
2. Implement run-clojure filter
3. Write comprehensive tests
4. Update documentation

---

## Appendix: Useful Commands

### Development

```bash
# Start REPL
bb nrepl

# Run tests
bb test

# Lint
bb lint

# Format
bb fmt

# Clean
bb clean

# Run main
bb main --help
```

### REPL

```clojure
;; Load dev
(dev)

;; Reload all
(refresh)

;; Lint
(lint)

;; Run tests
(run-all)

;; Test specific namespace
(require '[kaocha.repl :as k])
(k/run 'polydoc.filters.run-clojure-test)
```

### Testing Filters

```bash
# Test filter with Pandoc
echo 'test content' | \
  pandoc -t json | \
  clojure -M:main filter run-clojure | \
  pandoc -f json

# Test with file
pandoc -t json test.md | \
  polydoc filter run-clojure > output.json

pandoc -f json output.json -o output.html
```

### Database

```bash
# Create test database
sqlite3 test.db < schema.sql

# Query sections
sqlite3 test.db "SELECT * FROM sections"

# Test FTS
sqlite3 test.db "SELECT * FROM sections_fts WHERE sections_fts MATCH 'search term'"
```

---

**Document Version:** 1.0  
**Last Updated:** 2025-11-23  
**Status:** Draft
