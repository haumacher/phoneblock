/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.app.render.DefaultController;
import de.haumacher.phoneblock.app.render.TemplateRenderer;
import de.haumacher.phoneblock.app.render.controller.ShowCredentialsController;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.shared.Language;
import de.haumacher.phoneblock.util.I18N;
import de.haumacher.phoneblock.util.UserAgentType;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static de.haumacher.phoneblock.app.CreateAuthTokenServlet.MOBILE_LOGIN;
import static de.haumacher.phoneblock.app.EMailVerificationServlet.MOBILE_CODE_PAGE;
import static de.haumacher.phoneblock.app.EMailVerificationServlet.SIGNUP_CODE_PAGE;
import static de.haumacher.phoneblock.app.render.controller.ShowCredentialsController.SHOW_CREDENTIALS_PAGE;

/**
 * {@link HttpServlet} that completes the e-mail verification flow during user registration or account linking.
 *
 * <p>
 * This servlet is invoked when the user submits the verification code from either the web registration page
 * ({@link EMailVerificationServlet#SIGNUP_CODE_PAGE}) or the mobile account linking page
 * ({@link EMailVerificationServlet#MOBILE_CODE_PAGE}). It validates the code against the one stored in the
 * session and, if correct, creates or retrieves the user account.
 * </p>
 *
 * <h2>Registration Flow</h2>
 * <ol>
 *   <li>User enters e-mail address on signup page</li>
 *   <li>System sends verification code via e-mail and stores it in session</li>
 *   <li>User enters code on verification page, which posts to this servlet</li>
 *   <li>This servlet validates the code and creates/retrieves the user account</li>
 *   <li>User is redirected based on flow type (web or mobile)</li>
 * </ol>
 *
 * <h2>Web Registration ({@link #REGISTER_WEB})</h2>
 * <p>
 * For new accounts, redirects to {@link ShowCredentialsController#SHOW_CREDENTIALS_PAGE} to display the
 * generated password for Fritz!Box integration. For existing accounts, redirects directly to the settings page.
 * </p>
 *
 * <h2>Mobile Account Linking ({@link #REGISTER_MOBILE})</h2>
 * <p>
 * Redirects to {@link CreateAuthTokenServlet#MOBILE_LOGIN} to continue the token creation flow. The next step
 * is handled by {@link CreateAuthTokenServlet} which generates an API access token for the mobile app.
 * </p>
 *
 * @see EMailVerificationServlet for the servlet that sends the verification code
 * @see CreateAuthTokenServlet for the mobile token creation flow
 * @see LoginServlet for the traditional username/password login flow
 */
@WebServlet(urlPatterns = {
	RegistrationServlet.REGISTER_WEB,
	RegistrationServlet.REGISTER_MOBILE,
})
public class RegistrationServlet extends HttpServlet {

	/**
	 * Request attribute set, if registration fails.
	 */
	public static final String REGISTER_ERROR_ATTR = "errorMessage";

	/**
	 * URL pattern for web-based registration.
	 *
	 * <p>
	 * This endpoint is used when a user registers via the web interface at
	 * {@link EMailVerificationServlet#SIGNUP_CODE_PAGE}. After successful verification, new users are redirected
	 * to the credentials page, while existing users go to settings.
	 * </p>
	 */
	public static final String REGISTER_WEB = "/register-web";

	/**
	 * URL pattern for mobile app account linking.
	 *
	 * <p>
	 * This endpoint is used when linking a mobile app to a PhoneBlock account via
	 * {@link EMailVerificationServlet#MOBILE_CODE_PAGE}. After successful verification, the flow continues to
	 * {@link CreateAuthTokenServlet#MOBILE_LOGIN} where an API token is created.
	 * </p>
	 *
	 * @see CreateAuthTokenServlet#CREATE_TOKEN
	 */
	public static final String REGISTER_MOBILE = "/register-mobile";

	private static final Logger LOG = LoggerFactory.getLogger(RegistrationServlet.class);

	/**
	 * Processes the verification code submission and completes user registration.
	 *
	 * <p>
	 * Validates the submitted {@code code} parameter against the code stored in the session. If valid, either
	 * creates a new user account (if the e-mail is not yet registered) or retrieves the existing account.
	 * </p>
	 *
	 * <h3>Required Session Attributes</h3>
	 * <ul>
	 *   <li>{@code code} - The expected verification code (set by {@code EMailVerificationServlet})</li>
	 *   <li>{@code email} - The e-mail address being verified</li>
	 * </ul>
	 *
	 * <h3>Request Parameters</h3>
	 * <ul>
	 *   <li>{@code code} - The verification code entered by the user</li>
	 *   <li>{@code rememberMe} (optional) - If {@code "true"}, creates a persistent login cookie</li>
	 * </ul>
	 *
	 * <h3>Error Handling</h3>
	 * <p>
	 * On failure, re-displays the code entry page with an error message stored in the
	 * {@link #REGISTER_ERROR_ATTR} request attribute.
	 * </p>
	 *
	 * @param req the HTTP request containing the verification code
	 * @param resp the HTTP response for redirects or error page rendering
	 * @throws ServletException if request processing fails
	 * @throws IOException if an I/O error occurs during response writing
	 */
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		LoginServlet.forwardLocation(req);

