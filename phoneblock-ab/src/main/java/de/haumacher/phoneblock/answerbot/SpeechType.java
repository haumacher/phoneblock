/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.answerbot;

/**
 * Type of audio file to play during bot conversation.
 */
public enum SpeechType {
	
	HELLO, WAITING, WHO_IS_CALLING, STILL_THERE, QUESTION;
	
	@Override
	public String toString() {
		return getDirName();
	}

	/**
	 * The name of the directory where audio files for this speech type are found.
	 */
	public String getDirName() {
		return name().toLowerCase().replace('_', '-');
	}

	/** 
	 * Whether this type is (almost) silent audio.
	 */
	public boolean isSilent() {
		return this == WAITING;
	}
	
}
