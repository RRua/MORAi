# LinkedList Application

A Java application demonstrating LinkedList implementation with proper package structure and JAR generation.

## Project Structure

```
src/main/java/com/linkedlist/app/
├── Node.java                    # Node class for list elements
├── LinkedListIterator.java      # Iterator implementation
├── LinkedList.java              # Main LinkedList implementation
└── LinkedListDemo.java          # Application entry point
```

## Features

- **LinkedList Operations**: Insert, delete, search, traverse
- **Iterator Support**: Full Iterator interface implementation
- **Proper Packaging**: Maven-based project with JAR generation
- **Executable Application**: Ready-to-run JAR files

## Quick Start

### Prerequisites
- Java 11 or higher
- Maven 3.6 or higher

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
java -jar target/linkedlist-app-1.0.0-executable.jar
```

## Generated Artifacts

The build process creates two JAR files:

- `target/linkedlist-app-1.0.0.jar` - Standard JAR (requires classpath)
- `target/linkedlist-app-1.0.0-executable.jar` - Executable JAR (self-contained)

## LinkedList Operations

The application demonstrates:
- Inserting elements at head and tail
- Deleting elements by value  
- Searching for elements
- Getting list length
- Printing list contents
- Array traversal (two methods)
- Iterator-based operations
