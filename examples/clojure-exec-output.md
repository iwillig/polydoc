# Clojure Execution Demo

This document demonstrates the `clojure-exec` filter for Polydoc.

## Simple Arithmetic

Calculate the sum of numbers 1 through 10:

``` clojure-exec
;; Original code:
(reduce + (range 1 11))

;; Execution result:
Result:
55
```

## String Manipulation

Convert text to uppercase:

``` clojure-exec
;; Original code:
(clojure.string/upper-case "hello world")

;; Execution result:
Result:
"HELLO WORLD"
```

## Data Structures

Create and manipulate a map:

``` clojure-exec
;; Original code:
(assoc {:name "Alice" :age 30} :role :admin)

;; Execution result:
Result:
{:name "Alice", :age 30, :role :admin}
```

## Multi-line Code

More complex calculations:

``` clojure-exec
;; Original code:
(let [numbers [1 2 3 4 5]
      doubled (map #(* 2 %) numbers)
      sum (reduce + doubled)]
  sum)

;; Execution result:
Result:
30
```

## Code with Output

Print and return:

``` clojure-exec
;; Original code:
(do
  (println "Processing...")
  (println "Done!")
  42)

;; Execution result:
Output:
Processing...
Done!


Result:
42
```

## Regular Clojure Code (Not Executed)

This block has no `.clojure-exec` class, so it won't be executed:

``` clojure
;; This is just for display
(def my-function
  "A sample function"
  [x]
  (* x x))
```
