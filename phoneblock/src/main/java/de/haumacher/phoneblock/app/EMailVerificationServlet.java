/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.mail.MailService;
import de.haumacher.phoneblock.mail.MailServiceStarter;

/**
 * {@link HttpServlet} that is invoked from the <code>signup.jsp</code> form.
 */
@WebServlet(urlPatterns = "/verify-email")
public class EMailVerificationServlet extends HttpServlet {

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
			mailService.sendActivationMail(email, code);
		} catch (Exception ex) {
			ex.printStackTrace();
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
