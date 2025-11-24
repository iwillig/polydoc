# SQLite Execution Filter Examples

This demonstrates the `sqlite-exec` filter for executing SQL queries in documentation.

## Simple Query

```{.sqlite-exec}
SELECT 1 as number, 'Hello' as greeting;
```

## Multiple Rows

```{.sqlite-exec}
SELECT *
FROM (VALUES
  (1, 'Alice', 30),
  (2, 'Bob', 25),
  (3, 'Charlie', 35)
) AS users(id, name, age)
ORDER BY age DESC;
```

## Aggregation Query

```{.sqlite-exec}
WITH sales AS (
  SELECT * FROM (VALUES
    ('Electronics', 1000),
    ('Books', 500),
    ('Electronics', 1500),
    ('Books', 800),
    ('Clothing', 600)
  ) AS t(category, amount)
)
SELECT 
  category,
  COUNT(*) as count,
  SUM(amount) as total,
  AVG(amount) as average
FROM sales
GROUP BY category
ORDER BY total DESC;
```

## Text Format Output

Use `format="text"` to get plain text instead of a table:

```{.sqlite-exec format="text"}
SELECT 'Plain' as format, 'Text' as output, 'Result' as type;
```

## Database File

You can query an external SQLite database:

```{.sqlite-exec db="mydata.db"}
SELECT * FROM users LIMIT 10;
```

## Date and Time Functions

```{.sqlite-exec}
SELECT 
  date('now') as today,
  time('now') as current_time,
  datetime('now', 'localtime') as local_datetime;
```

## JSON Functions (SQLite 3.38+)

```{.sqlite-exec}
WITH data AS (
  SELECT json('{"name":"Alice","age":30,"city":"NYC"}') as json_data
)
SELECT 
  json_extract(json_data, '$.name') as name,
  json_extract(json_data, '$.age') as age,
  json_extract(json_data, '$.city') as city
FROM data;
```

## String Functions

```{.sqlite-exec}
SELECT 
  upper('hello world') as uppercase,
  lower('HELLO WORLD') as lowercase,
  length('polydoc') as string_length,
  substr('documentation', 1, 3) as substring,
  replace('SQLite rocks', 'rocks', 'is awesome') as replaced;
```

## Math Functions

```{.sqlite-exec}
SELECT 
  round(3.14159, 2) as pi_rounded,
  abs(-42) as absolute,
  power(2, 10) as power_of_two,
  sqrt(144) as square_root,
  random() % 100 as random_number;
```

## Window Functions

```{.sqlite-exec}
WITH scores AS (
  SELECT * FROM (VALUES
    ('Alice', 95),
    ('Bob', 87),
    ('Charlie', 92),
    ('David', 88),
    ('Eve', 90)
  ) AS t(name, score)
)
SELECT 
  name,
  score,
  rank() OVER (ORDER BY score DESC) as rank,
  row_number() OVER (ORDER BY score DESC) as row_num
FROM scores;
```

## Common Table Expressions (CTEs)

```{.sqlite-exec}
WITH RECURSIVE cnt(x) AS (
  SELECT 1
  UNION ALL
  SELECT x+1 FROM cnt
  LIMIT 10
)
SELECT x, x*x as square, x*x*x as cube
FROM cnt;
```

## Error Handling

Invalid SQL will show an error message:

```{.sqlite-exec}
THIS IS NOT VALID SQL;
```

## Usage with Pandoc

Process this document:

```bash
# Convert to HTML with SQL queries executed
pandoc sqlite-exec-demo.md -t json | \
  clojure -M:main filter -t sqlite-exec | \
  pandoc -f json -o output.html

# Or directly
clojure -M:main filter -t sqlite-exec \
  -i sqlite-exec-demo.json \
  -o output.json
```

## Integration Example

Combine with other filters:

```bash
pandoc document.md -t json | \
  clojure -M:main filter -t clojure-exec | \
  clojure -M:main filter -t sqlite-exec | \
  pandoc -f json -o output.html
```
