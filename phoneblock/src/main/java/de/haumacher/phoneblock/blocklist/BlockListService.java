/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.blocklist;

import java.util.List;

import javax.servlet.ServletContextListener;

import org.apache.ibatis.session.SqlSession;

import de.haumacher.phoneblock.analysis.NumberTree;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.SpamReports;

/**
 * Service providing fast access to the common {@link BlockList}.
 */
public class BlockListService implements ServletContextListener {
	
	private final DBService _dbService;

	/** 
	 * Creates a {@link BlockListService}.
	 */
	public BlockListService(DBService dbService) {
		_dbService = dbService;
	}
	
	public BlockList getBlockList() {
		BlockList blockList = new BlockList(100);
		try (SqlSession session = _dbService.db().openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			
			List<String> numbers = reports.getBlockList(4);
			NumberTree tree = new NumberTree();
			for (String phone : numbers) {
				tree.insert(phone);
			}
			tree.createBlockEntries(blockList);
		}
		return blockList;
	}
	

}
