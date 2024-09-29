/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.io.IOException;
import java.util.Arrays;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import de.haumacher.phoneblock.db.model.Rating;
import de.haumacher.phoneblock.db.model.Ratings;
import de.haumacher.phoneblock.util.ServletUtil;

/**
 * {@link HttpServlet} listing all possible ratings.
 */
@WebServlet(urlPatterns = "/api/ratings")
public class RatingsServlet extends HttpServlet {
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		ServletUtil.sendResult(req, resp, Ratings.create().setValues(Arrays.asList(Rating.values())));
	}

}
