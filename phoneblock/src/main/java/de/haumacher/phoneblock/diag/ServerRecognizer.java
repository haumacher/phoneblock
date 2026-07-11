/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

/**
 * The second source: the server's own native {@code WARN}/{@code ERROR} lines
 * (the ones {@code pb-log-summary.sh} groups). Proves the framework is
 * source-agnostic — no schema or pipeline change, just another recognizer and a
 * {@code source} value.
 *
 * <p>{@code originId} is the node id (one server → a constant); {@code userId} is
 * {@code null}, so server events can only ever match {@code DEV}/{@code NONE}
 * rules, never mail a user. The diagnostics framework's own log lines are skipped
 * to avoid a self-feedback loop.</p>
 */
public class ServerRecognizer implements LineRecognizer {

	/** The {@code source} value for server events. */
	public static final String SOURCE = "SERVER";

	private static final String OWN_PACKAGE = "de.haumacher.phoneblock.diag.";

	private final String _nodeId;

	public ServerRecognizer(String nodeId) {
		_nodeId = nodeId == null || nodeId.isBlank() ? "server" : nodeId;
	}

	@Override
	public DiagEvent recognize(TinylogLine line) {
		String level = line.level();
		boolean error = "ERROR".equals(level);
		if (!error && !"WARN".equals(level)) {
			return null;
		}
		if (line.className().startsWith(OWN_PACKAGE)) {
			return null; // don't ingest our own diagnostics logging.
		}
		String tag = simpleName(line.className());
		String message = tag + ": " + line.message();
		return new DiagEvent(SOURCE, _nodeId, null, error ? "E" : "W", null, tag, message, line.timestampMs());
	}

	private static String simpleName(String className) {
		int dot = className.lastIndexOf('.');
		return dot < 0 ? className : className.substring(dot + 1);
	}
}
