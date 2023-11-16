/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.answerbot;

import org.mjsip.media.MediaSpec;

/**
 * Audio formats supported by the answer bot.
 */
public enum AudioType {
	PCMA_WB("PCMA", 16000, 1),
	PCMA("PCMA", 8000, 1),
	;

	private String _codec;
	private int _sampleRate;
	private int _channels;

	/** 
	 * Creates a {@link AudioType}.
	 */
	AudioType(String name, int sampleRate, int channels) {
		_codec = name;
		_sampleRate = sampleRate;
		_channels = channels;
	}

	/** 
	 * The name of the format compatible with {@link MediaSpec#getCodec()}. 
	 */
	public String codec() {
		return _codec;
	}

	/** 
	 * The sample rate of this audio format.
	 */
	public int sampleRate() {
		return _sampleRate;
	}
	
	/**
	 * The number of channels.
	 */
	public int channels() {
		return _channels;
	}

	@Override
	public String toString() {
		return dirName();
	}

	/** 
	 * Whether this {@link AudioType} is compatible with the given {@link MediaSpec}.
	 */
	public boolean matches(MediaSpec spec) {
		return codec().equals(spec.getCodec()) && sampleRate() == spec.getSampleRate() && channels() == spec.getChannels();
	}

	/** 
	 * Directory where audio files of this type are stored.
	 */
	public String dirName() {
		return name().replace('_', '-');
	}
}
