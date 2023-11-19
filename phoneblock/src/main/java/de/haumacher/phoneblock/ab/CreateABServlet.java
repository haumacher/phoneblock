/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.ab;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.haumacher.phoneblock.db.settings.AnswerBotSip;

/**
 * Servlet creating an answerbot.
 */
@WebServlet(urlPatterns = CreateABServlet.PATH)
public class CreateABServlet extends HttpServlet {

	public static final String PATH = "/ab/create";
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String username = (String) req.getSession().getAttribute("ab-username");
		String passwd = (String) req.getSession().getAttribute("ab-passwd");
		String hostname = req.getParameter("hostname");
		
		AnswerBotSip bot = AnswerBotSip.create().setUserName(username).setPasswd(passwd).setHost(hostname);
		
		SipService.getInstance().register(bot);
	}
}
