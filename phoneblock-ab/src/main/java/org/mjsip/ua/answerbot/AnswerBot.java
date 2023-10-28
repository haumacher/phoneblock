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
import java.io.IOError;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.mjsip.media.AudioStreamer;
import org.mjsip.media.FlowSpec;
import org.mjsip.media.MediaDesc;
import org.mjsip.media.MediaStreamer;
import org.mjsip.media.RtpStreamReceiver;
import org.mjsip.media.RtpStreamReceiverListener;
import org.mjsip.media.RtpStreamReceiverListenerAdapter;
import org.mjsip.media.RtpStreamSender;
import org.mjsip.media.RtpStreamSenderListener;
import org.mjsip.media.StreamerOptions;
import org.mjsip.media.rx.AudioReceiver;
import org.mjsip.media.rx.AudioRxHandle;
import org.mjsip.media.rx.RtpAudioRxHandler;
import org.mjsip.media.rx.RtpReceiverOptions;
import org.mjsip.media.tx.AudioTXHandle;
import org.mjsip.media.tx.AudioTransmitter;
import org.mjsip.media.tx.RtpAudioTxHandle;
import org.mjsip.media.tx.RtpSenderOptions;
import org.mjsip.rtp.RtpControl;
import org.mjsip.rtp.RtpPayloadFormat;
import org.mjsip.sip.address.NameAddress;
import org.mjsip.sip.call.RegistrationOptions;
import org.mjsip.sip.message.SipMessage;
import org.mjsip.sip.provider.SipConfig;
import org.mjsip.sip.provider.SipProvider;
import org.mjsip.sip.provider.SipStack;
import org.mjsip.sound.AudioFile;
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
import org.mjsip.ua.sound.AlawSilenceTrimmer;
import org.mjsip.ua.streamer.StreamerFactory;
import org.slf4j.LoggerFactory;
import org.zoolu.net.UdpSocket;
import org.zoolu.sound.CodecType;
import org.zoolu.sound.SimpleAudioSystem;
import org.zoolu.util.Encoder;
import org.zoolu.util.Flags;

/**
 * {@link AnswerBot} is a VOIP server that automatically accepts incoming calls, sends an audio file and records
 * input received from the remote end.
 */
public class AnswerBot extends MultipleUAS {
	
	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(AnswerBot.class);

	/** Media file to play when answering the call. */
	public static String DEFAULT_ANNOUNCEMENT_FILE="./announcement-8000hz-mono-a-law.wav";

	private MediaConfig _mediaConfig;

	private final StreamerFactory _streamerFactory;

	/** 
	 * Creates an {@link AnswerBot}. 
	 */
	public AnswerBot(SipProvider sip_provider, StreamerFactory streamerFactory, RegistrationOptions regOptions,
			UAOptions uaConfig, MediaConfig mediaConfig, PortPool portPool, ServiceOptions serviceConfig) {
		super(sip_provider,portPool, regOptions, uaConfig, serviceConfig);
		_streamerFactory = streamerFactory;
		_mediaConfig = mediaConfig;
	}
	
	@Override
	protected UserAgentListener createCallHandler(SipMessage msg) {
		return new UserAgentListenerAdapter() {
			@Override
			public void onUaIncomingCall(UserAgent ua, NameAddress callee, NameAddress caller, MediaDesc[] media_descs) {
				LOG.info("Incomming call from: " + callee.getAddress());
				ua.accept(new MediaAgent(_mediaConfig.getMediaDescs(), _streamerFactory));
			}
		};
	}

