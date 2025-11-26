# JavaScript Execution Filter Demo

This document demonstrates the `javascript-exec` filter which executes JavaScript code blocks using GraalVM Polyglot.

## Basic JavaScript Execution

Simple arithmetic:

```{.javascript-exec}
1 + 2 + 3
```

## Console Output

Using console.log:

```{.javascript-exec}
console.log('Hello from JavaScript!');
console.log('The answer is:', 42);
42
```

## ES6 Features

### Const and Let

```{.javascript-exec}
const x = 10;
let y = 20;
console.log('x + y =', x + y);
x + y
```

### Arrow Functions

```{.javascript-exec}
const add = (a, b) => a + b;
const multiply = (a, b) => a * b;

console.log('add(5, 3) =', add(5, 3));
console.log('multiply(4, 6) =', multiply(4, 6));

add(10, 20)
```

### Array Methods

```{.javascript-exec}
const numbers = [1, 2, 3, 4, 5];

const sum = numbers.reduce((a, b) => a + b, 0);
const doubled = numbers.map(x => x * 2);
const evens = numbers.filter(x => x % 2 === 0);

console.log('Numbers:', numbers);
console.log('Sum:', sum);
console.log('Doubled:', doubled);
console.log('Evens:', evens);

sum
```

## Objects and JSON

```{.javascript-exec}
const person = {
  name: 'Alice',
  age: 30,
  city: 'San Francisco'
};

console.log('Person:', JSON.stringify(person, null, 2));
person.name
```

## String Manipulation

```{.javascript-exec}
const text = 'Hello World';
const upper = text.toUpperCase();
const lower = text.toLowerCase();
const reversed = text.split('').reverse().join('');

console.log('Original:', text);
console.log('Upper:', upper);
console.log('Lower:', lower);
console.log('Reversed:', reversed);

reversed
```

## Fibonacci Sequence

```{.javascript-exec}
function fibonacci(n) {
  if (n <= 1) return n;
  return fibonacci(n - 1) + fibonacci(n - 2);
}

const fibs = [];
for (let i = 0; i < 10; i++) {
  fibs.push(fibonacci(i));
}

console.log('First 10 Fibonacci numbers:', fibs);
fibs
```

## Using js-exec Alias

The filter also works with the `js-exec` class:

```{.js-exec}
const greeting = 'Hello';
const name = 'World';
console.log(greeting + ', ' + name + '!');
greeting + ', ' + name
```

## Error Handling

The filter gracefully handles errors:

```{.javascript-exec}
// This will produce an error
throw new Error('This is a test error');
```

## Complex Example: Data Processing

```{.javascript-exec}
// Sample data
const data = [
  { name: 'Alice', age: 30, salary: 80000 },
  { name: 'Bob', age: 25, salary: 60000 },
  { name: 'Charlie', age: 35, salary: 95000 },
  { name: 'Diana', age: 28, salary: 70000 }
];

// Calculate average salary
const avgSalary = data.reduce((sum, p) => sum + p.salary, 0) / data.length;

// Find highest earner
const highestEarner = data.reduce((max, p) => 
  p.salary > max.salary ? p : max
);

// Filter people over 30
const over30 = data.filter(p => p.age > 30);

console.log('Average salary:', avgSalary);
console.log('Highest earner:', highestEarner.name, '-', highestEarner.salary);
console.log('People over 30:', over30.map(p => p.name));

avgSalary
```

## Usage

To process this document:

```bash
# Using Pandoc pipeline
pandoc javascript-exec-demo.md -t json | \\
  clojure -M:main filter -t javascript-exec | \\
  pandoc -f json -o output.html

# Or directly
clojure -M:main filter -t javascript-exec \\
  -i javascript-exec-demo.json \\
  -o output.json
```

## Supported Features

- ✅ ES6+ syntax (const, let, arrow functions)
- ✅ Array methods (map, filter, reduce)
- ✅ Object literals and JSON
- ✅ Console.log output capture
- ✅ Error handling
- ✅ Both `javascript-exec` and `js-exec` classes
- ✅ Sandboxed execution (no host access)

## Not Supported

- ❌ Node.js modules (fs, http, etc.)
- ❌ npm packages
- ❌ Asynchronous operations (Promises, async/await)
- ❌ DOM manipulation
- ❌ Browser APIs
