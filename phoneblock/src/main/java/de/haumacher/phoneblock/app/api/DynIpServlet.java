/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

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
import de.haumacher.phoneblock.db.settings.AnswerBotDynDns;

/**
 * Servlet to record a customers dynamic IP address over DynIP protocol.
 */
@WebServlet(urlPatterns = "/api/dynip")
public class DynIpServlet extends HttpServlet {

	private static final Logger LOG = LoggerFactory.getLogger(DynIpServlet.class);
	
	private static final long ONE_DAY = 1000 * 60 * 60 * 24;
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			tryGet(req, resp);
		} catch (Throwable ex) {
			LOG.error("Faild to process request.", ex);
			throw ex;
		}
	}
	
	protected void tryGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String user = req.getParameter("user");
		if (user == null) {
			resp.sendError(HttpServletResponse.SC_FORBIDDEN, "No user name provided.");
			return;
		}
		user = user.trim();
		
		String passwd = req.getParameter("passwd");
		if (passwd == null) {
			LOG.warn("DynDNS update without password: " + user);
			resp.sendError(HttpServletResponse.SC_FORBIDDEN, "No password provided.");
			return;
		}
		passwd = passwd.trim();
		
		try (SqlSession session = DBService.getInstance().openSession()) {
			Users users = session.getMapper(Users.class);
			
			AnswerBotDynDns settings = users.getDynDns(user);
			if (settings == null || !settings.getDynDnsPasswd().equals(passwd)) {
				LOG.warn("DynDNS update with wrong password (" + passwd.length()  + " characters): " + user);
				resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Wrong user name or password.");
				return;
			}
			
			long now = System.currentTimeMillis();
			AnswerBotDynDns update = AnswerBotDynDns.create().setDyndnsUser(user);
			
			processAddress(update, req.getParameter("ip"));
			processAddress(update, req.getParameter("ip4"));
			processAddress(update, req.getParameter("ip6"));
			
			if (changed(settings.getIpv4(), update.getIpv4()) || changed(settings.getIpv6(), update.getIpv6()) || now - settings.getUpdated() > ONE_DAY) {
				users.updateDynDny(settings.getUserId(), update.getIpv4(), update.getIpv6(), now);
				
				session.commit();
				LOG.info("Updated IP address for: " + user);
			} else {
				LOG.info("IP address of user unchanged: " + user);
			}
			
			if (update.getIpv4().isEmpty() && update.getIpv6().isEmpty()) {
				LOG.warn("No addresses provided: " + user);
				resp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "No valid addresses provided.");
				return;
			}
		}
		
		// Mark as successfully processed.
		resp.setContentType("text/plain");
		resp.setCharacterEncoding("utf-8");
		resp.getWriter().write("OK");
	}

	private static boolean changed(String a, String b) {
		return ! a.equals(b);
	}

	private void processAddress(AnswerBotDynDns update, String ip) {
		if (ip == null) {
			return;
		}
		
		ip = ip.trim();

		if (ip.isEmpty()) {
			return;
		}
		
		InetAddress address;
		try {
			address = InetAddress.getByName(ip);
		} catch (UnknownHostException ex) {
			LOG.warn("Cannot resolve host: (" + ip + ") for user '" + update.getDyndnsUser() + "': " + ex.getMessage());
			return;
		}
		if (address.isAnyLocalAddress() || address.isLinkLocalAddress() || address.isLoopbackAddress()
				|| address.isSiteLocalAddress() || address.isMulticastAddress()) {
			
			LOG.warn("Provided invalid address (" + ip + "): " + update.getDyndnsUser());
		} else {
			if (address instanceof Inet4Address) {
				update.setIpv4(ip);
			} else if (address instanceof Inet6Address) {
				update.setIpv6(ip);
			}
		}
	}
}
