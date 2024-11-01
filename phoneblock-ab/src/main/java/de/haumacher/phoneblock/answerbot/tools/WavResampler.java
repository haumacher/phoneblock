/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.answerbot.tools;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.mjsip.sound.AudioFile;

/**
 * Tool for producing versions of WAV files of different sample rate from a master file.
 */
public class WavResampler {
	
	static class Options {
		@Argument(usage = "The input audio files.", required = true, multiValued = true)
		String[] inputs;

		@Option(name = "-o", usage = "The output audio file name.")
		String output;

		@Option(name = "-r", usage = "The sample rate to use.")
		int sampleRate = 16000;

		public String outputName(String inputName) {
			return output != null && inputs.length == 1 ? output : baseName(inputName) + "-" + sampleRate + ".wav";
		}
	}

	static String baseName(String fileName) {
		int sepIndex = fileName.lastIndexOf('.');
		if (sepIndex < 0) {
			return fileName;
		}
		return fileName.substring(0, sepIndex);
	}
	
	/**
	 * Main entry point form the command line.
	 */
	public static void main(String[] args) throws IOException, UnsupportedAudioFileException, CmdLineException {
		Options config = new Options();
		CmdLineParser parser = new CmdLineParser(config);
		parser.parseArgument(args);
		
		for (String input : config.inputs) {
			convertToALaw(input, config.outputName(input), config.sampleRate);
		}
	}

	/** 
	 * Converts a given audio file to ALaw encoded WAV with the given sample rate.
	 *
	 * @param inFileName The source audio file.
	 * @param outFileName The converted audio file to create.
	 * @param sampleRate The sample rate to use for the target audio file.
	 */
	public static void convertToALaw(String inFileName, String outFileName, int sampleRate)
			throws FileNotFoundException, IOException, UnsupportedAudioFileException {
		AudioInputStream in = AudioFile.getAudioFileInputStream(inFileName);
		
		in = encode(in, Encoding.PCM_SIGNED);
		in = mono(in);
		in = resample(in, sampleRate);
		in = scale(in, 16);
		in = encode(in, Encoding.ALAW);
		
		try (OutputStream out = AudioFile.getAudioFileOutputStream(outFileName, in.getFormat())) {
			byte buffer[] = new byte[4096];
			int direct;
			while ((direct = in.read(buffer)) > 0) {
				out.write(buffer, 0, direct);
			}
		}
	}

	private static AudioInputStream encode(AudioInputStream in, Encoding encoding) {
		return AudioSystem.getAudioInputStream(withEncoding(in.getFormat(), encoding), in);
	}

	private static AudioFormat withEncoding(AudioFormat format, Encoding encoding) {
		int sampleSizeInBits = encoding == Encoding.ALAW || encoding == Encoding.ULAW ? 8 : (format.getSampleSizeInBits() < 16 ? 16 : format.getSampleSizeInBits());
		return new AudioFormat(encoding, 
				format.getSampleRate(), 
				sampleSizeInBits,
				format.getChannels(), 
				format.getFrameSize() * sampleSizeInBits / format.getSampleSizeInBits(), 
				format.getFrameRate(), 
				format.isBigEndian());
	}

	private static AudioInputStream resample(AudioInputStream in, int sampleRate) {
		return AudioSystem.getAudioInputStream(withSampleRate(in.getFormat(), sampleRate), in);
	}
	
	private static AudioFormat withSampleRate(AudioFormat format, int sampleRate) {
		return new AudioFormat(
				format.getEncoding(), 
				sampleRate, 
				format.getSampleSizeInBits(),
				format.getChannels(), 
				format.getFrameSize(), 
				sampleRate * format.getFrameRate() / format.getSampleRate(), 
				format.isBigEndian());
	}

	private static AudioInputStream scale(AudioInputStream in, int sampleSizeInBits) {
		return AudioSystem.getAudioInputStream(withSampleSizeInBits(in.getFormat(), sampleSizeInBits), in);
	}
	
	private static AudioFormat withSampleSizeInBits(AudioFormat format, int sampleSizeInBits) {
		return new AudioFormat(
				format.getEncoding(), 
				format.getSampleRate(), 
				sampleSizeInBits,
				format.getChannels(), 
				format.getFrameSize() * sampleSizeInBits / format.getSampleSizeInBits(), 
				format.getFrameRate(), 
				format.isBigEndian());
	}

	private static AudioInputStream mono(AudioInputStream in) {
		return AudioSystem.getAudioInputStream(withChannels(in.getFormat(), 1), in);
	}
	
	private static AudioFormat withChannels(AudioFormat format, int channels) {
		return new AudioFormat(
				format.getEncoding(), 
				format.getSampleRate(), 
				format.getSampleSizeInBits(),
				channels, 
				format.getFrameSize() * channels / format.getChannels(), 
				format.getFrameRate(), 
				format.isBigEndian());
	}
	
}
