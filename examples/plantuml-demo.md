# PlantUML Filter Demo

The PlantUML filter allows you to embed UML diagrams directly in your Markdown documents.

## Sequence Diagram

```{.plantuml}
@startuml
Alice -> Bob: Authentication Request
Bob --> Alice: Authentication Response

Alice -> Bob: Another authentication Request
Alice <-- Bob: Another authentication Response
@enduml
```

## Class Diagram

```{.uml format=svg}
@startuml
class User {
  +String name
  +String email
  +login()
  +logout()
}

class Admin {
  +manageUsers()
}

User <|-- Admin
@enduml
```

## Activity Diagram

```{.plantuml}
@startuml
start
:User logs in;
if (Credentials valid?) then (yes)
  :Grant access;
else (no)
  :Show error;
  stop
endif
:Display dashboard;
stop
@enduml
```

## Text Output (ASCII Art)

For terminals or text-only output, use the `txt` format:

```{.plantuml format=txt}
@startuml
[Component A] --> [Component B]: Uses
[Component B] --> [Component C]: Depends on
@enduml
```

## Usage

Transform this document:

```bash
# Convert to JSON AST, apply filter, then to HTML
pandoc plantuml-demo.md -t json | \
  clojure -M:main filter -t plantuml | \
  pandoc -f json -o output.html

# Or directly
clojure -M:main filter -t plantuml -i plantuml-demo.md -o output.html
```

## Supported Formats

- `svg` - Scalable Vector Graphics (default)
- `png` - Portable Network Graphics
- `txt` - ASCII art (text mode)
- `pdf` - Portable Document Format
- `eps` - Encapsulated PostScript
- `latex` - LaTeX/TikZ format

Specify format with the `format` attribute:

```markdown
```{.plantuml format=png}
@startuml
...
@enduml
\```
```

## Notes

- Requires PlantUML to be installed and available in PATH
- Uses pipe mode for efficiency (stdin/stdout)
- Binary formats (SVG, PNG) are embedded as data URIs
- Text format is embedded as CodeBlock for easy viewing
