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

import de.haumacher.phoneblock.answerbot.AudioType;
import de.haumacher.phoneblock.answerbot.SpeechType;

/**
 * Tool to set-up a conversation directory with appropriate WAV files.
 */
public class ConversationInitializer {
	
	static class Options {
		@Option(name = "-c")
		String conversationDir = "./conversation";
	}

	/**
	 * Main entry point form the command line.
	 */
	public static void main(String[] args) throws CmdLineException, IOException, UnsupportedAudioFileException {
		Options options = new Options();
		new CmdLineParser(options).parseArgument(args);
		
		for (SpeechType state : SpeechType.values()) {
			File stateDir  = new File(options.conversationDir, state.getDirName());
			stateDir.mkdirs();
			
			for (File input : stateDir.listFiles(f -> f.isFile() && f.getName().endsWith(".wav"))) {
				for (AudioType formatType : AudioType.values()) {
					String formatName = formatType.dirName();
					File outputDir = new File(stateDir, formatName);

					if (state.isSilent()) {
						WavResampler.convertToALaw(input.getAbsolutePath(), new File(outputDir, input.getName()).getAbsolutePath(), formatType.sampleRate());
					} else {
						String baseName = WavResampler.baseName(input.getName());
						File tmp = File.createTempFile(baseName, ".wav", stateDir);
						WavResampler.convertToALaw(input.getAbsolutePath(), tmp.getAbsolutePath(), formatType.sampleRate());
						
						WavFileSplitter splitter = new WavFileSplitter(tmp) {
							@Override
							protected String getOutputFileName(int partId) {
								return baseName + "-" + partId + ".wav";
							}						
						};
						outputDir.mkdir();
						splitter.setOutputDir(outputDir);
						splitter.run();
						
						tmp.delete();
						
						if (splitter.getPartCntCreated() == 0) {
							System.err.println("WARNING: No speech files produced for input: " + input.getAbsolutePath());
						}
					}
				}
			}
		}
	}
}
