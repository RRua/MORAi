# LinkedList Application (Kotlin)

A Kotlin application demonstrating LinkedList implementation with proper Maven package structure and JAR generation.

## Project Structure

```
src/main/kotlin/com/linkedlist/app/
├── Node.kt                    # Node class for list elements
├── LinkedListIterator.kt      # Iterator implementation
├── LinkedList.kt              # Main LinkedList implementation
└── LinkedListDemo.kt          # Application entry point
```

## Features

- **LinkedList Operations**: Insert, delete, search, traverse
- **Iterator Support**: Full Iterator interface implementation
- **Kotlin Language Features**: Nullable types, string interpolation, concise syntax
- **Maven Build System**: Standard Kotlin Maven project structure
- **External Dependency**: Apache Commons Lang for demo purposes
- **Executable JARs**: Ready-to-run JAR files

## Quick Start

### Prerequisites
- Java 11 or higher
- Maven 3.6 or higher
- Kotlin 1.9.10 (managed by Maven)

### Build and Run

1. **Build the application:**
   ```bash
   ./build.sh
   ```

2. **Run the application:**
   ```bash
   ./run.sh
   ```

### Manual Commands

**Build:**
```bash
mvn clean compile package
```

**Run:**
```bash
java -jar target/linkedlist-app-kotlin-1.0.0-executable.jar
```

## Generated Artifacts

The build process creates two JAR files:

- `target/linkedlist-app-kotlin-1.0.0.jar` - Standard JAR (requires classpath)
- `target/linkedlist-app-kotlin-1.0.0-executable.jar` - Executable JAR (self-contained)

## Kotlin vs Java Comparison

This project demonstrates the same LinkedList implementation as the Java version but with Kotlin features:

- **Nullable types** (`Node?`) for safer null handling
- **String interpolation** (`"Length: ${list.length()}"`)
- **Concise syntax** and reduced boilerplate
- **Object declarations** for singleton pattern
- **Property syntax** instead of getters/setters

## LinkedList Operations

The application demonstrates:
- Inserting elements at head and tail
- Deleting elements by value  
- Searching for elements
- Getting list length
- Printing list contents
- Array traversal (two methods)
- Iterator-based operations
- External dependency usage (Apache Commons Lang)
