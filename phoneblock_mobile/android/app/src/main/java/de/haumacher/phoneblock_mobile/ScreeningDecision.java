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
