/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.answerbot.tools;

import java.io.File;
import java.io.IOException;

import javax.sound.sampled.UnsupportedAudioFileException;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.mjsip.ua.sound.WavFileSplitter;

/**
 * Tool to set-up a conversation directory with appropriate WAV files.
 */
public class ConversationInitializer {
	
	static class Options {
		@Option(name = "-c")
		String conversationDir = "./conversation";
	}
	
	static class Variant {
		int sampleRate;
		String formatName;

		/** 
		 * Creates a {@link Variant}.
		 */
		public Variant(String format, int sampleRate) {
			this.formatName = format;
			this.sampleRate = sampleRate;
		}
	}
	
	static final Variant[] VARIANTS = {
		new Variant("PCMA-WB", 16000),
		new Variant("PCMA", 8000),
	};

	/**
	 * Main entry point form the command line.
	 */
	public static void main(String[] args) throws CmdLineException, IOException, UnsupportedAudioFileException {
		Options options = new Options();
		new CmdLineParser(options).parseArgument(args);
		
		for (File dir : new File(options.conversationDir).listFiles(f -> f.isDirectory() && !f.getName().startsWith("."))) {
			for (File input : dir.listFiles(f -> f.isFile() && f.getName().endsWith(".wav"))) {
				for (Variant variant : VARIANTS) {
					String baseName = WavResampler.baseName(input.getName());
					File tmp = File.createTempFile(baseName, ".wav", dir);
					WavResampler.convertToALaw(input.getAbsolutePath(), tmp.getAbsolutePath(), variant.sampleRate);
					
					WavFileSplitter splitter = new WavFileSplitter(tmp) {
						@Override
						protected String getOutputFileName(int partId) {
							return baseName + "-" + partId + ".wav";
						}						
					};
					File outputDir = new File(dir, variant.formatName);
					outputDir.mkdir();
					splitter.setOutputDir(outputDir);
					splitter.run();
					
					tmp.delete();
				}
			}
		}
	}
}
