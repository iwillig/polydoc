# SQLite Execution Filter Examples

This demonstrates the `sqlite-exec` filter for executing SQL queries in
documentation.

## Simple Query

  :number   :greeting
  --------- -----------
  1         Hello

## Multiple Rows

``` sqlite-exec
;; SQL Query:
SELECT *
FROM (VALUES
  (1, 'Alice', 30),
  (2, 'Bob', 25),
  (3, 'Charlie', 35)
) AS users(id, name, age)
ORDER BY age DESC;

;; ERROR:
[SQLITE_ERROR] SQL error or missing database (near "(": syntax error)
```

## Aggregation Query

``` sqlite-exec
;; SQL Query:
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

;; ERROR:
[SQLITE_ERROR] SQL error or missing database (near "(": syntax error)
```

## Text Format Output

Use `format="text"` to get plain text instead of a table:

``` {.sqlite-exec format="text"}
;; SQL Query:
SELECT 'Plain' as format, 'Text' as output, 'Result' as type;

;; Results:
:format | :output | :type 
--------+---------+-------
Plain   | Text    | Result
```

## Database File

You can query an external SQLite database:

``` {.sqlite-exec db="mydata.db"}
;; SQL Query:
SELECT * FROM users LIMIT 10;

;; ERROR:
[SQLITE_ERROR] SQL error or missing database (no such table: users)
```

## Date and Time Functions

  :today       :current_time   :local_datetime
  ------------ --------------- ---------------------
  2025-11-26   23:10:33        2025-11-26 18:10:33

## JSON Functions (SQLite 3.38+)

  :name   :age   :city
  ------- ------ -------
  Alice   30     NYC

## String Functions

  :uppercase    :lowercase    :string_length   :substring   :replaced
  ------------- ------------- ---------------- ------------ -------------------
  HELLO WORLD   hello world   7                doc          SQLite is awesome

## Math Functions

  :pi_rounded   :absolute   :power_of_two   :square_root   :random_number
  ------------- ----------- --------------- -------------- ----------------
  3.14          42          1024.0          12.0           -42

## Window Functions

``` sqlite-exec
;; SQL Query:
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

;; ERROR:
[SQLITE_ERROR] SQL error or missing database (near "(": syntax error)
```

## Common Table Expressions (CTEs)

  :x   :square   :cube
  ---- --------- -------
  1    1         1
  2    4         8
  3    9         27
  4    16        64
  5    25        125
  6    36        216
  7    49        343
  8    64        512
  9    81        729
  10   100       1000

## Error Handling

Invalid SQL will show an error message:

``` sqlite-exec
;; SQL Query:
THIS IS NOT VALID SQL;

;; ERROR:
[SQLITE_ERROR] SQL error or missing database (near "THIS": syntax error)
```

## Usage with Pandoc

Process this document:

``` bash
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

``` bash
pandoc document.md -t json | \
  clojure -M:main filter -t clojure-exec | \
  clojure -M:main filter -t sqlite-exec | \
  pandoc -f json -o output.html
```
