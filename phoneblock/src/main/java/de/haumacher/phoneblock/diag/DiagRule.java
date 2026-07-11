/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A hot-editable detection rule (a row of {@code DIAG_RULE}). Matched against
 * {@code DIAG_SIGNATURE} signatures; on a persistence threshold it drives a
 * notification whose routing depends on {@link #getActor()}.
 */
public class DiagRule {

	/** Rule lifecycle states. */
	public static final String DRAFT = "DRAFT";
	public static final String SHADOW = "SHADOW";
	public static final String LIVE = "LIVE";
	public static final String DISABLED = "DISABLED";

	/** Notification routing. */
	public static final String ACTOR_USER = "USER";
	public static final String ACTOR_DEV = "DEV";
	public static final String ACTOR_NONE = "NONE";

	private long id;
	private String name = "";
	private String source;        // null = all sources
	private String matchTag;      // null = any tag
	private String matchRegex;
	private String category = "";
	private String actor = ACTOR_NONE;
	private int minDistinctDays = 1;
	private int minEvents = 1;
	private String templateKey;
	private String state = DRAFT;
	private String author = "";
	private String notes = "";
	private long created;
	private long updated;

	private transient Pattern compiled;

	/** The compiled {@link #getMatchRegex()} (cached), or {@code null} if invalid. */
	public Pattern pattern() {
		if (compiled == null && matchRegex != null) {
			try {
				compiled = Pattern.compile(matchRegex);
			} catch (PatternSyntaxException ex) {
				return null;
			}
		}
		return compiled;
	}

	/** Whether {@code signature} (already known to be of a matching source/tag) matches. */
	public boolean matches(String signature) {
		Pattern p = pattern();
		return p != null && p.matcher(signature).find();
	}

	public long getId() { return id; }
	public void setId(long id) { this.id = id; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getSource() { return source; }
	public void setSource(String source) { this.source = source; }
	public String getMatchTag() { return matchTag; }
	public void setMatchTag(String matchTag) { this.matchTag = matchTag; }
	public String getMatchRegex() { return matchRegex; }
	public void setMatchRegex(String matchRegex) { this.matchRegex = matchRegex; this.compiled = null; }
	public String getCategory() { return category; }
	public void setCategory(String category) { this.category = category; }
	public String getActor() { return actor; }
	public void setActor(String actor) { this.actor = actor; }
	public int getMinDistinctDays() { return minDistinctDays; }
	public void setMinDistinctDays(int minDistinctDays) { this.minDistinctDays = minDistinctDays; }
	public int getMinEvents() { return minEvents; }
	public void setMinEvents(int minEvents) { this.minEvents = minEvents; }
	public String getTemplateKey() { return templateKey; }
	public void setTemplateKey(String templateKey) { this.templateKey = templateKey; }
	public String getState() { return state; }
	public void setState(String state) { this.state = state; }
	public String getAuthor() { return author; }
	public void setAuthor(String author) { this.author = author; }
	public String getNotes() { return notes; }
	public void setNotes(String notes) { this.notes = notes; }
	public long getCreated() { return created; }
	public void setCreated(long created) { this.created = created; }
	public long getUpdated() { return updated; }
	public void setUpdated(long updated) { this.updated = updated; }
}
