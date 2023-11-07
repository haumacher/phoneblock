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
package de.haumacher.phoneblock.answerbot;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

import org.mjsip.config.OptionParser;
import org.mjsip.media.MediaDesc;
import org.mjsip.pool.PortConfig;
import org.mjsip.pool.PortPool;
import org.mjsip.sip.address.NameAddress;
import org.mjsip.sip.address.SipURI;
import org.mjsip.sip.call.ExtendedCall;
import org.mjsip.sip.message.SipMessage;
import org.mjsip.sip.provider.SipConfig;
import org.mjsip.sip.provider.SipProvider;
import org.mjsip.time.Scheduler;
import org.mjsip.time.SchedulerConfig;
import org.mjsip.ua.MediaAgent;
import org.mjsip.ua.MultipleUAS;
import org.mjsip.ua.UserAgent;
import org.mjsip.ua.UserAgentListener;
import org.mjsip.ua.UserAgentListenerAdapter;
import org.mjsip.ua.UserOptions;
import org.mjsip.ua.registration.RegistrationClient;
import org.mjsip.ua.registration.RegistrationLogger;
import org.mjsip.ua.streamer.StreamerFactory;
import org.slf4j.LoggerFactory;

import de.haumacher.msgbuf.json.JsonReader;
import de.haumacher.msgbuf.server.io.ReaderAdapter;
import de.haumacher.phoneblock.app.api.model.NumberInfo;

/**
 * {@link AnswerBot} is a VOIP server that automatically accepts incoming calls, sends an audio file and records
 * input received from the remote end.
 */
public class AnswerBot extends MultipleUAS {
	
	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(AnswerBot.class);

	private static final String VERSION;
	
	static {
		String version;
		try (InputStream in = AnswerBot.class.getResourceAsStream("/META-INF/maven/de.haumacher/phoneblock-ab/pom.properties")) {
			Properties properties = new Properties();
			properties.load(in);
			version = properties.getProperty("version");
		} catch (IOException ex) {
			LOG.error("Faild to read version.", ex);
			version = null;
		}
		
		VERSION = version == null ? "unknown" : version;
	}

	private final Map<String, List<File>> _audioFragments;

	private AnswerbotOptions _botConfig;

	private Function<String, UserOptions> _configForUser;

	/** 
	 * Creates an {@link AnswerBot}. 
	 */
	public AnswerBot(SipProvider sip_provider, AnswerbotOptions botOptions, Function<String, UserOptions> configForUser, PortPool portPool) {
		super(sip_provider, portPool, botOptions);
		_configForUser = configForUser;
		_botConfig = botOptions;

		Map<String, List<File>> audioFragments = new HashMap<>();
		File conversationDir = botOptions.conversationDir();
		for (File type : conversationDir.listFiles(f -> f.isDirectory() && !f.getName().startsWith("."))) {
			ArrayList<File> files = new ArrayList<>();
			String typeName = type.getName();
			audioFragments.put(typeName, files);
			for (File wav : type.listFiles(f -> f.isFile() && f.getName().endsWith(".wav"))) {
				files.add(wav);
				
				LOG.info("Found audio fragment for " + typeName + ": " + wav.getPath());
			}
		}
		_audioFragments = audioFragments;
	}
	
	@Override
	protected void onInviteReceived(SipMessage msg) {
		String from = msg.getFromUser();
		LOG.info("Incomming call from: " + from);
		
		String userName = SipURI.parseSipURI(msg.getToHeader().getValue()).getUserName();
		UserOptions user = _configForUser.apply(userName);
		if (user == null) {
			// Ignore.
			return;
		}
		
		final UserAgent ua = new UserAgent(sip_provider, _portPool, _config.forUser(user), createCallHandler(from));
		
		// since there is still no proper method to init the UA with an incoming call, trick it by using the onNewIncomingCall() callback method
		new ExtendedCall(sip_provider,msg,ua);
	}
	
	protected UserAgentListener createCallHandler(String from) {
		if (from == null) {
			// An anonymous call, accept.
			if (_botConfig.getAcceptAnonymous()) {
				return spamHandler();
			} else {
				return rejectHandler();
			}
		} else if (from.startsWith("**")) {
			// A local test call, accept.
			return spamHandler();
		} else {
			NumberInfo info;
			try {
				URL url = new URL("https://phoneblock.haumacher.de/phoneblock/api/num/" + from + "?format=json");
				URLConnection connection = url.openConnection();
				connection.addRequestProperty("accept", "application/json");
				connection.addRequestProperty("User-Agent", "PhoneBlock-AB/" + VERSION);
				try (InputStream in = connection.getInputStream()) {
					info = NumberInfo.readNumberInfo(new JsonReader(new ReaderAdapter(new InputStreamReader(in))));
				}
			} catch (IOException ex) {
				LOG.warn("Contacting PhoneBlock failed: " + ex.getMessage());
				return rejectHandler();
			}
			
			if (info.getVotes() < _botConfig.getMinVotes()) {
				// Not considered SPAM.
				LOG.info("Not spam: " + from + " (" + info.getVotes() + " votes)");
				return rejectHandler();
			}
			
			return spamHandler();
		}
	}

	/**
	 * A {@link UserAgentListener} that accepts the call and starts a nonsense conversation with the counterpart.
	 */
	private UserAgentListener spamHandler() {
		return new UserAgentListenerAdapter() {
			@Override
			public void onUaIncomingCall(UserAgent ua, NameAddress callee, NameAddress caller, MediaDesc[] media_descs) {
				String recordingFile;
				String recordingDir = _botConfig.recordingDir();
				if (recordingDir != null) {
					String callId = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss-SSS").format(new Date()) + " " + name(caller);
					recordingFile = recordingDir +  "/" + callId + ".wav";
				} else {
					recordingFile = null;
				}
				StreamerFactory streamerFactory = new DialogueFactory(_botConfig, _audioFragments, recordingFile);
				
				ua.accept(new MediaAgent(_botConfig.getMediaDescs(), streamerFactory));
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

	/**
	 * A {@link UserAgentListener} that rejects the call.
	 */
	private UserAgentListener rejectHandler() {
		return new UserAgentListenerAdapter() {
			@Override
			public void onUaIncomingCall(UserAgent ua, NameAddress callee, NameAddress caller, MediaDesc[] media_descs) {
				ua.hangup();
			}
		};
	}
	
	/** 
	 * The main entry point. 
	 */
	public static void main(String[] args) {
		String program = AnswerBot.class.getSimpleName();
		LOG.info(program + " " + VERSION);

		SipConfig sipConfig = new SipConfig();
		CustomerConfig userConfig = new CustomerConfig();
		SchedulerConfig schedulerConfig = new SchedulerConfig();
		PortConfig portConfig = new PortConfig();
		AnswerbotConfig botConfig = new AnswerbotConfig();
		
		OptionParser.parseOptions(args, ".phoneblock", sipConfig, botConfig, userConfig, schedulerConfig, portConfig);
		
		sipConfig.normalize();
		botConfig.normalize();

		SipProvider sipProvider = new SipProvider(sipConfig, new Scheduler(schedulerConfig));
		new AnswerBot(sipProvider, botConfig, (id) -> userConfig, portConfig.createPool());
		
		RegistrationClient rc = new RegistrationClient(sipProvider, userConfig, new RegistrationLogger());
		rc.loopRegister(userConfig);
	}

}
