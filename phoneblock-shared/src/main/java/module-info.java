/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */

/**
 * The PhoneBlock application.
 */
module de.haumacher.phoneblock.shared {
	exports de.haumacher.phoneblock.app.api.model;

	opens de.haumacher.phoneblock.app.api.model to org.mybatis;
	
	requires transitive de.haumacher.msgbuf;
}