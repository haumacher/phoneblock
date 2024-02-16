/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.ab;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.msgbuf.json.JsonWriter;
import de.haumacher.msgbuf.server.io.WriterAdapter;
import de.haumacher.phoneblock.ab.proto.ListAnswerbotResponse;
import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.Users;

/**
 * Servlet listing all answerbots of a user.
 */
@WebServlet(urlPatterns = ListABServlet.PATH)
public class ListABServlet extends ABApiServlet {

	private static final Logger LOG = LoggerFactory.getLogger(ListABServlet.class);
	
	public static final String PATH = "/ab/list";
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String login = LoginFilter.getAuthenticatedUser(req.getSession(false));
		if (login == null) {
			LOG.warn("Not logged in.");
			sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Not logged in.");
			return;
		}
		
		List<DBAnswerbotInfo> bots;
		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			
			Long userId = users.getUserId(login);
			if (userId == null) {
				LOG.warn("User not found: " + login);
				sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "User not found.");
				return;
			}
			
			bots = users.getAnswerBots(userId.longValue());
		} catch (RuntimeException ex) {
			LOG.error("DB looku failed for: " + login, ex);

			sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.getMessage());
			return;
		}
		
		resp.setContentType("application/json");
		resp.setCharacterEncoding("utf-8");
		ListAnswerbotResponse.create().setBots(bots).writeTo(new JsonWriter(new WriterAdapter(resp.getWriter())));
	}

}
