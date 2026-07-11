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
	 * Applies one event through the mapper. Must be called within an open
	 * session; the caller commits the batch.
	 */
	public void apply(DiagnosticsMapper mapper, DiagEvent event) {
		String scrubbed = truncate(Scrubber.scrub(event.message()));
		String signature = truncate(LogNormalizer.normalize(scrubbed));
		String sigId = LogNormalizer.sigId(event.source(), signature);
		long ts = event.timestampMs();
		int epochDay = event.epochDay();

		if (mapper.updateSignature(sigId, event.tag(), ts, ts, 1) == 0) {
			mapper.insertSignature(sigId, event.source(), signature, event.tag(), scrubbed, ts, ts, 1);
		}

		if (mapper.updateOriginSignature(sigId, event.originId(), ts, 1, epochDay) == 0) {
			mapper.insertOriginSignature(sigId, event.source(), event.originId(), event.userId(),
				ts, ts, 1, epochDay);
		}

		if (mapper.countSamples(sigId) < _sampleCap) {
			mapper.insertSample(ts, event.source(), sigId, event.originId(), event.userId(),
				event.severity(), event.uptimeS(), event.tag(), scrubbed);
		}
	}

	private static String truncate(String s) {
		return s.length() <= MAX_TEXT ? s : s.substring(0, MAX_TEXT);
	}
}
