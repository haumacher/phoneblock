package de.haumacher.phoneblock_mobile;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashSet;

import org.junit.Test;

/**
 * Tests for {@link CountryFilter}.
 */
public class CountryFilterTest {

    @Test
    public void testCallingCodesOfRegions() {
        assertEquals(new HashSet<>(Arrays.asList(1, 49, 43)),
            CountryFilter.callingCodes(Arrays.asList("US", "CA", "DE", "AT", "XX")));
    }
}
