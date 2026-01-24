/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */

/**
 * The PhoneBlock application.
 */
module de.haumacher.phoneblock.shared {
	exports de.haumacher.phoneblock.app.api.model;
	exports de.haumacher.phoneblock.shared;
	exports de.haumacher.phoneblock.shared.operations;
	exports de.haumacher.phoneblock.sync.client;
	exports de.haumacher.phoneblock.sync.storage;
	exports de.haumacher.phoneblock.sync.config;
	exports de.haumacher.phoneblock.sync.filter;
	exports de.haumacher.phoneblock.sync.cli;

	opens de.haumacher.phoneblock.app.api.model to org.mybatis;

	requires transitive de.haumacher.msgbuf;
	requires org.slf4j;
	requires java.net.http;
}