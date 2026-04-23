package io.github.sanadlab.runtime;

import org.junit.Test;
import static org.junit.Assert.*;

public class CacheKeyWrapperTest {

    @Test
    public void emptyKeysShouldBeEqual() {
        assertEquals(CacheKeyWrapper.EMPTY, CacheKeyWrapper.EMPTY);
        assertEquals(CacheKeyWrapper.EMPTY, new CacheKeyWrapper(new Object[0]));
    }

    @Test
    public void singlePrimitiveKeyEquality() {
        CacheKeyWrapper a = new CacheKeyWrapper(new Object[]{42});
        CacheKeyWrapper b = new CacheKeyWrapper(new Object[]{42});
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void differentPrimitiveKeysNotEqual() {
        CacheKeyWrapper a = new CacheKeyWrapper(new Object[]{42});
        CacheKeyWrapper b = new CacheKeyWrapper(new Object[]{99});
        assertNotEquals(a, b);
    }

    @Test
    public void compositeKeyEquality() {
        CacheKeyWrapper a = new CacheKeyWrapper(new Object[]{1, "hello", 3.14});
        CacheKeyWrapper b = new CacheKeyWrapper(new Object[]{1, "hello", 3.14});
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void compositeKeyDifference() {
        CacheKeyWrapper a = new CacheKeyWrapper(new Object[]{1, "hello"});
        CacheKeyWrapper b = new CacheKeyWrapper(new Object[]{1, "world"});
        assertNotEquals(a, b);
    }

    @Test
    public void nullArgsHandled() {
        CacheKeyWrapper a = new CacheKeyWrapper(new Object[]{null, "test"});
        CacheKeyWrapper b = new CacheKeyWrapper(new Object[]{null, "test"});
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void nullVsNonNull() {
        CacheKeyWrapper a = new CacheKeyWrapper(new Object[]{null});
        CacheKeyWrapper b = new CacheKeyWrapper(new Object[]{"test"});
        assertNotEquals(a, b);
    }

    @Test
    public void arrayArgs() {
        CacheKeyWrapper a = new CacheKeyWrapper(new Object[]{new int[]{1, 2, 3}});
        CacheKeyWrapper b = new CacheKeyWrapper(new Object[]{new int[]{1, 2, 3}});
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void booleanKey() {
        CacheKeyWrapper a = new CacheKeyWrapper(new Object[]{true});
        CacheKeyWrapper b = new CacheKeyWrapper(new Object[]{true});
        CacheKeyWrapper c = new CacheKeyWrapper(new Object[]{false});
        assertEquals(a, b);
        assertNotEquals(a, c);
    }
}
