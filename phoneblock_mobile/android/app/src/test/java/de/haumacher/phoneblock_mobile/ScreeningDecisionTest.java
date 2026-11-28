package de.haumacher.phoneblock_mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
    public void testPersonalBlacklistWinsOverWhitelist() {
        // A personally blocked number that is also on the global whitelist.
        ScreeningDecision decision = ScreeningDecision.decide("+49900123456", 0, 0, false, true, true,
            MIN_VOTES, true, MIN_RANGE_VOTES, PREFIXES);
        assertTrue(decision.block);
        assertNull(decision.matchedPrefix);
    }
}
