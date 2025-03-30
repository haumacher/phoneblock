package de.haumacher.phoneblock.app.render.controller;

import org.thymeleaf.context.WebContext;

import de.haumacher.phoneblock.app.render.DefaultController;
import jakarta.servlet.http.HttpServletRequest;

public class PhoneInfoController extends DefaultController {
	
	@Override
	protected void fillContext(WebContext ctx, HttpServletRequest request) {
		super.fillContext(ctx, request);
	}
}
