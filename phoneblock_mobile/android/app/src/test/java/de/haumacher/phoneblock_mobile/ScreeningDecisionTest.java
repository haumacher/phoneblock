package de.haumacher.phoneblock_mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

/**
 * Tests for {@link ScreeningDecision}, the block/allow decision of {@link CallChecker}.
 */
public class ScreeningDecisionTest {

    private static final int MIN_VOTES = 4;
    private static final int MIN_RANGE_VOTES = 10;

    /** Prefixes as {@link CallChecker} reads them from the wildcard_blocks table. */
    private static final List<String> PREFIXES = Arrays.asList("+3312", "+4990012");

    private static ScreeningDecision decideUnknownNumber(String number, List<String> prefixes) {
        return ScreeningDecision.decide(number, 0, 0, false, false, false, MIN_VOTES, true, MIN_RANGE_VOTES, prefixes);
    }

    @Test
    public void testPersonalWildcardBlocksMatchingNumber() {
        ScreeningDecision decision = decideUnknownNumber("+49900123456", PREFIXES);
        assertTrue(decision.block);
        assertEquals("+4990012", decision.matchedPrefix);
    }

    @Test
    public void testPersonalWildcardIgnoresNumberOutsidePrefix() {
        ScreeningDecision decision = decideUnknownNumber("+4990013000", PREFIXES);
        assertFalse(decision.block);
        assertNull(decision.matchedPrefix);
    }

    @Test
    public void testNoPrefixesAllowsUnknownNumber() {
        ScreeningDecision decision = decideUnknownNumber("+49900123456", Collections.emptyList());
        assertFalse(decision.block);
        assertNull(decision.matchedPrefix);
    }

    @Test
    public void testCommunityVotesBlockWithoutWildcardAttribution() {
        ScreeningDecision decision = ScreeningDecision.decide("+49900123456", MIN_VOTES, 0, false, false, false,
            MIN_VOTES, true, MIN_RANGE_VOTES, PREFIXES);
        assertTrue(decision.block);
        assertNull("blocked by votes, not by the wildcard rule", decision.matchedPrefix);
    }

    @Test
    public void testPersonalBlacklistBlocks() {
        ScreeningDecision decision = ScreeningDecision.decide("+4930555000", 0, 0, false, true, false,
            MIN_VOTES, true, MIN_RANGE_VOTES, PREFIXES);
        assertTrue(decision.block);
        assertNull(decision.matchedPrefix);
    }

    @Test
    public void testRangeVotesRespectSetting() {
        assertTrue(ScreeningDecision.decide("+4930555000", 0, MIN_RANGE_VOTES, false, false, false,
            MIN_VOTES, true, MIN_RANGE_VOTES, PREFIXES).block);
        assertFalse(ScreeningDecision.decide("+4930555000", 0, MIN_RANGE_VOTES, false, false, false,
            MIN_VOTES, false, MIN_RANGE_VOTES, PREFIXES).block);
    }

    @Test
    public void testWhitelistWinsOverPersonalWildcard() {
        ScreeningDecision whitelisted = ScreeningDecision.decide("+49900123456", 0, 0, false, false, true,
            MIN_VOTES, true, MIN_RANGE_VOTES, PREFIXES);
        assertFalse(whitelisted.block);
        assertNull(whitelisted.matchedPrefix);

        ScreeningDecision notWhitelisted = ScreeningDecision.decide("+49900123456", 0, 0, false, false, false,
            MIN_VOTES, true, MIN_RANGE_VOTES, PREFIXES);
        assertTrue(notWhitelisted.block);
        assertEquals("+4990012", notWhitelisted.matchedPrefix);
    }

    @Test
    public void testWhitelistWinsOverDerivedBlockRules() {
        assertFalse(ScreeningDecision.decide("+49900123456", MIN_VOTES, MIN_RANGE_VOTES, false, false, true,
            MIN_VOTES, true, MIN_RANGE_VOTES, PREFIXES).block);
    }

    @Test
    public void testWhitelistIsCheckedFirst() {
        // The server never reports both flags; should it, the whitelist wins.
        ScreeningDecision decision = ScreeningDecision.decide("+49900123456", 0, 0, false, true, true,
            MIN_VOTES, true, MIN_RANGE_VOTES, PREFIXES);
        assertFalse(decision.block);
        assertEquals(ScreeningDecision.Reason.WHITELIST, decision.reason);
    }

    private static final Set<Integer> GERMANY = Collections.singleton(49);

