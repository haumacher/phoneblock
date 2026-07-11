/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates the active ({@code SHADOW}/{@code LIVE}) rules against the current
 * aggregates and drives classification + notification:
 *
 * <ul>
 *   <li>a {@code LIVE} rule <b>classifies</b> its matching signatures (sets
 *       {@code CATEGORY}), removing them from the "unmatched" feed;</li>
 *   <li>for each origin over the rule's persistence threshold that is still
 *       <b>recently active</b>, a one-shot notification is created per
 *       {@code (rule, origin)} — {@code SHADOW} → a dry-run projection,
 *       {@code LIVE}+{@code USER} → a help mail (via {@link Notifier}),
 *       {@code LIVE}+{@code DEV} → a dev alert;</li>
 *   <li>a latched match whose origin has gone <b>quiet</b> (no events within the
 *       rearm window) is cleared, so a genuine recurrence re-notifies.</li>
 * </ul>
 *
 * <p>Rules are read fresh each run, so edits take effect without a redeploy. The
 * caller owns the session/transaction.</p>
 */
public class DiagnosticsMatcher {

	private static final Logger LOG = LoggerFactory.getLogger(DiagnosticsMatcher.class);

	private final long _quietMs;

	/**
	 * @param quietDays after this many days without a new event, a latched match
	 *        is cleared (rearmed).
	 */
	public DiagnosticsMatcher(int quietDays) {
		_quietMs = quietDays * 86_400_000L;
	}

	/** Outcome of one matcher pass. */
	public record MatchStats(int rules, int matched, int notified, int cleared, int classified) {}

	public MatchStats run(DiagnosticsMapper mapper, Notifier notifier, long now) {
		List<DiagRule> rules = mapper.listActiveRules();
		int matched = 0;
		int notified = 0;
		int cleared = 0;
		int classified = 0;

		for (DiagRule rule : rules) {
			if (rule.pattern() == null) {
				LOG.warn("Diagnostics rule {} ('{}') has an invalid regex — skipped.", rule.getId(), rule.getName());
				continue;
			}
			boolean live = DiagRule.LIVE.equals(rule.getState());

			for (SignatureRow sig : mapper.listSignatures(rule.getSource(), false)) {
				if (rule.getMatchTag() != null && !rule.getMatchTag().equals(sig.getTag())) {
					continue;
				}
				if (!rule.matches(sig.getSignature())) {
					continue;
				}
				matched++;

				if (live && !rule.getCategory().isEmpty() && !rule.getCategory().equals(sig.getCategory())) {
					mapper.setSignatureCategory(sig.getSigId(), rule.getCategory());
					classified++;
				}

				for (OriginRow origin : mapper.originsOverThreshold(
						sig.getSigId(), rule.getMinEvents(), rule.getMinDistinctDays())) {
					boolean recent = origin.getLastSeen() >= now - _quietMs;
					boolean active = mapper.countActiveNotifications(rule.getId(), origin.getOriginId()) > 0;

					if (recent && !active && !DiagRule.ACTOR_NONE.equals(rule.getActor())) {
						if (handleMatch(mapper, notifier, rule, sig.getSource(), origin, now)) {
							notified++;
						}
					} else if (!recent && active) {
						cleared += mapper.clearNotifications(rule.getId(), origin.getOriginId(), now);
					}
				}
			}
		}

		MatchStats stats = new MatchStats(rules.size(), matched, notified, cleared, classified);
		if (notified > 0 || cleared > 0 || classified > 0) {
			LOG.info("Diagnostics matcher: {} rules, {} sig-matches, {} notified, {} cleared, {} classified.",
				stats.rules(), stats.matched(), stats.notified(), stats.cleared(), stats.classified());
		}
		return stats;
	}

	private boolean handleMatch(DiagnosticsMapper mapper, Notifier notifier, DiagRule rule,
			String source, OriginRow origin, long now) {
		if (!DiagRule.LIVE.equals(rule.getState())) {
			// SHADOW: record what LIVE would have done, send nothing.
			mapper.insertNotification(source, origin.getOriginId(), origin.getUserId(), rule.getId(),
				"PENDING", true, now, null);
			return true;
		}

		if (DiagRule.ACTOR_USER.equals(rule.getActor())) {
			boolean sent = notifier.notifyUser(rule, source, origin.getOriginId(), origin.getUserId());
			if (sent) {
				mapper.insertNotification(source, origin.getOriginId(), origin.getUserId(), rule.getId(),
					"SENT", false, now, now);
				return true;
			}
			// Suppressed (kill switch / cap / no address) — leave unlatched to retry.
			return false;
		}

		if (DiagRule.ACTOR_DEV.equals(rule.getActor())) {
			notifier.notifyDev(rule, source, origin.getOriginId(), origin.getUserId());
			mapper.insertNotification(source, origin.getOriginId(), origin.getUserId(), rule.getId(),
				"SENT", false, now, now);
			return true;
		}

		return false;
	}
}
