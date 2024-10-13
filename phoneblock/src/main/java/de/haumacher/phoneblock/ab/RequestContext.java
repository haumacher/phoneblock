package de.haumacher.phoneblock.ab;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class RequestContext {

	public final HttpServletRequest req;
	public final HttpServletResponse resp;
	public final String login;

	public RequestContext(HttpServletRequest req, HttpServletResponse resp, String login) {
		this.req = req;
		this.resp = resp;
		this.login = login;
	}

}
