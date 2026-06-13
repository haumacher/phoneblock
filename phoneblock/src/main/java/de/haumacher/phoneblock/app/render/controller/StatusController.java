package de.haumacher.phoneblock.app.render.controller;

import org.thymeleaf.context.WebContext;

import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.app.render.DefaultController;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.settings.UserSettings;
import jakarta.servlet.http.HttpServletRequest;

public class StatusController extends DefaultController {

	/**
	 * Template path for the status page.
	 */
	public static final String STATUS_PAGE = "/status";

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

		// Only the active blocklist size is shown — it counts through
		// NUMBERS_SPAM_EVIDENCE_IDX. The former reported / total-votes /
		// inactive figures each required a full-table scan and were dropped
		// from the status page.
		request.setAttribute("blocklistCount", DBService.getInstance().getActiveBlocklistCount(minVotes));
	}
}
