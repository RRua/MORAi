package dev.memoize.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a cache invalidator. When this method is called,
 * the specified memoization caches are cleared.
 *
 * <p>If no method names are specified (default), ALL memoization caches
 * on the instance are cleared. If specific method names are provided,
 * only those methods' caches are invalidated.
 *
 * <p>Examples:
 * <pre>
 * // Invalidate ALL caches on the instance
 * &#64;CacheInvalidate
 * public void clearAll() { ... }
 *
 * // Invalidate only the "search" cache
 * &#64;CacheInvalidate("search")
 * public void updateSearchIndex() { ... }
 *
 * // Invalidate "search" and "length" caches, but not "describe"
 * &#64;CacheInvalidate({"search", "length"})
 * public void insert(int data) { ... }
 * </pre>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface CacheInvalidate {

    /**
     * Names of the @Memoize-annotated methods whose caches should be invalidated.
     * An empty array (default) means invalidate ALL caches on the instance.
     */
    String[] value() default {};
}
