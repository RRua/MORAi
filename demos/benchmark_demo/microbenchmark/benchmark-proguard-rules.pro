# Keep benchmark test classes so R8 / androidx.benchmark can see them.
-keep class com.memoize.bench.** { *; }
-keep class io.github.sanadlab.runtime.** { *; }
-keepclassmembers class * {
    private io.github.sanadlab.runtime.MemoCacheManager __memoCacheManager;
    private io.github.sanadlab.runtime.MemoDispatcher __memoDispatcher_*;
}
