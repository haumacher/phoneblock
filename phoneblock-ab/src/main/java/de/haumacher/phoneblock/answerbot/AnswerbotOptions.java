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
	 * The directory where to store recordings, <code>null</code> if recording is disabled.
	 */
	String recordingDir();

	/**
	 * The directory to load the bot conversation WAV files from. 
	 */
	File conversationDir();

	/** 
	 * Media format configuration.
	 */
	MediaDesc[] getMediaDescs();

	/** 
	 * Whether a {@link #getTestPrefix()} has been configured.
	 */
	default boolean hasTestPrefix() {
		return getTestPrefix() != null;
	}

	/** 
	 * A phone number that certainly trigger the answer bot without checking the SPAM database. 
	 * 
	 * <p>
	 * Use this for testing only.
	 * </p>
	 */
	String getTestPrefix();

	/**
	 * The API key for accessing the phoneblock API.
	 */
	String getPhoneBlockAPIKey();

	boolean getSendRatings();
}
