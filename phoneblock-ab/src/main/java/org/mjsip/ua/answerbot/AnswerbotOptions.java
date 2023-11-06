/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package org.mjsip.ua.answerbot;

import java.io.File;

import org.mjsip.ua.ServiceOptions;

/**
 * Configuration options of the {@link AnswerBot}.
 */
public interface AnswerbotOptions extends DialogOptions, ServiceOptions {

	/** 
	 * The directory where to store recordings, <code>null</code> if recording is diabled.
	 */
	String recordingDir();

	/**
	 * The directory to load the bot conversation WAV files from. 
	 */
	File conversationDir();

	/** 
	 * Whether to accept anonymous calls.
	 */
	boolean getAcceptAnonymous();

	/** 
	 * The minimum PhoneBlock votes to consider a call as SPAM and accept it. 
	 */
	int getMinVotes();

}
