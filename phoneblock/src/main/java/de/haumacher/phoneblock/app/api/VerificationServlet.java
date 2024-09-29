/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import de.haumacher.msgbuf.json.JsonReader;
import de.haumacher.msgbuf.server.io.ReaderAdapter;
import de.haumacher.phoneblock.app.RegistrationServlet;
import de.haumacher.phoneblock.app.api.model.RegistrationCompletion;
import de.haumacher.phoneblock.app.api.model.RegistrationResult;
import de.haumacher.phoneblock.app.api.model.SessionInfo;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.util.ServletUtil;

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
		
		String extId = email.trim().toLowerCase();
		String login = db.getLogin(RegistrationServlet.IDENTIFIED_BY_EMAIL, extId);
		String password;
		if (login == null) {
			login = UUID.randomUUID().toString();
			password = db.createUser(RegistrationServlet.IDENTIFIED_BY_EMAIL, extId, login, email);
		} else {
			password = db.resetPassword(login);
		}
		
		db.setEmail(login, email);
		
		ServletUtil.sendResult(req, resp, RegistrationResult.create().setSession(sessionInfo.getSession()).setLogin(login).setPassword(password));
	}
	
}
