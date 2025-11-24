# Polydoc Examples

This directory contains example files demonstrating Polydoc's features.

## Running Examples

### Clojure Execution Filter

Execute Clojure code blocks in your documentation:

```bash
# Process the example file
clojure -M:main filter -t clojure-exec \
  -i examples/test-clojure-exec.json

# Or use with Pandoc
pandoc examples/clojure-exec-demo.md -t json | \
  clojure -M:main filter -t clojure-exec | \
  pandoc -f json -o examples/clojure-exec-output.html
```

### SQLite Execution Filter

Execute SQL queries and format as tables:

```bash
# Process the example file
clojure -M:main filter -t sqlite-exec \
  -i examples/test-sqlite-exec.json

# Or use with Pandoc
pandoc examples/sqlite-exec-demo.md -t json | \
  clojure -M:main filter -t sqlite-exec | \
  pandoc -f json -o examples/sqlite-exec-output.html
```

## Example Files

- `test-clojure-exec.json` - Simple Pandoc AST with clojure-exec blocks
- `clojure-exec-demo.md` - Markdown file demonstrating clojure-exec usage
- `test-sqlite-exec.json` - Simple Pandoc AST with sqlite-exec blocks
- `sqlite-exec-demo.md` - Markdown file demonstrating sqlite-exec usage
- `README.md` - This file

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
