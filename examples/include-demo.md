# Include Filter Demo

The Include filter allows you to include external files in your Markdown documents, supporting multiple modes for different use cases.

## Basic File Inclusion (Parse Mode)

Include another Markdown file and parse it as Markdown (default):

```{.include}
test/fixtures/include/simple.md
```

The content above will be parsed as Markdown and included inline.

## Include as Code Block

Include a source file and display it with syntax highlighting:

```{.include mode=code lang=clojure}
test/fixtures/include/code.clj
```

## Include as Raw Markdown

Include content as raw Markdown without parsing:

```{.include mode=raw}
test/fixtures/include/simple.md
```

This is useful when you want to include Markdown that shouldn't be processed.

## Relative Paths with Base Directory

You can specify a base directory for resolving relative paths:

```{.include base="test/fixtures"}
include/code.clj
```

## Include Modes

### Parse Mode (default)

Parses the included file as Markdown and inserts the resulting AST nodes.

```markdown
```{.include}
path/to/document.md
\```
```

**Use when:** Including Markdown content that should be processed normally.

### Code Mode

Includes the file as a syntax-highlighted code block.

```markdown
```{.include mode=code lang=clojure}
src/example.clj
\```
```

**Use when:** Showing source code examples from actual files.

### Raw Mode

Includes the file as a RawBlock without any parsing.

```markdown
```{.include mode=raw}
template.html
\```
```

**Use when:** Including content that shouldn't be processed (HTML, templates, etc.).

## Advanced Features

### Cycle Detection

The filter automatically detects include cycles to prevent infinite loops:

```markdown
<!-- In file-a.md -->
```{.include}
file-b.md
\```

<!-- In file-b.md -->
```{.include}
file-a.md
\```

<!-- This will result in an error block showing the cycle -->
```

### Maximum Depth

Includes are limited to 10 levels deep by default to prevent excessive nesting.

### Path Resolution

- Relative paths are resolved relative to the current working directory
- Use the `base` attribute to set a different base directory
- Absolute paths are used as-is
- Paths are normalized (removing `.` and `..` components)

## Error Handling

When a file cannot be included (missing file, permission error, etc.), an error block is shown:

```{.include}
nonexistent-file.md
```

This will show:
```
ERROR including file: nonexistent-file.md
File not found: /absolute/path/to/nonexistent-file.md
```

## Usage

Transform this document:

```bash
# Convert to JSON AST, apply filter, then to HTML
pandoc include-demo.md -t json | \
  clojure -M:main filter -t include | \
  pandoc -f json -o output.html

# Or directly
clojure -M:main filter -t include -i include-demo.md -o output.html
```

## Combining with Other Filters

Include filter works well with other filters:

```bash
# First include external files, then execute Clojure code blocks
pandoc doc.md -t json | \
  clojure -M:main filter -t include | \
  clojure -M:main filter -t clojure-exec | \
  pandoc -f json -o output.html
```

## Use Cases

1. **Modular Documentation**: Break large documents into smaller, reusable files
2. **Code Examples**: Include actual source code that's maintained separately
3. **Shared Content**: Reuse content across multiple documents
4. **Templates**: Include common headers, footers, or boilerplate
5. **Book Building**: Assemble chapters from individual files

## Notes

- Requires Pandoc to be installed for parse mode
- File paths are resolved at filter execution time
- Included content is processed in the same Pandoc pipeline
- Parse mode supports nested includes (up to max depth)
