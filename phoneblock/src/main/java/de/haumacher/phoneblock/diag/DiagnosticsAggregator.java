/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

/**
 * Turns a {@link DiagEvent} into aggregate upserts: scrub → normalize to a
 * signature → roll up {@code DIAG_SIGNATURE} and {@code DIAG_ORIGIN_SIGNATURE},
 * and keep a capped raw sample. Pure logic over a {@link DiagnosticsMapper}; the
 * caller owns the session/transaction (one per poll batch).
 */
public class DiagnosticsAggregator {

	/** Column width of the text columns; scrubbed text is truncated to fit. */
	private static final int MAX_TEXT = 1024;

	private final int _sampleCap;

	/**
	 * @param sampleCap max retained {@code DIAG_SAMPLE} rows per signature.
	 */
	public DiagnosticsAggregator(int sampleCap) {
		_sampleCap = sampleCap;
	}

	/**
	 * Applies one event through the mapper using only the built-in scrub rules.
	 * Convenience for callers (and tests) that do not layer DB scrub rules.
	 */
	public void apply(DiagnosticsMapper mapper, DiagEvent event) {
		apply(mapper, event, Scrubber.builtin());
	}

	/**
	 * Applies one event through the mapper, scrubbing with the given
	 * {@link Scrubber} (built-ins plus any LIVE {@code DIAG_SCRUB_RULE} rows). Must
	 * be called within an open session; the caller commits the batch.
	 *
	 * <p>Signature and sample are scrubbed independently so a {@code SAMPLE}-only
	 * scrub rule can mask the retained text without forking the grouping key.</p>
	 */
	public void apply(DiagnosticsMapper mapper, DiagEvent event, Scrubber scrubber) {
		String forSignature = truncate(scrubber.scrubForSignature(event.message()));
		String forSample = truncate(scrubber.scrubForSample(event.message()));
		String signature = truncate(LogNormalizer.normalize(forSignature));
		String sigId = LogNormalizer.sigId(event.source(), signature);
		long ts = event.timestampMs();
		int epochDay = event.epochDay();

		if (mapper.updateSignature(sigId, event.tag(), ts, ts, 1) == 0) {
			mapper.insertSignature(sigId, event.source(), signature, event.tag(), forSample, ts, ts, 1);
		}

		if (mapper.updateOriginSignature(sigId, event.originId(), ts, 1, epochDay) == 0) {
			mapper.insertOriginSignature(sigId, event.source(), event.originId(), event.userId(),
				ts, ts, 1, epochDay);
		}

		if (mapper.countSamples(sigId) < _sampleCap) {
			mapper.insertSample(ts, event.source(), sigId, event.originId(), event.userId(),
				event.severity(), event.uptimeS(), event.tag(), forSample);
		}
	}

	private static String truncate(String s) {
		return s.length() <= MAX_TEXT ? s : s.substring(0, MAX_TEXT);
	}
}
