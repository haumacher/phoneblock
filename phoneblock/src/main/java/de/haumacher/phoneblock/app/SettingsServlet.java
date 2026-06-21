/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map.Entry;

import org.apache.ibatis.session.SqlSession;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.api.model.PhoneNumer;
import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.app.render.DefaultController;
import de.haumacher.phoneblock.app.render.TemplateRenderer;
import de.haumacher.phoneblock.app.render.controller.LoginController;
import de.haumacher.phoneblock.carddav.resource.AddressBookCache;
import de.haumacher.phoneblock.db.BlockList;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.DBUserSettings;
import de.haumacher.phoneblock.db.SpamReports;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.db.settings.AuthToken;
import de.haumacher.phoneblock.db.settings.UserSettings;
import de.haumacher.phoneblock.location.Countries;
import de.haumacher.phoneblock.util.I18N;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Servlet updating user settings.
 */
@WebServlet(urlPatterns = SettingsServlet.ACTION_PATH)
public class SettingsServlet extends HttpServlet {
	
	public static final String API_KEY_LABEL_PARAM = "apikey-label";
	public static final String KEY_ID_PREFIX = "key-";
	public static final String PATH = "/settings";
	public static final String ACTION_PATH = "/update-settings";

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		AuthToken authorization = LoginFilter.getAuthorization(req);
		if (authorization == null || !authorization.isAccessLogin()) {
			sendFailure(req, resp);
			return;
		}
		
		String userName = authorization.getUserName();
		
