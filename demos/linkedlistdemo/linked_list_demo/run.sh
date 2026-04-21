#!/bin/bash

echo "Running LinkedList Application..."
echo "================================"

# Check if executable JAR exists
EXECUTABLE_JAR="target/linkedlist-app-1.0.0-executable.jar"

if [ ! -f "$EXECUTABLE_JAR" ]; then
    echo "❌ Executable JAR not found: $EXECUTABLE_JAR"
    echo "Please run ./build.sh first to build the application."
    exit 1
fi

echo "🚀 Starting application..."
echo ""

# Run the application
java -jar "$EXECUTABLE_JAR"

echo ""
echo "✅ Application finished."
