package de.haumacher.phoneblock_mobile.log;

import org.junit.Test;
import static org.junit.Assert.*;

public class LogSanitizerTest {
    @Test
    public void hashPhone_returnsSha1PrefixPlusEightHex() {
        String hashed = LogSanitizer.hashPhone("+4930123456");
        assertTrue(hashed.startsWith("sha1:"));
        assertEquals(5 + 8, hashed.length());
        assertTrue(hashed.substring(5).matches("[0-9a-f]{8}"));
    }

    @Test
    public void hashPhone_isDeterministic() {
        assertEquals(
            LogSanitizer.hashPhone("+4930123456"),
            LogSanitizer.hashPhone("+4930123456"));
    }

    @Test
    public void hashPhone_nullReturnsPlaceholder() {
        assertEquals("sha1:-", LogSanitizer.hashPhone(null));
    }
}
