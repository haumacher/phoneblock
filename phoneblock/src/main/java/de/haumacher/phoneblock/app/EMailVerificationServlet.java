/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;

import javax.mail.internet.AddressException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.mail.MailService;
import de.haumacher.phoneblock.mail.MailServiceStarter;

/**
 * {@link HttpServlet} that is invoked from the <code>signup.jsp</code> form.
 */
@WebServlet(urlPatterns = "/verify-email")
public class EMailVerificationServlet extends HttpServlet {

	private static final Logger LOG = LoggerFactory.getLogger(EMailVerificationServlet.class);

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String email = req.getParameter("email");
		if (email == null || email.trim().isEmpty()) {
			req.setAttribute("message", "Die E-Mail darf nicht leer sein.");
			req.getRequestDispatcher("/signup.jsp").forward(req, resp);
			return;
		}
		
		String code = DBService.getInstance().generateVerificationCode();
		
		// End verification code to email address.
		try {
			MailService mailService = MailServiceStarter.getInstance();
			if (mailService == null) {
				LOG.error("Mail service not active!");
				req.setAttribute("message", "Es kann aktuell keine E-Mail versendet werden, bitte probiere es später noch einmal.");
				req.getRequestDispatcher("/signup.jsp").forward(req, resp);
				return;
			}

			mailService.sendActivationMail(email, code);
		} catch (AddressException ex) {
			LOG.warn("Failed to send message: " + ex.getMessage());
			
			req.setAttribute("message", "Es konnte keine E-Mail geschickt werden: " + ex.getMessage());
			req.getRequestDispatcher("/signup.jsp").forward(req, resp);
			return;
		} catch (Exception ex) {
			LOG.error("Failed to send message", ex);
			
			req.setAttribute("message", "Es konnte keine E-Mail geschickt werden: " + ex.getMessage());
			req.getRequestDispatcher("/signup.jsp").forward(req, resp);
			return;
		}
		
		req.getSession().setAttribute("email", email);
		req.getSession().setAttribute("code", code);
		req.setAttribute("email", email);
		req.getRequestDispatcher("/signup-code.jsp").forward(req, resp);
	}

}
