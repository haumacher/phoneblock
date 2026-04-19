package de.haumacher.phoneblock.app;

import java.io.IOException;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBDongleApplication;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.DongleApplications;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.db.settings.AuthToken;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Handles submission of the beta-tester application form for the PhoneBlock dongle.
 */
@WebServlet(urlPatterns = DongleApplicationServlet.SUBMIT_PATH)
public class DongleApplicationServlet extends HttpServlet {

	private static final Logger LOG = LoggerFactory.getLogger(DongleApplicationServlet.class);

	public static final String FORM_PATH = "/dongle-apply";
	public static final String SUBMIT_PATH = "/dongle-apply-submit";

	private static final Set<String> PROVIDERS = Set.of(
		"TELEKOM", "VODAFONE", "1UND1", "O2", "DEUTSCHE_GLASFASER",
		"PYUR", "NETCOLOGNE", "SWISSCOM", "MAGENTA_AT", "OTHER");

	private static final Set<String> CONNECTION_TYPES = Set.of(
		"DSL", "CABLE", "FIBER", "MOBILE", "ISDN");

	private static final Set<String> ROUTER_KINDS = Set.of(
		"FRITZBOX", "SPEEDPORT", "PROVIDER_BOX", "OTHER");

	private static final Set<String> SPAM_FREQUENCIES = Set.of(
		"BELOW_1", "1_TO_3", "4_TO_10", "MORE");

	private static final Set<String> SKILL_LEVELS = Set.of(
		"BASIC", "ESP32", "VOIP_EXPERT");

	private static final Set<String> COUNTRIES = Set.of(
		"DE", "AT", "CH", "OTHER");

	private static final int MAX_TEXT = 128;
	private static final int MAX_ZIP = 16;
	private static final int MAX_NOTES = 4096;

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		AuthToken authorization = LoginFilter.getAuthorization(req);
		if (authorization == null || !authorization.isAccessLogin()) {
			LoginServlet.requestLogin(req, resp);
			return;
		}
		String userName = authorization.getUserName();

		DBDongleApplication app = new DBDongleApplication();
		app.setName(trimmed(req, "name", MAX_TEXT));
		app.setStreet(trimmed(req, "street", MAX_TEXT));
		app.setZip(trimmed(req, "zip", MAX_ZIP));
		app.setCity(trimmed(req, "city", MAX_TEXT));
		app.setCountry(selected(req, "country", COUNTRIES));
		app.setProvider(selected(req, "provider", PROVIDERS));
		app.setProviderOther(trimmed(req, "providerOther", MAX_TEXT));
		app.setConnectionType(selected(req, "connectionType", CONNECTION_TYPES));
		app.setRouterKind(selected(req, "routerKind", ROUTER_KINDS));
		app.setRouterModel(trimmed(req, "routerModel", MAX_TEXT));
		app.setSpamFrequency(selected(req, "spamFrequency", SPAM_FREQUENCIES));

		String skillLevel = req.getParameter("skillLevel");
		if (skillLevel != null && !skillLevel.isBlank() && SKILL_LEVELS.contains(skillLevel)) {
			app.setSkillLevel(skillLevel);
		}

		app.setAllowPublish(req.getParameter("allowPublish") != null);
		app.setNotes(trimmed(req, "notes", MAX_NOTES));
		app.setStatus("NEW");
		app.setCreated(System.currentTimeMillis());

		if (!isValid(app)) {
			LOG.warn("Rejected dongle application from {}: missing or invalid fields.", userName);
			resp.sendRedirect(req.getContextPath() + FORM_PATH + "?error=invalid");
			return;
		}

		if (!req.getParameterMap().containsKey("acceptFollowup")) {
			resp.sendRedirect(req.getContextPath() + FORM_PATH + "?error=commitment");
			return;
		}

		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			Long userId = users.getUserId(userName);
			if (userId == null) {
				LoginServlet.requestLogin(req, resp);
				return;
			}

			DongleApplications apps = session.getMapper(DongleApplications.class);
			if (apps.countByUser(userId) > 0) {
				LOG.info("Duplicate dongle application from {}, ignored.", userName);
				resp.sendRedirect(req.getContextPath() + FORM_PATH + "?ok=1");
				return;
			}

			app.setUserId(userId);
			apps.insert(app);
			session.commit();
		}

		LOG.info("Stored dongle application from {}.", userName);
		resp.sendRedirect(req.getContextPath() + FORM_PATH + "?ok=1");
	}

	private static String trimmed(HttpServletRequest req, String param, int max) {
		String value = req.getParameter(param);
		if (value == null) {
			return null;
		}
		String trimmed = value.strip();
		if (trimmed.isEmpty()) {
			return null;
		}
		if (trimmed.length() > max) {
			trimmed = trimmed.substring(0, max);
		}
		return trimmed;
	}

	private static String selected(HttpServletRequest req, String param, Set<String> allowed) {
		String value = req.getParameter(param);
		if (value == null || !allowed.contains(value)) {
			return null;
		}
		return value;
	}

	private static boolean isValid(DBDongleApplication app) {
		if (app.getName() == null || app.getStreet() == null || app.getZip() == null
				|| app.getCity() == null || app.getCountry() == null
				|| app.getProvider() == null || app.getConnectionType() == null
				|| app.getRouterKind() == null || app.getSpamFrequency() == null) {
			return false;
		}
		if ("OTHER".equals(app.getProvider())
				&& (app.getProviderOther() == null || app.getProviderOther().isBlank())) {
			return false;
		}
		return true;
	}
}
