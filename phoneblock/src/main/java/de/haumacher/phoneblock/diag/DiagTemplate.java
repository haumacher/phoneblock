/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

/**
 * A localized help-mail template (a row of {@code DIAG_TEMPLATE}). Rendered with
 * safe {@code {placeholder}} substitution only — never code.
 */
public class DiagTemplate {

	private long id;
	private String templateKey;
	private String lang = "de";
	private String subject = "";
	private String body = "";
	private long updated;

	public long getId() { return id; }
	public void setId(long id) { this.id = id; }
	public String getTemplateKey() { return templateKey; }
	public void setTemplateKey(String templateKey) { this.templateKey = templateKey; }
	public String getLang() { return lang; }
	public void setLang(String lang) { this.lang = lang; }
	public String getSubject() { return subject; }
	public void setSubject(String subject) { this.subject = subject; }
	public String getBody() { return body; }
	public void setBody(String body) { this.body = body; }
	public long getUpdated() { return updated; }
	public void setUpdated(long updated) { this.updated = updated; }
}
