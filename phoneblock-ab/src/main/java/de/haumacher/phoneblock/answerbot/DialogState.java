/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.answerbot;

/**
 * The state of the conversation.
 */
public enum DialogState {
	/**
	 * Say hello after accepting the call.
	 */
	HELLO,
	
	/**
	 * Waiting for the counterpart to introduce himself.
	 */
	WAITING_FOR_INTRO,
	
	/**
	 * Ask the counterpart who is calling.
	 */
	WHO_IS_CALLING,
	
	/**
	 * Waiting for a response from the counterpart.
	 */
	WAITING_FOR_ANSWER,
	
	/**
	 * The counterpart did not say anything, ask if he is still there.
	 */
	NO_ANSWER,
	
	/**
	 * Listening to some answer from the counterpart;
	 */
	LISTENING,
	
	/**
	 * Asking a silly question in response to something the counterpart said.
	 */
	ASKING;
	
	/**
	 * If no event happened, what to say next?
	 */
	public DialogState next() {
		switch (this) {
		case HELLO: return WAITING_FOR_INTRO;
		case WAITING_FOR_INTRO: return WHO_IS_CALLING;
		case WHO_IS_CALLING: return WAITING_FOR_INTRO;
		case WAITING_FOR_ANSWER: return NO_ANSWER;
		case NO_ANSWER: return WAITING_FOR_ANSWER;
		case LISTENING: return LISTENING;
		case ASKING: return WAITING_FOR_ANSWER;
		}
		throw new AssertionError("No such state.");
	}

	/**
	 * The name of the directory to lookup audio files to play in a certain state of the dialogue.
	 */
	public SpeechType getSpeechType() {
		switch (this) {
		case HELLO: return SpeechType.HELLO;
		case WAITING_FOR_INTRO: return SpeechType.WAITING;
		case WHO_IS_CALLING: return SpeechType.WHO_IS_CALLING;
		case WAITING_FOR_ANSWER: return SpeechType.WAITING;
		case NO_ANSWER: return SpeechType.STILL_THERE;
		case LISTENING: return SpeechType.WAITING;
		case ASKING: return SpeechType.QUESTION;
		}
		throw new AssertionError("No such state.");
	}
	
	/** 
	 * Whether the answer bot is (almost) silent in this state.
	 */
	boolean isSilent() {
		return this == WAITING_FOR_ANSWER || this == WAITING_FOR_INTRO || this == LISTENING;
	}
}