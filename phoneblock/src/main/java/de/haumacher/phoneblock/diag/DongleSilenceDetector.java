/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.db.Users;

/**
 * Detects dongles that have gone <em>silent</em> — no token use (daily self-test)
 * for several days, so the device is likely decommissioned or the user needs a
 * nudge. An absence signal, invisible to the log-signature matcher, so it runs
 * its own query over {@code TOKENS}; but it reuses the notification ledger
 * (latch, audit), the mail templates and the caps/kill-switch via
 * {@link MailNotifier}.
 *
 * <p>Routing is governed by the seeded carrier rule
 * {@code "Dongle silent (no contact)"} (SHADOW → dry-run projection, LIVE → mail),
 * so it promotes through the same REST flow as every other rule.</p>
 */
public class DongleSilenceDetector {

	private static final Logger LOG = LoggerFactory.getLogger(DongleSilenceDetector.class);

	/** The seeded carrier rule that holds this detector's state/actor/template. */
	public static final String RULE_NAME = "Dongle silent (no contact)";

	private static final long DAY_MS = 86_400_000L;

	private final int _silenceDays;

	public DongleSilenceDetector(int silenceDays) {
		_silenceDays = silenceDays;
	}

	/** Outcome of one silence pass. */
	public record Stats(int notified, int cleared) {}

	public Stats run(DiagnosticsMapper mapper, Users users, Notifier notifier, long now) {
		DiagRule rule = mapper.getRuleByName(RULE_NAME);
		if (rule == null || DiagRule.DISABLED.equals(rule.getState()) || DiagRule.DRAFT.equals(rule.getState())) {
			return new Stats(0, 0);
		}

		long cutoff = now - _silenceDays * DAY_MS;
		boolean live = DiagRule.LIVE.equals(rule.getState()) && DiagRule.ACTOR_USER.equals(rule.getActor());

		// Rearm: a device that has checked in again (last access back within the
		// window) clears its latched silence notification.
		int cleared = mapper.clearReturnedSilentNotifications(rule.getId(), cutoff, now);

		int notified = 0;
		for (SilentDongle device : mapper.findSilentDongles(cutoff)) {
			if (mapper.countActiveNotifications(rule.getId(), device.getDeviceId()) > 0) {
				continue; // already latched — one nudge per silence period.
			}
			String userName = users.getUserName(device.getUserId());

			if (!live) {
				// SHADOW: project what LIVE would do, send nothing.
				mapper.insertNotification("DONGLE", device.getDeviceId(), userName, rule.getId(),
					"PENDING", true, now, null);
				notified++;
			} else if (notifier.notifyUser(rule, "DONGLE", device.getDeviceId(), userName)) {
				mapper.insertNotification("DONGLE", device.getDeviceId(), userName, rule.getId(),
					"SENT", false, now, now);
				notified++;
			}
		}

		if (notified > 0 || cleared > 0) {
			LOG.info("Dongle silence check: {} silent device(s) {}, {} rearmed.",
				notified, live ? "notified" : "projected (shadow)", cleared);
		}
		return new Stats(notified, cleared);
	}
}
