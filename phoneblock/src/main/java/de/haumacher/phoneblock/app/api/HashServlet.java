/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.io.IOException;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.api.model.PhoneNumer;
import de.haumacher.phoneblock.shared.PhoneHash;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * API servlet to compute a hash for a phone number (for testing and debugging only).
 * 
 * <p>
 * The resulting encoded SHA1 hash value is compatible with the the check API.
 * </p>
 */
@WebServlet(urlPatterns = HashServlet.PATH)
public class HashServlet extends HttpServlet {

	public static final String PATH = "/api/hash";
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String phoneText = req.getParameter("phone");
		
		if (phoneText == null) {
			ServletUtil.sendError(resp, "Phone number required.");
			return;
		}
		
		PhoneNumer phone = NumberAnalyzer.parsePhoneNumber(phoneText);
		if (phone == null) {
			ServletUtil.sendError(resp, "Invalid phone number.");
			return;
		}
		
		byte[] hash = NumberAnalyzer.getPhoneHash(phone);
		String encodedForm = PhoneHash.encodeHash(hash);
		
		ServletUtil.sendText(resp, encodedForm);
	}

}
