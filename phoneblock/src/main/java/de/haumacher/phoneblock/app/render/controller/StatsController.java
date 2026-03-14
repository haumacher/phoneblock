/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.render.controller;

import java.util.List;

import org.thymeleaf.context.WebContext;

import de.haumacher.phoneblock.app.render.DefaultController;
import de.haumacher.phoneblock.db.DBService;
import jakarta.servlet.http.HttpServletRequest;

public class StatsController extends DefaultController {

	/**
	 * Template path for the stats page.
	 */
	public static final String STATS_PAGE = "/stats";

	@Override
	protected void fillContext(WebContext ctx, HttpServletRequest request) {
		super.fillContext(ctx, request);

		Object[] history = DBService.getInstance().getUserRegistrationHistory(30);

		@SuppressWarnings("unchecked")
		List<String> labels = (List<String>) history[0];
		@SuppressWarnings("unchecked")
		List<Integer> data = (List<Integer>) history[1];

		StringBuilder registrationLabels = new StringBuilder();
		StringBuilder registrationData = new StringBuilder();

		registrationLabels.append('[');
		registrationData.append('[');
		boolean first = true;
		for (int i = 0; i < labels.size(); i++) {
			if (first) {
				first = false;
			} else {
				registrationLabels.append(',');
				registrationData.append(',');
			}
			jsString(registrationLabels, labels.get(i));
			registrationData.append(data.get(i));
		}
		registrationLabels.append(']');
		registrationData.append(']');

		request.setAttribute("registrationLabels", registrationLabels.toString());
		request.setAttribute("registrationData", registrationData.toString());
	}

	private static void jsString(StringBuilder js, String txt) {
		js.append('"');
		js.append(txt.replace("\"", "\\\""));
		js.append('"');
	}

}
