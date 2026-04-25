/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.apache.ibatis.session.SqlSession;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.SpamReports;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.db.settings.AuthToken;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Counts calls from already-known SPAM numbers to drive tailored blocklist
 * selection.
 *
 * <p>A client that has just detected an incoming SPAM call — e.g. through the
 * {@link PrefixCheckServlet k-anonymity lookup} — reports the plaintext number
 * back so the server can increment {@code NUMBERS.CALLS} and refresh
 * {@code NUMBERS.LASTPING}. This is intentionally different from
 * {@link RateServlet /rate}: a rating is submitted at most once per user and
 * number, whereas a single number can generate many calls, and call activity
 * is what decides which numbers make it onto space-constrained blocklists
 * (e.g. Fritz!Box phonebooks).</p>
 *
 * <p>Abuse is kept at bay by a single per-user daily quota: a lazy-reset
 * counter on the {@code USERS} row caps how many reports a user may push
 * into the global counter per UTC day. It is implemented as one atomic
 * {@code UPDATE} (see {@link Users#tryConsumeCallReportQuota}) — no periodic
 * reset job, no aggregate query. Reports beyond the quota are still written
 * to the per-user call log ({@code CALLERS}) for the user's own activity
 * history, but do not move the global counter.</p>
 *
 * <p>Reports for numbers that are not in the {@code NUMBERS} table are
 * silently accepted but have no effect — we do not create new rows, since
 * that would let clients pollute the database with numbers that have never
 * received a proper SPAM rating.</p>
 */
@WebServlet(urlPatterns = ReportCallServlet.PATTERN)
public class ReportCallServlet extends HttpServlet {

	/** Servlet path — matches {@link #PATTERN} minus the wildcard tail. */
	public static final String PATH = "/api/report-call";

	public static final String PATTERN = PATH + "/*";

	/**
	 * Maximum number of call reports a user may push into the global counter
	 * per UTC day. Generous for typical spam exposure (even heavily targeted
	 * users rarely see more than a handful of spam calls per day) while
	 * leaving abuse from a single account clearly below the legitimate
	 * baseline of independent reports.
	 */
	public static final int DAILY_QUOTA = 20;

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		AuthToken auth = LoginFilter.getAuthorization(req);
		if (auth == null) {
			ServletUtil.sendAuthenticationRequest(resp);
			return;
		}

		String pathInfo = req.getPathInfo();
		if (pathInfo == null || pathInfo.length() < 2) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing phone number in path.");
			return;
		}

		String phone = NumberAnalyzer.normalizeNumber(pathInfo.substring(1));
		if (phone.isEmpty() || phone.contains("*")) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid phone number.");
			return;
		}

		String dialPrefix = ServletUtil.lookupDialPrefix(req);
		de.haumacher.phoneblock.app.api.model.PhoneNumer number = NumberAnalyzer.analyze(phone, dialPrefix);
		if (number == null) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid phone number.");
			return;
		}
		String phoneId = NumberAnalyzer.getPhoneId(number);

		long now = System.currentTimeMillis();
		int today = (int) LocalDate.ofInstant(Instant.ofEpochMilli(now), ZoneOffset.UTC).toEpochDay();

		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			Users users = session.getMapper(Users.class);

			long userId = auth.getUserId();

			if (users.tryConsumeCallReportQuota(userId, today, DAILY_QUOTA) == 1) {
				// recordCall is a no-op for phones that are not in NUMBERS, which is
				// exactly the "only count known SPAM numbers" semantic we want.
				reports.recordCall(phoneId, now);
			}

			// Maintain the per-user call log regardless of global-counter outcome.
			if (users.addCall(userId, phoneId, now) == 0) {
				users.insertCaller(userId, phoneId, now);
			}

			session.commit();
		}

		resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
	}

}
