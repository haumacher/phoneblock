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
		
		SipService.getInstance().register(username, passwd, hostname);
		
	}
}
