# Python Execution Filter Demo

This document demonstrates the python-exec filter that executes Python code blocks.

## Basic Python Execution

```{.python-exec}
1 + 2 + 3
```

## Variables and Operations

```{.python-exec}
x = 10
y = 20
x + y
```

## List Operations

```{.python-exec}
numbers = [1, 2, 3, 4, 5]
sum(numbers)
```

## String Operations

```{.python-exec}
message = 'hello world'
message.upper()
```

## Functions

```{.python-exec}
def factorial(n):
    if n <= 1:
        return 1
    return n * factorial(n - 1)

factorial(5)
```

## Print Output

```{.python-exec}
print('Computing squares:')
for i in range(1, 6):
    print(f'{i}^2 = {i**2}')
100 + 200
```

## List Comprehensions

```{.py-exec}
squares = [x**2 for x in range(1, 11)]
squares
```
