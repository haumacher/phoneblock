package de.haumacher.phoneblock_mobile;

import com.google.i18n.phonenumbers.PhoneNumberUtil;

import java.util.List;
import java.util.Set;

/**
 * The pure block/allow decision of {@link CallChecker}, free of Android APIs so it can be unit
 * tested on the JVM.
 */
final class ScreeningDecision {

    /** The rule that decided the call. */
    enum Reason {
        /** The call has no number, or the caller withheld it (#806). */
        HIDDEN,
        /** The number is whitelisted (personal or global). */
        WHITELIST,
        /** The number is on the user's personal blacklist. */
        BLACKLIST,
        /** The number's country is not in the user's allowed countries (#709). */
        COUNTRY,
        /** The community votes for the number reach the user's threshold. */
        VOTES,
        /** The community votes for the number's range reach the user's threshold. */
        RANGE_VOTES,
        /** A personal wildcard prefix matches the number. */
        WILDCARD,
        /** No rule applies, the call is allowed. */
        NONE
    }

    /** Whether the call is to be blocked. */
    final boolean block;

    /** The rule that decided the call. */
    final Reason reason;

    /**
     * The personal wildcard prefix that caused the block, or {@code null} if the call is not blocked
     * by a wildcard rule.
     */
    final String matchedPrefix;

    private ScreeningDecision(boolean block, Reason reason, String matchedPrefix) {
        this.block = block;
        this.reason = reason;
        this.matchedPrefix = matchedPrefix;
    }

    private static ScreeningDecision block(Reason reason) {
        return new ScreeningDecision(true, reason, null);
    }

    private static ScreeningDecision allow(Reason reason) {
        return new ScreeningDecision(false, reason, null);
    }

    /**
     * Presentation values of {@code android.telecom.TelecomManager}. Copied, so that this class stays
     * free of Android APIs (the constants are part of the public API and never change).
     */
    static final int PRESENTATION_ALLOWED = 1;
    static final int PRESENTATION_RESTRICTED = 2;
    static final int PRESENTATION_UNKNOWN = 3;
    static final int PRESENTATION_PAYPHONE = 4;
    static final int PRESENTATION_UNAVAILABLE = 5;

    /**
     * The first screening rule (#806), applied before any lookup: decides calls whose caller number
     * is withheld or missing.
     *
     * <p>A call counts as hidden, if it has no number (no handle, an empty handle, or one without a
     * single digit such as {@code anonymous}), or if the network marks its number as not to be
     * shown: restricted (withheld by the caller), unknown, unavailable, or payphone. A payphone
     * delivers no callable number either, so it falls under the same setting as any other call
     * that cannot be identified.</p>
     *
     * @param number The scheme specific part of the call's handle, {@code null} if the call has no
     *        handle.
     * @param presentation The handle presentation of the call ({@code PRESENTATION_*}).
     * @param blockHidden Whether the user blocks calls with a withheld number.
     * @return The decision for a hidden call, {@code null} if the call has a number to look up. A
     *         call without a number is allowed with reason {@link Reason#HIDDEN} if the option is
     *         off, because no other rule can apply to it. A withheld call that still carries a
     *         number is only decided here if the option is on, otherwise it is screened by its
     *         number as before.
     */
    static ScreeningDecision decideHidden(String number, int presentation, boolean blockHidden) {
        boolean noNumber = !hasDigit(number);
        if (blockHidden && (noNumber || isWithheld(presentation))) {
            return block(Reason.HIDDEN);
        }
        if (noNumber) {
            return allow(Reason.HIDDEN);
        }
        return null;
    }

    private static boolean isWithheld(int presentation) {
        switch (presentation) {
            case PRESENTATION_RESTRICTED:
            case PRESENTATION_UNKNOWN:
            case PRESENTATION_PAYPHONE:
            case PRESENTATION_UNAVAILABLE:
                return true;
            default:
                return false;
        }
    }

