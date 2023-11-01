/*
 * Copyright (C) 2007 Luca Veltri - University of Parma - Italy
 * 
 * This source code is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * 
 * Author(s):
 * Luca Veltri (luca.veltri@unipr.it)
 */
package org.mjsip.ua.answerbot;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.kohsuke.args4j.Option;
import org.mjsip.config.OptionParser;
import org.mjsip.media.MediaDesc;
import org.mjsip.sip.address.NameAddress;
import org.mjsip.sip.call.RegistrationOptions;
import org.mjsip.sip.message.SipMessage;
import org.mjsip.sip.provider.SipConfig;
import org.mjsip.sip.provider.SipProvider;
import org.mjsip.sip.provider.SipStack;
import org.mjsip.time.Scheduler;
import org.mjsip.time.SchedulerConfig;
import org.mjsip.ua.MediaAgent;
import org.mjsip.ua.MediaConfig;
import org.mjsip.ua.MultipleUAS;
import org.mjsip.ua.ServiceConfig;
import org.mjsip.ua.ServiceOptions;
import org.mjsip.ua.UAConfig;
import org.mjsip.ua.UAOptions;
import org.mjsip.ua.UserAgent;
import org.mjsip.ua.UserAgentListener;
import org.mjsip.ua.UserAgentListenerAdapter;
import org.mjsip.ua.pool.PortConfig;
import org.mjsip.ua.pool.PortPool;
import org.mjsip.ua.streamer.StreamerFactory;
import org.slf4j.LoggerFactory;

/**
 * {@link AnswerBot} is a VOIP server that automatically accepts incoming calls, sends an audio file and records
 * input received from the remote end.
 */
public class AnswerBot extends MultipleUAS {
	
	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(AnswerBot.class);

	private final MediaConfig _mediaConfig;

	private final Map<String, List<File>> _audioFragments;

	private final String _recordingDir;

	/** 
	 * Creates an {@link AnswerBot}. 
	 * @param recordingDir 
	 */
	public AnswerBot(SipProvider sip_provider, Map<String, List<File>> audioFragments, RegistrationOptions regOptions,
			UAOptions uaConfig, MediaConfig mediaConfig, PortPool portPool, ServiceOptions serviceConfig, String recordingDir) {
		super(sip_provider,portPool, regOptions, uaConfig, serviceConfig);
		_audioFragments = audioFragments;
		_mediaConfig = mediaConfig;
		_recordingDir = recordingDir;
	}
	
	@Override
	protected UserAgentListener createCallHandler(SipMessage msg) {
		return new UserAgentListenerAdapter() {
			@Override
			public void onUaIncomingCall(UserAgent ua, NameAddress callee, NameAddress caller, MediaDesc[] media_descs) {
				String recordingFile;
				if (_recordingDir != null) {
					String callId = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss-SSS").format(new Date()) + " " + name(caller);
					recordingFile = _recordingDir +  "/" + callId + ".wav";
				} else {
					recordingFile = null;
				}
				StreamerFactory streamerFactory = new DialogueFactory(_audioFragments, recordingFile);
				
				LOG.info("Incomming call from: " + callee.getAddress());
				ua.accept(new MediaAgent(_mediaConfig.getMediaDescs(), streamerFactory));
			}

			private String name(NameAddress caller) {
				return afterAt(caller.getAddress().getSpecificPart());
			}

			private String afterAt(String specificPart) {
				int atIndex = specificPart.indexOf('@');
				if (atIndex < 0) {
					return specificPart;
				}
				return specificPart.substring(0, atIndex);
			}
		};
	}
	
	public static class Config {
		@Option(name = "--conversation", usage = "Directory with WAV files used for streaming during automatic conversation.")
		String conversationDir = "./conversation";
		
		@Option(name = "--recodings", usage = "Directory where to store recordings to, 'none' to disable recording. ")
		String recordingDir = ".";
		
		/**
		 * The configured recoding directory.
		 */
		public String getRecordingDir() {
			return recordingDir == null || recordingDir.isBlank() || "none".equals(recordingDir) ? null : recordingDir;
		}
	}

	/** 
	 * The main entry point. 
	 */
	public static void main(String[] args) {
		String program = AnswerBot.class.getSimpleName();
		LOG.info(program + " " + SipStack.version);

		SipConfig sipConfig = new SipConfig();
		UAConfig uaConfig = new UAConfig();
		SchedulerConfig schedulerConfig = new SchedulerConfig();
		MediaConfig mediaConfig = new MediaConfig();
		PortConfig portConfig = new PortConfig();
		ServiceConfig serviceConfig = new ServiceConfig();
		
		Config config = new Config();

		OptionParser.parseOptions(args, ".mjsip-answerbot", sipConfig, uaConfig, schedulerConfig, mediaConfig, portConfig, serviceConfig, config);
		
		sipConfig.normalize();
		uaConfig.normalize(sipConfig);
		mediaConfig.normalize();

		Map<String, List<File>> audioFragments = new HashMap<>();
		File conversation = new File(config.conversationDir);
		for (File type : conversation.listFiles(f -> f.isDirectory() && !f.getName().startsWith("."))) {
			ArrayList<File> files = new ArrayList<>();
			String typeName = type.getName();
			audioFragments.put(typeName, files);
			for (File wav : type.listFiles(f -> f.isFile() && f.getName().endsWith(".wav"))) {
				files.add(wav);
				
				LOG.info("Found audio fragment for " + typeName + ": " + wav.getPath());
			}
		}
		
		SipProvider sipProvider = new SipProvider(sipConfig, new Scheduler(schedulerConfig));
		new AnswerBot(sipProvider, audioFragments, uaConfig, uaConfig, mediaConfig, portConfig.createPool(), serviceConfig, config.recordingDir);
	}    

}
