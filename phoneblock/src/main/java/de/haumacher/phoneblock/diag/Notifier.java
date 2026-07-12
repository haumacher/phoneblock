/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

/**
 * The sink a {@link DiagnosticsMatcher} routes a confirmed, thresholded match to.
 * The matcher owns detection and the latch; the notifier owns the actual side
 * effect (mail, dev digest) and its own guards (kill switch, caps). Only invoked
 * for {@code LIVE} rules — {@code SHADOW} matches are recorded as dry-run
 * projections without touching the notifier.
 */
public interface Notifier {

	/**
	 * Attempts to send a user help mail for a {@code USER}-actor rule.
	 *
	 * @return {@code true} if a mail was actually sent (the matcher then latches
	 *         it as {@code SENT}); {@code false} if suppressed (kill switch, cap,
	 *         no address) so the matcher leaves it unlatched to retry later.
	 */
	boolean notifyUser(DiagRule rule, String source, String originId, String userId);

	/** Emits a developer alert for a {@code DEV}-actor rule (firmware/server bugs). */
	void notifyDev(DiagRule rule, String source, String originId, String userId);
}
