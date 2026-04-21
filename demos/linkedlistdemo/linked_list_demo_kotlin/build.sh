#!/bin/bash

echo "Building LinkedList Kotlin Application..."
echo "========================================"

# Clean any previous builds
echo "Cleaning previous builds..."
mvn clean

# Compile the project
echo "Compiling Kotlin project..."
mvn compile

# Package into JAR files
echo "Packaging into JAR files..."
mvn package

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Build successful!"
    echo ""
    echo "Generated JAR files:"
    echo "  📦 target/linkedlist-app-kotlin-1.0.0.jar (standard JAR)"
    echo "  🚀 target/linkedlist-app-kotlin-1.0.0-executable.jar (executable JAR)"
    echo ""
    echo "To run the application:"
    echo "  java -jar target/linkedlist-app-kotlin-1.0.0-executable.jar"
    echo "  or use: ./run.sh"
else
    echo "❌ Build failed!"
    exit 1
fi
