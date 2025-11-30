# Configuration System Review - Executive Summary

**Date:** 2025-11-29  
**Full Review:** See `CONFIG_SYSTEM_REVIEW.md`

## TL;DR

Polydoc uses a **YAML-based configuration system** (`polydoc.yml`) with Malli schema validation. The system is well-designed with clear separation of concerns, good defaults, and comprehensive test coverage.

## Configuration at a Glance

### Minimal Book Configuration

```yaml
title: "My Book"
sections:
  - intro.md
  - chapter1.md
```

That's all you need! Polydoc fills in sensible defaults for everything else.

### Full Book Configuration

```yaml
# Standard Pandoc metadata
title: "My Book"
author: "Author Name"
date: "2025-11-25"
lang: "en-US"
toc: true
toc-depth: 3
css:
  - css/style.css

# Polydoc-specific configuration
book:
  id: "my-book"                    # Unique identifier (auto-generated from title if omitted)
  version: "1.0.0"
  database: "polydoc.db"            # SQLite database path
  output-dir: "build/"
  filters:                          # Global filters for all sections
    - clojure-exec
    - plantuml
    - include

# Sections with per-section overrides
sections:
  - intro.md                        # Simple format
  - file: tutorial.md               # Extended format
    title: "Tutorial"
    filters: [clojure-exec]         # Override global filters
    metadata:
      difficulty: beginner
```

## CLI Commands

```bash
# Build a book
clojure -M:main book -c polydoc.yml -o output/

# Execute a filter on Pandoc AST
clojure -M:main filter -t clojure-exec -i input.json -o output.json

# Search documentation
clojure -M:main search -d polydoc.db -q "search query" -l 10

# Start viewer (coming soon)
clojure -M:main view -d polydoc.db -p 8080
```

## Key Design Patterns

1. **Schema Validation** - Malli schema with humanized error messages
2. **Polymorphic Sections** - Simple string or extended map format
3. **Path Resolution** - All file paths resolved to absolute during loading
4. **Sensible Defaults** - Works out-of-the-box with minimal config
5. **Filter Registry** - Easy to add new filters

## Architecture Highlights

```
polydoc.yml (YAML)
    ↓
Load & Validate (Malli)
    ↓
Resolve Paths (absolute)
    ↓
Initialize Database (SQLite + Ragtime)
    ↓
Process Sections (Pandoc filters)
    ↓
Generate Output (HTML, etc.)
```

## What Works Well ✅

- Schema validation with clear error messages
- Flexible section configuration (string or map)
- Comprehensive test coverage (metadata_test.clj)
- Clean separation: config → database → processing → output
- Sensible defaults for everything
- Absolute path resolution (no ambiguity)

## Quick Wins ⚠️

1. **Add example polydoc.yml files**
   - Minimal, standard, and advanced examples
   - Add to `examples/` directory

2. **Document filter behavior**
   - Section filters **replace** (not extend) global filters
   - Add clear examples in docs

3. **Database path resolution**
   - Currently relative to CWD
   - Should be relative to polydoc.yml like section files

4. **Add validation command**
   ```bash
   clojure -M:main validate -c polydoc.yml
   ```

## Configuration Extension Points

### Add a New Filter

```clojure
;; 1. Create filter namespace
(ns polydoc.filters.my-filter
  (:require [clojure.walk :as walk]))

(defn my-filter [ast]
  (walk/postwalk transform-fn ast))

;; 2. Register in polydoc.book.builder
(def filter-registry
  {"my-filter" my-filter/my-filter
   ...})
```

### Add a New Field

```clojure
;; 1. Add to Malli schema
[:new-field {:optional true} :string]

;; 2. Add getter function
(defn get-new-field [metadata]
  (get-in metadata [:book :new-field] "default"))

;; 3. Use in builder
(let [value (metadata/get-new-field metadata)]
  ...)
```

## Files to Know

### Core
- `src/polydoc/main.clj` - CLI configuration (cli-matic)
- `src/polydoc/book/metadata.clj` - YAML parsing & validation (Malli)
- `src/polydoc/book/builder.clj` - Configuration → build pipeline

### Database
- `src/polydoc/db/schema.clj` - Schema management (Ragtime)
- `resources/migrations/001-initial-schema.edn` - Database schema

### Tests
- `test/polydoc/book/metadata_test.clj` - Configuration testing

## Conclusion

**Solid foundation** with clear architecture and good test coverage. Ready for use with a few documentation improvements recommended.

**Rating: 8.5/10**
- Strong: Architecture, validation, testing
- Improve: Documentation, examples, minor edge cases
