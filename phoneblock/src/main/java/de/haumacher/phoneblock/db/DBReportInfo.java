/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import de.haumacher.phoneblock.callreport.model.ReportInfo;

/**
 * {@link ReportInfo} that can be fetched from the {@link DB}.
 */
public class DBReportInfo extends ReportInfo {
	
	public DBReportInfo(String timestamp, String lastid) {
		super();
		setTimestamp(timestamp);
		setLastid(lastid);
	}

}
