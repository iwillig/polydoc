# Polydoc Completion Plan

## Current State Assessment

**Working Well (22.9% Complete):**
- ✅ CLI framework with cli-matic
- ✅ Core filter utilities (AST walking, I/O, composition)
- ✅ Clojure execution filter (code blocks → executed results)
- ✅ SQLite execution filter (SQL queries → formatted tables)
- ✅ 28 tests, 91 assertions, 0 failures
- ✅ REPL-driven development environment
- ✅ Comprehensive documentation and examples

**Needs Work:**
- 🔴 2 lint warnings (missing require, unused namespace)
- 🟡 PlantUML rendering filter not implemented
- 🟡 Include filter not implemented
- 🟡 JavaScript/Python execution filters not implemented
- 🟡 Book building system not started
- 🟡 Search system not started
- 🟡 Interactive viewer not started
- 🟡 GraalVM compilation not configured

## Phase 1: Fix Immediate Issues

**Goal:** Clean codebase with zero warnings

**Fix lint warnings:**
- Add `(:require [clojure.pprint])` to `src/polydoc/filters/core.clj`
- Remove unused `polydoc.filters.core` require from `test/polydoc/filters/sqlite_exec_test.clj:5`
- Run lint: `clojure -M:lint -m clj-kondo.main --lint src test`
- Verify zero warnings

**Verify test suite:**
- Run tests: `clojure -M:test`
- Confirm 28 tests, 91 assertions, 0 failures
- Ensure all existing functionality works

## Phase 2: Complete Core Filters

**Goal:** Implement remaining essential filters

### PlantUML Rendering Filter

**Implementation:**
- Create `src/polydoc/filters/plantuml.clj`
- Detect code blocks with class "plantuml"
- Generate unique image filenames using content hash
- Shell out to PlantUML JAR: `java -jar plantuml.jar -o outputdir input.txt`
- Convert code block → image reference in AST
- Handle errors (missing PlantUML, invalid syntax)

**Testing:**
- Unit tests for diagram detection
- Integration tests for image generation
- Error handling tests
- Create `examples/plantuml-demo.md`

**CLI Integration:**
- Register in main.clj: `"plantuml"` → `plantuml/main`
- Update help text

### Include Filter

**Implementation:**
- Create `src/polydoc/filters/include.clj`
- Detect code blocks with class "include"
- Parse include directive: `{.include file="path/to/file.md"}`
- Read external file content
- Handle relative paths (resolve against current document directory)
- Insert file content into AST
- Support recursive includes with cycle detection

**Testing:**
- Test various include scenarios
- Test path resolution
- Test cycle detection
- Create `examples/include-demo.md`

**CLI Integration:**
- Register in main.clj: `"include"` → `include/main`
- Update help text

### JavaScript Execution Filter

**Implementation:**
- Create `src/polydoc/filters/javascript_exec.clj`
- Follow structure of `clojure_exec.clj`
- Shell out to Node.js: `node -e "code"`
- Capture stdout, stderr, exit code
- Format output similar to Clojure exec filter

**Testing:**
- Unit tests for code execution
- Error handling tests
- Edge case tests
- Create `examples/javascript-exec-demo.md`

**CLI Integration:**
- Register in main.clj: `"javascript-exec"` → `javascript-exec/main`
- Update help text

### Python Execution Filter

**Implementation:**
- Create `src/polydoc/filters/python_exec.clj`
- Follow structure of `clojure_exec.clj`
- Shell out to Python: `python3 -c "code"`
- Capture stdout, stderr, exit code
- Format output similar to Clojure exec filter

**Testing:**
- Unit tests for code execution
- Error handling tests
- Edge case tests
- Create `examples/python-exec-demo.md`

**CLI Integration:**
- Register in main.clj: `"python-exec"` → `python-exec/main`
- Update help text

## Phase 3: Book Building System

**Goal:** Combine multiple documents into searchable books with TOC

### Database Schema

**Implementation:**
- Create `src/polydoc/db/schema.clj`
- Define `books` table: book_id, name, metadata, created_at
- Define `sections` table: section_id, book_id, title, content, hash, level, order_index
- Create `sections_fts` FTS5 virtual table for full-text search
- Implement migration functions using next.jdbc
- Add FTS triggers to keep search index synced

**Testing:**
- Test schema creation
- Test migrations
- Test FTS trigger behavior

### TOC Parser

**Implementation:**
- Create `src/polydoc/book/toc.clj`
- Parse YAML TOC format using clj-yaml
- Validate structure (required fields, file existence)
- Resolve file paths relative to TOC file
- Build flat document order for processing

**Example TOC format:**
```yaml
title: "My Book"
author: "Author Name"
chapters:
  - title: "Chapter 1"
    file: "ch1.md"
    sections:
      - title: "Section 1.1"
        file: "ch1/sec1.md"
```

