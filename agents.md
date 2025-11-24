# Agents Guide for Polydoc Development

This document provides guidance for LLM agents working on the Polydoc project, a Clojure-based tool for building documentation systems using Pandoc filters.

## Project Overview

**Polydoc** is a documentation processing tool that brings Pandoc's powerful filtering capabilities to the JVM/Clojure ecosystem. It aims to provide:

- Advanced Pandoc filters compiled with GraalVM for reduced latency
- JVM-native documentation tooling (no Python/Node.js dependencies)
- Support for multiple document formats via Pandoc
- Interactive documentation viewing with SQLite-powered search
- LLM-driven document processing capabilities

### Key Capabilities

The project implements several types of Pandoc filters:

- **Code execution filters**: Run Clojure, SQLite, JavaScript, Python code blocks
- **Rendering filters**: Process PlantUML diagrams
- **Linting filters**: Check Clojure code with clj-kondo
- **Include filters**: Compose documents from multiple sources
- **Build system**: Generate books with table of contents and full-text search
- **Interactive viewer**: HTTP-based document browser with search

## Dependencies Analysis

### Core Dependencies (deps.edn)

```clojure
{:deps
 {org.clojure/clojure {:mvn/version "1.12.3"}           ; Latest Clojure
  
  ;; CLI & Terminal
  cli-matic/cli-matic {:mvn/version "0.5.4"}           ; CLI interface
  io.github.paintparty/bling {:mvn/version "0.8.8"}    ; Terminal formatting
  
  ;; Data Processing
  org.clojure/data.json {:mvn/version "2.5.1"}         ; JSON handling (Pandoc AST)
  clj-commons/clj-yaml {:mvn/version "1.0.29"}         ; YAML support
  org.clj-commons/pretty {:mvn/version "3.6.7"}        ; Pretty printing
  mvxcvi/puget {:mvn/version "1.3.4"}                  ; Pretty printing
  
  ;; Data Structures & Validation
  metosin/malli {:mvn/version "0.19.2"}                ; Schema validation
  funcool/lentes {:mvn/version "1.3.3"}                ; Functional lenses
  
  ;; Database
  com.github.seancorfield/honeysql {:mvn/version "2.7.1350"}   ; SQL DSL
  com.github.seancorfield/next.jdbc {:mvn/version "1.3.1070"}  ; JDBC wrapper
  org.xerial/sqlite-jdbc {:mvn/version "3.47.1.0"}             ; SQLite driver
  
  ;; Development Tools
  dev.weavejester/hashp {:mvn/version "0.5.1"}         ; Debug printing
  dev.glossa/metazoa {:mvn/version "0.2.298"}          ; Metadata tooling
  com.github.clojure-lsp/clojure-lsp {:mvn/version "2025.08.25-14.21.46"}}}
```

### Development Dependencies (:dev alias)

```clojure
:dev {:extra-deps
      {;; Testing Framework
       lambdaisland/kaocha {:mvn/version "1.91.1392"}
       lambdaisland/kaocha-cloverage {:mvn/version "1.1.89"}
       lambdaisland/kaocha-junit-xml {:mvn/version "1.17.101"}
       nubank/matcher-combinators {:mvn/version "3.9.1"}
       org.clojure/test.check {:mvn/version "1.1.1"}
       com.gfredericks/test.chuck {:mvn/version "0.2.13"}
       
       ;; Development Tools
       io.github.tonsky/clj-reload {:mvn/version "0.9.8"}
       com.stuartsierra.component.repl {:mvn/version "0.2.0"}
       
       ;; Analysis & Linting
       com.github.clojure-lsp/clojure-lsp {:mvn/version "2025.08.25-14.21.46"}
       clj-kondo/clj-kondo {:mvn/version "2022.09.08"}
       
       ;; nREPL
       org.clojure/tools.nrepl {:mvn/version "0.2.11"}}
      :extra-paths ["test" "dev"]}
```

### Project Structure

```
polydoc/
├── src/polydoc/           # Main source code
│   └── main.clj           # Entry point
├── dev/                   # Development namespace
│   ├── user.clj          # User namespace (REPL startup)
│   └── dev.clj           # Dev namespace (tools & helpers)
├── test/                  # Test directory
├── resources/             # Resources
├── classes/               # Compiled classes
├── deps.edn              # Dependencies
├── bb.edn                # Babashka config
└── README.org            # Project documentation
```

