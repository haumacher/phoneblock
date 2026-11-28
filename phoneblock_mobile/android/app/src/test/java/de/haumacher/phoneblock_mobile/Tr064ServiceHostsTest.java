package de.haumacher.phoneblock_mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/**
 * Tests for {@link Tr064ServiceHosts} (issue #660).
 */
public class Tr064ServiceHostsTest {

    private static Map<String, byte[]> txt(String... keyValues) {
        Map<String, byte[]> result = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put(keyValues[i], keyValues[i + 1].getBytes(StandardCharsets.UTF_8));
        }
        return result;
    }

    /** The announcement of a Fritz!Box as seen via mDNS. */
    @Test
    public void testFritzBoxAnnouncement() {
        Map<String, byte[]> attributes = txt(
            "ipv6", "fe80::1",
            "ipv4", "192.168.178.1",
            "path", "http://fritz.box:49000/tr64desc.xml");

        assertEquals(Arrays.asList("192.168.178.1", "fritz.box"),
            Tr064ServiceHosts.candidateHosts(attributes, Arrays.asList("192.168.178.1", "fe80::1%wlan0")));
    }

    @Test
    public void testHintFirstThenResolvedAddresses() {
        assertEquals(Arrays.asList("10.0.0.4", "10.0.0.5", "[fd00::4]"),
            Tr064ServiceHosts.candidateHosts(txt("ipv4", "10.0.0.4"),
                Arrays.asList("fd00::4", "10.0.0.5", "10.0.0.4")));
    }

    @Test
    public void testInvalidHintIgnored() {
        assertEquals(Collections.singletonList("10.0.0.4"),
            Tr064ServiceHosts.candidateHosts(txt("ipv4", "999.1.1.1"), Collections.singletonList("10.0.0.4")));
        assertEquals(Collections.singletonList("10.0.0.4"),
            Tr064ServiceHosts.candidateHosts(txt("ipv4", " "), Collections.singletonList("10.0.0.4")));
    }

    @Test
    public void testNoAttributesNoAddresses() {
        assertTrue(Tr064ServiceHosts.candidateHosts(null, null).isEmpty());
        assertTrue(Tr064ServiceHosts.candidateHosts(txt(), Collections.singletonList("fe80::1")).isEmpty());
    }

    @Test
    public void testPathOnly() {
        assertEquals(Collections.singletonList("fritz.repeater"),
            Tr064ServiceHosts.candidateHosts(txt("path", "http://fritz.repeater:49000/tr64desc.xml"), null));
    }

    @Test
    public void testHostOfUrl() {
        assertEquals("fritz.box", Tr064ServiceHosts.hostOfUrl("http://fritz.box:49000/tr64desc.xml"));
        assertNull(Tr064ServiceHosts.hostOfUrl("not a url"));
        assertNull(Tr064ServiceHosts.hostOfUrl("/tr64desc.xml"));
        assertNull(Tr064ServiceHosts.hostOfUrl(null));
    }

    @Test
    public void testIsIPv4() {
        assertTrue(Tr064ServiceHosts.isIPv4("192.168.178.1"));
        assertTrue(!Tr064ServiceHosts.isIPv4("192.168.178"));
        assertTrue(!Tr064ServiceHosts.isIPv4("256.1.1.1"));
        assertTrue(!Tr064ServiceHosts.isIPv4("fd00::1"));
    }
}
