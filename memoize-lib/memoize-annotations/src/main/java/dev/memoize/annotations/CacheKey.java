package dev.memoize.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies which field of a parameter to use for cache key construction.
 * When placed on a method parameter, only the specified field path is used
 * as part of the cache key instead of the entire parameter object.
 *
 * <p>Example:
 * <pre>
 * &#64;Memoize
 * public Route findRoute(&#64;CacheKey("id") Location start, &#64;CacheKey("id") Location end) {
 *     // Only start.id and end.id are used as cache keys
 * }
 * </pre>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PARAMETER)
public @interface CacheKey {

    /**
     * The field path to extract from the parameter for key construction.
     * Empty string means use the entire parameter (default behavior).
     */
    String value() default "";
}
