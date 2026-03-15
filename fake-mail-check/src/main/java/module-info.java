/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */

/**
 * Disposable e-mail domain detection service.
 */
module de.haumacher.mailcheck {
	exports de.haumacher.mailcheck;
	exports de.haumacher.mailcheck.cli;
	exports de.haumacher.mailcheck.dns;
	exports de.haumacher.mailcheck.scraper;

	exports de.haumacher.mailcheck.db to org.mybatis;

	requires transitive de.haumacher.msgbuf;
	requires transitive org.mybatis;
	requires transitive jakarta.servlet;
	requires transitive jakarta.mail;
	requires transitive org.slf4j;
	requires java.naming;
	requires java.net.http;
	requires java.sql;
	requires com.h2database;
}
