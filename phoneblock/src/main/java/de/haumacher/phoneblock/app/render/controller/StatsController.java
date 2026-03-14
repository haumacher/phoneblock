/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.render.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.thymeleaf.context.WebContext;

import de.haumacher.phoneblock.app.render.DefaultController;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.util.I18N;
import jakarta.servlet.http.HttpServletRequest;

public class StatsController extends DefaultController {

	/**
	 * Template path for the stats page.
	 */
	public static final String STATS_PAGE = "/stats";

	private static final String[] DIAL_COLORS = {
		"rgb(255, 99, 132)",
		"rgb(54, 162, 235)",
		"rgb(255, 206, 86)",
		"rgb(153, 102, 255)",
		"rgb(255, 159, 64)",
		"rgb(75, 220, 100)",
		"rgb(201, 100, 80)",
		"rgb(100, 149, 237)",
		"rgb(220, 100, 200)",
		"rgb(180, 180, 80)",
		"rgb(128, 128, 128)",
	};

	@Override
	protected void fillContext(WebContext ctx, HttpServletRequest request) {
		super.fillContext(ctx, request);

		Object[] history = DBService.getInstance().getUserRegistrationHistory(30);

		@SuppressWarnings("unchecked")
		List<String> labels = (List<String>) history[0];
		@SuppressWarnings("unchecked")
		List<Integer> data = (List<Integer>) history[1];
		@SuppressWarnings("unchecked")
		LinkedHashMap<String, List<Integer>> perDialData = (LinkedHashMap<String, List<Integer>>) history[2];

		StringBuilder registrationLabels = new StringBuilder();
		registrationLabels.append('[');
		boolean first = true;
		for (String label : labels) {
			if (first) {
				first = false;
			} else {
				registrationLabels.append(',');
			}
			jsString(registrationLabels, label);
		}
		registrationLabels.append(']');

		String userCountLabel = I18N.getMessage(request, "page.stats.userCount");
		String otherLabel = I18N.getMessage(request, "page.stats.otherCountries");

		StringBuilder datasets = new StringBuilder();
		datasets.append('[');

		// Total dataset (teal, thicker line).
		datasets.append("{\"label\":");
		jsString(datasets, userCountLabel);
		datasets.append(",\"data\":");
		appendIntList(datasets, data);
		datasets.append(",\"fill\":false,\"borderColor\":\"rgb(75, 192, 192)\",\"borderWidth\":3,\"tension\":0.1}");

		// Per-dial datasets.
		int colorIndex = 0;
		for (Map.Entry<String, List<Integer>> entry : perDialData.entrySet()) {
			datasets.append(',');
			String key = entry.getKey();
			String dialLabel = "OTHER".equals(key) ? otherLabel : key.isEmpty() ? "?" : key;
			String color = DIAL_COLORS[colorIndex % DIAL_COLORS.length];

			datasets.append("{\"label\":");
			jsString(datasets, dialLabel);
			datasets.append(",\"data\":");
			appendIntList(datasets, entry.getValue());
			datasets.append(",\"fill\":false,\"borderColor\":\"").append(color).append("\",\"tension\":0.1}");

			colorIndex++;
		}

		datasets.append(']');

		request.setAttribute("registrationLabels", registrationLabels.toString());
		request.setAttribute("registrationDatasets", datasets.toString());
	}

	private static void appendIntList(StringBuilder sb, List<Integer> values) {
		sb.append('[');
		boolean first = true;
		for (Integer v : values) {
			if (first) {
				first = false;
			} else {
				sb.append(',');
			}
			sb.append(v.intValue());
		}
		sb.append(']');
	}

	private static void jsString(StringBuilder js, String txt) {
		js.append('"');
		js.append(txt.replace("\\", "\\\\").replace("\"", "\\\""));
		js.append('"');
	}

}
