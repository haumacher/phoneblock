/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.callreport;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.haumacher.msgbuf.json.JsonReader;
import de.haumacher.msgbuf.json.JsonWriter;
import de.haumacher.msgbuf.server.io.ReaderAdapter;
import de.haumacher.msgbuf.server.io.WriterAdapter;
import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.callreport.model.CallReport;
import de.haumacher.phoneblock.callreport.model.ReportInfo;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;

/**
 * {@link HttpServlet} storing a call report from a user.
 */
@WebServlet(urlPatterns = CallReportServlet.URL_PATTERN)
public class CallReportServlet extends HttpServlet {

	public static final String URL_PATTERN = "/callreport";

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			DB db = DBService.getInstance();
			String userName = (String) req.getAttribute(LoginFilter.AUTHENTICATED_USER_ATTR);
			
			ReportInfo info = db.getCallReportInfo(userName);
			resp.setContentType("text/json");
			resp.setCharacterEncoding("utf-8");
			info.writeTo(new JsonWriter(new WriterAdapter(resp.getWriter())));
		} catch (Throwable ex) {
			ex.printStackTrace();
			throw ex;
		}
	}
	
	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			CallReport callReport = CallReport.readCallReport(new JsonReader(new ReaderAdapter(req.getReader())));
			String userName = (String) req.getAttribute(LoginFilter.AUTHENTICATED_USER_ATTR);
			
			DB db = DBService.getInstance();
			db.storeCallReport(userName, callReport);
		} catch (Throwable ex) {
			ex.printStackTrace();
			throw ex;
		}
	}
}