**Testing:**
- Test various TOC formats
- Test validation
- Test path resolution

### Book Builder

**Implementation:**
- Create `src/polydoc/book/builder.clj`
- Read TOC file
- Process each document through filters
- Extract sections from AST (walk for Header nodes)
- Compute content hash for each section
- Insert into database:
  - Create book record
  - Insert sections in order
  - FTS index auto-updated via triggers
- Generate combined output (HTML/PDF via Pandoc)

**Testing:**
- Integration tests for complete book building
- Test section extraction
- Test database insertion
- Test FTS indexing

### CLI Integration

**Implementation:**
- Add `book` command to main.clj
- Options:
  - `--toc/-t` - TOC file path (required)
  - `--output-db/-d` - Database path (default: book.db)
  - `--output-html/-o` - HTML output path (default: book.html)

**Example usage:**
```bash
polydoc book build -t book.yaml -d index.db -o book.html
```

## Phase 4: Search System

**Goal:** Full-text search across documentation with ranking and highlighting

### Search API

**Implementation:**
- Create `src/polydoc/search/core.clj`
- Implement FTS5 queries with next.jdbc
- Support query operators:
  - Boolean: `AND`, `OR`, `NOT`
  - Phrase: `"exact phrase"`
  - Field-specific: `title:introduction`
- Rank results by relevance
- Highlight matches in snippets using FTS5 highlight function

**Example query function:**
```clojure
(defn search-sections [ds query]
  (jdbc/execute! ds
    ["SELECT sections.*, 
            highlight(sections_fts, 1, '<mark>', '</mark>') as snippet,
            rank
      FROM sections
      JOIN sections_fts ON sections.section_id = sections_fts.rowid
      WHERE sections_fts MATCH ?
      ORDER BY rank
      LIMIT 50" query]))
```

**Testing:**
- Test basic search
- Test boolean operators
- Test phrase search
- Test field-specific search
- Test ranking
- Test highlighting

### CLI Search Command

**Implementation:**
- Add to main.clj
- Options:
  - `--query/-q` - Search query (required)
  - `--database/-d` - Database path (default: book.db)
  - `--limit/-l` - Max results (default: 50)
- Format output with bling for terminal display

**Example usage:**
```bash
polydoc search -q "pandoc filter" -d book.db
```

## Phase 5: Interactive Viewer

**Goal:** HTTP-based documentation browser with live search

### HTTP Server

**Implementation:**
- Create `src/polydoc/viewer/server.clj`
- Use http-kit for HTTP server
- Implement routes:
  - `GET /` - Main page with search
  - `GET /search?q=query` - Search results
  - `GET /section/:id` - Section detail
  - `GET /static/*` - Static assets (CSS, JS)
- Use hiccup for HTML generation

**Testing:**
- Test route handling
- Test search integration
- Test static file serving

### Client-Side Features

**Implementation:**
- Create basic HTML/CSS layout
- Search form with autocomplete
- Result display with highlighting
- Section navigation with TOC sidebar
- URL anchors for deep linking

### CLI View Command

**Implementation:**
- Add to main.clj
- Options:
  - `--database/-d` - Database path (default: book.db)
  - `--port/-p` - HTTP port (default: 8080)
- Optional: Open browser automatically

**Example usage:**
```bash
polydoc view -d book.db -p 8080
```

## Phase 6: Testing & Quality

**Goal:** Comprehensive test coverage and code quality

### Expand Test Suite

**Implementation:**
- Unit tests for all new filters
- Integration tests for book building
- Property-based tests with test.check:
  - AST transformations preserve structure
  - Search ranking consistency
  - Include cycle detection
- Performance tests for large documents
- Target: >80% code coverage

### Code Quality

**Implementation:**
- Zero lint warnings (clj-kondo)
- Format code consistently (cljfmt)
- Add docstrings to all public functions
- Update README with new features
- Create comprehensive examples

## Phase 7: GraalVM Native Compilation

**Goal:** Compile to native binary with GraalVM

### Reflection Configuration

**Implementation:**
- Identify reflection usage in dependencies
- Generate `reflection-config.json`
- Test all reflection paths
- Add metadata hints where needed

**Example reflection config:**
```json
[
  {
    "name": "java.sql.DriverManager",
    "methods": [{"name": "getConnection", "parameterTypes": ["java.lang.String"]}]
  }
]
```

### Resource Configuration

**Implementation:**
- Create `resource-config.json`
- Include necessary resources:
  - PlantUML JAR (if bundling)
  - HTML templates
  - CSS/JS assets
- Test resource loading

### Build Process

