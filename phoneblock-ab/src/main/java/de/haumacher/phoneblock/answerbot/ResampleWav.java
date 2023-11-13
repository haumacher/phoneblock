/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.answerbot;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.FloatBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.mjsip.sound.AudioFile;

import com.laszlosystems.libresample4j.Resampler;

/**
 * Tool for producing versions of WAV files of different sample rate from a master file.
 */
public class ResampleWav {
	
	public static void main(String[] args) throws IOException, UnsupportedAudioFileException {
		AudioInputStream in = AudioFile.getAudioFileInputStream(args[0]);
		AudioInputStream signed = encode(in, Encoding.PCM_SIGNED);
		AudioInputStream mono = mono(signed);
		AudioInputStream scaled = scale(mono, 16);
		AudioInputStream resampled = resample(scaled, 16000);
		AudioInputStream encoded = encode(resampled, Encoding.ALAW);
		
		try (OutputStream out = AudioFile.getAudioFileOutputStream(args[0] + "-16000.wav", encoded.getFormat())) {
			byte buffer[] = new byte[4096];
			int direct;
			while ((direct = encoded.read(buffer)) > 0) {
				out.write(buffer, 0, direct);
			}
		}
	}

	private static AudioInputStream encode(AudioInputStream in, Encoding encoding) {
		return AudioSystem.getAudioInputStream(withEncoding(in.getFormat(), encoding), in);
	}

	private static AudioFormat withEncoding(AudioFormat format, Encoding encoding) {
		int sampleSizeInBits = encoding == Encoding.ALAW || encoding == Encoding.ULAW ? 8 : format.getSampleSizeInBits();
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
	
	private static AudioInputStream resample2(AudioInputStream in, int sampleRate) {
		AudioFormat inputFormat = in.getFormat();
		float origSampleRate = inputFormat.getSampleRate();
		if (sampleRate == origSampleRate) {
			return in;
		}
		
		AudioFormat outputFormat = withSampleRate(inputFormat, sampleRate);
		
		if (inputFormat.getChannels() > 1) {
			throw new IllegalArgumentException("Only mono streams supported.");
		}
		if (inputFormat.getEncoding() != Encoding.PCM_SIGNED) {
			throw new IllegalArgumentException("Only PCM_SIGNED streams supported.");
		}

		double factor = ((double) sampleRate) / origSampleRate;
		
		Resampler resampler = new Resampler(true, factor, factor);
		
		int frameSize = inputFormat.getFrameSize();
		
		byte[] inBuffer = new byte[frameSize * 1024];
		byte[] outBuffer = new byte[frameSize * 1024];
		
		FloatBuffer inputBuffer = FloatBuffer.allocate(1024);
		FloatBuffer outputBuffer = FloatBuffer.allocate(1024);
		
		int sampleSizeInBits = inputFormat.getSampleSizeInBits();
		int fullScale = (1 << (sampleSizeInBits - 1)) - 1;

		boolean bigEndian = inputFormat.isBigEndian();
		int sampleSizeInBytes = sampleSizeInBits / 8;
		if (sampleSizeInBits % 8 > 0) {
			sampleSizeInBytes++;
		}
		int maxShift = (sampleSizeInBytes - 1) * 8;
		
		long targetLength = (long) (in.getFrameLength() * sampleRate / origSampleRate);
		
		return new AudioInputStream(new InputStream() {
			int inSize = 0;
			
			int outSize = 0;
			int outPos = 0;
			
			@Override
			public int read() throws IOException {
				while (true) {
					if (outPos < outSize) {
						return outBuffer[outPos++] & 0xFF;
					}
					
					outPos = 0;
					outSize = 0;
					
					int direct = in.read(inBuffer, inSize, inBuffer.length - inSize);
					if (direct < 0) {
						boolean complete = resampler.process(factor, inputBuffer, true, outputBuffer);
						inputBuffer.flip();
						if (complete) {
							return -1;
						}
						
						encode();
						continue;
					}
					
					inSize += direct;
					
					int max = inSize - (inSize % frameSize);
					for (int n = 0; n < max;) {
						int sample;
						
						if (bigEndian) {
							// Read sign.
							sample = inBuffer[n++] << maxShift;
							for (int shift = maxShift - 1; shift >= 0; shift-=8) {
								sample |= (inBuffer[n++] & 0xFF) << shift;
							}
						} else {
							sample = 0;
							for (int shift = 0; shift < maxShift; shift+=8) {
								sample |= (inBuffer[n++] & 0xFF) << shift;
							}
							// Read sign.
							sample |= inBuffer[n++] << maxShift;
						}
						
						float value = ((float) sample) / fullScale;
						inputBuffer.put(value);
					}
					
					if (max < inSize) {
						System.arraycopy(inBuffer, max, inBuffer, 0, inSize - max);
						inSize -= max;
					} else {
						inSize = 0;
					}
					
					resampler.process(factor, inputBuffer, false, outputBuffer);
					inputBuffer.flip();
					encode();
				}
			}

			private void encode() {
				while (outputBuffer.hasRemaining()) {
					int outSample = (int) (outputBuffer.get() * fullScale);
					
					if (bigEndian) {
						for (int shift = maxShift; shift >= 0; shift -= 8) {
							outBuffer[outSize++] = (byte) (outSample >>> shift);
						}
					} else {
						for (int shift = 0; shift <= maxShift; shift += 8) {
							outBuffer[outSize++] = (byte) (outSample >>> shift);
						}
					}
				}
				outputBuffer.flip();
			}
		}, outputFormat, targetLength);
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
