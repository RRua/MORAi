# === START: Copy and paste the entire block ===

# --- Step 0: Clean Previous Builds ---
./gradlew clean

# --- Step 1: Setup Environment Variables ---
# EDIT THIS to match your project's compileSdk version
COMPILE_SDK_VERSION=35

# This finds your Android SDK, assuming a standard location
ANDROID_HOME=${ANDROID_HOME:-~/Library/Android/sdk} # macOS
# ANDROID_HOME=${ANDROID_HOME:-~/Android/Sdk} # Linux
if [ ! -d "$ANDROID_HOME" ]; then echo "Error: ANDROID_HOME not found."; exit 1; fi
ANDROID_JAR="$ANDROID_HOME/platforms/android-$COMPILE_SDK_VERSION/android.jar"
if [ ! -f "$ANDROID_JAR" ]; then echo "Error: android.jar not found for SDK $COMPILE_SDK_VERSION"; exit 1; fi

# --- Step 2: Run Gradle to Generate R.class ---
echo "▶️ Running Gradle to generate necessary files (like R.class)..."
./gradlew -q :app:assembleDebug || { echo "Gradle task failed"; exit 1; }
# Find the generated R.jar file
R_JAR=$(find app/build -name "R.jar" | head -n 1)
if [ -z "$R_JAR" ]; then echo "Error: R.jar not found after Gradle run."; exit 1; fi

# --- Step 3: Rebuild the Classpath from Gradle's Output ---
echo "▶️ Processing classpath: extracting code from .aar files..."
# Get the raw classpath containing .aar and .jar files
RAW_CP=$(./gradlew -q --init-script print-classpath.init.gradle.kts printClasspath)
# Create a temporary directory for extracted libraries
TEMP_DIR=$(mktemp -d)
# This will be our new, kotlinc-friendly classpath
PROCESSED_CP=""

# Loop through each library path provided by Gradle
IFS=':' read -ra PATHS <<< "$RAW_CP"
for path in "${PATHS[@]}"; do
  if [[ "$path" == *.aar ]]; then
    # If it's an .aar file, unzip its classes.jar into a unique subdirectory
    BASENAME=$(basename "$path" .aar)
    unzip -qo "$path" classes.jar -d "$TEMP_DIR/$BASENAME"
    if [ -f "$TEMP_DIR/$BASENAME/classes.jar" ]; then
      PROCESSED_CP="$PROCESSED_CP:$TEMP_DIR/$BASENAME/classes.jar"
    fi
  elif [[ "$path" == *.jar ]]; then
    # If it's a .jar file, add it directly
    PROCESSED_CP="$PROCESSED_CP:$path"
  fi
done

# --- Step 4: Execute the Final kotlinc Command ---
echo "▶️ Compiling with the new, complete classpath..."
infer run --impurity -- kotlinc -cp "$ANDROID_JAR:$R_JAR$PROCESSED_CP" \
  app/src/main/java/com/linkedlist/app/*.kt \
  # app/src/main/java/com/linkedlist/app/*.java \
  -d build/classes/kotlin/assembleDebug

# --- Step 5: Cleanup ---
rm -rf "$TEMP_DIR"
echo "✅ Compilation finished successfully!"

# === END: Command block ===