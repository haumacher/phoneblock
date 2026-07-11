/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
			} else if (path.startsWith("/rules/")) {
				ruleDetail(resp, mapper, path.substring("/rules/".length()));
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
		// "/rules/<id>/state"
		String s = path.substring("/rules/".length());
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
