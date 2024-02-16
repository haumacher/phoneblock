package de.haumacher.phoneblock.ab;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
