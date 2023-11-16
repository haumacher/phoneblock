/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */

/**
 * The PhoneBlock answer bot.
 */
module phoneblock.ab {
	requires phoneblock.shared;

	requires transitive org.mjsip.sound;
	requires org.mjsip.sip;
	requires org.mjsip.ua;
	requires org.mjsip.util; 
	requires org.mjsip.net; 

	requires args4j;
	
	requires java.base;
	requires java.desktop;
	requires de.haumacher.msgbuf;
	requires org.slf4j;
	requires org.tinylog.api;
	
	exports de.haumacher.phoneblock.answerbot;
	exports de.haumacher.phoneblock.answerbot.tools;
	
	opens de.haumacher.phoneblock.answerbot.tools to args4j;
}