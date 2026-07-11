/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.DBUserSettings;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.mail.MailService;

/**
 * The live {@link Notifier}: sends the user help mail for a {@code LIVE}+
 * {@code USER} rule through {@link MailService}, and logs a {@code DEV} alert for
 * firmware/server-bug rules.
 *
 * <p>Guards, in order: the global mail kill switch ({@code diag.mail.enabled},
 * default off), the global and per-user daily caps, a resolvable recipient, and a
 * template in the user's language. Any guard failing returns {@code false} so the
 * matcher leaves the match unlatched to retry once conditions allow.</p>
 */
public class MailNotifier implements Notifier {

	private static final Logger LOG = LoggerFactory.getLogger(MailNotifier.class);

	private static final long DAY_MS = 86_400_000L;

	private final DBService _db;
	private final MailService _mail;
	private final int _userDailyCap;
	private final int _globalDailyCap;

	public MailNotifier(DBService db, MailService mail, int userDailyCap, int globalDailyCap) {
		_db = db;
		_mail = mail;
		_userDailyCap = userDailyCap;
		_globalDailyCap = globalDailyCap;
	}

	@Override
	public boolean notifyUser(DiagRule rule, String source, String originId, String userId) {
		if (userId == null || rule.getTemplateKey() == null || _mail == null) {
			return false;
		}
		try (SqlSession session = _db.db().openSession()) {
			Users users = session.getMapper(Users.class);
			if (!Boolean.parseBoolean(users.getProperty("diag.mail.enabled"))) {
				return false; // kill switch (default off) — no user mail until enabled.
			}

			DiagnosticsMapper dm = session.getMapper(DiagnosticsMapper.class);
			long now = System.currentTimeMillis();
			long dayAgo = now - DAY_MS;
			if (dm.countSentSince(dayAgo) >= _globalDailyCap) {
				LOG.warn("Diagnostics mail global daily cap ({}) reached — suppressing.", _globalDailyCap);
				return false;
			}
			if (dm.countSentForUserSince(userId, dayAgo) >= _userDailyCap) {
				LOG.info("Diagnostics mail per-user cap reached for {} — suppressing.", userId);
				return false;
			}

			DBUserSettings settings = users.getSettingsRaw(userId);
			if (settings == null || settings.getEmail() == null || settings.getEmail().isBlank()) {
				return false;
			}

			DiagTemplate tpl = resolveTemplate(dm, rule.getTemplateKey(), settings.getLang());
			if (tpl == null) {
				LOG.warn("No diagnostics template '{}' (rule {}) — cannot mail.", rule.getTemplateKey(), rule.getId());
				return false;
			}

			Map<String, String> vars = Map.of(
				"deviceId", originId,
				"source", source,
				"category", rule.getCategory(),
				"userId", userId);
			String subject = render(tpl.getSubject(), vars);
			String body = render(tpl.getBody(), vars);
			return _mail.sendDiagnosticsMail(settings, subject, body);
		} catch (Exception ex) {
			LOG.error("Diagnostics user notification failed.", ex);
			return false;
		}
	}

	@Override
	public void notifyDev(DiagRule rule, String source, String originId, String userId) {
		LOG.warn("Diagnostics DEV alert [{}]: rule '{}' (#{}) on {} origin {} (user {}).",
			rule.getCategory(), rule.getName(), rule.getId(), source, originId, userId);
	}

	/**
	 * Picks the template in the user's language, falling back to English (the
	 * international default) and only then to German.
	 */
	static DiagTemplate resolveTemplate(DiagnosticsMapper dm, String templateKey, String userLang) {
		String lang = userLang == null || userLang.isBlank() ? "en" : userLang;
		for (String candidate : new String[] {lang, "en", "de"}) {
			DiagTemplate tpl = dm.getTemplate(templateKey, candidate);
			if (tpl != null) {
				return tpl;
			}
		}
		return null;
	}

	/** Safe {@code {placeholder}} substitution — no code, no expression evaluation. */
	static String render(String template, Map<String, String> vars) {
		String s = template;
		for (Map.Entry<String, String> e : vars.entrySet()) {
			s = s.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
		}
		return s;
	}
}
