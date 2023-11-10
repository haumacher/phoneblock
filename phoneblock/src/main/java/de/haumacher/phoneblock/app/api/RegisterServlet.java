/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.haumacher.msgbuf.json.JsonReader;
import de.haumacher.msgbuf.server.io.ReaderAdapter;
import de.haumacher.phoneblock.app.api.model.RegistrationChallenge;
import de.haumacher.phoneblock.app.api.model.RegistrationRequest;
import de.haumacher.phoneblock.app.api.model.SessionInfo;
import de.haumacher.phoneblock.captcha.Captcha;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.mail.MailService;
import de.haumacher.phoneblock.mail.MailServiceStarter;
import de.haumacher.phoneblock.random.SecureRandomService;
import de.haumacher.phoneblock.scheduler.SchedulerService;
import de.haumacher.phoneblock.util.ServletUtil;

/**
 * Servlet for starting a registration process.
 */
@WebServlet(urlPatterns = "/api/register")
public class RegisterServlet extends HttpServlet {
	
	private static final ConcurrentMap<String, SessionInfo> _sessions = new ConcurrentHashMap<>();

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String session = DBService.getInstance().createPassword(20);
		Captcha captcha = new Captcha(SecureRandomService.getInstance().getRnd());
		String captchaImage = "data:image/png;base64," + Base64.getEncoder().encodeToString(captcha.getPng());
		_sessions.put(session, SessionInfo.create().setCreated(System.currentTimeMillis()).setSession(session).setAnswer(captcha.getText()));
		SchedulerService.getInstance().executor().schedule(() -> _sessions.remove(session), 15, TimeUnit.MINUTES);
		
		ServletUtil.sendResult(req, resp, RegistrationChallenge.create().setSession(session).setCaptcha(captchaImage));
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		RegistrationRequest registration = RegistrationRequest.readRegistrationRequest(new JsonReader(new ReaderAdapter(req.getReader())));
		String sessionId = registration.getSession();
		SessionInfo sessionInfo = getSession(sessionId);
		if (sessionInfo == null) {
			ServletUtil.sendError(resp, "Session expired.");
			return;
		}
		
		if (!sessionInfo.getAnswer().equals(registration.getAnswer())) {
			removeSession(sessionId);
			ServletUtil.sendError(resp, "Answer not correct.");
			return;
		}
		
		if (!sessionInfo.getEmail().isEmpty()) {
			ServletUtil.sendError(resp, "Mail was already sent.");
			return;
		}
		
		String code = DBService.getInstance().generateVerificationCode();
		
		// Send verification code to email address.
		MailService mailService = MailServiceStarter.getInstance();
		if (mailService == null) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Mail service currently not available, please try again later.");
			return;
		}
		
		try {
			mailService.sendActivationMail(registration.getEmail(), code);
		} catch (AddressException ex) {
			removeSession(sessionId);
			ServletUtil.sendError(resp, "Invalid e-mail address: " + ex.getMessage());
			return;
		} catch (IOException | MessagingException ex) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Sending mail failed, please try again later: " + ex.getMessage());
		}

		sessionInfo.setEmail(registration.getEmail()).setCode(code);
		
		ServletUtil.sendMessage(resp, HttpServletResponse.SC_OK, "Mail sent.");
	}

	/** 
	 * Removes the session with the given ID from the store. 
	 */
	public static SessionInfo removeSession(String sessionId) {
		return _sessions.remove(sessionId);
	}

	/**
	 * The registration session. 
	 */
	public static SessionInfo getSession(String sessionId) {
		return _sessions.get(sessionId);
	}
	
}
