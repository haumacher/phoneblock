/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.answerbot;

import java.io.File;

import org.mjsip.media.MediaDesc;
import org.mjsip.ua.StaticOptions;

/**
 * Configuration options of the {@link AnswerBot}.
 */
public interface AnswerbotOptions extends DialogOptions, StaticOptions {

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

	/** 
	 * Media format configuration.
	 */
	MediaDesc[] getMediaDescs();

	/** 
	 * Whether a {@link #getTestNumber()} has been configured.
	 */
	default boolean hasTestNumber() {
		return getTestNumber() != null;
	}

	/** 
	 * A phone number that certainly trigger the answer bot without checking the SPAM database. 
	 * 
	 * <p>
	 * Use this for testing only.
	 * </p>
	 */
	String getTestNumber();

}
