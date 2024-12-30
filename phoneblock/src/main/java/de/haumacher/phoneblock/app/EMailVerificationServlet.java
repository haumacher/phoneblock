/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;

import jakarta.mail.internet.AddressException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.mail.MailService;
import de.haumacher.phoneblock.mail.MailServiceStarter;

/**
 * {@link HttpServlet} that is invoked from the <code>signup.jsp</code> form.
 */
@WebServlet(urlPatterns = {
	EMailVerificationServlet.VERIFY_WEB,
})
public class EMailVerificationServlet extends HttpServlet {

	public static final String VERIFY_WEB = "/verify-web";
	
	private static final Logger LOG = LoggerFactory.getLogger(EMailVerificationServlet.class);

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		LoginServlet.forwardLocation(req);
		
		String email = req.getParameter("email");
		if (email == null || email.trim().isEmpty()) {
			sendFailure(req, resp, "Die E-Mail darf nicht leer sein.");
			return;
		}
		
		String code = DBService.getInstance().generateVerificationCode();
		
		// End verification code to email address.
		try {
			MailService mailService = MailServiceStarter.getInstance();
			if (mailService == null) {
				LOG.error("Mail service not active!");
				sendFailure(req, resp, "Es kann aktuell keine E-Mail versendet werden, bitte probiere es später noch einmal.");
				return;
			}

			mailService.sendActivationMail(email, code);
		} catch (AddressException ex) {
			LOG.warn("Failed to send message: " + ex.getMessage());
			sendFailure(req, resp, "Es konnte keine E-Mail geschickt werden: " + ex.getMessage());
			return;
		} catch (Exception ex) {
			LOG.error("Failed to send message.", ex);
			sendFailure(req, resp, "Es konnte keine E-Mail geschickt werden: " + ex.getMessage());
			return;
		}
		
		req.getSession().setAttribute("email", email);
		req.getSession().setAttribute("code", code);
		req.setAttribute("email", email);
		req.getRequestDispatcher(successPage(req)).forward(req, resp);
	}

	private void sendFailure(HttpServletRequest req, HttpServletResponse resp, String message)
			throws ServletException, IOException {
		req.setAttribute("message", message);
		req.getRequestDispatcher(failurePage(req)).forward(req, resp);
	}

	private String failurePage(HttpServletRequest req) {
		switch (req.getServletPath()) {
		case VERIFY_WEB: 
		default:
			return "/signup.jsp";
		}
	}

	private String successPage(HttpServletRequest req) {
		switch (req.getServletPath()) {
			case VERIFY_WEB: 
			default:
				return "/signup-code.jsp"; 
		}
	}

}