    private static boolean hasDigit(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                return true;
            }
        }
        return false;
    }

    /** Decision without the country filter. */
    static ScreeningDecision decide(String number, int votes, int votesWildcard, boolean archived,
            boolean blackListed, boolean whiteListed, int minVotes, boolean blockRanges, int minRangeVotes,
            List<String> wildcardPrefixes) {
        return decide(number, votes, votesWildcard, archived, blackListed, whiteListed, minVotes, blockRanges,
            minRangeVotes, wildcardPrefixes, null, false);
    }

    /**
     * Decides whether to block a call.
     *
     * <p>An explicit decision for the exact number wins over everything derived: a whitelist entry
     * (personal or global) allows, the user's personal blacklist blocks (the server never reports
     * both). Then the country filter blocks numbers from countries the user does not allow, then
     * the community votes for the number and for its number range, and finally the user's
     * personal wildcard prefixes.</p>
     *
     * @param number The caller's number in international format.
     * @param blackListed Whether the number is on the user's personal blacklist.
     * @param whiteListed Whether the server reports the number as whitelisted (an exact entry in
     *        the user's personal whitelist, or the global whitelist). Older servers do not send the
     *        flag for personal entries; then the remaining rules apply.
     * @param wildcardPrefixes The user's personal wildcard prefixes in international format.
     * @param allowedCallingCodes The calling codes of the countries the user allows, {@code null}
     *        if the country filter is off.
     * @param explicitCountryCode Whether the caller's number arrived with an explicit country code.
     *        A number in national format or a short code has no country of its own and is not
     *        subject to the country filter.
     */
    static ScreeningDecision decide(String number, int votes, int votesWildcard, boolean archived,
            boolean blackListed, boolean whiteListed, int minVotes, boolean blockRanges, int minRangeVotes,
            List<String> wildcardPrefixes, Set<Integer> allowedCallingCodes, boolean explicitCountryCode) {
        if (whiteListed) {
            return allow(Reason.WHITELIST);
        }
        if (blackListed) {
            return block(Reason.BLACKLIST);
        }
        if (allowedCallingCodes != null && explicitCountryCode) {
            int callingCode = callingCode(number);
            if (callingCode > 0 && !allowedCallingCodes.contains(callingCode)) {
                return block(Reason.COUNTRY);
            }
        }
        if (votes >= minVotes && !archived) {
            return block(Reason.VOTES);
        }
        if (blockRanges && votesWildcard >= minRangeVotes) {
            return block(Reason.RANGE_VOTES);
        }
        String matchedPrefix = findWildcardMatch(number, wildcardPrefixes);
        if (matchedPrefix != null) {
            return new ScreeningDecision(true, Reason.WILDCARD, matchedPrefix);
        }
        return allow(Reason.NONE);
    }

    /**
     * The calling code of a number in international format, e.g. {@code 49} for
     * {@code +4930123456}, or {@code 0} if the number has no known calling code.
     *
     * <p>Calling codes are prefix-free (no code is the start of another one), so the first known
     * code among the one- to three-digit prefixes is the number's code.</p>
     */
    static int callingCode(String number) {
        if (number == null || !number.startsWith("+")) {
            return 0;
        }
        Set<Integer> known = PhoneNumberUtil.getInstance().getSupportedCallingCodes();
        for (int length = 1; length <= 3 && length < number.length(); length++) {
            String digits = number.substring(1, 1 + length);
            if (!isDigits(digits)) {
                return 0;
            }
            int code = Integer.parseInt(digits);
            if (known.contains(code)) {
                return code;
            }
        }
        return 0;
    }

    private static boolean isDigits(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /** Finds the first wildcard prefix that matches the given number, or {@code null}. */
    static String findWildcardMatch(String number, List<String> wildcardPrefixes) {
        for (String prefix : wildcardPrefixes) {
            if (number.startsWith(prefix)) {
                return prefix;
            }
        }
        return null;
    }
}