		Object expectedCode = req.getSession().getAttribute("code");
		if (expectedCode == null) {
			sendError(req, resp, I18N.getMessage(req, "error.registration.code-expired"));
			return;
		}

		String code = req.getParameter("code");
		if (code == null || code.trim().isEmpty() || !code.equals(expectedCode)) {
			sendError(req, resp, I18N.getMessage(req, "error.registration.code-mismatch"));
			return;
		}

		String email = (String) req.getSession().getAttribute("email");
		
		String login;
		try {
			String passwd;
			
			DB db = DBService.getInstance();
			login = db.getEmailLogin(email);
			if (login == null) {
				login = UUID.randomUUID().toString();
				
				String displayName = DB.toDisplayName(email);
				
				Language language = DefaultController.selectLanguage(req);
				String dialPrefix = DefaultController.selectDialPrefix(req, language);
				
				passwd = db.createUser(login, displayName, language.tag, dialPrefix);
				db.setEmail(login, email);
			} else {
				// No longer known.
				passwd = null;
			}
			
			String rememberValue = req.getParameter(LoginServlet.REMEMBER_ME_PARAM);
			LoginServlet.processRememberMe(req, resp, db, rememberValue, login);

			startSetup(req, resp, login, passwd);
		} catch (Exception ex) {
			LOG.error("Failed to create user: " + email, ex);

			sendError(req, resp, I18N.getMessage(req, "error.registration.account-creation-failed", ex.getMessage()));
			return;
		}
	}

	/**
	 * Completes the login process and redirects the user to the appropriate next step.
	 *
	 * <p>
	 * This method establishes the user session and determines the redirect target based on the
	 * registration type (web or mobile) and whether this is a new or existing account.
	 * </p>
	 *
	 * <h3>Redirect Logic</h3>
	 * <ul>
	 *   <li><b>Mobile registration:</b> Redirects to {@link CreateAuthTokenServlet#MOBILE_LOGIN} for
	 *       API token creation</li>
	 *   <li><b>Web registration (new account):</b> Redirects to
	 *       {@link ShowCredentialsController#SHOW_CREDENTIALS_PAGE} to display the generated password,
	 *       then to a device-appropriate setup page</li>
	 *   <li><b>Web registration (existing account):</b> Redirects directly to the settings page or
	 *       the originally requested location</li>
	 * </ul>
	 *
	 * @param req the HTTP request, used to determine servlet path and detect user agent
	 * @param resp the HTTP response for sending redirects
	 * @param login the user's login identifier (UUID)
	 * @param passwd the generated password for new accounts, or {@code null} for existing accounts
	 * @throws ServletException if request processing fails
	 * @throws IOException if an I/O error occurs during redirect
	 */
	public static void startSetup(HttpServletRequest req, HttpServletResponse resp,
			String login, String passwd) throws ServletException, IOException {

		DB db = DBService.getInstance();
		LoginFilter.setSessionUser(req, db.createMasterLoginToken(login));

		switch (req.getServletPath()) {
		case REGISTER_MOBILE:
			// Redirect to location which contains appId and tokenLabel parameters
			String mobileLocation = LoginServlet.location(req, MOBILE_LOGIN);
			resp.sendRedirect(req.getContextPath() + mobileLocation);
			break;
		case REGISTER_WEB:
		default:
			if (passwd != null) {
				// For new accounts, redirect to credentials page first.
				req.getSession().setAttribute(DefaultController.PASSWORD_ATTR, passwd);

				// Use the answer bot setup page as default target location after login. 
				String targetLocation = LoginServlet.location(req, switch (UserAgentType.detect(req)) {
					case ANDROID -> "/setup-android";
					case IPHONE -> "/setup-iphone";
					default -> "/anrufbeantworter";
				});
				String credentialsUrl = req.getContextPath() + SHOW_CREDENTIALS_PAGE + LoginServlet.locationParam(targetLocation, true);
				resp.sendRedirect(credentialsUrl);
			} else {
				// For existing accounts, go directly to target location
				String location = LoginServlet.location(req, SettingsServlet.PATH);
				resp.sendRedirect(req.getContextPath() + location);
			}
			break;
		}
	}

	/**
	 * Renders the appropriate error page with the given error message.
	 *
	 * @param req the HTTP request
	 * @param resp the HTTP response
	 * @param message the localized error message to display
	 * @throws ServletException if template processing fails
	 * @throws IOException if an I/O error occurs during response writing
	 */
	private void sendError(HttpServletRequest req, HttpServletResponse resp, String message) throws ServletException, IOException {
		req.setAttribute(REGISTER_ERROR_ATTR, message);
		TemplateRenderer.getInstance(req).process(errorPage(req), req, resp);
	}

	/**
	 * Determines the error page template based on the registration type.
	 *
	 * @param req the HTTP request containing the servlet path
	 * @return {@link EMailVerificationServlet#MOBILE_CODE_PAGE} for mobile registration,
	 *         {@link EMailVerificationServlet#SIGNUP_CODE_PAGE} for web registration
	 */
	private String errorPage(HttpServletRequest req) {
		switch (req.getServletPath()) {
		case REGISTER_MOBILE:
			return MOBILE_CODE_PAGE;
		case REGISTER_WEB:
		default:
			return SIGNUP_CODE_PAGE;
		}
	}
}
