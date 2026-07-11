/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

/**
 * A source-specific recognizer: the only source-coupled part of the diagnostics
 * pipeline. Given a parsed server-log line, it returns a {@link DiagEvent} for
 * the lines it owns (establishing {@code source}, {@code originId} and
 * {@code userId}) or {@code null} for everything else.
 *
 * <p>The reader runs every registered recognizer over each line; the first
 * non-{@code null} result wins. Adding a new source (the mobile app, the
 * server's own WARN/ERROR) is a new recognizer plus a {@code source} value — no
 * schema or pipeline change.</p>
 */
public interface LineRecognizer {

	/** The event for this line, or {@code null} if this recognizer does not own it. */
	DiagEvent recognize(TinylogLine line);
}
