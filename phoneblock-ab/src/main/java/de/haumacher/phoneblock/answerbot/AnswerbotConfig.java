/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.answerbot;

import java.io.File;

import org.kohsuke.args4j.Option;
import org.mjsip.config.YesNoHandler;
import org.mjsip.media.FlowSpec.Direction;
import org.mjsip.media.MediaDesc;
import org.mjsip.sip.config.MediaDescHandler;

/**
 * Configuration options for the {@link AnswerBot}.
 */
public class AnswerbotConfig implements AnswerbotOptions {

	@Option(name = "--media", handler = MediaDescHandler.class)
	private MediaDesc[] _mediaDescs=new MediaDesc[]{};
	
	@Option(name = "--conversation", usage = "Directory with WAV files used for streaming during automatic conversation.")
	private File _conversationDir = new File("./conversation");
	
	@Option(name = "--recodings", usage = "Directory where to store recordings to, 'none' to disable recording.")
	private String _recordingDir = ".";
	
	@Option(name = "--test-prefix", usage = "Phone number prefix that triggers the answer bot to respond (for testing). " + 
			"A local number typically starts with '*', therefore this is prefix can be used to allow calling the answer bot locally.")
	private String _testPrefix = "*";
	
	/**
	 * The configured recoding directory.
	 */
	public String getRecordingDir() {
		return _recordingDir == null || _recordingDir.isBlank() || "none".equals(_recordingDir) ? null : _recordingDir;
	}
	
	@Option(name = "--buffer-time", usage = "Time in milliseconds to buffer the audio stream for silence detection.")
	private int _bufferTime = 20;
	
	@Option(name = "--min-silence-time", usage = "Number of milliseconds silence must be detected before responding.")
	private int _minSilenceTime = 500;
	
	@Option(name = "--padding-time", usage = "When recording, the number of milliseconds of silence to record before and after the conterpart's speach.")
	private int _paddingTime = 100;
	
	@Option(name = "--silence-db", usage = "The maximum value in decibel relative to full scale (dbfs) for an audio segment to be classified as silence.")
	private double silenceDb = -30;

	@Option(name = "--accept-anonymous", handler = YesNoHandler.class, usage = "Whether to let PhoneBlock accept anonymous calls. This is not recommended. Better configure a separate answering machine in you router to handle anonymous calls.")
	private boolean _acceptAnonymous = false;

	@Option(name = "--min-votes", usage = "The minimum number of PhoneBlock votes for a number to be consideres SPAM.")
	private int _minVotes = 4;
	
	@Override
	public int bufferTime() {
		return _bufferTime;
	}
	
	/**
	 * @see #bufferTime()
	 */
	public void setBufferTime(int bufferTime) {
		_bufferTime = bufferTime;
	}
	
	@Override
	public int minSilenceTime() {
		return _minSilenceTime;
	}
	
	/**
	 * @see #minSilenceTime()
	 */
	public void setMinSilenceTime(int minSilenceTime) {
		_minSilenceTime = minSilenceTime;
	}
	
	@Override
	public int paddingTime() {
		return _paddingTime;
	}
	
	/**
	 * @see #paddingTime()
	 */
	public void setPaddingTime(int paddingTime) {
		_paddingTime = paddingTime;
	}
	
	@Override
	public double silenceDb() {
		return silenceDb;
	}
	
	/**
	 * @see #silenceDb()
	 */
	public void setSilenceDb(double silenceDb) {
		this.silenceDb = silenceDb;
	}

	@Override
	public String recordingDir() {
		return _recordingDir;
	}
	
	/**
	 * @see #recordingDir()
	 */
	public void setRecordingDir(String recordingDir) {
		_recordingDir = recordingDir;
	}
	
	@Override
	public File conversationDir() {
		return _conversationDir;
	}
	
	/**
	 * @see #conversationDir()
	 */
	public void setConversationDir(File conversationDir) {
		_conversationDir = conversationDir;
	}
	
	@Override
	public boolean getAcceptAnonymous() {
		return _acceptAnonymous;
	}
	
	/**
	 * @see #getAcceptAnonymous()
	 */
	public void setAcceptAnonymous(boolean acceptAnonymous) {
		_acceptAnonymous = acceptAnonymous;
	}
	
	@Override
	public int getMinVotes() {
		return _minVotes;
	}
	
	/**
	 * @see #getMinVotes()
	 */
	public void setMinVotes(int minVotes) {
		_minVotes = minVotes;
	}
	
	@Override
	public String getTestNumber() {
		return _testPrefix;
	}
	
	/**
	 * @see #getTestNumber()
	 */
	public void setTestPrefix(String testPrefix) {
		_testPrefix = testPrefix;
	}

	@Override
	public MediaDesc[] getMediaDescs() {
		return _mediaDescs;
	}
	
	/**
	 * @see #getMediaDescs()
	 */
	public void setMediaDescs(MediaDesc[] mediaDescs) {
		_mediaDescs = mediaDescs;
	}
	
	@Override
	public String getMediaAddr() {
		return null;
	}
	
	@Override
	public boolean getNoOffer() {
		return false;
	}
	
	@Override
	public int getRefuseTime() {
		return -1;
	}
	
	@Override
	public Direction getDirection() {
		return Direction.FULL_DUPLEX;
	}

	/**
	 * Normalizes options, must be called after option parsing.
	 */
	public void normalize() {
		if ("none".equals(_recordingDir)) {
			_recordingDir = null;
		}
		
		if (!_conversationDir.isDirectory()) {
			System.err.println("Conversation directory does not exist: " + conversationDir().getAbsolutePath());
			System.exit(1);
		}
		if (_testPrefix != null && _testPrefix.isBlank()) {
			_testPrefix = null;
		}
	}
	
}