**Implementation:**
- Create `build.sh` script
- AOT compile main namespace
- Build uberjar with uberdeps
- Compile native image with GraalVM
- Test binary on Linux and macOS
- Measure startup time (target: <10ms)

**Example build script:**
```bash
#!/bin/bash
# AOT compile
clojure -M -e "(compile 'polydoc.main)"
# Build uberjar
clojure -M:uberdeps --main-class polydoc.main
# Native image
native-image \
  --no-fallback \
  --initialize-at-build-time \
  -H:ReflectionConfigurationFiles=reflection-config.json \
  -H:ResourceConfigurationFiles=resource-config.json \
  -jar target/polydoc.jar \
  polydoc
```

### CI/CD Setup

**Implementation:**
- Create `.github/workflows/build.yml`
- Test on multiple platforms (Linux, macOS, Windows optional)
- Upload artifacts
- Automate releases

## Phase 8: Documentation & Release

**Goal:** Production-ready documentation and release

### User Documentation

**Implementation:**
- Installation guide (Homebrew, binary download)
- Quick start tutorial
- Filter reference (all filters documented)
- Book building guide
- Search syntax guide
- Viewer setup instructions
- Examples for common use cases

### Developer Documentation

**Implementation:**
- Architecture overview
- Adding new filters guide
- Database schema documentation
- Testing guide
- Contributing guidelines
- Code of conduct

### Release Preparation

**Implementation:**
- Changelog (following Keep a Changelog format)
- Semantic versioning (start at 1.0.0)
- GitHub release with binaries
- Homebrew formula
- Submit to homebrew-core

**Example Homebrew formula:**
```ruby
class Polydoc < Formula
  desc "JVM-native Pandoc documentation system"
  homepage "https://github.com/user/polydoc"
  url "https://github.com/user/polydoc/releases/download/v1.0.0/polydoc-1.0.0.tar.gz"
  sha256 "..."
  
  def install
    bin.install "polydoc"
  end
end
```

## Success Criteria

**Functional Requirements:**
- All planned filters implemented and tested
- Book building works with complex TOCs
- Search returns relevant results with highlighting
- Viewer provides good UX
- Native binary works on Linux and macOS
- Test coverage >80%

**Quality Requirements:**
- Zero critical bugs
- Zero lint warnings
- All tests passing
- Documentation complete
- Examples work
- CI/CD green

**Performance Requirements:**
- Filter processing <100ms for typical documents
- Search queries <50ms on database with 1000 sections
- Native binary startup <10ms
- Book build for 50 documents <5 seconds

## Development Workflow

**Daily Cycle:**
1. Start REPL: `clojure -M:jvm-base:dev:nrepl`
2. Load dev namespace: `(require 'dev) (in-ns 'dev)`
3. Make changes in editor
4. Test in REPL: `(refresh)`, `(lint)`, `(run-all)`
5. Test CLI: `bb main <command>`
6. Commit when tests pass

**Before Each Commit:**
- Run linter: `bb lint` (zero warnings)
- Run tests: `bb test` (all passing)
- Update documentation if needed
- Add examples for new features

**Code Review Checklist:**
- Tests pass
- Linting clean
- Documentation updated
- Examples work
- REPL tested
- CLI tested
- Performance acceptable

## Required Tools

**Development:**
- Clojure 1.12.3+
- Pandoc 2.0+
- SQLite 3.35+
- Node.js (for JavaScript filter)
- Python 3 (for Python filter)

**Build:**
- GraalVM (latest)
- PlantUML JAR

**Documentation:**
- Pandoc AST specification
- cli-matic documentation
- next.jdbc guide
- SQLite FTS5 reference
- GraalVM native-image documentation

## Risk Mitigation

**GraalVM compilation issues:**
- Mitigation: Test compilation early in Phase 7
- Fallback: Provide uberjar as alternative

**Pandoc AST version compatibility:**
- Mitigation: Version detection, compatibility layer
- Fallback: Document supported Pandoc versions

**Performance issues with large documents:**
- Mitigation: Incremental processing, caching
- Fallback: Batch mode with progress indicators

**Scope creep:**
- Mitigation: Strict phase adherence, feature freeze after Phase 7
- Fallback: Move features to v2.0

**Testing gaps:**
- Mitigation: Write tests before implementation (TDD)
- Fallback: Extended testing phase if needed

## Next Steps

**Immediate:**
1. Fix lint warnings (Phase 1)
2. Verify test suite (Phase 1)
3. Start PlantUML filter (Phase 2)

**Short Term:**
1. Complete Phase 1 (fix issues)
2. Start Phase 2 (PlantUML filter)
3. Create comprehensive examples

**Medium Term:**
1. Complete Phase 2 (all filters)
2. Start Phase 3 (book building)
3. Begin database schema work
