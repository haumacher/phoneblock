/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import de.haumacher.phoneblock.db.model.SearchInfo;

/**
 * TODO
 */
public class DBSearchInfo extends SearchInfo {
	/** 
	 * Creates a {@link DBSearchInfo}.
	 */
	public DBSearchInfo(int revision, String phone, int searchesToday, int searchesTotal, long lastSearch) {
		setRevision(revision).setPhone(phone).setCount(searchesToday).setTotal(searchesTotal).setLastSearch(lastSearch);
	}
}