    private static ScreeningDecision decideCountry(String number, Set<Integer> allowed) {
        return ScreeningDecision.decide(number, 0, 0, false, false, false, MIN_VOTES, true, MIN_RANGE_VOTES,
            Collections.emptyList(), allowed, true);
    }

    @Test
    public void testCountryFilterBlocksForeignNumber() {
        ScreeningDecision decision = decideCountry("+43123456789", GERMANY);
        assertTrue(decision.block);
        assertEquals(ScreeningDecision.Reason.COUNTRY, decision.reason);
        assertNull(decision.matchedPrefix);
    }

    @Test
    public void testCountryFilterAllowsAllowedCountry() {
        ScreeningDecision decision = decideCountry("+4930123456", GERMANY);
        assertFalse(decision.block);
        assertEquals(ScreeningDecision.Reason.NONE, decision.reason);
    }

    @Test
    public void testWhitelistWinsOverCountryFilter() {
        ScreeningDecision decision = ScreeningDecision.decide("+43123456789", 0, 0, false, false, true,
            MIN_VOTES, true, MIN_RANGE_VOTES, Collections.emptyList(), GERMANY, true);
        assertFalse(decision.block);
        assertEquals(ScreeningDecision.Reason.WHITELIST, decision.reason);
    }

    @Test
    public void testCountryFilterOff() {
        assertFalse(decideCountry("+43123456789", null).block);
        assertFalse(decideUnknownNumber("+43123456789", Collections.emptyList()).block);
    }

    @Test
    public void testCountryFilterWithSharedCallingCode() {
        Set<Integer> allowed = new HashSet<>(Arrays.asList(49, 1));
        assertFalse(decideCountry("+12125550123", allowed).block);
        ScreeningDecision uk = decideCountry("+442071234567", allowed);
        assertTrue(uk.block);
        assertEquals(ScreeningDecision.Reason.COUNTRY, uk.reason);
    }

    @Test
    public void testCountryFilterIgnoresNumbersWithoutCountry() {
        // Arrived in national format or as short code: normalization only assigned the network country.
        ScreeningDecision national = ScreeningDecision.decide("+43112", 0, 0, false, false, false,
            MIN_VOTES, true, MIN_RANGE_VOTES, Collections.emptyList(), GERMANY, false);
        assertFalse(national.block);
        // No known calling code.
        assertFalse(decideCountry("+999123", GERMANY).block);
        assertFalse(decideCountry("12345", GERMANY).block);
    }

    @Test
    public void testCountryFilterPrecedesVotes() {
        ScreeningDecision decision = ScreeningDecision.decide("+43123456789", MIN_VOTES, MIN_RANGE_VOTES, false,
            false, false, MIN_VOTES, true, MIN_RANGE_VOTES, Collections.emptyList(), GERMANY, true);
        assertTrue(decision.block);
        assertEquals(ScreeningDecision.Reason.COUNTRY, decision.reason);

        ScreeningDecision home = ScreeningDecision.decide("+4930123456", MIN_VOTES, 0, false,
            false, false, MIN_VOTES, true, MIN_RANGE_VOTES, Collections.emptyList(), GERMANY, true);
        assertTrue(home.block);
        assertEquals(ScreeningDecision.Reason.VOTES, home.reason);
    }

    @Test
    public void testBlacklistPrecedesCountryFilter() {
        ScreeningDecision decision = ScreeningDecision.decide("+43123456789", 0, 0, false, true, false,
            MIN_VOTES, true, MIN_RANGE_VOTES, Collections.emptyList(), GERMANY, true);
        assertTrue(decision.block);
        assertEquals(ScreeningDecision.Reason.BLACKLIST, decision.reason);
    }

    @Test
    public void testCallingCodeExtraction() {
        assertEquals(1, ScreeningDecision.callingCode("+12125550123"));
        assertEquals(49, ScreeningDecision.callingCode("+4930123456"));
        assertEquals(43, ScreeningDecision.callingCode("+43123456789"));
        assertEquals(353, ScreeningDecision.callingCode("+35312345678"));
        assertEquals(7, ScreeningDecision.callingCode("+74951234567"));
        assertEquals(0, ScreeningDecision.callingCode("+4"));
        assertEquals(0, ScreeningDecision.callingCode("4930123456"));
        assertEquals(0, ScreeningDecision.callingCode(null));
    }

    @Test
    public void testOneDigitPrefixDoesNotMatchTwoDigitCode() {
        // +4 is no calling code: +43 must not be taken for an allowed "4".
        assertTrue(decideCountry("+43123456789", Collections.singleton(4)).block);
        assertTrue(decideCountry("+35312345678", Collections.singleton(35)).block);
    }
}