	/** 
	 * The main entry point. 
	 */
	public static void main(String[] args) {
		String program = AnswerBot.class.getSimpleName();
		LOG.info(program + " " + SipStack.version);

		Flags flags=new Flags(program, args);
		String config_file=flags.getString("-f","<file>", System.getProperty("user.home") + "/.mjsip-answerbot" ,"loads configuration from the given file");
		SipConfig sipConfig = SipConfig.init(config_file, flags);
		UAConfig uaConfig = UAConfig.init(config_file, flags, sipConfig);
		SchedulerConfig schedulerConfig = SchedulerConfig.init(config_file);
		MediaConfig mediaConfig = MediaConfig.init(config_file, flags);
		PortConfig portConfig = PortConfig.init(config_file, flags);
		ServiceOptions serviceConfig=ServiceConfig.init(config_file, flags);         
		flags.close();

		Map<String, List<File>> audioFragments = new HashMap<>();
		File conversation = new File("./conversation");
		for (File type : conversation.listFiles(f -> f.isDirectory() && !f.getName().startsWith("."))) {
			ArrayList<File> files = new ArrayList<>();
			String typeName = type.getName();
			audioFragments.put(typeName, files);
			for (File wav : type.listFiles(f -> f.isFile() && f.getName().endsWith(".wav"))) {
				files.add(wav);
				
				LOG.info("Found audio fragment for " + typeName + ": " + wav.getPath());
			}
		}
		
		StreamerFactory streamerFactory = new StreamerFactory() {
			@Override
			public MediaStreamer createMediaStreamer(FlowSpec flow_spec) {
				SpeechDispatcher speechDispatcher = new SpeechDispatcher(audioFragments);

				int sampleRate = flow_spec.getMediaSpec().getSampleRate();
				try {
					OutputStream recording = AudioFile.getAudioFileOutputStream("recording.wav", SimpleAudioSystem.getAudioFormat(flow_spec.getMediaSpec().getCodecType(), sampleRate));
							
					int bufferTime = 20;
					int minSilenceTime = 500;
					int paddingTime = 100;
					double silenceDb = -30;
					AlawSilenceTrimmer silenceTrimmer = new AlawSilenceTrimmer(sampleRate, bufferTime, minSilenceTime, paddingTime, silenceDb, recording, speechDispatcher);
					
					AudioTransmitter tx = new AudioTransmitter() {
						@Override
						public AudioTXHandle createSender(RtpSenderOptions options, UdpSocket udp_socket, AudioFormat audio_format,
								CodecType codec, int payload_type, RtpPayloadFormat payloadFormat, int sample_rate, int channels,
								Encoder additional_encoder, long packet_time, int packet_size, String remote_addr, int remote_port,
								RtpStreamSenderListener listener, RtpControl rtpControl) throws IOException {
							
							RtpStreamSender sender = new RtpStreamSender(options, speechDispatcher, true, payload_type, payloadFormat, sample_rate,
									channels, packet_time, packet_size, additional_encoder, udp_socket, remote_addr, remote_port, rtpControl, listener);
							return new RtpAudioTxHandle(sender);
						}
					};
					
					AudioReceiver rx = new AudioReceiver() {
						@Override
						public AudioRxHandle createReceiver(RtpReceiverOptions options, UdpSocket socket,
								AudioFormat audio_format, CodecType codec, int payload_type, RtpPayloadFormat payloadFormat,
								int sample_rate, int channels, Encoder additional_decoder,
								RtpStreamReceiverListener listener) throws IOException, UnsupportedAudioFileException {
							
							RtpStreamReceiverListener onTerminate = new RtpStreamReceiverListenerAdapter() {
								@Override
								public void onRtpStreamReceiverTerminated(RtpStreamReceiver rr, Exception error) {
									try {
										silenceTrimmer.close();
										recording.close();
									} catch (IOException ex) {
										LOG.error("Failed to close recording.", ex);
									}
								}
							}.andThen(listener);
							
							return new RtpAudioRxHandler(new RtpStreamReceiver(options, silenceTrimmer, additional_decoder, payloadFormat, socket, onTerminate));
						}
					};
					StreamerOptions options = StreamerOptions.builder().build();
					return new AudioStreamer(flow_spec, tx, rx, options);
				} catch (IOException | UnsupportedAudioFileException ex) {
					throw new IOError(ex);
				}
			}
		};
		
		SipProvider sipProvider = new SipProvider(sipConfig, new Scheduler(schedulerConfig));
		new AnswerBot(sipProvider, streamerFactory, uaConfig, uaConfig, mediaConfig, portConfig.createPool(), serviceConfig);
	}    

}