## Agent Development Workflow

When working on Polydoc, follow the **Gather-Action-Verify** loop pattern:

### 1. Gather Context

**Before making any changes:**

```clojure
;; Connect to the nREPL (port 7889 configured in deps.edn)
;; Start REPL: clojure -M:nrepl

;; Explore the codebase
(require '[clojure-lsp.api :as lsp-api])

;; Find diagnostics (errors, warnings)
(lsp-api/diagnostics {:namespace '[polydoc.main]})

;; List all loaded namespaces
(all-ns)
(map ns-name (all-ns))

;; Examine a specific namespace
(require '[polydoc.main])
(dir polydoc.main)

;; Get documentation
(doc polydoc.main/some-function)

;; View source code
(source polydoc.main/some-function)

;; Search for symbols
(apropos "filter")
```

**Read files strategically:**

- Use the `read` tool to examine source files
- Start with high-level overview (scan defn names)
- Dive into specific functions as needed
- Check related namespaces for dependencies

**Key questions to answer:**

- What is the current implementation?
- What are the dependencies?
- Are there existing tests?
- What's the expected behavior?
- Are there any diagnostics/errors?

### 2. Take Action

**Make focused, incremental changes:**

```clojure
;; Use standard editing tools to modify files
;; - Read the file first
;; - Make targeted edits
;; - Save the file

;; Clean up after editing
(require '[clojure-lsp.api :as lsp-api])
(lsp-api/clean-ns! {:namespace '[polydoc.main]})
(lsp-api/format! {:namespace '[polydoc.main]})

;; For safe renaming
(lsp-api/rename! {:from 'old-fn :to 'new-fn})
```

**Tool selection guide:**

| Task | Tool | Example |
|------|------|---------|
| Read code | `read` | Read source files |
| Edit code | `edit` or `write` | Modify functions |
| Rename safely | `lsp-api/rename!` | Updates all references |
| Clean imports | `lsp-api/clean-ns!` | Removes unused requires |
| Format code | `lsp-api/format!` | Applies formatting rules |

### 3. Verify Output

**Always verify changes:**

```clojure
;; 1. Reload the namespace
(require 'polydoc.main :reload)

;; 2. Test basic functionality
(polydoc.main/your-function test-input)

;; 3. Test edge cases
(polydoc.main/your-function nil)
(polydoc.main/your-function [])
(polydoc.main/your-function "invalid-input")

;; 4. Check for new diagnostics
(lsp-api/diagnostics {:namespace '[polydoc.main]})

;; 5. Run tests (if they exist)
(require 'polydoc.main-test :reload)
(clojure.test/run-tests 'polydoc.main-test)

;; Or use Kaocha
;; Shell: clojure -M:test
```

**Verification checklist:**

- [ ] Namespace reloaded successfully
- [ ] Function works with valid input
- [ ] Edge cases handled (nil, empty, invalid)
- [ ] No new diagnostics/warnings
- [ ] Existing tests still pass
- [ ] New behavior tested

## Common Development Tasks

### Adding a New Pandoc Filter

**Context needed:**
- What input format does the filter process? (Pandoc AST)
- What transformation should it perform?
- What external tools are needed? (e.g., PlantUML, SQLite)
- How should errors be handled?

**Implementation pattern:**

```clojure
;; 1. Read existing filter for reference
;; Example: filters that process code blocks

;; 2. Define filter function
(defn process-my-filter
  "Processes [specific element type] in Pandoc AST."
  [ast]
  (walk/postwalk
    (fn [node]
      (if (matches? node)
        (transform node)
        node))
    ast))

;; 3. Add CLI command in main.clj
;; Use cli-matic structure

;; 4. Test with sample Pandoc AST
(process-my-filter sample-ast)
```

### Working with Pandoc AST

Pandoc represents documents as JSON AST. Key points:

```clojure
;; Parse JSON AST from stdin/file
(require '[clojure.data.json :as json])
(def ast (json/read-str (slurp "input.json") :key-fn keyword))

;; Walk and transform AST
(require '[clojure.walk :as walk])

;; Emit modified AST to stdout
(println (json/write-str modified-ast))
```

**Common AST patterns:**

- `{:t "CodeBlock" :c [attrs code]}` - Code blocks
- `{:t "Para" :c [...]}` - Paragraphs  
- `{:t "Header" :c [level attrs content]}` - Headers

