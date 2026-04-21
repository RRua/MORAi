#!/bin/bash
# kotlinc-build.sh

echo "Building Kotlin project with kotlinc..."

# Download dependencies using Maven (they go to target/dependency by default)
echo "Downloading dependencies..."
mvn dependency:copy-dependencies

# Create target/classes directory if it doesn't exist
mkdir -p target/classes

# Compile with kotlinc (so Infer can intercept)
echo "Compiling Kotlin files..."
kotlinc -cp "target/dependency/commons-lang3-3.12.0.jar:target/dependency/kotlin-stdlib-1.9.10.jar" src/main/kotlin/com/linkedlist/app/*.kt -d target/classes

# Create JAR manually
echo "Creating JAR..."
jar cfe target/linkedlist-kotlin.jar com.linkedlist.app.LinkedListDemo -C target/classes .

echo "✅ Build completed! JAR created: target/linkedlist-kotlin.jar"