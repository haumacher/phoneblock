package de.haumacher.phoneblock.db;

import de.haumacher.phoneblock.app.api.model.SearchInfo;

public class DBSearchInfo extends SearchInfo {
	
	public DBSearchInfo(String phone, int cnt, int total, long updated) {
		setPhone(phone)
		.setCount(cnt)
		.setTotal(total)
		.setLastSearch(updated);
	}

}
