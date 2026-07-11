/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.msgbuf.json.JsonReader;
import de.haumacher.msgbuf.json.JsonToken;
import de.haumacher.msgbuf.json.JsonWriter;
import de.haumacher.msgbuf.server.io.ReaderAdapter;
import de.haumacher.msgbuf.server.io.WriterAdapter;
import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.settings.AuthToken;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The diagnostics introspection REST API ({@code /api/diag/*}) — the primitive an
 * agent (optionally via a thin MCP wrapper) drives to discover unmatched
 * signatures, dry-run candidate rules, author rules (into DRAFT/SHADOW) and
 * promote them.
 *
 * <p>Auth: every route requires the {@code accessDiagnostics} token capability;
 * the one elevated transition — moving a rule to {@code LIVE} — additionally
 * requires {@code accessAdmin}. Both are enforced here (mirroring the token-scope
 * pattern of the other endpoints). Writes land in {@code DRAFT}/{@code SHADOW}
 * only.</p>
 */
@WebServlet(urlPatterns = DiagnosticsApiServlet.URL_PATTERN)
public class DiagnosticsApiServlet extends HttpServlet {

	private static final Logger LOG = LoggerFactory.getLogger(DiagnosticsApiServlet.class);

	/** The servlet path (matched by {@code BasicLoginFilter} for the capability check). */
	public static final String SERVLET_PATH = "/api/diag";

	/** The full URL pattern this servlet is mapped to. */
	public static final String URL_PATTERN = SERVLET_PATH + "/*";

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		if (!authorize(req, resp, false)) {
			return;
		}
		String path = path(req);
		try (SqlSession session = DBService.getInstance().openSession()) {
			DiagnosticsMapper mapper = session.getMapper(DiagnosticsMapper.class);
			if (path.equals("/signatures")) {
				listSignatures(req, resp, mapper);
			} else if (path.startsWith("/signatures/")) {
				signatureDetail(resp, mapper, path.substring("/signatures/".length()));
			} else if (path.equals("/rules")) {
				listRules(req, resp, mapper);
			} else if (path.matches("/rules/\\d+/stats")) {
				ruleStats(resp, mapper, ruleId(path));
			} else if (path.startsWith("/rules/")) {
				ruleDetail(resp, mapper, path.substring("/rules/".length()));
			} else if (path.equals("/templates")) {
				listTemplates(req, resp, mapper);
			} else if (path.equals("/scrub")) {
				listScrub(req, resp, mapper);
			} else if (path.equals("/notifications")) {
				listNotifications(req, resp, mapper);
			} else if (path.equals("/ingest/status")) {
				ingestStatus(resp, mapper);
			} else if (path.startsWith("/origins/") && path.endsWith("/timeline")) {
				originTimeline(req, resp, mapper, path);
			} else {
				ServletUtil.sendMessage(resp, HttpServletResponse.SC_NOT_FOUND, "Unknown resource: " + path);
			}
		}
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		if (!authorize(req, resp, false)) {
			return;
		}
		String path = path(req);
		try (SqlSession session = DBService.getInstance().openSession()) {
			DiagnosticsMapper mapper = session.getMapper(DiagnosticsMapper.class);
			if (path.equals("/rules/dryrun")) {
				dryRun(req, resp, mapper);
			} else if (path.equals("/rules")) {
				createRule(req, resp, session, mapper);
			} else if (path.matches("/rules/\\d+/state")) {
				setRuleState(req, resp, session, mapper, ruleId(path));
			} else if (path.equals("/templates")) {
				upsertTemplate(req, resp, session, mapper);
			} else if (path.equals("/scrub")) {
				createScrub(req, resp, session, mapper);
			} else if (path.matches("/scrub/\\d+/state")) {
				setScrubState(req, resp, session, mapper, scrubId(path));
			} else if (path.equals("/scrub/audit")) {
				auditSamples(req, resp, mapper);
			} else if (path.equals("/mail/preview")) {
				mailPreview(req, resp, mapper);
			} else if (path.equals("/killswitch")) {
				killswitch(req, resp, session);
			} else {
				ServletUtil.sendMessage(resp, HttpServletResponse.SC_NOT_FOUND, "Unknown resource: " + path);
			}
		}
	}

	// ---- Read ----

	private void listSignatures(HttpServletRequest req, HttpServletResponse resp, DiagnosticsMapper mapper)
			throws IOException {
		String source = trimToNull(req.getParameter("source"));
		boolean onlyUnmatched = "true".equals(req.getParameter("matched")) ? false
				: Boolean.parseBoolean(req.getParameter("unmatched"));
		int limit = intParam(req, "limit", 100);
		List<SignatureRow> all = mapper.listSignatures(source, onlyUnmatched);
		writeJson(resp, w -> {
			w.beginArray();
			int n = 0;
			for (SignatureRow s : all) {
				if (n++ >= limit) {
					break;
				}
				writeSignature(w, s);
			}
			w.endArray();
		});
	}

	private void signatureDetail(HttpServletResponse resp, DiagnosticsMapper mapper, String sigId) throws IOException {
		SignatureRow sig = mapper.getSignature(sigId);
		if (sig == null) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_NOT_FOUND, "No such signature.");
			return;
		}
		List<Map<String, Object>> samples = mapper.listSamples(sigId, 20);
		writeJson(resp, w -> {
			w.beginObject();
			w.name("signature");
			writeSignature(w, sig);
			w.name("samples").beginArray();
			for (Map<String, Object> s : samples) {
				w.beginObject();
				w.name("receivedMs").value(asLong(s.get("RECEIVEDMS")));
				w.name("severity").value(str(s.get("SEVERITY")));
				// Optional source-specific attribute: the reporter uptime in seconds
				// (the dongle fills it; other sources leave it null).
				w.name("uptimeS");
				writeValue(w, s.get("UPTIMES"));
				w.name("tag").value(str(s.get("TAG")));
				w.name("originId").value(str(s.get("ORIGINID")));
				w.name("message").value(str(s.get("MESSAGESCRUBBED")));
				w.endObject();
			}
			w.endArray();
			w.endObject();
		});
	}

	private void listRules(HttpServletRequest req, HttpServletResponse resp, DiagnosticsMapper mapper)
			throws IOException {
		String state = trimToNull(req.getParameter("state"));
		List<DiagRule> rules = mapper.listRules(state);
		writeJson(resp, w -> {
			w.beginArray();
			for (DiagRule r : rules) {
				writeRule(w, r);
			}
			w.endArray();
		});
	}

	private void ruleDetail(HttpServletResponse resp, DiagnosticsMapper mapper, String idStr) throws IOException {
		long id = parseLong(idStr, -1);
		DiagRule rule = id < 0 ? null : mapper.getRule(id);
		if (rule == null) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_NOT_FOUND, "No such rule.");
			return;
		}
		writeJson(resp, w -> writeRule(w, rule));
	}

	private void listTemplates(HttpServletRequest req, HttpServletResponse resp, DiagnosticsMapper mapper)
			throws IOException {
		List<DiagTemplate> templates = mapper.listTemplates(trimToNull(req.getParameter("key")));
		writeJson(resp, w -> {
			w.beginArray();
			for (DiagTemplate t : templates) {
				writeTemplate(w, t);
			}
			w.endArray();
		});
	}

	// ---- Experiment ----

	private void dryRun(HttpServletRequest req, HttpServletResponse resp, DiagnosticsMapper mapper) throws IOException {
		Map<String, String> body = readObject(req);
		String regex = body.get("matchRegex");
		if (regex == null || regex.isBlank()) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST, "matchRegex is required.");
			return;
		}
		DiagnosticsMatcher.DryRun result;
		try {
			result = DiagnosticsMatcher.dryRun(mapper, trimToNull(body.get("source")),
				trimToNull(body.get("matchTag")), regex,
				parseInt(body.get("minDistinctDays"), 1), parseInt(body.get("minEvents"), 1));
		} catch (RuntimeException ex) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid regex: " + ex.getMessage());
			return;
		}
		writeJson(resp, w -> {
			w.beginObject();
			w.name("matchingSignatures").value(result.matchingSignatures());
			w.name("matchingOrigins").value(result.matchingOrigins());
			w.name("matchingUsers").value(result.matchingUsers());
			w.name("sampleSignatures").beginArray();
			for (String s : result.sampleSignatures()) {
				w.value(s);
			}
			w.endArray();
			w.endObject();
		});
	}

	// ---- Author ----

	private void createRule(HttpServletRequest req, HttpServletResponse resp, SqlSession session,
			DiagnosticsMapper mapper) throws IOException {
		Map<String, String> body = readObject(req);
		if (body.get("matchRegex") == null || body.get("matchRegex").isBlank()) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST, "matchRegex is required.");
			return;
		}
		String state = body.getOrDefault("state", DiagRule.DRAFT);
		if (DiagRule.LIVE.equals(state)) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_FORBIDDEN,
				"A rule cannot be created LIVE; create it in SHADOW and promote via /state.");
			return;
		}
		long now = System.currentTimeMillis();
		DiagRule rule = new DiagRule();
		rule.setName(body.getOrDefault("name", ""));
		rule.setSource(trimToNull(body.get("source")));
		rule.setMatchTag(trimToNull(body.get("matchTag")));
		rule.setMatchRegex(body.get("matchRegex"));
		rule.setCategory(body.getOrDefault("category", ""));
		rule.setActor(body.getOrDefault("actor", DiagRule.ACTOR_NONE));
		rule.setMinDistinctDays(parseInt(body.get("minDistinctDays"), 1));
		rule.setMinEvents(parseInt(body.get("minEvents"), 1));
		rule.setTemplateKey(trimToNull(body.get("templateKey")));
		rule.setState(DiagRule.SHADOW.equals(state) ? DiagRule.SHADOW : DiagRule.DRAFT);
		rule.setAuthor(LoginFilter.getAuthenticatedUser(req));
		rule.setNotes(body.getOrDefault("notes", ""));
		rule.setCreated(now);
		rule.setUpdated(now);

		mapper.insertRule(rule);
		bumpRulesetVersion(session);
		session.commit();
		writeJson(resp, w -> writeRule(w, rule));
	}

	private void setRuleState(HttpServletRequest req, HttpServletResponse resp, SqlSession session,
			DiagnosticsMapper mapper, long id) throws IOException {
		Map<String, String> body = readObject(req);
		String state = body.get("state");
		if (state == null || !isValidState(state)) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST,
				"state must be one of DRAFT, SHADOW, LIVE, DISABLED.");
			return;
		}
		// The one elevated transition requires accessAdmin.
		if (DiagRule.LIVE.equals(state) && !authorize(req, resp, true)) {
			return;
		}
		DiagRule rule = mapper.getRule(id);
		if (rule == null) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_NOT_FOUND, "No such rule.");
			return;
		}
		mapper.setRuleState(id, state, System.currentTimeMillis());
		bumpRulesetVersion(session);
		session.commit();
		LOG.info("Diagnostics rule {} state -> {} by {}.", id, state, LoginFilter.getAuthenticatedUser(req));
		writeJson(resp, w -> {
			w.beginObject();
			w.name("id").value(id);
			w.name("state").value(state);
			w.endObject();
		});
	}

	private void upsertTemplate(HttpServletRequest req, HttpServletResponse resp, SqlSession session,
			DiagnosticsMapper mapper) throws IOException {
		Map<String, String> body = readObject(req);
		String key = trimToNull(body.get("templateKey"));
		if (key == null || body.get("subject") == null || body.get("body") == null) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST,
				"templateKey, subject and body are required.");
			return;
		}
		DiagTemplate template = new DiagTemplate();
		template.setTemplateKey(key);
		template.setLang(body.getOrDefault("lang", "de"));
		template.setSubject(body.get("subject"));
		template.setBody(body.get("body"));
		template.setUpdated(System.currentTimeMillis());
		mapper.upsertTemplate(template);
		bumpRulesetVersion(session);
		session.commit();
		writeJson(resp, w -> writeTemplate(w, template));
	}

	private void listScrub(HttpServletRequest req, HttpServletResponse resp, DiagnosticsMapper mapper)
			throws IOException {
		List<DiagScrubRule> rules = mapper.listScrubRules(trimToNull(req.getParameter("state")));
		writeJson(resp, w -> {
			w.beginArray();
			for (DiagScrubRule r : rules) {
				writeScrub(w, r);
			}
			w.endArray();
		});
	}

	private void listNotifications(HttpServletRequest req, HttpServletResponse resp, DiagnosticsMapper mapper)
			throws IOException {
		String source = trimToNull(req.getParameter("source"));
		long ruleId = parseLong(req.getParameter("ruleId"), -1);
		String state = trimToNull(req.getParameter("state"));
		long since = parseLong(req.getParameter("since"), 0);
		int limit = intParam(req, "limit", 200);
		List<Map<String, Object>> rows = mapper.listNotifications(source, ruleId, state, since, limit);
		writeJson(resp, w -> writeRows(w, rows));
	}

	private void ruleStats(HttpServletResponse resp, DiagnosticsMapper mapper, long ruleId) throws IOException {
		DiagRule rule = mapper.getRule(ruleId);
		if (rule == null) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_NOT_FOUND, "No such rule.");
			return;
		}
		List<Map<String, Object>> byState = mapper.notificationStatsByState(ruleId);
		writeJson(resp, w -> {
			w.beginObject();
			w.name("ruleId").value(ruleId);
			w.name("state").value(rule.getState());
			w.name("notificationsByState").beginObject();
			for (Map<String, Object> row : byState) {
				w.name(str(row.get("STATE"))).value(asLong(row.get("N")));
			}
			w.endObject();
			w.endObject();
		});
	}

	private void ingestStatus(HttpServletResponse resp, DiagnosticsMapper mapper) throws IOException {
		IngestCursor cursor = mapper.getCursor("server");
		long now = System.currentTimeMillis();
		long lastTs = cursor == null ? 0 : cursor.getLastLineTs();
		writeJson(resp, w -> {
			w.beginObject();
			w.name("streamId").value("server");
			w.name("segmentCount").value(cursor == null ? -1 : cursor.getSegmentCount());
			w.name("byteOffset").value(cursor == null ? 0 : cursor.getByteOffset());
			w.name("lastLineTs").value(lastTs);
			w.name("lagMs").value(lastTs == 0 ? -1 : now - lastTs);
			w.name("signatures").value(mapper.countSignatures());
			w.name("originSignatures").value(mapper.countOriginSignatures());
			w.name("totalEvents").value(mapper.totalEvents());
			w.name("samples").value(mapper.countAllSamples());
			w.endObject();
		});
	}

	private void originTimeline(HttpServletRequest req, HttpServletResponse resp, DiagnosticsMapper mapper,
			String path) throws IOException {
		// "/origins/<source>/<originId>/timeline"
		String middle = path.substring("/origins/".length(), path.length() - "/timeline".length());
		int slash = middle.indexOf('/');
		if (slash < 0) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST,
				"Path must be /origins/{source}/{originId}/timeline.");
			return;
		}
		String source = middle.substring(0, slash);
		String originId = middle.substring(slash + 1);
		long since = parseLong(req.getParameter("since"), 0);
		List<Map<String, Object>> rows = mapper.originTimeline(source, originId, since);
		writeJson(resp, w -> writeRows(w, rows));
	}

	// ---- Author / experiment (scrub) ----

	/**
	 * Creates a scrub rule. A new rule only ever <em>adds</em> masking
	 * (tightening), so it lands {@code LIVE} immediately under
	 * {@code accessDiagnostics} — matching the design's asymmetric gate. The
	 * relaxing direction (disabling a rule) is the admin-gated
	 * {@code /scrub/{id}/state} transition.
	 */
	private void createScrub(HttpServletRequest req, HttpServletResponse resp, SqlSession session,
			DiagnosticsMapper mapper) throws IOException {
		Map<String, String> body = readObject(req);
		String pattern = body.get("pattern");
		if (pattern == null || pattern.isBlank()) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST, "pattern is required.");
			return;
		}
		try {
			Pattern.compile(pattern);
		} catch (PatternSyntaxException ex) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid pattern: " + ex.getMessage());
			return;
		}
		String appliesTo = body.getOrDefault("appliesTo", DiagScrubRule.BOTH);
		if (!DiagScrubRule.BOTH.equals(appliesTo) && !DiagScrubRule.SIGNATURE.equals(appliesTo)
				&& !DiagScrubRule.SAMPLE.equals(appliesTo)) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST,
				"appliesTo must be SIGNATURE, SAMPLE or BOTH.");
			return;
		}
		DiagScrubRule rule = new DiagScrubRule();
		rule.setName(body.getOrDefault("name", ""));
		rule.setSource(trimToNull(body.get("source")));
		rule.setPattern(pattern);
		rule.setReplacement(body.getOrDefault("replacement", ""));
		rule.setAppliesTo(appliesTo);
		rule.setState(DiagScrubRule.LIVE);
		rule.setVersion(1);
		rule.setAuthor(LoginFilter.getAuthenticatedUser(req));
		rule.setUpdated(System.currentTimeMillis());
		mapper.insertScrubRule(rule);
		bumpRulesetVersion(session);
		session.commit();
		LOG.info("Diagnostics scrub rule {} created LIVE by {}.", rule.getId(), rule.getAuthor());
		writeJson(resp, w -> writeScrub(w, rule));
	}

	/**
	 * Changes a scrub rule's state. Disabling relaxes the anonymizer (retains more
	 * text) and re-enabling re-tightens; both are outward-affecting anonymizer
	 * changes, so this transition requires {@code accessAdmin}.
	 */
	private void setScrubState(HttpServletRequest req, HttpServletResponse resp, SqlSession session,
			DiagnosticsMapper mapper, long id) throws IOException {
		if (!authorize(req, resp, true)) {
			return;
		}
		Map<String, String> body = readObject(req);
		String state = body.get("state");
		if (!DiagScrubRule.LIVE.equals(state) && !DiagScrubRule.DISABLED.equals(state)
				&& !DiagScrubRule.DRAFT.equals(state)) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST,
				"state must be one of DRAFT, LIVE, DISABLED.");
			return;
		}
		if (mapper.getScrubRule(id) == null) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_NOT_FOUND, "No such scrub rule.");
			return;
		}
		mapper.setScrubRuleState(id, state, System.currentTimeMillis());
		bumpRulesetVersion(session);
		session.commit();
		LOG.info("Diagnostics scrub rule {} state -> {} by {}.", id, state, LoginFilter.getAuthenticatedUser(req));
		writeJson(resp, w -> {
			w.beginObject();
			w.name("id").value(id);
			w.name("state").value(state);
			w.endObject();
		});
	}

	/**
	 * Scans retained samples for PII shapes that survived scrubbing. With a
	 * {@code candidatePattern} it reports the samples that pattern would newly
	 * mask (so an agent can test a proposed scrub rule against real data); without
	 * one it applies a set of built-in "suspicious shape" probes. No side effects.
	 */
	private void auditSamples(HttpServletRequest req, HttpServletResponse resp, DiagnosticsMapper mapper)
			throws IOException {
		Map<String, String> body = readObject(req);
		String source = trimToNull(body.get("source"));
		int limit = parseInt(body.get("limit"), 500);
		List<Pattern> probes = new ArrayList<>();
		String candidate = trimToNull(body.get("candidatePattern"));
		if (candidate != null) {
			try {
				probes.add(Pattern.compile(candidate));
			} catch (PatternSyntaxException ex) {
				ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST,
					"Invalid candidatePattern: " + ex.getMessage());
				return;
			}
		} else {
			// Default probes: email-ish, IPv4, and a long bare digit run (a phone or
			// account number that the conservative baseline leaves untouched).
			probes.add(Pattern.compile("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}"));
			probes.add(Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"));
			probes.add(Pattern.compile("\\b\\d{7,}\\b"));
		}
		List<Map<String, Object>> samples = mapper.recentSamples(source, limit);
		List<Map<String, Object>> matches = new ArrayList<>();
		for (Map<String, Object> s : samples) {
			String message = str(s.get("MESSAGE"));
			for (Pattern p : probes) {
				if (p.matcher(message).find()) {
					matches.add(s);
					break;
				}
			}
		}
		writeJson(resp, w -> {
			w.beginObject();
			w.name("scanned").value(samples.size());
			w.name("matched").value(matches.size());
			w.name("matches");
			writeRows(w, matches);
			w.endObject();
		});
	}

	/** Renders a template with placeholder substitution — no side effects. */
	private void mailPreview(HttpServletRequest req, HttpServletResponse resp, DiagnosticsMapper mapper)
			throws IOException {
		Map<String, String> body = readObject(req);
		String key = trimToNull(body.get("templateKey"));
		if (key == null) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST, "templateKey is required.");
			return;
		}
		String lang = body.getOrDefault("lang", "en");
		DiagTemplate tpl = MailNotifier.resolveTemplate(mapper, key, lang);
		if (tpl == null) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_NOT_FOUND, "No such template: " + key);
			return;
		}
		Map<String, String> vars = Map.of(
			"deviceId", body.getOrDefault("originId", "<device>"),
			"source", body.getOrDefault("source", ""),
			"category", body.getOrDefault("category", ""),
			"userId", body.getOrDefault("userId", ""));
		String subject = MailNotifier.render(tpl.getSubject(), vars);
		String rendered = MailNotifier.render(tpl.getBody(), vars);
		writeJson(resp, w -> {
			w.beginObject();
			w.name("templateKey").value(key);
			w.name("lang").value(tpl.getLang());
			w.name("subject").value(subject);
			w.name("body").value(rendered);
			w.endObject();
		});
	}

	/** Flips the global mail kill switch ({@code diag.mail.enabled}). Admin-gated. */
	private void killswitch(HttpServletRequest req, HttpServletResponse resp, SqlSession session) throws IOException {
		if (!authorize(req, resp, true)) {
			return;
		}
		Map<String, String> body = readObject(req);
		String enabled = body.get("enabled");
		if (enabled == null) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST, "enabled (boolean) is required.");
			return;
		}
		boolean on = Boolean.parseBoolean(enabled);
		de.haumacher.phoneblock.db.Users users = session.getMapper(de.haumacher.phoneblock.db.Users.class);
		users.updateProperty("diag.mail.enabled", Boolean.toString(on));
		session.commit();
		LOG.warn("Diagnostics mail kill switch set to {} by {}.", on, LoginFilter.getAuthenticatedUser(req));
		writeJson(resp, w -> {
			w.beginObject();
			w.name("enabled").value(on);
			w.endObject();
		});
	}

	// ---- helpers ----

	/**
	 * Enforces the token capability. {@code needAdmin=false} requires
	 * {@code accessDiagnostics}; {@code true} additionally requires
	 * {@code accessAdmin}. Writes the error response and returns {@code false} when
	 * unauthorized.
	 */
	private boolean authorize(HttpServletRequest req, HttpServletResponse resp, boolean needAdmin) throws IOException {
		AuthToken auth = LoginFilter.getAuthorization(req);
		if (auth == null || !auth.isAccessDiagnostics()) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_UNAUTHORIZED,
				"A token with the diagnostics capability is required.");
			return false;
		}
		if (needAdmin && !auth.isAccessAdmin()) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_FORBIDDEN,
				"Promoting a rule to LIVE requires the admin capability.");
			return false;
		}
		return true;
	}

	private void bumpRulesetVersion(SqlSession session) {
		de.haumacher.phoneblock.db.Users users = session.getMapper(de.haumacher.phoneblock.db.Users.class);
		String current = users.getProperty("diag.ruleset.version");
		long next = parseLong(current, 0) + 1;
		users.updateProperty("diag.ruleset.version", Long.toString(next));
	}

	private static void writeSignature(JsonWriter w, SignatureRow s) throws IOException {
		w.beginObject();
		w.name("sigId").value(s.getSigId());
		w.name("source").value(s.getSource());
		w.name("signature").value(s.getSignature());
		w.name("tag").value(s.getTag());
		w.name("category");
		if (s.getCategory() == null) {
			w.nullValue();
		} else {
			w.value(s.getCategory());
		}
		w.name("totalEvents").value(s.getTotalEvents());
		w.name("firstSeen").value(s.getFirstSeen());
		w.name("lastSeen").value(s.getLastSeen());
		w.endObject();
	}

	private static void writeRule(JsonWriter w, DiagRule r) throws IOException {
		w.beginObject();
		w.name("id").value(r.getId());
		w.name("name").value(r.getName());
		w.name("source"); nullable(w, r.getSource());
		w.name("matchTag"); nullable(w, r.getMatchTag());
		w.name("matchRegex").value(r.getMatchRegex());
		w.name("category").value(r.getCategory());
		w.name("actor").value(r.getActor());
		w.name("minDistinctDays").value(r.getMinDistinctDays());
		w.name("minEvents").value(r.getMinEvents());
		w.name("templateKey"); nullable(w, r.getTemplateKey());
		w.name("state").value(r.getState());
		w.name("author").value(r.getAuthor());
		w.name("notes").value(r.getNotes());
		w.endObject();
	}

	private static void writeTemplate(JsonWriter w, DiagTemplate t) throws IOException {
		w.beginObject();
		w.name("templateKey").value(t.getTemplateKey());
		w.name("lang").value(t.getLang());
		w.name("subject").value(t.getSubject());
		w.name("body").value(t.getBody());
		w.name("updated").value(t.getUpdated());
		w.endObject();
	}

	private static void writeScrub(JsonWriter w, DiagScrubRule r) throws IOException {
		w.beginObject();
		w.name("id").value(r.getId());
		w.name("name").value(r.getName());
		w.name("source"); nullable(w, r.getSource());
		w.name("pattern").value(r.getPattern());
		w.name("replacement").value(r.getReplacement());
		w.name("appliesTo").value(r.getAppliesTo());
		w.name("state").value(r.getState());
		w.name("version").value(r.getVersion());
		w.name("author").value(r.getAuthor());
		w.name("updated").value(r.getUpdated());
		w.endObject();
	}

	/** Writes a list of column-keyed rows as a JSON array of objects. */
	private static void writeRows(JsonWriter w, List<Map<String, Object>> rows) throws IOException {
		w.beginArray();
		for (Map<String, Object> row : rows) {
			w.beginObject();
			for (Map.Entry<String, Object> e : row.entrySet()) {
				w.name(e.getKey());
				writeValue(w, e.getValue());
			}
			w.endObject();
		}
		w.endArray();
	}

	private static void writeValue(JsonWriter w, Object v) throws IOException {
		if (v == null) {
			w.nullValue();
		} else if (v instanceof Boolean b) {
			w.value(b.booleanValue());
		} else if (v instanceof Number n) {
			w.value(n.longValue());
		} else {
			w.value(v.toString());
		}
	}

	private static void nullable(JsonWriter w, String v) throws IOException {
		if (v == null) {
			w.nullValue();
		} else {
			w.value(v);
		}
	}

	private interface JsonBody {
		void write(JsonWriter w) throws IOException;
	}

	private static void writeJson(HttpServletResponse resp, JsonBody body) throws IOException {
		resp.setContentType("application/json");
		resp.setCharacterEncoding("UTF-8");
		try (JsonWriter w = new JsonWriter(new WriterAdapter(resp.getWriter()))) {
			body.write(w);
		}
	}

	private static Map<String, String> readObject(HttpServletRequest req) throws IOException {
		Map<String, String> m = new HashMap<>();
		try (JsonReader in = new JsonReader(new ReaderAdapter(req.getReader()))) {
			in.setLenient(true);
			in.beginObject();
			while (in.hasNext()) {
				String name = in.nextName();
				JsonToken t = in.peek();
				if (t == JsonToken.NULL) {
					in.nextNull();
				} else if (t == JsonToken.BOOLEAN) {
					m.put(name, Boolean.toString(in.nextBoolean()));
				} else if (t == JsonToken.NUMBER) {
					m.put(name, Long.toString(in.nextLong()));
				} else if (t == JsonToken.STRING) {
					m.put(name, in.nextString());
				} else {
					in.skipValue();
				}
			}
			in.endObject();
		}
		return m;
	}

	private static String path(HttpServletRequest req) {
		String p = req.getPathInfo();
		return p == null ? "/" : p;
	}

	private static long ruleId(String path) {
		// "/rules/<id>/state" or "/rules/<id>/stats"
		String s = path.substring("/rules/".length());
		return parseLong(s.substring(0, s.indexOf('/')), -1);
	}

	private static long scrubId(String path) {
		// "/scrub/<id>/state"
		String s = path.substring("/scrub/".length());
		return parseLong(s.substring(0, s.indexOf('/')), -1);
	}

	private static boolean isValidState(String s) {
		return DiagRule.DRAFT.equals(s) || DiagRule.SHADOW.equals(s)
				|| DiagRule.LIVE.equals(s) || DiagRule.DISABLED.equals(s);
	}

	private static String trimToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private static int intParam(HttpServletRequest req, String name, int def) {
		return parseInt(req.getParameter(name), def);
	}

	private static int parseInt(String s, int def) {
		try {
			return s == null ? def : Integer.parseInt(s.trim());
		} catch (NumberFormatException ex) {
			return def;
		}
	}

	private static long parseLong(String s, long def) {
		try {
			return s == null ? def : Long.parseLong(s.trim());
		} catch (NumberFormatException ex) {
			return def;
		}
	}

	private static long asLong(Object o) {
		return o instanceof Number n ? n.longValue() : 0;
	}

	private static String str(Object o) {
		return o == null ? "" : o.toString();
	}
}
