/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */

/**
 * The PhoneBlock answer bot.
 */
module de.haumacher.phoneblock.ab {
	requires de.haumacher.phoneblock.shared;

	requires transitive org.mjsip.sound;
	requires transitive org.mjsip.sip;
	requires transitive org.mjsip.ua;
	requires org.mjsip.util; 
	requires org.mjsip.net; 

	requires args4j;
	
	requires java.base;
	requires java.desktop;
	requires de.haumacher.msgbuf;
	requires org.slf4j;
	requires org.tinylog.api;
	
	opens de.haumacher.phoneblock.answerbot to args4j;
	
	exports de.haumacher.phoneblock.answerbot;
	exports de.haumacher.phoneblock.answerbot.tools;
	
	opens de.haumacher.phoneblock.answerbot.tools to args4j;
}