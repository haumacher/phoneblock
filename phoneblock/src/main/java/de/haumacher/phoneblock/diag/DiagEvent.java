/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

/**
 * A single diagnostic event, produced by a source-specific
 * {@link LineRecognizer} from one server-log line and consumed by the
 * source-agnostic aggregation pipeline.
 *
 * <p>These are the common fields the core stores; the raw line format and the
 * way {@code originId}/{@code userId} are established are the only
 * source-specific concerns (see the design doc
 * {@code docs/plans/2026-07-11-diagnostics-framework-design.md}).</p>
 *
 * @param source     the reporting source, e.g. {@code "DONGLE"}.
 * @param originId   the distinct thing that reported (a dongle device id, an app
 *                   install id, …); never {@code null} (use a placeholder when
 *                   unknown).
 * @param userId     the account to attribute/notify, or {@code null} when the
 *                   source has no user association.
 * @param severity   {@code "E"} or {@code "W"} (error/warning).
 * @param uptimeS    the reporter uptime in seconds when the line was emitted, or
 *                   {@code null} if the source does not carry it.
 * @param tag        the component tag (e.g. {@code "sip"}), for convenience; also
 *                   present as the prefix of {@code message}.
 * @param message    the diagnostic message used for signature computation
 *                   (typically {@code "<tag>: <text>"}); already stripped of the
 *                   severity/uptime envelope.
 * @param timestampMs the event time in Unix millis, parsed from the log line's
 *                   own timestamp (not wall-clock at ingest).
 */
public record DiagEvent(
		String source,
		String originId,
		String userId,
		String severity,
		Long uptimeS,
		String tag,
		String message,
		long timestampMs) {

	/** The UTC epoch-day of {@link #timestampMs}, used for distinct-day counting. */
	public int epochDay() {
		return (int) Math.floorDiv(timestampMs, 86_400_000L);
	}
}
