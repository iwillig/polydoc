# Clojure Execution Demo

This document demonstrates the `clojure-exec` filter for Polydoc.

## Simple Arithmetic

Calculate the sum of numbers 1 through 10:

```{.clojure-exec}
(reduce + (range 1 11))
```

## String Manipulation

Convert text to uppercase:

```{.clojure-exec}
(clojure.string/upper-case "hello world")
```

## Data Structures

Create and manipulate a map:

```{.clojure-exec}
(assoc {:name "Alice" :age 30} :role :admin)
```

## Multi-line Code

More complex calculations:

```{.clojure-exec}
(let [numbers [1 2 3 4 5]
      doubled (map #(* 2 %) numbers)
      sum (reduce + doubled)]
  sum)
```

## Code with Output

Print and return:

```{.clojure-exec}
(do
  (println "Processing...")
  (println "Done!")
  42)
```

## Regular Clojure Code (Not Executed)

This block has no `.clojure-exec` class, so it won't be executed:

```clojure
;; This is just for display
(def my-function
  "A sample function"
  [x]
  (* x x))
```
