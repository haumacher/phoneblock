/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A hot-editable anonymizer rule (a row of {@code DIAG_SCRUB_RULE}). Applied by
 * the {@link Scrubber} on ingest to mask PII out of a log message before it
 * becomes a signature and/or a retained sample.
 *
 * <p>The built-in {@link Scrubber} rule set is the always-on baseline; LIVE rows
 * of this table are layered on top so an agent can grow the anonymizer without a
 * redeploy. See {@code docs/plans/2026-07-11-diagnostics-framework-design.md}.</p>
 */
public class DiagScrubRule {

	/** Lifecycle states. */
	public static final String DRAFT = "DRAFT";
	public static final String LIVE = "LIVE";
	public static final String DISABLED = "DISABLED";

	/** Where the masking applies. */
	public static final String SIGNATURE = "SIGNATURE";
	public static final String SAMPLE = "SAMPLE";
	public static final String BOTH = "BOTH";

	private long id;
	private String name = "";
	private String source;          // null = all sources
	private String pattern;
	private String replacement = "";
	private String appliesTo = BOTH;
	private String state = DRAFT;
	private int version = 1;
	private String author = "";
	private long updated;

	private transient Pattern compiled;

	/** The compiled {@link #getPattern()} (cached), or {@code null} if invalid. */
	public Pattern pattern() {
		if (compiled == null && pattern != null) {
			try {
				compiled = Pattern.compile(pattern);
			} catch (PatternSyntaxException ex) {
				return null;
			}
		}
		return compiled;
	}

	public long getId() { return id; }
	public void setId(long id) { this.id = id; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getSource() { return source; }
	public void setSource(String source) { this.source = source; }
	public String getPattern() { return pattern; }
	public void setPattern(String pattern) { this.pattern = pattern; this.compiled = null; }
	public String getReplacement() { return replacement; }
	public void setReplacement(String replacement) { this.replacement = replacement; }
	public String getAppliesTo() { return appliesTo; }
	public void setAppliesTo(String appliesTo) { this.appliesTo = appliesTo; }
	public String getState() { return state; }
	public void setState(String state) { this.state = state; }
	public int getVersion() { return version; }
	public void setVersion(int version) { this.version = version; }
	public String getAuthor() { return author; }
	public void setAuthor(String author) { this.author = author; }
	public long getUpdated() { return updated; }
	public void setUpdated(long updated) { this.updated = updated; }
}
