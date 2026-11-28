package de.haumacher.phoneblock_mobile;

import java.util.List;

/**
 * The pure block/allow decision of {@link CallChecker}, free of Android APIs so it can be unit
 * tested on the JVM.
 */
final class ScreeningDecision {

    /** Whether the call is to be blocked. */
    final boolean block;

    /**
     * The personal wildcard prefix that caused the block, or {@code null} if the call is not blocked
     * by a wildcard rule.
     */
    final String matchedPrefix;

    private ScreeningDecision(boolean block, String matchedPrefix) {
        this.block = block;
        this.matchedPrefix = matchedPrefix;
    }

    /**
     * Decides whether to block a call.
     *
     * <p>An explicit decision for the exact number wins over everything derived: the user's
     * personal blacklist blocks, then a whitelist entry (personal or global) allows. After that,
     * the community votes for the number and for its number range, and finally the user's
     * personal wildcard prefixes.</p>
     *
     * @param number The caller's number in international format.
     * @param blackListed Whether the number is on the user's personal blacklist.
     * @param whiteListed Whether the server reports the number as whitelisted (an exact entry in
     *        the user's personal whitelist, or the global whitelist). Older servers do not send the
     *        flag for personal entries; then the remaining rules apply.
     * @param wildcardPrefixes The user's personal wildcard prefixes in international format.
     */
    static ScreeningDecision decide(String number, int votes, int votesWildcard, boolean archived,
            boolean blackListed, boolean whiteListed, int minVotes, boolean blockRanges, int minRangeVotes,
            List<String> wildcardPrefixes) {
        if (blackListed) {
            return new ScreeningDecision(true, null);
        }
        if (whiteListed) {
            return new ScreeningDecision(false, null);
        }
        if (votes >= minVotes && !archived) {
            return new ScreeningDecision(true, null);
        }
        if (blockRanges && votesWildcard >= minRangeVotes) {
            return new ScreeningDecision(true, null);
        }
        String matchedPrefix = findWildcardMatch(number, wildcardPrefixes);
        return new ScreeningDecision(matchedPrefix != null, matchedPrefix);
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
