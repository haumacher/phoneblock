/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */

/**
 * The PhoneBlock application.
 */
module de.haumacher.phoneblock {
	exports de.haumacher.phoneblock.app;
	exports de.haumacher.phoneblock.app.api;
	exports de.haumacher.phoneblock.crawl;
	exports de.haumacher.phoneblock.meta;
	exports de.haumacher.phoneblock.meta.plugins;
	
	uses de.haumacher.phoneblock.meta.plugins.AbstractMetaSearch;
	
	provides de.haumacher.phoneblock.meta.plugins.AbstractMetaSearch with 
		de.haumacher.phoneblock.meta.plugins.MetaAnruferBewertung, 
		de.haumacher.phoneblock.meta.plugins.MetaCleverdialer,
		de.haumacher.phoneblock.meta.plugins.MetaTellows,
		de.haumacher.phoneblock.meta.plugins.MetaWemgehoert,
		de.haumacher.phoneblock.meta.plugins.MetaWerruft;
	
	exports de.haumacher.phoneblock.db to org.mybatis;
	exports de.haumacher.phoneblock.db.model to org.mybatis;
	
	requires de.haumacher.phoneblock.shared;
	requires de.haumacher.phoneblock.ab;

	requires org.mjsip.sound;
	requires org.mjsip.sip;
	requires org.mjsip.ua;
	requires org.mjsip.util; 

	requires args4j;
	requires com.google.api.client;
	requires com.google.auth;
	requires com.google.auth.oauth2;
	requires com.h2database;
	requires com.opencsv;
	requires ez.vcard;
	requires java.base;
	requires java.desktop;
	requires java.mail;
	requires java.management;
	requires java.naming;
	requires java.sql;
	requires java.xml;
	requires java.net.http;
	requires javax.servlet.api;
	requires de.haumacher.msgbuf;
	requires org.apache.httpcomponents.httpcore;
	requires org.jsoup;
	requires org.mybatis;
	requires org.slf4j;
	requires pac4j.core;
	requires pac4j.oauth;
	requires pac4j.javaee;
	requires javaee.pac4j;
	requires org.apache.commons.lang3;
	requires api;
	requires service;
	requires org.tinylog.api;
}