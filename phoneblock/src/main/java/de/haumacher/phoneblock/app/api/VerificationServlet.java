/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.io.IOException;

import de.haumacher.msgbuf.json.JsonReader;
import de.haumacher.msgbuf.server.io.ReaderAdapter;
import de.haumacher.phoneblock.app.api.model.RegistrationCompletion;
import de.haumacher.phoneblock.app.api.model.RegistrationResult;
import de.haumacher.phoneblock.app.api.model.SessionInfo;
import de.haumacher.phoneblock.app.render.DefaultController;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DB.EmailUser;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.shared.Language;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.mail.internet.AddressException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet completing a user registration.
 */
@WebServlet(urlPatterns = "/api/verify")
public class VerificationServlet extends HttpServlet {
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		RegistrationCompletion registration = RegistrationCompletion.readRegistrationCompletion(new JsonReader(new ReaderAdapter(req.getReader())));
		SessionInfo sessionInfo = RegisterServlet.removeSession(registration.getSession());
		if (sessionInfo == null) {
			ServletUtil.sendError(resp, "Session expired.");
			return;
		}
		
		if (!sessionInfo.getCode().equals(registration.getCode())) {
			ServletUtil.sendError(resp, "Invalid registration code.");
			return;
		}
		
		DB db = DBService.getInstance();
		String email = sessionInfo.getEmail();
		
		String login;
		String password;
		try {
			Language language = DefaultController.selectLanguage(req);
			String dialPrefix = DefaultController.selectDialPrefix(req, language);

			// A new account is created only if there is none for this address (#523).
			EmailUser account = db.getOrCreateEmailUser(email, email, language.tag, dialPrefix);
			login = account.login();
			if (account.created()) {
				password = account.password();
			} else {
				password = db.resetPassword(login);
			}
		} catch (AddressException e) {
			ServletUtil.sendError(resp, "Invalid e-mail address.");
			return;
		}
		
		
		ServletUtil.sendResult(req, resp, RegistrationResult.create().setSession(sessionInfo.getSession()).setLogin(login).setPassword(password));
	}
	
}
