/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.haumacher.msgbuf.json.JsonReader;
import de.haumacher.msgbuf.server.io.ReaderAdapter;
import de.haumacher.phoneblock.app.api.model.RegistrationCompletion;
import de.haumacher.phoneblock.app.api.model.RegistrationResult;
import de.haumacher.phoneblock.app.api.model.SessionInfo;
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
		
		String password = DBService.getInstance().createUser(sessionInfo.getEmail());
		
		ServletUtil.sendResult(resp, RegistrationResult.create().setSession(sessionInfo.getSession()).setEmail(sessionInfo.getEmail()).setPassword(password));
	}
	
}
