# Polydoc Examples

This directory contains example files demonstrating Polydoc's features.

## Output Files

Each example has both HTML and Markdown output files for easy viewing:

- **HTML output** (`*-output.html`) - Fully rendered HTML pages
- **Markdown output** (`*-output.md`) - Markdown format for viewing on GitHub

The Markdown output files are generated with:
```bash
pandoc examples/demo.md -t json | \
  clojure -M:main filter -t <filter-type> | \
  pandoc -f json -t markdown -o examples/demo-output.md
```

## Running Examples

### Clojure Execution Filter

Execute Clojure code blocks in your documentation:

```bash
# Process the example file
clojure -M:main filter -t clojure-exec \
  -i examples/test-clojure-exec.json

# Generate HTML output
pandoc examples/clojure-exec-demo.md -t json | \
  clojure -M:main filter -t clojure-exec | \
  pandoc -f json -o examples/clojure-exec-output.html

# Generate Markdown output (for GitHub viewing)
pandoc examples/clojure-exec-demo.md -t json | \
  clojure -M:main filter -t clojure-exec | \
  pandoc -f json -t markdown -o examples/clojure-exec-output.md
```

**View output:** [clojure-exec-output.md](clojure-exec-output.md) | [clojure-exec-output.html](clojure-exec-output.html)

### SQLite Execution Filter

Execute SQL queries and format as tables:

```bash
# Process the example file
clojure -M:main filter -t sqlite-exec \
  -i examples/test-sqlite-exec.json

# Generate HTML output
pandoc examples/sqlite-exec-demo.md -t json | \
  clojure -M:main filter -t sqlite-exec | \
  pandoc -f json -o examples/sqlite-exec-output.html

# Generate Markdown output (for GitHub viewing)
pandoc examples/sqlite-exec-demo.md -t json | \
  clojure -M:main filter -t sqlite-exec | \
  pandoc -f json -t markdown -o examples/sqlite-exec-output.md
```

**View output:** [sqlite-exec-output.md](sqlite-exec-output.md) | [sqlite-exec-output.html](sqlite-exec-output.html)

### PlantUML Filter

Render UML diagrams from PlantUML code:

```bash
# Process the example file
pandoc examples/plantuml-demo.md -t json | \
  clojure -M:main filter -t plantuml | \
  pandoc -f json -o examples/plantuml-output.html
```

### Include Filter

Include external files in your documentation:

```bash
# Generate HTML output
pandoc examples/include-demo.md -t json | \
  clojure -M:main filter -t include | \
  pandoc -f json -o examples/include-output.html

# Generate Markdown output (for GitHub viewing)
pandoc examples/include-demo.md -t json | \
  clojure -M:main filter -t include | \
  pandoc -f json -t markdown -o examples/include-output.md
```

**View output:** [include-output.md](include-output.md) | [include-output.html](include-output.html)

### JavaScript Execution Filter

Execute JavaScript code blocks using GraalVM:

```bash
# Process the example file
clojure -M:main filter -t javascript-exec \
  -i examples/test-javascript-exec.json

# Generate HTML output
pandoc examples/javascript-exec-demo.md -t json | \
  clojure -M:main filter -t javascript-exec | \
  pandoc -f json -o examples/javascript-exec-output.html

# Generate Markdown output (for GitHub viewing)
pandoc examples/javascript-exec-demo.md -t json | \
  clojure -M:main filter -t javascript-exec | \
  pandoc -f json -t markdown -o examples/javascript-exec-output.md
```

**View output:** [javascript-exec-output.md](javascript-exec-output.md) | [javascript-exec-output.html](javascript-exec-output.html)

## Example Files

### Input Files
- `clojure-exec-demo.md` - Clojure code execution examples
- `javascript-exec-demo.md` - JavaScript code execution examples
- `sqlite-exec-demo.md` - SQLite query execution examples
- `include-demo.md` - File inclusion examples
- `plantuml-demo.md` - PlantUML diagram examples
- `python-exec-demo.md` - Python code execution examples (future)

### Output Files
- `*-output.md` - Markdown output (easy to view on GitHub)
- `*-output.html` - HTML output (fully rendered pages)

### Test Data
- `test-clojure-exec.json` - Pandoc AST for testing clojure-exec
- `test-javascript-exec.json` - Pandoc AST for testing javascript-exec
- `test-sqlite-exec.json` - Pandoc AST for testing sqlite-exec

## Expected Output

When you run the clojure-exec filter on `test-clojure-exec.json`, you should see:

- The `clojure-exec` code block `(+ 1 2 3)` is replaced with:
  ```
  ;; Original code:
  (+ 1 2 3)

  ;; Execution result:
  Result:
  6
  ```
- Regular code blocks (without `clojure-exec` class) remain unchanged
