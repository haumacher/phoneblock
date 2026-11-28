package de.haumacher.phoneblock_mobile;

import static de.haumacher.phoneblock_mobile.ScreeningDecision.PRESENTATION_ALLOWED;
import static de.haumacher.phoneblock_mobile.ScreeningDecision.PRESENTATION_PAYPHONE;
import static de.haumacher.phoneblock_mobile.ScreeningDecision.PRESENTATION_RESTRICTED;
import static de.haumacher.phoneblock_mobile.ScreeningDecision.PRESENTATION_UNAVAILABLE;
import static de.haumacher.phoneblock_mobile.ScreeningDecision.PRESENTATION_UNKNOWN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.telecom.TelecomManager;

import org.junit.Test;

/**
 * Tests for {@link ScreeningDecision#decideHidden(String, int, boolean)}, the first screening rule:
 * calls with a withheld or missing number (#806).
 *
 * <p>A non-{@code null} decision means that {@link CallChecker} answers the call right away, without
 * querying PhoneBlock or the local data. All numbers are synthetic.</p>
 */
public class HiddenNumberTest {

    private static final String NUMBER = "+4930555000";

    @Test
    public void testPresentationConstantsMatchTelecom() {
        assertEquals(TelecomManager.PRESENTATION_ALLOWED, PRESENTATION_ALLOWED);
        assertEquals(TelecomManager.PRESENTATION_RESTRICTED, PRESENTATION_RESTRICTED);
        assertEquals(TelecomManager.PRESENTATION_UNKNOWN, PRESENTATION_UNKNOWN);
        assertEquals(TelecomManager.PRESENTATION_PAYPHONE, PRESENTATION_PAYPHONE);
        assertEquals(TelecomManager.PRESENTATION_UNAVAILABLE, PRESENTATION_UNAVAILABLE);
    }

    @Test
    public void testNoHandleDoesNotFail() {
        // The 1.3.1 crash: a call without handle. Must be decided without any lookup.
        assertHidden(ScreeningDecision.decideHidden(null, PRESENTATION_ALLOWED, false), false);
        assertHidden(ScreeningDecision.decideHidden(null, PRESENTATION_RESTRICTED, false), false);
        assertHidden(ScreeningDecision.decideHidden(null, PRESENTATION_RESTRICTED, true), true);
        assertHidden(ScreeningDecision.decideHidden(null, 0, true), true);
    }

    @Test
    public void testEmptyNumberIsHidden() {
        assertHidden(ScreeningDecision.decideHidden("", PRESENTATION_ALLOWED, true), true);
        assertHidden(ScreeningDecision.decideHidden("", PRESENTATION_ALLOWED, false), false);
    }

    @Test
    public void testNumberWithoutDigitsIsHidden() {
        assertHidden(ScreeningDecision.decideHidden("anonymous@example.invalid", PRESENTATION_ALLOWED, true), true);
        assertHidden(ScreeningDecision.decideHidden("+", PRESENTATION_ALLOWED, false), false);
    }

    @Test
    public void testWithheldPresentationsAreHidden() {
        for (int presentation : new int[] {PRESENTATION_RESTRICTED, PRESENTATION_UNKNOWN,
                PRESENTATION_PAYPHONE, PRESENTATION_UNAVAILABLE}) {
            assertHidden(ScreeningDecision.decideHidden("", presentation, true), true);
            assertHidden(ScreeningDecision.decideHidden("", presentation, false), false);
            // Even if the network passes a number along, a withheld call is blocked when the
            // option is on.
            assertHidden(ScreeningDecision.decideHidden(NUMBER, presentation, true), true);
        }
    }

    @Test
    public void testWithheldCallWithNumberIsScreenedAsBeforeWhenOff() {
        assertNull(ScreeningDecision.decideHidden(NUMBER, PRESENTATION_RESTRICTED, false));
        assertNull(ScreeningDecision.decideHidden(NUMBER, PRESENTATION_UNKNOWN, false));
    }

    @Test
    public void testNormalNumberIsUnaffected() {
        assertNull(ScreeningDecision.decideHidden(NUMBER, PRESENTATION_ALLOWED, true));
        assertNull(ScreeningDecision.decideHidden(NUMBER, PRESENTATION_ALLOWED, false));
        assertNull(ScreeningDecision.decideHidden("030555000", PRESENTATION_ALLOWED, true));
        assertNull(ScreeningDecision.decideHidden("110", PRESENTATION_ALLOWED, true));
    }

    private static void assertHidden(ScreeningDecision decision, boolean blocked) {
        assertNotNull("decided without lookup", decision);
        assertEquals(ScreeningDecision.Reason.HIDDEN, decision.reason);
        assertNull(decision.matchedPrefix);
        if (blocked) {
            assertTrue(decision.block);
        } else {
            assertFalse(decision.block);
        }
    }
}
