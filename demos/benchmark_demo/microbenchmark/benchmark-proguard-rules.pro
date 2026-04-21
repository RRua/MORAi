# Keep benchmark test classes so R8 / androidx.benchmark can see them.
-keep class com.memoize.bench.** { *; }
-keep class dev.memoize.runtime.** { *; }
-keepclassmembers class * {
    private dev.memoize.runtime.MemoCacheManager __memoCacheManager;
    private dev.memoize.runtime.MemoDispatcher __memoDispatcher_*;
}
