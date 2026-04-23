package io.github.sanadlab.runtime;

import java.util.Arrays;

/**
 * Immutable wrapper around an Object[] that provides correct hashCode and equals
 * semantics for use as a cache key. Uses Arrays.deepHashCode/deepEquals to handle
 * primitives, nested arrays, and null values uniformly.
 */
public final class CacheKeyWrapper {

    /** Singleton key for zero-argument methods. */
    public static final CacheKeyWrapper EMPTY = new CacheKeyWrapper(new Object[0]);

    private final Object[] args;
    private final int hashCode;

    public CacheKeyWrapper(Object[] args) {
        this.args = args;
        this.hashCode = Arrays.deepHashCode(args);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CacheKeyWrapper)) return false;
        CacheKeyWrapper other = (CacheKeyWrapper) obj;
        return Arrays.deepEquals(this.args, other.args);
    }

    @Override
    public String toString() {
        return "CacheKey" + Arrays.deepToString(args);
    }
}
