package de.haumacher.phoneblock.db;

import de.haumacher.phoneblock.db.model.NumberHistory;

public class DBNumberHistory extends NumberHistory {

	public DBNumberHistory(int rmin, int rmax, String phone, boolean active, int calls, int votes, int legitimate, int ping, int poll, int advertising, int gamble, int fraud, int searches) {
		setRMin(rmin)
		.setRMax(rmax)
		.setPhone(phone)
		.setActive(active)
		.setCalls(calls)
		.setVotes(votes)
		.setRatingLegitimate(legitimate)
		.setRatingPing(ping)
		.setRatingPoll(poll)
		.setRatingAdvertising(advertising)
		.setRatingGamble(gamble)
		.setRatingFraud(fraud)
		.setSearches(searches);
	}

}
