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

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.msgbuf.json.JsonWriter;
import de.haumacher.msgbuf.server.io.WriterAdapter;
import de.haumacher.phoneblock.ab.proto.CreateAnswerbotResponse;
import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.db.settings.AnswerBotSip;
import de.haumacher.phoneblock.random.SecureRandomService;

/**
 * Servlet creating an answerbot.
 */
@WebServlet(urlPatterns = CreateABServlet.PATH)
public class CreateABServlet extends HttpServlet {

	private static final Logger LOG = LoggerFactory.getLogger(CreateABServlet.class);
	
	public static final String PATH = "/ab/create";
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String login = LoginFilter.getAuthenticatedUser(req.getSession(false));
		if (login == null) {
			LOG.warn("Rejected answerbot creation for unauthorized request.");
			sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Not logged in.");
			return;
		}
		
		DB db = DBService.getInstance();
		String userName = "ab-" + db.createId(16);
		String password = db.createPassword(16);
		
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			
			Long userId = users.getUserId(login);
			if (userId == null) {
				LOG.warn("Rejected answerbot creation for unknown user: " + login);
				sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "User not found.");
				return;
			}
			
			long now = System.currentTimeMillis();
			int ok = users.createAnswerBot(userId.longValue(), userName, password, now);
			if (ok < 1) {
				LOG.warn("Cannot create answerbot for: " + login);
				sendError(resp, HttpServletResponse.SC_CONFLICT, "Creation failed.");
				return;
			}
			
			session.commit();
		} catch (RuntimeException ex) {
			LOG.error("Answerbot creation failed for: " + login, ex);

			sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.getMessage());
			return;
		}
		
		resp.setContentType("application/json");
		resp.setCharacterEncoding("utf-8");
		CreateAnswerbotResponse.create().setUserName(userName).writeTo(new JsonWriter(new WriterAdapter(resp.getWriter())));
		
		LOG.warn("Created answerbot '" + userName + "' for: " + login);
	}

	protected void sendError(HttpServletResponse resp, int code, String message) throws IOException {
		resp.setContentType("text/plain");
		resp.setCharacterEncoding("utf-8");
		resp.setStatus(code);
		resp.getWriter().append(message);
	}
}