### Database Operations

Polydoc uses SQLite for search indexing:

```clojure
(require '[next.jdbc :as jdbc])
(require '[honey.sql :as sql])

;; Create connection
(def db {:dbtype "sqlite" :dbname "polydoc.db"})
(def ds (jdbc/get-datasource db))

;; Execute query with HoneySQL
(jdbc/execute! ds
  (sql/format {:select [:*]
               :from [:sections]
               :where [:= :book_id 1]}))

;; Insert data
(jdbc/execute! ds
  (sql/format {:insert-into :sections
               :values [{:book_id 1
                        :content "..."
                        :hash "..."}]}))
```

### Adding CLI Commands

Polydoc uses cli-matic for command-line interface:

```clojure
;; In main.clj, add to command configuration
{:command "my-command"
 :description "Does something useful"
 :opts [{:option "input"
         :short "i"
         :as "Input file"
         :type :string
         :required true}]
 :runs my-command-fn}

;; Implement the function
(defn my-command-fn
  [{:keys [input]}]
  ;; Process input
  (println "Processing:" input))
```

### Using Debugging Tools

```clojure
;; hashp - Quick debugging with #p reader macro
(require '[hashp.install :as hashp])
(hashp/install!)

;; Use #p to debug any expression
(defn calculate [x y]
  (+ #p (* x 2) #p (/ y 3)))

(calculate 10 9)
;; Output to STDERR:
;; #p[user/calculate:2] (* x 2) => 20
;; #p[user/calculate:2] (/ y 3) => 3
;; => 23

;; Debug threading macros
(-> data
    (assoc :x 10)
    #p
    (update :y inc)
    #p)

;; Debug let bindings
(let [a #p (* x 2)
      b #p (+ y 3)
      c #p (- a b)]
  (/ c 2))
```

### Working with Metadata

Polydoc uses Metazoa for metadata tooling:

```clojure
(require '[glossa.metazoa :as meta])

;; View examples from metadata
(meta/view #'polydoc.main/some-function :glossa.metazoa/example)

;; Check that examples still work
(meta/check #'polydoc.main/some-function :glossa.metazoa/example)

;; Search for functions
(meta/search "name:filter*")

;; Query with Datalog
(meta/query
  '[:find [?name ...]
    :where
    [?e :ns ?ns]
    [?e :name ?name]]
  (the-ns 'polydoc.main))
```

## Best Practices for Polydoc Development

### DO:

1. **Read before writing**
   - Use `read` tool to examine files
   - Understand existing patterns
   - Check for similar implementations

2. **Test incrementally**
   - Reload namespace after changes
   - Test happy path first
   - Then test edge cases (nil, empty, invalid)

3. **Use appropriate tools**
   - Static analysis with clojure-lsp
   - Runtime testing with REPL
   - Combine both for confidence

4. **Follow Clojure idioms**
   - Pure functions where possible
   - Data transformation over mutation
   - Clear naming conventions

5. **Leverage existing libraries**
   - HoneySQL for SQL generation
   - Malli for validation
   - cli-matic for CLI structure

6. **Document as you go**
   - Add docstrings to functions
   - Update README.org for major changes
   - Include examples in metadata

7. **Use hashp for debugging**
   - Add `#p` liberally during development
   - Remove before committing
   - Shows context (ns/fn/line) with values

### DON'T:

1. **Skip verification**
   - Always test changes
   - Check diagnostics
   - Run tests if they exist

2. **Make large, unfocused changes**
   - One task at a time
   - Small, testable increments
   - Easy to verify and rollback

3. **Ignore errors**
   - Fix diagnostics immediately
   - Handle edge cases
   - Provide helpful error messages

4. **Forget to reload**
   - Always `:reload` after editing
   - Check that changes took effect
   - Re-run tests after reload

5. **Hardcode values**
   - Use configuration
   - Environment variables for paths
   - Make code reusable

6. **Assume without testing**
   - Verify behavior at REPL
   - Test edge cases explicitly
   - Don't rely on "it should work"

7. **Commit debug statements**
   - Remove `#p` statements before committing
   - Or use conditional enabling for development

## Common Issues & Solutions

### Issue: "Unable to resolve symbol after editing"

**Cause:** Forgot to reload namespace

```clojure
;; Solution
(require 'polydoc.main :reload)
(polydoc.main/new-function args)  ; Now works
```

### Issue: "Changes don't take effect"

