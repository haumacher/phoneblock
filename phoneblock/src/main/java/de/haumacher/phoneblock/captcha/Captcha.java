/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.captcha;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;

import javax.imageio.ImageIO;

/**
 * Generator for a CAPTCHA image that hides a random text.
 */
public class Captcha {

	private static final String SAVE_CHARS = "23456789qwertzuiopasdfghjkyxcvbnmQWERTZUPASDFGHJKLYXCVBNM";

	private static final int WIDTH = 250;
	private static final int HEIGHT = 60;
	private static final int MIN_WIDTH = 20;
	private static final int MIN_HEIGHT = 20;
	private static final int MAX_WIDTH = 60;
	private static final int MAX_HEIGHT = 40;
	private static final float HUE_RESERVE = 0.2f;
	private static final float HUE_RANGE = 0.1f;

	private final Random _rnd;
	
	private float _textHue;
	
	boolean _innerRange;
	private float _hueReservedStart;
	private float _hueReservedStop;

	private String _text;

	private byte[] _png;
	
	/** 
	 * Creates a {@link Captcha}.
	 */
	public Captcha() {
		this(new Random());
	}
	
	/** 
	 * Creates a {@link Captcha}.
	 */
	public Captcha(Random rnd) {
		_rnd = rnd;
		_text = generateText();
		_png = generatePng();
	}
	
	/**
	 * The CAPTCHA text.
	 */
	public String getText() {
		return _text;
	}
	
	/**
	 * The PNG-encoded CAPTCHA image hiding the {@link #getText()}.
	 */
	public byte[] getPng() {
		return _png;
	}
	
	private byte[] generatePng() {
		BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_3BYTE_BGR);
		Graphics2D graphics = (Graphics2D) img.getGraphics();
		
		_textHue = _rnd.nextFloat();
		_hueReservedStart = _textHue - HUE_RESERVE;
		_hueReservedStop = _textHue + HUE_RESERVE;
		
		if (_hueReservedStart < 0.0f) {
			//    |---- t ----|
			// ------|----------------------|-----
			//       0                      1
			//       |-- t ----|         |--|
			_hueReservedStart = 1.0f + _hueReservedStart;
			_innerRange = true;
		}
		
		if (_hueReservedStop > 1.0f) {
			//                    |---- t ----|
			// ------|----------------------|-----
			//       0                      1
			//       |--|         |---- t --|
			_hueReservedStop = _hueReservedStop - 1.0f;
			_innerRange = true;
		}
		
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setColor(randomColor());
		graphics.fillRect(0, 0, WIDTH, HEIGHT);
		AffineTransform initial = graphics.getTransform();
		
		for (int n = 0, cnt = 20; n < cnt; n++) {
			graphics.setColor(randomColor());
			int x = _rnd.nextInt(WIDTH) - MIN_WIDTH;
			int y = _rnd.nextInt(HEIGHT) - MIN_HEIGHT;
			int w = MIN_WIDTH + _rnd.nextInt(MAX_WIDTH - MIN_WIDTH);
			int h = MIN_HEIGHT + _rnd.nextInt(MAX_HEIGHT - MIN_HEIGHT);
			
			graphics.transform(AffineTransform.getRotateInstance(_rnd.nextFloat() * Math.PI, x + w / 2, y + h / 2));
			
			switch (_rnd.nextInt(2)) {
			case 0: {
				graphics.fillRect(x, y, w, h);
				break;
			}
			case 1: {
				graphics.fillOval(x, y, w, h);
				break;
			}
			}
			
			graphics.setTransform(initial);
		}
		Font font = Font.decode(Font.SANS_SERIF + " BOLD " + (HEIGHT - 20));
		graphics.setFont(font);

		FontMetrics fontMetrics = graphics.getFontMetrics();
		int textWidth = fontMetrics.stringWidth(_text);
		
		int x = 10;
		int gap = (WIDTH - 2*x - textWidth) / (_text.length() - 1);
		for (int n = 0, cnt = _text.length(); n < cnt; n++) {
			graphics.setColor(getColor(_textHue - HUE_RANGE + _rnd.nextFloat() * HUE_RANGE * 2));
			
			char ch = _text.charAt(n);
			int yOffset = _rnd.nextInt(20) - 10;
			int charWidth = fontMetrics.charWidth(ch);
			
			graphics.transform(AffineTransform.getRotateInstance(
				(_rnd.nextFloat() * 40 - 20) / 360 * (2 * Math.PI), x + charWidth / 2, HEIGHT / 2 + yOffset));
			
			graphics.drawString(Character.toString(ch), x, HEIGHT - 15 + yOffset);
			x += charWidth + gap;
			
			graphics.setTransform(initial);
		}
		
		ByteArrayOutputStream pngBuffer = new ByteArrayOutputStream();
		try {
			ImageIO.write(img, "png", pngBuffer);
		} catch (IOException ex) {
			throw new IOError(ex);
		}
		return pngBuffer.toByteArray();
	}

	private String generateText() {
		StringBuffer textBuffer = new StringBuffer();
		for (int n = 0; n < 7; n++) {
			char ch = SAVE_CHARS.charAt(_rnd.nextInt(SAVE_CHARS.length()));
			textBuffer.append(ch);
		}
		return textBuffer.toString();
	}

	private Color randomColor() {
		float hue;
		while (true) {
			hue = _rnd.nextFloat();
			if (_innerRange) {
				if (hue <= _hueReservedStop || hue >= _hueReservedStart) {
					continue;
				}
			} else {
				if (hue >= _hueReservedStart && hue <= _hueReservedStop) {
					continue;
				}
			}
			
			break;
		}
		return getColor(hue);
	}

	private Color getColor(float hue) {
		return Color.getHSBColor(hue, 1.0f, 0.8f + _rnd.nextFloat() * 0.2f);
	}

	/**
	 * Main method for testing.
	 */
	public static void main(String[] args) throws IOException {
		try (OutputStream out = new FileOutputStream("test.png")) {
			out.write(new Captcha().getPng());
		}
	}
}
