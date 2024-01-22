/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.ab;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.msgbuf.data.DataObject;
import de.haumacher.msgbuf.json.JsonReader;
import de.haumacher.msgbuf.json.JsonWriter;
import de.haumacher.msgbuf.server.io.ReaderAdapter;
import de.haumacher.msgbuf.server.io.WriterAdapter;
import de.haumacher.phoneblock.ab.proto.CheckAnswerBot;
import de.haumacher.phoneblock.ab.proto.CheckDynDns;
import de.haumacher.phoneblock.ab.proto.CreateAnswerBot;
import de.haumacher.phoneblock.ab.proto.CreateAnswerbotResponse;
import de.haumacher.phoneblock.ab.proto.DeleteAnswerBot;
import de.haumacher.phoneblock.ab.proto.DisableAnswerBot;
import de.haumacher.phoneblock.ab.proto.EnableAnswerBot;
import de.haumacher.phoneblock.ab.proto.EnterHostName;
import de.haumacher.phoneblock.ab.proto.SetupDynDns;
import de.haumacher.phoneblock.ab.proto.SetupDynDnsResponse;
import de.haumacher.phoneblock.ab.proto.SetupRequest;
import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.Users;

/**
 * Servlet creating an answerbot.
 */
@WebServlet(urlPatterns = CreateABServlet.PATH)
public class CreateABServlet extends ABApiServlet implements SetupRequest.Visitor<Void, RequestContext, IOException> {

	private static final Logger LOG = LoggerFactory.getLogger(CreateABServlet.class);
	
	public static final String PATH = "/ab/setup";
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String login = LoginFilter.getAuthenticatedUser(req.getSession(false));
		if (login == null) {
			LOG.warn("Rejected answerbot creation for unauthorized request.");
			sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Not logged in.");
			return;
		}
		
		SetupRequest cmd = SetupRequest.readSetupRequest(new JsonReader(new ReaderAdapter(req.getReader())));
		if (cmd == null) {
			LOG.warn("Command not understood.");
			sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Command not understood.");
			return;
		}
		