**Cause:** Old namespace still loaded

```clojure
;; Solution: Force complete reload
(require 'polydoc.main :reload-all)

;; Verify with read tool that edit was saved
```

### Issue: "Tests fail after refactoring"

**Cause:** Test namespace not reloaded

```clojure
;; Solution
(require 'polydoc.main-test :reload)
(clojure.test/run-tests 'polydoc.main-test)
```

### Issue: "Can't find function/namespace"

**Cause:** Exploring unfamiliar codebase

```clojure
;; Solution: Use discovery tools
(map ns-name (all-ns))
(apropos "filter")
(find-doc "pandoc")

;; Or search with metazoa
(require '[glossa.metazoa :as meta])
(meta/search "name:process*")
```

### Issue: "Pandoc AST structure unclear"

**Cause:** JSON structure is complex

```clojure
;; Solution: Pretty-print and explore
(require '[puget.printer :as puget])
(puget/cprint sample-ast)

;; Or use bling for terminal formatting
(require '[bling.core :as b])
(b/pprint sample-ast)
```

### Issue: "Need to trace execution flow"

**Cause:** Complex logic hard to follow

```clojure
;; Solution: Use hashp throughout
(defn complex-process [data]
  (-> data
      (step-1)
      #p
      (step-2)
      #p
      (step-3)
      #p))

;; Shows value at each step with context
```

## Development Workflow Commands

### Starting Development

```bash
# Start nREPL server
clojure -M:nrepl

# In another terminal, run tests
clojure -M:test

# Check for outdated dependencies
clojure -M:outdated -e
```

### In the REPL

```clojure
;; Start in user namespace
user=> (dev)  ; Load dev namespace

;; In dev namespace
dev=> (refresh)   ; Reload all namespaces
dev=> (lint)      ; Run clj-kondo
dev=> (run-all)   ; Run all tests

;; Install hashp for debugging
((requiring-resolve 'hashp.install/install!))
```

### Building & Testing

```bash
# Run tests with Kaocha
clojure -M:test

# Lint with clj-kondo
clojure -M:lint -m clj-kondo.main --lint src

# Format code with cljstyle
clojure -M:format -m cljstyle.main fix

# Generate documentation
clojure -X:codox
```

## LLM Agent Limitations

As an LLM agent working on this project, remember:

1. **You cannot debug complex issues alone**
   - Ask the user for help with obscure errors
   - Provide context: what you tried, what failed
   - Show error messages clearly

2. **You need user input for decisions**
   - Architecture choices
   - API design
   - Trade-offs between approaches

3. **Test assumptions immediately**
   - Don't assume code works
   - Verify at the REPL
   - Run tests to confirm

4. **Communicate progress**
   - Explain what you're doing
   - Show verification steps
   - Report errors clearly

## Resources

### Relevant Clojure Skills

- **Language**: `clojure_intro`, `clojure_repl`
- **Data**: `data_json`, `clj_yaml`, `malli`, `lentes`
- **Database**: `next_jdbc`, `honeysql`, `sqlite_jdbc`
- **CLI**: `cli_matic`, `bling`
- **Testing**: `kaocha`, `matcher_combinators`, `test_check`
- **Tools**: `clojure_lsp_api`, `metazoa`, `hashp-debugging`

### External Documentation

- [Pandoc Filters](https://pandoc.org/filters.html) - Filter system documentation
- [Pandoc AST](https://pandoc.org/using-the-pandoc-api.html) - AST structure reference
- [cli-matic](https://github.com/l3nz/cli-matic) - CLI framework
- [HoneySQL](https://github.com/seancorfield/honeysql) - SQL DSL
- [Malli](https://github.com/metosin/malli) - Data validation
- [hashp](https://github.com/weavejester/hashp) - Debug printing

## Summary

When working on Polydoc:

1. **Gather** - Read code, explore with REPL tools, use clojure-lsp for diagnostics
2. **Action** - Make focused changes with read/edit/write tools
3. **Verify** - Reload, test, check diagnostics, run tests

**Key principles:**
- Read before writing
- Test after editing  
- Use static analysis (clojure-lsp) + runtime testing (REPL)
- Use hashp (#p) for debugging during development
- Communicate clearly
- Fix errors immediately
- Follow Clojure idioms
- Leverage existing libraries

This ensures reliable code changes that work correctly in the Polydoc documentation processing system.
