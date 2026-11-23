/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.exchange;

import java.io.IOException;
import java.util.List;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.msgbuf.json.JsonWriter;
import de.haumacher.msgbuf.server.io.WriterAdapter;
import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.ExternalReports;
import de.haumacher.phoneblock.db.settings.AuthToken;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Partner report exchange feed: <code>GET /api/partner/reports?from=&lt;ms&gt;&amp;to=&lt;ms&gt;</code>.
 *
 * <p>
 * Delivers the spam reports of PhoneBlock users (never imported data) in the shape of the partner's
 * own feed, see {@link PartnerExport}. Requires a bearer token with the partner capability
 * ({@code TOKENS.ACCESS_PARTNER}, set by the operator), checked by
 * {@link de.haumacher.phoneblock.app.BasicLoginFilter} and again here.
 * </p>
 *
 * <p>Configuration via JNDI or system properties:</p>
 * <ul>
 * <li><code>exchange/secret</code> - HMAC secret for the anonymized report and sender ids. Without
 * it, the feed answers 503.</li>
 * </ul>
 */
@WebServlet(urlPatterns = PartnerReportsServlet.PATH)
public class PartnerReportsServlet extends HttpServlet {

	private static final Logger LOG = LoggerFactory.getLogger(PartnerReportsServlet.class);

	/** The servlet path. */
	public static final String PATH = "/api/partner/reports";

	private byte[] _secret;

	@Override
	public void init() throws ServletException {
		super.init();
		_secret = PartnerExport.secretBytes(loadSecret());
		if (_secret == null) {
			LOG.info("No partner exchange secret configured (exchange/secret), partner feed disabled.");
		}
	}

	private static String loadSecret() {
		try {
			Context envCtx = (Context) new InitialContext().lookup("java:comp/env");
			return String.valueOf(envCtx.lookup("exchange/secret"));
		} catch (NamingException ex) {
			return System.getProperty("exchange.secret");
		}
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		AuthToken auth = LoginFilter.getAuthorization(req);
		if (auth == null) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_UNAUTHORIZED,
				"A bearer token with the partner capability is required.");
			return;
		}
		if (!auth.isAccessPartner()) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_FORBIDDEN,
				"The token does not have the partner capability.");
			return;
		}
		if (_secret == null) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
				"The partner report exchange is not configured.");
			return;
		}

		Long from;
		Long to;
		try {
			from = parseTime(req.getParameter("from"));
			to = parseTime(req.getParameter("to"));
		} catch (NumberFormatException ex) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST,
				"Parameters 'from' and 'to' must be timestamps in milliseconds.");
			return;
		}

		PartnerExport.Window window = PartnerExport.window(from, to, System.currentTimeMillis());
		List<PartnerReport> reports;
		try (SqlSession session = DBService.getInstance().openSession()) {
			reports = PartnerExport.export(session.getMapper(ExternalReports.class), _secret, window);
		}
		LOG.info("Partner feed for token {}: {} reports in [{}, {}).", auth.getId(), reports.size(),
			window.from(), window.to());

		resp.setStatus(HttpServletResponse.SC_OK);
		resp.setContentType("application/json");
		resp.setCharacterEncoding("UTF-8");
		try (JsonWriter json = new JsonWriter(new WriterAdapter(resp.getWriter()))) {
			PartnerReport.writeFeed(json, reports);
		}
	}

	private static Long parseTime(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return Long.valueOf(value.strip());
	}

}
