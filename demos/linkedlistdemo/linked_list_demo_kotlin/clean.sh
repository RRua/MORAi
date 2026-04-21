#!/bin/bash

echo "Cleaning Kotlin LinkedList project..."

# Clean Maven build artifacts
if [ -d "target" ]; then
    echo "Removing target/ directory..."
    rm -rf target/
fi

# Clean any Infer output (if present)
if [ -d "infer-out" ]; then
    echo "Removing infer-out/ directory..."
    rm -rf infer-out/
fi

echo "✅ Kotlin project cleaned successfully!"
echo "   - Removed target/ (Maven build directory)"
echo "   - Removed infer-out/ (if present)"
echo "   - Removed any stray .class files"