		LOG.info("Received answerbot command from '" + login + "': " + cmd);
		try {
			cmd.visit(this, new RequestContext(req, resp));
		} catch (Throwable ex) {
			LOG.error("Request failed for: " + login, ex);
		}
	}

	@Override
	public Void visit(CreateAnswerBot self, RequestContext context) throws IOException {
		HttpServletResponse resp = context.resp;
		HttpServletRequest req = context.req;
		String login = LoginFilter.getAuthenticatedUser(req.getSession(false));
		
		DB db = DBService.getInstance();
		String userName = "ab-" + db.createId(16);
		String password = db.createPassword(16);
		
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			
			Long userId = users.getUserId(login);
			if (userId == null) {
				LOG.warn("Rejected answerbot creation for unknown user: " + login);
				sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "User not found.");
				return null;
			}
			
			long now = System.currentTimeMillis();
			
            long id;
			try (PreparedStatement statement = session.getConnection().prepareStatement(
					"insert into ANSWERBOT_SIP (USERID, USERNAME, PASSWD, CREATED, UPDATED) values (?, ?, ?, ?, ?)", 
					Statement.RETURN_GENERATED_KEYS)) {
		        statement.setLong(1, userId.longValue());
		        statement.setString(2, userName);
		        statement.setString(3, password);
		        statement.setLong(4, now);
		        statement.setLong(5, now);

		        int affectedRows = statement.executeUpdate();
				if (affectedRows < 1) {
					LOG.warn("Cannot create answerbot for: " + login);
					sendError(resp, HttpServletResponse.SC_CONFLICT, "Creation failed.");
					return null;
				}
				
		        try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
		            if (!generatedKeys.next()) {
		            	LOG.warn("Cannot retrieve answerbot ID for: " + login);
		            	sendError(resp, HttpServletResponse.SC_CONFLICT, "No ID retrieved.");
		            	return null;
		            }
		            
		            id = generatedKeys.getLong(1);
		        }			
				session.commit();
		        
				sendResult(resp, CreateAnswerbotResponse.create()
					.setId(id)
					.setUserName(userName)
					.setPassword(password));
				LOG.info("Created answerbot '" + userName + "' for: " + login);
	        } catch (SQLException ex) {
				LOG.warn("Answerbot creation failed for : " + login);
				sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Creation failed: " + ex.getMessage());
				return null;
			}
		}
		return null;
	}

	@Override
	public Void visit(EnterHostName self, RequestContext context) throws IOException {
		HttpServletResponse resp = context.resp;
		HttpServletRequest req = context.req;
		String login = LoginFilter.getAuthenticatedUser(req.getSession(false));
		
		String hostName = self.getHostName();
		
		boolean ok = checkHostName(hostName);
		if (!ok) {
			LOG.warn("Invalid host name '" + hostName + "' for: " + login);
			sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid host name.");
			return null;
		}
		
		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			
			users.answerbotDeleteDynDns(self.getId());
			users.answerbotEnterHostName(self.getId(), hostName);
			
			session.commit();
		}
		
		sendOk(resp);
		LOG.info("Set up host name '" + hostName + "' for: " + login);
		return null;
	}

	@Override
	public Void visit(SetupDynDns self, RequestContext context) throws IOException {
		HttpServletResponse resp = context.resp;
		HttpServletRequest req = context.req;
		String login = LoginFilter.getAuthenticatedUser(req.getSession(false));

		DB db = DBService.getInstance();
		
		long id = self.getId();
		String dynDnsUser = "fb-" + db.createId(16);
		String dynDnsPassword = db.createPassword(16);
		
		long now = System.currentTimeMillis();
		
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			
			DBAnswerbotInfo answerBot = users.getAnswerBot(id);
			if (answerBot == null) {
				LOG.warn("Answerbot '" + id + "' not found for: " + login);
				sendError(resp, HttpServletResponse.SC_CONFLICT, "Answer bot not found: " + id);
				return null;
			}
			
			users.answerbotDeleteDynDns(id);
			users.setupDynDns(id, answerBot.getUserId(), now, dynDnsUser, dynDnsPassword);
			
			session.commit();
		}
		sendResult(resp, SetupDynDnsResponse.create()
			.setId(id)
			.setDyndnsUser(dynDnsUser)
			.setDyndnsPassword(dynDnsPassword));

		LOG.info("Set up DynDNS user '" + dynDnsUser + "' for: " + login);
		return null;
	}

	@Override
	public Void visit(CheckDynDns self, RequestContext context) throws IOException {
		HttpServletResponse resp = context.resp;
		HttpServletRequest req = context.req;
		String login = LoginFilter.getAuthenticatedUser(req.getSession(false));

		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);

			long id = self.getId();
			DBAnswerbotInfo answerBot = users.getAnswerBot(id);
			if (answerBot == null) {
				LOG.warn("Answerbot '" + id + "' not found for: " + login);
				sendError(resp, HttpServletResponse.SC_CONFLICT, "Answer bot not found: " + id);
				return null;
			}

			String ipv4 = answerBot.getIp4();
			String ipv6 = answerBot.getIp6();
			if (isEmpty(ipv4) && isEmpty(ipv6)) {
				sendError(resp, HttpServletResponse.SC_CONFLICT, "No domain name set.");
				return null;
			}
		}
		
		sendOk(resp);
		LOG.info("DynDNS checked successfully for: " + login);
		return null;
	}

	private boolean checkHostName(String host) {
		try {
			InetAddress[] addresses = InetAddress.getAllByName(host);
			for (InetAddress address : addresses) {
				if (address.isAnyLocalAddress() || address.isLinkLocalAddress() || address.isLoopbackAddress()
						|| address.isMulticastAddress() || address.isSiteLocalAddress()) {
					// None of the potential addresses must be a local address. Otherwise "fritz.box" would be accepted.
					return false;
				}
			}
		} catch (UnknownHostException e) {
			LOG.warn("Cannot resolve host name of answerbot: " + host);
			return false;
		}
		return true;
	}

	@Override
	public Void visit(EnableAnswerBot self, RequestContext context) throws IOException {
		HttpServletResponse resp = context.resp;
		HttpServletRequest req = context.req;
		String login = LoginFilter.getAuthenticatedUser(req.getSession(false));

		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			
			long id = self.getId();
			DBAnswerbotInfo bot = users.getAnswerBot(id);
			if (isEmpty(bot.getHost()) && isEmpty(bot.getIp4()) && isEmpty(bot.getIp6())) {
				LOG.warn("No connection information for answer bot '" + id + "' for: " + login);
				sendError(resp, HttpServletResponse.SC_CONFLICT, "No connection information.");
			}
			
			SipService sipService = SipService.getInstance();
			String userName = bot.getUserName();
			sipService.disableAnwserBot(userName);
			sipService.enableAnwserBot(userName, true);
		}
		
		sendOk(resp);
		LOG.info("Answerbot enabled for: " + login);
		return null;
	}

	@Override
	public Void visit(DisableAnswerBot self, RequestContext context) throws IOException {
		HttpServletResponse resp = context.resp;
		HttpServletRequest req = context.req;
		String login = LoginFilter.getAuthenticatedUser(req.getSession(false));

		long id = self.getId();

		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);

			DBAnswerbotInfo bot = users.getAnswerBot(id);
			if (bot == null) {
				sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Answerbot '" + id + "' not found.");
				return null;
			}
			SipService.getInstance().disableAnwserBot(bot.getUserName());
		}
		
		sendOk(resp);
		LOG.info("Answerbot '" + id + "' disabled for: " + login);
		return null;
	}
	
	@Override
	public Void visit(DeleteAnswerBot self, RequestContext context) throws IOException {
		HttpServletResponse resp = context.resp;
		HttpServletRequest req = context.req;
		String login = LoginFilter.getAuthenticatedUser(req.getSession(false));

		long id = self.getId();
		
		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);

			DBAnswerbotInfo bot = users.getAnswerBot(id);
			if (bot == null) {
				sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Answerbot '" + id + "' not found.");
				return null;
			}
			SipService.getInstance().disableAnwserBot(bot.getUserName());
			
			users.answerbotDelete(id);
			session.commit();
		}
		
		sendOk(resp);
		LOG.info("Answerbot '" + id + "' deleted for: " + login);
		return null;
	}

	@Override
	public Void visit(CheckAnswerBot self, RequestContext context) throws IOException {
		HttpServletResponse resp = context.resp;
		HttpServletRequest req = context.req;
		String login = LoginFilter.getAuthenticatedUser(req.getSession(false));

		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);

			long id = self.getId();
			DBAnswerbotInfo bot = users.getAnswerBot(id);
			
			if (!bot.isRegistered()) {
				String registerMsg = bot.getRegisterMsg();
				if (registerMsg == null) {
					registerMsg = "No response.";
				}
				sendError(resp, HttpServletResponse.SC_CONFLICT, registerMsg);
				LOG.warn("Answerbot '" + id + "' not registered for: " + login + " (" + registerMsg + ")");
				return null;
			}
		}
		
		sendOk(resp);
		LOG.info("Answerbot checked sucessfully for: " + login);
		return null;
	}

	private void sendOk(HttpServletResponse resp) throws IOException {
		resp.setContentType("text/plain");
		resp.setCharacterEncoding("utf-8");
		resp.getWriter().append("OK");
	}

	private void sendResult(HttpServletResponse resp, DataObject result) throws IOException {
		resp.setContentType("application/json");
		resp.setCharacterEncoding("utf-8");
		result.writeTo(new JsonWriter(new WriterAdapter(resp.getWriter())));
	}

	private static boolean isEmpty(String str) {
		return str == null || str.isBlank();
	}

}
