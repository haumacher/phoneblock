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
import de.haumacher.phoneblock.db.DailyCount;
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
			datasets.append(",\"fill\":false,\"hidden\":true,\"borderColor\":\"").append(color).append("\",\"tension\":0.1}");

			colorIndex++;
		}

		datasets.append(']');

		int currentUserCount = DBService.getInstance().getUsers();
		int lastClosedDayCount = data.isEmpty() ? 0 : data.get(data.size() - 1);
		request.setAttribute("registrationLabels", registrationLabels.toString());
		request.setAttribute("registrationDatasets", datasets.toString());
		request.setAttribute("currentUserCount", currentUserCount);
		request.setAttribute("todayGrowth", currentUserCount - lastClosedDayCount);

		// Active installations chart.
		Object[] installations = DBService.getInstance().getActiveInstallationsHistory(30);

		@SuppressWarnings("unchecked")
		List<String> instLabels = (List<String>) installations[0];
		@SuppressWarnings("unchecked")
		LinkedHashMap<String, List<Integer>> perAgentData = (LinkedHashMap<String, List<Integer>>) installations[1];
		@SuppressWarnings("unchecked")
		List<Integer> answerbotData = (List<Integer>) installations[2];

		StringBuilder installationLabels = new StringBuilder();
		installationLabels.append('[');
		first = true;
		for (String label : instLabels) {
			if (first) {
				first = false;
			} else {
				installationLabels.append(',');
			}
			jsString(installationLabels, label);
		}
		installationLabels.append(']');

		String answerbotLabel = I18N.getMessage(request, "page.stats.answerbots");

		StringBuilder installationDatasets = new StringBuilder();
		installationDatasets.append('[');

		// Per-agent datasets.
		java.util.Set<String> defaultVisibleAgents = java.util.Set.of(
			"spamblocker", "fritzbox", "phoneblockmobile", "phonespamblocker");
		colorIndex = 0;
		boolean firstDataset = true;
		for (Map.Entry<String, List<Integer>> entry : perAgentData.entrySet()) {
			if (firstDataset) {
				firstDataset = false;
			} else {
				installationDatasets.append(',');
			}
			String key = entry.getKey();
			String agentLabel = "OTHER".equals(key) ? otherLabel : key.isEmpty() ? "?" : key;
			String color = DIAL_COLORS[colorIndex % DIAL_COLORS.length];
			boolean hidden = !defaultVisibleAgents.contains(key);

			installationDatasets.append("{\"label\":");
			jsString(installationDatasets, agentLabel);
			installationDatasets.append(",\"data\":");
			appendIntList(installationDatasets, entry.getValue());
			installationDatasets.append(",\"fill\":false");
			if (hidden) {
				installationDatasets.append(",\"hidden\":true");
			}
			installationDatasets.append(",\"borderColor\":\"").append(color).append("\",\"tension\":0.1}");

			colorIndex++;
		}

		// Answerbots (dashed dark cyan line).
		if (!firstDataset) {
			installationDatasets.append(',');
		}
		installationDatasets.append("{\"label\":");
		jsString(installationDatasets, answerbotLabel);
		installationDatasets.append(",\"data\":");
		appendIntList(installationDatasets, answerbotData);
		installationDatasets.append(",\"fill\":false,\"borderColor\":\"rgb(0, 139, 139)\",\"borderDash\":[5,5],\"tension\":0.1}");

		installationDatasets.append(']');

		request.setAttribute("installationLabels", installationLabels.toString());
		request.setAttribute("installationDatasets", installationDatasets.toString());

		// Blocked numbers by country pie chart.
		List<DailyCount> countryCounts = DBService.getInstance().getBlockedNumbersByCountry();

		StringBuilder pieLabels = new StringBuilder();
		StringBuilder pieData = new StringBuilder();
		StringBuilder pieColors = new StringBuilder();
		pieLabels.append('[');
		pieData.append('[');
		pieColors.append('[');
		int pieIndex = 0;
		for (DailyCount dc : countryCounts) {
			if (pieIndex > 0) {
				pieLabels.append(',');
				pieData.append(',');
				pieColors.append(',');
			}
			String dial = dc.getDial() == null || dc.getDial().isEmpty() ? "?" : dc.getDial();
			jsString(pieLabels, dial);
			pieData.append(dc.getCnt());
			jsString(pieColors, DIAL_COLORS[pieIndex % DIAL_COLORS.length]);
			pieIndex++;
		}
		pieLabels.append(']');
		pieData.append(']');
		pieColors.append(']');

		request.setAttribute("pieLabels", pieLabels.toString());
		request.setAttribute("pieData", pieData.toString());
		request.setAttribute("pieColors", pieColors.toString());
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
