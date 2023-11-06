/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.answerbot;

/**
 * Options for controlling the bot conversation.
 */
public interface DialogOptions {

	/** 
	 * Size of the audio buffer in milliseconds. Silence detection operates on buffered samples of that duration.
	 */
	int bufferTime();

	/**
	 * Minimum time that silence must be detected to respond to the counterpart. 
	 */
	int minSilenceTime();

	/** 
	 * When recording, the time of silence to include to the recording before and after the counterpart's speach. 
	 */
	int paddingTime();

	/**
	 * Value in decibel relative to full scale (dbfs) that is considered silence. 
	 */
	double silenceDb();

}
