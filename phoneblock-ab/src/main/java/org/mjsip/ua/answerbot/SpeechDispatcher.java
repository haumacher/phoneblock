/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package org.mjsip.ua.answerbot;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.sound.sampled.UnsupportedAudioFileException;

import org.mjsip.sound.AudioFile;
import org.mjsip.ua.sound.SilenceListener;
import org.slf4j.LoggerFactory;

/**
 * Audio {@link InputStream} that reads from varying WAV sources depending on the counterpart being silent or not.
 */
final class SpeechDispatcher extends InputStream implements SilenceListener {
	
	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(SpeechDispatcher.class);
	
	/**
	 * The state of the conversation.
	 */
	private enum State {
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
		SpeechDispatcher.State next() {
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
		 * What audio to play in the this state?
		 */
		String audioType() {
			switch (this) {
			case HELLO: return "hello";
			case WAITING_FOR_INTRO: return "waiting";
			case WHO_IS_CALLING: return "who-is-calling";
			case WAITING_FOR_ANSWER: return "waiting";
			case NO_ANSWER: return "still-there";
			case LISTENING: return "waiting";
			case ASKING: return "question";
			}
			throw new AssertionError("No such state.");
		}
	}
	
	private SpeechDispatcher.State _state = State.HELLO;

	private volatile InputStream _current;

	private final Map<String, List<File>> _audioFragments;

	private Random _rnd;
	
	/** 
	 * Creates a {@link SpeechDispatcher}.
	 *
	 * @param audioFragments
	 */
	public SpeechDispatcher(Map<String, List<File>> audioFragments) {
		_audioFragments = audioFragments;
		_rnd = new Random();
		
		_current = openAudio();
	}

	@Override
	public int read() throws IOException {
		int result = _current.read();
		if (result >= 0) {
			return result;
		}
		
		switchState(_state.next());
		return _current.read();
	}

	private void switchState(SpeechDispatcher.State next) {
		_state  = next;
		
		InputStream old = _current;
		_current = openAudio();
		try {
			old.close();
		} catch (IOException ex) {
			LOG.warn("Failed to close audio stream.", ex);
		}
	}

	private InputStream openAudio() {
		try {
			String type = _state.audioType();
			List<File> list = _audioFragments.get(type);
			File file = list.get(_rnd.nextInt(list.size()));
			LOG.info("Playing: " + file.getPath());
			return AudioFile.getAudioFileInputStream(file.getAbsolutePath());
		} catch (IOException | UnsupportedAudioFileException ex) {
			throw new IOError(ex);
		}
	}

	@Override
	public void onSilenceStarted(long clock) {
		switch (_state) {
		case LISTENING: switchState(State.ASKING); break;
		default: // Ignore.
		}
	}

	@Override
	public void onSilenceEnded(long clock) {
		switch (_state) {
		case WAITING_FOR_INTRO: 
		case WAITING_FOR_ANSWER: 
			switchState(State.LISTENING); break;
		default: // Ignore.
		}
	}
}