/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.callreport;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.msgbuf.json.JsonReader;
import de.haumacher.msgbuf.json.JsonWriter;
import de.haumacher.msgbuf.server.io.ReaderAdapter;
import de.haumacher.msgbuf.server.io.WriterAdapter;
import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.callreport.model.CallReport;
import de.haumacher.phoneblock.callreport.model.ReportInfo;
import de.haumacher.phoneblock.carddav.CardDavServlet;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@link HttpServlet} storing a call report from a user.
 */
@WebServlet(urlPatterns = CallReportServlet.URL_PATTERN)
public class CallReportServlet extends HttpServlet {

	private static final Logger LOG = LoggerFactory.getLogger(CardDavServlet.class);

	public static final String URL_PATTERN = "/callreport";

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String userName = LoginFilter.getAuthenticatedUser(req);
		if (userName == null) {
			ServletUtil.sendAuthenticationRequest(resp);
			return;
		}

		try {
			DB db = DBService.getInstance();
			
			ReportInfo info = db.getCallReportInfo(userName);
			resp.setContentType("text/json");
			resp.setCharacterEncoding("utf-8");
			info.writeTo(new JsonWriter(new WriterAdapter(resp.getWriter())));
		} catch (Throwable ex) {
			LOG.error("Failed to retrieve call report info.", ex);
			throw ex;
		}
	}
	
	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String userName = LoginFilter.getAuthenticatedUser(req);
		if (userName == null) {
			ServletUtil.sendAuthenticationRequest(resp);
			return;
		}

		try {
			CallReport callReport = CallReport.readCallReport(new JsonReader(new ReaderAdapter(req.getReader())));
			
			DB db = DBService.getInstance();
			db.storeCallReport(userName, callReport);
		} catch (Throwable ex) {
			LOG.error("Failed to store call report.", ex);
			throw ex;
		}
	}
}
