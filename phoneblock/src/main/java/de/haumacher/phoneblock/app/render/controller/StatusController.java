package de.haumacher.phoneblock.app.render.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.thymeleaf.context.WebContext;

import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.app.render.DefaultController;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.Statistics;
import de.haumacher.phoneblock.db.Status;
import de.haumacher.phoneblock.db.settings.UserSettings;
import jakarta.servlet.http.HttpServletRequest;

public class StatusController extends DefaultController {

	@Override
	protected void fillContext(WebContext ctx, HttpServletRequest request) {
		super.fillContext(ctx, request);

		long now = System.currentTimeMillis();
		UserSettings settings = LoginFilter.getUserSettings(request);
		int minVotes = settings != null ? settings.getMinVotes() : DB.MIN_VOTES;

		request.setAttribute("now", Long.valueOf(now));
		request.setAttribute("searches", DBService.getInstance().getTopSearches());
		request.setAttribute("reports", DBService.getInstance().getLatestSpamReports(now - 60 * 60 * 1000));
		request.setAttribute("newlyBlocked", DBService.getInstance().getLatestBlocklistEntries(minVotes));
		request.setAttribute("topSpammers", DBService.getInstance().getTopSpamReports(15));
		request.setAttribute("topSearches", DBService.getInstance().getTopSearchesOverall(15));

		Status status = DBService.getInstance().getStatus(minVotes);
		List<Statistics> statistic = status.getStatistics();
		int cnt = 0;
		Map<String, Integer> statistics = new HashMap<>();
		for (Statistics s : statistic) {
			statistics.put(s.getState(), s.getCnt());
			cnt += s.getCnt();
		}
		statistics.put("03-total", cnt);
		request.setAttribute("status", status);
		request.setAttribute("statistics", statistics);
	}
}
