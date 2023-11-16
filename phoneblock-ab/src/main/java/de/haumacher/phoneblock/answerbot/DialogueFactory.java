/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.answerbot;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.mjsip.media.AudioStreamer;
import org.mjsip.media.FlowSpec;
import org.mjsip.media.MediaSpec;
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
import org.mjsip.sound.AudioFile;
import org.mjsip.ua.sound.AlawSilenceTrimmer;
import org.mjsip.ua.streamer.StreamerFactory;
import org.slf4j.LoggerFactory;
import org.zoolu.net.UdpSocket;
import org.zoolu.sound.CodecType;
import org.zoolu.sound.SimpleAudioSystem;
import org.zoolu.util.Encoder;

/**
 * {@link StreamerFactory} creating a bot correspondence.
 */
public final class DialogueFactory implements StreamerFactory {
	
	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(DialogueFactory.class);
	
	private final Map<AudioType, Map<SpeechType, List<File>>> _audioFragmentsByType;
	private final String _recordingFile;
	private DialogOptions _config;

	/** 
	 * Creates a {@link DialogueFactory}.
	 */
	public DialogueFactory(DialogOptions config, Map<AudioType, Map<SpeechType, List<File>>> audioFragmentsByType, String recordingFile) {
		_config = config;
		_audioFragmentsByType = audioFragmentsByType;
		_recordingFile = recordingFile;
	}

	@Override
	public MediaStreamer createMediaStreamer(FlowSpec flow_spec) {
		AudioType matchedType = null;
		int maxSampleRate = 0;
		MediaSpec spec = flow_spec.getMediaSpec();
		for (AudioType type : _audioFragmentsByType.keySet()) {
			if (type.matches(spec)) {
				int sampleRate = type.sampleRate();
				if (sampleRate > maxSampleRate) {
					matchedType = type;
					maxSampleRate = sampleRate;
				}
			}
		}
		
		if (matchedType == null) {
			LOG.warn("No compatible audio found.");
			return null;
		}
		
		Map<SpeechType, List<File>> dialogue = _audioFragmentsByType.get(matchedType);
		if (dialogue == null) {
			LOG.warn("No audio data found for audio format: " + matchedType);
			return null;
		}
		
		SpeechDispatcher speechDispatcher = new SpeechDispatcher(dialogue);

		int sampleRate = flow_spec.getMediaSpec().getSampleRate();
		try {
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
			
			AudioReceiver rx;
			if (_recordingFile != null) {
				OutputStream recording = AudioFile.getAudioFileOutputStream(_recordingFile, SimpleAudioSystem.getAudioFormat(flow_spec.getMediaSpec().getCodecType(), sampleRate));
				
				AlawSilenceTrimmer silenceTrimmer = 
					new AlawSilenceTrimmer(sampleRate, _config.bufferTime(), _config.minSilenceTime(), _config.paddingTime(), _config.silenceDb(), recording, speechDispatcher);
				
				rx = new AudioReceiver() {
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
									AnswerBot.LOG.error("Failed to close recording.", ex);
								}
							}
						}.andThen(listener);
						
						return new RtpAudioRxHandler(new RtpStreamReceiver(options, silenceTrimmer, additional_decoder, payloadFormat, socket, onTerminate));
					}
				};
			} else {
				rx = null;
			}
			StreamerOptions options = StreamerOptions.builder().build();
			return new AudioStreamer(flow_spec, tx, rx, options);
		} catch (IOException ex) {
			throw new IOError(ex);
		}
	}
}