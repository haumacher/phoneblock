/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */

/**
 * The PhoneBlock application.
 */
module phoneblock {
	exports de.haumacher.phoneblock.app;
	exports de.haumacher.phoneblock.app.api;

	exports de.haumacher.phoneblock.db to org.mybatis;
	exports de.haumacher.phoneblock.db.model to org.mybatis;
	
	requires com.google.api.client;
	requires com.google.auth;
	requires com.google.auth.oauth2;
	requires com.h2database;
	requires com.opencsv;
	requires ez.vcard;
	requires java.desktop;
	requires java.mail;
	requires java.management;
	requires java.naming;
	requires java.sql;
	requires java.xml;
	requires javax.servlet.api;
	requires de.haumacher.msgbuf;
	requires org.apache.httpcomponents.httpcore;
	requires org.jsoup;
	requires org.mybatis;
	requires org.slf4j;
}