		String action = req.getParameter("action");
		if (action == null) {
			updateSettings(req, resp, userName);
		} else {
			switch (action) {
				case "lists":
					updateLists(req, resp, userName);
					return;

				case "deleteAPIKeys":
					deleteAPIKeys(req, resp, userName);
					return;

				case "createToken":
					createToken(req, resp, userName);
					return;

				case "renameAPIKey":
					renameAPIKey(req, resp, userName);
					return;

				case "renameDisplayName":
					renameDisplayName(req, resp, userName);
					return;

				default:
					forwardToSettings(req, resp, null);
			}
		}
	}

	private void createToken(HttpServletRequest req, HttpServletResponse resp, String userName) throws ServletException, IOException {
		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			Long userId = users.getUserId(userName);
			if (userId != null) {
				long now = System.currentTimeMillis();
				String tokenType = req.getParameter("token-type");
				String label = req.getParameter("token-label");

				// Validate and normalize token type - only accept well-defined values
				boolean isCardDav;
				if ("CARDDAV".equals(tokenType)) {
					isCardDav = true;
				} else {
					// Default to API for any other value (including null, invalid values, etc.)
					isCardDav = false;
				}

				// Generate default label if not provided
				if (label == null || label.isBlank()) {
					String typeLabel = isCardDav ? "CardDAV" : "API";
					label = typeLabel + " " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(now));
				}

				AuthToken token;
				if (isCardDav) {
					token = db.createCardDavToken(userName, now, req.getHeader("User-Agent"), label);
				} else {
					token = db.createAPIToken(userName, now, req.getHeader("User-Agent"), label);
				}
				session.commit();

				String location = req.getParameter(LoginServlet.LOCATION_ATTRIBUTE);
				if (location == null || location.isEmpty()) {
					location = "/show-api-key";
				}

				HttpSession httpSession = req.getSession();
				httpSession.setAttribute(DefaultController.API_KEY_ATTR, token);
				if (isCardDav) {
					// Remember at least until setup is complete.
					httpSession.setAttribute(DefaultController.CARD_DAV_TOKEN_ATTR, token);
				} 
				
				resp.sendRedirect(req.getContextPath() + location);
			} else {
				forwardToSettings(req, resp, "myAPIKeys");
			}
		}
	}

	private void deleteAPIKeys(HttpServletRequest req, HttpServletResponse resp, String userName) throws IOException {
		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			Long userId = users.getUserId(userName);
			if (userId != null) {
				for (Entry<String, String[]> entry : req.getParameterMap().entrySet()) {
					String key = entry.getKey();
					if (key.startsWith(KEY_ID_PREFIX)) {
						String id = key.substring(KEY_ID_PREFIX.length());

						users.deleteAuthToken(userId.longValue(), Long.parseLong(id));
					}
				}

				session.commit();
			}
		}
		forwardToSettings(req, resp, "myAPIKeys");
	}

	private void renameAPIKey(HttpServletRequest req, HttpServletResponse resp, String userName) throws IOException {
		String keyId = req.getParameter("keyId");
		String newLabel = req.getParameter("newLabel");

		if (keyId == null || newLabel == null || newLabel.isBlank()) {
			forwardToSettings(req, resp, "myAPIKeys");
			return;
		}

		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			Long userId = users.getUserId(userName);
			if (userId != null) {
				users.renameAuthToken(userId.longValue(), Long.parseLong(keyId), newLabel.trim());
				session.commit();
			}
		}
		forwardToSettings(req, resp, "myAPIKeys");
	}

	private void renameDisplayName(HttpServletRequest req, HttpServletResponse resp, String userName) throws IOException {
		String newDisplayName = req.getParameter("newDisplayName");

		if (newDisplayName == null || newDisplayName.isBlank()) {
			forwardToSettings(req, resp, null);
			return;
		}

		String trimmed = newDisplayName.trim();
		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			users.setDisplayName(userName, trimmed);
			session.commit();
		}

		UserSettings settings = LoginFilter.getUserSettings(req);
		if (settings != null) {
			settings.setDisplayName(trimmed);
			LoginFilter.refreshUserSettings(req, settings);
		}

		forwardToSettings(req, resp, null);
	}

	private void updateLists(HttpServletRequest req, HttpServletResponse resp, String userName) throws IOException {
		String redirect = req.getParameter("redirect");
		String addBlComment = nullIfBlank(req.getParameter("add-bl-comment"));
		Rating addBlRating = parseBlacklistRating(req.getParameter("add-bl-rating"));
		String addWlComment = nullIfBlank(req.getParameter("add-wl-comment"));

		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			Long userId = users.getUserId(userName);
			if (userId != null) {
				long owner = userId.longValue();

				DBUserSettings settings = users.getSettingsById(owner);
				String dialPrefix = settings.getDialPrefix();

				BlockList blocklist = session.getMapper(BlockList.class);
				SpamReports spamReports = session.getMapper(SpamReports.class);

				// Inline comment edit on an existing entry.
				String editPhone = req.getParameter("edit-comment-phone");
				if (editPhone != null && !editPhone.isEmpty()) {
					String editText = nullIfBlank(req.getParameter("edit-comment-text"));
					String phoneId = NumberAnalyzer.toId(editPhone, dialPrefix);
					if (phoneId != null) {
						updateOrCreateComment(spamReports, users, owner, phoneId, editText,
							"/whitelist".equals(redirect) ? Rating.A_LEGITIMATE : Rating.B_MISSED);
					}
				}

				for (Entry<String, String[]> entry : req.getParameterMap().entrySet()) {
					String key = entry.getKey();
					if (key.equals("add-wl")) {
						String addValues = entry.getValue()[0];
						for (String value : addValues.split("[,;]")) {
							String phoneId = NumberAnalyzer.toId(value, dialPrefix);
							if (phoneId == null) {
								continue;
							}

							long now = System.currentTimeMillis();
							PhoneNumer number = NumberAnalyzer.analyzePhoneID(phoneId);
							if (number != null) {
								db.addRating(userName, number, dialPrefix, Rating.A_LEGITIMATE, addWlComment, settings.getLang(), now);
							}
						}
					}
					else if (key.equals("add-bl")) {
						String addValues = entry.getValue()[0];
						for (String value : addValues.split("[,;]")) {
							String phone = NumberAnalyzer.toId(value, dialPrefix);
							if (phone == null) {
								continue;
							}

							long now = System.currentTimeMillis();
							PhoneNumer number = NumberAnalyzer.analyzePhoneID(phone);
							if (number != null) {
								db.addRating(userName, number, dialPrefix, addBlRating, addBlComment, settings.getLang(), now);
							}
						}
					}
					else if (key.equals("add-wc")) {
						// Personal wildcard-prefix block (#377): no community vote, honored only by
						// the user's own devices.
						String addValues = entry.getValue()[0];
						for (String value : addValues.split("[,;]")) {
							String prefix = NumberAnalyzer.toWildcardId(value, dialPrefix);
							if (prefix == null) {
								continue;
							}
							blocklist.removePersonalization(owner, prefix);
							// The new blocking wildcard subsumes the user's exact single-number
							// blocks and any narrower wildcard blocks under this prefix (#377);
							// allowed entries are kept as deliberate overrides.
							blocklist.removeBlocksWithPrefix(owner, prefix);
							blocklist.addWildcard(owner, prefix, true, System.currentTimeMillis());
						}
					}
					else if (key.startsWith("bl-")) {
						String rawPhone = key.substring("bl-".length());
						String phone = NumberAnalyzer.toId(rawPhone);
						if (phone == null) {
							// Safety: DB content may be inconsistent.
							blocklist.removePersonalization(owner, rawPhone);
						} else {
							blocklist.removePersonalization(owner, phone);
						}
					}

					else if (key.startsWith("wl-")) {
						String rawPhone = key.substring("wl-".length());
						String phone = NumberAnalyzer.toId(rawPhone);
						if (phone == null) {
							blocklist.removePersonalization(owner, rawPhone);
						} else {
							blocklist.removePersonalization(owner, phone);
						}
					}
					else if (key.startsWith("wc-")) {
						// Remove a wildcard-prefix block (#377); the form key carries the stored phone-ID prefix.
						blocklist.removeWildcard(owner, key.substring("wc-".length()));
					}
				}
			}

			session.commit();
		}

		AddressBookCache.getInstance().flushUserCache(userName);

		if ("/blacklist".equals(redirect) || "/whitelist".equals(redirect)) {
			resp.sendRedirect(req.getContextPath() + redirect);
			return;
		}

		forwardToSettings(req, resp, "blacklist");
	}

	/**
	 * Mirrors the upsert logic of {@code PersonalizationServlet#doPut}: edit the COMMENT column on an
	 * existing row, or insert a new comment with the list-default rating when the user had not rated
	 * the number yet.
	 */
	private static void updateOrCreateComment(SpamReports spamReports, Users users, long userId, String phoneId,
			String comment, Rating defaultRating) {
		int updated = spamReports.updateUserComment(userId, phoneId, comment);
		if (updated == 0) {
			DBUserSettings settings = users.getSettingsById(userId);
			spamReports.addComment(java.util.UUID.randomUUID().toString(), phoneId, defaultRating, comment,
				settings.getLang(), null, System.currentTimeMillis(), userId);
		}
	}

	private static Rating parseBlacklistRating(String name) {
		if (name == null || name.isEmpty()) {
			return Rating.B_MISSED;
		}
		try {
			Rating rating = Rating.valueOf(name);
			// A_LEGITIMATE would turn the entry into a whitelist exclusion, so coerce back to default.
			return rating == Rating.A_LEGITIMATE ? Rating.B_MISSED : rating;
		} catch (IllegalArgumentException ex) {
			return Rating.B_MISSED;
		}
	}

	private static String nullIfBlank(String s) {
		if (s == null) {
			return null;
		}
		String trimmed = s.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private void updateSettings(HttpServletRequest req, HttpServletResponse resp, String userName) throws IOException {
		int minVotes = Integer.parseInt(req.getParameter("minVotes"));
		int maxLength = Integer.parseInt(req.getParameter("maxLength"));
		boolean wildcards = req.getParameter("wildcards") != null;
		String myDialPrefixOrNull = Countries.normalizeDialPrefix(req.getParameter("myDialPrefix"), null);
		boolean nationalOnly = req.getParameter("nationalOnly") != null;
		
		if (minVotes <= 2) {
			minVotes = 2;
		}
		else if (minVotes <= 4) {
			minVotes = 4;
		}
		else if (minVotes <= 10) {
			minVotes = 10;
		}
		else {
			minVotes = 100;
		}
		
		if (maxLength <= 1000) {
			maxLength = 1000;
		} 
		else if (maxLength <= 2000) {
			maxLength = 2000;
		} 
		else if (maxLength <= 3000) {
			maxLength = 3000;
		}
		else if (maxLength <= 4000) {
			maxLength = 4000;
		} 
		else if (maxLength <= 5000) {
			maxLength = 5000;
		} 
		else {
			maxLength = 6000;
		}
		
		UserSettings settings = LoginFilter.getUserSettings(req);
		settings.setMinVotes(minVotes);
		settings.setMaxLength(maxLength);
		settings.setWildcards(wildcards);

		// Verify input.
		if (myDialPrefixOrNull != null) {
			settings.setDialPrefix(myDialPrefixOrNull);
		}

		settings.setNationalOnly(nationalOnly);

		DB db = DBService.getInstance();
		db.updateSettings(settings);

		// Refresh cached settings in session
		LoginFilter.refreshUserSettings(req, settings);

		// Ensure that a new block list is created, if the user is experimenting with the possible block list size.
		AddressBookCache.getInstance().flushUserCache(userName);
		
		forwardToSettings(req, resp, null);
	}

	private void forwardToSettings(HttpServletRequest req, HttpServletResponse resp, String fragment) throws IOException {
		resp.sendRedirect(req.getContextPath() + "/settings" + (fragment == null ? "" : "#" + fragment));
	}

	private void sendFailure(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		req.setAttribute("error", I18N.getMessage(req, "error.login.failed"));
		TemplateRenderer.getInstance(req).process(LoginController.LOGIN_PAGE, req, resp);
	}

}
