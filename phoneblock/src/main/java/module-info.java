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
	exports de.haumacher.phoneblock.db.settings to org.mybatis;
	
	requires transitive de.haumacher.phoneblock.shared;
	requires transitive de.haumacher.phoneblock.ab;

	requires transitive org.mjsip.sound;
	requires transitive org.mjsip.sip;
	requires transitive org.mjsip.ua;
	requires transitive org.mjsip.util; 

	requires transitive org.dnsjava;
	requires transitive args4j;
	requires transitive com.google.api.client;
	requires transitive com.google.auth;
	requires transitive com.google.auth.oauth2;
	requires transitive com.h2database;
	requires transitive com.opencsv;
	requires transitive com.googlecode.ezvcard;
	requires transitive com.ip2location;
	requires java.base;
	requires java.desktop;
	requires java.management;
	requires java.naming;
	requires java.sql;
	requires java.xml;
	requires java.net.http;
	requires transitive jakarta.servlet;
	requires transitive thymeleaf;
	requires transitive jakarta.servlet.jsp;
	requires transitive jakarta.el;
	requires transitive jakarta.mail;
	requires transitive org.simplejavamail.java_utils_mail_dkim;
	requires transitive de.haumacher.msgbuf;
	requires transitive org.apache.httpcomponents.httpcore;
	requires transitive org.jsoup;
	requires transitive org.mybatis;
	requires transitive org.slf4j;
	requires transitive pac4j.core;
	requires transitive pac4j.jakartaee;
	requires transitive pac4j.oidc;
	requires transitive org.apache.commons.lang3;
	requires transitive api;
	requires transitive service;
	requires transitive org.tinylog.api;
	requires transitive jakartaee.pac4j;
	requires transitive utils.data.fetcher;
	requires transitive org.eclipse.angus.mail;
}