/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.Users;

/**
 * Servlet to record a customers dynamic IP address over DynIP protocol.
 */
@WebServlet(urlPatterns = "/api/dynip")
public class DynIpServlet extends HttpServlet {

	private static final Logger LOG = LoggerFactory.getLogger(DynIpServlet.class);
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String user = req.getParameter("user");
		String passwd = req.getParameter("passwd");
		
		String userName = DBService.getInstance().login(user, passwd);
		if (userName == null) {
			LOG.warn("Failed to authenticate: " + parameters(req));
			return;
		}
		
		String ip = req.getParameter("ip");
		InetAddress address = InetAddress.getByName(ip);
		if (address.isAnyLocalAddress() || address.isLinkLocalAddress() || address.isLoopbackAddress()
				|| address.isMCGlobal() || address.isMCLinkLocal() || address.isMCNodeLocal() || address.isMCOrgLocal()
				|| address.isMCSiteLocal() || address.isMulticastAddress() || address.isSiteLocalAddress()) {
			LOG.warn("Provided invalid address: " + parameters(req));
			return;
		}
		
		try (SqlSession session = DBService.getInstance().openSession()) {
			Users users = session.getMapper(Users.class);
			
			Long userId = users.getUserId(userName);
			users.setIP(userId, ip);
			session.commit();
			
			LOG.info("Updated IP address for: " + userName);
		}
		
		// Mark as successfully processed.
		resp.setContentType("text/plain");
		resp.setCharacterEncoding("utf-8");
		resp.getWriter().write("OK");
	}

	private String parameters(HttpServletRequest req) {
		return req.getParameterMap().entrySet().stream().map(e -> e.getKey() + "=" + Arrays.asList(e.getValue())).collect(Collectors.joining(", "));
	}
}
