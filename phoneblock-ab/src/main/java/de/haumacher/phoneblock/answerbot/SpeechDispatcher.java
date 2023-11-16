/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.answerbot;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
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
	
	private DialogState _state = DialogState.HELLO;

	private volatile InputStream _current;

	private final Map<SpeechType, List<File>> _audioFragments;

	private Random _rnd;
	
	/** 
	 * Creates a {@link SpeechDispatcher}.
	 */
	public SpeechDispatcher(Map<SpeechType, List<File>> audioFragments) {
		_audioFragments = audioFragments;
		_rnd = new Random();
		_current = openAudio();
		checkFragments();
	}

	private void checkFragments() {
		for (SpeechType type : SpeechType.values()) {
			if (_audioFragments.getOrDefault(type, Collections.emptyList()).isEmpty()) {
				LOG.error("No media for dialogue state '" + type + "' found.");
			}
		}
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

	private void switchState(DialogState next) {
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
			List<File> list = _audioFragments.getOrDefault(_state, Collections.emptyList());
			if (list.isEmpty()) {
				throw new IllegalStateException("No media for dialogue state: " + _state);
			}
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
		case LISTENING: switchState(DialogState.ASKING); break;
		default: // Ignore.
		}
	}

	@Override
	public void onSilenceEnded(long clock) {
		switch (_state) {
		case WAITING_FOR_INTRO: 
		case WAITING_FOR_ANSWER: 
			switchState(DialogState.LISTENING); break;
		default: // Ignore.
		}
	}
}